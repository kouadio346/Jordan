package com.example.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.IconGenUiState
import com.example.data.IconStylePreset
import com.example.data.ProjectIconStore
import com.example.model.AppType
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBuild
import com.example.ui.theme.ForgeAmber
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SlateMuted

@Composable
fun ProjectIconAvatar(
    iconStore: ProjectIconStore,
    projectKey: String,
    iconVersion: Int,
    appName: String,
    modifier: Modifier = Modifier
) {
    val customBitmap = remember(projectKey, iconVersion) {
        iconStore.loadIconBitmap(projectKey)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MidnightSlate)
            .border(1.5.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = "Icône de $appName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(id = R.drawable.img_placeholder_app_icon),
                contentDescription = "Icône placeholder de $appName",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun AppIconCustomizerCard(
    iconStore: ProjectIconStore,
    projectKey: String,
    iconVersion: Int,
    appName: String,
    appType: AppType,
    iconGenState: IconGenUiState,
    onGenerateAiIcon: (prompt: String, style: IconStylePreset) -> Unit,
    onCustomizeAiIcon: (baseBitmap: Bitmap, editInstruction: String, style: IconStylePreset) -> Unit,
    onImportPhotoUri: (Uri) -> Unit,
    onResetToPlaceholder: () -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }
    val hasCustom = remember(projectKey, iconVersion) {
        iconStore.hasCustomIcon(projectKey)
    }

    Surface(
        color = ForgeSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ProjectIconAvatar(
                iconStore = iconStore,
                projectKey = projectKey,
                iconVersion = iconVersion,
                appName = appName.ifBlank { "Nouveau projet" },
                modifier = Modifier.size(64.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Icône de l'application",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        color = if (hasCustom) EmeraldBuild.copy(alpha = 0.16f) else ForgeAmber.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (hasCustom) "Personnalisée" else "Placeholder",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasCustom) EmeraldBuild else ForgeAmber,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = if (hasCustom) {
                        "Sera intégrée dans l'APK compilé et l'aperçu."
                    } else {
                        "Icône placeholder par défaut — personnalisez ou régénérez par IA."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = SlateMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onClearError()
                        showDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("open_icon_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Modifier", color = ElectricCyan)
                }
                if (hasCustom) {
                    TextButton(
                        onClick = onResetToPlaceholder,
                        modifier = Modifier.testTag("reset_icon_inline_button")
                    ) {
                        Text("Réinitialiser", style = MaterialTheme.typography.labelSmall, color = SlateMuted)
                    }
                }
            }
        }
    }

    if (showDialog) {
        AppIconGeneratorDialog(
            iconStore = iconStore,
            projectKey = projectKey,
            iconVersion = iconVersion,
            appName = appName,
            appType = appType,
            iconGenState = iconGenState,
            onGenerateAiIcon = onGenerateAiIcon,
            onCustomizeAiIcon = onCustomizeAiIcon,
            onImportPhotoUri = onImportPhotoUri,
            onResetToPlaceholder = onResetToPlaceholder,
            onDismiss = {
                onClearError()
                showDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppIconGeneratorDialog(
    iconStore: ProjectIconStore,
    projectKey: String,
    iconVersion: Int,
    appName: String,
    appType: AppType,
    iconGenState: IconGenUiState,
    onGenerateAiIcon: (prompt: String, style: IconStylePreset) -> Unit,
    onCustomizeAiIcon: (baseBitmap: Bitmap, editInstruction: String, style: IconStylePreset) -> Unit,
    onImportPhotoUri: (Uri) -> Unit,
    onResetToPlaceholder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedStyle by remember { mutableStateOf(IconStylePreset.NEON_CYBER) }
    var promptText by remember(appName, appType) {
        mutableStateOf(
            if (appName.isNotBlank()) {
                "Emblème moderne pour ${appName.trim()}"
            } else if (appType == AppType.GAME) {
                "Bouclier futuriste brillant de jeu arcade"
            } else {
                "Symbole technologique épuré avec prismes lumineux"
            }
        )
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onImportPhotoUri(uri)
        }
    }

    val isLoading = iconGenState is IconGenUiState.Loading
    val hasCustom = remember(projectKey, iconVersion) {
        iconStore.hasCustomIcon(projectKey)
    }

    Dialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianNight,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .border(1.5.dp, ElectricCyan.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                .testTag("icon_studio_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Studio d'Icône APK",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Personnalisez ou régénérez avec Gemini Image IA",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isLoading,
                        modifier = Modifier.testTag("icon_studio_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer"
                        )
                    }
                }

                // Live Icon Preview Box
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        ProjectIconAvatar(
                            iconStore = iconStore,
                            projectKey = projectKey,
                            iconVersion = iconVersion,
                            appName = appName.ifBlank { "App" },
                            modifier = Modifier.size(120.dp)
                        )
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(ObsidianNight.copy(alpha = 0.78f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = ElectricCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }

                if (iconGenState is IconGenUiState.Loading) {
                    Text(
                        text = iconGenState.stageLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = ElectricCyan,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else if (iconGenState is IconGenUiState.Success) {
                    Text(
                        text = iconGenState.message,
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldBuild,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                if (iconGenState is IconGenUiState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = CrimsonError,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = iconGenState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Style Presets
                Text(
                    text = "Style visuel",
                    style = MaterialTheme.typography.labelMedium,
                    color = ElectricCyan
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconStylePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedStyle == preset,
                            onClick = { if (!isLoading) selectedStyle = preset },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan,
                                containerColor = ForgeSurface,
                                labelColor = SlateMuted
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = promptText,
                    onValueChange = { promptText = it },
                    enabled = !isLoading,
                    label = { Text("Description ou modification souhaitée") },
                    placeholder = { Text("Ex: Fusée cybernétique cyan sur fond sombre, minimaliste...") },
                    minLines = 2,
                    maxLines = 4,
                    colors = forgeTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("icon_prompt_input")
                )

                // Generate new AI icon button
                Button(
                    onClick = { onGenerateAiIcon(promptText, selectedStyle) },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("icon_ai_generate_button")
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Régénérer une icône par IA", fontWeight = FontWeight.Bold)
                }

                // Customize current icon (Image-to-Image)
                OutlinedButton(
                    onClick = {
                        val baseBitmap = iconStore.loadIconBitmap(projectKey)
                            ?: BitmapFactory.decodeResource(context.resources, R.drawable.img_placeholder_app_icon)
                        if (baseBitmap != null) {
                            onCustomizeAiIcon(baseBitmap, promptText, selectedStyle)
                        }
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("icon_ai_customize_button")
                ) {
                    Icon(Icons.Filled.Brush, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Personnaliser l'icône actuelle (IA)", color = ElectricCyan)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("icon_import_photo_button")
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Galerie")
                    }

                    OutlinedButton(
                        onClick = onResetToPlaceholder,
                        enabled = !isLoading && hasCustom,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("icon_reset_placeholder_button")
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Placeholder")
                    }
                }
            }
        }
    }
}
