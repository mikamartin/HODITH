package com.secondmonday.hodith.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Insert
    suspend fun insert(notification: NotificationEntity): Long

    @Update
    suspend fun update(notification: NotificationEntity)

    @Delete
    suspend fun delete(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE id = :id")
    suspend fun getById(id: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE caseId = :caseId")
    fun observeNotificationsForCase(caseId: Long): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE caseId = :caseId")
    suspend fun getNotificationsForCase(caseId: Long): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE enabled = 1")
    suspend fun getEnabledNotifications(): List<NotificationEntity>

    @Query("SELECT * FROM notifications")
    suspend fun getAll(): List<NotificationEntity>
}
