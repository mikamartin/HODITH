package com.secondmonday.hodith.ui.logsheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TagInputTest {
    @Test
    fun `tagToAdd trims and returns a genuinely new tag`() {
        assertEquals("coffee", tagToAdd("  coffee  ", existingTags = emptyList()))
    }

    @Test
    fun `tagToAdd returns null for blank input`() {
        assertNull(tagToAdd("   ", existingTags = listOf("coffee")))
    }

    @Test
    fun `tagToAdd returns null when the tag already exists under a different casing`() {
        assertNull(tagToAdd("Coffee", existingTags = listOf("coffee")))
    }

    @Test
    fun `tagToAdd is case-sensitive-input-preserving when accepted`() {
        assertEquals("Tea", tagToAdd("Tea", existingTags = listOf("coffee")))
    }

    @Test
    fun `tagToAdd truncates a tag beyond the max length`() {
        val tag = tagToAdd("a".repeat(TAG_NAME_MAX_LENGTH + 10), existingTags = emptyList())

        assertEquals(TAG_NAME_MAX_LENGTH, tag?.length)
    }

    @Test
    fun `filterTagSuggestions excludes suggestions already selected regardless of casing`() {
        assertEquals(
            emptyList<String>(),
            filterTagSuggestions(suggestions = listOf("Coffee"), selectedTags = listOf("coffee"), query = ""),
        )
    }

    @Test
    fun `filterTagSuggestions with an empty query returns every remaining suggestion`() {
        assertEquals(
            listOf("coffee", "tea"),
            filterTagSuggestions(suggestions = listOf("coffee", "tea"), selectedTags = emptyList(), query = ""),
        )
    }

    @Test
    fun `filterTagSuggestions matches query as a case-insensitive substring`() {
        assertEquals(
            listOf("Coffee"),
            filterTagSuggestions(suggestions = listOf("Coffee", "Tea"), selectedTags = emptyList(), query = "coff"),
        )
    }
}
