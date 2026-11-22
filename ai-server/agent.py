import json
import os
import httpx

MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")
API_KEY = os.getenv("GEMINI_API_KEY", "").strip()

AGENT_RULES = """
You are the task planner for MR. PERFECT YADAV AI, a Hindi-first personal AI agent.
Convert the user's request into a clear, small-step plan.
Do not claim any action has been completed.
Never request passwords, OTPs, private keys, or banking PINs.
Mark actions that send messages, publish content, spend money, delete data,
change account/security settings, or access private files as requiring explicit
user confirmation before execution.
Only choose tools from this allowlist:
- answer: explain, teach, summarize, translate, brainstorm
- web_search: search public web information
- create_draft: prepare a message, post, email, document, or code without sending/publishing
- reminder: create or manage a reminder
- android_intent: request an Android app/screen action
- telegram_action: Telegram operations
- github_action: repository/code operations
- file_action: create/read/edit user-approved files
- unsupported: capability is not connected yet
If the user asks for an action that is not currently connected, say so in
limitations and do not pretend it can be done.
Return ONLY valid JSON with keys:
"understood" (string), "steps" (array of strings), "tools" (array of allowed tool names),
"needs_confirmation" (boolean), "limitations" (array of strings),
"reply" (short Hindi response).
Be conservative. If unclear, ask one concise question in reply and keep steps empty.
"""

async def create_plan(message: str) -> dict:
    if not API_KEY:
        raise RuntimeError("GEMINI_API_KEY is not configured on the server")
    url = f"https://generativelanguage.googleapis.com/v1beta/models/{MODEL}:generateContent"
    payload = {
        "systemInstruction": {"parts": [{"text": AGENT_RULES}]},
        "contents": [{"role": "user", "parts": [{"text": message[:12000]}]}],
        "generationConfig": {
            "temperature": 0.2,
            "maxOutputTokens": 1200,
            "responseMimeType": "application/json"
        }
    }
    async with httpx.AsyncClient(timeout=45) as client:
        response = await client.post(url, params={"key": API_KEY}, json=payload)
    if response.status_code >= 400:
        raise RuntimeError(f"AI provider returned HTTP {response.status_code}")
    data = response.json()
    raw = data["candidates"][0]["content"]["parts"][0]["text"]
    plan = json.loads(raw)
    allowed = {"answer", "web_search", "create_draft", "reminder",
               "android_intent", "telegram_action", "github_action",
               "file_action", "unsupported"}
    if not isinstance(plan, dict):
        raise ValueError("Invalid plan")
    plan["tools"] = [x for x in plan.get("tools", []) if x in allowed]
    plan["steps"] = [str(x)[:500] for x in plan.get("steps", [])[:8]]
    plan["limitations"] = [str(x)[:500] for x in plan.get("limitations", [])[:8]]
    plan["needs_confirmation"] = bool(plan.get("needs_confirmation", False))
    plan["understood"] = str(plan.get("understood", ""))[:1000]
    plan["reply"] = str(plan.get("reply", "मैंने आपके आदेश को समझ लिया है।"))[:1500]
    return plan
