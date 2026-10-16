package com.example.studentmanagementapp.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object SyncScheduler {

    private const val PERIODIC_WORK_NAME = "student-sync"

    // เงื่อนไข: ต้องมี Network ก่อนจึงเริ่มงาน
    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

    // งานครั้งเดียว (ผู้ใช้กดปุ่ม)
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints())
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    // งานทำซ้ำ (ขั้นต่ำ 15 นาที)
    fun schedulePeriodicSync(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(networkConstraints())
            .build()

        // ชื่อเดียว (unique) = มีงานประจำได้ชุดเดียว เรียกซ้ำก็ไม่เพิ่มงาน
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    // ใช้ตอน Logout: เลิกงานประจำ (ไม่มี token ก็จะโดน 401 ทุกรอบ)
    fun cancelPeriodicSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }
}