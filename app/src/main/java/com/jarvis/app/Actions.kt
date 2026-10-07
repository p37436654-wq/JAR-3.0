package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject

object Actions {

    fun run(
        context: Context,
        action: JSONObject
    ): String {

        return try {

            when (action.optString("type")) {

                "open_app" -> {
                    openApp(
                        context,
                        action.optString("name")
                    )
                }

                "url" -> {
                    openUrl(
                        context,
                        action.optString("url")
                    )
                }

                "search" -> {
                    search(
                        context,
                        action.optString("query")
                    )
                }

                "navigate" -> {
                    navigate(
                        context,
                        action.optString("place")
                    )
                }

                "settings" -> {
                    openSettings(
                        context,
                        action.optString("page")
                    )
                }

                "click" -> {

                    val text =
                        action.optString("text")

                    val service =
                        ControlService.inst

                    if (service == null) {
                        "control_off"
                    } else if (
                        service.findAndClick(text)
                    ) {
                        "ok"
                    } else {
                        "not_found:$text"
                    }
                }

                "system" -> {
                    systemAction(
                        action.optString("what")
                    )
                }

                "wait" -> {

                    val ms =
                        action.optLong(
                            "ms",
                            500L
                        )

                    Thread.sleep(
                        ms.coerceIn(
                            0L,
                            10000L
                        )
                    )

                    "ok"
                }

                else -> {
                    "unknown_action"
                }
            }

        } catch (e: Exception) {

            "error:${e.message ?: "failed"}"
        }
    }

    private fun openApp(
        context: Context,
        name: String
    ): String {

        val packageName =
            when (
                name.lowercase().trim()
            ) {

                "chrome",
                "google chrome" ->
                    "com.android.chrome"

                "youtube" ->
                    "com.google.android.youtube"

                "whatsapp" ->
                    "com.whatsapp"

                "instagram" ->
                    "com.instagram.android"

                "maps",
                "google maps" ->
                    "com.google.android.apps.maps"

                "settings" ->
                    "com.android.settings"

                "calculator" ->
                    "com.android.calculator"

                "clock" ->
                    "com.android.deskclock"

                "camera" ->
                    "com.android.camera"

                else ->
                    return "unknown_app:$name"
            }

        val launchIntent =
            context.packageManager
                .getLaunchIntentForPackage(
                    packageName
                )
                ?: return "app_not_installed:$name"

        launchIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK
        )

        context.startActivity(
            launchIntent
        )

        return "ok"
    }

    private fun openUrl(
        context: Context,
        url: String
    ): String {

        if (url.isBlank()) {
            return "url_missing"
        }

        val fixedUrl =
            if (
                url.startsWith("http://") ||
                url.startsWith("https://")
            ) {
                url
            } else {
                "https://$url"
            }

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(fixedUrl)
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)

        return "ok"
    }

    private fun search(
        context: Context,
        query: String
    ): String {

        if (query.isBlank()) {
            return "query_missing"
        }

        val encoded =
            Uri.encode(query)

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/search?q=$encoded"
                )
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)

        return "ok"
    }

    private fun navigate(
        context: Context,
        place: String
    ): String {

        if (place.isBlank()) {
            return "place_missing"
        }

        val encoded =
            Uri.encode(place)

        val intent =
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "geo:0,0?q=$encoded"
                )
            ).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)

        return "ok"
    }

    private fun openSettings(
        context: Context,
        page: String
    ): String {

        val action =
            when (
                page.lowercase().trim()
            ) {

                "wifi" ->
                    Settings.ACTION_WIFI_SETTINGS

                "bluetooth" ->
                    Settings.ACTION_BLUETOOTH_SETTINGS

                "display" ->
                    Settings.ACTION_DISPLAY_SETTINGS

                "sound" ->
                    Settings.ACTION_SOUND_SETTINGS

                "apps" ->
                    Settings.ACTION_APPLICATION_SETTINGS

                "battery" ->
                    Settings.ACTION_BATTERY_SAVER_SETTINGS

                "accessibility" ->
                    Settings.ACTION_ACCESSIBILITY_SETTINGS

                else ->
                    Settings.ACTION_SETTINGS
            }

        val intent =
            Intent(action).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }

        context.startActivity(intent)

        return "ok"
    }

    private fun systemAction(
        what: String
    ): String {

        val service =
            ControlService.inst
                ?: return "control_off"

        return when (
            what.lowercase().trim()
        ) {

            "back" -> {

                if (
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_BACK
                    )
                ) {
                    "ok"
                } else {
                    "failed"
                }
            }

            "home" -> {

                if (
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_HOME
                    )
                ) {
                    "ok"
                } else {
                    "failed"
                }
            }

            "recents" -> {

                if (
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_RECENTS
                    )
                ) {
                    "ok"
                } else {
                    "failed"
                }
            }

            "notifications" -> {

                if (
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_NOTIFICATIONS
                    )
                ) {
                    "ok"
                } else {
                    "failed"
                }
            }

            "quick_settings" -> {

                if (
                    service.performGlobalAction(
                        android.accessibilityservice
                            .AccessibilityService
                            .GLOBAL_ACTION_QUICK_SETTINGS
                    )
                ) {
                    "ok"
                } else {
                    "failed"
                }
            }

            else ->
                "unknown_system_action:$what"
        }
    }

    fun execute(
        context: Context,
        command: String
    ) {

        val lower =
            command.lowercase()

        when {

            lower == "go home" ||
            lower == "home" -> {

                systemAction("home")
            }

            lower == "go back" ||
            lower == "back" -> {

                systemAction("back")
            }

            lower.contains("open youtube") -> {

                openApp(
                    context,
                    "youtube"
                )
            }

            lower.contains("open chrome") -> {

                openApp(
                    context,
                    "chrome"
                )
            }

            lower.contains("open whatsapp") -> {

                openApp(
                    context,
                    "whatsapp"
                )
            }

            else -> {

                val service =
                    ControlService.inst

                if (
                    service != null &&
                    !service.findAndClick(command)
                ) {

                    Toast.makeText(
                        context,
                        "JAR: command not mapped",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}

