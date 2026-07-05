package com.secondmonday.hodith.data

import androidx.room.Embedded
import androidx.room.Relation

data class CaseWithEvents(
    @Embedded val case: CaseEntity,
    @Relation(parentColumn = "id", entityColumn = "caseId") val events: List<EventEntity>,
)
