package com.stashapp.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.stashapp.shared.db.StashDatabase
import com.stashapp.shared.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import app.cash.sqldelight.db.SqlDriver

class SqlDelightInventoryRepository(
    private val db: StashDatabase,
    private val driver: SqlDriver
) : InventoryEntryRepository, StorageLocationRepository, CategoryRepository, CatalogRepository {

    private val locationQueries = db.storageLocationQueries
    private val categoryQueries = db.categoryQueries
    private val entryQueries = db.inventoryEntryQueries
    private val catalogQueries = db.catalogProductQueries

    override fun getStorageLocations(parentId: String?): Flow<List<StorageLocation>> {
        val flow = if (parentId == null) {
            locationQueries.selectTopLevel()
        } else {
            locationQueries.selectByParent(parentId)
        }
        return flow.asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { StorageLocation(it.id, it.name, it.icon, it.parent_id) }
        }
    }

    override fun getAllStorageLocations(): Flow<List<StorageLocation>> {
        return locationQueries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { StorageLocation(it.id, it.name, it.icon, it.parent_id) }
        }
    }

    override suspend fun addStorageLocation(location: StorageLocation) {
        locationQueries.insert(location.id, location.name, location.icon, location.parentId)
    }

    override suspend fun updateStorageLocation(location: StorageLocation) {
        locationQueries.update(location.name, location.icon, location.parentId, location.id)
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

    override fun getExpiringEntries(now: Instant): Flow<List<InventoryEntry>> {
        return entryQueries.selectExpiring(
            now = now.toEpochMilli(),
            mapper = { id, name, quantity_amount, quantity_unit, expiration_date, shelf_life_sealed_seconds, shelf_life_opened_seconds, storage_location_id, category_id, opened_at, created_at, updated_at, alert_at ->
                com.stashapp.shared.db.Inventory_entry(
                    id, name, quantity_amount, quantity_unit, expiration_date,
                    shelf_life_sealed_seconds, shelf_life_opened_seconds,
                    storage_location_id, category_id, opened_at, created_at, updated_at, alert_at
                )
            }
        ).asFlow().mapToList(Dispatchers.IO).map { list ->
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

    override fun getProductByEan(ean: String): Flow<CatalogProduct?> {
        return catalogQueries.selectProductByEan(ean).asFlow().mapToOneOrNull(Dispatchers.IO).map { row ->
            row?.let { mapToCatalogDomain(it) }
        }
    }

    override fun searchCatalog(query: String): Flow<List<CatalogProduct>> {
        return catalogQueries.searchProducts(query).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToCatalogDomain(it) }
        }
    }

    private fun mapToCatalogDomain(row: com.stashapp.shared.db.Catalog_product): CatalogProduct {
        return CatalogProduct(
            ean = row.ean,
            name = row.name,
            brand = row.brand,
            defaultQuantity = if (row.default_quantity_amount != null && row.default_quantity_unit != null) {
                Quantity(BigDecimal(row.default_quantity_amount), MeasurementUnit.valueOf(row.default_quantity_unit))
            } else null
        )
    }

    override suspend fun upsertCatalogProduct(product: CatalogProduct) {
        catalogQueries.upsertProduct(
            ean = product.ean,
            name = product.name,
            brand = product.brand,
            default_quantity_amount = product.defaultQuantity?.amount?.toPlainString(),
            default_quantity_unit = product.defaultQuantity?.unit?.name
        )
    }

    override suspend fun bulkUpsertCatalogProducts(products: List<CatalogProduct>) {
        db.transaction {
            products.forEach { product ->
                catalogQueries.upsertProduct(
                    ean = product.ean,
                    name = product.name,
                    brand = product.brand,
                    default_quantity_amount = product.defaultQuantity?.amount?.toPlainString(),
                    default_quantity_unit = product.defaultQuantity?.unit?.name
                )
            }
        }
    }

    override suspend fun linkEntryToProduct(entryId: String, ean: String) {
        catalogQueries.insertMapping(entryId, ean)
    }

    override suspend fun getLinkedEan(entryId: String): String? {
        return catalogQueries.selectMappingByInventoryId(entryId).executeAsOneOrNull()?.ean
    }

    suspend fun executeRawSql(sql: String) {
        driver.execute(null, sql, 0)
    }

    suspend fun setBulkMode(enabled: Boolean) {
        if (enabled) {
            // TURBO: Disable synchronous disk syncs
            executeRawSql("PRAGMA synchronous = OFF;")
        } else {
            // SAFE: Restore synchronous syncs
            executeRawSql("PRAGMA synchronous = NORMAL;")
        }
    }
}
