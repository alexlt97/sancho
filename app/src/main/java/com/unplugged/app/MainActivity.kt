package com.unplugged.app

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val GalaxyColors = darkColorScheme(
    primary = Color(0xFF7EE787),
    onPrimary = Color(0xFF06210E),
    secondary = Color(0xFFC9A7FF),
    onSecondary = Color(0xFF24113F),
    background = Color(0xFF090711),
    onBackground = Color(0xFFF4EDFF),
    surface = Color(0xFF151021),
    onSurface = Color(0xFFF4EDFF),
    surfaceVariant = Color(0xFF27203A),
    onSurfaceVariant = Color(0xFFD0C5E3),
)

private val GalaxyGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFF080611),
        Color(0xFF17102A),
        Color(0xFF24123B),
        Color(0xFF0B0818),
    ),
)

private val ActionGreen = Color(0xFF1C6844)
private val StatusReadyGreen = Color(0xFF7EE787)
private val StatusRunningOrange = Color(0xFFFFB454)
private val StatusUnavailableRed = Color(0xFFFF7B72)
private val UserMessageBackground = Color(0xFF173B2D).copy(alpha = 0.72f)
private val AssistantMessageBackground = Color(0xFF211A35).copy(alpha = 0.78f)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            MaterialTheme(colorScheme = GalaxyColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = GalaxyColors.onBackground,
                ) {
                    val context = LocalContext.current
                    val modelImporter = remember(context) {
                        FileModelImporter(
                            contentResolver = context.contentResolver,
                            modelsDirectory = File(context.filesDir, "models"),
                        )
                    }
                    val localLlm = remember(context) {
                        if (BuildConfig.MOCK_PREVIEW) {
                            MockLocalLlm()
                        } else {
                            LiteRtLocalLlm(context.cacheDir)
                        }
                    }
                    GalaxyBackdrop {
                        SanchoChat(
                            modelImporter = modelImporter,
                            localLlm = localLlm,
                            initialModelReady = BuildConfig.MOCK_PREVIEW,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsPanel(
    state: ChatUiState,
    onImportModel: () -> Unit,
    reportCount: Int,
    onOpenReports: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Local model",
            style = MaterialTheme.typography.headlineSmall,
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.modelName ?: "No local model imported",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (state.isModelReady) {
                        "Ready for offline chat and image requests."
                    } else {
                        "Import a .litertlm model to enable offline chat."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onImportModel,
                    enabled = !state.isImportingModel && !state.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionGreen,
                        contentColor = Color.White,
                        disabledContainerColor = ActionGreen.copy(alpha = 0.45f),
                    ),
                ) {
                    Text(if (state.isImportingModel) "Importing…" else "Import model")
                }
                if (state.isImportingModel) {
                    Text(
                        text = state.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = onOpenReports,
                    enabled = !state.isImportingModel && !state.isGenerating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text("Reports ($reportCount)")
                }
            }
        }
        Text(
            text = "Models are stored in Sancho’s private app storage and remain on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ColumnScope.ReportPanel(
    state: ReportUiState,
    editingReportId: String?,
    onEditingReportIdChange: (String?) -> Unit,
    onSave: (IssueReport) -> Unit,
    onDelete: (String) -> Unit,
    onAddScreenshot: (String) -> Unit,
    onExport: () -> Unit,
) {
    val selectedReport = state.reports.firstOrNull { it.id == editingReportId }
    var category by remember(editingReportId, selectedReport?.updatedAtMillis) {
        mutableStateOf(selectedReport?.category ?: "Bug")
    }
    var title by remember(editingReportId, selectedReport?.updatedAtMillis) {
        mutableStateOf(selectedReport?.title.orEmpty())
    }
    var description by remember(editingReportId, selectedReport?.updatedAtMillis) {
        mutableStateOf(selectedReport?.description.orEmpty())
    }
    var reproductionSteps by remember(editingReportId, selectedReport?.updatedAtMillis) {
        mutableStateOf(selectedReport?.reproductionSteps.orEmpty())
    }
    var expectedBehavior by remember(editingReportId, selectedReport?.updatedAtMillis) {
        mutableStateOf(selectedReport?.expectedBehavior.orEmpty())
    }
    var actualBehavior by remember(editingReportId, selectedReport?.updatedAtMillis) {
        mutableStateOf(selectedReport?.actualBehavior.orEmpty())
    }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = { onEditingReportIdChange(null) }) {
                Text("New report")
            }
            Button(
                onClick = onExport,
                enabled = state.reports.isNotEmpty() && !state.isWorking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ActionGreen,
                    contentColor = Color.White,
                ),
            ) {
                Text("Export reports")
            }
        }

        state.status?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.reports.isNotEmpty()) {
            Text("Saved reports", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.reports, key = { it.id }) { report ->
                    TextButton(
                        onClick = { onEditingReportIdChange(report.id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(report.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = report.category,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "No saved reports. Create one when you find a bug or missing feature.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text("Report details", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { category = if (category == "Bug") "Missing feature" else "Bug" }) {
            Text("Type: $category")
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            minLines = 2,
            maxLines = 4,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = reproductionSteps,
            onValueChange = { reproductionSteps = it },
            label = { Text("Reproduction steps (optional)") },
            minLines = 1,
            maxLines = 3,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = expectedBehavior,
            onValueChange = { expectedBehavior = it },
            label = { Text("Expected behavior (optional)") },
            minLines = 1,
            maxLines = 3,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = actualBehavior,
            onValueChange = { actualBehavior = it },
            label = { Text("Actual behavior (optional)") },
            minLines = 1,
            maxLines = 3,
        )
        selectedReport?.screenshotFiles?.let { screenshots ->
            screenshots.forEach { screenshot ->
                Text(
                    text = "Screenshot: $screenshot",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val now = System.currentTimeMillis()
                    onSave(
                        IssueReport(
                            id = selectedReport?.id ?: UUID.randomUUID().toString(),
                            category = category,
                            title = title.trim(),
                            description = description.trim(),
                            reproductionSteps = reproductionSteps.trim(),
                            expectedBehavior = expectedBehavior.trim(),
                            actualBehavior = actualBehavior.trim(),
                            createdAtMillis = selectedReport?.createdAtMillis ?: now,
                            updatedAtMillis = now,
                            screenshotFiles = selectedReport?.screenshotFiles.orEmpty(),
                        ),
                    )
                },
                enabled = title.isNotBlank() && description.isNotBlank() && !state.isWorking,
            ) {
                Text("Save report")
            }
            if (selectedReport != null) {
                TextButton(
                    onClick = { onAddScreenshot(selectedReport.id) },
                    enabled = !state.isWorking,
                ) {
                    Text("Add screenshot")
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !state.isWorking,
                ) {
                    Text("Delete")
                }
            }
        }
    }

    if (confirmDelete && selectedReport != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete report?") },
            text = { Text("This removes the report and its screenshots from Sancho.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete(selectedReport.id)
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun GalaxyBackdrop(content: @Composable () -> Unit) {
    val stars = listOf(
        0.21f to 0.32f,
        0.38f to 0.11f,
        0.57f to 0.24f,
        0.76f to 0.09f,
        0.91f to 0.36f,
        0.14f to 0.72f,
        0.48f to 0.84f,
        0.84f to 0.76f,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GalaxyGradient),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            stars.forEachIndexed { index, (x, y) ->
                drawCircle(
                    color = Color.White.copy(alpha = if (index % 3 == 0) 0.42f else 0.2f),
                    radius = if (index % 3 == 0) 2.2f else 1.2f,
                    center = Offset(size.width * x, size.height * y),
                )
            }
        }
        content()
    }
}

@Composable
private fun SanchoChat(
    modelImporter: ModelImporter,
    localLlm: LocalLlm,
    initialModelReady: Boolean = false,
) {
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(modelImporter, localLlm, initialModelReady),
    )
    val state by chatViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val reportStore = remember(context) {
        ReportStore(
            contentResolver = context.contentResolver,
            reportsDirectory = File(context.filesDir, "reports"),
            cacheDirectory = context.cacheDir,
        )
    }
    val reportViewModel: ReportViewModel = viewModel(
        factory = ReportViewModel.factory(reportStore),
    )
    val reportState by reportViewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var showSettings by remember { mutableStateOf(false) }
    var showReports by remember { mutableStateOf(false) }
    var editingReportId by remember { mutableStateOf<String?>(null) }
    var screenshotReportId by remember { mutableStateOf<String?>(null) }
    val messageListState = rememberLazyListState()
    val isNearLatestMessage by remember {
        derivedStateOf {
            val layoutInfo = messageListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            layoutInfo.totalItemsCount == 0 ||
                lastVisibleIndex == null ||
                lastVisibleIndex >= layoutInfo.totalItemsCount - 2
        }
    }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            messageListState.scrollToItem(state.messages.lastIndex)
        }
    }
    val imageStore = remember(context) {
        FileImageAttachmentStore(
            contentResolver = context.contentResolver,
            imageDirectory = File(context.cacheDir, "image-inputs"),
        )
    }
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var attachmentMenuExpanded by remember { mutableStateOf(false) }
    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> uri?.let(chatViewModel::importModel) },
    )
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    when (val result = imageStore.copySelectedImage(uri)) {
                        is ImageAttachmentResult.Success -> chatViewModel.attachImage(result.attachment)
                        is ImageAttachmentResult.Failure -> chatViewModel.showImageError(result.message)
                    }
                }
            }
        },
    )
    val cameraPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { captured ->
            val file = pendingCameraFile
            pendingCameraFile = null
            if (captured && file != null) {
                chatViewModel.attachImage(ImageAttachment(file, "Camera photo"))
            } else {
                file?.delete()
            }
        },
    )
    val reportExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
        onResult = { uri -> uri?.let(reportViewModel::export) },
    )
    val reportScreenshotPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            val report = reportState.reports.firstOrNull { it.id == screenshotReportId }
            screenshotReportId = null
            if (uri != null && report != null) {
                reportViewModel.addScreenshot(report, uri)
            }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (showSettings || showReports) {
                Arrangement.Start
            } else {
                Arrangement.End
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showReports || showSettings) {
                IconButton(
                    onClick = {
                        if (showReports) {
                            showReports = false
                        } else {
                            showSettings = false
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back_24),
                        contentDescription = "Back to chat",
                    )
                }
                Text(
                    text = if (showReports) "Reports" else "Settings",
                    style = MaterialTheme.typography.titleLarge,
                )
            } else {
                ModelStatus(
                    state = state,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        showSettings = true
                        showReports = false
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_24),
                        contentDescription = "Open settings",
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }

        if (showReports) {
            ReportPanel(
                state = reportState,
                editingReportId = editingReportId,
                onEditingReportIdChange = { editingReportId = it },
                onSave = {
                    editingReportId = it.id
                    reportViewModel.save(it)
                },
                onDelete = {
                    reportViewModel.delete(it)
                    editingReportId = null
                },
                onAddScreenshot = { reportId ->
                    screenshotReportId = reportId
                    reportScreenshotPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onExport = { reportExportPicker.launch("sancho_report.zip") },
            )
        } else if (showSettings) {
            SettingsPanel(
                state = state,
                onImportModel = { modelPicker.launch(arrayOf("*/*")) },
                reportCount = reportState.reports.size,
                onOpenReports = {
                    showReports = true
                    editingReportId = null
                },
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.offset(y = (-4).dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )

        state.imageAttachment?.let { attachment ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    ImageThumbnail(
                        file = attachment.file,
                        contentDescription = "Preview of ${attachment.displayName}",
                        modifier = Modifier.size(72.dp),
                    )
                    Text(
                        text = "Image: ${attachment.displayName}",
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    TextButton(
                        onClick = chatViewModel::removeImage,
                        enabled = !state.isGenerating,
                    ) {
                        Text("Remove")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (message.author == "You") {
                        UserMessageBackground
                    } else {
                        AssistantMessageBackground
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            message.author,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (message.author == "You") {
                                GalaxyColors.primary
                            } else {
                                GalaxyColors.secondary
                            },
                        )
                        message.timeToFirstTokenMillis?.let { elapsedMillis ->
                            Text(
                                text = "TTFT: ${formatResponseTime(elapsedMillis)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (message.author == "Assistant") {
                            MarkdownText(message.text)
                        } else {
                            message.imageAttachment?.let { attachment ->
                                ImageThumbnail(
                                    file = attachment.file,
                                    contentDescription = "Image used in prompt: ${attachment.displayName}",
                                    modifier = Modifier.size(88.dp),
                                )
                            }
                            Text(message.text)
                        }
                    }
                }
            }
        }

        if (state.messages.isNotEmpty() && !isNearLatestMessage) {
            TextButton(
                onClick = {
                    scope.launch {
                        messageListState.animateScrollToItem(state.messages.lastIndex)
                    }
                },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(if (state.isGenerating) "Jump to latest response" else "Jump to latest")
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { attachmentMenuExpanded = true },
                        enabled = !state.isGenerating,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add_24),
                            contentDescription = "Add attachment",
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = state.draft,
                        onValueChange = chatViewModel::updateDraft,
                        placeholder = { Text("Ask something…") },
                        singleLine = false,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = GalaxyColors.primary,
                        ),
                    )
                    IconButton(
                        onClick = {
                            if (state.isGenerating) {
                                chatViewModel.cancelGeneration()
                            } else {
                                keyboardController?.hide()
                                focusManager.clearFocus(force = true)
                                chatViewModel.send()
                            }
                        },
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(44.dp)
                            .background(
                                color = if (state.isGenerating) {
                                    StatusRunningOrange
                                } else {
                                    ActionGreen
                                },
                                shape = CircleShape,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(
                                if (state.isGenerating) {
                                    R.drawable.ic_stop_24
                                } else {
                                    R.drawable.ic_send_24
                                },
                            ),
                            contentDescription = if (state.isGenerating) {
                                "Stop generation"
                            } else {
                                "Send message"
                            },
                            tint = Color.White,
                        )
                    }
                }
            }
            DropdownMenu(
                expanded = attachmentMenuExpanded,
                onDismissRequest = { attachmentMenuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Choose image") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_attach_file_24),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        attachmentMenuExpanded = false
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    enabled = !state.isGenerating,
                )
                DropdownMenuItem(
                    text = { Text("Use camera") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera_24),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        attachmentMenuExpanded = false
                        val file = imageStore.newCameraFile()
                        pendingCameraFile = file
                        val uri = FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file,
                        )
                        cameraPicker.launch(uri)
                    },
                    enabled = !state.isGenerating,
                )
            }
        }
    }
}
}

@Composable
private fun ImageThumbnail(
    file: File,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { decodeThumbnail(file) }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(MaterialTheme.shapes.small),
        )
    } else {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Image",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun decodeThumbnail(file: File, maxDimension: Int = 512): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    var sampleSize = 1
    while (bounds.outWidth / sampleSize > maxDimension ||
        bounds.outHeight / sampleSize > maxDimension
    ) {
        sampleSize *= 2
    }

    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return BitmapFactory.decodeFile(file.absolutePath, options)?.asImageBitmap()
}

@Composable
private fun ModelStatus(
    state: ChatUiState,
    modifier: Modifier = Modifier,
) {
    val (color, label, description) = when {
        state.isGenerating -> Triple(StatusRunningOrange, "Generating", "Model status: generating")
        state.isImportingModel -> Triple(StatusRunningOrange, "Loading", "Model status: loading")
        state.isModelReady -> Triple(StatusReadyGreen, "Ready", "Model status: ready")
        else -> Triple(StatusUnavailableRed, "Unavailable", "Model status: unavailable")
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
                .semantics { contentDescription = description },
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
