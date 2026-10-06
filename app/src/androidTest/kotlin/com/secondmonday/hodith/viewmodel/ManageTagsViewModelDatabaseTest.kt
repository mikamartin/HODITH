package com.secondmonday.hodith.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.data.HodithDatabase
import com.secondmonday.hodith.data.RoomHodithRepository
import com.secondmonday.hodith.data.createInMemoryDatabase
import com.secondmonday.hodith.data.testCase
import com.secondmonday.hodith.data.testEvent
import com.secondmonday.hodith.domain.TagSummary
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/**
 * [ManageTagsViewModel] over a real database, for the failure paths the JVM tests cannot reach: an
 * Android [android.database.SQLException] can't be constructed in a JVM unit test, so the failure is
 * produced by the database itself. Uses [runBlocking] rather than runTest: [withTimeout] needs real
 * time, because Room emits on background threads and runTest's virtual clock would expire it at once.
 */
@RunWith(AndroidJUnit4::class)
class ManageTagsViewModelDatabaseTest {
    private lateinit var db: HodithDatabase
    private lateinit var repository: RoomHodithRepository
    private var eventId: Long = 0

    @Before
    fun setUp() =
        runBlocking {
            Dispatchers.setMain(UnconfinedTestDispatcher())
            db = createInMemoryDatabase()
            repository =
                RoomHodithRepository(
                    database = db,
                    caseDao = db.caseDao(),
                    eventDao = db.eventDao(),
                    tagDao = db.tagDao(),
                    watchDao = db.watchDao(),
                    notificationEvalScheduler =
                        NotificationEvalScheduler(
                            scope = CoroutineScope(Dispatchers.Unconfined),
                            evaluator = Provider { error("not used by tag management") },
                        ),
                )
            val caseId = db.caseDao().insert(testCase())
            eventId = db.eventDao().insert(testEvent(caseId = caseId))
        }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private suspend fun awaitState(
        viewModel: ManageTagsViewModel,
        predicate: (ManageTagsUiState) -> Boolean,
    ): ManageTagsUiState = withTimeout(STATE_TIMEOUT_MILLIS) { viewModel.uiState.first { !it.isLoading && predicate(it) } }

    private suspend fun summaryNamed(
        viewModel: ManageTagsViewModel,
        name: String,
    ): TagSummary = awaitState(viewModel) { true }.tags.single { it.tag.name == name }

    private suspend fun tagId(name: String): Long = db.tagDao().getByName(name)!!.id

    @Test
    fun mergeWhoseTargetWasDeletedSinceTheWarning_reportsFailureAndLeavesTheSourceIntact() =
        runBlocking {
            repository.addTagToEvent(eventId, "espresso")
            repository.addTagToEvent(eventId, "coffee")
            val viewModel = ManageTagsViewModel(repository)
            viewModel.onRenameRequested(summaryNamed(viewModel, "espresso"), "coffee")
            awaitState(viewModel) { it.pending is PendingTagAction.Merge }
            // Another writer removes the target while the warning is open.
            repository.deleteTag(tagId("coffee"))

            viewModel.onConfirmPending()

            val state = awaitState(viewModel) { it.writeFailed }
            assertNull(state.pending)
            assertEquals(listOf("espresso"), repository.observeAllTags().first().map { it.name })
            assertEquals(listOf(eventId), db.tagDao().getAllEventTags().map { it.eventId })
        }

    @Test
    fun aFailureIsClearedByTheNextRequest() =
        runBlocking {
            repository.addTagToEvent(eventId, "espresso")
            repository.addTagToEvent(eventId, "coffee")
            val viewModel = ManageTagsViewModel(repository)
            viewModel.onRenameRequested(summaryNamed(viewModel, "espresso"), "coffee")
            awaitState(viewModel) { it.pending is PendingTagAction.Merge }
            repository.deleteTag(tagId("coffee"))
            viewModel.onConfirmPending()
            awaitState(viewModel) { it.writeFailed }

            viewModel.onDeleteRequested(summaryNamed(viewModel, "espresso"))

            val state = awaitState(viewModel) { it.pending is PendingTagAction.Delete }
            assertFalse(state.writeFailed)
        }

    @Test
    fun aSuccessfulWriteReportsNoFailure() =
        runBlocking {
            repository.addTagToEvent(eventId, "focus")
            val viewModel = ManageTagsViewModel(repository)
            viewModel.onDeleteRequested(summaryNamed(viewModel, "focus"))

            viewModel.onConfirmPending()

            awaitState(viewModel) { it.tags.isEmpty() }
            assertFalse(viewModel.uiState.first().writeFailed)
            assertNull(db.tagDao().getByName("focus"))
        }

    private companion object {
        const val STATE_TIMEOUT_MILLIS = 5_000L
    }
}
