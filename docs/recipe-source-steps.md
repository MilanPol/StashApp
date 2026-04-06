# Recipe Source Feature — Step-by-Step Implementation Guide

This document adds the **Recipe Source** feature to the existing Recipe management system. It allows users to:
1. **Enter a URL** pointing to an online recipe (e.g. a blog or cooking site).
2. **Scan text from a photo** using the device camera and ML Kit OCR, then pick lines to import as ingredients.

> [!IMPORTANT]
> Follow the steps in order. Each step builds on the previous one. Do NOT skip ahead.
> After each step, run `./gradlew assembleDebug` to verify everything compiles.

> [!CAUTION]
> **DO NOT** modify any files from the existing Recipe feature (domain models, database schema, repository) UNLESS this guide explicitly tells you to. The `RecipeSource` sealed class and the `source_type`/`source_ref` columns already exist and work correctly. You are only adding UI and the ML Kit text scanner.

---

## Prerequisites — Read These Files First

Before starting any step, read and understand these existing files:

| File | Why |
|---|---|
| `shared/src/main/java/com/stashapp/shared/domain/Recipe.kt` | Contains `RecipeSource` sealed class with `Manual`, `ImportedUrl(url)`, `ScannedPhoto(imageRef)`. The domain model is already complete. |
| `shared/src/main/java/com/stashapp/shared/data/SqlDelightRecipeRepository.kt` | Contains `encodeSource()` and `decodeSource()` methods that already persist `RecipeSource` to the `source_type` and `source_ref` columns. No changes needed here. |
| `shared/src/main/sqldelight/com/stashapp/shared/db/Recipe.sq` | The `recipe` table already has `source_type TEXT NOT NULL DEFAULT 'MANUAL'` and `source_ref TEXT` columns. No schema changes needed. |
| `app/src/main/java/com/stashapp/android/ui/components/BarcodeScannerView.kt` | Example of CameraX + ML Kit integration pattern. The text scanner will follow this exact same pattern but use `TextRecognition` instead of `BarcodeScanning`. |
| `app/build.gradle.kts` | Current dependencies. CameraX is already included. ML Kit barcode-scanning is already included. You need to ADD `text-recognition`. |
| `app/src/main/java/com/stashapp/android/ui/screens/AddEditRecipeScreen.kt` | The recipe editor screen. This is the main file you will modify. Currently it has no source selection UI. |
| `app/src/main/java/com/stashapp/android/MainActivity.kt` | Navigation setup. The `AddEditRecipeScreen` is called from `composable("new_recipe")` and `composable("edit_recipe/{recipeId}")`. Both already pass `recipeRepository` and `inventoryRepository`. |
| `app/src/main/res/values/strings.xml` | English string resources. All user-facing text must be a string resource. |
| `app/src/main/res/values-nl/strings.xml` | Dutch string resources. Every English string MUST have a Dutch translation. |
| `app/src/main/AndroidManifest.xml` | Already has `<uses-permission android:name="android.permission.CAMERA" />`. No changes needed. |

---

## Phase 1: Add ML Kit Text Recognition Dependency

### Step 1.1 — Add the text-recognition dependency

**File to MODIFY**: `app/build.gradle.kts`

Find this existing block near the end of the `dependencies` block:

```kotlin
    // Barcode Scanning (CameraX + ML Kit)
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
```

Add ONE line directly after the `barcode-scanning` line:

```kotlin
    implementation("com.google.mlkit:text-recognition:16.0.0")
```

The block should now look like:

```kotlin
    // Barcode Scanning (CameraX + ML Kit)
    val cameraVersion = "1.3.1"
    implementation("androidx.camera:camera-camera2:$cameraVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion")
    implementation("androidx.camera:camera-view:$cameraVersion")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.google.mlkit:text-recognition:16.0.0")
```

**Do NOT change any other dependencies.**

**Verify**: `./gradlew :app:dependencies` — should resolve without errors.

---

## Phase 2: Create the Text Scanner Component

### Step 2.1 — Create RecipeTextScannerView

**File to CREATE**: `app/src/main/java/com/stashapp/android/ui/components/RecipeTextScannerView.kt`

