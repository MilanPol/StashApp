# Staple Stock Feature Design Document

## Scope

Allow users to mark any inventory product as **staple stock** — meaning it should always be available. When the quantity drops to zero or below a user-defined minimum threshold, the item is automatically added to the shopping list.

---

## Goals

- Mark/unmark any product as staple stock
- Configure a minimum threshold per product (optional — defaults to 0)
- Automatically add to shopping list when threshold is crossed
- No duplicate entries on the shopping list

---

## Domain Model

Minimal extension to the existing `Product` entity:

```kotlin
data class Product(
    val id: ProductId,
    val name: String,
    val quantity: Double,
    val unit: Unit,
    val locationId: LocationId,
    // New fields:
    val isStaple: Boolean = false,
    val stapleMinimum: Double? = null,   // null = trigger at 0, set = trigger below this value
)
```

### Trigger logic

```kotlin
fun Product.needsRestock(): Boolean {
    if (!isStaple) return false
    val threshold = stapleMinimum ?: 0.0
    return quantity <= threshold
}
```

---

## Behaviour

### When is the shopping list updated?

The check runs whenever product quantity changes:

- After a **cook session** deduction is confirmed
- After a **restock session** item is confirmed (quantity goes up — may resolve a trigger)
- After a **manual quantity edit**

If `needsRestock()` returns true and the item is not already on the shopping list (`PENDING` or `PURCHASED`), a new `ShoppingListItem` is added automatically with source `FromInventory`.

If the item is already on the shopping list, nothing happens — no duplicate is created.

If the quantity is restored above the threshold (e.g. after restocking), the shopping list item is **not** automatically removed — the user may have already started shopping. It stays until manually deleted or restocked.

### Threshold examples

| `isStaple` | `stapleMinimum` | Trigger when |
|---|---|---|
| true | null | quantity == 0 |
| true | 2.0 | quantity <= 2.0 |
| false | — | never |

---

## Use Cases

```
shared/
  domain/
    usecase/    CheckStapleRestockUseCase   // runs after any quantity change
                ToggleStapleUseCase         // set isStaple + optional minimum
```

### CheckStapleRestockUseCase

```kotlin
class CheckStapleRestockUseCase(
    private val shoppingListRepository: ShoppingListRepository,
) {
    suspend fun execute(product: Product) {
        if (!product.needsRestock()) return

        val alreadyListed = shoppingListRepository
            .findActiveItem(normalizedName = product.name.normalize())

        if (alreadyListed == null) {
            shoppingListRepository.addItem(
                ShoppingListItem(
                    name = product.name,
                    quantity = product.stapleMinimum ?: 1.0,
                    unit = product.unit,
                    status = ShoppingItemStatus.PENDING,
                    sources = listOf(ShoppingItemSource.FromInventory(
                        productId = product.id,
                        productName = product.name,
                    )),
                )
            )
        }
    }
}
```

---

## UI

No separate screen needed. Staple stock is managed directly on the product detail screen:

```
┌─────────────────────────────────────┐
│  Halfvolle melk                     │
│  Koelkast · 1 liter                 │
│                                     │
│  Vaste voorraad        [ aan/uit ]  │
│  Minimum hoeveelheid   [ 2      ]   │  ← only visible when toggle is on
└─────────────────────────────────────┘
```

- Toggle enables/disables staple flag
- Minimum field appears when toggle is on — optional, defaults to 0
- `CheckStapleRestockUseCase` runs immediately when toggle is turned on if current quantity already meets the trigger condition

---

## Edge Cases

| Situation | Behaviour |
|---|---|
| Product deleted while on shopping list | Shopping list item stays — source reference becomes stale but item remains |
| Quantity manually set to 0 | Triggers restock check immediately |
| Item already on shopping list from recipe | Added as a separate item — a recipe need and a staple restock are independent, with different quantities and intent |
| Minimum set higher than current quantity | Triggers immediately on save |

---

## Out of Scope

- Notification / push alert when threshold is crossed
- Global staple list overview screen (manageable per product for now)
- Automatic reorder quantity suggestion
