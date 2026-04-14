package com.stashapp.android.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import com.stashapp.android.ui.components.ExpiringItemsDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.stashapp.android.util.StateSavers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.translated
import com.stashapp.android.ui.components.InventoryItemCard
import com.stashapp.shared.domain.InventoryEntry
import com.stashapp.shared.domain.StorageLocation
import com.stashapp.shared.domain.Category
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

enum class GroupingMode {
    LOCATION, CATEGORY
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToDetails: (String) -> Unit,
    onNavigateToGroup: (GroupingMode, String) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit = {},
    onNavigateToRecipes: () -> Unit = {},
    onNavigateToShoppingList: () -> Unit = {}
) {
    val entries by viewModel.entries.collectAsState()
    val activeLocationId by viewModel.activeLocationId.collectAsState()
    val locations by viewModel.locations.collectAsState()
    val allLocations by viewModel.allLocations.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val expiringEntries by viewModel.expiringEntries.collectAsState()
    val globalLeadDays by viewModel.globalLeadDays.collectAsState()
    
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showExpiringDialog by rememberSaveable { mutableStateOf(false) }
    var showAddLocationDialog by rememberSaveable { mutableStateOf(false) }
    var showAddCategoryDialog by rememberSaveable { mutableStateOf(false) }
    
    var editingLocation: StorageLocation? by rememberSaveable(stateSaver = StateSavers.nullableSaver(StateSavers.StorageLocationSaver)) { mutableStateOf<StorageLocation?>(null) }
    var editingCategory: Category? by rememberSaveable(stateSaver = StateSavers.nullableSaver(StateSavers.CategorySaver)) { mutableStateOf<Category?>(null) }
    var deletingLocation: StorageLocation? by rememberSaveable(stateSaver = StateSavers.nullableSaver(StateSavers.StorageLocationSaver)) { mutableStateOf<StorageLocation?>(null) }
    var deletingCategory: Category? by rememberSaveable(stateSaver = StateSavers.nullableSaver(StateSavers.CategorySaver)) { mutableStateOf<Category?>(null) }
    
    var groupingMode by rememberSaveable { mutableStateOf(GroupingMode.LOCATION) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (activeLocationId != null) {
                        IconButton(onClick = {
                            val parentId = allLocations.find { it.id == activeLocationId }?.parentId
                            viewModel.setActiveLocation(parentId)
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                },
                title = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_stash),
                            contentDescription = stringResource(R.string.app_name),
                            modifier = Modifier
                                .height(56.dp)
                                .padding(vertical = 4.dp), // Tiny vertical padding for perfect fit
                            contentScale = ContentScale.Fit
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showExpiringDialog = true }) {
                        BadgedBox(
                            badge = {
                                if (expiringEntries.isNotEmpty()) {
                                    Badge {
                                        Text(expiringEntries.size.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Filled.Notifications,
                                contentDescription = stringResource(R.string.nav_notifications),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    IconButton(onClick = onNavigateToRecipes) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = stringResource(R.string.nav_recipes),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    IconButton(onClick = onNavigateToShoppingList) {
                        Icon(
                            Icons.Default.ShoppingCart,
                            contentDescription = stringResource(R.string.nav_shopping_list),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

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
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.screen_title_settings)) },
                            onClick = {
                                onNavigateToSettings()
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
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp, end = 16.dp)
            ) {
                // Add Space (Secondary) - Just Text
                ExtendedFloatingActionButton(
                    onClick = { showAddLocationDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Text(stringResource(R.string.action_add_space))
                }

                // Add Item (Primary) - Just Text
                ExtendedFloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Text(stringResource(R.string.action_add_item))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            var searchQuery by remember { mutableStateOf("") }
            var isScanningBarcode by remember { mutableStateOf(false) }
            
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_all_items)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_hint)) },
                trailingIcon = {
                    IconButton(onClick = { isScanningBarcode = !isScanningBarcode }) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = "Scan Barcode")
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )

            AnimatedVisibility(visible = isScanningBarcode) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                    tonalElevation = 6.dp
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        com.stashapp.android.ui.components.BarcodeScannerView(
                            onBarcodeDetected = { ean ->
                                isScanningBarcode = false
                                scope.launch {
                                    val product = viewModel.getProductByEan(ean).firstOrNull()
                                    if (product != null) {
                                        searchQuery = product.name
                                    } else {
                                        snackbarHostState.showSnackbar("No product found for barcode: $ean")
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Breadcrumbs / Location Context
            if (activeLocationId != null) {
                val path = mutableListOf<StorageLocation>()
                var currId: String? = activeLocationId
                while (currId != null) {
                    val loc = allLocations.find { it.id == currId }
                    if (loc != null) {
                        path.add(0, loc)
                        currId = loc.parentId
                    } else break
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🏠", 
                        modifier = Modifier.clickable { viewModel.setActiveLocation(null) }
                    )
                    path.forEach { loc ->
                        Text(" > ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = loc.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (loc.id == activeLocationId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.clickable { viewModel.setActiveLocation(loc.id) }
                        )
                    }
                }
            }

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
                        contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(searchResults, key = { it.id }) { entry ->
                            InventoryItemCard(
                                entry = entry,
                                onUpdate = { viewModel.updateEntry(it) },
                                onDelete = { viewModel.removeEntry(entry.id) },
                                onDetailsClick = { onNavigateToDetails(entry.id) }
                            )
                        }
                    }
                }
            } else {
                // Show grid of groups
                val groupItems = if (groupingMode == GroupingMode.LOCATION) {
                    locations.map { loc -> 
                        // Recursive count helper
                        fun getDescendantIds(id: String): List<String> {
                            val children = allLocations.filter { it.parentId == id }
                            return children.map { it.id } + children.flatMap { getDescendantIds(it.id) }
                        }
                        val allIds = listOf(loc.id) + getDescendantIds(loc.id)
                        val count = entries.count { it.storageLocationId in allIds }
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
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 120.dp),
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
                                .clickable { 
                                    if (item is StorageLocation) {
                                        viewModel.setActiveLocation(item.id)
                                    } else {
                                        onNavigateToGroup(groupingMode, id)
                                    }
                                },
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

                    
                    // If we are in a location, show items directly in this location at the bottom of the grid
                    if (groupingMode == GroupingMode.LOCATION && activeLocationId != null) {
                        val localItems = entries.filter { it.storageLocationId == activeLocationId }
                        
                        if (groupItems.isEmpty() && localItems.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(stringResource(R.string.empty_space_title), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(onClick = { showAddDialog = true }) {
                                            Icon(Icons.Default.Add, null)
                                            Spacer(Modifier.width(4.dp))
                                            Text(stringResource(R.string.action_add_item))
                                        }
                                    }
                                }
                            }
                        }

                        if (localItems.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(
                                    stringResource(R.string.inventory_items_header), 
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                                )
                            }
                            items(localItems, key = { "item_${it.id}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                                InventoryItemCard(
                                    entry = entry,
                                    onUpdate = { viewModel.updateEntry(it) },
                                    onDelete = { viewModel.removeEntry(entry.id) },
                                    onDetailsClick = { onNavigateToDetails(entry.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemScreen(
            onSearchCatalog = { viewModel.searchCatalog(it) },
            onGetProductByEan = { viewModel.getProductByEan(it) },
            onUpsertCatalogProduct = { viewModel.upsertCatalogProduct(it) },
            onLinkEntryToProduct = { entryId, ean -> viewModel.linkEntryToProduct(entryId, ean) },
            onGetLinkedEan = { viewModel.getLinkedEan(it) },
            locations = allLocations,
            categories = categories,
            existingEntries = entries,
            globalLeadDays = globalLeadDays,
            preSelectedLocationId = activeLocationId,
            preSelectedCategoryId = null,
            onDismiss = { showAddDialog = false },
            onMerge = { sourceId, targetId ->
                viewModel.mergeEntries(sourceId, targetId)
                showAddDialog = false
            },
            onSave = { newOrModifiedEntry, isMerge ->
                if (isMerge) viewModel.updateEntry(newOrModifiedEntry)
                else viewModel.addEntry(newOrModifiedEntry)
                showAddDialog = false
            }
        )
    }

    if (showExpiringDialog) {
        ExpiringItemsDialog(
            entries = expiringEntries,
            onUpdate = { viewModel.updateEntry(it) },
            onDelete = { viewModel.removeEntry(it) },
            onDismiss = { showExpiringDialog = false },
            onNavigateToDetails = onNavigateToDetails
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
                if (initialCategory != null) viewModel.updateCategory(cat)
                else viewModel.addCategory(cat)
                showAddCategoryDialog = false
                editingCategory = null
            }
        )
    }

    if (showAddLocationDialog || editingLocation != null) {
        val initialLocation = editingLocation
        AddLocationDialog(
            initialLocation = initialLocation,
            allLocations = allLocations,
            suggestedParentId = activeLocationId,
            onDismiss = { 
                showAddLocationDialog = false
                editingLocation = null
            },
            onSave = { loc -> 
                if (initialLocation != null) viewModel.updateLocation(loc)
                else viewModel.addLocation(loc)
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
                        viewModel.removeLocation(locationToDelete.id)
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
                        viewModel.removeCategory(categoryToDelete.id)
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
    var name by rememberSaveable { mutableStateOf(initialCategory?.name ?: "") }
    var icon by rememberSaveable { mutableStateOf(initialCategory?.icon ?: "📁") }

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationDialog(
    initialLocation: StorageLocation? = null,
    allLocations: List<StorageLocation>,
    suggestedParentId: String? = null,
    onDismiss: () -> Unit, 
    onSave: (StorageLocation) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialLocation?.name ?: "") }
    var icon by rememberSaveable { mutableStateOf(initialLocation?.icon ?: "📦") }
    var parentId by rememberSaveable { mutableStateOf(initialLocation?.parentId ?: suggestedParentId) }
    var expandedParentPicker by rememberSaveable { mutableStateOf(false) }

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
                Spacer(Modifier.height(8.dp))
                
                // Parent Selection
                ExposedDropdownMenuBox(
                    expanded = expandedParentPicker,
                    onExpandedChange = { expandedParentPicker = !expandedParentPicker }
                ) {
                    val parentName = allLocations.find { it.id == parentId }?.name ?: stringResource(R.string.location_none_root)
                    OutlinedTextField(
                        value = parentName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.hint_parent_location)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedParentPicker) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedParentPicker,
                        onDismissRequest = { expandedParentPicker = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.location_none_root)) },
                            onClick = {
                                parentId = null
                                expandedParentPicker = false
                            }
                        )
                        allLocations.filter { it.id != initialLocation?.id }.forEach { loc ->
                            DropdownMenuItem(
                                text = { Text(loc.name) },
                                onClick = {
                                    parentId = loc.id
                                    expandedParentPicker = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(initialLocation?.copy(name = name.ifBlank { "New Space" }, icon = icon, parentId = parentId)
                    ?: StorageLocation(name = name.ifBlank { "New Space" }, icon = icon, parentId = parentId))
            }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
