package com.secondmonday.hodith.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromLogFlow(value: LogFlow): String = value.name

    @TypeConverter
    fun toLogFlow(value: String): LogFlow = LogFlow.valueOf(value)

    @TypeConverter
    fun fromDurationMode(value: DurationMode): String = value.name

    @TypeConverter
    fun toDurationMode(value: String): DurationMode = DurationMode.valueOf(value)

    @TypeConverter
    fun fromExpectedPer(value: ExpectedPer): String = value.name

    @TypeConverter
    fun toExpectedPer(value: String): ExpectedPer = ExpectedPer.valueOf(value)

    @TypeConverter
    fun fromVerdictMetric(value: VerdictMetric): String = value.name

    @TypeConverter
    fun toVerdictMetric(value: String): VerdictMetric = VerdictMetric.valueOf(value)

    @TypeConverter
    fun fromNotificationKind(value: NotificationKind): String = value.name

    @TypeConverter
    fun toNotificationKind(value: String): NotificationKind = NotificationKind.valueOf(value)
}
