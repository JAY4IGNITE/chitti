package com.owlcoders.chitti.db.dao

import androidx.room.*
import com.owlcoders.chitti.db.entities.Person
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY lastInteraction DESC")
    fun getAllPeople(): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%' ORDER BY lastInteraction DESC")
    fun searchPeople(query: String): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE name = :name LIMIT 1")
    suspend fun findPersonByName(name: String): Person?

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getPersonById(id: Int): Person?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Delete
    suspend fun deletePerson(person: Person)

    @Query("SELECT COUNT(*) FROM people")
    suspend fun getPersonCount(): Int
}
