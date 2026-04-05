# Recipe Feature Design Document

## Context

This document describes the design for a recipe feature added to an existing inventory app built with **Kotlin Multiplatform (KMM)**. The app already manages products with quantities/units, organized in a user-defined hierarchical location structure (dynamic nesting, no fixed room types).

---

## Goals

- Allow users to create, import, and scan recipes
- Match recipe ingredients against current inventory, regardless of storage location
- Show a shopping list for missing or low-stock ingredients
- Let users manually confirm per-ingredient stock deductions after cooking

---

## Domain Model

### New entities

```kotlin
data class Recipe(
    val id: RecipeId,
    val name: String,
    val description: String?,
    val servings: Int,
    val steps: List<String>,
    val ingredients: List<RecipeIngredient>,
    val source: RecipeSource,
    val createdAt: Instant,
)

data class RecipeIngredient(
    val id: RecipeIngredientId,
    val name: String,           // canonical name used for matching
    val quantity: Double,
    val unit: Unit,             // reuse existing Unit type from inventory
    val notes: String?,         // e.g. "finely chopped", optional
)

sealed class RecipeSource {
    object Manual : RecipeSource()
    data class ImportedUrl(val url: String) : RecipeSource()
    data class ScannedPhoto(val imageRef: String) : RecipeSource()
}
```

### Existing entities (reference)

```kotlin
// Already in the app — do not modify
data class Product(
    val id: ProductId,
    val name: String,
    val quantity: Double,
    val unit: Unit,
    val locationId: LocationId,
)
```

---

## Ingredient Matching

Since locations have no semantic type (no "kitchen", "pantry"), matching is **location-agnostic** and based on:

1. **Normalized name** — lowercase, trimmed, singular form (e.g. "tomatoes" → "tomato")
2. **Unit compatibility** — match or convert within the same unit category (mass, volume, count)

### Match result model

```kotlin
data class IngredientMatch(
    val recipeIngredient: RecipeIngredient,
    val matchedProducts: List<Product>,     // all products that match by name+unit
    val availableQuantity: Double,          // sum across all matched products
    val status: MatchStatus,
)

enum class MatchStatus {
    AVAILABLE,       // availableQuantity >= required
    LOW,             // availableQuantity > 0 but < required
    MISSING,         // no matching product found at all
}
```

### Shopping list

Derived from all ingredients with status `LOW` or `MISSING`. No separate entity needed — computed on the fly from the match results.

---

## Cook Session Flow

When a user starts cooking a recipe:

1. **Match** — compute `IngredientMatch` for all ingredients against current inventory
2. **Review** — show match results: available ✓, low ⚠, missing ✗
3. **Shopping list** — optionally export/share low+missing items
4. **Cook** — user proceeds to cook
5. **Confirm deductions** — per ingredient, user confirms whether to deduct from stock

### Cook session state

```kotlin
data class CookSession(
    val recipe: Recipe,
    val matches: List<IngredientMatch>,
    val deductions: Map<RecipeIngredientId, DeductionState>,
)

sealed class DeductionState {
    object Pending : DeductionState()
    data class Confirmed(val fromProducts: List<ProductDeduction>) : DeductionState()
    object Skipped : DeductionState()
}

data class ProductDeduction(
    val productId: ProductId,
    val amount: Double,
)
```

---

## Recipe Import

### Via URL

