package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.stashapp.shared.domain.usecase.CheckStapleRestockUseCase

class GroupListViewModel(
    private val entryRepository: InventoryEntryRepository,
    private val locationRepository: StorageLocationRepository,
    private val categoryRepository: CategoryRepository,
    private val catalogRepository: CatalogRepository,
    private val checkStapleRestockUseCase: CheckStapleRestockUseCase
) : ViewModel() {

    val entries: StateFlow<List<InventoryEntry>> =
        entryRepository.getAllEntries()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val locations: StateFlow<List<StorageLocation>> =
        locationRepository.getAllStorageLocations()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories: StateFlow<List<Category>> =
        categoryRepository.getCategories()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addEntry(entry: InventoryEntry) = viewModelScope.launch {
        entryRepository.addEntry(entry)
        if (entry.isStaple) {
            checkStapleRestockUseCase.execute(entry)
        }
    }

    fun updateEntry(entry: InventoryEntry) = viewModelScope.launch {
        entryRepository.updateEntry(entry)
        if (entry.isStaple) {
            checkStapleRestockUseCase.execute(entry)
        }
    }

    fun removeEntry(id: String) = viewModelScope.launch {
        val entry = entryRepository.getEntryById(id)
        entryRepository.removeEntry(id)
        if (entry != null && entry.isStaple) {
            checkStapleRestockUseCase.execute(entry)
        }
    }

    fun mergeEntries(sourceId: String, targetId: String) = viewModelScope.launch {
        entryRepository.mergeEntries(sourceId, targetId)
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
        private val checkStapleRestockUseCase: CheckStapleRestockUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupListViewModel(entryRepository, locationRepository, categoryRepository, catalogRepository, checkStapleRestockUseCase) as T
        }
    }
}
