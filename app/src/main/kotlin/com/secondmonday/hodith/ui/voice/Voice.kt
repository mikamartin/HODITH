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
    val eventListEmptyState: String
    val deleteEventConfirmTitle: String
    val deleteEventConfirmBody: String
    val deleteEventConfirmAction: String
    val deleteEventCancelAction: String
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
    val quickLogUndoAction: String

    fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ): String

    fun eventIntensityLabel(intensity: Int): String

    fun quickLogButtonDescription(caseName: String): String

    fun quickLogUndoMessage(caseName: String): String
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
    override val eventListEmptyState = "No events logged yet."
    override val deleteEventConfirmTitle = "Delete this event?"
    override val deleteEventConfirmBody = "This can't be undone."
    override val deleteEventConfirmAction = "Delete"
    override val deleteEventCancelAction = "Cancel"
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
    override val quickLogUndoAction = "Undo"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount · This week: $weekCount"

    override fun eventIntensityLabel(intensity: Int) = "Intensity $intensity"

    override fun quickLogButtonDescription(caseName: String) = "Log $caseName now"

    override fun quickLogUndoMessage(caseName: String) = "Logged $caseName."
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
    override val eventListEmptyState = "No evidence gathered yet."
    override val deleteEventConfirmTitle = "Strike this from the record?"
    override val deleteEventConfirmBody = "Once gone, it cannot be recalled."
    override val deleteEventConfirmAction = "Erase"
    override val deleteEventCancelAction = "Spare it"
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
    override val quickLogUndoAction = "Reverse it"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount — this week: $weekCount"

    override fun eventIntensityLabel(intensity: Int) = "Intensity: $intensity"

    override fun quickLogButtonDescription(caseName: String) = "Add $caseName to the record"

    override fun quickLogUndoMessage(caseName: String) = "$caseName entered into the record."
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
    override val eventListEmptyState = "Nothing logged yet — the plot is thin so far."
    override val deleteEventConfirmTitle = "Zap this event?"
    override val deleteEventConfirmBody = "Poof — no take-backs."
    override val deleteEventConfirmAction = "Zap it"
    override val deleteEventCancelAction = "Never mind"
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
    override val quickLogUndoAction = "Oops, undo!"

    override fun homeCaseCounts(
        todayCount: Int,
        weekCount: Int,
    ) = "Today: $todayCount (this week: $weekCount)"

    override fun eventIntensityLabel(intensity: Int) = "Feels like a $intensity!"

    override fun quickLogButtonDescription(caseName: String) = "Log $caseName!"

    override fun quickLogUndoMessage(caseName: String) = "Logged $caseName!"
}

val LocalVoice = staticCompositionLocalOf<Voice> { SeriousVoice }
