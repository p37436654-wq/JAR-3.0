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

        createForegroundNotification()
        createWakeLock()
        createTextToSpeech()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        handler.postDelayed(
            {

                if (
                    ttsReady &&
                    !busy
                ) {
                    listen()
                }

            },
            2000
        )

        return START_STICKY
    }

    private fun createForegroundNotification() {

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

            channel.description =
                "JAR 3.0 voice assistant"

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

            wakeLock = null
        }
    }

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

                        ttsReady = false

                        scheduleListening(
                            2000
                        )

                        return@post
                    }

                    var result =
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

                        result =
                            tts?.setLanguage(
                                Locale.US
                            )
                    }

                    if (
                        result ==
                        TextToSpeech.LANG_MISSING_DATA ||
                        result ==
                        TextToSpeech.LANG_NOT_SUPPORTED
                    ) {

                        ttsReady = false

                        scheduleListening(
                            2000
                        )

                        return@post
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
                                    speechFinished()
                                }
                            }

                            override fun onError(
                                utteranceId: String?
                            ) {

                                handler.post {
                                    speechFinished()
                                }
                            }
                        }
                    )

                    ttsReady = true

                    say(
                        "JAR 3.0 online. Ready, boss."
                    )
                }
            }
    }

    private fun speechFinished() {

        if (
            pendingSpeech.decrementAndGet() <= 0
        ) {

            pendingSpeech.set(0)

            if (!busy) {

                scheduleListening(
                    500
                )
            }
        }
    }

    private fun say(
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        handler.post {

            if (!ttsReady) {
                return@post
            }

            pendingSpeech.incrementAndGet()

            try {
                recognizer?.cancel()
            } catch (_: Exception) {
            }

            val utteranceId =
                "jar3_" +
                    System.nanoTime()

            val result =
                tts?.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    utteranceId
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

        if (busy) return

        if (
            pendingSpeech.get() > 0
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
                .isRecognitionAvailable(
                    this
                )
        ) {

            scheduleListening(
                3000
            )

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

            recognizer?.setRecognitionListener(
                recognitionListener
            )
        }

        try {

            recognizer?.cancel()

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
                            .EXTRA_LANGUAGE_PREFERENCE,
                        "en-IN"
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_PARTIAL_RESULTS,
                        false
                    )

                    putExtra(
                        RecognizerIntent
                            .EXTRA_MAX_RESULTS,
                        3
                    )
                }

            recognizer?.startListening(
                intent
            )

        } catch (_: Exception) {

            scheduleListening(
                1500
            )
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
                        ?.trim()
                        ?: ""

                if (
                    text.isNotBlank()
                ) {

                    handleSpeech(
                        text
                    )

                } else {

                    scheduleListening(
                        300
                    )
                }
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

                when (error) {

                    SpeechRecognizer
                        .ERROR_RECOGNIZER_BUSY -> {

                        try {
                            recognizer?.cancel()
                        } catch (_: Exception) {
                        }

                        scheduleListening(
                            1200
                        )
                    }

                    SpeechRecognizer
                        .ERROR_CLIENT,

                    SpeechRecognizer
                        .ERROR_NETWORK,

                    SpeechRecognizer
                        .ERROR_NETWORK_TIMEOUT,

                    SpeechRecognizer
                        .ERROR_NO_MATCH,

                    SpeechRecognizer
                        .ERROR_SPEECH_TIMEOUT -> {

                        scheduleListening(
                            500
                        )
                    }

                    else -> {

                        scheduleListening(
                            1000
                        )
                    }
                }
            }
        }

    private fun handleSpeech(
        text: String
    ) {

        val now =
            System.currentTimeMillis()

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
            200
        )
    }

    private fun processCommand(
        command: String
    ) {

        if (busy) {
            return
        }

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
                        response.optString(
                            "say"
                        )

                    if (
                        speech.isNotBlank()
                    ) {

                        say(
                            speech
                        )
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
                            0 until actions.length()
                        ) {

                            try {

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
                                        .append(
                                            "; "
                                        )
                                }

                            } catch (
                                e: Exception
                            ) {

                                failures
                                    .append(
                                        e.message
                                            ?: "Action failed"
                                    )
                                    .append(
                                        "; "
                                    )
                            }

                            Thread.sleep(
                                800
                            )
                        }
                    }

                    if (
                        !response.optBoolean(
                            "more",
                            false
                        )
                    ) {

                        break
                    }

                    Thread.sleep(
                        1200
                    )

                    val screen =
                        try {

                            ControlService
                                .inst
                                ?.screenText()
                                ?: "(phone control is OFF)"

                        } catch (_: Exception) {

                            "(screen unavailable)"
                        }

                    input =
                        "RESULT: " +
                            failures
                                .toString() +
                            "\nSCREEN: " +
                            screen
                }

            } catch (
                e: Exception
            ) {

                // THIS IS THE IMPORTANT CHANGE
                val error =
                    e.message
                        ?: "Unknown error"

                say(
                    "Error. " +
                        error.take(180)
                )

            } finally
