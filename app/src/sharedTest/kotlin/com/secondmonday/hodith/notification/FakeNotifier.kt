package com.secondmonday.hodith.notification

import com.secondmonday.hodith.data.CaseEntity
import com.secondmonday.hodith.data.WatchEntity
import com.secondmonday.hodith.ui.voice.Voice

/** Records calls instead of posting real notifications — same style as [com.secondmonday.hodith.widget.FakeWidgetRefresher]. */
class FakeNotifier : Notifier {
    val firedNotifications = mutableListOf<WatchEntity>()
    val dueCheckIns = mutableListOf<Pair<CaseEntity, Long>>()
    val cancelledCheckIns = mutableListOf<Long>()
    var groupSummaryRefreshes = 0

    override fun notifyNotificationFired(
        case: CaseEntity,
        watch: WatchEntity,
        voice: Voice,
    ) {
        firedNotifications += watch
    }

    override fun notifyCheckInDue(
        case: CaseEntity,
        silentDays: Long,
        voice: Voice,
    ) {
        dueCheckIns += case to silentDays
    }

    override fun cancelCheckIn(
        caseId: Long,
        voice: Voice,
    ) {
        cancelledCheckIns += caseId
    }

    override fun cancelCheckIns(
        caseIds: Collection<Long>,
        voice: Voice,
    ) {
        cancelledCheckIns += caseIds
    }

    override fun refreshGroupSummary(
        voice: Voice,
        alreadyCancelledChildId: Int?,
    ) {
        groupSummaryRefreshes++
    }
}
