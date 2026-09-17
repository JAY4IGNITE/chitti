package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notifications",
    indices = [Index("postTime"), Index("hash")]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val postTime: Long,
    val rawTitle: String = "",
    val rawText: String = "",
    val processed: Boolean = false,
    val hash: String = "" // for dedup: hash of source + task + time
)
