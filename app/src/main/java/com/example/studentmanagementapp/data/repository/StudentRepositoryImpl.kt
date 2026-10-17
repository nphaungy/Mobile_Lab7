package com.example.studentmanagementapp.data.repository

import android.util.Log
import com.example.studentmanagementapp.data.local.StudentDao
import com.example.studentmanagementapp.data.local.StudentEntity
import com.example.studentmanagementapp.data.local.SyncStatus
import com.example.studentmanagementapp.data.remote.StudentApi
import com.example.studentmanagementapp.data.remote.StudentRequest
import com.example.studentmanagementapp.data.remote.toEntity
import com.example.studentmanagementapp.domain.model.PushResult
import com.example.studentmanagementapp.domain.model.Student
import com.example.studentmanagementapp.domain.model.SyncOutcome
import com.example.studentmanagementapp.domain.repository.StudentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import com.example.studentmanagementapp.domain.model.SyncStatus as DomainSyncStatus

class StudentRepositoryImpl @Inject constructor(
    private val dao: StudentDao,
    private val api: StudentApi
) : StudentRepository {

    // Worker (เบื้องหลัง) กับปุ่มใน UI อาจเรียกพร้อมกันได้ จึงต้องผลัดกันทำ
    // (ได้ผลเพราะ @Singleton ใน RepositoryModule: ทั้งสองฝั่งได้ instance เดียวกัน)
    private val syncMutex = Mutex()

    override fun observeStudents(): Flow<List<Student>> {
        return dao.observeStudents()
            .map { entities ->
                entities.map { entity -> entity.toDomain() }
            }
    }

    override suspend fun addStudent(student: Student) {
        dao.insertStudent(
            StudentEntity(
                studentCode = student.studentCode,
                name = student.name,
                major = student.major,
                syncStatus = SyncStatus.PENDING
            )
        )
    }

    // ---------- ส่งรายการ PENDING ขึ้น Server ----------
    override suspend fun syncPending(): PushResult = syncMutex.withLock {
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
    override suspend fun refreshFromServer(): SyncOutcome = syncMutex.withLock {
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
    override suspend fun deleteStudent(student: Student): SyncOutcome = syncMutex.withLock {
        val current = dao.getByLocalId(student.id)
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
    override suspend fun updateStudent(student: Student): SyncOutcome = syncMutex.withLock {
        // อ่านแถวล่าสุดจาก Room เสมอ กัน object เก่าที่ UI ถืออยู่ทับ serverId/สถานะใหม่
        val current = dao.getByLocalId(student.id)
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

    // id = localId เพราะไม่ซ้ำกันเสมอ (serverId ?: localId อาจชนกันระหว่างแถวที่ sync แล้วกับยังไม่ sync)
    private fun StudentEntity.toDomain() = Student(
        id = localId,
        serverId = serverId,
        studentCode = studentCode,
        name = name,
        major = major,
        syncStatus = DomainSyncStatus.valueOf(syncStatus)
    )

    private companion object {
        const val TAG = "StudentRepositoryImpl"
    }
}
