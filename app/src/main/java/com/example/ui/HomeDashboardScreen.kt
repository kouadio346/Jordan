package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.ProjectIconStore
import com.example.model.AiApiKeyEntry
import com.example.model.AiRoutingSettings
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.StarterTemplate
import com.example.model.StarterTemplates
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBuild
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.ObsidianNight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    userEmail: String?,
    projectsState: ProjectsUiState,
    aiKeys: List<AiApiKeyEntry>,
    aiRoutingSettings: AiRoutingSettings,
    activeEngineLabel: String?,
    iconStore: ProjectIconStore,
    iconVersions: Map<String, Int>,
    statusMessage: String?,
    onClearStatusMessage: () -> Unit,
    onCreateNewClick: () -> Unit,
    onOpenArchitectChat: () -> Unit,
    onOpenSwarmTeam: () -> Unit,
    onOpenAiKeys: () -> Unit,
    onSelectPoolMode: () -> Unit,
    onSelectBuiltInMode: () -> Unit,
    onSelectSpecificKey: (String) -> Unit,
    onTemplateClick: (StarterTemplate) -> Unit,
    onProjectClick: (AppProject) -> Unit,
    onSignOutClick: () -> Unit
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
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ObsidianNight,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ElectricCyan.copy(alpha = 0.16f))
                                .border(1.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ForgeAPK Studio",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onOpenAiKeys,
                        modifier = Modifier.testTag("dashboard_ai_keys_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Clés API IA",
                            tint = ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = onSignOutClick,
                        modifier = Modifier.testTag("sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = "Se déconnecter",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNewClick,
                containerColor = ElectricCyan,
                contentColor = ObsidianNight,
                icon = {
                    Icon(Icons.Filled.Add, contentDescription = null)
                },
                text = {
                    Text(
                        text = stringResource(R.string.create_new_project),
                        fontWeight = FontWeight.Bold
                    )
                },
                modifier = Modifier.testTag("fab_create_project")
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 700.dp

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Hero Studio Card
                item {
                    HeroStudioBanner(
                        userEmail = userEmail,
                        onCreateClick = onCreateNewClick,
                        onChatClick = onOpenArchitectChat,
                        onSwarmClick = onOpenSwarmTeam
                    )
                }

                // Multi-Key AI Engine Switcher
                item {
                    ActiveAiEngineBar(
                        keys = aiKeys,
                        routingSettings = aiRoutingSettings,
                        activeEngineLabel = activeEngineLabel,
                        onSelectPoolMode = onSelectPoolMode,
                        onSelectBuiltInMode = onSelectBuiltInMode,
                        onSelectSpecificKey = onSelectSpecificKey,
                        onOpenKeysManager = onOpenAiKeys
                    )
                }

                // Quick-Start Templates
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Modèles IA instantanés",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Apps & Jeux",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(StarterTemplates.catalog, key = { it.id }) { tpl ->
                                TemplateCard(
                                    template = tpl,
                                    onClick = { onTemplateClick(tpl) }
                                )
                            }
                        }
                    }
                }

                // User Projects Header
                item {
                    Text(
                        text = "Vos Projets & APK",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                when (projectsState) {
                    is ProjectsUiState.Loading -> {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ElectricCyan)
                            }
                        }
                    }
                    is ProjectsUiState.Error -> {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = projectsState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                    is ProjectsUiState.Success -> {
                        if (projectsState.projects.isEmpty()) {
                            item {
                                EmptyProjectsCard(onCreateClick = onCreateNewClick)
                            }
                        } else if (isWide) {
                            val rows = projectsState.projects.chunked(2)
                            items(rows) { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { proj ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            ProjectCard(
                                                project = proj,
                                                iconStore = iconStore,
                                                iconVersion = iconVersions[proj.id] ?: 0,
                                                onClick = { onProjectClick(proj) }
                                            )
                                        }
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            items(projectsState.projects, key = { it.id }) { proj ->
                                ProjectCard(
                                    project = proj,
                                    iconStore = iconStore,
                                    iconVersion = iconVersions[proj.id] ?: 0,
                                    onClick = { onProjectClick(proj) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStudioBanner(
    userEmail: String?,
    onCreateClick: () -> Unit,
    onChatClick: () -> Unit,
    onSwarmClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, ForgeBorder, RoundedCornerShape(24.dp))
            .background(MidnightSlate)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_hero_banner),
            contentDescription = "ForgeAPK Hero Studio",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
                .height(210.dp)
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            ObsidianNight.copy(alpha = 0.94f),
                            ObsidianNight.copy(alpha = 0.72f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            if (!userEmail.isNullOrBlank()) {
                Text(
                    text = userEmail,
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = "Générez. Testez. Compilez en APK.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Décrivez une application Android ou un jeu dans n'importe quel langage (Kotlin, C#, GDScript, C++, Lua, Dart, WebGL...) et obtenez son APK signé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier.testTag("hero_create_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Générer", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onSwarmClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hero_swarm_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = EmeraldBuild,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Équipe IA", color = EmeraldBuild, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onChatClick,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("hero_architect_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Chat IA", color = ElectricCyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: StarterTemplate,
    onClick: () -> Unit
) {
    val accent = parseHexColor(template.accentHex, ElectricCyan)
    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .width(216.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, ForgeBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("template_card_${template.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = template.language.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Icon(
                    imageVector = if (template.appType == AppType.GAME) {
                        Icons.Filled.SportsEsports
                    } else {
                        Icons.Filled.Android
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = template.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = template.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ProjectCard(
    project: AppProject,
    iconStore: ProjectIconStore,
    iconVersion: Int,
    onClick: () -> Unit
) {
    val accent = parseHexColor(project.accentHex, ElectricCyan)

    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, ForgeBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("project_card_${project.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProjectIconAvatar(
                    iconStore = iconStore,
                    projectKey = project.id,
                    iconVersion = iconVersion,
                    appName = project.appName,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${project.packageName} · v${project.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    color = accent.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = project.language,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (project.summary.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = project.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${project.files.size} fichier(s) source",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!project.lastBuildApkName.isNullOrBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldBuild,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        val sizeKo = (project.lastBuildSizeBytes ?: 0L) / 1024L
                        Text(
                            text = "APK · ${sizeKo} Ko",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldBuild
                        )
                    }
                } else {
                    Text(
                        text = "Prêt à compiler",
                        style = MaterialTheme.typography.labelSmall,
                        color = ForgeAmber
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyProjectsCard(onCreateClick: () -> Unit) {
    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, ForgeBorder, RoundedCornerShape(20.dp))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Aucun projet pour l'instant",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choisissez un modèle ci-dessus ou décrivez votre idée d'application ou de jeu à l'IA.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianNight
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("empty_create_button")
            ) {
                Text("Créer mon premier projet", fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun parseHexColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
