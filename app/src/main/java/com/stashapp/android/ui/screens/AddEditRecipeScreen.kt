package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.stashapp.android.R
import com.stashapp.shared.domain.*
import com.stashapp.android.ui.components.translated
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecipeScreen(
    recipeRepository: RecipeRepository,
    inventoryRepository: InventoryEntryRepository,
    recipeId: String?,           // null = new recipe, non-null = editing
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("4") }
    var ingredients by remember { mutableStateOf(listOf(RecipeIngredient())) }
    
    // Collect inventory for suggestions
    val allInventoryEntries by inventoryRepository.getAllEntries().collectAsState(initial = emptyList())
    val suggestionNames = remember(allInventoryEntries) {
        allInventoryEntries.map { it.name }.distinct().sorted()
    }

    var isLoading by remember { mutableStateOf(recipeId != null) }

    LaunchedEffect(recipeId) {
        if (recipeId != null) {
            val recipe = recipeRepository.getRecipeById(recipeId)
            if (recipe != null) {
                name = recipe.name
                description = recipe.description ?: ""
                servings = recipe.servings.toString()
                ingredients = recipeRepository.getIngredientsForRecipe(recipeId)
                if (ingredients.isEmpty()) ingredients = listOf(RecipeIngredient(recipeId = recipeId))
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (recipeId == null) stringResource(R.string.recipe_add) else stringResource(R.string.action_edit)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val recipe = Recipe(
                                    id = recipeId ?: java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    description = description.ifBlank { null },
                                    servings = servings.toIntOrNull() ?: 4
                                )
                                if (recipeId == null) {
                                    recipeRepository.addRecipe(recipe)
                                } else {
                                    recipeRepository.updateRecipe(recipe)
                                }
                                
                                // Reset ingredients
                                recipeRepository.deleteIngredientsForRecipe(recipe.id)
                                ingredients.filter { it.name.isNotBlank() }.forEach { ingredient ->
                                    recipeRepository.addIngredient(ingredient.copy(recipeId = recipe.id))
                                }
                                
                                onNavigateBack()
                            }
                        },
                        enabled = name.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = stringResource(R.string.action_save))
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.recipe_name_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text(stringResource(R.string.recipe_description_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = servings,
                        onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) servings = it },
                        label = { Text(stringResource(R.string.recipe_servings_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                
                // Ingredients Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.recipe_ingredients_title), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { 
                            // PREPEND: Put the new empty ingredient at the top
                            ingredients = listOf(RecipeIngredient(recipeId = recipeId ?: "")) + ingredients 
                        }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.recipe_add_ingredient))
                        }
                    }
                }
                
                itemsIndexed(ingredients) { index, ingredient ->
                    IngredientForm(
                        ingredient = ingredient,
                        suggestions = suggestionNames,
                        onUpdate = { updated ->
                            val newList = ingredients.toMutableList()
                            newList[index] = updated
                            ingredients = newList
                        },
                        onDelete = {
                            val newList = ingredients.toMutableList()
                            newList.removeAt(index)
                            ingredients = if (newList.isEmpty()) listOf(RecipeIngredient()) else newList
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientForm(
    ingredient: RecipeIngredient,
    suggestions: List<String>,
    onUpdate: (RecipeIngredient) -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    SearchableNameField(
                        value = ingredient.name,
                        onValueChange = { onUpdate(ingredient.copy(name = it)) },
                        suggestions = suggestions,
                        label = stringResource(R.string.recipe_ingredient_name_hint)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                }
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ingredient.quantity?.toString() ?: "",
                    onValueChange = { 
                        val qty = it.toDoubleOrNull()
                        onUpdate(ingredient.copy(quantity = qty))
                    },
                    label = { Text(stringResource(R.string.recipe_ingredient_quantity_hint)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
                
                UnitDropdown(
                    currentUnit = ingredient.unit,
                    onUnitSelected = { onUpdate(ingredient.copy(unit = it)) },
                    modifier = Modifier.weight(1f)
                )
            }
            
            OutlinedTextField(
                value = ingredient.notes ?: "",
                onValueChange = { onUpdate(ingredient.copy(notes = it.ifBlank { null })) },
                label = { Text(stringResource(R.string.recipe_ingredient_notes_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableNameField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
    label: String
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = remember(value, suggestions) {
        if (value.isEmpty()) emptyList() 
        else suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(5)
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = { 
                onValueChange(it)
                expanded = true
            },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        
        if (expanded && filteredSuggestions.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = false),
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                filteredSuggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitDropdown(
    currentUnit: MeasurementUnit,
    onUnitSelected: (MeasurementUnit) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            readOnly = true,
            value = currentUnit.translated(),
            onValueChange = { },
            label = { Text(stringResource(R.string.unit)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MeasurementUnit.values().forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit.translated()) },
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    }
                )
            }
        }
    }
}
