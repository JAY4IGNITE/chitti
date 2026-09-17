package com.owlcoders.chitti.db.dao

import androidx.room.*
import com.owlcoders.chitti.db.entities.AutomationHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationHistoryDao {
    @Query("SELECT * FROM automation_history ORDER BY executedAt DESC")
    fun getAllHistory(): Flow<List<AutomationHistory>>

    @Query("SELECT * FROM automation_history WHERE actionType = :actionType ORDER BY executedAt DESC")
    fun getHistoryByAction(actionType: String): Flow<List<AutomationHistory>>

    @Query("SELECT * FROM automation_history ORDER BY executedAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 50): Flow<List<AutomationHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: AutomationHistory): Long

    @Delete
    suspend fun deleteHistory(history: AutomationHistory)

    @Query("DELETE FROM automation_history")
    suspend fun deleteAllHistory()

    @Query("SELECT COUNT(*) FROM automation_history")
    suspend fun getHistoryCount(): Int

    @Query("SELECT COUNT(*) FROM automation_history WHERE result = 'success'")
    suspend fun getSuccessCount(): Int
}
