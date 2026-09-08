package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.data.ObservationWindow
import com.secondmonday.hodith.data.VerdictMetric
import com.secondmonday.hodith.data.tracksDuration
import com.secondmonday.hodith.ui.common.NumberStepper
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.datePickerDateAtLocalStartOfDay
import com.secondmonday.hodith.viewmodel.formatMediumDate
import com.secondmonday.hodith.viewmodel.toDatePickerUtcMillis
import java.time.Instant
import java.time.ZoneId

internal val EXPECTED_COUNT_RANGE = 1..99
private const val DEFAULT_EXPECTED_COUNT = 3

/** The period options offered for [metric] — days-active drops Day (nonsensical) and adds a quarter. */
internal fun periodOptionsFor(metric: VerdictMetric): List<ExpectedPer> =
    if (metric == VerdictMetric.DAYS_ACTIVE) {
        listOf(ExpectedPer.WEEK, ExpectedPer.MONTH, ExpectedPer.QUARTER)
    } else {
        listOf(ExpectedPer.DAY, ExpectedPer.WEEK, ExpectedPer.MONTH)
    }

/**
 * Keeps [current] if [metric] still offers it, otherwise the nearest period that survives the
 * metric flip: Day (occurrence-only) steps up to Week, a quarter (days-active-only) steps down to
 * Month.
 */
internal fun coerceExpectedPer(
    metric: VerdictMetric,
    current: ExpectedPer,
): ExpectedPer =
    when {
        current in periodOptionsFor(metric) -> current
        metric == VerdictMetric.DAYS_ACTIVE -> ExpectedPer.WEEK
        else -> ExpectedPer.MONTH
    }

