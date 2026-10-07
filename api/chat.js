export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({
      error: "Method not allowed"
    });
  }

  try {
    const { prompt, info } = req.body || {};

    if (!prompt || typeof prompt !== "string") {
      return res.status(400).json({
        error: "Prompt is required"
      });
    }

    const apiKey = process.env.GROQ_API_KEY;

    if (!apiKey) {
      return res.status(500).json({
        error: "GROQ_API_KEY is missing"
      });
    }

    const system = `
You are JARVIS, a voice assistant inside an Android phone.

Return ONLY valid JSON in exactly this format:

{
  "say": "short natural response",
  "actions": [],
  "more": false
}

The "say" field is spoken aloud.

Available actions:

open_app
url
search
navigate
settings
system
click
type
enter
scroll
wait
call
sms
alarm
timer
flashlight
volume

If no action is required, use:
"actions":[]

Never invent screen text.

If asked who your boss is, say:
"Prem is my boss."
`;

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
                system +
                "\n\nPHONE INFO:\n" +
                (info || "No phone information available.")
            },
            {
              role: "user",
              content: prompt
            }
          ],

          temperature: 0.4,

          response_format: {
            type: "json_object"
          }
        })
      }
    );

    const data = await response.json();

    if (!response.ok) {
      console.error("GROQ ERROR:", data);

      return res.status(502).json({
        error: "Groq request failed",
        details:
          data?.error?.message ||
          data?.error ||
          "Unknown Groq error"
      });
    }

    const content =
      data?.choices?.[0]?.message?.content;

    if (!content) {
      return res.status(502).json({
        error: "No AI response received"
      });
    }

    let parsed;

    try {
      parsed = JSON.parse(content);
    } catch (error) {
      return res.status(502).json({
        error: "AI returned invalid JSON",
        raw: content
      });
    }

    return res.status(200).json({
      content: JSON.stringify(parsed)
    });

  } catch (error) {

    console.error("SERVER ERROR:", error);

    return res.status(500).json({
      error: "Server error",
      details: error?.message || "Unknown error"
    });
  }
  }
