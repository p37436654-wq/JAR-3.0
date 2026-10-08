package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {

    private const val API_URL =
        "https://api.groq.com/openai/v1/chat/completions"

    private const val MODEL =
        "openai/gpt-oss-120b"

    fun ask(
        user: String,
        info: String
    ): JSONObject {

        val apiKey =
            BuildConfig.GROQ_API_KEY

        if (apiKey.isBlank()) {
            throw Exception(
                "Groq API key is missing"
            )
        }

        val system =
            """
            You are JARVIS, a voice assistant inside an Android phone.

            Return ONLY one JSON object:

            {
              "say": "...",
              "actions": [],
              "more": false
            }

            "say" is what JARVIS speaks aloud.
            Keep it short and natural.

            Available actions:

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

            If no action is needed:
            "actions":[]

            If asked who your boss is, say exactly:
            Prem is my boss.

            PHONE INFORMATION:
            $info
            """.trimIndent()

        val messages =
            JSONArray()
                .put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", system)
                )
                .put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", user)
                )

        val body =
            JSONObject()
                .put("model", MODEL)
                .put("messages", messages)
                .put("temperature", 0.3)
                .put(
                    "response_format",
                    JSONObject()
                        .put("type", "json_object")
                )

        var connection: HttpURLConnection? = null

        try {

            connection =
                URL(API_URL)
                    .openConnection()
                    as HttpURLConnection

            connection.requestMethod = "POST"

            connection.connectTimeout = 15000
            connection.readTimeout = 30000

            connection.setRequestProperty(
                "Authorization",
                "Bearer $apiKey"
            )

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(
                    body.toString()
                        .toByteArray(Charsets.UTF_8)
                )
            }

            val code =
                connection.responseCode

            val stream =
                if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response =
                stream
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: ""

            if (code !in 200..299) {

                var message = response

                try {

                    message =
                        JSONObject(response)
                            .optJSONObject("error")
                            ?.optString(
                                "message",
                                response
                            )
                            ?: response

                } catch (_: Exception) {
                }

                throw Exception(
                    "Groq $code: " +
                        message.take(250)
                )
            }

            val data =
                JSONObject(response)

            val content =
                data
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")

            return try {

                JSONObject(content)

            } catch (_: Exception) {

                JSONObject()
                    .put("say", content)
                    .put(
                        "actions",
                        JSONArray()
                    )
                    .put(
                        "more",
                        false
                    )
            }

        } finally {

            connection?.disconnect()
        }
    }
}
