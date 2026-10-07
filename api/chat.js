export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  try {
    const { prompt, info } = req.body || {};

    if (!prompt || typeof prompt !== "string") {
      return res.status(400).json({ error: "Prompt is required" });
    }

    const apiKey = process.env.GROQ_API_KEY;

    if (!apiKey) {
      return res.status(500).json({
        error: "Groq API key is not configured"
      });
    }

    const system = `
You are JARVIS, a voice assistant living inside the user's Android phone.

Reply with ONLY one JSON object:

{
  "say": "...",
  "actions": [],
  "more": false
}

"say" is what you speak aloud.
Keep it short and natural.

Actions can include:
open_app{name}
call{to}
sms{to,text}
alarm{hour,minute,label}
timer{seconds}
flashlight{on:true/false}
volume{level:0-100}
url{url}
search{query}
navigate{place}
settings{page}
system{what}
click{text}
type{text}
enter{}
scroll{dir}
wait{ms}

Never invent screen text.

If no action is needed, return:
"actions":[]

If anyone asks who your boss is, say exactly:
Prem is my boss.
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
              content: system + "\n" + (info || "")
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
      return res.status(response.status).json({
        error: "Groq request failed"
      });
    }

    const content =
      data?.choices?.[0]?.message?.content;

    if (!content) {
      return res.status(502).json({
        error: "No AI response received"
      });
    }

    return res.status(200).json({
      content
    });

  } catch (error) {

    return res.status(500).json({
      error: "Server error"
    });
  }
  }
        
