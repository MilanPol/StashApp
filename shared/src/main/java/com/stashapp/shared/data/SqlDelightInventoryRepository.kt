package com.stashapp.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.stashapp.shared.db.StashDatabase
import com.stashapp.shared.domain.*
import com.stashapp.shared.domain.RestockItemStatus
import com.stashapp.shared.domain.RestockSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import app.cash.sqldelight.db.SqlDriver

class SqlDelightInventoryRepository(
    private val db: StashDatabase,
    private val driver: SqlDriver
) : InventoryEntryRepository, StorageLocationRepository, CategoryRepository, CatalogRepository, ShoppingListRepository, RestockRepository {

    private val locationQueries = db.storageLocationQueries
    private val categoryQueries = db.categoryQueries
    private val entryQueries = db.inventoryEntryQueries
    private val catalogQueries = db.catalogProductQueries
    private val shoppingQueries = db.shoppingListItemQueries
    private val restockQueries = db.restockSessionQueries

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
            mapper = { id, name, quantity_amount, quantity_unit, expiration_date, shelf_life_sealed_seconds, shelf_life_opened_seconds, storage_location_id, category_id, opened_at, created_at, updated_at, alert_at, is_staple, staple_minimum ->
                com.stashapp.shared.db.Inventory_entry(
                    id, name, quantity_amount, quantity_unit, expiration_date,
                    shelf_life_sealed_seconds, shelf_life_opened_seconds,
                    storage_location_id, category_id, opened_at, created_at, updated_at, alert_at, is_staple, staple_minimum
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
            is_staple = if (entry.isStaple) 1L else 0L,
            staple_minimum = entry.stapleMinimum?.toPlainString(),
            created_at = entry.createdAt.toEpochMilli(),
            updated_at = entry.updatedAt.toEpochMilli()
        )

        // Handle Catalog Mapping within the transaction if called from db.transaction
        entry.catalogEan?.let { ean ->
            catalogQueries.insertMapping(entry.id, ean)
        }
        
        // SYNC STAPLE RULES: Apply this item's staple settings to all similar items
        val minString = entry.stapleMinimum?.toPlainString()
        val isStapleLong = if (entry.isStaple) 1L else 0L
        val now = Instant.now().toEpochMilli()
        entryQueries.updateStapleRuleByName(isStapleLong, minString, now, entry.name)
        entry.catalogEan?.let { ean ->
            entryQueries.updateStapleRuleByEan(isStapleLong, minString, now, ean)
        }
    }

    override suspend fun updateEntry(entry: InventoryEntry) {
        // We defined insert OR REPLACE for insert, so we can just use addEntry for update
        addEntry(entry)
    }

    override suspend fun removeEntry(id: String) {
        entryQueries.deleteById(id)
    }

    override suspend fun mergeEntries(sourceId: String, targetId: String) {
        db.transaction {
            val source = entryQueries.selectById(sourceId).executeAsOneOrNull()
            val target = entryQueries.selectById(targetId).executeAsOneOrNull()

            if (source != null && target != null) {
                val sourceAmount = BigDecimal(source.quantity_amount)
                val targetAmount = BigDecimal(target.quantity_amount)
                val newAmount = targetAmount.add(sourceAmount)

                // Update target quantity and timestamp
                entryQueries.updateQuantity(
                    quantity_amount = newAmount.toPlainString(),
                    updated_at = Instant.now().toEpochMilli(),
                    id = targetId
                )

                // If source has mapping and target doesn't, move it
                val sourceMapping = catalogQueries.selectMappingByInventoryId(sourceId).executeAsOneOrNull()
                val targetMapping = catalogQueries.selectMappingByInventoryId(targetId).executeAsOneOrNull()
                if (sourceMapping != null && targetMapping == null) {
                    catalogQueries.insertMapping(targetId, sourceMapping.ean)
                }

                // Delete source entry (mapping will cascade delete)
                entryQueries.deleteById(sourceId)
            }
        }
    }

    override suspend fun findEntryByName(name: String): InventoryEntry? {
        return entryQueries.selectByName(name).executeAsOneOrNull()?.let { mapToDomain(it) }
    }

    override suspend fun findEntryByCatalogEan(ean: String): InventoryEntry? {
        return entryQueries.selectByCatalogEan(ean).executeAsOneOrNull()?.let { mapToDomain(it) }
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
        
        val ean = catalogQueries.selectMappingByInventoryId(row.id).executeAsOneOrNull()?.ean
        
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
            isStaple = row.is_staple == 1L,
            stapleMinimum = row.staple_minimum?.let { BigDecimal(it) },
            catalogEan = ean,
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

    // ShoppingListRepository Implementation
    override fun getItems(): Flow<List<ShoppingListItem>> {
        return shoppingQueries.selectAll().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToShoppingDomain(it) }
        }
    }

    override fun getActiveItems(): Flow<List<ShoppingListItem>> {
        return shoppingQueries.selectActive().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToShoppingDomain(it) }
        }
    }

    override fun getPurchasedItems(): Flow<List<ShoppingListItem>> {
        return shoppingQueries.selectPurchased().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToShoppingDomain(it) }
        }
    }

    override suspend fun addOrUpdateItem(item: ShoppingListItem) {
        shoppingQueries.insert(
            id = item.id,
            name = item.name,
            quantity_amount = item.quantity?.amount?.toPlainString(),
            quantity_unit = item.quantity?.unit?.name,
            is_purchased = if (item.isPurchased) 1L else 0L,
            is_restocked = if (item.isRestocked) 1L else 0L,
            catalog_ean = item.catalogEan
        )
    }

    override suspend fun deleteItem(id: String) {
        shoppingQueries.deleteById(id)
    }

    override suspend fun markAsPurchased(id: String, purchased: Boolean) {
        shoppingQueries.updatePurchased(if (purchased) 1L else 0L, id)
    }

    override suspend fun markAsRestocked(id: String) {
        shoppingQueries.updateRestocked(id)
    }

    private fun mapToShoppingDomain(row: com.stashapp.shared.db.Shopping_list_item): ShoppingListItem {
        val qty = if (row.quantity_amount != null && row.quantity_unit != null) {
            Quantity(BigDecimal(row.quantity_amount), MeasurementUnit.valueOf(row.quantity_unit))
        } else null
        return ShoppingListItem(
            id = row.id,
            name = row.name,
            quantity = qty,
            isPurchased = row.is_purchased == 1L,
            isRestocked = row.is_restocked == 1L,
            catalogEan = row.catalog_ean
        )
    }

    // RestockRepository Implementation
    override fun getActiveSession(): Flow<RestockSession?> {
        return restockQueries.selectActiveSession().asFlow().mapToOneOrNull(Dispatchers.IO).map { row ->
            row?.let { RestockSession(it.id, Instant.ofEpochMilli(it.created_at), RestockSessionStatus.valueOf(it.status)) }
        }
    }

    override fun getSessionItems(sessionId: String): Flow<List<RestockItem>> {
        return restockQueries.selectItemsBySession(sessionId).asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { mapToRestockItemDomain(it) }
        }
    }

    override suspend fun createSession(items: List<RestockItem>): String {
        val sessionId = UUID.randomUUID().toString()
        db.transaction {
            restockQueries.insertSession(sessionId, Instant.now().toEpochMilli(), RestockSessionStatus.OPEN.name)
            items.forEach { item ->
                restockQueries.insertItem(
                    id = item.id,
                    session_id = sessionId,
                    name = item.name,
                    quantity_amount = item.quantity.amount.toPlainString(),
                    quantity_unit = item.quantity.unit.name,
                    shopping_list_item_id = item.shoppingListItemId,
                    catalog_ean = item.catalogEan,
                    location_id = item.locationId,
                    expiration_date = item.expirationDate?.toEpochMilli(),
                    status = item.status.name
                )
            }
        }
        return sessionId
    }

    override suspend fun addItemToSession(sessionId: String, item: RestockItem) {
        restockQueries.insertItem(
            id = item.id,
            session_id = sessionId,
            name = item.name,
            quantity_amount = item.quantity.amount.toPlainString(),
            quantity_unit = item.quantity.unit.name,
            shopping_list_item_id = item.shoppingListItemId,
            catalog_ean = item.catalogEan,
            location_id = item.locationId,
            expiration_date = item.expirationDate?.toEpochMilli(),
            status = item.status.name
        )
    }

    override suspend fun updateItem(item: RestockItem) {
        restockQueries.updateItemDetails(
            name = item.name,
            quantity_amount = item.quantity.amount.toPlainString(),
            quantity_unit = item.quantity.unit.name,
            expiration_date = item.expirationDate?.toEpochMilli(),
            id = item.id
        )
    }

    override suspend fun updateItemLocation(itemId: String, locationId: String) {
        restockQueries.updateItemLocation(locationId, itemId)
    }

    override suspend fun confirmItem(itemId: String, mergeWithId: String?) {
        db.transaction {
            val itemRaw = restockQueries.selectItemById(itemId).executeAsOneOrNull() ?: return@transaction
            val item = mapToRestockItemDomain(itemRaw)
            
            if (mergeWithId != null) {
                // Merge into existing entry
                val target = entryQueries.selectById(mergeWithId).executeAsOneOrNull() ?: return@transaction
                val currentAmount = BigDecimal(target.quantity_amount)
                val newAmount = currentAmount.add(item.quantity.amount)
                
                entryQueries.updateQuantity(
                    quantity_amount = newAmount.toPlainString(),
                    updated_at = Instant.now().toEpochMilli(),
                    id = mergeWithId
                )
            } else {
                // Create new entry
                val entryId = UUID.randomUUID().toString()
                val now = Instant.now().toEpochMilli()

                // Inherit settings from an existing entry with the same EAN if available
                val existingSameEan = item.catalogEan?.let { ean ->
                    entryQueries.selectByCatalogEan(ean).executeAsList().firstOrNull()
                }

                entryQueries.insert(
                    id = entryId,
                    name = item.name,
                    quantity_amount = item.quantity.amount.toPlainString(),
                    quantity_unit = item.quantity.unit.name,
                    expiration_date = item.expirationDate?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()?.toString(),
                    shelf_life_sealed_seconds = existingSameEan?.shelf_life_sealed_seconds,
                    shelf_life_opened_seconds = existingSameEan?.shelf_life_opened_seconds,
                    storage_location_id = item.locationId,
                    category_id = existingSameEan?.category_id,
                    opened_at = null,
                    alert_at = existingSameEan?.alert_at, // Consider recalculating based on expiration date
                    is_staple = existingSameEan?.is_staple ?: 0L,
                    staple_minimum = existingSameEan?.staple_minimum,
                    created_at = now,
                    updated_at = now
                )
                
                if (item.catalogEan != null) {
                    catalogQueries.insertMapping(entryId, item.catalogEan)
                }
            }

            // Mark shopping list item as restocked if from list
            if (item.shoppingListItemId != null) {
                shoppingQueries.updateRestocked(item.shoppingListItemId)
            }

            // Mark restock item as confirmed
            restockQueries.updateItemStatus(RestockItemStatus.CONFIRMED.name, itemId)
        }
    }

    override suspend fun completeSession(sessionId: String) {
        restockQueries.updateSessionStatus(RestockSessionStatus.COMPLETED.name, sessionId)
    }

    override suspend fun cancelSession(sessionId: String) {
        restockQueries.updateSessionStatus(RestockSessionStatus.CANCELLED.name, sessionId)
    }

    private fun mapToRestockItemDomain(row: com.stashapp.shared.db.Restock_item): RestockItem {
        return RestockItem(
            id = row.id,
            sessionId = row.session_id,
            name = row.name,
            quantity = Quantity(BigDecimal(row.quantity_amount), MeasurementUnit.valueOf(row.quantity_unit)),
            shoppingListItemId = row.shopping_list_item_id,
            catalogEan = row.catalog_ean,
            locationId = row.location_id,
            expirationDate = row.expiration_date?.let { Instant.ofEpochMilli(it) },
            status = RestockItemStatus.valueOf(row.status)
        )
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
