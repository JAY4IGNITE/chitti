package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String = "user", // user, assistant
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val intent: String = "" // optional detected intent
)
