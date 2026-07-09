package com.chrono.ssh.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TerminalProfile
import com.chrono.ssh.core.model.TerminalSessionRecord
import com.chrono.ssh.core.service.BuildEditionPolicy
import com.chrono.ssh.core.service.EtCapableTransport
import com.chrono.ssh.core.service.HostKeyDecision
import com.chrono.ssh.core.service.HostKeyPrompt
import com.chrono.ssh.core.service.LocalShellSession
import com.chrono.ssh.core.service.MoshCapableTransport
import com.chrono.ssh.core.service.SshAuthFailureHints
import com.chrono.ssh.core.service.SshSession
import com.chrono.ssh.core.service.SshTransport
import com.chrono.ssh.core.service.TerminalBackAction
import com.chrono.ssh.core.service.TerminalBackPolicy
import com.chrono.ssh.core.service.TerminalInputRouter
import com.chrono.ssh.core.service.TerminalModifierRouter
import com.chrono.ssh.core.service.TerminalSessionRegistry
import com.chrono.ssh.core.service.TmuxAttachChoice
import com.chrono.ssh.core.service.TmuxAttachExisting
import com.chrono.ssh.core.service.TmuxAttachNew
import com.chrono.ssh.core.service.TmuxCommandBuilder
import com.chrono.ssh.core.service.TmuxSessionInfo
import com.chrono.ssh.core.service.TmuxSessionScanner
import com.chrono.ssh.core.service.TmuxWindowInfo
import com.chrono.ssh.core.service.buildChosenTmuxLaunchPlan
import com.chrono.ssh.ui.design.ChronoOsLogo
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.termiusTerminalOsLogoDrawableOrNull
import com.chrono.ssh.ui.terminal.ChronoSSHTerminalEngine
import com.chrono.ssh.ui.terminal.ChronoSSHTerminalView
import com.chrono.ssh.ui.terminal.TerminalCatalog
import com.chrono.ssh.ui.terminal.TerminalOscEvent
import com.chrono.ssh.ui.terminal.TerminalSearchDirection
import com.chrono.ssh.ui.terminal.TerminalSearchSelection
import com.chrono.ssh.ui.terminal.terminalLatestUrl
import com.chrono.ssh.ui.terminal.terminalSearchNavigate
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TerminalWorkspaceState(
    val serverId: String,
    engineFactory: () -> ChronoSSHTerminalEngine,
    val workspaceId: String = serverId
) {
    private val engineDelegate = lazy(LazyThreadSafetyMode.NONE, engineFactory)
    val engine: ChronoSSHTerminalEngine
        get() = engineDelegate.value

    var invalidations by mutableIntStateOf(0)
    var session by mutableStateOf<SshSession?>(null)
    var status by mutableStateOf("Idle")
    var lastAction by mutableStateOf("")
    var pendingHostKey by mutableStateOf<HostKeyPrompt?>(null)
    var pendingPassphraseDecision by mutableStateOf<HostKeyDecision?>(null)
    var oneTimePassphrase by mutableStateOf("")
    var startedAtEpochMillis by mutableStateOf(System.currentTimeMillis())
    var tmuxPickerOpen by mutableStateOf(false)
    var tmuxScanInProgress by mutableStateOf(false)
    var tmuxSessions by mutableStateOf<List<TmuxSessionInfo>>(emptyList())
    var tmuxSelectedSession by mutableStateOf<TmuxSessionInfo?>(null)
    var tmuxWindows by mutableStateOf<List<TmuxWindowInfo>>(emptyList())
    var tmuxWindowLoading by mutableStateOf(false)
    var tmuxNewSessionName by mutableStateOf("")
    var tmuxRestorableSessionName by mutableStateOf<String?>(null)
    var tmuxRestorableWindowIndex by mutableStateOf<Int?>(null)
    var reconnectRequestSerial by mutableIntStateOf(0)
    private var connectGeneration = 0

    val connected: Boolean
        get() = session != null

    fun detachEngineIfInitialized() {
        if (engineDelegate.isInitialized()) engine.detach()
    }

    fun disposeEngineIfInitialized() {
        if (engineDelegate.isInitialized()) engine.dispose()
    }

    fun nextConnectGeneration(): Int {
        connectGeneration += 1
        return connectGeneration
    }

    fun invalidateConnectGeneration(): Int {
        connectGeneration += 1
        return connectGeneration
    }

    fun isCurrentConnectGeneration(generation: Int): Boolean {
        return generation == connectGeneration
    }

    fun requestReconnect() {
        reconnectRequestSerial += 1
    }
}

private class TerminalModifierLatch {
    var ctrl = false
    var alt = false
    var shift = false
}

internal data class TerminalWorkspaceSummary(
    val key: String,
    val serverId: String,
    val connected: Boolean
)

internal data class TerminalWorkspaceSelection(
    val workspaceKey: String,
    val server: ServerProfile,
    val exists: Boolean
)

internal fun terminalWorkspaceSelection(
    selectedServerId: String?,
    servers: List<ServerProfile>,
    workspaces: List<TerminalWorkspaceSummary>
): TerminalWorkspaceSelection? {
    if (servers.isEmpty()) return null
    val serverIds = servers.mapTo(mutableSetOf()) { it.id }
    val validWorkspaces = workspaces.filter { it.serverId in serverIds }
    val requestedWorkspace = selectedServerId?.let { requested -> validWorkspaces.firstOrNull { it.key == requested } }
    val connectedWorkspaces = validWorkspaces.filter { it.connected }
    val selectedServer = requestedWorkspace
        ?.let { workspace -> servers.firstOrNull { it.id == workspace.serverId } }
        ?: selectedServerId?.let { id -> servers.firstOrNull { it.id == id } }
        ?: connectedWorkspaces.firstOrNull()?.let { workspace -> servers.firstOrNull { it.id == workspace.serverId } }
        ?: validWorkspaces.firstOrNull()?.let { workspace -> servers.firstOrNull { it.id == workspace.serverId } }
        ?: servers.first()
    val workspaceKey = requestedWorkspace?.key
        ?: connectedWorkspaces.firstOrNull { it.serverId == selectedServer.id }?.key
        ?: validWorkspaces.firstOrNull { it.serverId == selectedServer.id }?.key
        ?: selectedServer.id
    return TerminalWorkspaceSelection(
        workspaceKey = workspaceKey,
        server = selectedServer,
        exists = workspaces.any { it.key == workspaceKey }
    )
}

internal fun defaultTmuxSessionName(server: ServerProfile): String {
    return server.name
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
        .take(32)
        .ifBlank { "chrono" }
}

internal fun tmuxSessionSubtitle(session: TmuxSessionInfo): String {
    return buildList {
        add("${session.windows} window${if (session.windows == 1) "" else "s"}")
        if (session.attached) add("attached")
        session.lastAttached?.let { add("last attached $it") }
    }.joinToString(" · ")
}

internal fun tmuxWindowSubtitle(window: TmuxWindowInfo): String {
    return buildList {
        if (window.active) add("active")
        if (window.paneActive) add("active pane")
        window.activity?.let { add("activity $it") }
    }.joinToString(" · ")
}

internal fun terminalMoshStartupCommand(
    startupCommand: String,
    tmuxSessionName: String?,
    tmuxWindowIndex: Int?
): String {
    if (startupCommand.isNotBlank()) return startupCommand
    val sessionName = tmuxSessionName?.takeIf { it.isNotBlank() } ?: return ""
    return tmuxWindowIndex?.let { TmuxCommandBuilder.attachSessionWindow(sessionName, it) }
        ?: TmuxCommandBuilder.attachSession(sessionName)
}

internal fun terminalTopStripSessions(
    selectedWorkspaceKey: String,
    selectedServer: ServerProfile,
    activeSessions: List<Pair<String, ServerProfile>>
): List<Pair<String, ServerProfile>> {
    val active = activeSessions.distinctBy { it.first }
    return if (active.none { it.second.id == selectedServer.id }) {
        active.ifEmpty { listOf(selectedWorkspaceKey to selectedServer) }
    } else {
        active
    }
}

internal fun terminalSftpStripSessions(
    sftpSessions: List<Pair<String, ServerProfile>>
): List<Pair<String, ServerProfile>> {
    return sftpSessions.distinctBy { it.first }
}

internal fun terminalSessionChipLabel(name: String, occurrence: Int): String {
    return if (occurrence <= 1) name else "$name #$occurrence"
}

internal fun terminalSftpChipLabel(name: String, occurrence: Int): String {
    return terminalSessionChipLabel(name, occurrence)
}

internal fun terminalUserFacingError(error: Throwable): String {
    val raw = error.message.orEmpty()
    return when {
        raw.contains("auth", ignoreCase = true) ||
            raw.contains("permission denied", ignoreCase = true) -> raw
        raw.contains("host key", ignoreCase = true) ||
            raw.contains("fingerprint", ignoreCase = true) -> raw
        raw.contains("failed to connect", ignoreCase = true) ||
            raw.contains("connection reset", ignoreCase = true) ||
            raw.contains("broken pipe", ignoreCase = true) ||
            raw.contains("timeout", ignoreCase = true) -> "Connection interrupted. Reconnect to continue."
        else -> "Connection interrupted. Reconnect to continue."
    }
}

internal fun terminalShouldReconnectOnResume(
    hasSession: Boolean,
    hasPendingPassphrase: Boolean,
    hasPendingHostKey: Boolean,
    status: String,
    autoReconnect: Boolean
): Boolean {
    if (!autoReconnect || hasSession || hasPendingPassphrase || hasPendingHostKey) return false
    return status in setOf("Reconnecting", "Connection lost", "Disconnected")
}

internal fun terminalShouldRequestReconnectAfterDrop(
    autoReconnect: Boolean,
    hasPendingPassphrase: Boolean,
    hasPendingHostKey: Boolean
): Boolean {
    return autoReconnect && !hasPendingPassphrase && !hasPendingHostKey
}

