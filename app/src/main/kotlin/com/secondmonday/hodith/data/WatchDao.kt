package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchDao {
    @Insert
    suspend fun insert(watch: WatchEntity): Long

    @Update
    suspend fun update(watch: WatchEntity)

    @Delete
    suspend fun delete(watch: WatchEntity)

    @Query("SELECT * FROM watches WHERE id = :id")
    suspend fun getById(id: Long): WatchEntity?

    @Query("SELECT * FROM watches WHERE caseId = :caseId")
    fun observeWatchesForCase(caseId: Long): Flow<List<WatchEntity>>

    @Query("SELECT * FROM watches WHERE caseId = :caseId")
    suspend fun getWatchesForCase(caseId: Long): List<WatchEntity>

    @Query("SELECT * FROM watches WHERE enabled = 1")
    suspend fun getEnabledWatches(): List<WatchEntity>

    @Query("SELECT * FROM watches")
    suspend fun getAll(): List<WatchEntity>
}
