package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.compiler.ApkCompilerEngine
import com.example.data.AiCodeGenerator
import com.example.data.AiKeyVaultRepository
import com.example.data.AppIconAiService
import com.example.data.ChatRepository
import com.example.data.ConversationalArchitectService
import com.example.data.GitHubExportService
import com.example.data.GitHubTokenVault
import com.example.data.MultiAiClient
import com.example.data.ProjectIconStore
import com.example.data.ProjectRepository
import com.example.data.SwarmOrchestratorService
import com.example.firebase.attemptAutoSignIn
import com.example.firebase.configureAppCheck
import com.example.firebase.signOut
import com.example.model.AiRoutingMode
import com.example.model.AppType
import com.example.model.StarterTemplates
import com.example.model.TargetLanguage
import com.example.ui.AiKeysManagerScreen
import com.example.ui.AuthScreen
import com.example.ui.ConversationalArchitectScreen
import com.example.ui.CreateProjectScreen
import com.example.ui.HomeDashboardScreen
import com.example.ui.ProjectsUiState
import com.example.ui.StudioScreen
import com.example.ui.StudioViewModel
import com.example.ui.SwarmTeamScreen
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ForgeApkTheme
import com.example.ui.theme.ObsidianNight
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

const val ROUTE_DASHBOARD = "dashboard"
const val ROUTE_CREATE = "create"
const val ROUTE_ARCHITECT = "architect"
const val ROUTE_SWARM = "swarm"
const val ROUTE_AI_KEYS = "ai_keys"
const val ROUTE_STUDIO = "studio"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure App Check with launch intent before any UI or Firebase operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            ForgeApkTheme(darkTheme = true) {
                Surface(
                    color = ObsidianNight,
                    modifier = Modifier.fillMaxSize()
                ) {
                    ForgeApkApp()
                }
            }
        }
    }
}

@Composable
private fun ForgeApkApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    var autoSignInChecked by remember { mutableStateOf(Firebase.auth.currentUser != null) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    LaunchedEffect(Unit) {
        if (!autoSignInChecked) {
            attemptAutoSignIn(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                    autoSignInChecked = true
                },
                onUnauthenticated = {
                    autoSignInChecked = true
                },
                scope = scope
            )
        }
    }

    if (!autoSignInChecked && currentUser == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ElectricCyan)
        }
    } else if (currentUser == null) {
        AuthScreen(
            onAuthSuccess = {
                currentUser = Firebase.auth.currentUser
            }
        )
    } else {
        val uid = currentUser!!.uid
        val userEmail = currentUser?.email
        key(uid) {
            AuthenticatedForgeStudio(
                currentUserId = uid,
                userEmail = userEmail,
                onSignOut = {
                    signOut(credentialManager, scope) {
                        currentUser = null
                    }
                }
            )
        }
    }
}

