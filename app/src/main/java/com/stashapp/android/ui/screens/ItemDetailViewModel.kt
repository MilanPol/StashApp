package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.*
import com.stashapp.shared.domain.usecase.CheckStapleRestockUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

class ItemDetailViewModel(
    private val entryRepository: InventoryEntryRepository,
    private val locationRepository: StorageLocationRepository,
    private val categoryRepository: CategoryRepository,
    private val catalogRepository: CatalogRepository,
    private val checkStapleRestockUseCase: CheckStapleRestockUseCase,
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
        locationRepository.getAllStorageLocations()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateEntry(entry: InventoryEntry) = viewModelScope.launch {
        entryRepository.updateEntry(entry)
        if (entry.isStaple) {
            checkStapleRestockUseCase.execute(entry)
        }
    }

    fun removeEntry(onComplete: () -> Unit) = viewModelScope.launch {
        entryRepository.removeEntry(itemId)
        onComplete()
    }

    fun consumeFull(onComplete: () -> Unit) = viewModelScope.launch {
        val current = entry.value ?: return@launch
        if (current.isStaple) {
            val updated = current.copy(quantity = Quantity(BigDecimal.ZERO, current.quantity.unit), updatedAt = Instant.now())
            entryRepository.updateEntry(updated)
            checkStapleRestockUseCase.execute(updated)
        } else {
            entryRepository.removeEntry(itemId)
        }
        onComplete()
    }

    fun consumePartial(amount: BigDecimal, onComplete: () -> Unit) = viewModelScope.launch {
        val current = entry.value ?: return@launch
        val updated = current.consume(amount, Instant.now())
        if (updated.quantity.amount <= BigDecimal.ZERO) {
            if (updated.isStaple) {
                entryRepository.updateEntry(updated)
                checkStapleRestockUseCase.execute(updated)
            } else {
                entryRepository.removeEntry(itemId)
            }
        } else {
            entryRepository.updateEntry(updated)
            checkStapleRestockUseCase.execute(updated)
        }
        onComplete()
    }


    // Catalog operations
    fun searchCatalog(query: String): Flow<List<CatalogProduct>> = catalogRepository.searchCatalog(query)
    fun upsertCatalogProduct(product: CatalogProduct) = viewModelScope.launch { catalogRepository.upsertCatalogProduct(product) }
    fun linkEntryToProduct(entryId: String, ean: String) = viewModelScope.launch { catalogRepository.linkEntryToProduct(entryId, ean) }
    fun getProductByEan(ean: String): Flow<CatalogProduct?> = catalogRepository.getProductByEan(ean)
    suspend fun getLinkedEan(entryId: String): String? = catalogRepository.getLinkedEan(entryId)

    class Factory(
        private val entryRepository: InventoryEntryRepository,
        private val locationRepository: StorageLocationRepository,
        private val categoryRepository: CategoryRepository,
        private val catalogRepository: CatalogRepository,
        private val checkStapleRestockUseCase: CheckStapleRestockUseCase,
        private val itemId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ItemDetailViewModel(entryRepository, locationRepository, categoryRepository, catalogRepository, checkStapleRestockUseCase, itemId) as T
        }
    }
}
