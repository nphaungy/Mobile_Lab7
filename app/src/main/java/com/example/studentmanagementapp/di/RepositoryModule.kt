package com.example.studentmanagementapp.di

import com.example.studentmanagementapp.data.repository.StudentRepositoryImpl
import com.example.studentmanagementapp.domain.repository.StudentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindStudentRepository(
        implementation: StudentRepositoryImpl
    ): StudentRepository
}
