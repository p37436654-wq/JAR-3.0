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
    private var destroyed = false

    private val wakeWord =
        Regex(
            "\\b(jarvis|jarvish|jervis|jarvi)\\b",
            RegexOption.IGNORE_CASE
        )

    override fun onBind(intent: Intent?): IBinder? {
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

        startForegroundServiceNotification()
        createWakeLock()
        createTts()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        scheduleListen(2500)

        return START_STICKY
    }

    // =========================================================
    // FOREGROUND SERVICE
    // =========================================================

    private fun startForegroundServiceNotification() {

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val channel =
                NotificationChannel(
                    "jar3",
                    "JAR 3.0",
                    NotificationManager.IMPORTANCE_LOW
                )

            manager.createNotificationChannel(channel)
        }

        val notification =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

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

                Notification.Builder(this)
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
                        status != TextToSpeech.SUCCESS
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
                        object : UtteranceProgressListener() {

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
                                        scheduleListen(700)
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
                                        scheduleListen(1000)
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

            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jar_" + System.nanoTime()
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

        handler.removeCallbacks(
            listenRunnable
        )

        handler.postDelayed(
            listenRunnable,
            delay.coerceAtLeast(300L)
        )
    }

    private val listenRunnable =
        Runnable {
            listen()
        }

    // =========================================================
    // LISTEN
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

            scheduleListen(5000)
            return
        }

        if (recognizer == null) {

            recognizer =
                SpeechRecognizer.createSpeechRecognizer(
                    this
                )

            recognizer?.setRecognitionListener(
                listener
            )
        }

        try {

            val intent =
                Intent(
                    RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                )

            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                "en-IN"
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS,
                3
            )

            intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                false
            )

            listening = true

            recognizer?.startListening(
                intent
            )

        } catch (_: Exception) {

            listening = false

            scheduleListen(2000)
        }
    }

    // =========================================================
    // SPEECH RECOGNITION
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

                    scheduleListen(800)
                }
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

                val delay: Long

                when (error) {

                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        delay = 1000L
                    }

                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        delay = 1200L
                    }

                    SpeechRecognizer.ERROR_CLIENT -> {
                        delay = 2000L
                    }

                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        delay = 2500L
                    }

                    else -> {
                        delay = 1800L
                    }
                }

                scheduleListen(delay)
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

                awakeUntil =
                    now + 10000L

                speak(
                    "Yes boss?"
                )

            } else {

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

            processCommand(
                text
            )

        } else {

            scheduleListen(500)
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

                        speak(
                            speech
                        )
                    }

                    val failures =
                        StringBuilder()

                    val actions =
                        response.optJSONArray(
                            "actions"
                        )

                    if (
                        actions != null
                    ) {

                        for (
                            i in 0 until actions.length()
                        ) {

                            try {

                                val action =
                                    actions.getJSONObject(
                                        i
                                    )

                                val result =
                                    Actions.run(
                                        this,
                                        action
                                    )

                                if (
                                    result != "ok"
                                ) {

                                    failures
                                        .append(
                                            result
                                        )
                                        .append(
                                            "; "
                                        )
                                }

                            } catch (e: Exception) {

                                failures
                                    .append(
                                        e.message
                                            ?: "Action failed"
                                    )
                                    .append(
                                        "; "
                                    )
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

                    Thread.sleep(
                        1000L
                    )

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

                handler.post {

                    if (!destroyed) {

                        scheduleListen(
                            1500L
                        )
                    }
                }
            }
        }
    }

    // =========================================================
    // PHONE INFORMATION
    // =========================================================

    private fun phoneInfo(): String {

        val time: String

        try {

            time =
                SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(
                    Date()
                )

        } catch (_: Exception) {

            time = "unknown"
        }

        val battery: Int

        try {

            val manager =
                getSystemService(
                    BATTERY_SERVICE
                ) as BatteryManager

            battery =
                manager.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                )

        } catch (_: Exception) {

            battery = -1
        }

        val control: String

        if (
            ControlService.inst != null
        ) {

            control = "ON"

        } else {

            control = "OFF"
        }

        val batteryText: String

        if (battery >= 0) {

            batteryText =
                battery.toString() + "%"

        } else {

            batteryText = "unknown"
        }

        val result =
            StringBuilder()

        result.append("Time: ")
        result.append(time)
        result.append("\n")

        result.append("Battery: ")
        result.append(batteryT
