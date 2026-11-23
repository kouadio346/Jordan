package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.compiler.ApkCompilerEngine
import com.example.data.AiCodeGenerator
import com.example.data.AiKeyVaultRepository
import com.example.data.AppIconAiService
import com.example.data.ChatRepository
import com.example.data.ConversationalArchitectService
import com.example.data.GitHubExportService
import com.example.data.GitHubTokenVault
import com.example.data.IconGenUiState
import com.example.data.IconStylePreset
import com.example.data.MultiAiClient
import com.example.data.ProjectIconStore
import com.example.data.ProjectRepository
import com.example.data.StreamChunk
import com.example.data.SwarmOrchestratorService
import com.example.model.AiApiKeyEntry
import com.example.model.AiProvider
import com.example.model.AiRoutingMode
import com.example.model.AiRoutingSettings
import com.example.model.AiSpecialty
import com.example.model.ApkBuildResult
import com.example.model.AppProject
import com.example.model.AppType
import com.example.model.BuildLogEntry
import com.example.model.ChatMessage
import com.example.model.CodeSuggestion
import com.example.model.GitHubAccountState
import com.example.model.GitHubExportUiState
import com.example.model.KeyTestResult
import com.example.model.LogLevel
import com.example.model.ProjectFile
import com.example.model.SwarmExecutionState
import com.example.model.SwarmPhase
import com.example.model.TargetLanguage
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ProjectsUiState {
    data object Loading : ProjectsUiState
    data class Success(val projects: List<AppProject>) : ProjectsUiState
    data class Error(val message: String) : ProjectsUiState
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data class Generating(val stageLabel: String) : GenerationState
    data class Error(val message: String) : GenerationState
}

