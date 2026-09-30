package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.testsupport.millisAtDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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
    checkInsEnabled = checkInsEnabled,
    lastCheckInAt = lastCheckInAt,
    sortOrder = 0,
    archived = false,
)

class CheckInTest {
    @Test
    fun `toggle off always wins, regardless of settings default`() {
        val result = effectiveCheckInDays(checkInsEnabled = false, settingsDefaultDays = 14)

        assertNull(result)
    }

    @Test
    fun `toggle on uses the settings default`() {
        val result = effectiveCheckInDays(checkInsEnabled = true, settingsDefaultDays = 14)

        assertEquals(14, result)
    }

    @Test
    fun `toggle on with settings default off means off`() {
        val result = effectiveCheckInDays(checkInsEnabled = true, settingsDefaultDays = null)

        assertNull(result)
    }

    // ---- evaluateCheckIn: due-check anchored on the latest of event / check-in / creation ----

    @Test
    fun `evaluateCheckIn is never due when the toggle is off`() {
        val result =
            evaluateCheckIn(
                case(createdAt = millisAtDay(0), checkInsEnabled = false),
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
                settingsDefaultDays = 7,
                mostRecentEventAt = millisAtDay(50),
                now = millisAtDay(87),
            )

        assertTrue(result.due)
        assertEquals(7L, result.silentDays)
    }
}
