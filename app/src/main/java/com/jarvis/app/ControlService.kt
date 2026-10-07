package com.jarvis.app

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ControlService : AccessibilityService() {

    companion object {

        var instance: ControlService? = null
            private set

        val inst: ControlService?
            get() = instance
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {
        // JAR observes accessibility events here.
    }

    override fun onInterrupt() {
        // Service interrupted.
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun findAndClick(query: String): Boolean {

        val root = rootInActiveWindow
            ?: return false

        val nodes =
            root.findAccessibilityNodeInfosByText(query)

        for (node in nodes) {

            if (node.isClickable) {

                val result =
                    node.performAction(
                        AccessibilityNodeInfo.ACTION_CLICK
                    )

                node.recycle()

                return result
            }

            var parent = node.parent

            while (parent != null) {

                if (parent.isClickable) {

                    val result =
                        parent.performAction(
                            AccessibilityNodeInfo.ACTION_CLICK
                        )

                    node.recycle()

                    return result
                }

                parent = parent.parent
            }

            node.recycle()
        }

        return false
    }

    fun tap(
        x: Float,
        y: Float
    ): Boolean {

        if (
            android.os.Build.VERSION.SDK_INT < 24
        ) {
            return false
        }

        val path =
            Path().apply {
                moveTo(x, y)
            }

        val gesture =
            GestureDescription.Builder()
                .addStroke(
                    GestureDescription.StrokeDescription(
                        path,
                        0,
                        80
                    )
                )
                .build()

        return dispatchGesture(
            gesture,
            null,
            null
        )
    }

    fun screenText(): String {

        val root =
            rootInActiveWindow
                ?: return ""

        val result =
            StringBuilder()

        fun walk(
            node: AccessibilityNodeInfo?
        ) {

            if (node == null) return

            val text =
                node.text
                    ?.toString()
                    ?.trim()

            val description =
                node.contentDescription
                    ?.toString()
                    ?.trim()

            if (!text.isNullOrEmpty()) {
                result
                    .append(text)
                    .append(" ")
            }

            if (!description.isNullOrEmpty()) {
                result
                    .append(description)
                    .append(" ")
            }

            for (
                i in 0 until node.childCount
            ) {
                walk(node.getChild(i))
            }
        }

        walk(root)

        return result
            .toString()
            .trim()
            .take(6000)
    }
} 
            
    
