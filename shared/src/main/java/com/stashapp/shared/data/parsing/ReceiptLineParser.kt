package com.stashapp.shared.data.parsing

import com.stashapp.shared.domain.Quantity
import com.stashapp.shared.domain.MeasurementUnit
import java.math.BigDecimal

data class ReceiptLine(
    val name: String,
    val quantity: Quantity,
    val raw: String
)

object ReceiptLineParser {

    // Quantity pattern: number at end of line before price, or "X x Price", or "Xst", "Xkg" etc.
    private val quantityPattern = Regex("""(\d+(?:[.,]\d+)?)\s*[xX]""", RegexOption.IGNORE_CASE)
    private val unitQuantityPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(st|stk|stuk|pcs|piece|g|gr|gram|kg|kilo|l|ml|ltr)\b""", RegexOption.IGNORE_CASE)
    private val pricePattern = Regex("""\d+[.,]\d{2}""")

    fun parse(lines: List<String>): List<ReceiptLine> {
        return lines
            .filter { isProductLine(it) }
            .map { parseLine(it) }
    }

    private fun isProductLine(text: String): Boolean {
        // Skip header, footer, totals, VAT lines
        val skip = listOf("totaal", "total", "btw", "subtotaal", "wisselgeld",
                          "pinbetaling", "bedankt", "kassabon", "bon nr", "datum", "tijd")
        val lower = text.lowercase()
        return skip.none { lower.contains(it) } && text.trim().length > 2
    }

    private fun parseLine(text: String): ReceiptLine {
        val qtyValue = quantityPattern.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: unitQuantityPattern.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: 1.0
        
        val rawUnit = unitQuantityPattern.find(text)?.groupValues?.get(2)?.lowercase()
        val unit = when (rawUnit) {
            "g", "gr", "gram" -> MeasurementUnit.GRAMS
            "kg", "kilo" -> MeasurementUnit.KILOGRAMS
            "l", "ltr", "liter" -> MeasurementUnit.LITERS
            "ml" -> MeasurementUnit.MILLILITERS
            "st", "stk", "stuk", "pcs", "piece" -> MeasurementUnit.PIECES
            else -> MeasurementUnit.PIECES
        }

        // Clean name: remove quantity prefixes/suffixes and price
        var name = text
            .replace(quantityPattern, "")
            .replace(unitQuantityPattern, "")
            .replace(pricePattern, "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        
        // Strip leading/trailing symbols commonly found on receipts (e.g. A, B icons for VAT)
        name = name.replace(Regex("""^[A-Z]\s+"""), "").trim()

        return ReceiptLine(
            name = name,
            quantity = Quantity(BigDecimal.valueOf(qtyValue), unit),
            raw = text
        )
    }
}
