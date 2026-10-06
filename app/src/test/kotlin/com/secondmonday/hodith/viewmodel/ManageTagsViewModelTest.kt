package com.secondmonday.hodith.viewmodel

import com.secondmonday.hodith.data.EventTagCrossRef
import com.secondmonday.hodith.data.FakeHodithRepository
import com.secondmonday.hodith.data.TagEntity
import com.secondmonday.hodith.domain.TAG_FILTER_THRESHOLD
import com.secondmonday.hodith.domain.TagSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageTagsViewModelTest {
    private val repository = FakeHodithRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun seed(
        tags: List<TagEntity>,
        attachments: List<EventTagCrossRef> = emptyList(),
    ) {
        repository.tags.value = tags
        repository.eventTags.value = attachments
    }

    private fun tag(
        id: Long,
        name: String,
    ) = TagEntity(id = id, name = name)

    private fun attach(
        eventId: Long,
        tagId: Long,
    ) = EventTagCrossRef(eventId = eventId, tagId = tagId)

    private suspend fun ManageTagsViewModel.loaded(): ManageTagsUiState = uiState.first { !it.isLoading }

    private suspend fun ManageTagsViewModel.summaryNamed(name: String): TagSummary = loaded().tags.single { it.tag.name == name }

    @Test
    fun `tags are listed by name with their event counts, zero when unattached`() =
        runTest {
            seed(
                tags = listOf(tag(1, "focus"), tag(2, "calm"), tag(3, "unused")),
                attachments = listOf(attach(10, 1), attach(11, 1), attach(10, 2)),
            )

            val state = ManageTagsViewModel(repository).loaded()

            assertEquals(listOf("calm" to 1, "focus" to 2, "unused" to 0), state.tags.map { it.tag.name to it.eventCount })
        }

    @Test
    fun `filter is hidden at the threshold and shown one above it`() =
        runTest {
            val names = (1..TAG_FILTER_THRESHOLD + 1).map { tag(it.toLong(), "tag$it") }
            seed(tags = names.take(TAG_FILTER_THRESHOLD))
            val viewModel = ManageTagsViewModel(repository)
            assertFalse(viewModel.loaded().showFilter)

            repository.tags.value = names
            assertTrue(viewModel.loaded().showFilter)
        }

    @Test
    fun `query narrows the visible list when the filter is shown`() =
        runTest {
            val names = (1..TAG_FILTER_THRESHOLD).map { tag(it.toLong(), "tag$it") } + tag(99, "Coffee")
            seed(tags = names)
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onQueryChange("COFF")

            val state = viewModel.loaded()
            assertEquals(listOf("Coffee"), state.visibleTags.map { it.tag.name })
            assertEquals(TAG_FILTER_THRESHOLD + 1, state.tags.size)
        }

    @Test
    fun `query is ignored when the filter is hidden, so a shrinking list is never silently narrowed`() =
        runTest {
            seed(tags = listOf(tag(1, "coffee"), tag(2, "tea")))
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onQueryChange("tea")

            assertEquals(listOf("coffee", "tea"), viewModel.loaded().visibleTags.map { it.tag.name })
        }

    @Test
    fun `rename to a free name waits for confirmation before writing`() =
        runTest {
            seed(tags = listOf(tag(1, "espresso")), attachments = listOf(attach(10, 1), attach(11, 1)))
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onRenameRequested(viewModel.summaryNamed("espresso"), "  latte  ")

            assertEquals(PendingTagAction.Rename(tag(1, "espresso"), newName = "latte", eventCount = 2), viewModel.loaded().pending)
            assertEquals(
                "espresso",
                repository.tags.value
                    .single()
                    .name,
            )

            viewModel.onConfirmPending()

            assertEquals(
                "latte",
                repository.tags.value
                    .single()
                    .name,
            )
            assertNull(viewModel.loaded().pending)
        }

    @Test
    fun `rename onto another tag's name becomes a merge, with the overlap counted`() =
        runTest {
            // Event 11 already carries both tags, so the merge keeps one attachment for it.
            seed(
                tags = listOf(tag(1, "espresso"), tag(2, "Coffee")),
                attachments = listOf(attach(10, 1), attach(11, 1), attach(11, 2), attach(12, 2)),
            )
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onRenameRequested(viewModel.summaryNamed("espresso"), "coffee")

            assertEquals(
                PendingTagAction.Merge(
                    tag = tag(1, "espresso"),
                    target = tag(2, "Coffee"),
                    sourceEventCount = 2,
                    overlapCount = 1,
                ),
                viewModel.loaded().pending,
            )
            assertEquals(2, repository.tags.value.size)

            viewModel.onConfirmPending()

            assertEquals(listOf(tag(2, "Coffee")), repository.tags.value)
            // Three events, one attachment each to the target: event 11 is not duplicated.
            assertEquals(setOf(attach(10, 2), attach(11, 2), attach(12, 2)), repository.eventTags.value.toSet())
            assertEquals(3, repository.eventTags.value.size)
        }

    @Test
    fun `renaming a tag only in case is a plain rename, not a merge with itself`() =
        runTest {
            seed(tags = listOf(tag(1, "coffee")), attachments = listOf(attach(10, 1)))
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onRenameRequested(viewModel.summaryNamed("coffee"), "Coffee")

            assertEquals(PendingTagAction.Rename(tag(1, "coffee"), newName = "Coffee", eventCount = 1), viewModel.loaded().pending)
        }

    @Test
    fun `blank or unchanged rename requests nothing`() =
        runTest {
            seed(tags = listOf(tag(1, "coffee")))
            val viewModel = ManageTagsViewModel(repository)
            val coffee = viewModel.summaryNamed("coffee")

            viewModel.onRenameRequested(coffee, "   ")
            assertNull(viewModel.loaded().pending)

            viewModel.onRenameRequested(coffee, "coffee")
            assertNull(viewModel.loaded().pending)
        }

    @Test
    fun `a rename whose name was taken after the warning drops the write instead of violating the unique index`() =
        runTest {
            seed(tags = listOf(tag(1, "espresso")))
            val viewModel = ManageTagsViewModel(repository)
            viewModel.onRenameRequested(viewModel.summaryNamed("espresso"), "latte")
            repository.tags.value = repository.tags.value + tag(2, "Latte")

            viewModel.onConfirmPending()

            assertEquals(2, repository.tags.value.size)
            assertEquals(
                "espresso",
                repository.tags.value
                    .single { it.id == 1L }
                    .name,
            )
        }

    @Test
    fun `delete waits for confirmation, then removes the tag and only its attachments`() =
        runTest {
            seed(
                tags = listOf(tag(1, "focus"), tag(2, "calm")),
                attachments = listOf(attach(10, 1), attach(10, 2)),
            )
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onDeleteRequested(viewModel.summaryNamed("focus"))
            assertEquals(PendingTagAction.Delete(tag(1, "focus"), eventCount = 1), viewModel.loaded().pending)
            assertEquals(2, repository.tags.value.size)

            viewModel.onConfirmPending()

            assertEquals(listOf(tag(2, "calm")), repository.tags.value)
            assertEquals(listOf(attach(10, 2)), repository.eventTags.value)
        }

    @Test
    fun `dismissing a warning writes nothing`() =
        runTest {
            seed(tags = listOf(tag(1, "focus")), attachments = listOf(attach(10, 1)))
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onDeleteRequested(viewModel.summaryNamed("focus"))
            viewModel.onDismissPending()

            assertNull(viewModel.loaded().pending)
            assertEquals(listOf(tag(1, "focus")), repository.tags.value)
            assertEquals(listOf(attach(10, 1)), repository.eventTags.value)
        }

    @Test
    fun `merging a tag with no events merges with an overlap of zero`() =
        runTest {
            seed(tags = listOf(tag(1, "espresso"), tag(2, "coffee")), attachments = listOf(attach(10, 2)))
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onRenameRequested(viewModel.summaryNamed("espresso"), "coffee")

            assertEquals(
                PendingTagAction.Merge(tag(1, "espresso"), tag(2, "coffee"), sourceEventCount = 0, overlapCount = 0),
                viewModel.loaded().pending,
            )
        }

    @Test
    fun `confirming with nothing pending writes nothing`() =
        runTest {
            seed(tags = listOf(tag(1, "focus")), attachments = listOf(attach(10, 1)))
            val viewModel = ManageTagsViewModel(repository)

            viewModel.onConfirmPending()

            assertEquals(listOf(tag(1, "focus")), repository.tags.value)
            assertEquals(listOf(attach(10, 1)), repository.eventTags.value)
        }
}
