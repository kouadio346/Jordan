package com.example.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.GitHubExportService
import com.example.model.AppProject
import com.example.model.GitHubAccountState
import com.example.model.GitHubExportUiState
import com.example.model.GitHubRepoSummary
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

@Composable
fun GitHubExportDialog(
    project: AppProject,
    accountState: GitHubAccountState,
    exportState: GitHubExportUiState,
    onConnectToken: (String) -> Unit,
    onRefreshRepos: () -> Unit,
    onDisconnectAccount: () -> Unit,
    onPushToGitHub: (
        repoName: String,
        branch: String,
        commitMessage: String,
        createRepoFirst: Boolean,
        isPrivate: Boolean,
        repoDescription: String,
        ownerOverride: String?
    ) -> Unit,
    onDownloadZipToDownloads: () -> Unit,
    onShareSourcesZip: () -> Unit,
    onClearExportError: () -> Unit,
    onPostMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var tokenInput by remember { mutableStateOf("") }
    var showToken by remember { mutableStateOf(false) }

    var createNewRepo by remember(project.id) { mutableStateOf(true) }
    var newRepoName by remember(project.id, project.appName) {
        mutableStateOf(GitHubExportService.sanitizeRepoName(project.appName))
    }
    var newRepoDescription by remember(project.id, project.summary) {
        mutableStateOf(
            project.summary.lineSequence().firstOrNull().orEmpty()
                .ifBlank { "Application ${project.language} générée par ForgeAPK Studio" }
                .take(160)
        )
    }
    var isPrivateRepo by remember { mutableStateOf(true) }
    var selectedExistingRepo by remember(accountState) {
        mutableStateOf<GitHubRepoSummary?>(
            (accountState as? GitHubAccountState.Connected)?.repos?.firstOrNull()
        )
    }
    var branchName by remember(project.id) { mutableStateOf("main") }
    var commitMessage by remember(project.id, project.versionName) {
        mutableStateOf("feat: export ${project.appName} v${project.versionName} from ForgeAPK")
    }

    val isPushing = exportState is GitHubExportUiState.Loading
    val isConnecting = accountState is GitHubAccountState.Connecting
    val connected = accountState as? GitHubAccountState.Connected

    Dialog(
        onDismissRequest = { if (!isPushing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = ForgeSurface,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 760.dp)
                .border(1.dp, ForgeBorder, RoundedCornerShape(24.dp))
                .testTag("github_export_dialog")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MidnightSlate)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(ElectricCyan.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudUpload,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Exporter vers GitHub",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${project.appName} · ${project.files.size} fichiers + README + bundle",
                                style = MaterialTheme.typography.labelSmall,
                                color = SlateMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isPushing,
                        modifier = Modifier.testTag("github_close_dialog_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Fermer l'export GitHub",
                            tint = SlateMuted
                        )
                    }
                }

                // Body
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Quick Offline ZIP Export Strip
                    Surface(
                        color = MidnightSlate,
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
                                text = "Archive locale du dépôt (.zip)",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = onDownloadZipToDownloads,
                                    enabled = !isPushing,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("github_download_zip_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FolderZip,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Télécharger .zip", maxLines = 1, color = ElectricCyan)
                                }
                                OutlinedButton(
                                    onClick = onShareSourcesZip,
                                    enabled = !isPushing,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("github_share_zip_button")
                                ) {
                                    Text("Partager .zip", maxLines = 1)
                                }
                            }
                        }
                    }

                    // GitHub Connection Card
                    Surface(
                        color = MidnightSlate,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ForgeBorder, RoundedCornerShape(16.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Security,
                                        contentDescription = null,
                                        tint = if (connected != null) EmeraldBuild else ForgeAmber,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (connected != null) {
                                            "Connecté à @${connected.profile.login}"
                                        } else {
                                            "Connexion GitHub (Token PAT)"
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                if (connected != null) {
                                    TextButton(
                                        onClick = onDisconnectAccount,
                                        enabled = !isPushing,
                                        modifier = Modifier.testTag("github_disconnect_button")
                                    ) {
                                        Text("Déconnecter", color = CrimsonError)
                                    }
                                }
                            }

                            if (connected == null) {
                                Text(
                                    text = "Collez un Personal Access Token (scope repo ou Contents: Read & write). Stocké localement chiffré AES-256.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlateMuted
                                )
                                OutlinedTextField(
                                    value = tokenInput,
                                    onValueChange = { tokenInput = it },
                                    label = { Text("GitHub Personal Access Token") },
                                    placeholder = { Text("ghp_... ou github_pat_...") },
                                    singleLine = true,
                                    enabled = !isConnecting && !isPushing,
                                    visualTransformation = if (showToken) {
                                        VisualTransformation.None
                                    } else {
                                        PasswordVisualTransformation()
                                    },
                                    trailingIcon = {
                                        IconButton(onClick = { showToken = !showToken }) {
                                            Icon(
                                                imageVector = if (showToken) {
                                                    Icons.Filled.VisibilityOff
                                                } else {
                                                    Icons.Filled.Visibility
                                                },
                                                contentDescription = "Afficher ou masquer le token"
                                            )
                                        }
                                    },
                                    colors = forgeTextFieldColors(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("github_pat_input")
                                )

                                if (accountState is GitHubAccountState.Error) {
                                    Text(
                                        text = accountState.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = CrimsonError
                                    )
                                }

                                Button(
                                    onClick = { onConnectToken(tokenInput) },
                                    enabled = !isConnecting && tokenInput.isNotBlank(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ElectricCyan,
                                        contentColor = ObsidianNight
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("github_connect_button")
                                ) {
                                    if (isConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            color = ObsidianNight,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Vérification...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("Connecter mon compte GitHub", fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Text(
                                    text = "${connected.repos.size} dépôt(s) détecté(s) · Prêt pour le push direct via l'API GitHub.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EmeraldBuild
                                )
                            }
                        }
                    }

                    // Repository Target & Push Form (shown when connected)
                    if (connected != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = createNewRepo,
                                onClick = {
                                    onClearExportError()
                                    createNewRepo = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.AddCircleOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text("Nouveau dépôt") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("github_mode_new_repo")
                            )
                            FilterChip(
                                selected = !createNewRepo,
                                onClick = {
                                    onClearExportError()
                                    createNewRepo = false
                                    if (selectedExistingRepo == null) {
                                        selectedExistingRepo = connected.repos.firstOrNull()
                                        selectedExistingRepo?.let { branchName = it.defaultBranch }
                                    }
                                },
                                label = { Text("Dépôt existant (${connected.repos.size})") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyan
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("github_mode_existing_repo")
                            )
                        }

                        if (createNewRepo) {
                            OutlinedTextField(
                                value = newRepoName,
                                onValueChange = { newRepoName = it },
                                label = { Text("Nom du dépôt GitHub") },
                                singleLine = true,
                                enabled = !isPushing,
                                colors = forgeTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("github_repo_name_input")
                            )
                            OutlinedTextField(
                                value = newRepoDescription,
                                onValueChange = { newRepoDescription = it },
                                label = { Text("Description (optionnelle)") },
                                singleLine = true,
                                enabled = !isPushing,
                                colors = forgeTextFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MidnightSlate)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isPrivateRepo) Icons.Filled.Lock else Icons.Filled.Public,
                                        contentDescription = null,
                                        tint = if (isPrivateRepo) ForgeAmber else EmeraldBuild,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = if (isPrivateRepo) "Dépôt Privé" else "Dépôt Public",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isPrivateRepo) {
                                                "Visible uniquement par vous"
                                            } else {
                                                "Accessible à tous sur GitHub"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = SlateMuted
                                        )
                                    }
                                }
                                Switch(
                                    checked = isPrivateRepo,
                                    onCheckedChange = { isPrivateRepo = it },
                                    enabled = !isPushing,
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = ObsidianNight,
                                        checkedTrackColor = ElectricCyan
                                    )
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Choisir un dépôt existant :",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = SlateMuted
                                )
                                TextButton(
                                    onClick = onRefreshRepos,
                                    enabled = !isPushing
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Actualiser")
                                }
                            }

                            if (connected.repos.isEmpty()) {
                                Text(
                                    text = "Aucun dépôt trouvé sur ce compte. Passez sur « Nouveau dépôt ».",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ForgeAmber
                                )
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 170.dp)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    connected.repos.forEach { repo ->
                                        val isSelected = selectedExistingRepo?.id == repo.id
                                        Surface(
                                            color = if (isSelected) {
                                                ElectricCyan.copy(alpha = 0.14f)
                                            } else {
                                                MidnightSlate
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) ElectricCyan else ForgeBorder,
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .clickable(enabled = !isPushing) {
                                                    selectedExistingRepo = repo
                                                    branchName = repo.defaultBranch
                                                }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = repo.fullName,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (isSelected) {
                                                            ElectricCyan
                                                        } else {
                                                            MaterialTheme.colorScheme.onSurface
                                                        },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "Branche : ${repo.defaultBranch} · ${if (repo.isPrivate) "Privé" else "Public"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = SlateMuted
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (repo.isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                                                    contentDescription = null,
                                                    tint = SlateMuted,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = branchName,
                                onValueChange = { branchName = it },
                                label = { Text("Branche") },
                                singleLine = true,
                                enabled = !isPushing,
                                colors = forgeTextFieldColors(),
                                modifier = Modifier
                                    .weight(0.4f)
                                    .testTag("github_branch_input")
                            )
                            OutlinedTextField(
                                value = commitMessage,
                                onValueChange = { commitMessage = it },
                                label = { Text("Message de commit") },
                                singleLine = true,
                                enabled = !isPushing,
                                colors = forgeTextFieldColors(),
                                modifier = Modifier
                                    .weight(0.6f)
                                    .testTag("github_commit_message_input")
                            )
                        }
                    }

                    // Progress / Error / Success Feedback
                    when (exportState) {
                        is GitHubExportUiState.Loading -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = exportState.stageLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ElectricCyan
                                )
                                LinearProgressIndicator(
                                    progress = { exportState.progress.coerceIn(0f, 1f) },
                                    color = ElectricCyan,
                                    trackColor = MidnightSlate,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                            }
                        }

                        is GitHubExportUiState.Error -> {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = exportState.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }

                        is GitHubExportUiState.Success -> {
                            val res = exportState.result
                            Surface(
                                color = EmeraldBuild.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, EmeraldBuild.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                                    .testTag("github_export_success_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = EmeraldBuild,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Code publié sur GitHub !",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = EmeraldBuild,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = "${res.repoFullName} (${res.branch}) · commit ${res.commitSha.take(7)} · ${res.fileCount} fichiers",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = MonoFontFamily,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Button(
                                            onClick = {
                                                if (GitHubExportService.isValidGitHubWebUrl(res.repoUrl)) {
                                                    runCatching {
                                                        context.startActivity(
                                                            Intent(Intent.ACTION_VIEW, Uri.parse(res.repoUrl))
                                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        )
                                                    }.onFailure {
                                                        onPostMessage("Impossible d'ouvrir le navigateur.")
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = EmeraldBuild,
                                                contentColor = ObsidianNight
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .testTag("github_open_browser_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Ouvrir GitHub", fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(res.repoUrl))
                                                onPostMessage("Lien GitHub copié !")
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .height(48.dp)
                                                .testTag("github_copy_link_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.ContentCopy,
                                                contentDescription = "Copier le lien GitHub",
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copier")
                                        }
                                    }
                                }
                            }
                        }

                        GitHubExportUiState.Idle -> Unit
                    }

                    // Primary Push CTA (when connected)
                    if (connected != null) {
                        val targetOwner: String?
                        val targetRepoName: String
                        if (createNewRepo) {
                            targetOwner = connected.profile.login
                            targetRepoName = newRepoName
                        } else {
                            val parts = selectedExistingRepo?.fullName?.split('/')
                            targetOwner = parts?.getOrNull(0) ?: connected.profile.login
                            targetRepoName = parts?.getOrNull(1) ?: selectedExistingRepo?.name.orEmpty()
                        }

                        val canPush = !isPushing &&
                            targetRepoName.isNotBlank() &&
                            branchName.isNotBlank()

                        Button(
                            onClick = {
                                onPushToGitHub(
                                    targetRepoName,
                                    branchName,
                                    commitMessage,
                                    createNewRepo,
                                    isPrivateRepo,
                                    newRepoDescription,
                                    targetOwner
                                )
                            },
                            enabled = canPush,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianNight
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("github_push_button")
                        ) {
                            if (isPushing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = ObsidianNight,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publication sur GitHub...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (createNewRepo) {
                                        "Créer le dépôt & Publier le code"
                                    } else {
                                        "Publier le code sur GitHub"
                                    },
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
