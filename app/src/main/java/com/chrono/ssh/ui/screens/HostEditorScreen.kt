package com.chrono.ssh.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionCommand
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.EternalTerminalConfig
import com.chrono.ssh.core.model.FileProtocolConfig
import com.chrono.ssh.core.model.MoshConfig
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ProotProfileConfig
import com.chrono.ssh.core.model.RdpProfileConfig
import com.chrono.ssh.core.model.ReconnectPolicy
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.VncProfileConfig
import com.chrono.ssh.core.model.WakeOnLanConfig
import com.chrono.ssh.core.rclone.ParsedRemote
import com.chrono.ssh.core.rclone.RcloneClient
import com.chrono.ssh.core.rclone.RcloneConfigParseResult
import com.chrono.ssh.core.rclone.RcloneConfigParser
import com.chrono.ssh.core.service.CredentialDraftValidator
import com.chrono.ssh.core.service.CredentialUniquenessPolicy
import com.chrono.ssh.core.service.HostCommandSafety
import com.chrono.ssh.core.service.HostEndpointValidator
import com.chrono.ssh.core.service.HostEnvironmentPolicy
import com.chrono.ssh.core.service.HostUniquenessPolicy
import com.chrono.ssh.core.service.KeyMaterialInspector
import com.chrono.ssh.core.service.LocalProotRootfsStatus
import com.chrono.ssh.core.service.LocalProotRuntime
import com.chrono.ssh.core.service.ProxyJumpPolicy
import com.chrono.ssh.core.service.ServerStatusRefreshPolicy
import com.chrono.ssh.core.service.SshKeyGenerator
import com.chrono.ssh.core.service.WakeOnLanPolicy
import com.chrono.ssh.ui.design.CircleIconButton
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.SoftPill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun HostEditorScreen(
    server: ServerProfile?,
    servers: List<ServerProfile>,
    credentials: List<Credential>,
    externalError: String? = null,
    onBack: () -> Unit,
    onSave: (
        name: String,
        host: String,
        port: Int,
        username: String,
        group: String,
        tags: List<String>,
        osName: String,
        osVersion: String,
        accent: ServerAccent?,
        favorite: Boolean,
        environment: List<ConnectionCommand>,
        credentialId: String?,
        credentialLabel: String,
        credentialType: CredentialType,
        credentialSecret: String,
        credentialPassphrase: String,
        savePassphrase: Boolean,
        startupCommand: String,
        startDirectory: String,
        monitoringConfig: MonitoringConfig,
        reconnectPolicy: ReconnectPolicy,
        customLogoUri: String?,
        proxyJumpHostId: String?,
        notes: String,
        wakeOnLan: WakeOnLanConfig?,
        connectTimeoutSeconds: Int,
        sshCompressionEnabled: Boolean,
        protocol: ConnectionProtocol,
        moshConfig: MoshConfig,
        eternalTerminalConfig: EternalTerminalConfig,
        vncConfig: VncProfileConfig,
        rdpConfig: RdpProfileConfig,
        fileConfig: FileProtocolConfig,
        prootConfig: ProotProfileConfig
    ) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val connectableCredentials = remember(credentials) { credentials }
    val startingCredential = server?.credentialId?.let { id ->
        connectableCredentials.firstOrNull { it.id == id }
    }
    var name by remember(server?.id) { mutableStateOf(server?.name.orEmpty()) }
    var host by remember(server?.id) { mutableStateOf(server?.host.orEmpty()) }
    var port by remember(server?.id) { mutableStateOf((server?.port ?: 22).toString()) }
    var username by remember(server?.id) { mutableStateOf(server?.username ?: "root") }
    var group by remember(server?.id) { mutableStateOf(server?.group ?: "Cloud") }
    var tags by remember(server?.id) { mutableStateOf(server?.tags.orEmpty().filterNot { it == "All" }.joinToString(", ")) }
    var osName by remember(server?.id) { mutableStateOf(server?.osName ?: "Linux") }
    var osVersion by remember(server?.id) { mutableStateOf(server?.osVersion?.takeUnless { it == "Unknown" }.orEmpty()) }
    var selectedAccent by remember(server?.id) { mutableStateOf(server?.accent?.takeUnless { it.name.isBlank() }) }
    var favorite by remember(server?.id) { mutableStateOf(server?.favorite ?: false) }
    var environmentText by remember(server?.id) { mutableStateOf(HostEnvironmentPolicy.serialize(server?.environment.orEmpty())) }
    var proxyJumpHostId by remember(server?.id) { mutableStateOf(server?.proxyJumpHostId) }
    var startupCommand by remember(server?.id) { mutableStateOf(server?.startupCommand.orEmpty()) }
    var startDirectory by remember(server?.id) { mutableStateOf(server?.startDirectory.orEmpty()) }
    var notes by remember(server?.id) { mutableStateOf(server?.notes.orEmpty()) }
    var wolMacAddress by remember(server?.id) { mutableStateOf(server?.wakeOnLan?.macAddress.orEmpty()) }
    var wolBroadcastAddress by remember(server?.id) { mutableStateOf(server?.wakeOnLan?.broadcastAddress ?: "255.255.255.255") }
    var wolSecureOnPassword by remember(server?.id) { mutableStateOf(server?.wakeOnLan?.secureOnPassword.orEmpty()) }
    var monitoringEnabled by remember(server?.id) { mutableStateOf(server?.monitoringConfig?.enabled ?: true) }
    var pollIntervalSeconds by remember(server?.id) {
        mutableStateOf((server?.monitoringConfig?.pollIntervalSeconds ?: ServerStatusRefreshPolicy.ServerBoxDefaultSeconds).toString())
    }
    var useOptionalAgent by remember(server?.id) { mutableStateOf(server?.monitoringConfig?.useOptionalAgent ?: false) }
    var autoReconnect by remember(server?.id) { mutableStateOf(server?.reconnectPolicy?.autoReconnect ?: true) }
    var connectTimeoutSeconds by remember(server?.id) { mutableStateOf((server?.connectTimeoutSeconds ?: 10).toString()) }
    var sshCompressionEnabled by remember(server?.id) { mutableStateOf(server?.sshCompressionEnabled ?: false) }
    var protocol by remember(server?.id) { mutableStateOf(server?.protocol ?: ConnectionProtocol.Ssh) }
    var protocolOptionsOpen by remember(server?.id) { mutableStateOf(false) }
    var moshServerCommand by remember(server?.id) { mutableStateOf(server?.moshConfig?.serverCommand ?: "mosh-server") }
    var moshLocale by remember(server?.id) { mutableStateOf(server?.moshConfig?.locale ?: "en_US.UTF-8") }
    var moshColors by remember(server?.id) { mutableStateOf((server?.moshConfig?.colors ?: 256).toString()) }
    var moshPredictionMode by remember(server?.id) { mutableStateOf(server?.moshConfig?.predictionMode ?: "adaptive") }
    var etSshBootstrapPort by remember(server?.id) { mutableStateOf((server?.eternalTerminalConfig?.sshBootstrapPort ?: 22).toString()) }
    var etServerPort by remember(server?.id) { mutableStateOf((server?.eternalTerminalConfig?.etServerPort ?: 2022).toString()) }
    var etTerminalType by remember(server?.id) { mutableStateOf(server?.eternalTerminalConfig?.terminalType ?: "xterm-256color") }
    var etServerCommand by remember(server?.id) { mutableStateOf(server?.eternalTerminalConfig?.serverCommand ?: "etterminal") }
    var vncColorDepth by remember(server?.id) { mutableStateOf((server?.vncConfig?.colorDepthBits ?: 24).toString()) }
    var vncShared by remember(server?.id) { mutableStateOf(server?.vncConfig?.shared ?: true) }
    var vncViewOnly by remember(server?.id) { mutableStateOf(server?.vncConfig?.viewOnly ?: false) }
    var vncTargetFps by remember(server?.id) { mutableStateOf((server?.vncConfig?.targetFps ?: 30).toString()) }
    var vncTunnelOverSsh by remember(server?.id) { mutableStateOf(server?.vncConfig?.tunnelOverSsh ?: false) }
    var vncSshBootstrapPort by remember(server?.id) { mutableStateOf((server?.vncConfig?.sshBootstrapPort ?: 22).toString()) }
    var rdpWidth by remember(server?.id) { mutableStateOf((server?.rdpConfig?.width ?: 1600).toString()) }
    var rdpHeight by remember(server?.id) { mutableStateOf((server?.rdpConfig?.height ?: 900).toString()) }
    var rdpColorDepth by remember(server?.id) { mutableStateOf((server?.rdpConfig?.colorDepth ?: 16).toString()) }
    var rdpDomain by remember(server?.id) { mutableStateOf(server?.rdpConfig?.domain.orEmpty()) }
    var rdpUseNla by remember(server?.id) { mutableStateOf(server?.rdpConfig?.useNla ?: true) }
    var rdpTunnelOverSsh by remember(server?.id) { mutableStateOf(server?.rdpConfig?.tunnelOverSsh ?: false) }
    var rdpSshBootstrapPort by remember(server?.id) { mutableStateOf((server?.rdpConfig?.sshBootstrapPort ?: 22).toString()) }
    var fileRootPath by remember(server?.id) { mutableStateOf(server?.fileConfig?.rootPath.orEmpty()) }
    var fileTransferConcurrency by remember(server?.id) { mutableStateOf((server?.fileConfig?.transferConcurrency ?: 2).toString()) }
    var fileResumeTransfers by remember(server?.id) { mutableStateOf(server?.fileConfig?.resumeTransfers ?: true) }
    var fileVerifyChecksums by remember(server?.id) { mutableStateOf(server?.fileConfig?.verifyChecksums ?: false) }
    var prootDistroId by remember(server?.id) { mutableStateOf(server?.prootConfig?.distroId ?: "alpine-3.21") }
    var prootRootfsPath by remember(server?.id) { mutableStateOf(server?.prootConfig?.rootfsPath.orEmpty()) }
    var prootMountHome by remember(server?.id) { mutableStateOf(server?.prootConfig?.mountHome ?: true) }
    var keepAliveSeconds by remember(server?.id) { mutableStateOf((server?.reconnectPolicy?.keepAliveSeconds ?: 30).toString()) }
    var maxReconnectAttempts by remember(server?.id) { mutableStateOf((server?.reconnectPolicy?.maxAttempts ?: 3).toString()) }
    var customLogoUri by remember(server?.id) { mutableStateOf(server?.customLogoUri) }
    var selectedCredentialId by remember(server?.id) { mutableStateOf(startingCredential?.id) }
    var credentialLabel by remember(server?.id) { mutableStateOf(startingCredential?.label.orEmpty()) }
    var credentialType by remember(server?.id) { mutableStateOf(startingCredential?.type ?: CredentialType.Password) }
    var credentialSecret by remember(server?.id) { mutableStateOf("") }
    var credentialPassphrase by remember(server?.id) { mutableStateOf("") }
    var savePassphrase by remember(server?.id) { mutableStateOf(startingCredential?.passphraseRef != null) }
    var keyImportSummary by remember(server?.id) { mutableStateOf(startingCredential?.publicKeyPreview.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember(server?.id) { mutableStateOf(false) }
    var identityDialogOpen by remember(server?.id) { mutableStateOf(false) }
    var identityDialogMode by remember(server?.id) { mutableStateOf(IdentityDialogMode.Keys) }
    var rcloneRemoteDialogOpen by remember(server?.id) { mutableStateOf(false) }
    var rcloneUnlockDialogOpen by remember(server?.id) { mutableStateOf(false) }
    var pendingEncryptedRcloneConfig by remember(server?.id) { mutableStateOf<String?>(null) }
    var rcloneUnlockBusy by remember(server?.id) { mutableStateOf(false) }
    var importedRcloneRemotes by remember(server?.id) { mutableStateOf<List<ParsedRemote>>(emptyList()) }
    var prootInstallBusy by remember(server?.id) { mutableStateOf(false) }
    var prootCatalogOpen by remember(server?.id) { mutableStateOf(false) }
    var prootInstallStatus by remember(server?.id) {
        mutableStateOf(
            prootStatusText(LocalProotRuntime.rootfsStatus(context, server?.prootConfig ?: ProotProfileConfig()))
        )
    }
    var manualOsOpen by remember(server?.id) { mutableStateOf(false) }
    var osPickerOpen by remember(server?.id) { mutableStateOf(false) }
    var advancedSshOpen by remember(server?.id) { mutableStateOf(false) }
    var deleteSectionOpen by remember(server?.id) { mutableStateOf(false) }
    val pastedKeyInfo = if (credentialType == CredentialType.PrivateKey && credentialSecret.isNotBlank()) {
        KeyMaterialInspector.inspectPrivateKey(credentialSecret)
    } else {
        null
    }
    val jumpCandidates = remember(server?.id, servers) {
        servers.filter { candidate ->
            candidate.id != server?.id &&
                candidate.credentialId != null &&
                (server == null || !ProxyJumpPolicy.createsCycle(server, candidate, servers))
        }
    }

    fun importPrivateKeyText(source: String, imported: String) {
        val info = KeyMaterialInspector.inspectPrivateKey(imported)
        if (info.valid) {
            credentialType = CredentialType.PrivateKey
            credentialSecret = imported
            selectedCredentialId = null
            keyImportSummary = "${info.summary} (${info.fingerprint})"
            error = if (info.encrypted && credentialPassphrase.isBlank()) {
                "Encrypted key imported. Enter its passphrase before connecting."
            } else {
                null
            }
        } else {
            error = "$source key import failed: ${info.summary}"
        }
    }

    fun selectSavedCredential(credential: Credential?) {
        selectedCredentialId = credential?.id
        credentialLabel = credential?.label.orEmpty()
        credentialType = credential?.type ?: CredentialType.Password
        credentialSecret = ""
        credentialPassphrase = ""
        savePassphrase = credential?.passphraseRef != null
        keyImportSummary = credential?.publicKeyPreview.orEmpty()
        identityDialogOpen = false
        error = null
    }

    fun loadImportedRcloneConfig(text: String) {
        when (val parsed = RcloneConfigParser.parse(text)) {
            RcloneConfigParseResult.Encrypted -> {
                pendingEncryptedRcloneConfig = text
                rcloneUnlockDialogOpen = true
                error = "Encrypted rclone.conf selected. Enter its config password."
            }
            is RcloneConfigParseResult.Success -> {
                importedRcloneRemotes = parsed.remotes.filter { it.name.isNotBlank() && it.type.isNotBlank() }
                rcloneRemoteDialogOpen = importedRcloneRemotes.isNotEmpty()
                var saveError: String? = null
                if (importedRcloneRemotes.isNotEmpty()) {
                    runCatching {
                        val configDir = java.io.File(context.filesDir, "rclone").apply { mkdirs() }
                        java.io.File(configDir, "rclone.conf").writeText(text)
                    }.onFailure { failure ->
                        saveError = "rclone.conf parsed but could not be saved: ${failure.message ?: failure::class.java.simpleName}"
                    }
                }
                error = saveError ?: if (importedRcloneRemotes.isEmpty()) "No usable remotes found in rclone.conf." else null
            }
        }
    }

    val keyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                KeyMaterialInspector.readPrivateKeyText(input)
            }.orEmpty()
        }.onSuccess { imported ->
            importPrivateKeyText("File", imported)
        }.onFailure { failure ->
            error = "Key import failed: ${failure.message ?: failure::class.java.simpleName}"
        }
    }
    val logoImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        customLogoUri = uri.toString()
    }
    val rcloneConfigImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        }.onSuccess { text ->
            loadImportedRcloneConfig(text)
        }.onFailure { failure ->
            error = "rclone.conf import failed: ${failure.message ?: failure::class.java.simpleName}"
        }
    }
    val prootRootfsImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null || prootInstallBusy) return@rememberLauncherForActivityResult
        prootInstallBusy = true
        prootInstallStatus = "Installing rootfs..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val archiveName = runCatching {
                        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) cursor.getString(0) else null
                        }
                    }.getOrNull() ?: uri.lastPathSegment.orEmpty()
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        LocalProotRuntime.installRootfsArchive(context, archiveName, input, prootDistroId)
                    } ?: error("Could not open selected archive.")
                }
            }.onSuccess { path ->
                prootRootfsPath = ""
                prootInstallStatus = "PRoot rootfs installed at $path."
                error = null
            }.onFailure { failure ->
                prootInstallStatus = "PRoot rootfs import failed."
                error = "Rootfs import failed: ${failure.message ?: failure::class.java.simpleName}"
            }
            prootInstallBusy = false
        }
    }

    fun installCatalogRootfs(entryId: String) {
        val entry = LocalProotRuntime.RootfsCatalog.firstOrNull { it.id == entryId } ?: return
        if (prootInstallBusy) return
        prootCatalogOpen = false
        prootInstallBusy = true
        prootDistroId = entry.id
        prootRootfsPath = ""
        prootInstallStatus = "Downloading ${entry.label} ${entry.arch}..."
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    LocalProotRuntime.installCatalogRootfs(context, entry)
                }
            }.onSuccess { path ->
                prootInstallStatus = "PRoot rootfs installed at $path."
                error = null
            }.onFailure { failure ->
                prootInstallStatus = "PRoot rootfs download failed."
                error = "Rootfs download failed: ${failure.message ?: failure::class.java.simpleName}"
            }
            prootInstallBusy = false
        }
    }

    fun submit() {
        val parsedPort = port.toIntOrNull()
        val parsedPoll = pollIntervalSeconds.toIntOrNull()
        val parsedKeepAlive = keepAliveSeconds.toIntOrNull()
        val parsedConnectTimeout = connectTimeoutSeconds.toIntOrNull()
        val parsedReconnectAttempts = maxReconnectAttempts.toIntOrNull()
        val parsedMoshColors = moshColors.toIntOrNull()
        val parsedEtSshBootstrapPort = etSshBootstrapPort.toIntOrNull()
        val parsedEtServerPort = etServerPort.toIntOrNull()
        val parsedVncColorDepth = vncColorDepth.toIntOrNull()
        val parsedVncTargetFps = vncTargetFps.toIntOrNull()
        val parsedVncSshBootstrapPort = vncSshBootstrapPort.toIntOrNull()
        val parsedRdpWidth = rdpWidth.toIntOrNull()
        val parsedRdpHeight = rdpHeight.toIntOrNull()
        val parsedRdpColorDepth = rdpColorDepth.toIntOrNull()
        val parsedRdpSshBootstrapPort = rdpSshBootstrapPort.toIntOrNull()
        val parsedFileTransferConcurrency = fileTransferConcurrency.toIntOrNull()
        val environmentValidation = HostEnvironmentPolicy.parse(environmentText)
        val wakeOnLanError = WakeOnLanPolicy.errorFor(wolMacAddress, wolBroadcastAddress, wolSecureOnPassword)
        val wakeOnLanConfig = if (wolMacAddress.isBlank()) {
            null
        } else {
            WakeOnLanConfig(
                macAddress = WakeOnLanPolicy.normalizeMac(wolMacAddress).orEmpty(),
                broadcastAddress = WakeOnLanPolicy.normalizeBroadcast(wolBroadcastAddress).orEmpty(),
                secureOnPassword = WakeOnLanPolicy.normalizeSecureOn(wolSecureOnPassword)
            )
        }
        val selectedJumpHost = proxyJumpHostId?.let { id -> servers.firstOrNull { it.id == id } }
        val draftTarget = server ?: ServerProfile(
            id = "draft",
            name = name,
            host = host,
            port = parsedPort ?: 22,
            username = username,
            group = group,
            tags = emptyList(),
            osName = osName,
            osVersion = "",
            accent = ServerAccent("", 0),
            credentialId = selectedCredentialId,
            terminalProfileId = "",
            monitoringConfig = MonitoringConfig(true, ServerStatusRefreshPolicy.ServerBoxDefaultSeconds, false)
        )
        val duplicateHost = parsedPort?.let { cleanPort ->
            HostUniquenessPolicy.hasDuplicateEndpoint(
                servers,
                draftTarget.copy(host = host, port = cleanPort, username = username, protocol = protocol)
            )
        } == true
        val selectedCredential = selectedCredentialId?.let { id ->
            connectableCredentials.firstOrNull { it.id == id }
        }
        val loginTargetLabel = "${username.ifBlank { "user" }}@${host.ifBlank { "host" }}"
        val effectiveCredentialLabel = credentialLabel.ifBlank {
            when {
                credentialType == CredentialType.Password -> selectedCredential?.label ?: loginTargetLabel
                credentialType == CredentialType.PrivateKey -> selectedCredential?.label ?: "$loginTargetLabel key"
                else -> selectedCredential?.label ?: loginTargetLabel
            }
        }
        val duplicateCredentialLabel = CredentialUniquenessPolicy.hasDuplicateLabel(
            credentials,
            effectiveCredentialLabel,
            selectedCredentialId
        )
        when {
            HostEndpointValidator.errorFor(host) != null -> error = HostEndpointValidator.errorFor(host)
            duplicateHost -> error = HostUniquenessPolicy.DuplicateMessage
            username.isBlank() -> error = "Username is required."
            parsedPort == null || parsedPort !in 1..65535 -> error = "Port must be between 1 and 65535."
            parsedPoll == null || parsedPoll !in ServerStatusRefreshPolicy.MinEnabledSeconds..ServerStatusRefreshPolicy.MaxEnabledSeconds -> error = "Monitoring interval must be ${ServerStatusRefreshPolicy.MinEnabledSeconds}-${ServerStatusRefreshPolicy.MaxEnabledSeconds} seconds."
            parsedConnectTimeout == null || parsedConnectTimeout !in 3..60 -> error = "Connect timeout must be between 3 and 60 seconds."
            parsedKeepAlive == null || parsedKeepAlive !in 10..120 -> error = "Keepalive must be between 10 and 120 seconds."
            parsedReconnectAttempts == null || parsedReconnectAttempts !in 0..10 -> error = "Reconnect attempts must be between 0 and 10."
            !hostEditorMoshColorsValid(parsedMoshColors) -> error = "Mosh colors must be 8-256."
            !hostEditorPortValid(parsedEtSshBootstrapPort) || !hostEditorPortValid(parsedEtServerPort) -> error = "ET ports must be between 1 and 65535."
            parsedVncColorDepth == null || parsedVncTargetFps == null -> error = "VNC display values must be valid numbers."
            vncTunnelOverSsh && (parsedVncSshBootstrapPort == null || parsedVncSshBootstrapPort !in 1..65535) -> error = "VNC SSH port must be between 1 and 65535."
            parsedRdpWidth == null || parsedRdpHeight == null || parsedRdpColorDepth == null -> error = "RDP display values must be valid numbers."
            rdpTunnelOverSsh && (parsedRdpSshBootstrapPort == null || parsedRdpSshBootstrapPort !in 1..65535) -> error = "RDP SSH port must be between 1 and 65535."
            parsedFileTransferConcurrency == null -> error = "File transfer concurrency must be a valid number."
            !environmentValidation.valid -> error = environmentValidation.errors.firstOrNull()
            proxyJumpHostId != null && selectedJumpHost == null -> error = "Selected jump host is no longer available."
            ProxyJumpPolicy.errorForSelection(draftTarget, proxyJumpHostId, servers) != null -> error = ProxyJumpPolicy.errorForSelection(draftTarget, proxyJumpHostId, servers)
            wakeOnLanError != null -> error = wakeOnLanError
            !HostCommandSafety.isAutomaticCommandSafe(startupCommand) -> error = HostCommandSafety.unsafeAutomaticCommandMessage("Startup commands")
            credentialType == CredentialType.PrivateKey && credentialSecret.isNotBlank() && !KeyMaterialInspector.inspectPrivateKey(credentialSecret).valid -> error = KeyMaterialInspector.inspectPrivateKey(credentialSecret).summary
            !CredentialDraftValidator.validate(
                existing = selectedCredential,
                selectedCredentialId = selectedCredentialId,
                type = credentialType,
                secret = credentialSecret,
                label = effectiveCredentialLabel
            ).valid -> error = CredentialDraftValidator.validate(
                existing = selectedCredential,
                selectedCredentialId = selectedCredentialId,
                type = credentialType,
                secret = credentialSecret,
                label = effectiveCredentialLabel
            ).message
            duplicateCredentialLabel -> error = CredentialUniquenessPolicy.DuplicateLabelMessage
            else -> {
                val savePort = parsedPort!!
                val saveMoshColors = parsedMoshColors!!
                val saveEtSshBootstrapPort = parsedEtSshBootstrapPort!!
                val saveEtServerPort = parsedEtServerPort!!
                error = null
                onSave(
                    name,
                    host,
                    savePort,
                    username,
                    group,
                    tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                    osName,
                    osVersion,
                    selectedAccent,
                    favorite,
                    environmentValidation.entries,
                    selectedCredentialId,
                    effectiveCredentialLabel,
                    credentialType,
                    credentialSecret,
                    credentialPassphrase,
                    savePassphrase,
                    startupCommand,
                    startDirectory,
                    MonitoringConfig(
                        enabled = monitoringEnabled,
                        pollIntervalSeconds = ServerStatusRefreshPolicy.normalize(parsedPoll),
                        useOptionalAgent = useOptionalAgent
                    ),
                    ReconnectPolicy(
                        autoReconnect = autoReconnect,
                        keepAliveSeconds = parsedKeepAlive.coerceIn(10, 120),
                        maxAttempts = parsedReconnectAttempts.coerceIn(0, 10)
                    ),
                    customLogoUri,
                    proxyJumpHostId,
                    notes,
                    wakeOnLanConfig,
                    parsedConnectTimeout.coerceIn(3, 60),
                    sshCompressionEnabled,
                    protocol,
                    MoshConfig(
                        serverCommand = moshServerCommand,
                        locale = moshLocale,
                        colors = saveMoshColors,
                        predictionMode = moshPredictionMode
                    ),
                    EternalTerminalConfig(
                        sshBootstrapPort = saveEtSshBootstrapPort,
                        etServerPort = saveEtServerPort,
                        terminalType = etTerminalType,
                        serverCommand = etServerCommand
                    ),
                    VncProfileConfig(
                        colorDepthBits = parsedVncColorDepth,
                        shared = vncShared,
                        viewOnly = vncViewOnly,
                        targetFps = parsedVncTargetFps,
                        tunnelOverSsh = vncTunnelOverSsh,
                        sshBootstrapPort = parsedVncSshBootstrapPort ?: 22
                    ),
                    RdpProfileConfig(
                        width = parsedRdpWidth,
                        height = parsedRdpHeight,
                        colorDepth = parsedRdpColorDepth,
                        domain = rdpDomain,
                        useNla = rdpUseNla,
                        tunnelOverSsh = rdpTunnelOverSsh,
                        sshBootstrapPort = parsedRdpSshBootstrapPort ?: 22
                    ),
                    FileProtocolConfig(
                        rootPath = fileRootPath,
                        transferConcurrency = parsedFileTransferConcurrency,
                        resumeTransfers = fileResumeTransfers,
                        verifyChecksums = fileVerifyChecksums
                    ),
                    ProotProfileConfig(
                        distroId = prootDistroId,
                        rootfsPath = prootRootfsPath,
                        mountHome = prootMountHome
                    )
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton("<", "Back", modifier = Modifier.size(48.dp)) { onBack() }
            Text(
                if (server == null) "New Host" else "Edit Host",
                color = DeckColors.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                fontFamily = MaterialTheme.typography.headlineLarge.fontFamily,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp)
            )
            CircleIconButton("host-save", "Save host", modifier = Modifier.size(48.dp)) { submit() }
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 10.dp)
        ) {
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
            DeckTextField("Name", name, "Production API") { name = it }
            Spacer(Modifier.height(12.dp))
            DeckTextField("Host", host, "example.com or 192.168.1.20") { host = it.trim() }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                DeckTextField("User", username, "ubuntu", Modifier.weight(1f)) { username = it.trim() }
                DeckTextField("Port", port, "22", Modifier.weight(0.55f), KeyboardType.Number) { port = it.filter(Char::isDigit).take(5) }
            }
        }
        Spacer(Modifier.height(16.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
            Text("Identity", color = DeckColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryDeckButton("Saved Keys", Modifier.weight(1f)) {
                    credentialType = CredentialType.PrivateKey
                    identityDialogMode = IdentityDialogMode.Keys
                    identityDialogOpen = true
                }
                SecondaryDeckButton("Add Key", Modifier.weight(1f)) {
                    credentialType = CredentialType.PrivateKey
                    selectedCredentialId = null
                    credentialLabel = credentialLabel.ifBlank { "${username.ifBlank { "user" }}@${host.ifBlank { "host" }} key" }
                    credentialSecret = ""
                    credentialPassphrase = ""
                    savePassphrase = false
                    keyImportSummary = ""
                    error = null
                }
                SecondaryDeckButton("Vault", Modifier.weight(1f)) {
                    identityDialogMode = IdentityDialogMode.Vault
                    identityDialogOpen = true
                }
            }
            val selectedCredential = selectedCredentialId?.let { id -> connectableCredentials.firstOrNull { it.id == id } }
            Spacer(Modifier.height(8.dp))
            Text(
                selectedCredential?.let { "Selected: ${it.label}" } ?: "No saved identity selected.",
                color = DeckColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                CredentialType.entries.forEach { type ->
                    CredentialTypePill(
                        text = type.label(),
                        selected = credentialType == type,
                        modifier = Modifier.weight(1f),
                        color = when (type) {
                            CredentialType.Password -> DeckColors.Cyan
                            CredentialType.PrivateKey -> DeckColors.Purple
                            CredentialType.HardwareKey -> DeckColors.Green
                        }
                    ) {
                        if (credentialType != type) {
                            credentialType = type
                            selectedCredentialId?.let { id ->
                                val selected = connectableCredentials.firstOrNull { it.id == id }
                                if (selected?.type != type) {
                                    selectedCredentialId = null
                                    credentialLabel = ""
                                    credentialSecret = ""
                                    credentialPassphrase = ""
                                    savePassphrase = false
                                    keyImportSummary = ""
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            if (credentialType == CredentialType.PrivateKey || credentialType == CredentialType.HardwareKey) {
                DeckTextField("Key Name", credentialLabel, "Production deploy key") { credentialLabel = it }
                Spacer(Modifier.height(12.dp))
            } else if (selectedCredentialId == null) {
                Text(
                    "Password identity will be named from the login target.",
                    color = DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
            }
            if (selectedCredentialId != null) {
                SecondaryDeckButton("Identity Options", Modifier.fillMaxWidth()) {
                    identityDialogOpen = true
                }
                Spacer(Modifier.height(12.dp))
            }
            DeckTextField(
                label = when (credentialType) {
                    CredentialType.Password -> "Password"
                    CredentialType.PrivateKey -> "Private Key"
                    CredentialType.HardwareKey -> "Security Key Metadata"
                },
                value = credentialSecret,
                placeholder = if (selectedCredentialId == null) {
                    if (credentialType == CredentialType.HardwareKey) "Paste OpenSSH security-key stub or metadata" else "Enter and save credential"
                } else {
                    "Leave blank to keep saved credential"
                },
                keyboardType = KeyboardType.Text,
                visualTransformation = if (credentialType == CredentialType.Password) PasswordVisualTransformation() else VisualTransformation.None,
                singleLine = credentialType == CredentialType.Password
            ) { nextSecret ->
                credentialSecret = nextSecret
                if (nextSecret.isNotBlank()) {
                    selectedCredentialId = null
                    if (credentialType == CredentialType.PrivateKey) {
                        val info = KeyMaterialInspector.inspectPrivateKey(nextSecret)
                        keyImportSummary = if (info.valid) "${info.summary} (${info.fingerprint})" else info.summary
                        if (info.encrypted && credentialPassphrase.isBlank()) {
                            error = "Encrypted key detected. Enter its passphrase before connecting."
                        } else if (error?.contains("Encrypted key", ignoreCase = true) == true) {
                            error = null
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = credentialType == CredentialType.PrivateKey,
                enter = fadeIn(tween(110)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(150)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(120)) + fadeOut(tween(80))
            ) {
                Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryDeckButton("Import Key File", Modifier.weight(1f)) {
                        keyImportLauncher.launch(arrayOf("*/*", "application/octet-stream", "text/plain"))
                    }
                    SecondaryDeckButton("Paste Key", Modifier.weight(1f)) {
                        val pasted = clipboard.getText()?.text.orEmpty()
                        if (pasted.isBlank()) {
                            error = "Clipboard does not contain a private key."
                        } else {
                            importPrivateKeyText("Clipboard", pasted)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryDeckButton("Generate Ed25519", Modifier.weight(1f)) {
                        val generated = SshKeyGenerator.ed25519("${username.ifBlank { "user" }}@${host.ifBlank { "chronossh" }}")
                        credentialType = CredentialType.PrivateKey
                        credentialSecret = generated.privateKeyPem
                        selectedCredentialId = null
                        keyImportSummary = generated.authorizedPublicKey
                        credentialPassphrase = ""
                        savePassphrase = false
                        error = null
                    }
                    SecondaryDeckButton("Generate RSA", Modifier.weight(1f)) {
                        val generated = SshKeyGenerator.rsa("${username.ifBlank { "user" }}@${host.ifBlank { "chronossh" }}")
                        credentialType = CredentialType.PrivateKey
                        credentialSecret = generated.privateKeyPem
                        selectedCredentialId = null
                        keyImportSummary = generated.authorizedPublicKey
                        credentialPassphrase = ""
                        savePassphrase = false
                        error = null
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryDeckButton(if (savePassphrase) "Save Passphrase" else "Ask Passphrase", Modifier.fillMaxWidth()) {
                        savePassphrase = !savePassphrase
                    }
                }
                Spacer(Modifier.height(12.dp))
                DeckTextField(
                    label = "Key Passphrase",
                    value = credentialPassphrase,
                    placeholder = if (savePassphrase) "Save passphrase" else "Ask when needed",
                    keyboardType = KeyboardType.Password,
                    visualTransformation = PasswordVisualTransformation()
                ) { credentialPassphrase = it }
                if (keyImportSummary.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        pastedKeyInfo?.let { info -> if (info.valid) "${info.summary} (${info.fingerprint})" else info.summary } ?: keyImportSummary,
                        color = if (pastedKeyInfo?.valid == false) DeckColors.Red else DeckColors.SecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
            }
        }
        Spacer(Modifier.height(16.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
            DeckTextField("Group", group, "Cloud") { group = it }
            Spacer(Modifier.height(12.dp))
            DeckTextField("Tags", tags, "Production, Docker", keyboardType = KeyboardType.Text) { tags = it }
            Spacer(Modifier.height(12.dp))
            DeckTextField("Notes", notes, "Runbook, owner, maintenance window", keyboardType = KeyboardType.Text, singleLine = false) { notes = it }
            Spacer(Modifier.height(14.dp))
            ToggleRow("Favorite", favorite, "Show this host in the Favorites filter") {
                favorite = it
            }
            Spacer(Modifier.height(14.dp))
            SecondaryDeckButton("Custom Logo PNG", Modifier.fillMaxWidth()) {
                logoImportLauncher.launch(arrayOf("image/png", "image/*"))
            }
            AnimatedVisibility(
                visible = customLogoUri != null,
                enter = fadeIn() + expandVertically(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Custom logo selected", color = DeckColors.SecondaryText, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    SecondaryDeckButton("Clear Custom Logo", Modifier.fillMaxWidth()) {
                        customLogoUri = null
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
            Text("Connection Style", color = DeckColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(12.dp))
            Text("Accent", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                SoftPill("Auto", selected = selectedAccent == null, color = DeckColors.SecondaryText) {
                    selectedAccent = null
                }
                hostEditorAccentPresets().forEach { preset ->
                    SoftPill(preset.name, selected = selectedAccent == preset, color = androidx.compose.ui.graphics.Color(preset.argb)) {
                        selectedAccent = preset
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Profile", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                hostEditorConnectionPresets().forEach { preset ->
                    SoftPill(preset.label, selected = false, color = DeckColors.Purple) {
                        username = preset.username
                        port = preset.port.toString()
                        group = preset.group
                        osName = preset.osName
                        protocol = preset.protocol
                        monitoringEnabled = preset.monitoringEnabled
                        pollIntervalSeconds = preset.pollIntervalSeconds.toString()
                        autoReconnect = preset.autoReconnect
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("Protocol", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
            ) {
                ConnectionProtocol.entries.forEach { option ->
                    SoftPill(option.label(), selected = protocol == option, color = option.color()) {
                        protocol = option
                        port = option.defaultPort().toString()
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(protocol.detail(), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
            if (protocol != ConnectionProtocol.Ssh) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeckColors.SurfaceMuted)
                        .clickable { protocolOptionsOpen = !protocolOptionsOpen }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${protocol.label()} options", color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(protocol.optionsSummary(), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(if (protocolOptionsOpen) "-" else "+", color = DeckColors.SecondaryText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                AnimatedVisibility(
                    visible = protocolOptionsOpen,
                    enter = fadeIn(tween(80)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(100)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(80)) + fadeOut(tween(40))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.height(12.dp))
                        when (protocol) {
                            ConnectionProtocol.Mosh -> {
                                DeckTextField("Mosh Server", moshServerCommand, "mosh-server") { moshServerCommand = it.trim().take(96) }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    DeckTextField("Locale", moshLocale, "en_US.UTF-8", Modifier.weight(1f)) { moshLocale = it.trim().take(48) }
                                    DeckTextField("Colors", moshColors, "256", Modifier.weight(0.55f), KeyboardType.Number) { moshColors = it.filter(Char::isDigit).take(3) }
                                }
                            }
                            ConnectionProtocol.EternalTerminal -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    DeckTextField("SSH Port", etSshBootstrapPort, "22", Modifier.weight(1f), KeyboardType.Number) { etSshBootstrapPort = it.filter(Char::isDigit).take(5) }
                                    DeckTextField("ET Port", etServerPort, "2022", Modifier.weight(1f), KeyboardType.Number) { etServerPort = it.filter(Char::isDigit).take(5) }
                                }
                                Spacer(Modifier.height(12.dp))
                                DeckTextField("Terminal", etTerminalType, "xterm-256color") { etTerminalType = it.trim().take(48) }
                                Spacer(Modifier.height(12.dp))
                                DeckTextField("ET Command", etServerCommand, "etterminal") { etServerCommand = it.trim().take(96) }
                            }
                            ConnectionProtocol.Vnc -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    DeckTextField("Depth", vncColorDepth, "24", Modifier.weight(1f), KeyboardType.Number) { vncColorDepth = it.filter(Char::isDigit).take(2) }
                                    DeckTextField("FPS", vncTargetFps, "30", Modifier.weight(1f), KeyboardType.Number) { vncTargetFps = it.filter(Char::isDigit).take(2) }
                                }
                                Spacer(Modifier.height(12.dp))
                                ToggleRow("Shared", vncShared, "Allow sharing the VNC session") { vncShared = it }
                                ToggleRow("View Only", vncViewOnly, "Disable remote pointer and keyboard input") { vncViewOnly = it }
                                ToggleRow("Tunnel over SSH", vncTunnelOverSsh, "Route VNC through an SSH local forward") { vncTunnelOverSsh = it }
                                if (vncTunnelOverSsh) {
                                    Spacer(Modifier.height(12.dp))
                                    DeckTextField("SSH Port", vncSshBootstrapPort, "22", keyboardType = KeyboardType.Number) { vncSshBootstrapPort = it.filter(Char::isDigit).take(5) }
                                }
                            }
                            ConnectionProtocol.Rdp -> {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    DeckTextField("Width", rdpWidth, "1600", Modifier.weight(1f), KeyboardType.Number) { rdpWidth = it.filter(Char::isDigit).take(5) }
                                    DeckTextField("Height", rdpHeight, "900", Modifier.weight(1f), KeyboardType.Number) { rdpHeight = it.filter(Char::isDigit).take(5) }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    DeckTextField("Depth", rdpColorDepth, "16", Modifier.weight(0.55f), KeyboardType.Number) { rdpColorDepth = it.filter(Char::isDigit).take(2) }
                                    DeckTextField("Domain", rdpDomain, "WORKGROUP", Modifier.weight(1f)) { rdpDomain = it.trim().take(64) }
                                }
                                Spacer(Modifier.height(12.dp))
                                ToggleRow("NLA", rdpUseNla, "Use Network Level Authentication") { rdpUseNla = it }
                                ToggleRow("Tunnel over SSH", rdpTunnelOverSsh, "Route RDP through an SSH local forward") { rdpTunnelOverSsh = it }
                                if (rdpTunnelOverSsh) {
                                    Spacer(Modifier.height(12.dp))
                                    DeckTextField("SSH Port", rdpSshBootstrapPort, "22", keyboardType = KeyboardType.Number) { rdpSshBootstrapPort = it.filter(Char::isDigit).take(5) }
                                }
                            }
                            ConnectionProtocol.Smb, ConnectionProtocol.Rclone -> {
                                DeckTextField(
                                    "Root Path",
                                    startDirectory,
                                    if (protocol == ConnectionProtocol.Rclone) "remote:/" else "//${host.ifBlank { "server" }}/share"
                                ) {
                                    startDirectory = it.trim().take(160)
                                    fileRootPath = startDirectory
                                }
                                if (fileAdvancedControlsVisible(protocol)) {
                                    Spacer(Modifier.height(12.dp))
                                    DeckTextField("Concurrency", fileTransferConcurrency, "2", keyboardType = KeyboardType.Number) { fileTransferConcurrency = it.filter(Char::isDigit).take(2) }
                                    Spacer(Modifier.height(12.dp))
                                    ToggleRow("Resume Transfers", fileResumeTransfers, "Use rclone backend resume support when available") { fileResumeTransfers = it }
                                    ToggleRow("Verify Checksums", fileVerifyChecksums, "Use rclone backend checksum verification when available") { fileVerifyChecksums = it }
                                }
                            }
                            ConnectionProtocol.LocalProot -> {
                                DeckTextField("Distro", prootDistroId, "alpine-3.21") { prootDistroId = it.trim().take(64) }
                                Spacer(Modifier.height(12.dp))
                                DeckTextField("Rootfs Path", prootRootfsPath, "/data/user/0/.../rootfs") { prootRootfsPath = it.trim().take(180) }
                                Spacer(Modifier.height(12.dp))
                                ToggleRow("Mount Home", prootMountHome, "Bind ChronoSSH home into the local Linux shell") { prootMountHome = it }
                            }
                            ConnectionProtocol.Ssh -> Unit
                        }
                    }
                }
            }
            if (protocol == ConnectionProtocol.Rclone) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryDeckButton("Import rclone.conf", Modifier.weight(1f)) {
                        rcloneConfigImportLauncher.launch(arrayOf("*/*", "text/plain", "application/octet-stream"))
                    }
                    SecondaryDeckButton("Choose Remote", Modifier.weight(1f)) {
                        rcloneRemoteDialogOpen = true
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Remote path: ${startDirectory.ifBlank { "/" }}",
                    color = DeckColors.SecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (protocol == ConnectionProtocol.LocalProot) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryDeckButton(if (prootInstallBusy) "Installing..." else "Import Rootfs", Modifier.weight(1f)) {
                        if (!prootInstallBusy) {
                            prootRootfsImportLauncher.launch(arrayOf("application/gzip", "application/x-tar", "application/octet-stream", "*/*"))
                        }
                    }
                    SecondaryDeckButton("Download Rootfs", Modifier.weight(1f)) {
                        if (!prootInstallBusy) prootCatalogOpen = true
                    }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryDeckButton("Clear Rootfs", Modifier.fillMaxWidth()) {
                    if (!prootInstallBusy) {
                        LocalProotRuntime.clearRootfs(context, prootDistroId)
                        prootInstallStatus = prootStatusText(LocalProotRuntime.rootfsStatus(context, ProotProfileConfig(distroId = prootDistroId, rootfsPath = prootRootfsPath, mountHome = prootMountHome)))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    prootInstallStatus,
                    color = DeckColors.SecondaryText,
                    fontSize = 12.sp,
                    lineHeight = 15.sp
                )
            }
        }
        if (prootCatalogOpen) {
            AlertDialog(
                onDismissRequest = { prootCatalogOpen = false },
                title = { Text("Download PRoot Rootfs") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LocalProotRuntime.RootfsCatalog.forEach { entry ->
                            DeckCard(modifier = Modifier.fillMaxWidth(), radius = 16.dp, padding = PaddingValues(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.weight(1f)) {
                                        Text("${entry.label} ${entry.arch}", color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text("${entry.version} · ${(entry.sizeBytes / 1024 / 1024).coerceAtLeast(1)} MB", color = DeckColors.SecondaryText, fontSize = 12.sp)
                                    }
                                    SecondaryDeckButton("Install") { installCatalogRootfs(entry.id) }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { prootCatalogOpen = false }) { Text("Close") }
                }
            )
        }
        Spacer(Modifier.height(16.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, padding = PaddingValues(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { manualOsOpen = !manualOsOpen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Manual OS override", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
                    Text("Auto-detects after SSH metrics refresh", color = DeckColors.SecondaryText, fontSize = 13.sp)
                }
                Text(if (manualOsOpen) "-" else "+", color = DeckColors.SecondaryText, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            AnimatedVisibility(
                visible = manualOsOpen,
                enter = fadeIn(tween(80)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(100)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(80)) + fadeOut(tween(40))
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryDeckButton("Choose OS: $osName", Modifier.fillMaxWidth()) {
                        osPickerOpen = true
                    }
                    Spacer(Modifier.height(12.dp))
                    DeckTextField("OS Version", osVersion, "22.04, 12, 9.4") { osVersion = it.trim().take(48) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { advancedSshOpen = !advancedSshOpen },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Advanced SSH", color = DeckColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    Text("Start directory and startup command", color = DeckColors.SecondaryText, fontSize = 13.sp)
                }
                Text(if (advancedSshOpen) "-" else "+", color = DeckColors.SecondaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            AnimatedVisibility(
                visible = advancedSshOpen,
                enter = fadeIn(tween(80)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(100)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(80)) + fadeOut(tween(40))
            ) {
                Column(Modifier.fillMaxWidth()) {
                Spacer(Modifier.height(16.dp))
                DeckTextField("Start Directory", startDirectory, "/home/$username") { startDirectory = it }
                Spacer(Modifier.height(12.dp))
                DeckTextField("Startup Command", startupCommand, "tmux new -A -s main", keyboardType = KeyboardType.Text) { startupCommand = it }
                Spacer(Modifier.height(16.dp))
                DeckTextField("Environment", environmentText, "TERM=xterm-256color, LANG=en_US.UTF-8", keyboardType = KeyboardType.Text, singleLine = false) {
                    environmentText = it
                }
                if (jumpCandidates.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("ProxyJump", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        SoftPill("Direct", selected = proxyJumpHostId == null, color = DeckColors.SecondaryText) {
                            proxyJumpHostId = null
                        }
                        jumpCandidates.forEach { candidate ->
                            SoftPill(candidate.name, selected = proxyJumpHostId == candidate.id, color = DeckColors.Purple) {
                                proxyJumpHostId = candidate.id
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ToggleRow("Monitoring", monitoringEnabled, "Collect metrics automatically after trust and login") {
                    monitoringEnabled = it
                }
                Spacer(Modifier.height(12.dp))
                ToggleRow("Optional Agent", useOptionalAgent, "Prefer the lightweight ChronoSSH helper when installed") {
                    useOptionalAgent = it
                }
                Spacer(Modifier.height(12.dp))
                DeckTextField(
                    "Metric Interval",
                    pollIntervalSeconds,
                    "${ServerStatusRefreshPolicy.ServerBoxDefaultSeconds}",
                    keyboardType = KeyboardType.Number
                ) { pollIntervalSeconds = it.filter(Char::isDigit).take(2) }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    ServerStatusRefreshPolicy.PresetSeconds.forEach { seconds ->
                        SoftPill(
                            text = "${seconds}s",
                            selected = pollIntervalSeconds.toIntOrNull() == seconds,
                            color = DeckColors.Cyan
                        ) {
                            pollIntervalSeconds = seconds.toString()
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                ToggleRow("Auto Reconnect", autoReconnect, "Reconnect terminal sessions when possible") {
                    autoReconnect = it
                }
                Spacer(Modifier.height(12.dp))
                ToggleRow("SSH Compression", sshCompressionEnabled, "Negotiate zlib compression after login") {
                    sshCompressionEnabled = it
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    DeckTextField("Timeout", connectTimeoutSeconds, "10", Modifier.weight(1f), KeyboardType.Number) {
                        connectTimeoutSeconds = it.filter(Char::isDigit).take(2)
                    }
                    DeckTextField("Keepalive", keepAliveSeconds, "30", Modifier.weight(1f), KeyboardType.Number) {
                        keepAliveSeconds = it.filter(Char::isDigit).take(3)
                    }
                    DeckTextField("Retries", maxReconnectAttempts, "3", Modifier.weight(1f), KeyboardType.Number) {
                        maxReconnectAttempts = it.filter(Char::isDigit).take(2)
                    }
                }
                Spacer(Modifier.height(8.dp))
                PresetRow("Timeout", listOf(5, 10, 20, 30), connectTimeoutSeconds.toIntOrNull()) {
                    connectTimeoutSeconds = it.toString()
                }
                Spacer(Modifier.height(8.dp))
                PresetRow("Keepalive", listOf(15, 30, 60, 120), keepAliveSeconds.toIntOrNull()) {
                    keepAliveSeconds = it.toString()
                }
                Spacer(Modifier.height(8.dp))
                PresetRow("Retries", listOf(0, 3, 5, 8), maxReconnectAttempts.toIntOrNull()) {
                    maxReconnectAttempts = it.toString()
                }
                Spacer(Modifier.height(16.dp))
                Text("Wake on LAN", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                DeckTextField("MAC Address", wolMacAddress, "01:23:45:67:89:AB", keyboardType = KeyboardType.Text) {
                    wolMacAddress = it.trim().take(32)
                }
                Spacer(Modifier.height(12.dp))
                DeckTextField("Broadcast", wolBroadcastAddress, "255.255.255.255", keyboardType = KeyboardType.Text) {
                    wolBroadcastAddress = it.trim().take(64)
                }
                Spacer(Modifier.height(12.dp))
                DeckTextField("SecureON", wolSecureOnPassword, "Optional 6-byte password", keyboardType = KeyboardType.Text) {
                    wolSecureOnPassword = it.trim().take(32)
                }
            }
            }
        }
        Spacer(Modifier.height(16.dp))
        AnimatedVisibility(
            visible = error != null || externalError != null,
            enter = fadeIn() + expandVertically(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text((error ?: externalError).orEmpty(), color = DeckColors.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
            }
        }
        if (onDelete != null) {
            Spacer(Modifier.height(18.dp))
            DeckCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, padding = PaddingValues(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { deleteSectionOpen = !deleteSectionOpen },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Host actions", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Text("Delete is separated from editing controls", color = DeckColors.SecondaryText, fontSize = 13.sp)
                    }
                    Text(if (deleteSectionOpen) "-" else "+", color = DeckColors.SecondaryText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                }
                AnimatedVisibility(
                    visible = deleteSectionOpen,
                    enter = fadeIn(tween(110)) + expandVertically(expandFrom = Alignment.Top, animationSpec = tween(150)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(120)) + fadeOut(tween(80))
                ) {
                    Column(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Deleting removes this host profile. Saved identities remain in the vault.", color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                    DangerDeckButton("Delete Host", Modifier.fillMaxWidth()) { showDeleteDialog = true }
                }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        }
    }
    if (showDeleteDialog && onDelete != null) {
        DeleteHostDialog(
            hostName = server?.name.orEmpty().ifBlank { "this host" },
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                showDeleteDialog = false
                onDelete()
            }
        )
    }
    if (identityDialogOpen) {
        HostIdentityDialog(
            credentials = hostEditorIdentityDialogCredentials(connectableCredentials, identityDialogMode),
            selectedCredentialId = selectedCredentialId,
            onDismiss = { identityDialogOpen = false },
            onSelect = ::selectSavedCredential,
            onAddPassword = {
                identityDialogOpen = false
                credentialType = CredentialType.Password
                selectedCredentialId = null
                credentialLabel = ""
                credentialSecret = ""
                credentialPassphrase = ""
                savePassphrase = false
                keyImportSummary = ""
            },
            onAddKey = {
                identityDialogOpen = false
                credentialType = CredentialType.PrivateKey
                selectedCredentialId = null
                credentialLabel = credentialLabel.ifBlank { "${username.ifBlank { "user" }}@${host.ifBlank { "host" }} key" }
                credentialSecret = ""
                credentialPassphrase = ""
                savePassphrase = false
                keyImportSummary = ""
            },
            onClear = { selectSavedCredential(null) }
        )
    }
    if (rcloneRemoteDialogOpen) {
        RcloneRemotePickerDialog(
            remotes = importedRcloneRemotes,
            selectedPath = startDirectory,
            onDismiss = { rcloneRemoteDialogOpen = false },
            onSelect = { remote ->
                startDirectory = "${remote.name}:/"
                host = remote.name
                username = remote.type
                group = "Files"
                rcloneRemoteDialogOpen = false
            },
            onImport = {
                rcloneRemoteDialogOpen = false
                rcloneConfigImportLauncher.launch(arrayOf("*/*", "text/plain", "application/octet-stream"))
            }
        )
    }
    if (rcloneUnlockDialogOpen) {
        RcloneConfigPasswordDialog(
            busy = rcloneUnlockBusy,
            onDismiss = {
                if (!rcloneUnlockBusy) {
                    rcloneUnlockDialogOpen = false
                    pendingEncryptedRcloneConfig = null
                }
            },
            onUnlock = { password ->
                val encryptedText = pendingEncryptedRcloneConfig ?: return@RcloneConfigPasswordDialog
                rcloneUnlockBusy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching {
                            val configDir = java.io.File(context.filesDir, "rclone").apply { mkdirs() }
                            val configFile = java.io.File(configDir, "rclone.conf")
                            configFile.writeText(encryptedText)
                            RcloneClient(context).unlockAndDumpConfigJson(password)
                        }
                    }
                    result.onSuccess { dump ->
                        val remotes = RcloneConfigParser.parseDumpJson(dump)
                            .filter { it.name.isNotBlank() && it.type.isNotBlank() }
                        importedRcloneRemotes = remotes
                        rcloneUnlockDialogOpen = false
                        pendingEncryptedRcloneConfig = null
                        rcloneRemoteDialogOpen = remotes.isNotEmpty()
                        error = if (remotes.isEmpty()) "Encrypted rclone.conf unlocked, but no usable remotes were found." else null
                    }.onFailure { failure ->
                        error = "Encrypted rclone.conf unlock failed: ${failure.message ?: failure::class.java.simpleName}"
                    }
                    rcloneUnlockBusy = false
                }
            }
        )
    }
    if (osPickerOpen) {
        HostOsPickerDialog(
            selected = osName,
            onDismiss = { osPickerOpen = false },
            onSelect = { selected ->
                osName = selected
                osPickerOpen = false
            }
        )
    }
}

@Composable
private fun RcloneRemotePickerDialog(
    remotes: List<ParsedRemote>,
    selectedPath: String,
    onDismiss: () -> Unit,
    onSelect: (ParsedRemote) -> Unit,
    onImport: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query, remotes) {
        remotes.filter { remote ->
            val haystack = "${remote.name} ${remote.type}".lowercase()
            query.trim().lowercase().split(' ').filter { it.isNotBlank() }.all(haystack::contains)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("rclone remotes", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                if (remotes.isEmpty()) {
                    Text(
                        "Import an existing rclone.conf, then pick a remote here.",
                        color = DeckColors.SecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    DeckTextField("Search", query, "drive, s3, backup") { query = it.take(48) }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        matches.forEach { remote ->
                            val selected = selectedPath.startsWith("${remote.name}:") ||
                                selectedPath.startsWith("rclone://${remote.name}:")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (selected) DeckColors.Cyan.copy(alpha = 0.14f) else DeckColors.SurfaceMuted)
                                    .border(1.dp, if (selected) DeckColors.Cyan.copy(alpha = 0.38f) else DeckColors.CardStroke, RoundedCornerShape(16.dp))
                                    .clickable { onSelect(remote) }
                                    .padding(12.dp)
                            ) {
                                Column {
                                    Text(remote.name, color = DeckColors.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(remote.type, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onImport) {
                Text("Import", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun RcloneConfigPasswordDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onUnlock: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlock rclone.conf", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    "Enter the rclone config password to import its remotes.",
                    color = DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                DeckTextField(
                    "Config Password",
                    password,
                    "Required",
                    visualTransformation = PasswordVisualTransformation()
                ) { password = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onUnlock(password) },
                enabled = !busy && password.isNotBlank()
            ) {
                Text(if (busy) "Unlocking..." else "Unlock", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun HostOsPickerDialog(
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val matches = remember(query) { hostEditorFilteredOsPresets(query) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose OS", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                DeckTextField("Search", query, "Ubuntu, FreeBSD, OpenWrt") { query = it.take(48) }
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matches.forEach { os ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (os.equals(selected, ignoreCase = true)) DeckColors.Cyan.copy(alpha = 0.14f) else DeckColors.SurfaceMuted)
                                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp))
                                .clickable { onSelect(os) }
                                .padding(12.dp)
                        ) {
                            Text(os, color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        }
    )
}

private enum class IdentityDialogMode {
    Keys,
    Vault
}

@Composable
private fun HostIdentityDialog(
    credentials: List<Credential>,
    selectedCredentialId: String?,
    onDismiss: () -> Unit,
    onSelect: (Credential?) -> Unit,
    onAddPassword: () -> Unit,
    onAddKey: () -> Unit,
    onClear: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Identity", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryDeckButton("No saved identity", Modifier.fillMaxWidth()) { onSelect(null) }
                if (credentials.isEmpty()) {
                    Text(
                        "No saved passwords or private keys yet.",
                        color = DeckColors.SecondaryText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                } else {
                    credentials.forEach { credential ->
                        val selected = credential.id == selectedCredentialId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) DeckColors.Cyan.copy(alpha = 0.14f) else DeckColors.SurfaceMuted)
                                .border(1.dp, if (selected) DeckColors.Cyan.copy(alpha = 0.38f) else DeckColors.CardStroke, RoundedCornerShape(18.dp))
                                .clickable { onSelect(credential) }
                                .padding(14.dp)
                        ) {
                            Column {
                                Text(credential.label, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(hostEditorIdentityDetail(credential), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    SecondaryDeckButton("Add Password", Modifier.weight(1f), onAddPassword)
                    SecondaryDeckButton("Add Key", Modifier.weight(1f), onAddKey)
                }
                if (selectedCredentialId != null) {
                    SecondaryDeckButton("Clear Saved Identity", Modifier.fillMaxWidth(), onClear)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        }
    )
}

@Composable
private fun PresetRow(
    label: String,
    values: List<Int>,
    selected: Int?,
    onSelected: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        values.forEach { value ->
            SoftPill(
                text = if (label == "Retries") value.toString() else "${value}s",
                selected = selected == value,
                color = DeckColors.Cyan
            ) {
                onSelected(value)
            }
        }
    }
}

@Composable
private fun DeleteHostDialog(
    hostName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete host?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = { Text("This removes $hostName from ChronoSSH. Credentials remain in the vault unless you delete them separately.", color = DeckColors.SecondaryText) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = DeckColors.Red, fontWeight = FontWeight.Black)
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
private fun DeckTextField(
    label: String,
    value: String,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    onValueChange: (String) -> Unit
) {
    Column(modifier) {
        Text(label, color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            textStyle = TextStyle(
                color = DeckColors.PrimaryText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = if (label == "Private Key") FontFamily.Monospace else null
            ),
            cursorBrush = SolidColor(DeckColors.BrandAlt),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeckColors.SurfaceMuted)
                        .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                        .height(if (singleLine) 50.dp else 152.dp)
                        .padding(horizontal = 15.dp, vertical = 13.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isBlank()) {
                        Text(placeholder, color = DeckColors.TertiaryText, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun SecondaryDeckButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun CredentialTypePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(if (selected) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
            .border(1.dp, if (selected) DeckColors.CardStroke else DeckColors.Divider.copy(alpha = 0.35f), RoundedCornerShape(17.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (selected) color.copy(alpha = 0.78f) else DeckColors.SecondaryText.copy(alpha = 0.45f))
        )
        Text(
            text,
            color = if (selected) DeckColors.PrimaryText else DeckColors.SecondaryText,
            fontSize = 11.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    detail: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(width = 48.dp, height = 28.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (checked) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (checked) DeckColors.SecondaryText else DeckColors.TertiaryText)
            )
        }
    }
}

@Composable
private fun DangerDeckButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(DeckColors.Red.copy(alpha = 0.14f))
            .border(1.dp, DeckColors.Red.copy(alpha = 0.32f), RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = DeckColors.Red, fontSize = 16.sp, fontWeight = FontWeight.Black)
    }
}

private fun CredentialType.label(): String {
    return when (this) {
        CredentialType.Password -> "Password"
        CredentialType.PrivateKey -> "Private Key"
        CredentialType.HardwareKey -> "Hardware Key"
    }
}

internal fun hostEditorSavedKeyCredentials(credentials: List<Credential>): List<Credential> {
    return credentials.filter { it.type == CredentialType.PrivateKey || it.type == CredentialType.HardwareKey }
}

internal fun hostEditorIdentityDialogCredentials(credentials: List<Credential>, vaultMode: Boolean): List<Credential> {
    return if (vaultMode) hostEditorSelectableCredentials(credentials) else hostEditorSavedKeyCredentials(credentials)
}

private fun hostEditorIdentityDialogCredentials(credentials: List<Credential>, mode: IdentityDialogMode): List<Credential> {
    return hostEditorIdentityDialogCredentials(credentials, vaultMode = mode == IdentityDialogMode.Vault)
}

internal fun hostEditorSelectableCredentials(credentials: List<Credential>): List<Credential> {
    return credentials.sortedWith(compareBy<Credential> {
        when (it.type) {
            CredentialType.PrivateKey -> 0
            CredentialType.HardwareKey -> 1
            CredentialType.Password -> 2
        }
    }.thenByDescending { it.favorite }.thenBy { it.label.lowercase() })
}

internal fun hostEditorIdentityDialogActions(hasSelectedCredential: Boolean): List<String> {
    return buildList {
        add("No saved identity")
        add("Add Key")
        add("Add Password")
        if (hasSelectedCredential) add("Clear Saved Identity")
    }
}

internal fun hostEditorIdentityDetail(credential: Credential): String {
    val source = if (credential.secretBacked) "saved" else "needs key material"
    return "${credential.type.label()} / $source"
}

private data class HostEditorConnectionPreset(
    val label: String,
    val username: String,
    val port: Int,
    val group: String,
    val osName: String,
    val protocol: ConnectionProtocol,
    val monitoringEnabled: Boolean,
    val pollIntervalSeconds: Int,
    val autoReconnect: Boolean
)

internal fun hostEditorFilteredOsPresets(query: String): List<String> {
    val clean = query.trim()
    return hostEditorOsPresets()
        .filter { clean.isBlank() || it.contains(clean, ignoreCase = true) }
        .take(140)
}

internal fun hostEditorOsPresets(): List<String> {
    return listOf(
        "Linux", "Ubuntu", "Debian", "Fedora", "Arch Linux", "Alpine Linux", "AlmaLinux", "Rocky Linux",
        "CentOS", "Red Hat Enterprise Linux", "Oracle Linux", "openSUSE", "openSUSE Leap", "openSUSE Tumbleweed",
        "SUSE Linux Enterprise Server", "Linux Mint", "Pop!_OS", "Kali Linux", "NixOS", "Gentoo", "Manjaro",
        "Void Linux", "Slackware", "Mageia", "Clear Linux", "EndeavourOS", "Garuda Linux", "Zorin OS",
        "Elementary OS", "MX Linux", "antiX", "PCLinuxOS", "Solus", "Bodhi Linux", "Peppermint OS",
        "Parrot OS", "BlackArch", "Qubes OS", "Tails", "Deepin", "KDE neon", "Kubuntu", "Xubuntu",
        "Lubuntu", "Ubuntu Server", "Ubuntu Core", "Debian Testing", "Debian Unstable", "Raspberry Pi OS",
        "DietPi", "Armbian", "OpenWrt", "LEDE", "iStoreOS", "pfSense", "OPNsense", "TrueNAS SCALE",
        "TrueNAS CORE", "FreeBSD", "OpenBSD", "NetBSD", "DragonFly BSD", "macOS", "AIX", "Solaris",
        "OpenIndiana", "Illumos", "SmartOS", "OmniOS", "Proxmox VE", "Proxmox Backup Server",
        "VMware ESXi", "XCP-ng", "Citrix Hypervisor", "Harvester", "Unraid", "Synology DSM", "QNAP QTS",
        "Asustor ADM", "OpenMediaVault", "Rockstor", "NethServer", "ClearOS", "VyOS", "MikroTik RouterOS",
        "Cisco IOS", "Cisco NX-OS", "Juniper Junos", "Arista EOS", "Cumulus Linux", "SonicWall SonicOS",
        "Fortinet FortiOS", "Ubiquiti UniFi OS", "EdgeOS", "CoreELEC", "LibreELEC", "Batocera",
        "Home Assistant OS", "Android", "Android TV", "ChromeOS", "Windows Server", "Windows",
        "Windows Subsystem for Linux", "Amazon Linux", "Amazon Linux 2023", "Azure Linux", "Photon OS",
        "Flatcar Container Linux", "Fedora CoreOS", "Talos Linux", "Bottlerocket", "RancherOS",
        "Container-Optimized OS", "K3OS", "EulerOS", "openEuler", "Anolis OS", "TencentOS Server",
        "Alibaba Cloud Linux", "UOS Server", "Deepin Server", "Asianux", "Miracle Linux", "ALT Linux",
        "Calculate Linux", "Devuan", "Artix Linux", "Chimera Linux", "PostmarketOS", "Alpine Edge"
    ).distinct()
}

private fun hostEditorConnectionPresets(): List<HostEditorConnectionPreset> {
    return listOf(
        HostEditorConnectionPreset("Linux SSH", "root", 22, "Cloud", "Linux", ConnectionProtocol.Ssh, true, 2, true),
        HostEditorConnectionPreset("Mosh", "root", 22, "Mobile", "Linux", ConnectionProtocol.Mosh, true, 5, true),
        HostEditorConnectionPreset("Eternal", "root", 2022, "Mobile", "Linux", ConnectionProtocol.EternalTerminal, true, 5, true),
        HostEditorConnectionPreset("Desktop VNC", "user", 5900, "Desktop", "Linux", ConnectionProtocol.Vnc, false, 10, false),
        HostEditorConnectionPreset("Desktop RDP", "user", 3389, "Desktop", "Windows", ConnectionProtocol.Rdp, false, 10, false),
        HostEditorConnectionPreset("SMB Share", "user", 445, "Files", "Windows", ConnectionProtocol.Smb, false, 10, false),
        HostEditorConnectionPreset("Cloud/rclone", "user", 5572, "Files", "Linux", ConnectionProtocol.Rclone, false, 10, false),
        HostEditorConnectionPreset("Local PRoot", "user", 22, "Local", "Alpine Linux", ConnectionProtocol.LocalProot, false, 10, false)
    )
}

internal fun ConnectionProtocol.label(): String {
    return when (this) {
        ConnectionProtocol.Ssh -> "SSH"
        ConnectionProtocol.Mosh -> "Mosh"
        ConnectionProtocol.EternalTerminal -> "ET"
        ConnectionProtocol.Vnc -> "VNC"
        ConnectionProtocol.Rdp -> "RDP"
        ConnectionProtocol.Smb -> "SMB"
        ConnectionProtocol.Rclone -> "rclone"
        ConnectionProtocol.LocalProot -> "PRoot"
    }
}

private fun ConnectionProtocol.defaultPort(): Int {
    return when (this) {
        ConnectionProtocol.Ssh, ConnectionProtocol.Mosh, ConnectionProtocol.LocalProot -> 22
        ConnectionProtocol.EternalTerminal -> 2022
        ConnectionProtocol.Vnc -> 5900
        ConnectionProtocol.Rdp -> 3389
        ConnectionProtocol.Smb -> 445
        ConnectionProtocol.Rclone -> 5572
    }
}

private fun ConnectionProtocol.detail(): String {
    return when (this) {
        ConnectionProtocol.Ssh -> "Terminal, SFTP, metrics and tunnels."
        ConnectionProtocol.Mosh -> "SSH bootstrap with Mosh UDP roaming."
        ConnectionProtocol.EternalTerminal -> "SSH bootstrap with Eternal Terminal persistence."
        ConnectionProtocol.Vnc -> "VNC viewer profile."
        ConnectionProtocol.Rdp -> "RDP viewer profile."
        ConnectionProtocol.Smb -> "SMB file browser profile."
        ConnectionProtocol.Rclone -> "Embedded rclone file browser profile."
        ConnectionProtocol.LocalProot -> "Local shell with packaged PRoot runtime assets."
    }
}

private fun ConnectionProtocol.optionsSummary(): String {
    return when (this) {
        ConnectionProtocol.Ssh -> "Default SSH behavior"
        ConnectionProtocol.Mosh -> "Server command, locale, colors"
        ConnectionProtocol.EternalTerminal -> "Bootstrap and ET server ports"
        ConnectionProtocol.Vnc -> "Color depth, sharing, tunnel"
        ConnectionProtocol.Rdp -> "Display size, domain, tunnel"
        ConnectionProtocol.Smb -> "Share root path"
        ConnectionProtocol.Rclone -> "Root path and transfer behavior"
        ConnectionProtocol.LocalProot -> "Distro and rootfs behavior"
    }
}

internal fun fileAdvancedControlsVisible(protocol: ConnectionProtocol): Boolean {
    return protocol == ConnectionProtocol.Rclone
}

internal fun hostEditorMoshColorsValid(colors: Int?): Boolean {
    return colors in 8..256
}

internal fun hostEditorPortValid(port: Int?): Boolean {
    return port in 1..65535
}

internal fun prootStatusText(status: LocalProotRootfsStatus): String {
    return when {
        status.ready -> "PRoot rootfs ready at ${status.path}."
        status.hasShell -> "Rootfs has bin/sh but is missing ChronoSSH ready marker: ${status.path}."
        else -> "No complete PRoot rootfs installed. Local shell will use Android shell until import."
    }
}

private fun ConnectionProtocol.color(): androidx.compose.ui.graphics.Color {
    return when (this) {
        ConnectionProtocol.Ssh -> DeckColors.Cyan
        ConnectionProtocol.Mosh, ConnectionProtocol.EternalTerminal -> DeckColors.Green
        ConnectionProtocol.Vnc, ConnectionProtocol.Rdp -> DeckColors.Purple
        ConnectionProtocol.Smb, ConnectionProtocol.Rclone -> DeckColors.Orange
        ConnectionProtocol.LocalProot -> DeckColors.SecondaryText
    }
}

private fun hostEditorAccentPresets(): List<ServerAccent> {
    return listOf(
        ServerAccent("Blue", 0xFF2563EB),
        ServerAccent("Green", 0xFF16A34A),
        ServerAccent("Cyan", 0xFF0891B2),
        ServerAccent("Amber", 0xFFD97706),
        ServerAccent("Rose", 0xFFE11D48),
        ServerAccent("Slate", 0xFF64748B)
    )
}
