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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.stashapp.android.util.StateSavers
import com.stashapp.android.util.StateSavers.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.stashapp.android.R
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CameraAlt
import com.stashapp.shared.domain.*
import com.stashapp.android.ui.components.translated
import com.stashapp.android.ui.components.RecipeTextScannerView
import com.stashapp.android.util.IngredientParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class ScannerTarget { INGREDIENTS, DESCRIPTION }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecipeScreen(
    recipeRepository: RecipeRepository,
    inventoryRepository: InventoryEntryRepository,
    recipeId: String?,           // null = new recipe, non-null = editing
    onNavigateBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    
    var name by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var servings by rememberSaveable { mutableStateOf("4") }
    var ingredients by rememberSaveable(
        stateSaver = listSaver(StateSavers.RecipeIngredientSaver)
    ) { mutableStateOf(mutableListOf(RecipeIngredient())) }
    
    var sourceType by rememberSaveable { mutableStateOf("MANUAL") }
    var sourceRef by rememberSaveable { mutableStateOf("") }
    var showScanner by rememberSaveable { mutableStateOf(false) }
    var showPasteDialog by rememberSaveable { mutableStateOf(false) }
    var pasteText by rememberSaveable { mutableStateOf("") }
    var scannerTarget by rememberSaveable { mutableStateOf(ScannerTarget.INGREDIENTS) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    // Collect inventory for suggestions
    val allInventoryEntries by inventoryRepository.getAllEntries().collectAsState(initial = emptyList())
    val suggestionNames = remember(allInventoryEntries) {
        allInventoryEntries.map { it.name }.distinct().sorted()
    }

    var isLoading by rememberSaveable { mutableStateOf(recipeId != null) }

    LaunchedEffect(recipeId) {
        if (recipeId != null) {
            val recipe = recipeRepository.getRecipeById(recipeId)
            if (recipe != null) {
                name = recipe.name
                description = recipe.description ?: ""
                servings = recipe.servings.toString()
                ingredients = recipeRepository.getIngredientsForRecipe(recipeId).toMutableList()
                if (ingredients.isEmpty()) ingredients = mutableListOf(RecipeIngredient(recipeId = recipeId))
                
                val source = recipe.source
                sourceType = when (source) {
                    is RecipeSource.Manual -> "MANUAL"
                    is RecipeSource.ImportedText -> "PASTE"
                    is RecipeSource.ScannedPhoto -> "PHOTO"
                }
                sourceRef = when (source) {
                    is RecipeSource.ImportedText -> source.text
                    is RecipeSource.ScannedPhoto -> source.imageRef
                    else -> ""
                }
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
                    if (recipeId != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    IconButton(
                        onClick = {
                            scope.launch {
                                val source = when (sourceType) {
                                    "PASTE" -> RecipeSource.ImportedText(sourceRef)
                                    "PHOTO" -> RecipeSource.ScannedPhoto(sourceRef)
                                    else -> RecipeSource.Manual
                                }
                                val recipe = Recipe(
                                    id = recipeId ?: java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    description = description.ifBlank { null },
                                    servings = servings.toIntOrNull() ?: 4,
                                    source = source
                                )
                                if (recipeId == null) {
                                    recipeRepository.addRecipe(recipe)
                                } else {
                                    recipeRepository.updateRecipe(recipe)
                                }
                                
                                // Reset ingredients
                                recipeRepository.deleteIngredientsForRecipe(recipe.id)
                                for (ingredient in ingredients.filter { it.name.isNotBlank() }) {
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
                        minLines = 2,
                        trailingIcon = {
                            IconButton(onClick = {
                                scannerTarget = ScannerTarget.DESCRIPTION
                                showScanner = true
                            }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.scan_description_hint))
                            }
                        }
                    )
                }
                
                item {
                    OutlinedTextField(
                        value = servings,
                        onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) servings = it },
                        label = { Text(stringResource(R.string.recipe_servings_hint)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                
                // Source Section
                item {
                    Text(
                        stringResource(R.string.recipe_source_label),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = sourceType == "MANUAL",
                            onClick = { sourceType = "MANUAL" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {}
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.recipe_source_manual), maxLines = 1)
                            }
                        }
                        SegmentedButton(
                            selected = sourceType == "PASTE",
                            onClick = { sourceType = "PASTE" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {}
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.recipe_source_paste), maxLines = 1)
                            }
                        }
                        SegmentedButton(
                            selected = sourceType == "PHOTO",
                            onClick = { sourceType = "PHOTO" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {}
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.recipe_source_scan), maxLines = 1)
                            }
                        }
                    }
                }

                // Conditional source content
                if (sourceType == "PASTE") {
                    item {
                        OutlinedTextField(
                            value = sourceRef,
                            onValueChange = { sourceRef = it },
                            label = { Text(stringResource(R.string.hint_paste_ingredients)) },
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            placeholder = { Text("Paste ingredients here...") }
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val newIngredients = sourceRef.lines()
                                    .filter { it.isNotBlank() }
                                    .map { IngredientParser.parse(it, recipeId ?: "", suggestionNames) }
                                
                                ingredients = (newIngredients + ingredients.toList()).toMutableList()
                                
                                if (ingredients.size > 1 && ingredients.any { it.name.isBlank() && it.quantity == 1.0 }) {
                                    ingredients.removeAll { it.name.isBlank() && it.quantity == 1.0 }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text(stringResource(R.string.action_parse))
                        }
                    }
                }

                if (sourceType == "PHOTO") {
                    item {
                        Button(
                            onClick = { 
                                scannerTarget = ScannerTarget.INGREDIENTS
                                showScanner = true 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_start))
                        }
                    }
                }
                
                // Ingredients Section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.recipe_ingredients_title), style = MaterialTheme.typography.titleLarge)
                        Row {
                            IconButton(onClick = { 
                                // PREPEND: Put the new empty ingredient at the top
                                ingredients = (listOf(RecipeIngredient(recipeId = recipeId ?: "")) + ingredients.toList()).toMutableList() 
                            }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.recipe_add_ingredient))
                            }
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
                            ingredients = if (newList.isEmpty()) mutableListOf(RecipeIngredient()) else newList.toMutableList()
                        }
                    )
                }
            }
        }
    }

    if (showPasteDialog) {
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            title = { Text(stringResource(R.string.dialog_paste_ingredients_title)) },
            text = {
                OutlinedTextField(
                    value = pasteText,
                    onValueChange = { pasteText = it },
                    label = { Text(stringResource(R.string.hint_paste_ingredients)) },
                    modifier = Modifier.fillMaxWidth().height(300.dp),
                    placeholder = { Text("Example:\n2 cups Flour\n1 tsp Salt\n1/2 cup Water") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newIngredients = pasteText.lines()
                            .filter { it.isNotBlank() }
                            .map { IngredientParser.parse(it, recipeId ?: "", suggestionNames) }
                        
                        // Add to list and handle potential ambiguities correctly
                        ingredients = (newIngredients + ingredients.toList()).toMutableList()
                        
                        // Clean up if the list only had the initial empty ingredient
                        if (ingredients.size > 1 && ingredients.any { it.name.isBlank() && it.quantity == 1.0 }) {
                             ingredients.removeAll { it.name.isBlank() && it.quantity == 1.0 }
                        }
                        
                        pasteText = ""
                        showPasteDialog = false
                    }
                ) {
                    Text(stringResource(R.string.action_parse))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RecipeTextScannerView(
                    instructionText = if (scannerTarget == ScannerTarget.INGREDIENTS) {
                        stringResource(R.string.scan_select_lines)
                    } else {
                        stringResource(R.string.scan_description_hint)
                    },
                    onTextCaptured = { lines ->
                        if (scannerTarget == ScannerTarget.INGREDIENTS) {
                            // Convert each selected line to a RecipeIngredient
                            val newIngredients = lines.map { line ->
                                IngredientParser.parse(
                                    line = line, 
                                    recipeId = recipeId ?: "", 
                                    catalogNames = suggestionNames
                                )
                            }
                            ingredients = (newIngredients + ingredients.toList()).toMutableList()
                            sourceRef = "scanned_${System.currentTimeMillis()}"
                        } else {
                            // Append to description
                            val scannedDescription = lines.joinToString("\n")
                            description = if (description.isBlank()) scannedDescription else "$description\n$scannedDescription"
                        }
                        showScanner = false
                    },
                    onDismiss = { showScanner = false }
                )
            }
        }
    }

    if (showDeleteDialog && recipeId != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.recipe_delete_confirm)) },
            text = { Text(stringResource(R.string.recipe_delete_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            recipeRepository.deleteRecipe(recipeId)
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
