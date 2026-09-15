package com.secondmonday.hodith.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ConfidenceTier
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "hunches",
    foreignKeys = [
        ForeignKey(
            entity = CaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["caseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("caseId")],
)
@JsonClass(generateAdapter = true)
data class HunchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val direction: HunchDirection,
    val expectedCount: Int,
    val expectedPer: ExpectedPer,
    val createdAt: Long,
    val resolvedAt: Long?,
    /**
     * How the verdict counts observation (spec §8). A real, stored choice made once at creation,
     * never re-derived. Existing rows (schema < 8) and older backup files default to
     * [VerdictMetric.OCCURRENCE_COUNT] — today's only behaviour.
     */
    @ColumnInfo(defaultValue = "OCCURRENCE_COUNT")
    val metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
    /** Which slice of history the verdict measures (spec §8). Defaults to [ObservationWindow.SINCE_START]. */
    @ColumnInfo(defaultValue = "SINCE_START")
    val observationWindow: ObservationWindow = ObservationWindow.SINCE_START,
    /** Fixed window start, epoch millis — set only when [observationWindow] is [ObservationWindow.CUSTOM]. */
    val windowStartDate: Long? = null,
    /**
     * [com.secondmonday.hodith.domain.VerdictResult] fields, snapshotted at the moment this Hunch
     * was resolved so its history entry never drifts as Events are later edited or deleted
     * (spec §8; see [resolvedVerdictSnapshotTaken]). Null on an active Hunch, and briefly null on a
     * just-migrated resolved Hunch until the startup backfill (`HodithApplication`) computes them.
     */
    val resolvedTier: ConfidenceTier? = null,
    val resolvedEventCount: Int? = null,
    val resolvedActiveDayCount: Int? = null,
    val resolvedWindowDays: Long? = null,
    val resolvedObservedRate: Double? = null,
    val resolvedExpectedRate: Double? = null,
    val resolvedComparisonBand: ComparisonBand? = null,
    /**
     * Whether [resolvedTier]/[resolvedComparisonBand]/etc. have been computed for this Hunch.
     * Needed because [resolvedComparisonBand] `== null` is also the legitimate result for a Hunch
     * resolved before it ever reached a verdict — this flag is the only way to tell "genuinely no
     * verdict" apart from "not yet snapshotted."
     */
    @ColumnInfo(defaultValue = "0")
    val resolvedVerdictSnapshotTaken: Boolean = false,
)
