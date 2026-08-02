package com.secondmonday.hodith.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ConfidenceTier
import com.secondmonday.hodith.domain.FrequencyGranularity
import com.secondmonday.hodith.domain.HUNCH_NUDGE_EVENT_THRESHOLD
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_DAYS
import com.secondmonday.hodith.domain.PRELIMINARY_MIN_EVENTS
import com.secondmonday.hodith.domain.TrendDirection

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
    val bigPictureEventNoteEmptyState: String
    val bigPictureDialogCloseAction: String
    val bigPictureWeekViewDescription: String
    val homeNavLabel: String
    val bigPictureNavLabel: String
    val settingsNavLabel: String
    val comingSoonPlaceholder: String
    val newCaseTitle: String
    val editCaseTitle: String
    val newCaseFabDescription: String
    val backButtonDescription: String
    val caseNameLabel: String
    val caseNameHint: String
    val caseNameRequiredError: String
    val caseDescriptionLabel: String
    val caseDescriptionHint: String
    val caseIconLabel: String
    val caseIconRequiredError: String
    val caseIconSectionExpandDescription: String
    val caseIconSectionCollapseDescription: String
    val caseSectionInfoDescription: String
    val infoDialogDismissAction: String
    val caseLogFlowLabel: String
    val caseLogFlowOneTap: String
    val caseLogFlowDetailSheet: String
    val caseLogFlowInfoTitle: String
    val caseLogFlowInfoBody: String
    val caseDurationModeLabel: String
    val caseDurationModeNone: String
    val caseDurationModeManual: String
    val caseDurationModeStartStop: String
    val caseDurationModeInfoTitle: String
    val caseDurationModeInfoBody: String
    val caseIntensityToggleLabel: String
    val casePinnedToggleLabel: String
    val caseCheckInLabel: String
    val caseCheckInInfoTitle: String
    val caseCheckInInfoBody: String
    val caseSaveButton: String
    val caseDetailEditDescription: String
    val archiveCaseDescription: String
    val archiveCaseConfirmTitle: String
    val archiveCaseConfirmBody: String
    val archiveCaseConfirmAction: String
    val archiveCaseCancelAction: String
    val archivedCasesTitle: String
    val archivedCasesEmptyState: String
    val eventListEmptyState: String

    /** Log tab's summary line above the event list: total events logged and the Case's observation span so far. */
    fun logSummaryLine(
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
    val retroLogEntryDescription: String
    val logSheetNewEventTitle: String
    val logSheetEditEventTitle: String
    val logSheetTimeLabel: String
    val logSheetIntensityLabel: String
    val logSheetDurationLabel: String
    val logSheetDurationHint: String
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
    val staleOngoingEditEndTimeAction: String
    val staleOngoingStillGoingAction: String
    val quickLogUndoAction: String
    val settingsThemeSectionLabel: String
    val themeOptionPlain: String
    val themeOptionIntense: String
    val themeOptionBright: String
    val settingsPreviewLabel: String
    val settingsCheckInSectionLabel: String
    val checkInIntervalOptionOff: String
    val checkInIntervalOptionSeven: String
    val checkInIntervalOptionFourteen: String
    val checkInIntervalOptionThirty: String
    val settingsCheckInInfoTitle: String
    val settingsCheckInInfoBody: String
    val settingsDemoDataSectionLabel: String
    val settingsLoadDemoDataButton: String
    val settingsDemoDataLoadedMessage: String
    val settingsDeleteAllDataButton: String
    val settingsDeleteAllDataConfirmTitle: String
    val settingsDeleteAllDataConfirmBody: String
    val settingsDeleteAllDataConfirmAction: String
    val settingsDeleteAllDataCancelAction: String
    val settingsBackupSectionLabel: String
    val settingsExportButton: String
    val settingsImportButton: String
    val settingsImportConfirmTitle: String
    val settingsImportConfirmBody: String
    val settingsImportConfirmAction: String
    val settingsImportCancelAction: String
    val settingsExportSuccessMessage: String
    val settingsExportFailureMessage: String
    val settingsImportSuccessMessage: String
    val settingsImportFailureInvalidMessage: String
    val settingsImportFailureVersionMessage: String
    val settingsImportFailureIoMessage: String
    val aboutScreenTitle: String
    val aboutVersionLabel: String
    val aboutPrivacyLabel: String
    val aboutPrivacyBody: String
    val aboutLicensesLabel: String
    val aboutLicensesBody: String
    val hunchTabNoneTitle: String
    val hunchTabNoneBody: String
    val hunchAddButtonLabel: String
    val hunchNudgeTitle: String
    val hunchNudgeDismissAction: String
    val hunchEarlyHeadline: String
    val hunchResolveLabel: String
    val hunchCreatingTitle: String
    val hunchCreatingDirectionLabel: String
    val hunchCreatingFreqLabel: String
    val hunchCreatingFreqSuffix: String
    val hunchCreatingSaveButton: String
    val hunchCreatingDecreaseCountDescription: String
    val hunchCreatingIncreaseCountDescription: String
    val hunchHistoryHeader: String
    val hunchExpectedPerDay: String
    val hunchExpectedPerWeek: String
    val hunchExpectedPerMonth: String
    val caseDetailLogTabLabel: String
    val caseDetailInsightsTabLabel: String
    val caseDetailHunchTabLabel: String
    val insightsNotEnoughDataMessage: String
    val insightsSectionLabelTimeline: String get() = "Dot timeline"
    val insightsSectionLabelHeatmap: String get() = "Calendar heatmap"

    /** Spec §9's "current gap annotated" rule, plain phrasing: no record-breaking claim. */
    fun insightsGapNoteCurrent(days: Long): String

    /** As [insightsGapNoteCurrent], but the current gap ties or beats every past gap for this Case. */
    fun insightsGapNoteLongest(days: Long): String

    /** Reveals months beyond the heatmap's default 3-month preview. */
    val insightsHeatmapShowMoreAction: String

    /** Collapses the heatmap back to its default 3-month preview. */
    val insightsHeatmapShowFewerAction: String

    /** Spec §10 stat section labels — structural, identical across all three voices like [insightsSectionLabelTimeline]. */
    val insightsSectionLabelFrequency: String get() = "Frequency over time"
    val insightsSectionLabelRhythm: String get() = "Rhythm"
    val insightsSectionLabelGaps: String get() = "Gaps & clusters"
    val insightsSectionLabelTrend: String get() = "Trend"
    val insightsSectionLabelDuration: String get() = "Duration"
    val insightsSectionLabelIntensity: String get() = "Intensity"
    val insightsSectionLabelTags: String get() = "Tags"

    /** Tag breakdown's denominator row — structural, identical across all three voices. */
    val insightsTagsTotalLabel: String get() = "Total events"

    /** Frequency-over-time's info icon, explaining the fixed 12-bucket window and its auto-picked granularity. */
    val insightsFrequencyInfoTitle: String

    fun insightsFrequencyInfoBody(granularity: FrequencyGranularity): String

    /** Gaps & clusters stat-row labels — structural, identical across all three voices. */
    val insightsGapsLongestLabel: String get() = "Longest gap"
    val insightsGapsCurrentLabel: String get() = "Current gap"
    val insightsGapsAverageLabel: String get() = "Average gap"

    /** Spec §10's "tends to come in bursts" flag, shown as a badge on the Gaps & clusters card. */
    val insightsBurstFlagLabel: String

    /** Spec §10 trend arrow: last 30 days vs. the 30 before — purely descriptive, no judgement either way. */
    fun insightsTrendSentence(
        direction: TrendDirection,
        recentCount: Int,
        priorCount: Int,
    ): String

    /** Duration stat-row labels — structural, identical across all three voices. */
    val insightsDurationAverageLabel: String get() = "Average"
    val insightsDurationLongestLabel: String get() = "Longest"
    val insightsDurationTotalLabel: String get() = "Total"

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

    fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ): String

    fun archivedCasesLink(count: Int): String

    fun archivedCaseEventCount(count: Int): String

    fun unarchiveCaseDescription(caseName: String): String

    fun deleteCaseForeverDescription(caseName: String): String

    fun deleteCaseForeverConfirmBody(eventCount: Int): String

    fun eventIntensityLabel(intensity: Int): String

    fun eventDurationLabel(duration: String): String

    fun quickLogButtonDescription(caseName: String): String

    fun quickLogUndoMessage(caseName: String): String

    fun startActionDescription(caseName: String): String

    fun stopActionDescription(caseName: String): String

    fun ongoingIndicator(elapsed: String): String

    fun staleOngoingPromptMessage(
        caseName: String,
        elapsed: String,
    ): String

    fun bigPictureWeekDetailTitle(date: String): String

    fun hunchNudgeBody(
        caseIcon: String,
        caseName: String,
    ): String

    /** Title Case direction phrasing — the creation sheet's pills and, via [hunchHistoryRowText], history rows. */
    fun hunchDirectionPillLabel(direction: HunchDirection): String

    /** "Your hunch: too often, ~5×/week" — [expectedFrequencyLabel] is pre-formatted (see `formatExpectedFrequency`). */
    fun hunchChipLabel(
        direction: HunchDirection,
        expectedFrequencyLabel: String,
    ): String

    /** "Too often, ~7×/week" — same shape across all three voices, so this has one shared implementation. */
    fun hunchHistoryRowText(
        direction: HunchDirection,
        expectedFrequencyLabel: String,
    ): String = "${hunchDirectionPillLabel(direction)}, $expectedFrequencyLabel"

    /** "3 of 5 events · 9 of 14 days" toward the Preliminary bar. */
    fun hunchProgressLabel(
        eventCount: Int,
        windowDays: Long,
    ): String

    /** The confirmed 15-branch (direction × band) verdict headline; [observedRateLabel] is pre-formatted. */
    fun verdictHeadline(
        direction: HunchDirection,
        band: ComparisonBand,
        observedRateLabel: String,
    ): String

    /** [tier] is always Preliminary or Confident here — Early Days has no meta line. */
    fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ): String

    fun hunchHistorySummary(
        total: Int,
        heldUpCount: Int,
    ): String

    /** [band] `ABOUT_RIGHT` reads as "held up"; anything else reads as "off" — see spec §8. */
    fun hunchHistoryRowOutcome(
        band: ComparisonBand,
        observedRateLabel: String,
    ): String

    fun hunchHistoryRowWhen(monthsAgo: Long): String

    /** Badge text on the Hunch tab's cards — identical across all three voices, like the nav/tab labels. */
    val hunchEarlyBadgeLabel: String get() = "Early days"

    fun hunchTierBadgeLabel(tier: ConfidenceTier): String =
        when (tier) {
            ConfidenceTier.PRELIMINARY -> "Preliminary"
            else -> "Confident"
        }

    // ---- Triggers (Phase 9, spec §11/§14) ----
    val triggersScreenTitle: String
    val triggersOpenDescription: String
    val triggersFabDescription: String
    val triggersEmptyTitle: String
    val triggersEmptyBody: String
    val triggersEmptyCta: String

    fun triggerKindLabel(kind: TriggerKind): String

    /** [windowDays] only applies to [TriggerKind.AT_LEAST]; ignored for [TriggerKind.SILENT_FOR]. */
    fun triggerSummary(
        kind: TriggerKind,
        threshold: Int,
        windowDays: Int?,
    ): String

    fun triggerFiredAgo(daysAgo: Long): String

    fun triggerToggleDescription(summary: String): String

    fun triggerDeleteDescription(summary: String): String

    val triggersDeleteConfirmTitle: String
    val triggersDeleteConfirmBody: String
    val triggersDeleteConfirmAction: String
    val triggersDeleteCancelAction: String
    val triggersCreateTitle: String
    val triggersKindPickerLabel: String
    val triggersAtLeastLabel: String
    val triggersAtLeastSuffix: String
    val triggersWindowLabel: String
    val triggersWindowSeven: String
    val triggersWindowThirty: String
    val triggersWindowCustom: String
    val triggersWindowCustomHint: String
    val triggersSilentLabel: String
    val triggersSilentSuffix: String
    val triggersSaveButton: String
    val triggersCancelButton: String
    val triggersDecreaseCountDescription: String
    val triggersIncreaseCountDescription: String

    // ---- Notifications (Phase 9, spec §11) ----

    /** Shown in system Settings > App notifications, not in-app. */
    val notificationChannelName: String
    val notificationChannelDescription: String

    fun triggerFiredNotificationTitle(caseName: String): String

    fun checkInDueNotificationTitle(caseName: String): String

    fun checkInDueNotificationBody(silentDays: Long): String

    /** Check-in notification action buttons — [feature/notification-actions]. */
    val notificationLogAction: String
    val notificationAllQuietAction: String

    /** Anti-spam notification collapsing 2+ same-cycle due check-ins into one. */
    fun checkInsSummaryNotificationTitle(count: Int): String

    val notificationsDeniedBannerMessage: String
    val notificationsDeniedBannerAction: String

    // ---- Share cards (Phase 10, spec §13) ----
    val shareOpenDescription: String

    val shareRealityKicker: String get() = "Reality"
    val shareRealityEventsLabel: String get() = "events"
    val shareRealityDaysObservedLabel: String get() = "days observed"

    val shareHunchRealityKicker: String get() = "Hunch vs. reality"
    val shareHunchExpectedLabel: String
    val shareHunchObservedLabel: String

    val shareCardFooter: String get() = "counted with HODITH"

    /** Intense skin's rotated corner stamp — structural, like [shareRealityKicker]; never rendered under Plain/Bright. */
    val shareIntenseStampLabel: String get() = "Case File"

    /** Frequency section's share-card title, e.g. "Frequency by week" — reuses the granularity chip labels. */
    fun shareFrequencyTitle(granularity: FrequencyGranularity): String =
        "Frequency by ${
            when (granularity) {
                FrequencyGranularity.DAY -> insightsFrequencyGranularityDay
                FrequencyGranularity.WEEK -> insightsFrequencyGranularityWeek
                FrequencyGranularity.MONTH -> insightsFrequencyGranularityMonth
            }.lowercase()
        }"

    /** Share preview screen's format toggle — structural, identical across all three voices. */
    val shareFormatStoryLabel: String get() = "Story"
    val shareFormatSquareLabel: String get() = "Square"

    /** Toggles the Hunch vs. Reality beat on/off — only shown on Story format with a resolved Hunch. */
    val shareHunchVsRealityToggleLabel: String

    val shareNameFieldLabel: String
    val shareSectionsPickerLabel: String

    /** The share card's one-line caption. Impersonal only — no "I"/"you": whoever the card is shared
     * with isn't the one who made the Hunch, so first/second person addresses the wrong audience. */
    fun sharePunchline(
        direction: HunchDirection,
        band: ComparisonBand,
    ): String

    /** [com.secondmonday.hodith.widget.ListWidgetConfigureActivity] — shown once, the first time
     * a widget is added with nothing yet pinned (spec §15). */
    val widgetConfigureTitle: String
    val widgetConfigureBody: String
    val widgetConfigureNoCasesMessage: String
    val widgetConfigureConfirmAction: String
    val widgetConfigureSkipAction: String

    /** [com.secondmonday.hodith.widget.ListWidget]'s own copy. Only [PlainVoice]'s versions ever
     * render — the widget's chrome is fixed regardless of in-app theme (DEV_PLAYBOOK.md §4) — but
     * all three still get an entry per the Voice layer rule. */
    val widgetNoPinnedCasesMessage: String
    val widgetStopAction: String

    fun widgetTodayCount(count: Int): String
}

