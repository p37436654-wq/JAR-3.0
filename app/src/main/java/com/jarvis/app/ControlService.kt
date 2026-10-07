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
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // JARVIS can observe accessibility events while the service is enabled.
    }

    override fun onInterrupt() {
        // Service interrupted.
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun findAndClick(query: String): Boolean {
        val root = rootInActiveWindow ?: return false

        val nodes = root.findAccessibilityNodeInfosByText(query)

        for (node in nodes) {

            if (node.isClickable) {
                val result = node.performAction(
                    AccessibilityNodeInfo.ACTION_CLICK
                )

                node.recycle()
                return result
            }

            var parent = node.parent

            while (parent != null) {

                if (parent.isClickable) {
                    val result = parent.performAction(
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

    fun tap(x: Float, y: Float): Boolean {

        if (android.os.Build.VERSION.SDK_INT < 24) {
            return false
        }

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
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
}
