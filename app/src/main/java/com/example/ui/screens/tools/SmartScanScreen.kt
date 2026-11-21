package com.example.ui.screens.tools

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfFileRecord
import com.example.data.model.ScanFilter
import com.example.ui.SmartPdfViewModel
import com.example.ui.components.PdfResultCard
import com.example.ui.components.ProcessingBanner
import com.example.ui.components.ToolTopBar
import com.example.ui.theme.ElectricBlue
import com.example.util.FormatUtils
import java.io.File
import kotlin.math.roundToInt

@Composable
fun SmartScanScreen(
    viewModel: SmartPdfViewModel,
    onBack: () -> Unit,
    onOpenPdf: (PdfFileRecord) -> Unit
) {
    val state by viewModel.smartScanState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var showCameraMode by remember { mutableStateOf(state.pages.isEmpty()) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            cameraError = "Camera permission denied. You can still import photos to scan and enhance."
        } else {
            cameraError = null
        }
    }

    val photoImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPhotoForScan(uri)
            showCameraMode = false
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val record = state.resultRecord
        if (uri != null && record != null) {
            viewModel.exportPdfToUri(record, uri)
        }
    }

    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Smart Scan",
                subtitle = "${state.pages.size} scanned ${if (state.pages.size == 1) "page" else "pages"}",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showCameraMode = !showCameraMode }) {
                        Icon(
                            imageVector = if (showCameraMode) Icons.Filled.DocumentScanner else Icons.Filled.CameraAlt,
                            contentDescription = "Switch camera or editor"
                        )
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        onClick = { viewModel.saveScannedPagesAsPdf() },
                        enabled = state.pages.isNotEmpty() && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .height(52.dp)
                            .testTag("save_scan_pdf_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.isProcessing) "Saving Scan PDF..."
                            else "Save ${state.pages.size} Page(s) as PDF"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.isProcessing) {
                item {
                    ProcessingBanner(
                        message = "Processing scanned pages...",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            state.resultRecord?.let { record ->
                item {
                    PdfResultCard(
                        record = record,
                        badgeLabel = "Scan PDF Saved",
                        onOpen = { onOpenPdf(record) },
                        onDownload = { downloadLauncher.launch(record.name) },
                        onShare = {
                            FormatUtils.shareFile(context, File(record.filePath), "application/pdf")
                        },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            // Camera Capture Panel
            if (showCameraMode) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (hasCameraPermission) {
                                CameraXCaptureCard(
                                    onCaptured = { path ->
                                        viewModel.addCapturedScanFile(path)
                                        showCameraMode = false
                                    },
                                    onError = { msg -> cameraError = msg }
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CameraAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(44.dp)
                                    )
                                    Text(
                                        text = "Camera Access Needed to Scan",
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        text = "Grant camera permission to capture document pages directly, or import an existing document photo below.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Button(
                                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                                        modifier = Modifier.testTag("grant_camera_permission_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(Icons.Filled.CameraAlt, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("Allow Camera")
                                    }
                                }
                            }

                            cameraError?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    photoImportLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("scan_import_photo_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Import Photo to Scan & Enhance")
                            }
                        }
                    }
                }
            } else {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showCameraMode = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Scan Page")
                        }
                        OutlinedButton(
                            onClick = {
                                photoImportLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Add Photo")
                        }
                    }
                }
            }

            // Scanned Pages Strip + Editor
            if (state.pages.isNotEmpty()) {
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(state.pages, key = { _, p -> p.id }) { idx, _ ->
                            val selected = idx == state.selectedPageIndex
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.selectScanPage(idx) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Page ${idx + 1}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { viewModel.removeScanPage(idx) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.DeleteOutline,
                                            contentDescription = "Delete page ${idx + 1}",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                val activePage = state.pages.getOrNull(state.selectedPageIndex)
                if (activePage != null) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 600.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(290.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF0A0F1E)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    state.previewBitmap?.let { bmp ->
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Scanned page preview",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } ?: Text("Rendering preview...", color = Color.White)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Enhancement Filter", style = MaterialTheme.typography.labelLarge)
                                    OutlinedButton(
                                        onClick = { viewModel.updateCurrentScanPage(rotateDelta = 90) },
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Rotate 90°")
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ScanFilter.entries.forEach { filter ->
                                        FilterChip(
                                            selected = activePage.filter == filter,
                                            onClick = { viewModel.updateCurrentScanPage(filter = filter) },
                                            label = { Text(filter.label) }
                                        )
                                    }
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Brightness", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            "${activePage.brightness.roundToInt()}",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Slider(
                                        value = activePage.brightness,
                                        onValueChange = { viewModel.updateCurrentScanPage(brightness = it) },
                                        valueRange = -60f..60f
                                    )
                                }

                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Crop Margins", style = MaterialTheme.typography.labelLarge)
                                        Text(
                                            "${(activePage.cropMarginPercent * 100).roundToInt()}%",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                    }
                                    Slider(
                                        value = activePage.cropMarginPercent,
                                        onValueChange = { viewModel.updateCurrentScanPage(cropMarginPercent = it) },
                                        valueRange = 0f..0.22f
                                    )
                                }

                                OutlinedTextField(
                                    value = state.outputName,
                                    onValueChange = viewModel::updateScanOutputName,
                                    label = { Text("Output PDF Name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraXCaptureCard(
    onCaptured: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    DisposableEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (t: Throwable) {
                Log.w("SmartScan", "Camera unavailable", t)
                onError("Live camera unavailable on this device. Use 'Import Photo to Scan & Enhance' below.")
            }
        }, executor)

        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.BottomCenter
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
            // Capture shutter button
            Surface(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .size(64.dp)
                    .clip(CircleShape)
                    .clickable {
                        val scansDir = File(context.cacheDir, "scans").apply { if (!exists()) mkdirs() }
                        val outFile = File(scansDir, "cam_${System.currentTimeMillis()}.jpg")
                        val options = ImageCapture.OutputFileOptions.Builder(outFile).build()
                        imageCapture.takePicture(
                            options,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    onCaptured(outFile.absolutePath)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    onError("Capture failed: ${exception.localizedMessage}")
                                }
                            }
                        )
                    }
                    .testTag("camera_shutter_button"),
                shape = CircleShape,
                color = ElectricBlue,
                border = BorderStroke(3.dp, Color.White)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Capture page",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}
