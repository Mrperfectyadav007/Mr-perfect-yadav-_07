# MR. PERFECT YADAV AI — मोबाइल से Deploy

## Render पर API ऑनलाइन करें
1. GitHub में इस repository को खोलें।
2. Render.com पर GitHub से Sign in करें।
3. New → Blueprint चुनें और repository connect करें।
4. Render को `ai-server/render.yaml` मिलने दें।
5. Environment में GEMINI_API_KEY भरें। इसे GitHub code में कभी न लिखें।
6. `ENABLE_STANDALONE_TELEGRAM_BOT` को `false` ही रहने दें। इससे आपके मौजूदा Music Bot के token के साथ polling conflict नहीं होगा।
7. Deploy पूरा होने पर Render URL खोलकर `/health` देखें। जवाब में status ok आना चाहिए।

## मौजूदा Music Bot में AI जोड़ना
यह API server अपने आप मौजूदा Music Bot के अंदर code नहीं डाल सकता। Music Bot का वास्तविक source-code repository चाहिए। इस repository में Android app और website हैं, Music Bot का handler/source नहीं मिला। Music Bot का GitHub link मिलने पर उसके सही framework (Pyrogram/Telethon/aiogram आदि) के अनुसार AI message handler जोड़ा जाएगा।

मौजूदा bot का token इस API server में न डालें। एक ही token से दो polling processes चलाने पर conflict हो सकता है।

## API जाँच
POST /chat और POST /agent/plan उपलब्ध हैं। /agent/plan अभी केवल योजना बनाता है; फोन, वेबसाइट, संदेश, GitHub या फाइल पर वास्तविक action execute नहीं करता।
