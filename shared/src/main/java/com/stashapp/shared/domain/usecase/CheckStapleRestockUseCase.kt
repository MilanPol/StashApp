package com.stashapp.shared.domain.usecase

import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.first
import java.math.BigDecimal

class CheckStapleRestockUseCase(
    private val inventoryEntryRepository: InventoryEntryRepository,
    private val shoppingListRepository: ShoppingListRepository
) {
    suspend fun execute(entry: InventoryEntry) {
        if (!entry.isStaple || entry.stapleMinimum == null) return

        val allEntries = inventoryEntryRepository.getAllEntries().first()
        
        // Aggregate all "similar" items to check total stock
        val similarEntries = allEntries.filter { 
            val nameMatch = it.name.trim().equals(entry.name.trim(), ignoreCase = true)
            val eanMatch = entry.catalogEan != null && entry.catalogEan == it.catalogEan
            nameMatch || eanMatch
        }

        val totalQuantityAmount = similarEntries.fold(BigDecimal.ZERO) { acc, e ->
            // For now only sum if units match to avoid complex conversion
            if (e.quantity.unit == entry.quantity.unit) acc + e.quantity.amount else acc
        }

        val threshold = entry.stapleMinimum ?: BigDecimal.ZERO
        
        if (totalQuantityAmount <= threshold) {
            val deficit = threshold - totalQuantityAmount
            
            // If we have some stock but haven't hit the threshold, add the gap.
            // If we have NO stock, add the full threshold.
            val restockAmount = if (deficit <= BigDecimal.ZERO) {
                 if (totalQuantityAmount <= BigDecimal.ZERO) {
                     if (threshold > BigDecimal.ZERO) threshold else BigDecimal.ONE
                 } else return
            } else deficit

            val activeItems = shoppingListRepository.getActiveItems().first()
            val alreadyListed = activeItems.any { 
                it.name.trim().equals(entry.name.trim(), ignoreCase = true)
            }

            if (!alreadyListed) {
                shoppingListRepository.addOrUpdateItem(
                    ShoppingListItem(
                        name = entry.name,
                        quantity = Quantity(restockAmount, entry.quantity.unit),
                        isPurchased = false,
                        isRestocked = false
                    )
                )
            }
        }
    }
}
