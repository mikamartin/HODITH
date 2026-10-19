package com.secondmonday.hodith.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "cases")
@Serializable
data class CaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String? = null,
    val icon: String,
    val createdAt: Long,
    val logFlow: LogFlow,
    @ColumnInfo(defaultValue = "0") val tracksDuration: Boolean,
    val intensityEnabled: Boolean,
    val checkInsEnabled: Boolean,
    val lastCheckInAt: Long?,
    val sortOrder: Int,
    val archived: Boolean,
)
