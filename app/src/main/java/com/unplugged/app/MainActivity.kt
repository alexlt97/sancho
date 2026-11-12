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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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

private val ActionGreen = Color(0xFF176B49)

private data class BraveApiTestUiState(
    val testing: Boolean = false,
    val message: String? = null,
    val success: Boolean = false,
)

private enum class SettingsSection(
    val title: String,
    val summary: String,
) {
    MODELS("Models", "Import and manage local models"),
    VOICE("Voice settings", "Widget, Quick Settings, and speech"),
    REPORTS("Reports", "Review and export issue reports"),
    CHAT("Chat settings", "Metrics and web search"),
}

private enum class ChatSettingsPage(
    val title: String,
    val summary: String,
) {
    METRICS("Metrics", "Response timing and web-search diagnostics"),
    WEB_SEARCH("Web search", "Approval, sources, providers, and credentials"),
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            SanchoTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                ) {
                    val context = LocalContext.current
                    val modelImporter = remember(context) {
                        FileModelImporter(
                            contentResolver = context.contentResolver,
                            modelsDirectory = File(context.filesDir, "models"),
                        )
                    }
                    val chatHarness = remember(context) {
                        context.sanchoModelHarness(HarnessRole.CHAT_VISION, toolsEnabled = true)
                    }
                    val localLlm = remember(context) {
                        if (BuildConfig.MOCK_PREVIEW) {
                            MockLocalLlm()
                        } else {
                            LiteRtLocalLlm(
                                context.cacheDir,
                                harness = chatHarness,
                                toolCatalog = ToolCatalog.browserHandoff,
                            )
                        }
                    }
                    val toolExecutionCoordinator = remember(context) {
                        ToolExecutionCoordinator(
                            catalog = ToolCatalog.browserHandoff,
                            executors = mapOf(
                                ToolName.OPEN_WEB_SEARCH to RoutingSearchExecutor(
                                    context = context,
                                    settings = ChatSettingsStore(context),
                                ),
                            ),
                        )
                    }
                    SanchoBackdrop {
                        SanchoChat(
                            modelImporter = modelImporter,
                            localLlm = localLlm,
                            initialModelReady = BuildConfig.MOCK_PREVIEW,
                            toolExecutionCoordinator = toolExecutionCoordinator,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SettingsPanel(
    onOpenSection: (SettingsSection) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SettingsSection.entries.forEach { section ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSection(section) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(section.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            section.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(">", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ModelsSettingsPanel(
    state: ChatUiState,
    onImportModel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
private fun ColumnScope.VoiceSettingsPanel(
    selectedVariant: MoonshineModelVariant,
    installed: Boolean,
    moonshineModelMessage: String?,
    onVariantChange: (MoonshineModelVariant) -> Unit,
    onImportMoonshineModel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
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
                Text("Voice entry points", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The Sancho widget and Quick Settings tile use Moonshine, local Gemma, and Android TextToSpeech.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Speech model", style = MaterialTheme.typography.titleSmall)
                MoonshineModelVariant.entries.forEach { variant ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onVariantChange(variant) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedVariant == variant,
                            onClick = { onVariantChange(variant) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(variant.displayName)
                            Text(
                                variant.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    if (installed) {
                        "${selectedVariant.displayName} is installed and stays on this device."
                    } else {
                        "Import the selected model folder before using voice mode."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onImportMoonshineModel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (installed) "Replace ${selectedVariant.displayName}" else "Import ${selectedVariant.displayName}")
                }
                moonshineModelMessage?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    "Choose the folder containing the selected Moonshine .ort files, streaming_config.json, and tokenizer.bin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatSettingsPanel(
    onOpenPage: (ChatSettingsPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ChatSettingsPage.entries.forEach { page ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenPage(page) }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(page.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            page.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(">", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MetricsSettingsPanel(
    metricsEnabled: Boolean,
    onMetricsEnabledChange: (Boolean) -> Unit,
    toolMetricsEnabled: Boolean,
    onToolMetricsEnabledChange: (Boolean) -> Unit,
    onClearToolMetrics: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
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
                Text("Response metrics", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Choose which local timing details appear below assistant messages.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingSwitch(
                    title = "Show response metrics",
                    description = "TTFT, first shown text, total time, and parse time",
                    checked = metricsEnabled,
                    onCheckedChange = onMetricsEnabledChange,
                )
                HorizontalDivider()
                Text("Web-search diagnostics", style = MaterialTheme.typography.titleSmall)
                SettingSwitch(
                    title = "Store web-search metrics",
                    description = "Keep timing, status, and result counts only; never prompts or URLs",
                    checked = toolMetricsEnabled,
                    onCheckedChange = onToolMetricsEnabledChange,
                )
                TextButton(onClick = onClearToolMetrics) {
                    Text("Clear stored web metrics")
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ColumnScope.WebSearchSettingsPanel(
    showWebSources: Boolean,
    onShowWebSourcesChange: (Boolean) -> Unit,
    webSearchEnabled: Boolean,
    onWebSearchEnabledChange: (Boolean) -> Unit,
    showSearchQuery: Boolean,
    onShowSearchQueryChange: (Boolean) -> Unit,
    allowMultipleSearches: Boolean,
    onAllowMultipleSearchesChange: (Boolean) -> Unit,
    webSearchMode: WebSearchMode,
    onWebSearchModeChange: (WebSearchMode) -> Unit,
    searxngEndpoint: String,
    onSearxngEndpointChange: (String) -> Unit,
    braveApiKey: String,
    onBraveApiKeyChange: (String) -> Unit,
    braveApiTestState: BraveApiTestUiState,
    onTestBraveApiKey: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState()),
    ) {
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
                Text("Web search", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Control when Sancho may search and how results are shown.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show web sources")
                        Text(
                            "Display clickable source links below web-grounded answers",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showWebSources,
                        onCheckedChange = onShowWebSourcesChange,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow web search")
                        Text(
                            "Permit model-requested searches after your approval",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = webSearchEnabled,
                        onCheckedChange = onWebSearchEnabledChange,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show web-search query")
                        Text(
                            "Review and edit the query before searching",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = showSearchQuery,
                        onCheckedChange = onShowSearchQueryChange,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow multiple searches")
                        Text(
                            "Allow up to five searches in one prompt",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allowMultipleSearches,
                        onCheckedChange = onAllowMultipleSearchesChange,
                    )
                }
                HorizontalDivider()
                Text("Web search destination", style = MaterialTheme.typography.titleSmall)
                WebSearchMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = webSearchMode == mode,
                            onClick = { onWebSearchModeChange(mode) },
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                when (mode) {
                                    WebSearchMode.VANADIUM -> "Open in Vanadium"
                                    WebSearchMode.SEARXNG -> "Search with SearXNG"
                                    WebSearchMode.BRAVE_API -> "Search with Brave API"
                                },
                            )
                            Text(
                                when (mode) {
                                    WebSearchMode.VANADIUM -> "Use Brave Search in the browser"
                                    WebSearchMode.SEARXNG -> "Use a configured SearXNG HTTPS server"
                                    WebSearchMode.BRAVE_API -> "Use a configured Brave Search API key"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (webSearchMode == WebSearchMode.SEARXNG) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = searxngEndpoint,
                        onValueChange = onSearxngEndpointChange,
                        label = { Text("SearXNG HTTPS endpoint") },
                        placeholder = { Text("https://search.example") },
                        singleLine = true,
                        supportingText = { Text("Use the server base URL, without /search or query parameters.") },
                    )
                }
                if (webSearchMode == WebSearchMode.BRAVE_API) {
                    var showBraveApiKey by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = braveApiKey,
                        onValueChange = onBraveApiKeyChange,
                        label = { Text("Brave Search API key") },
                        singleLine = true,
                        visualTransformation = if (showBraveApiKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            TextButton(onClick = { showBraveApiKey = !showBraveApiKey }) {
                                Text(if (showBraveApiKey) "Hide" else "Show")
                            }
                        },
                        supportingText = {
                            Text("Stored encrypted with Android Keystore; never commit this key.")
                        },
                    )
                    Button(
                        enabled = braveApiKey.isNotBlank() && !braveApiTestState.testing,
                        onClick = onTestBraveApiKey,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ActionGreen,
                            contentColor = Color.White,
                        ),
                    ) {
                        Text(if (braveApiTestState.testing) "Testing…" else "Test API key")
                    }
                    braveApiTestState.message?.let { message ->
                        Text(
                            message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (braveApiTestState.success) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamingAssistantText(
    text: String,
    onDisplayed: ((String, Long) -> Unit)?,
) {
    Text(text)
    if (onDisplayed != null) {
        LaunchedEffect(text) {
            withFrameNanos { frameTimeNanos ->
                onDisplayed(text, frameTimeNanos)
            }
        }
    }
}

@Composable
private fun ToolConfirmationCard(
    showQuery: Boolean,
    query: String,
    webSearchMode: WebSearchMode,
    onApprove: (String?) -> Unit,
    onDeny: () -> Unit,
) {
    var editedQuery by remember(query) { mutableStateOf(query) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Sancho wants to search the web", style = MaterialTheme.typography.titleSmall)
            Text(
                when (webSearchMode) {
                    WebSearchMode.VANADIUM ->
                        "This will open Brave Search in Vanadium. Sancho will not send your image or conversation history."
                    WebSearchMode.SEARXNG ->
                        "This will send only the query to your configured SearXNG server. Images and conversation history stay local."
                    WebSearchMode.BRAVE_API ->
                        "This will send only the query to Brave Search API. Images and conversation history stay local."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showQuery) {
                OutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = editedQuery,
                    onValueChange = { editedQuery = it },
                    label = { Text("Search query") },
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onApprove(if (showQuery) editedQuery else null) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ActionGreen,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Search")
                }
                TextButton(onClick = onDeny) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun WebSearchActivity(phase: WebSearchPhase) {
    val (label, color) = when (phase) {
        WebSearchPhase.IDLE -> return
        WebSearchPhase.WAITING_FOR_APPROVAL -> "Waiting for approval" to MaterialTheme.colorScheme.tertiary
        WebSearchPhase.SEARCHING -> "Searching the web…" to MaterialTheme.colorScheme.tertiary
        WebSearchPhase.RESULTS_RECEIVED -> "Search results received" to MaterialTheme.colorScheme.primary
        WebSearchPhase.FAILED -> "Web search failed" to MaterialTheme.colorScheme.error
        WebSearchPhase.CANCELLED -> "Web search cancelled" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape),
            )
            Text(label, style = MaterialTheme.typography.bodySmall, color = color)
        }
    }
}

@Composable
private fun WebSources(
    sources: List<WebSource>,
    onOpenSource: (WebSource) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
        shape = MaterialTheme.shapes.small,
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Sources", style = MaterialTheme.typography.labelLarge)
            sources.forEachIndexed { index, source ->
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenSource(source) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = "${index + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = source.title,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = source.url,
                                modifier = Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
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
private fun SanchoBackdrop(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        content()
    }
}

@Composable
private fun SanchoChat(
    modelImporter: ModelImporter,
    localLlm: LocalLlm,
    initialModelReady: Boolean = false,
    toolExecutionCoordinator: ToolExecutionCoordinator? = null,
) {
    val context = LocalContext.current
    val chatSettings = remember(context) { ChatSettingsStore(context) }
    var metricsEnabled by remember(chatSettings) {
        mutableStateOf(chatSettings.metricsEnabled())
    }
    var showWebSources by remember(chatSettings) {
        mutableStateOf(chatSettings.showWebSources())
    }
    var webSearchEnabled by remember(chatSettings) {
        mutableStateOf(chatSettings.webSearchEnabled())
    }
    var toolMetricsEnabled by remember(chatSettings) {
        mutableStateOf(chatSettings.toolMetricsEnabled())
    }
    var showSearchQuery by remember(chatSettings) {
        mutableStateOf(chatSettings.showSearchQuery())
    }
    var allowMultipleSearches by remember(chatSettings) {
        mutableStateOf(chatSettings.allowMultipleSearches())
    }
    var webSearchMode by remember(chatSettings) {
        mutableStateOf(chatSettings.webSearchMode())
    }
    var searxngEndpoint by remember(chatSettings) {
        mutableStateOf(chatSettings.searxngEndpoint())
    }
    var braveApiKey by remember(chatSettings) {
        mutableStateOf(chatSettings.braveApiKey())
    }
    var braveApiTestState by remember { mutableStateOf(BraveApiTestUiState()) }
    val toolMetricsStore = remember(context) {
        FileToolMetricsStore(File(context.filesDir, "observability/tool-events.jsonl"))
    }
    val toolPolicyProvider = remember(chatSettings) {
        ToolPolicyProvider {
            ToolConversationPolicy(
                maxToolCallsPerRun = if (chatSettings.allowMultipleSearches()) 5 else 1,
            )
        }
    }
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(
            modelImporter,
            localLlm,
            initialModelReady,
            toolExecutionCoordinator,
            toolPolicyProvider,
            toolMetricsRecorder = toolMetricsStore,
            toolMetricsEnabledProvider = chatSettings::toolMetricsEnabled,
            webSearchEnabledProvider = chatSettings::webSearchEnabled,
        ),
    )
    val state by chatViewModel.uiState.collectAsStateWithLifecycle()
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
    var settingsSection by remember { mutableStateOf<SettingsSection?>(null) }
    var chatSettingsPage by remember { mutableStateOf<ChatSettingsPage?>(null) }
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
    val moonshineModelImporter = remember(context) {
        MoonshineModelImporter(
            context = context,
            contentResolver = context.contentResolver,
            modelsRoot = File(context.filesDir, "moonshine"),
        )
    }
    val moonshineSettings = remember(context) { MoonshineSettingsStore(context) }
    var moonshineVariant by remember(moonshineSettings) {
        mutableStateOf(moonshineSettings.selectedVariant())
    }
    var moonshineModelInstalled by remember(moonshineModelImporter, moonshineVariant) {
        mutableStateOf(moonshineModelImporter.isInstalled(moonshineVariant))
    }
    var moonshineModelMessage by remember { mutableStateOf<String?>(null) }
    val moonshineModelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            if (uri != null) {
                scope.launch {
                    moonshineModelImporter.importDirectory(uri, moonshineVariant).onSuccess {
                        moonshineModelInstalled = true
                        moonshineModelMessage = "Moonshine model imported."
                    }.onFailure { error ->
                        moonshineModelMessage = error.message ?: "Moonshine model import failed."
                    }
                }
            }
        },
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
                            settingsSection = null
                            chatSettingsPage = null
                        } else if (settingsSection == SettingsSection.CHAT && chatSettingsPage != null) {
                            chatSettingsPage = null
                        } else if (settingsSection != null) {
                            settingsSection = null
                            chatSettingsPage = null
                        } else {
                            showSettings = false
                        }
                    },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back_24),
                        contentDescription = "Back",
                    )
                }
                Text(
                    text = when {
                        showReports -> "Reports"
                        chatSettingsPage != null -> chatSettingsPage!!.title
                        settingsSection != null -> settingsSection!!.title
                        else -> "Settings"
                    },
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
                        settingsSection = null
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
            when (settingsSection) {
                null -> SettingsPanel(
                    onOpenSection = { section ->
                        if (section == SettingsSection.REPORTS) {
                            showReports = true
                            editingReportId = null
                        } else {
                            settingsSection = section
                            chatSettingsPage = null
                        }
                    },
                )
                SettingsSection.MODELS -> ModelsSettingsPanel(
                    state = state,
                    onImportModel = { modelPicker.launch(arrayOf("*/*")) },
                )
                SettingsSection.VOICE -> VoiceSettingsPanel(
                    selectedVariant = moonshineVariant,
                    installed = moonshineModelInstalled,
                    moonshineModelMessage = moonshineModelMessage,
                    onVariantChange = { variant ->
                        moonshineVariant = variant
                        moonshineSettings.setSelectedVariant(variant)
                    },
                    onImportMoonshineModel = { moonshineModelPicker.launch(null) },
                )
                SettingsSection.CHAT -> when (chatSettingsPage) {
                    null -> ChatSettingsPanel(onOpenPage = { chatSettingsPage = it })
                    ChatSettingsPage.METRICS -> MetricsSettingsPanel(
                        metricsEnabled = metricsEnabled,
                        onMetricsEnabledChange = { enabled ->
                            metricsEnabled = enabled
                            chatSettings.setMetricsEnabled(enabled)
                        },
                        toolMetricsEnabled = toolMetricsEnabled,
                        onToolMetricsEnabledChange = { enabled ->
                            toolMetricsEnabled = enabled
                            chatSettings.setToolMetricsEnabled(enabled)
                        },
                        onClearToolMetrics = {
                            toolMetricsStore.clear()
                            chatViewModel.showStatus("Stored web metrics cleared.")
                        },
                    )
                    ChatSettingsPage.WEB_SEARCH -> WebSearchSettingsPanel(
                    showWebSources = showWebSources,
                    onShowWebSourcesChange = { enabled ->
                        showWebSources = enabled
                        chatSettings.setShowWebSources(enabled)
                    },
                    webSearchEnabled = webSearchEnabled,
                    onWebSearchEnabledChange = { enabled ->
                        webSearchEnabled = enabled
                        chatSettings.setWebSearchEnabled(enabled)
                    },
                    showSearchQuery = showSearchQuery,
                    onShowSearchQueryChange = { enabled ->
                        showSearchQuery = enabled
                        chatSettings.setShowSearchQuery(enabled)
                    },
                    allowMultipleSearches = allowMultipleSearches,
                    onAllowMultipleSearchesChange = { enabled ->
                        allowMultipleSearches = enabled
                        chatSettings.setAllowMultipleSearches(enabled)
                    },
                    webSearchMode = webSearchMode,
                    onWebSearchModeChange = { mode ->
                        webSearchMode = mode
                        chatSettings.setWebSearchMode(mode)
                    },
                    searxngEndpoint = searxngEndpoint,
                    onSearxngEndpointChange = { endpoint ->
                        searxngEndpoint = endpoint
                        chatSettings.setSearxngEndpoint(endpoint)
                    },
                    braveApiKey = braveApiKey,
                    onBraveApiKeyChange = { apiKey ->
                        braveApiKey = apiKey
                        chatSettings.setBraveApiKey(apiKey)
                        braveApiTestState = BraveApiTestUiState()
                    },
                    braveApiTestState = braveApiTestState,
                    onTestBraveApiKey = {
                        braveApiTestState = BraveApiTestUiState(testing = true)
                        scope.launch {
                            braveApiTestState = when (val result = testBraveApiKey(braveApiKey)) {
                                is BraveApiKeyTestResult.Success -> BraveApiTestUiState(
                                    message = "Connected to Brave Search (${result.resultCount} results received).",
                                    success = true,
                                )
                                is BraveApiKeyTestResult.Failure -> BraveApiTestUiState(
                                    message = result.message,
                                )
                            }
                        }
                    },
                    )
                }
                SettingsSection.REPORTS -> Unit
            }
        } else {
            HorizontalDivider(
                modifier = Modifier.offset(y = (-4).dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            )

            state.pendingToolCall?.let { call ->
                val query = (call.arguments as? OpenWebSearchArguments)?.query.orEmpty()
                ToolConfirmationCard(
                    showQuery = showSearchQuery,
                    query = query,
                    webSearchMode = webSearchMode,
                    onApprove = chatViewModel::approvePendingTool,
                    onDeny = chatViewModel::denyPendingTool,
                )
            }

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

        WebSearchActivity(state.webSearchPhase)

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = messageListState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.messages) { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (message.author == "You") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
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
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        )
                        if (metricsEnabled && message.author == "Assistant") {
                            val responseMetrics = buildList {
                                message.timeToFirstTokenMillis?.let { elapsedMillis ->
                                    add("TTFT ${formatResponseTime(elapsedMillis)}")
                                }
                                message.totalResponseTimeMillis?.let { elapsedMillis ->
                                    add("Total ${formatResponseTime(elapsedMillis)}")
                                }
                                message.timeToFirstDisplayMillis?.let { elapsedMillis ->
                                    add("Shown ${formatResponseTime(elapsedMillis)}")
                                }
                                if (message.displayUpdateCount > 0) {
                                    add("UI ${message.displayUpdateCount}")
                                }
                                message.webSearchMetrics?.let { searchMetrics ->
                                    add(
                                        "Search ${formatResponseTime(searchMetrics.elapsedMillis)} · " +
                                            "${searchMetrics.resultCount} sources",
                                    )
                                }
                                message.markdownParseTimeMillis?.let { elapsedMillis ->
                                    add("Parse ${formatResponseTime(elapsedMillis)}")
                                }
                            }.joinToString(" · ")
                            if (responseMetrics.isNotEmpty()) {
                                Text(
                                    text = responseMetrics,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (message.author == "Assistant") {
                            if (message.isStreaming) {
                                StreamingAssistantText(
                                    text = message.text,
                                    onDisplayed = if (metricsEnabled) {
                                        { displayedText, frameTimeNanos ->
                                            chatViewModel.recordAssistantDisplayed(
                                                displayedText,
                                                frameTimeNanos,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                MarkdownText(
                                    markdown = message.text,
                                    onParseTimeMillis = if (metricsEnabled) {
                                        { elapsedMillis ->
                                            chatViewModel.recordMarkdownParseTime(
                                                message.text,
                                                elapsedMillis,
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                )
                            }
                            if (showWebSources && message.sources.isNotEmpty()) {
                                WebSources(
                                    sources = message.sources,
                                    onOpenSource = { source ->
                                        if (!openWebSourceInVanadium(context, source.url)) {
                                            chatViewModel.showStatus(
                                                "Vanadium could not open this source.",
                                            )
                                        }
                                    },
                                )
                            }
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
                            cursorColor = MaterialTheme.colorScheme.primary,
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
                                    MaterialTheme.colorScheme.tertiary
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
        state.isGenerating -> Triple(MaterialTheme.colorScheme.tertiary, "Generating", "Model status: generating")
        state.isImportingModel -> Triple(MaterialTheme.colorScheme.tertiary, "Loading", "Model status: loading")
        state.isModelReady -> Triple(MaterialTheme.colorScheme.primary, "Ready", "Model status: ready")
        else -> Triple(MaterialTheme.colorScheme.error, "Unavailable", "Model status: unavailable")
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
