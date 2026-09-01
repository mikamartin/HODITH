package com.secondmonday.hodith.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationModeTest {
    @Test
    fun `tracksDuration is false only for NONE`() {
        assertFalse(DurationMode.NONE.tracksDuration)
        assertTrue(DurationMode.MANUAL.tracksDuration)
        assertTrue(DurationMode.START_STOP.tracksDuration)
    }
}
