package com.owlcoders.chitti.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val relation: String = "", // friend, colleague, professor, family
    val lastInteraction: Long = System.currentTimeMillis()
)
