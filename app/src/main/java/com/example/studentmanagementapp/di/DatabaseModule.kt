package com.example.studentmanagementapp.di

import android.content.Context
import androidx.room.Room
import com.example.studentmanagementapp.data.local.AppDatabase
import com.example.studentmanagementapp.data.local.StudentDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "student_database"          // ชื่อเดิมของโปรเจกต์ (ไม่ใช้ "student.db" ตามใบงาน)
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    fun provideStudentDao(database: AppDatabase): StudentDao {
        return database.studentDao()
    }
}