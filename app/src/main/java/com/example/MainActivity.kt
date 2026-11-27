package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ai.FirebaseAiModelRouter
import com.example.auth.authStateFlow
import com.example.auth.signOut
import com.example.data.AgniRepository
import com.example.data.CustomModelConfigStore
import com.example.firebase.configureAppCheck
import com.example.ui.components.AgniSidebarContent
import com.example.ui.screens.ArchitectureFlowScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChatHistoryScreen
import com.example.ui.screens.ChatWorkspaceScreen
import com.example.ui.screens.MediaStudioScreen
import com.example.ui.screens.MemoryScreen
import com.example.ui.screens.SettingsAndFeedbackScreen
import com.example.ui.screens.WorkspaceHubScreen
import com.example.ui.theme.AgniTheme
import com.example.viewmodel.AgniDestination
import com.example.viewmodel.AgniViewModel
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1 (MANDATORY): Configure Firebase App Check with launch intent before UI/ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            AgniRootApp()
        }
    }
}

@Composable
private fun AgniRootApp() {
    val context = LocalContext.current
    val isFirebaseReady = remember(context) {
        FirebaseApp.getApps(context).isNotEmpty()
    }
    if (!isFirebaseReady) {
        AgniTheme(themeMode = "dark") {
            androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "Agni AI — Firebase Setup Required",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 12.dp))
                    androidx.compose.material3.Text(
                        text = "This APK was built without app/google-services.json. Add GOOGLE_SERVICES_JSON in GitHub Actions Secrets and rebuild the APK to enable Google Sign-In, Cloud Firestore, and Gemini AI.",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        return
    }

    val auth = Firebase.auth
    val currentUser by auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = auth.currentUser)
    var pendingAccountSwitch by remember { mutableStateOf(false) }

    val user = currentUser
    if (user == null) {
        AgniTheme(themeMode = "dark") {
            AuthScreen(
                autoLaunchPicker = pendingAccountSwitch,
                onPickerConsumed = { pendingAccountSwitch = false },
                onAuthSuccess = { pendingAccountSwitch = false }
            )
        }
    } else {
        key(user.uid) {
            AgniAuthenticatedShell(
                user = user,
                onRequestAccountSwitch = { pendingAccountSwitch = true }
            )
        }
    }
}

