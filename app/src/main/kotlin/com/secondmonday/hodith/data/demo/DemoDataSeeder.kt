package com.secondmonday.hodith.data.demo

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.data.offsetMinutesAt
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.EVENING_START_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import com.secondmonday.hodith.domain.MORNING_START_HOUR
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random

private const val SEED_SPAN_DAYS = 380
private const val SEED_RANDOM_SEED = 42L
private const val MIN_DURATION_MILLIS = 30L * MILLIS_PER_MINUTE
private const val MAX_DURATION_MILLIS = 6L * MILLIS_PER_HOUR
private const val MIN_INTENSITY = 1
private const val MAX_INTENSITY = 5
private const val NOTE_CHANCE_PERCENT = 45
private const val TAG_CHANCE_PERCENT = 50
private const val MAX_TAGS_PER_EVENT = 2

// Story C T4 showcase (Migraine's "aura" tag, see [TagOutcomeShiftSeed]): the fraction of a Case's
// events forced to carry the showcase tag, deterministically and exclusively of the normal tagsFor
// draw -- comfortably over both TAG_OUTCOME_MIN_TAGGED_SAMPLE_COUNT and ...MIN_UNTAGGED_SAMPLE_COUNT
// (domain, internal) at Migraine's BURSTY event count (~85 events over the full span).
private const val TAG_OUTCOME_SHOWCASE_CHANCE_PERCENT = 40

// Story C T6 showcase (Tantrum's duration trend/time-of-day split, see [CaseSeed.durationTrendShift]):
// duration scales linearly across the span from this factor to TANTRUM_DURATION_LATE_FACTOR (a
// difficult developmental stretch making meltdowns take longer to resolve, roughly doubling --
// comfortably over TREND_SLOPE_MIN_RELATIVE_DIFFERENCE's 20% floor, domain-internal), and any event
// whose local hour falls outside [MORNING_START_HOUR, EVENING_START_HOUR) is multiplied again by
// TANTRUM_EVENING_DURATION_FACTOR (the well-known "witching hour" pattern -- evening tantrums running
// longer than daytime ones -- comfortably over TIME_OF_DAY_SPLIT_MIN_RELATIVE_DIFFERENCE's 20% floor
// too). That boundary must match computeTimeOfDaySplitFindings' own day (MORNING+AFTERNOON) vs.
// evening (EVENING+NIGHT) fold exactly -- reusing the same domain constants rather than a seed-local
// approximation, since a narrower evening window here (e.g. hour >= 17 alone, missing NIGHT's 0..5
// wrap) would dilute the two groups' real difference by scattering un-boosted "NIGHT" events into the
// day group's mean.
private const val TANTRUM_DURATION_EARLY_FACTOR = 0.7
private const val TANTRUM_DURATION_LATE_FACTOR = 1.4
private const val TANTRUM_EVENING_DURATION_FACTOR = 1.5

// Dense enough that at least one demo Case shows a clear recent uptick — exercises the Trend
// card's UP direction and gives the calendar heatmap/Rhythm grid a busy recent stretch to shade.
// One event on every one of these consecutive days also doubles as the Gaps & streaks card's
// "longest streak" showcase, since no other density guarantees a multi-day run of active days.
private const val RECENT_SURGE_DAYS = 12
private const val RECENT_SURGE_PER_DAY = 3

// Comfortably longer than any density's maxGapDays, so the silence this produces is guaranteed
// to exceed every gap in the Case's own history — the only way to deterministically exercise the
// Gaps & streaks card's "longest stretch since it started" note instead of the plain one. Doubles
// as the Trends WENT_QUIET finding's demo sample for the same reason: a record-setting current gap
// is exactly what computeQuietSignal's isCurrentGapLongest condition needs (DemoDataSeederTest.kt).
private const val QUIET_SPELL_DAYS = 60L

