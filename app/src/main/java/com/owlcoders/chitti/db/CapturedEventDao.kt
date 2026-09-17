package com.owlcoders.chitti.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface CapturedEventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<CapturedEvent>>

    @Query("SELECT * FROM events WHERE rawText LIKE '%' || :query || '%' OR extractedWhat LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchEvents(query: String): Flow<List<CapturedEvent>>

    @Insert
    suspend fun insertEvent(event: CapturedEvent): Long

    @Delete
    suspend fun deleteEvent(event: CapturedEvent)

    @Query("DELETE FROM events")
    suspend fun deleteAllEvents()
}
