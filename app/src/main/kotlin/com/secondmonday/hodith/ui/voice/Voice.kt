package com.secondmonday.hodith.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.WatchKind
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.INTENSITY_MAX
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_DAYS
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_EVENTS
import com.secondmonday.hodith.domain.ShiftDirection
import com.secondmonday.hodith.domain.TagOutcome
import com.secondmonday.hodith.domain.TrendDirection
import com.secondmonday.hodith.domain.TrendReliability

private const val DAYS_PER_MONTH = 30
private const val WINDOW_PRESET_QUARTER_DAYS = 90
private const val WINDOW_PRESET_MONTHS_FROM_DAYS = 60

/** The middle-dot separator joining the parts of one info line, e.g. "3 events · 9 days". */
internal const val DOT_SEPARATOR = " · "

/** "day" / "week" / "month" / "3 months" — shared by every voice's Notification card-title copy. */
private fun perPhrase(per: ExpectedPer): String =
    when (per) {
        ExpectedPer.DAY -> "day"
        ExpectedPer.WEEK -> "week"
        ExpectedPer.MONTH -> "month"
        ExpectedPer.QUARTER -> "3 months"
    }

/**
 * One user-visible string per key, in three personalities (spec §12). Composables read
 * [LocalVoice] instead of branching on theme, so a string can never ship in only one voice.
 * Minimal key set for now — only what Home and Big Picture need so far; Phase 4 extends this
 * interface with the full string set rather than replacing it.
 */
interface Voice {
    val homeHeaderTitle: String
    val noCasesEmptyState: String
    val bigPictureEarlyDays: String
    val bigPictureMonthPickerTitle: String
    val bigPictureDayDetailEmptyState: String
    val bigPictureWeekDetailEmptyState: String
    val bigPictureWeekViewDescription: String
    val bigPictureCasesFilterLabel: String get() = "Cases"
    val bigPictureTagsFilterLabel: String get() = "Tags"
    val bigPictureYearFilterLabel: String get() = "Year"

    /** Big Picture's day/week detail-row field toggles (spec §9). Field labels are shared across voices. */
    val bigPictureDetailDialogTitle: String
    val bigPictureDetailEditDescription: String
    val bigPictureDetailNotesLabel: String get() = "Notes"
    val bigPictureDetailTagsLabel: String get() = "Tags"
    val bigPictureDetailDurationLabel: String get() = "Duration"
    val bigPictureDetailIntensityLabel: String get() = "Intensity"
    val bigPictureFilterCountAll: String
    val bigPictureFilterCountNone: String
    val bigPictureAllCasesLabel: String
    val bigPictureAllTagsLabel: String
    val bigPictureUntaggedOnlyLabel: String
    val bigPictureNoCasesSelectedNote: String
    val bigPictureSelectAllAction: String
    val bigPictureClearAllAction: String
    val homeNavLabel: String get() = "Home"
    val bigPictureNavLabel: String get() = "Big Picture"
    val settingsNavLabel: String get() = "Settings"
    val comingSoonPlaceholder: String
    val newCaseTitle: String
    val editCaseTitle: String
    val newCaseFabDescription: String
    val backButtonDescription: String get() = "Back"
    val caseNameLabel: String get() = "Name"
    val caseNameHint: String
    val caseNameRequiredError: String
    val caseNameDuplicateError: String
    val caseDescriptionLabel: String get() = "Description (optional)"
    val caseDescriptionHint: String
    val caseIconLabel: String get() = "Icon"
    val caseIconRequiredError: String
    val caseIconSectionExpandDescription: String
    val caseIconSectionCollapseDescription: String
    val caseSectionInfoDescription: String
    val infoDialogDismissAction: String
    val caseLogFlowLabel: String get() = "Logging"
    val caseLogFlowOneTap: String get() = "One tap"
    val caseLogFlowDetailSheet: String get() = "Detail sheet"
    val caseLogFlowInfoTitle: String
    val caseLogFlowInfoBody: String
    val caseDurationModeLabel: String get() = "Duration"
    val caseDurationModeNone: String get() = "None"
    val caseDurationModeManual: String get() = "Manual"
    val caseDurationModeStartStop: String get() = "Start/stop"
    val caseDurationModeInfoTitle: String
    val caseDurationModeInfoBody: String
    val caseIntensityToggleLabel: String get() = "Track intensity (1-5)"
    val caseCheckInLabel: String get() = "Check-in"
    val caseCheckInInfoTitle: String
    val caseCheckInInfoBody: String
    val caseSaveButton: String
    val caseDetailEditDescription: String
    val archiveCaseDescription: String
    val archiveCaseConfirmTitle: String
    val archiveCaseConfirmBody: String
    val archiveCaseConfirmAction: String
    val archiveCaseCancelAction: String

    /** Confirm shown when a Case leaves `START_STOP` mode while events are still running (spec §6). */
    val leaveStartStopConfirmTitle: String
    val leaveStartStopConfirmAction: String
    val leaveStartStopCancelAction: String

    /** Confirm shown when a Case enters `START_STOP` mode while it has open-ended events (spec §6). */
    val enterStartStopConfirmTitle: String
    val enterStartStopConfirmAction: String
    val enterStartStopCancelAction: String

    val archivedCasesTitle: String
    val archivedCasesEmptyState: String
    val eventListEmptyState: String

    /**
     * History tab's summary line above the event list: the headline rate (already formatted with its unit, or `null`
     * below the rate's own minimum), the total events logged, and the Case's observation span so far.
     */
    fun logSummaryLine(
        rate: String?,
        eventCount: Int,
        observedDays: Long,
    ): String

    val deleteEventConfirmTitle: String
    val deleteEventConfirmBody: String
    val deleteEventConfirmAction: String
    val deleteEventCancelAction: String
    val deleteCaseForeverConfirmTitle: String
    val deleteCaseForeverConfirmAction: String
    val deleteCaseForeverCancelAction: String
    val clearArchiveButtonDescription: String
    val clearArchiveConfirmTitle: String
    val clearArchiveConfirmAction: String
    val clearArchiveConfirmCancelAction: String
    val retroLogEntryDescription: String
    val logSheetNewEventTitle: String
    val logSheetEditEventTitle: String
    val logSheetTimeLabel: String
    val logSheetIntensityLabel: String
    val logSheetDurationLabel: String

    /**
     * Unit labels for the Manual-mode duration selector (spec §6). Structural chrome — a compact
     * three-segment control inside the field — so these are shared defaults, not authored per voice.
     */
    val logSheetDurationUnitMinutes: String get() = "Min"
    val logSheetDurationUnitHours: String get() = "Hr"
    val logSheetDurationUnitDays: String get() = "Day"
    val logSheetNoteLabel: String
    val logSheetNoteHint: String
    val logSheetTagsLabel: String
    val logSheetAddTagHint: String
    val logSheetRemoveTagDescription: String
    val logSheetSaveButton: String
    val logSheetPickerConfirm: String
    val logSheetPickerCancel: String
    val logSheetStartButton: String
    val logSheetEndLabel: String
    val logSheetOngoingLabel: String
    val logSheetStopNowAction: String

    /** Clears an edited event's end time, putting it back into the ongoing state (spec §6). */
    val logSheetBackToOngoingAction: String

    /** Shown under a time field when a picked value landed after `now`; the edit is discarded and the field keeps its prior value. */
    val logSheetFutureTimeNotice: String

    /** Shown under the Start field when a picked start time landed after the current end time; the edit is discarded. */
    val logSheetStartAfterEndNotice: String

    /** Shown under the End field when a picked end time landed before the current start time; the edit is discarded. */
    val logSheetEndBeforeStartNotice: String
    val quickLogUndoAction: String
    val settingsSupportSectionLabel: String
    val settingsRateAppButton: String
    val settingsContactUsButton: String
    val settingsAppearanceSectionLabel: String
    val settingsThemeSectionLabel: String
    val themeOptionPlain: String get() = "Plain"
    val themeOptionIntense: String get() = "Intense"
    val themeOptionBright: String get() = "Bright"
    val settingsThemeInfoTitle: String
    val settingsThemeInfoBody: String
    val settingsTimeFormatSectionLabel: String
    val timeFormatOption12Hour: String get() = "12-hour"
    val timeFormatOption24Hour: String get() = "24-hour"
    val settingsCheckInSectionLabel: String
    val checkInIntervalOptionOff: String get() = "Off"
    val checkInIntervalOptionSeven: String get() = "7d"
    val checkInIntervalOptionFourteen: String get() = "14d"
    val checkInIntervalOptionThirty: String get() = "30d"
    val settingsCheckInInfoTitle: String
    val settingsCheckInInfoBody: String
    val settingsDataSectionLabel: String
    val settingsCloudBackupToggleLabel: String
    val settingsCloudBackupInfoTitle: String
    val settingsCloudBackupInfoBody: String
    val settingsDeleteDataButton: String
    val settingsDeleteDataOptionsTitle: String
    val settingsDeleteDataOptionAll: String
    val settingsDeleteDataOptionLogsOnly: String
    val settingsDeleteDataDateLabel: String
    val settingsDeleteDataOptionsNextAction: String
    val settingsDeleteDataOptionsCancelAction: String
    val settingsDeleteAllDataConfirmTitle: String
    val settingsDeleteAllDataConfirmBody: String
    val settingsDeleteAllDataConfirmAction: String
    val settingsDeleteAllDataCancelAction: String
    val settingsDeleteDataLogsConfirmTitle: String

    fun settingsDeleteDataLogsConfirmBody(dateLabel: String): String

    val settingsDeleteDataLogsConfirmAction: String
    val settingsExportButton: String
    val settingsExportFormatDialogTitle: String
    val settingsExportFormatJsonOption: String
    val settingsExportFormatJsonDescription: String
    val settingsExportFormatCsvOption: String
    val settingsExportFormatCsvDescription: String
    val settingsExportFormatConfirmAction: String
    val settingsExportFormatCancelAction: String
    val settingsImportButton: String
    val settingsImportConfirmTitle: String
    val settingsImportConfirmBody: String
    val settingsImportConfirmAction: String
    val settingsImportCancelAction: String
    val settingsExportSuccessMessage: String
    val settingsExportFailureMessage: String
    val settingsCsvExportSuccessMessage: String
    val settingsCsvExportFailureMessage: String
    val settingsImportSuccessMessage: String
    val settingsImportFailureInvalidMessage: String
    val settingsImportFailureVersionMessage: String
    val settingsImportFailureIoMessage: String
    val settingsImportFailureSemanticMessage: String
    val settingsDeveloperModeSectionLabel: String
    val settingsLoadDemoDataButton: String
    val settingsDemoDataLoadedMessage: String
    val aboutScreenTitle: String
    val aboutIdeaLabel: String
    val aboutIdeaBody: String
    val aboutVersionLabel: String get() = "Version"
    val aboutDeveloperModeUnlockedMessage: String
    val aboutPrivacyLabel: String
    val aboutPrivacyBody: String
    val aboutPrivacyPolicyLinkLabel: String
    val aboutLicensesLabel: String
    val aboutLicensesBody: String

    /** Stepper suffix for a stated frequency's count — "times per" week/month/3 months. */
    val frequencyCountSuffix: String get() = "times per"

    /** Stepper suffix when the days-active metric is selected — "days active per" week/month/3 months. */
    val frequencyCountSuffixDaysActive: String get() = "days active per"
    val frequencyDecreaseCountDescription: String
    val frequencyIncreaseCountDescription: String
    val expectedPerDay: String get() = "Day"
    val expectedPerWeek: String get() = "Week"
    val expectedPerMonth: String get() = "Month"
    val expectedPerQuarter: String get() = "Quarter"

    /** Metric picker — shown only for a duration-tracking Case; one line, no jargon. */
    val metricOccurrenceLabel: String
    val metricDaysActiveLabel: String
    val caseDetailLogTabLabel: String get() = "History"
    val caseDetailInsightsTabLabel: String get() = "Insights"

    /**
     * Log tab's start/end sort toggle — structural, identical across all three voices like
     * [insightsFrequencyGranularityDay]. Shown only when the Case tracks duration (spec §6):
     * "Started" orders by when each event began, "Ended" floats still-running events to the top
     * then orders the rest by when they finished.
     */
    val logSortLabel: String get() = "Sort"
    val logSortByStartLabel: String get() = "Started"
    val logSortByEndLabel: String get() = "Ended"

    /** History tab's pinned Edit icon (spec §6) — same role as [bigPictureDetailEditDescription], persona-styled to match. Opens [logDetailDialogTitle]'s dialog of Notes/Tags/Duration/Intensity toggles. */
    val logDetailEditDescription: String
    val logDetailDialogTitle: String

    /** Reveals 50 more Log tab events beyond the currently loaded window (spec §6, PROGRESS.md F4). Persona-styled, like [insightsHeatmapShowMoreAction] — a similar "reveal more of the list" CTA. */
    val logShowMoreAction: String

    /** Insights tab with zero events (spec §9): a flat invitation, never a countdown or a count. */
    val insightsNothingLoggedMessage: String

    /** One-line note above the heatmap when a single event is logged and the Frequency/Trend cards are still held back. */
    val insightsSingleEventNote: String

    val insightsSectionLabelHeatmap: String get() = "Calendar heatmap"

    /** Reveals months beyond the heatmap's default 3-month preview. */
    val insightsHeatmapShowMoreAction: String

    /** Collapses the heatmap back to its default 3-month preview. */
    val insightsHeatmapShowFewerAction: String

    /** Spec §10 stat section labels — structural, identical across all three voices like [insightsSectionLabelHeatmap]. */
    val insightsSectionLabelFrequency: String get() = "Frequency over time"
    val insightsSectionLabelRhythm: String get() = "Rhythm"

    /** Replaces [insightsSectionLabelRhythm] when the Case has a multi-day event — the grid then plots event starts, not spans (spec §9). */
    val insightsSectionLabelRhythmStarts: String get() = "Start times"
    val insightsSectionLabelGaps: String get() = "Gaps & streaks"
    val insightsSectionLabelDuration: String get() = "Event duration"
    val insightsSectionLabelIntensity: String get() = "Intensity"
    val insightsSectionLabelTags: String get() = "Tags"

    /** Tag breakdown's denominator row — structural, identical across all three voices. */
    val insightsTagsTotalLabel: String get() = "Total events"

    /**
     * Spec §9/§10 drill-down (S10): tapping a heatmap day, an intensity square, or a tag row
     * opens the logged events behind it. [insightsDrillDownEmptyState] is a defensive fallback —
     * every tap target that reaches one of these is gated on having at least one matching event.
     */
    val insightsDrillDownEmptyState: String

    /** Accessibility label for a tappable heatmap cell, wrapping its already-formatted [dateLabel] (e.g. "Jul 14, 2026"). */
    fun insightsHeatmapDayTapDescription(dateLabel: String): String

    /** Accessibility label for a tappable intensity square. */
    fun insightsIntensitySquareTapDescription(level: Int): String

    /** Accessibility label for a tappable tag row. */
    fun insightsTagRowTapDescription(tagName: String): String

    /** Accessibility label for a tappable rhythm cell, wrapping its already-formatted [dayLabel] (e.g. "Monday") and [timeOfDayLabel] (e.g. "Morning"). */
    fun insightsRhythmCellTapDescription(
        dayLabel: String,
        timeOfDayLabel: String,
    ): String

    /** Intensity drill-down dialog title. */
    fun insightsIntensityDrillDownTitle(level: Int): String

    /** Tag drill-down dialog title. */
    fun insightsTagDrillDownTitle(tagName: String): String

    /** Rhythm cell drill-down dialog title, wrapping its already-formatted [dayLabel] and [timeOfDayLabel]. */
    fun insightsRhythmDrillDownTitle(
        dayLabel: String,
        timeOfDayLabel: String,
    ): String

    /** Frequency-over-time's info icon, explaining the fixed 12-bucket window and its auto-picked granularity. */
    val insightsFrequencyInfoTitle: String

    fun insightsFrequencyInfoBody(granularity: FrequencyGranularity): String

    /** Gaps & streaks card's min/avg/max row labels: the gap word is spelled out, unlike the share card's Min/Avg/Max. Structural. */
    val insightsGapsMinLabel: String get() = "Min gap"
    val insightsGapsAvgLabel: String get() = "Avg gap"
    val insightsGapsMaxLabel: String get() = "Max gap"

    /** Gaps & streaks card's figure labels, structural. "Current" breaks onto two lines so it sits level with the streak labels. */
    val insightsGapsCurrentLabel: String get() = "Current\ngap"

    /** Streak figures on the Gaps & streaks card (Insights and share) and the share card's Streaks card. Structural. */
    val insightsStreakLongestLabel: String get() = "Longest streak"
    val insightsStreakAverageLabel: String get() = "Average streak"

    /** Spec §10's "tends to come in bursts" flag, shown as a badge on the Gaps & streaks card. */
    val insightsBurstFlagLabel: String