@Composable
private fun AgniAuthenticatedShell(
    user: FirebaseUser,
    onRequestAccountSwitch: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: AgniViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                AgniViewModel(
                    appContext = app.applicationContext,
                    repository = AgniRepository(db),
                    aiRouter = FirebaseAiModelRouter(),
                    customModelStore = CustomModelConfigStore(app, user.uid),
                    currentUserId = user.uid,
                    initialDisplayName = user.displayName ?: "Agni Explorer",
                    initialEmail = user.email,
                    initialPhotoUrl = user.photoUrl?.toString()
                )
            }
        }
    )

    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val chatsState by viewModel.chatsState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val filesState by viewModel.filesState.collectAsStateWithLifecycle()
    val memoriesState by viewModel.memoriesState.collectAsStateWithLifecycle()
    val feedbackState by viewModel.feedbackState.collectAsStateWithLifecycle()
    val usage by viewModel.usage.collectAsStateWithLifecycle()
    val pipelineTrace by viewModel.pipelineTrace.collectAsStateWithLifecycle()
    val modelCatalog by viewModel.modelCatalog.collectAsStateWithLifecycle()
    val customEndpointConfig by viewModel.customEndpointConfig.collectAsStateWithLifecycle()
    val customTestState by viewModel.customTestState.collectAsStateWithLifecycle()
    val mediaStudioState by viewModel.mediaStudio.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(composer.statusSnackbar) {
        composer.statusSnackbar?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    AgniTheme(themeMode = settings.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isExpanded = maxWidth >= 840.dp
            val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

            val displayName = user.displayName?.takeIf { it.isNotBlank() } ?: "Agni Explorer"

            val mainContent: @Composable (Boolean) -> Unit = { showDrawerButton ->
                Scaffold(
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(bottom = innerPadding.calculateBottomPadding().coerceAtMost(0.dp))
                    ) {
                        when (composer.currentDestination) {
                            AgniDestination.CHAT -> {
                                ChatWorkspaceScreen(
                                    composer = composer,
                                    messagesState = messagesState,
                                    modelCatalog = modelCatalog,
                                    showDrawerButton = showDrawerButton,
                                    onOpenDrawer = { scope.launch { drawerState.open() } },
                                    onNewChat = { viewModel.startNewChat() },
                                    onSelectMode = { viewModel.selectMode(it) },
                                    onSelectModel = { viewModel.selectModel(it) },
                                    onSelectLanguage = { viewModel.selectLanguage(it) },
                                    onSelectTranslationTarget = { viewModel.selectTranslationTarget(it) },
                                    onToggleTransliteration = { viewModel.toggleTransliteration(it) },
                                    onSelectVoiceGender = { viewModel.selectVoiceGender(it) },
                                    onToggleAutoSpeakReplies = { viewModel.toggleAutoSpeakReplies(it) },
                                    onDraftChange = { viewModel.updateDraft(it) },
                                    onAttachFile = { viewModel.attachFile(it) },
                                    onClearAttachment = { viewModel.clearAttachment() },
                                    onVoiceListeningChanged = { viewModel.setVoiceListening(it) },
                                    onVoiceResult = { viewModel.onVoiceFinalTranscript(it) },
                                    onVoiceConversationResult = { viewModel.sendVoiceConversationTranscript(it) },
                                    onSendMessage = { viewModel.sendMessage(it) },
                                    onStopGeneration = { viewModel.stopGeneration() },
                                    onShowError = { viewModel.showError(it) },
                                    onClearError = { viewModel.clearError() },
                                    onOpenFeedback = { viewModel.navigateTo(AgniDestination.SETTINGS) },
                                    onOpenMediaStudio = { viewModel.navigateTo(AgniDestination.MEDIA_STUDIO) },
                                    onDeleteCurrentChat = { viewModel.deleteActiveChat() },
                                    onDeleteMessage = { viewModel.deleteMessage(it) },
                                    onNavigateDestination = { viewModel.navigateTo(it) }
                                )
                            }
                            AgniDestination.HISTORY -> {
                                ChatHistoryScreen(
                                    chatsState = chatsState,
                                    activeChatId = composer.activeChatId,
                                    searchQuery = composer.historySearchQuery,
                                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                    onBack = { viewModel.navigateTo(AgniDestination.CHAT) },
                                    onNewChat = { viewModel.startNewChat() },
                                    onSelectChat = { viewModel.selectChat(it) },
                                    onTogglePin = { viewModel.togglePinChat(it) },
                                    onDeleteChat = { viewModel.deleteChat(it) },
                                    onClearAllChats = { viewModel.clearAllChats() }
                                )
                            }
                            AgniDestination.MEDIA_STUDIO -> {
                                MediaStudioScreen(
                                    state = mediaStudioState,
                                    onBack = { viewModel.navigateTo(AgniDestination.CHAT) },
                                    onSearchImages = { viewModel.searchStudioImages(it) },
                                    onSelectSearchResultForEdit = { viewModel.selectSearchResultForEdit(it) },
                                    onDownloadSearchResult = { viewModel.downloadSearchResult(it) },
                                    onAddSearchResultToVideo = { viewModel.addSearchResultToVideo(it) },
                                    onSendBitmapToChat = { bmp, title -> viewModel.sendBitmapToChat(bmp, title) },
                                    onSendSearchResultToChat = { viewModel.sendSearchResultToChat(it) },
                                    onGenerateAiImage = { viewModel.generateAiImage(it) },
                                    onAiEditCurrentImage = { viewModel.aiEditCurrentImage(it) },
                                    onSetEditorBitmap = { bmp, title -> viewModel.setEditorBitmap(bmp, title) },
                                    onSaveEditedBitmap = { viewModel.saveEditedBitmap(it) },
                                    onAddBitmapToVideoSlides = { viewModel.addBitmapToVideoSlides(it) },
                                    onRemoveVideoSlide = { viewModel.removeVideoSlide(it) },
                                    onClearVideoSlides = { viewModel.clearVideoSlides() },
                                    onExportSlideshowVideo = { sec, title -> viewModel.exportSlideshowVideo(sec, title) },
                                    onTrimVideo = { uri, s, e -> viewModel.trimStudioVideo(uri, s, e) },
                                    onClearStudioMessage = { viewModel.clearStudioMessage() }
                                )
                            }
                            AgniDestination.HUB -> {
                                WorkspaceHubScreen(
                                    usage = usage,
                                    filesState = filesState,
                                    onBack = { viewModel.navigateTo(AgniDestination.CHAT) },
                                    onLaunchMode = { mode -> viewModel.startNewChat(mode) },
                                    onDeleteFileRecord = { viewModel.deleteFileRecord(it) }
                                )
                            }
                            AgniDestination.ARCHITECTURE -> {
                                ArchitectureFlowScreen(
                                    pipelineTrace = pipelineTrace,
                                    isCustomEndpointLive = customEndpointConfig.isConfigured,
                                    onBack = { viewModel.navigateTo(AgniDestination.CHAT) },
                                    onTryInChat = { viewModel.navigateTo(AgniDestination.CHAT) }
                                )
                            }
                            AgniDestination.MEMORY -> {
                                MemoryScreen(
                                    memoriesState = memoriesState,
                                    memoryEnabled = settings.memoryEnabled,
                                    onBack = { viewModel.navigateTo(AgniDestination.CHAT) },
                                    onToggleGlobalMemory = { viewModel.updateMemoryEnabled(it) },
                                    onAddMemory = { fact, cat -> viewModel.addMemory(fact, cat) },
                                    onToggleMemory = { viewModel.toggleMemory(it) },
                                    onDeleteMemory = { viewModel.deleteMemory(it) }
                                )
                            }
                            AgniDestination.SETTINGS -> {
                                SettingsAndFeedbackScreen(
                                    userDisplayName = displayName,
                                    userEmail = user.email,
                                    settings = settings,
                                    modelCatalog = modelCatalog,
                                    customEndpointConfig = customEndpointConfig,
                                    customTestState = customTestState,
                                    feedbackState = feedbackState,
                                    onBack = { viewModel.navigateTo(AgniDestination.CHAT) },
                                    onUpdateTheme = { viewModel.updateThemeMode(it) },
                                    onSelectLanguage = { viewModel.selectLanguage(it) },
                                    onSelectModel = { viewModel.selectModel(it) },
                                    onSaveCustomEndpoint = { label, url, modelId, token ->
                                        viewModel.saveAndActivateCustomEndpoint(label, url, modelId, token)
                                    },
                                    onTestCustomEndpoint = { label, url, modelId, token ->
                                        viewModel.testCustomEndpoint(label, url, modelId, token)
                                    },
                                    onClearCustomEndpoint = { viewModel.clearCustomEndpoint() },
                                    onToggleVoiceAutoSend = { viewModel.updateVoiceAutoSend(it) },
                                    onToggleMemory = { viewModel.updateMemoryEnabled(it) },
                                    onToggleWebResearch = { viewModel.updateWebResearchEnabled(it) },
                                    onSubmitFeedback = { r, c, msg -> viewModel.submitFeedback(r, c, msg) },
                                    onClearAllChats = { viewModel.clearAllChats() },
                                    onClearAllMemories = { viewModel.clearAllMemories() },
                                    onSwitchAccount = {
                                        onRequestAccountSwitch()
                                        signOut(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    },
                                    onSignOut = {
                                        signOut(
                                            credentialManager = credentialManager,
                                            scope = scope
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            val performAccountSwitch: () -> Unit = {
                onRequestAccountSwitch()
                signOut(
                    credentialManager = credentialManager,
                    scope = scope
                )
            }

            if (isExpanded) {
                Row(modifier = Modifier.fillMaxSize()) {
                    AgniSidebarContent(
                        userDisplayName = displayName,
                        userEmail = user.email,
                        currentDestination = composer.currentDestination,
                        activeChatId = composer.activeChatId,
                        searchQuery = composer.historySearchQuery,
                        chatsState = chatsState,
                        onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                        onNewChat = { viewModel.startNewChat() },
                        onSelectChat = { viewModel.selectChat(it) },
                        onTogglePin = { viewModel.togglePinChat(it) },
                        onDeleteChat = { viewModel.deleteChat(it) },
                        onClearAllChats = { viewModel.clearAllChats() },
                        onNavigate = { viewModel.navigateTo(it) },
                        onSwitchAccount = performAccountSwitch,
                        modifier = Modifier.width(300.dp)
                    )
                    VerticalDivider(modifier = Modifier.fillMaxHeight())
                    Box(modifier = Modifier.weight(1f)) {
                        mainContent(false)
                    }
                }
            } else {
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(modifier = Modifier.width(308.dp)) {
                            AgniSidebarContent(
                                userDisplayName = displayName,
                                userEmail = user.email,
                                currentDestination = composer.currentDestination,
                                activeChatId = composer.activeChatId,
                                searchQuery = composer.historySearchQuery,
                                chatsState = chatsState,
                                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                                onNewChat = {
                                    viewModel.startNewChat()
                                    scope.launch { drawerState.close() }
                                },
                                onSelectChat = {
                                    viewModel.selectChat(it)
                                    scope.launch { drawerState.close() }
                                },
                                onTogglePin = { viewModel.togglePinChat(it) },
                                onDeleteChat = { viewModel.deleteChat(it) },
                                onClearAllChats = { viewModel.clearAllChats() },
                                onNavigate = { dest ->
                                    viewModel.navigateTo(dest)
                                    scope.launch { drawerState.close() }
                                },
                                onSwitchAccount = {
                                    scope.launch { drawerState.close() }
                                    performAccountSwitch()
                                }
                            )
                        }
                    }
                ) {
                    mainContent(true)
                }
            }
        }
    }
}
