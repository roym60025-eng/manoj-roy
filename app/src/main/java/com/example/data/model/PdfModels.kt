package com.example.data.model

import android.net.Uri
import java.util.UUID

enum class PdfToolId(val title: String, val subtitle: String) {
    IMAGE_TO_PDF("Image to PDF", "Convert photos to PDF"),
    SMART_SCAN("Smart Scan", "Scan & enhance pages"),
    IMPORT_PDF("Import PDF", "Open & inspect PDFs"),
    COMPRESS_PDF("Compress PDF", "Shrink PDF file size"),
    PDF_TO_JPG("PDF to JPG", "Export pages as JPG"),
    MERGE_PDF("Merge PDF", "Combine multiple PDFs"),
    DOCX_TO_PDF("DOCX to PDF", "Convert Word documents"),
    MORE_TOOLS("More Tools", "Text PDF & utilities")
}

enum class PageSizeOption(val label: String, val widthPt: Int, val heightPt: Int) {
    A4("A4", 595, 842),
    LETTER("Letter", 612, 792),
    CUSTOM("Custom", 595, 842)
}

enum class OrientationOption(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class CompressionLevel(
    val label: String,
    val description: String,
    val scaleFactor: Float,
    val jpegQuality: Int
) {
    LOW("Low", "High clarity, gentle reduction", 1.5f, 82),
    MEDIUM("Medium", "Balanced quality & file size", 1.15f, 60),
    HIGH("High", "Smallest file size", 0.85f, 38)
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCED("Enhanced"),
    BLACK_AND_WHITE("B&W Doc"),
    GRAYSCALE("Grayscale")
}

enum class FileSortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    NAME_ASC("Name A–Z"),
    SIZE_DESC("Largest")
}

data class PdfFileRecord(
    val id: Long = 0,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val createdAt: Long,
    val sourceTool: String
)

data class SelectedImageItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val displayName: String,
    val localCachePath: String? = null
)

data class ScannedPageItem(
    val id: String = UUID.randomUUID().toString(),
    val rawFilePath: String,
    val rotationDegrees: Int = 0,
    val brightness: Float = 0f,
    val cropMarginPercent: Float = 0f,
    val filter: ScanFilter = ScanFilter.ENHANCED
)

data class PdfDocumentCandidate(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri?,
    val localFilePath: String? = null,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int
)

data class CompressionSummary(
    val record: PdfFileRecord,
    val originalSizeBytes: Long,
    val compressedSizeBytes: Long
) {
    val savedPercent: Int
        get() = if (originalSizeBytes > 0) {
            (((originalSizeBytes - compressedSizeBytes).toFloat() / originalSizeBytes) * 100f)
                .toInt()
                .coerceIn(-100, 99)
        } else 0
}

data class PdfToJpgResult(
    val sourceName: String,
    val imagePaths: List<String>,
    val zipFilePath: String?
)

data class DocxParagraph(
    val text: String,
    val isHeading1: Boolean = false,
    val isHeading2: Boolean = false,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isBullet: Boolean = false
)

data class AppSettings(
    val isDarkMode: Boolean = true,
    val hasSeenWelcome: Boolean = false,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4,
    val defaultImageQuality: Int = 85
)
