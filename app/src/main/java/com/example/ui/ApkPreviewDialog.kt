package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.compiler.ApkInspectionReport
import com.example.model.AppProject
import com.example.model.ProjectFileStats
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldBuild
import com.example.ui.theme.ForgeBorder
import com.example.ui.theme.ForgeSurface
import com.example.ui.theme.MidnightSlate
import com.example.ui.theme.MonoFontFamily
import com.example.ui.theme.ObsidianNight
import com.example.ui.theme.SlateMuted

@Composable
fun rememberDecodedIconBitmap(pngBytes: ByteArray?): ImageBitmap? {
    return remember(pngBytes?.contentHashCode()) {
        if (pngBytes == null || pngBytes.isEmpty()) {
            null
        } else {
            runCatching {
                BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
}

@Composable
fun ApkLauncherIconView(
    pngBytes: ByteArray?,
    appName: String,
    accentHex: String,
    modifier: Modifier = Modifier
) {
    val iconBitmap = rememberDecodedIconBitmap(pngBytes)
    val accent = parseHexColor(accentHex, ElectricCyan)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = "Icône APK de $appName",
            modifier = modifier.clip(RoundedCornerShape(14.dp))
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(RoundedCornerShape(14.dp))
                .background(ForgeSurface)
                .border(1.5.dp, accent, RoundedCornerShape(14.dp))
        ) {
            Icon(
                imageVector = Icons.Filled.Android,
                contentDescription = "Icône Android",
                tint = accent
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkPreviewDialog(
    project: AppProject,
    report: ApkInspectionReport,
    sha256: String?,
    certFingerprint: String?,
    onDismiss: () -> Unit
) {
    var activeTab by remember { mutableIntStateOf(0) }
    var reloadNonce by remember { mutableIntStateOf(0) }
    var inspectedEntry by remember { mutableStateOf<ProjectFileStats?>(null) }

    val previewTabs = listOf(
        "Exécuter" to Icons.Filled.PlayArrow,
        "Paquet & Arbre" to Icons.Filled.AccountTree,
        "Fiche Système" to Icons.Filled.Info
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ObsidianNight,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag("apk_preview_dialog")
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSlate)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ApkLauncherIconView(
                        pngBytes = report.iconPngBytes,
                        appName = project.appName,
                        accentHex = project.accentHex,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Aperçu APK · ${project.appName}",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${report.apkName} · ${report.totalSizeBytes / 1024} Ko",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("apk_preview_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer l'aperçu APK",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = MidnightSlate,
                    contentColor = ElectricCyan,
                    indicator = { tabPositions ->
                        if (activeTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                                color = ElectricCyan
                            )
                        }
                    }
                ) {
                    previewTabs.forEachIndexed { idx, (label, icon) ->
                        Tab(
                            selected = activeTab == idx,
                            onClick = { activeTab = idx },
                            text = { Text(label, maxLines = 1, fontSize = 12.sp) },
                            icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp)) },
                            selectedContentColor = ElectricCyan,
                            unselectedContentColor = SlateMuted,
                            modifier = Modifier.testTag("apk_preview_tab_$idx")
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (activeTab) {
                        0 -> {
                            ApkDeviceSimulatorPane(
                                project = project,
                                htmlFromApk = report.bundledHtml.ifBlank { project.playableHtml },
                                iconBytes = report.iconPngBytes,
                                reloadNonce = reloadNonce,
                                onReload = { reloadNonce++ }
                            )
                        }
                        1 -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    color = ForgeSurface,
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Vérification binaire de l'archive APK",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = ElectricCyan
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            ApkCheckBadge("AndroidManifest.xml", report.hasManifest)
                                            ApkCheckBadge("classes.dex (v035)", report.hasValidDexHeader)
                                            ApkCheckBadge("resources.arsc", report.hasResourcesArsc)
                                            ApkCheckBadge("Signature RSA", report.hasSignatureBlock)
                                            ApkCheckBadge("assets/index.html", report.bundledHtml.isNotBlank())
                                        }
                                    }
                                }

                                ProjectFileHierarchyExplorer(
                                    projectName = report.apkName,
                                    files = report.hierarchyFiles,
                                    initialMode = HierarchyVisualMode.TREEMAP,
                                    openFileActionLabel = "Voir entrée",
                                    treemapHeight = 280.dp,
                                    onSelectFile = { stats ->
                                        inspectedEntry = stats
                                    }
                                )
                            }
                        }
                        else -> {
                            ApkSystemInfoPane(
                                project = project,
                                report = report,
                                sha256 = sha256,
                                certFingerprint = certFingerprint
                            )
                        }
                    }
                }
            }
        }
    }

    val entryToView = inspectedEntry
    if (entryToView != null) {
        AlertDialog(
            onDismissRequest = { inspectedEntry = null },
            containerColor = ForgeSurface,
            title = {
                Text(
                    text = entryToView.normalizedPath,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = MonoFontFamily,
                    color = ElectricCyan
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ObsidianNight)
                        .border(1.dp, ForgeBorder, RoundedCornerShape(10.dp))
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = entryToView.file.content,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectedEntry = null }) {
                    Text("Fermer", color = ElectricCyan)
                }
            }
        )
    }
}

