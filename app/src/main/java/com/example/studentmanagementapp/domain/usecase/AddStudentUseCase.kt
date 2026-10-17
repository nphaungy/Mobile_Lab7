package com.example.studentmanagementapp.domain.usecase

import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.repository.StudentRepository
import javax.inject.Inject

class AddStudentUseCase @Inject constructor(
    private val repository: StudentRepository
) {
    suspend operator fun invoke(student: Student) {
        repository.addStudent(student)
    }
}
