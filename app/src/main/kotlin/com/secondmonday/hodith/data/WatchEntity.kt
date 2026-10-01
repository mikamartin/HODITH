package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(
    tableName = "watches",
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
data class WatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val kind: WatchKind,
    val threshold: Int,
    val windowDays: Int?,
    val expectedPer: ExpectedPer,
    val metric: VerdictMetric = VerdictMetric.OCCURRENCE_COUNT,
    val minIntensity: Int?,
    val enabled: Boolean,
    val armed: Boolean = true,
    val lastFiredAt: Long?,
)