object PlainVoice : Voice {
    override val homeHeaderTitle = "How often does it truly happen?"
    override val noCasesEmptyState = "No cases yet."
    override val bigPictureEarlyDays = "Insufficient data. Keep logging."
    override val bigPictureMonthPickerTitle = "Jump to month"
    override val bigPictureDayDetailEmptyState = "No events logged this day."
    override val bigPictureWeekDetailEmptyState = "No events logged this week."
    override val bigPictureEventNoteEmptyState = "No note"
    override val bigPictureDialogCloseAction = "Close"
    override val bigPictureWeekViewDescription = "Open week view"
    override val homeNavLabel = "Home"
    override val bigPictureNavLabel = "Big Picture"
    override val settingsNavLabel = "Settings"
    override val comingSoonPlaceholder = "Coming soon."
    override val newCaseTitle = "New case"
    override val editCaseTitle = "Edit case"
    override val newCaseFabDescription = "New case"
    override val backButtonDescription = "Back"
    override val caseNameLabel = "Name"
    override val caseNameHint = "e.g. Kiddo was rude"
    override val caseNameRequiredError = "Name is required."
    override val caseDescriptionLabel = "Description (optional)"
    override val caseDescriptionHint = "Any more detail worth noting"
    override val caseIconLabel = "Icon"
    override val caseIconRequiredError = "Pick an icon."
    override val caseIconSectionExpandDescription = "Show icon choices"
    override val caseIconSectionCollapseDescription = "Hide icon choices"
    override val caseSectionInfoDescription = "More info"
    override val infoDialogDismissAction = "Got it"
    override val caseLogFlowLabel = "Logging"
    override val caseLogFlowOneTap = "One tap"
    override val caseLogFlowDetailSheet = "Detail sheet"
    override val caseLogFlowInfoTitle = "About logging"
    override val caseLogFlowInfoBody =
        "One tap logs an event instantly with no extra fields — pick it for cases you don't need duration or " +
            "intensity on. Detail sheet opens a short form for time, duration, intensity, and notes before saving."
    override val caseDurationModeLabel = "Duration"
    override val caseDurationModeNone = "None"
    override val caseDurationModeManual = "Manual"
    override val caseDurationModeStartStop = "Start/stop"
    override val caseDurationModeInfoTitle = "About duration"
    override val caseDurationModeInfoBody =
        "None skips duration entirely. Manual lets you type a duration when logging. Start/stop tracks an " +
            "ongoing event live, from Start until you Stop it."
    override val caseIntensityToggleLabel = "Track intensity (1-5)"
    override val casePinnedToggleLabel = "Pin to widget"
    override val caseCheckInLabel = "Check-in"
    override val caseCheckInInfoTitle = "About check-in"
    override val caseCheckInInfoBody =
        "When on, this case gets a check-in nudge after a stretch of silence — sooner if it has an active " +
            "Hunch, otherwise using Settings' default interval. Off turns it off for this case."
    override val caseSaveButton = "Save"
    override val caseDetailEditDescription = "Edit case"
    override val archiveCaseDescription = "Archive case"
    override val archiveCaseConfirmTitle = "Archive this case?"
    override val archiveCaseConfirmBody =
        "It will be hidden from Home and Big Picture, but its data stays intact. You can view and restore it later."
    override val archiveCaseConfirmAction = "Archive"
    override val archiveCaseCancelAction = "Cancel"
    override val archivedCasesTitle = "Archived cases"
    override val archivedCasesEmptyState = "No archived cases."
    override val eventListEmptyState = "No events logged yet."

