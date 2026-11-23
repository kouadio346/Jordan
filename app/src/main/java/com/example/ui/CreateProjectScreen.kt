package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.SportsEsports
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.IconGenUiState
import com.example.data.IconStylePreset
import com.example.data.ProjectIconStore
import com.example.model.AiApiKeyEntry
import com.example.model.AiRoutingSettings
import com.example.model.AppType
import com.example.model.StarterTemplate
import com.example.model.TargetLanguage
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.ObsidianNight

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateProjectScreen(
    initialTemplate: StarterTemplate?,
    generationState: GenerationState,
    aiKeys: List<AiApiKeyEntry>,
    aiRoutingSettings: AiRoutingSettings,
    activeEngineLabel: String?,
    iconStore: ProjectIconStore,
    iconVersions: Map<String, Int>,
    iconGenState: IconGenUiState,
    onGenerateDraftIcon: (appName: String, appType: AppType, prompt: String, style: IconStylePreset) -> Unit,
    onCustomizeDraftIcon: (appName: String, baseBitmap: Bitmap, editInstruction: String, style: IconStylePreset) -> Unit,
    onImportDraftIcon: (Uri) -> Unit,
    onResetDraftIcon: () -> Unit,
    onClearIconError: () -> Unit,
    onOpenAiKeys: () -> Unit,
    onSelectPoolMode: () -> Unit,
    onSelectBuiltInMode: () -> Unit,
    onSelectSpecificKey: (String) -> Unit,
    onClearError: () -> Unit,
    onBackClick: () -> Unit,
    onLaunchInSwarm: (prompt: String, appType: AppType, language: TargetLanguage) -> Unit,
    onGenerateClick: (
        prompt: String,
        appType: AppType,
        language: TargetLanguage,
        appName: String,
        packageName: String
    ) -> Unit
) {
    var selectedType by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.appType ?: AppType.GAME)
    }
    var selectedLang by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.language ?: TargetLanguage.AUTO)
    }
    var appName by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.title ?: "")
    }
    var packageName by remember(initialTemplate) {
        mutableStateOf(
            if (initialTemplate != null) {
                "com.forge.${initialTemplate.title.lowercase().replace(Regex("[^a-z0-9]"), "")}"
            } else ""
        )
    }
    var prompt by remember(initialTemplate) {
        mutableStateOf(initialTemplate?.prompt ?: "")
    }
    var promptValidationError by remember { mutableStateOf(false) }

    val isGenerating = generationState is GenerationState.Generating

    val inspirationPrompts = remember(selectedType) {
        if (selectedType == AppType.GAME) {
            listOf(
                "Jeu de vaisseau spatial néon avec vagues d'ennemis et score",
                "Jeu casse-briques cyberpunk avec bonus multi-balles",
                "Jeu Snake arcade rétro avec vitesse progressive et combos",
                "Jeu RPG combat au tour par tour avec héros, sorts et boss"
            )
        } else {
            listOf(
                "Application de suivi de budget et dépenses par catégorie",
                "Calculatrice scientifique et convertisseur d'unités complet",
                "Application de fitness chronomètre HIIT & suivi de séries",
                "Bloc-notes Markdown avec recherche et étiquettes couleur"
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = ObsidianNight,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianNight,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        enabled = !isGenerating,
                        modifier = Modifier.testTag("create_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                },
                title = { Text("Nouveau projet par IA") },
                actions = {
                    IconButton(
                        onClick = onOpenAiKeys,
                        enabled = !isGenerating,
                        modifier = Modifier.testTag("create_ai_keys_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = "Clés API IA",
                            tint = ElectricCyan
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 680.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                ActiveAiEngineBar(
                    keys = aiKeys,
                    routingSettings = aiRoutingSettings,
                    activeEngineLabel = activeEngineLabel,
                    onSelectPoolMode = onSelectPoolMode,
                    onSelectBuiltInMode = onSelectBuiltInMode,
                    onSelectSpecificKey = onSelectSpecificKey,
                    onOpenKeysManager = onOpenAiKeys
                )

                // 1. Type selector: Android App vs Game
                Text(
                    text = "1. Type de création",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TypeSelectorCard(
                        selected = selectedType == AppType.GAME,
                        title = "Jeu 2D / 3D",
                        subtitle = "Unity, Godot, Lua, C++, WebGL",
                        icon = Icons.Filled.SportsEsports,
                        onClick = { selectedType = AppType.GAME },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_game_card")
                    )
                    TypeSelectorCard(
                        selected = selectedType == AppType.ANDROID_APP,
                        title = "App Android",
                        subtitle = "Kotlin, Java, Flutter, Python",
                        icon = Icons.Filled.Android,
                        onClick = { selectedType = AppType.ANDROID_APP },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_app_card")
                    )
                }

                // 2. Language / Engine selector
                Text(
                    text = "2. Langage & Moteur spécialisé",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TargetLanguage.entries.forEach { lang ->
                        val isSelected = selectedLang == lang
                        val accent = parseHexColor(lang.badgeColorHex, ElectricCyan)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedLang = lang },
                            label = {
                                Text("${lang.displayName} · ${lang.engineLabel}")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accent.copy(alpha = 0.22f),
                                selectedLabelColor = MaterialTheme.colorScheme.onBackground,
                                containerColor = ForgeSurface,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                selectedBorderColor = accent,
                                borderColor = ForgeBorder
                            ),
                            modifier = Modifier.testTag("lang_chip_${lang.name}")
                        )
                    }
                }

                // 3. App identity (optional)
                Text(
                    text = "3. Identité APK (optionnel)",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = appName,
                        onValueChange = {
                            appName = it
                            if (packageName.isBlank() || packageName.startsWith("com.forge.")) {
                                val slug = it.lowercase().replace(Regex("[^a-z0-9]"), "")
                                if (slug.isNotBlank()) {
                                    packageName = "com.forge.$slug"
                                }
                            }
                        },
                        label = { Text("Nom de l'app") },
                        placeholder = { Text("Ex: Neon Blaster") },
                        singleLine = true,
                        colors = forgeTextFieldColors(),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("input_app_name")
                    )
                    OutlinedTextField(
                        value = packageName,
                        onValueChange = { packageName = it },
                        label = { Text("Package ID") },
                        placeholder = { Text("com.forge.monapp") },
                        singleLine = true,
                        colors = forgeTextFieldColors(),
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("input_package_name")
                    )
                }

                AppIconCustomizerCard(
                    iconStore = iconStore,
                    projectKey = ProjectIconStore.DRAFT_PROJECT_KEY,
                    iconVersion = iconVersions[ProjectIconStore.DRAFT_PROJECT_KEY] ?: 0,
                    appName = appName,
                    appType = selectedType,
                    iconGenState = iconGenState,
                    onGenerateAiIcon = { desc, style ->
                        onGenerateDraftIcon(appName, selectedType, desc, style)
                    },
                    onCustomizeAiIcon = { baseBmp, edit, style ->
                        onCustomizeDraftIcon(appName, baseBmp, edit, style)
                    },
                    onImportPhotoUri = onImportDraftIcon,
                    onResetToPlaceholder = onResetDraftIcon,
                    onClearError = onClearIconError
                )

                // 4. Prompt
                Text(
                    text = "4. Décrivez votre application ou jeu",
                    style = MaterialTheme.typography.titleSmall,
                    color = ElectricCyan
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = {
                        prompt = it
                        if (it.isNotBlank()) promptValidationError = false
                        onClearError()
                    },
                    label = { Text("Prompt pour l'IA") },
                    placeholder = {
                        Text("Décrivez les écrans, mécaniques de jeu, contrôles tactiles, fonctionnalités...")
                    },
                    isError = promptValidationError,
                    supportingText = if (promptValidationError) {
                        {
                            Text(
                                text = "Décrivez votre projet ci-dessus ou choisissez une idée rapide ci-dessous.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else null,
                    minLines = 4,
                    maxLines = 8,
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_prompt")
                )

                // Quick inspiration chips
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Idées rapides :",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        inspirationPrompts.forEach { idea ->
                            Surface(
                                color = MidnightSlate,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, ForgeBorder, RoundedCornerShape(12.dp))
                                    .clickable {
                                        prompt = idea
                                        promptValidationError = false
                                        onClearError()
                                    }
                            ) {
                                Text(
                                    text = idea,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                if (promptValidationError) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Veuillez saisir une description du projet (section 4) ou toucher une idée rapide pour lancer la génération.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                if (generationState is GenerationState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = generationState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            OutlinedButton(
                                onClick = onOpenAiKeys,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("error_open_ai_keys_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Configurer une clé API IA (Gemini, OpenAI, Groq…)",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (prompt.isBlank()) {
                            promptValidationError = true
                        } else {
                            promptValidationError = false
                            onGenerateClick(
                                prompt,
                                selectedType,
                                selectedLang,
                                appName,
                                packageName
                            )
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("generate_project_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = ObsidianNight,
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Génération IA en cours...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Générer Code + Aperçu + Prêt pour APK",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        if (prompt.isBlank()) {
                            promptValidationError = true
                        } else {
                            promptValidationError = false
                            onLaunchInSwarm(prompt, selectedType, selectedLang)
                        }
                    },
                    enabled = !isGenerating,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("launch_swarm_from_create_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Groups,
                        contentDescription = null,
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Répartir en Équipe Multi-IA (Parallèle)",
                        color = ElectricCyan,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            if (generationState is GenerationState.Generating) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianNight.copy(alpha = 0.86f)),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = ForgeSurface,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .padding(24.dp)
                            .widthIn(max = 380.dp)
                            .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = ElectricCyan)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Forge en cours...",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = generationState.stageLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeSelectorCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) ElectricCyan else ForgeBorder
    val bgColor = if (selected) ElectricCyan.copy(alpha = 0.14f) else ForgeSurface

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun forgeTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = ElectricCyan,
    unfocusedBorderColor = ForgeBorder,
    focusedLabelColor = ElectricCyan,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = ElectricCyan,
    focusedContainerColor = ForgeSurface,
    unfocusedContainerColor = ForgeSurface
)
