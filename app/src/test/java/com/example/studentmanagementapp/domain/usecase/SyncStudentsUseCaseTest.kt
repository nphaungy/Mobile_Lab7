package com.example.studentmanagementapp.domain.usecase

import com.example.studentmanagementapp.domain.model.SyncOutcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncStudentsUseCaseTest {

    @Test
    fun `sync students calls repository sync`() = runTest {

        val repository = FakeStudentRepository()
        val useCase = SyncStudentsUseCase(repository)

        val result = useCase()

        assertEquals(1, repository.syncPendingCalls)
        assertEquals(SyncOutcome.OK, result.outcome)
    }
}
