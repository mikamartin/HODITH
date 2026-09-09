package com.secondmonday.hodith.data

import com.secondmonday.hodith.data.BigPictureDetailField.DURATION
import com.secondmonday.hodith.data.BigPictureDetailField.INTENSITY
import com.secondmonday.hodith.data.BigPictureDetailField.NOTES
import com.secondmonday.hodith.data.BigPictureDetailField.TAGS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers [BigPictureDetail]'s toggle helpers and its string round-trip (spec §9). */
class BigPictureDetailTest {
    /** All 16 notes/tags/duration/intensity combinations. */
    private val allValues: List<BigPictureDetail> =
        listOf(false, true).flatMap { n ->
            listOf(false, true).flatMap { t ->
                listOf(false, true).flatMap { d ->
                    listOf(false, true).map { i -> BigPictureDetail(n, t, d, i) }
                }
            }
        }

    // ---- DEFAULT ----

    @Test
    fun `DEFAULT is notes, tags and duration on with intensity off`() {
        assertEquals(BigPictureDetail(notes = true, tags = true, duration = true, intensity = false), BigPictureDetail.DEFAULT)
    }

    // ---- has / with ----

    @Test
    fun `has reads the field matching the enum entry`() {
        val detail = BigPictureDetail(notes = true, tags = false, duration = true, intensity = false)
        assertTrue(detail.has(NOTES))
        assertFalse(detail.has(TAGS))
        assertTrue(detail.has(DURATION))
        assertFalse(detail.has(INTENSITY))
    }

    @Test
    fun `with flips exactly one field and leaves the other three`() {
        val before = BigPictureDetail.DEFAULT
        val after = before.with(INTENSITY, true)

        assertTrue(after.intensity)
        assertEquals(before.notes, after.notes)
        assertEquals(before.tags, after.tags)
        assertEquals(before.duration, after.duration)
    }

    @Test
    fun `with is a no-op when the field already has that value`() {
        assertEquals(BigPictureDetail.DEFAULT, BigPictureDetail.DEFAULT.with(NOTES, true))
    }

    // ---- serialize / parse round-trip ----

    @Test
    fun `serialize then parse round-trips for every combination`() {
        allValues.forEach { value ->
            assertEquals("round-trip failed for $value", value, BigPictureDetail.parse(value.serialize()))
        }
    }

    @Test
    fun `serialize lists enabled tokens in enum order`() {
        assertEquals("notes,tags,duration", BigPictureDetail(notes = true, tags = true, duration = true, intensity = false).serialize())
        assertEquals("tags,intensity", BigPictureDetail(notes = false, tags = true, duration = false, intensity = true).serialize())
    }

    @Test
    fun `serialize of the all-off value is the empty string`() {
        assertEquals("", BigPictureDetail.ALL_OFF.serialize())
    }

    // ---- parse edge cases ----

    @Test
    fun `parse of null is DEFAULT - never stored`() {
        assertEquals(BigPictureDetail.DEFAULT, BigPictureDetail.parse(null))
    }

    @Test
    fun `parse of the empty string is all-off - an explicit choice, distinct from null`() {
        assertEquals(BigPictureDetail.ALL_OFF, BigPictureDetail.parse(""))
    }

    @Test
    fun `parse of whitespace only is all-off`() {
        assertEquals(BigPictureDetail.ALL_OFF, BigPictureDetail.parse("   "))
    }

    @Test
    fun `parse ignores unknown and duplicate tokens, keeping the valid ones`() {
        assertEquals(
            BigPictureDetail(notes = true, tags = true, duration = false, intensity = false),
            BigPictureDetail.parse("notes,bogus,tags,notes,streak"),
        )
    }

    @Test
    fun `parse is order-independent`() {
        assertEquals(BigPictureDetail.parse("notes,tags"), BigPictureDetail.parse("tags,notes"))
    }

    @Test
    fun `parse tolerates surrounding whitespace on a token`() {
        assertEquals(BigPictureDetail.parse("notes,duration"), BigPictureDetail.parse(" notes , duration "))
    }

    @Test
    fun `parse is case-sensitive - a mis-cased token is dropped`() {
        assertEquals(BigPictureDetail.ALL_OFF, BigPictureDetail.parse("Notes,TAGS"))
    }
}
