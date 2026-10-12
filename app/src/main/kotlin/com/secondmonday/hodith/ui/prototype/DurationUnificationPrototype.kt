// Throwaway Compose Preview spike for PROGRESS.md Story U1 (duration unification) — not wired to
// any ViewModel, domain code, or production screen. Local `remember` state only. Delete this file
// once the UX is validated and superseded by U2's real implementation (or rejected outright).
package com.secondmonday.hodith.ui.prototype

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.AppTheme
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.MILLIS_PER_HOUR
import com.secondmonday.hodith.domain.MILLIS_PER_MINUTE
import com.secondmonday.hodith.ui.common.SectionWithInfo
import com.secondmonday.hodith.ui.common.ToggleRow
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.HodithTheme
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalTimeFormat
import com.secondmonday.hodith.ui.voice.LocalVoice
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import com.secondmonday.hodith.viewmodel.DurationUnit
import com.secondmonday.hodith.viewmodel.durationUnitFor
import com.secondmonday.hodith.viewmodel.formatEventDate
import com.secondmonday.hodith.viewmodel.formatEventTimeOfDay
import java.time.ZoneId

private val PROTOTYPE_DURATION_UNITS = listOf(DurationUnit.MINUTES, DurationUnit.HOURS, DurationUnit.DAYS)

private fun prototypeDurationUnitLabel(unit: DurationUnit): String =
    when (unit) {
        DurationUnit.MINUTES -> "min"
        DurationUnit.HOURS -> "hr"
        DurationUnit.DAYS -> "day"
    }

/** Mirrors the real `durationFieldsFor` (file-private in LogDetailViewModel.kt) for this mockup's own local state. */
private fun prototypeDurationFields(durationMillis: Long): Pair<String, DurationUnit> {
    val positiveMillis = durationMillis.coerceAtLeast(0L)
    val unit = durationUnitFor(positiveMillis)
    val amount = if (positiveMillis > 0L) (positiveMillis / unit.millis).toString() else "0"
    return amount to unit
}

