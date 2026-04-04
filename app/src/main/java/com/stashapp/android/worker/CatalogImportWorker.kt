package com.stashapp.android.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.stashapp.android.StashApp
import com.stashapp.android.data.SettingsManager
import com.stashapp.shared.util.IngestCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CatalogImportWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as StashApp
        val settingsManager = SettingsManager(applicationContext)
        return withContext(Dispatchers.IO) {
            val repository = app.repository
            try {
                // Shared flag to let other parts of the app know we are busy
                app.isImporting.set(true)
                
                // TURBO: Enable high-speed ingestion mode
                repository.setBulkMode(true)
                
                // Calculate file size at runtime instead of hardcoding
                val totalBytes = try {
                    applicationContext.assets.openFd("dutch_catalog.sql").use { it.length }
                } catch (_: Exception) { 0L }
                val inputStream = applicationContext.assets.open("dutch_catalog.sql")
                
                IngestCatalog { sql -> repository.executeRawSql(sql) }.ingestFromSqlStream(
                    inputStream = inputStream,
                    totalBytes = totalBytes,
                    onProgress = { progress ->
                        // Communicate percentage back to UI (0-100)
                        val progressData = workDataOf("progress" to progress)
                        setProgress(progressData)
                    }
                )

                settingsManager.setCatalogImported(true)
                Result.success()
            } catch (e: Exception) {
                e.printStackTrace()
                Result.retry()
            } finally {
                // Restoration: Always restore safe mode
                repository.setBulkMode(false)
                app.isImporting.set(false)
            }
        }
    }
}

