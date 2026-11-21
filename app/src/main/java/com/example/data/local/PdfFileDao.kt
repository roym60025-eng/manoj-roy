package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfFileDao {
    @Query("SELECT * FROM pdf_files ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PdfFileEntity>>

    @Query("SELECT * FROM pdf_files WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PdfFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PdfFileEntity): Long

    @Update
    suspend fun update(entity: PdfFileEntity)

    @Query("DELETE FROM pdf_files WHERE id = :id")
    suspend fun deleteById(id: Long)
}
