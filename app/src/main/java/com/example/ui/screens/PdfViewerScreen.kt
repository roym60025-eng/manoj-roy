package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.PdfFileRecord
import com.example.ui.SmartPdfViewModel
import com.example.ui.components.ToolTopBar
import com.example.util.FormatUtils
import java.io.File
import kotlin.math.roundToInt

@Composable
fun PdfViewerScreen(
    fileId: Long,
    viewModel: SmartPdfViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var record by remember { mutableStateOf<PdfFileRecord?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(fileId) {
        record = viewModel.getFileById(fileId)
    }

    LaunchedEffect(record?.filePath, currentPage) {
        val path = record?.filePath ?: return@LaunchedEffect
        isLoadingPage = true
        zoomScale = 1f
        offsetX = 0f
        offsetY = 0f
        pageBitmap = viewModel.renderPdfPage(path, currentPage, 1280)
        isLoadingPage = false
    }

    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        val rec = record
        if (uri != null && rec != null) {
            viewModel.exportPdfToUri(rec, uri)
        }
    }

    val activeRecord = record

    Scaffold(
        topBar = {
            ToolTopBar(
                title = activeRecord?.name ?: "PDF Preview",
                subtitle = activeRecord?.let {
                    "Page ${currentPage + 1} of ${it.pageCount} • ${FormatUtils.formatFileSize(it.sizeBytes)}"
                },
                onBack = onBack,
                actions = {
                    if (activeRecord != null) {
                        IconButton(
                            onClick = { downloadLauncher.launch(activeRecord.name) },
                            modifier = Modifier.testTag("viewer_download_button")
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = "Save PDF to device")
                        }
                        IconButton(
                            onClick = {
                                FormatUtils.shareFile(context, File(activeRecord.filePath), "application/pdf")
                            },
                            modifier = Modifier.testTag("viewer_share_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Share PDF")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (activeRecord != null) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (activeRecord.pageCount > 1) {
                            Slider(
                                value = currentPage.toFloat(),
                                onValueChange = { currentPage = it.roundToInt().coerceIn(0, activeRecord.pageCount - 1) },
                                valueRange = 0f..(activeRecord.pageCount - 1).toFloat(),
                                steps = (activeRecord.pageCount - 2).coerceAtLeast(0)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (currentPage > 0) currentPage-- },
                                    enabled = currentPage > 0,
                                    modifier = Modifier.testTag("viewer_prev_page")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
                                }
                                Text(
                                    text = "${currentPage + 1} / ${activeRecord.pageCount}",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                IconButton(
                                    onClick = { if (currentPage < activeRecord.pageCount - 1) currentPage++ },
                                    enabled = currentPage < activeRecord.pageCount - 1,
                                    modifier = Modifier.testTag("viewer_next_page")
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        zoomScale = (zoomScale - 0.5f).coerceAtLeast(1f)
                                        if (zoomScale == 1f) {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    },
                                    enabled = zoomScale > 1f
                                ) {
                                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom out")
                                }
                                Text("${(zoomScale * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
                                IconButton(
                                    onClick = { zoomScale = (zoomScale + 0.5f).coerceAtMost(3f) },
                                    enabled = zoomScale < 3f
                                ) {
                                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom in")
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            if (activeRecord == null) {
                Text("PDF file not found.", style = MaterialTheme.typography.bodyLarge)
            } else if (isLoadingPage) {
                CircularProgressIndicator()
            } else {
                pageBitmap?.let { bmp ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .pointerInput(currentPage) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    zoomScale = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                    if (zoomScale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "PDF page ${currentPage + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = zoomScale,
                                    scaleY = zoomScale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                )
                        )
                    }
                } ?: Text("Unable to render this PDF page.")
            }
        }
    }
}
