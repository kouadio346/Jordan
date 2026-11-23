package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.AiApiKeyEntry
import com.example.model.AiSpecialty
import com.example.model.AppType
import com.example.model.ProjectFile
import com.example.model.ProjectFileStats
import com.example.model.SwarmExecutionState
import com.example.model.SwarmPhase
import com.example.model.SwarmTaskAssignment
import com.example.model.SwarmTaskStatus
import com.example.model.TargetLanguage
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SwarmTeamScreen(
    initialPlanText: String?,
    aiKeys: List<AiApiKeyEntry>,
    swarmState: SwarmExecutionState,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    onBackClick: () -> Unit,
    onOpenAiKeys: () -> Unit,
    onSplitPlanOnly: (plan: String, appType: AppType, language: TargetLanguage) -> Unit,
    onSplitAndRunTogether: (plan: String, appType: AppType, language: TargetLanguage) -> Unit,
    onLaunchPreparedPlan: () -> Unit,
    onResetSwarm: () -> Unit,
    onOpenCreatedProject: (String) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    var planText by remember(initialPlanText) {
        mutableStateOf(
            initialPlanText?.takeIf { it.isNotBlank() }
                ?: swarmState.planPrompt
        )
    }
    var selectedType by remember { mutableStateOf(swarmState.appType) }
    var selectedLang by remember { mutableStateOf(swarmState.targetLanguage) }
    var previewedFileStats by remember { mutableStateOf<ProjectFileStats?>(null) }

    LaunchedEffect(statusMessage) {
        if (!statusMessage.isNullOrBlank()) {
            snackbarHostState.showSnackbar(statusMessage)
            onClearStatusMessage()
        }
    }

    val enabledKeys = aiKeys.filter { it.isEnabled && it.apiKey.isNotBlank() }
    val isBusy = swarmState.phase == SwarmPhase.PLANNING ||
        swarmState.phase == SwarmPhase.EXECUTING_PARALLEL ||
        swarmState.phase == SwarmPhase.INTEGRATING

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
                        modifier = Modifier.testTag("swarm_back_button")
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
                            text = "Équipe Multi-IA Collaborative",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (enabledKeys.isEmpty()) {
                                "Firebase IA multi-agents · Connectez vos clés pour spécialiser"
                            } else {
                                "${enabledKeys.size} clé(s) connectée(s) + Firebase IA en équipe"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenAiKeys,
                        enabled = !isBusy,
                        modifier = Modifier.testTag("swarm_open_keys_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Gérer les clés API IA",
                            tint = ElectricCyan
                        )
                    }
                    if (swarmState.phase != SwarmPhase.IDLE && !isBusy) {
                        IconButton(
                            onClick = onResetSwarm,
                            modifier = Modifier.testTag("swarm_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Nouveau plan",
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
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .widthIn(max = 780.dp)
                    .fillMaxSize()
            ) {
                // Roster banner showing how connected keys match their best specialty
                item {
                    Surface(
                        color = ForgeSurface,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Groups,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Spécialistes disponibles",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isBusy, onClick = onOpenAiKeys)
                                ) {
                                    Text(
                                        text = "+ Clés IA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ElectricCyan,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Text(
                                text = "Chaque clé IA reçoit la partie du plan où elle excelle (UI, Logique/Algo, Aperçu Tactile ou Build), travaille en parallèle, puis tout est assemblé ensemble.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SlateMuted
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (enabledKeys.isEmpty()) {
                                    AiSpecialty.entries.forEach { spec ->
                                        SpecialistBadgePill(
                                            workerName = "Firebase IA",
                                            specialty = spec
                                        )
                                    }
                                } else {
                                    enabledKeys.forEach { key ->
                                        SpecialistBadgePill(
                                            workerName = key.displayTitle(),
                                            specialty = key.bestSpecialty()
                                        )
                                    }
                                    if (enabledKeys.size < AiSpecialty.entries.size) {
                                        SpecialistBadgePill(
                                            workerName = "Firebase IA",
                                            specialty = AiSpecialty.INTEGRATION_BUILD
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Plan Input Card
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
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Votre Plan de Projet",
                                style = MaterialTheme.typography.titleSmall,
                                color = ElectricCyan
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedType == AppType.ANDROID_APP,
                                    onClick = { if (!isBusy) selectedType = AppType.ANDROID_APP },
                                    label = { Text("App Android") }
                                )
                                FilterChip(
                                    selected = selectedType == AppType.GAME,
                                    onClick = { if (!isBusy) selectedType = AppType.GAME },
                                    label = { Text("Jeu 2D / 3D") }
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(
                                    TargetLanguage.AUTO,
                                    TargetLanguage.KOTLIN,
                                    TargetLanguage.CSHARP_UNITY,
                                    TargetLanguage.GDSCRIPT_GODOT,
                                    TargetLanguage.DART_FLUTTER,
                                    TargetLanguage.HTML5_WEBGL
                                ).forEach { lang ->
                                    FilterChip(
                                        selected = selectedLang == lang,
                                        onClick = { if (!isBusy) selectedLang = lang },
                                        label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = ElectricCyan
                                        )
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = planText,
                                onValueChange = { planText = it },
                                enabled = !isBusy,
                                label = { Text("Décrivez le plan complet à partager") },
                                placeholder = {
                                    Text("Ex: Créer une app de gestion de tâches avec écran calendrier, moteur de rappels intelligents, aperçu interactif et export APK...")
                                },
                                minLines = 3,
                                maxLines = 7,
                                colors = forgeTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("swarm_plan_input")
                            )

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { onSplitPlanOnly(planText, selectedType, selectedLang) },
                                    enabled = !isBusy && planText.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("swarm_split_only_button")
                                ) {
                                    Text("1. Répartir", color = ElectricCyan)
                                }
                                Button(
                                    onClick = { onSplitAndRunTogether(planText, selectedType, selectedLang) },
                                    enabled = !isBusy && planText.isNotBlank(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianNight
                                    ),
                                    modifier = Modifier
                                        .weight(1.4f)
                                        .testTag("swarm_run_together_button")
                                ) {
                                    if (isBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = ObsidianNight,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("En cours...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Répartir & Travailler", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Error alert
                if (swarmState.phase == SwarmPhase.ERROR && !swarmState.errorMessage.isNullOrBlank()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = CrimsonError
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = swarmState.errorMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Live synchronized progress & tasks board
                if (swarmState.phase != SwarmPhase.IDLE && swarmState.tasks.isNotEmpty()) {
                    item {
                        Surface(
                            color = MidnightSlate,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (swarmState.phase == SwarmPhase.DONE) EmeraldBuild else ElectricCyan.copy(alpha = 0.5f),
                                    RoundedCornerShape(16.dp)
                                )
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = swarmState.phase.label,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (swarmState.phase == SwarmPhase.DONE) EmeraldBuild else ElectricCyan
                                        )
                                        if (swarmState.appName.isNotBlank()) {
                                            Text(
                                                text = "${swarmState.appName} (${swarmState.packageName})",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SlateMuted
                                            )
                                        }
                                    }
                                    Text(
                                        text = "${swarmState.completedCount}/${swarmState.tasks.size}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                LinearProgressIndicator(
                                    progress = { swarmState.progressFraction },
                                    color = if (swarmState.phase == SwarmPhase.DONE) EmeraldBuild else ElectricCyan,
                                    trackColor = ForgeSurface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(999.dp))
                                )

                                if (swarmState.planSummary.isNotBlank()) {
                                    Text(
                                        text = swarmState.planSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SlateMuted
                                    )
                                }

                                if (swarmState.phase == SwarmPhase.PLAN_READY) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Button(
                                        onClick = onLaunchPreparedPlan,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = EmeraldBuild,
                                            contentColor = ObsidianNight
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("swarm_start_parallel_button")
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Lancer tous les spécialistes ensemble",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    items(swarmState.tasks, key = { it.id }) { task ->
                        SwarmTaskCard(task = task)
                    }

                    // Live Visual Hierarchy & TreeMap of files being developed by the AI team
                    val liveProducedFiles = linkedMapOf<String, ProjectFile>().apply {
                        swarmState.tasks.forEach { t ->
                            t.producedFiles.forEach { pf -> put(pf.path, pf) }
                        }
                    }.values.toList()
                    val pendingTargetPaths = swarmState.tasks
                        .filter { it.status == SwarmTaskStatus.WAITING || it.status == SwarmTaskStatus.WORKING }
                        .flatMap { it.targetFiles }
                        .toSet()

                    if (liveProducedFiles.isNotEmpty() || pendingTargetPaths.isNotEmpty()) {
                        item(key = "swarm_live_hierarchy") {
                            ProjectFileHierarchyExplorer(
                                projectName = swarmState.appName.ifBlank { "Projet Multi-IA" },
                                files = liveProducedFiles,
                                pendingFilePaths = pendingTargetPaths,
                                initialMode = HierarchyVisualMode.TREEMAP,
                                openFileActionLabel = "Inspecter",
                                treemapHeight = 250.dp,
                                onSelectFile = { stats ->
                                    if (!stats.isPendingGeneration) {
                                        previewedFileStats = stats
                                    }
                                }
                            )
                        }
                    }
                }

                // Final Synchronized Completion Card
                if (swarmState.phase == SwarmPhase.DONE && !swarmState.createdProjectId.isNullOrBlank()) {
                    item {
                        Surface(
                            color = EmeraldBuild.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, EmeraldBuild, RoundedCornerShape(18.dp))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldBuild,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    val secText = swarmState.totalDurationMs?.let {
                                        " en ${String.format(Locale.US, "%.1f", it / 1000.0)}s"
                                    } ?: ""
                                    Text(
                                        text = "Travail terminé ensemble$secText !",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = EmeraldBuild
                                    )
                                }
                                Text(
                                    text = "Toutes les contributions des spécialistes IA ont été fusionnées dans le projet \"${swarmState.appName}\". Vous pouvez tester l'aperçu, inspecter chaque fichier et compiler l'APK.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Button(
                                    onClick = { onOpenCreatedProject(swarmState.createdProjectId) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = EmeraldBuild,
                                        contentColor = ObsidianNight
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .testTag("swarm_open_studio_button")
                                ) {
                                    Icon(Icons.Filled.Build, contentDescription = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ouvrir dans Studio & Compiler l'APK",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val inspected = previewedFileStats
    if (inspected != null) {
        AlertDialog(
            onDismissRequest = { previewedFileStats = null },
            containerColor = ForgeSurface,
            title = {
                Column {
                    Text(
                        text = inspected.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricCyan
                    )
                    Text(
                        text = "${inspected.normalizedPath} · ${inspected.lineCount} lignes",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted
                    )
                }
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianNight)
                        .border(1.dp, ForgeBorder, RoundedCornerShape(10.dp))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = inspected.file.content.ifBlank { "// En cours de génération..." },
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { previewedFileStats = null }) {
                    Text("Fermer", color = ElectricCyan)
                }
            }
        )
    }
}

@Composable
private fun SpecialistBadgePill(
    workerName: String,
    specialty: AiSpecialty
) {
    val accent = parseHexColor(specialty.accentHex, ElectricCyan)
    Surface(
        color = MidnightSlate,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$workerName → ${specialty.shortTitle}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SwarmTaskCard(task: SwarmTaskAssignment) {
    var expanded by remember(task.id) { mutableStateOf(false) }
    val specAccent = parseHexColor(task.specialty.accentHex, ElectricCyan)
    val statusColor = when (task.status) {
        SwarmTaskStatus.WAITING -> SlateMuted
        SwarmTaskStatus.WORKING -> ElectricCyan
        SwarmTaskStatus.COMPLETED -> EmeraldBuild
        SwarmTaskStatus.RECOVERED -> ForgeAmber
        SwarmTaskStatus.FAILED -> CrimsonError
    }

    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, statusColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(enabled = task.producedFiles.isNotEmpty()) { expanded = !expanded }
            .testTag("swarm_task_card_${task.id}")
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    color = specAccent.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = task.specialty.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = specAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        SwarmTaskStatus.WORKING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = ElectricCyan,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        SwarmTaskStatus.COMPLETED, SwarmTaskStatus.RECOVERED -> {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = statusColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        SwarmTaskStatus.WAITING -> {
                            Icon(
                                imageVector = Icons.Filled.HourglassTop,
                                contentDescription = null,
                                tint = SlateMuted,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        SwarmTaskStatus.FAILED -> {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = CrimsonError,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                    val durationSuffix = task.durationMs?.let {
                        " · ${String.format(Locale.US, "%.1f", it / 1000.0)}s"
                    } ?: ""
                    Text(
                        text = "${task.status.label}$durationSuffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Text(
                text = task.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Assigné à : ${task.assignedWorkerLabel}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = task.description,
                style = MaterialTheme.typography.bodySmall,
                color = SlateMuted
            )

            if (task.notes.isNotBlank()) {
                Text(
                    text = task.notes,
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldBuild
                )
            }

            if (task.producedFiles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Code,
                        contentDescription = null,
                        tint = SlateMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${task.producedFiles.size} fichier(s) produit(s) — Touchez pour ${if (expanded) "masquer" else "voir"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted
                    )
                }
            }

            AnimatedVisibility(visible = expanded && task.producedFiles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    task.producedFiles.forEach { file ->
                        Surface(
                            color = ObsidianNight,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ForgeBorder, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${file.path} (${file.language})",
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ElectricCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = file.content.lines().take(6).joinToString("\n"),
                                    fontFamily = MonoFontFamily,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted,
                                    maxLines = 6,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
