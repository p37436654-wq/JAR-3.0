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
import java.util.concurrent.atomic.AtomicInteger

class JarvisService : Service() {

    private val handler =
        Handler(Looper.getMainLooper())

    private val worker =
        Executors.newSingleThreadExecutor()

    private val pendingSpeech =
        AtomicInteger(0)

    private var tts: TextToSpeech? = null

    private var ttsReady = false

    private var recognizer: SpeechRecognizer? = null

    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile
    private var busy = false

    private var awakeUntil = 0L

    private val wakeWord =
        Regex(
            "\\b(jarvis|jarvish|jervis|jarvi)\\b",
            RegexOption.IGNORE_CASE
        )

    private val listenAgain =
        Runnable {
            listen()
        }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_NOT_STICKY
    }

    override fun onCreate() {

        super.onCreate()

        /*
         * Microphone permission must already be granted
         * before starting the microphone foreground service.
         */
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        createForegroundNotification()

        createWakeLock()

        createTextToSpeech()
    }

    private fun createForegroundNotification() {

        val notificationManager =
            getSystemService(
                NotificationManager::class.java
            )

        if (Build.VERSION.SDK_INT >= 26) {

            val channel =
                NotificationChannel(
                    "jar3",
                    "JAR 3.0",
                    NotificationManager.IMPORTANCE_LOW
                )

            channel.description =
                "JAR 3.0 voice assistant"

            notificationManager
                .createNotificationChannel(channel)
        }

        val notification =
            if (Build.VERSION.SDK_INT >= 26) {

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

        if (
            Build.VERSION.SDK_INT >= 29
        ) {

            startForeground(
                1,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_MICROPHONE
            )

        } else {

            startForeground(
                1,
                notification
            )
        }
    }

    private fun createWakeLock() {

        val powerManager =
            getSystemService(
                PowerManager::class.java
            )

        wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "JAR3:VoiceAssistant"
            )

        wakeLock?.acquire()
    }

    private fun createTextToSpeech() {

        tts =
            TextToSpeech(
                this
            ) { status ->

                if (
                    status ==
                    TextToSpeech.SUCCESS
                ) {

                    tts?.setLanguage(
                        Locale("en", "IN")
                    )

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
                                id: String?
                            ) {
                            }

                            override fun onDone(
                                id: String?
                            ) {
                                speechFinished()
                            }

                            override fun onError(
                                id: String?
                            ) {
                                speechFinished()
                            }
                        }
                    )

                    ttsReady = true

                    say(
                        "JAR 3.0 online. Ready, boss."
                    )

                } else {

                    scheduleListening(500)
                }
            }
    }

    private fun speechFinished() {

        if (
            pendingSpeech
                .decrementAndGet() <= 0
        ) {

            pendingSpeech.set(0)

            scheduleListening(300)
        }
    }

    private fun say(
        text: String
    ) {

        if (
            text.isBlank()
        ) {
            return
        }

        handler.post {

            if (!ttsReady) {
                return@post
            }

            pendingSpeech.incrementAndGet()

            recognizer?.cancel()

            val result =
                tts?.speak(
                    text,
                    TextToSpeech.QUEUE_ADD,
                    null,
                    "jar3_" +
                        System.nanoTime()
                )

            if (
                result !=
                TextToSpeech.SUCCESS
            ) {

                speechFinished()
            }
        }
    }

    private fun scheduleListening(
        delay: Long
    ) {

        handler.removeCallbacks(
            listenAgain
        )

        handler.postDelayed(
            listenAgain,
            delay
        )
    }

    private fun listen() {

        if (
            busy ||
            pendingSpeech.get() > 0
        ) {
            return
        }

        if (
            recognizer == null
        ) {

            recognizer =
                SpeechRecognizer
                    .createSpeechRecognizer(
                        this
                    )
                    .also {

                        it.setRecognitionListener(
                            recognitionListener
                        )
                    }
        }

        try {

            val intent =
                Intent(
                    RecognizerIntent
                        .ACTION_RECOGNIZE_SPEECH
                ).apply {

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent
                            .LANGUAGE_MODEL_FREE_FORM
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_LANGUAGE,
                        "en-IN"
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_CALLING_PACKAGE,
                        packageName
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_PARTIAL_RESULTS,
                        false
                    )
                }

            recognizer?.startListening(
                intent
            )

        } catch (
            e: Exception
        ) {

            scheduleListening(1500)
        }
    }

    private val recognitionListener =
        object : RecognitionListener {

            override fun onReadyForSpeech(
                params: Bundle?
            ) {
            }

            override fun onBeginningOfSpeech() {
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

                val text =
                    results
                        ?.getStringArrayList(
                            SpeechRecognizer
                                .RESULTS_RECOGNITION
                        )
                        ?.firstOrNull()
                        ?: ""

                handleSpeech(text)
            }

            override fun onError(
                error: Int
            ) {

                if (
                    error ==
                    SpeechRecognizer
                        .ERROR_INSUFFICIENT_PERMISSIONS
                ) {

                    stopSelf()

                    return
                }

                if (
                    error ==
                    SpeechRecognizer
                        .ERROR_RECOGNIZER_BUSY
                ) {

                    recognizer?.cancel()

                    scheduleListening(
                        1200
                    )

                } else {

                    scheduleListening(
                        300
                    )
                }
            }
        }

    private fun handleSpeech(
        text: String
    ) {

        val now =
            System.currentTimeMillis()

        when {

            wakeWord.containsMatchIn(
                text
            ) -> {

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
                        now + 10000

                    say(
                        "Yes boss?"
                    )

                } else {

                    processCommand(
                        command
                    )
                }
            }

            now < awakeUntil &&
            text.isNotBlank() -> {

                processCommand(text)
            }

            else -> {

                scheduleListening(100)
            }
        }
    }

    private fun processCommand(
        command: String
    ) {

        busy = true

        worker.execute {

            var input =
                command

            try {

                for (
                    step in 0 until 6
                ) {

                    val response =
                        Brain.ask(
                            input,
                            phoneInfo()
                        )

                    val speech =
                        response
                            .optString(
                                "say"
                            )

                    if (
                        speech.isNotBlank()
                    ) {

                        say(speech)
                    }

                    val failures =
                        StringBuilder()

                    val actions =
                        response
                            .optJSONArray(
                                "actions"
                            )

                    if (
                        actions != null
                    ) {

                        for (
                            index in
                            0 until
                            actions.length()
                        ) {

                            val action =
                                actions
                                    .getJSONObject(
                                        index
                                    )

                            val result =
                                Actions.run(
                                    this,
                                    action
                                )

                            if (
                                result !=
                                "ok"
                            ) {

                                failures
                                    .append(
                                        result
                                    )
                                    .append("; ")
                            }

                            Thread.sleep(
                                800
                            )
                        }
                    }

                    if (
                        !response
                            .optBoolean(
                                "more"
                            )
                    ) {
                        break
                    }

                    Thread.sleep(
                        1200
                    )

                    input =
                        "RESULT: " +
                            failures +
                            "\nSCREEN: " +
                            (
                                ControlService
                                    .inst
                                    ?.screenText()
                                    ?: "(phone control is OFF)"
                            )
                }

            } catch (
                e: Exception
            ) {

                say(
                    "Sorry boss. AI connection failed."
                )
            }

            handler.post {

                busy = false

                awakeUntil =
                    System.currentTimeMillis() +
                        8000

                scheduleListening(
                    300
                )
            }
        }
    }

    private fun phoneInfo(): String {

        val battery =
            try {

                getSystemService(
                    BatteryManager::class.java
                ).getIntProperty(
                    BatteryManager
                        .BATTERY_PROPERTY_CAPACITY
                )

            } catch (
                e: Exception
            ) {
                -1
            }

        val time =
            SimpleDateFormat(
                "EEE d MMM yyyy, hh:mm a",
                Locale.getDefault()
            ).format(
                Date()
            )

        val control =
            if (
                ControlService.inst != null
            ) {
                "ON"
            } else {
                "OFF"
            }

        return """
            Now: $time.
            Battery: $battery%.
            Phone control: $control.
        """.trimIndent()
    }

    override fun onDestroy() {

        handler.removeCallbacksAndMessages(
            null
        )

        recognizer?.cancel()

        recognizer?.destroy()

        recognizer = null

        tts?.stop()

        tts?.shutdown()

        tts = null

        ttsReady = false

        worker.shutdownNow()

        wakeLock?.let {

            if (it.isHeld) {
                it.release()
            }
        }

        wakeLock = null

        super.onDestroy()
    }
}
