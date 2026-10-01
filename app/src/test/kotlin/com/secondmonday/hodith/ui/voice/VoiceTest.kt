package com.secondmonday.hodith.ui.voice

import com.secondmonday.hodith.domain.ComparisonBand
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
    fun `shareLogRangeNote combines both bounds, falls back to a labeled single bound, or reads All time`() {
        for (voice in voices) {
            assertEquals("Sep 3 – Sep 20", voice.shareLogRangeNote("Sep 3", "Sep 20"))
            assertEquals("${voice.shareLogDateFromLabel} Sep 3", voice.shareLogRangeNote("Sep 3", null))
            assertEquals("${voice.shareLogDateToLabel} Sep 20", voice.shareLogRangeNote(null, "Sep 20"))
            assertEquals(voice.shareLogRangeAllTimeLabel, voice.shareLogRangeNote(null, null))
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
                    else -> error("No sample value strategy for parameter type $kClass")
                }
            return if (type.isMarkedNullable) base + null else base
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
