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
        val repository = app.repository

        return withContext(Dispatchers.IO) {
            try {
                // Shared flag to let other parts of the app know we are busy
                app.isImporting = true
                
                // Total bytes for progress calculation (Dutch filtered slim TSV)
                val totalBytes = 2985757L
                val inputStream = applicationContext.assets.open("dutch_catalog.tsv")
                
                IngestCatalog(repository).ingestFromStream(
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
                app.isImporting = false
            }
        }
    }
}
