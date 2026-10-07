export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    const { prompt } = req.body || {};

    if (!prompt || typeof prompt !== "string") {
      return res.status(400).json({
        error: "Prompt is required"
      });
    }

    if (prompt.length > 4000) {
      return res.status(400).json({
        error: "Prompt is too long"
      });
    }

    const apiKey = process.env.GROQ_API_KEY;

    if (!apiKey) {
      return res.status(500).json({
        error: "Groq API key is not configured on the server"
      });
    }

    const response = await fetch(
      "https://api.groq.com/openai/v1/chat/completions",
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${apiKey}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          model: "llama-3.3-70b-versatile",
          messages: [
            {
              role: "system",
              content:
                "You are ULTRON, a personal Android AI assistant. Be concise, intelligent, helpful, and natural. Never claim that you performed an Android action unless the Android app actually performed it."
            },
            {
              role: "user",
              content: prompt
            }
          ],
          temperature: 0.4
        })
      }
    );

    const data = await response.json();

    if (!response.ok) {
      return res.status(response.status).json({
        error: "Groq request failed"
      });
    }

    const answer =
      data?.choices?.[0]?.message?.content;

    if (!answer) {
      return res.status(502).json({
        error: "No AI response received"
      });
    }

    return res.status(200).json({
      answer
    });

  } catch (error) {
    return res.status(500).json({
      error: "Server error"
    });
  }
        }
