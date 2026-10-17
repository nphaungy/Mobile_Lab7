package com.example.studentmanagementapp.domain.usecase

import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetStudentsUseCase @Inject constructor(
    private val repository: StudentRepository
) {
    operator fun invoke(): Flow<List<Student>> {
        return repository.observeStudents()
    }
}
