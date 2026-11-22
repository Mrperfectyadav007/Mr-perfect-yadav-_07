# MR. PERFECT YADAV AI — मोबाइल से Deploy

## Render पर API ऑनलाइन करें
1. GitHub में इस repository को खोलें।
2. Render.com पर GitHub से Sign in करें।
3. New → Blueprint चुनें और repository connect करें।
4. Render को `ai-server/render.yaml` मिलने दें।
5. Environment में GEMINI_API_KEY भरें। इसे GitHub code में कभी न लिखें।
6. Telegram को अलग bot से चलाना हो तो TELEGRAM_BOT_TOKEN भरें। अभी चल रहे music bot का token यहाँ न डालें।
7. Deploy पूरा होने पर Render URL खोलकर `/health` देखें। जवाब में status ok आना चाहिए।

## Telegram token सुरक्षा
यदि @Mr_perfect_yadav_07robot पहले से music bot चला रहा है, उसी token को इस backend में डालने से polling conflict हो सकता है। पहले अलग AI bot token बनाएं या मौजूदा music bot के code में AI handler integrate करें।

## API जाँच
POST /chat और POST /agent/plan उपलब्ध हैं। /agent/plan अभी केवल योजना बनाता है; फोन, वेबसाइट, संदेश, GitHub या फाइल पर वास्तविक action execute नहीं करता।
