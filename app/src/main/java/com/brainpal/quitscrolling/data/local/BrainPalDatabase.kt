package com.brainpal.quitscrolling.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.brainpal.quitscrolling.data.local.dao.ScrollEventDao
import com.brainpal.quitscrolling.data.local.entity.ScrollEventEntity

@Database(
    entities = [ScrollEventEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BrainPalDatabase : RoomDatabase() {
    abstract fun scrollEventDao(): ScrollEventDao

    companion object {
        @Volatile
        private var INSTANCE: BrainPalDatabase? = null

        fun getInstance(context: Context): BrainPalDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BrainPalDatabase::class.java,
                    "brainpal_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
