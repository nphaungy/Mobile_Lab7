package com.example.studentmanagementapp.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.studentmanagementapp.data.local.StudentEntity
import com.example.studentmanagementapp.data.repository.StudentRepository
import com.example.studentmanagementapp.data.repository.SyncOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class StatusMessage(val text: String, val isError: Boolean)

private data class ActionResult(
    val outcome: SyncOutcome,
    val successText: String? = null,
    val successIsError: Boolean = false
)

@HiltViewModel
class StudentViewModel @Inject constructor(
    private val repository: StudentRepository
) : ViewModel() {

    val students: StateFlow<List<StudentEntity>> =
        repository.observeStudents()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    var searchQuery by mutableStateOf("")
        private set

    val filteredStudents: StateFlow<List<StudentEntity>> =
        combine(students, snapshotFlow { searchQuery }) { list, query ->
            val q = query.trim()
            if (q.isEmpty()) list
            else list.filter {
                it.studentCode.contains(q, ignoreCase = true) ||
                        it.name.contains(q, ignoreCase = true) ||
                        it.major.contains(q, ignoreCase = true)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var isSyncing by mutableStateOf(false)
        private set
    var isOnline by mutableStateOf<Boolean?>(null)   // null = ยังไม่เคยติดต่อ Server
        private set
    var message by mutableStateOf<StatusMessage?>(null)
        private set
    var selectedStudent by mutableStateOf<StudentEntity?>(null)
        private set

    // บันทึกลง Room ทันที ไม่รอ Server
    fun addStudent(code: String, name: String, major: String) {
        viewModelScope.launch {
            try {
                repository.addStudent(
                    StudentEntity(
                        studentCode = code.trim(),
                        name = name.trim(),
                        major = major.trim()
                    )
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = StatusMessage("บันทึกลงเครื่องไม่สำเร็จ", isError = true)
            }
        }
    }

    // ส่ง PENDING แล้วดึงข้อมูลล่าสุด
    fun syncStudents() {
        runServerAction {
            val push = repository.syncPending()
            if (push.outcome != SyncOutcome.OK) {
                return@runServerAction ActionResult(push.outcome)
            }
            val pull = repository.refreshFromServer()
            ActionResult(
                outcome = pull,
                successText = "Sync สำเร็จ: ส่งขึ้น Server ${push.synced} รายการ" +
                        if (push.failed > 0) " (FAILED ${push.failed} รายการ)" else "",
                successIsError = push.failed > 0
            )
        }
    }

    fun loadStudents() {
        runServerAction { ActionResult(repository.refreshFromServer()) }
    }

    fun deleteStudent(student: StudentEntity) {
        runServerAction {
            val outcome = repository.deleteStudent(student)
            if (outcome == SyncOutcome.OK && selectedStudent?.localId == student.localId) {
                selectedStudent = null
            }
            ActionResult(outcome)
        }
    }

    fun updateStudent(student: StudentEntity) {
        runServerAction {
            val outcome = repository.updateStudent(student)
            if (outcome == SyncOutcome.OK) selectedStudent = null
            ActionResult(outcome)
        }
    }

    fun onSearchQueryChange(query: String) { searchQuery = query }

    fun selectStudent(student: StudentEntity?) { selectedStudent = student }

    // ทำทีละงาน ไม่ให้ซ้อนกัน
    private fun runServerAction(action: suspend () -> ActionResult) {
        if (isSyncing) {
            message = StatusMessage("กำลังติดต่อ Server อยู่ กรุณารอสักครู่แล้วลองใหม่", isError = true)
            return
        }
        viewModelScope.launch {
            isSyncing = true
            message = null
            try {
                report(action())
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                message = StatusMessage("เกิดข้อผิดพลาด: ${e.message}", isError = true)
            } finally {
                isSyncing = false
            }
        }
    }

    private fun report(result: ActionResult) {
        when (result.outcome) {
            SyncOutcome.OK -> {
                isOnline = true
                message = result.successText?.let { StatusMessage(it, result.successIsError) }
            }
            SyncOutcome.OFFLINE -> {
                isOnline = false
                message = StatusMessage(
                    "เชื่อมต่อ Server ไม่ได้: งานที่ต้องใช้ Server ยังไม่ถูกดำเนินการ " +
                            "(รายการที่เพิ่มไว้ในเครื่องยังเป็น PENDING)",
                    isError = true
                )
            }
            SyncOutcome.UNAUTHORIZED -> {
                isOnline = true
                message = StatusMessage(
                    "Token หมดอายุหรือไม่ถูกต้อง กรุณากด Logout แล้ว Login ใหม่",
                    isError = true
                )
            }
            SyncOutcome.SERVER_ERROR -> {
                isOnline = true
                message = StatusMessage("Server ตอบกลับผิดพลาด ลองใหม่อีกครั้ง", isError = true)
            }
        }
    }
}