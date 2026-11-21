package com.example.ui.screens.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PdfFileRecord
import com.example.ui.SmartPdfViewModel
import com.example.ui.components.PdfResultCard
import com.example.ui.components.ProcessingBanner
import com.example.ui.components.ToolTopBar
import com.example.util.FormatUtils
import java.io.File

@Composable
fun DocxToPdfScreen(
    viewModel: SmartPdfViewModel,
    onBack: () -> Unit,
    onOpenPdf: (PdfFileRecord) -> Unit
) {
    val state by viewModel.docxToPdfState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val docxPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.selectDocxUri(uri)
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
                title = "DOCX to PDF",
                subtitle = "Convert Word documents locally",
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
                        onClick = { viewModel.convertDocxToPdf() },
                        enabled = state.paragraphs.isNotEmpty() && !state.isProcessing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp)
                            .height(52.dp)
                            .testTag("convert_docx_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.isProcessing) "Converting DOCX..." else "Convert DOCX to PDF")
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
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = state.selectedFileName ?: "Select a Word (.docx) Document",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Preserves headings, paragraphs, bold/italic styling, and bullet lists on clean A4 pages.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = {
                                docxPicker.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "text/plain",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("pick_docx_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text(if (state.selectedFileName == null) "Browse DOCX File" else "Choose Another File")
                        }
                    }
                }
            }

            if (state.isProcessing) {
                item {
                    ProcessingBanner(
                        message = "Parsing & rendering Word document...",
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            state.resultRecord?.let { record ->
                item {
                    PdfResultCard(
                        record = record,
                        badgeLabel = "DOCX Converted",
                        onOpen = { onOpenPdf(record) },
                        onDownload = { downloadLauncher.launch(record.name) },
                        onShare = {
                            FormatUtils.shareFile(context, File(record.filePath), "application/pdf")
                        },
                        modifier = Modifier.widthIn(max = 600.dp)
                    )
                }
            }

            if (state.paragraphs.isNotEmpty()) {
                item {
                    OutlinedTextField(
                        value = state.outputName,
                        onValueChange = viewModel::updateDocxOutputName,
                        label = { Text("Output PDF Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 600.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
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
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Extracted Content Preview (${state.paragraphs.size} blocks)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            state.paragraphs.take(12).forEach { para ->
                                if (para.text.isNotBlank()) {
                                    Text(
                                        text = if (para.isBullet) "• ${para.text}" else para.text,
                                        style = when {
                                            para.isHeading1 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                            para.isHeading2 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                            else -> MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = if (para.isBold) FontWeight.Bold else FontWeight.Normal,
                                                fontStyle = if (para.isItalic) FontStyle.Italic else FontStyle.Normal
                                            )
                                        }
                                    )
                                }
                            }
                            if (state.paragraphs.size > 12) {
                                Text(
                                    text = "+ ${state.paragraphs.size - 12} more paragraphs will be rendered into the PDF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