    override fun logSummaryLine(
        eventCount: Int,
        observedDays: Long,
    ) = "$eventCount events logged · observed for $observedDays days"

    override val deleteEventConfirmTitle = "Delete this event?"
    override val deleteEventConfirmBody = "This can't be undone."
    override val deleteEventConfirmAction = "Delete"
    override val deleteEventCancelAction = "Cancel"
    override val deleteCaseForeverConfirmTitle = "Delete this case forever?"
    override val deleteCaseForeverConfirmAction = "Delete forever"
    override val deleteCaseForeverCancelAction = "Cancel"
    override val retroLogEntryDescription = "Log an event"
    override val logSheetNewEventTitle = "Log an event"
    override val logSheetEditEventTitle = "Edit event"
    override val logSheetTimeLabel = "When"
    override val logSheetIntensityLabel = "Intensity"
    override val logSheetDurationLabel = "Duration"
    override val logSheetDurationHint = "Minutes"
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
    override val staleOngoingEditEndTimeAction = "Edit end time"
    override val staleOngoingStillGoingAction = "Still going"
    override val quickLogUndoAction = "Undo"
    override val settingsThemeSectionLabel = "Theme"
    override val themeOptionPlain = "Plain"
    override val themeOptionIntense = "Intense"
    override val themeOptionBright = "Bright"
    override val settingsPreviewLabel = "Preview"
    override val settingsCheckInSectionLabel = "Check-ins"
    override val checkInIntervalOptionOff = "Off"
    override val checkInIntervalOptionSeven = "7d"
    override val checkInIntervalOptionFourteen = "14d"
    override val checkInIntervalOptionThirty = "30d"
    override val settingsCheckInInfoTitle = "About check-ins"
    override val settingsCheckInInfoBody =
        "How many days of silence trigger a check-in nudge, for cases without their own Hunch. A case with " +
            "an active Hunch uses its own pace-derived interval instead. Off turns off the app-wide default; " +
            "individual cases can still be turned off from their edit screen."
    override val settingsDemoDataSectionLabel = "Demo data"
    override val settingsLoadDemoDataButton = "Load demo data"
    override val settingsDemoDataLoadedMessage = "Demo data loaded."
    override val settingsDeleteAllDataButton = "Delete all data"
    override val settingsDeleteAllDataConfirmTitle = "Delete all data?"
    override val settingsDeleteAllDataConfirmBody =
        "Every case and event will be permanently deleted. This can't be undone."
    override val settingsDeleteAllDataConfirmAction = "Delete everything"
    override val settingsDeleteAllDataCancelAction = "Cancel"
    override val settingsBackupSectionLabel = "Backup"
    override val settingsExportButton = "Export data"
    override val settingsImportButton = "Import data"
    override val settingsImportConfirmTitle = "Replace all data?"
    override val settingsImportConfirmBody =
        "Importing will delete everything currently in the app and replace it with the backup file. This can't be undone."
    override val settingsImportConfirmAction = "Replace everything"
    override val settingsImportCancelAction = "Cancel"
    override val settingsExportSuccessMessage = "Backup saved."
    override val settingsExportFailureMessage = "Couldn't save the backup."
    override val settingsImportSuccessMessage = "Backup restored."
    override val settingsImportFailureInvalidMessage = "That file isn't a valid HODITH backup."
    override val settingsImportFailureVersionMessage = "That backup was made by a version of HODITH this app can't read."
    override val settingsImportFailureIoMessage = "Couldn't read that file."
    override val aboutScreenTitle = "About"
    override val aboutVersionLabel = "Version"
    override val aboutPrivacyLabel = "Privacy"
    override val aboutPrivacyBody =
        "Everything stays on your phone. HODITH has no network access and sends nothing anywhere."
    override val aboutLicensesLabel = "Licenses"
    override val aboutLicensesBody = "Open-source license details coming soon."
    override val hunchTabNoneTitle = "No hunch yet"
    override val hunchTabNoneBody =
        "Got a feeling about how often this happens? Add a Hunch to see how it compares to reality."
    override val hunchAddButtonLabel = "Add a Hunch"
    override val hunchNudgeTitle = "Got a feeling about this one?"
    override val hunchNudgeDismissAction = "Don't ask again"
    override val hunchEarlyHeadline = "Not enough data yet to judge your Hunch."
    override val hunchResolveLabel = "Resolve Hunch"
    override val hunchCreatingTitle = "New Hunch"
    override val hunchCreatingDirectionLabel = "How do you feel about this?"
    override val hunchCreatingFreqLabel = "About how often, in your gut?"
    override val hunchCreatingFreqSuffix = "times per"
    override val hunchCreatingSaveButton = "Save Hunch"
    override val hunchCreatingDecreaseCountDescription = "Decrease count"
    override val hunchCreatingIncreaseCountDescription = "Increase count"
    override val hunchHistoryHeader = "Past hunches"
    override val hunchExpectedPerDay = "Day"
    override val hunchExpectedPerWeek = "Week"
    override val hunchExpectedPerMonth = "Month"
    override val insightsNotEnoughDataMessage = "Not enough data yet."

    override fun insightsGapNoteCurrent(days: Long) = "$days days quiet right now."

    override fun insightsGapNoteLongest(days: Long) = "$days days quiet right now — the longest stretch since it started."

    override val insightsHeatmapShowMoreAction = "Show more months"
    override val insightsHeatmapShowFewerAction = "Show fewer months"

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

    override val caseDetailLogTabLabel = "Log"
    override val caseDetailInsightsTabLabel = "Insights"
    override val caseDetailHunchTabLabel = "Hunch"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount · This week: $weekCount"

    override fun archivedCasesLink(count: Int) = "Archived cases ($count)"

    override fun archivedCaseEventCount(count: Int) = "$count events logged"

    override fun unarchiveCaseDescription(caseName: String) = "Unarchive $caseName"

    override fun deleteCaseForeverDescription(caseName: String) = "Delete $caseName forever"

    override fun deleteCaseForeverConfirmBody(eventCount: Int) =
        "This case and its $eventCount logged events will be permanently deleted. This can't be undone."

    override fun eventIntensityLabel(intensity: Int) = "Intensity $intensity"

    override fun eventDurationLabel(duration: String) = "Duration: $duration"

    override fun quickLogButtonDescription(caseName: String) = "Log $caseName now"

    override fun quickLogUndoMessage(caseName: String) = "Logged $caseName."

    override fun startActionDescription(caseName: String) = "Start $caseName"

    override fun stopActionDescription(caseName: String) = "Stop $caseName"

    override fun ongoingIndicator(elapsed: String) = "Ongoing · $elapsed"

    override fun staleOngoingPromptMessage(
        caseName: String,
        elapsed: String,
    ) = "Still going, or forgot to stop $caseName? ($elapsed and counting.)"

    override fun bigPictureWeekDetailTitle(date: String) = "Week of $date"

    override fun hunchNudgeBody(
        caseIcon: String,
        caseName: String,
    ) = "You've logged $HUNCH_NUDGE_EVENT_THRESHOLD events for $caseIcon $caseName. Add a Hunch to see how it compares to reality."

    override fun hunchDirectionPillLabel(direction: HunchDirection) =
        when (direction) {
            HunchDirection.TOO_OFTEN -> "Too often"
            HunchDirection.NOT_ENOUGH -> "Not enough"
            HunchDirection.JUST_CURIOUS -> "Just curious"
        }

    override fun hunchChipLabel(
        direction: HunchDirection,
        expectedFrequencyLabel: String,
    ): String {
        val inline =
            when (direction) {
                HunchDirection.TOO_OFTEN -> "too often"
                HunchDirection.NOT_ENOUGH -> "not enough"
                HunchDirection.JUST_CURIOUS -> "just curious"
            }
        return "Your hunch: $inline, $expectedFrequencyLabel"
    }

    override fun hunchProgressLabel(
        eventCount: Int,
        windowDays: Long,
    ) = "$eventCount of $PRELIMINARY_MIN_EVENTS events · $windowDays of $PRELIMINARY_MIN_DAYS days"

    override fun verdictHeadline(
        direction: HunchDirection,
        band: ComparisonBand,
        observedRateLabel: String,
    ): String {
        val comparison =
            when (direction) {
                HunchDirection.TOO_OFTEN ->
                    when (band) {
                        ComparisonBand.MUCH_LESS -> "much less than you feared"
                        ComparisonBand.LESS -> "a bit below your estimate"
                        ComparisonBand.ABOUT_RIGHT -> "right around what you expected"
                        ComparisonBand.MORE -> "more than you expected"
                        ComparisonBand.MUCH_MORE -> "far more than you feared"
                    }
                HunchDirection.NOT_ENOUGH ->
                    when (band) {
                        ComparisonBand.MUCH_LESS -> "confirmed — far less than you'd like"
                        ComparisonBand.LESS -> "still less than you'd like"
                        ComparisonBand.ABOUT_RIGHT -> "right around what you expected"
                        ComparisonBand.MORE -> "more than you expected"
                        ComparisonBand.MUCH_MORE -> "happening far more than you thought"
                    }
                HunchDirection.JUST_CURIOUS ->
                    when (band) {
                        ComparisonBand.MUCH_LESS -> "much less than your estimate"
                        ComparisonBand.LESS -> "a bit less than your estimate"
                        ComparisonBand.ABOUT_RIGHT -> "right around your estimate"
                        ComparisonBand.MORE -> "a bit more than your estimate"
                        ComparisonBand.MUCH_MORE -> "much more than your estimate"
                    }
            }
        return "Observed: $observedRateLabel — $comparison."
    }

