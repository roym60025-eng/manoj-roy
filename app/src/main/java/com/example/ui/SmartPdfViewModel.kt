package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.PdfFileRepository
import com.example.data.local.SettingsRepository
import com.example.data.model.AppSettings
import com.example.data.model.CompressionLevel
import com.example.data.model.CompressionSummary
import com.example.data.model.DocxParagraph
import com.example.data.model.FileSortOption
import com.example.data.model.OrientationOption
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentCandidate
import com.example.data.model.PdfFileRecord
import com.example.data.model.PdfToolId
import com.example.data.model.PdfToJpgResult
import com.example.data.model.ScanFilter
import com.example.data.model.ScannedPageItem
import com.example.data.model.SelectedImageItem
import com.example.engine.PdfProcessingEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface UiToastEvent {
    data class Success(val message: String) : UiToastEvent
    data class Error(val message: String) : UiToastEvent
}

data class ImageToPdfUiState(
    val selectedImages: List<SelectedImageItem> = emptyList(),
    val pageSize: PageSizeOption = PageSizeOption.A4,
    val customWidthPt: Int = 595,
    val customHeightPt: Int = 842,
    val orientation: OrientationOption = OrientationOption.PORTRAIT,
    val marginPt: Int = 16,
    val imageQuality: Int = 85,
    val outputName: String = "Images_Document",
    val isProcessing: Boolean = false,
    val resultRecord: PdfFileRecord? = null
)

data class SmartScanUiState(
    val pages: List<ScannedPageItem> = emptyList(),
    val selectedPageIndex: Int = 0,
    val previewBitmap: Bitmap? = null,
    val outputName: String = "Scanned_Document",
    val isProcessing: Boolean = false,
    val resultRecord: PdfFileRecord? = null
)

data class ImportPdfUiState(
    val inspectedCandidate: PdfDocumentCandidate? = null,
    val inspectedUri: Uri? = null,
    val isProcessing: Boolean = false,
    val importedRecord: PdfFileRecord? = null
)

data class CompressPdfUiState(
    val selectedSource: PdfDocumentCandidate? = null,
    val level: CompressionLevel = CompressionLevel.MEDIUM,
    val outputName: String = "Compressed_Document",
    val isProcessing: Boolean = false,
    val summary: CompressionSummary? = null
)

data class PdfToJpgUiState(
    val selectedSource: PdfDocumentCandidate? = null,
    val quality: Int = 85,
    val isProcessing: Boolean = false,
    val result: PdfToJpgResult? = null
)

data class MergePdfUiState(
    val sources: List<PdfDocumentCandidate> = emptyList(),
    val outputName: String = "Merged_Document",
    val isProcessing: Boolean = false,
    val resultRecord: PdfFileRecord? = null
)

data class DocxToPdfUiState(
    val selectedFileName: String? = null,
    val paragraphs: List<DocxParagraph> = emptyList(),
    val outputName: String = "Word_Document",
    val isProcessing: Boolean = false,
    val resultRecord: PdfFileRecord? = null
)

class SmartPdfViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val fileRepository = PdfFileRepository(application, database.pdfFileDao())
    private val settingsRepository = SettingsRepository(application)
    val engine = PdfProcessingEngine(application, fileRepository)

    private val _toastEvents = MutableSharedFlow<UiToastEvent>()
    val toastEvents = _toastEvents.asSharedFlow()

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppSettings()
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _sortOption = MutableStateFlow(FileSortOption.NEWEST)
    val sortOption: StateFlow<FileSortOption> = _sortOption.asStateFlow()

    val allFiles: StateFlow<List<PdfFileRecord>> = fileRepository.allFiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val filteredFiles: StateFlow<List<PdfFileRecord>> = combine(
        fileRepository.allFiles,
        _searchQuery,
        _sortOption
    ) { files, query, sort ->
        val trimmed = query.trim()
        val searched = if (trimmed.isEmpty()) {
            files
        } else {
            files.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                    it.sourceTool.contains(trimmed, ignoreCase = true)
            }
        }
        when (sort) {
            FileSortOption.NEWEST -> searched.sortedByDescending { it.createdAt }
            FileSortOption.OLDEST -> searched.sortedBy { it.createdAt }
            FileSortOption.NAME_ASC -> searched.sortedBy { it.name.lowercase() }
            FileSortOption.SIZE_DESC -> searched.sortedByDescending { it.sizeBytes }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _imageToPdfState = MutableStateFlow(ImageToPdfUiState())
    val imageToPdfState: StateFlow<ImageToPdfUiState> = _imageToPdfState.asStateFlow()

    private val _smartScanState = MutableStateFlow(SmartScanUiState())
    val smartScanState: StateFlow<SmartScanUiState> = _smartScanState.asStateFlow()

    private val _importPdfState = MutableStateFlow(ImportPdfUiState())
    val importPdfState: StateFlow<ImportPdfUiState> = _importPdfState.asStateFlow()

    private val _compressPdfState = MutableStateFlow(CompressPdfUiState())
    val compressPdfState: StateFlow<CompressPdfUiState> = _compressPdfState.asStateFlow()

    private val _pdfToJpgState = MutableStateFlow(PdfToJpgUiState())
    val pdfToJpgState: StateFlow<PdfToJpgUiState> = _pdfToJpgState.asStateFlow()

    private val _mergePdfState = MutableStateFlow(MergePdfUiState())
    val mergePdfState: StateFlow<MergePdfUiState> = _mergePdfState.asStateFlow()

    private val _docxToPdfState = MutableStateFlow(DocxToPdfUiState())
    val docxToPdfState: StateFlow<DocxToPdfUiState> = _docxToPdfState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { appSettings ->
                _imageToPdfState.update { state ->
                    if (state.selectedImages.isEmpty() && state.resultRecord == null) {
                        state.copy(
                            pageSize = appSettings.defaultPageSize,
                            imageQuality = appSettings.defaultImageQuality
                        )
                    } else state
                }
            }
        }
    }

    // Settings & Onboarding
    fun markWelcomeSeen() {
        viewModelScope.launch { settingsRepository.setHasSeenWelcome(true) }
    }

    fun reopenWelcome() {
        viewModelScope.launch { settingsRepository.setHasSeenWelcome(false) }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDarkMode(enabled) }
    }

    fun setDefaultPageSize(option: PageSizeOption) {
        viewModelScope.launch { settingsRepository.setDefaultPageSize(option) }
    }

    fun setDefaultImageQuality(quality: Int) {
        viewModelScope.launch { settingsRepository.setDefaultImageQuality(quality) }
    }

    fun clearExportCache() {
        viewModelScope.launch {
            val bytes = fileRepository.clearExportCache()
            val label = if (bytes > 0) "Cleared temporary export cache." else "Export cache is already clean."
            _toastEvents.emit(UiToastEvent.Success(label))
        }
    }

    // Files Dashboard
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateSortOption(option: FileSortOption) {
        _sortOption.value = option
    }

    fun renameFile(record: PdfFileRecord, newName: String) {
        viewModelScope.launch {
            fileRepository.renameFile(record, newName)
                .onSuccess { updated ->
                    _toastEvents.emit(UiToastEvent.Success("Renamed to ${updated.name}"))
                }
                .onFailure { err ->
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Rename failed"))
                }
        }
    }

    fun deleteFile(record: PdfFileRecord) {
        viewModelScope.launch {
            fileRepository.deleteFile(record)
            _toastEvents.emit(UiToastEvent.Success("Deleted ${record.name}"))
        }
    }

    fun exportPdfToUri(record: PdfFileRecord, targetUri: Uri) {
        viewModelScope.launch {
            engine.exportFileToUri(File(record.filePath), targetUri)
                .onSuccess {
                    _toastEvents.emit(UiToastEvent.Success("Saved ${record.name} to device"))
                }
                .onFailure { err ->
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Export failed"))
                }
        }
    }

    fun exportFileToUri(sourceFile: File, targetUri: Uri, successMessage: String) {
        viewModelScope.launch {
            engine.exportFileToUri(sourceFile, targetUri)
                .onSuccess { _toastEvents.emit(UiToastEvent.Success(successMessage)) }
                .onFailure { err ->
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Export failed"))
                }
        }
    }

    suspend fun getFileById(id: Long): PdfFileRecord? = fileRepository.getById(id)

    suspend fun renderPdfPage(filePath: String, pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
        return engine.renderPdfPageBitmap(filePath, pageIndex, targetWidth)
    }

    // Image to PDF
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val newItems = uris.map { uri ->
                val name = engine.resolveDisplayName(uri, "Image")
                val cachedPath = engine.cachePickedImageUri(uri)
                SelectedImageItem(
                    uri = uri,
                    displayName = name,
                    localCachePath = cachedPath
                )
            }
            _imageToPdfState.update {
                it.copy(selectedImages = it.selectedImages + newItems, resultRecord = null)
            }
        }
    }

    fun removeImageAt(index: Int) {
        _imageToPdfState.update { state ->
            val updated = state.selectedImages.toMutableList()
            if (index in updated.indices) updated.removeAt(index)
            state.copy(selectedImages = updated, resultRecord = null)
        }
    }

    fun moveImage(fromIndex: Int, toIndex: Int) {
        _imageToPdfState.update { state ->
            val list = state.selectedImages.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            state.copy(selectedImages = list, resultRecord = null)
        }
    }

    fun updateImageToPdfConfig(
        pageSize: PageSizeOption? = null,
        customWidthPt: Int? = null,
        customHeightPt: Int? = null,
        orientation: OrientationOption? = null,
        marginPt: Int? = null,
        quality: Int? = null,
        outputName: String? = null
    ) {
        _imageToPdfState.update { state ->
            state.copy(
                pageSize = pageSize ?: state.pageSize,
                customWidthPt = customWidthPt ?: state.customWidthPt,
                customHeightPt = customHeightPt ?: state.customHeightPt,
                orientation = orientation ?: state.orientation,
                marginPt = marginPt ?: state.marginPt,
                imageQuality = quality ?: state.imageQuality,
                outputName = outputName ?: state.outputName
            )
        }
    }

    fun convertImagesToPdf() {
        val snapshot = _imageToPdfState.value
        if (snapshot.selectedImages.isEmpty()) {
            viewModelScope.launch { _toastEvents.emit(UiToastEvent.Error("Please select at least one image.")) }
            return
        }
        viewModelScope.launch {
            _imageToPdfState.update { it.copy(isProcessing = true, resultRecord = null) }
            engine.imagesToPdf(
                images = snapshot.selectedImages,
                pageSize = snapshot.pageSize,
                customWidthPt = snapshot.customWidthPt,
                customHeightPt = snapshot.customHeightPt,
                orientation = snapshot.orientation,
                marginPt = snapshot.marginPt,
                imageQuality = snapshot.imageQuality,
                outputName = snapshot.outputName
            ).onSuccess { record ->
                _imageToPdfState.update { it.copy(isProcessing = false, resultRecord = record) }
                _toastEvents.emit(UiToastEvent.Success("Created ${record.name} (${record.pageCount} pages)"))
            }.onFailure { err ->
                _imageToPdfState.update { it.copy(isProcessing = false) }
                _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Conversion failed"))
            }
        }
    }

    // Smart Scan
    fun addCapturedScanFile(filePath: String) {
        _smartScanState.update { state ->
            val updated = state.pages + ScannedPageItem(rawFilePath = filePath)
            state.copy(pages = updated, selectedPageIndex = updated.lastIndex, resultRecord = null)
        }
        refreshScanPreview()
    }

    fun importPhotoForScan(uri: Uri) {
        viewModelScope.launch {
            _smartScanState.update { it.copy(isProcessing = true) }
            engine.copyUriToTempScanImage(uri)
                .onSuccess { path ->
                    _smartScanState.update { state ->
                        val updated = state.pages + ScannedPageItem(rawFilePath = path)
                        state.copy(
                            pages = updated,
                            selectedPageIndex = updated.lastIndex,
                            isProcessing = false,
                            resultRecord = null
                        )
                    }
                    refreshScanPreview()
                }
                .onFailure { err ->
                    _smartScanState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Failed to import photo"))
                }
        }
    }

    fun selectScanPage(index: Int) {
        _smartScanState.update { state ->
            if (index in state.pages.indices) state.copy(selectedPageIndex = index) else state
        }
        refreshScanPreview()
    }

    fun updateCurrentScanPage(
        rotateDelta: Int = 0,
        brightness: Float? = null,
        cropMarginPercent: Float? = null,
        filter: ScanFilter? = null
    ) {
        _smartScanState.update { state ->
            val idx = state.selectedPageIndex
            val list = state.pages.toMutableList()
            if (idx in list.indices) {
                val cur = list[idx]
                list[idx] = cur.copy(
                    rotationDegrees = (cur.rotationDegrees + rotateDelta) % 360,
                    brightness = brightness ?: cur.brightness,
                    cropMarginPercent = cropMarginPercent ?: cur.cropMarginPercent,
                    filter = filter ?: cur.filter
                )
            }
            state.copy(pages = list, resultRecord = null)
        }
        refreshScanPreview()
    }

    fun removeScanPage(index: Int) {
        _smartScanState.update { state ->
            val list = state.pages.toMutableList()
            if (index in list.indices) list.removeAt(index)
            val newIdx = list.lastIndex.coerceAtLeast(0)
            state.copy(pages = list, selectedPageIndex = newIdx, previewBitmap = if (list.isEmpty()) null else state.previewBitmap, resultRecord = null)
        }
        refreshScanPreview()
    }

    fun updateScanOutputName(name: String) {
        _smartScanState.update { it.copy(outputName = name) }
    }

    private fun refreshScanPreview() {
        val state = _smartScanState.value
        val page = state.pages.getOrNull(state.selectedPageIndex) ?: return
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { engine.renderScanPreview(page) }
            _smartScanState.update { it.copy(previewBitmap = bmp) }
        }
    }

    fun saveScannedPagesAsPdf() {
        val state = _smartScanState.value
        if (state.pages.isEmpty()) {
            viewModelScope.launch { _toastEvents.emit(UiToastEvent.Error("Capture or import at least one page first.")) }
            return
        }
        viewModelScope.launch {
            _smartScanState.update { it.copy(isProcessing = true, resultRecord = null) }
            engine.scannedPagesToPdf(state.pages, state.outputName)
                .onSuccess { record ->
                    _smartScanState.update { it.copy(isProcessing = false, resultRecord = record) }
                    _toastEvents.emit(UiToastEvent.Success("Saved scanned PDF: ${record.name}"))
                }
                .onFailure { err ->
                    _smartScanState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Failed to save scan"))
                }
        }
    }

    // Import PDF
    fun inspectAndImportPdf(uri: Uri) {
        viewModelScope.launch {
            _importPdfState.update { it.copy(isProcessing = true, importedRecord = null) }
            engine.inspectPdfUri(uri)
                .onSuccess { candidate ->
                    _importPdfState.update {
                        it.copy(inspectedCandidate = candidate, inspectedUri = uri)
                    }
                    engine.importPdfFromUri(uri)
                        .onSuccess { record ->
                            _importPdfState.update {
                                it.copy(isProcessing = false, importedRecord = record)
                            }
                            _toastEvents.emit(UiToastEvent.Success("Imported ${record.name}"))
                        }
                        .onFailure { err ->
                            _importPdfState.update { it.copy(isProcessing = false) }
                            _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Import failed"))
                        }
                }
                .onFailure { err ->
                    _importPdfState.update {
                        it.copy(isProcessing = false, inspectedCandidate = null, importedRecord = null)
                    }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Invalid PDF file"))
                }
        }
    }

    // Compress PDF
    fun selectPdfForCompression(uri: Uri) {
        viewModelScope.launch {
            _compressPdfState.update { it.copy(isProcessing = true, summary = null) }
            engine.inspectPdfUri(uri)
                .onSuccess { candidate ->
                    val defaultOut = "${candidate.displayName.removeSuffix(".pdf")}_compressed"
                    _compressPdfState.update {
                        it.copy(selectedSource = candidate, outputName = defaultOut, isProcessing = false)
                    }
                }
                .onFailure { err ->
                    _compressPdfState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Invalid PDF file"))
                }
        }
    }

    fun selectLocalPdfForCompression(record: PdfFileRecord) {
        val candidate = PdfDocumentCandidate(
            uri = null,
            localFilePath = record.filePath,
            displayName = record.name,
            sizeBytes = record.sizeBytes,
            pageCount = record.pageCount
        )
        _compressPdfState.update {
            it.copy(
                selectedSource = candidate,
                outputName = "${record.name.removeSuffix(".pdf")}_compressed",
                summary = null
            )
        }
    }

    fun updateCompressionLevel(level: CompressionLevel) {
        _compressPdfState.update { it.copy(level = level, summary = null) }
    }

    fun updateCompressionOutputName(name: String) {
        _compressPdfState.update { it.copy(outputName = name) }
    }

    fun runCompression() {
        val state = _compressPdfState.value
        val source = state.selectedSource ?: return
        viewModelScope.launch {
            _compressPdfState.update { it.copy(isProcessing = true, summary = null) }
            engine.compressPdf(source, state.level, state.outputName)
                .onSuccess { summary ->
                    _compressPdfState.update { it.copy(isProcessing = false, summary = summary) }
                    _toastEvents.emit(UiToastEvent.Success("Compressed ${summary.record.name}"))
                }
                .onFailure { err ->
                    _compressPdfState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Compression failed"))
                }
        }
    }

    // PDF to JPG
    fun selectPdfForJpg(uri: Uri) {
        viewModelScope.launch {
            _pdfToJpgState.update { it.copy(isProcessing = true, result = null) }
            engine.inspectPdfUri(uri)
                .onSuccess { candidate ->
                    _pdfToJpgState.update { it.copy(selectedSource = candidate, isProcessing = false) }
                }
                .onFailure { err ->
                    _pdfToJpgState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Invalid PDF file"))
                }
        }
    }

    fun selectLocalPdfForJpg(record: PdfFileRecord) {
        val candidate = PdfDocumentCandidate(
            uri = null,
            localFilePath = record.filePath,
            displayName = record.name,
            sizeBytes = record.sizeBytes,
            pageCount = record.pageCount
        )
        _pdfToJpgState.update { it.copy(selectedSource = candidate, result = null) }
    }

    fun updatePdfToJpgQuality(quality: Int) {
        _pdfToJpgState.update { it.copy(quality = quality, result = null) }
    }

    fun convertPdfToJpg() {
        val state = _pdfToJpgState.value
        val source = state.selectedSource ?: return
        viewModelScope.launch {
            _pdfToJpgState.update { it.copy(isProcessing = true, result = null) }
            engine.pdfToJpg(source, state.quality)
                .onSuccess { result ->
                    _pdfToJpgState.update { it.copy(isProcessing = false, result = result) }
                    _toastEvents.emit(UiToastEvent.Success("Exported ${result.imagePaths.size} JPG pages"))
                }
                .onFailure { err ->
                    _pdfToJpgState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Export failed"))
                }
        }
    }

    // Merge PDF
    fun addPdfUrisToMerge(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _mergePdfState.update { it.copy(isProcessing = true, resultRecord = null) }
            val added = mutableListOf<PdfDocumentCandidate>()
            var firstError: String? = null
            uris.forEach { uri ->
                engine.inspectPdfUri(uri)
                    .onSuccess { added.add(it) }
                    .onFailure { if (firstError == null) firstError = it.localizedMessage }
            }
            _mergePdfState.update {
                it.copy(sources = it.sources + added, isProcessing = false)
            }
            if (firstError != null) {
                _toastEvents.emit(UiToastEvent.Error(firstError ?: "Some files could not be added"))
            }
        }
    }

    fun addLocalPdfToMerge(record: PdfFileRecord) {
        val candidate = PdfDocumentCandidate(
            uri = null,
            localFilePath = record.filePath,
            displayName = record.name,
            sizeBytes = record.sizeBytes,
            pageCount = record.pageCount
        )
        _mergePdfState.update {
            it.copy(sources = it.sources + candidate, resultRecord = null)
        }
    }

    fun moveMergePdf(fromIndex: Int, toIndex: Int) {
        _mergePdfState.update { state ->
            val list = state.sources.toMutableList()
            if (fromIndex in list.indices && toIndex in list.indices) {
                val item = list.removeAt(fromIndex)
                list.add(toIndex, item)
            }
            state.copy(sources = list, resultRecord = null)
        }
    }

    fun removeMergePdfAt(index: Int) {
        _mergePdfState.update { state ->
            val list = state.sources.toMutableList()
            if (index in list.indices) list.removeAt(index)
            state.copy(sources = list, resultRecord = null)
        }
    }

    fun updateMergeOutputName(name: String) {
        _mergePdfState.update { it.copy(outputName = name) }
    }

    fun runMergePdfs() {
        val state = _mergePdfState.value
        if (state.sources.size < 2) {
            viewModelScope.launch { _toastEvents.emit(UiToastEvent.Error("Add at least 2 PDF files to merge.")) }
            return
        }
        viewModelScope.launch {
            _mergePdfState.update { it.copy(isProcessing = true, resultRecord = null) }
            engine.mergePdfs(state.sources, state.outputName)
                .onSuccess { record ->
                    _mergePdfState.update { it.copy(isProcessing = false, resultRecord = record) }
                    _toastEvents.emit(UiToastEvent.Success("Merged into ${record.name} (${record.pageCount} pages)"))
                }
                .onFailure { err ->
                    _mergePdfState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Merge failed"))
                }
        }
    }

    // DOCX to PDF
    fun selectDocxUri(uri: Uri) {
        viewModelScope.launch {
            _docxToPdfState.update { it.copy(isProcessing = true, resultRecord = null) }
            val name = engine.resolveDisplayName(uri, "Document.docx")
            engine.parseDocxPreview(uri)
                .onSuccess { paragraphs ->
                    _docxToPdfState.update {
                        it.copy(
                            selectedFileName = name,
                            paragraphs = paragraphs,
                            outputName = name.substringBeforeLast('.'),
                            isProcessing = false
                        )
                    }
                }
                .onFailure { err ->
                    _docxToPdfState.update {
                        it.copy(
                            selectedFileName = null,
                            paragraphs = emptyList(),
                            isProcessing = false
                        )
                    }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Could not parse DOCX"))
                }
        }
    }

    fun updateDocxOutputName(name: String) {
        _docxToPdfState.update { it.copy(outputName = name) }
    }

    fun convertDocxToPdf() {
        val state = _docxToPdfState.value
        if (state.paragraphs.isEmpty()) {
            viewModelScope.launch { _toastEvents.emit(UiToastEvent.Error("Select a valid .docx file first.")) }
            return
        }
        viewModelScope.launch {
            _docxToPdfState.update { it.copy(isProcessing = true, resultRecord = null) }
            engine.docxToPdf(state.paragraphs, state.outputName, PdfToolId.DOCX_TO_PDF.title)
                .onSuccess { record ->
                    _docxToPdfState.update { it.copy(isProcessing = false, resultRecord = record) }
                    _toastEvents.emit(UiToastEvent.Success("Converted to ${record.name}"))
                }
                .onFailure { err ->
                    _docxToPdfState.update { it.copy(isProcessing = false) }
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Conversion failed"))
                }
        }
    }

    // More Tools: Quick Text to PDF
    fun createTextNotePdf(
        title: String,
        body: String,
        outputName: String,
        onCreated: (PdfFileRecord) -> Unit
    ) {
        if (body.isBlank()) {
            viewModelScope.launch { _toastEvents.emit(UiToastEvent.Error("Please enter text content.")) }
            return
        }
        viewModelScope.launch {
            val paragraphs = buildList {
                if (title.isNotBlank()) add(DocxParagraph(text = title.trim(), isHeading1 = true))
                body.lines().forEach { line ->
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("# ") -> add(DocxParagraph(trimmed.removePrefix("# "), isHeading1 = true))
                        trimmed.startsWith("## ") -> add(DocxParagraph(trimmed.removePrefix("## "), isHeading2 = true))
                        trimmed.startsWith("- ") || trimmed.startsWith("* ") ->
                            add(DocxParagraph(trimmed.drop(2), isBullet = true))
                        else -> add(DocxParagraph(line))
                    }
                }
            }
            engine.docxToPdf(paragraphs, outputName.ifBlank { "Quick_Note" }, "Text to PDF")
                .onSuccess { record ->
                    _toastEvents.emit(UiToastEvent.Success("Created ${record.name}"))
                    onCreated(record)
                }
                .onFailure { err ->
                    _toastEvents.emit(UiToastEvent.Error(err.localizedMessage ?: "Failed to create PDF"))
                }
        }
    }
}