// "Lost my keys"' trending-shift shape (see [trendingOccurrences]): the most recent this many days
// of the span switch from isolated, widely-spaced events to tight 2-3-day clusters, so all three
// of Story C T1's detectors fire on one Case at once for a real demo of the Trends section's
// cap/reveal and multi-finding rendering — not just one finding in isolation. Long enough to clear
// TREND_MIN_SPAN_DAYS (56, domain-internal) for the frequency-shift finding too.
private const val TRENDING_RECENT_PHASE_DAYS = 120L

// A couple of START_STOP demo Cases end with an event still running (endedAt == null) so the
// ongoing indicator, the Insights "current gap reads 0 while an event is active" rule (spec §10),
// and the multiple-running-events path all have live data on a fresh "Load demo data". Ages are
// measured back from now; the second is past a day so the elapsed indicator shows its "Xd Yh" form.
private val ONGOING_EVENT_AGES_MILLIS = listOf(2L * MILLIS_PER_HOUR, 26L * MILLIS_PER_HOUR)

private enum class SeedDensity { SPARSE, BURSTY, DENSE }

/**
 * Story C T4 showcase: forces [tagName] onto [TAG_OUTCOME_SHOWCASE_CHANCE_PERCENT] of a Case's
 * events (bypassing the normal random [tagsFor] draw for this tag specifically, the same way
 * [CaseSeed.trendingShift]/[CaseSeed.recentSurge] bypass the normal occurrence generator for their
 * own Cases) and multiplies those events' duration by [durationBoostFactor] — giving the demo Case a
 * real, deliberate tag→outcome effect so `computeTagOutcomeFindings` has something to find.
 */
private data class TagOutcomeShiftSeed(
    val tagName: String,
    val durationBoostFactor: Double,
)

private data class CaseSeed(
    val name: String,
    val icon: String,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val density: SeedDensity,
    val notes: List<String>,
    val tags: List<String>,
    val description: String? = null,
    val recentSurge: Boolean = false,
    val quietSpell: Boolean = false,
    // Replaces the normal density-driven occurrence generator with [trendingOccurrences] — a
    // widely-spaced, isolated-days history that turns into tight recent clusters, so gap shift,
    // streak shift, and frequency shift all fire together (see [TRENDING_RECENT_PHASE_DAYS]).
    val trendingShift: Boolean = false,
    // Extra events left open (endedAt == null) at the end of the span. START_STOP Cases only —
    // a null endedAt on a NONE/MANUAL Case would be a data bug, not an ongoing state.
    val ongoingEventCount: Int = 0,
    val tagOutcomeShift: TagOutcomeShiftSeed? = null,
    // Story C T6 showcase: applies TANTRUM_DURATION_EARLY_FACTOR..LATE_FACTOR's time-based scaling
    // and TANTRUM_EVENING_DURATION_FACTOR's evening bump to this Case's durations (see endedAtFor).
    // START_STOP Cases only — a NONE/MANUAL Case never reaches endedAtFor's duration branch at all.
    val durationTrendShift: Boolean = false,
)

