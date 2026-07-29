package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun hunch(
    expectedCount: Int,
    expectedPer: ExpectedPer,
) = HunchEntity(
    id = 1,
    caseId = 1,
    direction = HunchDirection.TOO_OFTEN,
    expectedCount = expectedCount,
    expectedPer = expectedPer,
    createdAt = 0L,
    resolvedAt = null,
)

class CheckInTest {
    @Test
    fun `toggle off always wins, regardless of hunch or settings default`() {
        val result = effectiveCheckInDays(checkInsEnabled = false, hunch = hunch(3, ExpectedPer.WEEK), settingsDefaultDays = 14)

        assertNull(result)
    }

    @Test
    fun `no hunch falls back to the settings default`() {
        val result = effectiveCheckInDays(checkInsEnabled = true, hunch = null, settingsDefaultDays = 14)

        assertEquals(14, result)
    }

    @Test
    fun `no hunch and settings default off means off`() {
        val result = effectiveCheckInDays(checkInsEnabled = true, hunch = null, settingsDefaultDays = null)

        assertNull(result)
    }

    @Test
    fun `an active hunch overrides the settings default entirely`() {
        val result = effectiveCheckInDays(checkInsEnabled = true, hunch = hunch(1, ExpectedPer.WEEK), settingsDefaultDays = null)

        assertEquals(14, result)
    }

    @Test
    fun `hunch-derived interval is 2x the expected gap`() {
        // 3x a week -> expected gap ~2.33 days -> 2x = ~4.67, rounds to 5.
        assertEquals(5, hunchCheckInDays(hunch(3, ExpectedPer.WEEK)))
    }

    @Test
    fun `hunch-derived interval clamps to the 3 day floor`() {
        // 7x a day -> tiny gap -> clamps up to the floor.
        assertEquals(HUNCH_CHECK_IN_MIN_DAYS, hunchCheckInDays(hunch(7, ExpectedPer.DAY)))
    }

    @Test
    fun `hunch-derived interval clamps to the 30 day ceiling`() {
        // Once a month -> 2x gap = 60 days -> clamps down to the ceiling.
        assertEquals(HUNCH_CHECK_IN_MAX_DAYS, hunchCheckInDays(hunch(1, ExpectedPer.MONTH)))
    }
}
