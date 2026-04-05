package com.stashapp.shared.domain

import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

// === Domain models ===

data class Recipe(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String? = null,
    val servings: Int = 4,
    val ingredients: List<RecipeIngredient> = emptyList(),
    val source: RecipeSource = RecipeSource.Manual,
    val createdAt: Instant = Instant.now()
)

data class RecipeIngredient(
    val id: String = UUID.randomUUID().toString(),
    val recipeId: String = "",
    val name: String = "",
    val quantity: Double? = null,
    val unit: MeasurementUnit = MeasurementUnit.PIECES,
    val notes: String? = null
)

sealed class RecipeSource {
    object Manual : RecipeSource()
    data class ImportedUrl(val url: String) : RecipeSource()
    data class ScannedPhoto(val imageRef: String) : RecipeSource()
}

// === Match models (used during cook sessions) ===

data class IngredientMatch(
    val recipeIngredient: RecipeIngredient,
    val matchedEntries: List<InventoryEntry>,
    val availableQuantity: Double,
    val status: MatchStatus
)

enum class MatchStatus {
    AVAILABLE,
    LOW,
    MISSING
}

// === Repository interface ===

interface RecipeRepository {
    fun getAllRecipes(): Flow<List<Recipe>>
    suspend fun getRecipeById(id: String): Recipe?
    suspend fun addRecipe(recipe: Recipe)
    suspend fun updateRecipe(recipe: Recipe)
    suspend fun deleteRecipe(id: String)
    suspend fun getIngredientsForRecipe(recipeId: String): List<RecipeIngredient>
    suspend fun addIngredient(ingredient: RecipeIngredient)
    suspend fun deleteIngredient(id: String)
    suspend fun deleteIngredientsForRecipe(recipeId: String)
}
