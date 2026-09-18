package com.secondmonday.hodith.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

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
@JsonClass(generateAdapter = true)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val occurredAt: Long,
    val endedAt: Long?,
    val intensity: Int?,
    val note: String?,
    val loggedAt: Long,
    // Captured once at log time from the device's zone id, so day/hour bucketing reflects where the
    // event actually happened rather than the device's zone whenever stats are later computed.
    @ColumnInfo(defaultValue = "0") val utcOffsetMinutes: Int = 0,
)

/** The instantaneous, undurationed event every `ONE_TAP` quick-log path creates. */
fun quickLogEvent(
    caseId: Long,
    now: Long,
) = EventEntity(
    caseId = caseId,
    occurredAt = now,
    endedAt = null,
    intensity = null,
    note = null,
    loggedAt = now,
    utcOffsetMinutes = ZoneId.systemDefault().offsetMinutesAt(now),
)

/** This event's own captured offset, for bucketing its timestamps into the calendar day/hour they actually occurred in. */
fun EventEntity.loggedZone(): ZoneOffset = zoneOffsetFromMinutes(utcOffsetMinutes)

/** Shared conversion so every captured-offset holder (this entity, `CalendarEvent`) agrees on the same minutes-to-offset math. */
fun zoneOffsetFromMinutes(minutes: Int): ZoneOffset = ZoneOffset.ofTotalSeconds(minutes * 60)

/** The UTC offset this zone's rules give at [atMillis] — not "now," so a backdated instant resolves its own historical (DST-correct) offset. */
fun ZoneId.offsetMinutesAt(atMillis: Long): Int = rules.getOffset(Instant.ofEpochMilli(atMillis)).totalSeconds / 60
