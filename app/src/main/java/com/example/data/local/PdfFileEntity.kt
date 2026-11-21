package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.data.model.PdfFileRecord

@Entity(tableName = "pdf_files")
data class PdfFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val filePath: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val createdAt: Long,
    val sourceTool: String
) {
    fun toModel(): PdfFileRecord = PdfFileRecord(
        id = id,
        name = name,
        filePath = filePath,
        sizeBytes = sizeBytes,
        pageCount = pageCount,
        createdAt = createdAt,
        sourceTool = sourceTool
    )

    companion object {
        fun fromModel(record: PdfFileRecord): PdfFileEntity = PdfFileEntity(
            id = record.id,
            name = record.name,
            filePath = record.filePath,
            sizeBytes = record.sizeBytes,
            pageCount = record.pageCount,
            createdAt = record.createdAt,
            sourceTool = record.sourceTool
        )
    }
}