sealed interface CompileState {
    data object Idle : CompileState
    data class Compiling(val logs: List<BuildLogEntry>, val progress: Float) : CompileState
    data class Success(val result: ApkBuildResult) : CompileState
    data class Error(val message: String) : CompileState
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudioViewModel(
    private val repository: ProjectRepository,
    private val chatRepository: ChatRepository,
    private val aiKeyVaultRepository: AiKeyVaultRepository,
    private val multiAiClient: MultiAiClient,
    private val aiGenerator: AiCodeGenerator,
    private val architectService: ConversationalArchitectService,
    private val swarmOrchestrator: SwarmOrchestratorService,
    private val compiler: ApkCompilerEngine,
    val iconStore: ProjectIconStore,
    private val iconAiService: AppIconAiService,
    private val gitHubTokenVault: GitHubTokenVault,
    private val gitHubExportService: GitHubExportService,
    private val currentUserId: String
) : ViewModel() {

    val projectsState: StateFlow<ProjectsUiState> = repository.observeProjects(currentUserId)
        .map<List<AppProject>, ProjectsUiState> { ProjectsUiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing projects", rootError)
            emit(ProjectsUiState.Error(rootError.localizedMessage ?: "Erreur de chargement des projets"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = ProjectsUiState.Loading
        )

    private val _activeChatSessionId = MutableStateFlow(GLOBAL_CHAT_SESSION)

    val chatMessages: StateFlow<List<ChatMessage>> = _activeChatSessionId
        .flatMapLatest { sessionId ->
            chatRepository.observeMessages(currentUserId, sessionId)
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    Log.w(TAG, "Error observing chat messages", firestoreError ?: error)
                    emit(emptyList())
                }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = emptyList()
        )

    val aiKeys: StateFlow<List<AiApiKeyEntry>> = aiKeyVaultRepository.keys
    val aiRoutingSettings: StateFlow<AiRoutingSettings> = aiKeyVaultRepository.routingSettings

    private val _testingKeyIds = MutableStateFlow<Set<String>>(emptySet())
    val testingKeyIds: StateFlow<Set<String>> = _testingKeyIds.asStateFlow()

    private val _activeEngineLabel = MutableStateFlow<String?>(null)
    val activeEngineLabel: StateFlow<String?> = _activeEngineLabel.asStateFlow()

    private val _chatStreamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val chatStreamState: StateFlow<ChatStreamState> = _chatStreamState.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationState>(GenerationState.Idle)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _compileState = MutableStateFlow<CompileState>(CompileState.Idle)
    val compileState: StateFlow<CompileState> = _compileState.asStateFlow()

    private val _swarmState = MutableStateFlow(SwarmExecutionState())
    val swarmState: StateFlow<SwarmExecutionState> = _swarmState.asStateFlow()

    val iconVersions: StateFlow<Map<String, Int>> = iconStore.iconVersions

    private val _iconGenState = MutableStateFlow<IconGenUiState>(IconGenUiState.Idle)
    val iconGenState: StateFlow<IconGenUiState> = _iconGenState.asStateFlow()

    private val _gitHubAccountState = MutableStateFlow<GitHubAccountState>(GitHubAccountState.Disconnected)
    val gitHubAccountState: StateFlow<GitHubAccountState> = _gitHubAccountState.asStateFlow()

    private val _gitHubExportState = MutableStateFlow<GitHubExportUiState>(GitHubExportUiState.Idle)
    val gitHubExportState: StateFlow<GitHubExportUiState> = _gitHubExportState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    init {
        restoreSavedGitHubSessionIfPresent()
    }

    private fun restoreSavedGitHubSessionIfPresent() {
        val savedToken = gitHubTokenVault.getToken() ?: return
        viewModelScope.launch {
            _gitHubAccountState.value = GitHubAccountState.Connecting
            val profileRes = gitHubExportService.verifyTokenAndFetchProfile(savedToken)
            profileRes.fold(
                onSuccess = { profile ->
                    gitHubTokenVault.saveCachedProfile(profile)
                    val repos = gitHubExportService.listUserRepositories(savedToken).getOrDefault(emptyList())
                    _gitHubAccountState.value = GitHubAccountState.Connected(profile = profile, repos = repos)
                },
                onFailure = {
                    val cached = gitHubTokenVault.getCachedProfile()
                    _gitHubAccountState.value = if (cached != null) {
                        GitHubAccountState.Connected(profile = cached, repos = emptyList())
                    } else {
                        GitHubAccountState.Disconnected
                    }
                }
            )
        }
    }

    fun connectGitHubToken(rawToken: String) {
        val clean = rawToken.trim()
        if (clean.isBlank()) {
            _gitHubAccountState.value = GitHubAccountState.Error("Veuillez coller un Personal Access Token GitHub.")
            return
        }
        viewModelScope.launch {
            _gitHubAccountState.value = GitHubAccountState.Connecting
            val profileRes = gitHubExportService.verifyTokenAndFetchProfile(clean)
            profileRes.fold(
                onSuccess = { profile ->
                    gitHubTokenVault.saveToken(clean)
                    gitHubTokenVault.saveCachedProfile(profile)
                    val repos = gitHubExportService.listUserRepositories(clean).getOrDefault(emptyList())
                    _gitHubAccountState.value = GitHubAccountState.Connected(profile = profile, repos = repos)
                    _statusMessage.value = "GitHub connecté : @${profile.login}"
                },
                onFailure = { err ->
                    _gitHubAccountState.value = GitHubAccountState.Error(
                        err.localizedMessage ?: "Échec de connexion à GitHub."
                    )
                }
            )
        }
    }

    fun refreshGitHubRepos() {
        val token = gitHubTokenVault.getToken() ?: return
        val currentConnected = _gitHubAccountState.value as? GitHubAccountState.Connected
        viewModelScope.launch {
            val profile = currentConnected?.profile
                ?: gitHubExportService.verifyTokenAndFetchProfile(token).getOrNull()
                ?: run {
                    _gitHubAccountState.value = GitHubAccountState.Error("Session GitHub expirée, reconnectez votre token.")
                    return@launch
                }
            val reposRes = gitHubExportService.listUserRepositories(token)
            reposRes.fold(
                onSuccess = { repos ->
                    _gitHubAccountState.value = GitHubAccountState.Connected(profile = profile, repos = repos)
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Impossible de rafraîchir la liste des dépôts."
                }
            )
        }
    }

    fun disconnectGitHub() {
        gitHubTokenVault.clear()
        _gitHubAccountState.value = GitHubAccountState.Disconnected
        _gitHubExportState.value = GitHubExportUiState.Idle
        _statusMessage.value = "Compte GitHub déconnecté."
    }

    fun pushProjectToGitHub(
        project: AppProject,
        repoName: String,
        branch: String,
        commitMessage: String,
        createRepoFirst: Boolean,
        isPrivate: Boolean,
        repoDescription: String,
        ownerOverride: String? = null
    ) {
        if (_gitHubExportState.value is GitHubExportUiState.Loading) return
        val token = gitHubTokenVault.getToken()
        val connected = _gitHubAccountState.value as? GitHubAccountState.Connected
        if (token.isNullOrBlank() || connected == null) {
            _gitHubExportState.value = GitHubExportUiState.Error("Connectez d'abord votre token GitHub.")
            return
        }
        val targetOwner = ownerOverride?.takeIf { it.isNotBlank() } ?: connected.profile.login

        viewModelScope.launch {
            _gitHubExportState.value = GitHubExportUiState.Loading("Initialisation de l'export GitHub...", 0.04f)
            val iconBytes = runCatching { compiler.resolveProjectLauncherIconBytes(project) }.getOrNull()
            val pushRes = gitHubExportService.pushProjectToGitHub(
                token = token,
                owner = targetOwner,
                repoName = repoName,
                branch = branch,
                commitMessage = commitMessage,
                createRepoFirst = createRepoFirst,
                isPrivate = isPrivate,
                repoDescription = repoDescription,
                project = project,
                launcherIconBytes = iconBytes,
                onProgress = { label, progress ->
                    _gitHubExportState.value = GitHubExportUiState.Loading(label, progress)
                }
            )
            pushRes.fold(
                onSuccess = { result ->
                    _gitHubExportState.value = GitHubExportUiState.Success(result)
                    _statusMessage.value = "Code publié sur github.com/${result.repoFullName} (${result.fileCount} fichiers)"
                    // Refresh repository list cleanly in the background
                    val updatedRepos = gitHubExportService.listUserRepositories(token).getOrNull()
                    if (updatedRepos != null) {
                        _gitHubAccountState.value = connected.copy(repos = updatedRepos)
                    }
                },
                onFailure = { err ->
                    _gitHubExportState.value = GitHubExportUiState.Error(
                        err.localizedMessage ?: "Échec de la publication sur GitHub."
                    )
                }
            )
        }
    }

    fun clearGitHubExportState() {
        if (_gitHubExportState.value !is GitHubExportUiState.Loading) {
            _gitHubExportState.value = GitHubExportUiState.Idle
        }
    }

    fun generateAiIcon(
        projectKey: String,
        appName: String,
        appType: AppType,
        customDescription: String,
        stylePreset: IconStylePreset
    ) {
        if (_iconGenState.value is IconGenUiState.Loading) return
        viewModelScope.launch {
            _iconGenState.value = IconGenUiState.Loading("Génération de l'icône avec Gemini IA (30–60s)...")
            try {
                val bitmap = iconAiService.generateAppIcon(
                    appName = appName,
                    appType = appType,
                    customDescription = customDescription,
                    stylePreset = stylePreset
                )
                iconStore.saveIconBitmap(projectKey, bitmap).getOrThrow()
                _iconGenState.value = IconGenUiState.Success("Nouvelle icône générée et appliquée !")
                _statusMessage.value = "Icône régénérée avec succès."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate AI icon", e)
                _iconGenState.value = IconGenUiState.Error(
                    e.localizedMessage ?: "Impossible de générer l'icône IA."
                )
            }
        }
    }

    fun customizeAiIcon(
        projectKey: String,
        appName: String,
        baseBitmap: Bitmap,
        editInstruction: String,
        stylePreset: IconStylePreset
    ) {
        if (_iconGenState.value is IconGenUiState.Loading) return
        viewModelScope.launch {
            _iconGenState.value = IconGenUiState.Loading("Personnalisation de l'icône par IA (30–60s)...")
            try {
                val bitmap = iconAiService.customizeExistingIcon(
                    baseBitmap = baseBitmap,
                    appName = appName,
                    editInstruction = editInstruction,
                    stylePreset = stylePreset
                )
                iconStore.saveIconBitmap(projectKey, bitmap).getOrThrow()
                _iconGenState.value = IconGenUiState.Success("Icône personnalisée appliquée !")
                _statusMessage.value = "Icône personnalisée enregistrée."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to customize AI icon", e)
                _iconGenState.value = IconGenUiState.Error(
                    e.localizedMessage ?: "Impossible de personnaliser l'icône."
                )
            }
        }
    }

    fun importCustomIcon(projectKey: String, uri: Uri) {
        viewModelScope.launch {
            _iconGenState.value = IconGenUiState.Loading("Importation de l'image...")
            val res = iconStore.importFromUri(projectKey, uri)
            res.fold(
                onSuccess = {
                    _iconGenState.value = IconGenUiState.Success("Icône importée depuis votre galerie !")
                    _statusMessage.value = "Icône importée et adaptée pour l'APK."
                },
                onFailure = { err ->
                    _iconGenState.value = IconGenUiState.Error(
                        err.localizedMessage ?: "Impossible d'importer cette image."
                    )
                }
            )
        }
    }

    fun resetProjectIcon(projectKey: String) {
        iconStore.clearIcon(projectKey)
        _iconGenState.value = IconGenUiState.Idle
        _statusMessage.value = "Icône placeholder par défaut restaurée."
    }

    fun clearIconGenState() {
        if (_iconGenState.value !is IconGenUiState.Loading) {
            _iconGenState.value = IconGenUiState.Idle
        }
    }

    fun resetSwarmState() {
        _swarmState.value = SwarmExecutionState()
    }

    fun prepareSwarmPlan(
        planPrompt: String,
        appType: AppType,
        targetLanguage: TargetLanguage,
        autoLaunchAfterSplit: Boolean = false
    ) {
        val trimmed = planPrompt.trim()
        if (trimmed.isBlank()) {
            _swarmState.value = _swarmState.value.copy(
                phase = SwarmPhase.ERROR,
                errorMessage = "Veuillez saisir le plan du projet à répartir."
            )
            return
        }

        viewModelScope.launch {
            _swarmState.value = SwarmExecutionState(
                phase = SwarmPhase.PLANNING,
                planPrompt = trimmed,
                appType = appType,
                targetLanguage = targetLanguage
            )
            val result = swarmOrchestrator.createDistributionPlan(
                planPrompt = trimmed,
                appType = appType,
                targetLanguage = targetLanguage
            )
            result.fold(
                onSuccess = { plannedState ->
                    _swarmState.value = plannedState
                    if (autoLaunchAfterSplit) {
                        executeCurrentSwarmPlan()
                    }
                },
                onFailure = { err ->
                    _swarmState.value = _swarmState.value.copy(
                        phase = SwarmPhase.ERROR,
                        errorMessage = err.localizedMessage ?: "Échec du découpage du plan."
                    )
                }
            )
        }
    }

    fun launchSwarmExecution() {
        val current = _swarmState.value
        if (current.tasks.isEmpty() || current.phase == SwarmPhase.EXECUTING_PARALLEL) return
        viewModelScope.launch {
            executeCurrentSwarmPlan()
        }
    }

    private suspend fun executeCurrentSwarmPlan() {
        val snapshot = _swarmState.value
        val result = swarmOrchestrator.executePlanInParallel(
            initialState = snapshot,
            onStateUpdate = { updated -> _swarmState.value = updated }
        )
        result.fold(
            onSuccess = { (doneState, unifiedProject) ->
                val saveRes = repository.createProject(unifiedProject)
                saveRes.fold(
                    onSuccess = { docId ->
                        iconStore.promoteDraftToProject(docId)
                        _swarmState.value = doneState.copy(createdProjectId = docId)
                        _statusMessage.value = "Équipe synchronisée : \"${unifiedProject.appName}\" assemblé par ${doneState.tasks.size} agents !"
                    },
                    onFailure = { err ->
                        _swarmState.value = doneState.copy(
                            phase = SwarmPhase.ERROR,
                            errorMessage = err.localizedMessage ?: "Erreur lors de la sauvegarde du projet assemblé."
                        )
                    }
                )
            },
            onFailure = { err ->
                _swarmState.value = _swarmState.value.copy(
                    phase = SwarmPhase.ERROR,
                    errorMessage = err.localizedMessage ?: "Erreur pendant le travail collaboratif."
                )
            }
        )
    }

    fun saveAiKey(
        existingId: String? = null,
        label: String,
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        customBaseUrl: String,
        specialtyOverride: AiSpecialty? = null,
        autoTest: Boolean = true
    ) {
        val saved = aiKeyVaultRepository.addOrUpdateKey(
            existingId = existingId,
            label = label,
            provider = provider,
            apiKey = apiKey,
            modelId = modelId,
            customBaseUrl = customBaseUrl,
            specialtyOverride = specialtyOverride
        )
        _statusMessage.value = "Clé \"${saved.displayTitle()}\" enregistrée et chiffrée localement."
        if (autoTest) {
            testAiKey(saved.id)
        }
    }

    fun toggleAiKeyEnabled(id: String) {
        aiKeyVaultRepository.toggleKeyEnabled(id)
    }

    fun moveAiKeyPriority(id: String, delta: Int) {
        aiKeyVaultRepository.moveKey(id, delta)
    }

    fun deleteAiKey(id: String) {
        aiKeyVaultRepository.deleteKey(id)
        _statusMessage.value = "Clé API supprimée du coffre."
    }

    fun setAiRoutingMode(mode: AiRoutingMode, selectedKeyId: String? = aiRoutingSettings.value.selectedKeyId) {
        aiKeyVaultRepository.setRoutingMode(mode, selectedKeyId)
    }

    fun setFallbackToBuiltIn(enabled: Boolean) {
        aiKeyVaultRepository.setFallbackToBuiltIn(enabled)
    }

    fun testAiKey(keyId: String) {
        val entry = aiKeys.value.firstOrNull { it.id == keyId } ?: return
        viewModelScope.launch {
            _testingKeyIds.value = _testingKeyIds.value + keyId
            try {
                when (val result = multiAiClient.testKeyConnection(entry)) {
                    is KeyTestResult.Success -> {
                        _statusMessage.value = "${entry.displayTitle()} : ${result.message}"
                    }
                    is KeyTestResult.Failure -> {
                        _statusMessage.value = "${entry.displayTitle()} : ${result.errorMessage}"
                    }
                }
            } finally {
                _testingKeyIds.value = _testingKeyIds.value - keyId
            }
        }
    }

    fun testAllAiKeys() {
        val enabled = aiKeys.value.filter { it.isEnabled }
        if (enabled.isEmpty()) {
            _statusMessage.value = "Aucune clé active à tester."
            return
        }
        viewModelScope.launch {
            var okCount = 0
            for (entry in enabled) {
                _testingKeyIds.value = _testingKeyIds.value + entry.id
                try {
                    if (multiAiClient.testKeyConnection(entry) is KeyTestResult.Success) {
                        okCount++
                    }
                } finally {
                    _testingKeyIds.value = _testingKeyIds.value - entry.id
                }
            }
            _statusMessage.value = "Test terminé : $okCount/${enabled.size} clé(s) connectée(s)."
        }
    }

    fun setActiveChatSession(sessionId: String) {
        if (_activeChatSessionId.value != sessionId) {
            _activeChatSessionId.value = sessionId
            _chatStreamState.value = ChatStreamState.Idle
        }
    }

    fun sendChatMessage(
        sessionId: String,
        userText: String,
        activeProject: AppProject?
    ) {
        val trimmed = userText.trim()
        if (trimmed.isBlank() || _chatStreamState.value is ChatStreamState.Streaming) return

        val historySnapshot = chatMessages.value
        viewModelScope.launch {
            _chatStreamState.value = ChatStreamState.Streaming("")
            val userMsg = ChatMessage(
                userId = currentUserId,
                sessionId = sessionId,
                role = "USER",
                text = trimmed
            )
            chatRepository.addMessage(userMsg)

            try {
                var completedTurn: com.example.model.ParsedAiTurn? = null
                architectService.sendMessageStream(
                    historyMessages = historySnapshot,
                    userInput = trimmed,
                    activeProject = activeProject
                ).collect { chunk ->
                    when (chunk) {
                        is StreamChunk.EngineActive -> {
                            _activeEngineLabel.value = if (chunk.failoverUsed) {
                                "${chunk.badge} (Failover)"
                            } else {
                                chunk.badge
                            }
                        }
                        is StreamChunk.Partial -> {
                            _chatStreamState.value = ChatStreamState.Streaming(chunk.accumulatedText)
                        }
                        is StreamChunk.Completed -> {
                            completedTurn = chunk.parsed
                        }
                    }
                }

                val parsed = completedTurn
                if (parsed != null) {
                    val modelMsg = ChatMessage(
                        userId = currentUserId,
                        sessionId = sessionId,
                        role = "MODEL",
                        text = parsed.displayText,
                        suggestions = parsed.suggestions,
                        followUps = parsed.followUps
                    )
                    chatRepository.addMessage(modelMsg)
                    _chatStreamState.value = ChatStreamState.Idle
                } else {
                    _chatStreamState.value = ChatStreamState.Error("Réponse IA incomplète.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Conversational chat failed", e)
                _chatStreamState.value = ChatStreamState.Error(
                    e.localizedMessage ?: "Échec de communication avec l'IA."
                )
            }
        }
    }

    fun clearChatSession(sessionId: String) {
        viewModelScope.launch {
            _chatStreamState.value = ChatStreamState.Idle
            val res = chatRepository.clearSession(currentUserId, sessionId)
            res.fold(
                onSuccess = { _statusMessage.value = "Nouvelle conversation démarrée." },
                onFailure = { err -> _statusMessage.value = err.localizedMessage ?: "Erreur lors de l'effacement." }
            )
        }
    }

    fun applySuggestionToProject(project: AppProject, suggestion: CodeSuggestion) {
        if (project.id.isBlank()) return
        val existingFiles = project.files.toMutableList()
        val matchIdx = existingFiles.indexOfFirst {
            it.path.equals(suggestion.filePath, ignoreCase = true)
        }
        val newFile = ProjectFile(
            path = suggestion.filePath,
            language = suggestion.language,
            content = suggestion.code
        )
        if (matchIdx >= 0) {
            existingFiles[matchIdx] = newFile
        } else if (existingFiles.size < 12) {
            existingFiles.add(newFile)
        } else {
            existingFiles[existingFiles.lastIndex] = newFile
        }

        val updated = project.copy(
            files = existingFiles,
            versionCode = project.versionCode + 1L
        )
        viewModelScope.launch {
            val res = repository.updateProject(project.id, updated)
            res.fold(
                onSuccess = {
                    _statusMessage.value = "Fichier \"${suggestion.filePath.substringAfterLast('/')}\" appliqué au projet !"
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Impossible d'appliquer la suggestion."
                }
            )
        }
    }

    fun createProjectFromConversation(
        messages: List<ChatMessage>,
        singleSuggestion: CodeSuggestion? = null,
        onCreated: (String) -> Unit
    ) {
        val conversationSummary = buildString {
            messages.takeLast(10).forEach { msg ->
                append("${msg.role}: ${msg.text.take(600)}\n")
            }
            if (singleSuggestion != null) {
                append("\nCode clé à intégrer (${singleSuggestion.filePath} - ${singleSuggestion.language}):\n${singleSuggestion.code.take(2500)}")
            }
        }.ifBlank { singleSuggestion?.code ?: "Application Android moderne" }

        val detectedLang = when (singleSuggestion?.language?.lowercase()) {
            "c#", "csharp" -> TargetLanguage.CSHARP_UNITY
            "gdscript" -> TargetLanguage.GDSCRIPT_GODOT
            "c++", "cpp" -> TargetLanguage.CPP_UNREAL
            "lua" -> TargetLanguage.LUA_LOVE2D
            "dart" -> TargetLanguage.DART_FLUTTER
            "python" -> TargetLanguage.PYTHON_KIVY
            "java" -> TargetLanguage.JAVA
            "html", "javascript" -> TargetLanguage.HTML5_WEBGL
            else -> TargetLanguage.KOTLIN
        }

        createProjectWithAi(
            prompt = ProjectRepository.trimToUtf8Bytes(
                "Construis un projet Android complet et compilable en APK basé sur cette conception :\n$conversationSummary",
                1950
            ),
            appType = AppType.ANDROID_APP,
            targetLanguage = detectedLang,
            requestedAppName = "",
            requestedPackageName = "",
            onCreated = onCreated
        )
    }

    fun createProjectWithAi(
        prompt: String,
        appType: AppType,
        targetLanguage: TargetLanguage,
        requestedAppName: String,
        requestedPackageName: String,
        onCreated: (String) -> Unit
    ) {
        if (prompt.isBlank()) {
            _generationState.value = GenerationState.Error("Veuillez décrire l'application ou le jeu à créer.")
            return
        }
        viewModelScope.launch {
            _generationState.value = GenerationState.Generating(
                "L'IA génère l'architecture ${targetLanguage.displayName} et le bundle exécutable..."
            )
            val aiResult = aiGenerator.generateProject(
                userPrompt = prompt,
                appType = appType,
                targetLanguage = targetLanguage,
                requestedAppName = requestedAppName,
                requestedPackageName = requestedPackageName
            )
            aiResult.fold(
                onSuccess = { outcome ->
                    val generated = outcome.project
                    _activeEngineLabel.value = outcome.engineBadge
                    _generationState.value = GenerationState.Generating("Sauvegarde du projet dans votre studio...")
                    val saveResult = repository.createProject(generated)
                    saveResult.fold(
                        onSuccess = { docId ->
                            iconStore.promoteDraftToProject(docId)
                            _generationState.value = GenerationState.Idle
                            _compileState.value = CompileState.Idle
                            val suffix = if (outcome.failoverUsed) " (failover ${outcome.engineBadge})" else " (${outcome.engineBadge})"
                            _statusMessage.value = "Projet \"${generated.appName}\" créé$suffix !"
                            onCreated(docId)
                        },
                        onFailure = { err ->
                            _generationState.value = GenerationState.Error(
                                err.localizedMessage ?: "Impossible de sauvegarder le projet."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _generationState.value = GenerationState.Error(
                        err.localizedMessage ?: "La génération IA a échoué."
                    )
                }
            )
        }
    }

    fun refineProjectWithAi(project: AppProject, instruction: String) {
        if (instruction.isBlank() || project.id.isBlank()) return
        viewModelScope.launch {
            _generationState.value = GenerationState.Generating("L'IA met à jour le code source et l'aperçu...")
            val aiResult = aiGenerator.refineProject(project, instruction)
            aiResult.fold(
                onSuccess = { outcome ->
                    val updated = outcome.project
                    _activeEngineLabel.value = outcome.engineBadge
                    val saveResult = repository.updateProject(project.id, updated)
                    saveResult.fold(
                        onSuccess = {
                            _generationState.value = GenerationState.Idle
                            _compileState.value = CompileState.Idle
                            _statusMessage.value = "Code mis à jour par ${outcome.engineBadge} (v${updated.versionCode})."
                        },
                        onFailure = { err ->
                            _generationState.value = GenerationState.Error(
                                err.localizedMessage ?: "Erreur lors de la sauvegarde."
                            )
                        }
                    )
                },
                onFailure = { err ->
                    _generationState.value = GenerationState.Error(
                        err.localizedMessage ?: "Échec de la modification IA."
                    )
                }
            )
        }
    }

    fun saveManualProjectChanges(project: AppProject) {
        if (project.id.isBlank()) return
        viewModelScope.launch {
            val res = repository.updateProject(project.id, project)
            res.fold(
                onSuccess = {
                    _statusMessage.value = "Modifications enregistrées."
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Erreur de sauvegarde."
                }
            )
        }
    }

    fun compileProjectApk(project: AppProject) {
        viewModelScope.launch {
            val liveLogs = mutableListOf<BuildLogEntry>()
            _compileState.value = CompileState.Compiling(liveLogs, 0.02f)
            try {
                val result = compiler.compileApk(project) { entry, progress ->
                    liveLogs.add(entry)
                    _compileState.value = CompileState.Compiling(liveLogs.toList(), progress)
                }
                _compileState.value = CompileState.Success(result)
                if (project.id.isNotBlank()) {
                    repository.recordBuildResult(
                        projectId = project.id,
                        apkName = result.apkName,
                        sizeBytes = result.sizeBytes,
                        sha256 = result.sha256
                    )
                }
                _statusMessage.value = "APK compilé et signé : ${result.apkName}"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "APK compilation failed", e)
                liveLogs.add(BuildLogEntry("ERROR", e.localizedMessage ?: "Échec de compilation", LogLevel.ERROR))
                _compileState.value = CompileState.Error(e.localizedMessage ?: "Échec de compilation APK.")
            }
        }
    }

    fun exportSourcesZip(project: AppProject, onReady: (File) -> Unit) {
        viewModelScope.launch {
            try {
                val zipFile = compiler.exportSourceZip(project)
                onReady(zipFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _statusMessage.value = e.localizedMessage ?: "Erreur lors de l'export ZIP."
            }
        }
    }

    fun deleteProject(projectId: String, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val res = repository.deleteProject(projectId)
            res.fold(
                onSuccess = {
                    _statusMessage.value = "Projet supprimé."
                    onDeleted()
                },
                onFailure = { err ->
                    _statusMessage.value = err.localizedMessage ?: "Impossible de supprimer le projet."
                }
            )
        }
    }

    fun postMessage(msg: String) {
        _statusMessage.value = msg
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearGenerationError() {
        if (_generationState.value is GenerationState.Error) {
            _generationState.value = GenerationState.Idle
        }
    }

    fun resetCompileState() {
        _compileState.value = CompileState.Idle
    }

    companion object {
        private const val TAG = "StudioViewModel"
        const val GLOBAL_CHAT_SESSION = "global_architect"
    }
}
