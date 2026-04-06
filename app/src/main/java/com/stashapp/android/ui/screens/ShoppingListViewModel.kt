package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.Quantity
import com.stashapp.shared.domain.ShoppingListItem
import com.stashapp.shared.domain.ShoppingListRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShoppingListViewModel(
    private val repository: ShoppingListRepository
) : ViewModel() {

    val items: StateFlow<List<ShoppingListItem>> = repository.getActiveItems()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addItem(name: String, quantity: Quantity? = null) {
        viewModelScope.launch {
            repository.addOrUpdateItem(ShoppingListItem(name = name, quantity = quantity))
        }
    }

    fun togglePurchased(item: ShoppingListItem) {
        viewModelScope.launch {
            repository.markAsPurchased(item.id, !item.isPurchased)
        }
    }

    fun deleteItem(id: String) {
        viewModelScope.launch {
            repository.deleteItem(id)
        }
    }

    class Factory(private val repository: ShoppingListRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ShoppingListViewModel(repository) as T
        }
    }
}
