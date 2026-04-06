package com.stashapp.android.ui.components

import androidx.camera.core.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.stashapp.android.R
import com.stashapp.shared.domain.CatalogProduct
import com.stashapp.shared.domain.MeasurementUnit
import com.stashapp.shared.domain.Quantity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestockItemSelectionDialog(
    onDismiss: () -> Unit,
    onProductSelected: (CatalogProduct, Double) -> Unit,
    onManualAdd: (String, Double) -> Unit,
    searchCatalog: (String) -> kotlinx.coroutines.flow.Flow<List<CatalogProduct>>,
    getProductByEan: suspend (String) -> CatalogProduct?
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(emptyList<CatalogProduct>()) }
    var isScanning by remember { mutableStateOf(false) }
    var quantity by remember { mutableStateOf("1") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            searchCatalog(searchQuery).collect { searchResults = it }
        } else {
            searchResults = emptyList()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(stringResource(R.string.action_add_manual_item)) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_cancel))
                        }
                    },
                    actions = {
                        IconButton(onClick = { isScanning = !isScanning }) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = stringResource(R.string.action_scan_barcode),
                                tint = if (isScanning) MaterialTheme.colorScheme.primary else LocalContentColor.current
                            )
                        }
                    }
                )

                if (isScanning) {
                    Box(modifier = Modifier.weight(1f)) {
                        BarcodeScannerView(
                            onBarcodeDetected = { ean ->
                                scope.launch {
                                    val product = getProductByEan(ean)
                                    if (product != null) {
                                        onProductSelected(product, quantity.toDoubleOrNull() ?: 1.0)
                                        isScanning = false
                                    } else {
                                        // Product not in catalog, but we have EAN
                                        // Could show a "new product" dialog, but for now just search
                                        searchQuery = ean
                                        isScanning = false
                                    }
                                }
                            }
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(16.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text(stringResource(R.string.hint_search_catalog)) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = quantity,
                                onValueChange = { quantity = it },
                                modifier = Modifier.width(100.dp),
                                label = { Text(stringResource(R.string.label_amount)) }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            if (searchQuery.isNotBlank() && searchResults.none { it.name.equals(searchQuery, ignoreCase = true) }) {
                                Button(
                                    onClick = { onManualAdd(searchQuery, quantity.toDoubleOrNull() ?: 1.0) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.action_add_manually))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(searchResults) { product ->
                                ListItem(
                                    headlineContent = { Text(product.name) },
                                    supportingContent = { Text("${product.brand ?: ""} ${product.ean}") },
                                    modifier = Modifier.clickable {
                                        onProductSelected(product, quantity.toDoubleOrNull() ?: 1.0)
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

