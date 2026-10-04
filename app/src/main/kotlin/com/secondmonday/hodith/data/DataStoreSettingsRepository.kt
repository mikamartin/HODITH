package com.secondmonday.hodith.data

import android.content.Context
import android.text.format.DateFormat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val THEME_KEY = stringPreferencesKey("theme")
private val TIME_FORMAT_KEY = stringPreferencesKey("time_format")
private val CHECK_IN_DEFAULT_INTERVAL_KEY = stringPreferencesKey("check_in_default_interval")
private val NOTIFICATION_PERMISSION_REQUESTED_KEY = booleanPreferencesKey("notification_permission_requested")
private val DEVELOPER_MODE_UNLOCKED_KEY = booleanPreferencesKey("developer_mode_unlocked")
private val CLOUD_BACKUP_ENABLED_KEY = booleanPreferencesKey("cloud_backup_enabled")
private val BIG_PICTURE_DETAIL_KEY = stringPreferencesKey("big_picture_detail")
private val LOG_SORT_ORDER_KEY = stringPreferencesKey("log_sort_order")
private val BIG_PICTURE_VISIBLE_CASE_IDS_KEY = stringPreferencesKey("big_picture_visible_case_ids")
private val BIG_PICTURE_VISIBLE_TAG_NAMES_KEY = stringPreferencesKey("big_picture_visible_tag_names")
private val BIG_PICTURE_SELECTED_YEAR_KEY = stringPreferencesKey("big_picture_selected_year")
private val LOG_DATE_FROM_KEY = stringPreferencesKey("log_date_from")
private val LOG_DATE_TO_KEY = stringPreferencesKey("log_date_to")
private val LOG_VISIBLE_FIELDS_KEY = stringPreferencesKey("log_visible_fields")
private val SHARE_SECTION_ORDER_KEY = stringPreferencesKey("share_section_order")

