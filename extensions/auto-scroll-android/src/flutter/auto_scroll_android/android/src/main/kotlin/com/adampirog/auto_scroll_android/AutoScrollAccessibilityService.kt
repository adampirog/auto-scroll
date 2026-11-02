package com.adampirog.auto_scroll_android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.sqrt

class AutoScrollAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        private var instance: AutoScrollAccessibilityService? = null

        fun isReady(): Boolean = instance != null

        fun swipe(
            direction: String,
            speed: Double,
            callback: (Boolean) -> Unit
        ) {
            val service = instance

            if (service == null) {
                callback(false)
                return
            }

            service.performSwipe(direction, speed, callback)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No screen content or accessibility events are collected.
    }

    override fun onInterrupt() {
        // No accessibility feedback to interrupt.
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun performSwipe(
        direction: String,
        speed: Double,
        callback: (Boolean) -> Unit
    ) {
        if (
            direction !in listOf("up", "down") ||
            !speed.isFinite() ||
            speed !in 0.5..5.0
        ) {
            callback(false)
            return
        }

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        if (width <= 0 || height <= 0) {
            callback(false)
            return
        }

        val x = width * 0.5f
        val top = height * 0.30f
        val bottom = height * 0.70f

        val distanceFraction = (
            0.12 + 0.05 * speed
        ).coerceIn(0.14, 0.38)

        val distance = height * distanceFraction.toFloat()

        val startY = if (direction == "down") bottom else top
        val endY = if (direction == "down") {
            bottom - distance
        } else {
            top + distance
        }

        val duration = (
            420 / sqrt(speed)
        ).toInt().coerceIn(200, 600)

        val path = Path().apply {
            moveTo(x, startY)
            lineTo(x, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0,
                    duration.toLong()
                )
            )
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    callback(true)
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    callback(false)
                }
            },
            Handler(Looper.getMainLooper())
        )

        if (!dispatched) {
            callback(false)
        }
    }
}

