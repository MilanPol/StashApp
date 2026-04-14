package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class RestockUiState(
    val items: List<RestockItem> = emptyList(),
    val locations: List<StorageLocation> = emptyList(),
    val isLoading: Boolean = true
)

class RestockDashboardViewModel(
    private val restockRepository: RestockRepository,
    private val locationRepository: StorageLocationRepository,
    private val entryRepository: InventoryEntryRepository,
    private val catalogRepository: CatalogRepository,
    private val sessionId: String
) : ViewModel() {

    private val _items = restockRepository.getSessionItems(sessionId)
    private val _locations = locationRepository.getAllStorageLocations()

    val uiState: StateFlow<RestockUiState> = combine(_items, _locations) { items, locations ->
        RestockUiState(items = items, locations = locations, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.Lazily, RestockUiState())

    fun updateItemLocation(itemId: String, locationId: String) {
        viewModelScope.launch {
            restockRepository.updateItemLocation(itemId, locationId)
        }
    }

    fun updateItemExpirationDate(itemId: String, expirationDate: java.time.Instant?) {
        viewModelScope.launch {
            val currentItems = uiState.value.items
            val currentItem = currentItems.find { it.id == itemId } ?: return@launch
            restockRepository.updateItem(currentItem.copy(expirationDate = expirationDate))
        }
    }

    fun searchCatalog(query: String): Flow<List<CatalogProduct>> {
        return catalogRepository.searchCatalog(query)
    }

    fun getProductByEan(ean: String): Flow<CatalogProduct?> {
        return catalogRepository.getProductByEan(ean)
    }

    suspend fun findPotentialMatch(name: String, ean: String?): InventoryEntry? {
        // High confidence match by EAN
        ean?.let {
            val byEan = entryRepository.findEntryByCatalogEan(it)
            if (byEan != null) return byEan
        }
        // Fallback to name match
        return entryRepository.findEntryByName(name)
    }

    fun updateItem(id: String, name: String, quantity: Quantity, expirationDate: java.time.Instant?, catalogEan: String?) {
        viewModelScope.launch {
            val currentItems = uiState.value.items
            val currentItem = currentItems.find { it.id == id } ?: return@launch
            
            restockRepository.updateItem(
                currentItem.copy(
                    name = name,
                    quantity = quantity,
                    expirationDate = expirationDate,
                    catalogEan = catalogEan
                )
            )
        }
    }

    fun addItem(name: String, quantity: Quantity, catalogEan: String?) {
        viewModelScope.launch {
            restockRepository.addItemToSession(
                sessionId,
                RestockItem(
                    sessionId = sessionId,
                    name = name,
                    quantity = quantity,
                    catalogEan = catalogEan
                )
            )
        }
    }

    fun confirmItem(itemId: String, mergeWithId: String? = null) {
        viewModelScope.launch {
            restockRepository.confirmItem(itemId, mergeWithId)
        }
    }

    fun completeSession() {
        viewModelScope.launch {
            restockRepository.completeSession(sessionId)
        }
    }

    class Factory(
        private val restockRepository: RestockRepository,
        private val locationRepository: StorageLocationRepository,
        private val entryRepository: InventoryEntryRepository,
        private val catalogRepository: CatalogRepository,
        private val sessionId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RestockDashboardViewModel(
                restockRepository, 
                locationRepository, 
                entryRepository, 
                catalogRepository, 
                sessionId
            ) as T
        }
    }
}
