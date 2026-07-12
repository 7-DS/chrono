package com.chrono.ssh.ui

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.core.content.ContextCompat
import com.chrono.ssh.core.data.ChronoSSHRepository
import com.chrono.ssh.core.data.CrashLogStore
import com.chrono.ssh.core.data.sanitizeLoadedSettings
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.TerminalSessionRecord
import com.chrono.ssh.core.model.CrashLogEntry
import com.chrono.ssh.core.service.AppLockCrashRecoveryPolicy
import com.chrono.ssh.core.service.BuildEditionPolicy
import com.chrono.ssh.core.service.ConnectionLaunchPolicy
import com.chrono.ssh.core.service.ConnectionLaunchSurface
import com.chrono.ssh.core.service.HostShareLinkCodec
import com.chrono.ssh.core.service.PinLockPolicy
import com.chrono.ssh.core.service.MonitoringProbeInFlightRegistry
import com.chrono.ssh.core.service.ServerStatusRefreshPolicy
import com.chrono.ssh.core.service.SftpSortMode
import com.chrono.ssh.core.service.TerminalSessionForegroundService
import com.chrono.ssh.core.service.TerminalSessionRegistry
import com.chrono.ssh.core.service.TcpReachabilityProbe
import com.chrono.ssh.core.service.UptimeMonitoringForegroundService
import com.chrono.ssh.core.service.WakeOnLanSender
import com.chrono.ssh.ui.design.AppBackground
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.DeckThemeCatalog
import com.chrono.ssh.ui.design.DeckThemeMode
import com.chrono.ssh.ui.design.HeadingFontFamilies
import com.chrono.ssh.ui.design.ChronoSSHTheme
import com.chrono.ssh.ui.design.headingFontFamilyFromPath
import com.chrono.ssh.ui.design.metricColorOverridesFrom
import com.chrono.ssh.ui.screens.HostEditorScreen
import com.chrono.ssh.ui.screens.HomeScreen
import com.chrono.ssh.ui.screens.HostsScreen
import com.chrono.ssh.ui.screens.PortForwardPage
import com.chrono.ssh.ui.screens.InterfacesScreen
import com.chrono.ssh.ui.screens.ConnectionsScreen
import com.chrono.ssh.ui.screens.RdpViewerScreen
import com.chrono.ssh.ui.screens.ServerActivityScreen
import com.chrono.ssh.ui.screens.ServerDetailScreen
import com.chrono.ssh.ui.screens.SettingsScreen
import com.chrono.ssh.ui.screens.SettingsSelectionPage
import com.chrono.ssh.ui.screens.SftpBrowserScreen
import com.chrono.ssh.ui.screens.SftpWorkspaceRuntime
import com.chrono.ssh.ui.screens.TerminalScreen
import com.chrono.ssh.ui.screens.TerminalWorkspaceState
import com.chrono.ssh.ui.screens.UptimeScreen
import com.chrono.ssh.ui.screens.VncViewerScreen
import com.chrono.ssh.ui.screens.defaultLocalForward
import com.chrono.ssh.ui.screens.terminalShouldRequestReconnectAfterDrop
import com.chrono.ssh.ui.screens.terminalUserFacingError
import com.chrono.ssh.ui.terminal.ChronoSSHTerminalEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

internal enum class AppTab(
    val label: String
) {
    Servers("Servers"),
    Connections("Connections"),
    Files("Files"),
    Vault("Vault"),
    Settings("Settings")
}

internal sealed interface ReturnTarget {
    data class Root(val tab: AppTab) : ReturnTarget
    data class ServerDetail(val serverId: String) : ReturnTarget
    data class Interfaces(val serverId: String) : ReturnTarget
    data class SftpBrowser(val serverId: String, val workspaceKey: String?) : ReturnTarget
    data class Terminal(val workspaceKey: String?, val previous: ReturnTarget?) : ReturnTarget
}

internal enum class HostDeepLinkTarget {
    Terminal,
    Sftp
}

internal data class HostDeepLink(
    val serverId: String,
    val target: HostDeepLinkTarget
)

internal fun terminalReturnTargetOnEntry(
    currentTarget: ReturnTarget,
    previousTarget: ReturnTarget?,
    terminalVisible: Boolean
): ReturnTarget? {
    return if (terminalVisible) previousTarget else currentTarget
}

internal enum class TerminalCloseIntent {
    HideSurface,
    DisconnectWorkspace
}

internal fun terminalCloseIntent(fromTerminalChrome: Boolean): TerminalCloseIntent {
    return if (fromTerminalChrome) TerminalCloseIntent.HideSurface else TerminalCloseIntent.DisconnectWorkspace
}

internal fun terminalReturnTargetAfterLastClose(previousTarget: ReturnTarget?): ReturnTarget {
    return previousTarget ?: ReturnTarget.Root(AppTab.Servers)
}

private fun AppSurface.rootTab(): AppTab? = when (this) {
    AppSurface.Home -> AppTab.Servers
    AppSurface.Connections -> AppTab.Connections
    AppSurface.Files -> AppTab.Files
    AppSurface.Vault -> AppTab.Vault
    AppSurface.Settings -> AppTab.Settings
    else -> null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun biometricUnlockAvailable(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
        @Suppress("DEPRECATION")
        val manager = context.getSystemService(FingerprintManager::class.java)
        @Suppress("DEPRECATION")
        manager?.isHardwareDetected == true && manager.hasEnrolledFingerprints()
    } else {
        // ponytail: AndroidX Biometric should replace this before framework biometric unlock is re-enabled.
        false
    }
}

private fun AppSurface.rootTabIndex(): Int? = rootTab()?.let { AppTab.entries.indexOf(it).takeIf { index -> index >= 0 } }

internal fun terminalMetricsSessionEligible(
    workspaceServerId: String,
    requestedServerId: String,
    connected: Boolean,
    hasSession: Boolean
): Boolean {
    return workspaceServerId == requestedServerId && connected && hasSession
}

internal fun terminalMetricsSessionLoopKey(workspaces: Collection<TerminalWorkspaceState>): String {
    return workspaces
        .filter { terminalMetricsSessionEligible(it.serverId, it.serverId, it.connected, it.session != null) }
        .map { it.serverId }
        .sorted()
        .joinToString("|")
}

internal fun foregroundServiceShouldRun(activeConnectionCount: Int): Boolean = activeConnectionCount > 0

internal fun foregroundServiceConnectionCount(activeConnectionCount: Int, registeredTerminalSessionCount: Int): Int {
    return maxOf(activeConnectionCount, registeredTerminalSessionCount.coerceAtLeast(0))
}

internal fun terminalForegroundServiceShouldRun(activeTerminalSessionCount: Int): Boolean =
    foregroundServiceShouldRun(activeTerminalSessionCount)

internal fun terminalShouldProbeReconnectOnForeground(connected: Boolean, hasSession: Boolean): Boolean {
    return connected && !hasSession
}

internal fun backgroundUsagePromptVisible(activeConnectionCount: Int, backgroundUsageAllowed: Boolean): Boolean {
    return activeConnectionCount > 0 && !backgroundUsageAllowed
}

internal fun notificationPermissionRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

