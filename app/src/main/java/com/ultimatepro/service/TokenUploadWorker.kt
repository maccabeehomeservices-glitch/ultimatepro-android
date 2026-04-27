package com.ultimatepro.service

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.ultimatepro.data.repository.CrmRepository
import com.ultimatepro.data.repository.Result
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class TokenUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: CrmRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val token = inputData.getString(KEY_TOKEN) ?: return Result.failure()
        val deviceInfo = inputData.getString(KEY_DEVICE_INFO)
        return when (repo.registerFcmToken(token, deviceInfo)) {
            is com.ultimatepro.data.repository.Result.Success -> Result.success()
            is com.ultimatepro.data.repository.Result.Error   -> Result.retry()
        }
    }

    companion object {
        const val KEY_TOKEN       = "token"
        const val KEY_DEVICE_INFO = "device_info"

        fun enqueue(context: Context, token: String) {
            val data = workDataOf(KEY_TOKEN to token)
            val request = OneTimeWorkRequestBuilder<TokenUploadWorker>()
                .setInputData(data)
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
