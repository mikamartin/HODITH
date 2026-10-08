package com.secondmonday.hodith.ui.voice

import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TrendFinding
import com.secondmonday.hodith.domain.TrendFindingKind
import com.secondmonday.hodith.domain.TrendReliability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.declaredMemberProperties

/**
 * Walks the [Voice] interface by reflection rather than hand-listing every key, so coverage can't
 * silently rot as keys are added (QA audit: the old hand-written list missed 68 of 291 keys).
 */
class VoiceTest {
    private val voices = listOf(PlainVoice, IntenseVoice, BrightVoice)

    @Test
    fun `every voice has a non-blank string for every key`() {
        for (voice in voices) {
            for (property in stringProperties) {
                val value = property.get(voice) as String
                assertTrue("${property.name} is blank for $voice", value.isNotBlank())
            }
            for (function in stringFunctions) {
                for (args in argumentCombinations(function)) {
                    val value = function.call(voice, *args.toTypedArray()) as String
                    assertTrue("${function.name}$args is blank for $voice", value.isNotBlank())
                }
            }
        }
    }

    @Test
    fun `trend comparison lines differ in every voice rather than falling back to one shared copy`() {
        assertEquals(3, voices.map { it.trendChanceBaselineLabel }.toSet().size)
        assertEquals(3, voices.map { it.trendRecurrenceDetailLabel("3 days") }.toSet().size)
    }

    @Test
    fun `tag combo headline names every tag in the combo in all three voices`() {
        val combo =
            TrendFinding(
                TrendFindingKind.TAG_COMBO,
                ShiftDirection.UP,
                TrendReliability.HINT,
                5,
                5.0,
                8.0,
                tagNames = listOf("Coffee", "Walk"),
                latestEvidenceAt = 0L,
            )
        for (voice in voices) {
            val headline = voice.insightsTrendHeadline(combo, bucketPhrase = "")
            assertTrue("$voice headline should name #Coffee: $headline", headline.contains("#Coffee"))
            assertTrue("$voice headline should name #Walk: $headline", headline.contains("#Walk"))
        }
    }

    @Test
    fun `historySummaryLine leads with the rate when there is one and reads the count line without it`() {
        for (voice in listOf(PlainVoice, IntenseVoice, BrightVoice)) {
            val withRate = voice.historySummaryLine(rate = "2.1/week", eventCount = 31, observedDays = 94)
            val withoutRate = voice.historySummaryLine(rate = null, eventCount = 31, observedDays = 94)

            assertTrue(withRate.startsWith("2.1/week · "))
            assertEquals(withoutRate, withRate.removePrefix("2.1/week · "))
        }
    }

