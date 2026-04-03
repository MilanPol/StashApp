# Notification System Design

This document outlines the technical design for implementing the expiration notification system in StashApp.

## 1. Objectives
- Provide timely alerts before items expire.
- Support per-item notification timing.
- Support global notification defaults.
- Support category-specific notification overrides.
- Ensure battery-efficient background processing.

## 2. Technical Stack
- **Scheduling**: `androidx.work:work-runtime-ktx` (WorkManager).
- **Persistence**: SQLDelight for item-specific dates; `androidx.datastore:datastore-preferences` for user settings.
- **UI**: Compose Material 3 `DatePicker` and `Switch` components.
- **Permissions**: `android.permission.POST_NOTIFICATIONS` (API 33+).

## 3. Data Model Changes

### SQLDelight (`InventoryEntry.sq`)
Added column to track when the alert should trigger:
```sql
ALTER TABLE inventory_entry ADD COLUMN alertAt INTEGER; -- Epoch milliseconds
```

### DataStore (Settings)
Keys to be stored:
- `global_alert_lead_days` (default: 1)
- `category_alert_lead_days_{id}` (mapping category ID to lead days)

## 4. Architecture

### Notification Worker
A `DailyExpiryWorker` will be registered to run once every 24 hours (e.g., at 08:00 AM).
1. Query `inventory_entry` for all items where `alertAt` is between *Now* and *Now + 24h*.
2. Group notifications if multiple items are expiring.
3. Call `NotificationManager` to display the alert.

### Scheduling Logic
- **On App Start/Boot**: Ensure the periodic `DailyExpiryWorker` is queued.
- **On Item Add/Edit**: Calculate `alertAt` based on the item's expiration date and the applicable lead time (Category > Global).

## 5. UI/UX Flow

### Add/Edit Item
- When an Expiration Date is selected, a "Notification" section becomes visible.
- Defaults to the Category or Global lead time.
- Allows user to "Customise Alert" (Date Picker).

### Settings Screen
- **Global Alert Lead Time**: Number picker for days.
- **Category Overrides**: List of categories with individual day pickers.
- **Test Notification**: Button to trigger an immediate dummy alert to verify permissions/sound.

## 6. Implementation Notes
- **Notification Channel**: Create a channel named "Expiration Alerts" with high importance.
- **Actions**: The notification should include a "Mark as Consumed" button to allow users to clear items without opening the app.
- **Permissions**: Check for notification permission on app launch or when the user first enables an alert.

---
*Created on: 2026-04-03*
