package com.secondmonday.hodith.data.demo

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.Clock
import javax.inject.Inject
import kotlin.random.Random

private const val SEED_SPAN_DAYS = 380
private const val DAY_MILLIS = 24L * 60 * 60 * 1000L
private const val SEED_RANDOM_SEED = 42L
private const val MIN_DURATION_MILLIS = 30L * 60 * 1000L
private const val MAX_DURATION_MILLIS = 6L * 60 * 60 * 1000L
private const val MIN_INTENSITY = 1
private const val MAX_INTENSITY = 5
private const val NOTE_CHANCE_PERCENT = 45
private const val TAG_CHANCE_PERCENT = 50
private const val MAX_TAGS_PER_EVENT = 2

private enum class SeedDensity { SPARSE, BURSTY, DENSE }

private data class CaseSeed(
    val name: String,
    val icon: String,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val density: SeedDensity,
    val notes: List<String>,
    val tags: List<String>,
)

// Deliberately varied on every axis Big Picture needs to exercise: duration mode, intensity,
// and event density (dense/bursty/sparse) spread across several months. Notes and tags are
// populated on only some events (not all, not none) so Case Detail's empty states and the log
// sheet's tag autocomplete both have real data to exercise.
private val CASE_SEEDS =
    listOf(
        CaseSeed(
            name = "Coffee",
            icon = "☕️",
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            density = SeedDensity.DENSE,
            notes = listOf("Perfectly balanced", "A bit weak", "Extra hot", "Oat milk today", "Burnt beans again"),
            tags = listOf("home", "cafe", "oat-milk", "decaf"),
        ),
        CaseSeed(
            name = "Migraine",
            icon = "🤕",
            durationMode = DurationMode.START_STOP,
            intensityEnabled = true,
            density = SeedDensity.BURSTY,
            notes = listOf("Started after screen time", "Woke up with it", "Triggered by wine", "Light sensitivity bad"),
            tags = listOf("aura", "light-sensitive", "medicated", "no-relief"),
        ),
        CaseSeed(
            name = "Lost my keys",
            icon = "🔑",
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            density = SeedDensity.SPARSE,
            notes = listOf("Found them in the fridge", "Under the couch again", "Left at the office", "In yesterday's jacket"),
            tags = listOf("morning-rush", "found-fast", "still-missing"),
        ),
        CaseSeed(
            name = "Argument",
            icon = "💢",
            durationMode = DurationMode.NONE,
            intensityEnabled = true,
            density = SeedDensity.BURSTY,
            notes = listOf("About chores", "Over the phone", "Blew over quickly", "Still tense after"),
            tags = listOf("at-dinner", "on-the-phone", "resolved", "unresolved"),
        ),
        CaseSeed(
            name = "Workout",
            icon = "🏋️",
            durationMode = DurationMode.START_STOP,
            intensityEnabled = false,
            density = SeedDensity.DENSE,
            notes = listOf("Leg day", "Easy recovery run", "Skipped cardio", "Felt strong today"),
            tags = listOf("gym", "home", "cardio", "strength"),
        ),
        CaseSeed(
            name = "Nosebleed",
            icon = "🩸",
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            density = SeedDensity.SPARSE,
            notes = listOf("Dry air, probably", "Right after a sneeze", "Out of nowhere"),
            tags = listOf("dry-weather", "minor", "prolonged"),
        ),
    )

/**
 * Inserts a fixed set of synthetic cases/events for exercising the app with realistic data.
 * Triggered manually from Settings ("Load demo data") — every call adds another full set
 * (mixing with whatever's already there), it is not a one-time or idempotent seed.
 */
