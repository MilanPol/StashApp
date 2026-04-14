package com.stashapp.android.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.stashapp.shared.data.parsing.ReceiptLineParser
import java.util.concurrent.Executors

@Composable
fun RecipeTextScannerView(
    instructionText: String,
    filterLines: Boolean = true,
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
    var detectedLines by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var candidateLines by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var stableFrameCount by rememberSaveable { mutableIntStateOf(0) }
    var totalFrameCount by rememberSaveable { mutableIntStateOf(0) }
    var isFrozen by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Use a ref so the analyzer closure always sees the latest value of isFrozen
    // without causing a recomposition-driven camera rebind.
    val isFrozenRef = remember { mutableStateOf(false) }
    isFrozenRef.value = isFrozen

    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview area (top 50%)
        if (!isFrozen) {
            Box(modifier = Modifier.weight(0.5f).fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }

                        // Bind camera once in factory — not in update
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
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
                                        if (!isFrozenRef.value) {
                                            processTextImageProxy(textRecognizer, imageProxy) { lines ->
                                                val filteredLines = if (filterLines) {
                                                    lines.filter { ReceiptLineParser.isProductLine(it) }
                                                } else {
                                                    lines.filter { it.length >= 2 }
                                                }
                                                
                                                if (!filterLines) {
                                                    // Description mode: no stabilization needed,
                                                    // show live results and let user capture manually
                                                    if (filteredLines.isNotEmpty()) {
                                                        detectedLines = filteredLines
                                                        stableFrameCount = 3 // keeps the UI from showing "Stabilizing..."
                                                    }
                                                } else if (filteredLines.isNotEmpty()) {
                                                    totalFrameCount++
                                                    if (candidateLines.isEmpty()) {
                                                        // First frame with content — seed the candidate
                                                        candidateLines = filteredLines
                                                        stableFrameCount = 1
                                                    } else {
                                                        // Compare content overlap with previous candidate
                                                        // Normalize aggressively: collapse whitespace, strip punctuation
                                                        // so OCR noise like "175 g" vs "175g" doesn't break matching
                                                        val normalize = { s: String ->
                                                            s.lowercase().trim()
                                                                .replace(Regex("""[\s]+"""), "")
                                                                .replace(Regex("""[^a-z0-9]"""), "")
                                                        }
                                                        val candidateSet = candidateLines.map(normalize).toSet()
                                                        val currentSet = filteredLines.map(normalize).toSet()
                                                        val intersection = candidateSet.intersect(currentSet).size
                                                        val union = candidateSet.union(currentSet).size
                                                        val similarity = if (union > 0) intersection.toDouble() / union else 0.0

                                                        if (similarity >= 0.35) {
                                                            stableFrameCount++
                                                            // Keep the version with more lines as the best candidate
                                                            if (filteredLines.size > candidateLines.size) {
                                                                candidateLines = filteredLines
                                                            }
                                                        } else {
                                                            candidateLines = filteredLines
                                                            stableFrameCount = 1
                                                        }
                                                    }

                                                    if (stableFrameCount >= 3 || totalFrameCount >= 8) {
                                                        detectedLines = candidateLines
                                                    }
                                                } else {
                                                    stableFrameCount = 0
                                                    candidateLines = emptyList()
                                                }
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
                        }, ContextCompat.getMainExecutor(ctx))

                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Live preview text overlay
                if (stableFrameCount < 3) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = "Stabilizing...",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else if (detectedLines.isNotEmpty()) {
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
                        text = instructionText,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val selectedLines = remember { mutableStateListOf<String>() }
                    LaunchedEffect(detectedLines) {
                        selectedLines.clear()
                        selectedLines.addAll(detectedLines)
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(detectedLines.size) { index ->
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