    override fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ) = when (tier) {
        ConfidenceTier.PRELIMINARY -> "Based on $eventCount events over $windowDays days. A few more weeks will sharpen this."
        else -> "Based on $eventCount events over $windowDays days."
    }

    override fun hunchHistorySummary(
        total: Int,
        heldUpCount: Int,
    ) = "$total hunches so far — ${total - heldUpCount} were off, $heldUpCount held up."

    override fun hunchHistoryRowOutcome(
        band: ComparisonBand,
        observedRateLabel: String,
    ) = when (band) {
        ComparisonBand.ABOUT_RIGHT -> "About right — observed $observedRateLabel"
        else -> "Way off — observed $observedRateLabel"
    }

    override fun hunchHistoryRowWhen(monthsAgo: Long) = "$monthsAgo months ago"

    override val triggersScreenTitle = "Triggers"
    override val triggersOpenDescription = "Open triggers"
    override val triggersFabDescription = "New trigger"
    override val triggersEmptyTitle = "No triggers yet"
    override val triggersEmptyBody = "Get a nudge when something happens too often, or goes quiet too long."
    override val triggersEmptyCta = "Add a trigger"

    override fun triggerKindLabel(kind: TriggerKind) =
        when (kind) {
            TriggerKind.AT_LEAST -> "Happens too often"
            TriggerKind.SILENT_FOR -> "Goes quiet too long"
        }

    override fun triggerSummary(
        kind: TriggerKind,
        threshold: Int,
        windowDays: Int?,
    ) = when (kind) {
        TriggerKind.AT_LEAST -> "$threshold+ times in $windowDays days"
        TriggerKind.SILENT_FOR -> "No events for $threshold days"
    }

    override fun triggerFiredAgo(daysAgo: Long) = "Fired $daysAgo days ago"

    override fun triggerToggleDescription(summary: String) = "Toggle trigger: $summary"

    override fun triggerDeleteDescription(summary: String) = "Delete trigger: $summary"

    override val triggersDeleteConfirmTitle = "Delete this trigger?"
    override val triggersDeleteConfirmBody = "You won't be notified by it anymore."
    override val triggersDeleteConfirmAction = "Delete"
    override val triggersDeleteCancelAction = "Cancel"
    override val triggersCreateTitle = "New trigger"
    override val triggersKindPickerLabel = "What should trigger it?"
    override val triggersAtLeastLabel = "At least"
    override val triggersAtLeastSuffix = "times"
    override val triggersWindowLabel = "Within"
    override val triggersWindowSeven = "7 days"
    override val triggersWindowThirty = "30 days"
    override val triggersWindowCustom = "Custom"
    override val triggersWindowCustomHint = "Days"
    override val triggersSilentLabel = "No events for"
    override val triggersSilentSuffix = "days"
    override val triggersSaveButton = "Save trigger"
    override val triggersCancelButton = "Cancel"
    override val triggersDecreaseCountDescription = "Decrease threshold"
    override val triggersIncreaseCountDescription = "Increase threshold"

    override val notificationChannelName = "Notifications"
    override val notificationChannelDescription = "Trigger and check-in alerts."

    override fun triggerFiredNotificationTitle(caseName: String) = "$caseName trigger"

    override fun checkInDueNotificationTitle(caseName: String) = "$caseName check-in"

    override fun checkInDueNotificationBody(silentDays: Long) = "Nothing logged in $silentDays days."

    override val notificationLogAction = "Log"
    override val notificationAllQuietAction = "All quiet"

    override fun checkInsSummaryNotificationTitle(count: Int) = "$count cases are quiet — tap to review"

    override val notificationsDeniedBannerMessage =
        "Notifications are off, so triggers and check-ins won't alert you — check back here instead."
    override val notificationsDeniedBannerAction = "Turn on notifications"

    override val widgetConfigureTitle = "Pick Cases for this widget"
    override val widgetConfigureBody = "Choose which Cases show up here. You can change this later from each Case's settings."
    override val widgetConfigureNoCasesMessage = "No cases yet. Add one in the app first."
    override val widgetConfigureConfirmAction = "Add to widget"
    override val widgetConfigureSkipAction = "Cancel"

    override val widgetNoPinnedCasesMessage = "No pinned Cases yet. Pin one from its Case screen to see it here."
    override val widgetStopAction = "Stop"

    override fun widgetTodayCount(count: Int) = "Today: $count"

    override val shareOpenDescription = "Share"
    override val shareHunchExpectedLabel = "expected"
    override val shareHunchObservedLabel = "observed"
    override val shareHunchVsRealityToggleLabel = "Show Hunch vs. Reality"
    override val shareNameFieldLabel = "Name on card"
    override val shareSectionsPickerLabel = "Include in card"

    override fun sharePunchline(
        direction: HunchDirection,
        band: ComparisonBand,
    ) = when (direction) {
        HunchDirection.TOO_OFTEN ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Way less often than feared."
                ComparisonBand.LESS -> "A little less often than feared."
                ComparisonBand.ABOUT_RIGHT -> "Just as often as expected."
                ComparisonBand.MORE -> "Plot twist: more often than expected."
                ComparisonBand.MUCH_MORE -> "Way more often than feared."
            }
        HunchDirection.NOT_ENOUGH ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Confirmed: happening far less than hoped."
                ComparisonBand.LESS -> "Still happening less than hoped."
                ComparisonBand.ABOUT_RIGHT -> "Just about as often as hoped."
                ComparisonBand.MORE -> "Good news: more often than expected."
                ComparisonBand.MUCH_MORE -> "Happening far more than expected."
            }
        HunchDirection.JUST_CURIOUS ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Way less than the guess."
                ComparisonBand.LESS -> "A little less than the guess."
                ComparisonBand.ABOUT_RIGHT -> "Right on the money."
                ComparisonBand.MORE -> "A little more than the guess."
                ComparisonBand.MUCH_MORE -> "Way more than the guess."
            }
    }
}

object IntenseVoice : Voice {
    override val homeHeaderTitle = "How oft dares it truly haunt?"
    override val noCasesEmptyState = "Nothing is being watched. Yet."
    override val bigPictureEarlyDays = "The evidence is yet insufficient for despair or joy."
    override val bigPictureMonthPickerTitle = "Leap to another month"
    override val bigPictureDayDetailEmptyState = "Nothing was recorded this day."
    override val bigPictureWeekDetailEmptyState = "Nothing was recorded this week."
    override val bigPictureEventNoteEmptyState = "No notes were left."
    override val bigPictureDialogCloseAction = "Seal it shut"
    override val bigPictureWeekViewDescription = "Unveil the week"
    override val homeNavLabel = "Home"
    override val bigPictureNavLabel = "Big Picture"
    override val settingsNavLabel = "Settings"
    override val comingSoonPlaceholder = "Not yet manifest."
    override val newCaseTitle = "Open a new case"
    override val editCaseTitle = "Revise the case"
    override val newCaseFabDescription = "Open a new case"
    override val backButtonDescription = "Back"
    override val caseNameLabel = "Name"
    override val caseNameHint = "e.g. Kiddo was rude"
    override val caseNameRequiredError = "It needs a name to be watched."
    override val caseDescriptionLabel = "Description (optional)"
    override val caseDescriptionHint = "Say more, if the shadows require it"
    override val caseIconLabel = "Icon"
    override val caseIconRequiredError = "Choose a mark for it."
    override val caseIconSectionExpandDescription = "Reveal the marks"
    override val caseIconSectionCollapseDescription = "Conceal the marks"
    override val caseSectionInfoDescription = "Unveil more"
    override val infoDialogDismissAction = "Understood"
    override val caseLogFlowLabel = "Logging"
    override val caseLogFlowOneTap = "One tap"
    override val caseLogFlowDetailSheet = "Detail sheet"
    override val caseLogFlowInfoTitle = "On the manner of recording"
    override val caseLogFlowInfoBody =
        "One tap seals the record the instant you touch it — no further rite required. The detail sheet asks " +
            "more of you: the hour, its length, its severity, its notes — reserved for cases that demand such detail."
    override val caseDurationModeLabel = "Duration"
    override val caseDurationModeNone = "None"
    override val caseDurationModeManual = "Manual"
    override val caseDurationModeStartStop = "Start/stop"
    override val caseDurationModeInfoTitle = "On the length of things"
    override val caseDurationModeInfoBody =
        "None takes no account of how long a thing lingers. Manual lets you name its length yourself. " +
            "Start/stop watches it unfold in real time, from the moment it begins until you declare it done."
    override val caseIntensityToggleLabel = "Track intensity (1-5)"
    override val casePinnedToggleLabel = "Pin to widget"
    override val caseCheckInLabel = "Check-in"
    override val caseCheckInInfoTitle = "On the watch kept"
    override val caseCheckInInfoBody =
        "When kept, the check-in nudge stirs after this case has lain silent too long — sooner still if a " +
            "Hunch keeps its own vigil, otherwise the interval Settings decree for all cases. Off silences the " +
            "nudge for this case alone."
    override val caseSaveButton = "Seal it"
    override val caseDetailEditDescription = "Revise the case"
    override val archiveCaseDescription = "Bury this case"
    override val archiveCaseConfirmTitle = "Bury this case?"
    override val archiveCaseConfirmBody =
        "It will vanish from Home and the record, but nothing is lost — it waits in the archive, ready to be exhumed."
    override val archiveCaseConfirmAction = "Bury it"
    override val archiveCaseCancelAction = "Abandon"
    override val archivedCasesTitle = "The buried cases"
    override val archivedCasesEmptyState = "Nothing lies buried here."
    override val eventListEmptyState = "No evidence gathered yet."

