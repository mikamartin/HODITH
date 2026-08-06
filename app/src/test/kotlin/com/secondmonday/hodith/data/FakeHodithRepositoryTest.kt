package com.secondmonday.hodith.data

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeHodithRepositoryTest {
    private val repository = FakeHodithRepository()

    private fun testCase(
        id: Long = 0L,
        name: String = "Coffee",
        sortOrder: Int = 0,
        archived: Boolean = false,
    ) = CaseEntity(
        id = id,
        name = name,
        icon = "☕️",
        createdAt = 0L,
        logFlow = LogFlow.ONE_TAP,
        durationMode = DurationMode.NONE,
        intensityEnabled = false,
        hunchNudgeDismissed = false,
        checkInsEnabled = true,
        lastCheckInAt = null,
        sortOrder = sortOrder,
        archived = archived,
    )

    private fun testEvent(
        id: Long = 0L,
        caseId: Long = 1L,
        occurredAt: Long = 0L,
    ) = EventEntity(id = id, caseId = caseId, occurredAt = occurredAt, endedAt = null, intensity = null, note = null, loggedAt = occurredAt)

    private fun testHunch(caseId: Long) =
        HunchEntity(
            caseId = caseId,
            direction = HunchDirection.JUST_CURIOUS,
            expectedCount = 1,
            expectedPer = ExpectedPer.WEEK,
            createdAt = 0L,
            resolvedAt = null,
        )

    private fun testTrigger(caseId: Long) =
        TriggerEntity(
            caseId = caseId,
            kind = TriggerKind.AT_LEAST,
            threshold = 3,
            windowDays = 7,
            enabled = true,
            armed = true,
            lastFiredAt = null,
        )

    @Test
    fun `insertCase assigns incrementing ids and updateCase replaces by id`() =
        runTest {
            val firstId = repository.insertCase(testCase(name = "First"))
            val secondId = repository.insertCase(testCase(name = "Second"))
            assertEquals(1L, firstId)
            assertEquals(2L, secondId)

            repository.updateCase(repository.getCase(firstId)!!.copy(name = "Renamed"))

            assertEquals("Renamed", repository.getCase(firstId)?.name)
            assertEquals("Second", repository.getCase(secondId)?.name)
        }

    @Test
    fun `observeActiveCasesWithEvents excludes archived cases, sorts by sortOrder, and joins matching events`() =
        runTest {
            val keptId = repository.insertCase(testCase(name = "Kept", sortOrder = 1))
            repository.insertCase(testCase(name = "Archived", sortOrder = 0, archived = true))
            repository.insertEvent(testEvent(caseId = keptId, occurredAt = 100L))
            repository.insertEvent(testEvent(caseId = 999L, occurredAt = 200L)) // belongs to a different case

            repository.observeActiveCasesWithEvents().test {
                val rows = awaitItem()
                assertEquals(listOf("Kept"), rows.map { it.case.name })
                assertEquals(1, rows.single().events.size)
                assertEquals(
                    100L,
                    rows
                        .single()
                        .events
                        .single()
                        .occurredAt,
                )
            }
        }

    @Test
    fun `observeArchivedCasesWithEvents sorts archived cases by name, case-insensitively`() =
        runTest {
            repository.insertCase(testCase(name = "banana", archived = true))
            repository.insertCase(testCase(name = "Apple", archived = true))
            repository.insertCase(testCase(name = "cherry", archived = false))

            repository.observeArchivedCasesWithEvents().test {
                assertEquals(listOf("Apple", "banana"), awaitItem().map { it.case.name })
            }
        }

    @Test
    fun `deleteCase cascades to its events, hunches, and triggers but leaves other cases' alone`() =
        runTest {
            val deletedId = repository.insertCase(testCase())
            val keptId = repository.insertCase(testCase(name = "Other"))
            repository.insertEvent(testEvent(caseId = deletedId))
            repository.insertEvent(testEvent(caseId = keptId))
            repository.insertHunch(testHunch(caseId = deletedId))
            repository.insertHunch(testHunch(caseId = keptId))
            repository.insertTrigger(testTrigger(caseId = deletedId))
            repository.insertTrigger(testTrigger(caseId = keptId))

            repository.deleteCase(repository.getCase(deletedId)!!)

            assertNull(repository.getCase(deletedId))
            assertTrue(repository.events.value.none { it.caseId == deletedId })
            assertEquals(1, repository.events.value.count { it.caseId == keptId })
            assertTrue(repository.hunches.value.none { it.caseId == deletedId })
            assertEquals(1, repository.hunches.value.count { it.caseId == keptId })
            assertTrue(repository.triggers.value.none { it.caseId == deletedId })
            assertEquals(1, repository.triggers.value.count { it.caseId == keptId })
        }

    @Test
    fun `eventsInWindow is a half-open range on occurredAt scoped to the given case`() =
        runTest {
            val caseId = 1L
            repository.events.value =
                listOf(
                    testEvent(id = 1L, caseId = caseId, occurredAt = 100L),
                    testEvent(id = 2L, caseId = caseId, occurredAt = 200L),
                    testEvent(id = 3L, caseId = caseId, occurredAt = 300L),
                    testEvent(id = 4L, caseId = 2L, occurredAt = 150L),
                )

            val inWindow = repository.eventsInWindow(caseId, windowStart = 100L, windowEnd = 300L)

            assertEquals(listOf(100L, 200L), inWindow.map { it.occurredAt })
        }

    @Test
    fun `getMostRecentEventForCase returns the latest event scoped to the given case`() =
        runTest {
            val caseId = 1L
            repository.events.value =
                listOf(
                    testEvent(id = 1L, caseId = caseId, occurredAt = 100L),
                    testEvent(id = 2L, caseId = caseId, occurredAt = 300L),
                    testEvent(id = 3L, caseId = 2L, occurredAt = 500L),
                )

            val mostRecent = repository.getMostRecentEventForCase(caseId)

            assertEquals(300L, mostRecent?.occurredAt)
        }

    @Test
    fun `observeEventsWithTagsForCase sorts newest-first and attaches only that event's tags`() =
        runTest {
            val caseId = repository.insertCase(testCase())
            val olderId = repository.insertEvent(testEvent(caseId = caseId, occurredAt = 100L))
            val newerId = repository.insertEvent(testEvent(caseId = caseId, occurredAt = 200L))
            repository.addTagToEvent(newerId, "focus")

            repository.observeEventsWithTagsForCase(caseId).test {
                val rows = awaitItem()
                assertEquals(listOf(newerId, olderId), rows.map { it.event.id })
                assertEquals(listOf("focus"), rows.first { it.event.id == newerId }.tags.map { it.name })
                assertTrue(rows.first { it.event.id == olderId }.tags.isEmpty())
            }
        }

    @Test
    fun `addTagToEvent reuses an existing tag by name instead of duplicating it`() =
        runTest {
            val firstEventId = repository.insertEvent(testEvent(id = 1L))
            val secondEventId = repository.insertEvent(testEvent(id = 2L))

            repository.addTagToEvent(firstEventId, "focus")
            repository.addTagToEvent(secondEventId, " focus ") // same name, extra whitespace

            assertEquals(listOf("focus"), repository.tags.value.map { it.name })
            assertEquals(2, repository.eventTags.value.size)
        }

    @Test
    fun `addTagToEvent is idempotent for the same event and tag`() =
        runTest {
            val eventId = repository.insertEvent(testEvent())

            repository.addTagToEvent(eventId, "focus")
            repository.addTagToEvent(eventId, "focus")

            assertEquals(1, repository.eventTags.value.size)
        }

    @Test
    fun `removeTagFromEvent removes only the matching cross-ref`() =
        runTest {
            val eventId = repository.insertEvent(testEvent())
            repository.addTagToEvent(eventId, "focus")
            repository.addTagToEvent(eventId, "calm")
            val focusTagId =
                repository.tags.value
                    .single { it.name == "focus" }
                    .id

            repository.removeTagFromEvent(eventId, focusTagId)

            repository.observeTagsForEvent(eventId).test {
                assertEquals(listOf("calm"), awaitItem().map { it.name })
            }
        }

    @Test
    fun `observeActiveHunch returns only the unresolved hunch for the case`() =
        runTest {
            val caseId = 1L
            repository.insertHunch(
                HunchEntity(
                    caseId = caseId,
                    direction = HunchDirection.TOO_OFTEN,
                    expectedCount = 1,
                    expectedPer = ExpectedPer.WEEK,
                    createdAt = 0L,
                    resolvedAt = 100L,
                ),
            )
            val activeId =
                repository.insertHunch(
                    HunchEntity(
                        caseId = caseId,
                        direction = HunchDirection.NOT_ENOUGH,
                        expectedCount = 1,
                        expectedPer = ExpectedPer.WEEK,
                        createdAt = 0L,
                        resolvedAt = null,
                    ),
                )

            repository.observeActiveHunch(caseId).test {
                assertEquals(activeId, awaitItem()?.id)
            }
        }

    @Test
    fun `getEnabledTriggers filters out disabled triggers across all cases`() =
        runTest {
            repository.insertTrigger(
                TriggerEntity(caseId = 1L, kind = TriggerKind.AT_LEAST, threshold = 3, windowDays = 7, enabled = true, lastFiredAt = null),
            )
            repository.insertTrigger(
                TriggerEntity(
                    caseId = 2L,
                    kind = TriggerKind.SILENT_FOR,
                    threshold = 1,
                    windowDays = null,
                    enabled = false,
                    lastFiredAt = null,
                ),
            )

            val enabled = repository.getEnabledTriggers()

            assertEquals(1, enabled.size)
            assertTrue(enabled.single().enabled)
        }

    @Test
    fun `importBackupData replaces all existing data rather than merging`() =
        runTest {
            val staleId = repository.insertCase(testCase(name = "Stale"))
            repository.insertEvent(testEvent(caseId = staleId))

            val backup = repository.exportBackupData().copy(cases = listOf(testCase(id = 5L, name = "Restored")), events = emptyList())
            repository.importBackupData(backup)

            assertEquals(listOf("Restored"), repository.cases.value.map { it.name })
            assertTrue(repository.events.value.isEmpty())
        }
}
