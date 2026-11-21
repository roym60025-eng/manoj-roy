package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfFileRecord
import com.example.ui.SmartPdfViewModel
import com.example.ui.components.LocalPdfPickerDialog
import com.example.ui.components.PdfResultCard
import com.example.ui.components.ProcessingBanner
import com.example.ui.components.ToolTopBar
import com.example.util.FormatUtils
import java.io.File

@Composable
fun MergePdfScreen(
    viewModel: SmartPdfViewModel,
    onBack: () -> Unit,
    onOpenPdf: (PdfFileRecord) -> Unit
) {
    val state by viewModel.mergePdfState.collectAsStateWithLifecycle()
    val localFiles by viewModel.allFiles.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showPickerDialog by remember { mutableStateOf(false) }

    val multiPdfPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addPdfUrisToMerge(uris)
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
                title = "Merge PDF",
                subtitle = "Combine & reorder PDFs",
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
                        onClick = { viewModel.runMergePdfs() },
                        enabled = state.sources.size >= 2 && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .height(52.dp)
                            .testTag("merge_pdfs_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.MergeType, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.isProcessing) "Merging PDFs..."
                            else "Merge ${state.sources.size} PDFs"
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
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { multiPdfPicker.launch(arrayOf("application/pdf")) },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("merge_browse_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Filled.AddCircleOutline, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Add from Device")
                    }
                    OutlinedButton(
                        onClick = { showPickerDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("merge_from_library_button"),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("From My Files")
                    }
                }
            }

            if (state.isProcessing) {
                item {
                    ProcessingBanner(
                        message = "Merging PDF documents...",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            state.resultRecord?.let { record ->
                item {
                    PdfResultCard(
                        record = record,
                        badgeLabel = "Merged PDF Ready",
                        onOpen = { onOpenPdf(record) },
                        onDownload = { downloadLauncher.launch(record.name) },
                        onShare = {
                            FormatUtils.shareFile(context, File(record.filePath), "application/pdf")
                        },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            if (state.sources.isEmpty()) {
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.MergeType,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Select 2 or more PDFs", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Reorder the selected documents in the exact sequence you want them combined.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(state.sources, key = { _, item -> item.id }) { index, item ->
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
                            Icon(
                                Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "#${index + 1} • ${item.displayName}",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.pageCount}p • ${FormatUtils.formatFileSize(item.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { viewModel.moveMergePdf(index, index - 1) },
                                enabled = index > 0
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                            }
                            IconButton(
                                onClick = { viewModel.moveMergePdf(index, index + 1) },
                                enabled = index < state.sources.lastIndex
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                            }
                            IconButton(onClick = { viewModel.removeMergePdfAt(index) }) {
                                Icon(
                                    Icons.Filled.DeleteOutline,
                                    contentDescription = "Remove PDF",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.outputName,
                        onValueChange = viewModel::updateMergeOutputName,
                        label = { Text("Merged Output Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        }
    }

    if (showPickerDialog) {
        LocalPdfPickerDialog(
            title = "Add PDF to Merge",
            files = localFiles,
            onPickFromDevice = { multiPdfPicker.launch(arrayOf("application/pdf")) },
            onSelectLocal = { viewModel.addLocalPdfToMerge(it) },
            onDismiss = { showPickerDialog = false }
        )
    }
}