internal fun terminalReconnectDelayMillis(attempt: Int): Long {
    return when {
        attempt <= 1 -> 750L
        attempt == 2 -> 1_500L
        attempt == 3 -> 3_000L
        else -> 5_000L
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    servers: List<ServerProfile>,
    terminalProfile: TerminalProfile,
    selectedServerId: String?,
    workspaces: SnapshotStateMap<String, TerminalWorkspaceState>,
    autoConnectRequestId: String?,
    credentials: List<Credential>,
    knownHosts: List<KnownHost>,
    snippets: List<Snippet>,
    transport: SshTransport,
    sftpWorkspaces: List<Pair<String, ServerProfile>> = emptyList(),
    selectedSftpWorkspaceKey: String? = null,
    onTerminalSessionChanged: (TerminalSessionRecord) -> Unit = {},
    onHostKeyRemembered: (ServerProfile, HostKeyPrompt) -> Unit = { _, _ -> },
    onTrustHost: suspend (ServerProfile) -> KnownHost? = { null },
    onConnectionEvent: (ServerProfile, ConnectionEventLevel, String) -> Unit = { _, _, _ -> },
    onShellConnected: (ServerProfile, SshSession) -> Unit = { _, _ -> },
    onOpenServer: (ServerProfile) -> TerminalWorkspaceState,
    onSelectServer: (String) -> Unit,
    onDuplicateServer: (ServerProfile) -> Unit,
    onCloseWorkspace: (String) -> Unit,
    onBack: () -> Unit,
    onEditHost: (ServerProfile) -> Unit,
    onSelectSftp: (String) -> Unit = {},
    onOpenSftp: (ServerProfile) -> Unit
) {
    if (servers.isEmpty()) {
        EmptyTerminal()
        return
    }
    val workspaceSummaries = workspaces.entries.map { entry ->
        TerminalWorkspaceSummary(entry.key, entry.value.serverId, entry.value.connected)
    }
    val selection = terminalWorkspaceSelection(selectedServerId, servers, workspaceSummaries)
    if (selection == null) {
        EmptyTerminal()
        return
    }
    val selectedServer = selection.server
    val workspaceKey = selection.workspaceKey
    val density = LocalDensity.current
    val terminalSideMargin = with(density) { terminalProfile.sideMarginDp.coerceIn(0, 8).toDp() }
    val terminalRightMargin = with(density) { terminalProfile.rightMarginDp.coerceIn(0, 8).toDp() }
    val terminalBackground = terminalComposeColor(terminalProfile.backgroundHex)
    val view = LocalView.current
    DisposableEffect(terminalProfile.keepScreenOn, view) {
        val window = (view.context as? ComponentActivity)?.window
        if (terminalProfile.keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if (terminalProfile.keepScreenOn) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val workspace = workspaces[workspaceKey]
    LaunchedEffect(workspaceKey, selection.exists) {
        if (!selection.exists) onOpenServer(selectedServer)
    }
    if (workspace == null) {
        var openingMenuOpen by remember(workspaceKey) { mutableStateOf(false) }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(terminalBackground)
        ) {
            TerminalTopBar(
                activeSessions = emptyList(),
                sftpSessions = sftpWorkspaces,
                selectedSftpWorkspaceKey = selectedSftpWorkspaceKey,
                selectedServer = selectedServer,
                selectedWorkspaceKey = workspaceKey,
                terminalBackground = terminalBackground,
                menuOpen = openingMenuOpen,
                onMenuOpenChange = { openingMenuOpen = it },
                tabMenuOpen = false,
                onTabMenuOpenChange = {},
                onSelect = onSelectServer,
                onSelectSftp = onSelectSftp,
                onClose = onBack,
                onReconnect = {},
                onDuplicate = { onDuplicateServer(selectedServer) },
                onOpenSftp = { onOpenSftp(selectedServer) },
                onSearch = {},
                onOpenLatestUrl = {},
                onCopyLatestUrl = {},
                onCopyLatestOutput = {},
                fullscreen = false,
                onToggleFullscreen = {},
                transcriptActionsEnabled = false
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(terminalBackground)
                    .terminalPanelFrame(terminalSideMargin, terminalRightMargin)
                    .clipToBounds()
            ) {
                OpeningTerminal()
            }
        }
        return
    }
    val credential = selectedServer.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }
    val knownHost = knownHosts.firstOrNull { it.host == selectedServer.host && it.port == selectedServer.port }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val rootView = LocalView.current
    val window = (context as? ComponentActivity)?.window
    val lifecycleOwner = LocalLifecycleOwner.current
    val clipboard = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val defaultTerminalTextSizePx = with(density) { terminalProfile.fontSizeSp.sp.toPx().toInt() }
    var terminalTextSizePx by remember(workspaceKey) { mutableIntStateOf(defaultTerminalTextSizePx) }
    var snippetsOpen by remember(workspaceKey) { mutableStateOf(false) }
    var snippetQuery by remember(workspaceKey) { mutableStateOf("") }
    var moreKeysOpen by remember(workspaceKey) { mutableStateOf(false) }
    var sessionMenuOpen by remember(workspaceKey) { mutableStateOf(false) }
    var tabMenuOpen by remember(workspaceKey) { mutableStateOf(false) }
    var scrollSettingsOpen by remember(workspaceKey) { mutableStateOf(false) }
    var scrollStep by remember(workspaceKey) { mutableIntStateOf(1) }
    var searchOpen by remember(workspaceKey) { mutableStateOf(false) }
    var searchQuery by remember(workspaceKey) { mutableStateOf("") }
    var searchSelection by remember(workspaceKey) { mutableStateOf<TerminalSearchSelection?>(null) }
    var ctrlLatched by remember(workspaceKey) { mutableStateOf(false) }
    var altLatched by remember(workspaceKey) { mutableStateOf(false) }
    var shiftLatched by remember(workspaceKey) { mutableStateOf(false) }
    val modifierLatch = remember(workspaceKey) { TerminalModifierLatch() }
    var fullscreen by remember(workspaceKey) { mutableStateOf(false) }
    var terminalView by remember(workspaceKey) { mutableStateOf<ChronoSSHTerminalView?>(null) }
    var directionalSwipeMode by remember(workspaceKey) { mutableStateOf(false) }
    var keyboardOpen by remember(workspaceKey) { mutableStateOf(false) }
    var pendingSnippet by remember(workspaceKey) { mutableStateOf<Snippet?>(null) }
    var snippetVariableValues by remember(workspaceKey) { mutableStateOf<Map<String, String>>(emptyMap()) }
    val imeVisible = WindowInsets.isImeVisible

    fun routeTerminalInput(input: String): String {
        val routed = TerminalModifierRouter.apply(input, modifierLatch.ctrl, modifierLatch.alt, modifierLatch.shift)
        if (routed.consumeCtrl) {
            modifierLatch.ctrl = false
            ctrlLatched = false
        }
        if (routed.consumeAlt) {
            modifierLatch.alt = false
            altLatched = false
        }
        if (routed.consumeShift) {
            modifierLatch.shift = false
            shiftLatched = false
        }
        return routed.output
    }

    fun clearTerminalModifiers() {
        modifierLatch.ctrl = false
        modifierLatch.alt = false
        modifierLatch.shift = false
        ctrlLatched = false
        altLatched = false
        shiftLatched = false
        if (workspace.status.endsWith(" latched")) workspace.status = "Shell"
    }

    fun rearmTerminalInput(showKeyboard: Boolean = terminalView?.isKeyboardRequested() == true || keyboardOpen || imeVisible) {
        terminalView?.rearmInputAfterTerminalAction(showKeyboard = showKeyboard)
    }

    fun resetTerminalTransientState() {
        snippetsOpen = false
        snippetQuery = ""
        moreKeysOpen = false
        sessionMenuOpen = false
        tabMenuOpen = false
        searchOpen = false
        searchQuery = ""
        searchSelection = null
        pendingSnippet = null
        snippetVariableValues = emptyMap()
        workspace.tmuxPickerOpen = false
        workspace.tmuxScanInProgress = false
        workspace.tmuxSelectedSession = null
        workspace.tmuxWindows = emptyList()
        workspace.tmuxWindowLoading = false
        ctrlLatched = false
        altLatched = false
        shiftLatched = false
        keyboardController?.hide()
        terminalView?.clearKeyboardRequest()
        terminalView?.noteKeyboardHidden()
        keyboardOpen = false
    }

    fun openTerminalPanel(panel: TerminalTransientPanel) {
        snippetsOpen = panel == TerminalTransientPanel.Snippets
        moreKeysOpen = panel == TerminalTransientPanel.MoreKeys
        sessionMenuOpen = panel == TerminalTransientPanel.Menu
        tabMenuOpen = false
        searchOpen = panel == TerminalTransientPanel.Search
        if (!snippetsOpen) snippetQuery = ""
        if (!searchOpen) {
            searchQuery = ""
            searchSelection = null
        }
    }

    fun configureEngineClipboard() {
        workspace.engine.setClipboardHandlers(
            copyText = { text -> clipboard.setText(AnnotatedString(text)) },
            pasteText = { clipboard.getText()?.text }
        )
        workspace.engine.setOscEventSink { event ->
            workspace.lastAction = when (event) {
                is TerminalOscEvent.ClipboardSet -> "Remote copied ${event.text.length} chars"
                is TerminalOscEvent.Hyperlink -> event.uri?.let { "Remote link: $it" } ?: "Remote link closed"
                is TerminalOscEvent.Notification -> listOf(event.title, event.body).filter { it.isNotBlank() }.joinToString(": ").ifBlank { "Remote notification" }
                is TerminalOscEvent.WorkingDirectory -> "Remote cwd: ${event.cwd}"
            }
        }
    }

    LaunchedEffect(workspaceKey, workspace.session) {
        if (workspace.session != null) return@LaunchedEffect
        val restored = TerminalSessionRegistry.session(workspaceKey) ?: return@LaunchedEffect
        configureEngineClipboard()
        workspace.engine.attach(restored)
        restored.setTerminalCloseHandler {
            scope.launch {
                if (workspace.session === restored) {
                    resetTerminalTransientState()
                    workspace.detachEngineIfInitialized()
                    workspace.session = null
                    workspace.status = "Closed"
                    workspace.lastAction = "Remote shell closed"
                    TerminalSessionRegistry.detach(workspaceKey, restored)
                    onConnectionEvent(selectedServer, ConnectionEventLevel.Warning, workspace.lastAction)
                    onTerminalSessionChanged(
                        TerminalSessionRecord(
                            id = restored.id,
                            serverId = selectedServer.id,
                            title = selectedServer.name,
                            status = ServerStatus.Offline,
                            startedAtEpochMillis = workspace.startedAtEpochMillis,
                            lastActiveEpochMillis = System.currentTimeMillis(),
                            transcriptPreview = restored.transcriptPreview,
                            tmuxSessionName = workspace.tmuxRestorableSessionName,
                            tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                        )
                    )
                }
            }
        }
        workspace.session = restored
        workspace.status = "Shell"
        workspace.lastAction = "Shell restored"
        onShellConnected(selectedServer, restored)
    }

    suspend fun scanTmuxSessionsAfterConnect(session: SshSession) {
        if (selectedServer.startupCommand.isNotBlank()) return
        workspace.tmuxScanInProgress = true
        val sessions = runCatching { TmuxSessionScanner(session).listSessions() }.getOrDefault(emptyList())
        workspace.tmuxScanInProgress = false
        if (workspace.session !== session) return
        workspace.tmuxSessions = sessions
        workspace.tmuxSelectedSession = null
        workspace.tmuxWindows = emptyList()
        workspace.tmuxNewSessionName = defaultTmuxSessionName(selectedServer)
        if (sessions.isNotEmpty()) {
            workspace.lastAction = "Tmux sessions found"
        }
    }

    fun openTmuxPicker() {
        val session = workspace.session ?: return
        scope.launch {
            workspace.tmuxScanInProgress = true
            val sessions = runCatching { TmuxSessionScanner(session).listSessions() }.getOrDefault(emptyList())
            if (workspace.session !== session) return@launch
            workspace.tmuxSessions = sessions
            workspace.tmuxSelectedSession = null
            workspace.tmuxWindows = emptyList()
            workspace.tmuxWindowLoading = false
            workspace.tmuxNewSessionName = defaultTmuxSessionName(selectedServer)
            workspace.tmuxScanInProgress = false
            workspace.tmuxPickerOpen = true
            workspace.lastAction = if (sessions.isEmpty()) "No tmux sessions found" else "Tmux sessions found"
        }
    }

    fun selectTmuxSession(sessionInfo: TmuxSessionInfo) {
        val session = workspace.session ?: return
        workspace.tmuxSelectedSession = sessionInfo
        workspace.tmuxWindows = emptyList()
        workspace.tmuxWindowLoading = true
        scope.launch {
            val windows = runCatching { TmuxSessionScanner(session).listWindows(sessionInfo.name) }.getOrDefault(emptyList())
            if (workspace.session !== session || workspace.tmuxSelectedSession?.name != sessionInfo.name) return@launch
            workspace.tmuxWindows = windows
            workspace.tmuxWindowLoading = false
        }
    }

    fun launchTmux(choice: TmuxAttachChoice) {
        val session = workspace.session ?: return
        val plan = buildChosenTmuxLaunchPlan(choice)
        scope.launch {
            session.writeTerminal("${plan.command}\n")
            workspace.tmuxPickerOpen = false
            workspace.tmuxSelectedSession = null
            workspace.tmuxWindows = emptyList()
            workspace.tmuxWindowLoading = false
            workspace.tmuxRestorableSessionName = plan.sessionName
            workspace.tmuxRestorableWindowIndex = plan.windowIndex
            workspace.lastAction = "Tmux ${plan.sessionName ?: "session"} launched"
            saveSessionRecord(session, selectedServer, workspace, onTerminalSessionChanged)
        }
    }

    LaunchedEffect(imeVisible) {
        if (!imeVisible && keyboardOpen) {
            terminalView?.noteKeyboardHidden()
            keyboardOpen = false
        }
    }

    LaunchedEffect(fullscreen, window, rootView) {
        val currentWindow = window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(currentWindow, rootView)
        if (fullscreen) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(window, rootView) {
        onDispose {
            val currentWindow = window ?: return@onDispose
            WindowCompat.getInsetsController(currentWindow, rootView).show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(rootView, workspaceKey) {
        val handler = View.OnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_F11) {
                if (event.action == KeyEvent.ACTION_DOWN) fullscreen = !fullscreen
                true
            } else {
                false
            }
        }
        rootView.setOnKeyListener(handler)
        onDispose { rootView.setOnKeyListener(null) }
    }

    BackHandler(enabled = true) {
        if (fullscreen) {
            fullscreen = false
            return@BackHandler
        }
        if (searchOpen) {
            searchOpen = false
            searchQuery = ""
            searchSelection = null
            return@BackHandler
        }
        when (
            TerminalBackPolicy.action(
                hasPendingSnippet = pendingSnippet != null,
                snippetsOpen = snippetsOpen,
                moreKeysOpen = moreKeysOpen,
                sessionMenuOpen = sessionMenuOpen,
                keyboardOpen = keyboardOpen,
                imeVisible = imeVisible,
                terminalRequestedKeyboard = terminalView?.isKeyboardRequested() == true
            )
        ) {
            TerminalBackAction.DismissSnippet -> {
                pendingSnippet = null
                snippetVariableValues = emptyMap()
            }
            TerminalBackAction.CloseTransientMenus -> {
                snippetsOpen = false
                moreKeysOpen = false
                sessionMenuOpen = false
            }
            TerminalBackAction.HideKeyboard -> {
                terminalView?.clearKeyboardRequest()
                terminalView?.clearFocus()
                keyboardController?.hide()
                keyboardOpen = false
            }
            TerminalBackAction.NavigateBack -> onBack()
        }
    }

    DisposableEffect(workspaceKey) {
        onDispose {
            resetTerminalTransientState()
            terminalView = null
        }
    }

    fun connectShell(decision: HostKeyDecision, privateKeyPassphrase: String? = null, reconnectAttempt: Int = 0) {
        if (workspace.session != null && decision != HostKeyDecision.TrustAndRemember) return
        if (!BuildEditionPolicy.supports(selectedServer.protocol)) {
            workspace.status = "${selectedServer.protocol.name} is not available in this build."
            return
        }
        val localShell = selectedServer.protocol == ConnectionProtocol.LocalProot
        val moshShell = selectedServer.protocol == ConnectionProtocol.Mosh
        val etShell = selectedServer.protocol == ConnectionProtocol.EternalTerminal
        workspace.status = "Connecting"
        val connectGeneration = workspace.nextConnectGeneration()
        workspace.pendingHostKey = null
        workspace.pendingPassphraseDecision = null
        onConnectionEvent(
            selectedServer,
            ConnectionEventLevel.Info,
            if (localShell) {
                "Local shell connect requested."
            } else {
                "Terminal connect requested: ${selectedServer.username}@${selectedServer.host}:${selectedServer.port}, credential=${credential?.label ?: "missing"} (${credential?.type?.name ?: "none"}), credentialReady=${credential?.secretBacked == true}, hostKey=${knownHost?.trustState ?: "Unknown"}, decision=$decision."
            }
        )
        scope.launch {
            try {
                val nextSession = if (localShell) {
                    LocalShellSession(selectedServer.id, context.applicationContext, selectedServer.name, selectedServer.prootConfig)
                } else if (moshShell) {
                    (transport as? MoshCapableTransport)?.connectMosh(
                        profile = selectedServer.copy(
                            startupCommand = terminalMoshStartupCommand(
                                startupCommand = selectedServer.startupCommand,
                                tmuxSessionName = workspace.tmuxRestorableSessionName,
                                tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                            )
                        ),
                        credential = credential,
                        hostKeyDecision = { decision },
                        privateKeyPassphrase = privateKeyPassphrase
                    ) ?: throw IllegalStateException("Mosh transport is not available in this build.")
                } else if (etShell) {
                    (transport as? EtCapableTransport)?.connectEt(
                        profile = selectedServer,
                        credential = credential,
                        hostKeyDecision = { decision },
                        privateKeyPassphrase = privateKeyPassphrase
                    ) ?: throw IllegalStateException("Eternal Terminal transport is not available in this build.")
                } else {
                    transport.connectShell(
                        profile = selectedServer,
                        credential = credential,
                        hostKeyDecision = { decision },
                        privateKeyPassphrase = privateKeyPassphrase
                    )
                }
                if (!workspace.isCurrentConnectGeneration(connectGeneration)) {
                    nextSession.close()
                    return@launch
                }
                configureEngineClipboard()
                workspace.engine.attach(nextSession)
                TerminalSessionRegistry.attach(workspaceKey, nextSession)
                nextSession.setTerminalCloseHandler {
                    scope.launch {
                        if (workspace.session === nextSession) {
                            resetTerminalTransientState()
                            workspace.detachEngineIfInitialized()
                            workspace.session = null
                            workspace.status = "Closed"
                            workspace.lastAction = if (localShell) "Local shell closed" else "Remote shell closed"
                            TerminalSessionRegistry.detach(workspaceKey, nextSession)
                            onConnectionEvent(selectedServer, ConnectionEventLevel.Warning, workspace.lastAction)
                            onTerminalSessionChanged(
                                TerminalSessionRecord(
                                    id = nextSession.id,
                                    serverId = selectedServer.id,
                                    title = selectedServer.name,
                                    status = ServerStatus.Offline,
                                    startedAtEpochMillis = workspace.startedAtEpochMillis,
                                    lastActiveEpochMillis = System.currentTimeMillis(),
                                    transcriptPreview = nextSession.transcriptPreview,
                                    tmuxSessionName = workspace.tmuxRestorableSessionName,
                                    tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                                )
                            )
                            val maxReconnects = selectedServer.reconnectPolicy.maxAttempts.coerceIn(0, 10)
                            if (selectedServer.reconnectPolicy.autoReconnect && maxReconnects > 0) {
                                workspace.status = "Reconnecting"
                                workspace.lastAction = "Reconnecting 1/$maxReconnects"
                                onConnectionEvent(selectedServer, ConnectionEventLevel.Info, "Terminal auto-reconnect 1/$maxReconnects.")
                                delay(terminalReconnectDelayMillis(1))
                                connectShell(HostKeyDecision.TrustAndRemember, reconnectAttempt = 1)
                            }
                        }
                    }
                }
                workspace.session = nextSession
                workspace.status = "Shell"
                workspace.startedAtEpochMillis = System.currentTimeMillis()
                val now = System.currentTimeMillis()
                workspace.lastAction = "Shell attached"
                onConnectionEvent(
                    selectedServer,
                    ConnectionEventLevel.Success,
                    if (localShell) {
                        if ((nextSession as? LocalShellSession)?.prootBacked == true) {
                            "Local PRoot shell connected."
                        } else {
                            "Local Android shell connected. Install a PRoot rootfs to use Linux userland."
                        }
                    } else {
                        "Terminal connected with ${credential?.type?.name ?: "unknown"} credential '${credential?.label ?: "missing"}'."
                    }
                )
                onShellConnected(selectedServer, nextSession)
                onTerminalSessionChanged(
                    TerminalSessionRecord(
                        id = nextSession.id,
                        serverId = selectedServer.id,
                        title = selectedServer.name,
                        status = ServerStatus.Online,
                        startedAtEpochMillis = workspace.startedAtEpochMillis,
                        lastActiveEpochMillis = now,
                        transcriptPreview = nextSession.transcriptPreview,
                        tmuxSessionName = workspace.tmuxRestorableSessionName,
                        tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                    )
                )
                if (!localShell && !moshShell) scanTmuxSessionsAfterConnect(nextSession)
            } catch (error: Exception) {
                if (!workspace.isCurrentConnectGeneration(connectGeneration)) return@launch
                if (error.requiresPrivateKeyPassphrase()) {
                    workspace.status = "Passphrase required"
                    workspace.lastAction = "Enter private-key passphrase"
                    workspace.pendingPassphraseDecision = decision
                    return@launch
                }
                val maxReconnects = selectedServer.reconnectPolicy.maxAttempts.coerceIn(0, 10)
                if (reconnectAttempt in 1 until maxReconnects) {
                    val nextAttempt = reconnectAttempt + 1
                    workspace.status = "Reconnecting"
                    workspace.lastAction = "Reconnecting $nextAttempt/$maxReconnects"
                    onConnectionEvent(selectedServer, ConnectionEventLevel.Warning, "Terminal auto-reconnect retry $nextAttempt/$maxReconnects after connection interruption.")
                    delay(terminalReconnectDelayMillis(nextAttempt))
                    connectShell(decision, privateKeyPassphrase, reconnectAttempt = nextAttempt)
                    return@launch
                }
                workspace.status = "Failed"
                workspace.lastAction = terminalUserFacingError(error)
                onConnectionEvent(
                    selectedServer,
                    ConnectionEventLevel.Error,
                    "Terminal connect failed: ${workspace.lastAction}"
                )
                onBack()
            }
        }
    }
    val keyboardActionsVisible = terminalQuickActionsVisible(
        keyboardVisible = keyboardOpen || imeVisible || terminalView?.isKeyboardRequested() == true,
        panelOpen = snippetsOpen || moreKeysOpen,
        searchOpen = searchOpen
    )

    fun reviewHostKey() {
        if (selectedServer.protocol == ConnectionProtocol.LocalProot) {
            connectShell(HostKeyDecision.TrustAndRemember)
            return
        }
        workspace.status = "Review host key"
        onConnectionEvent(selectedServer, ConnectionEventLevel.Info, "Terminal host-key review requested for ${selectedServer.host}:${selectedServer.port}.")
        scope.launch {
            try {
                val trustedHost = onTrustHost(selectedServer)
                if (trustedHost?.trusted == true) {
                    workspace.pendingHostKey = null
                    workspace.lastAction = "Host key trusted"
                    connectShell(HostKeyDecision.TrustAndRemember)
                    return@launch
                }
                val reviewedHost = trustedHost ?: transport.verifyHost(selectedServer)
                workspace.pendingHostKey = reviewedHost.let { host ->
                    HostKeyPrompt(
                        host = host.host,
                        port = host.port,
                        algorithm = host.algorithm,
                        fingerprint = host.fingerprint,
                        state = host.trustState,
                        message = "Review this fingerprint before opening a shell."
                    )
                }
                workspace.lastAction = "Host key review required"
                onConnectionEvent(
                    selectedServer,
                    if (workspace.pendingHostKey?.fingerprint?.startsWith("SHA256:") == true) ConnectionEventLevel.Warning else ConnectionEventLevel.Error,
                    "Host-key review result: ${workspace.pendingHostKey?.algorithm ?: "unknown"} ${workspace.pendingHostKey?.fingerprint ?: "unavailable"} [${workspace.pendingHostKey?.state ?: "Unknown"}]."
                )
            } catch (error: Exception) {
                workspace.status = "Failed"
                workspace.lastAction = terminalUserFacingError(error)
                onConnectionEvent(
                    selectedServer,
                    ConnectionEventLevel.Error,
                    "Terminal host-key review failed: ${workspace.lastAction}"
                )
                onBack()
            }
        }
    }

    fun disconnectShell(onClosed: (() -> Unit)? = null) {
        val current = workspace.session
        if (current == null) {
            workspace.invalidateConnectGeneration()
            resetTerminalTransientState()
            onClosed?.invoke()
            return
        }
        workspace.invalidateConnectGeneration()
        workspace.status = "Disconnecting"
        scope.launch {
            resetTerminalTransientState()
            current.setTerminalCloseHandler {}
            current.close()
            TerminalSessionRegistry.detach(workspaceKey, current)
            if (workspace.session !== current) {
                onClosed?.invoke()
                return@launch
            }
            workspace.detachEngineIfInitialized()
            workspace.session = null
            workspace.status = "Closed"
            workspace.lastAction = "Session closed"
            onConnectionEvent(selectedServer, ConnectionEventLevel.Info, "Terminal session disconnected.")
            onTerminalSessionChanged(
                TerminalSessionRecord(
                    id = current.id,
                    serverId = selectedServer.id,
                    title = selectedServer.name,
                    status = ServerStatus.Offline,
                    startedAtEpochMillis = workspace.startedAtEpochMillis,
                    lastActiveEpochMillis = System.currentTimeMillis(),
                    transcriptPreview = current.transcriptPreview,
                    tmuxSessionName = workspace.tmuxRestorableSessionName,
                    tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                )
            )
            onClosed?.invoke()
        }
    }

    fun openTerminalUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onSuccess {
            workspace.lastAction = "Opened $url"
        }.onFailure {
            workspace.lastAction = "Could not open URL"
        }
    }

    fun connectOrReview(reconnectAttempt: Int = 0) {
        if (selectedServer.protocol == ConnectionProtocol.LocalProot || knownHost?.trusted == true) {
            connectShell(HostKeyDecision.TrustAndRemember, reconnectAttempt = reconnectAttempt)
        } else {
            reviewHostKey()
        }
    }

    LaunchedEffect(autoConnectRequestId, workspaceKey, workspace.session, workspace.status, workspace.pendingHostKey, workspace.pendingPassphraseDecision) {
        val ready = workspace.session == null &&
            workspace.status !in setOf("Connecting", "Review host key", "Passphrase required") &&
            workspace.pendingHostKey == null &&
            workspace.pendingPassphraseDecision == null
        if (ready) connectOrReview()
    }
    LaunchedEffect(workspace.reconnectRequestSerial, workspaceKey) {
        if (workspace.reconnectRequestSerial == 0) return@LaunchedEffect
        if (
            terminalShouldReconnectOnResume(
                hasSession = workspace.session != null,
                hasPendingPassphrase = workspace.pendingPassphraseDecision != null,
                hasPendingHostKey = workspace.pendingHostKey != null,
                status = workspace.status,
                autoReconnect = selectedServer.reconnectPolicy.autoReconnect
            )
        ) {
            workspace.status = "Reconnecting"
            workspace.lastAction = "Reconnecting after interruption"
            connectOrReview(reconnectAttempt = 1)
        }
    }
    DisposableEffect(lifecycleOwner, workspaceKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                terminalView?.reactivateInputAfterResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val activeSessions = workspaceSummaries.mapNotNull { summary ->
        servers.firstOrNull { it.id == summary.serverId }?.let { server -> summary.key to server }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(terminalBackground)
    ) {
        TerminalTopBar(
            activeSessions = activeSessions,
            sftpSessions = sftpWorkspaces,
            selectedSftpWorkspaceKey = selectedSftpWorkspaceKey,
            selectedServer = selectedServer,
            selectedWorkspaceKey = workspaceKey,
            terminalBackground = terminalBackground,
            menuOpen = sessionMenuOpen,
            onMenuOpenChange = { open ->
                if (open) {
                    tabMenuOpen = false
                    openTerminalPanel(TerminalTransientPanel.Menu)
                } else {
                    sessionMenuOpen = false
                }
            },
            tabMenuOpen = tabMenuOpen,
            onTabMenuOpenChange = { open ->
                tabMenuOpen = open
                if (open) sessionMenuOpen = false
            },
            onSelect = { onSelectServer(it) },
            onSelectSftp = onSelectSftp,
            onClose = { onCloseWorkspace(workspaceKey) },
            onReconnect = { connectOrReview(reconnectAttempt = 1) },
            onDuplicate = { onDuplicateServer(selectedServer) },
            onOpenSftp = { onOpenSftp(selectedServer) },
            onSearch = { openTerminalPanel(TerminalTransientPanel.Search) },
            onOpenLatestUrl = {
                val url = terminalLatestUrl(workspace.engine.copyTranscript())
                if (url != null) openTerminalUrl(url) else workspace.lastAction = "No URL found"
            },
            onCopyLatestUrl = {
                val url = terminalLatestUrl(workspace.engine.copyTranscript())
                if (url != null) {
                    clipboard.setText(AnnotatedString(url))
                    workspace.lastAction = "URL copied"
                } else {
                    workspace.lastAction = "No URL found"
                }
            },
            onCopyLatestOutput = {
                val output = workspace.engine.copyLatestOutputBlock()
                if (output != null) {
                    clipboard.setText(AnnotatedString(output))
                    workspace.lastAction = "Latest output copied"
                } else {
                    workspace.lastAction = "No output found"
                }
            },
            fullscreen = fullscreen,
            onToggleFullscreen = { fullscreen = !fullscreen },
            transcriptActionsEnabled = workspace.session != null
        )
        AnimatedVisibility(
            visible = workspace.pendingHostKey != null,
            enter = fadeIn(tween(120)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
        ) {
            workspace.pendingHostKey?.let { prompt ->
                HostKeyReviewStrip(
                    prompt = prompt,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    onTrustRemember = {
                        onHostKeyRemembered(selectedServer, prompt)
                        workspace.lastAction = "Host key remembered"
                        connectShell(HostKeyDecision.TrustAndRemember)
                    },
                    onReject = {
                        workspace.pendingHostKey = null
                        workspace.status = "Rejected"
                        workspace.lastAction = "Host key rejected"
                    }
                )
            }
        }
        workspace.pendingPassphraseDecision?.let { decision ->
            PrivateKeyPassphraseDialog(
                hostName = selectedServer.name,
                passphrase = workspace.oneTimePassphrase,
                onPassphraseChange = { workspace.oneTimePassphrase = it },
                onDismiss = {
                    workspace.pendingPassphraseDecision = null
                    workspace.oneTimePassphrase = ""
                    workspace.status = "Idle"
                    workspace.lastAction = ""
                },
                onConnect = {
                    val passphrase = workspace.oneTimePassphrase
                    workspace.oneTimePassphrase = ""
                    connectShell(decision, passphrase)
                }
            )
        }
        if (workspace.tmuxPickerOpen) {
            TmuxAttachDialog(
                sessions = workspace.tmuxSessions,
                selectedSession = workspace.tmuxSelectedSession,
                windows = workspace.tmuxWindows,
                windowsLoading = workspace.tmuxWindowLoading,
                newSessionName = workspace.tmuxNewSessionName,
                onNewSessionNameChange = { workspace.tmuxNewSessionName = it },
                onSelectSession = { session -> selectTmuxSession(session) },
                onBackToSessions = {
                    workspace.tmuxSelectedSession = null
                    workspace.tmuxWindows = emptyList()
                    workspace.tmuxWindowLoading = false
                },
                onAttachSession = { session -> launchTmux(TmuxAttachExisting(session.name)) },
                onAttachWindow = { session, window -> launchTmux(TmuxAttachExisting(session.name, window.index)) },
                onNewSession = {
                    val name = workspace.tmuxNewSessionName.trim()
                    if (name.isNotBlank()) launchTmux(TmuxAttachNew(name))
                },
                onDismiss = {
                    workspace.tmuxPickerOpen = false
                    workspace.lastAction = "Tmux picker dismissed"
                }
            )
        }
        if (scrollSettingsOpen) {
            TerminalScrollSettingsDialog(
                scrollStep = scrollStep,
                onSelect = { step ->
                    scrollStep = step
                    workspace.lastAction = "Scroll speed: ${step}x"
                },
                onDismiss = { scrollSettingsOpen = false }
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(terminalBackground)
                .terminalPanelFrame(terminalSideMargin, terminalRightMargin)
                .clipToBounds()
        ) {
            val invalidationKey = workspace.invalidations
            AndroidView(
                modifier = Modifier
                    .fillMaxSize(),
                factory = { context ->
                    ChronoSSHTerminalView(context).apply {
                        setTerminalTextSizePx(terminalTextSizePx)
                        setTerminalTypeface(TerminalCatalog.typeface(context, terminalProfile.fontFamily))
                        setTerminalBackground(terminalProfile.backgroundHex)
                        setContentLeftPaddingPx(terminalProfile.sideMarginDp.coerceIn(0, 8))
                        setContentRightPaddingPx(terminalProfile.rightMarginDp.coerceIn(0, 8))
                        setDirectionalSwipeEnabled(directionalSwipeMode)
                        setOnKeyboardRequestChanged { keyboardOpen = it }
                        setOnUrlTap { url -> openTerminalUrl(url) }
                        setOnTextSizeChanged { terminalTextSizePx = it }
                        setInputTransform { routeTerminalInput(it) }
                        setBracketedPaste(terminalProfile.bracketedPaste)
                        setOnBeforePaste { clearTerminalModifiers() }
                        configureEngineClipboard()
                        bind(workspace.engine)
                        syncKeyboardRequestState()
                        terminalView = this
                    }
                },
                update = { view ->
                    @Suppress("UNUSED_VARIABLE")
                    val keepComposeObserving = invalidationKey
                    if (terminalView !== view) terminalView = view
                    configureEngineClipboard()
                    view.bind(workspace.engine)
                    view.setTerminalTextSizePx(terminalTextSizePx)
                    view.setTerminalTypeface(TerminalCatalog.typeface(view.context, terminalProfile.fontFamily))
                    view.setTerminalBackground(terminalProfile.backgroundHex)
                    view.setContentLeftPaddingPx(terminalProfile.sideMarginDp.coerceIn(0, 8))
                    view.setContentRightPaddingPx(terminalProfile.rightMarginDp.coerceIn(0, 8))
                    view.setDirectionalSwipeEnabled(directionalSwipeMode)
                    view.setOnTextSizeChanged { terminalTextSizePx = it }
                    view.setInputTransform { routeTerminalInput(it) }
                    view.setBracketedPaste(terminalProfile.bracketedPaste)
                    view.setOnBeforePaste { clearTerminalModifiers() }
                    view.setOnKeyboardRequestChanged { keyboardOpen = it }
                    view.setOnUrlTap { url -> openTerminalUrl(url) }
                    view.onTerminalUpdated()
                }
            )
        if (keyboardActionsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 10.dp, bottom = 10.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                TerminalRoundAction(TerminalQuickAction.Snippets) {
                    if (snippetsOpen) snippetsOpen = false else openTerminalPanel(TerminalTransientPanel.Snippets)
                }
                TerminalRoundAction(TerminalQuickAction.Paste) {
                    val clipboardText = clipboard.getText()?.text.orEmpty()
                    clearTerminalModifiers()
                    if (workspace.engine.pasteText(clipboardText)) {
                        workspace.lastAction = "Paste sent"
                    } else {
                        workspace.lastAction = "Clipboard empty"
                    }
                    rearmTerminalInput(showKeyboard = true)
                }
                TerminalRoundAction(TerminalQuickAction.AccessoryKeys) {
                    if (moreKeysOpen) moreKeysOpen = false else openTerminalPanel(TerminalTransientPanel.MoreKeys)
                }
            }
        }
        if (workspace.session != null && searchOpen) {
            val searchOffsets = workspace.engine.searchTranscript(searchQuery)
            TerminalSearchRow(
                query = searchQuery,
                status = terminalSearchStatus(searchQuery, searchOffsets, searchSelection),
                hasMatches = searchOffsets.isNotEmpty(),
                onQueryChange = {
                    searchQuery = it
                    searchSelection = null
                },
                onPrevious = {
                    searchSelection = terminalSearchNavigate(searchOffsets, searchSelection?.index, TerminalSearchDirection.Previous)
                    searchSelection?.let {
                        terminalView?.scrollToTranscriptOffset(it.offset)
                        workspace.lastAction = "Search ${it.index + 1}/${it.total}"
                    }
                },
                onNext = {
                    searchSelection = terminalSearchNavigate(searchOffsets, searchSelection?.index, TerminalSearchDirection.Next)
                    searchSelection?.let {
                        terminalView?.scrollToTranscriptOffset(it.offset)
                        workspace.lastAction = "Search ${it.index + 1}/${it.total}"
                    }
                },
                onClose = {
                    searchOpen = false
                    searchQuery = ""
                    searchSelection = null
                }
            )
        }
    }
        if (workspace.session != null && snippetsOpen) {
            Column {
                TerminalSnippetRow(
                    snippets = terminalVisibleSnippets(snippets, selectedServer.id, snippetQuery),
                    query = snippetQuery,
                    onQueryChange = { snippetQuery = it },
                    onRun = { snippet ->
                        val variables = snippetVariableNames(snippet)
                        if ((snippet.autoRun || !snippet.confirmBeforeRun) && variables.isEmpty()) {
                            clearTerminalModifiers()
                            workspace.engine.sendInput(renderSnippetCommand(snippet, selectedServer, emptyMap()) + "\r")
                            workspace.lastAction = "Snippet sent: ${snippet.name}"
                            snippetsOpen = false
                            rearmTerminalInput()
                            workspace.session?.let { current -> saveSessionRecord(current, selectedServer, workspace, onTerminalSessionChanged) }
                        } else if (pendingSnippet?.id == snippet.id) {
                            pendingSnippet = null
                            snippetVariableValues = emptyMap()
                        } else {
                            pendingSnippet = snippet
                            snippetVariableValues = variables.associateWith { variable ->
                                defaultSnippetVariableValue(variable, selectedServer)
                            }
                        }
                    }
                )
                pendingSnippet?.let { snippet ->
                    InlineSnippetRunPanel(
                        snippet = snippet,
                        server = selectedServer,
                        values = snippetVariableValues,
                        onValueChange = { variable, value ->
                            snippetVariableValues = snippetVariableValues + (variable to value)
                        },
                        onDismiss = {
                            pendingSnippet = null
                            snippetVariableValues = emptyMap()
                        },
                        onRun = { command ->
                            clearTerminalModifiers()
                            workspace.engine.sendInput(command + "\r")
                            workspace.lastAction = "Snippet sent: ${snippet.name}"
                            pendingSnippet = null
                            snippetVariableValues = emptyMap()
                            snippetsOpen = false
                            rearmTerminalInput()
                            workspace.session?.let { current -> saveSessionRecord(current, selectedServer, workspace, onTerminalSessionChanged) }
                        }
                    )
                }
            }
        }
        if (workspace.session != null) {
            ClickableTerminalActionRow(
                terminalProfile = terminalProfile,
                expanded = moreKeysOpen,
                directionalSwipeMode = directionalSwipeMode,
                ctrlLatched = ctrlLatched,
                altLatched = altLatched,
                shiftLatched = shiftLatched,
                onToggleDirectionalSwipe = {
                    directionalSwipeMode = !directionalSwipeMode
                    terminalView?.setDirectionalSwipeEnabled(directionalSwipeMode)
                    rearmTerminalInput(showKeyboard = false)
                },
                onOpenTmux = { openTmuxPicker() },
                onEnterScroll = {
                    clearTerminalModifiers()
                    workspace.engine.sendInput("\u0002[")
                    workspace.lastAction = "Tmux scroll mode"
                    rearmTerminalInput(showKeyboard = false)
                },
                onOpenScrollSettings = {
                    scrollSettingsOpen = true
                    rearmTerminalInput(showKeyboard = false)
                },
                onSend = { _, sequence ->
                    when (sequence) {
                        "<ctrl>" -> {
                            modifierLatch.ctrl = !modifierLatch.ctrl
                            ctrlLatched = modifierLatch.ctrl
                            workspace.status = if (ctrlLatched) "Ctrl latched" else "Shell"
                            workspace.lastAction = if (ctrlLatched) "Ctrl latched" else "Ctrl released"
                            rearmTerminalInput()
                        }
                        "<alt>" -> {
                            modifierLatch.alt = !modifierLatch.alt
                            altLatched = modifierLatch.alt
                            workspace.status = if (altLatched) "Alt latched" else "Shell"
                            workspace.lastAction = if (altLatched) "Alt latched" else "Alt released"
                            rearmTerminalInput()
                        }
                        "<altgr>" -> {
                            val next = !(modifierLatch.ctrl && modifierLatch.alt)
                            modifierLatch.ctrl = next
                            modifierLatch.alt = next
                            ctrlLatched = next
                            altLatched = next
                            workspace.status = if (next) "AltGr latched" else "Shell"
                            workspace.lastAction = if (next) "AltGr latched" else "AltGr released"
                            rearmTerminalInput()
                        }
                        "<shift>" -> {
                            modifierLatch.shift = !modifierLatch.shift
                            shiftLatched = modifierLatch.shift
                            workspace.status = if (shiftLatched) "Shift latched" else "Shell"
                            workspace.lastAction = if (shiftLatched) "Shift latched" else "Shift released"
                            rearmTerminalInput()
                        }
                        else -> {
                            val result = terminalAccessorySendResult(sequence, modifierLatch.ctrl, modifierLatch.alt, modifierLatch.shift)
                            if (result.consumeCtrl) {
                                modifierLatch.ctrl = false
                                ctrlLatched = false
                            }
                            if (result.consumeAlt) {
                                modifierLatch.alt = false
                                altLatched = false
                            }
                            if (result.consumeShift) {
                                modifierLatch.shift = false
                                shiftLatched = false
                            }
                            val outgoing = terminalAccessoryApplyScrollStep(result.output, scrollStep)
                            if (outgoing.isNotEmpty()) workspace.engine.sendInput(outgoing)
                            rearmTerminalInput()
                            workspace.lastAction = "Sent ${keyLabel(sequence)}"
                            workspace.session?.let { current -> saveSessionRecord(current, selectedServer, workspace, onTerminalSessionChanged) }
                        }
                    }
                }
            )
        }
}
}

private enum class TerminalTransientPanel {
    Snippets,
    MoreKeys,
    Menu,
    Search
}

@Composable
fun rememberTerminalWorkspaces(
    terminalProfile: TerminalProfile
): SnapshotStateMap<String, TerminalWorkspaceState> {
    return remember(terminalProfile.id) { mutableStateMapOf<String, TerminalWorkspaceState>() }
}

@Composable
private fun TerminalTopBar(
    activeSessions: List<Pair<String, ServerProfile>>,
    sftpSessions: List<Pair<String, ServerProfile>>,
    selectedSftpWorkspaceKey: String?,
    selectedServer: ServerProfile,
    selectedWorkspaceKey: String,
    terminalBackground: Color,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    tabMenuOpen: Boolean,
    onTabMenuOpenChange: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    onSelectSftp: (String) -> Unit,
    onClose: () -> Unit,
    onReconnect: () -> Unit,
    onDuplicate: () -> Unit,
    onOpenSftp: () -> Unit,
    onSearch: () -> Unit,
    onOpenLatestUrl: () -> Unit,
    onCopyLatestUrl: () -> Unit,
    onCopyLatestOutput: () -> Unit,
    fullscreen: Boolean,
    onToggleFullscreen: () -> Unit,
    transcriptActionsEnabled: Boolean
) {
    val sessionStrip = remember(activeSessions, selectedServer) {
        terminalTopStripSessions(selectedWorkspaceKey, selectedServer, activeSessions)
    }
    val sftpStrip = remember(sftpSessions) {
        terminalSftpStripSessions(sftpSessions)
    }
    val activeMenuActions: @Composable () -> Unit = {
        DropdownMenuItem(text = { TerminalMenuText("Duplicate") }, onClick = {
            onMenuOpenChange(false)
            onDuplicate()
        })
        DropdownMenuItem(text = { TerminalMenuText("Open SFTP") }, onClick = {
            onMenuOpenChange(false)
            onOpenSftp()
        })
        DropdownMenuItem(text = { TerminalMenuText("Disconnect", color = DeckColors.Red, heavy = true) }, onClick = {
            onMenuOpenChange(false)
            onClose()
        })
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(2f)
            .background(terminalBackground)
            .statusBarsPadding()
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val hostOccurrences = mutableMapOf<String, Int>()
                sessionStrip.forEach { (workspaceKey, server) ->
                    val occurrence = (hostOccurrences[server.name] ?: 0) + 1
                    hostOccurrences[server.name] = occurrence
                    SquircleSessionChip(
                        server = server,
                        selected = workspaceKey == selectedWorkspaceKey,
                        compact = false,
                        terminalBackground = terminalBackground,
                        label = terminalSessionChipLabel(server.name, occurrence),
                        menuOpen = tabMenuOpen && workspaceKey == selectedWorkspaceKey,
                        onMenuOpenChange = onTabMenuOpenChange,
                        menuContent = activeMenuActions.takeIf { workspaceKey == selectedWorkspaceKey },
                        onClick = {
                            if (workspaceKey != selectedWorkspaceKey) onSelect(workspaceKey)
                        }
                    )
                }
                val sftpOccurrences = mutableMapOf<String, Int>()
                sftpStrip.forEach { (workspaceKey, server) ->
                    val occurrence = (sftpOccurrences[server.name] ?: 0) + 1
                    sftpOccurrences[server.name] = occurrence
                    SquircleSessionChip(
                        server = server,
                        selected = workspaceKey == selectedSftpWorkspaceKey,
                        compact = false,
                        terminalBackground = terminalBackground,
                        badge = "SFTP",
                        label = terminalSftpChipLabel(server.name, occurrence),
                        onClick = {
                            if (workspaceKey != selectedSftpWorkspaceKey) onSelectSftp(workspaceKey)
                        }
                    )
                }
            }
            Box {
                TerminalTopAction(
                    terminalBackground = terminalBackground,
                    onClick = { onMenuOpenChange(true) }
                )
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                    shape = RoundedCornerShape(16.dp),
                    containerColor = DeckColors.SurfaceRaised,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier.border(1.dp, DeckColors.Divider, RoundedCornerShape(16.dp)),
                    properties = PopupProperties(focusable = true)
                ) {
                    DropdownMenuItem(text = { TerminalMenuText("Reconnect") }, onClick = {
                        onMenuOpenChange(false)
                        onReconnect()
                    })
                    DropdownMenuItem(text = { TerminalMenuText("Duplicate") }, onClick = {
                        onMenuOpenChange(false)
                        onDuplicate()
                    })
                    if (transcriptActionsEnabled) {
                        DropdownMenuItem(text = { TerminalMenuText("Search transcript") }, onClick = {
                            onMenuOpenChange(false)
                            onSearch()
                        })
                        DropdownMenuItem(text = { TerminalMenuText("Open latest URL") }, onClick = {
                            onMenuOpenChange(false)
                            onOpenLatestUrl()
                        })
                        DropdownMenuItem(text = { TerminalMenuText("Copy latest URL") }, onClick = {
                            onMenuOpenChange(false)
                            onCopyLatestUrl()
                        })
                        DropdownMenuItem(text = { TerminalMenuText("Copy latest output") }, onClick = {
                            onMenuOpenChange(false)
                            onCopyLatestOutput()
                        })
                    }
                    DropdownMenuItem(text = { TerminalMenuText("Connect via SFTP") }, onClick = {
                        onMenuOpenChange(false)
                        onOpenSftp()
                    })
                    DropdownMenuItem(text = { TerminalMenuText(if (fullscreen) "Exit fullscreen" else "Fullscreen") }, onClick = {
                        onMenuOpenChange(false)
                        onToggleFullscreen()
                    })
                    DropdownMenuItem(text = { TerminalMenuText("Disconnect", color = DeckColors.Red, heavy = true) }, onClick = {
                        onMenuOpenChange(false)
                        onClose()
                    })
                }
            }
        }
    }
}