    override fun logSummaryLine(
        eventCount: Int,
        observedDays: Long,
    ) = "$eventCount marks in the record — $observedDays days under watch"

    override val deleteEventConfirmTitle = "Strike this from the record?"
    override val deleteEventConfirmBody = "Once gone, it cannot be recalled."
    override val deleteEventConfirmAction = "Erase"
    override val deleteEventCancelAction = "Abandon"
    override val deleteCaseForeverConfirmTitle = "Erase this case forever?"
    override val deleteCaseForeverConfirmAction = "Erase forever"
    override val deleteCaseForeverCancelAction = "Abandon"
    override val retroLogEntryDescription = "Record the evidence"
    override val logSheetNewEventTitle = "Record the evidence"
    override val logSheetEditEventTitle = "Amend the record"
    override val logSheetTimeLabel = "The hour it happened"
    override val logSheetIntensityLabel = "Severity"
    override val logSheetDurationLabel = "How long it lingered"
    override val logSheetDurationHint = "Minutes"
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
    override val staleOngoingEditEndTimeAction = "Mark when it ended"
    override val staleOngoingStillGoingAction = "Still unfolding"
    override val quickLogUndoAction = "Reverse it"
    override val settingsThemeSectionLabel = "The chosen skin"
    override val themeOptionPlain = "Plain"
    override val themeOptionIntense = "Intense"
    override val themeOptionBright = "Bright"
    override val settingsPreviewLabel = "A glimpse"
    override val settingsCheckInSectionLabel = "The watch kept"
    override val checkInIntervalOptionOff = "Off"
    override val checkInIntervalOptionSeven = "7d"
    override val checkInIntervalOptionFourteen = "14d"
    override val checkInIntervalOptionThirty = "30d"
    override val settingsCheckInInfoTitle = "On the watch kept"
    override val settingsCheckInInfoBody =
        "How many days of silence rouse a check-in nudge, for any case with no Hunch watching over it. A " +
            "case bound to an active Hunch keeps its own pace-derived vigil instead. Off lays the app-wide " +
            "watch to rest; a single case's watch can still be silenced from its own page."
    override val settingsDemoDataSectionLabel = "Phantom data"
    override val settingsLoadDemoDataButton = "Conjure phantom cases"
    override val settingsDemoDataLoadedMessage = "The phantoms have arrived."
    override val settingsDeleteAllDataButton = "Erase everything"
    override val settingsDeleteAllDataConfirmTitle = "Erase everything?"
    override val settingsDeleteAllDataConfirmBody =
        "Every case and record will be struck from existence, beyond recall."
    override val settingsDeleteAllDataConfirmAction = "Erase it all"
    override val settingsDeleteAllDataCancelAction = "Abandon"
    override val settingsBackupSectionLabel = "The archive"
    override val settingsExportButton = "Copy the case files"
    override val settingsImportButton = "Restore the case files"
    override val settingsImportConfirmTitle = "Erase the present for the past?"
    override val settingsImportConfirmBody =
        "Every case and record here will be struck out, replaced by whatever's in that file. There's no undoing it."
    override val settingsImportConfirmAction = "Restore it"
    override val settingsImportCancelAction = "Abandon"
    override val settingsExportSuccessMessage = "The case files are copied."
    override val settingsExportFailureMessage = "The case files couldn't be copied."
    override val settingsImportSuccessMessage = "The case files are restored."
    override val settingsImportFailureInvalidMessage = "That file holds no case files this app recognizes."
    override val settingsImportFailureVersionMessage = "That file was sealed by a version of this app no longer spoken here."
    override val settingsImportFailureIoMessage = "That file could not be read."
    override val aboutScreenTitle = "The record"
    override val aboutVersionLabel = "Version"
    override val aboutPrivacyLabel = "What leaves this phone"
    override val aboutPrivacyBody =
        "Nothing. No network, no signal sent outward — every case stays sealed here."
    override val aboutLicensesLabel = "Borrowed bones"
    override val aboutLicensesBody = "The debts owed to other code will be listed here, in time."
    override val hunchTabNoneTitle = "No claim has been made"
    override val hunchTabNoneBody =
        "You have watched this case, but sworn nothing about it. State a hunch, and the record will one day answer."
    override val hunchAddButtonLabel = "State a hunch"
    override val hunchNudgeTitle = "Five entries lie in the record."
    override val hunchNudgeDismissAction = "Never ask again"
    override val hunchEarlyHeadline = "The evidence is yet insufficient for despair or joy."
    override val hunchResolveLabel = "Seal the verdict"
    override val hunchCreatingTitle = "State your hunch"
    override val hunchCreatingDirectionLabel = "What do you feel, truly?"
    override val hunchCreatingFreqLabel = "How often does it haunt you, in your gut?"
    override val hunchCreatingFreqSuffix = "times per"
    override val hunchCreatingSaveButton = "Seal the hunch"
    override val hunchCreatingDecreaseCountDescription = "Diminish the count"
    override val hunchCreatingIncreaseCountDescription = "Swell the count"
    override val hunchHistoryHeader = "The record of past claims"
    override val hunchExpectedPerDay = "Day"
    override val hunchExpectedPerWeek = "Week"
    override val hunchExpectedPerMonth = "Month"
    override val insightsNotEnoughDataMessage = "The file is too thin to read yet."

    override fun insightsGapNoteCurrent(days: Long) = "$days days of silence. The trail has gone cold — for now."

    override fun insightsGapNoteLongest(days: Long) = "$days days of silence — the coldest the trail has ever run."

    override val insightsHeatmapShowMoreAction = "Unseal the older files"
    override val insightsHeatmapShowFewerAction = "Reseal them"

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

    override val caseDetailLogTabLabel = "Log"
    override val caseDetailInsightsTabLabel = "Insights"
    override val caseDetailHunchTabLabel = "Hunch"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount — this week: $weekCount"

    override fun archivedCasesLink(count: Int) = "The buried ($count)"

    override fun archivedCaseEventCount(count: Int) = "$count entries in the record"

    override fun unarchiveCaseDescription(caseName: String) = "Exhume $caseName"

    override fun deleteCaseForeverDescription(caseName: String) = "Erase $caseName forever"

    override fun deleteCaseForeverConfirmBody(eventCount: Int) = "This case and its $eventCount entries will be erased beyond recall."

    override fun eventIntensityLabel(intensity: Int) = "Intensity: $intensity"

    override fun eventDurationLabel(duration: String) = "Lasted: $duration"

    override fun quickLogButtonDescription(caseName: String) = "Add $caseName to the record"

    override fun quickLogUndoMessage(caseName: String) = "$caseName entered into the record."

    override fun startActionDescription(caseName: String) = "Begin $caseName"

    override fun stopActionDescription(caseName: String) = "Seal $caseName"

    override fun ongoingIndicator(elapsed: String) = "Still unfolding — $elapsed"

    override fun staleOngoingPromptMessage(
        caseName: String,
        elapsed: String,
    ) = "$caseName has lingered $elapsed. Still unfolding, or simply forgotten?"

    override fun bigPictureWeekDetailTitle(date: String) = "The week of $date"

    override fun hunchNudgeBody(
        caseIcon: String,
        caseName: String,
    ) = "$caseIcon $caseName has been marked $HUNCH_NUDGE_EVENT_THRESHOLD times, and still no hunch stands against it. " +
        "Confess your suspicion, and let the record judge it."

    override fun hunchDirectionPillLabel(direction: HunchDirection) =
        when (direction) {
            HunchDirection.TOO_OFTEN -> "Too often"
            HunchDirection.NOT_ENOUGH -> "Too seldom"
            HunchDirection.JUST_CURIOUS -> "Merely curious"
        }

    override fun hunchChipLabel(
        direction: HunchDirection,
        expectedFrequencyLabel: String,
    ): String {
        val inline =
            when (direction) {
                HunchDirection.TOO_OFTEN -> "too often"
                HunchDirection.NOT_ENOUGH -> "too seldom"
                HunchDirection.JUST_CURIOUS -> "merely curious"
            }
        return "Your claim: $inline, $expectedFrequencyLabel"
    }

    override fun hunchProgressLabel(
        eventCount: Int,
        windowDays: Long,
    ) = "$eventCount of $PRELIMINARY_MIN_EVENTS entries · $windowDays of $PRELIMINARY_MIN_DAYS days"

