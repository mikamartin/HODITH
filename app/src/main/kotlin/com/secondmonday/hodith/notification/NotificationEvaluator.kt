package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.SettingsRepository
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.data.TriggerKind
import com.secondmonday.hodith.domain.CheckInDecision
import com.secondmonday.hodith.domain.Clock
import com.secondmonday.hodith.domain.atLeastWindowStart
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
            evaluateCheckIns(repo, voice)
        }

        /**
         * Spec §11 anti-spam: a single due check-in posts its own notification (with Log/All quiet
         * actions), but 2+ in the same pass collapse into one summary instead of alerting once per
         * Case. Only reachable from the periodic job — the immediate per-event hook only ever
         * touches the one Case an event mutation just affected.
         */
        private suspend fun evaluateCheckIns(
            repo: HodithRepository,
            voice: Voice,
        ) {
            val due =
                repo.getActiveCases().mapNotNull { case ->
                    dueCheckInDecision(repo, case)?.let { case to it.silentDays }
                }
            when (due.size) {
                0 -> Unit
                1 -> due.single().let { (case, silentDays) -> notifier.notifyCheckInDue(case, silentDays, voice) }
                else -> notifier.notifyCheckInsSummary(due.map { it.first }, voice)
            }
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
                            val windowStart = atLeastWindowStart(now, trigger.windowDays)
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
            val decision = dueCheckInDecision(repo, case) ?: return
            notifier.notifyCheckInDue(case, decision.silentDays, voice)
        }

        /**
         * Null unless due. Re-arming (moving `lastCheckInAt`/the anchor forward) only happens via
         * the "All quiet" notification action or a new event on the Case — never automatically at
         * evaluation time — so an ignored check-in keeps recurring on each periodic pass until acted on.
         */
        private suspend fun dueCheckInDecision(
            repo: HodithRepository,
            case: CaseEntity,
        ): CheckInDecision? {
            if (!case.checkInsEnabled) return null
            val hunch = repo.getActiveHunch(case.id)
            val settingsDefaultDays = settingsRepository.getCheckInDefaultInterval().days
            val mostRecentEventAt = repo.getMostRecentEventForCase(case.id)?.occurredAt
            val decision = evaluateCheckIn(case, hunch, settingsDefaultDays, mostRecentEventAt, clock.nowMillis())
            return decision.takeIf { it.due }
        }
    }
