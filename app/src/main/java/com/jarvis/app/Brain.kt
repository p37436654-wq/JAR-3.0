package com.jarvis.app

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Brain {

    const val MODEL =
        "llama-3.3-70b-versatile"

    private const val API_URL =
        "https://jar-30-yqa4.vercel.app/api/chat"

    private val history =
        ArrayList<JSONObject>()

    fun ask(
        user: String,
        info: String
    ): JSONObject {

        history.add(
            JSONObject()
                .put("role", "user")
                .put("content", user)
        )

        while (history.size > 14) {
            history.removeAt(0)
        }

        try {

            val body =
                JSONObject()
                    .put("prompt", user)
                    .put("info", info)

            val connection =
                URL(API_URL)
                    .openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "POST"

            connection.connectTimeout =
                15000

            connection.readTimeout =
                30000

            connection.setRequestProperty(
                "Content-Type",
                "application/json"
            )

            connection.doOutput = true

            connection.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
            }

            val responseCode =
                connection.responseCode

            val stream =
                if (
                    responseCode in 200..299
                ) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }

            val response =
                stream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            if (
                responseCode !in 200..299
            ) {
                throw Exception(
                    "AI server error $responseCode"
                )
            }

            val content =
                JSONObject(response)
                    .getString("content")

            history.add(
                JSONObject()
                    .put(
                        "role",
                        "assistant"
                    )
                    .put(
                        "content",
                        content
                    )
            )

            return try {

                JSONObject(content)

            } catch (e: Exception) {

                JSONObject()
                    .put(
                        "say",
                        content
                    )
                    .put(
                        "actions",
                        JSONArray()
                    )
                    .put(
                        "more",
                        false
                    )
            }

        } catch (e: Exception) {

            if (
                history.isNotEmpty() &&
                history.last()
                    .optString("role") == "user"
            ) {
                history.removeAt(
                    history.size - 1
                )
            }

            throw e
        }
    }
}                        
