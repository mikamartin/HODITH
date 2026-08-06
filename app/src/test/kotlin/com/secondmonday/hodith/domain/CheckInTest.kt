package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.HunchEntity
import com.secondmonday.hodith.data.LogFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

private val ZONE = ZoneId.systemDefault()

private fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

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

private fun case(
    createdAt: Long,
    checkInsEnabled: Boolean = true,
    lastCheckInAt: Long? = null,
) = CaseEntity(
    id = 1,
    name = "Case",
    icon = "icon",
    createdAt = createdAt,
    logFlow = LogFlow.ONE_TAP,
    durationMode = DurationMode.NONE,
    intensityEnabled = false,
    hunchNudgeDismissed = false,
    checkInsEnabled = checkInsEnabled,
    lastCheckInAt = lastCheckInAt,
    sortOrder = 0,
    archived = false,
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

    // ---- evaluateCheckIn: due-check anchored on the latest of event / check-in / creation ----

    @Test
    fun `evaluateCheckIn is never due when the toggle is off`() {
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0), checkInsEnabled = false),
                hunch = null,
                settingsDefaultDays = 7,
                mostRecentEventAt = null,
                now = millisAtDay(100),
            )

        assertFalse(result.due)
    }

    @Test
    fun `evaluateCheckIn fires at exactly the settings-default day gap since case creation with no events`() {
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0)),
                hunch = null,
                settingsDefaultDays = 14,
                mostRecentEventAt = null,
                now = millisAtDay(14),
            )

        assertTrue(result.due)
        assertEquals(14L, result.silentDays)
    }

    @Test
    fun `evaluateCheckIn does not fire one day short of the effective interval`() {
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0)),
                hunch = null,
                settingsDefaultDays = 14,
                mostRecentEventAt = null,
                now = millisAtDay(13),
            )

        assertFalse(result.due)
    }

    @Test
    fun `evaluateCheckIn anchors on the most recent event, not case creation`() {
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0)),
                hunch = null,
                settingsDefaultDays = 7,
                mostRecentEventAt = millisAtDay(90),
                now = millisAtDay(95),
            )

        assertFalse(result.due)
        assertEquals(5L, result.silentDays)
    }

    @Test
    fun `evaluateCheckIn anchors on the last check-in when it is more recent than the last event`() {
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0), lastCheckInAt = millisAtDay(80)),
                hunch = null,
                settingsDefaultDays = 7,
                mostRecentEventAt = millisAtDay(50),
                now = millisAtDay(87),
            )

        assertTrue(result.due)
        assertEquals(7L, result.silentDays)
    }

    @Test
    fun `evaluateCheckIn uses the hunch-derived interval over the settings default`() {
        // 3x a week -> hunch-derived interval of 5 days (see the hunch-derived-interval test above).
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0)),
                hunch = hunch(3, ExpectedPer.WEEK),
                settingsDefaultDays = 30,
                mostRecentEventAt = null,
                now = millisAtDay(5),
            )

        assertTrue(result.due)
    }
}
