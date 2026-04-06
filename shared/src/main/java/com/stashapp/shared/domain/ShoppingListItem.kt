package com.stashapp.shared.domain

import kotlinx.coroutines.flow.Flow
import java.util.UUID

data class ShoppingListItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val quantity: Quantity? = null,
    val isPurchased: Boolean = false,
    val isRestocked: Boolean = false,
    val catalogEan: String? = null
)

interface ShoppingListRepository {
    fun getItems(): Flow<List<ShoppingListItem>>
    fun getActiveItems(): Flow<List<ShoppingListItem>>
    fun getPurchasedItems(): Flow<List<ShoppingListItem>>
    suspend fun addOrUpdateItem(item: ShoppingListItem)
    suspend fun deleteItem(id: String)
    suspend fun markAsPurchased(id: String, purchased: Boolean)
    suspend fun markAsRestocked(id: String)
}
