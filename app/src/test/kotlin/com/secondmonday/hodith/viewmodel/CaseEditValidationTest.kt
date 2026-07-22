package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseEditValidationTest {
    @Test
    fun `blank name and missing icon are both invalid`() {
        val validation = validateCaseEdit(name = "  ", icon = null)

        assertFalse(validation.nameValid)
        assertFalse(validation.iconValid)
        assertFalse(validation.isValid)
    }

    @Test
    fun `non-blank name and selected icon are valid`() {
        val validation = validateCaseEdit(name = "Migraines", icon = "🤕")

        assertTrue(validation.nameValid)
        assertTrue(validation.iconValid)
        assertTrue(validation.isValid)
    }

    @Test
    fun `blank name is invalid even with an icon selected`() {
        val validation = validateCaseEdit(name = "", icon = "🤕")

        assertFalse(validation.isValid)
    }

    @Test
    fun `check-in default maps to null`() {
        assertEquals(null, checkInDaysFor(CheckInOption.DEFAULT, customDays = "14"))
    }

    @Test
    fun `check-in off maps to zero`() {
        assertEquals(0, checkInDaysFor(CheckInOption.OFF, customDays = "14"))
    }

    @Test
    fun `check-in custom maps to the parsed day count`() {
        assertEquals(14, checkInDaysFor(CheckInOption.CUSTOM, customDays = "14"))
    }

    @Test
    fun `check-in custom with blank input falls back to null`() {
        assertEquals(null, checkInDaysFor(CheckInOption.CUSTOM, customDays = ""))
    }

    @Test
    fun `check-in custom with zero falls back to null`() {
        assertEquals(null, checkInDaysFor(CheckInOption.CUSTOM, customDays = "0"))
    }

    @Test
    fun `one tap is allowed with no duration and no intensity`() {
        assertTrue(isOneTapAllowed(DurationMode.NONE, intensityEnabled = false))
    }

    @Test
    fun `one tap is allowed with start-stop duration`() {
        assertTrue(isOneTapAllowed(DurationMode.START_STOP, intensityEnabled = false))
    }

    @Test
    fun `one tap is disallowed with manual duration`() {
        assertFalse(isOneTapAllowed(DurationMode.MANUAL, intensityEnabled = false))
    }

    @Test
    fun `one tap is disallowed with intensity enabled`() {
        assertFalse(isOneTapAllowed(DurationMode.NONE, intensityEnabled = true))
    }

    @Test
    fun `logFlow is coerced to detail sheet when manual duration makes one tap invalid`() {
        val result = coerceLogFlow(LogFlow.ONE_TAP, DurationMode.MANUAL, intensityEnabled = false)

        assertEquals(LogFlow.DETAIL_SHEET, result)
    }

    @Test
    fun `logFlow is coerced to detail sheet when intensity makes one tap invalid`() {
        val result = coerceLogFlow(LogFlow.ONE_TAP, DurationMode.NONE, intensityEnabled = true)

        assertEquals(LogFlow.DETAIL_SHEET, result)
    }

    @Test
    fun `logFlow is left alone when one tap is still valid`() {
        val result = coerceLogFlow(LogFlow.ONE_TAP, DurationMode.START_STOP, intensityEnabled = false)

        assertEquals(LogFlow.ONE_TAP, result)
    }

    @Test
    fun `detail sheet logFlow is never touched by coercion`() {
        val result = coerceLogFlow(LogFlow.DETAIL_SHEET, DurationMode.MANUAL, intensityEnabled = true)

        assertEquals(LogFlow.DETAIL_SHEET, result)
    }

    @Test
    fun `loading an existing case self-corrects a stale invalid one-tap logFlow`() {
        val case =
            CaseEntity(
                name = "Migraine",
                icon = "🤕",
                createdAt = 0,
                logFlow = LogFlow.ONE_TAP,
                durationMode = DurationMode.START_STOP,
                intensityEnabled = true,
                hunchNudgeDismissed = false,
                pinned = false,
                checkInDays = null,
                lastCheckInAt = null,
                sortOrder = 0,
                archived = false,
            )

        assertEquals(LogFlow.DETAIL_SHEET, case.toUiState().logFlow)
    }

    @Test
    fun `loading an existing case leaves a valid one-tap logFlow alone`() {
        val case =
            CaseEntity(
                name = "Lost my keys",
                icon = "🔑",
                createdAt = 0,
                logFlow = LogFlow.ONE_TAP,
                durationMode = DurationMode.NONE,
                intensityEnabled = false,
                hunchNudgeDismissed = false,
                pinned = false,
                checkInDays = null,
                lastCheckInAt = null,
                sortOrder = 0,
                archived = false,
            )

        assertEquals(LogFlow.ONE_TAP, case.toUiState().logFlow)
    }
}
