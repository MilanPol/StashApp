package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stashapp.shared.domain.ExpirationDate
import com.stashapp.shared.domain.InventoryEntry
import com.stashapp.shared.domain.MeasurementUnit
import com.stashapp.shared.domain.Quantity
import com.stashapp.shared.domain.StorageLocation
import com.stashapp.shared.domain.Category
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    locations: List<StorageLocation>,
    categories: List<Category>,
    existingEntries: List<InventoryEntry>,
    onDismiss: () -> Unit,
    onSave: (InventoryEntry, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("") }
    
    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(MeasurementUnit.PIECES) }

    var expandedLocation by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf(locations.firstOrNull()) }

    var expandedCategory by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }

    var nameDropdownExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    val filteredSuggestions = existingEntries.filter { 
        it.name.contains(name, ignoreCase = true) && name.isNotBlank() 
    }.distinctBy { it.name }

    // Evaluation for "Merge Mode"
    val existingMatch = existingEntries.find {
        val selectedExpDate = datePickerState.selectedDateMillis?.let { ms -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() }
        it.name.equals(name, ignoreCase = true) &&
        it.storageLocationId == selectedLocation?.id &&
        it.quantity.unit == selectedUnit &&
        it.expirationDate?.date == selectedExpDate
    }
    
    var userWantsToMerge by remember { mutableStateOf(true) }
    val isMergeMode = existingMatch != null && userWantsToMerge

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxHeight(0.9f),
        title = { Text(if (isMergeMode) "Merge Inventory Item" else "Add Inventory Item") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Input with Autocomplete
                ExposedDropdownMenuBox(
                    expanded = nameDropdownExpanded,
                    onExpandedChange = { nameDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { 
                            name = it
                            nameDropdownExpanded = true
                        },
                        label = { Text("Item Name") },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true
                    )
                    if (filteredSuggestions.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = nameDropdownExpanded,
                            onDismissRequest = { nameDropdownExpanded = false }
                        ) {
                            filteredSuggestions.forEach { suggestion ->
                                DropdownMenuItem(
                                    text = { Text(suggestion.name) },
                                    onClick = {
                                        name = suggestion.name
                                        
                                        // Auto-fill parameters to instantly trigger merge mode logic
                                        if (suggestion.categoryId != null) {
                                            selectedCategory = categories.find { it.id == suggestion.categoryId }
                                        }
                                        if (suggestion.storageLocationId != null) {
                                            selectedLocation = locations.find { it.id == suggestion.storageLocationId }
                                        }
                                        selectedUnit = suggestion.quantity.unit
                                        
                                        datePickerState.selectedDateMillis = suggestion.expirationDate?.date
                                            ?.atStartOfDay(java.time.ZoneOffset.UTC)
                                            ?.toInstant()
                                            ?.toEpochMilli()
                                        
                                        nameDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(if (isMergeMode) "Amount to Add" else "Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = expandedUnit,
                        onExpandedChange = { expandedUnit = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = selectedUnit.name.lowercase(),
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUnit) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedUnit,
                            onDismissRequest = { expandedUnit = false }
                        ) {
                            MeasurementUnit.values().forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.name.lowercase()) },
                                    onClick = {
                                        selectedUnit = unit
                                        expandedUnit = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Location Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedLocation,
                    onExpandedChange = { expandedLocation = it }
                ) {
                    OutlinedTextField(
                        value = selectedLocation?.name ?: "Select Location",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Storage Location") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedLocation) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedLocation,
                        onDismissRequest = { expandedLocation = false }
                    ) {
                        locations.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text("${loc.icon} ${loc.name}") },
                                onClick = {
                                    selectedLocation = loc
                                    expandedLocation = false
                                }
                            )
                        }
                    }
                }

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = expandedCategory,
                    onExpandedChange = { expandedCategory = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category (Optional)") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                selectedCategory = null
                                expandedCategory = false
                            }
                        )
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("${cat.icon} ${cat.name}") },
                                onClick = {
                                    selectedCategory = cat
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                val selectedDateMillis = datePickerState.selectedDateMillis
                val expirationText = if (selectedDateMillis != null) {
                    val date = Instant.ofEpochMilli(selectedDateMillis).atZone(ZoneId.systemDefault()).toLocalDate()
                    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                    "Expires: ${date.format(formatter)}"
                } else "Set Expiration Date"
                
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(expirationText)
                }

                if (existingMatch != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = userWantsToMerge,
                            onCheckedChange = { userWantsToMerge = it }
                        )
                        Text(
                            text = "Merge with existing stock?",
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = quantityText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                    if (name.isNotBlank() && amount > BigDecimal.ZERO) {
                        val expDate = datePickerState.selectedDateMillis?.let { ms ->
                            val localDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                            ExpirationDate(localDate)
                        }

                        if (isMergeMode && existingMatch != null) {
                            val mergedEntry = existingMatch.copy(
                                quantity = Quantity(existingMatch.quantity.amount + amount, selectedUnit),
                                updatedAt = Instant.now()
                            )
                            onSave(mergedEntry, true)
                        } else {
                            val newEntry = InventoryEntry(
                                name = name,
                                quantity = Quantity(amount, selectedUnit),
                                expirationDate = expDate,
                                shelfLife = null,
                                storageLocationId = selectedLocation?.id,
                                categoryId = selectedCategory?.id
                            )
                            onSave(newEntry, false)
                        }
                    }
                }
            ) {
                Text(if (isMergeMode) "Add to Existing" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
