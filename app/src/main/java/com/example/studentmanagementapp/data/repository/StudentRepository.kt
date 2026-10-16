package com.example.studentmanagementapp.data.repository

import android.util.Log
import com.example.studentmanagementapp.data.local.StudentDao
import com.example.studentmanagementapp.data.local.StudentEntity
import com.example.studentmanagementapp.data.local.SyncStatus
import com.example.studentmanagementapp.data.remote.StudentApi
import com.example.studentmanagementapp.data.remote.StudentRequest
import com.example.studentmanagementapp.data.remote.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
enum class SyncOutcome { OK, OFFLINE, UNAUTHORIZED, SERVER_ERROR }

data class PushResult(val outcome: SyncOutcome, val synced: Int, val failed: Int)

@Singleton
class StudentRepository @Inject constructor(
    private val dao: StudentDao,
    private val api: StudentApi
) {

    // Worker (เบื้องหลัง) กับปุ่มใน UI อาจเรียกพร้อมกันได้ จึงต้องผลัดกันทำ
    // (ได้ผลเพราะ @Singleton: ทั้งสองฝั่งได้ Repository ตัวเดียวกันจาก Hilt)
    private val syncMutex = Mutex()

    fun observeStudents(): Flow<List<StudentEntity>> {
        return dao.observeStudents()
    }

    suspend fun addStudent(student: StudentEntity) {
        dao.insertStudent(student.copy(syncStatus = SyncStatus.PENDING))
    }

    // ---------- ส่งรายการ PENDING ขึ้น Server ----------
    suspend fun syncPending(): PushResult = syncMutex.withLock {
        val pending = dao.getPendingStudents()
        Log.d(TAG, "syncPending: พบรายการ PENDING ${pending.size} รายการ")

        var synced = 0
        var failed = 0
        var outcome = SyncOutcome.OK

        for (student in pending) {
            try {
                val response = api.createStudent(
                    StudentRequest(
                        studentCode = student.studentCode,
                        name = student.name,
                        major = student.major
                    )
                )
                dao.updateStudent(
                    student.copy(
                        serverId = response.id,
                        syncStatus = SyncStatus.SYNCED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                synced++
            } catch (e: IOException) {
                outcome = SyncOutcome.OFFLINE        // ติดต่อ Server ไม่ได้: หยุดทั้งรอบ
                break
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    outcome = SyncOutcome.UNAUTHORIZED  // token หมดอายุ/ไม่มี: หยุดทั้งรอบ
                    break
                }
                dao.updateStudent(                       // Server ปฏิเสธรายการนี้
                    student.copy(
                        syncStatus = SyncStatus.FAILED,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                failed++
            }
        }

        Log.d(TAG, "syncPending: จบรอบ outcome=$outcome ส่งสำเร็จ=$synced FAILED=$failed")
        PushResult(outcome, synced, failed)
    }

    // ---------- ดึงจาก Server มา merge เข้า Room ----------
    suspend fun refreshFromServer(): SyncOutcome = syncMutex.withLock {
        callServer {
            val remote = api.getStudents()
            val now = System.currentTimeMillis()

            for (dto in remote) {
                val local = dao.getByServerId(dto.id)
                if (local == null) {
                    dao.insertStudent(dto.toEntity())
                } else {
                    dao.updateStudent(
                        local.copy(
                            studentCode = dto.studentCode,
                            name = dto.name,
                            major = dto.major,
                            syncStatus = SyncStatus.SYNCED,
                            updatedAt = now
                        )
                    )
                }
            }
            dao.deleteSyncedNotIn(remote.map { it.id })
        }
    }

    // ---------- ลบ ----------
    suspend fun deleteStudent(student: StudentEntity): SyncOutcome = syncMutex.withLock {
        val current = dao.getByLocalId(student.localId)
        val serverId = current?.serverId
        when {
            current == null -> SyncOutcome.OK
            serverId == null -> {            // ยังไม่เคยขึ้น Server: ลบในเครื่องได้เลย
                dao.deleteStudent(current)
                SyncOutcome.OK
            }
            else -> callServer {
                api.deleteStudent(serverId)
                dao.deleteStudent(current)
            }
        }
    }

    // ---------- แก้ไข ----------
    suspend fun updateStudent(student: StudentEntity): SyncOutcome = syncMutex.withLock {
        // อ่านแถวล่าสุดจาก Room เสมอ กัน object เก่าที่ UI ถืออยู่ทับ serverId/สถานะใหม่
        val current = dao.getByLocalId(student.localId)
        if (current == null) {
            SyncOutcome.OK
        } else {
            val edited = current.copy(
                studentCode = student.studentCode,
                name = student.name,
                major = student.major,
                updatedAt = System.currentTimeMillis()
            )
            val serverId = current.serverId
            if (serverId == null) {          // ยังไม่ขึ้น Server: แก้ในเครื่องแล้วคิวส่งใหม่
                dao.updateStudent(edited.copy(syncStatus = SyncStatus.PENDING))
                SyncOutcome.OK
            } else {
                callServer {
                    api.updateStudent(
                        serverId,
                        StudentRequest(edited.studentCode, edited.name, edited.major)
                    )
                    dao.updateStudent(edited.copy(syncStatus = SyncStatus.SYNCED))
                }
            }
        }
    }

    private suspend fun callServer(block: suspend () -> Unit): SyncOutcome {
        return try {
            block()
            SyncOutcome.OK
        } catch (e: IOException) {
            SyncOutcome.OFFLINE
        } catch (e: HttpException) {
            if (e.code() == 401) SyncOutcome.UNAUTHORIZED else SyncOutcome.SERVER_ERROR
        }
    }

    private companion object {
        const val TAG = "StudentRepository"
    }
}