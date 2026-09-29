package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.bigpicture.BrightChip
import com.secondmonday.hodith.ui.bigpicture.CHIP_SHAPE
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle

/**
 * Small trigger chip ("Cases: N ▸") opening a full picker dialog — Big Picture's original pattern
 * (Cases/Tags/Year filters), reused as-is by the Case Detail Log tab's Sort/Range chips rather than
 * a second copy. [isFiltered] draws a highlight border/ring when this dimension is narrowed off its
 * default; otherwise the chip stays the neutral/unselected pill look, since the label+count
 * communicate state, not the chip's own selection styling.
 */
@Composable
fun FilterTriggerChip(
    label: String,
    count: String,
    onClick: () -> Unit,
    isFiltered: Boolean = false,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            BrightChip(selected = isFiltered, onToggle = onClick, tint = MaterialTheme.colorScheme.primary) {
                FilterTriggerChipContent(label, count)
            }
        CardDecorationStyle.PLAIN, CardDecorationStyle.INTENSE ->
            Row(
                modifier =
                    Modifier
                        .clip(CHIP_SHAPE)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            width = if (isFiltered) 1.5.dp else 1.dp,
                            color = if (isFiltered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = CHIP_SHAPE,
                        ).clickable(onClick = onClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilterTriggerChipContent(label, count)
            }
    }
}

/** [label] stays its own text node (unmodified, no colon) so existing exact-text chip lookups keep working; the colon lands on [count] instead. */
@Composable
private fun RowScope.FilterTriggerChipContent(
    label: String,
    count: String,
) {
    Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    Text(": $count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("▸", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}
