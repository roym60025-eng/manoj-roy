package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.SettingsRepository
import com.example.data.model.PdfFileRecord
import com.example.data.model.PdfToolId
import com.example.ui.components.AllToolsBottomSheet
import com.example.ui.components.MainTab
import com.example.ui.components.SmartPdfBottomBar
import com.example.ui.components.SmartPdfNavigationRail
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.screens.tools.CompressPdfScreen
import com.example.ui.screens.tools.DocxToPdfScreen
import com.example.ui.screens.tools.ImageToPdfScreen
import com.example.ui.screens.tools.ImportPdfScreen
import com.example.ui.screens.tools.MergePdfScreen
import com.example.ui.screens.tools.MoreToolsScreen
import com.example.ui.screens.tools.PdfToJpgScreen
import com.example.ui.screens.tools.SmartScanScreen
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.RoseError
import com.example.ui.theme.SmartPdfMakerTheme
import com.example.util.FormatUtils
import kotlinx.coroutines.flow.first
import java.io.File

object AppRoutes {
    const val WELCOME = "welcome"
    const val DASHBOARD = "dashboard"
    const val IMAGE_TO_PDF = "image_to_pdf"
    const val SMART_SCAN = "smart_scan"
    const val IMPORT_PDF = "import_pdf"
    const val COMPRESS_PDF = "compress_pdf"
    const val PDF_TO_JPG = "pdf_to_jpg"
    const val MERGE_PDF = "merge_pdf"
    const val DOCX_TO_PDF = "docx_to_pdf"
    const val MORE_TOOLS = "more_tools"
    const val SETTINGS = "settings"
    const val PDF_VIEWER = "pdf_viewer/{fileId}"

    fun pdfViewer(fileId: Long): String = "pdf_viewer/$fileId"

    fun routeForTool(toolId: PdfToolId): String = when (toolId) {
        PdfToolId.IMAGE_TO_PDF -> IMAGE_TO_PDF
        PdfToolId.SMART_SCAN -> SMART_SCAN
        PdfToolId.IMPORT_PDF -> IMPORT_PDF
        PdfToolId.COMPRESS_PDF -> COMPRESS_PDF
        PdfToolId.PDF_TO_JPG -> PDF_TO_JPG
        PdfToolId.MERGE_PDF -> MERGE_PDF
        PdfToolId.DOCX_TO_PDF -> DOCX_TO_PDF
        PdfToolId.MORE_TOOLS -> MORE_TOOLS
    }
}

