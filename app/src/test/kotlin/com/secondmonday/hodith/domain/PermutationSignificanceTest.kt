package com.secondmonday.hodith.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermutationSignificanceTest {
    // ---- permutationPValue ----

    @Test
    fun `permutationPValue is 1 when every permuted statistic is at least as extreme as the observed one`() {
        // A permutedStatistic that always returns 0 can never be less extreme than an observed 0.
        val pValue = permutationPValue(observedStatistic = 0.0, iterations = 100, seed = 1L) { 0.0 }

        assertEquals(1.0, pValue, 0.0001)
    }

    @Test
    fun `permutationPValue is 0 when no permuted statistic is ever as extreme as the observed one`() {
        val pValue = permutationPValue(observedStatistic = 100.0, iterations = 100, seed = 1L) { 0.0 }

        assertEquals(0.0, pValue, 0.0001)
    }

    @Test
    fun `permutationPValue is deterministic for the same seed`() {
        val first = permutationPValue(observedStatistic = 0.5, iterations = 200, seed = 42L) { it.nextDouble() }
        val second = permutationPValue(observedStatistic = 0.5, iterations = 200, seed = 42L) { it.nextDouble() }

        assertEquals(first, second, 0.0)
    }

    @Test
    fun `permutationPValue differs across seeds`() {
        val first = permutationPValue(observedStatistic = 0.5, iterations = 200, seed = 1L) { it.nextDouble() }
        val second = permutationPValue(observedStatistic = 0.5, iterations = 200, seed = 2L) { it.nextDouble() }

        assertNotEquals(first, second)
    }

    @Test
    fun `permutationPValue is exactly the fraction of permuted statistics at least as extreme, not an off-by-one`() {
        // Deterministic, non-random permutedStatistic: 4 of 10 calls are as extreme as observed (10.0
        // vs 5.0), 6 aren't -- pins the exact count/iterations division rather than just its endpoints.
        var call = 0
        val pValue =
            permutationPValue(observedStatistic = 5.0, iterations = 10, seed = 1L) {
                (if (call++ < 4) 10.0 else 0.0)
            }

        assertEquals(0.4, pValue, 0.0001)
    }

    @Test
    fun `permutationPValue calls permutedStatistic exactly iterations times`() {
        var callCount = 0
        permutationPValue(observedStatistic = 1.0, iterations = 37, seed = 1L) {
            callCount++
            0.0
        }

        assertEquals(37, callCount)
    }

    // ---- labelShufflePValue ----

    @Test
    fun `labelShufflePValue preserves each group's size across every shuffle`() {
        // A statistic that reports groupA's size: since the shuffle always re-splits the pooled
        // values into the same sizeA/sizeB, every permuted call must reproduce the same size split
        // as the original groups -- a structural guarantee, not a statistical one, so this fails hard
        // (not just "usually") if a future refactor breaks size preservation.
        val groupA = List(10) { it.toDouble() }
        val groupB = List(20) { it.toDouble() + 100 }

        val pValue = labelShufflePValue(groupA, groupB, iterations = 500, seed = 1L, statistic = { a, _ -> a.size.toDouble() })

        assertEquals(1.0, pValue, 0.0001)
    }

    @Test
    fun `labelShufflePValue is high for two groups with no real difference`() {
        // Identical values in both groups -- every shuffle reproduces the same (zero) statistic, so
        // nothing can look more extreme than the observed zero difference.
        val groupA = List(20) { 5.0 }
        val groupB = List(20) { 5.0 }

        val pValue = labelShufflePValue(groupA, groupB, iterations = 200, seed = 1L)

        assertEquals(1.0, pValue, 0.0001)
    }

    @Test
    fun `labelShufflePValue is low for two groups with a stark, low-variance difference`() {
        val groupA = List(30) { 1.0 }
        val groupB = List(30) { 10.0 }

        val pValue = labelShufflePValue(groupA, groupB, iterations = 1000, seed = 1L)

        assertTrue(pValue < 0.05)
    }

    @Test
    fun `labelShufflePValue is deterministic for the same seed`() {
        val groupA = listOf(1.0, 2.0, 3.0, 4.0, 5.0)
        val groupB = listOf(6.0, 7.0, 8.0, 9.0, 10.0)

        val first = labelShufflePValue(groupA, groupB, iterations = 200, seed = 7L)
        val second = labelShufflePValue(groupA, groupB, iterations = 200, seed = 7L)

        assertEquals(first, second, 0.0)
    }

    // ---- relativeDifferenceInMeans ----

    @Test
    fun `relativeDifferenceInMeans is positive when b's mean exceeds a's`() {
        val result = relativeDifferenceInMeans(listOf(2.0, 2.0), listOf(3.0, 3.0))

        assertEquals(0.5, result, 0.0001)
    }

    @Test
    fun `relativeDifferenceInMeans is negative when b's mean is below a's`() {
        val result = relativeDifferenceInMeans(listOf(4.0, 4.0), listOf(2.0, 2.0))

        assertEquals(-0.5, result, 0.0001)
    }

    @Test
    fun `relativeDifferenceInMeans is 0 when both means are 0`() {
        val result = relativeDifferenceInMeans(listOf(0.0, 0.0), listOf(0.0, 0.0))

        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun `relativeDifferenceInMeans is positive infinity when a's mean is 0 but b's is not`() {
        val result = relativeDifferenceInMeans(listOf(0.0, 0.0), listOf(1.0, 1.0))

        assertEquals(Double.POSITIVE_INFINITY, result, 0.0)
    }

    // ---- permutationSeedFor ----

    @Test
    fun `permutationSeedFor is deterministic for the same inputs`() {
        val first = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)
        val second = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)

        assertEquals(first, second)
    }

    @Test
    fun `permutationSeedFor differs when caseId differs`() {
        val first = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)
        val second = permutationSeedFor(caseId = 2L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)

        assertNotEquals(first, second)
    }

    @Test
    fun `permutationSeedFor differs when tagName differs`() {
        val first = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)
        val second = permutationSeedFor(caseId = 1L, tagName = "decaf", outcome = TagOutcome.DURATION, eventCount = 40)

        assertNotEquals(first, second)
    }

    @Test
    fun `permutationSeedFor differs when outcome differs`() {
        val first = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)
        val second = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.INTENSITY, eventCount = 40)

        assertNotEquals(first, second)
    }

    @Test
    fun `permutationSeedFor differs when eventCount differs`() {
        val first = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 40)
        val second = permutationSeedFor(caseId = 1L, tagName = "aura", outcome = TagOutcome.DURATION, eventCount = 41)

        assertNotEquals(first, second)
    }

    // ---- timelineShufflePValue ----

    @Test
    fun `timelineShufflePValue preserves the value list's size across every shuffle`() {
        // A statistic that reports the shuffled list's size: since shuffling never changes how many
        // values there are, every permuted call must reproduce the original size -- a structural
        // guarantee, not a statistical one.
        val values = List(15) { it.toLong() }

        val pValue = timelineShufflePValue(values, iterations = 500, seed = 1L, statistic = { it.size.toDouble() })

        assertEquals(1.0, pValue, 0.0001)
    }

    @Test
    fun `timelineShufflePValue is high for a statistic no reordering can beat`() {
        // Every permutation of the same multiset has the same sum, so a sum-based statistic is
        // identical before and after any shuffle -- nothing can look more extreme than observed.
        val values = listOf(1L, 2L, 3L, 4L, 5L)

        val pValue = timelineShufflePValue(values, iterations = 200, seed = 1L, statistic = { it.sum().toDouble() })

        assertEquals(1.0, pValue, 0.0001)
    }

    @Test
    fun `timelineShufflePValue is low when only the original order achieves an extreme statistic`() {
        // A statistic that's only extreme for the exact original (sorted) order -- almost every
        // shuffle scrambles it, so only a vanishing share of permutations should match or beat it.
        val values = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)
        val sortedStatistic: (List<Long>) -> Double = { list -> if (list == list.sorted()) 100.0 else 0.0 }

        val pValue = timelineShufflePValue(values, iterations = 1000, seed = 1L, statistic = sortedStatistic)

        assertTrue(pValue < 0.05)
    }

    @Test
    fun `timelineShufflePValue is deterministic for the same seed`() {
        val values = listOf(1L, 2L, 3L, 4L, 5L)

        val first = timelineShufflePValue(values, iterations = 200, seed = 7L, statistic = { it.sum().toDouble() })
        val second = timelineShufflePValue(values, iterations = 200, seed = 7L, statistic = { it.sum().toDouble() })

        assertEquals(first, second, 0.0)
    }

    // ---- timelineShuffleSeedFor ----

    @Test
    fun `timelineShuffleSeedFor is deterministic for the same inputs`() {
        val first = timelineShuffleSeedFor(caseId = 1L, gapCount = 20)
        val second = timelineShuffleSeedFor(caseId = 1L, gapCount = 20)

        assertEquals(first, second)
    }

    @Test
    fun `timelineShuffleSeedFor differs when caseId differs`() {
        val first = timelineShuffleSeedFor(caseId = 1L, gapCount = 20)
        val second = timelineShuffleSeedFor(caseId = 2L, gapCount = 20)

        assertNotEquals(first, second)
    }

    @Test
    fun `timelineShuffleSeedFor differs when gapCount differs`() {
        val first = timelineShuffleSeedFor(caseId = 1L, gapCount = 20)
        val second = timelineShuffleSeedFor(caseId = 1L, gapCount = 21)

        assertNotEquals(first, second)
    }

    // ---- trendSlopeSeedFor ----

    @Test
    fun `trendSlopeSeedFor is deterministic for the same inputs`() {
        val first = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)
        val second = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)

        assertEquals(first, second)
    }

    @Test
    fun `trendSlopeSeedFor differs when caseId differs`() {
        val first = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)
        val second = trendSlopeSeedFor(caseId = 2L, outcome = TagOutcome.DURATION, sampleCount = 20)

        assertNotEquals(first, second)
    }

    @Test
    fun `trendSlopeSeedFor differs when outcome differs`() {
        val first = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)
        val second = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.INTENSITY, sampleCount = 20)

        assertNotEquals(first, second)
    }

    @Test
    fun `trendSlopeSeedFor differs when sampleCount differs`() {
        val first = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)
        val second = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 21)

        assertNotEquals(first, second)
    }

    // ---- timeOfDaySplitSeedFor ----

    @Test
    fun `timeOfDaySplitSeedFor is deterministic for the same inputs`() {
        val first = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 40)
        val second = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 40)

        assertEquals(first, second)
    }

    @Test
    fun `timeOfDaySplitSeedFor differs when caseId differs`() {
        val first = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 40)
        val second = timeOfDaySplitSeedFor(caseId = 2L, outcome = TagOutcome.DURATION, sampleCount = 40)

        assertNotEquals(first, second)
    }

    @Test
    fun `timeOfDaySplitSeedFor differs when outcome differs`() {
        val first = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 40)
        val second = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.INTENSITY, sampleCount = 40)

        assertNotEquals(first, second)
    }

    @Test
    fun `timeOfDaySplitSeedFor differs when sampleCount differs`() {
        val first = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 40)
        val second = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 41)

        assertNotEquals(first, second)
    }

    @Test
    fun `trendSlopeSeedFor and timeOfDaySplitSeedFor differ from each other for the same inputs`() {
        // Otherwise a Case whose trend-slope and time-of-day-split candidates for the same outcome
        // happen to share a sample count would draw the identical permutation shuffle for both.
        val trendSlope = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)
        val timeOfDaySplit = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)

        assertNotEquals(trendSlope, timeOfDaySplit)
    }

    // ---- tagTimingSeedFor ----

    @Test
    fun `tagTimingSeedFor is deterministic for the same inputs`() {
        val first = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)
        val second = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)

        assertEquals(first, second)
    }

    @Test
    fun `tagTimingSeedFor differs when caseId differs`() {
        val first = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)
        val second = tagTimingSeedFor(caseId = 2L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)

        assertNotEquals(first, second)
    }

    @Test
    fun `tagTimingSeedFor differs when tagName differs`() {
        val first = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)
        val second = tagTimingSeedFor(caseId = 1L, tagName = "chore", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)

        assertNotEquals(first, second)
    }

    @Test
    fun `tagTimingSeedFor differs when dimension differs`() {
        val first = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)
        val second = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.WEEKDAY, sampleCount = 20)

        assertNotEquals(first, second)
    }

    @Test
    fun `tagTimingSeedFor differs when sampleCount differs`() {
        val first = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)
        val second = tagTimingSeedFor(caseId = 1L, tagName = "focus", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 21)

        assertNotEquals(first, second)
    }

    @Test
    fun `tagTimingSeedFor differs from trendSlopeSeedFor and timeOfDaySplitSeedFor for a colliding shape`() {
        // tagTimingSeedFor has no outcome to key on, but shares the same caseId/sampleCount shape
        // with the outcome-keyed seeds -- confirms its own trailing +2 discriminator keeps it from
        // colliding with trendSlopeSeedFor's +0 / timeOfDaySplitSeedFor's +1 (PermutationSignificance.kt's
        // own doc comment on the discriminator convention), the same collision class T6's own
        // trendSlopeSeedFor/timeOfDaySplitSeedFor tests were added to catch.
        val tagTiming = tagTimingSeedFor(caseId = 1L, tagName = "x", dimension = TagTimingDimension.TIME_OF_DAY, sampleCount = 20)
        val trendSlope = trendSlopeSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)
        val timeOfDaySplit = timeOfDaySplitSeedFor(caseId = 1L, outcome = TagOutcome.DURATION, sampleCount = 20)

        assertNotEquals(tagTiming, trendSlope)
        assertNotEquals(tagTiming, timeOfDaySplit)
    }
}