**Package**: `com.stashapp.android.ui.components`

This composable provides a camera preview that scans text using ML Kit's `TextRecognition`. It is modeled after the existing `BarcodeScannerView.kt` but uses `TextRecognizer` instead of `BarcodeScanner`.

**What to write**:

```kotlin
package com.stashapp.android.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.stashapp.android.R
import java.util.concurrent.Executors

@Composable
fun RecipeTextScannerView(
    onTextCaptured: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    val textRecognizer = remember {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Holds the latest recognized lines of text
    var detectedLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var isFrozen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview area (top 60%)
        if (!isFrozen) {
            Box(modifier = Modifier.weight(0.5f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { previewView ->
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()

                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                                .also {
                                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                                        if (!isFrozen) {
                                            processTextImageProxy(textRecognizer, imageProxy) { lines ->
                                                detectedLines = lines
                                            }
                                        } else {
                                            imageProxy.close()
                                        }
                                    }
                                }

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                            try {
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) {
                                Log.e("RecipeScanner", "Use case binding failed", e)
                            }
                        }, ContextCompat.getMainExecutor(context))
                    }
                )

                // Live preview text overlay
                if (detectedLines.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "${detectedLines.size} ${stringResource(R.string.scan_lines_detected)}",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Bottom controls
        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.let {
                if (isFrozen) it.weight(1f).fillMaxWidth()
                else it.weight(0.5f).fillMaxWidth()
            }
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (!isFrozen) {
                    // Pre-capture: show instructions + capture button
                    Text(
                        text = stringResource(R.string.scan_instructions),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Button(
                            onClick = { isFrozen = true },
                            modifier = Modifier.weight(1f),
                            enabled = detectedLines.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.scan_capture))
                        }
                    }
                } else {
                    // Post-capture: show detected lines with checkboxes
                    Text(
                        text = stringResource(R.string.scan_select_lines),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val selectedLines = remember { mutableStateListOf<String>() }
                    LaunchedEffect(detectedLines) {
                        selectedLines.clear()
                        selectedLines.addAll(detectedLines)
                    }

                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        androidx.compose.foundation.lazy.items(detectedLines.size) { index ->
                            val line = detectedLines[index]
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = selectedLines.contains(line),
                                    onCheckedChange = { checked ->
                                        if (checked) selectedLines.add(line)
                                        else selectedLines.remove(line)
                                    }
                                )
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isFrozen = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.scan_retake))
                        }
                        Button(
                            onClick = { onTextCaptured(selectedLines.toList()) },
                            modifier = Modifier.weight(1f),
                            enabled = selectedLines.isNotEmpty()
                        ) {
                            Text(stringResource(R.string.scan_use_selected))
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun processTextImageProxy(
    textRecognizer: com.google.mlkit.vision.text.TextRecognizer,
    imageProxy: ImageProxy,
    onLinesDetected: (List<String>) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        textRecognizer.process(image)
            .addOnSuccessListener { visionText ->
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { it.text.trim() }
                }.filter { it.isNotBlank() }
                onLinesDetected(lines)
            }
            .addOnFailureListener {
                Log.e("RecipeScanner", "Text recognition failed", it)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}
```

**Key rules**:
- Follow the EXACT same CameraX pattern as `BarcodeScannerView.kt`: `ProcessCameraProvider`, `Preview`, `ImageAnalysis`, `CameraSelector.DEFAULT_BACK_CAMERA`.
- Use `TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)` for the Latin text recognizer.
- The `isFrozen` state controls whether to keep analyzing frames or stop (when user taps "Capture").
- After capture, show a **checklist** of detected lines. The user can uncheck lines they don't want.
- When the user taps "Use Selected", call `onTextCaptured(selectedLines)`.
- All strings MUST use `stringResource(R.string.xxx)` — the actual string definitions are added in Step 3.1.

**Verify**: Do NOT run a build yet — we need to add the string resources first (Step 3.1).

---

## Phase 3: Add String Resources

### Step 3.1 — Add English string resources

**File to MODIFY**: `app/src/main/res/values/strings.xml`

