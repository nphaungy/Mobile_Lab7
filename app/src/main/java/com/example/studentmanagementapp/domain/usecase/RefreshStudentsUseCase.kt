package com.example.studentmanagementapp.domain.usecase

import com.example.studentmanagementapp.domain.model.SyncOutcome
import com.example.studentmanagementapp.domain.repository.StudentRepository
import javax.inject.Inject

class RefreshStudentsUseCase @Inject constructor(
    private val repository: StudentRepository
) {
    suspend operator fun invoke(): SyncOutcome {
        return repository.refreshFromServer()
    }
}
