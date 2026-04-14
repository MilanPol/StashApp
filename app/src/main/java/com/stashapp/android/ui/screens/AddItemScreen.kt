package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.stashapp.android.util.StateSavers
import com.stashapp.android.util.StateSavers.listSaver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.time.Instant
import java.time.ZoneId
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
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import com.stashapp.android.ui.components.BarcodeScannerView
import com.stashapp.shared.domain.CatalogProduct
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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
    onSearchCatalog: (String) -> Flow<List<CatalogProduct>>,
    onGetProductByEan: (String) -> Flow<CatalogProduct?>,
    onUpsertCatalogProduct: (CatalogProduct) -> Unit,
    onLinkEntryToProduct: (String, String) -> Unit,
    onGetLinkedEan: suspend (String) -> String?,
    locations: List<StorageLocation>,
    categories: List<Category>,
    existingEntries: List<InventoryEntry>,
    initialEntry: InventoryEntry? = null,
    preSelectedLocationId: String? = null,
    preSelectedCategoryId: String? = null,
    globalLeadDays: Int = 2,
    onDismiss: () -> Unit,
    onMerge: (String, String) -> Unit,
    onSave: (InventoryEntry, Boolean) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(if (initialEntry == null) AddMode.UNDEFINED else AddMode.MANUAL) }
    
    var name by rememberSaveable { mutableStateOf(initialEntry?.name ?: "") }
    var scannedEan by rememberSaveable { mutableStateOf<String?>(null) }
    var scannedProductFound by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var scannedProductName by rememberSaveable { mutableStateOf<String?>(null) }
    var quantityText by rememberSaveable { mutableStateOf(initialEntry?.quantity?.amount?.toString() ?: "") }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var hasCameraPermission by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    var catalogSearchResults by rememberSaveable(
        stateSaver = listSaver(StateSavers.CatalogProductSaver)
    ) { mutableStateOf(mutableListOf<CatalogProduct>()) }
    
    LaunchedEffect(name) {
        if (name.length >= 2) {
            onSearchCatalog(name).collect { results ->
                catalogSearchResults = results.toMutableList()
            }
        } else {
            catalogSearchResults = mutableListOf()
        }
    }
    var expandedUnit by rememberSaveable { mutableStateOf(false) }
    var selectedUnit by rememberSaveable { mutableStateOf(initialEntry?.quantity?.unit ?: MeasurementUnit.PIECES) }

    var expandedLocation by rememberSaveable { mutableStateOf(false) }
    var selectedLocation: StorageLocation? by rememberSaveable(stateSaver = StateSavers.nullableSaver(StateSavers.StorageLocationSaver)) { 
        mutableStateOf<StorageLocation?>(
            initialEntry?.storageLocationId?.let { id -> locations.find { it.id == id } }
            ?: locations.find { it.id == preSelectedLocationId } 
            ?: locations.firstOrNull()
        ) 
    }

    var expandedCategory by rememberSaveable { mutableStateOf(false) }
    var selectedCategory: Category? by rememberSaveable(stateSaver = StateSavers.nullableSaver(StateSavers.CategorySaver)) { 
        mutableStateOf<Category?>(
            initialEntry?.categoryId?.let { id -> categories.find { it.id == id } }
            ?: categories.find { it.id == preSelectedCategoryId }
        ) 
    }

    var nameDropdownExpanded by rememberSaveable { mutableStateOf(false) }

    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showAlertDialogPicker by rememberSaveable { mutableStateOf(false) }

    val initialDateMillisValue = initialEntry?.expirationDate?.date
        ?.atStartOfDay(java.time.ZoneOffset.UTC)
        ?.toInstant()
        ?.toEpochMilli()
    
    var selectedDateMillis by rememberSaveable { mutableStateOf(initialDateMillisValue) }
    var alertDateMillis by rememberSaveable { mutableStateOf(initialEntry?.alertAt?.toEpochMilli()) }

    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = selectedDateMillis)
    val alertDatePickerState = rememberDatePickerState(initialSelectedDateMillis = alertDateMillis)

    // Sync back to state so it survives rotation
    LaunchedEffect(datePickerState.selectedDateMillis) {
        selectedDateMillis = datePickerState.selectedDateMillis
    }
    LaunchedEffect(alertDatePickerState.selectedDateMillis) {
        alertDateMillis = alertDatePickerState.selectedDateMillis
    }

    var wantsNotification by rememberSaveable { mutableStateOf(initialEntry?.alertAt != null || initialEntry?.expirationDate != null || initialEntry == null) }
    var customAlertDateSet by rememberSaveable { mutableStateOf(initialEntry?.alertAt != null) }

    var isStaple by rememberSaveable { mutableStateOf(initialEntry?.isStaple ?: false) }
    var stapleMinimumText by rememberSaveable { mutableStateOf(initialEntry?.stapleMinimum?.toPlainString() ?: "0") }

    val filteredSuggestions = existingEntries.filter { 
        it.name.contains(name, ignoreCase = true) && name.isNotBlank() 
    }.distinctBy { it.name }

    var entryEanMap by remember { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(existingEntries) {
        val mapping = mutableMapOf<String, String>()
        existingEntries.forEach { entry ->
            onGetLinkedEan(entry.id)?.let { ean ->
                mapping[entry.id] = ean
            }
        }
        entryEanMap = mapping
    }

    val existingMatch = existingEntries.find {
        val selectedExpDate = datePickerState.selectedDateMillis?.let { ms -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate() }
        val nameMatches = it.name.equals(name, ignoreCase = true)
        val locationMatches = it.storageLocationId == selectedLocation?.id
        val unitMatches = it.quantity.unit == selectedUnit
        val expMatches = it.expirationDate?.date?.year == selectedExpDate?.year && 
                         it.expirationDate?.date?.month == selectedExpDate?.month &&
                         it.expirationDate?.date?.dayOfMonth == selectedExpDate?.dayOfMonth
        
        // Smart identity: Both must have same EAN or both must have none
        val existingEan = entryEanMap[it.id]
        val eanMatches = if (scannedEan != null && existingEan != null) {
            scannedEan == existingEan
        } else if (scannedEan == null && existingEan == null) {
            true 
        } else {
            false 
        }

        nameMatches && locationMatches && unitMatches && expMatches && eanMatches && it.id != initialEntry?.id
    }
    
    var userWantsToMerge by rememberSaveable { mutableStateOf(true) }
    val isMergeMode = existingMatch != null && userWantsToMerge

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
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
                            if (isMergeMode && existingMatch != null) {
                                if (initialEntry == null) {
                                    // ADD & MERGE
                                    finalEntryId = existingMatch.id
                                    val mergedEntry = existingMatch.copy(
                                        quantity = Quantity(existingMatch.quantity.amount + amount, selectedUnit),
                                        alertAt = calculatedAlertAt,
                                        isStaple = isStaple,
                                        stapleMinimum = if (isStaple) stapleMinimumText.toBigDecimalOrNull() else null,
                                        updatedAt = Instant.now()
                                    )
                                    onSave(mergedEntry, true)
                                } else {
                                    // EDIT & MERGE
                                    finalEntryId = existingMatch.id
                                    onMerge(initialEntry!!.id, existingMatch.id)
                                }
                            } else {
                                // REGULAR SAVE/UPDATE
                                val entryToSave = (initialEntry ?: InventoryEntry()).copy(
                                    name = name,
                                    quantity = Quantity(amount, selectedUnit),
                                    expirationDate = expDate,
                                    storageLocationId = selectedLocation?.id,
                                    categoryId = selectedCategory?.id,
                                    alertAt = calculatedAlertAt,
                                    isStaple = isStaple,
                                    stapleMinimum = if (isStaple) stapleMinimumText.toBigDecimalOrNull() else null,
                                    updatedAt = Instant.now()
                                )
                                finalEntryId = entryToSave.id
                                onSave(entryToSave, initialEntry != null)
                            }

                            // Learning Catalog: If we have a barcode, save the info to the catalog too
                            scannedEan?.let { ean ->
                                onUpsertCatalogProduct(
                                    CatalogProduct(
                                        ean = ean,
                                        name = name,
                                        defaultQuantity = Quantity(amount, selectedUnit)
                                    )
                                )
                                onLinkEntryToProduct(finalEntryId, ean)
                            }
                        }
                    }
                ) {
                    val buttonRes = when {
                        isMergeMode -> R.string.action_add_to_existing
                        initialEntry != null -> R.string.action_save
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
                                Text(
                                    stringResource(R.string.action_scan_barcode), 
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
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
                                Text(
                                    stringResource(R.string.action_add_manually), 
                                    style = MaterialTheme.typography.labelLarge,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
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
                                if (scannedEan != barcode) {
                                    scannedEan = barcode
                                    scannedProductFound = null
                                    scope.launch {
                                        // Use firstOrNull to avoid collecting indefinitely
                                        val product = onGetProductByEan(barcode).firstOrNull()
                                        if (product != null) {
                                            name = product.name
                                            scannedProductName = product.name
                                            scannedProductFound = true
                                            product.defaultQuantity?.let {
                                                quantityText = it.amount.toPlainString()
                                                selectedUnit = it.unit
                                            }
                                        } else {
                                            scannedProductFound = false
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
                    Text(stringResource(R.string.permission_camera_required))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.action_grant_permission))
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Barcode Confirmation Banner
                    if (scannedEan != null && scannedProductFound != null) {
                        val containerColor = if (scannedProductFound == true) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                        val contentColor = if (scannedProductFound == true) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = containerColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val eanStr = scannedEan ?: ""
                            val foundText = if (scannedEan != null) stringResource(R.string.barcode_found_message, eanStr, scannedProductName ?: "") else ""
                            val notFoundText = if (scannedEan != null) stringResource(R.string.barcode_not_found_message, eanStr) else ""
                            Text(
                                text = if (scannedProductFound == true) foundText else notFoundText,
                                color = contentColor,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

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
                                                scannedEan = product.ean
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
                                                // Only override location/category if we are adding a NEW item
                                                if (initialEntry == null) {
                                                    if (suggestion.categoryId != null) selectedCategory = categories.find { it.id == suggestion.categoryId }
                                                    if (suggestion.storageLocationId != null) selectedLocation = locations.find { it.id == suggestion.storageLocationId }
                                                    selectedUnit = suggestion.quantity.unit
                                                    datePickerState.selectedDateMillis = suggestion.expirationDate?.date?.atStartOfDay(java.time.ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
                                                }
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
                            MeasurementUnit.entries.forEach { unit ->
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
                            val parentName = locations.find { it.id == loc.parentId }?.name
                            val displayName = if (parentName != null) "${loc.icon} ${loc.name} ($parentName)" else "${loc.icon} ${loc.name}"
                            DropdownMenuItem(
                                text = { Text(displayName) },
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

                if (existingMatch != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = userWantsToMerge,
                            onCheckedChange = { userWantsToMerge = it }
                        )
                        Text(
                            text = if (initialEntry == null) stringResource(R.string.label_merge_with_existing) else stringResource(R.string.action_merge_into_duplicate),
                            color = MaterialTheme.colorScheme.secondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Staple Stock Section
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.label_staple_stock), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.staple_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = isStaple,
                        onCheckedChange = { isStaple = it }
                    )
                }
                
                if (isStaple) {
                    OutlinedTextField(
                        value = stapleMinimumText,
                        onValueChange = { stapleMinimumText = it },
                        label = { Text(stringResource(R.string.label_staple_minimum)) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
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
