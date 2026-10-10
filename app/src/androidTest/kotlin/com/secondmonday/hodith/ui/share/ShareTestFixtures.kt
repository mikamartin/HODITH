package com.secondmonday.hodith.ui.share

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.time.LocalDate
import java.time.ZoneId

/** Shared across this package's Share screen/tab tests, mirroring [com.secondmonday.hodith.widget.WidgetConfigureTestFixtures]. */
internal val ZONE: ZoneId = ZoneId.systemDefault()

internal fun millisAtDay(epochDay: Long): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(ZONE)
        .toInstant()
        .toEpochMilli()

internal fun ComposeContentTestRule.nameFieldText(): String =
    onNode(hasSetTextAction())
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text
