package com.example.data.local

import android.content.Context
import com.example.data.model.PdfFileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class PdfFileRepository(
    private val context: Context,
    private val dao: PdfFileDao
) {
    val pdfFilesDir: File
        get() = File(context.filesDir, "pdfs").apply { if (!exists()) mkdirs() }

    val exportsCacheDir: File
        get() = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }

    val allFiles: Flow<List<PdfFileRecord>> = dao.observeAll().map { entities ->
        entities.filter { File(it.filePath).exists() }.map { it.toModel() }
    }

    suspend fun getById(id: Long): PdfFileRecord? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toModel()
    }

    suspend fun saveRecord(record: PdfFileRecord): PdfFileRecord = withContext(Dispatchers.IO) {
        val insertedId = dao.insert(PdfFileEntity.fromModel(record))
        record.copy(id = insertedId)
    }

    suspend fun renameFile(record: PdfFileRecord, newRawName: String): Result<PdfFileRecord> =
        withContext(Dispatchers.IO) {
            runCatching {
                val sanitized = sanitizePdfFilename(newRawName)
                val currentFile = File(record.filePath)
                if (!currentFile.exists()) error("File no longer exists on device.")

                var targetFile = File(pdfFilesDir, sanitized)
                if (targetFile.absolutePath != currentFile.absolutePath && targetFile.exists()) {
                    val base = sanitized.removeSuffix(".pdf")
                    targetFile = File(pdfFilesDir, "${base}_${System.currentTimeMillis() % 10000}.pdf")
                }

                if (targetFile.absolutePath != currentFile.absolutePath) {
                    if (!currentFile.renameTo(targetFile)) {
                        currentFile.copyTo(targetFile, overwrite = true)
                        currentFile.delete()
                    }
                }

                val updated = record.copy(
                    name = targetFile.name,
                    filePath = targetFile.absolutePath,
                    sizeBytes = targetFile.length()
                )
                dao.update(PdfFileEntity.fromModel(updated))
                updated
            }
        }

    suspend fun deleteFile(record: PdfFileRecord) = withContext(Dispatchers.IO) {
        val file = File(record.filePath)
        if (file.exists()) {
            file.delete()
        }
        dao.deleteById(record.id)
    }

    suspend fun clearExportCache(): Long = withContext(Dispatchers.IO) {
        var clearedBytes = 0L
        exportsCacheDir.listFiles()?.forEach { child ->
            clearedBytes += child.walkBottomUp().sumOf { if (it.isFile) it.length() else 0L }
            child.deleteRecursively()
        }
        clearedBytes
    }

    fun uniqueTargetFile(desiredName: String): File {
        val sanitized = sanitizePdfFilename(desiredName)
        val candidate = File(pdfFilesDir, sanitized)
        if (!candidate.exists()) return candidate
        val base = sanitized.removeSuffix(".pdf")
        return File(pdfFilesDir, "${base}_${System.currentTimeMillis() % 100000}.pdf")
    }

    companion object {
        fun sanitizePdfFilename(raw: String): String {
            val cleaned = raw.trim()
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
                .ifBlank { "Document_${System.currentTimeMillis()}" }
            return if (cleaned.lowercase().endsWith(".pdf")) cleaned else "$cleaned.pdf"
        }
    }
}
