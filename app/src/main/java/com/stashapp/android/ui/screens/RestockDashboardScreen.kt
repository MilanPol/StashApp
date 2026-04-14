package com.stashapp.android.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.RestockItemSelectionDialog
import com.stashapp.android.ui.components.translated
import com.stashapp.shared.domain.RestockItem
import com.stashapp.shared.domain.RestockItemStatus
import com.stashapp.shared.domain.StorageLocation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockDashboardScreen(
    viewModel: RestockDashboardViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var itemToConfirm by remember { mutableStateOf<RestockItem?>(null) }
    var potentialMatch by remember { mutableStateOf<com.stashapp.shared.domain.InventoryEntry?>(null) }
    val scope = rememberCoroutineScope()

    BackHandler {
        if (uiState.items.any { it.status != RestockItemStatus.CONFIRMED }) {
            showConfirmationDialog = true
        } else {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.restock_title),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.items.any { it.status != RestockItemStatus.CONFIRMED }) {
                            showConfirmationDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.nav_back))
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
                    if (uiState.items.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.shopping_list_empty_title))
                            }
                        }
                    } else {
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
                                },
                                onExpirationDateSelected = { date ->
                                    viewModel.updateItemExpirationDate(item.id, date)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RestockItemSelectionDialog(
            onDismiss = { showAddDialog = false },
            onProductSelected = { product, qty, unit ->
                viewModel.addItem(
                    name = product.name,
                    quantity = com.stashapp.shared.domain.Quantity(java.math.BigDecimal.valueOf(qty), unit),
                    catalogEan = product.ean
                )
                showAddDialog = false
            },
            onManualAdd = { name, qty, unit ->
                viewModel.addItem(
                    name = name,
                    quantity = com.stashapp.shared.domain.Quantity(java.math.BigDecimal.valueOf(qty), unit),
                    catalogEan = null
                )
                showAddDialog = false
            },
            searchCatalog = { viewModel.searchCatalog(it) },
            getProductByEan = { viewModel.getProductByEan(it).first() }
        )
    }

    if (showConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text(stringResource(R.string.restock_exit_confirmation_title)) },
            text = { Text(stringResource(R.string.restock_exit_confirmation_message)) },
            confirmButton = {
                TextButton(onClick = onNavigateBack) {
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
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
    onUpdateItem: (String, String, com.stashapp.shared.domain.Quantity, java.time.Instant?, String?) -> Unit,
    onExpirationDateSelected: (java.time.Instant?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(false) }
    val isConfirmed = item.status == RestockItemStatus.CONFIRMED

    var editName by remember(item) { mutableStateOf(item.name) }
    var editAmount by remember(item) { mutableStateOf(item.quantity.amount.toString()) }
    var editUnit by remember(item) { mutableStateOf(item.quantity.unit) }
    var editExpirationDate by remember(item) { mutableStateOf(item.expirationDate) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var unitMenuExpanded by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (if (isEditing) editExpirationDate else item.expirationDate)?.toEpochMilli() ?: System.currentTimeMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val selectedDate = datePickerState.selectedDateMillis?.let { java.time.Instant.ofEpochMilli(it) }
                    if (isEditing) {
                        editExpirationDate = selectedDate
                    } else {
                        onExpirationDateSelected(selectedDate)
                    }
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isConfirmed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text(stringResource(R.string.label_item_name)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                    } else {
                        Text(text = item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            text = "${item.quantity.amount} ${item.quantity.unit.translated()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        item.expirationDate?.let {
                            val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                .withZone(java.time.ZoneId.systemDefault())
                            Text(
                                text = stringResource(R.string.expires_on, formatter.format(it)),
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
                                Text(stringResource(R.string.action_add_short))
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
                        label = { Text(stringResource(R.string.label_amount)) },
                        modifier = Modifier.weight(0.4f),
                        singleLine = true
                    )
                    
                    ExposedDropdownMenuBox(
                        expanded = unitMenuExpanded,
                        onExpandedChange = { unitMenuExpanded = !unitMenuExpanded },
                        modifier = Modifier.weight(0.6f)
                    ) {
                        OutlinedTextField(
                            value = editUnit.translated(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.unit)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        ExposedDropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false }
                        ) {
                            com.stashapp.shared.domain.MeasurementUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(unit.translated()) },
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
                Box(modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }) {
                    OutlinedTextField(
                        value = editExpirationDate?.let {
                            java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                .withZone(java.time.ZoneId.systemDefault())
                                .format(it)
                        } ?: stringResource(R.string.label_none),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text(stringResource(R.string.label_expiration_date)) },
                        leadingIcon = { Icon(Icons.Default.Event, null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        trailingIcon = {
                            if (editExpirationDate != null) {
                                IconButton(onClick = { editExpirationDate = null }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        }
                    )
                }
            }

            if (!isConfirmed && !isEditing) {
                Spacer(Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            val currentLocation = locations.find { it.id == item.locationId }
                            OutlinedTextField(
                                value = currentLocation?.let { "${it.icon} ${it.name}" } ?: stringResource(R.string.label_select_location),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.label_storage_location)) },
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
                    }

                    OutlinedIconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.padding(top = 8.dp).size(56.dp),
                        shape = MaterialTheme.shapes.extraSmall,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(
                            Icons.Default.Event, 
                            contentDescription = stringResource(R.string.label_set_expiration_date),
                            tint = if (item.expirationDate != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val loc = locations.find { it.id == item.locationId }
                if (loc != null) {
                    Text(
                        text = stringResource(R.string.label_stored_in, "${loc.icon} ${loc.name}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
