package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.InventoryItemCard
import com.stashapp.shared.domain.InventoryEntryRepository
import com.stashapp.shared.domain.StorageLocationRepository
import com.stashapp.shared.domain.CategoryRepository
import com.stashapp.shared.domain.CatalogRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    entryRepository: InventoryEntryRepository,
    locationRepository: StorageLocationRepository,
    categoryRepository: CategoryRepository,
    catalogRepository: CatalogRepository,
    groupType: String,
    groupId: String,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (String) -> Unit
) {
    val entries by entryRepository.getAllEntries().collectAsState(initial = emptyList())
    val locations by locationRepository.getAllStorageLocations().collectAsState(initial = emptyList())
    val categories by categoryRepository.getCategories().collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val isLocation = groupType == "LOCATION"
    
    val title = if (isLocation) {
        val loc = locations.find { it.id == groupId }
        loc?.let { "${it.icon} ${it.name}" } ?: stringResource(R.string.label_unknown)
    } else {
        val cat = categories.find { it.id == groupId }
        cat?.let { "${it.icon} ${it.name}" } ?: stringResource(R.string.label_unknown)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
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
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_in_group, title)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search_hint)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                singleLine = true
            )
            
            val groupEntries = entries.filter { 
                if (isLocation) it.storageLocationId == groupId else it.categoryId == groupId 
            }.filter {
                it.name.contains(searchQuery, ignoreCase = true)
            }

            if (groupEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_items_found))
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(groupEntries, key = { it.id }) { entry ->
                        InventoryItemCard(
                            entry = entry,
                            onUpdate = { scope.launch { entryRepository.updateEntry(it) } },
                            onDelete = { scope.launch { entryRepository.removeEntry(entry.id) } },
                            onDetailsClick = { onNavigateToDetails(entry.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddItemScreen(
            entryRepository = entryRepository,
            catalogRepository = catalogRepository,
            locations = locations,
            categories = categories,
            existingEntries = entries,
            preSelectedLocationId = if (isLocation) groupId else null,
            preSelectedCategoryId = if (!isLocation) groupId else null,
            onDismiss = { showAddDialog = false },
            onMerge = { sourceId, targetId ->
                scope.launch { entryRepository.mergeEntries(sourceId, targetId) }
                showAddDialog = false
            },
            onSave = { newOrModifiedEntry, isMerge ->
                scope.launch { 
                    if (isMerge) entryRepository.updateEntry(newOrModifiedEntry)
                    else entryRepository.addEntry(newOrModifiedEntry) 
                }
                showAddDialog = false
            }
        )
    }
}
