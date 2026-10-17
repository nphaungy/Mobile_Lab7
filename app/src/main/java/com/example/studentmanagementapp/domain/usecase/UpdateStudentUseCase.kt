package com.example.studentmanagementapp.domain.usecase

import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.model.SyncOutcome
import com.example.studentmanagementapp.domain.repository.StudentRepository
import javax.inject.Inject

class UpdateStudentUseCase @Inject constructor(
    private val repository: StudentRepository
) {
    suspend operator fun invoke(student: Student): SyncOutcome {
        return repository.updateStudent(student)
    }
}
