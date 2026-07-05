package com.secondmonday.hodit.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,
    val createdAt: Long,
    val logFlow: LogFlow,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val hunchNudgeDismissed: Boolean,
    val pinned: Boolean,
    val checkInDays: Int?,
    val lastCheckInAt: Long?,
    val sortOrder: Int,
    val archived: Boolean,
)
