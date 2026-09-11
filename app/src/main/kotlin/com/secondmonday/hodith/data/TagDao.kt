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

    // Case-insensitive: tag matching is case-insensitive throughout the app (mirrors Case Edit's
    // duplicate-name check and viewmodel.tagDiff), so "Coffee" reuses an existing "coffee" tag
    // instead of creating a near-duplicate. The unique index on `name` stays case-sensitive at
    // the schema level, but this lookup is what actually prevents that duplicate from being
    // inserted in the first place.
    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE")
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

    /**
     * One row per tag attachment, for every active Case's events (see [EventTagName]) — the Big
     * Picture grid's tag filter and detail-row pills. Flat, no per-event nesting, no `IN (...)`
     * chunking over event ids the way the old `@Relation` cascade needed.
     */
    @Query(
        "SELECT et.eventId AS eventId, t.name AS tagName " +
            "FROM event_tags et JOIN tags t ON t.id = et.tagId " +
            "JOIN events e ON e.id = et.eventId JOIN cases c ON c.id = e.caseId " +
            "WHERE c.archived = 0",
    )
    fun observeActiveCaseEventTagNames(): Flow<List<EventTagName>>

    // Tags aren't scoped to a case (they're a shared vocabulary across cases), so they don't
    // cascade when cases are deleted — deleteAllData() must clear them explicitly.
    @Query("DELETE FROM tags")
    suspend fun deleteAll()

    @Query("SELECT * FROM tags ORDER BY name")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM event_tags")
    suspend fun getAllEventTags(): List<EventTagCrossRef>
}
