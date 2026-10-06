package com.secondmonday.hodith.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.secondmonday.hodith.ui.theme.CardDecorationStyle
import com.secondmonday.hodith.ui.theme.GlowCard
import com.secondmonday.hodith.ui.theme.LocalCardDecorationStyle

/**
 * Area grouping shared by the Settings and Manage tags screens. Bright branches to [GlowCard] (same
 * dispatch as [com.secondmonday.hodith.ui.casedetail.InsightsTab]'s `InsightsCard`); Intense keeps the
 * thin-border [OutlinedCard]; Plain is a borderless white plank on the tinted screen background.
 */
@Composable
fun Plank(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    when (LocalCardDecorationStyle.current) {
        CardDecorationStyle.BRIGHT ->
            GlowCard {
                title?.let { PlankAreaHeader(it) }
                content()
            }
        CardDecorationStyle.PLAIN ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) { PlankContent(title, content) }
        CardDecorationStyle.INTENSE ->
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) { PlankContent(title, content) }
    }
}

@Composable
private fun PlankContent(
    title: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        title?.let { PlankAreaHeader(it) }
        content()
    }
}

@Composable
private fun PlankAreaHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Start,
    )
}
