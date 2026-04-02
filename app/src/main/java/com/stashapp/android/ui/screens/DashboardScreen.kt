package com.stashapp.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stashapp.shared.data.InMemoryInventoryRepository
import com.stashapp.shared.domain.InventoryEntry
import com.stashapp.shared.domain.StorageLocation
import com.stashapp.shared.domain.Category
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

enum class GroupingMode {
    LOCATION, CATEGORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: InMemoryInventoryRepository,
    onNavigateToDetails: (String) -> Unit
) {
    val entries by repository.getAllEntries().collectAsState(initial = emptyList())
    val locations by repository.getStorageLocations().collectAsState(initial = emptyList())
    val categories by repository.getCategories().collectAsState(initial = emptyList())
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showAddLocationDialog by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var groupingMode by remember { mutableStateOf(GroupingMode.LOCATION) }
    
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StashApp") },
                actions = {
                    var expandedMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More Options", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add Space") },
                            onClick = {
                                showAddLocationDialog = true
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add Category") },
                            onClick = {
                                showAddCategoryDialog = true
                                expandedMenu = false
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = "Add Item") },
                text = { Text("Add Item") }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Grouping Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = groupingMode == GroupingMode.LOCATION,
                    onClick = { groupingMode = GroupingMode.LOCATION },
                    label = { Text("By Location") }
                )
                FilterChip(
                    selected = groupingMode == GroupingMode.CATEGORY,
                    onClick = { groupingMode = GroupingMode.CATEGORY },
                    label = { Text("By Category") }
                )
            }

            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your stash is empty. Add some items!", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                val grouped = if (groupingMode == GroupingMode.LOCATION) {
                    entries.groupBy { it.storageLocationId }
                } else {
                    entries.groupBy { it.categoryId }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.forEach { (groupId, items) ->
                        val groupTitle = if (groupingMode == GroupingMode.LOCATION) {
                            val loc = locations.find { it.id == groupId }
                            loc?.let { "${it.icon} ${it.name}" } ?: "Unassigned Location"
                        } else {
                            val cat = categories.find { it.id == groupId }
                            cat?.let { "${it.icon} ${it.name}" } ?: "Uncategorized"
                        }

                        item {
                            Text(
                                text = groupTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                        }
                        items(items, key = { it.id }) { entry ->
                            InventoryItemCard(
                                entry = entry,
                                onUpdate = { scope.launch { repository.updateEntry(it) } },
                                onDelete = { scope.launch { repository.removeEntry(entry.id) } },
                                onDetailsClick = { onNavigateToDetails(entry.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemScreen(
            locations = locations,
            categories = categories,
            existingEntries = entries, // pass down to power Autocomplete/Merge
            onDismiss = { showAddDialog = false },
            onSave = { newOrModifiedEntry, isMerge ->
                scope.launch { 
                    if (isMerge) repository.updateEntry(newOrModifiedEntry)
                    else repository.addEntry(newOrModifiedEntry) 
                }
                showAddDialog = false
            }
        )
    }

    if (showAddLocationDialog) {
        AddLocationDialog(
            onDismiss = { showAddLocationDialog = false },
            onSave = { newSpace -> 
                scope.launch { repository.addStorageLocation(newSpace) }
                showAddLocationDialog = false
            }
        )
    }

    if (showAddCategoryDialog) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onSave = { newCat -> 
                scope.launch { repository.addCategory(newCat) }
                showAddCategoryDialog = false
            }
        )
    }
}

@Composable
fun AddCategoryDialog(onDismiss: () -> Unit, onSave: (Category) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📁") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name (e.g. Meat)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (Emoji)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(Category(name = name.ifBlank { "New Category" }, icon = icon))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddLocationDialog(onDismiss: () -> Unit, onSave: (StorageLocation) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📦") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Storage Space") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Space Name (e.g. Fridge)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text("Icon (Emoji)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(StorageLocation(name = name.ifBlank { "New Space" }, icon = icon))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun InventoryItemCard(
    entry: InventoryEntry,
    onUpdate: (InventoryEntry) -> Unit,
    onDelete: () -> Unit,
    onDetailsClick: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showConsumeDialog by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")
    val isExpired = entry.expirationDate?.isExpired(LocalDate.now()) == true

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.clickable { expanded = !expanded }.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = entry.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${entry.quantity.amount} ${entry.quantity.unit.name.lowercase()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isExpired) {
                    Text("Expired", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                } else if (entry.isOpen) {
                    Text("Opened", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Expand",
                    modifier = Modifier.rotate(rotation)
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    Divider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    
                    if (entry.expirationDate != null) {
                        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        Text(
                            "Expires: ${entry.expirationDate?.date?.format(formatter)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(onClick = {
                                    val remaining = entry.quantity.amount - BigDecimal.ONE
                                    if (remaining <= BigDecimal.ZERO) onDelete()
                                    else onUpdate(entry.copy(quantity = entry.quantity.copy(amount = remaining)))
                                }) { Text("-") }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = {
                                    val sum = entry.quantity.amount + BigDecimal.ONE
                                    onUpdate(entry.copy(quantity = entry.quantity.copy(amount = sum)))
                                }) { Text("+") }
                            }
                            TextButton(onClick = onDetailsClick) { Text("Details") }
                        }
                        
                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (!entry.isOpen) {
                                Button(onClick = { onUpdate(entry.open(Instant.now())) }) {
                                    Text("Open")
                                }
                                Spacer(Modifier.width(8.dp))
                            }
                            Button(
                                onClick = { showConsumeDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Text("Consume")
                            }
                        }
                    }
                }
            }
        }
    }
    
    if (showConsumeDialog) {
        ConsumeDialog(
            entry = entry,
            onDismiss = { showConsumeDialog = false },
            onConsumeFull = {
                onDelete()
                showConsumeDialog = false
            },
            onConsumePartial = { amount ->
                val updatedEntry = entry.consume(amount, Instant.now())
                if (updatedEntry.quantity.amount <= BigDecimal.ZERO) {
                    onDelete()
                } else {
                    onUpdate(updatedEntry)
                }
                showConsumeDialog = false
            }
        )
    }
}

@Composable
fun ConsumeDialog(
    entry: InventoryEntry,
    onDismiss: () -> Unit,
    onConsumeFull: () -> Unit,
    onConsumePartial: (BigDecimal) -> Unit
) {
    var amountText by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Consume ${entry.name}") },
        text = {
            Column {
                Text("How much did you use? (Current: ${entry.quantity.amount} ${entry.quantity.unit.name.lowercase()})")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val amount = amountText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (amount > BigDecimal.ZERO) {
                    onConsumePartial(amount)
                }
            }) {
                Text("Consume")
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onConsumeFull,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Consume Entirely")
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