    override fun verdictHeadline(
        direction: HunchDirection,
        band: ComparisonBand,
        observedRateLabel: String,
    ): String =
        when (direction) {
            HunchDirection.TOO_OFTEN ->
                when (band) {
                    ComparisonBand.MUCH_LESS -> "Your dread was exaggerated — it happens at $observedRateLabel, the record shows."
                    ComparisonBand.LESS -> "Less than you feared — $observedRateLabel, the record shows."
                    ComparisonBand.ABOUT_RIGHT -> "The record agrees with your dread — $observedRateLabel, near enough."
                    ComparisonBand.MORE -> "Worse than you feared — $observedRateLabel, the record shows."
                    ComparisonBand.MUCH_MORE -> "Your dread was justified — $observedRateLabel, far more than you feared."
                }
            HunchDirection.NOT_ENOUGH ->
                when (band) {
                    ComparisonBand.MUCH_LESS -> "Your fear is confirmed — a mere $observedRateLabel, the record shows."
                    ComparisonBand.LESS -> "Still wanting — $observedRateLabel, less than you hoped."
                    ComparisonBand.ABOUT_RIGHT -> "The record agrees — $observedRateLabel, near enough to your hope."
                    ComparisonBand.MORE -> "Better than you dared hope — $observedRateLabel, the record shows."
                    ComparisonBand.MUCH_MORE -> "Far beyond your hope — $observedRateLabel, the record shows."
                }
            HunchDirection.JUST_CURIOUS ->
                when (band) {
                    ComparisonBand.MUCH_LESS -> "Curiosity answered — $observedRateLabel, far below your guess."
                    ComparisonBand.LESS -> "Curiosity answered — $observedRateLabel, a little below your guess."
                    ComparisonBand.ABOUT_RIGHT -> "Curiosity answered — $observedRateLabel, near enough to your guess."
                    ComparisonBand.MORE -> "Curiosity answered — $observedRateLabel, a little above your guess."
                    ComparisonBand.MUCH_MORE -> "Curiosity answered — $observedRateLabel, far above your guess."
                }
        }

    override fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ) = when (tier) {
        ConfidenceTier.PRELIMINARY -> "$eventCount entries across $windowDays days. More time will harden this into certainty."
        else -> "$eventCount entries, borne out across $windowDays days."
    }

    override fun hunchHistorySummary(
        total: Int,
        heldUpCount: Int,
    ) = "$total claims stand in the record — ${total - heldUpCount} false, $heldUpCount true."

    override fun hunchHistoryRowOutcome(
        band: ComparisonBand,
        observedRateLabel: String,
    ) = when (band) {
        ComparisonBand.ABOUT_RIGHT -> "The record agrees — near enough, at $observedRateLabel"
        else -> "Far from true — $observedRateLabel, the record shows"
    }

    override fun hunchHistoryRowWhen(monthsAgo: Long) = "$monthsAgo months past"

    override val triggersScreenTitle = "Alarms"
    override val triggersOpenDescription = "Tend the alarms"
    override val triggersFabDescription = "Set a new alarm"
    override val triggersEmptyTitle = "No alarm is set"
    override val triggersEmptyBody = "Nothing yet watches this case. Set an alarm, and be warned when the pattern breaks."
    override val triggersEmptyCta = "Set an alarm"

    override fun triggerKindLabel(kind: TriggerKind) =
        when (kind) {
            TriggerKind.AT_LEAST -> "It comes too often"
            TriggerKind.SILENT_FOR -> "It falls silent too long"
        }

    override fun triggerSummary(
        kind: TriggerKind,
        threshold: Int,
        windowDays: Int?,
    ) = when (kind) {
        TriggerKind.AT_LEAST -> "$threshold or more, within $windowDays days"
        TriggerKind.SILENT_FOR -> "$threshold days of silence"
    }

    override fun triggerFiredAgo(daysAgo: Long) = "Sounded $daysAgo days ago"

    override fun triggerToggleDescription(summary: String) = "Toggle the alarm: $summary"

    override fun triggerDeleteDescription(summary: String) = "Silence the alarm: $summary"

    override val triggersDeleteConfirmTitle = "Silence this alarm?"
    override val triggersDeleteConfirmBody = "It will warn you no longer."
    override val triggersDeleteConfirmAction = "Silence it"
    override val triggersDeleteCancelAction = "Abandon"
    override val triggersCreateTitle = "Set an alarm"
    override val triggersKindPickerLabel = "What should you be warned of?"
    override val triggersAtLeastLabel = "At least"
    override val triggersAtLeastSuffix = "times"
    override val triggersWindowLabel = "Within"
    override val triggersWindowSeven = "7 days"
    override val triggersWindowThirty = "30 days"
    override val triggersWindowCustom = "Custom"
    override val triggersWindowCustomHint = "Days"
    override val triggersSilentLabel = "Silence of"
    override val triggersSilentSuffix = "days"
    override val triggersSaveButton = "Set the alarm"
    override val triggersCancelButton = "Abandon"
    override val triggersDecreaseCountDescription = "Diminish the threshold"
    override val triggersIncreaseCountDescription = "Swell the threshold"

    override val notificationChannelName = "Alarms"
    override val notificationChannelDescription = "What has stirred, and what has gone quiet."

    override fun triggerFiredNotificationTitle(caseName: String) = "$caseName has stirred"

    override fun checkInDueNotificationTitle(caseName: String) = "$caseName has gone quiet"

    override fun checkInDueNotificationBody(silentDays: Long) = "$silentDays days of silence. Has it stopped, or have you?"

    override val notificationLogAction = "Log it"
    override val notificationAllQuietAction = "All is still"

    override fun checkInsSummaryNotificationTitle(count: Int) = "$count cases have fallen silent — see which"

    override val notificationsDeniedBannerMessage =
        "Notifications are silenced. Alarms and the watch kept will not reach you — only what you find here."
    override val notificationsDeniedBannerAction = "Break the silence"

    override val widgetConfigureTitle = "Which cases shall haunt this widget?"
    override val widgetConfigureBody = "Choose what stands watch here. The choice may be revisited from any case's settings."
    override val widgetConfigureNoCasesMessage = "Nothing yet exists to watch. Summon a case first."
    override val widgetConfigureConfirmAction = "Bind to widget"
    override val widgetConfigureSkipAction = "Abandon"

    override val widgetNoPinnedCasesMessage = "Nothing pinned yet. Pin a case from its own screen to keep watch here."
    override val widgetStopAction = "Seal"

    override fun widgetTodayCount(count: Int) = "Today: $count"

    override val shareOpenDescription = "Share the record"
    override val shareHunchExpectedLabel = "claimed"
    override val shareHunchObservedLabel = "confirmed"
    override val shareHunchVsRealityToggleLabel = "Unveil the reckoning"
    override val shareNameFieldLabel = "Name for the record"
    override val shareSectionsPickerLabel = "What the record shows"

    override fun sharePunchline(
        direction: HunchDirection,
        band: ComparisonBand,
    ) = when (direction) {
        HunchDirection.TOO_OFTEN ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "The dread was overblown. Far less than feared."
                ComparisonBand.LESS -> "Less than feared, though the trail runs on."
                ComparisonBand.ABOUT_RIGHT -> "The record confirms the dread, near enough."
                ComparisonBand.MORE -> "Worse than feared, the evidence shows."
                ComparisonBand.MUCH_MORE -> "The dread was justified. Far more than feared."
            }
        HunchDirection.NOT_ENOUGH ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "The fear is confirmed. Far less than hoped."
                ComparisonBand.LESS -> "Still wanting, less than hoped."
                ComparisonBand.ABOUT_RIGHT -> "The record agrees, near enough to hope."
                ComparisonBand.MORE -> "Better than dared hoped, the evidence shows."
                ComparisonBand.MUCH_MORE -> "Far beyond hope, the evidence shows."
            }
        HunchDirection.JUST_CURIOUS ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Curiosity answered. Far below the guess."
                ComparisonBand.LESS -> "A little below the guess, the record shows."
                ComparisonBand.ABOUT_RIGHT -> "Curiosity answered. Near enough to the guess."
                ComparisonBand.MORE -> "A little above the guess, the record shows."
                ComparisonBand.MUCH_MORE -> "Curiosity answered. Far above the guess."
            }
    }
}

