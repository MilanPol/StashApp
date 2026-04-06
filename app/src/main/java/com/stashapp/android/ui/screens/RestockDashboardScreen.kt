package com.stashapp.android.ui.screens
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneId
import java.util.UUID

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.shared.domain.RestockItem
import com.stashapp.shared.domain.RestockItemStatus
import com.stashapp.shared.domain.StorageLocation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockDashboardScreen(
    viewModel: RestockDashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var itemToConfirm by remember { mutableStateOf<RestockItem?>(null) }
    var potentialMatch by remember { mutableStateOf<com.stashapp.shared.domain.InventoryEntry?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.restock_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_manual_item))
                    }
                    TextButton(onClick = {
                        viewModel.completeSession()
                        onNavigateBack()
                    }) {
                        Text(stringResource(R.string.action_ok))
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                Text(
                    text = stringResource(R.string.restock_instructions),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.items) { item ->
                        RestockItemCard(
                            item = item,
                            locations = uiState.locations,
                            onLocationSelected = { locId -> viewModel.updateItemLocation(item.id, locId) },
                            onConfirm = {
                                scope.launch {
                                    val match = viewModel.findPotentialMatch(item.name, item.catalogEan)
                                    if (match != null) {
                                        itemToConfirm = item
                                        potentialMatch = match
                                    } else {
                                        viewModel.confirmItem(item.id)
                                    }
                                }
                            },
                            onUpdateItem = { id, name, qty, expiry, ean ->
                                viewModel.updateItem(id, name, qty, expiry, ean)
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        com.stashapp.android.ui.components.RestockItemSelectionDialog(
            onDismiss = { showAddDialog = false },
            onProductSelected = { product, qty ->
                viewModel.addItem(
                    name = product.name,
                    quantity = com.stashapp.shared.domain.Quantity(java.math.BigDecimal.valueOf(qty), com.stashapp.shared.domain.MeasurementUnit.PIECES),
                    catalogEan = product.ean
                )
                showAddDialog = false
            },
            onManualAdd = { name, qty ->
                viewModel.addItem(
                    name = name,
                    quantity = com.stashapp.shared.domain.Quantity(java.math.BigDecimal.valueOf(qty), com.stashapp.shared.domain.MeasurementUnit.PIECES),
                    catalogEan = null
                )
                showAddDialog = false
            },
            searchCatalog = { viewModel.searchCatalog(it) },
            getProductByEan = { viewModel.getProductByEan(it).first() }
        )
    }

    itemToConfirm?.let { item ->
        potentialMatch?.let { match ->
            MergeChoiceDialog(
                itemName = item.name,
                matchLocationName = uiState.locations.find { it.id == match.storageLocationId }?.name ?: "Unknown",
                onDismiss = {
                    itemToConfirm = null
                    potentialMatch = null
                },
                onMerge = {
                    viewModel.confirmItem(item.id, mergeWithId = match.id)
                    itemToConfirm = null
                    potentialMatch = null
                },
                onAddUnique = {
                    viewModel.confirmItem(item.id)
                    itemToConfirm = null
                    potentialMatch = null
                }
            )
        }
    }
}

@Composable
fun MergeChoiceDialog(
    itemName: String,
    matchLocationName: String,
    onDismiss: () -> Unit,
    onMerge: () -> Unit,
    onAddUnique: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_restock_merge_title)) },
        text = {
            Text(stringResource(R.string.dialog_restock_merge_message, matchLocationName))
        },
        confirmButton = {
            Button(onClick = onMerge) {
                Text(stringResource(R.string.action_merge))
            }
        },
        dismissButton = {
            TextButton(onClick = onAddUnique) {
                Text(stringResource(R.string.action_add_unique))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockItemCard(
    item: RestockItem,
    locations: List<StorageLocation>,
    onLocationSelected: (String) -> Unit,
    onConfirm: () -> Unit,
    onUpdateItem: (String, String, com.stashapp.shared.domain.Quantity, java.time.Instant?, String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val isConfirmed = item.status == RestockItemStatus.CONFIRMED

    // Edit state
    var editName by remember(item) { mutableStateOf(item.name) }
    var editAmount by remember(item) { mutableStateOf(item.quantity.amount.toString()) }
    var editUnit by remember(item) { mutableStateOf(item.quantity.unit) }
    var editExpirationDate by remember(item) { mutableStateOf(item.expirationDate) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = editExpirationDate?.toEpochMilli() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    editExpirationDate = datePickerState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it) }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isConfirmed) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Item Name") },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    } else {
                        Text(text = item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${item.quantity.amount} ${item.quantity.unit.name.lowercase()}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        item.expirationDate?.let {
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                .withZone(java.time.ZoneId.systemDefault())
                            Text(
                                text = "Expires: ${formatter.format(it)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                
                if (isConfirmed) {
                    Icon(Icons.Default.Check, contentDescription = "Confirmed", tint = Color(0xFF2E7D32))
                } else {
                    Row {
                        if (isEditing) {
                            IconButton(onClick = {
                                val qty = com.stashapp.shared.domain.Quantity(
                                    java.math.BigDecimal(editAmount.ifBlank { "0" }),
                                    editUnit
                                )
                                onUpdateItem(item.id, editName, qty, editExpirationDate, item.catalogEan)
                                isEditing = false
                            }) {
                                Icon(Icons.Default.Save, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            Button(
                                onClick = onConfirm,
                                enabled = item.locationId != null,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("Add")
                            }
                        }
                    }
                }
            }

            if (isEditing) {
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editAmount,
                        onValueChange = { editAmount = it },
                        label = { Text("Amount") },
                        modifier = Modifier.weight(0.4f),
                        singleLine = true
                    )
                    
                    Box(modifier = Modifier.weight(0.6f)) {
                        OutlinedTextField(
                            value = editUnit.name,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Unit") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                            modifier = Modifier.fillMaxWidth().clickable { unitMenuExpanded = true }
                        )
                        DropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            com.stashapp.shared.domain.MeasurementUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.name) },
                                    onClick = {
                                        editUnit = unit
                                        unitMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editExpirationDate?.let {
                        java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(it)
                    } ?: "None set",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Expiration Date") },
                    leadingIcon = { Icon(Icons.Default.Event, null) },
                    modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true },
                    trailingIcon = {
                        if (editExpirationDate != null) {
                            IconButton(onClick = { editExpirationDate = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }

            if (!isConfirmed && !isEditing) {
                Spacer(Modifier.height(12.dp))
                
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    val currentLocation = locations.find { it.id == item.locationId }
                    OutlinedTextField(
                        value = currentLocation?.let { "${it.icon} ${it.name}" } ?: "Select Location",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Storage Location") },
                        leadingIcon = { Icon(Icons.Default.LocationOn, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        locations.forEach { location ->
                            DropdownMenuItem(
                                text = { Text("${location.icon} ${location.name}") },
                                onClick = {
                                    onLocationSelected(location.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                val loc = locations.find { it.id == item.locationId }
                if (loc != null) {
                    Text(
                        text = "Stored in: ${loc.icon} ${loc.name}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
