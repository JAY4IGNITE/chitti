package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val status: String = "pending", // pending, done
    val priority: Int = 0, // 0=low, 1=medium, 2=high
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val sourceType: String = "", // notification, sms, voice, camera, manual
    val sourceId: Int? = null
)
