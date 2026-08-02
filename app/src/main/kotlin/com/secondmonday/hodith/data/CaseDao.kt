package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CaseDao {
    @Insert
    suspend fun insert(case: CaseEntity): Long

    @Update
    suspend fun update(case: CaseEntity)

    @Delete
    suspend fun delete(case: CaseEntity)

    @Query("SELECT * FROM cases WHERE id = :id")
    suspend fun getById(id: Long): CaseEntity?

    @Query("SELECT * FROM cases WHERE id = :id")
    fun observeById(id: Long): Flow<CaseEntity?>

    @Query("SELECT * FROM cases WHERE archived = 0 ORDER BY sortOrder")
    fun observeActiveCases(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE archived = 0 ORDER BY sortOrder")
    suspend fun getActiveCases(): List<CaseEntity>

    @Transaction
    @Query("SELECT * FROM cases WHERE archived = 0 ORDER BY sortOrder")
    fun observeActiveCasesWithEvents(): Flow<List<CaseWithEvents>>

    @Transaction
    @Query("SELECT * FROM cases WHERE archived = 0 ORDER BY sortOrder")
    fun observeActiveCasesWithEventsAndTags(): Flow<List<CaseWithEventsAndTags>>

    @Transaction
    @Query("SELECT * FROM cases WHERE archived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchivedCasesWithEvents(): Flow<List<CaseWithEvents>>

    // Events/tags/hunches/triggers cascade via their FOREIGN KEY(...) ON DELETE CASCADE.
    @Query("DELETE FROM cases")
    suspend fun deleteAll()

    @Query("SELECT * FROM cases")
    suspend fun getAll(): List<CaseEntity>
}
