# Recipe Feature — Step-by-Step Implementation Guide

This document breaks the recipe feature (from `docs/recipe-feature.md`) into small, ordered implementation steps. Each step is self-contained and describes **exactly** which files to create or modify, what code to write, and how to verify the result compiles.

> [!IMPORTANT]
> Follow the steps in order. Each step builds on the previous one. Do NOT skip ahead.
> After each step, run `./gradlew assembleDebug` to verify everything compiles.

---

## Prerequisites — Read These Files First

Before starting any step, read and understand these existing files:

| File | Why |
|---|---|
| `shared/src/main/java/com/stashapp/shared/domain/Inventory.kt` | All existing domain models, enums (`MeasurementUnit`), data classes (`InventoryEntry`, `StorageLocation`, `Category`, `CatalogProduct`), and repository interfaces (`InventoryEntryRepository`, `StorageLocationRepository`, `CategoryRepository`, `CatalogRepository`) |
| `shared/src/main/java/com/stashapp/shared/data/SqlDelightInventoryRepository.kt` | The single concrete repository that implements all 4 interfaces above. Uses SQLDelight. This is the pattern you must follow for new repositories. |
| `shared/src/main/sqldelight/com/stashapp/shared/db/InventoryEntry.sq` | Example of a SQLDelight `.sq` file with `CREATE TABLE` and named queries. |
| `shared/src/main/sqldelight/com/stashapp/shared/db/3.sqm` | Example migration file. Next migration must be `4.sqm`. |
| `app/src/main/java/com/stashapp/android/MainActivity.kt` | Navigation setup using Jetpack Compose `NavHost`. All routes are registered here. The repository is created once and passed to ViewModels/screens. |
| `app/src/main/java/com/stashapp/android/ui/screens/DashboardViewModel.kt` | Example ViewModel with `ViewModelProvider.Factory` pattern. New ViewModels must follow this exact pattern. |
| `app/src/main/java/com/stashapp/android/ui/screens/DashboardScreen.kt` | Example of a full Compose screen with `Scaffold`, `LazyColumn`, FABs, dialogs. |
| `app/src/main/res/values/strings.xml` | English string resources. All user-facing text must be a string resource. |
| `app/src/main/res/values-nl/strings.xml` | Dutch string resources. Every English string must have a Dutch translation. |

---

## Phase 1: Database & Domain Models

### Step 1.1 — Create the Recipe domain models

**File to CREATE**: `shared/src/main/java/com/stashapp/shared/domain/Recipe.kt`

**Package**: `com.stashapp.shared.domain`

**What to write**: Define these data classes and interfaces in a single file:

```kotlin
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
    val steps: List<String> = emptyList(),
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
```

