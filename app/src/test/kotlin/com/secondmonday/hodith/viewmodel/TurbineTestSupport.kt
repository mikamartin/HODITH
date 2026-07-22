package com.secondmonday.hodith.viewmodel

import app.cash.turbine.ReceiveTurbine

/**
 * Skips a `stateIn`'d [kotlinx.coroutines.flow.StateFlow]'s initial `isLoading = true` default
 * before the first real `combine` emission lands, so tests don't need to special-case whether
 * turbine's first collected item is that placeholder or already the loaded state.
 */
internal suspend fun <T> ReceiveTurbine<T>.awaitLoadedItem(isLoading: (T) -> Boolean): T {
    var state = awaitItem()
    if (isLoading(state)) state = awaitItem()
    return state
}
