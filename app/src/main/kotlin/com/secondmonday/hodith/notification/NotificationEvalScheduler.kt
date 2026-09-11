package com.secondmonday.hodith.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Debounces the immediate Trigger/check-in evaluation that
 * [com.secondmonday.hodith.data.RoomHodithRepository] fires after every event mutation (spec §11).
 *
 * A rapid quick-log burst on one Case would otherwise launch one overlapping
 * [NotificationEvaluator.evaluateCase] per tap — several DAO reads plus a possible `triggers` write
 * each — all contending on the single SQLite connection. Here each Case keeps at most one pending
 * evaluation, replaced on every new request, so a burst collapses to one evaluation
 * [EVAL_DEBOUNCE_MILLIS] after the last tap. The window is well under a second, so a genuine single
 * edit still evaluates effectively immediately. The ~6h [NotificationEvalWorker] runs
 * [NotificationEvaluator.evaluateAll] directly and is unaffected.
 *
 * Takes [NotificationEvaluator] via [Provider] for the same Dagger-cycle reason the evaluator itself
 * takes the repository via [Provider]: the real repository depends on this scheduler.
 */
@Singleton
class NotificationEvalScheduler
    @Inject
    constructor(
        private val scope: CoroutineScope,
        private val evaluator: Provider<NotificationEvaluator>,
    ) {
        private val lock = Any()
        private val pending = mutableMapOf<Long, Job>()

        /**
         * Requests an evaluation of [caseId], cancelling any evaluation still waiting out its
         * debounce window for the same Case. Safe to call concurrently from any dispatcher.
         */
        fun schedule(caseId: Long) {
            synchronized(lock) {
                pending.remove(caseId)?.cancel()
                lateinit var job: Job
                job =
                    scope.launch {
                        delay(EVAL_DEBOUNCE_MILLIS)
                        try {
                            evaluator.get().evaluateCase(caseId)
                        } finally {
                            synchronized(lock) { if (pending[caseId] === job) pending.remove(caseId) }
                        }
                    }
                pending[caseId] = job
            }
        }

        companion object {
            /** Sub-second: long enough to swallow a tap burst, short enough that a lone edit still feels immediate. */
            const val EVAL_DEBOUNCE_MILLIS = 300L
        }
    }
