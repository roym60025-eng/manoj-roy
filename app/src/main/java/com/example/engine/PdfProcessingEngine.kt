package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.util.Xml
import com.example.data.local.PdfFileRepository
import com.example.data.model.CompressionLevel
import com.example.data.model.CompressionSummary
import com.example.data.model.DocxParagraph
import com.example.data.model.OrientationOption
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentCandidate
import com.example.data.model.PdfFileRecord
import com.example.data.model.PdfToolId
import com.example.data.model.PdfToJpgResult
import com.example.data.model.ScanFilter
import com.example.data.model.ScannedPageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PdfProcessingEngine(
    private val context: Context,
    private val repository: PdfFileRepository
) {
    fun resolveDisplayName(uri: Uri, fallback: String = "Document"): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: fallback
    }

    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        pageSize: PageSizeOption,
        customWidthPt: Int,
        customHeightPt: Int,
        orientation: OrientationOption,
        marginPt: Int,
        imageQuality: Int,
        outputName: String
    ): Result<PdfFileRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(imageUris.isNotEmpty()) { "Please select at least one image." }

            val baseW = if (pageSize == PageSizeOption.CUSTOM) customWidthPt.coerceIn(200, 2000) else pageSize.widthPt
            val baseH = if (pageSize == PageSizeOption.CUSTOM) customHeightPt.coerceIn(200, 2000) else pageSize.heightPt
            val (pageW, pageH) = if (orientation == OrientationOption.PORTRAIT) {
                min(baseW, baseH) to max(baseW, baseH)
            } else {
                max(baseW, baseH) to min(baseW, baseH)
            }

            val targetFile = repository.uniqueTargetFile(outputName)
            val pdfDocument = PdfDocument()
            val quality = imageQuality.coerceIn(30, 100)
            var renderedCount = 0

            try {
                imageUris.forEachIndexed { index, uri ->
                    val decoded = decodeSampledBitmap(uri, maxDimension = 1800)
                        ?: error("Unsupported or unreadable image at item ${index + 1}.")
                    val compressedBitmap = recompressBitmap(decoded, quality)
                    if (compressedBitmap !== decoded) decoded.recycle()

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val safeMargin = marginPt.coerceIn(0, min(pageW, pageH) / 4).toFloat()
                    val availW = (pageW - safeMargin * 2f).coerceAtLeast(10f)
                    val availH = (pageH - safeMargin * 2f).coerceAtLeast(10f)
                    val scale = min(availW / compressedBitmap.width, availH / compressedBitmap.height)
                    val drawW = compressedBitmap.width * scale
                    val drawH = compressedBitmap.height * scale
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(
                        compressedBitmap,
                        null,
                        RectF(left, top, left + drawW, top + drawH),
                        paint
                    )
                    pdfDocument.finishPage(page)
                    compressedBitmap.recycle()
                    renderedCount++
                }

                FileOutputStream(targetFile).use { out ->
                    pdfDocument.writeTo(out)
                }
            } finally {
                pdfDocument.close()
            }

            val record = PdfFileRecord(
                name = targetFile.name,
                filePath = targetFile.absolutePath,
                sizeBytes = targetFile.length(),
                pageCount = renderedCount,
                createdAt = System.currentTimeMillis(),
                sourceTool = PdfToolId.IMAGE_TO_PDF.title
            )
            repository.saveRecord(record)
        }
    }

    suspend fun copyUriToTempScanImage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = decodeSampledBitmap(uri, maxDimension = 1800)
                ?: error("Could not read image. Please choose a JPG, PNG, or WEBP file.")
            val scansDir = File(context.cacheDir, "scans").apply { if (!exists()) mkdirs() }
            val outFile = File(scansDir, "scan_${System.currentTimeMillis()}.jpg")
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }
            bitmap.recycle()
            outFile.absolutePath
        }
    }

    fun renderScanPreview(page: ScannedPageItem, maxDimension: Int = 900): Bitmap? {
        val raw = decodeSampledFileBitmap(File(page.rawFilePath), maxDimension) ?: return null
        val processed = applyScanAdjustments(
            source = raw,
            rotationDegrees = page.rotationDegrees,
            brightness = page.brightness,
            cropMarginPercent = page.cropMarginPercent,
            filter = page.filter
        )
        if (processed !== raw) raw.recycle()
        return processed
    }

    suspend fun scannedPagesToPdf(
        pages: List<ScannedPageItem>,
        outputName: String
    ): Result<PdfFileRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(pages.isNotEmpty()) { "Capture or import at least one page to save." }
            val targetFile = repository.uniqueTargetFile(outputName)
            val pdfDocument = PdfDocument()

            try {
                pages.forEachIndexed { index, item ->
                    val raw = decodeSampledFileBitmap(File(item.rawFilePath), maxDimension = 1800)
                        ?: error("Scan page ${index + 1} could not be read.")
                    val edited = applyScanAdjustments(
                        source = raw,
                        rotationDegrees = item.rotationDegrees,
                        brightness = item.brightness,
                        cropMarginPercent = item.cropMarginPercent,
                        filter = item.filter
                    )
                    if (edited !== raw) raw.recycle()

                    val isLandscape = edited.width > edited.height
                    val pageW = if (isLandscape) 842 else 595
                    val pageH = if (isLandscape) 595 else 842
                    val page = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    )
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val margin = 20f
                    val availW = pageW - margin * 2
                    val availH = pageH - margin * 2
                    val scale = min(availW / edited.width, availH / edited.height)
                    val drawW = edited.width * scale
                    val drawH = edited.height * scale
                    val left = (pageW - drawW) / 2f
                    val top = (pageH - drawH) / 2f

                    canvas.drawBitmap(
                        edited,
                        null,
                        RectF(left, top, left + drawW, top + drawH),
                        Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    )
                    pdfDocument.finishPage(page)
                    edited.recycle()
                }

                FileOutputStream(targetFile).use { out ->
                    pdfDocument.writeTo(out)
                }
            } finally {
                pdfDocument.close()
            }

            repository.saveRecord(
                PdfFileRecord(
                    name = targetFile.name,
                    filePath = targetFile.absolutePath,
                    sizeBytes = targetFile.length(),
                    pageCount = pages.size,
                    createdAt = System.currentTimeMillis(),
                    sourceTool = PdfToolId.SMART_SCAN.title
                )
            )
        }
    }

    suspend fun inspectPdfUri(uri: Uri): Result<PdfDocumentCandidate> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = resolveDisplayName(uri, "Imported.pdf")
            val tempFile = File.createTempFile("inspect_", ".pdf", context.cacheDir)
            try {
                copyUriToFile(uri, tempFile)
                val pages = countPdfPagesOrThrow(tempFile)
                PdfDocumentCandidate(
                    uri = uri,
                    localFilePath = null,
                    displayName = displayName,
                    sizeBytes = tempFile.length(),
                    pageCount = pages
                )
            } finally {
                tempFile.delete()
            }
        }
    }

    suspend fun importPdfFromUri(uri: Uri): Result<PdfFileRecord> = withContext(Dispatchers.IO) {
        runCatching {
            val rawName = resolveDisplayName(uri, "Imported_${System.currentTimeMillis()}.pdf")
            val targetFile = repository.uniqueTargetFile(rawName)
            copyUriToFile(uri, targetFile)
            val pageCount = try {
                countPdfPagesOrThrow(targetFile)
            } catch (t: Throwable) {
                targetFile.delete()
                throw t
            }

            repository.saveRecord(
                PdfFileRecord(
                    name = targetFile.name,
                    filePath = targetFile.absolutePath,
                    sizeBytes = targetFile.length(),
                    pageCount = pageCount,
                    createdAt = System.currentTimeMillis(),
                    sourceTool = PdfToolId.IMPORT_PDF.title
                )
            )
        }
    }

    suspend fun compressPdf(
        source: PdfDocumentCandidate,
        level: CompressionLevel,
        outputName: String
    ): Result<CompressionSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val tempInput = resolveToReadablePdfFile(source)
            val shouldDeleteTemp = source.localFilePath == null
            try {
                val originalSize = tempInput.length()
                val targetFile = repository.uniqueTargetFile(outputName)
                val pfd = ParcelFileDescriptor.open(tempInput, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pdfDocument = PdfDocument()
                val pageCount = renderer.pageCount
                require(pageCount > 0) { "Selected PDF has no pages." }

                try {
                    for (i in 0 until pageCount) {
                        val page = renderer.openPage(i)
                        val outW = (page.width * level.scaleFactor).roundToInt().coerceIn(220, 1800)
                        val outH = (page.height * level.scaleFactor).roundToInt().coerceIn(220, 2400)
                        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pageW = page.width
                        val pageH = page.height
                        page.close()

                        val compressed = recompressBitmap(bitmap, level.jpegQuality)
                        if (compressed !== bitmap) bitmap.recycle()

                        val docPage = pdfDocument.startPage(
                            PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create()
                        )
                        docPage.canvas.drawColor(Color.WHITE)
                        docPage.canvas.drawBitmap(
                            compressed,
                            null,
                            RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                        pdfDocument.finishPage(docPage)
                        compressed.recycle()
                    }
                    FileOutputStream(targetFile).use { out -> pdfDocument.writeTo(out) }
                } finally {
                    pdfDocument.close()
                    renderer.close()
                    pfd.close()
                }

                val savedRecord = repository.saveRecord(
                    PdfFileRecord(
                        name = targetFile.name,
                        filePath = targetFile.absolutePath,
                        sizeBytes = targetFile.length(),
                        pageCount = pageCount,
                        createdAt = System.currentTimeMillis(),
                        sourceTool = PdfToolId.COMPRESS_PDF.title
                    )
                )
                CompressionSummary(
                    record = savedRecord,
                    originalSizeBytes = originalSize,
                    compressedSizeBytes = targetFile.length()
                )
            } finally {
                if (shouldDeleteTemp) tempInput.delete()
            }
        }
    }

    suspend fun pdfToJpg(
        source: PdfDocumentCandidate,
        quality: Int
    ): Result<PdfToJpgResult> = withContext(Dispatchers.IO) {
        runCatching {
            val tempInput = resolveToReadablePdfFile(source)
            val shouldDeleteTemp = source.localFilePath == null
            val exportFolder = File(
                repository.exportsCacheDir,
                "jpg_${System.currentTimeMillis()}"
            ).apply { mkdirs() }
            val imagePaths = mutableListOf<String>()
            val safeQuality = quality.coerceIn(40, 100)

            try {
                val pfd = ParcelFileDescriptor.open(tempInput, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                val pageCount = renderer.pageCount
                require(pageCount > 0) { "Selected PDF has no pages." }

                try {
                    val baseName = source.displayName.removeSuffix(".pdf")
                        .replace(Regex("[^a-zA-Z0-9_-]"), "_")
                        .ifBlank { "page" }

                    for (i in 0 until pageCount) {
                        val page = renderer.openPage(i)
                        val scale = (1400f / max(page.width, page.height).coerceAtLeast(1)).coerceIn(1.5f, 3.0f)
                        val outW = (page.width * scale).roundToInt().coerceAtLeast(200)
                        val outH = (page.height * scale).roundToInt().coerceAtLeast(200)

                        val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        page.close()

                        val jpgFile = File(exportFolder, "${baseName}_page_${String.format("%02d", i + 1)}.jpg")
                        FileOutputStream(jpgFile).use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, safeQuality, out)
                        }
                        bitmap.recycle()
                        imagePaths.add(jpgFile.absolutePath)
                    }
                } finally {
                    renderer.close()
                    pfd.close()
                }

                val zipFile = File(exportFolder, "All_Pages_JPG.zip")
                ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
                    imagePaths.forEach { path ->
                        val file = File(path)
                        zos.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { input -> input.copyTo(zos) }
                        zos.closeEntry()
                    }
                }

                PdfToJpgResult(
                    sourceName = source.displayName,
                    imagePaths = imagePaths,
                    zipFilePath = zipFile.absolutePath
                )
            } finally {
                if (shouldDeleteTemp) tempInput.delete()
            }
        }
    }

    suspend fun mergePdfs(
        sources: List<PdfDocumentCandidate>,
        outputName: String
    ): Result<PdfFileRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(sources.size >= 2) { "Select at least 2 PDF files to merge." }
            val targetFile = repository.uniqueTargetFile(outputName)
            val pdfDocument = PdfDocument()
            var totalPages = 0

            try {
                sources.forEach { source ->
                    val tempInput = resolveToReadablePdfFile(source)
                    val shouldDelete = source.localFilePath == null
                    try {
                        val pfd = ParcelFileDescriptor.open(tempInput, ParcelFileDescriptor.MODE_READ_ONLY)
                        val renderer = PdfRenderer(pfd)
                        try {
                            for (i in 0 until renderer.pageCount) {
                                totalPages++
                                val srcPage = renderer.openPage(i)
                                val pageW = srcPage.width
                                val pageH = srcPage.height
                                val renderScale = 2.0f
                                val bmpW = (pageW * renderScale).roundToInt().coerceIn(200, 2200)
                                val bmpH = (pageH * renderScale).roundToInt().coerceIn(200, 2800)

                                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                srcPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                srcPage.close()

                                val outPage = pdfDocument.startPage(
                                    PdfDocument.PageInfo.Builder(pageW, pageH, totalPages).create()
                                )
                                outPage.canvas.drawColor(Color.WHITE)
                                outPage.canvas.drawBitmap(
                                    bitmap,
                                    null,
                                    RectF(0f, 0f, pageW.toFloat(), pageH.toFloat()),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                )
                                pdfDocument.finishPage(outPage)
                                bitmap.recycle()
                            }
                        } finally {
                            renderer.close()
                            pfd.close()
                        }
                    } finally {
                        if (shouldDelete) tempInput.delete()
                    }
                }

                FileOutputStream(targetFile).use { out -> pdfDocument.writeTo(out) }
            } finally {
                pdfDocument.close()
            }

            repository.saveRecord(
                PdfFileRecord(
                    name = targetFile.name,
                    filePath = targetFile.absolutePath,
                    sizeBytes = targetFile.length(),
                    pageCount = totalPages,
                    createdAt = System.currentTimeMillis(),
                    sourceTool = PdfToolId.MERGE_PDF.title
                )
            )
        }
    }

    suspend fun parseDocxPreview(uri: Uri): Result<List<DocxParagraph>> = withContext(Dispatchers.IO) {
        runCatching {
            val name = resolveDisplayName(uri, "document.docx")
            if (name.lowercase().endsWith(".doc")) {
                error("Legacy binary .doc files are not supported. Please select a .docx Word file.")
            }
            val paragraphs = context.contentResolver.openInputStream(uri)?.use { stream ->
                if (name.lowercase().endsWith(".txt")) {
                    stream.bufferedReader().readLines().map { DocxParagraph(text = it) }
                } else {
                    extractDocxParagraphs(stream)
                }
            } ?: error("Unable to open document.")

            if (paragraphs.isEmpty()) {
                error("No readable text paragraphs found in this DOCX file.")
            }
            paragraphs
        }
    }

    suspend fun docxToPdf(
        paragraphs: List<DocxParagraph>,
        outputName: String,
        sourceToolTitle: String = PdfToolId.DOCX_TO_PDF.title
    ): Result<PdfFileRecord> = withContext(Dispatchers.IO) {
        runCatching {
            require(paragraphs.isNotEmpty()) { "Document has no content to convert." }
            val targetFile = repository.uniqueTargetFile(outputName)
            val pageW = 595
            val pageH = 842
            val marginX = 48
            val marginTop = 52
            val marginBottom = 52
            val contentWidth = pageW - marginX * 2
            val maxY = pageH - marginBottom

            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            page.canvas.drawColor(Color.WHITE)
            var currentY = marginTop.toFloat()

            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.GRAY
                textSize = 9.5f
                textAlign = Paint.Align.CENTER
            }

            fun startNewPage() {
                page.canvas.drawText("Page $pageNumber", pageW / 2f, pageH - 22f, footerPaint)
                pdfDocument.finishPage(page)
                pageNumber++
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
                page.canvas.drawColor(Color.WHITE)
                currentY = marginTop.toFloat()
            }

            try {
                paragraphs.forEach { para ->
                    if (para.text.isBlank()) {
                        currentY += 10f
                        if (currentY > maxY) startNewPage()
                        return@forEach
                    }

                    val fontSize = when {
                        para.isHeading1 -> 19f
                        para.isHeading2 -> 15f
                        else -> 11.5f
                    }
                    val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (para.isHeading1 || para.isHeading2) Color.rgb(18, 36, 86) else Color.rgb(30, 41, 59)
                        textSize = fontSize
                        typeface = Typeface.create(
                            Typeface.SANS_SERIF,
                            when {
                                para.isHeading1 || para.isHeading2 || (para.isBold && para.isItalic) -> {
                                    if (para.isBold && para.isItalic) Typeface.BOLD_ITALIC else Typeface.BOLD
                                }
                                para.isBold -> Typeface.BOLD
                                para.isItalic -> Typeface.ITALIC
                                else -> Typeface.NORMAL
                            }
                        )
                    }

                    val displayText = if (para.isBullet) "•  ${para.text}" else para.text
                    val spannable = SpannableStringBuilder(displayText)
                    if (para.isBold) {
                        spannable.setSpan(StyleSpan(Typeface.BOLD), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    val indent = if (para.isBullet) 12 else 0
                    val layout = StaticLayout.Builder
                        .obtain(spannable, 0, spannable.length, textPaint, contentWidth - indent)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(3f, 1.18f)
                        .build()

                    val spacingAfter = when {
                        para.isHeading1 -> 12f
                        para.isHeading2 -> 9f
                        else -> 6.5f
                    }

                    if (currentY + layout.height > maxY && currentY > marginTop + 10f) {
                        startNewPage()
                    }

                    page.canvas.save()
                    page.canvas.translate((marginX + indent).toFloat(), currentY)
                    layout.draw(page.canvas)
                    page.canvas.restore()

                    currentY += layout.height + spacingAfter
                }

                page.canvas.drawText("Page $pageNumber", pageW / 2f, pageH - 22f, footerPaint)
                pdfDocument.finishPage(page)

                FileOutputStream(targetFile).use { out -> pdfDocument.writeTo(out) }
            } finally {
                pdfDocument.close()
            }

            repository.saveRecord(
                PdfFileRecord(
                    name = targetFile.name,
                    filePath = targetFile.absolutePath,
                    sizeBytes = targetFile.length(),
                    pageCount = pageNumber,
                    createdAt = System.currentTimeMillis(),
                    sourceTool = sourceToolTitle
                )
            )
        }
    }

    suspend fun renderPdfPageBitmap(
        filePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            try {
                if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
                val page = renderer.openPage(pageIndex)
                val aspect = page.height.toFloat() / page.width.coerceAtLeast(1)
                val outW = targetWidthPx.coerceIn(240, 1800)
                val outH = (outW * aspect).roundToInt().coerceIn(240, 2600)
                val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmap
            } finally {
                renderer.close()
                pfd.close()
            }
        }.getOrNull()
    }

    suspend fun exportFileToUri(sourceFile: File, targetUri: Uri): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(sourceFile.exists()) { "Source file was not found." }
                context.contentResolver.openOutputStream(targetUri)?.use { out ->
                    sourceFile.inputStream().use { input -> input.copyTo(out) }
                } ?: error("Could not open destination for saving.")
                Unit
            }
        }

    private fun resolveToReadablePdfFile(source: PdfDocumentCandidate): File {
        if (source.localFilePath != null) {
            val f = File(source.localFilePath)
            if (f.exists()) return f
        }
        val uri = source.uri ?: error("PDF source is unavailable.")
        val temp = File.createTempFile("src_pdf_", ".pdf", context.cacheDir)
        copyUriToFile(uri, temp)
        return temp
    }

    private fun copyUriToFile(uri: Uri, target: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("Unable to read selected file.")
    }

    private fun countPdfPagesOrThrow(file: File): Int {
        if (file.length() < 8) error("Selected file is empty or invalid.")
        val header = ByteArray(5)
        file.inputStream().use { it.read(header) }
        if (String(header) != "%PDF-") {
            error("Invalid PDF file format.")
        }
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            if (count <= 0) error("PDF contains 0 pages.")
            count
        } catch (se: SecurityException) {
            error("Password-protected PDFs cannot be opened.")
        } catch (t: Throwable) {
            error("Could not open PDF: ${t.localizedMessage ?: "corrupt file"}")
        }
    }

    private fun extractDocxParagraphs(stream: InputStream): List<DocxParagraph> {
        val result = mutableListOf<DocxParagraph>()
        ZipInputStream(stream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val parser = Xml.newPullParser()
                    parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    parser.setInput(zis, "UTF-8")

                    var inParagraph = false
                    var inText = false
                    var currentText = StringBuilder()
                    var isHeading1 = false
                    var isHeading2 = false
                    var hasBold = false
                    var hasItalic = false
                    var isBullet = false

                    var event = parser.eventType
                    while (event != XmlPullParser.END_DOCUMENT) {
                        val tag = parser.name?.substringAfter(':')
                        when (event) {
                            XmlPullParser.START_TAG -> {
                                when (tag) {
                                    "p" -> {
                                        inParagraph = true
                                        currentText = StringBuilder()
                                        isHeading1 = false
                                        isHeading2 = false
                                        hasBold = false
                                        hasItalic = false
                                        isBullet = false
                                    }
                                    "pStyle" -> if (inParagraph) {
                                        val styleVal = parser.getAttributeValue(null, "w:val")
                                            ?: parser.getAttributeValue(null, "val")
                                            ?: ""
                                        if (styleVal.contains("Heading1", true) || styleVal.equals("Title", true)) {
                                            isHeading1 = true
                                        } else if (styleVal.contains("Heading2", true) || styleVal.contains("Subtitle", true)) {
                                            isHeading2 = true
                                        }
                                    }
                                    "numPr" -> if (inParagraph) isBullet = true
                                    "b" -> if (inParagraph) {
                                        val v = parser.getAttributeValue(null, "w:val")
                                            ?: parser.getAttributeValue(null, "val")
                                        if (v != "0" && v != "false") hasBold = true
                                    }
                                    "i" -> if (inParagraph) {
                                        val v = parser.getAttributeValue(null, "w:val")
                                            ?: parser.getAttributeValue(null, "val")
                                        if (v != "0" && v != "false") hasItalic = true
                                    }
                                    "t" -> if (inParagraph) inText = true
                                    "tab" -> if (inParagraph) currentText.append("    ")
                                    "br" -> if (inParagraph) currentText.append("\n")
                                }
                            }
                            XmlPullParser.TEXT -> {
                                if (inParagraph && inText) {
                                    currentText.append(parser.text)
                                }
                            }
                            XmlPullParser.END_TAG -> {
                                when (tag) {
                                    "t" -> inText = false
                                    "p" -> {
                                        if (inParagraph) {
                                            result.add(
                                                DocxParagraph(
                                                    text = currentText.toString().trim(),
                                                    isHeading1 = isHeading1,
                                                    isHeading2 = isHeading2,
                                                    isBold = hasBold,
                                                    isItalic = hasItalic,
                                                    isBullet = isBullet
                                                )
                                            )
                                            inParagraph = false
                                        }
                                    }
                                }
                            }
                        }
                        event = parser.next()
                    }
                    break
                }
                entry = zis.nextEntry
            }
        }
        return result
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        } ?: return null

        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDimension || boundsOpts.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
            runCatching {
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        } ?: ExifInterface.ORIENTATION_NORMAL

        return applyExifOrientation(decoded, orientation)
    }

    private fun decodeSampledFileBitmap(file: File, maxDimension: Int): Bitmap? {
        if (!file.exists()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDimension || boundsOpts.outHeight / sampleSize > maxDimension) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    private fun recompressBitmap(source: Bitmap, quality: Int): Bitmap {
        val baos = ByteArrayOutputStream()
        source.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val bytes = baos.toByteArray()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: source
    }

    private fun applyScanAdjustments(
        source: Bitmap,
        rotationDegrees: Int,
        brightness: Float,
        cropMarginPercent: Float,
        filter: ScanFilter
    ): Bitmap {
        // 1. Crop edges
        val safeCrop = cropMarginPercent.coerceIn(0f, 0.25f)
        val cropX = (source.width * safeCrop).roundToInt()
        val cropY = (source.height * safeCrop).roundToInt()
        val cropW = (source.width - cropX * 2).coerceAtLeast(20)
        val cropH = (source.height - cropY * 2).coerceAtLeast(20)
        val cropped = if (safeCrop > 0.001f) {
            Bitmap.createBitmap(source, cropX, cropY, cropW, cropH)
        } else {
            source
        }

        // 2. Rotate
        val normalizedRot = ((rotationDegrees % 360) + 360) % 360
        val rotated = if (normalizedRot != 0) {
            val matrix = Matrix().apply { postRotate(normalizedRot.toFloat()) }
            Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true).also {
                if (cropped !== source) cropped.recycle()
            }
        } else {
            cropped
        }

        // 3. Brightness + Document Enhancement ColorMatrix
        val output = Bitmap.createBitmap(rotated.width, rotated.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val cm = ColorMatrix()

        when (filter) {
            ScanFilter.ORIGINAL -> Unit
            ScanFilter.ENHANCED -> {
                // Boost contrast slightly and crisp highlights for document legibility
                val contrast = 1.28f
                val translate = (-0.5f * contrast + 0.5f) * 255f + 12f
                cm.set(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            }
            ScanFilter.GRAYSCALE -> {
                cm.setSaturation(0f)
            }
            ScanFilter.BLACK_AND_WHITE -> {
                val gray = ColorMatrix().apply { setSaturation(0f) }
                val highContrast = ColorMatrix(
                    floatArrayOf(
                        2.1f, 0f, 0f, 0f, -125f,
                        0f, 2.1f, 0f, 0f, -125f,
                        0f, 0f, 2.1f, 0f, -125f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(gray)
                cm.postConcat(highContrast)
            }
        }

        if (brightness != 0f) {
            val b = brightness.coerceIn(-100f, 100f)
            val brightMatrix = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, b,
                    0f, 1f, 0f, 0f, b,
                    0f, 0f, 1f, 0f, b,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            cm.postConcat(brightMatrix)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(rotated, Rect(0, 0, rotated.width, rotated.height), Rect(0, 0, output.width, output.height), paint)
        if (rotated !== source) rotated.recycle()
        return output
    }
}