    /** Gaps & streaks' info icon: one short line per gap/streak metric, plus the active-span caveat that lets a streak outrun the event count (spec §9). */
    val insightsGapsInfoTitle: String
    val insightsGapsInfoBody: String

    /** Spec §10 trend arrow: last 30 days vs. the 30 before — purely descriptive, no judgement either way. */
    fun insightsTrendSentence(
        direction: TrendDirection,
        recentCount: Int,
        priorCount: Int,
    ): String

    /**
     * Spec §10 Trends "went quiet" finding: the Case's current, still-open silence
     * ([currentGapLabel]) has outlasted every past gap it's ever had ([longestPastGapLabel],
     * already formatted, e.g. via `formatDays`) — framed as an open question per spec §4 ("ask
     * rather than silently report a trend"), never as a statement that the user did something
     * wrong. No `direction` parameter, unlike [insightsGapShiftSentence] — this finding only ever
     * means the silence grew to a record, there's no shrinking case.
     */
    fun insightsWentQuietSentence(
        currentGapLabel: String,
        longestPastGapLabel: String,
    ): String

    /** Went-quiet finding row's evidence line, shown inline (not behind a tap) — phrased like [insightsGapShiftEvidenceLabel] but keyed on the past gaps the record beat. */
    fun insightsWentQuietEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trend card: an optional extra line noting the average gap has shifted noticeably
     * across the Case's history — descriptive only, absent when
     * [com.secondmonday.hodith.domain.computeGapShift] finds nothing noticeable. [priorAverageLabel]/
     * [recentAverageLabel] are the two half-averages (already formatted, e.g. via `formatDays`) so
     * the sentence states the shift in real numbers, not direction alone.
     */
    fun insightsGapShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ): String

    /** As [insightsGapShiftSentence], for streak length rather than gap length. */
    fun insightsStreakShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ): String

    /** Spec §10 Trends section header — structural, shared with the Share card's own Trends section (`ShareCardTemplate.kt`'s `MiniTrendsSection`). */
    val insightsSectionLabelTrends: String get() = "Trends"

    /** Trends section "show more": navigates to the full findings list, unlike the in-place reveal verb of [insightsHeatmapShowMoreAction]. */
    val insightsTrendsShowMoreAction: String

    /**
     * Trends section's one shared info icon (spec §10) — what a finding is, and what
     * [TrendReliability.HINT] vs [TrendReliability.PATTERN] means. One dialog for the whole
     * section rather than one per finding row: with only three possible kinds today the same
     * explanation would otherwise repeat verbatim on every row: reliability itself is shown
     * inline as a tag ([trendReliabilityHintLabel]/[trendReliabilityPatternLabel]), so the tap
     * target only needs to carry the *meaning* of that tag, once.
     */
    val insightsTrendsInfoTitle: String
    val insightsTrendsInfoBody: String

    /** Tag card's "see all": navigates to the full tag list, the same navigate-not-expand shape as [insightsTrendsShowMoreAction]. */
    val insightsTagsSeeAllAction: String

    /** Collapsed tag card's count row for how many distinct tags the Case has, beside [insightsTagsTotalLabel]'s event count. */
    val insightsTagsDistinctLabel: String

    /** Full tag list screen's info dialog, explaining what each row counts. */
    val insightsTagsInfoTitle: String
    val insightsTagsInfoBody: String

    /** Trends finding row: the visible reliability tag next to the sentence — structural, identical across all three voices like the stat-row labels above. */
    val trendReliabilityHintLabel: String get() = "Hint"
    val trendReliabilityPatternLabel: String get() = "Pattern"

    /** Gap-shift finding row's evidence line, shown inline (not behind a tap) — phrased like [verdictMeta] but keyed on [sampleCount] (gaps compared) rather than an event-count/day-window pair. */
    fun insightsGapShiftEvidenceLabel(sampleCount: Int): String

    /** As [insightsGapShiftEvidenceLabel], for the streak-shift finding row. */
    fun insightsStreakShiftEvidenceLabel(sampleCount: Int): String

    /**
     * Frequency-shift finding row's evidence line — states the fixed comparison window rather
     * than a variable count, since [insightsTrendSentence] (its main sentence) already states
     * both counts directly.
     */
    fun insightsFrequencyShiftEvidenceLabel(): String

    /**
     * Spec §10 Trends "tag share shift" finding (Story C T2): [tagName]'s share of the Case's own
     * events has shifted noticeably between the earlier and more recent half of its history —
     * [priorShareLabel]/[recentShareLabel] are the two half-shares (already formatted, e.g. via
     * `formatPercent`) so the sentence states the shift in real numbers, not direction alone. Like
     * every Trends sentence, this describes a correlation the user can investigate, never a cause —
     * "tends to," not "causes."
     */
    fun insightsTagShareShiftSentence(
        tagName: String,
        direction: ShiftDirection,
        priorShareLabel: String,
        recentShareLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the tag-share-shift finding row — keyed on the events behind the half/half split, not the tag's own occurrence count. */
    fun insightsTagShareShiftEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "common tag combos" finding (Story C T9): [tagNamesLabel] is the already-joined
     * display string (e.g. `"Coffee + Late"`), not a raw list — Voice keys stay plain `String`/`Int`
     * parameters, the same precedent [insightsTagTimingSentence]'s `bucketPhrase` sets for a
     * pre-formatted phrase. [count] of [totalEvents] is the literal pair, not formatted ahead of time,
     * the same shape [insightsTrendSentence]'s event counts use. A descriptive finding, not a tested
     * one, so no direction to word either way.
     */
    fun insightsTagComboSentence(
        tagNamesLabel: String,
        count: Int,
        totalEvents: Int,
    ): String

    /** As [insightsTagTimingEvidenceLabel], for the tag-combo finding row — keyed on the combo's own co-occurrence count. */
    fun insightsTagComboEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "recurrence shape" finding (Story C T3): whether this Case's past gaps form
     * an early-spike pattern ([direction] [ShiftDirection.UP] — recurrence usually follows within
     * [thresholdLabel], already formatted e.g. via `formatDays`) or a dead-zone pattern ([direction]
     * [ShiftDirection.DOWN] — it almost never does). [shareLabel] is the observed share of gaps that
     * landed at or under [thresholdLabel] (already formatted, e.g. via `formatPercent`). Unlike
     * [insightsWentQuietSentence] (a claim about the Case's current, still-open silence), this is a
     * distribution-shape claim true regardless of current state — the two can both appear on the
     * same Case at once, so the dead-zone wording here describes the *pattern* ("tends to take a
     * while") rather than echoing went-quiet's "still happening?" framing.
     */
    fun insightsRecurrenceShapeSentence(
        direction: ShiftDirection,
        thresholdLabel: String,
        shareLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the recurrence-shape finding row. */
    fun insightsRecurrenceShapeEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "tag → outcome" finding (Story C T4): [tagName]'s events differ from the
     * Case's other events on [outcome] (intensity or duration) — [direction] [ShiftDirection.UP]
     * means events with the tag run higher/longer, [ShiftDirection.DOWN] means lower/shorter.
     * [relativeDifferenceLabel] is the relative difference between the two groups (already formatted,
     * e.g. via `formatPercent`); [withoutTagLabel]/[withTagLabel] are the two group means, already
     * formatted in [outcome]'s own unit. Unlike every other Trends sentence, this one describes an
     * effect that has actually been tested for significance, not just a threshold crossing — still
     * "tends to," never "causes."
     */
    fun insightsTagOutcomeSentence(
        tagName: String,
        outcome: TagOutcome,
        direction: ShiftDirection,
        relativeDifferenceLabel: String,
        withoutTagLabel: String,
        withTagLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the tag-outcome finding row. */
    fun insightsTagOutcomeEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "change point" finding (Story C T5): the typical gap between events has
     * shifted since a best-supported split point in the Case's own history — [direction]
     * [ShiftDirection.UP] means the gap grew (happening less often), [ShiftDirection.DOWN] means it
     * shrank (happening more often). [dateLabel] is the split's approximate date (already formatted
     * via `formatApproximateMonth`, e.g. "mid-March" — a best-supported estimate, not an exact day);
     * [priorLabel]/[recentLabel] are the two segments' average gap length (already formatted via
     * `formatDays`). Like [insightsTagOutcomeSentence], this describes an effect already tested for
     * significance, not just a threshold crossing — "tends to," never "causes."
     */
    fun insightsChangePointSentence(
        direction: ShiftDirection,
        dateLabel: String,
        priorLabel: String,
        recentLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the change-point finding row. */
    fun insightsChangePointEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "trend slope" finding (Story C T6): [outcome] (intensity or duration) has a
     * real slope over the Case's own history — [direction] [ShiftDirection.UP] means it's climbing
     * (running more intense, or longer), [ShiftDirection.DOWN] means it's easing (less intense, or
     * shorter). [priorLabel]/[recentLabel] are the time-ordered first-half/second-half averages
     * (already formatted, in [outcome]'s own unit). Like [insightsTagOutcomeSentence], this describes
     * an effect already tested for significance — "tends to," never "causes."
     */
    fun insightsTrendSlopeSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        priorLabel: String,
        recentLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the trend-slope finding row. */
    fun insightsTrendSlopeEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "time-of-day split" finding (Story C T6): [outcome] (intensity or duration)
     * differs between day and evening events — [direction] [ShiftDirection.UP] means evening events
     * run higher/longer, [ShiftDirection.DOWN] means day events do. [dayLabel]/[eveningLabel] are the
     * two group means (already formatted, in [outcome]'s own unit). Both directions need their own
     * wording — this isn't only ever "evenings are worse." Like [insightsTagOutcomeSentence], this
     * describes an effect already tested for significance — "tends to," never "causes."
     */
    fun insightsTimeOfDaySplitSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        dayLabel: String,
        eveningLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the time-of-day-split finding row. */
    fun insightsTimeOfDaySplitEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "tag timing" finding (Story C T7): [tagName]'s own events cluster into
     * [bucketPhrase] (already formatted and phrase-ready, e.g. "on Tuesdays" or "in the evening")
     * beyond the Case's overall rhythm there. No `direction` parameter, unlike
     * [insightsGapShiftSentence] — this finding only ever means the tag concentrates there, never
     * that it avoids somewhere (T7's feasibility ruling). [baselineLabel]/[taggedLabel] are that
     * bucket's share of the Case's own events overall vs. the tag's own events (already formatted
     * via `formatPercent`). Like [insightsTagOutcomeSentence], this describes an effect already
     * tested for significance — "tends to," never "causes."
     */
    fun insightsTagTimingSentence(
        tagName: String,
        bucketPhrase: String,
        baselineLabel: String,
        taggedLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the tag-timing finding row. */
    fun insightsTagTimingEvidenceLabel(sampleCount: Int): String

    /**
     * Spec §10 Trends "weekday vs weekend" finding (Story C T8, the scoped fallback from that
     * item's cycles/seasonality investigation): the Case's own events lean toward weekends
     * ([direction] [ShiftDirection.UP]) or weekdays ([ShiftDirection.DOWN]) more than the roughly
     * 2-in-7-day calendar baseline. [weekdayLabel]/[weekendLabel] are the Case's own observed shares
     * (already formatted via `formatPercent`) — the fixed baseline is a literal in the sentence
     * text, not a parameter, since it never varies per Case. Both directions need their own
     * wording, the same [insightsTimeOfDaySplitSentence] rule. Like [insightsTagTimingSentence],
     * this describes an effect already tested for significance — "tends to," never "causes."
     */
    fun insightsWeekdayWeekendSentence(
        direction: ShiftDirection,
        weekdayLabel: String,
        weekendLabel: String,
    ): String

    /** As [insightsGapShiftEvidenceLabel], for the weekday-vs-weekend finding row. */
    fun insightsWeekdayWeekendEvidenceLabel(sampleCount: Int): String

    /** Duration's info icon: clarifies the average/longest/total figures only count events that have already ended. */
    val insightsDurationInfoTitle: String
    val insightsDurationInfoBody: String

    val insightsIntensityAverageLabel: String get() = "Average intensity"

    /** Frequency chart's user-overridable granularity chips — structural, identical across all three voices. */
    val insightsFrequencyGranularityDay: String get() = "Day"
    val insightsFrequencyGranularityWeek: String get() = "Week"
    val insightsFrequencyGranularityMonth: String get() = "Month"

    /** Rhythm heatmap's row labels — structural, identical across all three voices. */
    val insightsTimeOfDayMorning: String get() = "Morning"
    val insightsTimeOfDayAfternoon: String get() = "Afternoon"
    val insightsTimeOfDayEvening: String get() = "Evening"
    val insightsTimeOfDayNight: String get() = "Night"

    /** Rhythm's info icon title, per voice. */
    val insightsRhythmInfoTitle: String

    /**
     * Rhythm's info icon body: just the four time-of-day boundaries, each already formatted per
     * [com.secondmonday.hodith.ui.theme.LocalTimeFormat] — structural, identical across all three
     * voices, since these are objective clock times rather than voiced copy.
     */
    fun insightsRhythmInfoBody(
        morningStart: String,
        afternoonStart: String,
        eveningStart: String,
        nightStart: String,
    ): String =
        "Morning: $morningStart – $afternoonStart\n" +
            "Afternoon: $afternoonStart – $eveningStart\n" +
            "Evening: $eveningStart – $nightStart\n" +
            "Night: $nightStart – $morningStart"

    fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ): String

    fun archivedCasesLink(count: Int): String

    fun archivedCaseEventCount(count: Int): String

    fun unarchiveCaseDescription(caseName: String): String

    fun deleteCaseForeverDescription(caseName: String): String

    fun deleteCaseForeverConfirmBody(eventCount: Int): String

    fun clearArchiveConfirmBody(caseCount: Int): String

    fun eventIntensityLabel(intensity: Int): String

    fun eventDurationLabel(duration: String): String

    fun quickLogButtonDescription(caseName: String): String

    fun quickLogUndoMessage(caseName: String): String

    fun startActionDescription(caseName: String): String

    fun stopActionDescription(caseName: String): String

    /** The "Ongoing" chip that marks a running event on every surface (spec §6). */
    val ongoingPillLabel: String

    /** Trailing summary after [ongoingPillLabel] once more than one event is running (spec §6). */
    fun ongoingCountIndicator(count: Int): String

    /** Body of the [leaveStartStopConfirmTitle] dialog — names how many events will be stopped. */
    fun leaveStartStopConfirmBody(runningCount: Int): String

    /** Body of the [enterStartStopConfirmTitle] dialog — names how many open-ended events become instant events. */
    fun enterStartStopConfirmBody(openEndedCount: Int): String

    fun bigPictureWeekDetailTitle(date: String): String

    /** Day/week detail row for a still-running event, in place of a clock time. [since] is a time if it started today, otherwise a date. */
    fun bigPictureEventOngoingSince(since: String): String

    /** Day/week detail row for a finished multi-day event, in place of a clock time. */
    fun bigPictureEventSpanRange(
        start: String,
        end: String,
    ): String

    fun bigPictureFilterCount(selected: Int): String = "$selected"

    /** The unit an expectation's Early-days progress is counted in for the occurrence metric — "events" / "entries" / "logs". */
    val expectationProgressUnitEvents: String

    /** The days-active metric's progress unit — a domain term, so identical across voices. */
    val expectationProgressUnitDaysActive: String get() = "active days"

    /** "3 of 5 events · 9 of 14 days" toward the Preliminary bar; [unit] is [expectationProgressUnitEvents] or [expectationProgressUnitDaysActive]. */
    fun expectationProgressLabel(
        observedCount: Int,
        unit: String,
        windowDays: Long,
    ): String = "$observedCount of $PRELIMINARY_MIN_EVENTS $unit${DOT_SEPARATOR}$windowDays of $PRELIMINARY_MIN_DAYS days"

    /**
     * Short comparison fragment for a Watch card's tinted Now zone — "well over expected" /
     * "far above the expected rate" / "way more than expected" depending on voice; [daysActive]
     * prepends the voice's "active" qualifier when the Watch counts active days rather than raw
     * occurrences.
     */
    fun watchComparisonLabel(
        band: ComparisonBand,
        daysActive: Boolean,
    ): String

    /** Appended to a Preliminary-tier verdict meta line by both [verdictMeta] and [verdictMetaDaysActive]. */
    val verdictPreliminaryTail: String

    /** [base] as-is for a Confident verdict, [base] plus [verdictPreliminaryTail] for a Preliminary one. */
    fun verdictMetaLine(
        base: String,
        tier: ConfidenceTier,
    ): String = if (tier == ConfidenceTier.PRELIMINARY) "$base $verdictPreliminaryTail" else base

    /** [tier] is always Preliminary or Confident here — Early Days has no meta line. */
    fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ): String

    /** The days-active counterpart of [verdictMeta] — "Based on 24 active days over 30 days." */
    fun verdictMetaDaysActive(
        tier: ConfidenceTier,
        activeDayCount: Int,
        windowDays: Long,
    ): String

    /** Badge text for a Watch card's Now zone before any verdict exists — "Early days" / "Undecided" / "Too soon to say". */
    val expectationEarlyBadgeLabel: String

    /** Badge text once a verdict exists — "Established"/"Early read" (Plain), "Confirmed"/"Suspected" (Intense), "Locked in!"/"First peek!" (Bright). */
    fun expectationTierBadgeLabel(tier: ConfidenceTier): String

    /** A Watch card's two-zone layout: the eyebrow above the title — "Watching for" / "The watch" / "Eyes on". */
    val watchDefinitionEyebrow: String

    /** The eyebrow above the tinted Now zone — "Now" / "As it stands" / "Right now". */
    val watchNowEyebrow: String

    // ---- Notifications (Phase 9, spec §11/§14) ----
    val watchesTabDescription: String
    val watchesFabDescription: String
    val watchesEmptyTitle: String
    val watchesEmptyBody: String
    val watchesEmptyCta: String

    fun watchKindLabel(kind: WatchKind): String

    /** [windowDays] only applies to [WatchKind.OFTEN]; ignored for [WatchKind.QUIET]. */
    fun watchSummary(
        kind: WatchKind,
        threshold: Int,
        windowDays: Int?,
    ): String

    fun watchFiredAgo(daysAgo: Long): String

    fun watchToggleDescription(summary: String): String

    fun watchDeleteDescription(summary: String): String

    val watchesDeleteConfirmTitle: String
    val watchesDeleteConfirmBody: String
    val watchesDeleteConfirmAction: String
    val watchesDeleteCancelAction: String
    val watchesCreateTitle: String

    /** Bell tab editor's title when editing an existing Notification, as opposed to [watchesCreateTitle]. */
    val watchesEditTitle: String
    val watchesKindPickerLabel: String
    val watchesOftenLabel: String get() = "At least"
    val watchesWindowLabel: String get() = "Within"
    val watchesWindowCustom: String get() = "Custom"
    val watchesWindowCustomHint: String get() = "Days"

    /** A lookback preset's chip label: "14 days", "2mo", "Quarter". A unit abbreviation, so identical across voices like [watchesQuietSuffix]. */
    fun watchesWindowPresetLabel(days: Int): String =
        when {
            days < WINDOW_PRESET_MONTHS_FROM_DAYS -> "$days days"
            days == WINDOW_PRESET_QUARTER_DAYS -> "Quarter"
            else -> "${days / DAYS_PER_MONTH}mo"
        }

    val watchesQuietLabel: String
    val watchesQuietSuffix: String get() = "days"
    val watchesSaveButton: String
    val watchesCancelButton: String
    val watchesDecreaseCountDescription: String
    val watchesIncreaseCountDescription: String

    /** Editor's metric-picker section label, shown only for a duration-tracking Case; options are [metricOccurrenceLabel]/[metricDaysActiveLabel]. */
    val watchesMetricLabel: String get() = "Measure"

    /** Editor's intensity-at-least picker label, shown only when the Case has intensity tracking on. */
    val watchesIntensityLabel: String get() = "Minimum intensity"

    /** The intensity-at-least toggle's accessible description — structural, identical across all three voices. */
    val watchesIntensityToggleDescription: String get() = "Filter by intensity"

    /** One intensity-level circle's accessible description, e.g. "At least 2". Structural, identical across all three voices. */
    fun watchesIntensityOptionDescription(level: Int): String = "At least $level"

    /**
     * Bell-tab card title for an OFTEN Notification — "3 times or more per week" for
     * [VerdictMetric.OCCURRENCE_COUNT], "3 active days or more per week" for
     * [VerdictMetric.DAYS_ACTIVE]; [per] is pre-labeled via a per-voice unit word.
     */
    fun watchCardTitleOften(
        threshold: Int,
        per: ExpectedPer,
        metric: VerdictMetric,
    ): String

    /** Bell-tab card title for a QUIET Notification — "Quiet for 14 days". */
    fun watchCardTitleQuiet(threshold: Int): String

    /**
     * Bell-tab card settings line — lookback plus, only on a duration-tracking Case
     * ([showMeasure]), what's being counted, plus an intensity clause when [minIntensity] is set.
     * Quiet Notifications have no settings line beyond their own card title.
     */
    fun watchSettingsLine(
        lookbackDays: Int,
        showMeasure: Boolean,
        metric: VerdictMetric,
        minIntensity: Int?,
    ): String

    /** QUIET Watch card's Now-zone line — "quiet for 3 days" (the zone's own eyebrow already says "Now"/etc., so no prefix here). */
    fun watchNowLineQuiet(silentDays: Long): String

    // ---- Notifications (Phase 9, spec §11) ----

    /** Shown in system Settings > App notifications, not in-app. */
    val notificationChannelName: String
    val notificationChannelDescription: String

    fun notificationFiredTitle(caseName: String): String

    fun checkInDueNotificationTitle(caseName: String): String

    fun checkInDueNotificationBody(silentDays: Long): String

    /** Check-in notification action buttons — [feature/notification-actions]. */
    val notificationLogAction: String
    val notificationAllQuietAction: String

    /** Title of the Android group summary that bundles HODITH's notification and check-in notifications (spec §11). */
    fun notificationsGroupSummaryTitle(count: Int): String

    val notificationsDeniedBannerMessage: String
    val notificationsDeniedBannerAction: String

    // ---- Share cards (Phase 10, spec §13) ----
    val shareOpenDescription: String

    /** The Share screen's top-bar title — structural, identical across all three voices. The share button keeps [shareOpenDescription]. */
    val shareScreenTitle: String get() = "Share"

    /** [timestamp] is the card's own generation date and time — a still-open [com.secondmonday.hodith.domain.TrendFindingKind.WENT_QUIET] finding is only true at the moment the card is made, and dating the whole card (not just that one finding) is the honest reading for every snapshot section on it. */
    fun shareCardFooter(timestamp: String): String = "counted with HODITH app${DOT_SEPARATOR}$timestamp"

    /** Intense skin's rotated corner stamp — structural, shown on the Intense skin only; never rendered under Plain/Bright. */
    val shareIntenseStampLabel: String get() = "Case File"

    // ---- Insight share card panels and hero (spec §13): the labels below are structural, identical across all three voices ----
    val shareStatMinLabel: String get() = "Min"
    val shareStatAvgLabel: String get() = "Avg"
    val shareStatMaxLabel: String get() = "Max"

    /** Gaps, Streaks and Duration titles — the card's panel headings and the Story picker's row labels alike. */
    val shareGapsTitle: String get() = "Gaps"
    val shareStreaksTitle: String get() = "Streaks"
    val shareDurationTitle: String get() = "Duration"

    /** The Story card's Tags section and its picker row: the card lists only the busiest few, so the title says so. */
    val shareTopTagsTitle: String get() = "Top tags"

    /** The headline rate's unit, drawn small after the number; [shareRateBelowOneMarker] stands in for the number under one a month. */
    val shareRatePerDayUnit: String get() = "/day"
    val shareRatePerWeekUnit: String get() = "/week"
    val shareRatePerMonthUnit: String get() = "/month"
    val shareRateBelowOneMarker: String get() = "<1"

    /** [value] is the already-formatted average, e.g. "3.4" or "3". */
    fun shareSquareIntensityAverage(value: String): String = "average $value of $INTENSITY_MAX"

    /** The voice's own word for a logged event, agreeing with [eventCount]: "event(s)", "mark(s)", "log(s)". */
    fun shareSquareEventNoun(eventCount: Int): String

    /** The hero's top line once a rate is shown, e.g. "94d observed · 31 events". */
    fun shareSquareObservedLine(
        days: Long,
        eventCount: Int,
    ): String = "${days}d observed${DOT_SEPARATOR}$eventCount ${shareSquareEventNoun(eventCount)}"

    /** The hero's top line while there is no rate yet, e.g. "12d observed"; the event count is then the headline itself. */
    fun shareSquareObservedDays(days: Long): String = "${days}d observed"

    /** The pill beside the headline rate when it moved: [priorRate] is the earlier window's rate, already formatted, in the headline's own unit. */
    fun shareSquareTrendFrom(priorRate: String): String

    /** The pill beside the headline rate when it did not move. */
    val shareSquareTrendSame: String

    /** The Gaps panel's top-right label while the went-quiet signal is live; [gap] is the compact day count, e.g. "14d". */
    fun shareSquareQuietLabel(gap: String): String

    /** The Share screen's three tabs and the heading above each card's preview — structural, identical across all three voices. */
    val shareTabSummaryLabel: String get() = "Summary"
    val shareTabInsightsLabel: String get() = "Insights"
    val shareTabHistoryLabel: String get() = "History"
    val sharePreviewLabel: String get() = "Preview"

    val shareNameFieldLabel: String get() = "Name on card"

    /** The heading over the Insights and History controls — structural, identical across all three voices. */
    val shareSectionsPickerLabel: String get() = "Include"
    val shareSectionDragHandleDescription: String

    // ---- Log Share (a second share card of the Case's actual entries, not a data export — see
    // PROGRESS.md's "Share button: add a Log Share option" item) ----

    /** [shown]/[total] when the cap (spec §13) trims the match count. */
    fun shareLogTruncationNote(
        shown: Int,
        total: Int,
    ): String

    /** Shown on the card (and the config screen's preview) when the current filter matches nothing. */
    val shareLogEmptyRangeMessage: String

    /** Sort/date-range/field-picker labels — structural, identical across all three voices. */
    val shareLogSortNewestLabel: String get() = "Newest first"
    val shareLogSortOldestLabel: String get() = "Oldest first"
    val shareLogFieldNotesLabel: String get() = "Notes"
    val shareLogFieldTagsLabel: String get() = "Tags"
    val shareLogRangeAllTimeLabel: String get() = "All time"

    /**
     * The single combined Range control (History tab's filter chip, History Share's own trigger) that
     * replaced separate From/To controls — [shareLogRangeLabel] is its label, and
     * [shareLogRangeSelectedLabel] is the compact chip's collapsed value once a range is set (the
     * actual bounds render separately via [shareLogRangeNote], since a formatted date pair didn't
     * fit the chip's own width).
     */
    val shareLogRangeLabel: String get() = "Range"
    val shareLogRangeSelectedLabel: String get() = "Selected"

    /** [from]/[to] are already-formatted dates. */
    fun shareLogRangeNote(
        from: String,
        to: String,
    ): String = "$from – $to"

    /** [com.secondmonday.hodith.widget.ListWidgetConfigureActivity] — shown every time a List
     * widget is added or reconfigured (spec §15); each instance picks its own Cases. */
    val widgetConfigureTitle: String
    val widgetConfigureBody: String
    val widgetConfigureNoCasesMessage: String
    val widgetConfigureConfirmAction: String
    val widgetConfigureSkipAction: String

    /** [com.secondmonday.hodith.widget.SingleCaseWidgetConfigureActivity] — shown every time a
     * Single-case widget is added, since each instance is bound to its own Case and there's
     * nothing to skip straight past. Reuses [widgetConfigureNoCasesMessage] and
     * [widgetConfigureSkipAction] — that copy doesn't assume single vs. multi selection. */
    val singleCaseWidgetConfigureTitle: String
    val singleCaseWidgetConfigureBody: String
    val singleCaseWidgetConfigureConfirmAction: String

    /** [com.secondmonday.hodith.widget.ListWidget]/[com.secondmonday.hodith.widget.SingleCaseWidget]'s
     * own copy. Only [PlainVoice]'s versions ever render — the widgets' chrome is fixed regardless
     * of in-app theme (DEV_PLAYBOOK.md §4) — but all three still get an entry per the Voice layer
     * rule. */
    val widgetNoCasesSelectedMessage: String

    /** [com.secondmonday.hodith.widget.SingleCaseWidget] — shown when its bound Case has been
     * deleted or archived since the widget was configured. */
    val widgetCaseNotFoundMessage: String

    fun widgetTodayCount(count: Int): String = "Today: $count"
}

object PlainVoice : Voice {
    override val homeHeaderTitle = "How often does it truly happen?"
    override val noCasesEmptyState = "No cases yet."
    override val bigPictureEarlyDays = "Insufficient data. Keep logging."
    override val bigPictureMonthPickerTitle = "Jump to month"
    override val bigPictureDayDetailEmptyState = "No events logged this day."
    override val bigPictureWeekDetailEmptyState = "No events logged this week."
    override val bigPictureWeekViewDescription = "Open week view"
    override val bigPictureDetailDialogTitle = "Row detail"
    override val bigPictureDetailEditDescription = "Edit which detail the rows show"
    override val bigPictureFilterCountAll = "All"
    override val bigPictureFilterCountNone = "None"
    override val bigPictureAllCasesLabel = "All Cases"
    override val bigPictureAllTagsLabel = "All tags"
    override val bigPictureUntaggedOnlyLabel = "Untagged only"
    override val bigPictureNoCasesSelectedNote = "No Cases selected — the calendar will be empty."
    override val bigPictureSelectAllAction = "Select all"
    override val bigPictureClearAllAction = "Clear all"
    override val comingSoonPlaceholder = "Coming soon."
    override val newCaseTitle = "New case"
    override val editCaseTitle = "Edit case"
    override val newCaseFabDescription = "New case"
    override val caseNameHint = "e.g. Kiddo was rude"
    override val caseNameRequiredError = "Name is required."
    override val caseNameDuplicateError = "A case with this name already exists."
    override val caseDescriptionHint = "Any more detail worth noting"
    override val caseIconRequiredError = "Pick an icon."
    override val caseIconSectionExpandDescription = "Show icon choices"
    override val caseIconSectionCollapseDescription = "Hide icon choices"
    override val caseSectionInfoDescription = "More info"
    override val infoDialogDismissAction = "Got it"
    override val caseLogFlowInfoTitle = "About logging"
    override val caseLogFlowInfoBody =
        "One tap logs an event instantly with no extra fields — pick it for cases you don't need duration or " +
            "intensity on. Detail sheet opens a short form for time, duration, intensity, and notes before saving."
    override val caseDurationModeInfoTitle = "About duration"
    override val caseDurationModeInfoBody =
        "None skips duration entirely. Manual lets you type a duration when logging. Start/stop tracks an " +
            "ongoing event live, from Start until you Stop it."
    override val caseCheckInInfoTitle = "About check-in"
    override val caseCheckInInfoBody =
        "When on, this case gets a check-in nudge after a stretch of silence, using Settings' default interval. " +
            "Off turns it off for this case."
    override val caseSaveButton = "Save"
    override val caseDetailEditDescription = "Edit case"
    override val archiveCaseDescription = "Archive case"
    override val archiveCaseConfirmTitle = "Archive this case?"
    override val archiveCaseConfirmBody =
        "It will be hidden from Home and Big Picture, but its data stays intact. You can restore it, or delete it forever, " +
            "from Archived Cases."
    override val archiveCaseConfirmAction = "Archive"
    override val archiveCaseCancelAction = "Cancel"
    override val leaveStartStopConfirmTitle = "Stop the running events?"
    override val leaveStartStopConfirmAction = "Stop and switch"
    override val leaveStartStopCancelAction = "Keep Start/Stop"
    override val enterStartStopConfirmTitle = "Keep existing events as instant?"
    override val enterStartStopConfirmAction = "Switch and keep them"
    override val enterStartStopCancelAction = "Keep current mode"
    override val archivedCasesTitle = "Archived cases"
    override val archivedCasesEmptyState = "No archived cases."
    override val eventListEmptyState = "No events logged yet."

    override fun logSummaryLine(
        rate: String?,
        eventCount: Int,
        observedDays: Long,
    ) = listOfNotNull(rate, "$eventCount events logged${DOT_SEPARATOR}observed for $observedDays days").joinToString(DOT_SEPARATOR)

    override val logShowMoreAction = "Show more events"

    override val deleteEventConfirmTitle = "Delete this event?"
    override val deleteEventConfirmBody = "This can't be undone."
    override val deleteEventConfirmAction = "Delete"
    override val deleteEventCancelAction = "Cancel"
    override val deleteCaseForeverConfirmTitle = "Delete this case forever?"
    override val deleteCaseForeverConfirmAction = "Delete forever"
    override val deleteCaseForeverCancelAction = "Cancel"
    override val clearArchiveButtonDescription = "Clear archive"
    override val clearArchiveConfirmTitle = "Clear the whole archive?"
    override val clearArchiveConfirmAction = "Clear archive"
    override val clearArchiveConfirmCancelAction = "Cancel"
    override val retroLogEntryDescription = "Log an event"
    override val logSheetNewEventTitle = "Log an event"
    override val logSheetEditEventTitle = "Edit event"
    override val logSheetTimeLabel = "When"
    override val logSheetIntensityLabel = "Intensity"
    override val logSheetDurationLabel = "Duration"
    override val logSheetNoteLabel = "Note (optional)"
    override val logSheetNoteHint = "Anything worth remembering"
    override val logSheetTagsLabel = "Tags"
    override val logSheetAddTagHint = "Add a tag"
    override val logSheetRemoveTagDescription = "Remove tag"
    override val logSheetSaveButton = "Save"
    override val logSheetPickerConfirm = "OK"
    override val logSheetPickerCancel = "Cancel"
    override val logSheetStartButton = "Start"
    override val logSheetEndLabel = "Ended"
    override val logSheetOngoingLabel = "Ongoing"
    override val logSheetStopNowAction = "Stop now"
    override val logSheetBackToOngoingAction = "Back to ongoing"
    override val logSheetFutureTimeNotice = "Can't be in the future."
    override val logSheetStartAfterEndNotice = "Can't start after the end time."
    override val logSheetEndBeforeStartNotice = "Can't end before the start time."
    override val quickLogUndoAction = "Undo"
    override val settingsSupportSectionLabel = "Support"
    override val settingsRateAppButton = "Rate the app"
    override val settingsContactUsButton = "Contact us"
    override val settingsAppearanceSectionLabel = "Appearance"
    override val settingsThemeSectionLabel = "Theme"
    override val settingsThemeInfoTitle = "About themes"
    override val settingsThemeInfoBody =
        "Each theme pairs its own colors with a distinct tone of voice used throughout the app."
    override val settingsTimeFormatSectionLabel = "Time format"
    override val settingsCheckInSectionLabel = "Check-ins"
    override val settingsCheckInInfoTitle = "About check-ins"
    override val settingsCheckInInfoBody =
        "How many days of silence trigger a check-in nudge, for cases with check-ins on. Off turns off the " +
            "app-wide default; individual cases can still be turned off from their edit screen."
    override val settingsDataSectionLabel = "Data"
    override val settingsCloudBackupToggleLabel = "Include HODITH in device backup"
    override val settingsCloudBackupInfoTitle = "About device backup"
    override val settingsCloudBackupInfoBody =
        "When this is on, Android's own device backup can carry HODITH's data along with everything else " +
            "on your phone, if you have phone backup turned on. Turning it off stops future backups from " +
            "including HODITH's data — it won't remove a backup that's already been made."
    override val settingsDeleteDataButton = "Delete data"
    override val settingsDeleteDataOptionsTitle = "What to delete"
    override val settingsDeleteDataOptionAll = "All data"
    override val settingsDeleteDataOptionLogsOnly = "History only"
    override val settingsDeleteDataDateLabel = "Delete history before"
    override val settingsDeleteDataOptionsNextAction = "Continue"
    override val settingsDeleteDataOptionsCancelAction = "Cancel"
    override val settingsDeleteAllDataConfirmTitle = "Delete all data?"
    override val settingsDeleteAllDataConfirmBody =
        "Every case and event will be permanently deleted. This can't be undone."
    override val settingsDeleteAllDataConfirmAction = "Delete everything"
    override val settingsDeleteAllDataCancelAction = "Cancel"
    override val settingsDeleteDataLogsConfirmTitle = "Delete this history?"

    override fun settingsDeleteDataLogsConfirmBody(dateLabel: String) =
        "Every event logged before $dateLabel will be permanently deleted. This can't be undone."

    override val settingsDeleteDataLogsConfirmAction = "Delete history"
    override val settingsExportButton = "Export data"
    override val settingsExportFormatDialogTitle = "Choose a format"
    override val settingsExportFormatJsonOption = "JSON backup"
    override val settingsExportFormatJsonDescription = "A full backup of everything, for restoring later in HODITH."
    override val settingsExportFormatCsvOption = "CSV"
    override val settingsExportFormatCsvDescription =
        "A table you can open in a spreadsheet editor like Microsoft Excel, Google Sheets, or LibreOffice."
    override val settingsExportFormatConfirmAction = "Export"
    override val settingsExportFormatCancelAction = "Cancel"
    override val settingsImportButton = "Import data"
    override val settingsImportConfirmTitle = "Replace all data?"
    override val settingsImportConfirmBody =
        "Importing will delete everything currently in the app and replace it with the backup file. This can't be undone."
    override val settingsImportConfirmAction = "Replace everything"
    override val settingsImportCancelAction = "Cancel"
    override val settingsExportSuccessMessage = "Backup saved."
    override val settingsExportFailureMessage = "Couldn't save the backup."
    override val settingsCsvExportSuccessMessage = "CSV saved."
    override val settingsCsvExportFailureMessage = "Couldn't save the CSV."
    override val settingsImportSuccessMessage = "Backup restored."
    override val settingsImportFailureInvalidMessage = "That file isn't a valid HODITH backup."
    override val settingsImportFailureVersionMessage = "That backup was made by a version of HODITH this app can't read."
    override val settingsImportFailureIoMessage = "Couldn't read that file."
    override val settingsImportFailureSemanticMessage = "That backup's data doesn't check out, so nothing was restored."
    override val settingsDeveloperModeSectionLabel = "Developer mode"
    override val settingsLoadDemoDataButton = "Load demo data"
    override val settingsDemoDataLoadedMessage = "Demo data loaded."
    override val aboutScreenTitle = "About"
    override val aboutIdeaLabel = "What HODITH is"
    override val aboutIdeaBody =
        "Sometimes a thought hits you: this always happens — or this never happens anymore. " +
            "HODITH lets you check. Open a Case on what you've noticed, log it as life happens, " +
            "and see what the data actually says."
    override val aboutDeveloperModeUnlockedMessage = "Developer mode unlocked."
    override val aboutPrivacyLabel = "Privacy"
    override val aboutPrivacyBody =
        "HODITH itself has no network access and sends nothing anywhere. But if you have your phone's own " +
            "backup turned on, it can still include HODITH's data — a toggle in Settings lets you turn that " +
            "off, though it only stops future backups, not ones already made."
    override val aboutPrivacyPolicyLinkLabel = "Read the full privacy policy"
    override val aboutLicensesLabel = "Licenses"
    override val aboutLicensesBody =
        "HODITH is built with open-source libraries — AndroidX Jetpack, Hilt, Room, Moshi, Glance, " +
            "WorkManager, and Kotlin Coroutines — each licensed under the Apache License 2.0."
    override val frequencyDecreaseCountDescription = "Decrease count"
    override val frequencyIncreaseCountDescription = "Increase count"
    override val metricOccurrenceLabel = "How often it happens"
    override val metricDaysActiveLabel = "How long it's active"

    override val insightsNothingLoggedMessage = "Log an event to see insights."
    override val insightsSingleEventNote = "One event logged so far."

    override val insightsHeatmapShowMoreAction = "Show more months"
    override val insightsHeatmapShowFewerAction = "Show fewer months"

    override val insightsDrillDownEmptyState = "No matching events logged."

    override fun insightsHeatmapDayTapDescription(dateLabel: String) = "See $dateLabel's events"

    override fun insightsIntensitySquareTapDescription(level: Int) = "See intensity $level events"

    override fun insightsTagRowTapDescription(tagName: String) = "See #$tagName events"

    override fun insightsRhythmCellTapDescription(
        dayLabel: String,
        timeOfDayLabel: String,
    ) = "See $dayLabel ${timeOfDayLabel.lowercase()} events"

    override fun insightsIntensityDrillDownTitle(level: Int) = "Intensity $level"

    override fun insightsTagDrillDownTitle(tagName: String) = "Tagged #$tagName"

    override fun insightsRhythmDrillDownTitle(
        dayLabel: String,
        timeOfDayLabel: String,
    ) = "$dayLabel ${timeOfDayLabel.lowercase()}s"

    override val insightsBurstFlagLabel = "Tends to come in bursts"

    override fun insightsTrendSentence(
        direction: TrendDirection,
        recentCount: Int,
        priorCount: Int,
    ) = when (direction) {
        TrendDirection.UP -> "$recentCount events in the last 30 days — up from $priorCount the 30 days before."
        TrendDirection.DOWN -> "$recentCount events in the last 30 days — down from $priorCount the 30 days before."
        TrendDirection.FLAT -> "$recentCount events in the last 30 days — the same as the 30 days before."
    }

    override fun insightsWentQuietSentence(
        currentGapLabel: String,
        longestPastGapLabel: String,
    ) =
        "Nothing logged in $currentGapLabel, past this Case's previous longest gap of $longestPastGapLabel. Still happening, or has it wound down?"

    override fun insightsWentQuietEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps."

    override fun insightsGapShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The average gap has grown from $priorAverageLabel to $recentAverageLabel."
        ShiftDirection.DOWN -> "The average gap has shrunk from $priorAverageLabel to $recentAverageLabel."
    }

    override fun insightsStreakShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The average streak has grown from $priorAverageLabel to $recentAverageLabel."
        ShiftDirection.DOWN -> "The average streak has shrunk from $priorAverageLabel to $recentAverageLabel."
    }

    override val insightsFrequencyInfoTitle = "About this chart"

    override fun insightsFrequencyInfoBody(granularity: FrequencyGranularity): String {
        val unit =
            when (granularity) {
                FrequencyGranularity.DAY -> "days"
                FrequencyGranularity.WEEK -> "weeks"
                FrequencyGranularity.MONTH -> "months"
            }
        return "Showing the most recent 12 $unit. The granularity is picked automatically based on how long this case " +
            "has been tracked, but you can switch it manually above."
    }

    override val insightsGapsInfoTitle = "About gaps & streaks"
    override val insightsGapsInfoBody =
        "Shortest gap: the shortest stretch between two events.\n" +
            "Longest gap: the longest stretch with no event active.\n" +
            "Current gap: time since the last event ended, or 0 while one is running.\n" +
            "Average gap: the typical stretch between events.\n" +
            "Longest streak: the most days in a row with at least one event active.\n" +
            "Average streak: the typical length of those runs.\n\n" +
            "A duration event counts on every day it was active, so a single long event can carry a streak on its own. " +
            "\"Tends to come in bursts\" shows when the gaps vary a lot."

    override val insightsTrendsShowMoreAction = "See all trends"
    override val insightsTagsSeeAllAction = "See all tags"
    override val insightsTagsDistinctLabel = "Total tags"
    override val insightsTagsInfoTitle = "About tags"
    override val insightsTagsInfoBody =
        "Each tag counts the logged events it appears on. Tap one to see those events."

    override val insightsTrendsInfoTitle = "About trends"
    override val insightsTrendsInfoBody =
        "Each row is a shift spotted somewhere in this case's own history — not a prediction, just a description of what changed.\n\n" +
            "Hint means the shift crossed a basic threshold but hasn't been checked for statistical significance yet. " +
            "Pattern means it has been checked and holds up."

    override fun insightsGapShiftEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps."

    override fun insightsStreakShiftEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount streaks."

    override fun insightsFrequencyShiftEvidenceLabel() = "Comparing the last 30 days to the 30 before."

    override fun insightsTagShareShiftSentence(
        tagName: String,
        direction: ShiftDirection,
        priorShareLabel: String,
        recentShareLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "\"$tagName\" tends to show up more lately, $recentShareLabel of events now, up from $priorShareLabel."
        ShiftDirection.DOWN -> "\"$tagName\" tends to show up less lately, $recentShareLabel of events now, down from $priorShareLabel."
    }

    override fun insightsTagShareShiftEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events."

    override fun insightsTagComboSentence(
        tagNamesLabel: String,
        count: Int,
        totalEvents: Int,
    ) = "\"$tagNamesLabel\" tend to show up together — $count of $totalEvents events."

    override fun insightsTagComboEvidenceLabel(sampleCount: Int) = "Based on those $sampleCount events."

    override fun insightsRecurrenceShapeSentence(
        direction: ShiftDirection,
        thresholdLabel: String,
        shareLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "This tends to follow quickly, $shareLabel of past gaps landing within $thresholdLabel."
        ShiftDirection.DOWN -> "This rarely follows quickly, only $shareLabel of past gaps landed within $thresholdLabel."
    }

    override fun insightsRecurrenceShapeEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps."

    override fun insightsTagOutcomeSentence(
        tagName: String,
        outcome: TagOutcome,
        direction: ShiftDirection,
        relativeDifferenceLabel: String,
        withoutTagLabel: String,
        withTagLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP ->
                    "\"$tagName\" tends to run more intense, averaging $withTagLabel vs $withoutTagLabel without it, " +
                        "up $relativeDifferenceLabel."
                ShiftDirection.DOWN ->
                    "\"$tagName\" tends to run less intense, averaging $withTagLabel vs $withoutTagLabel without it, " +
                        "down $relativeDifferenceLabel."
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP ->
                    "\"$tagName\" tends to last longer, averaging $withTagLabel vs $withoutTagLabel without it, " +
                        "up $relativeDifferenceLabel."
                ShiftDirection.DOWN ->
                    "\"$tagName\" tends to last shorter, averaging $withTagLabel vs $withoutTagLabel without it, " +
                        "down $relativeDifferenceLabel."
            }
    }

    override fun insightsTagOutcomeEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events."

    override fun insightsChangePointSentence(
        direction: ShiftDirection,
        dateLabel: String,
        priorLabel: String,
        recentLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The gap between events has grown, from $priorLabel to $recentLabel, since around $dateLabel."
        ShiftDirection.DOWN -> "The gap between events has shrunk, from $priorLabel to $recentLabel, since around $dateLabel."
    }

    override fun insightsChangePointEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps."

    override fun insightsTrendSlopeSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        priorLabel: String,
        recentLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP -> "Intensity has been climbing, averaging $recentLabel lately vs $priorLabel earlier."
                ShiftDirection.DOWN -> "Intensity has been easing, averaging $recentLabel lately vs $priorLabel earlier."
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP -> "Episodes have been running longer, averaging $recentLabel lately vs $priorLabel earlier."
                ShiftDirection.DOWN -> "Episodes have been running shorter, averaging $recentLabel lately vs $priorLabel earlier."
            }
    }

    override fun insightsTrendSlopeEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events."

    override fun insightsTimeOfDaySplitSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        dayLabel: String,
        eveningLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP -> "Evening events tend to run more intense, averaging $eveningLabel vs $dayLabel during the day."
                ShiftDirection.DOWN -> "Daytime events tend to run more intense, averaging $dayLabel vs $eveningLabel in the evening."
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP -> "Evening events tend to last longer, averaging $eveningLabel vs $dayLabel during the day."
                ShiftDirection.DOWN -> "Daytime events tend to last longer, averaging $dayLabel vs $eveningLabel in the evening."
            }
    }

    override fun insightsTimeOfDaySplitEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events."

    override fun insightsTagTimingSentence(
        tagName: String,
        bucketPhrase: String,
        baselineLabel: String,
        taggedLabel: String,
    ) = "\"$tagName\" tends to cluster $bucketPhrase, $taggedLabel of its events there vs $baselineLabel case-wide."

    override fun insightsTagTimingEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount tagged events."

    override fun insightsWeekdayWeekendSentence(
        direction: ShiftDirection,
        weekdayLabel: String,
        weekendLabel: String,
    ) = when (direction) {
        ShiftDirection.UP ->
            "This Case's events lean toward weekends: $weekendLabel of them land on Saturday or Sunday, more than the roughly 2-in-7 days you'd expect by chance."
        ShiftDirection.DOWN ->
            "This Case's events lean toward weekdays: $weekdayLabel of them land Monday through Friday, more than you'd expect by chance."
    }

    override fun insightsWeekdayWeekendEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events."

    override val insightsDurationInfoTitle = "About duration"
    override val insightsDurationInfoBody =
        "Shortest, average, and longest time are based on events that have ended. A still-running event isn't counted until it stops."

    override val insightsRhythmInfoTitle = "About rhythm"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount${DOT_SEPARATOR}This week: $weekCount"

    override fun archivedCasesLink(count: Int) = "Archived cases ($count)"

    override fun archivedCaseEventCount(count: Int) = "$count events logged"

    override fun unarchiveCaseDescription(caseName: String) = "Unarchive $caseName"

    override fun deleteCaseForeverDescription(caseName: String) = "Delete $caseName forever"

    override fun deleteCaseForeverConfirmBody(eventCount: Int) =
        "This case and its $eventCount logged events will be permanently deleted. This can't be undone."

    override fun clearArchiveConfirmBody(caseCount: Int) =
        "$caseCount archived cases and their logged events will be permanently deleted. This can't be undone."

    override fun eventIntensityLabel(intensity: Int) = "Intensity $intensity"

    override fun eventDurationLabel(duration: String) = "Duration: $duration"

    override fun quickLogButtonDescription(caseName: String) = "Log $caseName now"

    override fun quickLogUndoMessage(caseName: String) = "Logged $caseName."

    override fun startActionDescription(caseName: String) = "Start $caseName"

    override fun stopActionDescription(caseName: String) = "Stop $caseName"

    override val ongoingPillLabel = "Ongoing"

    override fun ongoingCountIndicator(count: Int) = "$count running"

    override fun leaveStartStopConfirmBody(runningCount: Int) =
        "Only Start/Stop tracks a running event, so switching away stops all $runningCount of them now, at the current time."

    override fun enterStartStopConfirmBody(openEndedCount: Int) =
        "This case has $openEndedCount events with no end time. Under Start/Stop they would look like they are still running, " +
            "so they will be kept as instant one-time events instead."

    override fun bigPictureWeekDetailTitle(date: String) = "Week of $date"

    override fun bigPictureEventOngoingSince(since: String) = "Ongoing since $since"

    override fun bigPictureEventSpanRange(
        start: String,
        end: String,
    ) = "Lasted $start – $end"

    override val expectationProgressUnitEvents = "events"

    override fun watchComparisonLabel(
        band: ComparisonBand,
        daysActive: Boolean,
    ): String {
        val phrase =
            when (band) {
                ComparisonBand.MUCH_LESS -> "well under expected"
                ComparisonBand.LESS -> "a bit under expected"
                ComparisonBand.ABOUT_RIGHT -> "about right"
                ComparisonBand.MORE -> "a bit over expected"
                ComparisonBand.MUCH_MORE -> "well over expected"
            }
        return if (daysActive) "active $phrase" else phrase
    }

    override val verdictPreliminaryTail = "A few more weeks will sharpen this."

    override fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ) = verdictMetaLine("Based on $eventCount events over $windowDays days.", tier)

    override fun verdictMetaDaysActive(
        tier: ConfidenceTier,
        activeDayCount: Int,
        windowDays: Long,
    ) = verdictMetaLine("Based on $activeDayCount active days over $windowDays days.", tier)

    override val expectationEarlyBadgeLabel = "Early days"

    override fun expectationTierBadgeLabel(tier: ConfidenceTier) =
        when (tier) {
            ConfidenceTier.PRELIMINARY -> "Early read"
            else -> "Established"
        }

    override val watchDefinitionEyebrow = "Watching for"
    override val watchNowEyebrow = "Now"

    override val watchesTabDescription = "Rules"
    override val watchesFabDescription = "New rule"
    override val watchesEmptyTitle = "Nothing set up yet"
    override val watchesEmptyBody = "Set up a rule to alert you when this happens too often, or goes quiet for too long."
    override val watchesEmptyCta = "Set up a rule"

    override fun watchKindLabel(kind: WatchKind) =
        when (kind) {
            WatchKind.OFTEN -> "Happens too often"
            WatchKind.QUIET -> "Goes quiet too long"
        }

    override fun watchSummary(
        kind: WatchKind,
        threshold: Int,
        windowDays: Int?,
    ) = when (kind) {
        WatchKind.OFTEN -> "$threshold+ times in $windowDays days"
        WatchKind.QUIET -> "No events for $threshold days"
    }

    override fun watchFiredAgo(daysAgo: Long) = if (daysAgo == 0L) "Fired today" else "Fired $daysAgo days ago"

    override fun watchToggleDescription(summary: String) = "Toggle rule: $summary"

    override fun watchDeleteDescription(summary: String) = "Delete rule: $summary"

    override val watchesDeleteConfirmTitle = "Delete this rule?"
    override val watchesDeleteConfirmBody = "You won't be notified by it anymore."
    override val watchesDeleteConfirmAction = "Delete"
    override val watchesDeleteCancelAction = "Cancel"
    override val watchesCreateTitle = "Set up a rule"
    override val watchesEditTitle = "Edit this rule"
    override val watchesKindPickerLabel = "What should set it off?"
    override val watchesQuietLabel = "No events for"
    override val watchesSaveButton = "Save"
    override val watchesCancelButton = "Cancel"
    override val watchesDecreaseCountDescription = "Decrease threshold"
    override val watchesIncreaseCountDescription = "Increase threshold"

    override fun watchCardTitleOften(
        threshold: Int,
        per: ExpectedPer,
        metric: VerdictMetric,
    ) = when (metric) {
        VerdictMetric.OCCURRENCE_COUNT -> "$threshold times or more per ${perPhrase(per)}"
        VerdictMetric.DAYS_ACTIVE -> "$threshold active days or more per ${perPhrase(per)}"
    }

    override fun watchCardTitleQuiet(threshold: Int) = "No events for $threshold days"

    override fun watchSettingsLine(
        lookbackDays: Int,
        showMeasure: Boolean,
        metric: VerdictMetric,
        minIntensity: Int?,
    ): String {
        val parts = mutableListOf("Looking back $lookbackDays days")
        if (showMeasure) {
            parts += if (metric == VerdictMetric.DAYS_ACTIVE) "counting days active" else "counting times"
        }
        if (minIntensity != null) parts += "intensity $minIntensity+"
        return parts.joinToString(DOT_SEPARATOR)
    }

    override fun watchNowLineQuiet(silentDays: Long) = if (silentDays == 0L) "logged today" else "quiet for $silentDays days"

    override val notificationChannelName = "Notifications"
    override val notificationChannelDescription = "Notification and check-in alerts."

    override fun notificationFiredTitle(caseName: String) = "$caseName rule"

    override fun checkInDueNotificationTitle(caseName: String) = "$caseName check-in"

    override fun checkInDueNotificationBody(silentDays: Long) =
        "Nothing logged in $silentDays days. Has it stopped, or just gone unrecorded?"

    override val notificationLogAction = "Log"
    override val notificationAllQuietAction = "All quiet"

    override fun notificationsGroupSummaryTitle(count: Int) = "$count cases need a look — tap to review"

    override val notificationsDeniedBannerMessage =
        "Notifications are off, so rules and check-ins won't alert you — check back here instead."
    override val notificationsDeniedBannerAction = "Turn on notifications"

    override val widgetConfigureTitle = "Pick Cases for this widget"
    override val widgetConfigureBody = "Choose which Cases show up here. Long-press this widget and tap Edit to change your picks later."
    override val widgetConfigureNoCasesMessage = "No cases yet. Add one in the app first."
    override val widgetConfigureConfirmAction = "Add to widget"
    override val widgetConfigureSkipAction = "Cancel"

    override val singleCaseWidgetConfigureTitle = "Pick a Case for this widget"
    override val singleCaseWidgetConfigureBody = "Choose which Case shows up here. Add another widget to track a different one."
    override val singleCaseWidgetConfigureConfirmAction = "Add to widget"

    override val widgetNoCasesSelectedMessage = "No Cases picked for this widget yet. Long-press it and tap Edit to choose some."
    override val widgetCaseNotFoundMessage = "This Case is gone. Tap to open HODITH."

    override val shareOpenDescription = "Share"

    override fun shareSquareEventNoun(eventCount: Int) = if (eventCount == 1) "event" else "events"

    override fun shareSquareTrendFrom(priorRate: String) = "from $priorRate a month ago"

    override val shareSquareTrendSame = "same as a month ago"

    override fun shareSquareQuietLabel(gap: String) = "Quiet for $gap"

    override val shareSectionDragHandleDescription = "Drag to reorder"

    override fun shareLogTruncationNote(
        shown: Int,
        total: Int,
    ) = "Showing the most recent $shown of $total. Narrow the range to include more."

    override val shareLogEmptyRangeMessage = "No entries in this range."

    override val logDetailEditDescription = "Edit which detail the history shows"
    override val logDetailDialogTitle = "History detail"
}

