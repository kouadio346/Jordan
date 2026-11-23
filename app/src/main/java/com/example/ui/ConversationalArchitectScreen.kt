package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AiApiKeyEntry
import com.example.model.AiRoutingSettings
import com.example.model.AppProject
import com.example.model.ArchitectStarterPrompts
import com.example.model.ChatMessage
import com.example.model.CodeSuggestion
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBuild
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SlateMuted

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationalArchitectScreen(
    messages: List<ChatMessage>,
    streamState: ChatStreamState,
    aiKeys: List<AiApiKeyEntry>,
    aiRoutingSettings: AiRoutingSettings,
    activeEngineLabel: String?,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    onBackClick: () -> Unit,
    onOpenAiKeys: () -> Unit,
    onSelectPoolMode: () -> Unit,
    onSelectBuiltInMode: () -> Unit,
    onSelectSpecificKey: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearConversation: () -> Unit,
    onApplySuggestion: (CodeSuggestion) -> Unit,
    onBuildApkFromConversation: () -> Unit,
    onLaunchPlanInSwarm: ((String) -> Unit)? = null,
    onPostMessage: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

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
                        modifier = Modifier.testTag("architect_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                title = {
                    Column {
                        Text(
                            text = "Architecte IA Conversationnel",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Suggestions de code itératives Android & Jeux",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenAiKeys,
                        modifier = Modifier.testTag("architect_ai_keys_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Clés API IA",
                            tint = ElectricCyan
                        )
                    }
                    if (messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearConversation,
                            modifier = Modifier.testTag("architect_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Nouvelle conversation",
                                tint = SlateMuted
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            ConversationalArchitectPane(
                activeProject = null,
                messages = messages,
                streamState = streamState,
                aiKeys = aiKeys,
                aiRoutingSettings = aiRoutingSettings,
                activeEngineLabel = activeEngineLabel,
                onOpenAiKeys = onOpenAiKeys,
                onSelectPoolMode = onSelectPoolMode,
                onSelectBuiltInMode = onSelectBuiltInMode,
                onSelectSpecificKey = onSelectSpecificKey,
                onSendMessage = onSendMessage,
                onClearConversation = onClearConversation,
                onApplySuggestion = onApplySuggestion,
                onBuildApkFromConversation = onBuildApkFromConversation,
                onLaunchPlanInSwarm = onLaunchPlanInSwarm,
                onPostMessage = onPostMessage
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConversationalArchitectPane(
    activeProject: AppProject?,
    messages: List<ChatMessage>,
    streamState: ChatStreamState,
    aiKeys: List<AiApiKeyEntry>,
    aiRoutingSettings: AiRoutingSettings,
    activeEngineLabel: String?,
    onOpenAiKeys: () -> Unit,
    onSelectPoolMode: () -> Unit,
    onSelectBuiltInMode: () -> Unit,
    onSelectSpecificKey: (String) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearConversation: () -> Unit,
    onApplySuggestion: (CodeSuggestion) -> Unit,
    onBuildApkFromConversation: (() -> Unit)?,
    onLaunchPlanInSwarm: ((String) -> Unit)? = null,
    onPostMessage: (String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isStreaming = streamState is ChatStreamState.Streaming
    val hasSuggestions = messages.any { it.suggestions.isNotEmpty() }

    LaunchedEffect(messages.size, streamState) {
        val total = messages.size + (if (isStreaming) 1 else 0)
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ActiveAiEngineBar(
            keys = aiKeys,
            routingSettings = aiRoutingSettings,
            activeEngineLabel = activeEngineLabel,
            onSelectPoolMode = onSelectPoolMode,
            onSelectBuiltInMode = onSelectBuiltInMode,
            onSelectSpecificKey = onSelectSpecificKey,
            onOpenKeysManager = onOpenAiKeys,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
        // Optional context header when embedded in StudioScreen or when suggestions exist
        if (activeProject != null || hasSuggestions) {
            Surface(
                color = MidnightSlate,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ForgeBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (activeProject != null) {
                            "Contexte : ${activeProject.appName} (${activeProject.language})"
                        } else {
                            "Prêt à transformer la conversation en projet APK"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (activeProject == null && (hasSuggestions || messages.isNotEmpty())) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (onLaunchPlanInSwarm != null) {
                                OutlinedButton(
                                    onClick = {
                                        val planDigest = messages.takeLast(6)
                                            .joinToString("\n") { "${it.role}: ${it.text.take(350)}" }
                                        onLaunchPlanInSwarm(planDigest)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("architect_send_to_swarm_button")
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Équipe IA", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                                }
                            }
                            if (hasSuggestions && onBuildApkFromConversation != null) {
                                Button(
                                    onClick = onBuildApkFromConversation,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldBuild,
                                        contentColor = ObsidianNight
                                    ),
                                    modifier = Modifier.testTag("architect_create_apk_button")
                                ) {
                                    Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Créer APK", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else if (activeProject != null && messages.isNotEmpty()) {
                        IconButton(
                            onClick = onClearConversation,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteSweep,
                                contentDescription = "Effacer la discussion",
                                tint = SlateMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Messages area
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty() && !isStreaming) {
                item {
                    ArchitectWelcomeEmptyState(
                        activeProject = activeProject,
                        onSelectStarter = { starterPrompt ->
                            onSendMessage(starterPrompt)
                        }
                    )
                }
            } else {
                items(messages, key = { it.id.ifBlank { "${it.role}_${it.text.hashCode()}" } }) { msg ->
                    if (msg.role == "USER") {
                        UserChatBubble(text = msg.text)
                    } else {
                        ModelChatBubble(
                            message = msg,
                            hasActiveProject = activeProject != null,
                            onApplySuggestion = onApplySuggestion,
                            onFollowUpClick = { followUp ->
                                if (!isStreaming) onSendMessage(followUp)
                            },
                            onCopied = { onPostMessage("Code copié dans le presse-papiers.") }
                        )
                    }
                }
            }

            if (streamState is ChatStreamState.Streaming) {
                item(key = "streaming_bubble") {
                    StreamingAiBubble(partialText = streamState.partialText)
                }
            }

            if (streamState is ChatStreamState.Error) {
                item(key = "error_bubble") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = streamState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
        }

        // Composer Bar
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
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = {
                        Text(
                            if (activeProject == null) {
                                "Décrivez votre idée d'app Android..."
                            } else {
                                "Demandez une évolution ou un écran..."
                            }
                        )
                    },
                    minLines = 1,
                    maxLines = 4,
                    enabled = !isStreaming,
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("architect_chat_input")
                )
                Button(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotBlank()) {
                            inputText = ""
                            onSendMessage(trimmed)
                        }
                    },
                    enabled = !isStreaming && inputText.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("architect_chat_send_button")
                ) {
                    if (isStreaming) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = ObsidianNight,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Envoyer"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ArchitectWelcomeEmptyState(
    activeProject: AppProject?,
    onSelectStarter: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(20.dp))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElectricCyan.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (activeProject != null) {
                                "Itérez sur ${activeProject.appName}"
                            } else {
                                "Concevez votre projet par dialogue"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "L'IA propose du code prêt à intégrer étape par étape",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }
            }
        }

        Text(
            text = if (activeProject != null) "Suggestions rapides :" else "Idées pour démarrer :",
            style = MaterialTheme.typography.labelLarge,
            color = ElectricCyan
        )

        val starters = if (activeProject != null) {
            listOf(
                "Ajouter un nouvel écran" to "Propose un nouvel écran Jetpack Compose / UI complémentaire pour ${activeProject.appName} avec son ViewModel.",
                "Persistance & Modèles" to "Génère les modèles de données et un Repository propre pour sauvegarder l'état de ${activeProject.appName}.",
                "Animations & Polish M3" to "Ajoute des transitions et animations Material 3 fluides pour améliorer l'expérience utilisateur.",
                "Tests Unitaires" to "Écris une suite de tests unitaires JUnit pour vérifier la logique métier de ${activeProject.appName}."
            )
        } else {
            ArchitectStarterPrompts.prompts
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            starters.forEach { (title, prompt) ->
                Surface(
                    color = ForgeSurface,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
                        .clickable { onSelectStarter(prompt) }
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = prompt,
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UserChatBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            color = ElectricCyan.copy(alpha = 0.14f),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .border(
                    1.dp,
                    ElectricCyan.copy(alpha = 0.45f),
                    RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                )
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModelChatBubble(
    message: ChatMessage,
    hasActiveProject: Boolean,
    onApplySuggestion: (CodeSuggestion) -> Unit,
    onFollowUpClick: (String) -> Unit,
    onCopied: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    ForgeBorder,
                    RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
                )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ForgeAPK Architect",
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan
                    )
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                message.suggestions.forEachIndexed { idx, suggestion ->
                    CodeSuggestionCard(
                        suggestion = suggestion,
                        hasActiveProject = hasActiveProject,
                        onCopy = onCopied,
                        onApply = { onApplySuggestion(suggestion) },
                        testTagSuffix = "${message.id}_$idx"
                    )
                }
            }
        }

        if (message.followUps.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                message.followUps.forEachIndexed { idx, chipText ->
                    Surface(
                        color = MidnightSlate,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, ForgeAmber.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                            .clickable { onFollowUpClick(chipText) }
                            .testTag("followup_chip_$idx")
                    ) {
                        Text(
                            text = "↳ $chipText",
                            style = MaterialTheme.typography.labelMedium,
                            color = ForgeAmber,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeSuggestionCard(
    suggestion: CodeSuggestion,
    hasActiveProject: Boolean,
    onCopy: () -> Unit,
    onApply: () -> Unit,
    testTagSuffix: String
) {
    val clipboard = LocalClipboardManager.current
    var copiedRecently by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFF060A14),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MidnightSlate)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = suggestion.title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${suggestion.filePath} · ${suggestion.language}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(suggestion.code))
                        copiedRecently = true
                        onCopy()
                    },
                    modifier = Modifier.testTag("copy_suggestion_$testTagSuffix")
                ) {
                    Icon(
                        imageVector = if (copiedRecently) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copier le code",
                        tint = if (copiedRecently) EmeraldBuild else SlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = suggestion.code,
                    fontFamily = MonoFontFamily,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                    color = Color(0xFFE2E8F0)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MidnightSlate.copy(alpha = 0.65f))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onApply,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("apply_suggestion_$testTagSuffix")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddCircleOutline,
                        contentDescription = null,
                        tint = EmeraldBuild,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (hasActiveProject) "Appliquer au projet" else "Créer projet avec ce code",
                        color = EmeraldBuild,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingAiBubble(partialText: String) {
    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                ElectricCyan.copy(alpha = 0.4f),
                RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
            )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = ElectricCyan,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Génération en direct...",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
            }
            Text(
                text = partialText.ifBlank { "Analyse de votre idée Android..." },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