@Composable
private fun SquircleSessionChip(
    server: ServerProfile,
    selected: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
    terminalBackground: Color = DeckColors.Terminal,
    badge: String? = null,
    label: String = server.name,
    menuOpen: Boolean = false,
    onMenuOpenChange: (Boolean) -> Unit = {},
    menuContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = terminalTabFillColor(terminalBackground),
        animationSpec = tween(160, easing = LinearOutSlowInEasing),
        label = "sessionChipBackground"
    )
    Row(
        modifier = Modifier
            .then(modifier)
            .height(34.dp)
            .then(if (compact) Modifier.width(34.dp) else Modifier.widthIn(min = 84.dp, max = 157.dp))
            .clip(RoundedCornerShape(9.dp))
            .background(background.copy(alpha = 0.60f))
            .border(0.6.dp, Color.White.copy(alpha = 0.03f), RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(start = 3.dp, end = if (compact) 3.dp else 8.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            modifier = Modifier
                .weight(1f, fill = !compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                TerminalSessionLogo(server, Modifier.size(28.dp))
                if (badge != null) {
                    Text(
                        badge.take(1),
                        color = Color.White,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .clip(RoundedCornerShape(4.dp))
                            .background(DeckColors.Cyan.copy(alpha = 0.9f))
                            .padding(horizontal = 3.dp, vertical = 1.dp)
                    )
                }
            }
            if (!compact) {
                Text(
                    label,
                    color = if (selected) Color.White else DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (menuContent != null) {
                    Box {
                        Text(
                            "⋮",
                            color = Color.White.copy(alpha = 0.78f),
                            fontSize = 16.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onMenuOpenChange(true) }
                                .padding(horizontal = 3.dp, vertical = 2.dp)
                        )
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { onMenuOpenChange(false) },
                            shape = RoundedCornerShape(16.dp),
                            containerColor = DeckColors.SurfaceRaised,
                            tonalElevation = 0.dp,
                            shadowElevation = 0.dp,
                            modifier = Modifier.border(1.dp, DeckColors.Divider, RoundedCornerShape(16.dp)),
                            properties = PopupProperties(focusable = true)
                        ) {
                            menuContent()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalSessionLogo(server: ServerProfile, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val customLogo = remember(server.customLogoUri) {
        server.customLogoUri?.let { uriText ->
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriText))?.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        if (customLogo != null) {
            Image(
                bitmap = customLogo,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val terminalLogo = remember(server.osName) { termiusTerminalOsLogoDrawableOrNull(server.osName) }
            if (terminalLogo != null) {
                Image(
                    painter = painterResource(terminalLogo),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                ChronoOsLogo(server.osName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun TerminalTopAction(
    terminalBackground: Color = DeckColors.Terminal,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "terminalTopActionPress"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(terminalTabFillColor(terminalBackground))
            .border(0.6.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(18.dp)) {
            val radius = 1.75.dp.toPx()
            val centerX = size.width / 2f
            val gap = size.height / 3.3f
            drawCircle(Color.White.copy(alpha = 0.88f), radius, androidx.compose.ui.geometry.Offset(centerX, size.height / 2f - gap))
            drawCircle(Color.White.copy(alpha = 0.88f), radius, androidx.compose.ui.geometry.Offset(centerX, size.height / 2f))
            drawCircle(Color.White.copy(alpha = 0.88f), radius, androidx.compose.ui.geometry.Offset(centerX, size.height / 2f + gap))
        }
    }
}

@Composable
private fun TerminalMenuText(
    text: String,
    color: Color = Color.White,
    heavy: Boolean = false
) {
    Text(
        text,
        color = color,
        fontSize = 15.sp,
        lineHeight = 18.sp,
        fontWeight = if (heavy) FontWeight.Black else FontWeight.Bold
    )
}

private fun Modifier.terminalPanelFrame(leftMargin: Dp, rightMargin: Dp): Modifier {
    return padding(start = leftMargin, end = rightMargin)
}

@Composable
private fun ConnectionEmptyState(
    server: ServerProfile,
    credential: Credential?,
    knownHost: KnownHost?,
    status: String,
    lastAction: String,
    onAddIdentity: () -> Unit,
    onReviewHostKey: () -> Unit,
    onConnect: () -> Unit
) {
    val localShell = server.protocol == ConnectionProtocol.LocalProot
    val ready = localShell || (credential?.secretBacked == true && knownHost?.trusted == true)
    val action = when {
        localShell -> "Connect"
        credential?.secretBacked != true -> "Add Identity"
        knownHost?.trusted != true -> "Review Host Key"
        else -> "Connect"
    }
    val detail = when {
        localShell && status == "Connecting" -> "Opening local shell..."
        localShell -> "Ready to open local shell"
        credential?.secretBacked != true -> "No saved password or key is linked to this host."
        knownHost?.trusted != true -> "Trust this host fingerprint before opening the shell."
        status == "Connecting" -> "Opening SSH session..."
        status == "Passphrase required" -> "Private-key passphrase required."
        status == "Failed" -> lastAction
        else -> "Ready to connect"
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(DeckColors.SurfaceMuted)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(22.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            TerminalSessionLogo(server, Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(16.dp))
        Text(detail, color = if (status == "Failed") DeckColors.Red else Color.White.copy(alpha = 0.64f), fontSize = 14.sp, fontFamily = FontFamily.Monospace, maxLines = 3, lineHeight = 20.sp)
        Spacer(Modifier.height(18.dp))
        TerminalButton(
            text = action,
            color = if (ready) DeckColors.Green else DeckColors.Orange,
            onClick = {
                when {
                    credential?.secretBacked != true -> onAddIdentity()
                    knownHost?.trusted != true -> onReviewHostKey()
                    else -> onConnect()
                }
            }
        )
        if (status == "Failed" && lastAction.isNotBlank() && lastAction != detail) {
            Spacer(Modifier.height(14.dp))
            Text(lastAction, color = Color.White.copy(alpha = 0.58f), fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun TerminalSearchRow(
    query: String,
    status: String,
    hasMatches: Boolean,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeckColors.Terminal)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = terminalTextFieldColors(),
            label = { Text("Find") }
        )
        Text(
            text = status,
            color = DeckColors.SecondaryText,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Bold
        )
        TerminalButton("Prev", DeckColors.Cyan, enabled = hasMatches, onClick = onPrevious)
        TerminalButton("Next", DeckColors.Cyan, enabled = hasMatches, onClick = onNext)
        TerminalButton("X", DeckColors.Red, onClick = onClose)
    }
}

@Composable
private fun PrivateKeyPassphraseDialog(
    hostName: String,
    passphrase: String,
    onPassphraseChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Private key passphrase", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Enter the passphrase for $hostName. It will be used once and not saved.", color = DeckColors.SecondaryText, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = terminalTextFieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Passphrase") }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = passphrase.isNotBlank(), onClick = onConnect) {
                Text("Connect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun TmuxAttachDialog(
    sessions: List<TmuxSessionInfo>,
    selectedSession: TmuxSessionInfo?,
    windows: List<TmuxWindowInfo>,
    windowsLoading: Boolean,
    newSessionName: String,
    onNewSessionNameChange: (String) -> Unit,
    onSelectSession: (TmuxSessionInfo) -> Unit,
    onBackToSessions: () -> Unit,
    onAttachSession: (TmuxSessionInfo) -> Unit,
    onAttachWindow: (TmuxSessionInfo, TmuxWindowInfo) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(selectedSession?.name ?: "Tmux sessions", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (selectedSession == null) {
                    sessions.forEach { session ->
                        TmuxPickerRow(
                            title = session.name,
                            subtitle = tmuxSessionSubtitle(session),
                            onClick = { onSelectSession(session) }
                        )
                    }
                    OutlinedTextField(
                        value = newSessionName,
                        onValueChange = onNewSessionNameChange,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = terminalTextFieldColors(),
                        label = { Text("New session") }
                    )
                } else {
                    TmuxPickerRow(
                        title = "Attach session",
                        subtitle = tmuxSessionSubtitle(selectedSession),
                        onClick = { onAttachSession(selectedSession) }
                    )
                    if (windowsLoading) {
                        Text("Loading windows...", color = DeckColors.SecondaryText, fontSize = 13.sp)
                    } else if (windows.isEmpty()) {
                        Text("No windows found", color = DeckColors.SecondaryText, fontSize = 13.sp)
                    } else {
                        windows.forEach { window ->
                            TmuxPickerRow(
                                title = "${window.index}: ${window.name.ifBlank { "window" }}",
                                subtitle = tmuxWindowSubtitle(window),
                                onClick = { onAttachWindow(selectedSession, window) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedSession == null) {
                TextButton(enabled = newSessionName.isNotBlank(), onClick = onNewSession) {
                    Text("New", fontWeight = FontWeight.Bold)
                }
            } else {
                TextButton(onClick = onBackToSessions) {
                    Text("Back", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Skip")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun TmuxPickerRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .background(DeckColors.TerminalPanel)
            .padding(12.dp)
    ) {
        Text(title, color = DeckColors.PrimaryText, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle.isNotBlank()) Text(subtitle, color = DeckColors.SecondaryText, fontSize = 12.sp)
    }
}

@Composable
private fun TerminalScrollSettingsDialog(
    scrollStep: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scroll Speed", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(1, 2, 4, 8).forEach { step ->
                    TerminalSpecialActionKey(
                        label = "${step}x",
                        active = scrollStep == step,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(step) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun TerminalSnippetRow(
    snippets: List<Snippet>,
    query: String,
    onQueryChange: (String) -> Unit,
    onRun: (Snippet) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            colors = terminalTextFieldColors(),
            label = { Text("Find snippets") },
            modifier = Modifier.fillMaxWidth()
        )
        if (snippets.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Snippets", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
                snippets.forEach { snippet ->
                    val color = when {
                        snippet.autoRun -> DeckColors.Green
                        snippet.favorite -> DeckColors.Orange
                        else -> DeckColors.Purple
                    }
                    TerminalButton(snippet.name, color) { onRun(snippet) }
                }
            }
        }
    }
}

@Composable
private fun terminalTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DeckColors.CardStroke,
    unfocusedBorderColor = DeckColors.CardStroke.copy(alpha = 0.72f),
    focusedTextColor = DeckColors.PrimaryText,
    unfocusedTextColor = DeckColors.PrimaryText,
    cursorColor = DeckColors.BrandAlt,
    focusedContainerColor = DeckColors.SurfaceMuted,
    unfocusedContainerColor = DeckColors.SurfaceMuted,
    focusedLabelColor = DeckColors.SecondaryText,
    unfocusedLabelColor = DeckColors.SecondaryText
)

@Composable
private fun InlineSnippetRunPanel(
    snippet: Snippet,
    server: ServerProfile,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit
) {
    val variables = snippetVariableNames(snippet)
    val render = renderSnippetCommandResult(snippet, server, values)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DeckColors.TerminalPanel)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        Text(snippet.name, color = DeckColors.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (snippet.description.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(snippet.description, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(8.dp))
        Text(render.command, color = DeckColors.SecondaryText, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        render.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = DeckColors.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        variables.forEach { variable ->
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = values[variable].orEmpty(),
                onValueChange = { onValueChange(variable, it) },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = terminalTextFieldColors(),
                label = { Text(variable) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TerminalButton("Run", DeckColors.Green, enabled = render.error == null) {
                if (render.error == null) onRun(render.command)
            }
            TerminalButton("Close", DeckColors.SecondaryText, onClick = onDismiss)
        }
    }
}

@Composable
private fun SnippetRunDialog(
    snippet: Snippet,
    server: ServerProfile,
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onRun: (String) -> Unit
) {
    val variables = snippetVariableNames(snippet)
    val render = renderSnippetCommandResult(snippet, server, values)
    val command = render.command
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Run snippet", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(snippet.name, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                if (snippet.description.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(snippet.description, color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 17.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(command, color = DeckColors.SecondaryText, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                render.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DeckColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                variables.forEach { variable ->
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = values[variable].orEmpty(),
                        onValueChange = { onValueChange(variable, it) },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = terminalTextFieldColors(),
                        label = { Text(variable) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = render.error == null, onClick = { if (render.error == null) onRun(command) }) {
                Text("Run", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

private enum class TerminalQuickAction {
    Snippets,
    Pointer,
    Paste,
    AccessoryKeys
}

private fun TerminalQuickAction.contentDescription(): String = when (this) {
    TerminalQuickAction.Snippets -> "Snippets"
    TerminalQuickAction.Pointer -> "Directional swipe"
    TerminalQuickAction.Paste -> "Paste"
    TerminalQuickAction.AccessoryKeys -> "Accessory keys"
}

@Composable
private fun TerminalRoundAction(
    action: TerminalQuickAction,
    enabled: Boolean = true,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = tween(110, easing = LinearOutSlowInEasing),
        label = "terminalRoundActionScale"
    )
    val iconColor by animateColorAsState(
        targetValue = if (enabled) DeckColors.PrimaryText else DeckColors.SecondaryText,
        animationSpec = tween(160, easing = LinearOutSlowInEasing),
        label = "terminalRoundActionIcon"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(38.dp)
            .semantics { contentDescription = action.contentDescription() }
            .clip(CircleShape)
            .background(if (active) DeckColors.TerminalAccent.copy(alpha = 0.24f) else if (enabled) DeckColors.SurfaceMuted else DeckColors.TerminalPanel)
            .border(1.dp, DeckColors.CardStroke, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        TerminalQuickActionGlyph(action, iconColor, Modifier.size(19.dp))
    }
}

@Composable
private fun TerminalQuickActionGlyph(action: TerminalQuickAction, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        when (action) {
            TerminalQuickAction.Snippets -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.15f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.70f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.36f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.52f), androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.68f), androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TerminalQuickAction.Pointer -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.16f), androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.60f), androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.84f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TerminalQuickAction.Paste -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.21f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.64f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.12f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.30f, size.height * 0.20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.48f), androidx.compose.ui.geometry.Offset(size.width * 0.65f, size.height * 0.48f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.64f), androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            TerminalQuickAction.AccessoryKeys -> {
                repeat(3) { row ->
                    repeat(3) { column ->
                        drawRoundRect(
                            color = color,
                            topLeft = androidx.compose.ui.geometry.Offset(size.width * (0.16f + column * 0.26f), size.height * (0.18f + row * 0.22f)),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.16f, size.height * 0.12f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                            style = stroke
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TerminalButton(
    text: String,
    color: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val animatedColor by animateColorAsState(
        targetValue = if (enabled) color else Color.White.copy(alpha = 0.38f),
        animationSpec = tween(180),
        label = "terminalButtonColor"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) DeckColors.SurfaceMuted else DeckColors.TerminalPanel)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = animatedColor, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun HostKeyReviewStrip(
    prompt: HostKeyPrompt,
    modifier: Modifier = Modifier,
    onTrustRemember: () -> Unit,
    onReject: () -> Unit
) {
    val canOfferTrustActions = terminalHostKeyCanTrust(prompt.state)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(DeckColors.TerminalPanel)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
            .padding(12.dp)
    ) {
        TerminalLine("warn host key review: ${prompt.host}:${prompt.port}", DeckColors.Orange)
        TerminalLine("${prompt.algorithm} ${prompt.fingerprint}", DeckColors.SecondaryText)
        TerminalLine(prompt.message, DeckColors.SecondaryText)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canOfferTrustActions) {
                TerminalButton("Trust & Connect", DeckColors.Green, onClick = onTrustRemember)
            }
            TerminalButton("Reject", DeckColors.Red, onClick = onReject)
        }
    }
}

internal fun terminalHostKeyCanTrust(state: HostKeyTrustState): Boolean {
    return state !in setOf(HostKeyTrustState.Changed, HostKeyTrustState.Rejected)
}

@Composable
private fun TerminalLine(text: String, color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color(0xFFE8EDF8)) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
}

@Composable
private fun EmptyTerminal() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(DeckColors.Terminal)
            .padding(18.dp)
    ) {
        TerminalLine("chronoSSH terminal", DeckColors.TerminalAccent)
        TerminalLine("No hosts yet. Add one from Servers or Vault.")
    }
}

@Composable
private fun OpeningTerminal() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeckColors.TerminalPanel)
            .padding(18.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TerminalLine("Opening terminal", DeckColors.TerminalAccent)
    }
}

@Composable
private fun TerminalChip(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.TerminalPanel)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun ClickableTerminalActionRow(
    terminalProfile: TerminalProfile,
    expanded: Boolean,
    directionalSwipeMode: Boolean,
    ctrlLatched: Boolean,
    altLatched: Boolean,
    shiftLatched: Boolean,
    onToggleDirectionalSwipe: () -> Unit,
    onOpenTmux: () -> Unit,
    onEnterScroll: () -> Unit,
    onOpenScrollSettings: () -> Unit,
    onSend: (String, String) -> Unit
) {
    val baseKeys = listOf("Pointer", "Tmux", "Scroll", "Esc", "Ctrl", "Alt", "AltGr", "Tab", "←", "↓", "↑", "→", "Enter", "Bksp")
    val moreKeys = listOf(
        "Home", "End", "PgUp", "PgDn", "Ins", "Del", "Shift", "/", "\\", "-", "_", "|", "~", ".",
        ":", ";", "(", ")", "[", "]", "{", "}", "<", ">", "F1", "F2", "F3", "F4",
        "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    )
    val labels = terminalActionRowLabels(terminalProfile, expanded, baseKeys, moreKeys)
    val terminalAccent = terminalComposeColor(terminalProfile.cursorHex)
    @Composable
    fun AccessoryButton(label: String) {
        when (label) {
            "Pointer" -> TerminalPointerToggle(directionalSwipeMode, terminalAccent, Modifier.widthIn(min = 42.dp), onToggleDirectionalSwipe)
            "Tmux" -> TerminalSpecialActionKey("tm", terminalAccent = terminalAccent, onClick = onOpenTmux)
            "Scroll" -> TerminalSpecialActionKey("scr", terminalAccent = terminalAccent, onClick = onEnterScroll, onLongClick = onOpenScrollSettings)
            else -> TerminalActionKey(label, terminalProfile, ctrlLatched, altLatched, shiftLatched, terminalAccent = terminalAccent, onSend = onSend)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeckColors.Terminal)
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (expanded && !terminalProfile.accessoryFullScroll && !terminalProfile.accessorySingleRow) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                labels.forEach { label -> AccessoryButton(label) }
            }
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                labels.forEach { label -> AccessoryButton(label) }
            }
        }
    }
}

@Composable
private fun TerminalPointerToggle(
    active: Boolean,
    terminalAccent: Color,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit
) {
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) terminalAccent.copy(alpha = 0.24f) else DeckColors.TerminalPanel)
            .border(1.dp, if (active) terminalAccent.copy(alpha = 0.5f) else DeckColors.CardStroke, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(17.dp)) {
            val strokeColor = if (active) DeckColors.PrimaryText else DeckColors.SecondaryText
            val strokeWidth = 2.1.dp.toPx()
            val x = size.width * 0.5f
            drawLine(strokeColor, Offset(x, size.height * 0.16f), Offset(x, size.height * 0.82f), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(x, size.height * 0.16f), Offset(size.width * 0.34f, size.height * 0.34f), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(x, size.height * 0.16f), Offset(size.width * 0.66f, size.height * 0.34f), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(size.width * 0.31f, size.height * 0.62f), Offset(x, size.height * 0.82f), strokeWidth, StrokeCap.Round)
            drawLine(strokeColor, Offset(size.width * 0.69f, size.height * 0.62f), Offset(x, size.height * 0.82f), strokeWidth, StrokeCap.Round)
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TerminalSpecialActionKey(
    label: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    terminalAccent: Color = DeckColors.TerminalAccent
) {
    val clickModifier = if (onLongClick == null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) terminalAccent.copy(alpha = 0.24f) else DeckColors.TerminalPanel)
            .border(1.dp, if (active) terminalAccent.copy(alpha = 0.5f) else DeckColors.CardStroke, RoundedCornerShape(12.dp))
            .then(clickModifier)
            .widthIn(min = 42.dp)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White.copy(alpha = 0.86f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun TerminalActionKey(
    label: String,
    terminalProfile: TerminalProfile,
    ctrlLatched: Boolean,
    altLatched: Boolean,
    shiftLatched: Boolean,
    modifier: Modifier = Modifier,
    terminalAccent: Color = DeckColors.TerminalAccent,
    onSend: (String, String) -> Unit
) {
    val active = (label == "Ctrl" && ctrlLatched) ||
        (label == "Alt" && altLatched) ||
        (label == "AltGr" && ctrlLatched && altLatched) ||
        (label == "Shift" && shiftLatched)
    val sequence = sequenceForTerminalLabel(label, terminalProfile)
    val clickModifier = if (terminalAccessoryKeyRepeats(label)) {
        Modifier.pointerInput(label, sequence, ctrlLatched, altLatched, shiftLatched) {
            awaitEachGesture {
                awaitFirstDown().consume()
                onSend(label, sequence)
                val releasedDuringDelay = withTimeoutOrNull(TERMINAL_ACCESSORY_INITIAL_REPEAT_DELAY_MS) {
                    waitForUpOrCancellation()
                }
                if (releasedDuringDelay != null) return@awaitEachGesture
                while (currentEvent.changes.any { it.pressed }) {
                    onSend(label, sequence)
                    val released = withTimeoutOrNull(TERMINAL_ACCESSORY_REPEAT_INTERVAL_MS) {
                        waitForUpOrCancellation()
                    }
                    if (released != null) break
                }
            }
        }
    } else {
        Modifier.clickable { onSend(label, sequence) }
    }
    Box(
        modifier = modifier
            .height(32.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) DeckColors.SurfaceMuted else DeckColors.TerminalPanel)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(12.dp))
            .widthIn(min = terminalAccessoryKeyMinWidth(label))
            .then(clickModifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = if (active) terminalAccent else Color.White.copy(alpha = 0.86f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

internal fun terminalAccessoryKeyRepeats(label: String): Boolean {
    return label in setOf("↑", "↓", "←", "→", "Home", "End", "PgUp", "PgDn", "Del", "Bksp")
}

internal fun terminalAccessoryExpandedMaxLines(labels: List<String>): Int {
    return ((labels.size + terminalAccessoryExpandedColumnCount(labels) - 1) / terminalAccessoryExpandedColumnCount(labels))
        .coerceAtLeast(1)
}

internal fun terminalAccessoryExpandedColumnCount(labels: List<String>): Int {
    return when {
        labels.size <= 18 -> 6
        labels.size <= 35 -> 7
        else -> 9
    }
}

internal fun terminalAccessoryPackedRows(labels: List<String>): List<List<String>> {
    val columns = terminalAccessoryExpandedColumnCount(labels)
    val rows = terminalAccessoryExpandedMaxLines(labels)
    val base = labels.size / rows
    val remainder = labels.size % rows
    var index = 0
    return List(rows) { row ->
        val rowSize = (base + if (row < remainder) 1 else 0).coerceIn(1, columns)
        labels.subList(index, (index + rowSize).coerceAtMost(labels.size)).also {
            index += it.size
        }
    }.filter { it.isNotEmpty() }
}

internal fun terminalAccessoryKeyMinWidth(label: String): Dp {
    return when {
        label.length <= 2 -> 34.dp
        label.length <= 5 -> 48.dp
        else -> 56.dp
    }
}

internal fun terminalDirectionForSwipe(deltaX: Float, deltaY: Float, threshold: Float = 18f): String? {
    val distance = kotlin.math.hypot(deltaX, deltaY)
    if (distance < threshold) return null
    return if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY)) {
        if (deltaX >= 0f) "→" else "←"
    } else {
        if (deltaY >= 0f) "↓" else "↑"
    }
}

private const val TERMINAL_ACCESSORY_INITIAL_REPEAT_DELAY_MS = 400L
private const val TERMINAL_ACCESSORY_REPEAT_INTERVAL_MS = 50L

internal fun terminalActionRowLabels(
    terminalProfile: TerminalProfile,
    expanded: Boolean,
    baseKeys: List<String> = listOf("Swipe", "Esc", "Ctrl", "Alt", "AltGr", "Tab", "←", "↓", "↑", "→", "Enter", "Bksp"),
    moreKeys: List<String> = listOf(
        "Home", "End", "PgUp", "PgDn", "Ins", "Del", "Shift", "/", "\\", "-", "_", "|", "~", ".",
        ":", ";", "(", ")", "[", "]", "{", "}", "<", ">", "F1", "F2", "F3", "F4",
        "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"
    )
): List<String> {
    val fixedKeys = listOf("Pointer", "Tmux", "Scroll")
    val profileKeys = terminalProfile.keyRows.firstOrNull()?.keys.orEmpty().map { it.label }.filter { it.isNotBlank() }
    val editableKeys = if (expanded) (profileKeys + baseKeys + moreKeys) else profileKeys.ifEmpty { baseKeys }
    return (fixedKeys + editableKeys.filterNot { it in fixedKeys }).distinct()
}

internal fun terminalQuickActionsVisible(
    keyboardVisible: Boolean,
    panelOpen: Boolean,
    searchOpen: Boolean
): Boolean = (keyboardVisible || panelOpen) && !searchOpen

internal fun terminalLatestOutputBlock(transcript: String, maxChars: Int = 4_000): String? {
    val lines = transcript.lines().map { it.trimEnd() }
    val end = lines.indexOfLast { it.isNotBlank() }
    if (end < 0) return null
    var start = end
    while (start > 0 && lines[start - 1].isNotBlank()) start--
    return lines.subList(start, end + 1)
        .joinToString("\n")
        .takeLast(maxChars.coerceAtLeast(1))
        .takeIf { it.isNotBlank() }
}

private fun keyLabel(sequence: String): String {
    return when (sequence) {
        "\u001B" -> "Esc"
        "\t" -> "Tab"
        "\u001B[A" -> "Up"
        "\u001B[B" -> "Down"
        "\u001B[D" -> "Left"
        "\u001B[C" -> "Right"
        "\r" -> "Enter"
        "\u007F" -> "Backspace"
        else -> sequence.ifBlank { "input" }
    }
}

internal fun terminalAccessoryShouldSendLiteral(sequence: String): Boolean {
    return sequence.length == 1 && sequence[0].code in 0x20..0x7e
}

internal data class TerminalAccessorySendResult(
    val output: String,
    val consumeCtrl: Boolean,
    val consumeAlt: Boolean,
    val consumeShift: Boolean
)

internal fun terminalAccessorySendResult(
    sequence: String,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean
): TerminalAccessorySendResult {
    if (terminalAccessoryShouldSendLiteral(sequence) && !ctrl && !alt && !shift) {
        return TerminalAccessorySendResult(sequence, consumeCtrl = false, consumeAlt = false, consumeShift = false)
    }
    val routed = TerminalModifierRouter.apply(sequence, ctrl, alt, shift)
    return TerminalAccessorySendResult(
        routed.output,
        consumeCtrl = routed.consumeCtrl || ctrl,
        consumeAlt = routed.consumeAlt || alt,
        consumeShift = routed.consumeShift || shift
    )
}

internal fun terminalAccessoryApplyScrollStep(output: String, scrollStep: Int): String {
    val step = scrollStep.coerceIn(1, 8)
    if (step == 1 || !terminalAccessoryScrollStepApplies(output)) return output
    return output.repeat(step)
}

private fun terminalAccessoryScrollStepApplies(output: String): Boolean {
    return output in setOf(
        "\u001B[A",
        "\u001B[B",
        "\u001B[D",
        "\u001B[C",
        "\u001B[5~",
        "\u001B[6~",
        "\u001B[H",
        "\u001B[F"
    )
}

private fun serverAccentColor(server: ServerProfile): Color {
    return Color(server.accent.argb.coerceIn(0L, 0xffffffffL).toInt())
}

private fun terminalComposeColor(hex: String): Color {
    return runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(DeckColors.Terminal)
}

private fun terminalTabFillColor(base: Color): Color {
    return Color(
        red = base.red + (1f - base.red) * 0.30f,
        green = base.green + (1f - base.green) * 0.30f,
        blue = base.blue + (1f - base.blue) * 0.30f,
        alpha = base.alpha
    )
}

private fun sequenceForTerminalLabel(label: String, terminalProfile: TerminalProfile): String {
    terminalProfile.keyRows.firstOrNull()?.keys.orEmpty().firstOrNull { it.label == label }?.let {
        return TerminalInputRouter.sequenceFor(it)
    }
    return defaultTerminalAccessorySequence(label)
}

internal fun defaultTerminalAccessorySequence(label: String): String {
    return when (label) {
        "Esc" -> "\u001B"
        "Ctrl" -> "<ctrl>"
        "Alt" -> "<alt>"
        "AltGr" -> "<altgr>"
        "Shift" -> "<shift>"
        "Tab" -> "\t"
        "↑", "Up" -> "\u001B[A"
        "↓", "Down" -> "\u001B[B"
        "←", "Left" -> "\u001B[D"
        "→", "Right" -> "\u001B[C"
        "Enter" -> "\r"
        "Bksp" -> "\u007F"
        "Home" -> "\u001B[H"
        "End" -> "\u001B[F"
        "PgUp" -> "\u001B[5~"
        "PgDn" -> "\u001B[6~"
        "Ins" -> "\u001B[2~"
        "Del" -> "\u001B[3~"
        "F1" -> "\u001BOP"
        "F2" -> "\u001BOQ"
        "F3" -> "\u001BOR"
        "F4" -> "\u001BOS"
        "F5" -> "\u001B[15~"
        "F6" -> "\u001B[17~"
        "F7" -> "\u001B[18~"
        "F8" -> "\u001B[19~"
        "F9" -> "\u001B[20~"
        "F10" -> "\u001B[21~"
        "F11" -> "\u001B[23~"
        "F12" -> "\u001B[24~"
        else -> label
    }
}

private fun saveSessionRecord(
    session: SshSession,
    server: ServerProfile,
    workspace: TerminalWorkspaceState,
    onTerminalSessionChanged: (TerminalSessionRecord) -> Unit
) {
    onTerminalSessionChanged(
        TerminalSessionRecord(
            id = session.id,
            serverId = server.id,
            title = server.name,
            status = ServerStatus.Online,
            startedAtEpochMillis = workspace.startedAtEpochMillis,
            lastActiveEpochMillis = System.currentTimeMillis(),
            transcriptPreview = session.transcriptPreview,
            tmuxSessionName = workspace.tmuxRestorableSessionName,
            tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
        )
    )
}

private fun Throwable.requiresPrivateKeyPassphrase(): Boolean {
    return SshAuthFailureHints.requiresPrivateKeyPassphrase(this)
}

internal fun snippetVariableNames(snippet: Snippet): List<String> {
    val declared = snippet.variables
    val inCommand = snippetVariableRegex().findAll(snippet.command).map { it.groupValues[1] }
    return (declared + inCommand).map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

internal fun terminalVisibleSnippets(
    snippets: List<Snippet>,
    serverId: String,
    query: String,
    limit: Int = 20
): List<Snippet> {
    val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
    return snippets
        .asSequence()
        .filter { it.serverScope == null || it.serverScope == serverId }
        .filter { snippet ->
            if (terms.isEmpty()) true else {
                val haystack = buildString {
                    append(snippet.name).append(' ')
                    append(snippet.command).append(' ')
                    append(snippet.tags.joinToString(" "))
                }.lowercase()
                terms.all { it in haystack }
            }
        }
        .sortedWith(
            compareBy<Snippet> { it.serverScope != serverId }
                .thenByDescending { it.favorite }
                .thenBy { it.group.lowercase() }
                .thenBy { it.name.lowercase() }
        )
        .take(limit)
        .toList()
}

internal fun defaultSnippetVariableValue(variable: String, server: ServerProfile): String {
    return when (variable.lowercase()) {
        "host" -> server.host
        "user", "username" -> server.username
        "port" -> server.port.toString()
        "name" -> server.name
        "service" -> "ssh"
        else -> ""
    }
}

internal fun renderSnippetCommand(
    snippet: Snippet,
    server: ServerProfile,
    values: Map<String, String>
): String {
    return renderSnippetCommandResult(snippet, server, values).command
}

internal data class SnippetCommandRender(
    val command: String,
    val error: String?
)

internal fun renderSnippetCommandResult(
    snippet: Snippet,
    server: ServerProfile,
    values: Map<String, String>
): SnippetCommandRender {
    val unsafeVariable = values.entries.firstOrNull { SnippetRenderPolicy.unsafeValue(it.value) }
    val command = renderSnippetCommandUnchecked(snippet, server, values)
    return SnippetCommandRender(
        command = command,
        error = unsafeVariable?.let { "Snippet variable '${it.key}' contains shell control characters." }
    )
}

private fun renderSnippetCommandUnchecked(
    snippet: Snippet,
    server: ServerProfile,
    values: Map<String, String>
): String {
    return snippetVariableRegex().replace(snippet.command) { match ->
        val variable = match.groupValues[1]
        values[variable] ?: defaultSnippetVariableValue(variable, server)
    }
}

internal object SnippetRenderPolicy {
    private val shellControlChars = setOf(';', '&', '|', '`', '$', '(', ')', '<', '>', '\\', '\'', '"', '\r', '\n')

    fun unsafeValue(value: String): Boolean = value.any { it in shellControlChars }
}

internal fun terminalSearchStatus(
    query: String,
    offsets: List<Int>,
    selection: TerminalSearchSelection?
): String {
    if (query.isBlank()) return "Ready"
    if (offsets.isEmpty()) return "0"
    val selected = selection?.takeIf { it.index in offsets.indices } ?: return offsets.size.toString()
    return "${selected.index + 1}/${offsets.size}"
}

private fun snippetVariableRegex() = Regex("""\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}""")