object IntenseVoice : Voice {
    override val homeHeaderTitle = "How oft dares it truly haunt?"
    override val noCasesEmptyState = "Nothing is being watched. Yet."
    override val bigPictureEarlyDays = "The evidence is yet insufficient for despair or joy."
    override val bigPictureMonthPickerTitle = "Leap to another month"
    override val bigPictureDayDetailEmptyState = "Nothing was recorded this day."
    override val bigPictureWeekDetailEmptyState = "Nothing was recorded this week."
    override val bigPictureWeekViewDescription = "Unveil the week"
    override val bigPictureDetailDialogTitle = "What each entry reveals"
    override val bigPictureDetailEditDescription = "Choose what each entry reveals"
    override val bigPictureFilterCountAll = "Every one"
    override val bigPictureFilterCountNone = "Not one"
    override val bigPictureAllCasesLabel = "Every Case"
    override val bigPictureAllTagsLabel = "Every tag"
    override val bigPictureUntaggedOnlyLabel = "Unmarked only"
    override val bigPictureNoCasesSelectedNote = "No Cases stand watch — the calendar stays blank."
    override val bigPictureSelectAllAction = "Mark every one"
    override val bigPictureClearAllAction = "Clear every mark"
    override val comingSoonPlaceholder = "Not yet manifest."
    override val newCaseTitle = "Open a new case"
    override val editCaseTitle = "Revise the case"
    override val newCaseFabDescription = "Open a new case"
    override val caseNameHint = "e.g. The migraine returns"
    override val caseNameRequiredError = "It needs a name to be watched."
    override val caseNameDuplicateError = "Another case already bears this name."
    override val caseDescriptionHint = "Say more, if the shadows require it"
    override val caseIconRequiredError = "Choose a mark for it."
    override val caseIconSectionExpandDescription = "Reveal the marks"
    override val caseIconSectionCollapseDescription = "Conceal the marks"
    override val caseSectionInfoDescription = "Unveil more"
    override val infoDialogDismissAction = "Understood"
    override val caseLogFlowInfoTitle = "On the manner of recording"
    override val caseLogFlowInfoBody =
        "One tap seals the record the instant you touch it — no further rite required. The detail sheet asks " +
            "more of you: the hour, its length, its severity, its notes — reserved for cases that demand such detail."
    override val caseDurationModeInfoTitle = "On the length of things"
    override val caseDurationModeInfoBody =
        "None takes no account of how long a thing lingers. Manual lets you name its length yourself. " +
            "Start/stop watches it unfold in real time, from the moment it begins until you declare it done."
    override val caseCheckInInfoTitle = "On the watch kept"
    override val caseCheckInInfoBody =
        "When kept, the check-in nudge stirs after this case has lain silent too long, the interval Settings " +
            "decree for all cases. Off silences the nudge for this case alone."
    override val caseSaveButton = "Seal it"
    override val caseDetailEditDescription = "Revise the case"
    override val archiveCaseDescription = "Bury this case"
    override val archiveCaseConfirmTitle = "Bury this case?"
    override val archiveCaseConfirmBody =
        "It will vanish from Home and the record, but nothing is lost — it waits in the archive, ready to be exhumed, " +
            "or erased forever if you so choose."
    override val archiveCaseConfirmAction = "Bury it"
    override val archiveCaseCancelAction = "Abandon"
    override val leaveStartStopConfirmTitle = "Seal what still runs?"
    override val leaveStartStopConfirmAction = "Seal them and switch"
    override val leaveStartStopCancelAction = "Leave Start/Stop be"
    override val enterStartStopConfirmTitle = "Fix them in place?"
    override val enterStartStopConfirmAction = "Fix them and switch"
    override val enterStartStopCancelAction = "Leave the mode as it lies"
    override val archivedCasesTitle = "The buried cases"
    override val archivedCasesEmptyState = "Nothing lies buried here."
    override val eventListEmptyState = "No evidence gathered yet."

