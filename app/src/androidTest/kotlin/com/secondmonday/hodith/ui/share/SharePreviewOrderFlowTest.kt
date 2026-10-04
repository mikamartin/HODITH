package com.secondmonday.hodith.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.DataStoreSettingsRepository
import com.secondmonday.hodith.data.DurationMode
import com.secondmonday.hodith.data.HodithDatabase
import com.secondmonday.hodith.data.RoomHodithRepository
import com.secondmonday.hodith.data.ShareInsightsSection
import com.secondmonday.hodith.data.createInMemoryDatabase
import com.secondmonday.hodith.data.share.ShareImageExporter
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.FakeClock
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import com.secondmonday.hodith.testtags.Smoke
import com.secondmonday.hodith.testtags.UiTest
import com.secondmonday.hodith.ui.common.setHodithContent
import com.secondmonday.hodith.ui.voice.PlainVoice
import com.secondmonday.hodith.viewmodel.ShareCardFormat
import com.secondmonday.hodith.viewmodel.ShareViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Provider

/**
 * The full path a reorder takes on a device, minus only Hilt: a real Room database, the real
 * [ShareViewModel] with its DataStore-backed order, and [SharePreviewScreen] driven by a long-press
 * drag on the picker. Checks that the card follows the reorder.
 */
@UiTest
@RunWith(AndroidJUnit4::class)
class SharePreviewOrderFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HodithDatabase
    private lateinit var viewModel: ShareViewModel

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        val repository =
            RoomHodithRepository(
                database = db,
                caseDao = db.caseDao(),
                eventDao = db.eventDao(),
                tagDao = db.tagDao(),
                watchDao = db.watchDao(),
                notificationEvalScheduler =
                    NotificationEvalScheduler(
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        evaluator = Provider { error("not used by the share screen") },
                    ),
            )
        val settings =
            DataStoreSettingsRepository(
                dataStore =
                    PreferenceDataStoreFactory.create(
                        produceFile = { context.preferencesDataStoreFile("order-flow-${UUID.randomUUID()}") },
                    ),
                context = context,
            )
        val caseId =
            runBlocking {
                val id = db.caseDao().insert(testCase(durationMode = DurationMode.NONE))
                // Two events, a few days apart: enough for Gaps, Streaks and Start times to all have data.
                listOf(0L, 3L, 9L).forEach { day ->
                    db.eventDao().insert(testEvent(caseId = id, occurredAt = DAY_MILLIS * day))
                }
                id
            }
        viewModel =
            ShareViewModel(
                repository = repository,
                clock = FakeClock(DAY_MILLIS * 20),
                shareImageExporter = UnusedExporter,
                settingsRepository = settings,
                savedStateHandle = SavedStateHandle(mapOf("caseId" to caseId)),
            )
        viewModel.setFormat(ShareCardFormat.STORY)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun dragStartTimesToTheTop_movesItsCardSectionToTheTop() {
        composeTestRule.setHodithContent {
            val uiState by viewModel.uiState.collectAsState()
            SharePreviewScreen(
                uiState = uiState,
                now = DAY_MILLIS * 20,
                graphicsLayer = rememberGraphicsLayer(),
                onBack = {},
                onFormatSelect = {},
                onDisplayNameChange = {},
                onSectionToggle = { _, _ -> },
                onSectionMove = viewModel::moveSection,
                onShareClick = {},
            )
        }
        composeTestRule.waitUntil { viewModel.uiState.value.case != null }
        composeTestRule.waitForIdle()

        // Drag from the row's title, not the grip: the whole row is the drag target. The picker comes before the card, so its copy is first.
        composeTestRule.onAllNodesWithText(PlainVoice.insightsSectionLabelRhythmStarts).onFirst().performTouchInput {
            down(center)
            advanceEventTime(1_000)
            moveBy(Offset(0f, -3_000f))
            up()
        }

        composeTestRule.waitUntil {
            viewModel.uiState.value.sectionOrder
                .first() == ShareInsightsSection.RHYTHM
        }
        composeTestRule.waitForIdle()

        assertEquals(
            ShareInsightsSection.RHYTHM,
            viewModel.uiState.value.sectionOrder
                .first(),
        )
        assertTrue(
            composeTestRule.cardTitleTop(PlainVoice.insightsSectionLabelRhythmStarts) <
                composeTestRule.cardTitleTop(PlainVoice.shareGapsTitle),
        )
    }

    private companion object {
        const val DAY_MILLIS = 24L * 60 * 60 * 1000

        /** Never reached: the test does not share the card as an image. */
        val UnusedExporter =
            object : ShareImageExporter {
                override suspend fun exportToShareUri(
                    bitmap: Bitmap,
                    fileNamePrefix: String,
                ): Uri = error("not used by the order flow")
            }
    }
}
