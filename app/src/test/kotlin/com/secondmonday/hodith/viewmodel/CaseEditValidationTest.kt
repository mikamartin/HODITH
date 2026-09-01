package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.testsupport.testCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseEditValidationTest {
    private fun otherCase(
        id: Long,
        name: String,
    ) = testCase(id = id, name = name, icon = "🤕", logFlow = LogFlow.DETAIL_SHEET)

    @Test
    fun `blank name and missing icon are both invalid`() {
        val validation = validateCaseEdit(name = "  ", icon = null, editingCaseId = null, otherActiveCases = emptyList())

        assertFalse(validation.nameValid)
        assertFalse(validation.iconValid)
        assertFalse(validation.isValid)
    }

    @Test
    fun `non-blank name and selected icon are valid`() {
        val validation =
            validateCaseEdit(name = "Migraines", icon = "🤕", editingCaseId = null, otherActiveCases = emptyList())

        assertTrue(validation.nameValid)
        assertTrue(validation.iconValid)
        assertTrue(validation.isValid)
    }

    @Test
    fun `blank name is invalid even with an icon selected`() {
        val validation = validateCaseEdit(name = "", icon = "🤕", editingCaseId = null, otherActiveCases = emptyList())

        assertFalse(validation.isValid)
    }

    @Test
    fun `name matching another active case is a case-insensitive duplicate`() {
        val validation =
            validateCaseEdit(
                name = "migraines",
                icon = "🤕",
                editingCaseId = null,
                otherActiveCases = listOf(otherCase(id = 1L, name = "Migraines")),
            )

        assertTrue(validation.nameDuplicate)
        assertFalse(validation.isValid)
    }

    @Test
    fun `editing a case does not flag its own unchanged name as a duplicate`() {
        val validation =
            validateCaseEdit(
                name = "Migraines",
                icon = "🤕",
                editingCaseId = 1L,
                otherActiveCases = listOf(otherCase(id = 1L, name = "Migraines")),
            )

        assertFalse(validation.nameDuplicate)
        assertTrue(validation.isValid)
    }

    @Test
    fun `name matching a different case while editing is still a duplicate`() {
        val validation =
            validateCaseEdit(
                name = "Migraines",
                icon = "🤕",
                editingCaseId = 1L,
                otherActiveCases = listOf(otherCase(id = 2L, name = "Migraines")),
            )

        assertTrue(validation.nameDuplicate)
        assertFalse(validation.isValid)
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
            testCase(
                name = "Migraine",
                icon = "🤕",
                logFlow = LogFlow.ONE_TAP,
                durationMode = DurationMode.START_STOP,
                intensityEnabled = true,
            )

        assertEquals(LogFlow.DETAIL_SHEET, case.toUiState().logFlow)
    }

    @Test
    fun `loading an existing case leaves a valid one-tap logFlow alone`() {
        val case =
            testCase(
                name = "Lost my keys",
                icon = "🔑",
                logFlow = LogFlow.ONE_TAP,
                durationMode = DurationMode.NONE,
                intensityEnabled = false,
            )

        assertEquals(LogFlow.ONE_TAP, case.toUiState().logFlow)
    }
}
