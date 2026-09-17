package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "deadlines",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId"), Index("dueDate")]
)
data class Deadline(
    @PrimaryKey val taskId: Int,
    val dueDate: Long, // epoch ms
    val dueTime: String = "",
    val location: String = ""
)
