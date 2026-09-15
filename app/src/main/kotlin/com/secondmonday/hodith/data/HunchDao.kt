package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HunchDao {
    @Insert
    suspend fun insert(hunch: HunchEntity): Long

    @Update
    suspend fun update(hunch: HunchEntity)

    @Delete
    suspend fun delete(hunch: HunchEntity)

    @Query("SELECT * FROM hunches WHERE caseId = :caseId AND resolvedAt IS NULL LIMIT 1")
    fun observeActiveHunch(caseId: Long): Flow<HunchEntity?>

    @Query("SELECT * FROM hunches WHERE caseId = :caseId AND resolvedAt IS NULL LIMIT 1")
    suspend fun getActiveHunch(caseId: Long): HunchEntity?

    @Query("SELECT * FROM hunches WHERE caseId = :caseId ORDER BY createdAt DESC")
    fun observeHunchHistory(caseId: Long): Flow<List<HunchEntity>>

    @Query("SELECT * FROM hunches")
    suspend fun getAll(): List<HunchEntity>

    /** Resolved Hunches whose verdict snapshot (`resolved*` columns) hasn't been backfilled yet — see [com.secondmonday.hodith.HodithApplication]. */
    @Query("SELECT * FROM hunches WHERE resolvedAt IS NOT NULL AND resolvedVerdictSnapshotTaken = 0")
    suspend fun getResolvedHunchesMissingSnapshot(): List<HunchEntity>

    /**
     * Keeps only the [keep] most-recently-resolved Hunches for [caseId], deleting the rest.
     * The active (unresolved) Hunch is never touched — `resolvedAt IS NOT NULL` excludes it.
     */
    @Query(
        """
        DELETE FROM hunches
        WHERE caseId = :caseId AND resolvedAt IS NOT NULL
        AND id NOT IN (
            SELECT id FROM hunches
            WHERE caseId = :caseId AND resolvedAt IS NOT NULL
            ORDER BY resolvedAt DESC, id DESC
            LIMIT :keep
        )
        """,
    )
    suspend fun deleteResolvedHunchesBeyondLimit(
        caseId: Long,
        keep: Int,
    )
}
