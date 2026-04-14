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

    fun isProductLine(text: String): Boolean {
        val trimmed = text.trim()
        val lower = trimmed.lowercase()

        // Skip header, footer, totals, VAT lines
        val skip = listOf("totaal", "total", "btw", "subtotaal", "wisselgeld",
                          "pinbetaling", "bedankt", "kassabon", "bon nr", "datum", "tijd")
        if (skip.any { lower.contains(it) }) return false
        
        // 1. Too short (single char, symbols)
        if (trimmed.length < 3) return false
        
        // 2. Pure numbers / prices / dates
        if (trimmed.matches(Regex("""^[\d.,€$£\-/: ]+$"""))) return false
        
        // 3. Must contain at least one letter sequence of 2+ chars
        if (!trimmed.contains(Regex("""[a-zA-ZáàâäéèêëíìîïóòôöúùûüñçÄÖÜß]{2,}"""))) return false
        
        // 4. Reject lines that are mostly digits (>70% digits)
        val digitRatio = trimmed.count { it.isDigit() }.toDouble() / trimmed.length
        if (digitRatio > 0.7) return false
        
        // 5. Reject lines that look like standalone timestamps (HH:MM or HH:MM:SS)
        if (trimmed.matches(Regex("""^\d{1,2}:\d{2}(:\d{2})?$"""))) return false
        
        // 6. Reject lines with 4+ consecutive digits UNLESS they contain a measurement unit
        val hasMeasurementUnit = trimmed.contains(
            Regex("""\d+(g|gr|gram|kg|kilo|l|ml|ltr|cl|oz|st|stk|stuk|pcs)\b""", RegexOption.IGNORE_CASE)
        )
        if (!hasMeasurementUnit && trimmed.contains(Regex("""\d{4,}"""))) return false
        
        return true
    }

    private fun parseLine(text: String): ReceiptLine {
        val qtyValue = quantityPattern.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: unitQuantityPattern.find(text)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?: 1.0
        
        val rawUnit = unitQuantityPattern.find(text)?.groupValues?.get(2)?.lowercase()
        val unit = when (rawUnit) {
            "g", "gr", "gram" -> MeasurementUnit.GRAMS
            "kg", "kilo" -> MeasurementUnit.KILOGRAMS
            "l", "ltr", "liter", "liters" -> MeasurementUnit.LITERS
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
