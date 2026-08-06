package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@Entity(tableName = "cases")
@JsonClass(generateAdapter = true)
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
    val checkInsEnabled: Boolean,
    val lastCheckInAt: Long?,
    val sortOrder: Int,
    val archived: Boolean,
)
