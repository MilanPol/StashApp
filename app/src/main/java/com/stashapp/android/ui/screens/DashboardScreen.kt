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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.shared.domain.InventoryRepository
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
    repository: InventoryRepository,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToGroup: (GroupingMode, String) -> Unit = { _, _ -> }
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
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    var expandedMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { expandedMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.nav_more_options), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    DropdownMenu(
                        expanded = expandedMenu,
                        onDismissRequest = { expandedMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_space)) },
                            onClick = {
                                showAddLocationDialog = true
                                expandedMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_add_category)) },
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
                icon = { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_item)) },
                text = { Text(stringResource(R.string.action_add_item)) }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            var searchQuery by remember { mutableStateOf("") }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_all_items)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            // Grouping Toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = groupingMode == GroupingMode.LOCATION,
                    onClick = { groupingMode = GroupingMode.LOCATION },
                    label = { Text(stringResource(R.string.group_by_location)) }
                )
                FilterChip(
                    selected = groupingMode == GroupingMode.CATEGORY,
                    onClick = { groupingMode = GroupingMode.CATEGORY },
                    label = { Text(stringResource(R.string.group_by_category)) }
                )
            }

            if (searchQuery.isNotBlank()) {
                val searchResults = entries.filter { it.name.contains(searchQuery, ignoreCase = true) }
                if (searchResults.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_items_found))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults, key = { it.id }) { entry ->
                            InventoryItemCard(
                                entry = entry,
                                onUpdate = { scope.launch { repository.updateEntry(it) } },
                                onDelete = { scope.launch { repository.removeEntry(entry.id) } },
                                onDetailsClick = { onNavigateToDetails(entry.id) }
                            )
                        }
                    }
                }
            } else {
                // Show grid of groups
                val groupItems = if (groupingMode == GroupingMode.LOCATION) {
                    locations.map { loc -> 
                        val count = entries.count { it.storageLocationId == loc.id }
                        loc.id to ("${loc.icon} ${loc.name}" to count) 
                    }
                } else {
                    categories.map { cat -> 
                        val count = entries.count { it.categoryId == cat.id }
                        cat.id to ("${cat.icon} ${cat.name}" to count) 
                    }
                }

                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(groupItems.size, key = { groupItems[it].first }) { index ->
                        val (id, data) = groupItems[index]
                        val (title, count) = data
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToGroup(groupingMode, id) },
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = title, style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(8.dp))
                                Text(text = stringResource(R.string.items_count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
            existingEntries = entries,
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
        title = { Text(stringResource(R.string.dialog_new_category_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hint_category_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text(stringResource(R.string.hint_icon_emoji)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(Category(name = name.ifBlank { "New Category" }, icon = icon))
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun AddLocationDialog(onDismiss: () -> Unit, onSave: (StorageLocation) -> Unit) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📦") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_new_space_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.hint_space_name)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = icon,
                    onValueChange = { icon = it },
                    label = { Text(stringResource(R.string.hint_icon_emoji)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(StorageLocation(name = name.ifBlank { "New Space" }, icon = icon))
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
            containerColor = if (isExpired) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
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
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (isExpired) {
                    Text(stringResource(R.string.label_expired), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                } else if (entry.isOpen) {
                    Text(stringResource(R.string.label_opened), color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(8.dp))
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = stringResource(R.string.expand),
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
                            stringResource(R.string.expires_on, entry.expirationDate?.date?.format(formatter) ?: ""),
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
                            TextButton(onClick = onDetailsClick) { Text(stringResource(R.string.action_view_details)) }
                            
                            Row {
                                if (!entry.isOpen) {
                                    OutlinedButton(onClick = { onUpdate(entry.open(Instant.now())) }) {
                                        Text(stringResource(R.string.action_open))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                }
                                Button(
                                    onClick = { showConsumeDialog = true }
                                ) {
                                    Text(stringResource(R.string.action_consume))
                                }
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
        title = { Text(stringResource(R.string.dialog_consume_title, entry.name)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_consume_description, entry.quantity.amount.toString(), entry.quantity.unit.name.lowercase()))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.label_amount)) },
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
                Text(stringResource(R.string.action_consume))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onConsumeFull,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.action_consume_entirely))
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
