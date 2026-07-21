package com.secondmonday.hodith.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

private const val DEFAULT_TICK_INTERVAL_MILLIS = 60_000L

/**
 * A [State] that refreshes every [intervalMillis] for as long as the calling composable is on
 * screen — used to keep an ongoing event's elapsed-time display counting up (spec §6) without
 * threading a timer through the data-driven ViewModel `StateFlow`s. This is UI-only refresh, not
 * business logic, so it deliberately reads [clockNow] directly rather than going through the
 * pattern CLAUDE.md reserves for verdict/trigger/stats' injected `Clock`.
 */
@Composable
fun rememberTickingNow(
    intervalMillis: Long = DEFAULT_TICK_INTERVAL_MILLIS,
    clockNow: () -> Long,
): State<Long> {
    val now = remember { mutableLongStateOf(clockNow()) }
    LaunchedEffect(intervalMillis) {
        while (true) {
            delay(intervalMillis)
            now.longValue = clockNow()
        }
    }
    return now
}
