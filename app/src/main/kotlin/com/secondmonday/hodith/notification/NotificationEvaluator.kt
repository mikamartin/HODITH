package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.MILLIS_PER_DAY
import com.secondmonday.hodith.domain.evaluateAtLeast
import com.secondmonday.hodith.domain.evaluateCheckIn
import com.secondmonday.hodith.domain.evaluateSilentFor
import com.secondmonday.hodith.ui.voice.Voice
import com.secondmonday.hodith.ui.voice.voiceFor
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Provider

/**
 * Evaluates Triggers and check-ins against real data (spec §11) and posts notifications for
 * anything due. Takes [HodithRepository] via [Provider], not directly: [HodithRepository]'s real
 * implementation depends on this class (to evaluate immediately on event insert/edit/delete), so a
 * direct dependency here would make the Dagger graph circular. [Provider] defers resolution until
 * [evaluateCase]/[evaluateAll] actually run, by which point the graph is fully built.
 */
class NotificationEvaluator
    @Inject
    constructor(
        private val repository: Provider<HodithRepository>,
        private val settingsRepository: SettingsRepository,
        private val clock: Clock,
        private val notifier: Notifier,
    ) {
        /** For the immediate hook: only the one Case an event insert/edit/delete just touched. */
        suspend fun evaluateCase(caseId: Long) {
            val repo = repository.get()
            val case = repo.getCase(caseId) ?: return
            if (case.archived) return
            val voice = currentVoice()
            evaluateTriggers(repo, case, repo.getTriggersForCase(caseId).filter { it.enabled }, voice)
            evaluateCheckInForCase(repo, case, voice)
        }

        /** For the periodic job: every enabled Trigger and every active Case's check-in. */
        suspend fun evaluateAll() {
            val repo = repository.get()
            val voice = currentVoice()
            repo.getEnabledTriggers().groupBy { it.caseId }.forEach { (caseId, triggers) ->
                val case = repo.getCase(caseId)
                if (case != null && !case.archived) evaluateTriggers(repo, case, triggers, voice)
            }
            repo.getActiveCases().forEach { case -> evaluateCheckInForCase(repo, case, voice) }
        }

        private suspend fun currentVoice(): Voice = voiceFor(settingsRepository.observeTheme().first())

        private suspend fun evaluateTriggers(
            repo: HodithRepository,
            case: CaseEntity,
            triggers: List<TriggerEntity>,
            voice: Voice,
        ) {
            if (triggers.isEmpty()) return
            val now = clock.nowMillis()
            for (trigger in triggers) {
                val decision =
                    when (trigger.kind) {
                        TriggerKind.AT_LEAST -> {
                            val windowStart = now - (trigger.windowDays ?: 0) * MILLIS_PER_DAY
                            // eventsInWindow's range is half-open ([start, end)) — +1 so an event
                            // occurring at exactly `now` (e.g. the one that just triggered this
                            // immediate-eval hook) still counts.
                            val events = repo.eventsInWindow(case.id, windowStart, now + 1)
                            evaluateAtLeast(trigger, events, now)
                        }
                        TriggerKind.SILENT_FOR -> {
                            val mostRecentEventAt = repo.getMostRecentEventForCase(case.id)?.occurredAt
                            evaluateSilentFor(trigger, mostRecentEventAt, case.createdAt, now)
                        }
                    }
                if (decision.newArmed != trigger.armed || decision.newLastFiredAt != trigger.lastFiredAt) {
                    repo.updateTrigger(trigger.copy(armed = decision.newArmed, lastFiredAt = decision.newLastFiredAt))
                }
                if (decision.shouldFire) {
                    notifier.notifyTriggerFired(case, trigger, voice)
                }
            }
        }

        private suspend fun evaluateCheckInForCase(
            repo: HodithRepository,
            case: CaseEntity,
            voice: Voice,
        ) {
            if (!case.checkInsEnabled) return
            val hunch = repo.getActiveHunch(case.id)
            val settingsDefaultDays = settingsRepository.getCheckInDefaultInterval().days
            val mostRecentEventAt = repo.getMostRecentEventForCase(case.id)?.occurredAt
            val now = clock.nowMillis()
            val decision = evaluateCheckIn(case, hunch, settingsDefaultDays, mostRecentEventAt, now)
            if (decision.due) {
                // Fixes the fire at `now`, so this Case can't check in again before its own interval
                // elapses even though "All quiet" — the real re-arm action — is branch 6 (see notifier docs).
                repo.updateCase(case.copy(lastCheckInAt = now))
                notifier.notifyCheckInDue(case, decision.silentDays, voice)
            }
        }
    }
