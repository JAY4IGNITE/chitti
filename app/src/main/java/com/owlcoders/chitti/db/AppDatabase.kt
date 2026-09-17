package com.owlcoders.chitti.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.owlcoders.chitti.db.dao.*
import com.owlcoders.chitti.db.entities.*

@Database(
    entities = [
        CapturedEvent::class,
        Task::class,
        Deadline::class,
        Reminder::class,
        Commitment::class,
        CalendarEvent::class,
        NotificationEntity::class,
        Document::class,
        Memory::class,
        Person::class,
        AutomationHistory::class,
        ChatHistoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // Legacy DAO (kept for backward compatibility)
    abstract fun eventDao(): CapturedEventDao

    // New DAOs per plan.md §8
    abstract fun taskDao(): TaskDao
    abstract fun memoryDao(): MemoryDao
    abstract fun notificationDao(): NotificationDao
    abstract fun documentDao(): DocumentDao
    abstract fun personDao(): PersonDao
    abstract fun automationHistoryDao(): AutomationHistoryDao
    abstract fun chatHistoryDao(): ChatHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chitti_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