// Deliberately varied on every axis Big Picture and Case Detail's Insights tab need to exercise:
// duration mode, intensity, event density (dense/bursty/sparse) spread across several months, a
// recent logging surge, a quiet spell long enough to set a new "longest stretch since it started"
// record, and events still running now (one Case with a single ongoing event, one with several).
// Notes and tags are populated on only some events (not all, not none) so Case
// Detail's empty states and the log sheet's tag autocomplete both have real data to exercise.
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
            description = "Any cup counted, home-brewed or bought",
            recentSurge = true,
        ),
        CaseSeed(
            name = "Migraine",
            icon = "🤕",
            durationMode = DurationMode.START_STOP,
            intensityEnabled = true,
            density = SeedDensity.BURSTY,
            notes = listOf("Started after screen time", "Woke up with it", "Triggered by wine", "Light sensitivity bad"),
            // "aura" is deliberately not in this general pool -- it's assigned exclusively via
            // tagOutcomeShift below, so the tagged/untagged split behind the Story C T4 showcase
            // stays clean rather than also picking up random hits from the normal tagsFor draw.
            tags = listOf("light-sensitive", "medicated", "no-relief"),
            description = "From first twinge to when it fully lifts, not just the worst of it",
            ongoingEventCount = 1,
            // Story C T4 showcase (see HODITH_SPEC.md §10's own "aura migraines last 40% longer"
            // example): a 60% duration boost gives real margin over both the 20% descriptive floor
            // and the permutation test's significance bar, not a result sitting right at the edge.
            tagOutcomeShift = TagOutcomeShiftSeed(tagName = "aura", durationBoostFactor = 1.6),
        ),
        CaseSeed(
            name = "Lost my keys",
            icon = "🔑",
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            // Unused — trendingShift replaces the density-driven generator entirely.
            density = SeedDensity.SPARSE,
            notes = listOf("Found them in the fridge", "Under the couch again", "Left at the office", "In yesterday's jacket"),
            tags = listOf("morning-rush", "found-fast", "still-missing"),
            trendingShift = true,
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
            name = "Tantrum",
            icon = "😭",
            durationMode = DurationMode.START_STOP,
            intensityEnabled = false,
            density = SeedDensity.DENSE,
            notes = listOf("Overtired, probably", "Wrong-color cup incident", "Right before bedtime", "Grocery store meltdown"),
            tags = listOf("overtired", "hungry", "public", "bedtime"),
            durationTrendShift = true,
        ),
        CaseSeed(
            name = "Nosebleed",
            icon = "🩸",
            durationMode = DurationMode.NONE,
            intensityEnabled = false,
            density = SeedDensity.SPARSE,
            notes = listOf("Dry air, probably", "Right after a sneeze", "Out of nowhere"),
            tags = listOf("dry-weather", "minor", "prolonged"),
            quietSpell = true,
        ),
        CaseSeed(
            name = "Noisy neighbours",
            icon = "🔊",
            durationMode = DurationMode.START_STOP,
            intensityEnabled = true,
            density = SeedDensity.BURSTY,
            notes = listOf("Party upstairs again", "Drilling at 8am", "Bass through the wall", "Shouting in the hallway"),
            tags = listOf("upstairs", "outside", "late-night", "weekday"),
            // One recent, one left running from yesterday — a Case with more than one event going
            // at once, and old enough on the second to trip the stale-ongoing prompt.
            ongoingEventCount = 2,
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
            val spanStart = now - SEED_SPAN_DAYS * MILLIS_PER_DAY

            CASE_SEEDS.forEachIndexed { index, caseSeed ->
                val caseId =
                    repository.insertCase(
                        CaseEntity(
                            name = caseSeed.name,
                            description = caseSeed.description,
                            icon = caseSeed.icon,
                            createdAt = spanStart,
                            logFlow = LogFlow.ONE_TAP,
                            durationMode = caseSeed.durationMode,
                            intensityEnabled = caseSeed.intensityEnabled,
                            checkInsEnabled = true,
                            lastCheckInAt = null,
                            sortOrder = index,
                            archived = false,
                        ),
                    )

                val random = Random(SEED_RANDOM_SEED + index)
                val occurrenceSpanEnd = if (caseSeed.quietSpell) now - QUIET_SPELL_DAYS * MILLIS_PER_DAY else now
                val occurrences =
                    if (caseSeed.trendingShift) {
                        trendingOccurrences(spanStart, occurrenceSpanEnd, random)
                    } else {
                        occurrencesFor(caseSeed.density, spanStart, occurrenceSpanEnd, random)
                    }
                val withSurge = if (caseSeed.recentSurge) occurrences + recentSurgeOccurrences(now, random) else occurrences
                withSurge.sorted().forEach { occurredAt ->
                    val showcaseTag = caseSeed.tagOutcomeShift?.takeIf { random.nextInt(100) < TAG_OUTCOME_SHOWCASE_CHANCE_PERCENT }
                    val endedAt =
                        endedAtFor(
                            caseSeed.durationMode,
                            occurredAt,
                            now,
                            random,
                            durationBoostFactor = showcaseTag?.durationBoostFactor ?: 1.0,
                            durationTrendShift = caseSeed.durationTrendShift,
                            spanStart = spanStart,
                        )
                    insertSeedEvent(caseId, occurredAt, endedAt, caseSeed, random, forcedTag = showcaseTag?.tagName)
                }

                repeat(caseSeed.ongoingEventCount) { ongoingIndex ->
                    val startedAt = now - ONGOING_EVENT_AGES_MILLIS[ongoingIndex.coerceAtMost(ONGOING_EVENT_AGES_MILLIS.lastIndex)]
                    insertSeedEvent(caseId, startedAt, endedAt = null, caseSeed = caseSeed, random = random)
                }
            }
        }

        /**
         * One synthetic event with this Case's intensity/note/tag mix, [endedAt] `null` for an
         * ongoing one. [forcedTag] (Story C T4 showcase) replaces the normal random [tagsFor] draw
         * entirely for this event, keeping the showcase tag's tagged/untagged split clean.
         */
        private suspend fun insertSeedEvent(
            caseId: Long,
            occurredAt: Long,
            endedAt: Long?,
            caseSeed: CaseSeed,
            random: Random,
            forcedTag: String? = null,
        ) {
            val eventId =
                repository.insertEvent(
                    EventEntity(
                        caseId = caseId,
                        occurredAt = occurredAt,
                        endedAt = endedAt,
                        intensity = intensityFor(caseSeed.intensityEnabled, random),
                        note = noteFor(caseSeed.notes, random),
                        loggedAt = occurredAt,
                        utcOffsetMinutes = ZoneId.systemDefault().offsetMinutesAt(occurredAt),
                    ),
                )
            val tags = if (forcedTag != null) listOf(forcedTag) else tagsFor(caseSeed.tags, random)
            tags.forEach { tagName -> repository.addTagToEvent(eventId, tagName) }
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
    var cursor = spanStart + random.nextLong(MILLIS_PER_DAY)
    while (cursor < spanEnd) {
        occurrences += cursor
        val gapDays = random.nextInt(minGapDays, maxGapDays + 1)
        cursor += gapDays * MILLIS_PER_DAY + random.nextLong(MILLIS_PER_DAY)
    }
    return occurrences
}