Find the closing `</resources>` tag. Insert these strings **immediately BEFORE** it:

```xml
    <!-- Recipe Source Feature -->
    <string name="recipe_source_label">Source</string>
    <string name="recipe_source_manual">Manual entry</string>
    <string name="recipe_source_url">Website</string>
    <string name="recipe_source_scan">Photo scan</string>
    <string name="recipe_source_url_hint">Recipe URL (e.g. https://…)</string>
    <string name="recipe_source_url_saved">Reference saved</string>
    <string name="scan_instructions">Point your camera at a recipe. Text will be detected automatically.</string>
    <string name="scan_capture">Capture</string>
    <string name="scan_retake">Retake</string>
    <string name="scan_select_lines">Select lines to import as ingredients:</string>
    <string name="scan_use_selected">Use selected</string>
    <string name="scan_lines_detected">lines detected</string>
    <string name="scan_start">Scan recipe</string>
    <string name="recipe_source_url_open">Open link</string>
```

**Do NOT modify or remove any existing strings.**

---

### Step 3.2 — Add Dutch string resources

**File to MODIFY**: `app/src/main/res/values-nl/strings.xml`

Find the closing `</resources>` tag. Insert these strings **immediately BEFORE** it:

```xml
    <!-- Recipe Source Feature -->
    <string name="recipe_source_label">Bron</string>
    <string name="recipe_source_manual">Handmatig invoeren</string>
    <string name="recipe_source_url">Website</string>
    <string name="recipe_source_scan">Foto scannen</string>
    <string name="recipe_source_url_hint">Recept URL (bijv. https://…)</string>
    <string name="recipe_source_url_saved">Referentie opgeslagen</string>
    <string name="scan_instructions">Richt je camera op een recept. Tekst wordt automatisch gedetecteerd.</string>
    <string name="scan_capture">Vastleggen</string>
    <string name="scan_retake">Opnieuw</string>
    <string name="scan_select_lines">Selecteer regels om te importeren als ingrediënten:</string>
    <string name="scan_use_selected">Selectie gebruiken</string>
    <string name="scan_lines_detected">regels gedetecteerd</string>
    <string name="scan_start">Recept scannen</string>
    <string name="recipe_source_url_open">Link openen</string>
```

**Do NOT modify or remove any existing strings.**

**Verify**: `./gradlew assembleDebug` — should compile now (with the scanner component and all strings).

---

## Phase 4: Update AddEditRecipeScreen with Source Selection

### Step 4.1 — Modify AddEditRecipeScreen

**File to MODIFY**: `app/src/main/java/com/stashapp/android/ui/screens/AddEditRecipeScreen.kt`

This is the most complex step. You will make these changes to the existing file:

#### 4.1a — Add new imports

At the top of the file, add these imports (merge with existing imports, do not duplicate):

```kotlin
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Edit
import com.stashapp.shared.domain.RecipeSource
import com.stashapp.android.ui.components.RecipeTextScannerView
```

The file already imports `com.stashapp.shared.domain.*` which covers `RecipeSource`, but verify this is the case. If the file imports specific classes like `Recipe`, `RecipeIngredient`, `RecipeRepository` individually, add `import com.stashapp.shared.domain.RecipeSource` explicitly.

#### 4.1b — Add source state variables

Inside the `AddEditRecipeScreen` composable function, find the state variable declarations. They currently look like:

```kotlin
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var servings by remember { mutableStateOf("4") }
    var ingredients by remember { mutableStateOf(listOf(RecipeIngredient())) }
```

Add these lines **directly after** the `ingredients` variable:

```kotlin
    var sourceType by remember { mutableStateOf("MANUAL") }
    var sourceRef by remember { mutableStateOf("") }
    var showScanner by remember { mutableStateOf(false) }
```

#### 4.1c — Load source when editing

Inside the `LaunchedEffect(recipeId)` block, find where the recipe fields are loaded:

```kotlin
            if (recipe != null) {
                name = recipe.name
                description = recipe.description ?: ""
                servings = recipe.servings.toString()
                ingredients = recipeRepository.getIngredientsForRecipe(recipeId)
                if (ingredients.isEmpty()) ingredients = listOf(RecipeIngredient(recipeId = recipeId))
            }
```

