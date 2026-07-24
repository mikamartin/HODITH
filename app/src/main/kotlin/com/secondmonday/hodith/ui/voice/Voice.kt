package com.secondmonday.hodith.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf
import com.secondmonday.hodith.data.AppTheme

/**
 * One user-visible string per key, in three personalities (spec §12). Composables read
 * [LocalVoice] instead of branching on theme, so a string can never ship in only one voice.
 * Minimal key set for now — only what Home and Big Picture need so far; Phase 4 extends this
 * interface with the full string set rather than replacing it.
 */
interface Voice {
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
    val caseCheckInDefault: String
    val caseCheckInCustom: String
    val caseCheckInOff: String
    val caseCheckInCustomDaysHint: String
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
    val deleteEventConfirmTitle: String
    val deleteEventConfirmBody: String
    val deleteEventConfirmAction: String
    val deleteEventCancelAction: String
    val deleteCaseForeverConfirmTitle: String
    val deleteCaseForeverConfirmAction: String
    val deleteCaseForeverCancelAction: String
    val retroLogEntryLabel: String
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
    val settingsDemoDataSectionLabel: String
    val settingsLoadDemoDataButton: String
    val settingsDemoDataLoadedMessage: String
    val settingsDeleteAllDataButton: String
    val settingsDeleteAllDataConfirmTitle: String
    val settingsDeleteAllDataConfirmBody: String
    val settingsDeleteAllDataConfirmAction: String
    val settingsDeleteAllDataCancelAction: String

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
}

object PlainVoice : Voice {
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
    override val caseCheckInDefault = "Use default"
    override val caseCheckInCustom = "Custom"
    override val caseCheckInOff = "Off"
    override val caseCheckInCustomDaysHint = "Days"
    override val caseCheckInInfoTitle = "About check-in"
    override val caseCheckInInfoBody =
        "A check-in nudge appears after a stretch of silence on this case. Use default follows Settings' " +
            "app-wide interval, Custom sets a day count just for this case, Off disables it entirely."
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
    override val deleteEventConfirmTitle = "Delete this event?"
    override val deleteEventConfirmBody = "This can't be undone."
    override val deleteEventConfirmAction = "Delete"
    override val deleteEventCancelAction = "Cancel"
    override val deleteCaseForeverConfirmTitle = "Delete this case forever?"
    override val deleteCaseForeverConfirmAction = "Delete forever"
    override val deleteCaseForeverCancelAction = "Cancel"
    override val retroLogEntryLabel = "It happened earlier…"
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
    override val settingsDemoDataSectionLabel = "Demo data"
    override val settingsLoadDemoDataButton = "Load demo data"
    override val settingsDemoDataLoadedMessage = "Demo data loaded."
    override val settingsDeleteAllDataButton = "Delete all data"
    override val settingsDeleteAllDataConfirmTitle = "Delete all data?"
    override val settingsDeleteAllDataConfirmBody =
        "Every case and event will be permanently deleted. This can't be undone."
    override val settingsDeleteAllDataConfirmAction = "Delete everything"
    override val settingsDeleteAllDataCancelAction = "Cancel"

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
}

object IntenseVoice : Voice {
    override val noCasesEmptyState = "Nothing is being watched. Yet."
    override val bigPictureEarlyDays = "The evidence is yet insufficient for despair."
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
    override val caseCheckInDefault = "Use default"
    override val caseCheckInCustom = "Custom"
    override val caseCheckInOff = "Off"
    override val caseCheckInCustomDaysHint = "Days"
    override val caseCheckInInfoTitle = "On the watch kept"
    override val caseCheckInInfoBody =
        "The check-in nudge stirs after this case has lain silent too long. Use default heeds the interval " +
            "Settings decree for all cases. Custom sets its own count of days. Off silences the nudge for good."
    override val caseSaveButton = "Seal it"
    override val caseDetailEditDescription = "Revise the case"
    override val archiveCaseDescription = "Bury this case"
    override val archiveCaseConfirmTitle = "Bury this case?"
    override val archiveCaseConfirmBody =
        "It will vanish from Home and the record, but nothing is lost — it waits in the archive, ready to be exhumed."
    override val archiveCaseConfirmAction = "Bury it"
    override val archiveCaseCancelAction = "Spare it"
    override val archivedCasesTitle = "The buried cases"
    override val archivedCasesEmptyState = "Nothing lies buried here."
    override val eventListEmptyState = "No evidence gathered yet."
    override val deleteEventConfirmTitle = "Strike this from the record?"
    override val deleteEventConfirmBody = "Once gone, it cannot be recalled."
    override val deleteEventConfirmAction = "Erase"
    override val deleteEventCancelAction = "Spare it"
    override val deleteCaseForeverConfirmTitle = "Erase this case forever?"
    override val deleteCaseForeverConfirmAction = "Erase forever"
    override val deleteCaseForeverCancelAction = "Spare it"
    override val retroLogEntryLabel = "It happened before now…"
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
    override val settingsDemoDataSectionLabel = "Phantom data"
    override val settingsLoadDemoDataButton = "Conjure phantom cases"
    override val settingsDemoDataLoadedMessage = "The phantoms have arrived."
    override val settingsDeleteAllDataButton = "Erase everything"
    override val settingsDeleteAllDataConfirmTitle = "Erase everything?"
    override val settingsDeleteAllDataConfirmBody =
        "Every case and record will be struck from existence, beyond recall."
    override val settingsDeleteAllDataConfirmAction = "Erase it all"
    override val settingsDeleteAllDataCancelAction = "Spare it"

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
}

object BrightVoice : Voice {
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
    override val caseCheckInDefault = "Use default"
    override val caseCheckInCustom = "Custom"
    override val caseCheckInOff = "Off"
    override val caseCheckInCustomDaysHint = "Days"
    override val caseCheckInInfoTitle = "Check-in, explained"
    override val caseCheckInInfoBody =
        "The check-in nudge pops up if this case goes quiet too long. Use default follows whatever Settings " +
            "says app-wide, Custom lets you pick your own day count, Off turns it off completely."
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
    override val deleteEventConfirmTitle = "Zap this event?"
    override val deleteEventConfirmBody = "Poof — no take-backs."
    override val deleteEventConfirmAction = "Zap it"
    override val deleteEventCancelAction = "Never mind"
    override val deleteCaseForeverConfirmTitle = "Delete this case for good?"
    override val deleteCaseForeverConfirmAction = "Yeet it forever"
    override val deleteCaseForeverCancelAction = "Nah, never mind"
    override val retroLogEntryLabel = "Oh right, it happened earlier…"
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
    override val settingsDemoDataSectionLabel = "Pretend data"
    override val settingsLoadDemoDataButton = "Load some pretend chaos!"
    override val settingsDemoDataLoadedMessage = "Fake drama, loaded!"
    override val settingsDeleteAllDataButton = "Nuke everything"
    override val settingsDeleteAllDataConfirmTitle = "Nuke everything?"
    override val settingsDeleteAllDataConfirmBody = "Every case and event goes poof — for real, no take-backs."
    override val settingsDeleteAllDataConfirmAction = "Yeet it all"
    override val settingsDeleteAllDataCancelAction = "Nah, never mind"

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
}

val LocalVoice = staticCompositionLocalOf<Voice> { PlainVoice }

fun voiceFor(theme: AppTheme): Voice =
    when (theme) {
        AppTheme.PLAIN -> PlainVoice
        AppTheme.INTENSE -> IntenseVoice
        AppTheme.BRIGHT -> BrightVoice
    }
