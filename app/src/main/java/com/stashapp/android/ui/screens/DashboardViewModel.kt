package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.android.data.SettingsManager
import com.stashapp.shared.domain.*
import com.stashapp.shared.domain.usecase.CheckStapleRestockUseCase
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant

class DashboardViewModel(
    private val entryRepository: InventoryEntryRepository,
    private val locationRepository: StorageLocationRepository,
    private val categoryRepository: CategoryRepository,
    private val catalogRepository: CatalogRepository,
    private val settingsManager: SettingsManager,
    private val checkStapleRestockUseCase: CheckStapleRestockUseCase
) : ViewModel() {

    val entries: StateFlow<List<InventoryEntry>> =
        entryRepository.getAllEntries()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val activeLocationId: StateFlow<String?> =
        settingsManager.activeLocationId
            .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val locations: StateFlow<List<StorageLocation>> =
        activeLocationId.flatMapLatest { parentId ->
            locationRepository.getStorageLocations(parentId)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allLocations: StateFlow<List<StorageLocation>> =
        locationRepository.getAllStorageLocations()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val categories: StateFlow<List<Category>> =
        categoryRepository.getCategories()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val expiringEntries: StateFlow<List<InventoryEntry>> =
        entryRepository.getExpiringEntries(Instant.now())
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val globalLeadDays: StateFlow<Int> =
        settingsManager.globalLeadDays
            .stateIn(viewModelScope, SharingStarted.Lazily, 2)

    // Entry operations
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

    // Location operations
    fun addLocation(location: StorageLocation) = viewModelScope.launch { locationRepository.addStorageLocation(location) }
    fun updateLocation(location: StorageLocation) = viewModelScope.launch { locationRepository.updateStorageLocation(location) }
    fun removeLocation(id: String) = viewModelScope.launch { locationRepository.removeStorageLocation(id) }

    // Category operations
    fun addCategory(category: Category) = viewModelScope.launch { categoryRepository.addCategory(category) }
    fun updateCategory(category: Category) = viewModelScope.launch { categoryRepository.updateCategory(category) }
    fun removeCategory(id: String) = viewModelScope.launch { categoryRepository.removeCategory(id) }

    // Active location navigation
    fun setActiveLocation(locationId: String?) = viewModelScope.launch {
        settingsManager.setActiveLocationId(locationId)
    }

    // Catalog operations
    fun searchCatalog(query: String): Flow<List<CatalogProduct>> = catalogRepository.searchCatalog(query)
    
    fun upsertCatalogProduct(product: CatalogProduct) = viewModelScope.launch {
        catalogRepository.upsertCatalogProduct(product)
    }

    fun linkEntryToProduct(entryId: String, ean: String) = viewModelScope.launch {
        catalogRepository.linkEntryToProduct(entryId, ean)
    }

    fun getProductByEan(ean: String): Flow<CatalogProduct?> = catalogRepository.getProductByEan(ean)

    suspend fun getLinkedEan(entryId: String): String? = catalogRepository.getLinkedEan(entryId)

    class Factory(
        private val entryRepository: InventoryEntryRepository,
        private val locationRepository: StorageLocationRepository,
        private val categoryRepository: CategoryRepository,
        private val catalogRepository: CatalogRepository,
        private val settingsManager: SettingsManager,
        private val checkStapleRestockUseCase: CheckStapleRestockUseCase
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(entryRepository, locationRepository, categoryRepository, catalogRepository, settingsManager, checkStapleRestockUseCase) as T
        }
    }
}
