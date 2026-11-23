package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.IconGenUiState
import com.example.data.IconStylePreset
import com.example.data.ProjectIconStore
import com.example.model.AiApiKeyEntry
import com.example.model.AiRoutingSettings
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.ChatMessage
import com.example.model.CodeSuggestion
import com.example.model.GitHubAccountState
import com.example.model.GitHubExportUiState
import com.example.model.ProjectHierarchyBuilder
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SlateMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(
    project: AppProject,
    generationState: GenerationState,
    compileState: CompileState,
    chatMessages: List<ChatMessage>,
    chatStreamState: ChatStreamState,
    aiKeys: List<AiApiKeyEntry>,
    aiRoutingSettings: AiRoutingSettings,
    activeEngineLabel: String?,
    iconStore: ProjectIconStore,
    iconVersions: Map<String, Int>,
    iconGenState: IconGenUiState,
    onGenerateProjectIcon: (appName: String, appType: AppType, prompt: String, style: IconStylePreset) -> Unit,
    onCustomizeProjectIcon: (appName: String, baseBitmap: Bitmap, editInstruction: String, style: IconStylePreset) -> Unit,
    onImportProjectIcon: (Uri) -> Unit,
    onResetProjectIcon: () -> Unit,
    onClearIconError: () -> Unit,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    onBackClick: () -> Unit,
    onOpenAiKeys: () -> Unit,
    onSelectPoolMode: () -> Unit,
    onSelectBuiltInMode: () -> Unit,
    onSelectSpecificKey: (String) -> Unit,
    onRefineWithAi: (String) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onApplySuggestion: (CodeSuggestion) -> Unit,
    onSaveProjectChanges: (AppProject) -> Unit,
    onCompileApk: () -> Unit,
    onExportSourcesZip: ((java.io.File) -> Unit) -> Unit,
    gitHubAccountState: GitHubAccountState,
    gitHubExportState: GitHubExportUiState,
    onConnectGitHubToken: (String) -> Unit,
    onRefreshGitHubRepos: () -> Unit,
    onDisconnectGitHub: () -> Unit,
    onPushProjectToGitHub: (
        repoName: String,
        branch: String,
        commitMessage: String,
        createRepoFirst: Boolean,
        isPrivate: Boolean,
        repoDescription: String,
        ownerOverride: String?
    ) -> Unit,
    onClearGitHubExportError: () -> Unit,
    onDeleteProject: () -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var showGitHubDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    val tabs = listOf(
        "Aperçu" to Icons.Filled.PlayArrow,
        "Chat IA" to Icons.AutoMirrored.Filled.Chat,
        "Code" to Icons.Filled.Code,
        "APK" to Icons.Filled.Build,
        "Config" to Icons.Filled.Settings
    )

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = ObsidianNight,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianNight,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier.testTag("studio_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour au studio"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = project.appName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${project.language} · ${project.packageName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            onClearGitHubExportError()
                            showGitHubDialog = true
                        },
                        modifier = Modifier.testTag("studio_github_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = "Exporter vers GitHub",
                            tint = ForgeAmber
                        )
                    }
                    IconButton(
                        onClick = onOpenAiKeys,
                        modifier = Modifier.testTag("studio_ai_keys_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Clés API IA",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = {
                            selectedTab = 3
                            onCompileApk()
                        },
                        modifier = Modifier.testTag("topbar_quick_compile")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Build,
                            contentDescription = "Compiler APK",
                            tint = ElectricCyan
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MidnightSlate,
                contentColor = ElectricCyan,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = ElectricCyan
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { idx, (label, icon) ->
                    Tab(
                        selected = selectedTab == idx,
                        onClick = { selectedTab = idx },
                        text = { Text(label, maxLines = 1, fontSize = 12.sp) },
                        icon = {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                        selectedContentColor = ElectricCyan,
                        unselectedContentColor = SlateMuted,
                        modifier = Modifier.testTag("studio_tab_$idx")
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (selectedTab) {
                    0 -> LivePreviewTab(project = project)
                    1 -> ConversationalArchitectPane(
                        activeProject = project,
                        messages = chatMessages,
                        streamState = chatStreamState,
                        aiKeys = aiKeys,
                        aiRoutingSettings = aiRoutingSettings,
                        activeEngineLabel = activeEngineLabel,
                        onOpenAiKeys = onOpenAiKeys,
                        onSelectPoolMode = onSelectPoolMode,
                        onSelectBuiltInMode = onSelectBuiltInMode,
                        onSelectSpecificKey = onSelectSpecificKey,
                        onSendMessage = onSendChatMessage,
                        onClearConversation = onClearChat,
                        onApplySuggestion = onApplySuggestion,
                        onBuildApkFromConversation = null,
                        onPostMessage = onPostMessage
                    )
                    2 -> CodeAndAiTab(
                        project = project,
                        generationState = generationState,
                        onRefineWithAi = onRefineWithAi,
                        onSaveFiles = { updatedFiles ->
                            onSaveProjectChanges(project.copy(files = updatedFiles))
                        }
                    )
                    3 -> StudioCompilerTab(
                        project = project,
                        compileState = compileState,
                        onCompileClick = onCompileApk,
                        onExportSourcesZip = onExportSourcesZip,
                        onOpenGitHubExport = {
                            onClearGitHubExportError()
                            showGitHubDialog = true
                        },
                        onPostMessage = onPostMessage
                    )
                    4 -> ConfigTab(
                        project = project,
                        iconStore = iconStore,
                        iconVersion = iconVersions[project.id] ?: 0,
                        iconGenState = iconGenState,
                        onGenerateProjectIcon = onGenerateProjectIcon,
                        onCustomizeProjectIcon = onCustomizeProjectIcon,
                        onImportProjectIcon = onImportProjectIcon,
                        onResetProjectIcon = onResetProjectIcon,
                        onClearIconError = onClearIconError,
                        onSaveMetadata = onSaveProjectChanges,
                        onOpenGitHubExport = {
                            onClearGitHubExportError()
                            showGitHubDialog = true
                        },
                        onDeleteProject = onDeleteProject
                    )
                }
            }
        }
    }

    if (showGitHubDialog) {
        GitHubExportDialog(
            project = project,
            accountState = gitHubAccountState,
            exportState = gitHubExportState,
            onConnectToken = onConnectGitHubToken,
            onRefreshRepos = onRefreshGitHubRepos,
            onDisconnectAccount = onDisconnectGitHub,
            onPushToGitHub = onPushProjectToGitHub,
            onDownloadZipToDownloads = {
                onExportSourcesZip { zipFile ->
                    val saveRes = ArtifactExportHelper.saveFileToDownloads(
                        context = context,
                        sourceFile = zipFile,
                        displayName = zipFile.name,
                        mimeType = "application/zip"
                    )
                    saveRes.fold(
                        onSuccess = { dest -> onPostMessage("Archive source enregistrée : $dest") },
                        onFailure = { err -> onPostMessage(err.localizedMessage ?: "Erreur d'enregistrement ZIP.") }
                    )
                }
            },
            onShareSourcesZip = {
                onExportSourcesZip { zipFile ->
                    ArtifactExportHelper.shareArtifactFile(
                        context = context,
                        file = zipFile,
                        mimeType = "application/zip",
                        chooserTitle = "Partager les sources (${zipFile.name})"
                    )
                }
            },
            onClearExportError = onClearGitHubExportError,
            onPostMessage = onPostMessage,
            onDismiss = {
                onClearGitHubExportError()
                showGitHubDialog = false
            }
        )
    }
}

@Composable
private fun LivePreviewTab(project: AppProject) {
    var reloadNonce by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Simulateur Mobile Live",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { reloadNonce++ },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("preview_reload_button")
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Relancer")
                }
                OutlinedButton(
                    onClick = { isFullscreen = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("preview_fullscreen_button")
                ) {
                    Icon(Icons.Filled.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Plein écran")
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, ForgeBorder, RoundedCornerShape(24.dp))
                .background(Color.Black)
        ) {
            SandboxedWebView(
                html = project.playableHtml,
                reloadNonce = reloadNonce,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                color = ObsidianNight,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MidnightSlate)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${project.appName} — Test Plein Écran",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                        TextButton(onClick = { isFullscreen = false }) {
                            Text("Fermer", color = ElectricCyan)
                        }
                    }
                    SandboxedWebView(
                        html = project.playableHtml,
                        reloadNonce = reloadNonce,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SandboxedWebView(
    html: String,
    reloadNonce: Int,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                tag = ""
            }
        },
        update = { webView ->
            val key = "$reloadNonce:${html.hashCode()}"
            if (webView.tag != key) {
                webView.tag = key
                webView.loadDataWithBaseURL(
                    "https://forgeapk.local/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        },
        modifier = modifier
    )
}

@Composable
private fun CodeAndAiTab(
    project: AppProject,
    generationState: GenerationState,
    onRefineWithAi: (String) -> Unit,
    onSaveFiles: (List<com.example.model.ProjectFile>) -> Unit
) {
    var selectedFileIdx by remember(project.id, project.files.size) { mutableIntStateOf(0) }
    val safeIdx = selectedFileIdx.coerceIn(0, (project.files.size - 1).coerceAtLeast(0))
    val currentFile = project.files.getOrNull(safeIdx)

    var isEditing by remember(safeIdx, project.id) { mutableStateOf(false) }
    var editedContent by remember(safeIdx, currentFile?.content) {
        mutableStateOf(currentFile?.content ?: "")
    }
    var aiInstruction by remember { mutableStateOf("") }
    // 0 = Code Editor, 1 = Visual Tree & TreeMap Explorer
    var codeViewMode by remember(project.id) { mutableIntStateOf(0) }
    var explorerMode by remember(project.id) { mutableStateOf(HierarchyVisualMode.TREE) }
    val isAiBusy = generationState is GenerationState.Generating

    Column(modifier = Modifier.fillMaxSize()) {
        // View Switcher + Quick File Chips Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MidnightSlate)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CodeSubModeChip(
                label = "Code",
                icon = Icons.Filled.Code,
                selected = codeViewMode == 0,
                onClick = { codeViewMode = 0 },
                testTag = "code_submode_editor",
                modifier = Modifier.weight(1f)
            )
            CodeSubModeChip(
                label = "Arbre",
                icon = Icons.Filled.AccountTree,
                selected = codeViewMode == 1 && explorerMode == HierarchyVisualMode.TREE,
                onClick = {
                    isEditing = false
                    explorerMode = HierarchyVisualMode.TREE
                    codeViewMode = 1
                },
                testTag = "code_submode_tree",
                modifier = Modifier.weight(1f)
            )
            CodeSubModeChip(
                label = "TreeMap",
                icon = Icons.Filled.GridView,
                selected = codeViewMode == 1 && explorerMode == HierarchyVisualMode.TREEMAP,
                onClick = {
                    isEditing = false
                    explorerMode = HierarchyVisualMode.TREEMAP
                    codeViewMode = 1
                },
                testTag = "code_submode_treemap",
                modifier = Modifier.weight(1f)
            )
        }

        if (codeViewMode == 1) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                ProjectFileHierarchyExplorer(
                    projectName = project.appName,
                    files = project.files,
                    selectedFilePath = currentFile?.path,
                    initialMode = explorerMode,
                    openFileActionLabel = "Ouvrir le code",
                    treemapHeight = 320.dp,
                    onSelectFile = { picked ->
                        val targetIdx = project.files.indexOfFirst {
                            ProjectHierarchyBuilder.normalizePath(it.path)
                                .equals(picked.normalizedPath, ignoreCase = true)
                        }
                        if (targetIdx >= 0) {
                            selectedFileIdx = targetIdx
                            isEditing = false
                            codeViewMode = 0
                        }
                    }
                )
            }
        } else {
            // File tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                project.files.forEachIndexed { idx, file ->
                    val shortName = file.path.substringAfterLast('/')
                    FilterChip(
                        selected = idx == safeIdx,
                        onClick = {
                            isEditing = false
                            selectedFileIdx = idx
                        },
                        label = { Text(shortName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan,
                            containerColor = ForgeSurface,
                            labelColor = SlateMuted
                        ),
                        modifier = Modifier.testTag("code_file_chip_$idx")
                    )
                }
            }

            // File path & Edit/Save bar
            if (currentFile != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSlate)
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${currentFile.path} (${currentFile.language})",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isEditing) {
                        TextButton(
                            onClick = {
                                val updated = project.files.toMutableList()
                                updated[safeIdx] = currentFile.copy(content = editedContent)
                                onSaveFiles(updated)
                                isEditing = false
                            }
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Enregistrer")
                        }
                    } else {
                        TextButton(onClick = { isEditing = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Éditer")
                        }
                    }
                }
            }

            // Code area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF060A14))
            ) {
                if (currentFile != null) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { editedContent = it },
                            textStyle = TextStyle(
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                                .padding(14.dp)
                        ) {
                            Text(
                                text = currentFile.content,
                                fontFamily = MonoFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }
                }
            }
        }

        // AI Refinement Bar
        Surface(
            color = MidnightSlate,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = aiInstruction,
                    onValueChange = { aiInstruction = it },
                    placeholder = { Text("Demander à l'IA : ajouter un niveau, convertir en C++...") },
                    singleLine = true,
                    enabled = !isAiBusy,
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_refine_input")
                )
                Button(
                    onClick = {
                        val req = aiInstruction.trim()
                        if (req.isNotBlank()) {
                            aiInstruction = ""
                            onRefineWithAi(req)
                        }
                    },
                    enabled = !isAiBusy && aiInstruction.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("ai_refine_send_button")
                ) {
                    if (isAiBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ObsidianNight,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Envoyer à l'IA"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeSubModeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) ElectricCyan.copy(alpha = 0.18f) else ForgeSurface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = if (selected) ElectricCyan.copy(alpha = 0.7f) else ForgeBorder,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ElectricCyan else SlateMuted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) ElectricCyan else SlateMuted,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConfigTab(
    project: AppProject,
    iconStore: ProjectIconStore,
    iconVersion: Int,
    iconGenState: IconGenUiState,
    onGenerateProjectIcon: (appName: String, appType: AppType, prompt: String, style: IconStylePreset) -> Unit,
    onCustomizeProjectIcon: (appName: String, baseBitmap: Bitmap, editInstruction: String, style: IconStylePreset) -> Unit,
    onImportProjectIcon: (Uri) -> Unit,
    onResetProjectIcon: () -> Unit,
    onClearIconError: () -> Unit,
    onSaveMetadata: (AppProject) -> Unit,
    onOpenGitHubExport: () -> Unit,
    onDeleteProject: () -> Unit
) {
    var appName by remember(project.id, project.appName) { mutableStateOf(project.appName) }
    var packageName by remember(project.id, project.packageName) { mutableStateOf(project.packageName) }
    var versionName by remember(project.id, project.versionName) { mutableStateOf(project.versionName) }
    var accentHex by remember(project.id, project.accentHex) { mutableStateOf(project.accentHex) }
    var confirmDelete by remember { mutableStateOf(false) }
    val appType = remember(project.appType) { AppType.fromRaw(project.appType) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Icône & Identité APK",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        AppIconCustomizerCard(
            iconStore = iconStore,
            projectKey = project.id,
            iconVersion = iconVersion,
            appName = appName,
            appType = appType,
            iconGenState = iconGenState,
            onGenerateAiIcon = { desc, style ->
                onGenerateProjectIcon(appName, appType, desc, style)
            },
            onCustomizeAiIcon = { baseBmp, edit, style ->
                onCustomizeProjectIcon(appName, baseBmp, edit, style)
            },
            onImportPhotoUri = onImportProjectIcon,
            onResetToPlaceholder = onResetProjectIcon,
            onClearError = onClearIconError
        )

        OutlinedTextField(
            value = appName,
            onValueChange = { appName = it },
            label = { Text("Nom de l'application") },
            singleLine = true,
            colors = forgeTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = packageName,
            onValueChange = { packageName = it },
            label = { Text("Package ID Android") },
            singleLine = true,
            colors = forgeTextFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = versionName,
                onValueChange = { versionName = it },
                label = { Text("Version") },
                singleLine = true,
                colors = forgeTextFieldColors(),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = accentHex,
                onValueChange = { accentHex = it },
                label = { Text("Couleur (#Hex)") },
                singleLine = true,
                colors = forgeTextFieldColors(),
                modifier = Modifier.weight(1f)
            )
        }

        Button(
            onClick = {
                onSaveMetadata(
                    project.copy(
                        appName = appName,
                        packageName = packageName,
                        versionName = versionName,
                        accentHex = accentHex
                    )
                )
            },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = ObsidianNight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("save_config_button")
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Sauvegarder la configuration", fontWeight = FontWeight.Bold)
        }

        if (project.summary.isNotBlank()) {
            Surface(
                color = ForgeSurface,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Architecture générée par l'IA",
                        style = MaterialTheme.typography.titleSmall,
                        color = ElectricCyan
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = project.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onOpenGitHubExport,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("config_github_export_button")
        ) {
            Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = ForgeAmber)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Publier le code sur GitHub", color = ForgeAmber)
        }

        OutlinedButton(
            onClick = { confirmDelete = true },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("delete_project_button")
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, tint = CrimsonError)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Supprimer ce projet", color = CrimsonError)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = ForgeSurface,
            title = { Text("Supprimer ${project.appName} ?") },
            text = { Text("Cette action supprimera définitivement le code source et les métadonnées de ce projet.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDeleteProject()
                    }
                ) {
                    Text("Supprimer", color = CrimsonError)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Annuler", color = ElectricCyan)
                }
            }
        )
    }
}