@Composable
private fun AuthenticatedForgeStudio(
    currentUserId: String,
    userEmail: String?,
    onSignOut: () -> Unit
) {
    val viewModel: StudioViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                val keyVault = AiKeyVaultRepository(app, currentUserId)
                val multiAiClient = MultiAiClient(keyVault)
                StudioViewModel(
                    repository = ProjectRepository(db),
                    chatRepository = ChatRepository(db),
                    aiKeyVaultRepository = keyVault,
                    multiAiClient = multiAiClient,
                    aiGenerator = AiCodeGenerator(multiAiClient),
                    architectService = ConversationalArchitectService(multiAiClient),
                    swarmOrchestrator = SwarmOrchestratorService(multiAiClient, keyVault),
                    compiler = ApkCompilerEngine(app),
                    iconStore = ProjectIconStore(app),
                    iconAiService = AppIconAiService(),
                    gitHubTokenVault = GitHubTokenVault(app, currentUserId),
                    gitHubExportService = GitHubExportService(),
                    currentUserId = currentUserId
                )
            }
        }
    )

    val navController = rememberNavController()
    val projectsState by viewModel.projectsState.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val compileState by viewModel.compileState.collectAsStateWithLifecycle()
    val swarmState by viewModel.swarmState.collectAsStateWithLifecycle()
    val iconVersions by viewModel.iconVersions.collectAsStateWithLifecycle()
    val iconGenState by viewModel.iconGenState.collectAsStateWithLifecycle()
    val gitHubAccountState by viewModel.gitHubAccountState.collectAsStateWithLifecycle()
    val gitHubExportState by viewModel.gitHubExportState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val chatStreamState by viewModel.chatStreamState.collectAsStateWithLifecycle()
    val aiKeys by viewModel.aiKeys.collectAsStateWithLifecycle()
    val aiRoutingSettings by viewModel.aiRoutingSettings.collectAsStateWithLifecycle()
    val testingKeyIds by viewModel.testingKeyIds.collectAsStateWithLifecycle()
    val activeEngineLabel by viewModel.activeEngineLabel.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()

    val openAiKeys = { navController.navigate(ROUTE_AI_KEYS) }
    val selectPoolMode = { viewModel.setAiRoutingMode(AiRoutingMode.AUTO_FAILOVER_POOL, null) }
    val selectBuiltInMode = { viewModel.setAiRoutingMode(AiRoutingMode.BUILTIN_FIREBASE, null) }
    val selectSpecificKey: (String) -> Unit = { keyId ->
        viewModel.setAiRoutingMode(AiRoutingMode.SINGLE_KEY, keyId)
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_DASHBOARD
    ) {
        composable(ROUTE_DASHBOARD) {
            HomeDashboardScreen(
                userEmail = userEmail,
                projectsState = projectsState,
                aiKeys = aiKeys,
                aiRoutingSettings = aiRoutingSettings,
                activeEngineLabel = activeEngineLabel,
                iconStore = viewModel.iconStore,
                iconVersions = iconVersions,
                statusMessage = statusMessage,
                onClearStatusMessage = viewModel::clearStatusMessage,
                onCreateNewClick = {
                    viewModel.clearGenerationError()
                    navController.navigate(ROUTE_CREATE)
                },
                onOpenArchitectChat = {
                    viewModel.setActiveChatSession(StudioViewModel.GLOBAL_CHAT_SESSION)
                    navController.navigate(ROUTE_ARCHITECT)
                },
                onOpenSwarmTeam = {
                    navController.navigate(ROUTE_SWARM)
                },
                onOpenAiKeys = openAiKeys,
                onSelectPoolMode = selectPoolMode,
                onSelectBuiltInMode = selectBuiltInMode,
                onSelectSpecificKey = selectSpecificKey,
                onTemplateClick = { template ->
                    viewModel.clearGenerationError()
                    navController.navigate("$ROUTE_CREATE?templateId=${template.id}")
                },
                onProjectClick = { project ->
                    viewModel.resetCompileState()
                    viewModel.setActiveChatSession("project_${project.id}")
                    navController.navigate("$ROUTE_STUDIO/${project.id}")
                },
                onSignOutClick = onSignOut
            )
        }

        composable(ROUTE_AI_KEYS) {
            AiKeysManagerScreen(
                keys = aiKeys,
                routingSettings = aiRoutingSettings,
                testingKeyIds = testingKeyIds,
                statusMessage = statusMessage,
                onClearStatusMessage = viewModel::clearStatusMessage,
                onBackClick = { navController.popBackStack() },
                onSaveKey = { existingId, label, provider, apiKey, modelId, customUrl, specialtyOverride ->
                    viewModel.saveAiKey(
                        existingId = existingId,
                        label = label,
                        provider = provider,
                        apiKey = apiKey,
                        modelId = modelId,
                        customBaseUrl = customUrl,
                        specialtyOverride = specialtyOverride
                    )
                },
                onToggleKeyEnabled = viewModel::toggleAiKeyEnabled,
                onMoveKeyPriority = viewModel::moveAiKeyPriority,
                onDeleteKey = viewModel::deleteAiKey,
                onTestKey = viewModel::testAiKey,
                onTestAllKeys = viewModel::testAllAiKeys,
                onSetRoutingMode = viewModel::setAiRoutingMode,
                onSetFallbackToBuiltIn = viewModel::setFallbackToBuiltIn
            )
        }

        composable(ROUTE_ARCHITECT) {
            LaunchedEffect(Unit) {
                viewModel.setActiveChatSession(StudioViewModel.GLOBAL_CHAT_SESSION)
            }
            ConversationalArchitectScreen(
                messages = chatMessages,
                streamState = chatStreamState,
                aiKeys = aiKeys,
                aiRoutingSettings = aiRoutingSettings,
                activeEngineLabel = activeEngineLabel,
                statusMessage = statusMessage,
                onClearStatusMessage = viewModel::clearStatusMessage,
                onBackClick = { navController.popBackStack() },
                onOpenAiKeys = openAiKeys,
                onSelectPoolMode = selectPoolMode,
                onSelectBuiltInMode = selectBuiltInMode,
                onSelectSpecificKey = selectSpecificKey,
                onSendMessage = { prompt ->
                    viewModel.sendChatMessage(
                        sessionId = StudioViewModel.GLOBAL_CHAT_SESSION,
                        userText = prompt,
                        activeProject = null
                    )
                },
                onClearConversation = {
                    viewModel.clearChatSession(StudioViewModel.GLOBAL_CHAT_SESSION)
                },
                onApplySuggestion = { suggestion ->
                    viewModel.createProjectFromConversation(
                        messages = chatMessages,
                        singleSuggestion = suggestion,
                        onCreated = { docId ->
                            viewModel.setActiveChatSession("project_$docId")
                            navController.navigate("$ROUTE_STUDIO/$docId") {
                                popUpTo(ROUTE_DASHBOARD)
                            }
                        }
                    )
                },
                onBuildApkFromConversation = {
                    viewModel.createProjectFromConversation(
                        messages = chatMessages,
                        singleSuggestion = null,
                        onCreated = { docId ->
                            viewModel.setActiveChatSession("project_$docId")
                            navController.navigate("$ROUTE_STUDIO/$docId") {
                                popUpTo(ROUTE_DASHBOARD)
                            }
                        }
                    )
                },
                onLaunchPlanInSwarm = { digest ->
                    viewModel.prepareSwarmPlan(
                        planPrompt = digest,
                        appType = AppType.ANDROID_APP,
                        targetLanguage = TargetLanguage.AUTO,
                        autoLaunchAfterSplit = false
                    )
                    navController.navigate("$ROUTE_SWARM?plan=${Uri.encode(digest)}")
                },
                onPostMessage = viewModel::postMessage
            )
        }

        composable(
            route = "$ROUTE_SWARM?plan={plan}",
            arguments = listOf(
                navArgument("plan") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val encodedPlan = backStackEntry.arguments?.getString("plan")
            val decodedPlan = encodedPlan?.let { Uri.decode(it) }
            SwarmTeamScreen(
                initialPlanText = decodedPlan,
                aiKeys = aiKeys,
                swarmState = swarmState,
                statusMessage = statusMessage,
                onClearStatusMessage = viewModel::clearStatusMessage,
                onBackClick = { navController.popBackStack() },
                onOpenAiKeys = openAiKeys,
                onSplitPlanOnly = { plan, appType, lang ->
                    viewModel.prepareSwarmPlan(
                        planPrompt = plan,
                        appType = appType,
                        targetLanguage = lang,
                        autoLaunchAfterSplit = false
                    )
                },
                onSplitAndRunTogether = { plan, appType, lang ->
                    viewModel.prepareSwarmPlan(
                        planPrompt = plan,
                        appType = appType,
                        targetLanguage = lang,
                        autoLaunchAfterSplit = true
                    )
                },
                onLaunchPreparedPlan = viewModel::launchSwarmExecution,
                onResetSwarm = viewModel::resetSwarmState,
                onOpenCreatedProject = { docId ->
                    viewModel.setActiveChatSession("project_$docId")
                    navController.navigate("$ROUTE_STUDIO/$docId") {
                        popUpTo(ROUTE_DASHBOARD)
                    }
                }
            )
        }

        composable(
            route = "$ROUTE_CREATE?templateId={templateId}",
            arguments = listOf(
                navArgument("templateId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val templateId = backStackEntry.arguments?.getString("templateId")
            val template = StarterTemplates.catalog.firstOrNull { it.id == templateId }

            CreateProjectScreen(
                initialTemplate = template,
                generationState = generationState,
                aiKeys = aiKeys,
                aiRoutingSettings = aiRoutingSettings,
                activeEngineLabel = activeEngineLabel,
                iconStore = viewModel.iconStore,
                iconVersions = iconVersions,
                iconGenState = iconGenState,
                onGenerateDraftIcon = { name, type, prompt, style ->
                    viewModel.generateAiIcon(ProjectIconStore.DRAFT_PROJECT_KEY, name, type, prompt, style)
                },
                onCustomizeDraftIcon = { name, baseBmp, edit, style ->
                    viewModel.customizeAiIcon(ProjectIconStore.DRAFT_PROJECT_KEY, name, baseBmp, edit, style)
                },
                onImportDraftIcon = { uri ->
                    viewModel.importCustomIcon(ProjectIconStore.DRAFT_PROJECT_KEY, uri)
                },
                onResetDraftIcon = {
                    viewModel.resetProjectIcon(ProjectIconStore.DRAFT_PROJECT_KEY)
                },
                onClearIconError = viewModel::clearIconGenState,
                onOpenAiKeys = openAiKeys,
                onSelectPoolMode = selectPoolMode,
                onSelectBuiltInMode = selectBuiltInMode,
                onSelectSpecificKey = selectSpecificKey,
                onClearError = viewModel::clearGenerationError,
                onBackClick = { navController.popBackStack() },
                onLaunchInSwarm = { prompt, appType, language ->
                    viewModel.prepareSwarmPlan(
                        planPrompt = prompt,
                        appType = appType,
                        targetLanguage = language,
                        autoLaunchAfterSplit = true
                    )
                    navController.navigate("$ROUTE_SWARM?plan=${Uri.encode(prompt)}")
                },
                onGenerateClick = { prompt, appType, language, appName, packageName ->
                    viewModel.createProjectWithAi(
                        prompt = prompt,
                        appType = appType,
                        targetLanguage = language,
                        requestedAppName = appName,
                        requestedPackageName = packageName,
                        onCreated = { docId ->
                            viewModel.setActiveChatSession("project_$docId")
                            navController.navigate("$ROUTE_STUDIO/$docId") {
                                popUpTo(ROUTE_DASHBOARD)
                            }
                        }
                    )
                }
            )
        }

        composable(
            route = "$ROUTE_STUDIO/{projectId}",
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: ""
            val projects = (projectsState as? ProjectsUiState.Success)?.projects.orEmpty()
            val project = projects.firstOrNull { it.id == projectId }

            LaunchedEffect(projectId) {
                if (projectId.isNotBlank()) {
                    viewModel.setActiveChatSession("project_$projectId")
                }
            }

            if (project == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            } else {
                val sessionId = "project_${project.id}"
                StudioScreen(
                    project = project,
                    generationState = generationState,
                    compileState = compileState,
                    chatMessages = chatMessages,
                    chatStreamState = chatStreamState,
                    aiKeys = aiKeys,
                    aiRoutingSettings = aiRoutingSettings,
                    activeEngineLabel = activeEngineLabel,
                    iconStore = viewModel.iconStore,
                    iconVersions = iconVersions,
                    iconGenState = iconGenState,
                    onGenerateProjectIcon = { name, type, prompt, style ->
                        viewModel.generateAiIcon(project.id, name, type, prompt, style)
                    },
                    onCustomizeProjectIcon = { name, baseBmp, edit, style ->
                        viewModel.customizeAiIcon(project.id, name, baseBmp, edit, style)
                    },
                    onImportProjectIcon = { uri ->
                        viewModel.importCustomIcon(project.id, uri)
                    },
                    onResetProjectIcon = {
                        viewModel.resetProjectIcon(project.id)
                    },
                    onClearIconError = viewModel::clearIconGenState,
                    statusMessage = statusMessage,
                    onClearStatusMessage = viewModel::clearStatusMessage,
                    onBackClick = { navController.popBackStack() },
                    onOpenAiKeys = openAiKeys,
                    onSelectPoolMode = selectPoolMode,
                    onSelectBuiltInMode = selectBuiltInMode,
                    onSelectSpecificKey = selectSpecificKey,
                    onRefineWithAi = { instruction ->
                        viewModel.refineProjectWithAi(project, instruction)
                    },
                    onSendChatMessage = { msg ->
                        viewModel.sendChatMessage(
                            sessionId = sessionId,
                            userText = msg,
                            activeProject = project
                        )
                    },
                    onClearChat = {
                        viewModel.clearChatSession(sessionId)
                    },
                    onApplySuggestion = { suggestion ->
                        viewModel.applySuggestionToProject(project, suggestion)
                    },
                    onSaveProjectChanges = { updated ->
                        viewModel.saveManualProjectChanges(updated)
                    },
                    onCompileApk = {
                        viewModel.compileProjectApk(project)
                    },
                    onExportSourcesZip = { onReady ->
                        viewModel.exportSourcesZip(project, onReady)
                    },
                    gitHubAccountState = gitHubAccountState,
                    gitHubExportState = gitHubExportState,
                    onConnectGitHubToken = viewModel::connectGitHubToken,
                    onRefreshGitHubRepos = viewModel::refreshGitHubRepos,
                    onDisconnectGitHub = viewModel::disconnectGitHub,
                    onPushProjectToGitHub = { repoName, branch, commitMsg, createRepoFirst, isPrivate, repoDesc, ownerOverride ->
                        viewModel.pushProjectToGitHub(
                            project = project,
                            repoName = repoName,
                            branch = branch,
                            commitMessage = commitMsg,
                            createRepoFirst = createRepoFirst,
                            isPrivate = isPrivate,
                            repoDescription = repoDesc,
                            ownerOverride = ownerOverride
                        )
                    },
                    onClearGitHubExportError = viewModel::clearGitHubExportState,
                    onDeleteProject = {
                        viewModel.deleteProject(project.id) {
                            navController.popBackStack(ROUTE_DASHBOARD, inclusive = false)
                        }
                    },
                    onPostMessage = viewModel::postMessage
                )
            }
        }
    }
}
