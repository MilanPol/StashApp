# Ubiquitous Language — Stash App

A shared vocabulary for the entire domain. All code, documentation, and communication should use these terms consistently.

---

## Inventory (Core Domain)

| Term | Definition |
|---|---|
| **InventoryEntry** | A single physical instance of an item in stock. Three cartons of milk = three InventoryEntries. |
| **Quantity** | The remaining amount of an InventoryEntry, expressed as an amount + unit (e.g., 500ml, 3 pieces). Value object. |
| **Unit** | The measurement unit for a quantity: liters, grams, pieces, etc. |
| **ShelfLife** | The estimated time an InventoryEntry remains consumable. Decreases when opened. |
| **ExpirationDate** | The date an InventoryEntry becomes unusable (sealed). Typically set from product packaging or CatalogItem defaults. |
| **OpenedAt** | The timestamp when an InventoryEntry was first opened. Triggers a recalculation of ShelfLife. |
| **StorageLocation** | A hierarchical tree of physical spots within a SharedLocation (e.g., Kitchen → Pantry → Top Shelf). Each location can have child locations via `parentId`, enabling precise item tracking. |
| **Consume** | Using (part of) an InventoryEntry — reduces Quantity. Can be partial or full. |
| **Deplete** | An InventoryEntry is fully consumed — Quantity reaches zero. |
| **Expire** | An InventoryEntry passes its ExpirationDate or ShelfLife runs out. |

### Inventory Events

| Event | Meaning |
|---|---|
| **InventoryEntryAdded** | A new item was placed into stock. |
| **InventoryEntryOpened** | An item was opened for the first time — ShelfLife is recalculated. |
| **InventoryEntryConsumed** | An item's Quantity was reduced (partial or full). |
| **InventoryEntryRemoved** | An item was removed from stock (discarded, finished, or expired). |
| **InventoryEntryAdjusted** | An item's properties were manually corrected (e.g., wrong quantity). |

---

## Item Catalog (Supporting)

| Term | Definition |
|---|---|
| **CatalogItem** | A template/definition describing a type of thing. Not a physical instance — the "blueprint" that an InventoryEntry or ShoppingListItem references. |
| **Category** | A grouping of CatalogItems (e.g., dairy, canned goods, homemade, cleaning supplies). |
| **DefaultUnit** | The standard unit used when creating an InventoryEntry from this CatalogItem. |
| **DefaultShelfLife** | Typical shelf life while sealed, used to pre-fill ExpirationDate. |
| **DefaultShelfLifeAfterOpening** | Typical shelf life after opening, used to recalculate ShelfLife on the `InventoryEntryOpened` event. |
| **Barcode** | An optional product identifier (EAN/UPC) for scanning and external lookup. |

> [!NOTE]
> Named "Item Catalog" (not "Product Catalog") because not everything is a purchased product. A jar of homemade jam is a valid CatalogItem.

---

## Shopping (Supporting)

| Term | Definition |
|---|---|
| **ShoppingList** | An ordered collection of things to purchase. Has its own lifecycle independent of Inventory. |
| **ShoppingListItem** | A single entry on a ShoppingList. Optionally linked to a CatalogItem for default values. |
| **PurchaseRecord** | Records the act of buying a ShoppingListItem. Triggers the Shopping → Inventory flow. |
| **Shopping → Inventory Flow** | The seamless transition of a purchased item into an InventoryEntry, pre-filled with CatalogItem defaults to minimize manual input. |

---

## Sharing (Supporting)

| Term | Definition |
|---|---|
| **SharedLocation** | A physical place (house, apartment, office) where inventory is stored and shared. The top-level scope for all inventory. |
| **Member** | A person with access to a SharedLocation's inventory. No roles for now — all members have equal access. |

> [!IMPORTANT]
> SharedLocation is **not** "Household". It's about physical location and shared access, regardless of social relationship.

---

## Notifications (Supporting)

| Term | Definition |
|---|---|
| **AlertRule** | A user-defined rule that triggers a Notification (e.g., "notify 3 days before ExpirationDate"). |
| **Notification** | A generated alert for the user. Created when an AlertRule condition is met. |
| **NotificationPreference** | A user's settings for how and when they receive Notifications. |

---

## User Management (Later)

| Term | Definition |
|---|---|
| **User** | An authenticated person using the app. |
| **Role** | A set of permissions assigned to a User within a SharedLocation (future). |

---

## Recipes (Later)

| Term | Definition |
|---|---|
| **Recipe** | A set of instructions with required ingredients, linked to CatalogItems. |
| **Ingredient** | A reference to a CatalogItem with a required Quantity. |
| **Availability** | Whether the current Inventory has sufficient stock to make a Recipe. |

---

## Cross-Cutting Concepts

| Term | Definition |
|---|---|
| **Bounded Context** | A clear boundary around a part of the domain with its own model, language, and rules. |
| **Domain Event** | A record that something meaningful happened in the domain (e.g., `InventoryEntryOpened`). Used for communication between contexts. |
| **Value Object** | An immutable object defined by its attributes, not by identity (e.g., Quantity, ShelfLife). |
| **Entity** | An object with a distinct identity that persists over time (e.g., InventoryEntry, CatalogItem). |
| **Aggregate** | A cluster of entities and value objects treated as a single unit for data changes. |
| **Local-first** | Data is stored and usable on-device first. Sync with a server happens when connectivity allows. |
| **Selective Event Sourcing** | Only certain domain changes are stored as events (e.g., inventory mutations), not every state change in the system. |

---

## Context Map Relationships

| Term | Definition |
|---|---|
| **Conformist** | Downstream context adopts the upstream model as-is without translation. |
| **Published Language** | Upstream defines a shared contract (events, schemas) that downstream contexts subscribe to. |
| **ACL (Anti-Corruption Layer)** | Downstream translates the upstream model into its own language to maintain independence. |