    override fun logSummaryLine(
        rate: String?,
        eventCount: Int,
        observedDays: Long,
    ) = listOfNotNull(rate, "$eventCount marks in the record — $observedDays days under watch").joinToString(DOT_SEPARATOR)

    override val logShowMoreAction = "Exhume more of the record"

    override val deleteEventConfirmTitle = "Strike this from the record?"
    override val deleteEventConfirmBody = "Once gone, it cannot be recalled."
    override val deleteEventConfirmAction = "Erase"
    override val deleteEventCancelAction = "Abandon"
    override val deleteCaseForeverConfirmTitle = "Erase this case forever?"
    override val deleteCaseForeverConfirmAction = "Erase forever"
    override val deleteCaseForeverCancelAction = "Abandon"
    override val clearArchiveButtonDescription = "Erase the archive"
    override val clearArchiveConfirmTitle = "Erase the whole archive?"
    override val clearArchiveConfirmAction = "Erase archive"
    override val clearArchiveConfirmCancelAction = "Abandon"
    override val retroLogEntryDescription = "Record the evidence"
    override val logSheetNewEventTitle = "Record the evidence"
    override val logSheetEditEventTitle = "Amend the record"
    override val logSheetTimeLabel = "The hour it happened"
    override val logSheetIntensityLabel = "Severity"
    override val logSheetDurationLabel = "How long it lingered"
    override val logSheetNoteLabel = "Notes (optional)"
    override val logSheetNoteHint = "Whatever the shadows recall"
    override val logSheetTagsLabel = "Marks"
    override val logSheetAddTagHint = "Name a mark"
    override val logSheetRemoveTagDescription = "Strike this mark"
    override val logSheetSaveButton = "Commit to the record"
    override val logSheetPickerConfirm = "So be it"
    override val logSheetPickerCancel = "Retreat"
    override val logSheetStartButton = "Begin"
    override val logSheetEndLabel = "The hour it ended"
    override val logSheetOngoingLabel = "Still unfolding"
    override val logSheetStopNowAction = "Seal it now"
    override val logSheetBackToOngoingAction = "Unseal it — still unfolding"
    override val logSheetFutureTimeNotice = "No hour ahead of this one can be claimed."
    override val logSheetStartAfterEndNotice = "A beginning cannot follow its own end."
    override val logSheetEndBeforeStartNotice = "An end cannot precede its own beginning."
    override val quickLogUndoAction = "Reverse it"
    override val settingsSupportSectionLabel = "The outside world"
    override val settingsRateAppButton = "Render a verdict"
    override val settingsContactUsButton = "Send word"
    override val settingsAppearanceSectionLabel = "The face it wears"
    override val settingsThemeSectionLabel = "The chosen skin"
    override val settingsThemeInfoTitle = "On the chosen skin"
    override val settingsThemeInfoBody =
        "Each skin carries its own hues — and its own tongue. Change it, and the words themselves change shape."
    override val settingsTimeFormatSectionLabel = "The reckoning of hours"
    override val settingsCheckInSectionLabel = "The watch kept"
    override val settingsCheckInInfoTitle = "On the watch kept"
    override val settingsCheckInInfoBody =
        "How many days of silence rouse a check-in nudge, for any case keeping the watch. Off lays the " +
            "app-wide watch to rest; a single case's watch can still be silenced from its own page."
    override val settingsDataSectionLabel = "The archive"
    override val settingsCloudBackupToggleLabel = "Let the archive travel"
    override val settingsCloudBackupInfoTitle = "On letting it travel"
    override val settingsCloudBackupInfoBody =
        "Left open, the phone's own reckoning carries a copy of this archive beyond these walls, wherever " +
            "its backup already goes. Close it, and no new copy leaves — but what has already gone cannot " +
            "be summoned home."
    override val settingsDeleteDataButton = "Erase data"
    override val settingsDeleteDataOptionsTitle = "What to erase"
    override val settingsDeleteDataOptionAll = "Every record"
    override val settingsDeleteDataOptionLogsOnly = "Old records only"
    override val settingsDeleteDataDateLabel = "Strike records before"
    override val settingsDeleteDataOptionsNextAction = "Proceed"
    override val settingsDeleteDataOptionsCancelAction = "Abandon"
    override val settingsDeleteAllDataConfirmTitle = "Erase everything?"
    override val settingsDeleteAllDataConfirmBody =
        "Every case and record will be struck from existence, beyond recall."
    override val settingsDeleteAllDataConfirmAction = "Erase it all"
    override val settingsDeleteAllDataCancelAction = "Abandon"
    override val settingsDeleteDataLogsConfirmTitle = "Strike these records?"

