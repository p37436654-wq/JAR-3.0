package com.jarvis.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val d = resources.displayMetrics.density

        val root = LinearLayout(this).apply {

            orientation = LinearLayout.VERTICAL

            gravity = Gravity.CENTER_HORIZONTAL

            setPadding(
                (20 * d).toInt(),
                (40 * d).toInt(),
                (20 * d).toInt(),
                (30 * d).toInt()
            )

            setBackgroundColor(
                Color.parseColor("#05070D")
            )
        }

        val orb = View(this).apply {

            background =
                GradientDrawable().apply {

                    shape =
                        GradientDrawable.OVAL

                    setColor(
                        Color.parseColor("#00E5FF")
                    )
                }

            layoutParams =
                LinearLayout.LayoutParams(
                    (140 * d).toInt(),
                    (140 * d).toInt()
                ).apply {

                    bottomMargin =
                        (25 * d).toInt()
                }
        }

        val title =
            TextView(this).apply {

                text = "JAR 3.0"

                textSize = 32f

                setTextColor(
                    Color.WHITE
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    (8 * d).toInt()
                )
            }

        val subtitle =
            TextView(this).apply {

                text =
                    "Your personal voice assistant"

                textSize = 16f

                setTextColor(
                    Color.LTGRAY
                )

                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    (25 * d).toInt()
                )
            }

        fun button(
            text: String,
            action: () -> Unit
        ): Button {

            return Button(this).apply {

                this.text = text

                textSize = 15f

                setOnClickListener {
                    action()
                }

                layoutParams =
                    LinearLayout.LayoutParams(
                        -1,
                        (55 * d).toInt()
                    ).apply {

                        bottomMargin =
                            (12 * d).toInt()
                    }
            }
        }

        val permissions =
            mutableListOf(

                Manifest.permission.RECORD_AUDIO,

                Manifest.permission.CALL_PHONE,

                Manifest.permission.SEND_SMS,

                Manifest.permission.READ_CONTACTS
            )

        if (
            Build.VERSION.SDK_INT >= 33
        ) {

            permissions.add(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }

        root.addView(orb)

        root.addView(title)

        root.addView(subtitle)

        root.addView(
            button(
                "1. Allow microphone & phone permissions"
            ) {

                requestPermissions(
                    permissions.toTypedArray(),
                    100
                )
            }
        )

        root.addView(
            button(
                "2. Allow display over other apps"
            ) {

                try {

                    startActivity(
                        Intent(
                            Settings
                                .ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse(
                                "package:$packageName"
                            )
                        )
                    )

                } catch (
                    e: Exception
                ) {

                    startActivity(
                        Intent(
                            Settings
                                .ACTION_MANAGE_OVERLAY_PERMISSION
                        )
                    )
                }
            }
        )

        root.addView

        
