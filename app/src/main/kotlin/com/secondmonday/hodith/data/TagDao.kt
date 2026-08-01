package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Query("SELECT * FROM tags WHERE name = :name")
    suspend fun getByName(name: String): TagEntity?

    @Query("SELECT * FROM tags ORDER BY name")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEventTag(crossRef: EventTagCrossRef)

    @Delete
    suspend fun deleteEventTag(crossRef: EventTagCrossRef)

    @Query(
        "SELECT tags.* FROM tags " +
            "INNER JOIN event_tags ON tags.id = event_tags.tagId " +
            "WHERE event_tags.eventId = :eventId",
    )
    fun observeTagsForEvent(eventId: Long): Flow<List<TagEntity>>

    @Query(
        "SELECT DISTINCT tags.* FROM tags " +
            "INNER JOIN event_tags ON tags.id = event_tags.tagId " +
            "INNER JOIN events ON events.id = event_tags.eventId " +
            "WHERE events.caseId = :caseId ORDER BY tags.name",
    )
    fun observeTagsForCase(caseId: Long): Flow<List<TagEntity>>

    // Tags aren't scoped to a case (they're a shared vocabulary across cases), so they don't
    // cascade when cases are deleted — deleteAllData() must clear them explicitly.
    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM event_tags")
    suspend fun getAllEventTags(): List<EventTagCrossRef>
}
