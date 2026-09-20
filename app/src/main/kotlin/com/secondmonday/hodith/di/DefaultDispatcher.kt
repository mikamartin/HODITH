package com.secondmonday.hodith.di

import javax.inject.Qualifier

/**
 * Qualifies the [kotlinx.coroutines.CoroutineDispatcher] used to run the `combine` mapping work
 * off the main thread in `HomeViewModel.uiState`/`BigPictureViewModel.uiState`. Production binds
 * this to the real `Dispatchers.Default` ([DispatcherModule]) — the load-bearing ANR fix from
 * commits 670b605/fe6b9b9 (see CLEANUP_LOG.md). JVM unit tests skip Hilt and instead pass a
 * deterministic test dispatcher directly to the ViewModel constructor, matching how these same
 * tests already redirect `Dispatchers.Main`.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher
