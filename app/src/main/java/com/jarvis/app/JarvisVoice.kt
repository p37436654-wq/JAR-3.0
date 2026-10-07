package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.WebView
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

object JarvisVoice {

    private var recognizer: SpeechRecognizer? = null

    private val worker =
        Executors.newSingleThreadExecutor()

    fun start(
        context: Context,
        web: WebView
    ) {

        stop()

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(context)
        ) {
            web.evaluateJavascript(
                "window.jarvisError('Speech recognition is not available');",
                null
            )
            return
        }

        recognizer =
            SpeechRecognizer
                .createSpeechRecognizer(context)
                .apply {

                    setRecognitionListener(
                        object : RecognitionListener {

                            override fun onReadyForSpeech(
                                params: Bundle?
                            ) {
                                web.evaluateJavascript(
                                    "window.jarvisState('listening');",
                                    null
                                )
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(
                                rmsdB: Float
                            ) {}

                            override fun onBufferReceived(
                                buffer: ByteArray?
                            ) {}

                            override fun onEndOfSpeech() {}

                            override fun onPartialResults(
                                partialResults: Bundle?
                            ) {}

                            override fun onEvent(
                                eventType: Int,
                                params: Bundle?
                            ) {}

                            override fun onError(
                                error: Int
                            ) {
                                web.evaluateJavascript(
                                    "window.jarvisState('idle');",
                                    null
                                )
                            }

                            override fun onResults(
                                results: Bundle?
                            ) {

                                val text =
                                    results
                                        ?.getStringArrayList(
                                            SpeechRecognizer.RESULTS_RECOGNITION
                                        )
                                        ?.firstOrNull()
                                        ?: return

                                web.evaluateJavascript(
                                    "window.jarvisTranscript(${JSONObject.quote(text)});",
                                    null
                                )

                                worker.execute {

                                    try {

                                        val response =
                                            Brain.ask(
                                                text,
                                                ""
                                            )

                                        val answer =
                                            response
                                                .optString("say")

                                        web.post {

                                            web.evaluateJavascript(
                                                "window.jarvisAnswer(${JSONObject.quote(answer)});",
                                                null
                                            )
                                        }

                                    } catch (
                                        e: Exception
                                    ) {

                                        web.post {

                                            web.evaluateJavascript(
                                                "window.jarvisError(${JSONObject.quote("AI connection failed")});",
                                                null
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )

                    val intent =
                        Intent(
                            RecognizerIntent
                                .ACTION_RECOGNIZE_SPEECH
                        ).apply {

                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE,
                                Locale.getDefault()
                            )

                            putExtra(
                                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                                false
                            )
                        }

                    startListening(intent)
                }
    }

    fun stop() {

        recognizer?.stopListening()
        recognizer?.destroy()
        recognizer = null
    }
}
