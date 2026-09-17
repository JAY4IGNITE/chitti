package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memories",
    indices = [Index("category"), Index("key")]
)
data class Memory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String = "note", // college, branch, project, person, note
    val key: String = "",
    val value: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
