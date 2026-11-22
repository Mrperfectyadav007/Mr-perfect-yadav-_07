
export default {
  async fetch(request, env) {
    const headers = {
      "Content-Type": "application/json",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    };

    if (request.method === "OPTIONS") {
      return new Response(null, { headers });
    }

    if (request.method !== "POST") {
      return new Response(
        JSON.stringify({ error: "POST only" }),
        { status: 405, headers }
      );
    }

    if (!env.OPENAI_API_KEY) {
      return new Response(
        JSON.stringify({ error: "API key not configured" }),
        { status: 500, headers }
      );
    }

    try {
      const body = await request.json();
      const message = String(body.message || "").trim();

      if (!message || message.length > 4000) {
        return new Response(
          JSON.stringify({ error: "Invalid message" }),
          { status: 400, headers }
        );
      }

      const result = await fetch(
        "https://api.openai.com/v1/responses",
        {
          method: "POST",
          headers: {
            "Authorization": `Bearer ${env.OPENAI_API_KEY}`,
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            model: "gpt-4.1-mini",
            input: message
          })
        }
      );

      const data = await result.json();

      return new Response(JSON.stringify(data), {
        status: result.status,
        headers
      });

    } catch (error) {
      return new Response(
        JSON.stringify({ error: "Server error" }),
        { status: 500, headers }
      );
    }
  }
};
