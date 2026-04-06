package com.stashapp.android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.stashapp.android.R
import com.stashapp.android.ui.components.RecipeTextScannerView
import com.stashapp.shared.domain.MatchConfidence
import com.stashapp.shared.domain.ReceiptMatch
import com.stashapp.shared.domain.ReceiptScanResult

@Composable
fun ReceiptScanScreen(
    viewModel: ReceiptScanViewModel,
    onNavigateBack: () -> Unit,
    onConfirmScan: (ReceiptScanResult) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is ReceiptScanUiState.Scanning -> {
                    RecipeTextScannerView(
                        instructionText = "Scan your receipt lines",
                        onTextCaptured = { lines -> viewModel.onTextCaptured(lines) },
                        onDismiss = onNavigateBack
                    )
                }
                is ReceiptScanUiState.Processing -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text("Matching receipt items...")
                    }
                }
                is ReceiptScanUiState.ReviewMatches -> {
                    ReviewMatchesContent(
                        result = state.result,
                        onRetake = { viewModel.reset() },
                        onConfirm = { onConfirmScan(state.result) }
                    )
                }
                is ReceiptScanUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Error, "Error", tint = MaterialTheme.colorScheme.error)
                        Text("An error occurred during scan")
                        Button(onClick = { viewModel.reset() }) { Text("Try Again") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewMatchesContent(
    result: ReceiptScanResult,
    onRetake: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Scan Summary", style = MaterialTheme.typography.headlineMedium)
        Text("${result.matches.size} items found", style = MaterialTheme.typography.bodyMedium)
        
        Spacer(Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(result.matches) { match ->
                MatchCard(match)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.scan_retake))
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.restock_confirm_all))
            }
        }
    }
}

@Composable
private fun MatchCard(match: ReceiptMatch) {
    val containerColor = when (match.confidence) {
        MatchConfidence.EXACT -> Color(0xFFE8F5E9) // Very faint green
        MatchConfidence.FUZZY -> Color(0xFFFFFDE7) // Very faint yellow
        MatchConfidence.NONE -> MaterialTheme.colorScheme.surfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = match.receiptLine.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${match.receiptLine.quantity.amount} ${match.receiptLine.quantity.unit.name.lowercase()}",
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (match.matchedShoppingItem != null || match.matchedCatalogProduct != null) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = match.matchedCatalogProduct?.name ?: match.matchedShoppingItem?.name ?: ""
                    Icon(
                        if (match.matchedCatalogProduct != null) Icons.Default.Inventory else Icons.Default.ShoppingCart,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Match: $name",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (match.confidence != MatchConfidence.NONE) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Confidence: ${match.confidence.name}",
                    style = MaterialTheme.typography.labelSmall,
                    color = when(match.confidence) {
                        MatchConfidence.EXACT -> Color(0xFF2E7D32)
                        MatchConfidence.FUZZY -> Color(0xFFF9A825)
                        else -> Color.Gray
                    }
                )
            }
        }
    }
}
