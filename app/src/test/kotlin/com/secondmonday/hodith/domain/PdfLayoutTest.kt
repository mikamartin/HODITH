package com.secondmonday.hodith.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `paginatePdfItems` is PDF export's page-splitting math (feature/pdf-export) - pure arithmetic,
 * no Android dependency, so the page count a History or Insights PDF ends up with is exercised
 * directly rather than only through a generated file.
 */
class PdfLayoutTest {
    @Test
    fun `empty items still yields one empty page`() {
        val pages = paginatePdfItems(emptyList<Int>(), firstPageCapacity = 5, continuationCapacity = 10)

        assertEquals(listOf(emptyList<Int>()), pages)
    }

    @Test
    fun `everything fits on the first page when it's under capacity`() {
        val pages = paginatePdfItems((1..3).toList(), firstPageCapacity = 5, continuationCapacity = 10)

        assertEquals(listOf(listOf(1, 2, 3)), pages)
    }

    @Test
    fun `exactly filling the first page's capacity needs no second page`() {
        val pages = paginatePdfItems((1..5).toList(), firstPageCapacity = 5, continuationCapacity = 10)

        assertEquals(listOf(listOf(1, 2, 3, 4, 5)), pages)
    }

    @Test
    fun `one item over the first page's capacity starts a continuation page`() {
        val pages = paginatePdfItems((1..6).toList(), firstPageCapacity = 5, continuationCapacity = 10)

        assertEquals(listOf(listOf(1, 2, 3, 4, 5), listOf(6)), pages)
    }

    @Test
    fun `continuation pages use the larger continuation capacity, not the first page's`() {
        val pages = paginatePdfItems((1..20).toList(), firstPageCapacity = 5, continuationCapacity = 10)

        assertEquals(
            listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 10, 11, 12, 13, 14, 15), listOf(16, 17, 18, 19, 20)),
            pages,
        )
    }

    @Test
    fun `a zero first-page capacity still makes progress instead of looping forever`() {
        val pages = paginatePdfItems((1..3).toList(), firstPageCapacity = 0, continuationCapacity = 2)

        assertEquals(listOf(listOf(1), listOf(2, 3)), pages)
    }
}
