package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.translated
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
import com.stashapp.android.ui.components.BarcodeScannerView
import com.stashapp.shared.domain.CatalogProduct
import com.stashapp.shared.domain.InventoryRepository
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

enum class AddMode { UNDEFINED, MANUAL, SCAN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemScreen(
    repository: InventoryRepository,
    locations: List<StorageLocation>,
    categories: List<Category>,
    existingEntries: List<InventoryEntry>,
    initialEntry: InventoryEntry? = null,
    preSelectedLocationId: String? = null,
    preSelectedCategoryId: String? = null,
    globalLeadDays: Int = 2,
    onDismiss: () -> Unit,
    onSave: (InventoryEntry, Boolean) -> Unit
) {
    var mode by remember { mutableStateOf(if (initialEntry == null) AddMode.UNDEFINED else AddMode.MANUAL) }
    
    var name by remember { mutableStateOf(initialEntry?.name ?: "") }
    var scannedBarcode by remember { mutableStateOf<String?>(null) }
    var quantityText by remember { mutableStateOf(initialEntry?.quantity?.amount?.toString() ?: "") }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var catalogSearchResults by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    
    LaunchedEffect(name) {
        if (name.length >= 2) {
            repository.searchCatalog(name).collect { results ->
                catalogSearchResults = results
            }
        } else {
            catalogSearchResults = emptyList()
        }
    }
    var expandedUnit by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(initialEntry?.quantity?.unit ?: MeasurementUnit.PIECES) }

    var expandedLocation by remember { mutableStateOf(false) }
    var selectedLocation by remember { mutableStateOf(
        initialEntry?.storageLocationId?.let { id -> locations.find { it.id == id } }
        ?: locations.find { it.id == preSelectedLocationId } 
        ?: locations.firstOrNull()
    ) }

