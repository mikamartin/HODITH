package com.secondmonday.hodith.ui.logsheet

import com.secondmonday.hodith.viewmodel.LogDraft

// Shared by LogDetailScreenTest/LogDetailSheetTest/LogDetailIntensityTest — all drive the same
// LogDetailForm through their own container and need the same minimal, empty-state draft as a
// starting point. [endedAt] here is always well under a minute past occurredAt, so the
// Days/Hours/Minutes fields it implies are always "0"/"0"/"0" -- never blank, since endedAt is non-null.
internal fun draft(endedAt: Long? = null) =
    LogDraft(
        occurredAt = 0L,
        intensity = null,
        note = "",
        tags = emptyList(),
        endedAt = endedAt,
        daysInput = if (endedAt == null) "" else "0",
        hoursInput = if (endedAt == null) "" else "0",
        minutesInput = if (endedAt == null) "" else "0",
    )
