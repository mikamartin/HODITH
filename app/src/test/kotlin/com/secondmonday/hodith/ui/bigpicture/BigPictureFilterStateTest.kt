package com.secondmonday.hodith.ui.bigpicture

import com.secondmonday.hodith.viewmodel.CalendarCase
import org.junit.Assert.assertEquals
import org.junit.Test

class BigPictureFilterStateTest {
    @Test
    fun `isTagVisible shows every event when all tags are selected`() {
        assertEquals(true, isTagVisible(eventTags = listOf("work"), visibleTagNames = setOf("work"), allTagCount = 1))
        assertEquals(true, isTagVisible(eventTags = emptyList(), visibleTagNames = setOf("work"), allTagCount = 1))
    }

    @Test
    fun `isTagVisible shows only untagged events when zero tags are selected`() {
        assertEquals(true, isTagVisible(eventTags = emptyList(), visibleTagNames = emptySet(), allTagCount = 2))
        assertEquals(false, isTagVisible(eventTags = listOf("work"), visibleTagNames = emptySet(), allTagCount = 2))
    }

    @Test
    fun `isTagVisible with a partial selection matches only events carrying a selected tag`() {
        assertEquals(
            true,
            isTagVisible(eventTags = listOf("work"), visibleTagNames = setOf("work"), allTagCount = 2),
        )
        assertEquals(
            false,
            isTagVisible(eventTags = listOf("weekend"), visibleTagNames = setOf("work"), allTagCount = 2),
        )
    }

    @Test
    fun `isTagVisible with a partial selection still hides untagged events`() {
        assertEquals(
            false,
            isTagVisible(eventTags = emptyList(), visibleTagNames = setOf("work"), allTagCount = 2),
        )
    }

    private val coffee = CalendarCase(id = 1L, icon = "☕", name = "Coffee")
    private val tea = CalendarCase(id = 2L, icon = "🫖", name = "Tea")
    private val allCases = listOf(coffee, tea)

    @Test
    fun `bigPictureCaseLegend is AllSelected when every case is selected`() {
        assertEquals(CaseLegendState.AllSelected, bigPictureCaseLegend(allCases, setOf(1L, 2L)))
    }

    @Test
    fun `bigPictureCaseLegend is NoneSelected when zero cases are selected`() {
        assertEquals(CaseLegendState.NoneSelected, bigPictureCaseLegend(allCases, emptySet()))
    }

    @Test
    fun `bigPictureCaseLegend itemizes a partial selection, preserving case order`() {
        assertEquals(CaseLegendState.Some(listOf(coffee)), bigPictureCaseLegend(allCases, setOf(1L)))
    }

    private val allTags = listOf("work", "weekend")

    @Test
    fun `bigPictureTagLegend is AllSelected when every tag is selected`() {
        assertEquals(TagLegendState.AllSelected, bigPictureTagLegend(allTags, setOf("work", "weekend")))
    }

    @Test
    fun `bigPictureTagLegend is UntaggedOnly when zero tags are selected`() {
        assertEquals(TagLegendState.UntaggedOnly, bigPictureTagLegend(allTags, emptySet()))
    }

    @Test
    fun `bigPictureTagLegend itemizes a partial selection, preserving tag order`() {
        assertEquals(TagLegendState.Some(listOf("work")), bigPictureTagLegend(allTags, setOf("work")))
    }

    @Test
    fun `bigPictureTagLegend is AllSelected, never UntaggedOnly, when no tags exist at all`() {
        assertEquals(TagLegendState.AllSelected, bigPictureTagLegend(emptyList(), emptySet()))
    }
}
