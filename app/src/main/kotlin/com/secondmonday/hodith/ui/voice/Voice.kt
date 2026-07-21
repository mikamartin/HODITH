package com.secondmonday.hodith.ui.voice

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One user-visible string per key, in three personalities (spec §12). Composables read
 * [LocalVoice] instead of branching on theme, so a string can never ship in only one voice.
 * Minimal key set for now — only what Home and Big Picture need so far; Phase 4 extends this
 * interface with the full string set rather than replacing it.
 */
interface Voice {
    val noCasesEmptyState: String
    val bigPictureEarlyDays: String
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
    val caseLogFlowLabel: String
    val caseLogFlowOneTap: String
    val caseLogFlowDetailSheet: String
    val caseDurationModeLabel: String
    val caseDurationModeNone: String
    val caseDurationModeManual: String
    val caseDurationModeStartStop: String
    val caseIntensityToggleLabel: String
    val casePinnedToggleLabel: String
    val caseCheckInLabel: String
    val caseCheckInDefault: String
    val caseCheckInCustom: String
    val caseCheckInOff: String
    val caseCheckInCustomDaysHint: String
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
}

object SeriousVoice : Voice {
    override val noCasesEmptyState = "No cases yet."
    override val bigPictureEarlyDays = "Insufficient data. Keep logging."
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
    override val caseLogFlowLabel = "Logging"
    override val caseLogFlowOneTap = "One tap"
    override val caseLogFlowDetailSheet = "Detail sheet"
    override val caseDurationModeLabel = "Duration"
    override val caseDurationModeNone = "None"
    override val caseDurationModeManual = "Manual"
    override val caseDurationModeStartStop = "Start/stop"
    override val caseIntensityToggleLabel = "Track intensity (1-5)"
    override val casePinnedToggleLabel = "Pin to widget"
    override val caseCheckInLabel = "Check-in"
    override val caseCheckInDefault = "Use default"
    override val caseCheckInCustom = "Custom"
    override val caseCheckInOff = "Off"
    override val caseCheckInCustomDaysHint = "Days"
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
}

object GothVoice : Voice {
    override val noCasesEmptyState = "Nothing is being watched. Yet."
    override val bigPictureEarlyDays = "The evidence is yet insufficient for despair."
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
    override val caseLogFlowLabel = "Logging"
    override val caseLogFlowOneTap = "One tap"
    override val caseLogFlowDetailSheet = "Detail sheet"
    override val caseDurationModeLabel = "Duration"
    override val caseDurationModeNone = "None"
    override val caseDurationModeManual = "Manual"
    override val caseDurationModeStartStop = "Start/stop"
    override val caseIntensityToggleLabel = "Track intensity (1-5)"
    override val casePinnedToggleLabel = "Pin to widget"
    override val caseCheckInLabel = "Check-in"
    override val caseCheckInDefault = "Use default"
    override val caseCheckInCustom = "Custom"
    override val caseCheckInOff = "Off"
    override val caseCheckInCustomDaysHint = "Days"
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
}

object QuirkyVoice : Voice {
    override val noCasesEmptyState = "It's quiet in here… suspiciously quiet."
    override val bigPictureEarlyDays = "Too soon to tell — feed me more moments!"
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
    override val caseLogFlowLabel = "Logging"
    override val caseLogFlowOneTap = "One tap"
    override val caseLogFlowDetailSheet = "Detail sheet"
    override val caseDurationModeLabel = "Duration"
    override val caseDurationModeNone = "None"
    override val caseDurationModeManual = "Manual"
    override val caseDurationModeStartStop = "Start/stop"
    override val caseIntensityToggleLabel = "Track intensity (1-5)"
    override val casePinnedToggleLabel = "Pin to widget"
    override val caseCheckInLabel = "Check-in"
    override val caseCheckInDefault = "Use default"
    override val caseCheckInCustom = "Custom"
    override val caseCheckInOff = "Off"
    override val caseCheckInCustomDaysHint = "Days"
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
}

val LocalVoice = staticCompositionLocalOf<Voice> { SeriousVoice }
