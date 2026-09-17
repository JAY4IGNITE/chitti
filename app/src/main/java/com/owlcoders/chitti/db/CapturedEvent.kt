package com.owlcoders.chitti.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class CapturedEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourceApp: String,
    val rawText: String,
    val extractedWhat: String?,
    val extractedWhen: String?,
    val extractedWho: String?,
    val status: String,
    val timestamp: Long
)
