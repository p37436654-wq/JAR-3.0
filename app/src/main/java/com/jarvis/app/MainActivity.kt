package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient

class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this).apply {

            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false

            webViewClient = WebViewClient()
            webChromeClient = WebChromeClient()

            addJavascriptInterface(
                JarvisBridge(),
                "Android"
            )

            loadUrl("file:///android_asset/hud.html")
        }

        setContentView(web)

        if (
            android.os.Build.VERSION.SDK_INT >= 23 &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        }
    }

    inner class JarvisBridge {

        @JavascriptInterface
        fun openAccessibility() {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            )
        }

        @JavascriptInterface
        fun startListening() {
            JarvisVoice.start(
                this@MainActivity,
                web
            )
        }

        @JavascriptInterface
        fun stopListening() {
            JarvisVoice.stop()
        }

        @JavascriptInterface
        fun execute(command: String) {
            Actions.execute(
                this@MainActivity,
                command
            )
        }

        @JavascriptInterface
        fun askAI(prompt: String) {

            Brain.ask(
                this@MainActivity,
                prompt
            ) { answer ->

                runOnUiThread {

                    web.evaluateJavascript(
                        "window.jarvisAnswer(${org.json.JSONObject.quote(answer)});",
                        null
                    )
                }
            }
        }

        @JavascriptInterface
        fun saveKey(key: String) {

            getSharedPreferences(
                "jarvis",
                MODE_PRIVATE
            )
                .edit()
                .putString("groq_key", key)
                .apply()
        }
    }

    override fun onDestroy() {

        JarvisVoice.stop()

        if (::web.isInitialized) {
            web.destroy()
        }

        super.onDestroy()
    }
}
