package com.stashapp.shared.util

import com.stashapp.shared.domain.CatalogProduct
import com.stashapp.shared.domain.InventoryRepository
import com.stashapp.shared.domain.MeasurementUnit
import com.stashapp.shared.domain.Quantity
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.math.BigDecimal

/**
 * Utility to ingest Dutch products from the filtered Open Food Facts TSV.
 * Optimized for high throughput and low memory allocation.
 */
class IngestCatalog(private val repository: InventoryRepository) {

    private val quantityRegex = """([\d.,]+)\s*([a-zA-Z]+)""".toRegex()
    private val digitRegex = """(\d+)""".toRegex()

    suspend fun ingestFromStream(
        inputStream: InputStream, 
        totalBytes: Long = 0L,
        onProgress: (suspend (Float) -> Unit)? = null,
        limit: Int = Int.MAX_VALUE
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream), 32768) // Larger buffer
        val chunkLimit = 500 // Sprints of 500 rows
        val currentBatch = mutableListOf<CatalogProduct>()
        
        var count = 0
        var bytesRead = 0L
        var lastReportedPercentage = -1
        
        reader.use { r ->
            var line: String? = r.readLine()
            while (line != null && count < limit) {
                bytesRead += line.length + 1
                
                // SMART PROGRESS: Only report when percentage actually increases
                if (totalBytes > 0 && onProgress != null) {
                    val currentPercentage = (bytesRead * 100 / totalBytes).toInt()
                    if (currentPercentage > lastReportedPercentage) {
                        onProgress(currentPercentage.toFloat() / 100f)
                        lastReportedPercentage = currentPercentage
                    }
                }

                // TURBO PARSING: Pointer-based tab finding (Zero allocation for unused columns)
                try {
                    val firstTab = line.indexOf('\t')
                    if (firstTab != -1) {
                        val ean = line.substring(0, firstTab).trim()
                        
                        // Skip header
                        if (ean.lowercase() == "code" || ean.lowercase() == "ean") {
                            line = r.readLine()
                            continue
                        }

                        val secondTab = line.indexOf('\t', firstTab + 1)
                        if (secondTab != -1) {
                            val name = line.substring(firstTab + 1, secondTab).trim()
                            
                            val thirdTab = line.indexOf('\t', secondTab + 1)
                            val rawQuantity = if (thirdTab != -1) {
                                line.substring(secondTab + 1, thirdTab).trim()
                            } else ""

                            val brand = if (thirdTab != -1) {
                                line.substring(thirdTab + 1).trim().takeIf { it.isNotBlank() && it.lowercase() != "unknown" }
                            } else null

                            if (ean.isNotBlank() && name.isNotBlank() && ean.length >= 8) {
                                currentBatch.add(
                                    CatalogProduct(
                                        ean = ean,
                                        name = name,
                                        brand = brand,
                                        defaultQuantity = parseQuantity(rawQuantity)
                                    )
                                )
                                
                                if (currentBatch.size >= chunkLimit) {
                                    repository.bulkUpsertCatalogProducts(currentBatch)
                                    currentBatch.clear()
                                    // Short, sharp rest to keep the system responsive but fast
                                    kotlinx.coroutines.yield()
                                    kotlinx.coroutines.delay(100)
                                }
                                count++
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore malformed lines to keep moving fast
                }
                
                line = r.readLine()
            }
        }
        
        if (currentBatch.isNotEmpty()) {
            repository.bulkUpsertCatalogProducts(currentBatch)
        }
    }

    private fun parseQuantity(raw: String): Quantity? {
        if (raw.isBlank() || raw.lowercase() == "unknown") return null

        return try {
            val match = quantityRegex.find(raw)
            if (match != null) {
                val amountStr = match.groupValues[1].replace(",", ".")
                val unitStr = match.groupValues[2].lowercase()
                
                val amount = BigDecimal(amountStr)
                val unit = when (unitStr) {
                    "g" -> MeasurementUnit.GRAMS
                    "kg" -> MeasurementUnit.KILOGRAMS
                    "mg" -> MeasurementUnit.MILLIGRAMS
                    "l" -> MeasurementUnit.LITERS
                    "ml" -> MeasurementUnit.MILLILITERS
                    "cl" -> MeasurementUnit.CENTILITERS
                    "oz" -> MeasurementUnit.OUNCES
                    else -> MeasurementUnit.PIECES
                }
                Quantity(amount, unit)
            } else if (raw.contains("pack", ignoreCase = true)) {
                val numMatch = digitRegex.find(raw)
                val amount = numMatch?.value?.toInt()?.toBigDecimal() ?: BigDecimal.ONE
                Quantity(amount, MeasurementUnit.PIECES)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}
