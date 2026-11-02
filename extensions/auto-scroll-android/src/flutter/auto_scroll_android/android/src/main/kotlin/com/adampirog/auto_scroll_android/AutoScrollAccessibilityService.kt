package com.adampirog.auto_scroll_android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.view.accessibility.AccessibilityEvent
import kotlin.math.sqrt

class AutoScrollAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_COMMAND =
            "com.adampirog.autoscroll.ACCESSIBILITY_COMMAND"

        const val EXTRA_COMMAND = "command"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_SPEED = "speed"

        const val RESULT_OK = 1
        const val RESULT_FAILED = 0

        private const val PREFS = "auto_scroll"
        private const val PREF_VISIBLE = "overlay_visible"
        private const val PREF_SPEED = "speed"
        private const val PREF_DIRECTION = "direction"
    }

    private val handler = Handler(Looper.getMainLooper())

    private val preferences by lazy {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private var overlay: AutoScrollOverlay? = null
    private var receiverRegistered = false

    private var direction = "down"
    private var speed = 1.0
    private var playing = false
    private var generation = 0

    private var nextGesture: Runnable? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reply = getReply(intent) ?: return

            when (intent.getStringExtra(EXTRA_COMMAND)) {
                "state" -> {
                    reply.send(RESULT_OK, state())
                }

                "show" -> {
                    val shown = showOverlay()
                    reply.send(
                        if (shown) RESULT_OK else RESULT_FAILED,
                        state()
                    )
                }

                "hide" -> {
                    hideOverlay()
                    reply.send(RESULT_OK, state())
                }

                "configure" -> {
                    val newDirection =
                        intent.getStringExtra(EXTRA_DIRECTION)

                    val newSpeed = intent.getDoubleExtra(
                        EXTRA_SPEED,
                        Double.NaN
                    )

                    if (
                        newDirection !in listOf("up", "down") ||
                        !newSpeed.isFinite() ||
                        newSpeed !in 0.5..5.0
                    ) {
                        reply.send(RESULT_FAILED, state())
                        return
                    }

                    setConfiguration(newDirection!!, newSpeed)
                    reply.send(RESULT_OK, state())
                }

                else -> {
                    reply.send(RESULT_FAILED, state())
                }
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                pauseScrolling()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()

        direction = preferences.getString(
            PREF_DIRECTION,
            "down"
        )?.takeIf { it == "up" || it == "down" } ?: "down"

        speed = preferences.getFloat(
            PREF_SPEED,
            1.0f
        ).toDouble().coerceIn(0.5, 5.0)

        if (!receiverRegistered) {
            val filter = IntentFilter(ACTION_COMMAND)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    commandReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                registerReceiver(commandReceiver, filter)
            }

            registerReceiver(
                screenReceiver,
                IntentFilter(Intent.ACTION_SCREEN_OFF)
            )

            receiverRegistered = true
        }

        if (preferences.getBoolean(PREF_VISIBLE, false)) {
            showOverlay(persist = false)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // AutoScroll does not inspect content or accessibility events.
    }

    override fun onInterrupt() {
        pauseScrolling()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.refreshBounds()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        release()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        pauseScrolling()
        hideOverlay(persist = false)

        if (receiverRegistered) {
            unregisterReceiver(commandReceiver)
            unregisterReceiver(screenReceiver)
            receiverRegistered = false
        }
    }

    @Suppress("DEPRECATION")
    private fun getReply(intent: Intent): ResultReceiver? {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            intent.getParcelableExtra(
                EXTRA_REPLY,
                ResultReceiver::class.java
            )
        } else {
            intent.getParcelableExtra(EXTRA_REPLY)
        }
    }

    private fun state(): Bundle {
        return Bundle().apply {
            putBoolean("visible", overlay != null)
            putBoolean("playing", playing)
            putString("direction", direction)
            putDouble("speed", speed)
        }
    }

    private fun setConfiguration(
        newDirection: String,
        newSpeed: Double
    ) {
        direction = newDirection
        speed = newSpeed

        preferences.edit()
            .putString(PREF_DIRECTION, direction)
            .putFloat(PREF_SPEED, speed.toFloat())
            .apply()

        overlay?.sync(
            direction = direction,
            speed = speed,
            playing = playing
        )
    }

    private fun showOverlay(persist: Boolean = true): Boolean {
        if (overlay != null) return true

        val controller = AutoScrollOverlay(
            service = this,
            onToggle = ::toggleScrolling,
            onClose = { hideOverlay() },
            onDirection = { newDirection ->
                setConfiguration(newDirection, speed)
            },
            onSpeed = { newSpeed ->
                setConfiguration(direction, newSpeed)
            }
        )

        return try {
            controller.show(direction, speed, playing)
            overlay = controller

            if (persist) {
                preferences.edit()
                    .putBoolean(PREF_VISIBLE, true)
                    .apply()
            }

            true
        } catch (_: Exception) {
            controller.hide()
            false
        }
    }

    private fun hideOverlay(persist: Boolean = true) {
        pauseScrolling()
        overlay?.hide()
        overlay = null

        if (persist) {
            preferences.edit()
                .putBoolean(PREF_VISIBLE, false)
                .apply()
        }
    }

    private fun toggleScrolling() {
        if (playing) {
            pauseScrolling()
        } else {
            startScrolling()
        }
    }

    private fun startScrolling() {
        if (playing) return

        playing = true
        val token = ++generation

        overlay?.sync(direction, speed, playing)

        performGesture(token)
    }

    private fun pauseScrolling() {
        generation++
        playing = false

        nextGesture?.let(handler::removeCallbacks)
        nextGesture = null

        overlay?.sync(direction, speed, playing)
    }

    private fun performGesture(token: Int) {
        if (!playing || token != generation) return

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        if (width <= 0 || height <= 0) {
            pauseScrolling()
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

        val durationMs = (
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
                    durationMs.toLong()
                )
            )
            .build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(
                    gestureDescription: GestureDescription?
                ) {
                    if (!playing || token != generation) return

                    val delayMs = (
                        170 / speed
                    ).toLong().coerceIn(40L, 340L)

                    val task = Runnable {
                        nextGesture = null
                        performGesture(token)
                    }

                    nextGesture = task
                    handler.postDelayed(task, delayMs)
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    if (token == generation) {
                        pauseScrolling()
                    }
                }
            },
            handler
        )

        if (!dispatched) {
            pauseScrolling()
        }
    }
}
