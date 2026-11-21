package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CompressionLevel
import com.example.data.model.PdfFileRecord
import com.example.ui.SmartPdfViewModel
import com.example.ui.components.LocalPdfPickerDialog
import com.example.ui.components.PdfResultCard
import com.example.ui.components.ProcessingBanner
import com.example.ui.components.ToolTopBar
import com.example.ui.theme.EmeraldSuccess
import com.example.util.FormatUtils
import java.io.File

@Composable
fun CompressPdfScreen(
    viewModel: SmartPdfViewModel,
    onBack: () -> Unit,
    onOpenPdf: (PdfFileRecord) -> Unit
) {
    val state by viewModel.compressPdfState.collectAsStateWithLifecycle()
    val localFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPickerDialog by remember { mutableStateOf(false) }

    val devicePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectPdfForCompression(uri)
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val record = state.summary?.record
        if (uri != null && record != null) {
            viewModel.exportPdfToUri(record, uri)
        }
    }

    Scaffold(
        topBar = {
            ToolTopBar(
                title = "Compress PDF",
                subtitle = "Low, Medium & High reduction",
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
                        onClick = { viewModel.runCompression() },
                        enabled = state.selectedSource != null && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .height(52.dp)
                            .testTag("compress_pdf_run_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.Compress, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isProcessing) "Compressing..." else "Compress PDF Now")
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                    Icon(
                                        Icons.Filled.PictureAsPdf,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            src.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "${src.pageCount} pages • Original: ${FormatUtils.formatFileSize(src.sizeBytes)}",
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
                                .testTag("compress_pick_pdf_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (state.selectedSource == null) "Select PDF" else "Change PDF")
                        }
                    }
                }
            }

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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("2. Compression Level", style = MaterialTheme.typography.titleMedium)
                        CompressionLevel.entries.forEach { level ->
                            val selected = state.level == level
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { viewModel.updateCompressionLevel(level) },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { viewModel.updateCompressionLevel(level) }
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column {
                                        Text(
                                            "${level.label} Compression",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            level.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = state.outputName,
                            onValueChange = viewModel::updateCompressionOutputName,
                            label = { Text("Output File Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }
            }

            if (state.isProcessing) {
                item {
                    ProcessingBanner(
                        message = "Optimizing and compressing pages...",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            state.summary?.let { summary ->
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        border = BorderStroke(1.dp, EmeraldSuccess.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (summary.savedPercent > 0) "Size reduced by ${summary.savedPercent}%"
                                    else "Compression complete",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = EmeraldSuccess
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Original Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(FormatUtils.formatFileSize(summary.originalSizeBytes), style = MaterialTheme.typography.titleMedium)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Compressed Size", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        FormatUtils.formatFileSize(summary.compressedSizeBytes),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldSuccess
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    PdfResultCard(
                        record = summary.record,
                        badgeLabel = "Compressed PDF",
                        onOpen = { onOpenPdf(summary.record) },
                        onDownload = { downloadLauncher.launch(summary.record.name) },
                        onShare = {
                            FormatUtils.shareFile(context, File(summary.record.filePath), "application/pdf")
                        },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }
        }
    }

    if (showPickerDialog) {
        LocalPdfPickerDialog(
            title = "Select PDF to Compress",
            files = localFiles,
            onPickFromDevice = { devicePicker.launch(arrayOf("application/pdf")) },
            onSelectLocal = { viewModel.selectLocalPdfForCompression(it) },
            onDismiss = { showPickerDialog = false }
        )
    }
}
