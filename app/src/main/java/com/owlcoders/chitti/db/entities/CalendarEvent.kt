package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_events",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId"), Index("eventStart")]
)
data class CalendarEvent(
    @PrimaryKey val taskId: Int,
    val eventTitle: String = "",
    val eventStart: Long = 0L, // epoch ms
    val eventEnd: Long = 0L, // epoch ms
    val eventLocation: String = "",
    val calendarId: String = "" // external calendar uid
)