@Composable
private fun ApkDeviceSimulatorPane(
    project: AppProject,
    htmlFromApk: String,
    iconBytes: ByteArray?,
    reloadNonce: Int,
    onReload: () -> Unit
) {
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
                text = "Exécution directe de assets/index.html depuis l'APK",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldBuild,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onReload,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("apk_preview_reload_button")
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Relancer")
            }
        }

        // Simulated Android Phone Frame
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .border(2.5.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(28.dp))
                .background(Color.Black)
        ) {
            // Mock Android Status + App Header Bar inside the simulated device
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MidnightSlate)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ApkLauncherIconView(
                        pngBytes = iconBytes,
                        appName = project.appName,
                        accentHex = project.accentHex,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = project.appName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(EmeraldBuild)
                )
            }

            SandboxedWebView(
                html = htmlFromApk,
                reloadNonce = reloadNonce,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ApkSystemInfoPane(
    project: AppProject,
    report: ApkInspectionReport,
    sha256: String?,
    certFingerprint: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Android Package Installer Header Card
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(18.dp))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApkLauncherIconView(
                    pngBytes = report.iconPngBytes,
                    appName = project.appName,
                    accentHex = project.accentHex,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = project.appName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = project.packageName,
                        fontFamily = MonoFontFamily,
                        style = MaterialTheme.typography.bodySmall,
                        color = ElectricCyan
                    )
                    Text(
                        text = "Version ${project.versionName} (${project.versionCode}) · ${report.entries.size} entrées",
                        style = MaterialTheme.typography.labelSmall,
                        color = SlateMuted
                    )
                }
            }
        }

        Surface(
            color = MidnightSlate,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = EmeraldBuild,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Signature & Métadonnées",
                        style = MaterialTheme.typography.titleSmall,
                        color = EmeraldBuild
                    )
                }
                InfoRow("Fichier APK", report.apkName)
                InfoRow("Taille totale", "${report.totalSizeBytes} octets (${report.totalSizeBytes / 1024} Ko)")
                InfoRow("Bytecode DEX", "${report.dexSizeBytes} octets (${if (report.hasValidDexHeader) "v035 valide" else "invalide"})")
                InfoRow("Signature PKCS#7", if (report.hasSignatureBlock) "RSA-2048 complète" else "Manquante")
                if (!sha256.isNullOrBlank()) {
                    InfoRow("SHA-256", sha256.take(32) + "...")
                }
                if (!certFingerprint.isNullOrBlank()) {
                    InfoRow("Empreinte RSA", certFingerprint.take(32) + "...")
                }
            }
        }
    }
}

@Composable
private fun ApkCheckBadge(label: String, passed: Boolean) {
    val color = if (passed) EmeraldBuild else CrimsonError
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (passed) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SlateMuted
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = MonoFontFamily,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
