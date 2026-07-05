package com.secondmonday.hodith.domain

import javax.inject.Inject

class SystemClock
    @Inject
    constructor() : Clock {
        override fun nowMillis(): Long = System.currentTimeMillis()
    }
