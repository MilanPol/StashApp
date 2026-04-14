package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.Quantity
import com.stashapp.shared.domain.RestockItem
import com.stashapp.shared.domain.RestockRepository
import com.stashapp.shared.domain.MeasurementUnit
import com.stashapp.shared.domain.ShoppingListItem
import com.stashapp.shared.domain.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class ShoppingListViewModel(
    private val shoppingRepository: ShoppingListRepository,
    private val restockRepository: RestockRepository
) : ViewModel() {

    private val _navigationEvent = MutableSharedFlow<String>()
    val navigationEvent = _navigationEvent.asSharedFlow()

    val items: StateFlow<List<ShoppingListItem>> = shoppingRepository.getActiveItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addItem(name: String, quantity: Quantity? = null) {
        viewModelScope.launch {
            shoppingRepository.addOrUpdateItem(ShoppingListItem(name = name, quantity = quantity))
        }
    }

    fun togglePurchased(item: ShoppingListItem) {
        viewModelScope.launch {
            shoppingRepository.markAsPurchased(item.id, !item.isPurchased)
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            shoppingRepository.deleteItem(id)
        }
    }

    fun startManualRestock() {
        viewModelScope.launch {
            val purchasedItems = shoppingRepository.getPurchasedItems().first()
            val restockItems = purchasedItems.map { shopItem ->
                RestockItem(
                    sessionId = "", // Repo handles
                    name = shopItem.name,
                    quantity = shopItem.quantity ?: Quantity(java.math.BigDecimal.ONE, MeasurementUnit.PIECES),
                    shoppingListItemId = shopItem.id,
                    catalogEan = shopItem.catalogEan
                )
            }
            val sessionId = restockRepository.createSession(restockItems)
            _navigationEvent.emit("restock_dashboard/$sessionId")
        }
    }

    class Factory(
        private val shoppingRepository: ShoppingListRepository,
        private val restockRepository: RestockRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShoppingListViewModel(shoppingRepository, restockRepository) as T
        }
    }
}
