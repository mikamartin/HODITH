package com.secondmonday.hodith.ui.casedetail

import com.secondmonday.hodith.data.ExpectedPer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchEditorSheetTest {
    @Test
    fun `windowDaysFor scales the short preset with the rate`() {
        assertEquals(7, windowDaysFor(ExpectedPer.DAY, WindowPreset.SHORT))
        assertEquals(14, windowDaysFor(ExpectedPer.WEEK, WindowPreset.SHORT))
        assertEquals(60, windowDaysFor(ExpectedPer.MONTH, WindowPreset.SHORT))
        assertEquals(120, windowDaysFor(ExpectedPer.QUARTER, WindowPreset.SHORT))
    }

    @Test
    fun `windowDaysFor scales the long preset with the rate`() {
        assertEquals(14, windowDaysFor(ExpectedPer.DAY, WindowPreset.LONG))
        assertEquals(30, windowDaysFor(ExpectedPer.WEEK, WindowPreset.LONG))
        assertEquals(90, windowDaysFor(ExpectedPer.MONTH, WindowPreset.LONG))
        assertEquals(180, windowDaysFor(ExpectedPer.QUARTER, WindowPreset.LONG))
    }

    @Test
    fun `windowDaysFor has no fixed days for the custom preset`() {
        ExpectedPer.entries.forEach { assertNull(windowDaysFor(it, WindowPreset.CUSTOM)) }
    }

    @Test
    fun `the long preset is always longer than the short one`() {
        ExpectedPer.entries.forEach { per ->
            assertTrue(windowDaysFor(per, WindowPreset.LONG)!! > windowDaysFor(per, WindowPreset.SHORT)!!)
        }
    }

    @Test
    fun `windowPresetFor round-trips each preset's own days`() {
        ExpectedPer.entries.forEach { per ->
            assertEquals(WindowPreset.SHORT, windowPresetFor(windowDaysFor(per, WindowPreset.SHORT), per))
            assertEquals(WindowPreset.LONG, windowPresetFor(windowDaysFor(per, WindowPreset.LONG), per))
        }
    }

    @Test
    fun `windowPresetFor defaults a null window to the short preset`() {
        assertEquals(WindowPreset.SHORT, windowPresetFor(null, ExpectedPer.WEEK))
    }

    @Test
    fun `windowPresetFor reads a window matching no preset for the rate as custom`() {
        assertEquals(WindowPreset.CUSTOM, windowPresetFor(45, ExpectedPer.WEEK))
        // 14 is the Week short preset but not the Month one, so the same days read differently per rate.
        assertEquals(WindowPreset.SHORT, windowPresetFor(14, ExpectedPer.WEEK))
        assertEquals(WindowPreset.CUSTOM, windowPresetFor(14, ExpectedPer.MONTH))
    }

    @Test
    fun `minIntensityAfterToggle starts at the lowest level when switched on`() {
        assertEquals(1, minIntensityAfterToggle(checked = true))
    }

    @Test
    fun `minIntensityAfterToggle drops the filter when switched off`() {
        assertNull(minIntensityAfterToggle(checked = false))
    }

    @Test
    fun `isIntensityLevelHighlighted highlights the selected level and everything above it`() {
        val highlighted = (1..5).filter { isIntensityLevelHighlighted(it, selected = 3) }
        assertEquals(listOf(3, 4, 5), highlighted)
    }

    @Test
    fun `isIntensityLevelHighlighted highlights every level when the lowest is selected`() {
        assertEquals((1..5).toList(), (1..5).filter { isIntensityLevelHighlighted(it, selected = 1) })
    }

    @Test
    fun `isIntensityLevelHighlighted highlights only the top level when it is selected`() {
        assertEquals(listOf(5), (1..5).filter { isIntensityLevelHighlighted(it, selected = 5) })
        assertFalse(isIntensityLevelHighlighted(4, selected = 5))
    }
}