object BrightVoice : Voice {
    override val homeHeaderTitle = "How often does it totally happen?!"
    override val noCasesEmptyState = "It's quiet in here… suspiciously quiet."
    override val bigPictureEarlyDays = "Too soon to tell — feed me more moments!"
    override val bigPictureMonthPickerTitle = "Jump to a month!"
    override val bigPictureDayDetailEmptyState = "Nothing logged this day — a blank page."
    override val bigPictureWeekDetailEmptyState = "Nothing logged this week — a blank page."
    override val bigPictureEventNoteEmptyState = "No note — mystery!"
    override val bigPictureDialogCloseAction = "Got it, close this"
    override val bigPictureWeekViewDescription = "Peek at the week!"
    override val homeNavLabel = "Home"
    override val bigPictureNavLabel = "Big Picture"
    override val settingsNavLabel = "Settings"
    override val comingSoonPlaceholder = "Plot twist: not built yet!"
    override val newCaseTitle = "Crack open a new case"
    override val editCaseTitle = "Tweak the case"
    override val newCaseFabDescription = "Crack open a new case"
    override val backButtonDescription = "Back"
    override val caseNameLabel = "Name"
    override val caseNameHint = "e.g. Kiddo was rude"
    override val caseNameRequiredError = "Give it a name first!"
    override val caseDescriptionLabel = "Description (optional)"
    override val caseDescriptionHint = "Spill any extra details"
    override val caseIconLabel = "Icon"
    override val caseIconRequiredError = "Pick a little icon for it!"
    override val caseIconSectionExpandDescription = "Show me the icons!"
    override val caseIconSectionCollapseDescription = "Tuck the icons away"
    override val caseSectionInfoDescription = "Wait, what does this mean?"
    override val infoDialogDismissAction = "Got it!"
    override val caseLogFlowLabel = "Logging"
    override val caseLogFlowOneTap = "One tap"
    override val caseLogFlowDetailSheet = "Detail sheet"
    override val caseLogFlowInfoTitle = "Logging, explained"
    override val caseLogFlowInfoBody =
        "One tap logs it the second you tap — zero fuss, zero fields. Detail sheet pops up a quick form for " +
            "time, duration, intensity, and notes if you want more detail."
    override val caseDurationModeLabel = "Duration"
    override val caseDurationModeNone = "None"
    override val caseDurationModeManual = "Manual"
    override val caseDurationModeStartStop = "Start/stop"
    override val caseDurationModeInfoTitle = "Duration, explained"
    override val caseDurationModeInfoBody =
        "None means duration's not tracked. Manual lets you type in how long it took. Start/stop tracks it " +
            "live — hit Start, then Stop when it's over."
    override val caseIntensityToggleLabel = "Track intensity (1-5)"
    override val casePinnedToggleLabel = "Pin to widget"
    override val caseCheckInLabel = "Check-in"
    override val caseCheckInInfoTitle = "Check-in, explained"
    override val caseCheckInInfoBody =
        "Flip it on and you'll get a nudge after a quiet stretch — quicker if there's a Hunch running the " +
            "numbers, otherwise whatever Settings says. Off means no nudges for this case."
    override val caseSaveButton = "Save it!"
    override val caseDetailEditDescription = "Tweak the case"
    override val archiveCaseDescription = "Shelve this case"
    override val archiveCaseConfirmTitle = "Shelve this case?"
    override val archiveCaseConfirmBody =
        "It'll hide from Home and Big Picture, but nothing gets deleted — find it in the archive whenever you want it back."
    override val archiveCaseConfirmAction = "Shelve it"
    override val archiveCaseCancelAction = "Nah, keep it out"
    override val archivedCasesTitle = "The archive"
    override val archivedCasesEmptyState = "Nothing shelved yet — tidy!"
    override val eventListEmptyState = "Nothing logged yet — the plot is thin so far."

    override fun logSummaryLine(
        eventCount: Int,
        observedDays: Long,
    ) = "$eventCount logs so far, tracked for $observedDays days!"

    override val deleteEventConfirmTitle = "Zap this event?"
    override val deleteEventConfirmBody = "Poof — no take-backs."
    override val deleteEventConfirmAction = "Zap it"
    override val deleteEventCancelAction = "Never mind"
    override val deleteCaseForeverConfirmTitle = "Delete this case for good?"
    override val deleteCaseForeverConfirmAction = "Yeet it forever"
    override val deleteCaseForeverCancelAction = "Nah, never mind"
    override val retroLogEntryDescription = "Log the moment"
    override val logSheetNewEventTitle = "Log the moment"
    override val logSheetEditEventTitle = "Tweak this moment"
    override val logSheetTimeLabel = "When'd it happen?"
    override val logSheetIntensityLabel = "How intense?"
    override val logSheetDurationLabel = "How long?"
    override val logSheetDurationHint = "Minutes"
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
    override val staleOngoingEditEndTimeAction = "Fix the end time"
    override val staleOngoingStillGoingAction = "Yep, still going!"
    override val quickLogUndoAction = "Oops, undo!"
    override val settingsThemeSectionLabel = "Pick your vibe"
    override val themeOptionPlain = "Plain"
    override val themeOptionIntense = "Intense"
    override val themeOptionBright = "Bright"
    override val settingsPreviewLabel = "Sneak peek"
    override val settingsCheckInSectionLabel = "Nudge me"
    override val checkInIntervalOptionOff = "Off"
    override val checkInIntervalOptionSeven = "7d"
    override val checkInIntervalOptionFourteen = "14d"
    override val checkInIntervalOptionThirty = "30d"
    override val settingsCheckInInfoTitle = "Check-ins, explained"
    override val settingsCheckInInfoBody =
        "Sets how many quiet days trigger a nudge, for cases without their own Hunch. Got a Hunch running? " +
            "That case gets its own smarter timing instead. Off means no app-wide nudges — you can still " +
            "flip a single case off from its edit screen."
    override val settingsDemoDataSectionLabel = "Pretend data"
    override val settingsLoadDemoDataButton = "Load some pretend chaos!"
    override val settingsDemoDataLoadedMessage = "Fake drama, loaded!"
    override val settingsDeleteAllDataButton = "Nuke everything"
    override val settingsDeleteAllDataConfirmTitle = "Nuke everything?"
    override val settingsDeleteAllDataConfirmBody = "Every case and event goes poof — for real, no take-backs."
    override val settingsDeleteAllDataConfirmAction = "Yeet it all"
    override val settingsDeleteAllDataCancelAction = "Nah, never mind"
    override val settingsBackupSectionLabel = "Backup"
    override val settingsExportButton = "Save a backup!"
    override val settingsImportButton = "Restore a backup!"
    override val settingsImportConfirmTitle = "Swap in the backup?"
    override val settingsImportConfirmBody = "Everything here gets wiped and replaced with what's in that file. No undo button, promise!"
    override val settingsImportConfirmAction = "Swap it in!"
    override val settingsImportCancelAction = "Nah, never mind"
    override val settingsExportSuccessMessage = "Backup saved!"
    override val settingsExportFailureMessage = "Backup didn't save. Oops."
    override val settingsImportSuccessMessage = "Backup restored!"
    override val settingsImportFailureInvalidMessage = "That's not a HODITH backup file!"
    override val settingsImportFailureVersionMessage = "That backup's from a version this app can't read."
    override val settingsImportFailureIoMessage = "Couldn't read that file. Weird."
    override val aboutScreenTitle = "About HODITH!"
    override val aboutVersionLabel = "Version"
    override val aboutPrivacyLabel = "Privacy"
    override val aboutPrivacyBody = "Everything stays right here on your phone — no internet, no sneaky data stuff!"
    override val aboutLicensesLabel = "Licenses"
    override val aboutLicensesBody = "The nerdy open-source credits are coming soon!"
    override val hunchTabNoneTitle = "No guess yet!"
    override val hunchTabNoneBody = "Got a gut feeling about how often this happens? Make a guess and see if reality agrees."
    override val hunchAddButtonLabel = "Make a guess!"
    override val hunchNudgeTitle = "Ooh, 5 logs in!"
    override val hunchNudgeDismissAction = "Nah, don't ask"
    override val hunchEarlyHeadline = "Too soon to tell — feed me more moments!"
    override val hunchResolveLabel = "Lock it in"
    override val hunchCreatingTitle = "Make your guess!"
    override val hunchCreatingDirectionLabel = "How do you feel about it?"
    override val hunchCreatingFreqLabel = "How often does your gut say?"
    override val hunchCreatingFreqSuffix = "times per"
    override val hunchCreatingSaveButton = "Save my guess!"
    override val hunchCreatingDecreaseCountDescription = "Fewer!"
    override val hunchCreatingIncreaseCountDescription = "More!"
    override val hunchHistoryHeader = "Your hunch history!"
    override val hunchExpectedPerDay = "Day"
    override val hunchExpectedPerWeek = "Week"
    override val hunchExpectedPerMonth = "Month"
    override val insightsNotEnoughDataMessage = "Give it a little more time — the pattern's not ready yet!"

    override fun insightsGapNoteCurrent(days: Long) = "$days days of quiet! Suspicious… or just a slow patch?"

    override fun insightsGapNoteLongest(days: Long) = "$days days of quiet — the longest it's EVER gone!"

    override val insightsHeatmapShowMoreAction = "Show me more!"
    override val insightsHeatmapShowFewerAction = "Okay, tuck it back away"

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

    override val caseDetailLogTabLabel = "Log"
    override val caseDetailInsightsTabLabel = "Insights"
    override val caseDetailHunchTabLabel = "Hunch"

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

    override fun eventIntensityLabel(intensity: Int) = "Feels like a $intensity!"

    override fun eventDurationLabel(duration: String) = "Went on for $duration"

    override fun quickLogButtonDescription(caseName: String) = "Log $caseName!"

    override fun quickLogUndoMessage(caseName: String) = "Logged $caseName!"

    override fun startActionDescription(caseName: String) = "Start $caseName!"

    override fun stopActionDescription(caseName: String) = "Stop $caseName!"

    override fun ongoingIndicator(elapsed: String) = "Still going · $elapsed"

    override fun staleOngoingPromptMessage(
        caseName: String,
        elapsed: String,
    ) = "$caseName's been going $elapsed — still happening, or did you just forget?"

    override fun bigPictureWeekDetailTitle(date: String) = "Week of $date"

    override fun hunchNudgeBody(
        caseIcon: String,
        caseName: String,
    ) = "You've logged $caseIcon $caseName $HUNCH_NUDGE_EVENT_THRESHOLD times without saying what you expected. " +
        "Wanna guess and see if you're right?"

    override fun hunchDirectionPillLabel(direction: HunchDirection) =
        when (direction) {
            HunchDirection.TOO_OFTEN -> "Too often"
            HunchDirection.NOT_ENOUGH -> "Not enough"
            HunchDirection.JUST_CURIOUS -> "Just curious"
        }

