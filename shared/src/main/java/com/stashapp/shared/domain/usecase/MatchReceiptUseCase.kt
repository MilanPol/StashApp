package com.stashapp.shared.domain.usecase

import com.stashapp.shared.data.parsing.ReceiptLine
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.first
import java.time.Instant

class MatchReceiptUseCase(
    private val catalogRepository: CatalogRepository,
    private val shoppingListRepository: ShoppingListRepository
) {
    suspend fun execute(lines: List<ReceiptLine>): ReceiptScanResult {
        // Collect all active shopping list items once
        val shoppingList = shoppingListRepository.getActiveItems().first()
        
        val matches = lines.map { line ->
            // Try to match with Catalog first (more precise identity)
            val catalogMatch = matchWithCatalog(line.name)
            
            // Try to match with Shopping List
            val shoppingMatch = matchWithShoppingList(line.name, shoppingList)
            
            // Determine confidence (EXACT if names match perfectly after normalization)
            val confidence = when {
                catalogMatch != null && catalogMatch.name.lowercase().trim() == line.name.lowercase().trim() -> MatchConfidence.EXACT
                shoppingMatch != null && shoppingMatch.name.lowercase().trim() == line.name.lowercase().trim() -> MatchConfidence.EXACT
                catalogMatch != null || shoppingMatch != null -> MatchConfidence.FUZZY
                else -> MatchConfidence.NONE
            }

            ReceiptMatch(
                receiptLine = line,
                matchedShoppingItem = shoppingMatch,
                matchedCatalogProduct = catalogMatch,
                confidence = confidence
            )
        }

        return ReceiptScanResult(lines, matches, Instant.now())
    }

    private suspend fun matchWithCatalog(name: String): CatalogProduct? {
        // Simple search for now, could be improved with fuzzy search libraries
        val results = catalogRepository.searchCatalog(name).first()
        return results.firstOrNull() // Pick the first best match
    }

    private fun matchWithShoppingList(name: String, items: List<ShoppingListItem>): ShoppingListItem? {
        val normalized = name.lowercase().trim()
        return items.find { it.name.lowercase().trim() == normalized }
            ?: items.find { normalized.contains(it.name.lowercase().trim()) }
    }
}
