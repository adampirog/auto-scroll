package com.adampirog.auto_scroll_android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipePlanTest {

    @Test
    fun `one times preserves original down swipe`() {
        val swipe = ScrollRules.swipe(1000, 1000, "down", 1.0)

        assertEquals(500f, swipe.x, 0.1f)
        assertEquals(700f, swipe.startY, 0.1f)
        assertEquals(530f, swipe.endY, 0.1f)
        assertEquals(420L, swipe.durationMs)
        assertEquals(170L, ScrollRules.delayMs(1.0))
    }

    @Test
    fun `up and down move in opposite directions`() {
        val up = ScrollRules.swipe(1000, 1000, "up", 5.0)
        val down = ScrollRules.swipe(1000, 1000, "down", 5.0)

        assertTrue(up.endY > up.startY)
        assertTrue(down.endY < down.startY)
    }

    @Test
    fun `twenty five times reaches high speed bounds`() {
        val swipe = ScrollRules.swipe(1000, 1000, "down", 25.0)

        assertEquals(170f, swipe.endY, 0.1f)
        assertEquals(75L, swipe.durationMs)
        assertEquals(10L, ScrollRules.delayMs(25.0))
    }

    @Test
    fun `higher speeds do not slow down or shorten swipe`() {
        val plans = (1..25).map { speed ->
            ScrollRules.swipe(
                1000,
                1000,
                "down",
                speed.toDouble()
            )
        }

        for (index in 1 until plans.size) {
            assertTrue(
                plans[index].durationMs <= plans[index - 1].durationMs
            )

            assertTrue(
                plans[index].endY <= plans[index - 1].endY
            )

            assertTrue(
                ScrollRules.delayMs((index + 1).toDouble()) <=
                    ScrollRules.delayMs(index.toDouble())
            )
        }
    }

    @Test
    fun `all supported speeds keep swipes inside the display`() {
        for (speed in 1..25) {
            for (direction in listOf("up", "down")) {
                val swipe = ScrollRules.swipe(
                    1080,
                    2400,
                    direction,
                    speed.toDouble()
                )

                assertTrue(swipe.startY in 0f..2400f)
                assertTrue(swipe.endY in 0f..2400f)
                assertTrue(swipe.durationMs in 75L..420L)
            }
        }
    }

    @Test
    fun `invalid screen size and configuration fail explicitly`() {
        assertThrows(IllegalArgumentException::class.java) {
            ScrollRules.swipe(0, 1000, "down", 1.0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ScrollRules.swipe(1000, -1, "down", 1.0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ScrollRules.swipe(1000, 1000, "left", 1.0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            ScrollRules.swipe(1000, 1000, "down", 26.0)
        }
    }
}

