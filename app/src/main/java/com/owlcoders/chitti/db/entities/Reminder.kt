package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId"), Index("triggerTime")]
)
data class Reminder(
    @PrimaryKey val taskId: Int,
    val triggerTime: Long, // epoch ms
    val repeatRule: String = "none", // none, daily, weekly
    val alertBefore: Int = 15 // minutes before trigger
)
