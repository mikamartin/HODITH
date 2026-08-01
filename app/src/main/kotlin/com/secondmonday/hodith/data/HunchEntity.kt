package com.secondmonday.hodith.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
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
)
