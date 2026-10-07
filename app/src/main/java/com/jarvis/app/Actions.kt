package com.jarvis.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object Actions {

    fun execute(context: Context, raw: String) {

        val command = raw.lowercase().trim()

        when {

            command == "home" ||
            command.contains("go home") ||
            command.contains("open home") -> {

                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService
                        .GLOBAL_ACTION_HOME
                )
            }

            command == "back" ||
            command.contains("go back") -> {

                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService
                        .GLOBAL_ACTION_BACK
                )
            }

            command.contains("recent") -> {

                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService
                        .GLOBAL_ACTION_RECENTS
                )
            }

            command.contains("notifications") -> {

                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService
                        .GLOBAL_ACTION_NOTIFICATIONS
                )
            }

            command.contains("quick settings") -> {

                ControlService.instance?.performGlobalAction(
                    android.accessibilityservice.AccessibilityService
                        .GLOBAL_ACTION_QUICK_SETTINGS
                )
            }

            command.contains("accessibility settings") -> {

                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            command.contains("settings") -> {

                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            command.contains("youtube") -> {
                openUrl(context, "https://www.youtube.com")
            }

            command.contains("google") -> {
                openUrl(context, "https://www.google.com")
            }

            command.contains("chrome") -> {
                launchPackage(context, "com.android.chrome")
            }

            command.contains("whatsapp") -> {
                launchPackage(context, "com.whatsapp")
            }

            command.contains("camera") -> {
                launchCamera(context)
            }

            else -> {
                val clicked =
                    ControlService.instance?.findAndClick(command) ?: false

                if (!clicked) {
                    Toast.makeText(
                        context,
                        "JARVIS: I couldn't find that control.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun openUrl(
        context: Context,
        url: String
    ) {

        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse(url)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }

    private fun launchPackage(
        context: Context,
        packageName: String
    ) {

        val intent =
            context.packageManager
                .getLaunchIntentForPackage(packageName)

        if (intent != null) {

            intent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            context.startActivity(intent)

        } else {

            Toast.makeText(
                context,
                "App not installed.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun launchCamera(context: Context) {

        val intent = Intent(
            "android.media.action.IMAGE_CAPTURE"
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}