Add these two lines **after** the `if (ingredients.isEmpty())` line, still inside the `if (recipe != null)` block:

```kotlin
                sourceType = when (recipe.source) {
                    is RecipeSource.Manual -> "MANUAL"
                    is RecipeSource.ImportedUrl -> "URL"
                    is RecipeSource.ScannedPhoto -> "PHOTO"
                }
                sourceRef = when (recipe.source) {
                    is RecipeSource.ImportedUrl -> (recipe.source as RecipeSource.ImportedUrl).url
                    is RecipeSource.ScannedPhoto -> (recipe.source as RecipeSource.ScannedPhoto).imageRef
                    else -> ""
                }
```

#### 4.1d — Pass source to Recipe constructor on save

Find the save logic inside the `IconButton(onClick = { ... })` in the top bar actions. It currently creates the recipe like:

```kotlin
                                val recipe = Recipe(
                                    id = recipeId ?: java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    description = description.ifBlank { null },
                                    servings = servings.toIntOrNull() ?: 4
                                )
```

Replace that with:

```kotlin
                                val source = when (sourceType) {
                                    "URL" -> RecipeSource.ImportedUrl(sourceRef)
                                    "PHOTO" -> RecipeSource.ScannedPhoto(sourceRef)
                                    else -> RecipeSource.Manual
                                }
                                val recipe = Recipe(
                                    id = recipeId ?: java.util.UUID.randomUUID().toString(),
                                    name = name,
                                    description = description.ifBlank { null },
                                    servings = servings.toIntOrNull() ?: 4,
                                    source = source
                                )
```

#### 4.1e — Add source selection UI to the LazyColumn

Inside the `LazyColumn`, find the Servings `item` block:

```kotlin
                item {
                    OutlinedTextField(
                        value = servings,
                        onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) servings = it },
                        label = { Text(stringResource(R.string.recipe_servings_hint)) },
                        ...
                    )
                }
                
                // Ingredients Section
```

Add a NEW `item` block **between** the Servings item and the `// Ingredients Section` comment:

```kotlin
                // Source Section
                item {
                    Text(
                        stringResource(R.string.recipe_source_label),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = sourceType == "MANUAL",
                            onClick = { sourceType = "MANUAL" },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text(stringResource(R.string.recipe_source_manual), maxLines = 1)
                        }
                        SegmentedButton(
                            selected = sourceType == "URL",
                            onClick = { sourceType = "URL" },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text(stringResource(R.string.recipe_source_url), maxLines = 1)
                        }
                        SegmentedButton(
                            selected = sourceType == "PHOTO",
                            onClick = { sourceType = "PHOTO" },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = { Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        ) {
                            Text(stringResource(R.string.recipe_source_scan), maxLines = 1)
                        }
                    }
                }

                // Conditional source content
                if (sourceType == "URL") {
                    item {
                        OutlinedTextField(
                            value = sourceRef,
                            onValueChange = { sourceRef = it },
                            label = { Text(stringResource(R.string.recipe_source_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }

                if (sourceType == "PHOTO") {
                    item {
                        Button(
                            onClick = { showScanner = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.scan_start))
                        }
                    }
                }
```

#### 4.1f — Add the Scanner Dialog

At the very end of the `AddEditRecipeScreen` composable function (after the `Scaffold` closing brace, but still inside the function), add:

```kotlin
    if (showScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            )
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                RecipeTextScannerView(
                    onTextCaptured = { lines ->
                        // Convert each selected line to a RecipeIngredient
                        val newIngredients = lines.map { line ->
                            RecipeIngredient(
                                recipeId = recipeId ?: "",
                                name = line
                            )
                        }
                        ingredients = newIngredients + ingredients
                        sourceRef = "scanned_${System.currentTimeMillis()}"
                        showScanner = false
                    },
                    onDismiss = { showScanner = false }
                )
            }
        }
    }
```

