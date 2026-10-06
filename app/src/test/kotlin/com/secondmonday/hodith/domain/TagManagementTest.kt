package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.TagEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagManagementTest {
    private val coffee = TagEntity(id = 1, name = "coffee")
    private val office = TagEntity(id = 2, name = "Office")
    private val summaries =
        listOf(
            TagSummary(coffee, eventCount = 3),
            TagSummary(office, eventCount = 0),
            TagSummary(TagEntity(id = 3, name = "Coffee shop"), eventCount = 1),
        )

    @Test
    fun `filter appears only above the threshold`() {
        assertFalse(shouldShowTagFilter(TAG_FILTER_THRESHOLD))
        assertTrue(shouldShowTagFilter(TAG_FILTER_THRESHOLD + 1))
    }

    @Test
    fun `filter matches a substring of the name ignoring case in either direction`() {
        assertEquals(listOf("coffee", "Coffee shop"), filterTagSummaries(summaries, "COFF").map { it.tag.name })
        assertEquals(listOf("coffee", "Office", "Coffee shop"), filterTagSummaries(summaries, "OFF").map { it.tag.name })
    }

    @Test
    fun `filter ignores surrounding whitespace in the query`() {
        assertEquals(listOf("Office"), filterTagSummaries(summaries, "  office  ").map { it.tag.name })
    }

    @Test
    fun `blank filter returns every tag`() {
        assertEquals(summaries, filterTagSummaries(summaries, "   "))
    }

    @Test
    fun `filter with no match returns an empty list`() {
        assertTrue(filterTagSummaries(summaries, "zzz").isEmpty())
    }

    @Test
    fun `rename to a blank name is no change`() {
        assertEquals(TagRenameOutcome.NoChange, classifyTagRename(coffee, "   ", collision = null))
    }

    @Test
    fun `rename to the same spelling is no change`() {
        assertEquals(TagRenameOutcome.NoChange, classifyTagRename(coffee, " coffee ", collision = null))
    }

    @Test
    fun `case-only change on the same tag is a plain rename, not a merge`() {
        assertEquals(TagRenameOutcome.Rename, classifyTagRename(coffee, "Coffee", collision = null))
    }

    @Test
    fun `free name is a plain rename`() {
        assertEquals(TagRenameOutcome.Rename, classifyTagRename(coffee, "espresso", collision = null))
    }

    @Test
    fun `name held by another tag merges into that tag`() {
        assertEquals(TagRenameOutcome.Merge(office), classifyTagRename(coffee, "office", collision = office))
    }

    @Test
    fun `a collision with the tag being renamed itself is not a merge`() {
        assertEquals(TagRenameOutcome.Rename, classifyTagRename(coffee, "Coffee", collision = coffee))
    }
}
