package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.ui.SmartPdfViewModel
import com.example.ui.components.LocalPdfPickerDialog
import com.example.ui.components.ProcessingBanner
import com.example.ui.components.ToolTopBar
import com.example.util.FormatUtils
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfToJpgScreen(
    viewModel: SmartPdfViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.pdfToJpgState.collectAsStateWithLifecycle()
    val localFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPickerDialog by remember { mutableStateOf(false) }
    var pendingSaveImagePath by remember { mutableStateOf<String?>(null) }

    val devicePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectPdfForJpg(uri)
    }

    val saveSingleJpgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/jpeg")
    ) { uri ->
        val path = pendingSaveImagePath
        if (uri != null && path != null) {
            val file = File(path)
            viewModel.exportFileToUri(file, uri, "Saved ${file.name}")
        }
        pendingSaveImagePath = null
    }

    val saveAllZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val zipPath = state.result?.zipFilePath
        if (uri != null && zipPath != null) {
            viewModel.exportFileToUri(File(zipPath), uri, "Saved all JPG pages as ZIP")
        }
    }

    Scaffold(
        topBar = {
            ToolTopBar(
                title = "PDF to JPG",
                subtitle = "Convert every page to JPG",
                onBack = onBack
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
                        onClick = { viewModel.convertPdfToJpg() },
                        enabled = state.selectedSource != null && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .height(52.dp)
                            .testTag("convert_pdf_to_jpg_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isProcessing) "Converting Pages..." else "Convert to JPG Images")
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
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("1. Choose PDF File", style = MaterialTheme.typography.titleMedium)
                        state.selectedSource?.let { src ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            src.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${src.pageCount} pages • ${FormatUtils.formatFileSize(src.sizeBytes)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { showPickerDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pdf_to_jpg_pick_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (state.selectedSource == null) "Select PDF" else "Change PDF")
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("JPG Quality", style = MaterialTheme.typography.labelLarge)
                                Text("${state.quality}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = state.quality.toFloat(),
                                onValueChange = { viewModel.updatePdfToJpgQuality(it.roundToInt()) },
                                valueRange = 40f..100f
                            )
                        }
                    }
                }
            }

            if (state.isProcessing) {
                item {
                    ProcessingBanner(
                        message = "Rendering PDF pages to JPG...",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            state.result?.let { result ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "${result.imagePaths.size} JPG Pages Ready",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val base = result.sourceName.removeSuffix(".pdf")
                                        saveAllZipLauncher.launch("${base}_JPG_Pages.zip")
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("download_all_jpg_zip"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Download All (ZIP)")
                                }
                                OutlinedButton(
                                    onClick = {
                                        FormatUtils.shareMultipleFiles(
                                            context = context,
                                            files = result.imagePaths.map { File(it) },
                                            mimeType = "image/jpeg",
                                            chooserTitle = "Share all JPG pages"
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("share_all_jpgs"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Share All")
                                }
                            }
                        }
                    }
                }

                itemsIndexed(result.imagePaths) { index, path ->
                    val file = File(path)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = "Page ${index + 1} JPG",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Page ${index + 1}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    FormatUtils.formatFileSize(file.length()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    pendingSaveImagePath = path
                                    saveSingleJpgLauncher.launch(file.name)
                                }
                            ) {
                                Icon(Icons.Filled.Download, contentDescription = "Save JPG page ${index + 1}")
                            }
                            IconButton(
                                onClick = {
                                    FormatUtils.shareFile(context, file, "image/jpeg")
                                }
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = "Share JPG page ${index + 1}")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPickerDialog) {
        LocalPdfPickerDialog(
            title = "Select PDF to Convert",
            files = localFiles,
            onPickFromDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onSelectLocal = { viewModel.selectLocalPdfForJpg(it) },
            onDismiss = { showPickerDialog = false }
        )
    }
}
