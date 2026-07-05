package com.secondmonday.hodith.debug

import android.util.Log
import com.secondmonday.hodith.AppInitializer
import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.EventEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.LogFlow
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.timeline.MAX_INTENSITY
import com.secondmonday.hodith.domain.timeline.MIN_INTENSITY
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

private const val TAG = "SeedDataInitializer"
private const val SEED_SPAN_DAYS = 380
private const val DAY_MILLIS = 24L * 60 * 60 * 1000L
private const val SEED_RANDOM_SEED = 42L
private const val MIN_DURATION_MILLIS = 30L * 60 * 1000L
private const val MAX_DURATION_MILLIS = 6L * 60 * 60 * 1000L

private enum class SeedDensity { SPARSE, BURSTY, DENSE }

private data class CaseSeed(
    val name: String,
    val icon: String,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val density: SeedDensity,
)

// Deliberately varied on every axis Big Picture needs to exercise: duration mode, intensity,
// and event density (dense/bursty/sparse) spread across all four ZoomLevel presets.
private val CASE_SEEDS =
    listOf(
        CaseSeed("Coffee", "☕️", DurationMode.NONE, intensityEnabled = false, density = SeedDensity.DENSE),
        CaseSeed("Migraine", "🤕", DurationMode.START_STOP, intensityEnabled = true, density = SeedDensity.BURSTY),
        CaseSeed("Lost my keys", "🔑", DurationMode.NONE, intensityEnabled = false, density = SeedDensity.SPARSE),
        CaseSeed("Argument", "💢", DurationMode.NONE, intensityEnabled = true, density = SeedDensity.BURSTY),
        CaseSeed("Workout", "🏋️", DurationMode.START_STOP, intensityEnabled = false, density = SeedDensity.DENSE),
        CaseSeed("Nosebleed", "🩸", DurationMode.NONE, intensityEnabled = false, density = SeedDensity.SPARSE),
    )

/**
 * Populates the database with synthetic cases/events on first launch so Big Picture has
 * something to render before Case CRUD (Phase 3) exists. Debug-only: never bound in release
 * (see AppInitializerModule's empty @Multibinds set there).
 */
class SeedDataInitializer
    @Inject
    constructor(
        private val repository: HodithRepository,
        private val clock: Clock,
    ) : AppInitializer {
        override fun init() {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                if (repository.observeActiveCases().first().isNotEmpty()) return@launch
                seed()
            }
        }

        private suspend fun seed() {
            val now = clock.nowMillis()
            val spanStart = now - SEED_SPAN_DAYS * DAY_MILLIS
            var eventCount = 0

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
                    repository.insertEvent(
                        EventEntity(
                            caseId = caseId,
                            occurredAt = occurredAt,
                            endedAt = endedAtFor(caseSeed.durationMode, occurredAt, now, random),
                            intensity = intensityFor(caseSeed.intensityEnabled, random),
                            note = null,
                            loggedAt = occurredAt,
                        ),
                    )
                    eventCount++
                }
            }

            Log.d(TAG, "Seeded ${CASE_SEEDS.size} cases, $eventCount events")
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