class DemoDataSeeder
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
    ) {
        suspend fun seed() {
            val now = clock.nowMillis()
            val spanStart = now - SEED_SPAN_DAYS * DAY_MILLIS

            CASE_SEEDS.forEachIndexed { index, caseSeed ->
                val caseId =
                    repository.insertCase(
                        CaseEntity(
                            name = caseSeed.name,
                            icon = caseSeed.icon,
                            createdAt = spanStart,
                            logFlow = LogFlow.ONE_TAP,
                            durationMode = caseSeed.durationMode,
                            intensityEnabled = caseSeed.intensityEnabled,
                            hunchNudgeDismissed = false,
                            pinned = false,
                            checkInDays = null,
                            lastCheckInAt = null,
                            sortOrder = index,
                            archived = false,
                        ),
                    )

                val random = Random(SEED_RANDOM_SEED + index)
                occurrencesFor(caseSeed.density, spanStart, now, random).forEach { occurredAt ->
                    val eventId =
                        repository.insertEvent(
                            EventEntity(
                                caseId = caseId,
                                occurredAt = occurredAt,
                                endedAt = endedAtFor(caseSeed.durationMode, occurredAt, now, random),
                                intensity = intensityFor(caseSeed.intensityEnabled, random),
                                note = noteFor(caseSeed.notes, random),
                                loggedAt = occurredAt,
                            ),
                        )
                    tagsFor(caseSeed.tags, random).forEach { tagName -> repository.addTagToEvent(eventId, tagName) }
                }
            }
        }
    }

private fun occurrencesFor(
    density: SeedDensity,
    spanStart: Long,
    spanEnd: Long,
    random: Random,
): List<Long> =
    when (density) {
        SeedDensity.DENSE -> spacedOccurrences(spanStart, spanEnd, random, minGapDays = 1, maxGapDays = 3)
        SeedDensity.SPARSE -> spacedOccurrences(spanStart, spanEnd, random, minGapDays = 20, maxGapDays = 45)
        SeedDensity.BURSTY -> burstyOccurrences(spanStart, spanEnd, random)
    }

private fun spacedOccurrences(
    spanStart: Long,
    spanEnd: Long,
    random: Random,
    minGapDays: Int,
    maxGapDays: Int,
): List<Long> {
    val occurrences = mutableListOf<Long>()
    var cursor = spanStart + random.nextLong(DAY_MILLIS)
    while (cursor < spanEnd) {
        occurrences += cursor
        val gapDays = random.nextInt(minGapDays, maxGapDays + 1)
        cursor += gapDays * DAY_MILLIS + random.nextLong(DAY_MILLIS)
    }
    return occurrences
}

private fun burstyOccurrences(
    spanStart: Long,
    spanEnd: Long,
    random: Random,
): List<Long> {
    val occurrences = mutableListOf<Long>()
    var clusterStart = spanStart + random.nextLong(DAY_MILLIS * 10)
    while (clusterStart < spanEnd) {
        val clusterSize = random.nextInt(3, 7)
        repeat(clusterSize) {
            val occurredAt = clusterStart + random.nextLong(DAY_MILLIS * 2)
            if (occurredAt < spanEnd) occurrences += occurredAt
        }
        val gapDays = random.nextInt(10, 31)
        clusterStart += gapDays * DAY_MILLIS
    }
    return occurrences.sorted()
}

private fun endedAtFor(
    durationMode: DurationMode,
    occurredAt: Long,
    spanEnd: Long,
    random: Random,
): Long? {
    if (durationMode != DurationMode.START_STOP) return null
    val duration = random.nextLong(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
    return (occurredAt + duration).coerceAtMost(spanEnd)
}

private fun intensityFor(
    intensityEnabled: Boolean,
    random: Random,
): Int? = if (intensityEnabled) random.nextInt(MIN_INTENSITY, MAX_INTENSITY + 1) else null

private fun noteFor(
    notes: List<String>,
    random: Random,
): String? = if (random.nextInt(100) < NOTE_CHANCE_PERCENT) notes.random(random) else null

private fun tagsFor(
    tags: List<String>,
    random: Random,
): List<String> {
    if (random.nextInt(100) >= TAG_CHANCE_PERCENT) return emptyList()
    val count = random.nextInt(1, MAX_TAGS_PER_EVENT + 1)
    return tags.shuffled(random).take(count)
}
