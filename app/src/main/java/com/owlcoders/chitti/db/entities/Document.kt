package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class Document(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val filePath: String = "",
    val mimeType: String = "",
    val title: String = "",
    val contentText: String = "",
    val ocrText: String = "",
    val indexed: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
