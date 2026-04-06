package com.stashapp.shared.domain

import java.time.Instant
import com.stashapp.shared.data.parsing.ReceiptLine

data class ReceiptScanResult(
    val lines: List<ReceiptLine>,
    val matches: List<ReceiptMatch>,
    val scannedAt: Instant = Instant.now()
)

data class ReceiptMatch(
    val receiptLine: ReceiptLine,
    val matchedShoppingItem: ShoppingListItem? = null,
    val matchedCatalogProduct: CatalogProduct? = null,
    val confidence: MatchConfidence = MatchConfidence.NONE
)

enum class MatchConfidence {
    EXACT,
    FUZZY,
    NONE
}
