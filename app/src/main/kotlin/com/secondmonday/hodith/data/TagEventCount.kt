package com.secondmonday.hodith.data

/**
 * How many events carry one tag, across every Case. Tags with no attachments have no row, so
 * callers treat a missing tag as zero.
 */
data class TagEventCount(
    val tagId: Long,
    val eventCount: Int,
)
