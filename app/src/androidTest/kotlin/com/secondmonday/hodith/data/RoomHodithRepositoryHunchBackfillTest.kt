package com.secondmonday.hodith.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.secondmonday.hodith.notification.NotificationEvalScheduler
import com.secondmonday.hodith.testtags.Smoke
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Provider

/**
 * [RoomHodithRepository.backfillResolvedHunchVerdicts] against a real Room database — the one-time
 * fix-up that freezes a pre-existing resolved Hunch's verdict (see `HunchEntity.resolved*` columns
 * and `HodithApplication.onCreate`) once for rows migrated in from before those columns existed.
 */
@RunWith(AndroidJUnit4::class)
class RoomHodithRepositoryHunchBackfillTest {
    private lateinit var db: HodithDatabase
    private lateinit var repository: RoomHodithRepository

    @Before
    fun setUp() {
        db = createInMemoryDatabase()
        repository =
            RoomHodithRepository(
                database = db,
                caseDao = db.caseDao(),
                eventDao = db.eventDao(),
                tagDao = db.tagDao(),
                hunchDao = db.hunchDao(),
                triggerDao = db.triggerDao(),
                notificationEvalScheduler =
                    NotificationEvalScheduler(
                        scope = CoroutineScope(Dispatchers.Unconfined),
                        evaluator = Provider { error("not used by the backfill") },
                    ),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Smoke
    @Test
    fun backfill_snapshotsAPendingResolvedHunch_countingOnlyEventsAtOrBeforeResolvedAt() =
        runTest {
            val caseId = db.caseDao().insert(testCase(name = "Coffee"))
            db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 50L))
            db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 150L)) // after resolvedAt, excluded
            val hunchId = db.hunchDao().insert(testHunch(caseId = caseId, resolvedAt = 100L))

            repository.backfillResolvedHunchVerdicts()

            val snapshot = db.hunchDao().getAll().single { it.id == hunchId }
            assertTrue(snapshot.resolvedVerdictSnapshotTaken)
            assertEquals(1, snapshot.resolvedEventCount)
        }

    @Test
    fun backfill_leavesAnActiveHunchAlone() =
        runTest {
            val caseId = db.caseDao().insert(testCase(name = "Coffee"))
            val hunchId = db.hunchDao().insert(testHunch(caseId = caseId, resolvedAt = null))

            repository.backfillResolvedHunchVerdicts()

            val hunch = db.hunchDao().getAll().single { it.id == hunchId }
            assertFalse(hunch.resolvedVerdictSnapshotTaken)
        }

    @Test
    fun backfill_isANoOpOnceAlreadySnapshotted() =
        runTest {
            val caseId = db.caseDao().insert(testCase(name = "Coffee"))
            db.hunchDao().insert(testHunch(caseId = caseId, resolvedAt = 100L))
            repository.backfillResolvedHunchVerdicts()
            val firstPass = db.hunchDao().getAll().single()

            // A later Event edit must not retroactively change the now-frozen snapshot.
            db.eventDao().insert(testEvent(caseId = caseId, occurredAt = 10L))
            repository.backfillResolvedHunchVerdicts()

            assertEquals(firstPass, db.hunchDao().getAll().single())
        }

    @Test
    fun backfill_omitsComparisonBandWhenTheHunchNeverReachedAVerdict() =
        runTest {
            val caseId = db.caseDao().insert(testCase(name = "Coffee"))
            val hunchId = db.hunchDao().insert(testHunch(caseId = caseId, resolvedAt = 1L))

            repository.backfillResolvedHunchVerdicts()

            val snapshot = db.hunchDao().getAll().single { it.id == hunchId }
            assertTrue(snapshot.resolvedVerdictSnapshotTaken)
            assertEquals(null, snapshot.resolvedComparisonBand)
        }
}
