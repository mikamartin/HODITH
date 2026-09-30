package com.secondmonday.hodith.domain

import com.secondmonday.hodith.data.ExpectedPer
import com.secondmonday.hodith.data.VerdictMetric

/**
 * A stated expectation to check reality against: N per [per], measured by [metric], over a window
 * starting at [windowStart] (already resolved to an instant — a caller's rolling lookback or a
 * frozen custom date, [computeVerdict] doesn't care which).
 */
data class Expectation(
    val count: Int,
    val per: ExpectedPer,
    val metric: VerdictMetric,
    val windowStart: Long,
)
