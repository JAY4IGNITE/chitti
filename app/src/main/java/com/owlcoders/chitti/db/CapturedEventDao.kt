package com.owlcoders.chitti.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedEventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<CapturedEvent>>

    @Insert
    suspend fun insertEvent(event: CapturedEvent)
}