    override fun settingsDeleteDataLogsConfirmBody(dateLabel: String) =
        "Every record before $dateLabel will be struck from existence, beyond recall."

    override val settingsDeleteDataLogsConfirmAction = "Strike them"
    override val settingsExportButton = "Copy the case files"
    override val settingsExportFormatDialogTitle = "Choose a form"
    override val settingsExportFormatJsonOption = "Full case file"
    override val settingsExportFormatJsonDescription = "Every record, kept whole. The only form HODITH restores from."
    override val settingsExportFormatCsvOption = "Transcribed table"
    override val settingsExportFormatCsvDescription =
        "A ledger you can open in Microsoft Excel, Google Sheets, or LibreOffice."
    override val settingsExportFormatConfirmAction = "Copy it"
    override val settingsExportFormatCancelAction = "Abandon"
    override val settingsImportButton = "Restore the case files"
    override val settingsImportConfirmTitle = "Erase the present for the past?"
    override val settingsImportConfirmBody =
        "Every case and record here will be struck out, replaced by whatever's in that file. There's no undoing it."
    override val settingsImportConfirmAction = "Restore it"
    override val settingsImportCancelAction = "Abandon"
    override val settingsExportSuccessMessage = "The case files are copied."
    override val settingsExportFailureMessage = "The case files couldn't be copied."
    override val settingsCsvExportSuccessMessage = "The case files are transcribed."
    override val settingsCsvExportFailureMessage = "The case files could not be transcribed."
    override val settingsImportSuccessMessage = "The case files are restored."
    override val settingsImportFailureInvalidMessage = "That file holds no case files this app recognizes."
    override val settingsImportFailureVersionMessage = "That file was sealed by a version of this app no longer spoken here."
    override val settingsImportFailureIoMessage = "That file could not be read."
    override val settingsImportFailureSemanticMessage = "That file's records don't hold together — nothing here can be trusted to restore."
    override val settingsDeveloperModeSectionLabel = "Behind the curtain"
    override val settingsLoadDemoDataButton = "Conjure phantom cases"
    override val settingsDemoDataLoadedMessage = "The phantoms have arrived."
    override val aboutScreenTitle = "The record"
    override val aboutIdeaLabel = "The premise"
    override val aboutIdeaBody =
        "A thought lands: this always happens. This never happens anymore. You don't actually know. " +
            "Open a Case. Log the evidence. Let the Verdict speak."
    override val aboutDeveloperModeUnlockedMessage = "The curtain has fallen. What lies behind is yours now."
    override val aboutPrivacyLabel = "What leaves this phone"
    override val aboutPrivacyBody =
        "Nothing leaves through us — no network, no signal sent outward. But the phone itself may still " +
            "carry a copy beyond these walls, if its own backup is left running. A ward in Settings can " +
            "seal that gate; what has already escaped, it cannot call back."
    override val aboutPrivacyPolicyLinkLabel = "Read the full accounting"
    override val aboutLicensesLabel = "Borrowed bones"
    override val aboutLicensesBody =
        "This app stands on borrowed bones: AndroidX, Hilt, Room, Moshi, Glance, WorkManager, and " +
            "Kotlin Coroutines — each bound by the Apache License 2.0."
    override val frequencyDecreaseCountDescription = "Diminish the count"
    override val frequencyIncreaseCountDescription = "Swell the count"
    override val metricOccurrenceLabel = "How often it begins"
    override val metricDaysActiveLabel = "How many days it holds"

