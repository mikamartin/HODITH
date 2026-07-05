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
    fun fromHunchDirection(value: HunchDirection): String = value.name

    @TypeConverter
    fun toHunchDirection(value: String): HunchDirection = HunchDirection.valueOf(value)

    @TypeConverter
    fun fromExpectedPer(value: ExpectedPer): String = value.name

    @TypeConverter
    fun toExpectedPer(value: String): ExpectedPer = ExpectedPer.valueOf(value)

    @TypeConverter
    fun fromTriggerKind(value: TriggerKind): String = value.name

    @TypeConverter
    fun toTriggerKind(value: String): TriggerKind = TriggerKind.valueOf(value)
}
