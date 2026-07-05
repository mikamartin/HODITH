package com.secondmonday.hodith.ui.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.HodithDatabase
import com.secondmonday.hodith.data.HodithRepository
import com.secondmonday.hodith.data.createInMemoryDatabase
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.timeline.TimeWindow
import com.secondmonday.hodith.ui.voice.SeriousVoice
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real Room -> [HodithRepository] -> [CaseWithEvents.toTimelineRowData] pipeline
 * feeding [TimelineGrid], rather than the synthetic [sampleTimelineRows] used by previews. Tap
 * navigation (dot -> event, row -> Case) is deliberately not covered here: those destinations
 * don't exist until Phase 3, so that assertion lives there instead (see TESTING.md).
 */
@RunWith(AndroidJUnit4::class)
class TimelineGridInstrumentedTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var db: HodithDatabase
    private lateinit var repository: HodithRepository

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository = HodithRepository(db.caseDao(), db.eventDao(), db.tagDao(), db.hunchDao(), db.triggerDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun rendersRowFromRepositoryData() =
        runTest {
            val caseId = repository.insertCase(testCase(name = "Migraines"))
            repository.insertEvent(testEvent(caseId = caseId, occurredAt = 1_000L))
            val rows = repository.observeActiveCasesWithEvents().first().map { it.toTimelineRowData() }

            composeTestRule.setContent {
                MaterialTheme {
                    TimelineGrid(
                        rows = rows,
                        initialWindow = TimeWindow(0L, 2_000L),
                        onDotTap = { _, _ -> },
                        onCaseTap = {},
                    )
                }
            }

            composeTestRule.onNodeWithText("Migraines").assertExists()
        }

    @Test
    fun showsEmptyState_whenNoCasesExist() =
        runTest {
            val rows = repository.observeActiveCasesWithEvents().first().map { it.toTimelineRowData() }

            composeTestRule.setContent {
                MaterialTheme {
                    TimelineGrid(
                        rows = rows,
                        initialWindow = TimeWindow(0L, 1_000L),
                        onDotTap = { _, _ -> },
                        onCaseTap = {},
                    )
                }
            }

            composeTestRule.onNodeWithText(SeriousVoice.bigPictureEmptyState).assertExists()
        }

    @Test
    fun showsEarlyDaysPlaceholder_whenCaseHasNoEvents() =
        runTest {
            repository.insertCase(testCase(name = "Migraines"))
            val rows = repository.observeActiveCasesWithEvents().first().map { it.toTimelineRowData() }

            composeTestRule.setContent {
                MaterialTheme {
                    TimelineGrid(
                        rows = rows,
                        initialWindow = TimeWindow(0L, 1_000L),
                        onDotTap = { _, _ -> },
                        onCaseTap = {},
                    )
                }
            }

            composeTestRule.onNodeWithText(SeriousVoice.bigPictureEarlyDays).assertExists()
        }
}