**Key rules**:
- The dialog must be FULLSCREEN. Use `DialogProperties(usePlatformDefaultWidth = false)` and `Modifier.fillMaxSize()`.
- Scanned lines are converted to `RecipeIngredient` objects with just the `name` field filled in. The user can then edit the quantity and unit manually.
- New scanned ingredients are PREPENDED to the existing list (same pattern as the "Add Ingredient" button).
- `sourceRef` is set to a timestamp string to record that scanning was used.
- When the dialog is dismissed (cancel or text captured), `showScanner` is set to `false`.

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

## Phase 5: Show Source Info on Recipe List Cards

### Step 5.1 — Update RecipeListScreen to show source indicator

**File to MODIFY**: `app/src/main/java/com/stashapp/android/ui/screens/RecipeListScreen.kt`

Add this import at the top:

```kotlin
import com.stashapp.shared.domain.RecipeSource
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.CameraAlt
```

Find the `RecipeCard` composable. Inside the `Row` that contains the servings text and the Cook button, add a source icon **between** the servings text and the Cook button. The current Row contents look like:

```kotlin
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.recipe_servings, recipe.servings),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                
                Button(
                    onClick = onCookClick,
                    ...
```

Add the source icon after the servings Text and before the Button:

```kotlin
                // Source indicator icon
                when (recipe.source) {
                    is RecipeSource.ImportedUrl -> Icon(
                        Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    is RecipeSource.ScannedPhoto -> Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    else -> { /* No icon for manual */ }
                }
```

**Verify**: `./gradlew :app:compileDebugKotlin` — should compile.

---

## Phase 6: Verification

### Step 6.1 — Full build check

Run:
```bash
./gradlew assembleDebug
```

This must complete with zero errors.

### Step 6.2 — Manual testing checklist

After installing the debug APK on a device:

1. **Manual mode (default)**: Open "Add Recipe". The Source section should show three buttons: "Manual entry" (selected by default), "Website", "Photo scan". No extra fields should appear. Save a recipe — it should work as before.

2. **Website URL mode**: Select the "Website" button. A URL text field should appear. Enter `https://www.example.com/pasta`. Save the recipe. Go back to the recipe list — the recipe card should show a small globe icon (🌐) next to the servings count. Open the recipe for editing — the URL field should still contain the URL.

3. **Photo scan mode**: Select "Photo scan". A "Scan recipe" button should appear. Tap it. The camera should open (grant permission if asked). Point it at some printed text (a cookbook, a recipe webpage on another screen). Lines of text should be detected (counter shows "X lines detected"). Tap "Capture". A checklist of detected lines should appear. Uncheck any irrelevant lines. Tap "Use selected". The dialog should close and the selected lines should appear as ingredient names at the TOP of the ingredient list. Save the recipe.

4. **Source persists**: Close and reopen the app. Edit the recipe you just created. The source type should still be correctly selected (URL or Photo), and the reference should be preserved.

---

## Summary of all files changed/created

| Action | File |
|---|---|
| **MODIFY** | `app/build.gradle.kts` — add `text-recognition` dependency |
| **CREATE** | `app/src/main/java/com/stashapp/android/ui/components/RecipeTextScannerView.kt` |
| **MODIFY** | `app/src/main/res/values/strings.xml` — add source/scan strings |
| **MODIFY** | `app/src/main/res/values-nl/strings.xml` — add Dutch source/scan strings |
| **MODIFY** | `app/src/main/java/com/stashapp/android/ui/screens/AddEditRecipeScreen.kt` — add source UI |
| **MODIFY** | `app/src/main/java/com/stashapp/android/ui/screens/RecipeListScreen.kt` — add source icon |

> [!WARNING]
> **Do NOT modify these files** — they are already complete and correct:
> - `shared/src/main/java/com/stashapp/shared/domain/Recipe.kt`
> - `shared/src/main/java/com/stashapp/shared/data/SqlDelightRecipeRepository.kt`
> - `shared/src/main/sqldelight/com/stashapp/shared/db/Recipe.sq`
> - `shared/src/main/sqldelight/com/stashapp/shared/db/4.sqm`
> - `app/src/main/java/com/stashapp/android/MainActivity.kt`
> - `app/src/main/AndroidManifest.xml`
