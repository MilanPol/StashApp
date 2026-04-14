package com.stashapp.shared.domain.usecase

import com.stashapp.shared.data.parsing.ReceiptLine
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.first
import java.time.Instant

class MatchReceiptUseCase(
    private val catalogRepository: CatalogRepository,
    private val shoppingListRepository: ShoppingListRepository,
    private val inventoryEntryRepository: InventoryEntryRepository
) {
    suspend fun execute(lines: List<ReceiptLine>): ReceiptScanResult {
        // Collect all active shopping list items once
        val shoppingList = shoppingListRepository.getActiveItems().first()
        val inventory = inventoryEntryRepository.getAllEntries().first()
        
        val matches = lines.map { line ->
            // Try to match with Catalog first (more precise identity)
            val catalogMatch = matchWithCatalog(line.name)
            
            // Try to match with Shopping List
            val shoppingMatch = matchWithShoppingList(line.name, shoppingList)

            // Suggestive match with Inventory
            val inventoryMatches = matchWithInventory(line.name, inventory, catalogMatch?.ean)
            
            // Determine confidence (EXACT if names match perfectly after normalization)
            val isExactCatalog = catalogMatch != null && catalogMatch.name.lowercase().trim() == line.name.lowercase().trim()
            val isExactShopping = shoppingMatch != null && shoppingMatch.name.lowercase().trim() == line.name.lowercase().trim()
            val confidence = when {
                isExactCatalog || isExactShopping -> MatchConfidence.EXACT
                catalogMatch != null || shoppingMatch != null -> MatchConfidence.FUZZY
                else -> MatchConfidence.NONE
            }

            ReceiptMatch(
                receiptLine = line,
                matchedShoppingItem = shoppingMatch,
                matchedCatalogProduct = catalogMatch,
                matchedInventoryEntries = inventoryMatches,
                confidence = confidence
            )
        }

        return ReceiptScanResult(lines, matches, Instant.now())
    }

    private suspend fun matchWithCatalog(name: String): CatalogProduct? {
        val results = catalogRepository.searchCatalog(name).first()
        if (results.isEmpty()) return null
        
        val exact = results.find { it.name.lowercase().trim() == name.lowercase().trim() }
        if (exact != null) return exact
        
        return results.maxByOrNull { 
            FuzzyMatcher.tokenOverlap(it.name, name) + (FuzzyMatcher.levenshteinSimilarity(it.name, name) * 0.5) 
        }
    }

    private fun matchWithShoppingList(name: String, items: List<ShoppingListItem>): ShoppingListItem? {
        val exact = items.find { it.name.lowercase().trim() == name.lowercase().trim() }
        if (exact != null) return exact

        val scored = items.map { 
            it to (FuzzyMatcher.tokenOverlap(it.name, name) + FuzzyMatcher.levenshteinSimilarity(it.name, name)) 
        }
        val best = scored.maxByOrNull { it.second }
        return if (best != null && best.second > 0.8) best.first else null
    }

    private fun matchWithInventory(name: String, inventory: List<InventoryEntry>, ean: String?): List<InventoryEntry> {
        return inventory.filter { entry ->
            val eanMatch = ean != null && entry.catalogEan == ean
            val exactName = entry.name.lowercase().trim() == name.lowercase().trim()
            val fuzzy = FuzzyMatcher.tokenOverlap(entry.name, name) > 0.5 || FuzzyMatcher.levenshteinSimilarity(entry.name, name) > 0.7
            eanMatch || exactName || fuzzy
        }
    }
}