    override val insightsNothingLoggedMessage = "Log the first piece of evidence to open the file."
    override val insightsSingleEventNote = "One piece of evidence on record so far."

    override val insightsHeatmapShowMoreAction = "Unseal the older files"
    override val insightsHeatmapShowFewerAction = "Reseal them"

    override val insightsDrillDownEmptyState = "Nothing on record matches."

    override fun insightsHeatmapDayTapDescription(dateLabel: String) = "Unseal $dateLabel"

    override fun insightsIntensitySquareTapDescription(level: Int) = "Unseal intensity $level entries"

    override fun insightsTagRowTapDescription(tagName: String) = "Unseal #$tagName entries"

    override fun insightsRhythmCellTapDescription(
        dayLabel: String,
        timeOfDayLabel: String,
    ) = "Unseal $dayLabel ${timeOfDayLabel.lowercase()} entries"

    override fun insightsIntensityDrillDownTitle(level: Int) = "Marked intensity $level"

    override fun insightsTagDrillDownTitle(tagName: String) = "Marked #$tagName"

    override fun insightsRhythmDrillDownTitle(
        dayLabel: String,
        timeOfDayLabel: String,
    ) = "Marked $dayLabel ${timeOfDayLabel.lowercase()}s"

    override val insightsBurstFlagLabel = "It comes in waves, not a rhythm"

    override fun insightsTrendSentence(
        direction: TrendDirection,
        recentCount: Int,
        priorCount: Int,
    ) = when (direction) {
        TrendDirection.UP -> "$recentCount marks in the last thirty days — risen from $priorCount before. It quickens."
        TrendDirection.DOWN -> "$recentCount marks in the last thirty days — fallen from $priorCount before. It recedes, for now."
        TrendDirection.FLAT -> "$recentCount marks in the last thirty days — unchanged from what came before. Steady, as ever."
    }

    override fun insightsWentQuietSentence(
        currentGapLabel: String,
        longestPastGapLabel: String,
    ) =
        "Silence for $currentGapLabel now, longer than this Case has ever gone quiet before ($longestPastGapLabel was the old record). Still unfolding, or has it already ended?"

    override fun insightsWentQuietEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount silences."

    override fun insightsGapShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The silences have lengthened, from $priorAverageLabel to $recentAverageLabel."
        ShiftDirection.DOWN -> "The silences have shortened, from $priorAverageLabel to $recentAverageLabel."
    }

    override fun insightsStreakShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The waking spells now run from $priorAverageLabel to $recentAverageLabel."
        ShiftDirection.DOWN -> "The waking spells have shrunk from $priorAverageLabel to $recentAverageLabel."
    }

    override val insightsFrequencyInfoTitle = "On the shape of this record"

    override fun insightsFrequencyInfoBody(granularity: FrequencyGranularity): String {
        val unit =
            when (granularity) {
                FrequencyGranularity.DAY -> "days"
                FrequencyGranularity.WEEK -> "weeks"
                FrequencyGranularity.MONTH -> "months"
            }
        return "Twelve $unit, no further back — the record does not dwell on distant history. Its grain is chosen by how " +
            "long this case has been watched, though you may set it yourself above."
    }

    override val insightsGapsInfoTitle = "On silences and spells"
    override val insightsGapsInfoBody =
        "Shortest gap: the briefest silence between two events.\n" +
            "Longest gap: the longest silence with nothing stirring.\n" +
            "Current gap: how long since the last event ended, or nothing while one still runs.\n" +
            "Average gap: the usual quiet between events.\n" +
            "Longest streak: the most consecutive days something was active.\n" +
            "Average streak: how long those spells tend to last.\n\n" +
            "An event with duration marks every day it was active, so one long event can hold a streak alone. " +
            "\"It comes in waves, not a rhythm\" appears when the gaps are wildly uneven."

    override val insightsTrendsShowMoreAction = "Read the full record"
    override val insightsTagsSeeAllAction = "Read the full tally"
    override val insightsTagsDistinctLabel = "Tags in total"
    override val insightsTagsInfoTitle = "On the tags"
    override val insightsTagsInfoBody =
        "Each tag counts the events it appears on. Tap one to read them."

    override val insightsTrendsInfoTitle = "On what these mean"
    override val insightsTrendsInfoBody =
        "Each line names a shift found somewhere in this case's own past. It is a description, not a forecast.\n\n" +
            "A hint has crossed a threshold, nothing more. A pattern has been tested, and holds."

    override fun insightsGapShiftEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount silences."

    override fun insightsStreakShiftEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount waking spells."

    override fun insightsFrequencyShiftEvidenceLabel() = "Weighed against the thirty days before."

    override fun insightsTagShareShiftSentence(
        tagName: String,
        direction: ShiftDirection,
        priorShareLabel: String,
        recentShareLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "\"$tagName\" claims more of the record now, $recentShareLabel of entries, up from $priorShareLabel."
        ShiftDirection.DOWN -> "\"$tagName\" claims less of the record now, $recentShareLabel of entries, down from $priorShareLabel."
    }

    override fun insightsTagShareShiftEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount entries."

    override fun insightsTagComboSentence(
        tagNamesLabel: String,
        count: Int,
        totalEvents: Int,
    ) = "\"$tagNamesLabel\" keep appearing as one — $count of $totalEvents entries carry both."

    override fun insightsTagComboEvidenceLabel(sampleCount: Int) = "Drawn from those $sampleCount entries."

    override fun insightsRecurrenceShapeSentence(
        direction: ShiftDirection,
        thresholdLabel: String,
        shareLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "It returns quickly more often than not, $shareLabel of past silences ending within $thresholdLabel."
        ShiftDirection.DOWN -> "It rarely returns quickly, only $shareLabel of past silences ended within $thresholdLabel."
    }

    override fun insightsRecurrenceShapeEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount silences."

    override fun insightsTagOutcomeSentence(
        tagName: String,
        outcome: TagOutcome,
        direction: ShiftDirection,
        relativeDifferenceLabel: String,
        withoutTagLabel: String,
        withTagLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP ->
                    "\"$tagName\" cuts deeper, $withTagLabel against $withoutTagLabel without it, up $relativeDifferenceLabel."
                ShiftDirection.DOWN ->
                    "\"$tagName\" cuts less deep, $withTagLabel against $withoutTagLabel without it, down $relativeDifferenceLabel."
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP ->
                    "\"$tagName\" lingers longer, $withTagLabel against $withoutTagLabel without it, up $relativeDifferenceLabel."
                ShiftDirection.DOWN ->
                    "\"$tagName\" passes quicker, $withTagLabel against $withoutTagLabel without it, down $relativeDifferenceLabel."
            }
    }

    override fun insightsTagOutcomeEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount entries."

    override fun insightsChangePointSentence(
        direction: ShiftDirection,
        dateLabel: String,
        priorLabel: String,
        recentLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The silence between them has stretched, from $priorLabel to $recentLabel, since around $dateLabel."
        ShiftDirection.DOWN -> "The silence between them has drawn tighter, from $priorLabel to $recentLabel, since around $dateLabel."
    }

    override fun insightsChangePointEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount gaps."

    override fun insightsTrendSlopeSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        priorLabel: String,
        recentLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP -> "It's been cutting deeper lately, averaging $recentLabel against $priorLabel before."
                ShiftDirection.DOWN -> "It's been cutting less deep lately, averaging $recentLabel against $priorLabel before."
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP -> "It's been lingering longer lately, averaging $recentLabel against $priorLabel before."
                ShiftDirection.DOWN -> "It's been passing quicker lately, averaging $recentLabel against $priorLabel before."
            }
    }

    override fun insightsTrendSlopeEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount entries."

    override fun insightsTimeOfDaySplitSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        dayLabel: String,
        eveningLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP -> "The evening hits harder, $eveningLabel against $dayLabel by day."
                ShiftDirection.DOWN -> "The day hits harder, $dayLabel against $eveningLabel by evening."
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP -> "The evening lingers longer, $eveningLabel against $dayLabel by day."
                ShiftDirection.DOWN -> "The day lingers longer, $dayLabel against $eveningLabel by evening."
            }
    }

    override fun insightsTimeOfDaySplitEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount entries."

    override fun insightsTagTimingSentence(
        tagName: String,
        bucketPhrase: String,
        baselineLabel: String,
        taggedLabel: String,
    ) = "\"$tagName\" gathers $bucketPhrase, $taggedLabel there against $baselineLabel case-wide."

    override fun insightsTagTimingEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount tagged entries."

    override fun insightsWeekdayWeekendSentence(
        direction: ShiftDirection,
        weekdayLabel: String,
        weekendLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The weekend pulls harder, $weekendLabel of its events landing there against what chance alone allows."
        ShiftDirection.DOWN -> "The week itself pulls harder, $weekdayLabel of its events landing on a weekday, more than chance allows."
    }

    override fun insightsWeekdayWeekendEvidenceLabel(sampleCount: Int) = "Drawn from the last $sampleCount entries."

    override val insightsDurationInfoTitle = "On what is counted"
    override val insightsDurationInfoBody =
        "Shortest, average, and longest are drawn only from what has already ended. What still runs is not counted until it is done."

    override val insightsRhythmInfoTitle = "On the pull of the clock"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount — this week: $weekCount"

    override fun archivedCasesLink(count: Int) = "The buried ($count)"

    override fun archivedCaseEventCount(count: Int) = "$count entries in the record"

    override fun unarchiveCaseDescription(caseName: String) = "Exhume $caseName"

    override fun deleteCaseForeverDescription(caseName: String) = "Erase $caseName forever"

    override fun deleteCaseForeverConfirmBody(eventCount: Int) = "This case and its $eventCount entries will be erased beyond recall."

    override fun clearArchiveConfirmBody(caseCount: Int) = "$caseCount buried cases and their entries will be erased beyond recall."

    override fun eventIntensityLabel(intensity: Int) = "Intensity: $intensity"

    override fun eventDurationLabel(duration: String) = "Lasted: $duration"

    override fun quickLogButtonDescription(caseName: String) = "Add $caseName to the record"

    override fun quickLogUndoMessage(caseName: String) = "$caseName entered into the record."

    override fun startActionDescription(caseName: String) = "Begin $caseName"

    override fun stopActionDescription(caseName: String) = "Seal $caseName"

    override val ongoingPillLabel = "Still unfolding"

    override fun ongoingCountIndicator(count: Int) = "$count still unfolding"

    override fun leaveStartStopConfirmBody(runningCount: Int) =
        "Only Start/Stop keeps a thread open, so leaving it seals all $runningCount that still run — here, now, at this very moment."

    override fun enterStartStopConfirmBody(openEndedCount: Int) =
        "$openEndedCount events end nowhere. Start/Stop would read them as still breathing, so each is fixed to the single moment it happened instead."

    override fun bigPictureWeekDetailTitle(date: String) = "The week of $date"

    override fun bigPictureEventOngoingSince(since: String) = "Unfolding since $since"

    override fun bigPictureEventSpanRange(
        start: String,
        end: String,
    ) = "Ran $start to $end"

    override val expectationProgressUnitEvents = "entries"

    override fun watchComparisonLabel(
        band: ComparisonBand,
        daysActive: Boolean,
    ): String {
        val phrase =
            when (band) {
                ComparisonBand.MUCH_LESS -> "far below the expected rate"
                ComparisonBand.LESS -> "a little below the expected rate"
                ComparisonBand.ABOUT_RIGHT -> "near enough to the expected rate"
                ComparisonBand.MORE -> "a little above the expected rate"
                ComparisonBand.MUCH_MORE -> "far above the expected rate"
            }
        return if (daysActive) "active $phrase" else phrase
    }

    override val verdictPreliminaryTail = "More time will harden this into certainty."

    override fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ) = verdictMetaLine("$eventCount entries over $windowDays days.", tier)

    override fun verdictMetaDaysActive(
        tier: ConfidenceTier,
        activeDayCount: Int,
        windowDays: Long,
    ) = verdictMetaLine("$activeDayCount active days over $windowDays days.", tier)

    override val expectationEarlyBadgeLabel = "Undecided"

    override fun expectationTierBadgeLabel(tier: ConfidenceTier) =
        when (tier) {
            ConfidenceTier.PRELIMINARY -> "Suspected"
            else -> "Confirmed"
        }

    override val watchDefinitionEyebrow = "The watch"
    override val watchNowEyebrow = "As it stands"

    override val watchesTabDescription = "Alarms"
    override val watchesFabDescription = "Set a new alarm"
    override val watchesEmptyTitle = "No alarm is set"
    override val watchesEmptyBody =
        "Set an alarm and the record will warn you — the moment this happens too often, or falls silent too long."
    override val watchesEmptyCta = "Set an alarm"

    override fun watchKindLabel(kind: WatchKind) =
        when (kind) {
            WatchKind.OFTEN -> "It comes too often"
            WatchKind.QUIET -> "It falls silent too long"
        }

    override fun watchSummary(
        kind: WatchKind,
        threshold: Int,
        windowDays: Int?,
    ) = when (kind) {
        WatchKind.OFTEN -> "$threshold or more, within $windowDays days"
        WatchKind.QUIET -> "$threshold days of silence"
    }

    override fun watchFiredAgo(daysAgo: Long) = if (daysAgo == 0L) "Sounded today" else "Sounded $daysAgo days ago"

    override fun watchToggleDescription(summary: String) = "Toggle the alarm: $summary"

    override fun watchDeleteDescription(summary: String) = "Silence the alarm: $summary"

    override val watchesDeleteConfirmTitle = "Silence this alarm?"
    override val watchesDeleteConfirmBody = "It will warn you no longer."
    override val watchesDeleteConfirmAction = "Silence it"
    override val watchesDeleteCancelAction = "Abandon"
    override val watchesCreateTitle = "Set an alarm"
    override val watchesEditTitle = "Tend the alarm"
    override val watchesKindPickerLabel = "What should you be warned of?"
    override val watchesQuietLabel = "Silence of"
    override val watchesSaveButton = "Save"
    override val watchesCancelButton = "Abandon"
    override val watchesDecreaseCountDescription = "Diminish the threshold"
    override val watchesIncreaseCountDescription = "Swell the threshold"

    override fun watchCardTitleOften(
        threshold: Int,
        per: ExpectedPer,
        metric: VerdictMetric,
    ) = when (metric) {
        VerdictMetric.OCCURRENCE_COUNT -> "$threshold times or more, per ${perPhrase(per)}"
        VerdictMetric.DAYS_ACTIVE -> "$threshold active days or more, per ${perPhrase(per)}"
    }

    override fun watchCardTitleQuiet(threshold: Int) = "$threshold days of silence"

    override fun watchSettingsLine(
        lookbackDays: Int,
        showMeasure: Boolean,
        metric: VerdictMetric,
        minIntensity: Int?,
    ): String {
        val parts = mutableListOf("Watching the last $lookbackDays days")
        if (showMeasure) {
            parts += if (metric == VerdictMetric.DAYS_ACTIVE) "counting active days" else "counting every entry"
        }
        if (minIntensity != null) parts += "intensity $minIntensity or worse"
        return parts.joinToString(DOT_SEPARATOR)
    }

    override fun watchNowLineQuiet(silentDays: Long) = if (silentDays == 0L) "stirred today" else "$silentDays days of silence"

    override val notificationChannelName = "Alarms"
    override val notificationChannelDescription = "What has stirred, and what has gone quiet."

    override fun notificationFiredTitle(caseName: String) = "$caseName has stirred"

    override fun checkInDueNotificationTitle(caseName: String) = "$caseName has gone quiet"

    override fun checkInDueNotificationBody(silentDays: Long) = "$silentDays days of silence. Has it stopped, or have you?"

    override val notificationLogAction = "Log it"
    override val notificationAllQuietAction = "All is still"

    override fun notificationsGroupSummaryTitle(count: Int) = "$count cases stir — see which"

    override val notificationsDeniedBannerMessage =
        "Notifications are silenced. Alarms and the watch kept will not reach you — only what you find here."
    override val notificationsDeniedBannerAction = "Break the silence"

    override val widgetConfigureTitle = "Which cases shall haunt this widget?"
    override val widgetConfigureBody = "Choose what stands watch here. Hold the widget and choose Edit to summon different watchers later."
    override val widgetConfigureNoCasesMessage = "Nothing yet exists to watch. Summon a case first."
    override val widgetConfigureConfirmAction = "Bind to widget"
    override val widgetConfigureSkipAction = "Abandon"

    override val singleCaseWidgetConfigureTitle = "Which case shall haunt this widget?"
    override val singleCaseWidgetConfigureBody = "Choose what stands watch here. Summon another widget to keep watch over something else."
    override val singleCaseWidgetConfigureConfirmAction = "Bind to widget"

    override val widgetNoCasesSelectedMessage = "Nothing stands watch here yet. Hold the widget and choose Edit to summon one."
    override val widgetCaseNotFoundMessage = "This watch has ended. Tap to return to HODITH."

    override val shareOpenDescription = "Share the record"

    override fun shareSquareEventNoun(eventCount: Int) = if (eventCount == 1) "mark" else "marks"

    override fun shareSquareTrendFrom(priorRate: String) = "from $priorRate a month prior"

    override val shareSquareTrendSame = "same as a month prior"

    override fun shareSquareQuietLabel(gap: String) = "Silent for $gap"

    override val shareSectionDragHandleDescription = "Drag to rearrange"

    override fun shareLogTruncationNote(
        shown: Int,
        total: Int,
    ) = "$shown of $total entered into evidence. Narrow the range for the rest."

    override val shareLogEmptyRangeMessage = "No evidence in this window."

    override val logDetailEditDescription = "Edit which detail the record shows"
    override val logDetailDialogTitle = "Record detail"
}

