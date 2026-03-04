# C4 Architecture Model — Stash App

---

## Level 1: System Context Diagram

*Who uses the system, and what does it interact with?*

```mermaid
graph TB
    User["👤 Household Member<br/>(Primary User)"]

    subgraph "Stash System"
        APP["📱 Stash App<br/><br/>Manage household inventory,<br/>shopping lists, and notifications"]
    end

    EXT_PRODUCT["🌐 External Product APIs<br/>(Open Food Facts, Supermarket data)"]
    EXT_NOTIF["📨 Push Notification Service<br/>(FCM / APNs)"]
    EXT_LLM["🤖 LLM Service<br/>(Shelf life estimation)"]

    User -- "Manages inventory,<br/>creates shopping lists,<br/>views notifications" --> APP
    APP -. "Looks up product data<br/>(future)" .-> EXT_PRODUCT
    APP -. "Sends push notifications" .-> EXT_NOTIF
    APP -. "Queries shelf life<br/>after opening (future)" .-> EXT_LLM
```

> [!NOTE]
> Dashed lines = future integrations. The app is designed to work **fully offline** without any external dependencies. External services only enrich the experience.

---

## Level 2: Container Diagram

*What are the major technical building blocks?*

```mermaid
graph TB
    subgraph "User Devices"
        ANDROID["📱 Android App<br/><br/>(Compose UI)<br/>Kotlin"]
        IOS["📱 iOS App<br/><br/>(Compose Multiplatform UI)<br/>Kotlin/Swift"]

        subgraph "KMP Shared Module"
            DOMAIN["🧩 Domain Layer<br/><br/>Entities, Events,<br/>Business Logic<br/>(Pure Kotlin)"]
            LOCAL_DB["💾 Local Database<br/><br/>(SQLDelight)<br/>SQLite"]
            EVENT_STORE["📋 Local Event Store<br/><br/>Selective event log<br/>for sync"]
        end
    end

    subgraph "Backend"
        SYNC["☁️ Sync Server<br/><br/>Event relay +<br/>conflict resolution<br/>(Kotlin/Python)"]
        SERVER_DB["🗄️ Server Database<br/><br/>Event storage +<br/>user/location data"]
    end

    ANDROID --> DOMAIN
    IOS --> DOMAIN
    DOMAIN --> LOCAL_DB
    DOMAIN --> EVENT_STORE
    EVENT_STORE -- "Sync when online<br/>(push/pull events)" --> SYNC
    SYNC --> SERVER_DB
```

### Container Descriptions

| Container | Technology | Purpose |
|---|---|---|
| **Android App** | Kotlin, Compose | Native Android UI shell |
| **iOS App** | KMP + Compose Multiplatform | Native iOS UI shell |
| **KMP Shared Module** | Kotlin Multiplatform | All shared business logic, domain model, and data access |
| **Local Database** | SQLDelight (SQLite) | On-device persistence — the app works fully offline |
| **Local Event Store** | SQLDelight (SQLite) | Selective event log for inventory mutations, used for sync |
| **Sync Server** | Kotlin (Ktor) or Python | Relays events between devices, handles conflict resolution |
| **Server Database** | PostgreSQL | Stores events and shared location/membership data |

---

## Level 3: Component Diagram — KMP Shared Module

*How is the shared module structured internally?*

