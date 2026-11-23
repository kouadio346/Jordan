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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AiApiKeyEntry
import com.example.model.AiProvider
import com.example.model.AiRoutingMode
import com.example.model.AiRoutingSettings
import com.example.model.AiSpecialty
import com.example.model.KeyHealthStatus
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBuild
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SlateMuted

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiKeysManagerScreen(
    keys: List<AiApiKeyEntry>,
    routingSettings: AiRoutingSettings,
    testingKeyIds: Set<String>,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    onBackClick: () -> Unit,
    onSaveKey: (
        existingId: String?,
        label: String,
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        customBaseUrl: String,
        specialtyOverride: AiSpecialty?
    ) -> Unit,
    onToggleKeyEnabled: (String) -> Unit,
    onMoveKeyPriority: (String, Int) -> Unit,
    onDeleteKey: (String) -> Unit,
    onTestKey: (String) -> Unit,
    onTestAllKeys: () -> Unit,
    onSetRoutingMode: (AiRoutingMode, String?) -> Unit,
    onSetFallbackToBuiltIn: (Boolean) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var dialogOpen by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AiApiKeyEntry?>(null) }
    var presetProvider by remember { mutableStateOf(AiProvider.OPENAI) }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    val enabledCount = keys.count { it.isEnabled && it.apiKey.isNotBlank() }

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
                        modifier = Modifier.testTag("ai_keys_back_button")
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
                            text = "Coffre Clés API IA",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "$enabledCount clé(s) active(s) · Chiffrement Keystore",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                },
                actions = {
                    if (keys.isNotEmpty()) {
                        TextButton(
                            onClick = onTestAllKeys,
                            enabled = testingKeyIds.isEmpty(),
                            modifier = Modifier.testTag("test_all_keys_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.NetworkCheck,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tout tester", color = ElectricCyan)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingEntry = null
                    presetProvider = AiProvider.OPENAI
                    dialogOpen = true
                },
                containerColor = ElectricCyan,
                contentColor = ObsidianNight,
                modifier = Modifier.testTag("fab_add_ai_key")
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Ajouter une clé API IA")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxSize()
            ) {
                // Security & Built-in engine banner
                item {
                    Surface(
                        color = ForgeSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElectricCyan.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Security,
                                    contentDescription = null,
                                    tint = ElectricCyan
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Multi-IA & Failover Automatique",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Connectez plusieurs clés (OpenAI, Claude, DeepSeek, Groq, Mistral, OpenRouter, Gemini ou Custom). Vos clés restent chiffrées sur l'appareil (AES-256 Android Keystore). Si une clé atteint son quota, ForgeAPK bascule automatiquement sur la suivante.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                            }
                        }
                    }
                }

                // Routing Strategy Selector
                item {
                    Surface(
                        color = MidnightSlate,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ForgeBorder, RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.SettingsInputComponent,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mode de routage pour le codage",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            AiRoutingMode.entries.forEach { mode ->
                                val selected = routingSettings.mode == mode
                                Surface(
                                    color = if (selected) ElectricCyan.copy(alpha = 0.12f) else ForgeSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                            1.dp,
                                            if (selected) ElectricCyan else ForgeBorder,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            onSetRoutingMode(mode, routingSettings.selectedKeyId)
                                        }
                                        .testTag("routing_mode_${mode.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = if (selected) ElectricCyan else SlateMuted,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = mode.title,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = mode.subtitle,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = SlateMuted
                                            )
                                        }
                                    }
                                }
                            }

                            if (routingSettings.mode != AiRoutingMode.BUILTIN_FIREBASE) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Repli auto sur Firebase IA (0 clé)",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Prend le relais si toutes vos clés échouent",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = SlateMuted
                                        )
                                    }
                                    Switch(
                                        checked = routingSettings.fallbackToBuiltIn,
                                        onCheckedChange = onSetFallbackToBuiltIn,
                                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianNight, checkedTrackColor = EmeraldBuild),
                                        modifier = Modifier.testTag("fallback_builtin_switch")
                                    )
                                }
                            }
                        }
                    }
                }

                // Quick Add Provider chips
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Connecter un fournisseur rapidement",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricCyan
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AiProvider.entries.forEach { provider ->
                                val accent = parseHexColor(provider.accentHex, ElectricCyan)
                                Surface(
                                    color = ForgeSurface,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                        .clickable {
                                            editingEntry = null
                                            presetProvider = provider
                                            dialogOpen = true
                                        }
                                        .testTag("quick_add_provider_${provider.name}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(accent)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "+ ${provider.shortName}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Connected Keys Section Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Clés connectées (${keys.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (keys.size > 1 && routingSettings.mode == AiRoutingMode.AUTO_FAILOVER_POOL) {
                            Text(
                                text = "Ordre = priorité de bascule",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateMuted
                            )
                        }
                    }
                }

                if (keys.isEmpty()) {
                    item {
                        Surface(
                            color = ForgeSurface,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ForgeBorder, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "Aucune clé API externe enregistrée",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Le moteur Firebase IA intégré reste prêt gratuitement. Ajoutez une ou plusieurs clés (Groq, DeepSeek, OpenAI, Claude, Gemini, Mistral, OpenRouter) pour débloquer d'autres modèles et le failover automatique.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        editingEntry = null
                                        presetProvider = AiProvider.OPENAI
                                        dialogOpen = true
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianNight
                                    ),
                                    modifier = Modifier.testTag("empty_add_key_button")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Connecter une clé API", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    itemsIndexed(keys, key = { _, item -> item.id }) { index, entry ->
                        ConnectedKeyCard(
                            entry = entry,
                            priorityRank = index + 1,
                            totalCount = keys.size,
                            isSelectedPrimary = routingSettings.selectedKeyId == entry.id &&
                                routingSettings.mode != AiRoutingMode.BUILTIN_FIREBASE,
                            isTesting = entry.id in testingKeyIds,
                            onToggleEnabled = { onToggleKeyEnabled(entry.id) },
                            onMoveUp = { onMoveKeyPriority(entry.id, -1) },
                            onMoveDown = { onMoveKeyPriority(entry.id, 1) },
                            onSelectPrimary = {
                                onSetRoutingMode(
                                    if (routingSettings.mode == AiRoutingMode.BUILTIN_FIREBASE) {
                                        AiRoutingMode.SINGLE_KEY
                                    } else {
                                        routingSettings.mode
                                    },
                                    entry.id
                                )
                            },
                            onTest = { onTestKey(entry.id) },
                            onEdit = {
                                editingEntry = entry
                                presetProvider = entry.provider
                                dialogOpen = true
                            },
                            onDelete = { onDeleteKey(entry.id) }
                        )
                    }
                }
            }
        }
    }

    if (dialogOpen) {
        AiKeyEditorDialog(
            initialEntry = editingEntry,
            initialProvider = presetProvider,
            onDismiss = { dialogOpen = false },
            onSave = { existingId, label, provider, apiKey, modelId, customUrl, specOverride ->
                onSaveKey(existingId, label, provider, apiKey, modelId, customUrl, specOverride)
                dialogOpen = false
            }
        )
    }
}

