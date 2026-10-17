package com.example.studentmanagementapp.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.example.studentmanagementapp.domain.model.SyncOutcome
import com.example.studentmanagementapp.domain.usecase.SyncStudentsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncStudentsUseCase: SyncStudentsUseCase
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() เริ่มทำงาน (ครั้งที่ ${runAttemptCount + 1})")

        return try {
            val push = syncStudentsUseCase()

            val result = when (push.outcome) {
                SyncOutcome.OK -> Result.success()
                SyncOutcome.OFFLINE, SyncOutcome.SERVER_ERROR -> Result.retry()
                SyncOutcome.UNAUTHORIZED -> Result.failure()
            }
            Log.d(TAG, "doWork() จบ: outcome=${push.outcome} -> $result")
            result

        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.d(TAG, "doWork() IOException -> retry")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "doWork() ผิดพลาดที่ไม่คาดคิด -> failure", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}