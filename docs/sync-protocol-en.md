# Sync Protocol — Inventory App

## Core Principle
Cloudflare Workers KV as a temporary message queue. No permanent data storage — only a relay for mutations between clients. Data always lives locally on the device.

---

## Identity and Groups
Users generate a human-readable group ID on first use:
```
APPLE-MANGO-7823
```
This ID is shared via QR code or chat. The ID serves as both the group identifier and the basis for the encryption key.

---

## Encryption
```
Group ID → PBKDF2/Argon2 → AES-256-GCM encryption key
Mutation payload → AES-256-GCM encrypt → base64 → Cloudflare
```
Cloudflare only sees encrypted blobs, never plaintext data. Transport is additionally encrypted via HTTPS/TLS.

---

## Mutation Format
```json
{
  "id": "uuid",
  "timestamp": "2026-04-04T10:00:00Z",
  "userId": "abc",
  "type": "UPDATE_ITEM",
  "payload": { "itemId": "123", "quantity": 5 }
}
```

---

## Conflict Resolution
Last-write-wins based on timestamp. Mutations are timestamped making this deterministic and straightforward.

---

## Cloudflare API Endpoints
```
POST   /mutations/{groupId}                    → submit mutation
GET    /mutations/{groupId}?since={timestamp}  → fetch since last sync
DELETE /mutations/{groupId}                    → confirm after processing
```

---

## TTL
KV entries have a TTL of **30 days**. Mutations that are not fetched are automatically deleted. No permanent storage.

---

## Android Polling via WorkManager
```kotlin
// Background task every 15 minutes
WorkManager task
  → GET /mutations/{groupId}?since={lastSyncTimestamp}
  → decrypt payload
  → apply mutations to local SQLite
  → send DELETE confirmation
  → update lastSyncTimestamp locally

// Plus: always poll immediately on app start
```

---

## Community Contributions (Opt-in)
```kotlin
if (settings.contributeToCommunity) {
    pushPendingMutations()
}
```

---

## Dependencies
| Component | Purpose |
|---|---|
| Cloudflare Workers | Sync logic and API endpoints |
| Cloudflare KV | Temporary mutation storage with TTL |
| Android WorkManager | Background polling |
| javax.crypto | AES-256-GCM encryption, no external dependencies |

---

## Notes
- No server owns the data — Cloudflare is purely a relay
- Cloudflare KV free tier is more than sufficient for small user groups
- Architecture is reusable for other apps built on the same principle
