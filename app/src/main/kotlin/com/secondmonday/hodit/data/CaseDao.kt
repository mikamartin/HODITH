package com.secondmonday.hodit.data

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

    @Transaction
    @Query("SELECT * FROM cases WHERE archived = 0 ORDER BY sortOrder")
    fun observeActiveCasesWithEvents(): Flow<List<CaseWithEvents>>
}
