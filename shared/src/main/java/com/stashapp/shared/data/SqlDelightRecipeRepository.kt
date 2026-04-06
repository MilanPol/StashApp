package com.stashapp.shared.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.stashapp.shared.db.StashDatabase
import com.stashapp.shared.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class SqlDelightRecipeRepository(
    private val db: StashDatabase
) : RecipeRepository {

    private val recipeQueries = db.recipeQueries

    override fun getAllRecipes(): Flow<List<Recipe>> {
        return recipeQueries.selectAllRecipes().asFlow().mapToList(Dispatchers.IO).map { list ->
            list.map { row -> mapRecipeToDomain(row) }
        }
    }

    override suspend fun getRecipeById(id: String): Recipe? {
        val row = recipeQueries.selectRecipeById(id).executeAsOneOrNull()
        return row?.let { mapRecipeToDomain(it) }
    }

    override suspend fun addRecipe(recipe: Recipe) {
        val (sourceType, sourceRef) = encodeSource(recipe.source)
        recipeQueries.insertRecipe(
            id = recipe.id,
            name = recipe.name,
            description = recipe.description,
            servings = recipe.servings.toLong(),
            source_type = sourceType,
            source_ref = sourceRef,
            created_at = recipe.createdAt.toEpochMilli()
        )
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        // INSERT OR REPLACE handles updates
        addRecipe(recipe)
    }

    override suspend fun deleteRecipe(id: String) {
        recipeQueries.deleteRecipeById(id)
    }

    override suspend fun getIngredientsForRecipe(recipeId: String): List<RecipeIngredient> {
        return recipeQueries.selectIngredientsByRecipeId(recipeId).executeAsList().map { row ->
            RecipeIngredient(
                id = row.id,
                recipeId = row.recipe_id,
                name = row.name,
                quantity = row.quantity,
                unit = try { MeasurementUnit.valueOf(row.unit) } catch (_: Exception) { MeasurementUnit.PIECES },
                notes = row.notes
            )
        }
    }

    override suspend fun addIngredient(ingredient: RecipeIngredient) {
        recipeQueries.insertIngredient(
            id = ingredient.id,
            recipe_id = ingredient.recipeId,
            name = ingredient.name,
            quantity = ingredient.quantity,
            unit = ingredient.unit.name,
            notes = ingredient.notes
        )
    }

    override suspend fun deleteIngredient(id: String) {
        recipeQueries.deleteIngredientById(id)
    }

    override suspend fun deleteIngredientsForRecipe(recipeId: String) {
        recipeQueries.deleteIngredientsByRecipeId(recipeId)
    }

    // --- Private helpers ---

    private fun mapRecipeToDomain(row: com.stashapp.shared.db.Recipe): Recipe {
        return Recipe(
            id = row.id,
            name = row.name,
            description = row.description,
            servings = row.servings.toInt(),
            source = decodeSource(row.source_type, row.source_ref),
            createdAt = Instant.ofEpochMilli(row.created_at)
        )
    }

    private fun encodeSource(source: RecipeSource): Pair<String, String?> {
        return when (source) {
            is RecipeSource.Manual -> "MANUAL" to null
            is RecipeSource.ImportedText -> "TEXT" to source.text
            is RecipeSource.ScannedPhoto -> "PHOTO" to source.imageRef
        }
    }

    private fun decodeSource(type: String, ref: String?): RecipeSource {
        return when (type) {
            "TEXT", "URL" -> RecipeSource.ImportedText(ref ?: "")
            "PHOTO" -> RecipeSource.ScannedPhoto(ref ?: "")
            else -> RecipeSource.Manual
        }
    }
}
