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

    private var listening = false

    private var busy = false

    private var destroyed = false

    private var awakeUntil = 0L

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

        createNotification()

        createWakeLock()

        createTts()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        scheduleListening(2500L)

        return START_STICKY
    }

    private fun createNotification() {

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

        val notification =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

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

    private fun createTts() {

        tts =
            TextToSpeech(
                this
            ) { status ->

                if (
                    status !=
                    TextToSpeech.SUCCESS
                ) {
                    return@TextToSpeech
                }

                handler.post {

                    val result =
                        tts?.setLanguage(
                            Locale(
                                "en",
                                "IN"
                            )
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
            destroyed ||
            text.isBlank()
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

            scheduleListening(5000L)

            return
        }

        if (recognizer == null) {

            recognizer =
                SpeechRecognizer.createSpeechRecognizer(
                    this
                )

            recognizer?.setRecognitionListener(
                speechListener
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

            scheduleListening(2000L)
        }
    }

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

                    scheduleListening(800L)

                } else {

                    handleSpeech(text)
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

                val delay =
                    when (error) {

                        SpeechRecognizer.ERROR_NO_MATCH ->
                            1000L

                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            1200L

                        SpeechRecognizer.ERROR_CLIENT ->
                            2000L

                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            2500L

                        else ->
                            1800L
                    }

                scheduleListening(delay)
            }
        }

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

            if (
                command.length < 2
            ) {

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

            processCommand(text)

        } else {

            scheduleListening(500L)
        }
    }

    private fun processCommand(
        command: String
    ) {

        if (busy || destroyed) {
            return
        }

        busy = true

        executor.execute {

            try {

                val phoneInformation =
                    buildPhoneInformation()

                val response =
                    Brain.ask(
                        command,
                        phoneInformation
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

                            Actions.run(
                                this,
                                action
                            )

                        } catch (_: Exception) {
                        }

                        index++
                    }
                }

            } catch (error: Exception) {

                val message =
                    error.message
                        ?: "Unknown error"

                speak(
                    "Error. " +
                        message.take(180)
                )

            } finally {

                busy = false

                handler.post {

                    if (!destroyed) {

                        scheduleListening(
                            1500L
                        )
                    }
                }
            }
        }
    }

    private fun buildPhoneInformation(): String {

        val time =
            try {

                SimpleDateFormat(
                    "hh:mm a",
                    Locale.getDefault()
                ).format(Date())

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

        val batteryText =
            if (battery >= 0) {
                battery.toString() + "%"
            } else {
                "unknown"
            }

        val control =
            if (ControlService.inst != null) {
                "ON"
            } else {
                "OFF"
            }

        return (
            "Time: " + time +
            "\nBattery: " + batteryText +
            "\nPhone control: " + control +
            "\nAndroid: " + Build.VERSION.RELEASE +
            "\nDevice: " + Build.MODEL
        )
    }

    override fun onDestroy() {

        destroyed = true

        handler.removeCallbacks(
            listenRunnable
        )

        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }

        listening = false

        try {
            recognizer?.cancel()
        } catch (_: Exception) {
        }

        try {
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null

        try {
            tts?.stop()
        } catch (_: Exception) {
        }

        try {
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
