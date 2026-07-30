package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TriggerDao {
    @Insert
    suspend fun insert(trigger: TriggerEntity): Long

    @Update
    suspend fun update(trigger: TriggerEntity)

    @Delete
    suspend fun delete(trigger: TriggerEntity)

    @Query("SELECT * FROM triggers WHERE id = :id")
    suspend fun getById(id: Long): TriggerEntity?

    @Query("SELECT * FROM triggers WHERE caseId = :caseId")
    fun observeTriggersForCase(caseId: Long): Flow<List<TriggerEntity>>

    @Query("SELECT * FROM triggers WHERE caseId = :caseId")
    suspend fun getTriggersForCase(caseId: Long): List<TriggerEntity>

    @Query("SELECT * FROM triggers WHERE enabled = 1")
    suspend fun getEnabledTriggers(): List<TriggerEntity>
}
