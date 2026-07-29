package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val icon: String,
    val createdAt: Long,
    val logFlow: LogFlow,
    val durationMode: DurationMode,
    val intensityEnabled: Boolean,
    val hunchNudgeDismissed: Boolean,
    val pinned: Boolean,
    val checkInsEnabled: Boolean,
    val lastCheckInAt: Long?,
    val sortOrder: Int,
    val archived: Boolean,
)