    @Test
    fun `no per-voice key returns an identical string across all three voices`() {
        // Structural keys (interface `get() = "literal"` defaults, and defaulted `fun`s built only
        // from those) are meant to be identical and are excluded automatically: they're non-abstract
        // in the interface, so `isAbstract` is false for them but true for every key each voice must
        // supply itself.
        val violations = mutableListOf<String>()

        for (property in stringProperties.filter { it.isAbstract }) {
            val values = voices.map { property.get(it) as String }
            if (values.toSet().size == 1) {
                violations += "${property.name} is identical across all voices: \"${values.first()}\""
            }
        }
        for (function in stringFunctions.filter { it.isAbstract }) {
            for (args in argumentCombinations(function)) {
                val values = voices.map { function.call(it, *args.toTypedArray()) as String }
                if (values.toSet().size == 1) {
                    violations += "${function.name}$args is identical across all voices: \"${values.first()}\""
                }
            }
        }

        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `watchesWindowPresetLabel reads short windows in days, longer ones in months, and 90 as Quarter`() {
        val expected =
            mapOf(7 to "7 days", 14 to "14 days", 30 to "30 days", 60 to "2mo", 90 to "Quarter", 120 to "4mo", 180 to "6mo")
        for (voice in voices) {
            expected.forEach { (days, label) -> assertEquals("$voice: $days days", label, voice.watchesWindowPresetLabel(days)) }
        }
    }

    @Test
    fun `watchComparisonLabel distinguishes every comparison band`() {
        for (voice in voices) {
            val labels = ComparisonBand.entries.map { voice.watchComparisonLabel(it, daysActive = false) }
            assertEquals("$voice: every band should read distinctly", labels.size, labels.toSet().size)
        }
    }

    @Test
    fun `shareHistoryRangeNote joins both formatted bounds with a dash`() {
        for (voice in voices) {
            assertEquals("Sep 3 – Sep 20", voice.shareHistoryRangeNote("Sep 3", "Sep 20"))
        }
    }

    @Test
    fun `Square share copy states the voice's own event noun and agrees with the count`() {
        val expected = mapOf(PlainVoice to ("event" to "events"), IntenseVoice to ("entry" to "entries"), BrightVoice to ("log" to "logs"))
        for ((voice, nouns) in expected) {
            assertEquals(nouns.first, voice.shareSquareEventNoun(1))
            assertEquals(nouns.second, voice.shareSquareEventNoun(0))
            assertEquals(nouns.second, voice.shareSquareEventNoun(31))
            assertEquals("94d observed · 31 ${nouns.second}", voice.shareSquareObservedLine(94, 31))
            assertEquals("1d observed · 1 ${nouns.first}", voice.shareSquareObservedLine(1, 1))
            assertEquals("12d observed", voice.shareSquareObservedDays(12))
        }
    }

    @Test
    fun `Square share structural labels read the same in every voice`() {
        for (voice in voices) {
            assertEquals("Min", voice.shareStatMinLabel)
            assertEquals("Avg", voice.shareStatAvgLabel)
            assertEquals("Max", voice.shareStatMaxLabel)
            assertEquals("/week", voice.shareRatePerWeekUnit)
            assertEquals("average 3 of 5", voice.shareSquareIntensityAverage("3"))
            assertEquals("Name on card", voice.shareNameFieldLabel)
            assertEquals("Streaks", voice.shareStreaksTitle)
        }
    }

    @Test
    fun `insights gaps labels spell out gap and keep the streak labels`() {
        for (voice in voices) {
            assertEquals("Min gap", voice.insightsGapsMinLabel)
            assertEquals("Avg gap", voice.insightsGapsAvgLabel)
            assertEquals("Max gap", voice.insightsGapsMaxLabel)
            assertEquals("Longest streak", voice.insightsStreakLongestLabel)
            assertEquals("Average streak", voice.insightsStreakAverageLabel)
        }
    }

    @Test
    fun `share gaps and duration titles name the panels the way the cards do`() {
        for (voice in voices) {
            assertEquals("Gaps", voice.shareGapsTitle)
            assertEquals("Duration", voice.shareDurationTitle)
        }
    }

    @Test
    fun `Square share trend and quiet phrases carry their values`() {
        for (voice in voices) {
            assertTrue(voice.shareSquareTrendFrom("1.4").contains("1.4"))
            assertTrue(voice.shareSquareQuietLabel("14d").contains("14d"))
        }
    }

    companion object {
        private val stringProperties =
            Voice::class.declaredMemberProperties.filter { it.returnType.classifier == String::class }
        private val stringFunctions =
            Voice::class.declaredMemberFunctions.filter { it.returnType.classifier == String::class }

        /** One representative value per non-enum parameter type; enum params get every entry. */
        private fun sampleValues(type: KType): List<Any?> {
            val kClass = type.classifier as KClass<*>
            val base: List<Any?> =
                when {
                    kClass.java.isEnum -> kClass.java.enumConstants!!.toList()
                    kClass == String::class -> listOf("Test Case")
                    kClass == Int::class -> listOf(3)
                    kClass == Long::class -> listOf(5L)
                    kClass == Boolean::class -> listOf(true, false)
                    kClass == TrendFinding::class -> trendFindingSamples()
                    else -> error("No sample value strategy for parameter type $kClass")
                }
            return if (type.isMarkedNullable) base + null else base
        }

        /** Every kind, direction and outcome, so each trend headline branch is checked in all three voices. */
        private fun trendFindingSamples(): List<TrendFinding> =
            TrendFindingKind.entries.flatMap { kind ->
                ShiftDirection.entries.flatMap { direction ->
                    listOf<TagOutcome?>(null, TagOutcome.INTENSITY, TagOutcome.DURATION).map { outcome ->
                        TrendFinding(
                            kind,
                            direction,
                            TrendReliability.PATTERN,
                            3,
                            2.0,
                            5.0,
                            tagName = "Test Case",
                            outcome = outcome,
                            latestEvidenceAt = 0L,
                        )
                    }
                }
            }

        private fun argumentCombinations(function: KFunction<*>): List<List<Any?>> {
            val params = function.parameters.filter { it.kind == KParameter.Kind.VALUE }
            return params
                .map { sampleValues(it.type) }
                .fold(listOf(emptyList<Any?>())) { combinations, values ->
                    combinations.flatMap { prefix -> values.map { prefix + it } }
                }
        }
    }
}
