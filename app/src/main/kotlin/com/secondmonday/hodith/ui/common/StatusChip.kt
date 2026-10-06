package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A small filled label marking one row's status, e.g. a Trends row's Pattern/Hint tier or the Gaps
 * card's burst flag. The one chip shape the Insights cards share, so a status reads the same wherever it
 * appears; the caller picks the colour pair from the theme.
 */
@Composable
fun StatusChip(
    label: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier, color = containerColor, contentColor = contentColor, shape = MaterialTheme.shapes.small) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
