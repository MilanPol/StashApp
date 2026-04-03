package com.stashapp.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.stashapp.shared.db.StashDatabase
import com.stashapp.shared.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

class SqlDelightInventoryRepository(
    db: StashDatabase
) : InventoryRepository {

    private val locationQueries = db.storageLocationQueries
    private val categoryQueries = db.categoryQueries
    private val entryQueries = db.inventoryEntryQueries

    override fun getStorageLocations(): Flow<List<StorageLocation>> {
        return locationQueries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { StorageLocation(it.id, it.name, it.icon) }
        }
    }

    override suspend fun addStorageLocation(location: StorageLocation) {
        locationQueries.insert(location.id, location.name, location.icon)
    }

    override suspend fun updateStorageLocation(location: StorageLocation) {
        locationQueries.update(location.name, location.icon, location.id)
    }

    override suspend fun removeStorageLocation(id: String) {
        locationQueries.deleteById(id)
    }

    override fun getCategories(): Flow<List<Category>> {
        return categoryQueries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { Category(it.id, it.name, it.icon, it.default_lead_days?.toInt()) }
        }
    }

    override suspend fun addCategory(category: Category) {
        categoryQueries.insert(category.id, category.name, category.icon, category.defaultLeadDays?.toLong())
    }

    override suspend fun updateCategory(category: Category) {
        categoryQueries.update(category.name, category.icon, category.defaultLeadDays?.toLong(), category.id)
    }

    override suspend fun removeCategory(id: String) {
        categoryQueries.deleteById(id)
    }

    override fun getAllEntries(): Flow<List<InventoryEntry>> {
        return entryQueries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToDomain(it) }
        }
    }

    override suspend fun getEntryById(id: String): InventoryEntry? {
        val row = entryQueries.selectById(id).executeAsOneOrNull()
        return row?.let { mapToDomain(it) }
    }

    override suspend fun addEntry(entry: InventoryEntry) {
        entryQueries.insert(
            id = entry.id,
            name = entry.name,
            quantity_amount = entry.quantity.amount.toPlainString(),
            quantity_unit = entry.quantity.unit.name,
            expiration_date = entry.expirationDate?.date?.toString(),
            shelf_life_sealed_seconds = entry.shelfLife?.sealed?.seconds,
            shelf_life_opened_seconds = entry.shelfLife?.afterOpening?.seconds,
            storage_location_id = entry.storageLocationId,
            category_id = entry.categoryId,
            opened_at = entry.openedAt?.toEpochMilli(),
            alert_at = entry.alertAt?.toEpochMilli(),
            created_at = entry.createdAt.toEpochMilli(),
            updated_at = entry.updatedAt.toEpochMilli()
        )
    }

    override suspend fun updateEntry(entry: InventoryEntry) {
        // We defined insert OR REPLACE for insert, so we can just use addEntry for update
        addEntry(entry)
    }

    override suspend fun removeEntry(id: String) {
        entryQueries.deleteById(id)
    }

    private fun mapToDomain(row: com.stashapp.shared.db.Inventory_entry): InventoryEntry {
        val quantity = Quantity(
            amount = BigDecimal(row.quantity_amount),
            unit = MeasurementUnit.valueOf(row.quantity_unit)
        )
        val expirationDate = row.expiration_date?.let { ExpirationDate(LocalDate.parse(it)) }
        val shelfLife = if (row.shelf_life_sealed_seconds != null && row.shelf_life_opened_seconds != null) {
            ShelfLife(
                sealed = Duration.ofSeconds(row.shelf_life_sealed_seconds),
                afterOpening = Duration.ofSeconds(row.shelf_life_opened_seconds)
            )
        } else null
        
        return InventoryEntry(
            id = row.id,
            name = row.name,
            quantity = quantity,
            expirationDate = expirationDate,
            shelfLife = shelfLife,
            storageLocationId = row.storage_location_id,
            categoryId = row.category_id,
            openedAt = row.opened_at?.let { Instant.ofEpochMilli(it) },
            alertAt = row.alert_at?.let { Instant.ofEpochMilli(it) },
            createdAt = Instant.ofEpochMilli(row.created_at),
            updatedAt = Instant.ofEpochMilli(row.updated_at)
        )
    }
}
