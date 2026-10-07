package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object Actions {

    fun execute(context: Context, raw: String) {
        val c = raw.lowercase().trim()

        when {
            c == "home" || c.contains("go home") || c.contains("open home") ->
                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
                )

            c == "back" || c.contains("go back") ->
                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK
                )

            c.contains("recent") ->
                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS
                )

            c.contains("notifications") ->
                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
                )

            c.contains("quick settings") ->
                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
                )

            c.contains("accessibility settings") ->
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            c.contains("settings") ->
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

            c.contains("youtube") ->
                openUrl(context, "https://www.youtube.com")

            c.contains("google") ->
                openUrl(context, "https://www.google.com")

            c.contains("chrome") ->
                launchPackage(context, "com.android.chrome")

            c.contains("whatsapp") ->
                launchPackage(context, "com.whatsapp")

            c.contains("open camera") || c == "camera" ->
                launchPackage(context, "com.android.camera")

            else -> {
                ControlService.instance?.findAndClick(c)
            }
        }
    }

    private fun openUrl(context: Context, url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun launchPackage(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}
