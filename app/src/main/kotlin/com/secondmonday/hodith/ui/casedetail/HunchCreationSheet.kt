package com.secondmonday.hodith.ui.casedetail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.HunchDirection
import com.secondmonday.hodith.ui.common.SegmentedChoiceRow
import com.secondmonday.hodith.ui.voice.Voice

private val EXPECTED_COUNT_RANGE = 1..99
private const val DEFAULT_EXPECTED_COUNT = 3

/** New-Hunch bottom sheet (spec §7): direction, expected count/unit — same shape as [com.secondmonday.hodith.ui.logsheet.LogDetailSheet]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HunchCreationSheet(
    voice: Voice,
    onDismiss: () -> Unit,
    onSave: (direction: HunchDirection, expectedCount: Int, expectedPer: ExpectedPer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var direction by remember { mutableStateOf(HunchDirection.TOO_OFTEN) }
    var expectedCount by remember { mutableIntStateOf(DEFAULT_EXPECTED_COUNT) }
    var expectedPer by remember { mutableStateOf(ExpectedPer.WEEK) }

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

            Column {
                Text(voice.hunchCreatingDirectionLabel, style = MaterialTheme.typography.labelLarge)
                SegmentedChoiceRow(
                    options = HunchDirection.entries.map { it to voice.hunchDirectionPillLabel(it) },
                    selected = direction,
                    onSelect = { direction = it },
                )
            }

            Column {
                Text(voice.hunchCreatingFreqLabel, style = MaterialTheme.typography.labelLarge)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    IconButton(
                        onClick = { expectedCount = (expectedCount - 1).coerceIn(EXPECTED_COUNT_RANGE) },
                        modifier = Modifier.semantics { contentDescription = voice.hunchCreatingDecreaseCountDescription },
                    ) {
                        Text("−", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(expectedCount.toString(), style = MaterialTheme.typography.headlineSmall)
                    IconButton(
                        onClick = { expectedCount = (expectedCount + 1).coerceIn(EXPECTED_COUNT_RANGE) },
                        modifier = Modifier.semantics { contentDescription = voice.hunchCreatingIncreaseCountDescription },
                    ) {
                        Text("+", style = MaterialTheme.typography.headlineSmall)
                    }
                    Text(
                        voice.hunchCreatingFreqSuffix,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                SegmentedChoiceRow(
                    options =
                        listOf(
                            ExpectedPer.DAY to voice.hunchExpectedPerDay,
                            ExpectedPer.WEEK to voice.hunchExpectedPerWeek,
                            ExpectedPer.MONTH to voice.hunchExpectedPerMonth,
                        ),
                    selected = expectedPer,
                    onSelect = { expectedPer = it },
                )
            }

            Button(
                onClick = { onSave(direction, expectedCount, expectedPer) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(voice.hunchCreatingSaveButton)
            }
        }
    }
}
