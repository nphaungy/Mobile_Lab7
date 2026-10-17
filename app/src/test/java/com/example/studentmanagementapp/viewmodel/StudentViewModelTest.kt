package com.example.studentmanagementapp.viewmodel

import com.example.studentmanagementapp.domain.usecase.AddStudentUseCase
import com.example.studentmanagementapp.domain.usecase.DeleteStudentUseCase
import com.example.studentmanagementapp.domain.usecase.FakeStudentRepository
import com.example.studentmanagementapp.domain.usecase.GetStudentsUseCase
import com.example.studentmanagementapp.domain.usecase.RefreshStudentsUseCase
import com.example.studentmanagementapp.domain.usecase.SyncStudentsUseCase
import com.example.studentmanagementapp.domain.usecase.UpdateStudentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentViewModelTest {

    private val repository = FakeStudentRepository()

    // viewModelScope ใช้ Dispatchers.Main ซึ่งไม่มีบน JVM จึงแทนด้วย test dispatcher
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = StudentViewModel(
        getStudentsUseCase = GetStudentsUseCase(repository),
        syncStudentsUseCase = SyncStudentsUseCase(repository),
        addStudentUseCase = AddStudentUseCase(repository),
        refreshStudentsUseCase = RefreshStudentsUseCase(repository),
        updateStudentUseCase = UpdateStudentUseCase(repository),
        deleteStudentUseCase = DeleteStudentUseCase(repository)
    )

    @Test
    fun `students state comes from use case`() = runTest {
        val viewModel = createViewModel()

        // stateIn(WhileSubscribed) ต้องมีผู้ subscribe จึงเริ่มดึงข้อมูล
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.students.collect {}
        }

        assertEquals(1, viewModel.students.value.size)
        assertEquals("สมชาย", viewModel.students.value.first().name)
    }

    @Test
    fun `sync students calls sync use case`() = runTest {
        val viewModel = createViewModel()

        viewModel.syncStudents()

        assertEquals(1, repository.syncPendingCalls)
        assertEquals(true, viewModel.isOnline)
        assertEquals(false, viewModel.isSyncing)
    }
}
