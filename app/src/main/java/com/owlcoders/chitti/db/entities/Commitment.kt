package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "commitments",
    foreignKeys = [ForeignKey(
        entity = Task::class,
        parentColumns = ["id"],
        childColumns = ["taskId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("taskId")]
)
data class Commitment(
    @PrimaryKey val taskId: Int,
    val promiseTo: String = "",
    val promiseText: String = "",
    val createdFrom: String = "" // notification, sms, voice, manual
)