    var expandedCategory by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<Category?>(
        initialEntry?.categoryId?.let { id -> categories.find { it.id == id } }
        ?: categories.find { it.id == preSelectedCategoryId }
    ) }

    var nameDropdownExpanded by remember { mutableStateOf(false) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showAlertDialogPicker by remember { mutableStateOf(false) }

    val initialDateMillis = initialEntry?.expirationDate?.date
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateMillis)
    val alertDatePickerState = rememberDatePickerState(initialSelectedDateMillis = initialEntry?.alertAt?.toEpochMilli())

    var wantsNotification by remember { mutableStateOf(initialEntry?.alertAt != null || initialEntry?.expirationDate != null) }
    var customAlertDateSet by remember { mutableStateOf(initialEntry?.alertAt != null) }

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
        confirmButton = {
            if (mode != AddMode.UNDEFINED) {
                Button(
                    onClick = {
                        val amount = quantityText.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        if (name.isNotBlank() && amount > BigDecimal.ZERO) {
                            val expDate = datePickerState.selectedDateMillis?.let { ms ->
                                val localDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                                ExpirationDate(localDate)
                            }

                            val calculatedAlertAt = if (wantsNotification) {
                                val alertMs = if (customAlertDateSet) {
                                    alertDatePickerState.selectedDateMillis
                                } else {
                                    datePickerState.selectedDateMillis?.let { expMs ->
                                        val leadDays = selectedCategory?.defaultLeadDays ?: globalLeadDays
                                        Instant.ofEpochMilli(expMs).minus(java.time.Duration.ofDays(leadDays.toLong())).toEpochMilli()
                                    }
                                }
                                alertMs?.let { Instant.ofEpochMilli(it) }
                            } else null

                            val finalEntryId: String
                            if (isMergeMode && existingMatch != null && initialEntry == null) {
                                finalEntryId = existingMatch.id
                                val mergedEntry = existingMatch.copy(
                                    quantity = Quantity(existingMatch.quantity.amount + amount, selectedUnit),
                                    alertAt = calculatedAlertAt,
                                    updatedAt = Instant.now()
                                )
                                onSave(mergedEntry, true)
                            } else {
                                val entryToSave = (initialEntry ?: InventoryEntry()).copy(
                                    name = name,
                                    quantity = Quantity(amount, selectedUnit),
                                    expirationDate = expDate,
                                    storageLocationId = selectedLocation?.id,
                                    categoryId = selectedCategory?.id,
                                    alertAt = calculatedAlertAt,
                                    updatedAt = Instant.now()
                                )
                                finalEntryId = entryToSave.id
                                onSave(entryToSave, initialEntry != null)
                            }

                            // Learning Catalog: If we have a barcode, save the info to the catalog too
                            scannedBarcode?.let { ean ->
                                scope.launch {
                                    repository.upsertCatalogProduct(
                                        CatalogProduct(
                                            ean = ean,
                                            name = name,
                                            defaultQuantity = Quantity(amount, selectedUnit)
                                        )
                                    )
                                    repository.linkEntryToProduct(finalEntryId, ean)
                                }
                            }
                        }
                    }
                ) {
                    val buttonRes = when {
                        initialEntry != null -> R.string.action_save
                        isMergeMode -> R.string.action_add_to_existing
                        else -> R.string.action_save
                    }
                    Text(stringResource(buttonRes))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        title = {
            val titleRes = when {
                initialEntry != null -> R.string.action_edit
                isMergeMode -> R.string.dialog_merge_item_title
                else -> R.string.dialog_add_item_title
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(titleRes))
                if (initialEntry == null) {
                    if (mode != AddMode.UNDEFINED) {
                        IconButton(onClick = { mode = AddMode.UNDEFINED }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                    if (mode != AddMode.UNDEFINED) {
                        Row {
                            IconButton(onClick = { mode = AddMode.SCAN }) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Scan", tint = if (mode == AddMode.SCAN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                            IconButton(onClick = { mode = AddMode.MANUAL }) {
                                Icon(Icons.Default.Edit, contentDescription = "Manual", tint = if (mode == AddMode.MANUAL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        },
        text = {
            if (mode == AddMode.UNDEFINED) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        stringResource(R.string.dialog_add_item_choice_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ElevatedCard(
                            onClick = { mode = AddMode.SCAN },
                            modifier = Modifier.weight(1f).height(140.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.CameraAlt, stringResource(R.string.action_scan_barcode), modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.action_scan_barcode), style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        ElevatedCard(
                            onClick = { mode = AddMode.MANUAL },
                            modifier = Modifier.weight(1f).height(140.dp),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Edit, stringResource(R.string.action_add_manually), modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text(stringResource(R.string.action_add_manually), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            } else if (mode == AddMode.SCAN && hasCameraPermission) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                        tonalElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            BarcodeScannerView { barcode ->
                                scannedBarcode = barcode
                                scope.launch {
                                    repository.getProductByEan(barcode).collect { product ->
                                        if (product != null) {
                                            name = product.name
                                            product.defaultQuantity?.let {
                                                quantityText = it.amount.toPlainString()
                                                selectedUnit = it.unit
                                            }
                                        }
                                        mode = AddMode.MANUAL
                                    }
                                }
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.hint_scan_barcode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            } else if (mode == AddMode.SCAN && !hasCameraPermission) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Camera permission is required to scan barcodes.")
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Name Input with Catalog Autocomplete
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { 
                                name = it
                                nameDropdownExpanded = true
                            },
                            label = { Text(stringResource(R.string.label_item_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        if (nameDropdownExpanded && (catalogSearchResults.isNotEmpty() || filteredSuggestions.isNotEmpty())) {
                            Spacer(Modifier.height(4.dp))
                            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                                Column {
                                    // Catalog Results First
                                    catalogSearchResults.forEach { product ->
                                        ListItem(
                                            headlineContent = { Text(product.name) },
                                            supportingContent = { product.brand?.let { Text(it) } },
                                            modifier = Modifier.clickable {
                                                name = product.name
                                                scannedBarcode = product.ean
                                                product.defaultQuantity?.let {
                                                    quantityText = it.amount.toPlainString()
                                                    selectedUnit = it.unit
                                                }
                                                nameDropdownExpanded = false
                                            }
                                        )
                                    }
                                    if (catalogSearchResults.isNotEmpty() && filteredSuggestions.isNotEmpty()) HorizontalDivider()
                                    // Then existing suggestions
                                    filteredSuggestions.forEach { suggestion ->
                                        ListItem(
                                            headlineContent = { Text(suggestion.name) },
                                            trailingContent = { Text("Existing", style = MaterialTheme.typography.labelSmall) },
                                            modifier = Modifier.clickable {
                                                name = suggestion.name
                                                if (suggestion.categoryId != null) selectedCategory = categories.find { it.id == suggestion.categoryId }
                                                if (suggestion.storageLocationId != null) selectedLocation = locations.find { it.id == suggestion.storageLocationId }
                                                selectedUnit = suggestion.quantity.unit
                                                datePickerState.selectedDateMillis = suggestion.expirationDate?.date?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
                                                nameDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text(if (isMergeMode) stringResource(R.string.label_amount_to_add) else stringResource(R.string.label_amount)) },
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
                            value = selectedUnit.translated().lowercase(),
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
                                    text = { Text(unit.translated().lowercase()) },
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
                        value = selectedLocation?.name ?: stringResource(R.string.label_select_location),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_storage_location)) },
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
                        value = selectedCategory?.name ?: stringResource(R.string.label_none),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_category_optional)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedCategory) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.label_none)) },
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
                    stringResource(R.string.expires_on, date.format(formatter))
                } else stringResource(R.string.label_set_expiration_date)
                
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(expirationText)
                }

                if (existingMatch != null && initialEntry == null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = userWantsToMerge,
                            onCheckedChange = { userWantsToMerge = it }
                        )
                        Text(
                            text = stringResource(R.string.label_merge_with_existing),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Notification Section
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_notifications), style = MaterialTheme.typography.titleSmall)
                        val subText = if (wantsNotification) {
                            if (customAlertDateSet) stringResource(R.string.label_custom_alert_date)
                            else {
                                val days = selectedCategory?.defaultLeadDays ?: globalLeadDays
                                "${stringResource(R.string.label_alert_me)} $days ${stringResource(R.string.label_days_before)}"
                            }
                        } else stringResource(R.string.label_none)
                        Text(subText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = wantsNotification,
                        onCheckedChange = { wantsNotification = it }
                    )
                }

                if (wantsNotification) {
                    val alertDateMillis = alertDatePickerState.selectedDateMillis
                    val calculatedAlertDate = if (!customAlertDateSet) {
                         datePickerState.selectedDateMillis?.let { expMs ->
                             val leadDays = selectedCategory?.defaultLeadDays ?: globalLeadDays
                             Instant.ofEpochMilli(expMs).minus(java.time.Duration.ofDays(leadDays.toLong())).toEpochMilli()
                         }
                    } else alertDateMillis

                    val alertText = if (calculatedAlertDate != null) {
                        val date = Instant.ofEpochMilli(calculatedAlertDate).atZone(ZoneId.systemDefault()).toLocalDate()
                        val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                        date.format(formatter)
                    } else stringResource(R.string.label_set_expiration_date)

                    OutlinedButton(
                        onClick = { 
                            showAlertDialogPicker = true 
                            customAlertDateSet = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${stringResource(R.string.label_alert_me)}: $alertText")
                    }
                }
            }
        }
    }
)

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
    if (showAlertDialogPicker) {
        DatePickerDialog(
            onDismissRequest = { showAlertDialogPicker = false },
            confirmButton = {
                TextButton(onClick = { showAlertDialogPicker = false }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAlertDialogPicker = false 
                    customAlertDateSet = false
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        ) {
            DatePicker(state = alertDatePickerState)
        }
    }
}
