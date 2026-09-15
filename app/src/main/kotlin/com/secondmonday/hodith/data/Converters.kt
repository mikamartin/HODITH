package com.secondmonday.hodith.data

import androidx.room.TypeConverter
import com.secondmonday.hodith.domain.ComparisonBand
import com.secondmonday.hodith.domain.ConfidenceTier

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
    fun fromVerdictMetric(value: VerdictMetric): String = value.name

    @TypeConverter
    fun toVerdictMetric(value: String): VerdictMetric = VerdictMetric.valueOf(value)

    @TypeConverter
    fun fromObservationWindow(value: ObservationWindow): String = value.name

    @TypeConverter
    fun toObservationWindow(value: String): ObservationWindow = ObservationWindow.valueOf(value)

    @TypeConverter
    fun fromTriggerKind(value: TriggerKind): String = value.name

    @TypeConverter
    fun toTriggerKind(value: String): TriggerKind = TriggerKind.valueOf(value)

    @TypeConverter
    fun fromConfidenceTier(value: ConfidenceTier?): String? = value?.name

    @TypeConverter
    fun toConfidenceTier(value: String?): ConfidenceTier? = value?.let { ConfidenceTier.valueOf(it) }

    @TypeConverter
    fun fromComparisonBand(value: ComparisonBand?): String? = value?.name

    @TypeConverter
    fun toComparisonBand(value: String?): ComparisonBand? = value?.let { ComparisonBand.valueOf(it) }
}
