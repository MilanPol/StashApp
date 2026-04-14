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
        inputStream: InputStream
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream), 65536) // Massive buffer
        var inQuote = false
        val statementBuffer = StringBuilder()
        
        reader.use { r ->
            var line: String? = r.readLine()
            while (line != null) {

                // process all lines, even empty ones, to preserve data fidelity inside quotes
                val chars = line.toCharArray()
                    var i = 0
                    while (i < chars.size) {
                        val c = chars[i]
                        
                        if (c == '\'') {
                            // Handle escaped quote ''
                            if (i + 1 < chars.size && chars[i + 1] == '\'') {
                                statementBuffer.append("''")
                                i += 2 // Skip both quotes
                                continue
                            } else {
                                statementBuffer.append("'")
                                inQuote = !inQuote
                                i++
                                continue
                            }
                        }
                        
                        if (c == ';' && !inQuote) {
                            val sql = statementBuffer.toString().trim()
                            if (sql.isNotEmpty()) {
                                executeSingleStatement(sql)
                            }
                            statementBuffer.setLength(0)
                        } else {
                            statementBuffer.append(c)
                        }
                        i++
                    }
                statementBuffer.append("\n")
                
                // Breathe 
                kotlinx.coroutines.yield()
                line = r.readLine()
            }
            
            // Execute any final trailing statement
            val finalLeftover = statementBuffer.toString().trim()
            if (finalLeftover.isNotEmpty()) {
                executeSingleStatement(finalLeftover)
            }
        }
    }

    private suspend fun executeSingleStatement(statement: String) {
        val trimmed = statement.trim()
        if (trimmed.isEmpty()) return
        
        try {
            executeSql("$trimmed;")
        } catch (e: Exception) {
            // SILENTLY IGNORE: If a manual COMMIT/BEGIN fails because it's redundant/unsupported
            val uppercase = trimmed.uppercase()
            if (uppercase.startsWith("COMMIT") || 
                uppercase.startsWith("BEGIN TRANSACTION") ||
                uppercase.startsWith("ROLLBACK")) {
                Log.d("IngestCatalog", "Skipped redundant transaction command: $trimmed")
            } else {
                Log.w("IngestCatalog", "Skipped malformed SQL: ${trimmed.take(100)}", e)
            }
        }
    }
}

