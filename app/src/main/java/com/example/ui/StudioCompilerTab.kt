package com.example.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.compiler.ApkInspectionReport
import com.example.compiler.ApkPackageInspector
import com.example.model.ApkBuildResult
import com.example.model.AppProject
import com.example.model.BuildLogEntry
import com.example.model.LogLevel
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StudioCompilerTab(
    project: AppProject,
    compileState: CompileState,
    onCompileClick: () -> Unit,
    onExportSourcesZip: ((java.io.File) -> Unit) -> Unit,
    onOpenGitHubExport: () -> Unit,
    onPostMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val consoleScroll = rememberScrollState()

    val currentResult = (compileState as? CompileState.Success)?.result
    var showApkPreview by remember { mutableStateOf(false) }

    val activeReport by produceState<ApkInspectionReport?>(
        initialValue = null,
        key1 = currentResult?.apkFile?.absolutePath,
        key2 = project.lastBuildApkName
    ) {
        value = withContext(Dispatchers.IO) {
            val targetFile = currentResult?.apkFile ?: ApkPackageInspector.findCachedApkForProject(
                filesDir = context.filesDir,
                appName = project.appName,
                versionName = project.versionName,
                lastBuildApkName = project.lastBuildApkName
            )
            targetFile?.let { ApkPackageInspector.inspectApk(it).getOrNull() }
        }
    }

    val safCreateApkLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri ->
        val file = currentResult?.apkFile
        if (uri != null && file != null && file.exists()) {
            val res = ArtifactExportHelper.copyFileToUri(context, file, uri)
            res.fold(
                onSuccess = { onPostMessage("APK enregistré avec succès !") },
                onFailure = { err -> onPostMessage(err.localizedMessage ?: "Erreur d'enregistrement.") }
            )
        }
    }

    val logsToDisplay: List<BuildLogEntry> = when (compileState) {
        is CompileState.Compiling -> compileState.logs
        is CompileState.Success -> compileState.result.logs
        else -> emptyList()
    }

    LaunchedEffect(logsToDisplay.size) {
        if (logsToDisplay.isNotEmpty()) {
            consoleScroll.animateScrollTo(consoleScroll.maxValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Target Spec Card
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(18.dp))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Cible de Compilation Android",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                SpecRow("Application", project.appName)
                SpecRow("Package ID", project.packageName)
                SpecRow("Version", "${project.versionName} (build ${project.versionCode})")
                SpecRow("Langage source", "${project.language} (${project.files.size} fichiers)")
                SpecRow("Pipeline", "AXML + ARSC + Dalvik DEX v035 + RSA-2048")
            }
        }

        // Compile CTA
        val isCompiling = compileState is CompileState.Compiling
        Button(
            onClick = onCompileClick,
            enabled = !isCompiling,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = ObsidianNight
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("compile_apk_button")
        ) {
            if (isCompiling) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = ObsidianNight,
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Compilation en cours...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Filled.Build, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.compile_apk), fontWeight = FontWeight.Bold)
            }
        }

        if (compileState is CompileState.Compiling) {
            LinearProgressIndicator(
                progress = { compileState.progress },
                color = ElectricCyan,
                trackColor = ForgeSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }

        if (compileState is CompileState.Error) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = compileState.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp)
                )
            }
        }

        // Success Artifact Card
        if (compileState is CompileState.Success) {
            val res = compileState.result
            ApkArtifactCard(
                project = project,
                result = res,
                inspectionReport = activeReport,
                onPreviewApk = {
                    if (activeReport != null) {
                        showApkPreview = true
                    } else {
                        onPostMessage("Analyse du paquet APK en cours...")
                    }
                },
                onDownloadToDownloads = {
                    val saveRes = ArtifactExportHelper.saveFileToDownloads(
                        context = context,
                        sourceFile = res.apkFile,
                        displayName = res.apkName,
                        mimeType = "application/vnd.android.package-archive"
                    )
                    saveRes.fold(
                        onSuccess = { dest -> onPostMessage("APK enregistré dans : $dest") },
                        onFailure = { err -> onPostMessage(err.localizedMessage ?: "Erreur lors du téléchargement.") }
                    )
                },
                onSaveAsSaf = {
                    safCreateApkLauncher.launch(res.apkName)
                },
                onShareApk = {
                    ArtifactExportHelper.shareArtifactFile(
                        context = context,
                        file = res.apkFile,
                        mimeType = "application/vnd.android.package-archive",
                        chooserTitle = "Partager ${res.apkName}"
                    )
                },
                onExportZip = {
                    onExportSourcesZip { zipFile ->
                        ArtifactExportHelper.shareArtifactFile(
                            context = context,
                            file = zipFile,
                            mimeType = "application/zip",
                            chooserTitle = "Exporter les sources (${zipFile.name})"
                        )
                    }
                }
            )
        } else if (!project.lastBuildApkName.isNullOrBlank() || activeReport != null) {
            val cachedReport = activeReport
            Surface(
                color = MidnightSlate,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, ForgeBorder, RoundedCornerShape(14.dp))
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (cachedReport != null) {
                            ApkLauncherIconView(
                                pngBytes = cachedReport.iconPngBytes,
                                appName = project.appName,
                                accentHex = project.accentHex,
                                modifier = Modifier.size(36.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldBuild,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (cachedReport != null) {
                                "Dernier APK prêt : ${cachedReport.apkName} (${cachedReport.totalSizeBytes / 1024L} Ko)"
                            } else {
                                "Dernier build : ${project.lastBuildApkName} (${(project.lastBuildSizeBytes ?: 0L) / 1024L} Ko). Cliquez sur Compiler pour régénérer le fichier local."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                    if (cachedReport != null) {
                        OutlinedButton(
                            onClick = { showApkPreview = true },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("preview_cached_apk_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aperçu de l'APK compilé", color = ElectricCyan)
                        }
                    }
                }
            }
        }

        // Always-available Source Code & GitHub Export Card
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, ForgeBorder, RoundedCornerShape(18.dp))
                .testTag("github_source_export_card")
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Code Source & GitHub",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Publiez ce projet sur GitHub ou téléchargez l'archive complète (${project.files.size} fichiers + README).",
                            style = MaterialTheme.typography.bodySmall,
                            color = SlateMuted
                        )
                    }
                }

                Button(
                    onClick = onOpenGitHubExport,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ForgeAmber,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("github_export_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Publier le code sur GitHub", fontWeight = FontWeight.Bold)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            onExportSourcesZip { zipFile ->
                                val saveRes = ArtifactExportHelper.saveFileToDownloads(
                                    context = context,
                                    sourceFile = zipFile,
                                    displayName = zipFile.name,
                                    mimeType = "application/zip"
                                )
                                saveRes.fold(
                                    onSuccess = { dest -> onPostMessage("Sources enregistrées : $dest") },
                                    onFailure = { err -> onPostMessage(err.localizedMessage ?: "Erreur d'enregistrement ZIP.") }
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("download_sources_zip_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Télécharger .zip", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            onExportSourcesZip { zipFile ->
                                ArtifactExportHelper.shareArtifactFile(
                                    context = context,
                                    file = zipFile,
                                    mimeType = "application/zip",
                                    chooserTitle = "Partager les sources (${zipFile.name})"
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("share_sources_zip_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Partager .zip", maxLines = 1)
                    }
                }
            }
        }

        // Terminal Console Output
        Surface(
            color = Color(0xFF050811),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSlate)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Terminal,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Console Compilateur ForgeAPK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(consoleScroll)
                        .padding(12.dp)
                ) {
                    if (logsToDisplay.isEmpty()) {
                        Text(
                            text = "> Prêt à compiler ${project.packageName}...\n> Appuyez sur \"Compiler & Signer l'APK\" pour générer classes.dex, AndroidManifest.xml (AXML), resources.arsc et signer le paquet.",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = MonoFontFamily,
                            color = SlateMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            logsToDisplay.forEach { entry ->
                                val logColor = when (entry.level) {
                                    LogLevel.INFO -> SlateMuted
                                    LogLevel.STEP -> ElectricCyan
                                    LogLevel.SUCCESS -> EmeraldBuild
                                    LogLevel.WARN -> ForgeAmber
                                    LogLevel.ERROR -> CrimsonError
                                }
                                Text(
                                    text = "[${entry.tag}] ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MonoFontFamily,
                                    color = logColor
                                )
                            }
                        }
                    }
                }
            }
        }
        if (showApkPreview && activeReport != null) {
            ApkPreviewDialog(
                project = project,
                report = activeReport!!,
                sha256 = currentResult?.sha256 ?: project.lastBuildSha256,
                certFingerprint = currentResult?.certFingerprint,
                onDismiss = { showApkPreview = false }
            )
        }
    }
}

