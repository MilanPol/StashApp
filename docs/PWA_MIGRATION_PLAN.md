# StashApp PWA / KMP Migration Plan

This document outlines the strategic roadmap for migrating StashApp from an Android-only application to a Progressive Web App (PWA) and Android app using **Compose Multiplatform** and **Kotlin Multiplatform (KMP)**.

## Core Objective
Convert the existing Android single-platform repository into a Kotlin Multiplatform codebase, allowing the same codebase to compile an Android `.apk` and a standalone Web Application (Wasm/JS) while preserving all existing UI and business logic.

> **Note on Android Nativity:**
> Kotlin Multiplatform is completely different from frameworks like Flutter or React Native. When compiling for Android, the code compiles directly into standard JVM bytecode. Jetpack Compose UI renders natively, SQLite uses the native C drivers, and all hardware APIs act identically to a plain Android app. **StashApp will remain 100% native on Android with zero performance or architectural loss.**

---

## Codebase At-a-Glance

| Module | Files | Key Contents |
|--------|-------|-------------|
| `shared/` | 12 `.kt` files | Domain models, use cases, repositories, SQLDelight queries, parsers |
| `app/screens/` | 20 `.kt` files | 10 screens + 10 ViewModels (Dashboard, AddItem, Recipes, Restock, ShoppingList, etc.) |
| `app/components/` | 6 `.kt` files | `BarcodeScannerView`, `RecipeTextScannerView`, `InventoryItemCard`, dialogs |
| `app/` other | ~11 `.kt` files | `MainActivity`, workers, notifications, utilities |

---

## Phase 1: Project Reconstruction (~1 day)

Currently, both modules use Android-specific Gradle plugins. These must be restructured.

### Action Items
- Replace `org.jetbrains.kotlin.android` with `org.jetbrains.kotlin.multiplatform` in both modules.
- Set up target platforms in `build.gradle.kts`: `android()` + `wasmJs { browser() }`
- Restructure source folders to `commonMain/`, `androidMain/`, `wasmJsMain/`

| Task | Complexity | Time |
|------|-----------|------|
| Rewrite `build.gradle.kts` for both modules | Medium | 2–3 hours |
| Restructure folder layout | Low (mechanical) | 1–2 hours |
| Resolve initial compilation errors | Medium | 2–3 hours |

**Risk: Low** — Mostly configuration and file moves.

---

## Phase 2: SQLDelight Decoupling (~0.5 day)

Already using SQLDelight — just need to swap the driver per platform.

### Action Items
- Move `.sq` files and generated interfaces to `commonMain`
- Create `expect fun createDriver()` with `actual` implementations per platform
  - **Android**: `AndroidSqliteDriver` (unchanged)
  - **Web**: `WebWorkerDriver` (SQLite in IndexedDB)

| Task | Complexity | Time |
|------|-----------|------|
| Move `.sq` files to `commonMain` | Low | 1 hour |
| Create `expect/actual` driver factory | Low | 1–2 hours |
| Add `web-worker-driver` dependency and verify | Medium | 2 hours |

**Risk: Low** — SQLDelight was designed for exactly this.

---

## Phase 3: UI / ViewModels / Navigation (~2–3 days)

Most Compose UI ports 1:1, but Android-bound lifecycle classes must be swapped.

### Action Items
- Swap `androidx.compose.*` → `org.jetbrains.compose` equivalents
- Migrate 10 ViewModels to KMP-compatible ViewModel
- Replace `NavHost` with Voyager or PreCompose
- Replace DataStore with `multiplatform-settings`

| Task | Complexity | Time |
|------|-----------|------|
| Swap Compose dependencies | Low | 1–2 hours |
| Migrate 10 ViewModels to KMP ViewModel | Medium | 3–4 hours |
| Replace `NavHost` with Voyager/PreCompose | **High** | 4–6 hours |
| Replace DataStore with multiplatform-settings | Low | 1–2 hours |
| Fix `stringResource` / resource management | Medium | 2–3 hours |
| Verify all 10 screens render on Android | Medium | 2–3 hours |

**Risk: Medium** — Navigation rewrite is the riskiest sub-task. The current `MainActivity` has ~10 routes with deep argument passing that must be converted to `Screen` objects.

---

## Phase 4: Camera & OCR Hardware (~3–4 days)

The biggest challenge. ML Kit and CameraX don't exist in browsers.

### Action Items
- Define `expect` scanner interfaces in `commonMain`
- **Android**: Wrap existing CameraX/ML Kit components (already written)
- **Web**: Implement JS-interop alternatives:
  - `getUserMedia` for camera access
  - **ZXing.js** for barcode/EAN scanning
  - **Tesseract.js** for receipt/recipe OCR

| Task | Complexity | Time |
|------|-----------|------|
| Define `expect/actual` scanner interfaces | Low | 1–2 hours |
| Wrap existing Android code as `actual` | Medium | 2–3 hours |
| Implement Web camera access (getUserMedia) | Medium | 3–4 hours |
| Integrate ZXing.js for barcode scanning | Medium | 3–4 hours |
| Integrate Tesseract.js for OCR | **High** | 4–6 hours |
| Build Compose scanner UI for Web | Medium | 3–4 hours |
| Testing & error handling | Medium | 2–3 hours |

**Risk: High** — Tesseract.js OCR will be slower than ML Kit. Browser permission handling varies across browsers.

---

## Phase 5: PWA Wrapping & Deployment (~1 day)

Make the web build installable and offline-capable.

### Action Items
- Configure Webpack/Wasm build output
- Create `manifest.json` (name, icons, `standalone` display mode)
- Write Service Worker for offline caching
- Deploy (Vercel / Netlify / GitHub Pages)

| Task | Complexity | Time |
|------|-----------|------|
| Configure Webpack/Wasm output | Low | 1–2 hours |
| Create `manifest.json` + icons | Low | 1 hour |
| Write Service Worker for offline | Medium | 2–3 hours |
| Deploy and test installation | Low | 1–2 hours |

**Risk: Low** — Standard web deployment work.

---

## Overall Effort Summary

| Phase | Description | Time | Risk |
|-------|-------------|------|------|
| 1 | Project Reconstruction | ~1 day | Low |
| 2 | SQLDelight Decoupling | ~0.5 day | Low |
| 3 | UI / ViewModels / Navigation | ~2–3 days | Medium |
| 4 | Camera & OCR (Hardware) | ~3–4 days | **High** |
| 5 | PWA Wrapping | ~1 day | Low |
| | | | |
| **Total** | | **~8–10 working days** | |

> **Note:** These estimates assume working with AI assistance. Without it, expect roughly 2–3× the time (~3–4 weeks). The Android app remains 100% native throughout all phases.
