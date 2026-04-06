package com.stashapp.shared.domain

import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.math.BigDecimal
import java.time.temporal.ChronoUnit

enum class MeasurementUnit {
    LITERS, MILLILITERS, CENTILITERS, KILOGRAMS, GRAMS, MILLIGRAMS, OUNCES, PIECES
}

data class Quantity(
    val amount: BigDecimal,
    val unit: MeasurementUnit
) {
    fun subtract(amountToSubtract: BigDecimal): Quantity {
        val newAmount = amount - amountToSubtract
        return Quantity(if (newAmount < BigDecimal.ZERO) BigDecimal.ZERO else newAmount, unit)
    }

    val isEmpty: Boolean get() = amount <= BigDecimal.ZERO
}

data class ShelfLife(
    val sealed: Duration,
    val afterOpening: Duration
) {
    fun remaining(openedAt: Instant, currentTime: Instant): Duration {
        val elapsedSinceOpening = Duration.between(openedAt, currentTime)
        val remaining = afterOpening.minus(elapsedSinceOpening)
        return if (remaining.isNegative) Duration.ZERO else remaining
    }
}

data class ExpirationDate(
    val date: LocalDate
) {
    fun isExpired(currentDate: LocalDate): Boolean = currentDate.isAfter(date)
    fun daysRemaining(currentDate: LocalDate): Long = ChronoUnit.DAYS.between(currentDate, date)
}

data class StorageLocation(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "",
    val parentId: String? = null
)

data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "",
    val defaultLeadDays: Int? = null
)

data class InventoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val quantity: Quantity = Quantity(BigDecimal.ZERO, MeasurementUnit.PIECES),
    val expirationDate: ExpirationDate? = null,
    val shelfLife: ShelfLife? = null,
    val storageLocationId: String? = null,
    val categoryId: String? = null,
    val openedAt: Instant? = null,
    val alertAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
) {
    fun open(time: Instant): InventoryEntry {
        return if (openedAt == null) this.copy(openedAt = time, updatedAt = time) else this
    }

    fun consume(amount: BigDecimal, time: Instant): InventoryEntry {
        val newOpenedAt = openedAt ?: time
        return this.copy(
            quantity = quantity.subtract(amount),
            openedAt = newOpenedAt,
            updatedAt = time
        )
    }

    fun isExpired(currentDate: LocalDate): Boolean {
        return expirationDate?.isExpired(currentDate) == true
    }

    val isOpen: Boolean get() = openedAt != null
}

data class CatalogProduct(
    val ean: String,
    val name: String,
    val brand: String? = null,
    val defaultQuantity: Quantity? = null
)

interface InventoryEntryRepository {
    fun getAllEntries(): Flow<List<InventoryEntry>>
    fun getExpiringEntries(now: Instant): Flow<List<InventoryEntry>>
    suspend fun getEntryById(id: String): InventoryEntry?
    suspend fun addEntry(entry: InventoryEntry)
    suspend fun updateEntry(entry: InventoryEntry)
    suspend fun removeEntry(id: String)
    suspend fun mergeEntries(sourceId: String, targetId: String)
}

interface StorageLocationRepository {
    fun getStorageLocations(parentId: String? = null): Flow<List<StorageLocation>>
    fun getAllStorageLocations(): Flow<List<StorageLocation>>
    suspend fun addStorageLocation(location: StorageLocation)
    suspend fun updateStorageLocation(location: StorageLocation)
    suspend fun removeStorageLocation(id: String)
}

interface CategoryRepository {
    fun getCategories(): Flow<List<Category>>
    suspend fun addCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun removeCategory(id: String)
}

interface CatalogRepository {
    fun getProductByEan(ean: String): Flow<CatalogProduct?>
    fun searchCatalog(query: String): Flow<List<CatalogProduct>>
    suspend fun upsertCatalogProduct(product: CatalogProduct)
    suspend fun bulkUpsertCatalogProducts(products: List<CatalogProduct>)
    suspend fun linkEntryToProduct(entryId: String, ean: String)
    suspend fun getLinkedEan(entryId: String): String?
}
