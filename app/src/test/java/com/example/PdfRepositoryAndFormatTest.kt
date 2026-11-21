package com.example

import com.example.data.local.PdfFileRepository
import com.example.data.model.CompressionSummary
import com.example.data.model.PdfFileRecord
import com.example.util.FormatUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfRepositoryAndFormatTest {

    @Test
    fun sanitizePdfFilename_appendsExtensionAndStripsIllegalChars() {
        assertEquals("My_Report.pdf", PdfFileRepository.sanitizePdfFilename("My/Report"))
        assertEquals("Invoice.pdf", PdfFileRepository.sanitizePdfFilename("Invoice.pdf"))
        assertEquals("Notes_1.pdf", PdfFileRepository.sanitizePdfFilename("  Notes:1  "))
    }

    @Test
    fun formatFileSize_formatsBytesKbMb() {
        assertEquals("512 B", FormatUtils.formatFileSize(512))
        assertEquals("2.0 KB", FormatUtils.formatFileSize(2048))
        assertEquals("1.5 MB", FormatUtils.formatFileSize((1.5 * 1024 * 1024).toLong()))
    }

    @Test
    fun compressionSummary_calculatesSavedPercent() {
        val dummy = PdfFileRecord(
            id = 1,
            name = "test.pdf",
            filePath = "/tmp/test.pdf",
            sizeBytes = 250_000,
            pageCount = 3,
            createdAt = 1000L,
            sourceTool = "Compress PDF"
        )
        val summary = CompressionSummary(
            record = dummy,
            originalSizeBytes = 1_000_000,
            compressedSizeBytes = 250_000
        )
        assertEquals(75, summary.savedPercent)
    }
}
