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
}
