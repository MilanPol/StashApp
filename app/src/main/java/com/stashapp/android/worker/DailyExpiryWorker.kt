package com.stashapp.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.stashapp.android.notifications.NotificationHelper
import com.stashapp.shared.data.SqlDelightInventoryRepository
import com.stashapp.shared.db.StashDatabase
import kotlinx.coroutines.flow.first
import java.time.Instant

class DailyExpiryWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as com.stashapp.android.StashApp
        
        // Skip if we are currently doing a heavy import to avoid ANR/contention
        if (app.isImporting) {
            return Result.retry()
        }

        val repository = app.repository
        val notificationHelper = NotificationHelper(applicationContext)

        // Get only items where alert_at is today or in the past
        val now = Instant.now()
        val entries = repository.getExpiringEntries(now).first()
        val expiringItems = entries.map { it.name }

        if (expiringItems.isNotEmpty()) {
            notificationHelper.showExpirationSummary(expiringItems)
        }

        return Result.success()
    }
}