@Singleton
class DataStoreSettingsRepository
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
        @ApplicationContext private val context: Context,
    ) : SettingsRepository {
        override fun observeTheme(): Flow<AppTheme> =
            dataStore.data.map { preferences ->
                preferences[THEME_KEY]?.let { name ->
                    runCatching { AppTheme.valueOf(name) }.getOrNull()
                } ?: AppTheme.PLAIN
            }

        override suspend fun setTheme(theme: AppTheme) {
            dataStore.edit { preferences -> preferences[THEME_KEY] = theme.name }
        }

        override fun observeTimeFormat(): Flow<TimeFormat> =
            dataStore.data.map { preferences ->
                preferences[TIME_FORMAT_KEY]?.let { name ->
                    runCatching { TimeFormat.valueOf(name) }.getOrNull()
                } ?: deviceTimeFormat()
            }

        override suspend fun setTimeFormat(format: TimeFormat) {
            dataStore.edit { preferences -> preferences[TIME_FORMAT_KEY] = format.name }
        }

        private fun deviceTimeFormat(): TimeFormat =
            if (DateFormat.is24HourFormat(context)) TimeFormat.TWENTY_FOUR_HOUR else TimeFormat.TWELVE_HOUR

        override fun observeCheckInDefaultInterval(): Flow<CheckInDefaultInterval> =
            dataStore.data.map { preferences ->
                preferences[CHECK_IN_DEFAULT_INTERVAL_KEY]?.let { name ->
                    runCatching { CheckInDefaultInterval.valueOf(name) }.getOrNull()
                } ?: CheckInDefaultInterval.SEVEN
            }

        override suspend fun setCheckInDefaultInterval(interval: CheckInDefaultInterval) {
            dataStore.edit { preferences -> preferences[CHECK_IN_DEFAULT_INTERVAL_KEY] = interval.name }
        }

        override suspend fun getCheckInDefaultInterval(): CheckInDefaultInterval = observeCheckInDefaultInterval().first()

        override suspend fun hasRequestedNotificationPermission(): Boolean = observeHasRequestedNotificationPermission().first()

        override fun observeHasRequestedNotificationPermission(): Flow<Boolean> =
            dataStore.data.map { preferences -> preferences[NOTIFICATION_PERMISSION_REQUESTED_KEY] ?: false }

        override suspend fun setNotificationPermissionRequested() {
            dataStore.edit { preferences -> preferences[NOTIFICATION_PERMISSION_REQUESTED_KEY] = true }
        }

        override fun observeDeveloperModeUnlocked(): Flow<Boolean> =
            dataStore.data.map { preferences -> preferences[DEVELOPER_MODE_UNLOCKED_KEY] ?: false }

        override suspend fun setDeveloperModeUnlocked() {
            dataStore.edit { preferences -> preferences[DEVELOPER_MODE_UNLOCKED_KEY] = true }
        }

        override fun observeCloudBackupEnabled(): Flow<Boolean> =
            dataStore.data.map { preferences -> preferences[CLOUD_BACKUP_ENABLED_KEY] ?: true }

        override suspend fun setCloudBackupEnabled(enabled: Boolean) {
            dataStore.edit { preferences -> preferences[CLOUD_BACKUP_ENABLED_KEY] = enabled }
        }

        override fun observeBigPictureDetail(): Flow<BigPictureDetail> =
            dataStore.data.map { preferences -> BigPictureDetail.parse(preferences[BIG_PICTURE_DETAIL_KEY]) }

        override suspend fun setBigPictureDetail(detail: BigPictureDetail) {
            dataStore.edit { preferences -> preferences[BIG_PICTURE_DETAIL_KEY] = detail.serialize() }
        }

        override fun observeLogSortOrder(): Flow<LogSortOrder> =
            dataStore.data.map { preferences ->
                preferences[LOG_SORT_ORDER_KEY]?.let { name ->
                    runCatching { LogSortOrder.valueOf(name) }.getOrNull()
                } ?: LogSortOrder.BY_START
            }

        override suspend fun setLogSortOrder(order: LogSortOrder) {
            dataStore.edit { preferences -> preferences[LOG_SORT_ORDER_KEY] = order.name }
        }

        override fun observeLogDateFrom(): Flow<Long?> =
            dataStore.data.map { preferences -> preferences[LOG_DATE_FROM_KEY]?.toLongOrNull() }

        override suspend fun setLogDateFrom(millis: Long?) {
            dataStore.edit { preferences ->
                if (millis == null) preferences.remove(LOG_DATE_FROM_KEY) else preferences[LOG_DATE_FROM_KEY] = millis.toString()
            }
        }

        override fun observeLogDateTo(): Flow<Long?> = dataStore.data.map { preferences -> preferences[LOG_DATE_TO_KEY]?.toLongOrNull() }

        override suspend fun setLogDateTo(millis: Long?) {
            dataStore.edit { preferences ->
                if (millis == null) preferences.remove(LOG_DATE_TO_KEY) else preferences[LOG_DATE_TO_KEY] = millis.toString()
            }
        }

        /** Absent key (never stored, or every field explicitly re-selected) reads as every field on — matches [LogRowField]'s "all on" default elsewhere. */
        override fun observeLogVisibleFields(): Flow<Set<LogRowField>> =
            dataStore.data.map { preferences ->
                preferences[LOG_VISIBLE_FIELDS_KEY]?.let { raw ->
                    if (raw.isEmpty()) {
                        emptySet()
                    } else {
                        raw.split(",").mapNotNull { name -> runCatching { LogRowField.valueOf(name) }.getOrNull() }.toSet()
                    }
                } ?: LogRowField.entries.toSet()
            }

        override suspend fun setLogVisibleFields(fields: Set<LogRowField>) {
            dataStore.edit { preferences -> preferences[LOG_VISIBLE_FIELDS_KEY] = fields.joinToString(",") { it.name } }
        }

        /** Absent key reads as declaration order; unknown names in a stored value are dropped rather than failing the whole list. */
        override fun observeShareSectionOrder(): Flow<List<ShareInsightsSection>> =
            dataStore.data.map { preferences ->
                val saved =
                    preferences[SHARE_SECTION_ORDER_KEY]
                        ?.split(",")
                        ?.mapNotNull { name -> runCatching { ShareInsightsSection.valueOf(name) }.getOrNull() }
                        .orEmpty()
                orderedShareSections(saved)
            }

        override suspend fun setShareSectionOrder(order: List<ShareInsightsSection>) {
            dataStore.edit { preferences -> preferences[SHARE_SECTION_ORDER_KEY] = order.joinToString(",") { it.name } }
        }

        override fun observeBigPictureVisibleCaseIds(): Flow<Set<Long>?> =
            dataStore.data.map { preferences ->
                preferences[BIG_PICTURE_VISIBLE_CASE_IDS_KEY]?.let { raw ->
                    if (raw.isEmpty()) emptySet() else raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
                }
            }

        override suspend fun setBigPictureVisibleCaseIds(caseIds: Set<Long>?) {
            dataStore.edit { preferences ->
                if (caseIds == null) {
                    preferences.remove(BIG_PICTURE_VISIBLE_CASE_IDS_KEY)
                } else {
                    preferences[BIG_PICTURE_VISIBLE_CASE_IDS_KEY] = caseIds.joinToString(",")
                }
            }
        }

        override fun observeBigPictureVisibleTagNames(): Flow<Set<String>?> =
            dataStore.data.map { preferences ->
                preferences[BIG_PICTURE_VISIBLE_TAG_NAMES_KEY]?.let { raw ->
                    if (raw.isEmpty()) emptySet() else raw.split("\n").toSet()
                }
            }

        override suspend fun setBigPictureVisibleTagNames(tagNames: Set<String>?) {
            dataStore.edit { preferences ->
                if (tagNames == null) {
                    preferences.remove(BIG_PICTURE_VISIBLE_TAG_NAMES_KEY)
                } else {
                    preferences[BIG_PICTURE_VISIBLE_TAG_NAMES_KEY] = tagNames.joinToString("\n")
                }
            }
        }

        override fun observeBigPictureSelectedYear(): Flow<Int?> =
            dataStore.data.map { preferences -> preferences[BIG_PICTURE_SELECTED_YEAR_KEY]?.toIntOrNull() }

        override suspend fun setBigPictureSelectedYear(year: Int?) {
            dataStore.edit { preferences ->
                if (year == null) {
                    preferences.remove(BIG_PICTURE_SELECTED_YEAR_KEY)
                } else {
                    preferences[BIG_PICTURE_SELECTED_YEAR_KEY] = year.toString()
                }
            }
        }
    }
