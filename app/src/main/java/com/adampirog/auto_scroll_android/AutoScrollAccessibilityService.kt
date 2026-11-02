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
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoScrollAccessibilityService : AccessibilityService() {

    companion object {
        const val ACTION_COMMAND =
            "com.adampirog.autoscroll.ACCESSIBILITY_COMMAND"

        const val COMMAND_PERMISSION =
            "com.adampirog.autoscroll.permission.ACCESSIBILITY_COMMAND"

        const val EXTRA_COMMAND = "command"
        const val EXTRA_REPLY = "reply"
        const val EXTRA_DIRECTION = "direction"
        const val EXTRA_SPEED = "speed"

        const val RESULT_OK = 1
        const val RESULT_FAILED = 0

        const val MIN_SPEED = ScrollRules.MIN_SPEED
        const val MAX_SPEED = ScrollRules.MAX_SPEED

        private const val TAG = "AutoScrollService"
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
    private var commandRegistered = false
    private var screenRegistered = false

    private var direction = ScrollRules.DEFAULT_DIRECTION
    private var speed = ScrollRules.MIN_SPEED
    private var playing = false

    private var generation = 0
    private var nextGesture: Runnable? = null

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val reply = getReply(intent) ?: return

            when (intent.getStringExtra(EXTRA_COMMAND)) {
                "state" -> reply.send(RESULT_OK, state())

                "show" -> {
                    val success = showOverlay()
                    reply.send(
                        if (success) RESULT_OK else RESULT_FAILED,
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

                    if (!ScrollRules.isValid(newDirection, newSpeed)) {
                        reply.send(RESULT_FAILED, state())
                        return
                    }

                    setConfiguration(newDirection!!, newSpeed)
                    reply.send(RESULT_OK, state())
                }

                else -> reply.send(RESULT_FAILED, state())
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

        direction = ScrollRules.restoreDirection(
            preferences.getString(
                PREF_DIRECTION,
                ScrollRules.DEFAULT_DIRECTION
            )
        )

        speed = ScrollRules.normalizeSpeed(
            preferences.getFloat(
                PREF_SPEED,
                ScrollRules.MIN_SPEED.toFloat()
            ).toDouble()
        )

        registerReceivers()

        if (preferences.getBoolean(PREF_VISIBLE, false)) {
            showOverlay(persist = false)
        }
    }

    private fun registerReceivers() {
        if (!commandRegistered) {
            try {
                val filter = IntentFilter(ACTION_COMMAND)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        commandReceiver,
                        filter,
                        COMMAND_PERMISSION,
                        null,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    registerReceiver(
                        commandReceiver,
                        filter,
                        COMMAND_PERMISSION,
                        null
                    )
                }

                commandRegistered = true
            } catch (e: RuntimeException) {
                Log.e(TAG, "Command receiver registration failed", e)
            }
        }

        if (!screenRegistered) {
            try {
                registerReceiver(
                    screenReceiver,
                    IntentFilter(Intent.ACTION_SCREEN_OFF)
                )
                screenRegistered = true
            } catch (e: RuntimeException) {
                Log.e(TAG, "Screen receiver registration failed", e)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window contents and events are not inspected.
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
        hideOverlay(persist = false)
        unregisterReceivers()
    }

    private fun unregisterReceivers() {
        if (commandRegistered) {
            try {
                unregisterReceiver(commandReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Command receiver already unregistered", e)
            } finally {
                commandRegistered = false
            }
        }

        if (screenRegistered) {
            try {
                unregisterReceiver(screenReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Screen receiver already unregistered", e)
            } finally {
                screenRegistered = false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getReply(intent: Intent): ResultReceiver? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                EXTRA_REPLY,
                ResultReceiver::class.java
            )
        } else {
            intent.getParcelableExtra(EXTRA_REPLY)
        }

    private fun state(): Bundle = Bundle().apply {
        putBoolean("visible", overlay != null)
        putBoolean("playing", playing)
        putString("direction", direction)
        putDouble("speed", speed)
    }

    private fun setConfiguration(
        newDirection: String,
        newSpeed: Double
    ) {
        direction = newDirection
        speed = ScrollRules.normalizeSpeed(newSpeed)

        preferences.edit()
            .putString(PREF_DIRECTION, direction)
            .putFloat(PREF_SPEED, speed.toFloat())
            .apply()

        overlay?.sync(direction, speed, playing)
    }

    private fun showOverlay(persist: Boolean = true): Boolean {
        if (overlay != null) return true

        val controller = AutoScrollOverlay(
            service = this,
            onToggle = ::toggleScrolling,
            onClose = { hideOverlay() },
            onDirection = { setConfiguration(it, speed) },
            onSpeed = { setConfiguration(direction, it) }
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
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to show overlay", e)

            try {
                controller.hide()
            } catch (cleanupError: RuntimeException) {
                Log.w(TAG, "Overlay cleanup failed", cleanupError)
            }

            false
        }
    }

    private fun hideOverlay(persist: Boolean = true) {
        pauseScrolling()

        val current = overlay
        overlay = null

        if (current != null) {
            try {
                current.hide()
            } catch (e: RuntimeException) {
                Log.w(TAG, "Unable to remove overlay", e)
            }
        }

        if (persist) {
            preferences.edit()
                .putBoolean(PREF_VISIBLE, false)
                .apply()
        }
    }

    private fun toggleScrolling() {
        if (playing) pauseScrolling() else startScrolling()
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
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        if (width <= 0 || height <= 0) {
            pauseScrolling()
            return
        }

        val swipe = ScrollRules.swipe(
            widthPx = width,
            heightPx = height,
            direction = direction,
            speed = speed
        )

        val path = Path().apply {
            moveTo(swipe.x, swipe.startY)
            lineTo(swipe.x, swipe.endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    0L,
                    swipe.durationMs
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

                    val task = Runnable {
                        nextGesture = null
                        performGesture(token)
                    }

                    nextGesture = task
                    handler.postDelayed(
                        task,
                        ScrollRules.delayMs(speed)
                    )
                }

                override fun onCancelled(
                    gestureDescription: GestureDescription?
                ) {
                    if (token == generation) pauseScrolling()
                }
            },
            handler
        )

        if (!dispatched) pauseScrolling()
    }
}
