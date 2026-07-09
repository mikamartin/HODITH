package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events WHERE caseId = :caseId ORDER BY occurredAt DESC")
    fun observeEventsForCase(caseId: Long): Flow<List<EventEntity>>

    @Transaction
    @Query("SELECT * FROM events WHERE caseId = :caseId ORDER BY occurredAt DESC")
    fun observeEventsWithTagsForCase(caseId: Long): Flow<List<EventWithTags>>

    @Query(
        "SELECT * FROM events WHERE caseId = :caseId " +
            "AND occurredAt >= :windowStart AND occurredAt < :windowEnd ORDER BY occurredAt",
    )
    suspend fun eventsInWindow(
        caseId: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<EventEntity>

    @Query("SELECT * FROM events WHERE caseId = :caseId AND endedAt IS NULL LIMIT 1")
    suspend fun getOngoingEvent(caseId: Long): EventEntity?
}
