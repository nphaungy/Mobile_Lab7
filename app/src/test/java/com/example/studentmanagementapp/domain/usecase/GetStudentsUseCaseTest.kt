package com.example.studentmanagementapp.domain.usecase

import com.example.studentmanagementapp.domain.model.PushResult
import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.model.SyncOutcome
import com.example.studentmanagementapp.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeStudentRepository : StudentRepository {

    private val students =
        MutableStateFlow(
            listOf(
                Student(
                    id = 1,
                    studentCode = "66001",
                    name = "สมชาย",
                    major = "IT"
                )
            )
        )

    override fun observeStudents(): Flow<List<Student>> {
        return students
    }

    override suspend fun addStudent(student: Student) {
        students.value =
            students.value + student
    }

    // นับจำนวนครั้งที่ถูกเรียก ใช้ตรวจว่า Use Case / ViewModel เรียก Sync จริง
    var syncPendingCalls = 0
        private set

    override suspend fun syncPending(): PushResult {
        // Fake ไม่ต้องเรียก API จริง
        syncPendingCalls++
        return PushResult(SyncOutcome.OK, synced = 0, failed = 0)
    }

    override suspend fun refreshFromServer(): SyncOutcome = SyncOutcome.OK

    override suspend fun updateStudent(student: Student): SyncOutcome = SyncOutcome.OK

    override suspend fun deleteStudent(student: Student): SyncOutcome = SyncOutcome.OK
}

class GetStudentsUseCaseTest {

    @Test
    fun `get students returns student list`() = runTest {

        val repository = FakeStudentRepository()
        val useCase = GetStudentsUseCase(repository)

        val result = useCase().first()

        assertEquals(1, result.size)
        assertEquals("สมชาย", result.first().name)
    }
}