private fun burstyOccurrences(
    spanStart: Long,
    spanEnd: Long,
    random: Random,
): List<Long> {
    val occurrences = mutableListOf<Long>()
    var clusterStart = spanStart + random.nextLong(MILLIS_PER_DAY * 10)
    while (clusterStart < spanEnd) {
        val clusterSize = random.nextInt(3, 7)
        repeat(clusterSize) {
            val occurredAt = clusterStart + random.nextLong(MILLIS_PER_DAY * 2)
            if (occurredAt < spanEnd) occurrences += occurredAt
        }
        val gapDays = random.nextInt(10, 31)
        clusterStart += gapDays * MILLIS_PER_DAY
    }
    return occurrences.sorted()
}

/**
 * Widely-spaced isolated days for most of the span, switching to tight 2-3-day-in-a-row clusters
 * for the most recent [TRENDING_RECENT_PHASE_DAYS] — deliberately shaped so all three of Story C
 * T1's detectors clear their thresholds on the same Case: the average gap between occurrences
 * shrinks (gap shift), the runs get longer (streak shift), and there are more occurrences in the
 * last 30 days than the 30 before (frequency shift). Verified against `computeGapShift`/
 * `computeStreakShift`/`computeTrendStats` directly, not just eyeballed — see this constant's own
 * doc comment for the margins each threshold needs clearing by.
 */
