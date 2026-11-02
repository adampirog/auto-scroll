package com.adampirog.auto_scroll_android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollRulesTest {

    @Test
    fun `range endpoints are valid`() {
        assertTrue(ScrollRules.isValid("down", 1.0))
        assertTrue(ScrollRules.isValid("up", 25.0))
    }

    @Test
    fun `unknown directions are rejected`() {
        for (direction in listOf(null, "", "left", "UP")) {
            assertFalse(ScrollRules.isValid(direction, 5.0))
        }
    }

    @Test
    fun `out of range and non finite speeds are rejected`() {
        for (speed in listOf(
            0.5,
            0.0,
            25.1,
            Double.NaN,
            Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY
        )) {
            assertFalse(ScrollRules.isValid("down", speed))
        }
    }

    @Test
    fun `saved directions use a safe fallback`() {
        assertEquals("up", ScrollRules.restoreDirection("up"))
        assertEquals("down", ScrollRules.restoreDirection("down"))
        assertEquals("down", ScrollRules.restoreDirection("sideways"))
        assertEquals("down", ScrollRules.restoreDirection(null))
    }

    @Test
    fun `saved speeds are rounded and clamped`() {
        assertEquals(1.0, ScrollRules.normalizeSpeed(0.5), 0.0)
        assertEquals(3.0, ScrollRules.normalizeSpeed(2.7), 0.0)
        assertEquals(25.0, ScrollRules.normalizeSpeed(100.0), 0.0)
    }

    @Test
    fun `invalid saved speeds default to one`() {
        assertEquals(
            1.0,
            ScrollRules.normalizeSpeed(Double.NaN),
            0.0
        )

        assertEquals(
            1.0,
            ScrollRules.normalizeSpeed(Double.POSITIVE_INFINITY),
            0.0
        )
    }
}

