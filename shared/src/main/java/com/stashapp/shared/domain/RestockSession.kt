package com.stashapp.shared.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

data class RestockSession(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Instant = Instant.now(),
    val status: RestockSessionStatus = RestockSessionStatus.OPEN
)

enum class RestockSessionStatus {
    OPEN,
    COMPLETED,
    CANCELLED
}

data class RestockItem(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val name: String,
    val quantity: Quantity,
    val shoppingListItemId: String? = null,
    val catalogEan: String? = null,
    val locationId: String? = null,
    val expirationDate: Instant? = null,
    val status: RestockItemStatus = RestockItemStatus.UNASSIGNED
)

enum class RestockItemStatus {
    UNASSIGNED,
    ASSIGNED,
    CONFIRMED
}

interface RestockRepository {
    fun getActiveSession(): Flow<RestockSession?>
    fun getSessionItems(sessionId: String): Flow<List<RestockItem>>
    suspend fun createSession(items: List<RestockItem>): String
    suspend fun addItemToSession(sessionId: String, item: RestockItem)
    suspend fun updateItem(item: RestockItem)
    suspend fun updateItemLocation(itemId: String, locationId: String)
    suspend fun confirmItem(itemId: String, mergeWithId: String? = null)
    suspend fun completeSession(sessionId: String)
    suspend fun cancelSession(sessionId: String)
}
