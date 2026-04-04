package com.stashapp.shared.util

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * High-performance SQL Ingestion engine.
 * Bypasses object mapping by executing pre-optimized SQL scripts directly.
 */
class IngestCatalog(private val executeSql: suspend (String) -> Unit) {

    suspend fun ingestFromSqlStream(
        inputStream: InputStream, 
        totalBytes: Long = 0L,
        onProgress: (suspend (Float) -> Unit)? = null
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream), 65536) // Massive buffer
        var bytesRead = 0L
        var lastReportedPercentage = -1
        
        reader.use { r ->
            var line: String? = r.readLine()
            while (line != null) {
                bytesRead += line.length + 1
                
                // PROGRESS: Only report when percentage actually increases
                if (totalBytes > 0 && onProgress != null) {
                    val currentPercentage = (bytesRead * 100 / totalBytes).toInt().coerceAtMost(100)
                    if (currentPercentage > lastReportedPercentage) {
                        onProgress(currentPercentage.toFloat() / 100f)
                        lastReportedPercentage = currentPercentage
                    }
                }

                // NATIVE EXECUTION: Run the pre-optimized SQL line directly
                if (line.isNotBlank()) {
                    try {
                        executeSql(line)
                    } catch (e: Exception) {
                        Log.w("IngestCatalog", "Skipped malformed SQL line: ${line.take(80)}", e)
                    }
                }
                
                // Breathe between heavy SQL batches
                kotlinx.coroutines.yield()

                line = r.readLine()
            }
        }
    }
}

