package com.stashapp.shared.data

import com.stashapp.shared.domain.InventoryEntry
import com.stashapp.shared.domain.InventoryRepository
import com.stashapp.shared.domain.StorageLocation
import com.stashapp.shared.domain.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow

class InMemoryInventoryRepository : InventoryRepository {
    private val _locations = MutableStateFlow<List<StorageLocation>>(listOf(
        StorageLocation(name = "Fridge", icon = "❄️"),
        StorageLocation(name = "Freezer", icon = "🧊"),
        StorageLocation(name = "Pantry", icon = "🥫")
    ))

    private val _categories = MutableStateFlow<List<Category>>(listOf(
        Category(name = "Dairy", icon = "🥛"),
        Category(name = "Produce", icon = "🍎"),
        Category(name = "Pantry Staples", icon = "🍚")
    ))

    private val _entries = MutableStateFlow<List<InventoryEntry>>(emptyList())

    override fun getAllEntries(): Flow<List<InventoryEntry>> {
        return _entries.asStateFlow()
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

    override fun getStorageLocations(): Flow<List<StorageLocation>> = _locations.asStateFlow()

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
}
