const ALLOWED_ORIGINS = new Set([
  "https://jarvis99.pages.dev",
  "https://mrperfectyadav007.github.io"
]);

function isAllowedOrigin(origin) {
  return ALLOWED_ORIGINS.has(origin) ||
    /^https:\/\/[a-z0-9-]+\.jarvis99\.pages\.dev$/i.test(origin);
}

function cors(origin) {
  return {
    "Access-Control-Allow-Origin": isAllowedOrigin(origin) ? origin : "null",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
    "Vary": "Origin"
  };
}

export default {
  async fetch(request, env) {
    const origin = request.headers.get("Origin") || "";
    const allowed = isAllowedOrigin(origin);
    const headers = cors(origin);

    if (request.method === "OPTIONS") {
      return new Response(null, { status: allowed ? 204 : 403, headers });
    }
    if (!allowed) {
      return Response.json({ error: "Origin not allowed. Open JARVIS from its approved Pages website." }, { status: 403, headers });
    }

    const url = new URL(request.url);
    if (url.pathname !== "/chat" || request.method !== "POST") {
      return Response.json({ error: "Use POST /chat" }, { status: 404, headers });
    }
    if (!env.OPENAI_API_KEY) {
      return Response.json({ error: "Worker में OPENAI_API_KEY secret सेट नहीं है।" }, { status: 500, headers });
    }

    try {
      const body = await request.json();
      const message = String(body.message || "").trim();
      if (!message) return Response.json({ error: "Message खाली है।" }, { status: 400, headers });
      if (message.length > 24000) return Response.json({ error: "Message बहुत लंबा है।" }, { status: 413, headers });

      const upstream = await fetch("https://api.openai.com/v1/responses", {
        method: "POST",
        headers: {
          "Authorization": "Bearer " + env.OPENAI_API_KEY,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: "gpt-6.2-astra",
          instructions: "You are JARVIS, the personal AI assistant for MR. PERFECT YADAV. Answer in the user's language. Be helpful, accurate, natural and clear. Give detailed explanations when asked; otherwise be concise.",
          input: message,
          max_output_tokens: 1400
        })
      });

      const data = await upstream.json();
      if (!upstream.ok) {
        return Response.json({ error: data.error?.message || "OpenAI API request failed" }, { status: upstream.status, headers });
      }

      const answer = (data.output || [])
        .flatMap(item => item.content || [])
        .filter(part => part.type === "output_text")
        .map(part => part.text || "")
        .join("\n").trim();

      if (!answer) return Response.json({ error: "OpenAI ने कोई text जवाब नहीं भेजा।" }, { status: 502, headers });
      return Response.json({ answer }, { headers });
    } catch (error) {
      return Response.json({ error: error.message || "Worker error" }, { status: 500, headers });
    }
  }
};
