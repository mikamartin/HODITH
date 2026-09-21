package com.secondmonday.hodith.ui.logsheet

import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.LogDraft

// Shared by LogDetailScreenTest/LogDetailSheetTest — both drive the same LogDetailForm through
// their own container and need the same minimal, empty-state draft as a starting point.
internal fun draft(endedAt: Long? = null) =
    LogDraft(
        occurredAt = 0L,
        intensity = null,
        durationAmount = "",
        durationUnit = DurationUnit.MINUTES,
        note = "",
        tags = emptyList(),
        endedAt = endedAt,
        existingEndedAt = endedAt,
    )
