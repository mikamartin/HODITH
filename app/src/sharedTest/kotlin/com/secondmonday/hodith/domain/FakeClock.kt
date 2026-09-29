package com.secondmonday.hodith.domain

class FakeClock(
    private var currentMillis: Long = 0L,
) : Clock {
    override fun nowMillis(): Long = currentMillis

    fun set(millis: Long) {
        currentMillis = millis
    }

    fun advanceBy(millis: Long) {
        currentMillis += millis
    }
}
