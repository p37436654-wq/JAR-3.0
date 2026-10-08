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

    private val executor =
        Executors.newSingleThreadExecutor()

    private var tts: TextToSpeech? = null

    private var recognizer: SpeechRecognizer? = null

    private var wakeLock: PowerManager.WakeLock? = null

    private var ttsReady = false

    private var busy = false

    private var listening = false

    private var awakeUntil = 0L

    private var destroyed = false

    private val wakeWord =
        Regex(
            "\\b(jarvis|jarvish|jervis|jarvi)\\b",
            RegexOption.IGNORE_CASE
        )

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        if (
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        startForegroundNotification()

        createWakeLock()

        createTextToSpeech()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        scheduleListening(2500L)

        return START_STICKY
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private fun startForegroundNotification() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

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

            val powerManager =
                getSystemService(
                    POWER_SERVICE
                ) as PowerManager

            wakeLock =
                powerManager.newWakeLock(
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

    private fun createTextToSpeech() {

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

                    val languageResult =
                        tts?.setLanguage(
                            Locale(
                                "en",
                                "IN"
                            )
                        )

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

                    tts?.setPitch(
                        0.75f
                    )

                    tts?.setSpeechRate(
                        1.05f
                    )

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

                                    if (
                                        !busy &&
                                        !destroyed
                                    ) {

                                        scheduleListening(
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

                                        scheduleListening(
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

            if (listening) {

                try {
                    recognizer?.stopListening()
                } catch (_: Exception) {
                }

                listening = false
            }

            val id =
                "jar_" +
                    System.nanoTime()

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                id
            )
        }
    }

    // =========================================================
    // LISTENING SCHEDULER
    // =========================================================

    private val listenRunnable =
        Runnable {
            startListening()
        }

    private fun scheduleListening(
        delay: Long
    ) {

        if (destroyed) {
            return
        }

        handler.removeCallbacks(
            listenRunnable
        )

        handler.postDelayed(
            listenRunnable,
            delay
        )
    }

    // =========================================================
    // START SPEECH RECOGNITION
    // =========================================================

    private fun startListening() {

        if (destroyed) {
            return
        }

        if (busy) {
            return
        }

        if (!ttsReady) {
            return
        }

        if (listening) {
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
            !SpeechRecognizer.isRecognitionAvailable(
                this
            )
        ) {

            scheduleListening(
                5000L
            )

            return
        }

        if (recognizer == null) {

            recognizer =
                SpeechRecognizer
                    .createSpeechRecognizer(
                        this
                    )

            recognizer?.setRecognitionListener(
                speechListener
            )
        }

        try {

            val speechIntent =
                Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                )

            speechIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            speechIntent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-IN"
            )

            speechIntent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )

            speechIntent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )

            listening = true

            recognizer?.startListening(
                speechIntent
            )

        } catch (_: Exception) {

            listening = false

            scheduleListening(
                2000L
            )
        }
    }

    // =========================================================
    // SPEECH LISTENER
    // =========================================================

    private val speechListener =
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

                if (text.isEmpty()) {

                    scheduleListening(
                        800L
                    )

                    return
                }

                handleSpeech(
                    text
                )
            }

            override fun onError(
                error: Int
            ) {

                listening = false

                if (destroyed) {
                    return
                }

                if (
                    error ==
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                ) {

                    stopSelf()

                    return
                }

                var delay = 1800L

                if (
                    error ==
                    SpeechRecognizer.ERROR_NO_MATCH
                ) {

                    delay = 1000L
                }

                if (
                    error ==
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                ) {

                    delay = 1200L
                }

                if (
                    error ==
                    SpeechRecognizer.ERROR_CLIENT
                ) {

                    delay = 2000L
                }

                if (
                    error ==
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                ) {

                    delay = 2500L
                }

                scheduleListening(
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

        val hasWakeWord =
            wakeWord.containsMatchIn(
                text
            )

        if (hasWakeWord) {

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

            if (
                command.length < 2
            ) {

                awakeUntil =
                    now + 10000L

                speak(
                    "Yes boss?"
                )

                return
            }

            processCommand(
                command
            )

            return
        }

        if (
            now < awakeUntil &&
            text.isNotBlank()
        ) {

            processCommand(
                text
            )

            return
        }

        scheduleListening(
            500L
        )
    }

    // =========================================================
    // PROCESS COMMAND
    // =========================================================

    private fun processCommand(
        command: String
    ) {

        if (busy) {
            return
        }

        if (destroyed) {
            return
        }

        busy = true

        executor.execute {

            try {

                var input =
                    command

                var attempt = 0

                while (
                    attempt < 6
                ) {

                    attempt++

                    // -----------------------------------------
                    // ASK BRAIN
                    // -----------------------------------------

                    val response =
                        Brain.ask(
                            input,
                            phoneInfo()
                        )

                    // -----------------------------------------
                    // SPEAK RESPONSE
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
                    // ACTIONS
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

                        var index = 0

                        while (
                            index < actions.length()
                        ) {

                            try {

                                val action =
                                    actions.getJSONObject(
                                        index
                                    )

                                val result =
                                    Actions.run(
                                        this,
                                        action
                                    )

                                if (
                                    result != "ok"
                                ) {

                                    failures.append(
                                        result
                                    )

                                    failures.append(
                                        "; "
                                    )
                                }

                            } catch (
                                actionError: Exception
                            ) {

                                failures.append(
                                    actionError.message
                                        ?: "Action failed"
                                )

                                failures.append(
                                    "; "
                                )
                            }

                            index++
                        }
                    }

                    // -----------------------------------------
                    // CHECK IF MORE INFORMATION IS NEEDED
                    // -----------------------------------------

                    val more =
                        response.optBoolean(
                            "more",
                            false
                        )

                    if (!more) {
                        break
                    }

                    Thread.sleep(
                        1000L
                    )

                    // -----------------------------------------
                    // READ SCREEN
                    // -----------------------------------------

                    val screenText =
                        try {

                            ControlService
                                .inst
                              
