package com.jarvis.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class JarvisService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()

    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var ttsReady = false
    private var busy = false
    private var listening = false
    private var awakeUntil = 0L

    private val wakeWord =
        Regex(
            "\\b(jarvis|jarvish|jervis|jarvi)\\b",
            RegexOption.IGNORE_CASE
        )

    override fun onBind(intent: Intent?): IBinder? = null

    // =========================================================
    // CREATE
    // =========================================================

    override fun onCreate() {
        super.onCreate()

        if (
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        startForegroundServiceNotification()
        createWakeLock()
        createTts()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        handler.postDelayed(
            {
                if (ttsReady && !busy) {
                    listen()
                }
            },
            1500
        )

        return START_STICKY
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private fun startForegroundServiceNotification() {

        val manager =
            getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel = NotificationChannel(
                "jar3",
                "JAR 3.0",
                NotificationManager.IMPORTANCE_LOW
            )

            manager.createNotificationChannel(channel)
        }

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                Notification.Builder(this, "jar3")
                    .setContentTitle("JAR 3.0 is active")
                    .setContentText("Voice assistant is ready")
                    .setSmallIcon(
                        android.R.drawable.ic_btn_speak_now
                    )
                    .setOngoing(true)
                    .build()

            } else {

                Notification.Builder(this)
                    .setContentTitle("JAR 3.0 is active")
                    .setContentText("Voice assistant is ready")
                    .setSmallIcon(
                        android.R.drawable.ic_btn_speak_now
                    )
                    .setOngoing(true)
                    .build()
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

        } else {

            startForeground(
                1,
                notification
            )
        }
    }

    // =========================================================
    // WAKE LOCK
    // =========================================================

    private fun createWakeLock() {

        try {

            val power =
                getSystemService(POWER_SERVICE)
                    as PowerManager

            wakeLock =
                power.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "JAR3:VoiceAssistant"
                )

            wakeLock?.acquire()

        } catch (_: Exception) {
        }
    }

    // =========================================================
    // TEXT TO SPEECH
    // =========================================================

    private fun createTts() {

        tts =
            TextToSpeech(this) { status ->

                handler.post {

                    if (
                        status !=
                        TextToSpeech.SUCCESS
                    ) {
                        return@post
                    }

                    val result =
                        tts?.setLanguage(
                            Locale("en", "IN")
                        )

                    if (
                        result ==
                        TextToSpeech.LANG_MISSING_DATA ||
                        result ==
                        TextToSpeech.LANG_NOT_SUPPORTED
                    ) {
                        tts?.setLanguage(
                            Locale.US
                        )
                    }

                    tts?.setPitch(0.75f)
                    tts?.setSpeechRate(1.05f)

                    tts?.setOnUtteranceProgressListener(
                        object :
                            UtteranceProgressListener() {

                            override fun onStart(
                                utteranceId: String?
                            ) {
                            }

                            override fun onDone(
                                utteranceId: String?
                            ) {

                                handler.post {

                                    if (!busy) {
                                        listenLater(500)
                                    }
                                }
                            }

                            override fun onError(
                                utteranceId: String?
                            ) {

                                handler.post {

                                    if (!busy) {
                                        listenLater(500)
                                    }
                                }
                            }
                        }
                    )

                    ttsReady = true

                    speak(
                        "JAR 3.0 online. Ready, boss."
                    )
                }
            }
    }

    private fun speak(text: String) {

        if (
            !ttsReady ||
            text.isBlank()
        ) {
            return
        }

        handler.post {

            // Stop recognition while JARVIS speaks.
            stopListeningOnly()

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jar_${System.nanoTime()}"
            )
        }
    }

    // =========================================================
    // LISTENING
    // =========================================================

    private fun createRecognizer() {

        if (recognizer != null) {
            return
        }

        recognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)

        recognizer?.setRecognitionListener(
            listener
        )
    }

    private fun listenLater(delay: Long) {

        handler.removeCallbacksAndMessages(
            "JARVIS_LISTEN"
        )

        handler.postAtTime(
            {
                listen()
            },
            "JARVIS_LISTEN",
            System.currentTimeMillis() + delay
        )
    }

    private fun listen() {

        if (
            busy ||
            !ttsReady ||
            listening
        ) {
            return
        }

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        if (
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ) {
            listenLater(3000)
            return
        }

        createRecognizer()

        try {

            val intent =
                Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE,
                        "en-IN"
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_MAX_RESULTS,
                        3
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                        false
                    )

                    // Keep the recognition session
                    // from ending too aggressively.
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1500
                    )

                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        1000
                    )
                }

            listening = true

            recognizer?.startListening(
                intent
            )

        } catch (_: Exception) {

            listening = false
            listenLater(1500)
        }
    }

    private fun stopListeningOnly() {

        if (!listening) {
            return
        }

        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }

        listening = false
    }

    // =========================================================
    // SPEECH LISTENER
    // =========================================================

    private val listener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {
                listening = true
            }

            override fun onBeginningOfSpeech() {
                listening = true
            }

            override fun onRmsChanged(
                rmsdB: Float
            ) {
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
            }

            override fun onEndOfSpeech() {
                listening = false
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
            }

            override fun onResults(
                results: Bundle?
            ) {

                listening = false

                val text =
                    results
                        ?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                if (text.isNotEmpty()) {

                    handleSpeech(text)

                } else {

                    listenLater(250)
                }
            }

            override fun onError(
                error: Int
            ) {

                listening = false

                if (
                    error ==
                    SpeechRecognizer
                        .ERROR_INSUFFICIENT_PERMISSIONS
                ) {
                    stopSelf()
                    return
                }

                // SpeechRecognizer naturally ends after
                // silence/errors. Restart quietly.
                listenLater(500)
            }
        }

    // =========================================================
    // WAKE WORD
    // =========================================================

    private fun handleSpeech(
        text: String
    ) {

        val now =
            System.currentTimeMillis()

        if (
            wakeWord.containsMatchIn(text)
        ) {

            val command =
                text
                    .replaceFirst(
                        wakeWord,
                        ""
                    )
                    .trim(
                        ' ',
                        ',',
                        '.',
                        '!',
                        '?'
                    )

            if (command.length < 2) {

                // Stay awake for 10 seconds.
                awakeUntil =
                    now + 10000

                speak(
                    "Yes boss?"
                )

            } else {

                // Direct command:
                // "Jarvis open WhatsApp"
                processCommand(
                    command
                )
            }

            return
        }

        if (
            now < awakeUntil &&
            text.isNotBlank()
        ) {

            // Follow-up command after:
            // "Jarvis"
            processCommand(text)

        } else {

            listenLater(200)
        }
    }

    // =========================================================
    // AI COMMAND
    // =========================================================

    private fun processCommand(
        command: String
    ) {

        if (busy) {
            return
        }

        busy = true
        awakeUntil = 0L

        executor.execute {

            try {

                var input = command

                repeat(6) {

                    val response =
                        Brain.ask(
                            input,
                            phoneInfo()
                        )

                    val speech =
                        response.optString(
                            "say"
                        )

                    if (
                        speech.isNotBlank()
                    ) {
                        speak(speech)
                    }

                    val failures =
                        StringBuilder()

                    val actions =
                        response.optJSONArray(
                            "actions"
                        )

                    if (actions != null) {

                        for (
                            i in 0 until actions.length()
                        ) {

                            try {

                                val action =
                                    actions.getJSONObject(i)

                                val result =
                                    Actions.run(
                                        this,
                                        action
                                    )

                                if (
                                    result != "ok"
                                ) {

                                    failures
                                        .append(result)
                                        .append("; ")
                                }

                            } catch (e: Exception) {

                                failures
                                    .append(
                                        e.message
                                            ?: "Action failed"
                                    )
                                    .append("; ")
                            }
                        }
                    }

                    if (
                        !response.optBoolean(
                            "more",
                            false
                        )
                    ) {
                        return@repeat
                    }

                    Thread.sleep(800)

                    val screen =
                        try {

                            ControlService.inst
                                ?.screenText()
                                ?: "(phone control is OFF)"

                        } catch (_: Exception) {

                            "(screen unavailable)"
                        }

                    input =
                        "RESULT: " +
                            failures.toString() +
                            "\nSCREEN: " +
                            screen
                }

            } catch (e: Exception) {

                val error =
                    e.message
                        ?: "Unknown error"

                speak(
                    "Error. " +
                        error.take(180)
                )

            } finally {

                busy = false

                handler.postDelayed(
                    {
                        listen()
                    },
                    1200
                )
            }
        }
    }

    // =========================================================
    // PHONE INFORMATION
    // =========================================================

    private fun phoneInfo(): String {

        val time =
            try {

                SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(
                    Date()
                )

            } catch (_: Exception) {

                "unknown"
            }

        val battery =
            try {

                val manager =
                    getSystemService(
                        BATTERY_SERVICE
                    ) as BatteryManager

                manager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

            } catch (_: Exception) {

                -1
            }

        val control =
            if (
                ControlService.inst != null
            ) {
                "ON"
            } else {
                "OFF"
            }

        return """
            Time: $time
            Battery: ${
                if (battery >= 0)
                    "$battery%"
                else
                    "unknown"
            }
            Phone control: $control
            Android: ${Build.VERSION.RELEASE}
            Device: ${Build.MODEL}
        """.trimIndent()
    }

    // =========================================================
    // CLEANUP
    // =========================================================

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
        listening = false

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }

        tts = null
        ttsReady = false

        try {

            if (
                wakeLock?.isHeld == true
            ) {
                wakeLock?.release()
            }

        } catch (_: Exception) {
        }

        wakeLock = null

        try {
            executor.shutdownNow()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}