@Composable
private fun ApkArtifactCard(
    project: AppProject,
    result: ApkBuildResult,
    inspectionReport: ApkInspectionReport?,
    onPreviewApk: () -> Unit,
    onDownloadToDownloads: () -> Unit,
    onSaveAsSaf: () -> Unit,
    onShareApk: () -> Unit,
    onExportZip: () -> Unit
) {
    Surface(
        color = EmeraldBuild.copy(alpha = 0.08f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, EmeraldBuild.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
            .testTag("apk_artifact_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ApkLauncherIconView(
                    pngBytes = inspectionReport?.iconPngBytes,
                    appName = project.appName,
                    accentHex = project.accentHex,
                    modifier = Modifier.size(44.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = result.apkName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val integrityLabel = if (inspectionReport?.isIntegrityValid == true) {
                        "DEX + AXML + RSA vérifiés"
                    } else {
                        "APK signé"
                    }
                    Text(
                        text = "$integrityLabel · ${result.sizeBytes / 1024} Ko · ${result.entryCount} entrées · ${result.durationMs} ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = EmeraldBuild
                    )
                }
            }

            SpecRow("SHA-256", result.sha256.take(24) + "...")
            SpecRow("Certificat RSA", result.certFingerprint.take(23) + "...")

            Button(
                onClick = onPreviewApk,
                enabled = inspectionReport != null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianNight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("preview_apk_button")
            ) {
                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Aperçu APK inclus (Exécuter & Inspecter)", fontWeight = FontWeight.Bold)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onDownloadToDownloads,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldBuild,
                        contentColor = ObsidianNight
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("download_apk_button")
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.download_apk), fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onShareApk,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("share_apk_button")
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.share_apk))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = onSaveAsSaf,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("save_as_apk_button")
                ) {
                    Icon(Icons.Filled.SaveAs, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Enregistrer sous")
                }
                OutlinedButton(
                    onClick = onExportZip,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_sources_zip_button")
                ) {
                    Icon(Icons.Filled.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.export_sources))
                }
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
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
