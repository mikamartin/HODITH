package com.secondmonday.hodith.data

/**
 * Settings' clock-time preference (spec §14): whether times render 12-hour ("3:45 PM") or
 * 24-hour ("15:45"). Until the user picks explicitly there is no stored value, and
 * [DataStoreSettingsRepository] seeds the reading from the device's own clock setting
 * (`DateFormat.is24HourFormat`) on every read — so the default follows the device.
 */
enum class TimeFormat {
    TWELVE_HOUR,
    TWENTY_FOUR_HOUR,
    ;

    val is24Hour: Boolean
        get() = this == TWENTY_FOUR_HOUR
}
