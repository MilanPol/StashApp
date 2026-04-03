package com.stashapp.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
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
import com.stashapp.android.ui.components.translated
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
    
    var editingLocation by remember { mutableStateOf<StorageLocation?>(null) }
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var deletingLocation by remember { mutableStateOf<StorageLocation?>(null) }
    var deletingCategory by remember { mutableStateOf<Category?>(null) }
    
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
                        loc to count
                    }
                } else {
                    categories.map { cat -> 
                        val count = entries.count { it.categoryId == cat.id }
                        cat to count
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = groupItems,
                        key = { (item, _) -> 
                            when (item) {
                                is StorageLocation -> "loc_${item.id}"
                                is Category -> "cat_${item.id}"
                                else -> item.hashCode()
                            }
                        }
                    ) { (item, count) ->
                        val (id, title, icon) = when (item) {
                            is StorageLocation -> Triple(item.id, item.name, item.icon)
                            is Category -> Triple(item.id, item.name, item.icon)
                            else -> Triple("", "", "")
                        }
                        
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNavigateToGroup(groupingMode, id) },
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                var menuExpanded by remember { mutableStateOf(false) }
                                
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = "$icon $title", style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.height(8.dp))
                                    Text(text = stringResource(R.string.items_count, count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                
                                Box(modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)) {
                                    IconButton(onClick = { menuExpanded = true }) {
                                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.nav_more_options))
                                    }
                                    DropdownMenu(
                                        expanded = menuExpanded,
                                        onDismissRequest = { menuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_edit)) },
                                            onClick = {
                                                if (item is StorageLocation) editingLocation = item
                                                else if (item is Category) editingCategory = item
                                                menuExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) },
                                            onClick = {
                                                if (item is StorageLocation) deletingLocation = item
                                                else if (item is Category) deletingCategory = item
                                                menuExpanded = false
                                            }
                                        )
                                    }
                                }
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

    if (showAddCategoryDialog || editingCategory != null) {
        val initialCategory = editingCategory
        AddCategoryDialog(
            initialCategory = initialCategory,
            onDismiss = { 
                showAddCategoryDialog = false
                editingCategory = null
            },
            onSave = { cat -> 
                scope.launch { 
                    if (initialCategory != null) repository.updateCategory(cat)
                    else repository.addCategory(cat) 
                }
                showAddCategoryDialog = false
                editingCategory = null
            }
        )
    }

    if (showAddLocationDialog || editingLocation != null) {
        val initialLocation = editingLocation
        AddLocationDialog(
            initialLocation = initialLocation,
            onDismiss = { 
                showAddLocationDialog = false
                editingLocation = null
            },
            onSave = { loc -> 
                scope.launch { 
                    if (initialLocation != null) repository.updateStorageLocation(loc)
                    else repository.addStorageLocation(loc) 
                }
                showAddLocationDialog = false
                editingLocation = null
            }
        )
    }

    if (deletingLocation != null) {
        val locationToDelete = deletingLocation!!
        AlertDialog(
            onDismissRequest = { deletingLocation = null },
            title = { Text(stringResource(R.string.dialog_delete_space_title)) },
            text = { Text(stringResource(R.string.delete_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.removeStorageLocation(locationToDelete.id) }
                        deletingLocation = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingLocation = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (deletingCategory != null) {
        val categoryToDelete = deletingCategory!!
        AlertDialog(
            onDismissRequest = { deletingCategory = null },
            title = { Text(stringResource(R.string.dialog_delete_category_title)) },
            text = { Text(stringResource(R.string.delete_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { repository.removeCategory(categoryToDelete.id) }
                        deletingCategory = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingCategory = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
fun AddCategoryDialog(
    initialCategory: Category? = null,
    onDismiss: () -> Unit, 
    onSave: (Category) -> Unit
) {
    var name by remember { mutableStateOf(initialCategory?.name ?: "") }
    var icon by remember { mutableStateOf(initialCategory?.icon ?: "📁") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialCategory != null) stringResource(R.string.action_edit) else stringResource(R.string.dialog_new_category_title)) },
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
                onSave(initialCategory?.copy(name = name.ifBlank { "New Category" }, icon = icon) 
                    ?: Category(name = name.ifBlank { "New Category" }, icon = icon))
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
fun AddLocationDialog(
    initialLocation: StorageLocation? = null,
    onDismiss: () -> Unit, 
    onSave: (StorageLocation) -> Unit
) {
    var name by remember { mutableStateOf(initialLocation?.name ?: "") }
    var icon by remember { mutableStateOf(initialLocation?.icon ?: "📦") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialLocation != null) stringResource(R.string.action_edit) else stringResource(R.string.dialog_new_space_title)) },
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
                onSave(initialLocation?.copy(name = name.ifBlank { "New Space" }, icon = icon)
                    ?: StorageLocation(name = name.ifBlank { "New Space" }, icon = icon))
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
                        text = "${entry.quantity.amount} ${entry.quantity.unit.translated().lowercase()}",
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
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                Text(stringResource(R.string.dialog_consume_description, entry.quantity.amount.toString(), entry.quantity.unit.translated().lowercase()))
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
