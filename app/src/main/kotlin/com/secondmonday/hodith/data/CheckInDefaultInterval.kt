package com.secondmonday.hodith.data

/** Settings' app-wide default check-in interval (spec §11). */
enum class CheckInDefaultInterval(
    val days: Int?,
) {
    OFF(null),
    SEVEN(7),
    FOURTEEN(14),
    THIRTY(30),
}