/**
 * New-Hunch bottom sheet (spec §7/§8): direction, expected count/unit, plus two once-at-creation
 * choices — the verdict metric (only for a Case that already tracks duration) and the observation
 * window (every Case). All sections render flat and always visible when applicable; no disclosure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HunchCreationSheet(
    voice: Voice,
    durationMode: DurationMode,
    caseCreatedAt: Long,
    onDismiss: () -> Unit,
    onSave: (
        direction: HunchDirection,
        expectedCount: Int,
        expectedPer: ExpectedPer,
        metric: VerdictMetric,
        observationWindow: ObservationWindow,
        windowStartDate: Long?,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    var direction by remember { mutableStateOf(HunchDirection.TOO_OFTEN) }
    var expectedCount by remember { mutableIntStateOf(DEFAULT_EXPECTED_COUNT) }
    var expectedPer by remember { mutableStateOf(ExpectedPer.WEEK) }
    var metric by remember { mutableStateOf(VerdictMetric.OCCURRENCE_COUNT) }
    var observationWindow by remember { mutableStateOf(ObservationWindow.SINCE_START) }
    var customStartDate by remember { mutableStateOf(caseCreatedAt) }
    var showDatePicker by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(voice.hunchCreatingTitle, style = MaterialTheme.typography.titleLarge)

            LabelledSection(voice.hunchCreatingDirectionLabel) {
                SegmentedChoiceRow(
                    options = HunchDirection.entries.map { it to voice.hunchDirectionPillLabel(it) },
                    selected = direction,
                    onSelect = { direction = it },
                )
            }

            if (durationMode.tracksDuration) {
                LabelledSection(voice.hunchCreatingMetricLabel) {
                    SegmentedChoiceRow(
                        options =
                            listOf(
                                VerdictMetric.OCCURRENCE_COUNT to voice.hunchMetricOccurrence,
                                VerdictMetric.DAYS_ACTIVE to voice.hunchMetricDaysActive,
                            ),
                        selected = metric,
                        onSelect = { picked ->
                            metric = picked
                            expectedPer = coerceExpectedPer(picked, expectedPer)
                        },
                    )
                }
            }

            LabelledSection(voice.hunchCreatingFreqLabel) {
                NumberStepper(
                    value = expectedCount,
                    range = EXPECTED_COUNT_RANGE,
                    suffix =
                        if (metric == VerdictMetric.DAYS_ACTIVE) {
                            voice.hunchCreatingFreqSuffixDaysActive
                        } else {
                            voice.hunchCreatingFreqSuffix
                        },
                    decreaseDescription = voice.hunchCreatingDecreaseCountDescription,
                    increaseDescription = voice.hunchCreatingIncreaseCountDescription,
                    onChange = { expectedCount = it },
                )
                SegmentedChoiceRow(
                    options = periodOptionsFor(metric).map { it to expectedPerLabel(it, voice) },
                    selected = expectedPer,
                    onSelect = { expectedPer = it },
                )
            }

            LabelledSection(voice.hunchCreatingWindowLabel) {
                SegmentedChoiceRow(
                    options =
                        listOf(
                            ObservationWindow.SINCE_START to voice.hunchWindowSinceStart,
                            ObservationWindow.LAST_3_MONTHS to voice.hunchWindowLast3Months,
                            ObservationWindow.CUSTOM to voice.hunchWindowCustom,
                        ),
                    selected = observationWindow,
                    onSelect = { observationWindow = it },
                )
                if (observationWindow == ObservationWindow.CUSTOM) {
                    Text(
                        voice.hunchWindowCustomDatePrompt,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    OutlinedButton(
                        onClick = { showDatePicker = true },
                        modifier =
                            Modifier
                                .padding(top = 4.dp)
                                .semantics { contentDescription = voice.hunchWindowCustomDateDescription },
                    ) {
                        Text(formatMediumDate(Instant.ofEpochMilli(customStartDate).atZone(ZoneId.systemDefault()).toLocalDate()))
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        direction,
                        expectedCount,
                        expectedPer,
                        metric,
                        observationWindow,
                        customStartDate.takeIf { observationWindow == ObservationWindow.CUSTOM },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(voice.hunchCreatingSaveButton)
            }
        }
    }

    if (showDatePicker) {
        WindowStartDatePickerDialog(
            selectedDate = customStartDate,
            minDate = caseCreatedAt,
            voice = voice,
            onDismiss = { showDatePicker = false },
            onConfirm = { picked ->
                customStartDate = picked
                showDatePicker = false
            },
        )
    }
}

/** A `labelLarge` heading over its section content — the sheet's one repeated structural shape. */
@Composable
private fun LabelledSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        content()
    }
}

private fun expectedPerLabel(
    per: ExpectedPer,
    voice: Voice,
): String =
    when (per) {
        ExpectedPer.DAY -> voice.hunchExpectedPerDay
        ExpectedPer.WEEK -> voice.hunchExpectedPerWeek
        ExpectedPer.MONTH -> voice.hunchExpectedPerMonth
        ExpectedPer.QUARTER -> voice.hunchExpectedPerQuarter
    }

/**
 * Local-date picker for the custom window start, floored at [minDate] (the Case's own creation —
 * a "custom" pick identical to "since the start" is just a confusing label). Reuses the shared
 * [toDatePickerUtcMillis] / [datePickerDateAtLocalStartOfDay] round-trip the Log sheet's date
 * picker also uses.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WindowStartDatePickerDialog(
    selectedDate: Long,
    minDate: Long,
    voice: Voice,
    onDismiss: () -> Unit,
    onConfirm: (localMillis: Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val minUtcMillis = toDatePickerUtcMillis(minDate, zone)
    val datePickerState =
        rememberDatePickerState(
            initialSelectedDateMillis = toDatePickerUtcMillis(selectedDate, zone),
            selectableDates =
                remember(minUtcMillis) {
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minUtcMillis
                    }
                },
        )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { utcMillis ->
                    onConfirm(maxOf(datePickerDateAtLocalStartOfDay(utcMillis, zone), minDate))
                }
                onDismiss()
            }) { Text(voice.logSheetPickerConfirm) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(voice.logSheetPickerCancel) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