@Composable
private fun ConnectedKeyCard(
    entry: AiApiKeyEntry,
    priorityRank: Int,
    totalCount: Int,
    isSelectedPrimary: Boolean,
    isTesting: Boolean,
    onToggleEnabled: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onSelectPrimary: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var revealKey by remember(entry.id) { mutableStateOf(false) }
    val accent = parseHexColor(entry.provider.accentHex, ElectricCyan)
    val healthColor = when (entry.status) {
        KeyHealthStatus.CONNECTED -> EmeraldBuild
        KeyHealthStatus.RATE_LIMITED -> ForgeAmber
        KeyHealthStatus.ERROR -> CrimsonError
        KeyHealthStatus.UNTESTED -> SlateMuted
    }

    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelectedPrimary) 1.5.dp else 1.dp,
                color = if (isSelectedPrimary) accent else ForgeBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .testTag("key_card_${entry.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "#$priorityRank · ${entry.provider.shortName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = entry.displayTitle(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = entry.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ObsidianNight,
                        checkedTrackColor = accent
                    ),
                    modifier = Modifier.testTag("key_switch_${entry.id}")
                )
            }

            // Key masked display + Model + Health
            Surface(
                color = ObsidianNight,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ForgeBorder, RoundedCornerShape(10.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (revealKey) entry.apiKey else entry.maskedKey(),
                            fontFamily = MonoFontFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val specialty = entry.bestSpecialty()
                        Text(
                            text = "Modèle : ${entry.effectiveModel()} · Spécialité : ${specialty.shortTitle}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SlateMuted
                        )
                    }
                    IconButton(
                        onClick = { revealKey = !revealKey },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (revealKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (revealKey) "Masquer la clé" else "Afficher la clé",
                            tint = SlateMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Status + usage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = buildString {
                    append(entry.status.label)
                    if (entry.status == KeyHealthStatus.CONNECTED && entry.lastLatencyMs != null) {
                        append(" · ${entry.lastLatencyMs} ms")
                    } else if (!entry.lastError.isNullOrBlank() && entry.status != KeyHealthStatus.CONNECTED) {
                        append(" · ${entry.lastError}")
                    }
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = healthColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${entry.usageCount} req.",
                    style = MaterialTheme.typography.labelSmall,
                    color = SlateMuted
                )
            }

            // Action row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onTest,
                    enabled = !isTesting,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = ElectricCyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.NetworkCheck,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Tester", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                }

                FilterChip(
                    selected = isSelectedPrimary,
                    onClick = onSelectPrimary,
                    label = {
                        Text(
                            text = if (isSelectedPrimary) "Prioritaire" else "Prioriser",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent.copy(alpha = 0.2f),
                        selectedLabelColor = accent
                    )
                )

                Spacer(modifier = Modifier.weight(1f))

                if (totalCount > 1) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = priorityRank > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Monter la priorité",
                            tint = if (priorityRank > 1) SlateMuted else ForgeBorder,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onMoveDown,
                        enabled = priorityRank < totalCount,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Baisser la priorité",
                            tint = if (priorityRank < totalCount) SlateMuted else ForgeBorder,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Modifier la clé",
                        tint = SlateMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Supprimer la clé",
                        tint = CrimsonError,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiKeyEditorDialog(
    initialEntry: AiApiKeyEntry?,
    initialProvider: AiProvider,
    onDismiss: () -> Unit,
    onSave: (
        existingId: String?,
        label: String,
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        customBaseUrl: String,
        specialtyOverride: AiSpecialty?
    ) -> Unit
) {
    var provider by remember(initialEntry, initialProvider) {
        mutableStateOf(initialEntry?.provider ?: initialProvider)
    }
    var label by remember(initialEntry) { mutableStateOf(initialEntry?.label ?: "") }
    var apiKey by remember(initialEntry) { mutableStateOf(initialEntry?.apiKey ?: "") }
    var modelId by remember(initialEntry, initialProvider) {
        mutableStateOf(initialEntry?.modelId ?: (initialEntry?.provider ?: initialProvider).defaultModel)
    }
    var customBaseUrl by remember(initialEntry) { mutableStateOf(initialEntry?.customBaseUrl ?: "") }
    var specialtyOverride by remember(initialEntry) { mutableStateOf(initialEntry?.specialtyOverride) }
    var showKey by remember { mutableStateOf(false) }

    val canSave = apiKey.isNotBlank() &&
        (provider != AiProvider.CUSTOM_OPENAI || customBaseUrl.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ForgeSurface,
        title = {
            Text(
                text = if (initialEntry == null) "Connecter une clé API IA" else "Modifier la clé IA",
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Fournisseur IA",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AiProvider.entries.forEach { p ->
                        val selected = p == provider
                        val accent = parseHexColor(p.accentHex, ElectricCyan)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                if (provider != p) {
                                    provider = p
                                    if (initialEntry == null) {
                                        modelId = p.defaultModel
                                    }
                                }
                            },
                            label = { Text(p.shortName, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = accent
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Nom personnalisé (optionnel)") },
                    placeholder = { Text("Ex: ${provider.shortName} Perso #1") },
                    singleLine = true,
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_key_label_input")
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("Clé API *") },
                    placeholder = { Text(provider.keyPlaceholder) },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(
                                imageVector = if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (showKey) "Masquer" else "Afficher"
                            )
                        }
                    },
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_api_key_input")
                )

                OutlinedTextField(
                    value = modelId,
                    onValueChange = { modelId = it },
                    label = { Text("Identifiant du modèle") },
                    placeholder = { Text(provider.defaultModel) },
                    singleLine = true,
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_model_input")
                )

                if (provider.suggestedModels.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        provider.suggestedModels.forEach { preset ->
                            Surface(
                                color = MidnightSlate,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        1.dp,
                                        if (modelId == preset) ElectricCyan else ForgeBorder,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { modelId = preset }
                            ) {
                                Text(
                                    text = preset,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (modelId == preset) ElectricCyan else SlateMuted,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                if (provider == AiProvider.CUSTOM_OPENAI || customBaseUrl.isNotBlank()) {
                    OutlinedTextField(
                        value = customBaseUrl,
                        onValueChange = { customBaseUrl = it },
                        label = {
                            Text(if (provider == AiProvider.CUSTOM_OPENAI) "URL Endpoint *" else "URL personnalisée (optionnel)")
                        },
                        placeholder = { Text("https://api.together.xyz/v1/chat/completions") },
                        singleLine = true,
                        colors = forgeTextFieldColors(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_custom_url_input")
                    )
                }
                Text(
                    text = "Spécialité en équipe Multi-IA",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = specialtyOverride == null,
                        onClick = { specialtyOverride = null },
                        label = { Text("Auto", style = MaterialTheme.typography.labelSmall) }
                    )
                    AiSpecialty.entries.forEach { spec ->
                        FilterChip(
                            selected = specialtyOverride == spec,
                            onClick = { specialtyOverride = spec },
                            label = { Text(spec.shortTitle, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialEntry?.id,
                        label,
                        provider,
                        apiKey,
                        modelId,
                        customBaseUrl,
                        specialtyOverride
                    )
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianNight
                ),
                modifier = Modifier.testTag("dialog_save_key_button")
            ) {
                Text("Enregistrer & Tester", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler", color = SlateMuted)
            }
        }
    )
}

/**
 * Compact engine & connected API key switcher bar used across Create, Chat IA, and Studio screens.
 */
@Composable
fun ActiveAiEngineBar(
    keys: List<AiApiKeyEntry>,
    routingSettings: AiRoutingSettings,
    activeEngineLabel: String?,
    onSelectPoolMode: () -> Unit,
    onSelectBuiltInMode: () -> Unit,
    onSelectSpecificKey: (String) -> Unit,
    onOpenKeysManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val enabledKeys = keys.filter { it.isEnabled && it.apiKey.isNotBlank() }

    Surface(
        color = MidnightSlate,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Key,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val summaryText = when {
                        !activeEngineLabel.isNullOrBlank() -> "Moteur actif : $activeEngineLabel"
                        routingSettings.mode == AiRoutingMode.BUILTIN_FIREBASE || enabledKeys.isEmpty() ->
                            "Moteur : Firebase IA (gemini-3.8-flash)"
                        routingSettings.mode == AiRoutingMode.AUTO_FAILOVER_POOL ->
                            "Pool Multi-Clés (${enabledKeys.size} active(s))"
                        else -> {
                            val selected = enabledKeys.firstOrNull { it.id == routingSettings.selectedKeyId }
                                ?: enabledKeys.first()
                            "Clé : ${selected.displayTitle()}"
                        }
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = ElectricCyan.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onOpenKeysManager)
                        .testTag("manage_ai_keys_chip")
                ) {
                    Text(
                        text = if (enabledKeys.isEmpty()) "+ Clés API" else "Gérer (${enabledKeys.size})",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (enabledKeys.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (enabledKeys.size > 1) {
                        val poolSelected = routingSettings.mode == AiRoutingMode.AUTO_FAILOVER_POOL
                        EnginePillChip(
                            label = "Pool Auto (${enabledKeys.size})",
                            selected = poolSelected,
                            accent = ElectricCyan,
                            onClick = onSelectPoolMode
                        )
                    }
                    enabledKeys.forEach { keyEntry ->
                        val isKeySelected = routingSettings.mode == AiRoutingMode.SINGLE_KEY &&
                            routingSettings.selectedKeyId == keyEntry.id
                        val accent = parseHexColor(keyEntry.provider.accentHex, ElectricCyan)
                        EnginePillChip(
                            label = keyEntry.displayTitle(),
                            selected = isKeySelected,
                            accent = accent,
                            onClick = { onSelectSpecificKey(keyEntry.id) }
                        )
                    }
                    EnginePillChip(
                        label = "Firebase IA",
                        selected = routingSettings.mode == AiRoutingMode.BUILTIN_FIREBASE,
                        accent = EmeraldBuild,
                        onClick = onSelectBuiltInMode
                    )
                }
            }
        }
    }
}

@Composable
private fun EnginePillChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) accent.copy(alpha = 0.22f) else ForgeSurface,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                1.dp,
                if (selected) accent else ForgeBorder,
                RoundedCornerShape(999.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) accent else SlateMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}
