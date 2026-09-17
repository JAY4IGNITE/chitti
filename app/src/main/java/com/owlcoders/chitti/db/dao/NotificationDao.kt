package com.owlcoders.chitti.db.dao

import androidx.room.*
import com.owlcoders.chitti.db.entities.NotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY postTime DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE processed = 0 ORDER BY postTime DESC")
    fun getUnprocessedNotifications(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE hash = :hash AND postTime > :sinceTime LIMIT 1")
    suspend fun findDuplicate(hash: String, sinceTime: Long): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE rawText LIKE '%' || :query || '%' OR rawTitle LIKE '%' || :query || '%' ORDER BY postTime DESC")
    fun searchNotifications(query: String): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity): Long

    @Query("UPDATE notifications SET processed = 1 WHERE id = :id")
    suspend fun markProcessed(id: Int)

    @Delete
    suspend fun deleteNotification(notification: NotificationEntity)

    @Query("DELETE FROM notifications")
    suspend fun deleteAllNotifications()

    @Query("SELECT COUNT(*) FROM notifications")
    suspend fun getNotificationCount(): Int

    @Query("SELECT COUNT(*) FROM notifications WHERE processed = 0")
    suspend fun getUnprocessedCount(): Int
}
