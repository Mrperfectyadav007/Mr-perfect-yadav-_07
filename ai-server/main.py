import os
import asyncio
from contextlib import asynccontextmanager
from typing import Optional

import httpx
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from telegram import Update
from telegram.ext import Application, CommandHandler, MessageHandler, ContextTypes, filters

GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "").strip()
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash").strip()
TELEGRAM_BOT_TOKEN = os.getenv("TELEGRAM_BOT_TOKEN", "").strip()
SYSTEM_PROMPT = (
    "You are MR. PERFECT YADAV AI, a helpful, accurate Hindi-first personal assistant. "
    "Reply naturally in the language used by the user, preferably clear Hindi. "
    "Be honest when uncertain. Do not claim to have performed actions you cannot perform."
)

class ChatRequest(BaseModel):
    message: str = Field(min_length=1, max_length=12000)
    history: list[dict[str, str]] = Field(default_factory=list, max_length=20)

class ChatResponse(BaseModel):
    reply: str
    model: str

async def ask_gemini(message: str, history: Optional[list[dict[str, str]]] = None) -> str:
    if not GEMINI_API_KEY:
        raise RuntimeError("GEMINI_API_KEY is not configured on the server")
    contents = []
    for item in (history or [])[-20:]:
        role = "model" if item.get("role") in ("assistant", "model") else "user"
        text = str(item.get("content", ""))[:12000]
        if text:
            contents.append({"role": role, "parts": [{"text": text}]})
    contents.append({"role": "user", "parts": [{"text": message}]})
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"
    payload = {
        "systemInstruction": {"parts": [{"text": SYSTEM_PROMPT}]},
        "contents": contents,
        "generationConfig": {"temperature": 0.7, "maxOutputTokens": 2048}
    }
    async with httpx.AsyncClient(timeout=45) as client:
        response = await client.post(url, params={"key": GEMINI_API_KEY}, json=payload)
    if response.status_code >= 400:
        raise RuntimeError(f"AI provider returned HTTP {response.status_code}")
    data = response.json()
    try:
        return data["candidates"][0]["content"]["parts"][0]["text"].strip()
    except (KeyError, IndexError, TypeError):
        raise RuntimeError("AI provider returned an empty response")

telegram_app = None

async def start_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "राधे राधे! 🙏\nमैं MR. PERFECT YADAV AI हूँ। अपना सवाल हिंदी या अंग्रेज़ी में भेजें।"
    )

async def help_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text(
        "/start - AI शुरू करें\n/help - सहायता\n/about - जानकारी\n"
        "सीधे अपना सवाल लिखें और AI से जवाब पाएँ।"
    )

async def about_command(update: Update, context: ContextTypes.DEFAULT_TYPE):
    await update.message.reply_text("MR. PERFECT YADAV AI — Hindi-first live AI assistant.")

async def telegram_message(update: Update, context: ContextTypes.DEFAULT_TYPE):
    if not update.message or not update.message.text:
        return
    try:
        answer = await ask_gemini(update.message.text)
        for start in range(0, len(answer), 4000):
            await update.message.reply_text(answer[start:start + 4000])
    except Exception:
        await update.message.reply_text("अभी AI से कनेक्शन नहीं हो पाया। कृपया थोड़ी देर बाद फिर कोशिश करें।")

@asynccontextmanager
async def lifespan(app: FastAPI):
    global telegram_app
    if TELEGRAM_BOT_TOKEN:
        telegram_app = Application.builder().token(TELEGRAM_BOT_TOKEN).build()
        telegram_app.add_handler(CommandHandler("start", start_command))
        telegram_app.add_handler(CommandHandler("help", help_command))
        telegram_app.add_handler(CommandHandler("about", about_command))
        telegram_app.add_handler(MessageHandler(filters.TEXT & ~filters.COMMAND, telegram_message))
        await telegram_app.initialize()
        await telegram_app.start()
        await telegram_app.updater.start_polling(drop_pending_updates=True)
    yield
    if telegram_app:
        await telegram_app.updater.stop()
        await telegram_app.stop()
        await telegram_app.shutdown()

app = FastAPI(title="MR. PERFECT YADAV AI", version="0.1.0", lifespan=lifespan)
origins = [x.strip() for x in os.getenv("ALLOWED_ORIGINS", "*").split(",") if x.strip()]
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=False,
    allow_methods=["GET", "POST"],
    allow_headers=["Content-Type", "Authorization"],
)

@app.get("/health")
async def health():
    return {"status": "ok", "assistant": "MR. PERFECT YADAV AI"}

@app.post("/chat", response_model=ChatResponse)
async def chat(body: ChatRequest):
    try:
        reply = await ask_gemini(body.message, body.history)
        return ChatResponse(reply=reply, model=GEMINI_MODEL)
    except RuntimeError as exc:
        if "not configured" in str(exc):
            raise HTTPException(status_code=503, detail="AI service is not configured")
        raise HTTPException(status_code=502, detail="AI provider is temporarily unavailable")
    except Exception:
        raise HTTPException(status_code=502, detail="AI request failed")
