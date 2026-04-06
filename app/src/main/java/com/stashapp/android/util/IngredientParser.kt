package com.stashapp.android.util

import com.stashapp.shared.domain.MeasurementUnit
import com.stashapp.shared.domain.RecipeIngredient

object IngredientParser {
    
    // Regex to match quantity (including fractions like 1/2 or decimals like 1.5)
    // Matches: Group 1 = Quantity, Group 2 = Unit, Group 3 = Rest (Name/Notes)
    private val ingredientRegex = Regex("""^([\d.,/]+)?\s*([a-zA-Z]+)?\s*(.*)$""")

    // Regex to match common bullets, checkboxes, and list markers (e.g., "▢", "*", "-")
    // NOTE: This does NOT consume the leading number if it's a quantity.
    private val symbolPrefixRegex = Regex("""^[^a-zA-Z\d\s]+""")
    private val numberListPrefixRegex = Regex("""^\d+[.)]\s*""")

    /**
     * Parses a raw line of text (from OCR or manual input) into a RecipeIngredient.
     * 
     * Examples:
     * "125g Bloem" -> Quantity: 125.0, Unit: GRAMS, Name: Bloem
     * "2 stuks eieren" -> Quantity: 2.0, Unit: PIECES, Name: eieren
     * "1/2 ui" -> Quantity: 0.5, Unit: PIECES, Name: ui
     * "Zout naar smaak" -> Quantity: 1.0, Unit: PIECES, Name: Zout naar smaak
     */
    fun parse(line: String, recipeId: String = "", catalogNames: List<String> = emptyList()): RecipeIngredient {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return RecipeIngredient(recipeId = recipeId)

        // 1. Clean common symbols (bullets, checkboxes)
        var cleaned = trimmed.replaceFirst(symbolPrefixRegex, "").trim()
        
        // 2. Clean list markers like "1. " but only if it's followed by MORE numbers or words 
        // (to avoid stripping the actual quantity if it's just a number)
        if (numberListPrefixRegex.find(cleaned) != null && !cleaned.contains(Regex("""^\d+\s+\d+"""))) {
            // If it looks like a list marker and not just a quantity followed by another number
            // This is a bit complex, let's just strip symbols for now as they are the main issue.
        }

        val match = ingredientRegex.find(cleaned)
        if (match == null) return RecipeIngredient(recipeId = recipeId, name = cleanName(cleaned))

        val rawQuantity = match.groups[1]?.value
        val rawUnit = match.groups[2]?.value
        val rawName = match.groups[3]?.value?.trim() ?: ""

        val quantity = parseQuantity(rawQuantity)
        
        // Check if the "unit" part is actually a real unit
        val (unit, wasRealUnit) = parseUnitWithCheck(rawUnit)
        
        // Logic to construct the final name:
        val finalName = when {
            rawName.isNotEmpty() -> smartMatch(rawName, catalogNames)
            !wasRealUnit && rawUnit != null -> smartMatch(rawUnit, catalogNames)
            else -> cleanName(cleaned)
        }

        return RecipeIngredient(
            recipeId = recipeId,
            name = finalName,
            quantity = quantity,
            unit = unit
        )
    }

    /**
     * Cleans an ingredient name of any remaining checkboxes, leading digits, or symbols.
     */
    private fun cleanName(name: String): String {
        // Strip everything that isn't a letter from the start of the string
        return name.replaceFirst(Regex("^[^a-zA-Z]+"), "").trim()
    }

    private fun parseUnitWithCheck(raw: String?): Pair<MeasurementUnit, Boolean> {
        if (raw == null) return MeasurementUnit.PIECES to false
        
        val unit = when (raw.lowercase()) {
            "g", "gr", "gram", "grams" -> MeasurementUnit.GRAMS
            "kg", "kilo", "kilogram", "kilograms" -> MeasurementUnit.KILOGRAMS
            "l", "lt", "liter", "liters" -> MeasurementUnit.LITERS
            "ml", "milliliter", "milliliters" -> MeasurementUnit.MILLILITERS
            "cl", "centiliter", "centiliters" -> MeasurementUnit.CENTILITERS
            "mg", "milligram", "milligrams" -> MeasurementUnit.MILLIGRAMS
            "oz", "ounce", "ounces" -> MeasurementUnit.OUNCES
            "st", "stk", "stuk", "stuks", "pcs", "piece", "pieces" -> MeasurementUnit.PIECES
            else -> null
        }

        return if (unit != null) {
            unit to true
        } else {
            MeasurementUnit.PIECES to false
        }
    }

    private fun parseQuantity(raw: String?): Double {
        if (raw == null) return 1.0
        
        return try {
            if (raw.contains("/")) {
                // Handle fractions like "1/2"
                val parts = raw.split("/")
                if (parts.size == 2) {
                    parts[0].trim().toDouble() / parts[1].trim().toDouble()
                } else 1.0
            } else {
                // Handle decimals with . or ,
                raw.replace(",", ".").toDouble()
            }
        } catch (e: Exception) {
            1.0
        }
    }

    /**
     * Logic to find the best match in the catalog to ensure consistency.
     * If no close match is found, returns the original name.
     */
    private fun smartMatch(name: String, catalogNames: List<String>): String {
        if (catalogNames.isEmpty()) return name
        
        val normalizedInput = name.lowercase().trim()
        
        // 1. Precise match (case insensitive)
        val exactMatch = catalogNames.find { it.lowercase() == normalizedInput }
        if (exactMatch != null) return exactMatch

        // 2. Contains match (e.g., "Grote Eieren" contains "Eieren")
        // Only if the catalog name is a significant part of the input
        val partialMatch = catalogNames.find { normalizedInput.contains(it.lowercase()) && it.length > 3 }
        if (partialMatch != null) return partialMatch

        return name
    }
}
