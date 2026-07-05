package com.secondmonday.hodit.data

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
}
