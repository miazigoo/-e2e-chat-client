package com.example.securechatapp.data.background

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class OutboxDrainWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val dependencies = EntryPointAccessors.fromApplication(
            applicationContext,
            BackgroundWorkerEntryPoint::class.java,
        )

        val session = dependencies.sessionLocalDataSource().getSessionSnapshot()
        if (session?.accessToken.isNullOrBlank()) {
            return Result.success()
        }

        return runCatching {
            dependencies.outboxDispatcher().recoverStuckMessages()
            dependencies.outboxDispatcher().drainAll()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "secure_chat_outbox_drain"
    }
}
