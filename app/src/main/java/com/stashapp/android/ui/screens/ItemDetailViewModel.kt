package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

class ItemDetailViewModel(
    private val entryRepository: InventoryEntryRepository,
    private val locationRepository: StorageLocationRepository,
    private val categoryRepository: CategoryRepository,
    private val catalogRepository: CatalogRepository,
    private val itemId: String
) : ViewModel() {

    private val _allEntries = entryRepository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val entry: StateFlow<InventoryEntry?> = _allEntries
        .map { entries -> entries.find { it.id == itemId } }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val allEntries: StateFlow<List<InventoryEntry>> = _allEntries

    val categories: StateFlow<List<Category>> =
        categoryRepository.getCategories()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val locations: StateFlow<List<StorageLocation>> =
        locationRepository.getStorageLocations()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateEntry(entry: InventoryEntry) = viewModelScope.launch {
        entryRepository.updateEntry(entry)
    }

    fun removeEntry(onComplete: () -> Unit) = viewModelScope.launch {
        entryRepository.removeEntry(itemId)
        onComplete()
    }

    fun consumeFull(onComplete: () -> Unit) = viewModelScope.launch {
        entryRepository.removeEntry(itemId)
        onComplete()
    }

    fun consumePartial(amount: BigDecimal, onComplete: () -> Unit) = viewModelScope.launch {
        val current = entry.value ?: return@launch
        val updated = current.consume(amount, Instant.now())
        if (updated.quantity.amount <= BigDecimal.ZERO) {
            entryRepository.removeEntry(itemId)
        } else {
            entryRepository.updateEntry(updated)
        }
        onComplete()
    }

    // Provide sub-repositories to child composables (AddItemScreen)
    val catalogRepo: CatalogRepository get() = catalogRepository
    val entryRepo: InventoryEntryRepository get() = entryRepository

    class Factory(
        private val entryRepository: InventoryEntryRepository,
        private val locationRepository: StorageLocationRepository,
        private val categoryRepository: CategoryRepository,
        private val catalogRepository: CatalogRepository,
        private val itemId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ItemDetailViewModel(entryRepository, locationRepository, categoryRepository, catalogRepository, itemId) as T
        }
    }
}
