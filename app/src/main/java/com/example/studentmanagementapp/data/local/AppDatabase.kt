package com.example.studentmanagementapp.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StudentEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun studentDao(): StudentDao
}