private fun trendingOccurrences(
    spanStart: Long,
    spanEnd: Long,
    random: Random,
): List<Long> {
    val recentPhaseStart = spanEnd - TRENDING_RECENT_PHASE_DAYS * MILLIS_PER_DAY
    val historic = spacedOccurrences(spanStart, recentPhaseStart, random, minGapDays = 30, maxGapDays = 38)

    val recent = mutableListOf<Long>()
    var cursor = recentPhaseStart + random.nextLong(MILLIS_PER_DAY)
    var clusterIndex = 0
    while (cursor < spanEnd) {
        // Alternates 2-day/3-day clusters (never a lone day) so the recent era's runs average
        // noticeably longer than the historic era's isolated ones, clearing computeStreakShift's
        // minimum-absolute-days floor with margin rather than landing just short of it.
        val clusterLength = (clusterIndex % 2) + 2
        repeat(clusterLength) { dayOffset ->
            val occurredAt = cursor + dayOffset * MILLIS_PER_DAY + random.nextLong(MILLIS_PER_DAY / 2)
            if (occurredAt < spanEnd) recent += occurredAt
        }
        cursor += (clusterLength + random.nextInt(8, 13)) * MILLIS_PER_DAY
        clusterIndex++
    }
    return historic + recent
}

/** [RECENT_SURGE_DAYS] × [RECENT_SURGE_PER_DAY] events packed into the days immediately before [now]. */
private fun recentSurgeOccurrences(
    now: Long,
    random: Random,
): List<Long> =
    (0 until RECENT_SURGE_DAYS).flatMap { daysAgo ->
        val dayStart = now - (daysAgo + 1) * MILLIS_PER_DAY
        List(RECENT_SURGE_PER_DAY) { dayStart + random.nextLong(MILLIS_PER_DAY) }
    }

private fun endedAtFor(
    durationMode: DurationMode,
    occurredAt: Long,
    spanEnd: Long,
    random: Random,
    durationBoostFactor: Double = 1.0,
    durationTrendShift: Boolean = false,
    spanStart: Long = occurredAt,
): Long? {
    if (durationMode != DurationMode.START_STOP) return null
    val trendMultiplier = if (durationTrendShift) durationTrendMultiplierFor(occurredAt, spanStart, spanEnd) else 1.0
    val duration = (random.nextLong(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS) * durationBoostFactor * trendMultiplier).toLong()
    return (occurredAt + duration).coerceAtMost(spanEnd)
}

/**
 * Story C T6 showcase (Tantrum, [CaseSeed.durationTrendShift]): [TANTRUM_DURATION_EARLY_FACTOR]..
 * [TANTRUM_DURATION_LATE_FACTOR] scaled linearly by how far [occurredAt] falls between [spanStart]
 * and [spanEnd] (a difficult developmental stretch making meltdowns take longer to resolve over
 * time), times [TANTRUM_EVENING_DURATION_FACTOR] again when [occurredAt]'s own local hour falls
 * outside [MORNING_START_HOUR]..[EVENING_START_HOUR) (the "witching hour" pattern — evening tantrums
 * running longer than daytime ones, matching
 * [com.secondmonday.hodith.domain.computeTimeOfDaySplitFindings]' own day/evening fold exactly) —
 * the two effects compose independently since occurrence generation doesn't correlate time-of-span
 * with hour-of-day.
 */
private fun durationTrendMultiplierFor(
    occurredAt: Long,
    spanStart: Long,
    spanEnd: Long,
): Double {
    val timeFraction = ((occurredAt - spanStart).toDouble() / (spanEnd - spanStart).coerceAtLeast(1)).coerceIn(0.0, 1.0)
    val trendFactor = TANTRUM_DURATION_EARLY_FACTOR + (TANTRUM_DURATION_LATE_FACTOR - TANTRUM_DURATION_EARLY_FACTOR) * timeFraction
    val hour = Instant.ofEpochMilli(occurredAt).atZone(ZoneId.systemDefault()).hour
    val isEvening = hour < MORNING_START_HOUR || hour >= EVENING_START_HOUR
    val eveningFactor = if (isEvening) TANTRUM_EVENING_DURATION_FACTOR else 1.0
    return trendFactor * eveningFactor
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
