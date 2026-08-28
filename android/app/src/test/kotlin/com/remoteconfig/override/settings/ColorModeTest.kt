package com.remoteconfig.override.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ColorModeTest {
    @Test
    fun fromValue_roundTrips() {
        ColorMode.entries.forEach { mode ->
            assertEquals(mode, ColorMode.fromValue(mode.value))
        }
    }

    @Test
    fun fromValue_fallsBackToSystem() {
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(999))
        assertEquals(ColorMode.SYSTEM, ColorMode.fromValue(-1))
    }

    @Test
    fun isDark_matchesSpec() {
        assertTrue(ColorMode.DARK.isDark)
        assertFalse(ColorMode.LIGHT.isDark)
        assertFalse(ColorMode.SYSTEM.isDark)
    }
}