internal fun notificationPermissionAlreadyAllowed(context: Context): Boolean {
    return !notificationPermissionRequired() ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

internal fun notificationPermissionPromptVisible(activeConnectionCount: Int, notificationAllowed: Boolean): Boolean {
    return activeConnectionCount > 0 && !notificationAllowed
}

internal fun backgroundUsageAlreadyAllowed(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

internal fun backgroundUsageSettingsIntent(context: Context): Intent {
    return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
    }
}

internal fun fallbackBackgroundUsageSettingsIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

internal fun appShouldRelockOnResume(
    hasAppLockPin: Boolean,
    wasBackgrounded: Boolean,
    currentlyLocked: Boolean
): Boolean {
    return hasAppLockPin && wasBackgrounded && !currentlyLocked
}

internal fun appLockStateAfterSettingsChange(
    wasEnabled: Boolean,
    isEnabled: Boolean,
    currentlyLocked: Boolean
): Boolean {
    return when {
        !isEnabled -> false
        !wasEnabled -> false
        else -> currentlyLocked
    }
}

internal fun appLockStateAfterLockNow(hasAppLockPin: Boolean): Boolean = hasAppLockPin

internal fun initialAppLockState(hasPersistedPin: Boolean): Boolean = hasPersistedPin

internal fun appLockRenderCrashContext(settings: AppSettings): String? {
    if (!appLockPinUsable(settings)) return null
    return "AppLock render armed: persisted PIN present; disable app lock if startup Compose crashes before unlock."
}

internal fun appLockPinUsable(settings: AppSettings): Boolean {
    return PinLockPolicy.persistedPinUsable(settings.appLockPinHash, settings.appLockPinSalt)
}

internal fun appLockRecoveredSettings(settings: AppSettings, pin: String): AppSettings? {
    if (appLockPinUsable(settings)) return null
    if (settings.appLockPinHash.isNullOrBlank() && settings.appLockPinSalt.isNullOrBlank() && !settings.appLockBiometricEnabled && settings.appLockRenderArmedAtEpochMillis == null) return null
    if (pin.isNotBlank() && pin.length < 6) return null
    return settings.copy(appLockPinHash = null, appLockPinSalt = null, appLockBiometricEnabled = false, appLockRenderArmedAtEpochMillis = null)
}

internal fun startupSettings(settings: AppSettings): AppSettings = sanitizeLoadedSettings(settings)

internal fun startupSettingsAfterCrashRecovery(
    settings: AppSettings,
    crashes: List<CrashLogEntry>,
    nowEpochMillis: Long
): AppSettings {
    val clean = startupSettings(settings)
    if (!appLockPinUsable(clean) && !clean.appLockBiometricEnabled) return clean.copy(appLockRenderArmedAtEpochMillis = null)
    return if (
        AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(crashes, nowEpochMillis) ||
        appLockRenderArmedMarkerExpired(clean.appLockRenderArmedAtEpochMillis, nowEpochMillis)
    ) {
        clean.copy(appLockPinHash = null, appLockPinSalt = null, appLockBiometricEnabled = false, appLockRenderArmedAtEpochMillis = null)
    } else {
        clean
    }
}

internal fun appLockRenderArmedMarkerExpired(armedAtEpochMillis: Long?, nowEpochMillis: Long): Boolean {
    return AppLockCrashRecoveryPolicy.renderMarkerExpired(armedAtEpochMillis, nowEpochMillis)
}

internal data class PersistedSettingsState(
    val settings: AppSettings,
    val locked: Boolean
)

internal fun persistedSettingsStateAfterChange(
    previousSettings: AppSettings,
    nextSettings: AppSettings,
    currentlyLocked: Boolean
): PersistedSettingsState {
    val cleanSettings = startupSettings(nextSettings)
    return PersistedSettingsState(
        settings = cleanSettings,
        locked = appLockStateAfterSettingsChange(
            wasEnabled = appLockPinUsable(previousSettings),
            isEnabled = appLockPinUsable(cleanSettings),
            currentlyLocked = currentlyLocked
        )
    )
}

internal fun appLockWasBackgroundedAfterLifecycleEvent(previous: Boolean, event: Lifecycle.Event): Boolean {
    return previous || event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP
}

internal fun connectionBackTarget(transientReturnTarget: ReturnTarget?): ReturnTarget {
    return transientReturnTarget ?: ReturnTarget.Root(AppTab.Servers)
}

internal fun vaultInitialSectionForRoute(requestedSection: String?, credentialId: String?): String? {
    return requestedSection ?: credentialId?.let { "Keys" }
}

internal fun shouldCollectMetricsBeforeProbe(
    monitoringEnabled: Boolean,
    credentialReady: Boolean,
    hostTrusted: Boolean,
    alreadyCollecting: Boolean,
    refreshDue: Boolean
): Boolean {
    return monitoringEnabled && credentialReady && hostTrusted && !alreadyCollecting && refreshDue
}

internal fun manualRefreshShouldCollectMetricsBeforeProbe(
    monitoringEnabled: Boolean,
    credentialReady: Boolean,
    hostTrusted: Boolean,
    alreadyCollecting: Boolean
): Boolean {
    return shouldCollectMetricsBeforeProbe(
        monitoringEnabled = monitoringEnabled,
        credentialReady = credentialReady,
        hostTrusted = hostTrusted,
        alreadyCollecting = alreadyCollecting,
        refreshDue = true
    )
}

internal data class MetricsRefreshSkipDiagnostic(
    val level: ConnectionEventLevel,
    val reason: String
)

internal fun metricsRefreshSkipDiagnostic(
    monitoringEnabled: Boolean,
    credentialReady: Boolean,
    hostTrusted: Boolean,
    alreadyCollecting: Boolean,
    refreshDue: Boolean,
    serverName: String
): MetricsRefreshSkipDiagnostic? {
    return when {
        !refreshDue -> null
        !monitoringEnabled -> MetricsRefreshSkipDiagnostic(ConnectionEventLevel.Info, "monitoring is disabled for $serverName.")
        !credentialReady -> MetricsRefreshSkipDiagnostic(ConnectionEventLevel.Warning, "save a password or private key for $serverName.")
        !hostTrusted -> MetricsRefreshSkipDiagnostic(ConnectionEventLevel.Warning, "approve the SSH host key for $serverName.")
        alreadyCollecting -> MetricsRefreshSkipDiagnostic(ConnectionEventLevel.Info, "refresh already running for $serverName.")
        else -> null
    }
}

internal fun shouldRunTcpProbeForLoop(
    refreshDue: Boolean,
    lastProbeAtEpochMillis: Long?,
    nowEpochMillis: Long,
    minProbeIntervalMillis: Long
): Boolean {
    if (refreshDue) return true
    val lastProbeAt = lastProbeAtEpochMillis ?: return true
    return nowEpochMillis - lastProbeAt >= minProbeIntervalMillis
}

internal fun shouldRunTcpProbeAfterMetricsGate(
    alreadyCollecting: Boolean,
    refreshDue: Boolean,
    lastProbeAtEpochMillis: Long?,
    nowEpochMillis: Long,
    minProbeIntervalMillis: Long
): Boolean {
    if (alreadyCollecting) return false
    return shouldRunTcpProbeForLoop(refreshDue, lastProbeAtEpochMillis, nowEpochMillis, minProbeIntervalMillis)
}

internal fun parseHostDeepLink(payload: String): HostDeepLink? {
    val clean = payload.trim()
    if (clean.length > 8_192 || !clean.startsWith("chronossh://", ignoreCase = true)) return null
    val route = clean.substringAfter("://", missingDelimiterValue = "")
    val authority = route.takeWhile { it != '?' && it != '/' && it != '#' }
    if (!authority.equals("host", ignoreCase = true)) return null
    val pathTarget = route.drop(authority.length).substringBefore("?").substringBefore("#").trim('/')
    val params = parseHostDeepLinkQuery(route.substringAfter("?", missingDelimiterValue = "").substringBefore("#")) ?: return null
    val serverId = params["id"]?.trim()?.takeIf { it.isNotBlank() && it.length <= 256 } ?: return null
    val target = when ((params["open"] ?: params["target"] ?: pathTarget).trim().lowercase()) {
        "terminal", "shell", "ssh" -> HostDeepLinkTarget.Terminal
        "sftp", "files" -> HostDeepLinkTarget.Sftp
        else -> return null
    }
    return HostDeepLink(serverId, target)
}

private fun parseHostDeepLinkQuery(query: String): Map<String, String>? {
    if (query.isBlank()) return emptyMap()
    val tokens = query.split("&")
    if (tokens.size > 64) return null
    return tokens.mapNotNull { token ->
        val key = urlDecode(token.substringBefore("=", missingDelimiterValue = "").trim()) ?: return null
        if (key.isBlank()) return@mapNotNull null
        val value = urlDecode(token.substringAfter("=", missingDelimiterValue = "")) ?: return null
        if (key.length > 64 || value.length > 2_048) return null
        key to value
    }.toMap()
}

private fun urlDecode(value: String): String? = runCatching {
    URLDecoder.decode(value, StandardCharsets.UTF_8.name())
}.getOrNull()

@Composable
fun ChronoSSHApp(
    inboundHostShareLink: String? = null,
    onInboundHostShareLinkConsumed: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val repositoryResult = remember { runCatching { ChronoSSHRepository(context.applicationContext) } }
    val repository = repositoryResult.getOrElse { error ->
        StartupRecoveryScreen(error)
        return
    }
    val initialSettings = remember {
        val loaded = repository.loadSettings()
        startupSettingsAfterCrashRecovery(
            settings = loaded,
            crashes = CrashLogStore.load(context.applicationContext),
            nowEpochMillis = System.currentTimeMillis()
        ).also { recovered ->
            if (recovered != loaded) {
                repository.saveSettings(recovered)
                CrashLogStore.clear(context.applicationContext)
            }
        }
    }
    val probe = remember { TcpReachabilityProbe() }
    val wakeOnLanSender = remember { WakeOnLanSender() }
    val probeRegistry = remember { MonitoringProbeInFlightRegistry() }
    val loopProbeLastAt = remember { androidx.compose.runtime.mutableStateMapOf<String, Long>() }
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Servers) }
    var selectedServerId by remember { mutableStateOf<String?>(null) }
    var showUptime by remember { mutableStateOf(false) }
    var showInterfacesFor by remember { mutableStateOf<String?>(null) }
    var showActivityFor by remember { mutableStateOf<String?>(null) }
    var portForwardFor by remember { mutableStateOf<String?>(null) }
    var vncViewerFor by remember { mutableStateOf<String?>(null) }
    var rdpViewerFor by remember { mutableStateOf<String?>(null) }
    var editingServerId by remember { mutableStateOf<String?>(null) }
    var creatingServer by remember { mutableStateOf(false) }
    var hostEditorError by remember { mutableStateOf<String?>(null) }
    var selectedConnectionServerId by remember { mutableStateOf<String?>(null) }
    var terminalVisible by remember { mutableStateOf(false) }
    var transientReturnTarget by remember { mutableStateOf<ReturnTarget?>(null) }
    var sftpReturnTarget by remember { mutableStateOf<ReturnTarget?>(null) }
    var autoConnectRequestId by remember { mutableStateOf<String?>(null) }
    var vaultCredentialToOpen by remember { mutableStateOf<String?>(null) }
    var vaultInitialSection by remember { mutableStateOf<String?>(null) }
    var vaultInitialSectionRequestKey by remember { mutableStateOf(0) }
    var vaultForwardServerId by remember { mutableStateOf<String?>(null) }
    var vaultForwardDraftRequestKey by remember { mutableStateOf(0) }
    var filesServerId by remember { mutableStateOf<String?>(null) }
    var sftpBrowserServerId by remember { mutableStateOf<String?>(null) }
    var selectedSftpWorkspaceKey by remember { mutableStateOf<String?>(null) }
    var settingsSelectionPage by remember { mutableStateOf<SettingsSelectionPage?>(null) }
    var themeMode by remember {
        mutableStateOf(runCatching { DeckThemeMode.valueOf(initialSettings.themeModeName) }.getOrDefault(DeckThemeMode.System))
    }
    var themeFamilyId by remember { mutableStateOf(initialSettings.themeFamilyId) }
    var appSettings by remember { mutableStateOf(initialSettings) }
    var appLocked by remember { mutableStateOf(appLockPinUsable(initialSettings)) }
    var appWasBackgrounded by remember { mutableStateOf(false) }
    var hostInfoDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val systemDark = isSystemInDarkTheme()
    val palette = DeckThemeCatalog.paletteFor(themeMode, themeFamilyId, systemDark)
    val terminalWorkspaces = remember {
        androidx.compose.runtime.mutableStateMapOf<String, TerminalWorkspaceState>()
    }
    val sftpWorkspaces = remember {
        androidx.compose.runtime.mutableStateMapOf<String, String>()
    }
    val sftpRuntimes = remember {
        androidx.compose.runtime.mutableStateMapOf<String, SftpWorkspaceRuntime>()
    }
    val activeTerminalSessionCount = terminalWorkspaces.values.count { it.connected }
    LaunchedEffect(activeTerminalSessionCount) {
        TerminalSessionRegistry.pruneDisconnected()
    }
    val activeNonTerminalConnectionCount = sftpWorkspaces.size + listOfNotNull(vncViewerFor, rdpViewerFor).size
    val activeConnectionCount = activeTerminalSessionCount + activeNonTerminalConnectionCount
    val registeredTerminalSessionCount by TerminalSessionRegistry.activeCountFlow.collectAsState()
    val serviceConnectionCount = foregroundServiceConnectionCount(
        activeConnectionCount = activeConnectionCount,
        registeredTerminalSessionCount = registeredTerminalSessionCount
    )
    var backgroundUsageAllowed by remember { mutableStateOf(backgroundUsageAlreadyAllowed(context)) }
    var notificationPermissionAllowed by remember {
        mutableStateOf(notificationPermissionAlreadyAllowed(context))
    }
    fun refreshRuntimePermissionState() {
        notificationPermissionAllowed = notificationPermissionAlreadyAllowed(context)
        backgroundUsageAllowed = backgroundUsageAlreadyAllowed(context)
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationPermissionAllowed = granted || notificationPermissionAlreadyAllowed(context)
    }
    val backgroundUsageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refreshRuntimePermissionState()
    }
    fun requestNotificationPermission() {
        if (!notificationPermissionRequired()) {
            notificationPermissionAllowed = true
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(context, activeConnectionCount) {
        refreshRuntimePermissionState()
    }
    LaunchedEffect(context, serviceConnectionCount, activeNonTerminalConnectionCount) {
        TerminalSessionForegroundService.setRunning(
            context,
            foregroundServiceShouldRun(serviceConnectionCount),
            connectionCount = serviceConnectionCount,
            nonTerminalConnectionCount = activeNonTerminalConnectionCount
        )
    }
    LaunchedEffect(context, appSettings.uptimeBackgroundMonitoringEnabled) {
        UptimeMonitoringForegroundService.setRunning(context, appSettings.uptimeBackgroundMonitoringEnabled)
    }
    LaunchedEffect(context, activeConnectionCount, repository.servers.size) {
        while (true) {
            val snapshots = TerminalSessionRegistry.snapshots()
            if (snapshots.isEmpty()) break
            val serversById = repository.servers.associateBy { it.id }
            snapshots.forEach { snapshot ->
                val server = serversById[snapshot.serverId]
                repository.upsertTerminalSession(
                    TerminalSessionRecord(
                        id = snapshot.sessionId,
                        serverId = snapshot.serverId,
                        title = server?.name ?: snapshot.serverId,
                        status = ServerStatus.Online,
                        startedAtEpochMillis = snapshot.attachedAtEpochMillis,
                        lastActiveEpochMillis = snapshot.lastSeenEpochMillis,
                        transcriptPreview = snapshot.transcriptPreview
                    )
                )
            }
            val snapshotServiceConnectionCount = foregroundServiceConnectionCount(
                activeConnectionCount = activeConnectionCount,
                registeredTerminalSessionCount = snapshots.size
            )
            TerminalSessionForegroundService.setRunning(
                context,
                foregroundServiceShouldRun(snapshotServiceConnectionCount),
                connectionCount = snapshotServiceConnectionCount,
                nonTerminalConnectionCount = activeNonTerminalConnectionCount
            )
            delay(15_000L)
        }
    }
    fun requestBackgroundUsage() {
        runCatching { backgroundUsageLauncher.launch(backgroundUsageSettingsIntent(context)) }
            .onFailure { backgroundUsageLauncher.launch(fallbackBackgroundUsageSettingsIntent()) }
    }

    fun persistSettings(settings: AppSettings) {
        val nextState = persistedSettingsStateAfterChange(appSettings, settings, appLocked)
        val cleanSettings = nextState.settings
        appSettings = cleanSettings
        appLocked = nextState.locked
        themeMode = runCatching { DeckThemeMode.valueOf(cleanSettings.themeModeName) }.getOrDefault(DeckThemeMode.System)
        themeFamilyId = cleanSettings.themeFamilyId
        repository.saveSettings(cleanSettings)
    }

    fun armAppLockRenderMarker() {
        if (!appLocked || !appLockPinUsable(appSettings) || appSettings.appLockRenderArmedAtEpochMillis != null) return
        persistSettings(appSettings.copy(appLockRenderArmedAtEpochMillis = System.currentTimeMillis()))
    }

    fun clearAppLockRenderMarker() {
        if (appSettings.appLockRenderArmedAtEpochMillis == null) return
        persistSettings(appSettings.copy(appLockRenderArmedAtEpochMillis = null))
    }

    LaunchedEffect(appSettings.appLockPinHash, appSettings.appLockPinSalt) {
        if (appSettings.appLockPinHash != null && !appLockPinUsable(appSettings)) {
            persistSettings(appSettings.copy(appLockPinHash = null, appLockPinSalt = null, appLockBiometricEnabled = false))
        }
    }

    fun persistTheme(nextMode: DeckThemeMode = themeMode, nextFamily: String = themeFamilyId) {
        val availableFamilies = DeckThemeCatalog.familiesFor(nextMode, systemDark)
        val normalizedFamily = nextFamily.takeIf { candidate -> availableFamilies.any { it.id == candidate } }
            ?: availableFamilies.firstOrNull()?.id
            ?: DeckThemeCatalog.DEFAULT_FAMILY_ID
        persistSettings(appSettings.copy(themeModeName = nextMode.name, themeFamilyId = normalizedFamily))
    }

    DisposableEffect(context, appSettings.appLockPinHash, appLocked) {
        val owner = context.findActivity() as? LifecycleOwner
        if (owner == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> appWasBackgrounded = appLockWasBackgroundedAfterLifecycleEvent(appWasBackgrounded, event)
                Lifecycle.Event.ON_RESUME -> {
                    refreshRuntimePermissionState()
                    if (appShouldRelockOnResume(appLockPinUsable(appSettings), appWasBackgrounded, appLocked)) {
                        appLocked = true
                    }
                    terminalWorkspaces.values.forEach { workspace ->
                        if (terminalShouldProbeReconnectOnForeground(workspace.connected, workspace.session != null)) {
                            workspace.status = "Reconnecting"
                            workspace.lastAction = "Reconnecting after returning to chronoSSH"
                            workspace.requestReconnect()
                        }
                    }
                    appWasBackgrounded = false
                }
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    fun activeTerminalMetricsSession(server: ServerProfile) = terminalWorkspaces.values.firstOrNull { workspace ->
        terminalMetricsSessionEligible(
            workspaceServerId = workspace.serverId,
            requestedServerId = server.id,
            connected = workspace.connected,
            hasSession = workspace.session != null
        )
    }?.session

    fun probeServer(serverId: String) {
        val server = repository.servers.firstOrNull { it.id == serverId } ?: return
        repository.markConnecting(server.id)
        scope.launch {
            val credentialReady = repository.credentialFor(server)?.secretBacked == true
            val hostTrusted = repository.knownHostFor(server)?.trusted == true
            val alreadyCollecting = repository.isCollectingMetrics(server.id)
            if (manualRefreshShouldCollectMetricsBeforeProbe(
                    monitoringEnabled = server.monitoringConfig.enabled,
                    credentialReady = credentialReady,
                    hostTrusted = hostTrusted,
                    alreadyCollecting = alreadyCollecting
                )
            ) {
                activeTerminalMetricsSession(server)?.let { session ->
                    repository.collectMetricsFromSession(server, session)
                } ?: repository.collectMetrics(server)
                return@launch
            }
            metricsRefreshSkipDiagnostic(
                monitoringEnabled = server.monitoringConfig.enabled,
                credentialReady = credentialReady,
                hostTrusted = hostTrusted,
                alreadyCollecting = alreadyCollecting,
                refreshDue = true,
                serverName = server.name
            )?.let { diagnostic ->
                repository.noteMetricsRefreshSkipped(server, diagnostic.level, diagnostic.reason)
            }
            val result = probe.probe(server)
            repository.updateProbeResult(
                serverId = server.id,
                status = if (result.reachable) ServerStatus.Online else ServerStatus.Offline,
                latencyMs = result.latencyMs,
                message = result.message
            )
            if (result.reachable) {
                val session = activeTerminalMetricsSession(server)
                if (session == null) {
                    repository.collectMetrics(server)
                } else {
                    repository.collectMetricsFromSession(server, session)
                }
            }
        }
    }

    fun reviewHostKey(server: ServerProfile) {
        scope.launch {
            repository.reviewOrTrustKnownHost(server)
        }
    }

    fun wakeHost(server: ServerProfile) {
        val config = server.wakeOnLan ?: return
        scope.launch {
            repository.appendEvent(server.id, ConnectionEventLevel.Info, "Sending Wake-on-LAN packet to ${config.macAddress} via ${config.broadcastAddress}.")
            runCatching { wakeOnLanSender.wake(config) }
                .onSuccess {
                    repository.appendEvent(server.id, ConnectionEventLevel.Success, "Wake-on-LAN packet sent.")
                }
                .onFailure { error ->
                    repository.appendEvent(server.id, ConnectionEventLevel.Error, "Wake-on-LAN failed: ${error.message ?: error::class.java.simpleName}")
                }
        }
    }

    fun openConnectionWorkspace(server: ServerProfile, duplicate: Boolean = false, workspaceKeyOverride: String? = null): TerminalWorkspaceState {
        val workspaceKey = workspaceKeyOverride ?: if (duplicate) "${server.id}|${System.currentTimeMillis()}" else server.id
        selectedConnectionServerId = workspaceKey
        return terminalWorkspaces.getOrPut(workspaceKey) {
            TerminalWorkspaceState(
                serverId = server.id,
                workspaceId = workspaceKey,
                engineFactory = {
                    ChronoSSHTerminalEngine(
                        profile = repository.terminalProfile,
                        onChanged = {
                            terminalWorkspaces[workspaceKey]?.let { it.invalidations += 1 }
                        },
                        onSessionDisconnected = { failedSession, error ->
                            terminalWorkspaces[workspaceKey]?.let { workspace ->
                                if (workspace.session === failedSession) {
                                    workspace.session = null
                                    workspace.lastAction = error?.let(::terminalUserFacingError) ?: "Connection interrupted. Reconnect to continue."
                                    workspace.detachEngineIfInitialized()
                                    repository.appendEvent(server.id, ConnectionEventLevel.Warning, "Terminal session dropped: ${workspace.lastAction}")
                                    if (terminalShouldRequestReconnectAfterDrop(
                                            autoReconnect = server.reconnectPolicy.autoReconnect,
                                            hasPendingPassphrase = workspace.pendingPassphraseDecision != null,
                                            hasPendingHostKey = workspace.pendingHostKey != null
                                        )
                                    ) {
                                        workspace.status = "Reconnecting"
                                        workspace.lastAction = "Reconnecting after interruption"
                                        workspace.requestReconnect()
                                    } else {
                                        workspace.status = "Closed"
                                    }
                                    repository.upsertTerminalSession(
                                        TerminalSessionRecord(
                                            id = failedSession.id,
                                            serverId = server.id,
                                            title = server.name,
                                            status = ServerStatus.Offline,
                                            startedAtEpochMillis = workspace.startedAtEpochMillis,
                                            lastActiveEpochMillis = System.currentTimeMillis(),
                                            transcriptPreview = failedSession.transcriptPreview,
                                            tmuxSessionName = workspace.tmuxRestorableSessionName,
                                            tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                                        )
                                    )
                                }
                            }
                        }
                    )
                }
            ).also { workspace ->
                val restorable = repository.terminalSessions
                    .filter { it.serverId == server.id && !it.tmuxSessionName.isNullOrBlank() }
                    .maxByOrNull { it.lastActiveEpochMillis }
                workspace.tmuxRestorableSessionName = restorable?.tmuxSessionName
                workspace.tmuxRestorableWindowIndex = restorable?.tmuxWindowIndex
            }
        }
    }

    LaunchedEffect(repository.servers.size, registeredTerminalSessionCount) {
        val serversById = repository.servers.associateBy { it.id }
        val snapshots = TerminalSessionRegistry.snapshots()
        snapshots.forEach { snapshot ->
            if (snapshot.workspaceId !in terminalWorkspaces) {
                serversById[snapshot.serverId]?.let { server ->
                    openConnectionWorkspace(server, workspaceKeyOverride = snapshot.workspaceId).apply {
                        status = "Restoring"
                        lastAction = "Restoring background shell"
                        startedAtEpochMillis = snapshot.attachedAtEpochMillis
                    }
                }
            }
        }
        if (snapshots.isNotEmpty() && !terminalVisible) {
            selectedConnectionServerId = snapshots.first().workspaceId
            selectedTab = AppTab.Connections
            terminalVisible = true
        }
    }

    fun currentReturnTarget(): ReturnTarget {
        return when {
            sftpBrowserServerId != null -> ReturnTarget.SftpBrowser(sftpBrowserServerId!!, selectedSftpWorkspaceKey)
            showInterfacesFor != null -> ReturnTarget.Interfaces(showInterfacesFor!!)
            showActivityFor != null -> ReturnTarget.ServerDetail(showActivityFor!!)
            selectedServerId != null -> ReturnTarget.ServerDetail(selectedServerId!!)
            else -> ReturnTarget.Root(selectedTab)
        }
    }

    fun restoreReturnTarget(target: ReturnTarget?) {
        selectedServerId = null
        showUptime = false
        showInterfacesFor = null
        showActivityFor = null
        vncViewerFor = null
        rdpViewerFor = null
        creatingServer = false
        editingServerId = null
        hostEditorError = null
        when (target) {
            is ReturnTarget.ServerDetail -> {
                selectedTab = AppTab.Servers
                selectedServerId = target.serverId
            }
            is ReturnTarget.Interfaces -> {
                selectedTab = AppTab.Servers
                showInterfacesFor = target.serverId
            }
            is ReturnTarget.SftpBrowser -> {
                selectedTab = AppTab.Connections
                sftpBrowserServerId = target.serverId
                selectedSftpWorkspaceKey = target.workspaceKey
            }
            is ReturnTarget.Terminal -> {
                selectedTab = AppTab.Connections
                selectedConnectionServerId = target.workspaceKey
                selectedSftpWorkspaceKey = null
                sftpBrowserServerId = null
                terminalVisible = true
                transientReturnTarget = target.previous
            }
            is ReturnTarget.Root -> selectedTab = target.tab
            null -> Unit
        }
    }

    fun launchConnection(server: ServerProfile) {
        if (!BuildEditionPolicy.supports(server.protocol)) return
        when (ConnectionLaunchPolicy.surface(server.protocol)) {
            ConnectionLaunchSurface.DesktopViewer -> {
                selectedServerId = null
                showInterfacesFor = null
                showActivityFor = null
                sftpBrowserServerId = null
                selectedSftpWorkspaceKey = null
                creatingServer = false
                editingServerId = null
                terminalVisible = false
                when (server.protocol) {
                    ConnectionProtocol.Vnc -> vncViewerFor = server.id
                    ConnectionProtocol.Rdp -> rdpViewerFor = server.id
                    else -> Unit
                }
            }
            ConnectionLaunchSurface.FileBrowser -> {
                sftpReturnTarget = if (terminalVisible) {
                    ReturnTarget.Terminal(selectedConnectionServerId, transientReturnTarget)
                } else if (transientReturnTarget == null) {
                    currentReturnTarget()
                } else {
                    transientReturnTarget
                }
                val workspaceKey = "sftp:${server.id}|${System.currentTimeMillis()}"
                sftpWorkspaces[workspaceKey] = server.id
                sftpRuntimes[workspaceKey] = SftpWorkspaceRuntime()
                selectedSftpWorkspaceKey = workspaceKey
                selectedConnectionServerId = terminalWorkspaces.entries.firstOrNull { entry -> entry.value.serverId == server.id }?.key ?: server.id
                sftpBrowserServerId = server.id
                terminalVisible = false
                selectedTab = AppTab.Connections
            }
            ConnectionLaunchSurface.Terminal -> {
                runCatching {
                    transientReturnTarget = terminalReturnTargetOnEntry(currentReturnTarget(), transientReturnTarget, terminalVisible)
                    selectedServerId = null
                    showInterfacesFor = null
                    showActivityFor = null
                    sftpBrowserServerId = null
                    selectedSftpWorkspaceKey = null
                    creatingServer = false
                    editingServerId = null
                    openConnectionWorkspace(server)
                    selectedTab = AppTab.Connections
                    terminalVisible = true
                    autoConnectRequestId = null
                }.onFailure { error ->
                    repository.recordCrashContext(
                        "Terminal launch failed for ${server.name} (${server.id}) with " +
                            "${error::class.java.name}: ${error.message.orEmpty()}. " +
                            "font=${repository.terminalProfile.fontFamily}, theme=${repository.terminalProfile.themeName}."
                    )
                    selectedTab = AppTab.Connections
                    terminalVisible = false
                }
            }
        }
    }

    fun duplicateConnection(server: ServerProfile) {
        transientReturnTarget = terminalReturnTargetOnEntry(currentReturnTarget(), transientReturnTarget, terminalVisible)
        openConnectionWorkspace(server, duplicate = true)
        selectedTab = AppTab.Connections
        terminalVisible = true
        autoConnectRequestId = null
    }

    fun openSftpWorkspace(server: ServerProfile) {
        sftpReturnTarget = if (terminalVisible) {
            ReturnTarget.Terminal(selectedConnectionServerId, transientReturnTarget)
        } else if (transientReturnTarget == null) {
            currentReturnTarget()
        } else {
            transientReturnTarget
        }
        val workspaceKey = "sftp:${server.id}|${System.currentTimeMillis()}"
        sftpWorkspaces[workspaceKey] = server.id
        sftpRuntimes[workspaceKey] = SftpWorkspaceRuntime()
        selectedSftpWorkspaceKey = workspaceKey
        selectedConnectionServerId = terminalWorkspaces.entries.firstOrNull { entry -> entry.value.serverId == server.id }?.key ?: server.id
        sftpBrowserServerId = server.id
        terminalVisible = false
        selectedTab = AppTab.Connections
    }

    LaunchedEffect(inboundHostShareLink, appLocked) {
        val payload = inboundHostShareLink ?: return@LaunchedEffect
        if (appLocked) return@LaunchedEffect
        val deepLink = parseHostDeepLink(payload)
        val deepLinkServer = deepLink?.let { link -> repository.servers.firstOrNull { it.id == link.serverId } }
        if (deepLink != null && deepLinkServer != null) {
            selectedServerId = null
            showInterfacesFor = null
            creatingServer = false
            editingServerId = null
            hostEditorError = null
            selectedSftpWorkspaceKey = null
            sftpBrowserServerId = null
            when (deepLink.target) {
                HostDeepLinkTarget.Terminal -> {
                    val workspace = openConnectionWorkspace(deepLinkServer)
                    selectedTab = AppTab.Connections
                    terminalVisible = true
                    autoConnectRequestId = "${workspace.workspaceId}|${System.currentTimeMillis()}"
                }
                HostDeepLinkTarget.Sftp -> openSftpWorkspace(deepLinkServer)
            }
        } else if (deepLink == null) {
            HostShareLinkCodec.decode(payload)?.let { imported ->
                repository.upsertServer(imported)
                selectedTab = AppTab.Servers
                selectedServerId = imported.id
                repository.appendEvent(imported.id, ConnectionEventLevel.Success, "Host imported from link.")
            }
        }
        onInboundHostShareLinkConsumed(payload)
    }

    fun selectSftpWorkspace(workspaceKey: String) {
        val serverId = sftpWorkspaces[workspaceKey] ?: return
        sftpReturnTarget = if (terminalVisible) {
            ReturnTarget.Terminal(selectedConnectionServerId, transientReturnTarget)
        } else if (sftpBrowserServerId == null) {
            currentReturnTarget()
        } else {
            sftpReturnTarget
        }
        selectedSftpWorkspaceKey = workspaceKey
        sftpBrowserServerId = serverId
        terminalVisible = false
        selectedTab = AppTab.Connections
    }

    fun closeSftpWorkspace(workspaceKey: String) {
        val wasSelected = selectedSftpWorkspaceKey == workspaceKey
        sftpWorkspaces.remove(workspaceKey)
        sftpRuntimes.remove(workspaceKey)?.client?.let { client ->
            scope.launch { runCatching { client.close() } }
        }
        if (!wasSelected) return
        val nextKey = nextSftpWorkspaceAfterClose(workspaceKey, selectedSftpWorkspaceKey, sftpWorkspaces.keys.toList())
        if (nextKey == null) {
            selectedSftpWorkspaceKey = null
            sftpBrowserServerId = null
        } else {
            selectSftpWorkspace(nextKey)
        }
    }

    fun closeConnectionWorkspace(workspaceKey: String) {
        val workspace = terminalWorkspaces[workspaceKey]
        workspace?.session?.let { session ->
            val server = repository.servers.firstOrNull { it.id == workspace.serverId }
            repository.upsertTerminalSession(
                TerminalSessionRecord(
                    id = session.id,
                    serverId = workspace.serverId,
                    title = server?.name ?: workspace.serverId,
                    status = ServerStatus.Offline,
                    startedAtEpochMillis = workspace.startedAtEpochMillis,
                    lastActiveEpochMillis = System.currentTimeMillis(),
                    transcriptPreview = session.transcriptPreview,
                    tmuxSessionName = workspace.tmuxRestorableSessionName,
                    tmuxWindowIndex = workspace.tmuxRestorableWindowIndex
                )
            )
            session.setTerminalCloseHandler {}
            scope.launch { session.close() }
            TerminalSessionRegistry.detach(workspaceKey, session)
        }
        workspace?.disposeEngineIfInitialized()
        terminalWorkspaces.remove(workspaceKey)
        selectedConnectionServerId = nextTerminalWorkspaceAfterClose(
            closedKey = workspaceKey,
            selectedKey = selectedConnectionServerId,
            remainingKeys = terminalWorkspaces.filterValues { it.connected }.keys.toList()
        )
        if (selectedConnectionServerId == null) {
            autoConnectRequestId = null
            terminalVisible = false
            restoreReturnTarget(terminalReturnTargetAfterLastClose(transientReturnTarget))
            transientReturnTarget = null
        } else {
            terminalVisible = true
        }
    }

    fun closeTerminalSurface() {
        terminalVisible = false
        restoreReturnTarget(transientReturnTarget)
        transientReturnTarget = null
    }

    fun closeSftpSurface() {
        val target = sftpReturnTarget ?: transientReturnTarget
        sftpBrowserServerId = null
        selectedSftpWorkspaceKey = null
        restoreReturnTarget(target)
        sftpReturnTarget = null
        if (target !is ReturnTarget.Terminal) transientReturnTarget = null
    }

    val surface: AppSurface = when {
        creatingServer || editingServerId != null -> AppSurface.HostEditor(editingServerId)
        vncViewerFor != null -> AppSurface.VncViewer(vncViewerFor!!)
        rdpViewerFor != null -> AppSurface.RdpViewer(rdpViewerFor!!)
        sftpBrowserServerId != null -> AppSurface.SftpBrowser(sftpBrowserServerId, selectedSftpWorkspaceKey)
        portForwardFor != null -> AppSurface.PortForward(portForwardFor!!)
        showInterfacesFor != null -> AppSurface.Interfaces(showInterfacesFor!!)
        showActivityFor != null -> AppSurface.ServerActivity(showActivityFor!!)
        selectedServerId != null -> AppSurface.ServerDetail(selectedServerId!!)
        selectedTab == AppTab.Servers && showUptime -> AppSurface.Uptime
        terminalVisible -> AppSurface.Terminal
        else -> when (selectedTab) {
            AppTab.Servers -> AppSurface.Home
            AppTab.Connections -> AppSurface.Connections
            AppTab.Files -> AppSurface.Files
            AppTab.Vault -> AppSurface.Vault
            AppTab.Settings -> AppSurface.Settings
        }
    }

    BackHandler(enabled = surface !is AppSurface.Home || selectedTab != AppTab.Servers) {
        when {
            settingsSelectionPage != null -> settingsSelectionPage = null
            creatingServer || editingServerId != null -> {
                creatingServer = false
                editingServerId = null
                hostEditorError = null
            }
            vncViewerFor != null -> vncViewerFor = null
            rdpViewerFor != null -> rdpViewerFor = null
            sftpBrowserServerId != null -> closeSftpSurface()
            portForwardFor != null -> portForwardFor = null
            showInterfacesFor != null -> showInterfacesFor = null
            showActivityFor != null -> showActivityFor = null
            selectedServerId != null -> selectedServerId = null
            showUptime -> showUptime = false
            surface is AppSurface.Terminal -> closeTerminalSurface()
            selectedTab == AppTab.Connections -> {
                restoreReturnTarget(connectionBackTarget(transientReturnTarget))
                transientReturnTarget = null
            }
            selectedTab == AppTab.Vault && transientReturnTarget != null -> {
                restoreReturnTarget(transientReturnTarget)
                transientReturnTarget = null
                vaultInitialSection = null
                vaultForwardServerId = null
            }
            selectedTab != AppTab.Servers -> selectedTab = AppTab.Servers
        }
    }

    val headingFonts = remember(appSettings) {
        HeadingFontFamilies(
            fallback = FontFamily.Default,
            home = headingFontFamilyFromPath(appSettings.homeHeadingFontPath),
            connections = headingFontFamilyFromPath(appSettings.connectionsHeadingFontPath),
            files = headingFontFamilyFromPath(appSettings.filesHeadingFontPath),
            vault = headingFontFamilyFromPath(appSettings.vaultHeadingFontPath),
            settings = headingFontFamilyFromPath(appSettings.settingsHeadingFontPath)
        )
    }
    ChronoSSHTheme(palette = palette, headingFonts = headingFonts, accentOverrideHex = appSettings.appAccentColorHex) {
        AppBackground {
            Box(Modifier.fillMaxSize()) {
                if (appLocked && appLockPinUsable(appSettings)) {
                    LaunchedEffect(appSettings.appLockPinHash, appSettings.appLockPinSalt) {
                        armAppLockRenderMarker()
                        appLockRenderCrashContext(appSettings)?.let(repository::recordCrashContext)
                    }
                    AppLockScreen(
                        settings = appSettings,
                        onRecoveredSettings = { recovered -> persistSettings(recovered) },
                        onDisposed = ::clearAppLockRenderMarker,
                        onUnlocked = {
                            appLocked = false
                            clearAppLockRenderMarker()
                        }
                    )
                } else {
                    val showBottomTabs = surface is AppSurface.Home ||
                        surface is AppSurface.Connections ||
                        surface is AppSurface.Files ||
                        surface is AppSurface.Vault ||
                        surface is AppSurface.Settings
                    val sftpWorkspaceServers = sftpWorkspaces.entries.mapNotNull { entry ->
                        repository.servers.firstOrNull { it.id == entry.value }?.let { server -> entry.key to server }
                    }
                    Column(Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                    when (surface) {
                        AppSurface.Home -> HomeScreen(
                            servers = repository.servers,
                            snapshots = repository.snapshots,
                            knownHosts = repository.knownHosts,
                            networkMode = appSettings.serverCardNetworkMode,
                            diskMode = appSettings.serverCardDiskMode,
                            metricColorPreset = appSettings.serverMetricColorPreset,
                            metricColorOverrides = metricColorOverridesFrom(appSettings),
                            serverCardLatencyVisible = appSettings.serverCardLatencyVisible,
                            onAddServer = { creatingServer = true },
                            onTrustHost = ::reviewHostKey,
                            onUptimeClick = { showUptime = true },
                            onServerClick = { selectedServerId = it.id },
                            onTerminalClick = { launchConnection(it) },
                            onProbeClick = { probeServer(it.id) }
                        )

                        AppSurface.Uptime -> UptimeScreen(
                            servers = repository.servers,
                            snapshots = repository.snapshots,
                            metricHistory = repository.metricHistory,
                            backgroundMonitoringEnabled = appSettings.uptimeBackgroundMonitoringEnabled,
                            onBackgroundMonitoringChange = { enabled ->
                                persistSettings(appSettings.copy(uptimeBackgroundMonitoringEnabled = enabled))
                            },
                            onHostMonitoringChange = { server, enabled ->
                                repository.upsertServer(
                                    server.copy(monitoringConfig = server.monitoringConfig.copy(enabled = enabled))
                                )
                            },
                            onBack = { showUptime = false },
                            onServerClick = {
                                showUptime = false
                                selectedServerId = it.id
                            },
                            onRefresh = { probeServer(it.id) }
                        )

                        AppSurface.Terminal -> TerminalScreen(
                            servers = repository.servers,
                            terminalProfile = repository.terminalProfile,
                            selectedServerId = selectedConnectionServerId,
                            workspaces = terminalWorkspaces,
                            autoConnectRequestId = autoConnectRequestId,
                            credentials = repository.credentials,
                            knownHosts = repository.knownHosts,
                            snippets = repository.snippets,
                            transport = repository.sshTransport,
                            sftpWorkspaces = sftpWorkspaceServers,
                            selectedSftpWorkspaceKey = selectedSftpWorkspaceKey,
                            onTerminalSessionChanged = { repository.upsertTerminalSession(it) },
                            onHostKeyRemembered = { server, prompt ->
                                repository.rememberHostKey(server, prompt.algorithm, prompt.fingerprint)
                            },
                            onTrustHost = { server ->
                                repository.reviewOrTrustKnownHost(server)
                            },
                            onConnectionEvent = { server, level, message ->
                                repository.appendEvent(server.id, level, message)
                            },
                            onShellConnected = { server, session ->
                                repository.markCredentialUsedFor(server)
                                scope.launch { repository.collectMetricsFromSession(server, session) }
                            },
                            onOpenServer = { openConnectionWorkspace(it) },
                            onSelectServer = {
                                if (!terminalVisible) {
                                    transientReturnTarget = terminalReturnTargetOnEntry(currentReturnTarget(), transientReturnTarget, terminalVisible)
                                }
                                selectedConnectionServerId = it
                                selectedSftpWorkspaceKey = null
                                sftpBrowserServerId = null
                                terminalVisible = true
                                selectedTab = AppTab.Connections
                            },
                            onDuplicateServer = { duplicateConnection(it) },
                            onCloseWorkspace = { closeConnectionWorkspace(it) },
                            onBack = {
                                closeTerminalSurface()
                            },
                            onEditHost = {
                                editingServerId = it.id
                                hostEditorError = null
                            },
                            onSelectSftp = ::selectSftpWorkspace,
                            onOpenSftp = ::openSftpWorkspace
                        )

                        AppSurface.Connections -> ConnectionsScreen(
                            servers = repository.servers,
                            workspaces = terminalWorkspaces,
                            sftpWorkspaces = sftpWorkspaceServers,
                            snapshots = repository.snapshots,
                            forwards = repository.forwards,
                            forwardStatuses = repository.forwardStatuses,
                            onOpenWorkspace = { workspaceKey ->
                                transientReturnTarget = terminalReturnTargetOnEntry(currentReturnTarget(), transientReturnTarget, terminalVisible)
                                selectedConnectionServerId = workspaceKey
                                selectedSftpWorkspaceKey = null
                                sftpBrowserServerId = null
                                terminalVisible = true
                            },
                            onOpenTerminal = { server ->
                                transientReturnTarget = terminalReturnTargetOnEntry(currentReturnTarget(), transientReturnTarget, terminalVisible)
                                openConnectionWorkspace(server)
                                selectedTab = AppTab.Connections
                                terminalVisible = true
                            },
                            onOpenSftpWorkspace = ::selectSftpWorkspace,
                            onOpenSftp = ::openSftpWorkspace,
                            onToggleForward = { forward ->
                                scope.launch { repository.toggleForward(forward.id) }
                            },
                            onDuplicate = { duplicateConnection(it) },
                            onClose = { workspaceKey ->
                                when (terminalCloseIntent(fromTerminalChrome = false)) {
                                    TerminalCloseIntent.HideSurface -> closeTerminalSurface()
                                    TerminalCloseIntent.DisconnectWorkspace -> closeConnectionWorkspace(workspaceKey)
                                }
                            }
                        )

                        AppSurface.Files -> HostsScreen(
                            title = "Files",
                            subtitle = "SFTP and transfers",
                            servers = repository.servers,
                            credentials = repository.credentials,
                            knownHosts = repository.knownHosts,
                            snippets = repository.snippets,
                            forwards = repository.forwards,
                            forwardStatuses = repository.forwardStatuses,
                            terminalSessions = repository.terminalSessions,
                            sftpBookmarks = repository.sftpBookmarks,
                            transfers = repository.transfers,
                            mode = "files",
                            initialSection = null,
                            initialServerId = filesServerId,
                            preselectedCredentialId = null,
                            onAddHost = { creatingServer = true },
                            onServerClick = { editingServerId = it.id },
                            onTerminalClick = { launchConnection(it) },
                            onTrustHost = ::reviewHostKey,
                            onMoveServer = repository::moveServer,
                            onToggleForward = { forward ->
                                scope.launch { repository.toggleForward(forward.id) }
                            },
                            onUpsertForward = { forward ->
                                scope.launch {
                                    repository.upsertForward(forward).onFailure { error ->
                                        repository.appendEvent(forward.serverId, ConnectionEventLevel.Error, "Forward save failed: ${error.message ?: error::class.java.simpleName}")
                                    }
                                }
                            },
                            onDeleteForward = { forward ->
                                scope.launch { repository.deleteForward(forward.id) }
                            },
                            onDeleteCredential = { credential ->
                                scope.launch { repository.deleteCredential(credential.id) }
                            },
                            onUpsertCredential = { credential -> repository.upsertCredential(credential) },
                            onRenameCredential = { credential, label -> repository.renameCredential(credential.id, label) },
                            onUnlinkCredential = { credential -> repository.unlinkCredentialFromHosts(credential.id) },
                            onAddPrivateKeyCredential = { label, secret, passphrase, savePassphrase ->
                                scope.launch {
                                    runCatching {
                                        repository.upsertCredentialDraft(
                                            existingId = null,
                                            label = label,
                                            type = CredentialType.PrivateKey,
                                            secret = secret,
                                            passphrase = passphrase,
                                            savePassphrase = savePassphrase
                                        )
                                    }.onFailure { error ->
                                        repository.appendEvent(
                                            "vault",
                                            ConnectionEventLevel.Error,
                                            "Key save failed: ${error.message ?: error::class.java.simpleName}"
                                        )
                                    }
                                }
                            },
                            onReplaceCredentialSecret = { credential, secret, passphrase, savePassphrase ->
                                scope.launch {
                                    runCatching {
                                        repository.upsertCredentialDraft(
                                            existingId = credential.id,
                                            label = credential.label,
                                            type = credential.type,
                                            secret = secret,
                                            passphrase = passphrase,
                                            savePassphrase = savePassphrase
                                        )
                                    }.onFailure { error ->
                                        repository.appendEvent(
                                            credential.id,
                                            ConnectionEventLevel.Error,
                                            "Credential replacement failed: ${error.message ?: error::class.java.simpleName}"
                                        )
                                    }
                                }
                            },
                            onDeleteKnownHost = { knownHost -> repository.deleteKnownHost(knownHost.id) },
                            onUpsertSnippet = { snippet -> repository.upsertSnippet(snippet) },
                            onDeleteSnippet = { snippet -> repository.deleteSnippet(snippet.id) },
                            onLoadCredentialPayload = { credential -> repository.loadCredentialPayload(credential) },
                            onTransferChanged = { repository.upsertTransfer(it) },
                            onCancelTransfer = { repository.cancelTransfer(it) },
                            onClearFinishedTransfers = { repository.clearFinishedTransfers() },
                            onSftpBookmarkChanged = { repository.upsertSftpBookmark(it) },
                            onSftpBookmarkDeleted = { repository.deleteSftpBookmark(it.id) },
                            onSftpConnected = { repository.markCredentialUsedFor(it) },
                            sshTransport = repository.sshTransport,
                            backupPreview = repository.exportBackupPreview()
                        )

                        is AppSurface.SftpBrowser -> SftpBrowserScreen(
                            servers = repository.servers,
                            credentials = repository.credentials,
                            knownHosts = repository.knownHosts,
                            bookmarks = repository.sftpBookmarks,
                            transfers = repository.transfers,
                            initialServerId = surface.serverId,
                            sftpWorkspaces = sftpWorkspaceServers,
                            selectedSftpWorkspaceKey = surface.workspaceKey,
                            sftpRuntimes = sftpRuntimes,
                            onSelectSftpWorkspace = ::selectSftpWorkspace,
                            onCloseSftpWorkspace = ::closeSftpWorkspace,
                            onSftpWorkspaceFailed = ::closeSftpWorkspace,
                            onBack = {
                                closeSftpSurface()
                            },
                            onServerClick = { editingServerId = it.id },
                            onTerminalClick = { launchConnection(it) },
                            onTrustHost = ::reviewHostKey,
                            onTransferChanged = { repository.upsertTransfer(it) },
                            onSftpBookmarkChanged = { repository.upsertSftpBookmark(it) },
                            onSftpBookmarkDeleted = { repository.deleteSftpBookmark(it.id) },
                            onSftpConnected = { repository.markCredentialUsedFor(it) },
                            sftpDefaultSortMode = appSettings.sftpDefaultSortMode(),
                            sftpDefaultSortDescending = appSettings.sftpDefaultSortDescending,
                            sftpShowHiddenByDefault = appSettings.sftpShowHiddenByDefault,
                            sshTransport = repository.sshTransport
                        )

                        AppSurface.Vault -> HostsScreen(
                            title = "Vault",
                            subtitle = "Hosts, keys, forwards",
                            servers = repository.servers,
                            credentials = repository.credentials,
                            knownHosts = repository.knownHosts,
                            snippets = repository.snippets,
                            forwards = repository.forwards,
                            forwardStatuses = repository.forwardStatuses,
                            terminalSessions = repository.terminalSessions,
                            sftpBookmarks = repository.sftpBookmarks,
                            transfers = repository.transfers,
                            mode = "vault",
                            initialSection = vaultInitialSectionForRoute(vaultInitialSection, vaultCredentialToOpen),
                            initialSectionRequestKey = vaultInitialSectionRequestKey,
                            initialServerId = null,
                            initialForwardServerId = vaultForwardServerId,
                            initialForwardDraftRequestKey = vaultForwardDraftRequestKey,
                            preselectedCredentialId = vaultCredentialToOpen,
                            onAddHost = { creatingServer = true },
                            onServerClick = { editingServerId = it.id },
                            onTerminalClick = { launchConnection(it) },
                            onTrustHost = ::reviewHostKey,
                            onMoveServer = repository::moveServer,
                            onToggleForward = { forward ->
                                scope.launch { repository.toggleForward(forward.id) }
                            },
                            onUpsertForward = { forward ->
                                scope.launch {
                                    repository.upsertForward(forward).onFailure { error ->
                                        repository.appendEvent(forward.serverId, ConnectionEventLevel.Error, "Forward save failed: ${error.message ?: error::class.java.simpleName}")
                                    }
                                }
                            },
                            onDeleteForward = { forward ->
                                scope.launch { repository.deleteForward(forward.id) }
                            },
                            onDeleteCredential = { credential ->
                                scope.launch { repository.deleteCredential(credential.id) }
                            },
                            onUpsertCredential = { credential -> repository.upsertCredential(credential) },
                            onRenameCredential = { credential, label -> repository.renameCredential(credential.id, label) },
                            onUnlinkCredential = { credential -> repository.unlinkCredentialFromHosts(credential.id) },
                            onAddPrivateKeyCredential = { label, secret, passphrase, savePassphrase ->
                                scope.launch {
                                    runCatching {
                                        repository.upsertCredentialDraft(
                                            existingId = null,
                                            label = label,
                                            type = CredentialType.PrivateKey,
                                            secret = secret,
                                            passphrase = passphrase,
                                            savePassphrase = savePassphrase
                                        )
                                    }.onFailure { error ->
                                        repository.appendEvent(
                                            "vault",
                                            ConnectionEventLevel.Error,
                                            "Key save failed: ${error.message ?: error::class.java.simpleName}"
                                        )
                                    }
                                }
                            },
                            onReplaceCredentialSecret = { credential, secret, passphrase, savePassphrase ->
                                scope.launch {
                                    runCatching {
                                        repository.upsertCredentialDraft(
                                            existingId = credential.id,
                                            label = credential.label,
                                            type = credential.type,
                                            secret = secret,
                                            passphrase = passphrase,
                                            savePassphrase = savePassphrase
                                        )
                                    }.onFailure { error ->
                                        repository.appendEvent(
                                            credential.id,
                                            ConnectionEventLevel.Error,
                                            "Credential replacement failed: ${error.message ?: error::class.java.simpleName}"
                                        )
                                    }
                                }
                            },
                            onDeleteKnownHost = { knownHost -> repository.deleteKnownHost(knownHost.id) },
                            onUpsertSnippet = { snippet -> repository.upsertSnippet(snippet) },
                            onDeleteSnippet = { snippet -> repository.deleteSnippet(snippet.id) },
                            onLoadCredentialPayload = { credential -> repository.loadCredentialPayload(credential) },
                            onTransferChanged = { repository.upsertTransfer(it) },
                            onCancelTransfer = { repository.cancelTransfer(it) },
                            onClearFinishedTransfers = { repository.clearFinishedTransfers() },
                            onSftpBookmarkChanged = { repository.upsertSftpBookmark(it) },
                            sshTransport = repository.sshTransport,
                            backupPreview = repository.exportBackupPreview()
                        )

                        AppSurface.Settings -> SettingsScreen(
                            themeMode = themeMode,
                            themeFamilyId = themeFamilyId,
                            settings = appSettings,
                            selectionPage = settingsSelectionPage,
                            diagnostics = repository.connectionEvents.values.flatten().sortedByDescending { it.atEpochMillis }.take(80),
                            crashLogs = repository.crashLogs,
                            backupContent = repository.exportMetadataBackup(),
                            biometricAvailable = biometricUnlockAvailable(context),
                            onSelectionPageChange = { settingsSelectionPage = it },
                            onThemeModeChange = {
                                themeMode = it
                                persistTheme(nextMode = it)
                            },
                            onThemeFamilyChange = {
                                themeFamilyId = it
                                persistTheme(nextFamily = it)
                            },
                            onSettingsChange = ::persistSettings,
                            onLockNow = { appLocked = appLockStateAfterLockNow(appLockPinUsable(appSettings)) },
                            backgroundUsageAllowed = backgroundUsageAllowed,
                            onRequestBackgroundUsage = ::requestBackgroundUsage,
                            onInspectBackupImport = { repository.inspectBackupImport(it) },
                            onImportBackupMetadata = { repository.importMetadataBackup(it) },
                            onImportHostShareLink = { payload ->
                                val imported = HostShareLinkCodec.decode(payload)
                                if (imported == null) {
                                    "Host link invalid."
                                } else {
                                    repository.upsertServer(imported)
                                    "Host link imported: ${imported.name}"
                                }
                            },
                            onImportOpenSshConfig = { repository.importOpenSshConfig(it) },
                            onClearCrashLogs = { repository.clearCrashLogs() }
                        )

                        is AppSurface.PortForward -> {
                            val serverId = surface.serverId
                            val server = repository.servers.firstOrNull { it.id == serverId }
                            if (server == null) {
                                LaunchedEffect(serverId) {
                                    portForwardFor = null
                                }
                                HomeScreen(
                                    servers = repository.servers,
                                    snapshots = repository.snapshots,
                                    knownHosts = repository.knownHosts,
                                    networkMode = appSettings.serverCardNetworkMode,
                                    diskMode = appSettings.serverCardDiskMode,
                                    metricColorPreset = appSettings.serverMetricColorPreset,
                                    metricColorOverrides = metricColorOverridesFrom(appSettings),
                                    serverCardLatencyVisible = appSettings.serverCardLatencyVisible,
                                    onAddServer = { creatingServer = true },
                                    onTrustHost = ::reviewHostKey,
                                    onUptimeClick = { showUptime = true },
                                    onServerClick = { selectedServerId = it.id },
                                    onTerminalClick = { launchConnection(it) },
                                    onProbeClick = { probeServer(it.id) }
                                )
                            } else {
                                PortForwardPage(
                                    serverId = server.id,
                                    forwards = repository.forwards,
                                    forwardStatuses = repository.forwardStatuses,
                                    servers = repository.servers,
                                    onBack = { portForwardFor = null },
                                    onToggleForward = { forward ->
                                        scope.launch { repository.toggleForward(forward.id) }
                                    },
                                    onUpsertForward = { forward ->
                                        scope.launch {
                                            repository.upsertForward(forward).onFailure { error ->
                                                repository.appendEvent(forward.serverId, ConnectionEventLevel.Error, "Forward save failed: ${error.message ?: error::class.java.simpleName}")
                                            }
                                        }
                                    },
                                    onDeleteForward = { forward ->
                                        scope.launch { repository.deleteForward(forward.id) }
                                    }
                                )
                            }
                        }

                        is AppSurface.VncViewer -> {
                            val serverId = surface.serverId
                            val server = repository.servers.firstOrNull { it.id == serverId }
                            if (server == null) {
                                LaunchedEffect(serverId) { vncViewerFor = null }
                                HomeScreen(
                                    servers = repository.servers,
                                    snapshots = repository.snapshots,
                                    knownHosts = repository.knownHosts,
                                    networkMode = appSettings.serverCardNetworkMode,
                                    diskMode = appSettings.serverCardDiskMode,
                                    metricColorPreset = appSettings.serverMetricColorPreset,
                                    metricColorOverrides = metricColorOverridesFrom(appSettings),
                                    serverCardLatencyVisible = appSettings.serverCardLatencyVisible,
                                    onAddServer = { creatingServer = true },
                                    onTrustHost = ::reviewHostKey,
                                    onUptimeClick = { showUptime = true },
                                    onServerClick = { selectedServerId = it.id },
                                    onTerminalClick = { launchConnection(it) },
                                    onProbeClick = { probeServer(it.id) }
                                )
                            } else {
                                VncViewerScreen(
                                    server = server,
                                    credentials = repository.credentials,
                                    sshTransport = repository.sshTransport,
                                    onBack = { vncViewerFor = null },
                                    onConnectionEvent = { level, message -> repository.appendEvent(server.id, level, message) },
                                    onLoadCredentialPayload = { credential -> repository.loadCredentialPayload(credential) }
                                )
                            }
                        }

                        is AppSurface.RdpViewer -> {
                            val serverId = surface.serverId
                            val server = repository.servers.firstOrNull { it.id == serverId }
                            if (server == null) {
                                LaunchedEffect(serverId) { rdpViewerFor = null }
                                HomeScreen(
                                    servers = repository.servers,
                                    snapshots = repository.snapshots,
                                    knownHosts = repository.knownHosts,
                                    networkMode = appSettings.serverCardNetworkMode,
                                    diskMode = appSettings.serverCardDiskMode,
                                    metricColorPreset = appSettings.serverMetricColorPreset,
                                    metricColorOverrides = metricColorOverridesFrom(appSettings),
                                    serverCardLatencyVisible = appSettings.serverCardLatencyVisible,
                                    onAddServer = { creatingServer = true },
                                    onTrustHost = ::reviewHostKey,
                                    onUptimeClick = { showUptime = true },
                                    onServerClick = { selectedServerId = it.id },
                                    onTerminalClick = { launchConnection(it) },
                                    onProbeClick = { probeServer(it.id) }
                                )
                            } else {
                                RdpViewerScreen(
                                    server = server,
                                    credentials = repository.credentials,
                                    sshTransport = repository.sshTransport,
                                    onBack = { rdpViewerFor = null },
                                    onConnectionEvent = { level, message -> repository.appendEvent(server.id, level, message) },
                                    onLoadCredentialPayload = { credential -> repository.loadCredentialPayload(credential) }
                                )
                            }
                        }

                        is AppSurface.ServerDetail -> {
                            val serverId = surface.serverId
                            val server = repository.servers.firstOrNull { it.id == serverId }
                            if (server == null) {
                                LaunchedEffect(serverId) {
                                    selectedServerId = null
                                }
                                HomeScreen(
                                    servers = repository.servers,
                                    snapshots = repository.snapshots,
                                    knownHosts = repository.knownHosts,
                                    networkMode = appSettings.serverCardNetworkMode,
                                    diskMode = appSettings.serverCardDiskMode,
                                    metricColorPreset = appSettings.serverMetricColorPreset,
                                    metricColorOverrides = metricColorOverridesFrom(appSettings),
                                    serverCardLatencyVisible = appSettings.serverCardLatencyVisible,
                                    onAddServer = { creatingServer = true },
                                    onTrustHost = ::reviewHostKey,
                                    onUptimeClick = { showUptime = true },
                                    onServerClick = { selectedServerId = it.id },
                                    onTerminalClick = { launchConnection(it) },
                                    onProbeClick = { probeServer(it.id) }
                                )
                            } else {
                                LaunchedEffect(server.id) {
                                    val session = activeTerminalMetricsSession(server)
                                    if (session == null) {
                                        repository.collectMetrics(server, forceDetails = true)
                                    } else {
                                        repository.collectMetricsFromSession(server, session, forceDetails = true)
                                    }
                                }
                                ServerDetailScreen(
                                    server = server,
                                    snapshot = repository.snapshotFor(server.id),
                                    metricHistory = repository.metricHistoryFor(server.id),
                                    onBack = { selectedServerId = null },
                                    onInterfaces = { showInterfacesFor = server.id },
                                    onProbe = { probeServer(server.id) },
                                    onEdit = {
                                        selectedServerId = null
                                        editingServerId = server.id
                                    },
                                    onTerminal = {
                                        launchConnection(server)
                                    },
                                    onSftp = {
                                        openSftpWorkspace(server)
                                    },
                                    onPortForward = {
                                        portForwardFor = server.id
                                    },
                                    onActivity = {
                                        showActivityFor = server.id
                                    },
                                    onHostInfo = {
                                        hostInfoDialog = server.name to "Loading..."
                                        scope.launch {
                                            val text = repository.collectHostInfo(server).fold(
                                                onSuccess = { it },
                                                onFailure = { error -> error.message ?: error::class.java.simpleName }
                                            )
                                            hostInfoDialog = server.name to text
                                        }
                                    },
                                    onContainerAction = { container, action ->
                                        scope.launch {
                                            val target = container.id.ifBlank { container.name }
                                            repository.runContainerAction(
                                                server = server,
                                                engine = container.engine,
                                                target = target,
                                                action = action
                                            )
                                        }
                                    },
                                    onContainerOutput = { container, action, onResult ->
                                        scope.launch {
                                            val target = container.id.ifBlank { container.name }
                                            val text = repository.collectContainerInfo(server, container.engine, target, action).fold(
                                                onSuccess = { it },
                                                onFailure = { error -> error.message ?: error::class.java.simpleName }
                                            )
                                            onResult(text)
                                        }
                                    },
                                    onProcessAction = { process, action ->
                                        scope.launch {
                                            repository.runProcessAction(
                                                server = server,
                                                pid = process.pid,
                                                action = action
                                            )
                                        }
                                    },
                                    onServiceAction = { service, action ->
                                        scope.launch {
                                            if (action == "status" || action == "logs") {
                                                hostInfoDialog = "${service.unit} ${action}" to "Loading..."
                                                val text = repository.collectServiceInfo(server, service.unit, action).fold(
                                                    onSuccess = { it },
                                                    onFailure = { error -> error.message ?: error::class.java.simpleName }
                                                )
                                                hostInfoDialog = "${service.unit} ${action}" to text
                                            } else {
                                                repository.runServiceAction(
                                                    server = server,
                                                    unit = service.unit,
                                                    action = action
                                                )
                                            }
                                        }
                                    },
                                    onDetailToolOpen = {
                                        scope.launch {
                                            val session = activeTerminalMetricsSession(server)
                                            if (session == null) {
                                                repository.collectMetrics(server, forceDetails = true)
                                            } else {
                                                repository.collectMetricsFromSession(server, session, forceDetails = true)
                                            }
                                        }
                                    },
                                    onWake = if (server.wakeOnLan != null) {
                                        { wakeHost(server) }
                                    } else {
                                        null
                                    },
                                    metricColorPreset = appSettings.serverMetricColorPreset,
                                    metricColorOverrides = metricColorOverridesFrom(appSettings),
                                    cpuUsageDisplayMode = appSettings.cpuUsageDisplayMode,
                                    serverDetailCardOrder = appSettings.serverDetailCardOrder,
                                    serverDetailHiddenCards = appSettings.serverDetailHiddenCards
                                )
                            }
                        }

                        is AppSurface.ServerActivity -> {
                            val serverId = surface.serverId
                            val server = repository.servers.firstOrNull { it.id == serverId }
                            if (server == null) {
                                LaunchedEffect(serverId) {
                                    showActivityFor = null
                                }
                            } else {
                                ServerActivityScreen(
                                    server = server,
                                    events = repository.connectionEvents[server.id].orEmpty(),
                                    onBack = { showActivityFor = null }
                                )
                            }
                        }

                        is AppSurface.Interfaces -> {
                            val serverId = surface.serverId
                            val server = repository.servers.firstOrNull { it.id == serverId }
                            if (server == null) {
                                LaunchedEffect(serverId) {
                                    showInterfacesFor = null
                                }
                                HomeScreen(
                                    servers = repository.servers,
                                    snapshots = repository.snapshots,
                                    knownHosts = repository.knownHosts,
                                    networkMode = appSettings.serverCardNetworkMode,
                                    diskMode = appSettings.serverCardDiskMode,
                                    metricColorPreset = appSettings.serverMetricColorPreset,
                                    metricColorOverrides = metricColorOverridesFrom(appSettings),
                                    serverCardLatencyVisible = appSettings.serverCardLatencyVisible,
                                    onAddServer = { creatingServer = true },
                                    onTrustHost = ::reviewHostKey,
                                    onUptimeClick = { showUptime = true },
                                    onServerClick = { selectedServerId = it.id },
                                    onTerminalClick = { launchConnection(it) },
                                    onProbeClick = { probeServer(it.id) }
                                )
                            } else {
                                InterfacesScreen(
                                    server = server,
                                    snapshot = repository.snapshotFor(server.id),
                                    onClose = { showInterfacesFor = null }
                                )
                            }
                        }

                        is AppSurface.HostEditor -> {
                            val editingServer = surface.serverId?.let { id -> repository.servers.firstOrNull { it.id == id } }
                            HostEditorScreen(
                                server = editingServer,
                                servers = repository.servers,
                                credentials = repository.credentials,
                                externalError = hostEditorError,
                                onBack = {
                                    creatingServer = false
                                    editingServerId = null
                                    hostEditorError = null
                                },
                                onSave = { name, host, port, username, group, tags, osName, osVersion, accent, favorite, environment, credentialId, credentialLabel, credentialType, credentialSecret, credentialPassphrase, savePassphrase, startupCommand, startDirectory, monitoringConfig, reconnectPolicy, customLogoUri, proxyJumpHostId, notes, wakeOnLan, connectTimeoutSeconds, sshCompressionEnabled, protocol, moshConfig, eternalTerminalConfig, vncConfig, rdpConfig, fileConfig, prootConfig ->
                                    hostEditorError = null
                                    scope.launch {
                                        try {
                                            val savedCredential = if (hostSaveShouldReuseSelectedCredential(credentialId, credentialSecret, credentialPassphrase)) {
                                                null
                                            } else {
                                                repository.upsertCredentialDraft(
                                                    existingId = credentialId,
                                                    label = credentialLabel,
                                                    type = credentialType,
                                                    secret = credentialSecret,
                                                    passphrase = credentialPassphrase,
                                                    savePassphrase = savePassphrase
                                                )
                                            }
                                            val profile = repository.profileFromDraft(
                                                existing = editingServer,
                                                name = name,
                                                host = host,
                                                port = port,
                                                username = username,
                                                group = group,
                                                tags = tags,
                                                osName = osName,
                                                osVersion = osVersion,
                                                accent = accent,
                                                favorite = favorite,
                                                environment = environment,
                                                credentialId = savedCredential?.id ?: credentialId,
                                                startupCommand = startupCommand,
                                                startDirectory = startDirectory,
                                                monitoringConfig = monitoringConfig,
                                                reconnectPolicy = reconnectPolicy,
                                                customLogoUri = customLogoUri,
                                                proxyJumpHostId = proxyJumpHostId,
                                                notes = notes,
                                                wakeOnLan = wakeOnLan,
                                                connectTimeoutSeconds = connectTimeoutSeconds,
                                                sshCompressionEnabled = sshCompressionEnabled,
                                                protocol = protocol,
                                                moshConfig = moshConfig,
                                                eternalTerminalConfig = eternalTerminalConfig,
                                                vncConfig = vncConfig,
                                                rdpConfig = rdpConfig,
                                                fileConfig = fileConfig,
                                                prootConfig = prootConfig
                                            )
                                            repository.upsertServer(profile)
                                            creatingServer = false
                                            editingServerId = null
                                        } catch (error: Exception) {
                                            hostEditorError = "Save failed: ${error.message ?: error::class.java.simpleName}"
                                        }
                                    }
                                },
                                onDelete = editingServer?.let { server ->
                                    {
                                        scope.launch {
                                            repository.deleteServer(server.id)
                                            if (selectedServerId == server.id) selectedServerId = null
                                            if (showInterfacesFor == server.id) showInterfacesFor = null
                                            if (showActivityFor == server.id) showActivityFor = null
                                            terminalWorkspaces.keys
                                                .filter { key -> terminalWorkspaces[key]?.serverId == server.id }
                                                .toList()
                                                .forEach { closeConnectionWorkspace(it) }
                                            sftpWorkspaceKeysForServer(sftpWorkspaces, server.id).forEach { closeSftpWorkspace(it) }
                                            editingServerId = null
                                            creatingServer = false
                                            hostEditorError = null
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                    AnimatedVisibility(
                        visible = backgroundUsagePromptVisible(serviceConnectionCount, backgroundUsageAllowed),
                        enter = slideInVertically(tween(220, easing = LinearOutSlowInEasing)) { it / 2 } + fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                        exit = slideOutVertically(tween(180, easing = LinearOutSlowInEasing)) { it / 2 } + fadeOut(tween(180, easing = LinearOutSlowInEasing))
                    ) {
                        BackgroundUsagePrompt(::requestBackgroundUsage)
                    }

                    AnimatedVisibility(
                        visible = notificationPermissionPromptVisible(serviceConnectionCount, notificationPermissionAllowed),
                        enter = slideInVertically(tween(220, easing = LinearOutSlowInEasing)) { it / 2 } + fadeIn(tween(220, easing = LinearOutSlowInEasing)),
                        exit = slideOutVertically(tween(180, easing = LinearOutSlowInEasing)) { it / 2 } + fadeOut(tween(180, easing = LinearOutSlowInEasing))
                    ) {
                        NotificationPermissionPrompt(::requestNotificationPermission)
                    }

                    if (showBottomTabs) {
                        BottomTabs(
                            selectedTab = selectedTab,
                            onSelected = {
                                selectedTab = it
                                selectedServerId = null
                                showInterfacesFor = null
                                creatingServer = false
                                editingServerId = null
                                hostEditorError = null
                                vaultCredentialToOpen = null
                                vaultInitialSection = null
                                vaultForwardServerId = null
                                settingsSelectionPage = null
                                if (it != AppTab.Files) filesServerId = null
                                sftpBrowserServerId = null
                                selectedSftpWorkspaceKey = null
                                terminalVisible = false
                                transientReturnTarget = null
                            }
                        )
                    }
                }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        repository.startAutoStartForwards()
    }

    LaunchedEffect(Unit) {
        // Fold byte-for-byte identical password identities into a single entry on launch.
        runCatching { repository.mergeDuplicatePasswordCredentials() }
    }

    hostInfoDialog?.let { (title, body) ->
        AlertDialog(
            onDismissRequest = { hostInfoDialog = null },
            containerColor = DeckColors.Surface,
            title = { Text(title, color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
            text = { Text(body, color = DeckColors.PrimaryText, fontSize = 13.sp, lineHeight = 18.sp) },
            confirmButton = {
                TextButton(onClick = { hostInfoDialog = null }) {
                    Text("Close", color = DeckColors.Cyan)
                }
            }
        )
    }

    LaunchedEffect(repository.servers.size, repository.credentials.size, repository.knownHosts.size, appSettings.autoRefreshSeconds, terminalMetricsSessionLoopKey(terminalWorkspaces.values)) {
        while (true) {
            repository.servers.forEach { server ->
                launch {
                    val credentialReady = repository.credentialFor(server)?.secretBacked == true
                    val hostTrusted = repository.knownHostFor(server)?.trusted == true
                    val alreadyCollecting = repository.isCollectingMetrics(server.id)
                    val refreshDue = repository.shouldRefreshMetrics(server)
                    if (shouldCollectMetricsBeforeProbe(
                            monitoringEnabled = server.monitoringConfig.enabled,
                            credentialReady = credentialReady,
                            hostTrusted = hostTrusted,
                            alreadyCollecting = alreadyCollecting,
                            refreshDue = refreshDue
                        )
                    ) {
                        val session = activeTerminalMetricsSession(server)
                        if (session == null) {
                            repository.collectMetrics(server)
                        } else {
                            repository.collectMetricsFromSession(server, session)
                        }
                        return@launch
                    }
                    metricsRefreshSkipDiagnostic(
                        monitoringEnabled = server.monitoringConfig.enabled,
                        credentialReady = credentialReady,
                        hostTrusted = hostTrusted,
                        alreadyCollecting = alreadyCollecting,
                        refreshDue = refreshDue,
                        serverName = server.name
                    )?.let { diagnostic ->
                        repository.noteMetricsRefreshSkipped(server, diagnostic.level, diagnostic.reason)
                    }
                    val now = System.currentTimeMillis()
                    val minProbeIntervalMillis = ServerStatusRefreshPolicy.effectiveIntervalSeconds(
                        appSeconds = appSettings.autoRefreshSeconds,
                        hostSeconds = server.monitoringConfig.pollIntervalSeconds
                    ) * 1000L
                    if (!shouldRunTcpProbeAfterMetricsGate(alreadyCollecting, refreshDue, loopProbeLastAt[server.id], now, minProbeIntervalMillis)) {
                        return@launch
                    }
                    if (!probeRegistry.tryBegin(server.id)) {
                        repository.noteMetricsRefreshSkipped(
                            server,
                            ConnectionEventLevel.Info,
                            "probe already running for ${server.name}."
                        )
                        return@launch
                    }
                    val probeResult = try {
                        probe.probe(server)
                    } finally {
                        probeRegistry.finish(server.id)
                    }
                    loopProbeLastAt[server.id] = System.currentTimeMillis()
                    repository.updateProbeResult(
                        serverId = server.id,
                        status = if (probeResult.reachable) ServerStatus.Online else ServerStatus.Offline,
                        latencyMs = probeResult.latencyMs,
                        message = probeResult.message
                    )
                    if (probeResult.reachable) {
                        val credentialReadyNow = repository.credentialFor(server)?.secretBacked == true
                        val hostTrustedNow = repository.knownHostFor(server)?.trusted == true
                        when {
                            !server.monitoringConfig.enabled -> repository.noteMetricsRefreshSkipped(
                                server,
                                ConnectionEventLevel.Info,
                                "monitoring is disabled for ${server.name}."
                            )
                            !credentialReadyNow -> repository.noteMetricsRefreshSkipped(
                                server,
                                ConnectionEventLevel.Warning,
                                "save a password or private key for ${server.name}."
                            )
                            !hostTrustedNow -> repository.noteMetricsRefreshSkipped(
                                server,
                                ConnectionEventLevel.Warning,
                                "approve the SSH host key for ${server.name}."
                            )
                            repository.isCollectingMetrics(server.id) -> repository.noteMetricsRefreshSkipped(
                                server,
                                ConnectionEventLevel.Info,
                                "refresh already running for ${server.name}."
                            )
                            repository.shouldRefreshMetrics(server) -> {
                                val session = activeTerminalMetricsSession(server)
                                if (session == null) {
                                    repository.collectMetrics(server)
                                } else {
                                    repository.collectMetricsFromSession(server, session)
                                }
                            }
                        }
                    }
                }
            }
            val refreshSeconds = ServerStatusRefreshPolicy.liveLoopSeconds(appSettings.autoRefreshSeconds)
            delay(refreshSeconds * 1000L)
        }
    }
}

@Composable
private fun AppLockScreen(
    settings: AppSettings,
    onRecoveredSettings: (AppSettings) -> Unit,
    onDisposed: () -> Unit,
    onUnlocked: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    fun unlock() {
        appLockRecoveredSettings(settings, pin)?.let { recovered ->
            error = null
            onRecoveredSettings(recovered)
            onUnlocked()
            return
        }
        if (PinLockPolicy.verify(pin, settings.appLockPinHash, settings.appLockPinSalt)) {
            error = null
            onUnlocked()
        } else {
            error = "Incorrect PIN."
            pin = ""
        }
    }
    BackHandler(enabled = true) {}
    DisposableEffect(Unit) {
        onDispose { onDisposed() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("chronoSSH", color = DeckColors.PrimaryText, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            Text("App locked", color = DeckColors.SecondaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = {
                    pin = it.filter(Char::isDigit).take(12)
                    error = null
                },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                label = { Text("PIN") },
                shape = RoundedCornerShape(18.dp),
                colors = lockTextFieldColors()
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = DeckColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            TextButton(enabled = pin.length >= 6, onClick = ::unlock) {
                Text("Unlock", color = if (pin.length >= 6) DeckColors.Cyan else DeckColors.SecondaryText, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun lockTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
private fun StartupRecoveryScreen(error: Throwable) {
    val palette = DeckThemeCatalog.paletteFor(DeckThemeMode.System, "chrono", isSystemInDarkTheme())
    ChronoSSHTheme(palette = palette) {
        AppBackground {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("chronoSSH", color = DeckColors.PrimaryText, fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(12.dp))
                    Text("Startup recovery", color = DeckColors.Red, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error.message ?: error::class.java.simpleName,
                        color = DeckColors.SecondaryText,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BackgroundUsagePrompt(onAllow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DeckColors.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Keep sessions connected", color = DeckColors.PrimaryText, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text("Allow unrestricted battery use for background connections.", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp)
        }
        TextButton(onClick = onAllow) {
            Text("Allow", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun NotificationPermissionPrompt(onAllow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(DeckColors.Surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Show connection status", color = DeckColors.PrimaryText, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            Text("Allow notifications so Android keeps active connections visible.", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp)
        }
        TextButton(onClick = onAllow) {
            Text("Allow", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun BottomTabs(
    modifier: Modifier = Modifier,
    selectedTab: AppTab,
    onSelected: (AppTab) -> Unit
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(DeckColors.NavSurface)
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        val tabWidth = maxWidth / AppTab.entries.size
        val selectedIndex = AppTab.entries.indexOf(selectedTab).coerceAtLeast(0)
        val indicatorWidth = 54.dp.coerceAtMost(tabWidth)
        val underlineWidth = 22.dp.coerceAtMost(tabWidth)
        val indicatorX by animateDpAsState(
            targetValue = tabWidth * selectedIndex + (tabWidth - indicatorWidth) / 2,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            label = "navIndicatorX"
        )
        val underlineX by animateDpAsState(
            targetValue = tabWidth * selectedIndex + (tabWidth - underlineWidth) / 2,
            animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
            label = "navUnderlineX"
        )
        Box(
            modifier = Modifier
                .offset(x = indicatorX, y = 4.dp)
                .size(width = indicatorWidth, height = 31.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(DeckColors.Surface.copy(alpha = 0.68f))
        )
        Box(
            modifier = Modifier
                .offset(x = underlineX, y = 55.dp)
                .size(width = underlineWidth, height = 2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(DeckColors.PrimaryText.copy(alpha = 0.72f))
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val interactionSource = remember(tab) { MutableInteractionSource() }
                val iconColor by animateColorAsState(
                    targetValue = if (selected) DeckColors.PrimaryText else DeckColors.SecondaryText,
                    animationSpec = tween(220, easing = LinearOutSlowInEasing),
                    label = "navIconColor"
                )
                val labelColor by animateColorAsState(
                    targetValue = if (selected) DeckColors.PrimaryText else DeckColors.SecondaryText,
                    animationSpec = tween(220, easing = LinearOutSlowInEasing),
                    label = "navLabelColor"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
                    label = "navIconScale"
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .semantics { contentDescription = tab.label }
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onSelected(tab) }
                        .padding(horizontal = 2.dp, vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(width = 54.dp, height = 31.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon(),
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier
                                .size(20.dp)
                                .scale(iconScale)
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(tab.label, color = labelColor, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Box(Modifier.size(width = 22.dp, height = 2.dp))
                }
            }
        }
    }
}

private fun AppTab.icon() = when (this) {
    AppTab.Servers -> Icons.Rounded.Storage
    AppTab.Connections -> Icons.Rounded.AccountTree
    AppTab.Files -> Icons.Rounded.FolderOpen
    AppTab.Vault -> Icons.Rounded.Lock
    AppTab.Settings -> Icons.Rounded.Settings
}

@Composable
private fun NavGlyph(tab: AppTab, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        when (tab) {
            AppTab.Servers -> {
                listOf(0.20f, 0.50f).forEach { top ->
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * top),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.18f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        style = stroke
                    )
                    drawLine(
                        color,
                        androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * (top + 0.09f)),
                        androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * (top + 0.09f)),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color, radius = size.minDimension * 0.045f, center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.82f), style = Fill)
            }
            AppTab.Connections -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.36f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.36f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.64f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.085f, center = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.36f), style = stroke)
                drawCircle(color, radius = size.minDimension * 0.085f, center = androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.64f), style = stroke)
            }
            AppTab.Files -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.34f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.42f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.34f), androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.35f, size.height * 0.24f), androidx.compose.ui.geometry.Offset(size.width * 0.56f, size.height * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.55f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.55f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppTab.Vault -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.40f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.34f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.40f), androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.40f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.045f, center = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.57f), style = Fill)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.61f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.67f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            AppTab.Settings -> {
                listOf(0.30f to 0.36f, 0.50f to 0.64f, 0.70f to 0.46f).forEach { (x, knobY) ->
                    drawLine(
                        color,
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.20f),
                        androidx.compose.ui.geometry.Offset(size.width * x, size.height * 0.80f),
                        strokeWidth = stroke.width,
                        cap = StrokeCap.Round
                    )
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * x - size.width * 0.085f, size.height * knobY - size.height * 0.045f),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.17f, size.height * 0.09f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                        style = Fill
                    )
                }
            }
        }
    }
}

internal fun nextSftpWorkspaceAfterClose(
    closedKey: String,
    selectedKey: String?,
    remainingKeys: List<String>
): String? {
    if (selectedKey != closedKey) return selectedKey?.takeIf { it in remainingKeys }
    return remainingKeys.firstOrNull()
}

internal fun nextTerminalWorkspaceAfterClose(
    closedKey: String,
    selectedKey: String?,
    remainingKeys: List<String>
): String? {
    if (selectedKey != closedKey) return selectedKey?.takeIf { it in remainingKeys } ?: remainingKeys.firstOrNull()
    return remainingKeys.firstOrNull()
}

internal fun sftpWorkspaceKeysForServer(workspaces: Map<String, String>, serverId: String): List<String> {
    return workspaces.filterValues { it == serverId }.keys.toList()
}

internal fun hostSaveShouldReuseSelectedCredential(
    credentialId: String?,
    credentialSecret: String,
    credentialPassphrase: String
): Boolean {
    return credentialId != null && credentialSecret.isBlank() && credentialPassphrase.isBlank()
}

private fun AppSettings.sftpDefaultSortMode(): SftpSortMode {
    return runCatching { SftpSortMode.valueOf(sftpDefaultSortModeName) }.getOrDefault(SftpSortMode.Name)
}
