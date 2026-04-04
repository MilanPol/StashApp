package com.stashapp.shared.data

import com.stashapp.shared.domain.InventoryEntry
import com.stashapp.shared.domain.InventoryRepository
import com.stashapp.shared.domain.StorageLocation
import com.stashapp.shared.domain.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import com.stashapp.shared.domain.CatalogProduct

class InMemoryInventoryRepository : InventoryRepository {
    private val _locations = MutableStateFlow<List<StorageLocation>>(listOf(
        StorageLocation(name = "Fridge", icon = "❄️"),
        StorageLocation(name = "Freezer", icon = "🧊"),
        StorageLocation(name = "Pantry", icon = "🥫")
    ))

    private val _categories = MutableStateFlow<List<Category>>(listOf(
        Category(name = "Dairy", icon = "🥛", defaultLeadDays = 2),
        Category(name = "Produce", icon = "🍎", defaultLeadDays = 1),
        Category(name = "Pantry Staples", icon = "🍚", defaultLeadDays = 7)
    ))

    private val _entries = MutableStateFlow<List<InventoryEntry>>(emptyList())

    override fun getAllEntries(): Flow<List<InventoryEntry>> {
        return _entries.asStateFlow()
    }

    override fun getExpiringEntries(now: java.time.Instant): Flow<List<com.stashapp.shared.domain.InventoryEntry>> {
        return _entries.map { list ->
            list.filter { it.alertAt != null && !it.alertAt.isAfter(now) }
                .sortedBy { it.alertAt }
        }
    }

    override suspend fun getEntryById(id: String): InventoryEntry? {
        return _entries.value.find { it.id == id }
    }

    override suspend fun addEntry(entry: InventoryEntry) {
        _entries.update { it + entry }
    }

    override suspend fun updateEntry(entry: InventoryEntry) {
        _entries.update { list ->
            list.map { if (it.id == entry.id) entry else it }
        }
    }

    override suspend fun removeEntry(id: String) {
        _entries.update { list ->
            list.filterNot { it.id == id }
        }
    }

    override fun getStorageLocations(parentId: String?): Flow<List<StorageLocation>> {
        return _locations.map { list ->
            list.filter { it.parentId == parentId }.sortedBy { it.name }
        }
    }

    override suspend fun addStorageLocation(location: StorageLocation) {
        _locations.update { it + location }
    }

    override suspend fun updateStorageLocation(location: StorageLocation) {
        _locations.update { list ->
            list.map { if (it.id == location.id) location else it }
        }
    }

    override suspend fun removeStorageLocation(id: String) {
        _locations.update { list ->
            list.filterNot { it.id == id }
        }
    }
    
    override fun getCategories(): Flow<List<Category>> = _categories.asStateFlow()

    override suspend fun addCategory(category: Category) {
        _categories.update { it + category }
    }

    override suspend fun updateCategory(category: Category) {
        _categories.update { list ->
            list.map { if (it.id == category.id) category else it }
        }
    }

    override suspend fun removeCategory(id: String) {
        _categories.update { list ->
            list.filterNot { it.id == id }
        }
    }

    // Catalog Implementation
    private val _catalog = MutableStateFlow<Map<String, CatalogProduct>>(emptyMap())
    private val _mappings = MutableStateFlow<Map<String, String>>(emptyMap())

    override fun getProductByEan(ean: String): Flow<CatalogProduct?> {
        return _catalog.map { it[ean] }
    }

    override fun searchCatalog(query: String): Flow<List<CatalogProduct>> {
        return _catalog.map { catalog ->
            catalog.values.filter { 
                it.name.contains(query, ignoreCase = true) || 
                it.brand?.contains(query, ignoreCase = true) == true ||
                it.ean == query
            }.take(20)
        }
    }

    override suspend fun upsertCatalogProduct(product: CatalogProduct) {
        _catalog.update { it + (product.ean to product) }
    }

    override suspend fun bulkUpsertCatalogProducts(products: List<CatalogProduct>) {
        _catalog.update { it + products.associateBy { p -> p.ean } }
    }

    override suspend fun linkEntryToProduct(entryId: String, ean: String) {
        _mappings.update { it + (entryId to ean) }
    }

    override suspend fun getLinkedEan(entryId: String): String? {
        return _mappings.value[entryId]
    }
}
