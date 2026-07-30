package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "triggers",
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
data class TriggerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val caseId: Long,
    val kind: TriggerKind,
    val threshold: Int,
    val windowDays: Int?,
    val enabled: Boolean,
    val armed: Boolean = true,
    val lastFiredAt: Long?,
)
