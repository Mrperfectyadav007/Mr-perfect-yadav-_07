# MR. PERFECT YADAV AI — backend starter

This service provides one shared Hindi-first AI brain for the Android app and a Telegram bot.

## Features in this starter
- FastAPI endpoint: POST /chat
- Health endpoint: GET /health
- Telegram commands: /start, /help, /about
- Text messages answered by Gemini
- API keys are read only from environment variables; never commit real keys.

## Run locally
1. Copy .env.example to .env and fill in secrets privately.
2. Install requirements: pip install -r requirements.txt
3. Start: uvicorn main:app --host 0.0.0.0 --port 8000

## Hosting secrets
Set GEMINI_API_KEY and (if enabling Telegram) TELEGRAM_BOT_TOKEN in your hosting provider's private environment/secrets settings. Set GEMINI_MODEL optionally. Then run:
uvicorn main:app --host 0.0.0.0 --port $PORT

## Android integration
Use the deployed HTTPS base URL and POST /chat with JSON:
{"message":"नमस्ते","history":[]}
The Android app can use Android SpeechRecognizer for Hindi speech input and Android TextToSpeech for selectable installed Hindi female/male voices. Voice availability depends on the voices installed on the phone.

## Telegram note
This backend polls the configured bot token. Do not run a second polling process with the same token. If the existing music bot already uses @Mr_perfect_yadav_07robot, adding a second polling process to that same token will conflict; integrate the AI handlers into its existing bot process or use a separate AI bot token.