@Composable
private fun PrototypeDurationUnitSelector(
    selected: DurationUnit,
    onSelect: (DurationUnit) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PROTOTYPE_DURATION_UNITS.forEach { unit ->
            val isSelected = unit == selected
            Text(
                text = prototypeDurationUnitLabel(unit),
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .clickable { onSelect(unit) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The merged Log/Edit Event control being validated: a start time, then a single "Ongoing"
 * switch. On hides the end-time/duration fields; off reveals them, with duration and end time
 * kept in sync (editing either recalculates the other from [startedAt]) rather than stored
 * separately. Start/end date and time buttons are display-only in this mockup — the real
 * pickers already exist and aren't the UX question being validated here.
 */
@Composable
private fun UnifiedDurationControl(
    startedAt: Long,
    now: Long,
    initialOngoing: Boolean,
    initialEndedAt: Long,
    zone: ZoneId,
    voice: Voice,
) {
    var ongoing by remember { mutableStateOf(initialOngoing) }
    var endedAt by remember { mutableStateOf(initialEndedAt) }
    val initialFields = remember { prototypeDurationFields(initialEndedAt - startedAt) }
    var durationAmount by remember { mutableStateOf(initialFields.first) }
    var durationUnit by remember { mutableStateOf(initialFields.second) }

    fun close(at: Long) {
        ongoing = false
        endedAt = at
        val fields = prototypeDurationFields(at - startedAt)
        durationAmount = fields.first
        durationUnit = fields.second
    }

    fun recalcEndFromDuration(
        amount: String,
        unit: DurationUnit,
    ) {
        val value = amount.toLongOrNull()
        if (value != null) endedAt = startedAt + value * unit.millis
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        Column {
            Text("Start", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedButton(onClick = {}) { Text(formatEventDate(startedAt, zone)) }
                OutlinedButton(onClick = {}) { Text(formatEventTimeOfDay(startedAt, LocalTimeFormat.current.is24Hour, zone)) }
            }
        }

        // Placeholder label — not a Voice key; U2 adds the real one once this shape is validated.
        ToggleRow(
            label = "Ongoing",
            checked = ongoing,
            onCheckedChange = { checked -> if (checked) ongoing = true else close(now.coerceAtLeast(startedAt)) },
        )

        if (ongoing) {
            Text(voice.logSheetOngoingLabel, style = MaterialTheme.typography.bodyLarge)
            OutlinedButton(onClick = { close(now.coerceAtLeast(startedAt)) }) { Text(voice.logSheetStopNowAction) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {}) { Text(formatEventDate(endedAt, zone)) }
                OutlinedButton(onClick = {}) { Text(formatEventTimeOfDay(endedAt, LocalTimeFormat.current.is24Hour, zone)) }
            }
            OutlinedTextField(
                value = durationAmount,
                onValueChange = { input ->
                    durationAmount = input
                    recalcEndFromDuration(input, durationUnit)
                },
                label = { Text(voice.logSheetDurationLabel) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                trailingIcon = {
                    PrototypeDurationUnitSelector(
                        selected = durationUnit,
                        onSelect = { unit ->
                            durationUnit = unit
                            recalcEndFromDuration(durationAmount, unit)
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** 1. Case Edit — Duration becomes a single on/off switch, replacing the 3-way NONE/MANUAL/START_STOP picker. */
@Composable
private fun CaseDurationSwitchPreviewContent() {
    var tracksDuration by remember { mutableStateOf(true) }
    val voice = LocalVoice.current
    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
        SectionWithInfo(
            voice.caseDurationModeLabel,
            voice.caseDurationModeInfoTitle,
            voice.caseDurationModeInfoBody,
            voice.caseSectionInfoDescription,
        ) {
            // Placeholder label — not a Voice key; U2 adds the real one once this shape is validated.
            ToggleRow(label = "Track duration", checked = tracksDuration, onCheckedChange = { tracksDuration = it })
        }
    }
}

/** 2. Log Event — a fresh event defaults to ongoing (today's "leaving endedAt null IS ongoing" convention). */
@Composable
private fun LogEventUnifiedControlPreviewContent() {
    val voice = LocalVoice.current
    val now = remember { System.currentTimeMillis() }
    Column(modifier = Modifier.fillMaxWidth()) {
        UnifiedDurationControl(
            startedAt = now,
            now = now,
            initialOngoing = true,
            initialEndedAt = now,
            zone = ZoneId.systemDefault(),
            voice = voice,
        )
    }
}

/** 3. Edit Event — one ongoing sample and one already-closed sample, to exercise both toggle directions. */
@Composable
private fun EditEventUnifiedControlPreviewContent() {
    val voice = LocalVoice.current
    val now = remember { System.currentTimeMillis() }
    val ongoingStartedAt = now - 2 * MILLIS_PER_HOUR
    val closedStartedAt = now - 3 * MILLIS_PER_DAY
    val closedEndedAt = closedStartedAt + 90 * MILLIS_PER_MINUTE

    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        Text("Ongoing sample", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp))
        UnifiedDurationControl(
            startedAt = ongoingStartedAt,
            now = now,
            initialOngoing = true,
            initialEndedAt = ongoingStartedAt,
            zone = ZoneId.systemDefault(),
            voice = voice,
        )
        HorizontalDivider()
        Text("Closed sample", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 0.dp))
        UnifiedDurationControl(
            startedAt = closedStartedAt,
            now = now,
            initialOngoing = false,
            initialEndedAt = closedEndedAt,
            zone = ZoneId.systemDefault(),
            voice = voice,
        )
    }
}

@Preview(name = "Case duration switch — Plain light", showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun CaseDurationSwitchPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            CaseDurationSwitchPreviewContent()
        }
    }
}

@Preview(name = "Case duration switch — Bright light", showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun CaseDurationSwitchBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) {
            CaseDurationSwitchPreviewContent()
        }
    }
}

@Preview(name = "Log event unified control — Plain light", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
private fun LogEventUnifiedControlPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            LogEventUnifiedControlPreviewContent()
        }
    }
}

@Preview(name = "Log event unified control — Bright light", showBackground = true, widthDp = 360, heightDp = 420)
@Composable
private fun LogEventUnifiedControlBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) {
            LogEventUnifiedControlPreviewContent()
        }
    }
}

@Preview(name = "Edit event unified control — Plain light", showBackground = true, widthDp = 360, heightDp = 820)
@Composable
private fun EditEventUnifiedControlPlainLightPreview() {
    HodithTheme(theme = AppTheme.PLAIN, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.PLAIN,
            LocalVoice provides voiceFor(AppTheme.PLAIN),
        ) {
            EditEventUnifiedControlPreviewContent()
        }
    }
}

@Preview(name = "Edit event unified control — Bright light", showBackground = true, widthDp = 360, heightDp = 820)
@Composable
private fun EditEventUnifiedControlBrightLightPreview() {
    HodithTheme(theme = AppTheme.BRIGHT, darkTheme = false) {
        CompositionLocalProvider(
            LocalCardDecorationStyle provides CardDecorationStyle.BRIGHT,
            LocalVoice provides voiceFor(AppTheme.BRIGHT),
        ) {
            EditEventUnifiedControlPreviewContent()
        }
    }
}
