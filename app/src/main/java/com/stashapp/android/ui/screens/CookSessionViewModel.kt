package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant

class CookSessionViewModel(
    private val recipeRepository: RecipeRepository,
    private val entryRepository: InventoryEntryRepository,
    private val shoppingRepository: ShoppingListRepository,
    private val recipeId: String
) : ViewModel() {

    private val _recipe = MutableStateFlow<Recipe?>(null)
    val recipe: StateFlow<Recipe?> = _recipe

    private val _ingredients = MutableStateFlow<List<RecipeIngredient>>(emptyList())
    val ingredients: StateFlow<List<RecipeIngredient>> = _ingredients

    private val _matches = MutableStateFlow<List<IngredientMatch>>(emptyList())
    val matches: StateFlow<List<IngredientMatch>> = _matches

    private val _servingMultiplier = MutableStateFlow(1.0)
    val servingMultiplier: StateFlow<Double> = _servingMultiplier

    private val _cookingDone = MutableStateFlow(false)
    val cookingDone: StateFlow<Boolean> = _cookingDone

    init {
        viewModelScope.launch {
            val loadedRecipe = recipeRepository.getRecipeById(recipeId)
            _recipe.value = loadedRecipe
            if (loadedRecipe != null) {
                val loadedIngredients = recipeRepository.getIngredientsForRecipe(recipeId)
                _ingredients.value = loadedIngredients
                computeMatches(loadedIngredients)
            }
        }
    }

    fun setServingMultiplier(multiplier: Double) {
        _servingMultiplier.value = multiplier
        viewModelScope.launch {
            computeMatches(_ingredients.value)
        }
    }

    private suspend fun computeMatches(ingredients: List<RecipeIngredient>) {
        val allEntries = entryRepository.getAllEntries().first()
        val multiplier = _servingMultiplier.value

        _matches.value = ingredients.map { ingredient ->
            val requiredQty = (ingredient.quantity ?: 0.0) * multiplier
            // Match by name (case-insensitive contains)
            val matched = allEntries.filter { entry ->
                entry.name.lowercase().contains(ingredient.name.lowercase()) ||
                ingredient.name.lowercase().contains(entry.name.lowercase())
            }
            // Sum available quantity from matched entries (only same unit)
            val available = matched
                .filter { it.quantity.unit == ingredient.unit }
                .sumOf { it.quantity.amount.toDouble() }

            val status = when {
                ingredient.quantity == null -> MatchStatus.AVAILABLE // untrackable
                matched.isEmpty() -> MatchStatus.MISSING
                available >= requiredQty -> MatchStatus.AVAILABLE
                available > 0 -> MatchStatus.LOW
                else -> MatchStatus.MISSING
            }

            IngredientMatch(
                recipeIngredient = ingredient,
                matchedEntries = matched,
                availableQuantity = available,
                status = status
            )
        }
    }

    fun addMissingToShoppingList() {
        viewModelScope.launch {
            val multiplier = _servingMultiplier.value
            for (match in _matches.value) {
                if (match.status == MatchStatus.MISSING) {
                    val requiredQty = (match.recipeIngredient.quantity ?: continue) * multiplier
                    shoppingRepository.addOrUpdateItem(
                        ShoppingListItem(
                            name = match.recipeIngredient.name,
                            quantity = Quantity(BigDecimal.valueOf(requiredQty), match.recipeIngredient.unit)
                        )
                    )
                } else if (match.status == MatchStatus.LOW) {
                    val requiredQty = (match.recipeIngredient.quantity ?: continue) * multiplier
                    val deficit = requiredQty - match.availableQuantity
                    if (deficit > 0) {
                        shoppingRepository.addOrUpdateItem(
                            ShoppingListItem(
                                name = match.recipeIngredient.name,
                                quantity = Quantity(BigDecimal.valueOf(deficit), match.recipeIngredient.unit)
                            )
                        )
                    }
                }
            }
        }
    }

    fun confirmDeductions() {
        viewModelScope.launch {
            val multiplier = _servingMultiplier.value
            for (match in _matches.value) {
                if (match.status == MatchStatus.AVAILABLE || match.status == MatchStatus.LOW) {
                    val requiredQty = (match.recipeIngredient.quantity ?: continue) * multiplier
                    var remaining = requiredQty

                    for (entry in match.matchedEntries) {
                        if (remaining <= 0) break
                        if (entry.quantity.unit != match.recipeIngredient.unit) continue

                        val entryAmount = entry.quantity.amount.toDouble()
                        val deduct = minOf(remaining, entryAmount)
                        remaining -= deduct

                        val newAmount = entryAmount - deduct
                        if (newAmount <= 0.001) {
                            entryRepository.removeEntry(entry.id)
                        } else {
                            val updated = entry.copy(
                                quantity = entry.quantity.copy(
                                    amount = BigDecimal.valueOf(newAmount)
                                ),
                                updatedAt = Instant.now()
                            )
                            entryRepository.updateEntry(updated)
                        }
                    }
                }
            }
            _cookingDone.value = true
        }
    }

    class Factory(
        private val recipeRepository: RecipeRepository,
        private val entryRepository: InventoryEntryRepository,
        private val shoppingRepository: ShoppingListRepository,
        private val recipeId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CookSessionViewModel(recipeRepository, entryRepository, shoppingRepository, recipeId) as T
        }
    }
}