**Key rules**:
- Reuse the existing `MeasurementUnit` enum from `Inventory.kt` (it's in the same package, so no import needed beyond the package).
- Use `String` for IDs, matching the pattern of `InventoryEntry`, `StorageLocation`, etc.
- Use `UUID.randomUUID().toString()` for default ID generation, same as existing models.
- `RecipeIngredient.quantity` is `Double?` (nullable) because some ingredients like "salt to taste" have no quantity.

**Verify**: `./gradlew :shared:compileDebugKotlin` — should compile with no errors.

---

### Step 1.2 — Create the SQLDelight schema for recipes

**File to CREATE**: `shared/src/main/sqldelight/com/stashapp/shared/db/Recipe.sq`

**What to write**:

```sql
CREATE TABLE recipe (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    servings INTEGER NOT NULL DEFAULT 4,
    steps TEXT NOT NULL DEFAULT '',
    source_type TEXT NOT NULL DEFAULT 'MANUAL',
    source_ref TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE recipe_ingredient (
    id TEXT NOT NULL PRIMARY KEY,
    recipe_id TEXT NOT NULL,
    name TEXT NOT NULL,
    quantity REAL,
    unit TEXT NOT NULL DEFAULT 'PIECES',
    notes TEXT,
    FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);

CREATE INDEX idx_ingredient_recipe ON recipe_ingredient(recipe_id);

-- Recipe queries

selectAllRecipes:
SELECT * FROM recipe ORDER BY name;

selectRecipeById:
SELECT * FROM recipe WHERE id = ?;

insertRecipe:
INSERT OR REPLACE INTO recipe(id, name, description, servings, steps, source_type, source_ref, created_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteRecipeById:
DELETE FROM recipe WHERE id = ?;

-- Ingredient queries

selectIngredientsByRecipeId:
SELECT * FROM recipe_ingredient WHERE recipe_id = ? ORDER BY name;

insertIngredient:
INSERT OR REPLACE INTO recipe_ingredient(id, recipe_id, name, quantity, unit, notes)
VALUES (?, ?, ?, ?, ?, ?);

deleteIngredientById:
DELETE FROM recipe_ingredient WHERE id = ?;

deleteIngredientsByRecipeId:
DELETE FROM recipe_ingredient WHERE recipe_id = ?;
```

**Key rules**:
- `steps` is stored as a single TEXT column. The repository will join/split with `\n` as separator.
- `source_type` is stored as TEXT: one of `"MANUAL"`, `"URL"`, `"PHOTO"`.
- `source_ref` stores the URL or image reference (nullable, only set for URL/PHOTO sources).
- `quantity` is `REAL` (SQLite float) because `RecipeIngredient.quantity` is `Double?`.
- Use `INSERT OR REPLACE` to match the existing pattern in `InventoryEntry.sq`.

---

### Step 1.3 — Create the database migration

**File to CREATE**: `shared/src/main/sqldelight/com/stashapp/shared/db/4.sqm`

**What to write**:

```sql
CREATE TABLE recipe (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    description TEXT,
    servings INTEGER NOT NULL DEFAULT 4,
    steps TEXT NOT NULL DEFAULT '',
    source_type TEXT NOT NULL DEFAULT 'MANUAL',
    source_ref TEXT,
    created_at INTEGER NOT NULL
);

CREATE TABLE recipe_ingredient (
    id TEXT NOT NULL PRIMARY KEY,
    recipe_id TEXT NOT NULL,
    name TEXT NOT NULL,
    quantity REAL,
    unit TEXT NOT NULL DEFAULT 'PIECES',
    notes TEXT,
    FOREIGN KEY (recipe_id) REFERENCES recipe(id) ON DELETE CASCADE
);

CREATE INDEX idx_ingredient_recipe ON recipe_ingredient(recipe_id);
```

**Key rule**: The migration file must contain the exact same `CREATE TABLE` and `CREATE INDEX` statements as `Recipe.sq`. It must NOT contain the named queries (those only go in the `.sq` file).

---

### Step 1.4 — Update the SQLDelight schema version

**File to MODIFY**: `shared/build.gradle.kts` (or `shared/build.gradle`)

Find the `sqldelight` configuration block. It will contain a `schemaOutputDirectory` and/or `version` setting. Increment the version from `3` to `4` (or ensure the `deriveSchemaFromMigrations` setting is present). 

> [!NOTE]  
> If the project uses `deriveSchemaFromMigrations = true`, you do NOT need to change a version number. Just adding `4.sqm` is enough. Check the existing `sqldelight` block in `shared/build.gradle.kts` to confirm.

**Verify**: `./gradlew :shared:generateDebugStashDatabaseInterface` — should generate the new query classes without errors.

---

### Step 1.5 — Create the SqlDelightRecipeRepository

**File to CREATE**: `shared/src/main/java/com/stashapp/shared/data/SqlDelightRecipeRepository.kt`

**Package**: `com.stashapp.shared.data`

**What to write**:

```kotlin
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
            steps = recipe.steps.joinToString("\n"),
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
            steps = if (row.steps.isBlank()) emptyList() else row.steps.split("\n"),
            source = decodeSource(row.source_type, row.source_ref),
            createdAt = Instant.ofEpochMilli(row.created_at)
        )
    }

    private fun encodeSource(source: RecipeSource): Pair<String, String?> {
        return when (source) {
            is RecipeSource.Manual -> "MANUAL" to null
            is RecipeSource.ImportedUrl -> "URL" to source.url
            is RecipeSource.ScannedPhoto -> "PHOTO" to source.imageRef
        }
    }

    private fun decodeSource(type: String, ref: String?): RecipeSource {
        return when (type) {
            "URL" -> RecipeSource.ImportedUrl(ref ?: "")
            "PHOTO" -> RecipeSource.ScannedPhoto(ref ?: "")
            else -> RecipeSource.Manual
        }
    }
}
```

**Key rules**:
- Follow the exact same patterns as `SqlDelightInventoryRepository.kt`:
  - Use `asFlow().mapToList(Dispatchers.IO)` for Flow queries.
  - Use `executeAsOneOrNull()` for single-row queries.
  - Use `executeAsList()` for list queries that don't need to be reactive.
- The `mapRecipeToDomain` function loads the recipe WITHOUT its ingredients. Ingredients are loaded separately via `getIngredientsForRecipe()`. This avoids complex joins.
- Steps are stored as newline-separated text.

**Verify**: `./gradlew :shared:compileDebugKotlin` — should compile.

---

## Phase 2: Wire Repository into MainActivity

### Step 2.1 — Create the RecipeRepository instance in MainActivity

**File to MODIFY**: `app/src/main/java/com/stashapp/android/MainActivity.kt`

**What to change**:

1. Add this import at the top (with the other imports):
```kotlin
import com.stashapp.shared.data.SqlDelightRecipeRepository
```

2. Find the line where the existing `repository` (`SqlDelightInventoryRepository`) is created. It will look something like:
```kotlin
val repository = SqlDelightInventoryRepository(database, driver)
```
Right **after** that line, add:
```kotlin
val recipeRepository = SqlDelightRecipeRepository(database)
```

**Do NOT change anything else in this step.** The new repository is not wired to any screens yet — that happens in Phase 4.

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

## Phase 3: Recipe List Screen & Navigation

### Step 3.1 — Add string resources

**File to MODIFY**: `app/src/main/res/values/strings.xml`

Add these strings inside the `<resources>` tag, at the end (before the closing `</resources>`):

```xml
    <!-- Recipe Feature -->
    <string name="nav_recipes">Recipes</string>
    <string name="recipe_list_title">Recipes</string>
    <string name="recipe_add">Add recipe</string>
    <string name="recipe_empty_title">No recipes yet</string>
    <string name="recipe_empty_subtitle">Create your first recipe to get started</string>
    <string name="recipe_servings">%1$d servings</string>
    <string name="recipe_ingredients_count">%1$d ingredients</string>
    <string name="recipe_name_hint">Recipe name</string>
    <string name="recipe_description_hint">Description (optional)</string>
    <string name="recipe_servings_hint">Servings</string>
    <string name="recipe_add_ingredient">Add ingredient</string>
    <string name="recipe_ingredient_name_hint">Ingredient name</string>
    <string name="recipe_ingredient_quantity_hint">Quantity</string>
    <string name="recipe_ingredient_notes_hint">Notes (e.g. finely chopped)</string>
    <string name="recipe_steps_title">Steps</string>
    <string name="recipe_add_step">Add step</string>
    <string name="recipe_step_hint">Step %1$d</string>
    <string name="recipe_ingredients_title">Ingredients</string>
    <string name="recipe_delete_confirm">Delete this recipe?</string>
    <string name="recipe_delete_message">This cannot be undone.</string>
    <string name="recipe_cook">Cook</string>
    <string name="cook_select_recipe">Select a recipe</string>
    <string name="cook_review_title">Review ingredients</string>
    <string name="cook_status_available">Available</string>
    <string name="cook_status_low">Low stock</string>
    <string name="cook_status_missing">Missing</string>
    <string name="cook_deduct_title">Confirm deductions</string>
    <string name="cook_deduct_skip">Skip</string>
    <string name="cook_deduct_confirm">Deduct</string>
    <string name="cook_done_title">Done cooking!</string>
    <string name="cook_done_message">Inventory has been updated.</string>
```

**File to MODIFY**: `app/src/main/res/values-nl/strings.xml`

Add the Dutch translations inside the `<resources>` tag, at the end:

```xml
    <!-- Recipe Feature -->
    <string name="nav_recipes">Recepten</string>
    <string name="recipe_list_title">Recepten</string>
    <string name="recipe_add">Recept toevoegen</string>
    <string name="recipe_empty_title">Nog geen recepten</string>
    <string name="recipe_empty_subtitle">Maak je eerste recept om te beginnen</string>
    <string name="recipe_servings">%1$d porties</string>
    <string name="recipe_ingredients_count">%1$d ingrediënten</string>
    <string name="recipe_name_hint">Receptnaam</string>
    <string name="recipe_description_hint">Beschrijving (optioneel)</string>
    <string name="recipe_servings_hint">Porties</string>
    <string name="recipe_add_ingredient">Ingrediënt toevoegen</string>
    <string name="recipe_ingredient_name_hint">Ingrediëntnaam</string>
    <string name="recipe_ingredient_quantity_hint">Hoeveelheid</string>
    <string name="recipe_ingredient_notes_hint">Notities (bijv. fijngesneden)</string>
    <string name="recipe_steps_title">Stappen</string>
    <string name="recipe_add_step">Stap toevoegen</string>
    <string name="recipe_step_hint">Stap %1$d</string>
    <string name="recipe_ingredients_title">Ingrediënten</string>
    <string name="recipe_delete_confirm">Dit recept verwijderen?</string>
    <string name="recipe_delete_message">Dit kan niet ongedaan worden gemaakt.</string>
    <string name="recipe_cook">Koken</string>
    <string name="cook_select_recipe">Selecteer een recept</string>
    <string name="cook_review_title">Ingrediënten controleren</string>
    <string name="cook_status_available">Beschikbaar</string>
    <string name="cook_status_low">Beperkte voorraad</string>
    <string name="cook_status_missing">Ontbreekt</string>
    <string name="cook_deduct_title">Aftrek bevestigen</string>
    <string name="cook_deduct_skip">Overslaan</string>
    <string name="cook_deduct_confirm">Aftrekken</string>
    <string name="cook_done_title">Klaar met koken!</string>
    <string name="cook_done_message">Voorraad is bijgewerkt.</string>
```

**Verify**: `./gradlew assembleDebug` — should compile.

---

### Step 3.2 — Create the RecipeListViewModel

**File to CREATE**: `app/src/main/java/com/stashapp/android/ui/screens/RecipeListViewModel.kt`

**Package**: `com.stashapp.android.ui.screens`

**What to write**:

```kotlin
package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.Recipe
import com.stashapp.shared.domain.RecipeRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RecipeListViewModel(
    private val recipeRepository: RecipeRepository
) : ViewModel() {

    val recipes: StateFlow<List<Recipe>> =
        recipeRepository.getAllRecipes()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun deleteRecipe(id: String) = viewModelScope.launch {
        recipeRepository.deleteRecipe(id)
    }

    class Factory(
        private val recipeRepository: RecipeRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecipeListViewModel(recipeRepository) as T
        }
    }
}
```

**Key rule**: Follow the exact same `ViewModelProvider.Factory` pattern as `DashboardViewModel.kt`.

---

### Step 3.3 — Create the RecipeListScreen

**File to CREATE**: `app/src/main/java/com/stashapp/android/ui/screens/RecipeListScreen.kt`

**Package**: `com.stashapp.android.ui.screens`

This screen displays a list of all recipes. It has:
- A `Scaffold` with a top `TopAppBar` showing the title "Recipes" and a back button.
- A `FloatingActionButton` to add a new recipe.
- A `LazyColumn` listing all recipes as `ElevatedCard` items.
- Each card shows the recipe name, servings count, and ingredient count.
- Clicking a card navigates to the recipe detail/edit screen.
- An empty state when no recipes exist.

**Function signature**:
```kotlin
@Composable
fun RecipeListScreen(
    viewModel: RecipeListViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToRecipe: (recipeId: String) -> Unit,
    onNavigateToNewRecipe: () -> Unit
)
```

**Implementation notes**:
- Collect `viewModel.recipes` using `collectAsState()`.
- Use `stringResource(R.string.recipe_list_title)` for the title.
- Use `stringResource(R.string.recipe_servings, recipe.servings)` for serving count text.
- Use `stringResource(R.string.recipe_ingredients_count, recipe.ingredients.size)` for ingredient count. Note: `recipe.ingredients` will be empty from the list query (loaded separately). For the count on the list screen, add a `ingredientCount` field to the Recipe OR make a second query. **Simplest approach**: just show the servings count on the list card and show ingredients on the detail screen.
- Use `Icons.Default.Add` for the FAB icon.
- Use `Icons.AutoMirrored.Filled.ArrowBack` for the back button.
- Use the Material3 color scheme (`MaterialTheme.colorScheme`) for all colors.
- Add `120.dp` bottom padding to the `LazyColumn` content to avoid FAB overlap (same pattern as `DashboardScreen.kt`).

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

### Step 3.4 — Create the AddEditRecipeScreen

**File to CREATE**: `app/src/main/java/com/stashapp/android/ui/screens/AddEditRecipeScreen.kt`

**Package**: `com.stashapp.android.ui.screens`

This screen is a full-screen form for creating or editing a recipe. It has:
- A `Scaffold` with a top bar showing "Add recipe" or "Edit" and a save button.
- A `LazyColumn` with:
  - `OutlinedTextField` for recipe name (required).
  - `OutlinedTextField` for description (optional).
  - `OutlinedTextField` for servings (number, defaults to 4).
  - A "Steps" section header with an "Add step" button.
  - For each step: an `OutlinedTextField` with a delete button.
  - An "Ingredients" section header with an "Add ingredient" button.
  - For each ingredient: a card containing name field, quantity field, unit dropdown, notes field, and a delete button.

**Function signature**:
```kotlin
@Composable
fun AddEditRecipeScreen(
    recipeRepository: RecipeRepository,
    recipeId: String?,           // null = new recipe, non-null = editing
    onNavigateBack: () -> Unit   // called after save or cancel
)
```

**Implementation notes**:
- Use `remember { mutableStateOf(...) }` for all form fields.
- If `recipeId` is non-null, load the existing recipe and its ingredients with `LaunchedEffect(recipeId)` calling `recipeRepository.getRecipeById(recipeId)` and `recipeRepository.getIngredientsForRecipe(recipeId)`. Pre-fill all fields.
- For the unit dropdown, reuse the existing `MeasurementUnit` enum values. Show them in a `DropdownMenu`.
- On save:
  1. Create the `Recipe` object.
  2. Call `recipeRepository.addRecipe(recipe)`.
  3. Call `recipeRepository.deleteIngredientsForRecipe(recipe.id)` (clear old ingredients).
  4. For each ingredient, call `recipeRepository.addIngredient(ingredient)` with `recipeId = recipe.id`.
  5. Call `onNavigateBack()`.
- Wrap save logic in `rememberCoroutineScope().launch { ... }`.

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

### Step 3.5 — Register navigation routes in MainActivity

**File to MODIFY**: `app/src/main/java/com/stashapp/android/MainActivity.kt`

Add these imports at the top:
```kotlin
import com.stashapp.android.ui.screens.RecipeListScreen
import com.stashapp.android.ui.screens.RecipeListViewModel
import com.stashapp.android.ui.screens.AddEditRecipeScreen
import com.stashapp.shared.domain.RecipeRepository
```

Inside the `NavHost` block (after the `composable("expiring_soon")` block, before the closing `}` of `NavHost`), add:

```kotlin
composable("recipes") {
    val recipeListViewModel: RecipeListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = RecipeListViewModel.Factory(
            recipeRepository = recipeRepository
        )
    )
    RecipeListScreen(
        viewModel = recipeListViewModel,
        onNavigateBack = { navController.popBackStack() },
        onNavigateToRecipe = { recipeId ->
            navController.navigate("edit_recipe/$recipeId")
        },
        onNavigateToNewRecipe = {
            navController.navigate("new_recipe")
        }
    )
}
composable("new_recipe") {
    AddEditRecipeScreen(
        recipeRepository = recipeRepository,
        recipeId = null,
        onNavigateBack = { navController.popBackStack() }
    )
}
composable("edit_recipe/{recipeId}") { backStackEntry ->
    val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
    AddEditRecipeScreen(
        recipeRepository = recipeRepository,
        recipeId = recipeId,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

### Step 3.6 — Add a "Recipes" button to the DashboardScreen

**File to MODIFY**: `app/src/main/java/com/stashapp/android/ui/screens/DashboardScreen.kt`

The `DashboardScreen` composable function signature must gain one parameter:
```kotlin
onNavigateToRecipes: () -> Unit
```

Add a visible entry point to the recipe list. This could be:
- A menu item in the top bar's overflow menu (next to Settings), OR
- An `IconButton` in the top bar row.

**Recommended approach**: Add an `IconButton` with `Icons.Default.MenuBook` in the `TopAppBar` actions, next to the existing settings gear icon. When clicked, call `onNavigateToRecipes()`.

Then update the `DashboardScreen` call in `MainActivity.kt` to pass the new parameter:
```kotlin
onNavigateToRecipes = {
    navController.navigate("recipes")
}
```

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

## Phase 4: Cook Session Flow

### Step 4.1 — Create the CookSessionViewModel

**File to CREATE**: `app/src/main/java/com/stashapp/android/ui/screens/CookSessionViewModel.kt`

**Package**: `com.stashapp.android.ui.screens`

This ViewModel manages the cooking flow for a single recipe. It:
1. Loads the recipe and its ingredients.
2. Loads ALL inventory entries.
3. Computes `IngredientMatch` for each ingredient against inventory.
4. Allows the user to confirm or skip deductions.
5. Applies deductions to inventory.

```kotlin
package com.stashapp.android.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stashapp.shared.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class CookSessionViewModel(
    private val recipeRepository: RecipeRepository,
    private val entryRepository: InventoryEntryRepository,
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
            // Match by normalized name (case-insensitive contains)
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
                                    amount = java.math.BigDecimal.valueOf(newAmount)
                                ),
                                updatedAt = java.time.Instant.now()
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
        private val recipeId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CookSessionViewModel(recipeRepository, entryRepository, recipeId) as T
        }
    }
}
```

**Key rules**:
- `first()` is used on the `getAllEntries()` flow to get a snapshot of current inventory for matching.
- Name matching is simple case-insensitive substring matching for now. More sophisticated matching (normalization, fuzzy) can be added later.
- Deductions iterate through matched entries, consuming from each until the required amount is fulfilled. If an entry's amount drops to zero (or near-zero), it is deleted.

---

### Step 4.2 — Create the CookSessionScreen

**File to CREATE**: `app/src/main/java/com/stashapp/android/ui/screens/CookSessionScreen.kt`

**Package**: `com.stashapp.android.ui.screens`

This is a full-screen showing the cooking session. It has:
- A `Scaffold` with top bar showing the recipe name.
- A `LazyColumn` listing each ingredient with its match status.
- Color-coded status icons: green checkmark for AVAILABLE, orange warning for LOW, red X for MISSING.
- A summary section at the bottom showing total available vs missing.
- A "Cook & Deduct Inventory" button at the bottom.
- After deductions, show a "Done!" confirmation with a button to go back.

**Function signature**:
```kotlin
@Composable
fun CookSessionScreen(
    viewModel: CookSessionViewModel,
    onNavigateBack: () -> Unit
)
```

**Implementation notes**:
- Collect all ViewModel state flows using `collectAsState()`.
- For each `IngredientMatch`, show:
  - The ingredient name.
  - The required quantity (scaled by multiplier) and unit.
  - The status icon and color.
  - If LOW, show "Have X, Need Y".
- Use `MaterialTheme.colorScheme.error` for MISSING, a custom orange/amber for LOW, and `MaterialTheme.colorScheme.primary` for AVAILABLE.
- The deduct button calls `viewModel.confirmDeductions()`.
- When `viewModel.cookingDone` becomes true, show the done dialog/screen.

---

### Step 4.3 — Register cook session navigation in MainActivity

**File to MODIFY**: `app/src/main/java/com/stashapp/android/MainActivity.kt`

Add these imports:
```kotlin
import com.stashapp.android.ui.screens.CookSessionScreen
import com.stashapp.android.ui.screens.CookSessionViewModel
```

Inside the `NavHost`, add:

```kotlin
composable("cook/{recipeId}") { backStackEntry ->
    val recipeId = backStackEntry.arguments?.getString("recipeId") ?: ""
    val cookViewModel: CookSessionViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = CookSessionViewModel.Factory(
            recipeRepository = recipeRepository,
            entryRepository = repository,
            recipeId = recipeId
        )
    )
    CookSessionScreen(
        viewModel = cookViewModel,
        onNavigateBack = { navController.popBackStack() }
    )
}
```

Also ensure the `RecipeListScreen` or the recipe detail screen has a "Cook" button that navigates to `"cook/$recipeId"`.

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

## Phase 5: Verification

### Step 5.1 — Full build check

Run:
```bash
./gradlew assembleDebug
```

This must complete with zero errors.

### Step 5.2 — Manual testing checklist

After installing the debug APK on a device:

1. **Navigate to Recipes**: From the dashboard, tap the recipes icon. The recipe list should appear (empty initially).
2. **Create a Recipe**: Tap the FAB, fill in "Pasta Bolognese" with 4 servings, add ingredients "Spaghetti" (500g), "Tomato sauce" (400g), "Ground beef" (300g). Add a step "Cook pasta". Save.
3. **Verify Recipe Persists**: Go back to recipe list, then back in. The recipe should still be there.
4. **Edit a Recipe**: Tap the recipe card. All fields should be pre-filled. Change the name and save.
5. **Cook Session**: From the recipe detail or list, start a cook session. Ingredients should show as MISSING (since you likely have no matching inventory). Add some matching items to your inventory, then try again — they should show as AVAILABLE.
6. **Deductions**: After cooking, verify the inventory quantities decreased or items were removed.

---

## Summary of all files changed/created

| Action | File |
|---|---|
| **CREATE** | `shared/src/main/java/com/stashapp/shared/domain/Recipe.kt` |
| **CREATE** | `shared/src/main/sqldelight/com/stashapp/shared/db/Recipe.sq` |
| **CREATE** | `shared/src/main/sqldelight/com/stashapp/shared/db/4.sqm` |
| **CREATE** | `shared/src/main/java/com/stashapp/shared/data/SqlDelightRecipeRepository.kt` |
| **CREATE** | `app/src/main/java/com/stashapp/android/ui/screens/RecipeListViewModel.kt` |
| **CREATE** | `app/src/main/java/com/stashapp/android/ui/screens/RecipeListScreen.kt` |
| **CREATE** | `app/src/main/java/com/stashapp/android/ui/screens/AddEditRecipeScreen.kt` |
| **CREATE** | `app/src/main/java/com/stashapp/android/ui/screens/CookSessionViewModel.kt` |
| **CREATE** | `app/src/main/java/com/stashapp/android/ui/screens/CookSessionScreen.kt` |
| **MODIFY** | `shared/build.gradle.kts` (only if version bump needed) |
| **MODIFY** | `app/src/main/java/com/stashapp/android/MainActivity.kt` |
| **MODIFY** | `app/src/main/java/com/stashapp/android/ui/screens/DashboardScreen.kt` |
| **MODIFY** | `app/src/main/res/values/strings.xml` |
| **MODIFY** | `app/src/main/res/values-nl/strings.xml` |
