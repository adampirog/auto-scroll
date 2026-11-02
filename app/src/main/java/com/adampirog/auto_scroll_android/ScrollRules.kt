package com.adampirog.auto_scroll_android

import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal data class SwipePlan(
    val x: Float,
    val startY: Float,
    val endY: Float,
    val durationMs: Long
)

/** Android-independent rules used by the accessibility service. */
internal object ScrollRules {
    const val MIN_SPEED = 1.0
    const val MAX_SPEED = 25.0
    const val DEFAULT_DIRECTION = "down"

    fun isValid(direction: String?, speed: Double): Boolean =
        (direction == "up" || direction == "down") &&
            speed.isFinite() && speed in MIN_SPEED..MAX_SPEED

    fun restoreDirection(value: String?): String =
        value?.takeIf { it == "up" || it == "down" }
            ?: DEFAULT_DIRECTION

    fun normalizeSpeed(value: Double): Double =
        if (value.isFinite()) {
            value.roundToInt().coerceIn(1, 25).toDouble()
        } else {
            MIN_SPEED
        }

    fun delayMs(speed: Double): Long {
        require(speed.isFinite() && speed in MIN_SPEED..MAX_SPEED)

        return (170.0 / speed)
            .roundToLong()
            .coerceIn(10L, 170L)
    }

    fun swipe(
        widthPx: Int,
        heightPx: Int,
        direction: String,
        speed: Double
    ): SwipePlan {
        require(widthPx > 0 && heightPx > 0) {
            "Screen dimensions must be positive"
        }

        require(isValid(direction, speed)) {
            "Invalid scrolling configuration"
        }

        val distanceFraction = (
            0.17 + 0.015 * (speed - 1.0)
        ).coerceIn(0.17, 0.53)

        val distance = heightPx * distanceFraction.toFloat()

        val startY = heightPx * if (direction == "down") {
            0.70f
        } else {
            0.30f
        }

        val endY = if (direction == "down") {
            startY - distance
        } else {
            startY + distance
        }

        val durationMs = (
            420.0 / speed.pow(0.75)
        ).roundToLong().coerceIn(75L, 420L)

        return SwipePlan(
            x = widthPx * 0.5f,
            startY = startY,
            endY = endY,
            durationMs = durationMs
        )
    }
}

