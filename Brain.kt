package com.jarvis.app

import android.content.Context
import android.speech.tts.TextToSpeech
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

object Brain {

    // Put your own Groq API key in the JARVIS settings area of the HTML.
    private const val DEFAULT_MODEL = "llama-3.3-70b-versatile"
    private const val API_URL = "https://api.groq.com/openai/v1/chat/completions"

    private var tts: TextToSpeech? = null

    fun ask(context: Context, prompt: String, callback: (String) -> Unit) {
        val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
        val key = prefs.getString("groq_key", "") ?: ""

        if (key.isBlank()) {
            callback("I am ready. Add your Groq API key in the JARVIS settings to enable the AI brain.")
            return
        }

        thread {
            try {
                val connection = URL(API_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $key")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 30000

                val messages = JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "You are JARVIS, a concise Android assistant. " +
                                    "Answer clearly. Never claim you performed an action unless Android actually did it."
                            )
                    )
                    .put(JSONObject().put("role", "user").put("content", prompt))

                val body = JSONObject()
                    .put("model", DEFAULT_MODEL)
                    .put("messages", messages)
                    .put("temperature", 0.4)

                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val stream = if (connection.responseCode in 200..299)
                    connection.inputStream else connection.errorStream

                val response = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                if (connection.responseCode !in 200..299) {
                    callback("AI request failed: ${json.optString("error", "unknown error")}")
                    return@thread
                }

                val answer = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

                callback(answer)
            } catch (e: Exception) {
                callback("I couldn't reach the AI service: ${e.message ?: "unknown error"}")
            }
        }
    }

    fun speak(context: Context, text: String) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.getDefault()
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS")
                }
            }
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS")
        }
    }
}
