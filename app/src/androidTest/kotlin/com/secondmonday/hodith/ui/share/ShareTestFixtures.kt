package com.secondmonday.hodith.ui.share

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** Shared across this package's Share screen/tab tests, mirroring [com.secondmonday.hodith.widget.WidgetConfigureTestFixtures]. */
internal val ZONE: TimeZone = TimeZone.currentSystemDefault()

internal fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .fromEpochDays(epochDay.toInt())
        .atStartOfDayIn(ZONE)
        .toEpochMilliseconds()

internal fun ComposeContentTestRule.nameFieldText(): String =
    onNode(hasSetTextAction())
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text
