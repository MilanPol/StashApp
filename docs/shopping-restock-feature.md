# Shopping List & Restock Feature Design Document

## Scope

This document covers two tightly coupled features:

1. **Shopping list management** — create, edit, and delete items manually
2. **Receipt scan + restock session** — scan a receipt after shopping to match items against the shopping list and assign them to inventory locations in bulk

---

## Goals

- Simple CRUD for shopping list items
- Scan a receipt (photo) to automatically match purchased items against the shopping list
- Restock dashboard: assign multiple scanned items to a location in bulk
- Inventory updated on confirmation

---

## Shopping List Management

### Basic CRUD

Standard operations on `ShoppingListItem` as defined in the main feature doc:

- **Add** — name, optional quantity, optional unit
- **Edit** — any field inline
- **Delete** — swipe to delete or explicit delete action
- **Check off** — marks item as `PURCHASED` (does not affect inventory yet)

No sorting or categorization in scope for MVP — flat list, manual order.

---

## Receipt Scanning

### Pipeline

```
camera / gallery
      │
      ▼
 image preprocessing       contrast, deskew, crop
      │
      ▼
 ML Kit Text Recognition   printed text only (receipts are always printed)
      │
      ▼
 receipt line parser        extract item name + quantity per line
      │
      ▼
 shopping list matcher      match scanned items against PENDING shopping list items
      │
      ▼
 restock session            user assigns matched + unmatched items to locations
```

### Preprocessing

Receipts are narrow, high-contrast, printed on thermal paper — easier to OCR than cookbook pages. Standard grayscale + contrast increase is sufficient. Deskew recommended since receipts are often photographed at an angle.

### Receipt Line Parser

Receipts have a different structure than ingredient lists. A typical receipt line looks like:

```
Halfvolle melk 1L          2  x  1.29
Kipfilet                   1  x  4.99
Appels 6st                 1  x  2.49
```

The parser extracts **name** and **quantity** only — price is ignored.

```kotlin
data class ReceiptLine(
    val name: String,
    val quantity: Double,   // defaults to 1.0 if not found
    val raw: String,        // original OCR line for correction UI
)

object ReceiptLineParser {

    // Quantity pattern: number at end of line before price, or "Xst", "Xkg" etc.
    private val quantityPattern = Regex("""(\d+(?:[.,]\d+)?)\s*x""", RegexOption.IGNORE_CASE)
    private val unitQuantityPattern = Regex("""(\d+(?:[.,]\d+)?)\s*(st|kg|g|l|ml|ltr)\b""", RegexOption.IGNORE_CASE)

    fun parse(lines: List<TextLine>): List<ReceiptLine> {
        return lines
            .filter { isProductLine(it.text) }
            .map { parseLine(it) }
    }

    private fun isProductLine(text: String): Boolean {
        // Skip header, footer, totals, VAT lines
        val skip = listOf("totaal", "total", "btw", "subtotaal", "wisselgeld",
                          "pinbetaling", "bedankt", "kassabon", "bon nr")
        return skip.none { text.lowercase().contains(it) }
            && text.trim().length > 3
    }

    private fun parseLine(line: TextLine): ReceiptLine {
        val text = line.text
        val quantity = quantityPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: unitQuantityPattern.find(text)?.groupValues?.get(1)?.toDoubleOrNull()
            ?: 1.0
        // Strip quantity/price tokens to get clean name
        val name = text
            .replace(quantityPattern, "")
            .replace(Regex("""\d+[.,]\d{2}"""), "")   // prices
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
        return ReceiptLine(name = name, quantity = quantity, raw = text)
    }
}
```

### Shopping List Matching

After parsing, each `ReceiptLine` is matched against `PENDING` shopping list items using the same normalized name matching as ingredient matching.

```kotlin
data class ReceiptMatch(
    val receiptLine: ReceiptLine,
    val matchedItem: ShoppingListItem?,   // null = not on shopping list
    val confidence: MatchConfidence,
)

enum class MatchConfidence {
    EXACT,    // normalized names identical
    FUZZY,    // close match (e.g. "halfvolle melk 1L" → "melk")
    NONE,     // no match found — item was not on the shopping list
}
```

