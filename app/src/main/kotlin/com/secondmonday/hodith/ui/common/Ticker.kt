package com.secondmonday.hodith.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

private const val DEFAULT_TICK_INTERVAL_MILLIS = 60_000L

/**
 * A [MutableState] that refreshes every [intervalMillis] for as long as the calling composable is
 * on screen — used to keep an ongoing event's elapsed-time display counting up (spec §6) without
 * threading a timer through the data-driven ViewModel `StateFlow`s. This is UI-only refresh, not
 * business logic, so it deliberately reads [clockNow] directly rather than going through the
 * pattern CLAUDE.md reserves for verdict/notification/stats' injected `Clock`.
 *
 * Returned as a [MutableState] (not a read-only `State`) so a caller whose action changes whether
 * an event reads as still-running — e.g. Stop on a `MANUAL` event, where [isStillRunning] compares
 * the new `endedAt` against this very value — can write a fresh [clockNow] reading into it
 * immediately, rather than show a stale result for up to [intervalMillis] until the next tick.
 * `START_STOP`'s own Stop doesn't need this (its ongoing check is `endedAt == null`, independent
 * of `now`), but `MANUAL`'s does.
 */
@Composable
fun rememberTickingNow(
    intervalMillis: Long = DEFAULT_TICK_INTERVAL_MILLIS,
    clockNow: () -> Long,
): MutableState<Long> {
    val now = remember { mutableLongStateOf(clockNow()) }
    LaunchedEffect(intervalMillis) {
        while (true) {
            delay(intervalMillis)
            now.longValue = clockNow()
        }
    }
    return now
}