    override fun hunchChipLabel(
        direction: HunchDirection,
        expectedFrequencyLabel: String,
    ): String {
        val inline =
            when (direction) {
                HunchDirection.TOO_OFTEN -> "too often"
                HunchDirection.NOT_ENOUGH -> "not enough"
                HunchDirection.JUST_CURIOUS -> "just curious"
            }
        return "Your guess: $inline, $expectedFrequencyLabel"
    }

    override fun hunchProgressLabel(
        eventCount: Int,
        windowDays: Long,
    ) = "$eventCount of $PRELIMINARY_MIN_EVENTS logs · $windowDays of $PRELIMINARY_MIN_DAYS days"

    override fun verdictHeadline(
        direction: HunchDirection,
        band: ComparisonBand,
        observedRateLabel: String,
    ): String =
        when (direction) {
            HunchDirection.TOO_OFTEN ->
                when (band) {
                    ComparisonBand.MUCH_LESS -> "Plot twist: only $observedRateLabel. Your brain lied!"
                    ComparisonBand.LESS -> "So far: $observedRateLabel — a little less than you guessed!"
                    ComparisonBand.ABOUT_RIGHT -> "Nailed it: $observedRateLabel — right on the money!"
                    ComparisonBand.MORE -> "Whoa: $observedRateLabel — more than you guessed!"
                    ComparisonBand.MUCH_MORE -> "Plot twist: $observedRateLabel — way more than you feared!"
                }
            HunchDirection.NOT_ENOUGH ->
                when (band) {
                    ComparisonBand.MUCH_LESS -> "Yep, called it: $observedRateLabel — barely happening at all."
                    ComparisonBand.LESS -> "So far: $observedRateLabel — still less than you'd like."
                    ComparisonBand.ABOUT_RIGHT -> "Nailed it: $observedRateLabel — right on the money!"
                    ComparisonBand.MORE -> "Good news: $observedRateLabel — more than you thought!"
                    ComparisonBand.MUCH_MORE -> "Whoa: $observedRateLabel — way more than you thought!"
                }
            HunchDirection.JUST_CURIOUS ->
                when (band) {
                    ComparisonBand.MUCH_LESS -> "Turns out: $observedRateLabel — way less than your guess!"
                    ComparisonBand.LESS -> "Turns out: $observedRateLabel — a bit less than your guess!"
                    ComparisonBand.ABOUT_RIGHT -> "Turns out: $observedRateLabel — right on your guess!"
                    ComparisonBand.MORE -> "Turns out: $observedRateLabel — a bit more than your guess!"
                    ComparisonBand.MUCH_MORE -> "Turns out: $observedRateLabel — way more than your guess!"
                }
        }

    override fun verdictMeta(
        tier: ConfidenceTier,
        eventCount: Int,
        windowDays: Long,
    ) = when (tier) {
        ConfidenceTier.PRELIMINARY -> "Based on $eventCount logs over $windowDays days. Give it a few more weeks to be sure."
        else -> "Based on $eventCount logs over $windowDays days."
    }

    override fun hunchHistorySummary(
        total: Int,
        heldUpCount: Int,
    ) = "$total guesses so far — ${total - heldUpCount} were off, $heldUpCount was spot-on!"

    override fun hunchHistoryRowOutcome(
        band: ComparisonBand,
        observedRateLabel: String,
    ) = when (band) {
        ComparisonBand.ABOUT_RIGHT -> "Spot-on — actually $observedRateLabel"
        else -> "Way off — actually $observedRateLabel"
    }

    override fun hunchHistoryRowWhen(monthsAgo: Long) = "$monthsAgo months ago"

    override val triggersScreenTitle = "Alerts!"
    override val triggersOpenDescription = "Check your alerts!"
    override val triggersFabDescription = "New alert!"
    override val triggersEmptyTitle = "No alerts yet!"
    override val triggersEmptyBody = "Want a nudge when something happens a lot, or goes quiet for a while? Set one up!"
    override val triggersEmptyCta = "Add an alert!"

    override fun triggerKindLabel(kind: TriggerKind) =
        when (kind) {
            TriggerKind.AT_LEAST -> "Happening a lot"
            TriggerKind.SILENT_FOR -> "Gone quiet"
        }

    override fun triggerSummary(
        kind: TriggerKind,
        threshold: Int,
        windowDays: Int?,
    ) = when (kind) {
        TriggerKind.AT_LEAST -> "$threshold+ times in $windowDays days"
        TriggerKind.SILENT_FOR -> "Quiet for $threshold days"
    }

    override fun triggerFiredAgo(daysAgo: Long) = "Popped off $daysAgo days ago!"

    override fun triggerToggleDescription(summary: String) = "Toggle alert: $summary"

    override fun triggerDeleteDescription(summary: String) = "Remove alert: $summary"

    override val triggersDeleteConfirmTitle = "Remove this alert?"
    override val triggersDeleteConfirmBody = "No more heads-up from this one."
    override val triggersDeleteConfirmAction = "Remove it"
    override val triggersDeleteCancelAction = "Never mind"
    override val triggersCreateTitle = "New alert!"
    override val triggersKindPickerLabel = "What should trigger it?"
    override val triggersAtLeastLabel = "At least"
    override val triggersAtLeastSuffix = "times"
    override val triggersWindowLabel = "Within"
    override val triggersWindowSeven = "7 days"
    override val triggersWindowThirty = "30 days"
    override val triggersWindowCustom = "Custom"
    override val triggersWindowCustomHint = "Days"
    override val triggersSilentLabel = "Quiet for"
    override val triggersSilentSuffix = "days"
    override val triggersSaveButton = "Save alert!"
    override val triggersCancelButton = "Never mind"
    override val triggersDecreaseCountDescription = "Fewer!"
    override val triggersIncreaseCountDescription = "More!"

    override val notificationChannelName = "Nudges"
    override val notificationChannelDescription = "Heads-up for triggers and check-ins."

    override fun triggerFiredNotificationTitle(caseName: String) = "$caseName just hit a trigger!"

    override fun checkInDueNotificationTitle(caseName: String) = "Quick check-in: $caseName"

    override fun checkInDueNotificationBody(silentDays: Long) = "Nothing logged in $silentDays days — all quiet, or did you forget?"

    override val notificationLogAction = "Log it!"
    override val notificationAllQuietAction = "All quiet!"

    override fun checkInsSummaryNotificationTitle(count: Int) = "$count cases are quiet — tap to check in!"

    override val notificationsDeniedBannerMessage =
        "Notifications are off, so trigger and check-in nudges can't reach you — swing by here instead!"
    override val notificationsDeniedBannerAction = "Turn on notifications"

    override val widgetConfigureTitle = "Pick your widget's stars!"
    override val widgetConfigureBody = "Choose which Cases get to show off here. Change your mind anytime from that Case's settings."
    override val widgetConfigureNoCasesMessage = "No cases yet! Make one in the app first."
    override val widgetConfigureConfirmAction = "Add to widget!"
    override val widgetConfigureSkipAction = "Never mind"

    override val widgetNoPinnedCasesMessage = "No pinned Cases yet! Pin one from its Case screen to see it here."
    override val widgetStopAction = "Stop!"

    override fun widgetTodayCount(count: Int) = "Today: $count"

    override val shareOpenDescription = "Share it!"
    override val shareHunchExpectedLabel = "guessed"
    override val shareHunchObservedLabel = "turns out"
    override val shareHunchVsRealityToggleLabel = "Show the surprise!"
    override val shareNameFieldLabel = "Name it!"
    override val shareSectionsPickerLabel = "Pick what to show!"

    override fun sharePunchline(
        direction: HunchDirection,
        band: ComparisonBand,
    ) = when (direction) {
        HunchDirection.TOO_OFTEN ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Phew! Way less than feared!"
                ComparisonBand.LESS -> "Whew, a little less than feared!"
                ComparisonBand.ABOUT_RIGHT -> "Nailed the guess!"
                ComparisonBand.MORE -> "Uh oh, more than expected!"
                ComparisonBand.MUCH_MORE -> "Whoa! Way more than feared!"
            }
        HunchDirection.NOT_ENOUGH ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Yep, called it — barely happening!"
                ComparisonBand.LESS -> "Still not enough, just as guessed!"
                ComparisonBand.ABOUT_RIGHT -> "Nailed it — right where expected!"
                ComparisonBand.MORE -> "More than expected — nice!"
                ComparisonBand.MUCH_MORE -> "Whoa, way more than hoped!"
            }
        HunchDirection.JUST_CURIOUS ->
            when (band) {
                ComparisonBand.MUCH_LESS -> "Surprise! Way less than guessed!"
                ComparisonBand.LESS -> "Turns out, a bit less than guessed!"
                ComparisonBand.ABOUT_RIGHT -> "Nailed it — right on the money!"
                ComparisonBand.MORE -> "Turns out, a bit more than guessed!"
                ComparisonBand.MUCH_MORE -> "Surprise! Way more than guessed!"
            }
    }
}

val LocalVoice = staticCompositionLocalOf<Voice> { PlainVoice }

fun voiceFor(theme: AppTheme): Voice =
    when (theme) {
        AppTheme.PLAIN -> PlainVoice
        AppTheme.INTENSE -> IntenseVoice
        AppTheme.BRIGHT -> BrightVoice
    }