@Composable
fun SmartPdfApp(
    viewModel: SmartPdfViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val initialSeenWelcome by produceState<Boolean?>(initialValue = null) {
        value = SettingsRepository(context).settings.first().hasSeenWelcome
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var lastToastIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastEvents.collect { event ->
            lastToastIsError = event is UiToastEvent.Error
            val msg = when (event) {
                is UiToastEvent.Success -> event.message
                is UiToastEvent.Error -> event.message
            }
            snackbarHostState.showSnackbar(
                message = msg,
                duration = SnackbarDuration.Short
            )
        }
    }

    SmartPdfMakerTheme(darkTheme = settings.isDarkMode) {
        val seen = initialSeenWelcome
        if (seen == null) {
            Box(modifier = Modifier.fillMaxSize())
        } else {
            val navController = rememberNavController()
            var currentTab by rememberSaveable { mutableStateOf(MainTab.HOME) }
            var showAllToolsSheet by rememberSaveable { mutableStateOf(false) }
            var pendingDownloadRecord by remember { mutableStateOf<PdfFileRecord?>(null) }

            val downloadFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.CreateDocument("application/pdf")
            ) { uri ->
                val target = pendingDownloadRecord
                if (uri != null && target != null) {
                    viewModel.exportPdfToUri(target, uri)
                }
                pendingDownloadRecord = null
            }

            fun navigateToTool(toolId: PdfToolId) {
                navController.navigate(AppRoutes.routeForTool(toolId)) {
                    launchSingleTop = true
                }
            }

            fun openPdfViewer(record: PdfFileRecord) {
                navController.navigate(AppRoutes.pdfViewer(record.id)) {
                    launchSingleTop = true
                }
            }

            Scaffold(
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = if (lastToastIsError) RoseError else EmeraldSuccess,
                            contentColor = Color.White
                        )
                    }
                }
            ) { rootPadding ->
                NavHost(
                    navController = navController,
                    startDestination = if (seen) AppRoutes.DASHBOARD else AppRoutes.WELCOME,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(AppRoutes.WELCOME) {
                        WelcomeScreen(
                            onOpenTool = { tool ->
                                viewModel.markWelcomeSeen()
                                navController.navigate(AppRoutes.DASHBOARD) {
                                    popUpTo(AppRoutes.WELCOME) { inclusive = true }
                                }
                                navigateToTool(tool)
                            },
                            onContinueToDashboard = {
                                viewModel.markWelcomeSeen()
                                navController.navigate(AppRoutes.DASHBOARD) {
                                    popUpTo(AppRoutes.WELCOME) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(AppRoutes.DASHBOARD) {
                        val allFiles by viewModel.allFiles.collectAsStateWithLifecycle()
                        val filteredFiles by viewModel.filteredFiles.collectAsStateWithLifecycle()
                        val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                        val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val useRail = maxWidth >= 680.dp

                            if (useRail) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    SmartPdfNavigationRail(
                                        currentTab = currentTab,
                                        onSelectTab = { currentTab = it },
                                        onPlusClick = { showAllToolsSheet = true },
                                        onOpenSettings = {
                                            navController.navigate(AppRoutes.SETTINGS) {
                                                launchSingleTop = true
                                            }
                                        }
                                    )
                                    Box(modifier = Modifier.weight(1f)) {
                                        val contentPadding = PaddingValues(
                                            top = rootPadding.calculateTopPadding(),
                                            bottom = rootPadding.calculateBottomPadding()
                                        )
                                        when (currentTab) {
                                            MainTab.HOME -> HomeScreen(
                                                recentFiles = allFiles,
                                                isDarkMode = settings.isDarkMode,
                                                onToggleTheme = viewModel::toggleDarkMode,
                                                onOpenTool = ::navigateToTool,
                                                onOpenFile = ::openPdfViewer,
                                                onOpenAllFiles = { currentTab = MainTab.FILES },
                                                onOpenSettings = {
                                                    navController.navigate(AppRoutes.SETTINGS) {
                                                        launchSingleTop = true
                                                    }
                                                },
                                                contentPadding = contentPadding
                                            )
                                            MainTab.FILES -> FilesScreen(
                                                files = filteredFiles,
                                                searchQuery = searchQuery,
                                                sortOption = sortOption,
                                                onSearchChange = viewModel::updateSearchQuery,
                                                onSortChange = viewModel::updateSortOption,
                                                onOpenFile = ::openPdfViewer,
                                                onShareFile = {
                                                    FormatUtils.shareFile(context, File(it.filePath), "application/pdf")
                                                },
                                                onDownloadFile = {
                                                    pendingDownloadRecord = it
                                                    downloadFileLauncher.launch(it.name)
                                                },
                                                onRenameFile = viewModel::renameFile,
                                                onDeleteFile = viewModel::deleteFile,
                                                onImportPdfClick = { navigateToTool(PdfToolId.IMPORT_PDF) },
                                                contentPadding = contentPadding
                                            )
                                        }
                                    }
                                }
                            } else {
                                Scaffold(
                                    bottomBar = {
                                        SmartPdfBottomBar(
                                            currentTab = currentTab,
                                            onSelectTab = { currentTab = it },
                                            onPlusClick = { showAllToolsSheet = true }
                                        )
                                    }
                                ) { innerPadding ->
                                    val combinedPadding = PaddingValues(
                                        top = innerPadding.calculateTopPadding(),
                                        bottom = innerPadding.calculateBottomPadding()
                                    )
                                    when (currentTab) {
                                        MainTab.HOME -> HomeScreen(
                                            recentFiles = allFiles,
                                            isDarkMode = settings.isDarkMode,
                                            onToggleTheme = viewModel::toggleDarkMode,
                                            onOpenTool = ::navigateToTool,
                                            onOpenFile = ::openPdfViewer,
                                            onOpenAllFiles = { currentTab = MainTab.FILES },
                                            onOpenSettings = {
                                                navController.navigate(AppRoutes.SETTINGS) {
                                                    launchSingleTop = true
                                                }
                                            },
                                            contentPadding = combinedPadding
                                        )
                                        MainTab.FILES -> FilesScreen(
                                            files = filteredFiles,
                                            searchQuery = searchQuery,
                                            sortOption = sortOption,
                                            onSearchChange = viewModel::updateSearchQuery,
                                            onSortChange = viewModel::updateSortOption,
                                            onOpenFile = ::openPdfViewer,
                                            onShareFile = {
                                                FormatUtils.shareFile(context, File(it.filePath), "application/pdf")
                                            },
                                            onDownloadFile = {
                                                pendingDownloadRecord = it
                                                downloadFileLauncher.launch(it.name)
                                            },
                                            onRenameFile = viewModel::renameFile,
                                            onDeleteFile = viewModel::deleteFile,
                                            onImportPdfClick = { navigateToTool(PdfToolId.IMPORT_PDF) },
                                            contentPadding = combinedPadding
                                        )
                                    }
                                }
                            }
                        }

                        if (showAllToolsSheet) {
                            AllToolsBottomSheet(
                                onDismiss = { showAllToolsSheet = false },
                                onSelectTool = ::navigateToTool
                            )
                        }
                    }

                    composable(AppRoutes.IMAGE_TO_PDF) {
                        ImageToPdfScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer
                        )
                    }

                    composable(AppRoutes.SMART_SCAN) {
                        SmartScanScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer
                        )
                    }

                    composable(AppRoutes.IMPORT_PDF) {
                        ImportPdfScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer
                        )
                    }

                    composable(AppRoutes.COMPRESS_PDF) {
                        CompressPdfScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer
                        )
                    }

                    composable(AppRoutes.PDF_TO_JPG) {
                        PdfToJpgScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(AppRoutes.MERGE_PDF) {
                        MergePdfScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer
                        )
                    }

                    composable(AppRoutes.DOCX_TO_PDF) {
                        DocxToPdfScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer
                        )
                    }

                    composable(AppRoutes.MORE_TOOLS) {
                        MoreToolsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onOpenPdf = ::openPdfViewer,
                            onOpenSettings = {
                                navController.navigate(AppRoutes.SETTINGS) { launchSingleTop = true }
                            },
                            onOpenFiles = {
                                currentTab = MainTab.FILES
                                navController.popBackStack(AppRoutes.DASHBOARD, inclusive = false)
                            },
                            onShowWelcome = {
                                navController.navigate(AppRoutes.WELCOME) {
                                    popUpTo(AppRoutes.DASHBOARD) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(AppRoutes.SETTINGS) {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() },
                            onShowWelcome = {
                                navController.navigate(AppRoutes.WELCOME) {
                                    popUpTo(AppRoutes.DASHBOARD) { inclusive = true }
                                }
                            }
                        )
                    }

                    composable(
                        route = AppRoutes.PDF_VIEWER,
                        arguments = listOf(navArgument("fileId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val fileId = backStackEntry.arguments?.getLong("fileId") ?: -1L
                        PdfViewerScreen(
                            fileId = fileId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
