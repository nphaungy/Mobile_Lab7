package com.example.studentmanagementapp.domain.repository

import com.example.studentmanagementapp.domain.model.PushResult
import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.model.SyncOutcome
import kotlinx.coroutines.flow.Flow

interface StudentRepository {

    fun observeStudents(): Flow<List<Student>>

    suspend fun addStudent(student: Student)

    suspend fun syncPending(): PushResult

    suspend fun refreshFromServer(): SyncOutcome

    suspend fun updateStudent(student: Student): SyncOutcome

    suspend fun deleteStudent(student: Student): SyncOutcome
}
