package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** Hand-rolled in-memory test double for [SettingsRepository], same style as [FakeHodithRepository]. */
class FakeSettingsRepository : SettingsRepository {
    val theme = MutableStateFlow(AppTheme.PLAIN)
    val timeFormat = MutableStateFlow(TimeFormat.TWELVE_HOUR)
    val checkInDefaultInterval = MutableStateFlow(CheckInDefaultInterval.SEVEN)
    val notificationPermissionRequested = MutableStateFlow(false)
    val developerModeUnlocked = MutableStateFlow(false)
    val cloudBackupEnabled = MutableStateFlow(true)
    val bigPictureDetail = MutableStateFlow(BigPictureDetail.DEFAULT)
    val historySortOrder = MutableStateFlow(HistorySortOrder.BY_START)
    val historyDateFrom = MutableStateFlow<Long?>(null)
    val historyDateTo = MutableStateFlow<Long?>(null)
    val historyVisibleFields = MutableStateFlow(HistoryRowField.entries.toSet())
    val bigPictureVisibleCaseIds = MutableStateFlow<Set<Long>?>(null)
    val bigPictureVisibleTagNames = MutableStateFlow<Set<String>?>(null)
    val bigPictureSelectedYear = MutableStateFlow<Int?>(null)
    val shareSectionOrder = MutableStateFlow(orderedShareSections(emptyList()))

    override fun observeTheme(): Flow<AppTheme> = theme

    override suspend fun setTheme(theme: AppTheme) {
        this.theme.value = theme
    }

    override fun observeTimeFormat(): Flow<TimeFormat> = timeFormat

    override suspend fun setTimeFormat(format: TimeFormat) {
        this.timeFormat.value = format
    }

    override fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval> = checkInDefaultInterval

    override suspend fun getCheckInDefaultInterval(): CheckInDefaultInterval = checkInDefaultInterval.value

    override suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval) {
        this.checkInDefaultInterval.value = interval
    }

    override suspend fun hasRequestedNotificationPermission(): Boolean = notificationPermissionRequested.value

    override fun observeHasRequestedNotificationPermission(): Flow<Boolean> = notificationPermissionRequested

    override suspend fun setNotificationPermissionRequested() {
        notificationPermissionRequested.value = true
    }

    override fun observeDeveloperModeUnlocked(): Flow<Boolean> = developerModeUnlocked

    override suspend fun setDeveloperModeUnlocked() {
        developerModeUnlocked.value = true
    }

    override fun observeCloudBackupEnabled(): Flow<Boolean> = cloudBackupEnabled

    override suspend fun setCloudBackupEnabled(enabled: Boolean) {
        cloudBackupEnabled.value = enabled
    }

    override fun observeBigPictureDetail(): Flow<BigPictureDetail> = bigPictureDetail

    override suspend fun setBigPictureDetail(detail: BigPictureDetail) {
        bigPictureDetail.value = detail
    }

    override fun observeHistorySortOrder(): Flow<HistorySortOrder> = historySortOrder

    override suspend fun setHistorySortOrder(order: HistorySortOrder) {
        this.historySortOrder.value = order
    }

    override fun observeHistoryDateFrom(): Flow<Long?> = historyDateFrom

    override suspend fun setHistoryDateFrom(millis: Long?) {
        this.historyDateFrom.value = millis
    }

    override fun observeHistoryDateTo(): Flow<Long?> = historyDateTo

    override suspend fun setHistoryDateTo(millis: Long?) {
        this.historyDateTo.value = millis
    }

    override fun observeHistoryVisibleFields(): Flow<Set<HistoryRowField>> = historyVisibleFields

    override suspend fun setHistoryVisibleFields(fields: Set<HistoryRowField>) {
        this.historyVisibleFields.value = fields
    }

    override fun observeShareSectionOrder(): Flow<List<ShareInsightsSection>> = shareSectionOrder.map { orderedShareSections(it) }

    override suspend fun setShareSectionOrder(order: List<ShareInsightsSection>) {
        this.shareSectionOrder.value = orderedShareSections(order)
    }

    override fun observeBigPictureVisibleCaseIds(): Flow<Set<Long>?> = bigPictureVisibleCaseIds

    override suspend fun setBigPictureVisibleCaseIds(caseIds: Set<Long>?) {
        bigPictureVisibleCaseIds.value = caseIds
    }

    override fun observeBigPictureVisibleTagNames(): Flow<Set<String>?> = bigPictureVisibleTagNames

    override suspend fun setBigPictureVisibleTagNames(tagNames: Set<String>?) {
        bigPictureVisibleTagNames.value = tagNames
    }

    override fun observeBigPictureSelectedYear(): Flow<Int?> = bigPictureSelectedYear

    override suspend fun setBigPictureSelectedYear(year: Int?) {
        bigPictureSelectedYear.value = year
    }
}
