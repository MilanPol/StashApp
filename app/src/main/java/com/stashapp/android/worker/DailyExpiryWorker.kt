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
        val driver = AndroidSqliteDriver(StashDatabase.Schema, applicationContext, "stashapp.db")
        val database = StashDatabase(driver)
        val repository = SqlDelightInventoryRepository(database)
        val notificationHelper = NotificationHelper(applicationContext)

        // Get all items where alert_at is today or in the past
        val allEntries = repository.getAllEntries().first()
        val now = Instant.now()
        
        val expiringItems = allEntries.filter { entry ->
            val alertAt = entry.alertAt
            alertAt != null && alertAt.isBefore(now)
        }.map { it.name }

        if (expiringItems.isNotEmpty()) {
            notificationHelper.showExpirationSummary(expiringItems)
        }

        return Result.success()
    }
}