object BrightVoice : Voice {
    override val homeHeaderTitle = "How often does it totally happen?!"
    override val noCasesEmptyState = "It's quiet in here… suspiciously quiet."
    override val bigPictureEarlyDays = "Too soon to tell — feed me more moments!"
    override val bigPictureMonthPickerTitle = "Jump to a month!"
    override val bigPictureDayDetailEmptyState = "Nothing logged this day — a blank page."
    override val bigPictureWeekDetailEmptyState = "Nothing logged this week — a blank page."
    override val bigPictureWeekViewDescription = "Peek at the week!"
    override val bigPictureDetailDialogTitle = "Row detail!"
    override val bigPictureDetailEditDescription = "Pick what shows up in each row!"
    override val bigPictureFilterCountAll = "All!"
    override val bigPictureFilterCountNone = "None!"
    override val bigPictureAllCasesLabel = "All Cases!"
    override val bigPictureAllTagsLabel = "All tags!"
    override val bigPictureUntaggedOnlyLabel = "Untagged only!"
    override val bigPictureNoCasesSelectedNote = "No Cases picked — nothing to show!"
    override val bigPictureSelectAllAction = "Select all!"
    override val bigPictureClearAllAction = "Clear all!"
    override val comingSoonPlaceholder = "Plot twist: not built yet!"
    override val newCaseTitle = "Crack open a new case"
    override val editCaseTitle = "Tweak the case"
    override val newCaseFabDescription = "Crack open a new case"
    override val caseNameHint = "e.g. Perfect coffee!"
    override val caseNameRequiredError = "Give it a name first!"
    override val caseNameDuplicateError = "You've already got a case with that name!"
    override val caseDescriptionHint = "Spill any extra details"
    override val caseIconRequiredError = "Pick a little icon for it!"
    override val caseIconSectionExpandDescription = "Show me the icons!"
    override val caseIconSectionCollapseDescription = "Tuck the icons away"
    override val caseSectionInfoDescription = "Wait, what does this mean?"
    override val infoDialogDismissAction = "Got it!"
    override val caseLogFlowInfoTitle = "Logging, explained"
    override val caseLogFlowInfoBody =
        "One tap logs it the second you tap — zero fuss, zero fields. Detail sheet pops up a quick form for " +
            "time, duration, intensity, and notes if you want more detail."
    override val caseDurationModeInfoTitle = "Duration, explained"
    override val caseDurationModeInfoBody =
        "None means duration's not tracked. Manual lets you type in how long it took. Start/stop tracks it " +
            "live — hit Start, then Stop when it's over."
    override val caseCheckInInfoTitle = "Check-in, explained"
    override val caseCheckInInfoBody =
        "Flip it on and you'll get a nudge after a quiet stretch, whatever Settings says. Off means no " +
            "nudges for this case."
    override val caseSaveButton = "Save it!"
    override val caseDetailEditDescription = "Tweak the case"
    override val archiveCaseDescription = "Shelve this case"
    override val archiveCaseConfirmTitle = "Shelve this case?"
    override val archiveCaseConfirmBody =
        "It'll hide from Home and Big Picture, but nothing's deleted here — find it in the archive to bring it back, " +
            "or to yeet it forever instead."
    override val archiveCaseConfirmAction = "Shelve it"
    override val archiveCaseCancelAction = "Nah, keep it out"
    override val leaveStartStopConfirmTitle = "Stop what's still running?"
    override val leaveStartStopConfirmAction = "Stop 'em and switch"
    override val leaveStartStopCancelAction = "Nope, keep Start/Stop!"
    override val enterStartStopConfirmTitle = "Keep the old ones as one-offs?"
    override val enterStartStopConfirmAction = "Yep, switch and keep 'em"
    override val enterStartStopCancelAction = "Nope, keep this mode!"
    override val archivedCasesTitle = "The archive"
    override val archivedCasesEmptyState = "Nothing shelved yet — tidy!"
    override val eventListEmptyState = "Nothing logged yet — the plot is thin so far."

    override fun logSummaryLine(
        rate: String?,
        eventCount: Int,
        observedDays: Long,
    ) = listOfNotNull(rate, "$eventCount logs so far, tracked for $observedDays days!").joinToString(DOT_SEPARATOR)

    override val logShowMoreAction = "Show me more!"

    override val deleteEventConfirmTitle = "Zap this event?"
    override val deleteEventConfirmBody = "Poof — no take-backs."
    override val deleteEventConfirmAction = "Zap it"
    override val deleteEventCancelAction = "Never mind"
    override val deleteCaseForeverConfirmTitle = "Delete this case for good?"
    override val deleteCaseForeverConfirmAction = "Yeet it forever"
    override val deleteCaseForeverCancelAction = "Nah, never mind"
    override val clearArchiveButtonDescription = "Clear out the archive"
    override val clearArchiveConfirmTitle = "Clear out the whole archive?"
    override val clearArchiveConfirmAction = "Yeet it all"
    override val clearArchiveConfirmCancelAction = "Nah, never mind"
    override val retroLogEntryDescription = "Log the moment"
    override val logSheetNewEventTitle = "Log the moment"
    override val logSheetEditEventTitle = "Tweak this moment"
    override val logSheetTimeLabel = "When'd it happen?"
    override val logSheetIntensityLabel = "How intense?"
    override val logSheetDurationLabel = "How long?"
    override val logSheetNoteLabel = "Note (optional)"
    override val logSheetNoteHint = "Spill the details"
    override val logSheetTagsLabel = "Tags"
    override val logSheetAddTagHint = "Slap on a tag"
    override val logSheetRemoveTagDescription = "Yeet this tag"
    override val logSheetSaveButton = "Log it!"
    override val logSheetPickerConfirm = "Yep!"
    override val logSheetPickerCancel = "Nah"
    override val logSheetStartButton = "Start it!"
    override val logSheetEndLabel = "Wrapped up at"
    override val logSheetOngoingLabel = "Still going!"
    override val logSheetStopNowAction = "Stop the clock!"
    override val logSheetBackToOngoingAction = "Actually, still going!"
    override val logSheetFutureTimeNotice = "You can't log the future!"
    override val logSheetStartAfterEndNotice = "That can't start after it ends!"
    override val logSheetEndBeforeStartNotice = "That can't end before it starts!"
    override val quickLogUndoAction = "Oops, undo!"
    override val settingsSupportSectionLabel = "Spread the word!"
    override val settingsRateAppButton = "Give us stars!"
    override val settingsContactUsButton = "Say hello!"
    override val settingsAppearanceSectionLabel = "Look & feel!"
    override val settingsThemeSectionLabel = "Pick your vibe"
    override val settingsThemeInfoTitle = "About themes!"
    override val settingsThemeInfoBody =
        "Every theme comes with its own colors and its own voice — switch it up and watch the whole app talk differently!"
    override val settingsTimeFormatSectionLabel = "Clock style!"
    override val settingsCheckInSectionLabel = "Nudge me"
    override val settingsCheckInInfoTitle = "Check-ins, explained"
    override val settingsCheckInInfoBody =
        "Sets how many quiet days trigger a nudge, for cases with check-ins on. Off means no app-wide " +
            "nudges — you can still flip a single case off from its edit screen."
    override val settingsDataSectionLabel = "Your stuff!"
    override val settingsCloudBackupToggleLabel = "Back up my stuff!"
    override val settingsCloudBackupInfoTitle = "About backing up!"
    override val settingsCloudBackupInfoBody =
        "When this is on, your phone's own backup can scoop up HODITH's data along with everything else, " +
            "if you've got phone backup turned on. Switch it off and future backups skip HODITH — but heads " +
            "up, it won't erase a backup that already happened!"
    override val settingsDeleteDataButton = "Nuke data"
    override val settingsDeleteDataOptionsTitle = "What's getting nuked?"
    override val settingsDeleteDataOptionAll = "Everything"
    override val settingsDeleteDataOptionLogsOnly = "Old history only"
    override val settingsDeleteDataDateLabel = "Nuke history before"
    override val settingsDeleteDataOptionsNextAction = "Let's go"
    override val settingsDeleteDataOptionsCancelAction = "Nah, never mind"
    override val settingsDeleteAllDataConfirmTitle = "Nuke everything?"
    override val settingsDeleteAllDataConfirmBody = "Every case and event goes poof — for real, no take-backs."
    override val settingsDeleteAllDataConfirmAction = "Yeet it all"
    override val settingsDeleteAllDataCancelAction = "Nah, never mind"
    override val settingsDeleteDataLogsConfirmTitle = "Nuke this history?"

    override fun settingsDeleteDataLogsConfirmBody(dateLabel: String) = "Every entry before $dateLabel goes poof — for real, no take-backs."

    override val settingsDeleteDataLogsConfirmAction = "Yeet 'em"
    override val settingsExportButton = "Save a backup!"
    override val settingsExportFormatDialogTitle = "Pick a format!"
    override val settingsExportFormatJsonOption = "JSON backup"
    override val settingsExportFormatJsonDescription = "Everything, safe and sound. The one HODITH can load back in."
    override val settingsExportFormatCsvOption = "CSV table"
    override val settingsExportFormatCsvDescription = "Open it up in Microsoft Excel, Google Sheets, or LibreOffice and poke around!"
    override val settingsExportFormatConfirmAction = "Save it!"
    override val settingsExportFormatCancelAction = "Nah, never mind"
    override val settingsImportButton = "Restore a backup!"
    override val settingsImportConfirmTitle = "Swap in the backup?"
    override val settingsImportConfirmBody = "Everything here gets wiped and replaced with what's in that file. No undo button, promise!"
    override val settingsImportConfirmAction = "Swap it in!"
    override val settingsImportCancelAction = "Nah, never mind"
    override val settingsExportSuccessMessage = "Backup saved!"
    override val settingsExportFailureMessage = "Backup didn't save. Oops."
    override val settingsCsvExportSuccessMessage = "CSV saved!"
    override val settingsCsvExportFailureMessage = "CSV didn't save. Oops."
    override val settingsImportSuccessMessage = "Backup restored!"
    override val settingsImportFailureInvalidMessage = "That's not a HODITH backup file!"
    override val settingsImportFailureVersionMessage = "That backup's from a version this app can't read."
    override val settingsImportFailureIoMessage = "Couldn't read that file. Weird."
    override val settingsImportFailureSemanticMessage = "That backup's data looks off, so nothing got restored!"
    override val settingsDeveloperModeSectionLabel = "Nerd mode!"
    override val settingsLoadDemoDataButton = "Load some pretend chaos!"
    override val settingsDemoDataLoadedMessage = "Fake drama, loaded!"
    override val aboutScreenTitle = "About HODITH!"
    override val aboutIdeaLabel = "What's this app about?"
    override val aboutIdeaBody =
        "Ever catch yourself thinking 'this ALWAYS happens'? HODITH helps you find out if that's " +
            "actually true! Log a Case, track it over time, and see what the data says!"
    override val aboutDeveloperModeUnlockedMessage = "Developer mode unlocked! Go wild."
    override val aboutPrivacyLabel = "Privacy"
    override val aboutPrivacyBody =
        "HODITH itself doesn't touch the internet — zero network access, promise! But if your phone's own " +
            "backup is turned on, it might scoop up HODITH's data anyway. Flip the switch in Settings to " +
            "stop that — heads up though, it only stops future backups, past ones stick around!"
    override val aboutPrivacyPolicyLinkLabel = "Read the full privacy policy!"
    override val aboutLicensesLabel = "Licenses"
    override val aboutLicensesBody =
        "HODITH is built on awesome open-source stuff — AndroidX Jetpack, Hilt, Room, Moshi, Glance, " +
            "WorkManager, and Kotlin Coroutines — all under the Apache License 2.0!"
    override val frequencyDecreaseCountDescription = "Fewer!"
    override val frequencyIncreaseCountDescription = "More!"
    override val metricOccurrenceLabel = "How often it kicks off"
    override val metricDaysActiveLabel = "How many days it's a thing"

    override val insightsNothingLoggedMessage = "Log a moment and the insights start taking shape!"
    override val insightsSingleEventNote = "One event in — the picture starts here!"

    override val insightsHeatmapShowMoreAction = "Show me more!"
    override val insightsHeatmapShowFewerAction = "Okay, tuck it back away"

    override val insightsDrillDownEmptyState = "Nothing matches here — yet!"

    override fun insightsHeatmapDayTapDescription(dateLabel: String) = "Peek at $dateLabel!"

    override fun insightsIntensitySquareTapDescription(level: Int) = "See the intensity $level moments!"

    override fun insightsTagRowTapDescription(tagName: String) = "See the #$tagName moments!"

    override fun insightsRhythmCellTapDescription(
        dayLabel: String,
        timeOfDayLabel: String,
    ) = "See the $dayLabel ${timeOfDayLabel.lowercase()} moments!"

    override fun insightsIntensityDrillDownTitle(level: Int) = "Intensity $level moments"

    override fun insightsTagDrillDownTitle(tagName: String) = "Tagged #$tagName!"

    override fun insightsRhythmDrillDownTitle(
        dayLabel: String,
        timeOfDayLabel: String,
    ) = "$dayLabel ${timeOfDayLabel.lowercase()}s!"

    override val insightsBurstFlagLabel = "Comes in bursts!"

    override fun insightsTrendSentence(
        direction: TrendDirection,
        recentCount: Int,
        priorCount: Int,
    ) = when (direction) {
        TrendDirection.UP -> "$recentCount logs in the last 30 days — up from $priorCount! Busy stretch."
        TrendDirection.DOWN -> "$recentCount logs in the last 30 days — down from $priorCount! Quieter lately."
        TrendDirection.FLAT -> "$recentCount logs in the last 30 days — same as before. Steady as she goes!"
    }

    override fun insightsWentQuietSentence(
        currentGapLabel: String,
        longestPastGapLabel: String,
    ) =
        "Nothing logged in $currentGapLabel. That beats this Case's old record of $longestPastGapLabel! Still going, or has it quietly wrapped up?"