Matched shopping list items are automatically marked as `PURCHASED`. Items with `NONE` confidence are still included in the restock session — the user bought them even if they weren't on the list.

---

## Restock Dashboard

The restock dashboard is opened after receipt scanning (or manually after shopping without a receipt). It shows all `PURCHASED` items and lets the user assign them to locations in bulk.

### Flow

1. User opens restock dashboard
2. All `PURCHASED` shopping list items are shown as unassigned cards
3. User selects one or more items
4. User picks a location from the HOME subtree
5. Selected items are assigned to that location → inventory updated → items removed from shopping list
6. Repeat until all items are assigned
7. Session complete

### UI Concept

```
┌─────────────────────────────────────┐
│  Opruimen (8 items)                 │
├─────────────────────────────────────┤
│  ☐ Halfvolle melk      2x           │
│  ☐ Kipfilet            1x           │
│  ☐ Appels              1x           │
│  ☐ Yoghurt             3x           │
│  ☐ Brood               1x           │
│  ──────────────────────────────     │
│  ☑ Pasta               2x  → Kast  │
│  ☑ Rijst               1x  → Kast  │
├─────────────────────────────────────┤
│  [ Selecteer locatie voor 3 items ] │
└─────────────────────────────────────┘
```

- Tap items to select (multi-select)
- "Selecteer locatie" opens location picker (HOME subtree)
- Assigned items move to a "done" section with their location shown
- No suggested locations — always starts empty

### State model

```kotlin
data class RestockSession(
    val id: RestockSessionId,
    val items: List<RestockItem>,
    val createdAt: Instant,
)

data class RestockItem(
    val id: RestockItemId,
    val name: String,
    val quantity: Double,
    val unit: CanonicalUnit?,
    val shoppingListItemId: ShoppingListItemId?,   // null if not from shopping list
    val locationId: LocationId?,                   // null until assigned
    val status: RestockStatus,
)

enum class RestockStatus {
    UNASSIGNED,    // not yet given a location
    ASSIGNED,      // location chosen, pending final confirmation
    CONFIRMED,     // inventory updated, removed from shopping list
}
```

### Confirmation

When the user finishes the session ("Klaar"), all `ASSIGNED` items are confirmed in a single transaction:
- Product quantity added to inventory at the chosen location
- Shopping list items marked as `RESTOCKED` and removed from the active list
- Restock session closed

If the user exits mid-session, `ASSIGNED` items are persisted — the session can be resumed later.

---

## Domain model additions

```kotlin
// New — receipt scan result, transient (not persisted)
data class ReceiptScanResult(
    val lines: List<ReceiptLine>,
    val matches: List<ReceiptMatch>,
    val scannedAt: Instant,
)
```

`RestockSession` is persisted to allow resuming. `ReceiptScanResult` is transient — once the restock session is created from it, the raw scan data is no longer needed.

---

## Architecture

```
shared/
  domain/
    model/         RestockSession, RestockItem, RestockStatus
                   ReceiptLine, ReceiptMatch, MatchConfidence
    usecase/       ScanReceiptUseCase
                   MatchReceiptToShoppingListUseCase
                   StartRestockSessionUseCase
                   AssignItemsToLocationUseCase
                   ConfirmRestockSessionUseCase

  data/
    repository/    RestockSessionRepositoryImpl (SQLDelight)
    parsing/       ReceiptLineParser (new)
                   IngredientParser (existing — shared)

androidApp/
  ui/restock/      RestockDashboardScreen
                   LocationPickerSheet
  ui/shopping/     ShoppingListScreen (CRUD — likely already exists)
                   ReceiptScanScreen
```

---

## Failure Modes

| Failure | Mitigation |
|---|---|
| OCR misreads product name | Raw line shown in restock dashboard, user can edit name |
| No match found on shopping list | Item still included in restock session with `NONE` confidence |
| Receipt format unknown (foreign supermarket) | Parser returns best-effort result, user corrects |
| User exits restock mid-session | Session persisted, resumable |

---

## Out of Scope

- Price tracking
- Supermarket-specific receipt templates
- Automatic location suggestions based on history
- Multi-receipt scanning in one session
