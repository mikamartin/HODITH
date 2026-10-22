package com.secondmonday.hodith.domain

import com.secondmonday.hodith.testsupport.millisAtDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTest {
    // ---- computeCadenceWindow ----

    @Test
    fun `computeCadenceWindow is null below the minimum gap count`() {
        assertNull(computeCadenceWindow(listOf(5L)))
    }

    @Test
    fun `computeCadenceWindow fires at exactly the minimum gap count`() {
        assertTrue(computeCadenceWindow(listOf(5L, 5L)) != null)
    }

    @Test
    fun `computeCadenceWindow uses the median, not the mean, for an odd-count gap list`() {
        // 2, 2, 2, 2, 20 -- mean is 5.6, median is 2.0. A burst-then-one-long-silence shape.
        val window = computeCadenceWindow(listOf(2L, 2L, 2L, 2L, 20L))

        assertEquals(2.0, window?.typicalIntervalDays ?: -1.0, 0.0001)
    }

    @Test
    fun `computeCadenceWindow uses the median, not the mean, for an even-count gap list`() {
        // 2, 2, 2, 2 -- mean and median agree here; pair with the next test's unequal case.
        val window = computeCadenceWindow(listOf(2L, 2L, 2L, 2L))

        assertEquals(2.0, window?.typicalIntervalDays ?: -1.0, 0.0001)
    }

    @Test
    fun `computeCadenceWindow averages the two middle values for an even-count gap list`() {
        // 1, 1, 9, 9 -- sorted middle pair (1, 9) averages to 5.0; a plain mean would also be 5.0
        // here, so this pins the even-count averaging branch itself, not just "isn't the mean".
        val window = computeCadenceWindow(listOf(9L, 1L, 1L, 9L))

        assertEquals(5.0, window?.typicalIntervalDays ?: -1.0, 0.0001)
    }

    @Test
    fun `computeCadenceWindow is robust to one huge outlier that would skew a mean`() {
        // 2, 2, 2, 2, 2, 1000 -- mean would be ~168.3; median stays 2.0.
        val gaps = List(5) { 2L } + 1000L
        val window = computeCadenceWindow(gaps)

        assertEquals(2.0, window?.typicalIntervalDays ?: -1.0, 0.0001)
        assertTrue((gaps.average()) > 100.0) // sanity check: the mean really would have been skewed
    }

    @Test
    fun `computeCadenceWindow's recency threshold is set by the floor when the typical interval is small`() {
        // Typical interval 1.0 day; multiplier * typical = 2.5, under the 3.0 floor.
        val window = computeCadenceWindow(listOf(1L, 1L))

        assertEquals(1.0, window?.typicalIntervalDays ?: -1.0, 0.0001)
        assertEquals(CADENCE_RECENCY_FLOOR_DAYS, window?.recencyThresholdDays ?: -1.0, 0.0001)
    }

    @Test
    fun `computeCadenceWindow's recency threshold is set by the multiplier when the typical interval is large`() {
        // Typical interval 10.0 days; multiplier * typical = 25.0, well over the 3.0 floor.
        val window = computeCadenceWindow(listOf(10L, 10L))

        assertEquals(10.0, window?.typicalIntervalDays ?: -1.0, 0.0001)
        assertEquals(10.0 * CADENCE_RECENCY_MULTIPLIER, window?.recencyThresholdDays ?: -1.0, 0.0001)
    }

    // ---- computeCadenceWindowFromTimestamps ----

    @Test
    fun `computeCadenceWindowFromTimestamps is null below the minimum timestamp count`() {
        assertNull(computeCadenceWindowFromTimestamps(listOf(millisAtDay(0))))
    }

    @Test
    fun `computeCadenceWindowFromTimestamps fires at exactly the minimum timestamp count`() {
        val timestamps = listOf(millisAtDay(0), millisAtDay(5), millisAtDay(10))

        assertTrue(computeCadenceWindowFromTimestamps(timestamps) != null)
    }

    @Test
    fun `computeCadenceWindowFromTimestamps gives the same result regardless of input order`() {
        val sorted = listOf(millisAtDay(0), millisAtDay(3), millisAtDay(13))
        val shuffled = listOf(millisAtDay(13), millisAtDay(0), millisAtDay(3))

        assertEquals(computeCadenceWindowFromTimestamps(sorted), computeCadenceWindowFromTimestamps(shuffled))
    }

    @Test
    fun `computeCadenceWindowFromTimestamps diffs timestamps into the same gaps computeCadenceWindow would use directly`() {
        val timestamps = listOf(millisAtDay(0), millisAtDay(2), millisAtDay(6), millisAtDay(12))

        val fromTimestamps = computeCadenceWindowFromTimestamps(timestamps)
        val fromGaps = computeCadenceWindow(listOf(2L, 4L, 6L))

        assertEquals(fromGaps, fromTimestamps)
    }

    // ---- isWithinCadenceRecency ----

    @Test
    fun `isWithinCadenceRecency is true exactly at the recency threshold`() {
        val window = CadenceWindow(typicalIntervalDays = 5.0, recencyThresholdDays = 12.0)

        assertTrue(isWithinCadenceRecency(window, daysSinceLatest = 12L))
    }

    @Test
    fun `isWithinCadenceRecency is false just past the recency threshold`() {
        val window = CadenceWindow(typicalIntervalDays = 5.0, recencyThresholdDays = 12.0)

        assertTrue(isWithinCadenceRecency(window, daysSinceLatest = 12L))
        assertTrue(!isWithinCadenceRecency(window, daysSinceLatest = 13L))
    }
}