    override fun insightsWentQuietEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps!"

    override fun insightsGapShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "Gaps have grown from $priorAverageLabel to $recentAverageLabel!"
        ShiftDirection.DOWN -> "Gaps have shrunk from $priorAverageLabel to $recentAverageLabel!"
    }

    override fun insightsStreakShiftSentence(
        direction: ShiftDirection,
        priorAverageLabel: String,
        recentAverageLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "Streaks have grown from $priorAverageLabel to $recentAverageLabel!"
        ShiftDirection.DOWN -> "Streaks have shrunk from $priorAverageLabel to $recentAverageLabel!"
    }

    override val insightsFrequencyInfoTitle = "What am I looking at?"

    override fun insightsFrequencyInfoBody(granularity: FrequencyGranularity): String {
        val unit =
            when (granularity) {
                FrequencyGranularity.DAY -> "days"
                FrequencyGranularity.WEEK -> "weeks"
                FrequencyGranularity.MONTH -> "months"
            }
        return "Just the last 12 $unit — we pick days/weeks/months automatically depending on how long you've been " +
            "tracking, but feel free to flip it yourself up top!"
    }

    override val insightsGapsInfoTitle = "Gaps & streaks, explained!"
    override val insightsGapsInfoBody =
        "Shortest gap: the tiniest pause between two events.\n" +
            "Longest gap: the biggest quiet stretch with nothing going on.\n" +
            "Current gap: time since the last event wrapped up, or 0 while something's still running.\n" +
            "Average gap: the usual space between events.\n" +
            "Longest streak: the most days in a row with at least one event active.\n" +
            "Average streak: how long those runs usually go.\n\n" +
            "Heads up: a duration event counts on every day it was active, so one long event can fill a whole streak by itself! " +
            "\"Comes in bursts!\" pops up when the gaps are all over the place."

    override val insightsTrendsShowMoreAction = "See them all!"
    override val insightsTagsSeeAllAction = "See every tag!"
    override val insightsTagsDistinctLabel = "Total tags!"
    override val insightsTagsInfoTitle = "About these tags!"
    override val insightsTagsInfoBody =
        "Each tag counts the events it's on. Tap one to see them all!"

    override val insightsTrendsInfoTitle = "What these mean!"
    override val insightsTrendsInfoBody =
        "Each row is a shift we spotted somewhere in this case's own history, just describing what changed, not what's next.\n\n" +
            "Hint means it crossed a basic threshold, nothing fancier yet. Pattern means we checked it, and it holds up!"

    override fun insightsGapShiftEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps!"

    override fun insightsStreakShiftEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount streaks!"

    override fun insightsFrequencyShiftEvidenceLabel() = "Comparing the last 30 days to the 30 before!"

    override fun insightsTagShareShiftSentence(
        tagName: String,
        direction: ShiftDirection,
        priorShareLabel: String,
        recentShareLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "\"$tagName\" is popping up more lately, now $recentShareLabel of events, up from $priorShareLabel!"
        ShiftDirection.DOWN -> "\"$tagName\" is popping up less lately, now $recentShareLabel of events, down from $priorShareLabel!"
    }

    override fun insightsTagShareShiftEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events!"

    override fun insightsTagComboSentence(
        tagNamesLabel: String,
        count: Int,
        totalEvents: Int,
    ) = "\"$tagNamesLabel\" keep showing up as a pair — $count of $totalEvents events!"

    override fun insightsTagComboEvidenceLabel(sampleCount: Int) = "Based on those $sampleCount events!"

    override fun insightsRecurrenceShapeSentence(
        direction: ShiftDirection,
        thresholdLabel: String,
        shareLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "This tends to come back fast, $shareLabel of past gaps landed within $thresholdLabel!"
        ShiftDirection.DOWN -> "This rarely comes back fast, only $shareLabel of past gaps landed within $thresholdLabel!"
    }

    override fun insightsRecurrenceShapeEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps!"

    override fun insightsTagOutcomeSentence(
        tagName: String,
        outcome: TagOutcome,
        direction: ShiftDirection,
        relativeDifferenceLabel: String,
        withoutTagLabel: String,
        withTagLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP ->
                    "\"$tagName\" hits harder, averaging $withTagLabel vs $withoutTagLabel without it, up $relativeDifferenceLabel!"
                ShiftDirection.DOWN ->
                    "\"$tagName\" hits softer, averaging $withTagLabel vs $withoutTagLabel without it, down $relativeDifferenceLabel!"
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP ->
                    "\"$tagName\" sticks around longer, averaging $withTagLabel vs $withoutTagLabel without it, " +
                        "up $relativeDifferenceLabel!"
                ShiftDirection.DOWN ->
                    "\"$tagName\" wraps up faster, averaging $withTagLabel vs $withoutTagLabel without it, down $relativeDifferenceLabel!"
            }
    }

    override fun insightsTagOutcomeEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events!"

    override fun insightsChangePointSentence(
        direction: ShiftDirection,
        dateLabel: String,
        priorLabel: String,
        recentLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "The gap between events has stretched out, from $priorLabel to $recentLabel, since around $dateLabel!"
        ShiftDirection.DOWN -> "The gap between events has tightened up, from $priorLabel to $recentLabel, since around $dateLabel!"
    }

    override fun insightsChangePointEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount gaps!"

    override fun insightsTrendSlopeSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        priorLabel: String,
        recentLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP -> "Intensity's been climbing, averaging $recentLabel lately vs $priorLabel before!"
                ShiftDirection.DOWN -> "Intensity's been easing up, averaging $recentLabel lately vs $priorLabel before!"
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP -> "Episodes have been running longer, averaging $recentLabel lately vs $priorLabel before!"
                ShiftDirection.DOWN -> "Episodes have been wrapping up faster, averaging $recentLabel lately vs $priorLabel before!"
            }
    }

    override fun insightsTrendSlopeEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events!"

    override fun insightsTimeOfDaySplitSentence(
        outcome: TagOutcome,
        direction: ShiftDirection,
        dayLabel: String,
        eveningLabel: String,
    ) = when (outcome) {
        TagOutcome.INTENSITY ->
            when (direction) {
                ShiftDirection.UP -> "Evenings hit harder, averaging $eveningLabel vs $dayLabel by day!"
                ShiftDirection.DOWN -> "Days hit harder, averaging $dayLabel vs $eveningLabel by evening!"
            }
        TagOutcome.DURATION ->
            when (direction) {
                ShiftDirection.UP -> "Evenings stick around longer, averaging $eveningLabel vs $dayLabel by day!"
                ShiftDirection.DOWN -> "Days stick around longer, averaging $dayLabel vs $eveningLabel by evening!"
            }
    }

    override fun insightsTimeOfDaySplitEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events!"

    override fun insightsTagTimingSentence(
        tagName: String,
        bucketPhrase: String,
        baselineLabel: String,
        taggedLabel: String,
    ) = "\"$tagName\" loves $bucketPhrase, $taggedLabel of its events land there vs $baselineLabel case-wide!"

    override fun insightsTagTimingEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount tagged events!"

    override fun insightsWeekdayWeekendSentence(
        direction: ShiftDirection,
        weekdayLabel: String,
        weekendLabel: String,
    ) = when (direction) {
        ShiftDirection.UP -> "This Case loves a weekend: $weekendLabel of its events land on Saturday or Sunday, more than you'd expect!"
        ShiftDirection.DOWN -> "This Case is a weekday devotee: $weekdayLabel of its events land Monday through Friday!"
    }

    override fun insightsWeekdayWeekendEvidenceLabel(sampleCount: Int) = "Based on the last $sampleCount events!"

    override val insightsDurationInfoTitle = "What counts toward duration!"
    override val insightsDurationInfoBody =
        "Shortest, average, and longest time only include events that have wrapped up. Anything still running doesn't count yet!"

    override val insightsRhythmInfoTitle = "When does it happen?!"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount (this week: $weekCount)"

    override fun archivedCasesLink(count: Int) = "The archive ($count)"

    override fun archivedCaseEventCount(count: Int) = "$count logged moments"

    override fun unarchiveCaseDescription(caseName: String) = "Bring back $caseName"

    override fun deleteCaseForeverDescription(caseName: String) = "Yeet $caseName forever"

    override fun deleteCaseForeverConfirmBody(eventCount: Int) =
        "$eventCount logged moments go away with it. No take-backs, for real this time."

    override fun clearArchiveConfirmBody(caseCount: Int) = "$caseCount shelved cases go away with it. No take-backs, for real this time."

    override fun eventIntensityLabel(intensity: Int) = "Feels like a $intensity!"

    override fun eventDurationLabel(duration: String) = "Went on for $duration"

    override fun quickLogButtonDescription(caseName: String) = "Log $caseName!"

    override fun quickLogUndoMessage(caseName: String) = "Logged $caseName!"

    override fun startActionDescription(caseName: String) = "Start $caseName!"

    override fun stopActionDescription(caseName: String) = "Stop $caseName!"

    override val ongoingPillLabel = "Still going"

    override fun ongoingCountIndicator(count: Int) = "$count still going!"

    override fun leaveStartStopConfirmBody(runningCount: Int) =
        "Start/Stop is the only mode that tracks a live event, so switching away stops all $runningCount running ones right now."

    override fun enterStartStopConfirmBody(openEndedCount: Int) =
        "You've got $openEndedCount events with no end time. Start/Stop would treat them as still running, so they'll be kept as instant one-offs instead."

    override fun bigPictureWeekDetailTitle(date: String) = "Week of $date"

    override fun bigPictureEventOngoingSince(since: String) = "Still going since $since"

    override fun bigPictureEventSpanRange(
        start: String,
        end: String,
    ) = "Went on $start–$end"

    override val expectationProgressUnitEvents = "logs"

    // ABOUT_RIGHT deliberately avoids "Nailed it"/"on target" framing (spec §4): a Watch just as
    // often flags something unwanted, so hitting the expected rate isn't an achievement to praise.
    override fun watchComparisonLabel(
        band: ComparisonBand,
        daysActive: Boolean,
    ): String {
        val phrase =
            when (band) {
                ComparisonBand.MUCH_LESS -> "way less than expected"
                ComparisonBand.LESS -> "a bit less than expected"
                ComparisonBand.ABOUT_RIGHT -> "right in line with expected"
                ComparisonBand.MORE -> "a bit more than expected"
                ComparisonBand.MUCH_MORE -> "way more than expected"
            }
        return if (daysActive) "active $phrase" else phrase
    }

    override val verdictPreliminaryTail = "Give it a few more weeks to be sure!"

    override fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ) = verdictMetaLine("Based on $eventCount logs over $windowDays days.", tier)

    override fun verdictMetaDaysActive(
        tier: ConfidenceTier,
        activeDayCount: Int,
        windowDays: Long,
    ) = verdictMetaLine("That's $activeDayCount active days out of $windowDays days!", tier)

    override val expectationEarlyBadgeLabel = "Too soon to say"

    override fun expectationTierBadgeLabel(tier: ConfidenceTier) =
        when (tier) {
            ConfidenceTier.PRELIMINARY -> "First peek!"
            else -> "Locked in!"
        }

    override val watchDefinitionEyebrow = "Eyes on"
    override val watchNowEyebrow = "Right now"

    override val watchesTabDescription = "Alerts!"
    override val watchesFabDescription = "New alert!"
    override val watchesEmptyTitle = "No alerts yet!"
    override val watchesEmptyBody = "Get pinged when this happens a lot, or goes quiet for a while — you choose!"
    override val watchesEmptyCta = "Add an alert!"

    override fun watchKindLabel(kind: WatchKind) =
        when (kind) {
            WatchKind.OFTEN -> "Happening a lot"
            WatchKind.QUIET -> "Gone quiet"
        }

    override fun watchSummary(
        kind: WatchKind,
        threshold: Int,
        windowDays: Int?,
    ) = when (kind) {
        WatchKind.OFTEN -> "$threshold+ times in $windowDays days"
        WatchKind.QUIET -> "Quiet for $threshold days"
    }

    override fun watchFiredAgo(daysAgo: Long) = if (daysAgo == 0L) "Popped off today!" else "Popped off $daysAgo days ago!"

    override fun watchToggleDescription(summary: String) = "Toggle alert: $summary"

    override fun watchDeleteDescription(summary: String) = "Remove alert: $summary"

    override val watchesDeleteConfirmTitle = "Remove this alert?"
    override val watchesDeleteConfirmBody = "No more heads-up from this one."
    override val watchesDeleteConfirmAction = "Remove it"
    override val watchesDeleteCancelAction = "Never mind"
    override val watchesCreateTitle = "New alert!"
    override val watchesEditTitle = "Edit alert!"
    override val watchesKindPickerLabel = "What sets it off?!"
    override val watchesQuietLabel = "Quiet for"
    override val watchesSaveButton = "Save!"
    override val watchesCancelButton = "Never mind"
    override val watchesDecreaseCountDescription = "Fewer!"
    override val watchesIncreaseCountDescription = "More!"

    override fun watchCardTitleOften(
        threshold: Int,
        per: ExpectedPer,
        metric: VerdictMetric,
    ) = when (metric) {
        VerdictMetric.OCCURRENCE_COUNT -> "$threshold times or more per ${perPhrase(per)}"
        VerdictMetric.DAYS_ACTIVE -> "$threshold active days or more per ${perPhrase(per)}"
    }

    override fun watchCardTitleQuiet(threshold: Int) = "Quiet for $threshold days"

    override fun watchSettingsLine(
        lookbackDays: Int,
        showMeasure: Boolean,
        metric: VerdictMetric,
        minIntensity: Int?,
    ): String {
        val parts = mutableListOf("Last $lookbackDays days")
        if (showMeasure) {
            parts += if (metric == VerdictMetric.DAYS_ACTIVE) "counting days active" else "counting times"
        }
        if (minIntensity != null) parts += "intensity $minIntensity+"
        return parts.joinToString(DOT_SEPARATOR)
    }

    override fun watchNowLineQuiet(silentDays: Long) = if (silentDays == 0L) "logged today!" else "quiet for $silentDays days"

    override val notificationChannelName = "Nudges"
    override val notificationChannelDescription = "Heads-up for notifications and check-ins."

    override fun notificationFiredTitle(caseName: String) = "$caseName's alert fired!"

    override fun checkInDueNotificationTitle(caseName: String) = "Quick check-in: $caseName"

    override fun checkInDueNotificationBody(silentDays: Long) = "Nothing logged in $silentDays days — all quiet, or did you forget?"

    override val notificationLogAction = "Log it!"
    override val notificationAllQuietAction = "All quiet!"

    override fun notificationsGroupSummaryTitle(count: Int) = "$count cases want your eyes 👀"

    override val notificationsDeniedBannerMessage =
        "Notifications are off, so watch and check-in alerts can't reach you — swing by here instead!"
    override val notificationsDeniedBannerAction = "Turn on notifications"

    override val widgetConfigureTitle = "Pick your widget's stars!"
    override val widgetConfigureBody = "Choose which Cases get to show off here. Long-press it and tap Edit to pick new stars anytime!"
    override val widgetConfigureNoCasesMessage = "No cases yet! Make one in the app first."
    override val widgetConfigureConfirmAction = "Add to widget!"
    override val widgetConfigureSkipAction = "Never mind"

    override val singleCaseWidgetConfigureTitle = "Pick your widget's star!"
    override val singleCaseWidgetConfigureBody = "Choose which Case gets to show off here. Add another widget for a different star!"
    override val singleCaseWidgetConfigureConfirmAction = "Add to widget!"

    override val widgetNoCasesSelectedMessage = "No stars picked for this widget yet! Long-press it and tap Edit to choose some."
    override val widgetCaseNotFoundMessage = "This Case wandered off! Tap to open HODITH."

    override val shareOpenDescription = "Share it!"

    override fun shareSquareEventNoun(eventCount: Int) = if (eventCount == 1) "log" else "logs"

    override fun shareSquareTrendFrom(priorRate: String) = "from $priorRate last month"

    override val shareSquareTrendSame = "same as last month"

    override fun shareSquareQuietLabel(gap: String) = "Quiet for $gap"

    override val shareSectionDragHandleDescription = "Drag me to a new spot!"

    override fun shareLogTruncationNote(
        shown: Int,
        total: Int,
    ) = "Showing $shown of $total! Narrow the range to fit the rest!"

    override val shareLogEmptyRangeMessage = "Nothing logged in this range yet!"

    override val logDetailEditDescription = "Pick what each history entry shows!"
    override val logDetailDialogTitle = "History detail!"
}

val LocalVoice = staticCompositionLocalOf<Voice> { PlainVoice }

fun voiceFor(theme: AppTheme): Voice =
    when (theme) {
        AppTheme.PLAIN -> PlainVoice
        AppTheme.INTENSE -> IntenseVoice
        AppTheme.BRIGHT -> BrightVoice
    }
