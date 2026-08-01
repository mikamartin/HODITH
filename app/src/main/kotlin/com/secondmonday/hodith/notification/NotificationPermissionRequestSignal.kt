package com.secondmonday.hodith.notification

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signals that the POST_NOTIFICATIONS system dialog should be shown (spec §11: once, on first
 * Trigger created or first check-in enabled). Singleton rather than owned by whichever screen-scoped
 * ViewModel triggers it, because [com.secondmonday.hodith.ui.case.CaseEditRoute] navigates away the
 * instant a save completes — a per-screen event risks the composable being disposed before its own
 * effect gets a chance to launch the dialog. Collected once, at the app root, so it survives
 * navigation regardless of which screen requested it.
 */
@Singleton
class NotificationPermissionRequestSignal
    @Inject
    constructor() {
        private val channel = Channel<Unit>(Channel.BUFFERED)
        val events: Flow<Unit> = channel.receiveAsFlow()

        suspend fun request() {
            channel.send(Unit)
        }
    }
