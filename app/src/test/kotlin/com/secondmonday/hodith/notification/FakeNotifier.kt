package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.TriggerEntity
import com.secondmonday.hodith.ui.voice.Voice

/** Records calls instead of posting real notifications — same style as [com.secondmonday.hodith.widget.FakeWidgetRefresher]. */
class FakeNotifier : Notifier {
    val firedTriggers = mutableListOf<TriggerEntity>()
    val dueCheckIns = mutableListOf<Pair<CaseEntity, Long>>()

    override fun notifyTriggerFired(
        case: CaseEntity,
        trigger: TriggerEntity,
        voice: Voice,
    ) {
        firedTriggers += trigger
    }

    override fun notifyCheckInDue(
        case: CaseEntity,
        silentDays: Long,
        voice: Voice,
    ) {
        dueCheckIns += case to silentDays
    }
}
