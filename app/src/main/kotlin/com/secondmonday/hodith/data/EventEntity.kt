package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "events",
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
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long?,
    val intensity: Int?,
    val note: String?,
    val loggedAt: Long,
    val staleNudgeDismissedAt: Long? = null,
)

/** The instantaneous, undurationed event every `ONE_TAP` quick-log path creates. */
fun quickLogEvent(
    caseId: Long,
    now: Long,
) = EventEntity(caseId = caseId, occurredAt = now, endedAt = null, intensity = null, note = null, loggedAt = now)
