package com.secondmonday.hodith.data

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeTheme(): Flow<AppTheme>

    suspend fun setTheme(theme: AppTheme)

    /** Spec §14: 12h/24h clock display. No stored value means "follow the device" — see [TimeFormat]. */
    fun observeTimeFormat(): Flow<TimeFormat>

    suspend fun setTimeFormat(format: TimeFormat)

    fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval>

    suspend fun getCheckInDefaultInterval(): CheckInDefaultInterval

    suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval)

    /** Spec §11: POST_NOTIFICATIONS is requested once, on first notification created or first check-in enabled — never again after. */
    suspend fun hasRequestedNotificationPermission(): Boolean

    fun observeHasRequestedNotificationPermission(): Flow<Boolean>

    suspend fun setNotificationPermissionRequested()

    /** Hidden developer-mode unlock (About screen's version-tap gesture) — one-way, like [setNotificationPermissionRequested]. */
    fun observeDeveloperModeUnlocked(): Flow<Boolean>

    suspend fun setDeveloperModeUnlocked()

    /** Spec §16: opts out of Android's OS-level device backup (cloud backup and device-transfer alike). Default on. */
    fun observeCloudBackupEnabled(): Flow<Boolean>

    suspend fun setCloudBackupEnabled(enabled: Boolean)

    /** Spec §9: which optional fields the Big Picture day/week detail rows show. Default [BigPictureDetail.DEFAULT]. */
    fun observeBigPictureDetail(): Flow<BigPictureDetail>

    suspend fun setBigPictureDetail(detail: BigPictureDetail)

    /** Case Detail's History tab sort order (spec §6). Default [HistorySortOrder.BY_START]. */
    fun observeHistorySortOrder(): Flow<HistorySortOrder>

    suspend fun setHistorySortOrder(order: HistorySortOrder)

    /**
     * The History tab's Range filter (spec §6/§13) — device-wide, applied across every Case, same as
     * [observeHistorySortOrder] rather than a per-Case value. `null` on either side means unbounded.
     */
    fun observeHistoryDateFrom(): Flow<Long?>

    suspend fun setHistoryDateFrom(millis: Long?)

    fun observeHistoryDateTo(): Flow<Long?>

    suspend fun setHistoryDateTo(millis: Long?)

    /** The History tab's per-row field selection (spec §6/§13) — device-wide, absent key defaults to every field on. */
    fun observeHistoryVisibleFields(): Flow<Set<HistoryRowField>>

    suspend fun setHistoryVisibleFields(fields: Set<HistoryRowField>)

    /** The Story share card's section order (spec §13) — device-wide; see [orderedShareSections] for how a partial stored order is completed. */
    fun observeShareSectionOrder(): Flow<List<ShareInsightsSection>>

    suspend fun setShareSectionOrder(order: List<ShareInsightsSection>)

    /**
     * Big Picture's Case/Tag/Year filters (spec §9). `null` means "no filter stored" (everything
     * visible / all years) rather than a literal snapshot, so a Case or tag added later is visible
     * by default and a deleted one doesn't linger — see `BigPictureFilterState.resolveVisibleSelection`.
     */
    fun observeBigPictureVisibleCaseIds(): Flow<Set<Long>?>

    suspend fun setBigPictureVisibleCaseIds(caseIds: Set<Long>?)

    fun observeBigPictureVisibleTagNames(): Flow<Set<String>?>

    suspend fun setBigPictureVisibleTagNames(tagNames: Set<String>?)

    fun observeBigPictureSelectedYear(): Flow<Int?>

    suspend fun setBigPictureSelectedYear(year: Int?)
}
