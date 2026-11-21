package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PdfFileRepository
import com.example.data.model.ScanFilter
import com.example.data.model.ScannedPageItem
import com.example.engine.PdfProcessingEngine
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PdfEngineRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var repository: PdfFileRepository
    private lateinit var engine: PdfProcessingEngine

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PdfFileRepository(context, db.pdfFileDao())
        engine = PdfProcessingEngine(context, repository)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun cachePickedImageUri_and_copyUriToTempScanImage_decodesUriReliably() = runTest {
        val sampleFile = File(context.cacheDir, "sample_photo.png")
        val bmp = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.BLUE)
        }
        FileOutputStream(sampleFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bmp.recycle()

        val uri = Uri.fromFile(sampleFile)
        val cachedPath = engine.cachePickedImageUri(uri)
        assertNotNull(cachedPath)
        assertTrue(File(cachedPath!!).exists())

        val scanResult = engine.copyUriToTempScanImage(uri)
        assertTrue("Expected scan import success but got ${scanResult.exceptionOrNull()}", scanResult.isSuccess)
        val scanPath = scanResult.getOrThrow()
        assertTrue(File(scanPath).exists())

        val preview = engine.renderScanPreview(
            ScannedPageItem(
                rawFilePath = scanPath,
                rotationDegrees = 90,
                brightness = 15f,
                cropMarginPercent = 0.05f,
                filter = ScanFilter.ENHANCED
            )
        )
        assertNotNull(preview)
        assertEquals(Bitmap.Config.ARGB_8888, preview!!.config)
    }
}