```mermaid
graph TB
    subgraph "KMP Shared Module"
        subgraph "Domain Layer (Pure Kotlin)"
            INV_DOM["📦 Inventory<br/><br/>InventoryEntry<br/>Quantity, ShelfLife<br/>StorageLocation"]
            CAT_DOM["📦 Item Catalog<br/><br/>CatalogItem<br/>Category<br/>ShelfLifeDefaults"]
            SHOP_DOM["📦 Shopping<br/><br/>ShoppingList<br/>ShoppingListItem<br/>PurchaseRecord"]
            SHARE_DOM["📦 Sharing<br/><br/>SharedLocation<br/>Member"]
            NOTIF_DOM["📦 Notifications<br/><br/>AlertRule<br/>Notification<br/>NotificationPreference"]
        end

        subgraph "Application Layer"
            INV_SVC["⚙️ InventoryService"]
            CAT_SVC["⚙️ CatalogService"]
            SHOP_SVC["⚙️ ShoppingService"]
            SHARE_SVC["⚙️ SharingService"]
            NOTIF_SVC["⚙️ NotificationService"]
            EVENT_BUS["📡 EventBus"]
        end

        subgraph "Infrastructure Layer"
            REPO["🗃️ Repositories<br/>(SQLDelight impl)"]
            EVT_STORE["📋 EventStore<br/>(SQLDelight impl)"]
            SYNC_CLIENT["🔄 SyncClient<br/>(HTTP/WebSocket)"]
        end
    end

    INV_SVC --> INV_DOM
    CAT_SVC --> CAT_DOM
    SHOP_SVC --> SHOP_DOM
    SHARE_SVC --> SHARE_DOM
    NOTIF_SVC --> NOTIF_DOM

    SHOP_SVC -- "ItemMarkedAsPurchased" --> EVENT_BUS
    EVENT_BUS -- "triggers InventoryEntryAdded" --> INV_SVC
    INV_SVC -- "InventoryEntry events" --> EVENT_BUS
    EVENT_BUS -- "evaluates AlertRules" --> NOTIF_SVC

    INV_SVC --> REPO
    CAT_SVC --> REPO
    SHOP_SVC --> REPO
    SHARE_SVC --> REPO
    NOTIF_SVC --> REPO

    INV_SVC --> EVT_STORE
    EVT_STORE --> SYNC_CLIENT
```

### Layer Responsibilities

| Layer | Responsibility |
|---|---|
| **Domain** | Pure business logic — entities, value objects, events. No framework dependencies. |
| **Application** | Use cases / services — orchestrates domain objects, publishes events via EventBus. |
| **Infrastructure** | Technical implementations — database access, event store, network sync. |

---

## Design Decisions

### 1. Local-First Architecture

| Aspect | Decision |
|---|---|
| **What** | All data is stored locally first. The app is fully functional offline. |
| **Why** | A household inventory app must work instantly, even without internet. Users open the fridge and need to check/update stock in seconds. |
| **How** | SQLDelight provides a local SQLite database on each device. The sync server is only needed for multi-device/multi-user scenarios. |
| **Trade-off** | Adds complexity for sync and conflict resolution, but UX benefit is significant. |

### 2. Selective Event Sourcing

| Aspect | Decision |
|---|---|
| **What** | Only **inventory mutations** are stored as events. Other contexts use standard CRUD. |
| **Why** | Full event sourcing adds complexity everywhere. Inventory mutations are the only ones that need sync across devices and provide audit trail value. |
| **How** | The Local Event Store captures `InventoryEntryAdded`, `Opened`, `Consumed`, `Removed`, `Adjusted`. These events are synced to the server and replayed on other devices. |
| **Trade-off** | Simpler than full ES, but means Shopping and Catalog changes use last-writer-wins sync. Acceptable because inventory is the time-sensitive core. |

### 3. Kotlin Multiplatform (KMP)

| Aspect | Decision |
|---|---|
| **What** | Shared domain/application/data layer in Kotlin, platform-specific UI shells. |
| **Why** | Write business logic once, run on Android and iOS. Domain logic stays pure Kotlin with no platform dependencies. |
| **How** | KMP module contains domain, application, and infrastructure layers. Compose Multiplatform for shared UI. |
| **Trade-off** | KMP ecosystem is maturing but some libraries may lack iOS support. SQLDelight and Ktor are well-supported. |

### 4. Sync Strategy

| Aspect | Decision |
|---|---|
| **What** | Event-based sync with Dropbox-style conflict resolution. |
| **Why** | Simple, maintainable, fits the selective event sourcing approach. |
| **How** | Devices push local events to the server, pull remote events. For inventory: events are ordered by timestamp, last-writer-wins per field with conflict detection. For other contexts: standard last-writer-wins. |
| **Trade-off** | Not as robust as CRDTs, but much simpler to implement and debug. Acceptable for household-scale usage. |
| **Upgrade path** | Architecture allows upgrading to CRDTs or OT in a later phase if needed — the event-based foundation makes this possible without a full rewrite. |

### 5. Layered Architecture per Context

| Aspect | Decision |
|---|---|
| **What** | Clean separation: Domain → Application → Infrastructure. |
| **Why** | Domain layer stays testable and framework-free. Infrastructure can be swapped without affecting business logic. |
| **How** | Domain defines repository interfaces (ports). Infrastructure provides implementations (adapters). Application layer orchestrates via services. |
| **Trade-off** | More files/structure upfront, but pays off in testability and maintainability. |
