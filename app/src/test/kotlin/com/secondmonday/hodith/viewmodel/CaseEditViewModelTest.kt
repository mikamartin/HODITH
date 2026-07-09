package com.secondmonday.hodith.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaseEditViewModelTest {
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
}
