package com.owlcoders.chitti.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.owlcoders.chitti.db.entities.Reminder

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: Reminder)

    @Query("SELECT * FROM reminders WHERE triggerTime > :now ORDER BY triggerTime ASC")
    suspend fun getUpcoming(now: Long): List<Reminder>

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun deleteByTaskId(taskId: Int)

    @Query("DELETE FROM reminders")
    suspend fun deleteAll()
}
