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
 */
class IngestCatalog(private val repository: InventoryRepository) {

    suspend fun ingestFromStream(
        inputStream: InputStream, 
        totalBytes: Long = 0L,
        onProgress: ((Float) -> Unit)? = null,
        minColumns: Int = 19,
        limit: Int = Int.MAX_VALUE
    ) {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val chunkLimit = 500
        val currentBatch = mutableListOf<CatalogProduct>()
        
        var count = 0
        var bytesRead = 0L
        reader.use { r ->
            var line: String? = r.readLine()
            while (line != null && count < limit) {
                bytesRead += line.length + 1 // +1 for the newline character
                
                if (totalBytes > 0 && onProgress != null && count % 500 == 0) {
                    onProgress(bytesRead.toFloat() / totalBytes.toFloat())
                }

                val columns = line.split("\t")
                if (columns.size >= 2) {
                    val ean = columns[0].trim()
                    
                    // Skip header row
                    if (ean.lowercase() == "code" || ean.lowercase() == "ean") {
                        line = r.readLine()
                        continue
                    }
                    val name = columns[1].trim()
                    val brand = columns[3].trim().takeIf { it.isNotBlank() && it.lowercase() != "unknown" }
                    val rawQuantity = columns[2].trim()

                    if (ean.isNotBlank() && name.isNotBlank() && (ean.length >= 8)) {
                        val defaultQuantity = parseQuantity(rawQuantity)
                        
                        currentBatch.add(
                            CatalogProduct(
                                ean = ean,
                                name = name,
                                brand = brand,
                                defaultQuantity = defaultQuantity
                            )
                        )
                        
                        if (currentBatch.size >= chunkLimit) {
                            repository.bulkUpsertCatalogProducts(currentBatch.toList())
                            currentBatch.clear()
                        }
                        count++
                    }
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
            // Very basic matching: "500 g" or "1 kg" or "1.5 l"
            val regex = """([\d.,]+)\s*([a-zA-Z]+)""".toRegex()
            val match = regex.find(raw)
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
                // Try extracting number from "6 pack":
                val numMatch = """(\d+)""".toRegex().find(raw)
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
