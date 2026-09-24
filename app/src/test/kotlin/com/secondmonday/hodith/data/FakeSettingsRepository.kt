package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Hand-rolled in-memory test double for [SettingsRepository], same style as [FakeHodithRepository]. */
class FakeSettingsRepository : SettingsRepository {
    val theme = MutableStateFlow(AppTheme.PLAIN)
    val timeFormat = MutableStateFlow(TimeFormat.TWELVE_HOUR)
    val checkInDefaultInterval = MutableStateFlow(CheckInDefaultInterval.SEVEN)
    val notificationPermissionRequested = MutableStateFlow(false)
    val developerModeUnlocked = MutableStateFlow(false)
    val cloudBackupEnabled = MutableStateFlow(true)
    val bigPictureDetail = MutableStateFlow(BigPictureDetail.DEFAULT)
    val logSortOrder = MutableStateFlow(LogSortOrder.BY_START)
    val bigPictureVisibleCaseIds = MutableStateFlow<Set<Long>?>(null)
    val bigPictureVisibleTagNames = MutableStateFlow<Set<String>?>(null)
    val bigPictureSelectedYear = MutableStateFlow<Int?>(null)

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

    override fun observeLogSortOrder(): Flow<LogSortOrder> = logSortOrder

    override suspend fun setLogSortOrder(order: LogSortOrder) {
        this.logSortOrder.value = order
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