- Use a recipe scraper library or a structured data parser (schema.org/Recipe JSON-LD is widely supported)
- Candidate library: [`recipe-scraper`](https://github.com/hhursev/recipe-scrapers) logic can be ported or called via a backend proxy if needed in KMM context

### Via Photo (OCR / Vision)

Fully native, no third-party cloud vision service.

| Platform | Solution |
|---|---|
| **Android** | Google ML Kit Text Recognition v2 — on-device, no network required |
| **PWA (iOS/browser)** | Web Text Detection API (`TextDetector`) where supported; fallback to [Tesseract.js](https://tesseract.projectnaptha.com/) (WASM, runs in browser) |

**Android flow:**
1. Capture image via camera or gallery
2. Pass bitmap to `TextRecognizer` (ML Kit)
3. Raw OCR text passed to `IngredientParser` (see section below)
4. Present parsed result to user for review/correction before saving

---

## Ingredient Parsing

Used for both OCR output (photo scan) and plain text input (manual or URL import fallback). The parser is **language-agnostic by design** — it normalizes input before pattern matching, so adding a language means only adding dictionary data, not changing parsing logic.

### Pipeline

```
raw text line
    │
    ▼
NumberNormalizer        "½"  →  0.5 / "twee" →  2.0 / "1/2" → 0.5
    │
    ▼
UnitDictionary          "eetlepel" → TABLESPOON / "tbsp" → TABLESPOON
    │
    ▼
StopWordFilter          strip "van", "of", "de", "fresh", "gehakte" etc.
    │
    ▼
PatternMatcher          [quantity?] [unit?] [name]
    │
    ▼
ParsedIngredient        { quantity: 2.0, unit: TABLESPOON, name: "olijfolie" }
```

### Models

```kotlin
data class ParsedIngredient(
    val quantity: Double?,      // null if not found (e.g. "salt to taste")
    val unit: CanonicalUnit?,   // null if unitless (e.g. "3 eggs")
    val name: String,
    val raw: String,            // original line, preserved for user correction UI
)

enum class CanonicalUnit {
    // Volume
    TEASPOON, TABLESPOON, CUP, ML, L,
    // Mass
    GRAM, KG, OZ, LB,
    // Count
    PIECE,
    // Ambiguous
    PINCH, HANDFUL, TO_TASTE,
}
```

### UnitDictionary — multilingual

Each language contributes a flat map of surface forms to `CanonicalUnit`. The parser tries all registered languages and picks the first match.

```kotlin
object UnitDictionary {
    val entries: Map<String, CanonicalUnit> = buildMap {
        // Dutch
        putAll(mapOf(
            "eetlepel" to TABLESPOON, "el" to TABLESPOON,
            "theelepel" to TEASPOON,  "tl" to TEASPOON,
            "kop"       to CUP,
            "gram"      to GRAM,      "g" to GRAM,
            "kilogram"  to KG,        "kg" to KG,
            "liter"     to L,         "l" to L,
            "milliliter" to ML,       "ml" to ML,
            "snufje"    to PINCH,
        ))
        // English
        putAll(mapOf(
            "tablespoon" to TABLESPOON, "tbsp" to TABLESPOON, "tbs" to TABLESPOON,
            "teaspoon"   to TEASPOON,   "tsp" to TEASPOON,
            "cup"        to CUP,
            "gram"       to GRAM,       "g" to GRAM,
            "ounce"      to OZ,         "oz" to OZ,
            "pound"      to LB,         "lb" to LB,
            "pinch"      to PINCH,
        ))
        // French
        putAll(mapOf(
            "cuillère à soupe" to TABLESPOON, "c. à s." to TABLESPOON,
            "cuillère à café"  to TEASPOON,   "c. à c." to TEASPOON,
            "tasse"            to CUP,
            "gramme"           to GRAM,       "g" to GRAM,
            "pincée"           to PINCH,
        ))
    }
}
```

Adding a new language = adding a new `putAll` block. No changes to parser logic.

### NumberNormalizer

Handles the common cases encountered in recipes:

```kotlin
object NumberNormalizer {
    // Unicode fractions
    private val unicodeFractions = mapOf('½' to 0.5, '⅓' to 0.333, '¼' to 0.25, '¾' to 0.75)
    // Written numbers (extendable per language)
    private val writtenNumbers = mapOf(
        "een" to 1.0, "twee" to 2.0, "drie" to 3.0,   // NL
        "one" to 1.0, "two"  to 2.0, "three" to 3.0,  // EN
        "un"  to 1.0, "deux" to 2.0, "trois" to 3.0,  // FR
    )

    fun normalize(token: String): Double? {
        // 1. Unicode fraction character
        if (token.length == 1) unicodeFractions[token[0]]?.let { return it }
        // 2. Slash fraction e.g. "1/2", "3/4"
        val slash = token.split("/")
        if (slash.size == 2) return slash[0].toDoubleOrNull()?.div(slash[1].toDoubleOrNull() ?: return null)
        // 3. Mixed e.g. "1½" → 1.5
        // 4. Written number
        return writtenNumbers[token.lowercase()] ?: token.toDoubleOrNull()
    }
}
```

### StopWordFilter

Strips words that add no semantic value to the ingredient name. Per-language lists, applied after unit extraction.

```kotlin
val stopWords = mapOf(
    "nl" to setOf("van", "de", "het", "een", "vers", "gehakte", "gesneden", "of"),
    "en" to setOf("of", "fresh", "chopped", "diced", "sliced", "about"),
    "fr" to setOf("de", "du", "des", "frais", "haché", "émincé"),
)
```

### User correction UI

After parsing, always show results in an editable form before saving. The `raw` field on `ParsedIngredient` is shown as context so the user understands what was detected.

---



```
shared/
  domain/
    model/         Recipe, RecipeIngredient, RecipeSource,
                   IngredientMatch, CookSession, ...
    repository/    RecipeRepository (interface)
                   InventoryRepository (already exists)
    usecase/       GetRecipesUseCase
                   MatchIngredientsUseCase
                   ImportRecipeFromUrlUseCase
                   ScanRecipeFromPhotoUseCase
                   StartCookSessionUseCase
                   ConfirmDeductionsUseCase

  data/
    repository/    RecipeRepositoryImpl (SQLDelight)
    remote/        RecipeImportService (HTTP scraper)
    parsing/       IngredientParser
                   UnitDictionary
                   NumberNormalizer

androidApp/
  ui/recipes/      RecipeListScreen, RecipeDetailScreen,
                   CookSessionScreen, ShoppingListScreen

iosApp/
  ui/recipes/      (mirror of Android screens in SwiftUI)
```

---

## Location Types

A minimal type system is added to locations — not to categorize every room, but to mark a **top-level location as a dwelling (home)**. This is the only type distinction needed for the recipe feature.

```kotlin
enum class LocationType {
    HOME,   // marks a dwelling — scopes a cook session
    OTHER,  // default for all other locations
}

data class Location(
    val id: LocationId,
    val name: String,
    val parentId: LocationId?,
    val type: LocationType = LocationType.OTHER,
)
```

### Cook session scope

When a user starts a cook session, they select a **HOME location**. The session then considers all products found anywhere in the subtree of that location — recursively across all nested rooms, cabinets, shelves, etc.

```
Home (type = HOME)
├── Kitchen
│   ├── Fridge
│   └── Cabinet
├── Pantry
└── Garage
    └── Storage shelf
```

All products in this entire tree are in scope for ingredient matching. The user does not need to specify which room — the home boundary is the only relevant scope.

### Selecting a home at cook time

- If only one HOME location exists → auto-select, no prompt
- If multiple HOME locations exist (e.g. holiday home, partner's place) → show picker before starting the session

---

## Shopping List

The shopping list is a standalone feature — a single active list that can be fed from three sources:

1. **Manual** — user adds an item directly
2. **Recipe** — missing/low ingredients from a cook session are added
3. **Inventory** — products that fall below a threshold are added (low-stock signal)

### Domain model

```kotlin
data class ShoppingList(
    val id: ShoppingListId,
    val items: List<ShoppingListItem>,
    val updatedAt: Instant,
)

data class ShoppingListItem(
    val id: ShoppingListItemId,
    val name: String,
    val quantity: Double?,
    val unit: CanonicalUnit?,
    val checked: Boolean = false,
    val sources: List<ShoppingItemSource>,   // merged — see below
)

sealed class ShoppingItemSource {
    object Manual : ShoppingItemSource()
    data class FromRecipe(val recipeId: RecipeId, val recipeName: String) : ShoppingItemSource()
    data class FromInventory(val productId: ProductId, val productName: String) : ShoppingItemSource()
}
```

### Merging

When an item is added that matches an existing item by name + compatible unit, they are **merged into one**:

- Quantities are summed
- The new source is appended to `sources`
- The user sees e.g. "Tomaten — 400g · vanuit: Pasta recept, handmatig"

If units are incompatible (e.g. grams vs pieces), the items are kept separate.

### Lifecycle

The shopping list sits at the center of a full inventory cycle:

```
inventory low / recipe missing
        │
        ▼
  shopping list
        │
        ▼
  user goes shopping — checks off items (quick, no location picking)
        │
        ▼
  restock session — user assigns each checked item to a location
        │
        ▼
  inventory updated — item removed from shopping list
```

**While shopping:** checking off an item marks it as `purchased` but does not touch inventory yet. The UI stays fast and simple — no location pickers, no interruptions.

**Restock session:** triggered when the user gets home. Shows all `purchased` items in a dedicated flow where each item gets assigned to a location within the HOME subtree. On confirmation, the product quantity is added to inventory and the item is removed from the shopping list.

```kotlin
data class ShoppingListItem(
    val id: ShoppingListItemId,
    val name: String,
    val quantity: Double?,
    val unit: CanonicalUnit?,
    val status: ShoppingItemStatus,
    val sources: List<ShoppingItemSource>,
)

enum class ShoppingItemStatus {
    PENDING,      // not yet bought
    PURCHASED,    // checked off while shopping — awaiting restock
    RESTOCKED,    // assigned to location — removed from active list
}
```

Checked items that are not yet restocked remain visible in a "purchased" section so the user can complete the restock session later without losing context.

### Untrackable ingredients

Ingredients without a quantity (`quantity: null`) or with unit `TO_TASTE` or `PINCH` are **silently excluded** from matching and the shopping list. They appear in the recipe view for reference but the app makes no inventory assumptions about them. This covers herbs, salt, pepper, and similar staples that are impractical to track.

---

## Design Decisions (resolved)

### RecipeIngredient → Product linking

`RecipeIngredient.name` is **not** a direct foreign key to `Product`. Instead, an intermediate mapping table decouples the two domains:

```kotlin
data class IngredientProductMapping(
    val id: MappingId,
    val normalizedIngredientName: String,   // e.g. "tomato"
    val productId: ProductId,
    val confirmedByUser: Boolean,           // true = user explicitly linked, false = auto-matched
)
```

This means:
- Recipes have no hard dependency on inventory — a recipe stays valid even if a product is renamed or deleted
- The matcher uses this table as a lookup cache; if no mapping exists it falls back to fuzzy name matching
- User corrections in the cook session review screen write back to this table, improving future matches over time
- Products can be restructured freely without breaking recipes

### Multi-serving scaling

Ingredient quantities in a cook session **scale proportionally** with the chosen serving count. Deductions scale accordingly. This applies to both the match computation and the confirmation step.

```kotlin
val scaleFactor = chosenServings.toDouble() / recipe.servings.toDouble()
val scaledQuantity = recipeIngredient.quantity?.times(scaleFactor)
```

### Shopping list export

Not in scope for now.

---

## Open Questions

- [ ] Unit conversion: define conversion factors in shared domain or delegate to a library? (`systems-of-units` / `quantities` are candidates)
- [ ] Low-stock threshold: fixed value per product, or a percentage of a user-defined "normal" quantity?
- [ ] Should the restock session suggest the last known location for a product (from `IngredientProductMapping` or product history)?
