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

    // =========================================================
    // CORE
    // =========================================================

    private val handler =
        Handler(Looper.getMainLooper())

    private val executor =
        Executors.newSingleThreadExecutor()

    private var tts: TextToSpeech? = null

    private var recognizer: SpeechRecognizer? = null

    private var wakeLock: PowerManager.WakeLock? = null

    // =========================================================
    // STATE
    // =========================================================

    private var ttsReady = false

    private var busy = false

    private var listening = false

    private var awakeUntil = 0L

    private var destroyed = false

    // =========================================================
    // WAKE WORD
    // =========================================================

    private val wakeWord =
        Regex(
            "\\b(jarvis|jarvish|jervis|jarvi)\\b",
            RegexOption.IGNORE_CASE
        )

    // =========================================================
    // SERVICE BIND
    // =========================================================

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    // =========================================================
    // SERVICE CREATE
    // =========================================================

    override fun onCreate() {

        super.onCreate()

        // Check microphone permission.
        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            stopSelf()

            return
        }

        startForegroundServiceNotification()

        createWakeLock()

        createTts()
    }

    // =========================================================
    // SERVICE START
    // =========================================================

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        scheduleListen(2500L)

        return START_STICKY
    }

    // =========================================================
    // FOREGROUND NOTIFICATION
    // =========================================================

    private fun startForegroundServiceNotification() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        // Android 8+
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    "jar3",
                    "JAR 3.0",
                    NotificationManager.IMPORTANCE_LOW
                )

            manager.createNotificationChannel(
                channel
            )
        }

        val notification: Notification

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            notification =
                Notification.Builder(
                    this,
                    "jar3"
                )
                    .setContentTitle(
                        "JAR 3.0 is active"
                    )
                    .setContentText(
                        "Voice assistant is ready"
                    )
                    .setSmallIcon(
                        android.R.drawable.ic_btn_speak_now
                    )
                    .setOngoing(true)
                    .build()

        } else {

            notification =
                Notification.Builder(
                    this
                )
                    .setContentTitle(
                        "JAR 3.0 is active"
                    )
                    .setContentText(
                        "Voice assistant is ready"
                    )
                    .setSmallIcon(
                        android.R.drawable.ic_btn_speak_now
                    )
                    .setOngoing(true)
                    .build()
        }

        // Android 10+
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {

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
                getSystemService(
                    POWER_SERVICE
                ) as PowerManager

            wakeLock =
                power.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "JAR3:VoiceAssistant"
                )

            wakeLock?.acquire()

        } catch (_: Exception) {
            // Ignore wake lock failure.
        }
    }

    // =========================================================
    // TEXT TO SPEECH SETUP
    // =========================================================

    private fun createTts() {

        tts =
            TextToSpeech(
                this
            ) { status ->

                handler.post {

                    if (
                        status !=
                        TextToSpeech.SUCCESS
                    ) {

                        return@post
                    }

                    // Try Indian English first.
                    val languageResult =
                        tts?.setLanguage(
                            Locale(
                                "en",
                                "IN"
                            )
                        )

                    // Fallback to US English.
                    if (
                        languageResult ==
                        TextToSpeech.LANG_MISSING_DATA ||
                        languageResult ==
                        TextToSpeech.LANG_NOT_SUPPORTED
                    ) {

                        tts?.setLanguage(
                            Locale.US
                        )
                    }

                    // JARVIS voice settings.
                    tts?.setPitch(
                        0.75f
                    )

                    tts?.setSpeechRate(
                        1.05f
                    )

                    // Know when speech finishes.
                    tts?.setOnUtteranceProgressListener(
                        object :
                            UtteranceProgressListener() {

                            override fun onStart(
                                utteranceId: String?
                            ) {
                                // Speaking started.
                            }

                            override fun onDone(
                                utteranceId: String?
                            ) {

                                handler.post {

                                    if (
                                        !busy &&
                                        !destroyed
                                    ) {

                                        scheduleListen(
                                            700L
                                        )
                                    }
                                }
                            }

                            override fun onError(
                                utteranceId: String?
                            ) {

                                handler.post {

                                    if (
                                        !busy &&
                                        !destroyed
                                    ) {

                                        scheduleListen(
                                            1000L
                                        )
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

    // =========================================================
    // SPEAK
    // =========================================================

    private fun speak(
        text: String
    ) {

        if (
            !ttsReady ||
            text.isBlank() ||
            destroyed
        ) {

            return
        }

        handler.post {

            // Stop recognition before JAR speaks.
            if (listening) {

                try {

                    recognizer?.stopListening()

                } catch (_: Exception) {
                }

                listening = false
            }

            val utteranceId =
                "jar_" +
                    System.nanoTime()

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                utteranceId
            )
        }
    }

    // =========================================================
    // LISTEN SCHEDULER
    // =========================================================

    private fun scheduleListen(
        delay: Long
    ) {

        if (destroyed) {
            return
        }

        // Prevent multiple pending listen jobs.
        handler.removeCallbacks(
            listenRunnable
        )

        handler.postDelayed(
            listenRunnable,
            delay.coerceAtLeast(
                300L
            )
        )
    }

    private val listenRunnable =
        Runnable {

            listen()
        }

    // =========================================================
    // START LISTENING
    // =========================================================

    private fun listen() {

        if (
            destroyed ||
            busy ||
            !ttsReady ||
            listening
        ) {

            return
        }

        // Check microphone permission.
        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            stopSelf()

            return
        }

        // Check whether Android has speech recognition.
        if (
            !SpeechRecognizer
                .isRecognitionAvailable(
                    this
                )
        ) {

            scheduleListen(
                5000L
            )

            return
        }

        // Create recognizer only once.
        if (
            recognizer == null
        ) {

            recognizer =
                SpeechRecognizer
                    .createSpeechRecognizer(
                        this
                    )

            recognizer?.setRecognitionListener(
                recognitionListener
            )
        }

        try {

            val intent =
                Intent(
                    RecognizerIntent
                        .ACTION_RECOGNIZE_SPEECH
                )

            intent.putExtra(
                RecognizerIntent
                    .EXTRA_LANGUAGE_MODEL,
                RecognizerIntent
                    .LANGUAGE_MODEL_FREE_FORM
            )

            intent.putExtra(
                RecognizerIntent
                    .EXTRA_LANGUAGE,
                "en-IN"
            )

            intent.putExtra(
                RecognizerIntent
                    .EXTRA_MAX_RESULTS,
                3
            )

            intent.putExtra(
                RecognizerIntent
                    .EXTRA_PARTIAL_RESULTS,
                false
            )

            listening = true

            recognizer?.startListening(
                intent
            )

        } catch (_: Exception) {

            listening = false

            scheduleListen(
                2000L
            )
        }
    }

    // =========================================================
    // SPEECH RECOGNITION LISTENER
    // =========================================================

    private val recognitionListener =
        object :
            RecognitionListener {

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
                // Audio level.
            }

            override fun onBufferReceived(
                buffer: ByteArray?
            ) {
                // Raw audio buffer.
            }

            override fun onEndOfSpeech() {
                // Speech ended.
            }

            override fun onPartialResults(
                partialResults: Bundle?
            ) {
                // Partial results disabled.
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?
            ) {
                // No custom events.
            }

            override fun onResults(
                results: Bundle?
            ) {

                listening = false

                val recognizedText =
                    results
                        ?.getStringArrayList(
                            SpeechRecognizer
                                .RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?.trim()
                        ?: ""

                if (
                    recognizedText.isNotEmpty()
                ) {

                    handleSpeech(
                        recognizedText
                    )

                } else {

                    scheduleListen(
                        800L
                    )
                }
            }

            override fun onError(
                error: Int
            ) {

                listening = false

                if (destroyed) {
                    return
                }

                // Permission problem.
                if (
                    error ==
                    SpeechRecognizer
                        .ERROR_INSUFFICIENT_PERMISSIONS
                ) {

                    stopSelf()

                    return
                }

                val delay: Long =
                    when (error) {

                        SpeechRecognizer
                            .ERROR_NO_MATCH -> {

                            1000L
                        }

                        SpeechRecognizer
                            .ERROR_SPEECH_TIMEOUT -> {

                            1200L
                        }

                        SpeechRecognizer
                            .ERROR_CLIENT -> {

                            2000L
                        }

                        SpeechRecognizer
                            .ERROR_RECOGNIZER_BUSY -> {

                            2500L
                        }

                        else -> {

                            1800L
                        }
                    }

                scheduleListen(
                    delay
                )
            }
        }

    // =========================================================
    // HANDLE SPEECH
    // =========================================================

    private fun handleSpeech(
        text: String
    ) {

        val now =
            System.currentTimeMillis()

        // -----------------------------------------
        // Wake word detected
        // -----------------------------------------

        if (
            wakeWord.containsMatchIn(
                text
            )
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

            // Just "Jarvis".
            if (
                command.length < 2
            ) {

                awakeUntil =
                    now + 10000L

                speak(
                    "Yes boss?"
                )

            } else {

                // "Jarvis open YouTube"
                processCommand(
                    command
                )
            }

            return
        }

        // -----------------------------------------
        // Temporary awake mode
        // -----------------------------------------

        if (
            now < awakeUntil &&
            text.isNotBlank()
        ) {

            processCommand(
                text
            )

        } else {

            // Ignore random speech.
            scheduleListen(
                500L
            )
        }
    }

    // =========================================================
    // BRAIN + ACTIONS
    // =========================================================

    private fun processCommand(
        command: String
    ) {

        if (
            busy ||
            destroyed
        ) {

            return
        }

        busy = true

        executor.execute {

            try {

                var input =
                    command

                repeat(6) {

                    // -----------------------------------------
                    // Ask Brain
                    // -----------------------------------------

                    val response =
                        Brain.ask(
                            input,
                            phoneInfo()
                        )

                    // -----------------------------------------
                    // Speak response
                    // -----------------------------------------

                    val speech =
                        response.optString(
                            "say"
                        )

                    if (
                        speech.isNotBlank()
                    ) {

                        speak(
                            speech
                        )
                    }

                    // -----------------------------------------
                    // Execute actions
                    // -----------------------------------------

                    val failures =
                        StringBuilder()

                    val actions =
                        response.optJSONArray(
                            "actions"
                        )

                    if (
                        actions != null
                    ) {

                        for
