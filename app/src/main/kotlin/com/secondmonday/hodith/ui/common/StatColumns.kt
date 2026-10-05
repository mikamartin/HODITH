package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.viewmodel.MinAvgMaxValues

/** The three column labels of a [MinAvgMaxRow]. */
internal data class MinAvgMaxLabels(
    val min: String,
    val avg: String,
    val max: String,
)

/** The share card's Min / Avg / Max labels, used by its Gaps and Duration panels. */
internal fun Voice.shareMinAvgMaxLabels(): MinAvgMaxLabels = MinAvgMaxLabels(shareStatMinLabel, shareStatAvgLabel, shareStatMaxLabel)

/** Three equal columns, label over value: the shortest, average and longest of a measure. Shared by the Insights cards and the Share card's panels. */
@Composable
internal fun MinAvgMaxRow(
    labels: MinAvgMaxLabels,
    values: MinAvgMaxValues,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatColumn(labels.min, values.min)
        StatColumn(labels.avg, values.avg)
        StatColumn(labels.max, values.max)
    }
}

/** One label over its value, as a weighted column of a stat row. */
@Composable
internal fun RowScope.StatColumn(
    label: String,
    value: String,
) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}
