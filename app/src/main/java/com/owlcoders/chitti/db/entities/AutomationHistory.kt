package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "automation_history")
data class AutomationHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val actionType: String = "", // OPEN_APP, CREATE_REMINDER, etc.
    val parameters: String = "", // JSON string of action params
    val executedAt: Long = System.currentTimeMillis(),
    val result: String = "", // success, failed, cancelled
    val userConfirmed: Boolean = false
)
