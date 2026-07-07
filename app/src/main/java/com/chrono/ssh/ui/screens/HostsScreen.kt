@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.chrono.ssh.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.SftpBookmark
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TerminalSessionRecord
import com.chrono.ssh.core.model.TransferDirection
import com.chrono.ssh.core.model.TransferRecord
import com.chrono.ssh.core.model.TransferRecordState
import com.chrono.ssh.core.service.ForwardStatus
import com.chrono.ssh.core.service.HostShareLinkCodec
import com.chrono.ssh.core.service.HostShareQrCodec
import com.chrono.ssh.core.service.HostShareQrMatrix
import com.chrono.ssh.core.service.KeyMaterialInspector
import com.chrono.ssh.core.service.MetricFormatters
import com.chrono.ssh.core.service.PortForwardValidator
import com.chrono.ssh.core.service.SftpBrowserOperationPolicy
import com.chrono.ssh.core.service.SftpClient
import com.chrono.ssh.core.service.SftpClientHealth
import com.chrono.ssh.core.service.SftpEntry
import com.chrono.ssh.core.service.SftpEntryType
import com.chrono.ssh.core.service.SftpErrorMapper
import com.chrono.ssh.core.service.SftpFileNamePolicy
import com.chrono.ssh.core.service.SftpHostTransferPolicy
import com.chrono.ssh.core.service.SftpPathResolver
import com.chrono.ssh.core.service.SftpPermissionModePolicy
import com.chrono.ssh.core.service.SftpSortMode
import com.chrono.ssh.core.service.SftpTextFilePolicy
import com.chrono.ssh.core.service.ScpClient
import com.chrono.ssh.core.service.ScpTransferPolicy
import com.chrono.ssh.core.service.SnippetValidator
import com.chrono.ssh.core.service.SmbTargetPolicy
import com.chrono.ssh.core.service.SshAuthFailureHints
import com.chrono.ssh.core.service.SshTransport
import com.chrono.ssh.core.service.SshKeyGenerator
import com.chrono.ssh.core.service.TransferCancellationRegistry
import com.chrono.ssh.core.service.VaultSecretAction
import com.chrono.ssh.core.service.VaultSecretExportPolicy
import com.chrono.ssh.core.service.VaultPublicKeyPolicy
import com.chrono.ssh.core.service.displayName
import com.chrono.ssh.core.service.routeLabel
import com.chrono.ssh.ui.design.CircleIconButton
import com.chrono.ssh.ui.design.ChronoOsLogo
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.LargeScreenTitle
import com.chrono.ssh.ui.design.HeadingFontTarget
import com.chrono.ssh.ui.design.SoftPill
import com.chrono.ssh.ui.design.osLogoDrawableOrNull
import androidx.core.content.FileProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SftpWorkspaceRuntime {
    var client by mutableStateOf<SftpClient?>(null)
    var clientServerId by mutableStateOf<String?>(null)
}

@Composable
fun HostsScreen(
    title: String,
    subtitle: String,
    servers: List<ServerProfile>,
    credentials: List<Credential>,
    knownHosts: List<KnownHost>,
    snippets: List<Snippet>,
    forwards: List<PortForwardRule>,
    forwardStatuses: Map<String, ForwardStatus> = emptyMap(),
    terminalSessions: List<TerminalSessionRecord>,
    sftpBookmarks: List<SftpBookmark>,
    transfers: List<TransferRecord>,
    mode: String,
    initialSection: String? = null,
    initialSectionRequestKey: Int = 0,
    initialServerId: String? = null,
    initialForwardServerId: String? = null,
    initialForwardDraftRequestKey: Int = 0,
    preselectedCredentialId: String? = null,
    onAddHost: () -> Unit,
    onServerClick: (ServerProfile) -> Unit,
    onTerminalClick: (ServerProfile) -> Unit,
    onTrustHost: (ServerProfile) -> Unit,
    onMoveServer: (String, Int) -> Unit = { _, _ -> },
    onToggleForward: (PortForwardRule) -> Unit,
    onUpsertForward: (PortForwardRule) -> Unit = {},
    onDeleteForward: (PortForwardRule) -> Unit = {},
    onDeleteCredential: (Credential) -> Unit = {},
    onRenameCredential: (Credential, String) -> Unit = { _, _ -> },
    onUnlinkCredential: (Credential) -> Unit = {},
    onReplaceCredentialSecret: (Credential, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onAddPrivateKeyCredential: (String, String, String, Boolean) -> Unit = { _, _, _, _ -> },
    onUpsertCredential: (Credential) -> Unit = {},
    onDeleteKnownHost: (KnownHost) -> Unit = {},
    onUpsertSnippet: (Snippet) -> Result<Snippet> = { Result.success(it) },
    onDeleteSnippet: (Snippet) -> Unit = {},
    onLoadCredentialPayload: suspend (Credential) -> String = { "" },
    onTransferChanged: (TransferRecord) -> Unit = {},
    onCancelTransfer: (String) -> Unit = {},
    onClearFinishedTransfers: () -> Unit = {},
    onSftpBookmarkChanged: (SftpBookmark) -> Unit = {},
    onSftpBookmarkDeleted: (SftpBookmark) -> Unit = {},
    onSftpConnected: (ServerProfile) -> Unit = {},
    sshTransport: SshTransport,
    backupPreview: String = ""
) {
    val sections = if (mode == "files") {
        listOf("Files", "Queue")
    } else {
        listOf("Hosts", "Keys", "Known", "Snippets", "Tunnels", "Stats")
    }
    var selectedSection by remember(mode, initialSection) {
        mutableStateOf(initialSection?.takeIf { it in sections } ?: sections.first())
    }
    LaunchedEffect(initialSectionRequestKey, initialSection) {
        initialSection?.takeIf { it in sections }?.let { selectedSection = it }
    }
    var filesInitialServerId by remember(mode, initialServerId) {
        mutableStateOf(initialServerId)
    }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var hostShareStatus by remember { mutableStateOf<String?>(null) }
    var qrShare by remember { mutableStateOf<ShareQrPayload?>(null) }

    fun shareHostLink(server: ServerProfile) {
        val link = HostShareLinkCodec.encode(server)
        shareTextLink(context, server.name, link)
        hostShareStatus = "Host link ready to share: ${server.name}"
    }

    fun shareHostQr(server: ServerProfile) {
        shareQrImage(context, hostSharePayload(server)).onSuccess {
            hostShareStatus = "Host QR ready to share: ${server.name}"
        }.onFailure {
            hostShareStatus = "QR share failed: ${it.message ?: it::class.java.simpleName}"
        }
    }

    qrShare?.let { payload ->
        ShareQrDialog(
            payload = payload,
            onDismiss = { qrShare = null },
            onCopy = {
                clipboard.setText(AnnotatedString(payload.link))
                hostShareStatus = "${payload.title} link copied"
            },
            onShareQr = {
                shareQrImage(context, payload).onFailure {
                    hostShareStatus = "QR share failed: ${it.message ?: it::class.java.simpleName}"
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                LargeScreenTitle(
                    if (mode == "files") "SFTP" else if (mode == "vault") "Vault" else title,
                    headingTarget = if (mode == "files") HeadingFontTarget.Files else if (mode == "vault") HeadingFontTarget.Vault else null
                )
                if (mode != "files" && mode != "vault") {
                    Text(subtitle, color = DeckColors.SecondaryText, fontSize = 18.sp)
                }
            }
            if (mode != "files" && mode != "vault") {
                CircleIconButton("host-add", "Add host", onClick = onAddHost)
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            sections.forEach { section ->
                SoftPill(
                    text = if (section == "Files") "SFTP" else section,
                    selected = section == selectedSection,
                    color = if (section == "Tunnels" || section == "Files") DeckColors.Cyan else DeckColors.BrandAlt
                ) { selectedSection = section }
            }
        }
        Spacer(Modifier.height(20.dp))
        hostShareStatus?.let {
            Text(it, color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        key(selectedSection) {
            when (selectedSection) {
                "Files" -> TransferSection(
                    servers = servers,
                    credentials = credentials,
                    knownHosts = knownHosts,
                    bookmarks = sftpBookmarks,
                    transfers = transfers,
                    initialServerId = filesInitialServerId,
                    onServerClick = onServerClick,
                    onTerminalClick = onTerminalClick,
                    onTrustHost = onTrustHost,
                    onTransferChanged = onTransferChanged,
                    onSftpBookmarkChanged = onSftpBookmarkChanged,
                    onSftpBookmarkDeleted = onSftpBookmarkDeleted,
                    onSftpConnected = onSftpConnected,
                    transport = sshTransport
                )
                "Queue" -> QueueSection(transfers, onCancelTransfer, onClearFinishedTransfers)
                "Keys" -> CredentialsSection(
                    credentials = credentials,
                    onDeleteCredential = onDeleteCredential,
                    onRenameCredential = onRenameCredential,
                    onUnlinkCredential = onUnlinkCredential,
                    onReplaceCredentialSecret = onReplaceCredentialSecret,
                    onAddPrivateKeyCredential = onAddPrivateKeyCredential,
                    onUpsertCredential = onUpsertCredential,
                    onLoadCredentialPayload = onLoadCredentialPayload,
                    preselectedCredentialId = preselectedCredentialId,
                    onImportCredentialLink = {
                        val status = importCredentialShareLink(clipboard.getText()?.text.orEmpty(), credentials, onUpsertCredential)
                        hostShareStatus = status.message
                    },
                    onCopyCredentialLink = { credential ->
                        val payload = credentialSharePayload(credential)
                        clipboard.setText(AnnotatedString(payload.link))
                        hostShareStatus = "${payload.title} link copied"
                    },
                    onShareCredentialLink = { credential ->
                        val payload = credentialSharePayload(credential)
                        shareTextLink(context, payload.title, payload.link)
                        hostShareStatus = "${payload.title} link ready to share"
                    },
                    onShowCredentialQr = { qrShare = credentialSharePayload(it) }
                )
                "Known" -> KnownHostsSection(knownHosts, servers, onTrustHost, onDeleteKnownHost)
                "Snippets" -> SnippetsSection(
                    snippets = snippets,
                    servers = servers,
                    onUpsertSnippet = onUpsertSnippet,
                    onDeleteSnippet = onDeleteSnippet,
                    onImportSnippetLink = {
                        val status = importSnippetShareLink(clipboard.getText()?.text.orEmpty(), onUpsertSnippet)
                        hostShareStatus = status.message
                    },
                    onCopySnippetLink = { snippet ->
                        val payload = snippetSharePayload(snippet)
                        clipboard.setText(AnnotatedString(payload.link))
                        hostShareStatus = "${payload.title} link copied"
                    },
                    onShareSnippetLink = { snippet ->
                        val payload = snippetSharePayload(snippet)
                        shareTextLink(context, payload.title, payload.link)
                        hostShareStatus = "${payload.title} link ready to share"
                    },
                    onShowSnippetQr = { qrShare = snippetSharePayload(it) }
                )
                "Tunnels" -> TunnelsSection(
                    forwards = forwards,
                    forwardStatuses = forwardStatuses,
                    servers = servers,
                    initialServerId = initialForwardServerId,
                    initialDraftRequestKey = initialForwardDraftRequestKey,
                    onToggleForward = onToggleForward,
                    onUpsertForward = onUpsertForward,
                    onDeleteForward = onDeleteForward,
                    onImportForwardLink = {
                        val status = importForwardShareLink(clipboard.getText()?.text.orEmpty(), servers, onUpsertForward)
                        hostShareStatus = status.message
                    },
                    onCopyForwardLink = { forward ->
                        val payload = forwardSharePayload(forward, servers)
                        clipboard.setText(AnnotatedString(payload.link))
                        hostShareStatus = "${payload.title} link copied"
                    },
                    onShareForwardLink = { forward ->
                        val payload = forwardSharePayload(forward, servers)
                        shareTextLink(context, payload.title, payload.link)
                        hostShareStatus = "${payload.title} link ready to share"
                    },
                    onShowForwardQr = { qrShare = forwardSharePayload(it, servers) }
                )
                "Stats" -> BackupSection(backupPreview)
                else -> {
                    HostReadinessSection(
                        servers = servers,
                        credentials = credentials,
                        knownHosts = knownHosts,
                        forwards = forwards,
                        terminalSessions = terminalSessions,
                        onServerClick = onServerClick,
                        onTerminalClick = onTerminalClick,
                        onTrustHost = onTrustHost,
                        onMoveServer = onMoveServer,
                        onCopyHostLink = { server ->
                            clipboard.setText(AnnotatedString(HostShareLinkCodec.encode(server)))
                            hostShareStatus = "Host link copied: ${server.name}"
                        },
                        onShareHostLink = ::shareHostLink,
                        onShowHostQr = { qrShare = hostSharePayload(it) },
                        onOpenFiles = { server ->
                            val launch = hostFilesLaunch(server.id)
                            filesInitialServerId = launch.serverId
                            selectedSection = launch.section
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(84.dp))
    }
}

@Composable
fun SftpBrowserScreen(
    servers: List<ServerProfile>,
    credentials: List<Credential>,
    knownHosts: List<KnownHost>,
    bookmarks: List<SftpBookmark>,
    initialServerId: String?,
    sftpWorkspaces: List<Pair<String, ServerProfile>> = emptyList(),
    selectedSftpWorkspaceKey: String? = null,
    onSelectSftpWorkspace: (String) -> Unit = {},
    onCloseSftpWorkspace: (String) -> Unit = {},
    onSftpWorkspaceFailed: (String) -> Unit = {},
    onBack: () -> Unit,
    onServerClick: (ServerProfile) -> Unit,
    onTerminalClick: (ServerProfile) -> Unit,
    onTrustHost: (ServerProfile) -> Unit,
    onTransferChanged: (TransferRecord) -> Unit,
    onSftpBookmarkChanged: (SftpBookmark) -> Unit,
    onSftpBookmarkDeleted: (SftpBookmark) -> Unit,
    onSftpConnected: (ServerProfile) -> Unit = {},
    sftpRuntimes: Map<String, SftpWorkspaceRuntime> = emptyMap(),
    transfers: List<TransferRecord> = emptyList(),
    sftpDefaultSortMode: SftpSortMode = SftpSortMode.Name,
    sftpDefaultSortDescending: Boolean = false,
    sftpShowHiddenByDefault: Boolean = false,
    sshTransport: SshTransport
) {
    var backRequestSerial by remember { mutableStateOf(0) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DeckColors.Surface)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton("<", "Back", modifier = Modifier.size(46.dp), onClick = { backRequestSerial += 1 })
        }
        Spacer(Modifier.height(10.dp))
        if (sftpWorkspaces.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                sftpWorkspaces.forEach { (key, server) ->
                    SftpWorkspaceTab(
                        server = server,
                        selected = key == selectedSftpWorkspaceKey,
                        onSelect = { onSelectSftpWorkspace(key) },
                        onClose = { onCloseSftpWorkspace(key) }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val visibleWorkspaces: List<Pair<String, ServerProfile?>> = sftpWorkspaces
            val activeWorkspaceKey = selectedSftpWorkspaceKey
                ?.takeIf { selectedKey -> visibleWorkspaces.any { it.first == selectedKey } }
                ?: visibleWorkspaces.firstOrNull()?.first
            if (visibleWorkspaces.isEmpty()) {
                TransferSection(
                    modifier = Modifier.fillMaxSize(),
                    servers = servers,
                    credentials = credentials,
                    knownHosts = knownHosts,
                    bookmarks = bookmarks,
                    transfers = transfers,
                    initialServerId = initialServerId,
                    onServerClick = onServerClick,
                    onTerminalClick = onTerminalClick,
                    onTrustHost = onTrustHost,
                    onTransferChanged = onTransferChanged,
                    onSftpBookmarkChanged = onSftpBookmarkChanged,
                    onSftpBookmarkDeleted = onSftpBookmarkDeleted,
                    onSftpConnected = onSftpConnected,
                    runtime = sftpRuntimes["files"],
                    onSftpWorkspaceFailed = onSftpWorkspaceFailed,
                    transport = sshTransport,
                    defaultSortMode = sftpDefaultSortMode,
                    defaultSortDescending = sftpDefaultSortDescending,
                    showHiddenByDefault = sftpShowHiddenByDefault,
                    workspaceStateKey = "files",
                    fullPage = true,
                    active = true,
                    externalBackRequestSerial = backRequestSerial,
                    onExternalBackFallback = onBack
                )
            }
            visibleWorkspaces.forEach { (workspaceKey, workspaceServer) ->
                val active = workspaceKey == activeWorkspaceKey
                TransferSection(
                    modifier = if (active) Modifier.fillMaxSize() else Modifier.size(0.dp),
                    servers = servers,
                    credentials = credentials,
                    knownHosts = knownHosts,
                    bookmarks = bookmarks,
                    transfers = transfers,
                    initialServerId = workspaceServer?.id ?: initialServerId,
                    onServerClick = onServerClick,
                    onTerminalClick = onTerminalClick,
                    onTrustHost = onTrustHost,
                    onTransferChanged = onTransferChanged,
                    onSftpBookmarkChanged = onSftpBookmarkChanged,
                    onSftpBookmarkDeleted = onSftpBookmarkDeleted,
                    onSftpConnected = onSftpConnected,
                    runtime = sftpRuntimes[workspaceKey],
                    onSftpWorkspaceFailed = onSftpWorkspaceFailed,
                    transport = sshTransport,
                    defaultSortMode = sftpDefaultSortMode,
                    defaultSortDescending = sftpDefaultSortDescending,
                    showHiddenByDefault = sftpShowHiddenByDefault,
                    workspaceStateKey = workspaceKey,
                    fullPage = true,
                    active = active,
                    externalBackRequestSerial = if (active) backRequestSerial else 0,
                    onExternalBackFallback = onBack
                )
            }
        }
    }
}

@Composable
private fun BackupSection(backupPreview: String) {
    var expanded by remember { mutableStateOf(false) }
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(20.dp)) {
        Text("Stats", color = DeckColors.PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(10.dp))
        VaultInfoRow("Included", "Hosts, keys list, known hosts, snippets, tunnels and settings", DeckColors.Cyan)
        VaultInfoRow("Protected", "Passwords and private keys stay on this device", DeckColors.Orange)
        VaultInfoRow("Review", "Check the contents before export", DeckColors.Green)
        Spacer(Modifier.height(14.dp))
        HostAction(if (expanded) "Hide" else "Review") { expanded = !expanded }
        if (expanded) {
            Spacer(Modifier.height(14.dp))
            Text(
                backupPreview.ifBlank { "No exportable records." },
                color = DeckColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = 18,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HostReadinessSection(
    servers: List<ServerProfile>,
    credentials: List<Credential>,
    knownHosts: List<KnownHost>,
    forwards: List<PortForwardRule>,
    terminalSessions: List<TerminalSessionRecord>,
    onServerClick: (ServerProfile) -> Unit,
    onTerminalClick: (ServerProfile) -> Unit,
    onTrustHost: (ServerProfile) -> Unit,
    onMoveServer: (String, Int) -> Unit,
    onCopyHostLink: (ServerProfile) -> Unit,
    onShareHostLink: (ServerProfile) -> Unit,
    onShowHostQr: (ServerProfile) -> Unit,
    onOpenFiles: (ServerProfile) -> Unit
) {
    var reordering by remember { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HostAction(if (reordering) "Done" else "Reorder hosts") { reordering = !reordering }
    }
    Spacer(Modifier.height(12.dp))
    servers.forEachIndexed { index, server ->
        val credential = server.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }
        val knownHost = knownHosts.firstOrNull { it.host == server.host && it.port == server.port }
        val liveSessions = terminalSessions.count { it.serverId == server.id && it.status.name in listOf("Online", "Connecting") }
        if (reordering) {
            HostReorderControls(
                server = server,
                canMoveUp = index > 0,
                canMoveDown = index < servers.lastIndex,
                onMoveServer = onMoveServer
            )
            Spacer(Modifier.height(8.dp))
        }
        HostVaultCard(server, credential, knownHost, liveSessions, onServerClick, onTerminalClick, onTrustHost, onCopyHostLink, onShareHostLink, onShowHostQr, onOpenFiles)
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun HostReorderControls(
    server: ServerProfile,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveServer: (String, Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(server.name, color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        HostAction("Up") { if (canMoveUp) onMoveServer(server.id, -1) }
        HostAction("Down") { if (canMoveDown) onMoveServer(server.id, 1) }
    }
}

internal fun hostSavedForwardCount(serverId: String, forwards: List<PortForwardRule>): Int {
    return forwards.count { it.serverId == serverId }
}

internal data class HostFilesLaunch(
    val section: String,
    val serverId: String
)

internal fun hostFilesLaunch(serverId: String): HostFilesLaunch {
    return HostFilesLaunch("Files", serverId)
}

internal fun fileCopyDestinationReady(
    server: ServerProfile,
    credential: Credential?,
    trusted: Boolean
): Boolean {
    return when (server.protocol) {
        ConnectionProtocol.Smb -> credential?.secretBacked == true && credential.type == CredentialType.Password
        ConnectionProtocol.Rclone -> true
        else -> credential?.secretBacked == true && trusted
    }
}

internal data class SftpOpenFailureRecovery(
    val path: String,
    val entries: List<SftpEntry>
)

internal fun sftpOpenFailureRecovery(
    previousPath: String,
    previousEntries: List<SftpEntry>,
    requestedPath: String
): SftpOpenFailureRecovery {
    return if (previousPath.isNotBlank()) {
        SftpOpenFailureRecovery(previousPath, previousEntries)
    } else {
        SftpOpenFailureRecovery(requestedPath, emptyList())
    }
}

internal fun sftpTextEditNeedsDiscardConfirmation(original: String, current: String): Boolean {
    return original != current
}

internal fun scpTransferFailureMessage(direction: TransferDirection, error: Throwable, path: String): String {
    val action = if (direction == TransferDirection.Upload) "upload to" else "download"
    return "SCP ${direction.name.lowercase()} failed: ${SftpErrorMapper.message(action, path, error)}"
}

internal fun transferCancelledStatus(protocol: String, direction: TransferDirection, displayName: String): String {
    val action = if (direction == TransferDirection.Upload) "Upload" else "Download"
    return "${protocol.trim().ifBlank { "SFTP" }} $action cancelled: ${displayName.ifBlank { "file" }}"
}

internal fun sftpCompactBreadcrumbSegments(
    segments: List<Pair<String, String>>,
    maxVisible: Int = 4
): List<Pair<String, String>> {
    val limit = maxVisible.coerceAtLeast(3)
    if (segments.size <= limit) return segments
    val tailCount = limit - 2
    return listOf(segments.first(), "..." to segments[segments.size - tailCount - 1].second) + segments.takeLast(tailCount)
}

internal fun sftpHostListExpanded(selectedServerId: String?, hostsExpanded: Boolean): Boolean {
    return selectedServerId == null || hostsExpanded
}

@Composable
private fun HostVaultCard(
    server: ServerProfile,
    credential: Credential?,
    knownHost: KnownHost?,
    liveSessions: Int,
    onServerClick: (ServerProfile) -> Unit,
    onTerminalClick: (ServerProfile) -> Unit,
    onTrustHost: (ServerProfile) -> Unit,
    onCopyHostLink: (ServerProfile) -> Unit,
    onShareHostLink: (ServerProfile) -> Unit,
    onShowHostQr: (ServerProfile) -> Unit,
    onOpenFiles: (ServerProfile) -> Unit
) {
    var expanded by remember(server.id) { mutableStateOf(false) }
    DeckCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        radius = 18.dp,
        padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultHostVisual(server.osName, Modifier.size(30.dp))
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, color = DeckColors.PrimaryText, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (liveSessions > 0) {
                    Text("$liveSessions active session${if (liveSessions == 1) "" else "s"}", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SftpToolbarGlyph(if (expanded) "chevron-up" else "chevron-down", DeckColors.SecondaryText, Modifier.size(18.dp))
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            VaultInfoRow("Identity", credential?.let { "${it.type.label()} · ${it.label}" } ?: "Not linked", if (credential?.secretBacked == true) DeckColors.Green else DeckColors.Orange)
            VaultInfoRow("Host key", knownHost?.fingerprint ?: "Not trusted yet", if (knownHost?.trusted == true) DeckColors.Green else DeckColors.Orange)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HostAction("Copy Link") { onCopyHostLink(server) }
                HostAction("QR") { onShowHostQr(server) }
                HostAction("Edit") { onServerClick(server) }
            }
        }
    }
}

private data class ShareQrPayload(
    val title: String,
    val subtitle: String,
    val link: String,
    val safeId: String
)

private fun hostSharePayload(server: ServerProfile): ShareQrPayload {
    return ShareQrPayload(
        title = server.name,
        subtitle = "Host profile",
        link = HostShareLinkCodec.encode(server),
        safeId = server.id
    )
}

private fun snippetSharePayload(snippet: Snippet): ShareQrPayload {
    return ShareQrPayload(
        title = snippet.name.ifBlank { "Snippet" },
        subtitle = "Command snippet",
        link = HostShareLinkCodec.encode(snippet),
        safeId = snippet.id
    )
}

private fun credentialSharePayload(credential: Credential): ShareQrPayload {
    return ShareQrPayload(
        title = credential.label.ifBlank { "Identity" },
        subtitle = "${credential.type.label()} metadata",
        link = HostShareLinkCodec.encode(credential),
        safeId = credential.id
    )
}

private fun forwardSharePayload(forward: PortForwardRule, servers: List<ServerProfile>): ShareQrPayload {
    val serverName = servers.firstOrNull { it.id == forward.serverId }?.name ?: forward.serverId
    return ShareQrPayload(
        title = serverName.ifBlank { "Tunnel" },
        subtitle = forward.type.displayName() + " tunnel",
        link = HostShareLinkCodec.encode(forward),
        safeId = forward.id
    )
}

private fun shareTextLink(context: Context, subject: String, link: String) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(share, "Share $subject"))
}

private fun shareQrImage(context: Context, payload: ShareQrPayload): Result<Unit> = runCatching {
    val matrix = HostShareQrCodec.encode(payload.link, size = 512) ?: error("Could not render QR.")
    val bitmap = hostShareQrBitmap(matrix)
    val shareDir = File(context.cacheDir, "host-qr-shares").apply { mkdirs() }
    val safeId = payload.safeId.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "share" }
    val shareFile = File(shareDir, "$safeId-${System.currentTimeMillis()}.png")
    shareFile.outputStream().use {
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "Could not write QR image." }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", shareFile)
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_SUBJECT, payload.title)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(share, "Share QR for ${payload.title}"))
}

@Composable
private fun ShareQrDialog(
    payload: ShareQrPayload,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onShareQr: () -> Unit
) {
    val link = payload.link
    val matrix = remember(link) { HostShareQrCodec.encode(link, size = 256) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share QR", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(payload.title, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(payload.subtitle, color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                matrix?.let { HostShareQrCanvas(it) }
                    ?: Text("Could not render this link.", color = DeckColors.SecondaryText, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(link, color = DeckColors.SecondaryText, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        },
        confirmButton = {
            TextButton(onClick = onShareQr) { Text("Share QR") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCopy) { Text("Copy") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.PrimaryText
    )
}

private fun hostShareQrBitmap(matrix: HostShareQrMatrix): Bitmap {
    return Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888).apply {
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                setPixel(x, y, if (matrix.dark(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
            }
        }
    }
}

@Composable
private fun HostShareQrCanvas(matrix: HostShareQrMatrix) {
    Canvas(
        modifier = Modifier
            .size(256.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        val cellWidth = size.width / matrix.width
        val cellHeight = size.height / matrix.height
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.dark(x, y)) {
                    drawRect(
                        color = Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(x * cellWidth, y * cellHeight),
                        size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultInfoRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Spacer(Modifier.width(9.dp))
        Text(label, color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(88.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, color = DeckColors.PrimaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SftpWorkspaceTab(
    server: ServerProfile,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val borderColor = if (selected) DeckColors.CardStroke else DeckColors.Divider.copy(alpha = 0.35f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable(onClick = onSelect)
            .padding(start = 13.dp, end = 7.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            ChronoOsLogo(server.osName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Text(
            server.name,
            color = if (selected) DeckColors.PrimaryText else DeckColors.SecondaryText,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(DeckColors.SurfaceMuted)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Text("x", color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TransferSection(
    servers: List<ServerProfile>,
    credentials: List<Credential>,
    knownHosts: List<KnownHost>,
    bookmarks: List<SftpBookmark>,
    transfers: List<TransferRecord>,
    initialServerId: String?,
    onServerClick: (ServerProfile) -> Unit,
    onTerminalClick: (ServerProfile) -> Unit,
    onTrustHost: (ServerProfile) -> Unit,
    onTransferChanged: (TransferRecord) -> Unit,
    onSftpBookmarkChanged: (SftpBookmark) -> Unit,
    onSftpBookmarkDeleted: (SftpBookmark) -> Unit,
    onSftpConnected: (ServerProfile) -> Unit,
    runtime: SftpWorkspaceRuntime? = null,
    onSftpWorkspaceFailed: (String) -> Unit = {},
    transport: SshTransport,
    defaultSortMode: SftpSortMode = SftpSortMode.Name,
    defaultSortDescending: Boolean = false,
    showHiddenByDefault: Boolean = false,
    modifier: Modifier = Modifier,
    workspaceStateKey: String? = null,
    fullPage: Boolean = false,
    active: Boolean = true,
    externalBackRequestSerial: Int = 0,
    onExternalBackFallback: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val browserStateKey = workspaceStateKey ?: "files"
    var selectedServerId by remember(servers, initialServerId, browserStateKey) {
        mutableStateOf(initialServerId?.takeIf { id -> servers.any { it.id == id } })
    }
    var currentPath by remember(browserStateKey, selectedServerId) {
        val server = servers.firstOrNull { it.id == selectedServerId }
        mutableStateOf(server?.let { SftpPathResolver.defaultStartPath(it, bookmarks) }.orEmpty())
    }
    var allEntries by remember(browserStateKey, selectedServerId) { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var entries by remember(browserStateKey, selectedServerId) { mutableStateOf<List<SftpEntry>>(emptyList()) }
    var browserStatus by remember(browserStateKey, selectedServerId) { mutableStateOf(if (selectedServerId == null) "Select a host" else "SFTP closed") }
    val localRuntime = remember(browserStateKey, selectedServerId) { SftpWorkspaceRuntime() }
    val activeRuntime = runtime ?: localRuntime
    var lastLoadedPath by remember(browserStateKey, selectedServerId) { mutableStateOf("") }
    var pathBackStack by remember(browserStateKey, selectedServerId) { mutableStateOf<List<String>>(emptyList()) }
    var activeOperation by remember(browserStateKey, selectedServerId) { mutableStateOf<String?>(null) }
    var sortMode by remember(browserStateKey, selectedServerId, defaultSortMode) { mutableStateOf(defaultSortMode) }
    var sortDescending by remember(browserStateKey, selectedServerId, defaultSortDescending) { mutableStateOf(defaultSortDescending) }
    var showHiddenFiles by remember(browserStateKey, selectedServerId, showHiddenByDefault) { mutableStateOf(showHiddenByDefault) }
    var filterQuery by remember(browserStateKey, selectedServerId) { mutableStateOf("") }
    var hostsExpanded by remember(browserStateKey, selectedServerId) { mutableStateOf(false) }
    var pendingUpload by remember { mutableStateOf<PendingSftpUpload?>(null) }
    var pendingDownload by remember { mutableStateOf<PendingSftpDownload?>(null) }
    var pendingHostTransfer by remember { mutableStateOf<PendingSftpHostTransfer?>(null) }
    var pendingHostTransferPassphrase by remember { mutableStateOf<PendingSftpHostTransferPassphrase?>(null) }
    var pendingScpUpload by remember { mutableStateOf<PendingScpTransfer?>(null) }
    var pendingScpDownload by remember { mutableStateOf<PendingScpTransfer?>(null) }
    var pendingScpPathAction by remember { mutableStateOf<PendingScpPathAction?>(null) }
    var pendingFileAction by remember { mutableStateOf<PendingSftpAction?>(null) }
    var pendingTextEdit by remember { mutableStateOf<PendingSftpTextEdit?>(null) }
    var textEditContent by remember { mutableStateOf("") }
    var textEditOriginalContent by remember { mutableStateOf("") }
    var confirmTextEditDiscard by remember { mutableStateOf(false) }
    var pendingBookmarkAction by remember { mutableStateOf<PendingSftpBookmarkAction?>(null) }
    var pendingPassphrase by remember { mutableStateOf<PendingSftpPassphrase?>(null) }
    var sftpPassphrase by remember { mutableStateOf("") }
    var hostTransferDestinationPath by remember { mutableStateOf("") }
    var scpPathText by remember { mutableStateOf("") }
    var actionText by remember { mutableStateOf("") }
    var bookmarkText by remember { mutableStateOf("") }

    fun upsertTransfer(
        id: String,
        server: ServerProfile,
        entry: SftpEntry,
        direction: TransferDirection,
        progress: Float,
        state: TransferRecordState,
        message: String
    ) {
        onTransferChanged(
            TransferRecord(
                id = id,
                serverId = server.id,
                direction = direction,
                remotePath = entry.path,
                localDisplayName = entry.name,
                progress = progress,
                state = state,
                message = message,
                updatedAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    fun displayNameFor(uri: Uri): String {
        val displayName = runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
            ?: uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null }
            ?: "upload-${System.currentTimeMillis()}"
        return SftpPathResolver.leafName(displayName)
    }

    fun sizeFor(uri: Uri): Long {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
        }.getOrNull()?.takeIf { it > 0L } ?: 0L
    }

    fun sftpErrorMessage(action: String, error: Exception, path: String = currentPath.ifBlank { "." }): String {
        return SftpErrorMapper.message(action.lowercase(), path.ifBlank { "." }, error)
    }

    fun transferFailureMessage(direction: TransferDirection, error: Exception, path: String): String {
        val mapped = sftpErrorMessage(direction.name, error, path)
        return when (direction) {
            TransferDirection.Upload -> "$mapped Choose the local file again; Android grants picker access only for this transfer."
            TransferDirection.Download -> "$mapped Choose a destination again to save another copy."
        }
    }

    fun shouldDropSftpClient(error: Throwable): Boolean {
        return SftpClientHealth.shouldDropClient(error)
    }

    fun transferId(server: ServerProfile, entry: SftpEntry, direction: TransferDirection): String {
        return "transfer-${server.id}-${entry.path.hashCode()}-${direction.name}-${System.currentTimeMillis()}"
    }

    fun scpTransferId(server: ServerProfile, remotePath: String, direction: TransferDirection): String {
        return "scp-${server.id}-${remotePath.hashCode()}-${direction.name}-${System.currentTimeMillis()}"
    }

    fun scpEntry(remotePath: String, localDisplayName: String): SftpEntry {
        return SftpEntry(
            name = ScpTransferPolicy.safeDisplayName(localDisplayName, remotePath),
            path = remotePath,
            directory = false,
            sizeBytes = 0,
            modifiedEpochMillis = System.currentTimeMillis()
        )
    }

    fun credentialFor(server: ServerProfile): Credential? {
        return server.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }
    }

    fun trustedFor(server: ServerProfile): Boolean {
        if (server.protocol == ConnectionProtocol.Smb) return true
        if (server.protocol == ConnectionProtocol.Rclone) return true
        return knownHosts.any { it.host == server.host && it.port == server.port && it.trusted }
    }

    fun fileCopyDestinationReady(server: ServerProfile): Boolean {
        return fileCopyDestinationReady(server, credentialFor(server), trustedFor(server))
    }

    fun canStartScp(server: ServerProfile): Boolean {
        val credential = credentialFor(server)
        return when {
            credential?.secretBacked != true -> {
                browserStatus = "Add a saved password or private key to ${server.name}, then start SCP."
                false
            }
            !trustedFor(server) -> {
                browserStatus = "Review and trust ${server.name}'s host key before SCP."
                false
            }
            else -> true
        }
    }

    fun clearPendingSftpUi() {
        pendingUpload = null
        pendingDownload = null
        pendingHostTransfer = null
        pendingHostTransferPassphrase = null
        pendingScpUpload = null
        pendingScpDownload = null
        pendingScpPathAction = null
        pendingFileAction = null
        pendingTextEdit = null
        textEditContent = ""
        textEditOriginalContent = ""
        confirmTextEditDiscard = false
        pendingBookmarkAction = null
        pendingPassphrase = null
        sftpPassphrase = ""
        hostTransferDestinationPath = ""
        scpPathText = ""
        actionText = ""
        bookmarkText = ""
    }

    fun sortedEntries(list: List<SftpEntry>): List<SftpEntry> {
        return SftpPathResolver.sortForFileManager(
            SftpPathResolver.visibleForFileManager(list, showHiddenFiles),
            sortMode,
            sortDescending
        )
    }

    fun displayedEntries(): List<SftpEntry> {
        return SftpPathResolver.filterForFileManager(entries, filterQuery)
    }

    fun activeClientFor(server: ServerProfile, action: String): SftpClient? {
        val client = activeRuntime.client
        if (client == null || activeRuntime.clientServerId != server.id) {
            browserStatus = SftpBrowserOperationPolicy.missingClientMessage(server.name, action)
            return null
        }
        return client
    }

    fun dropActiveSftpClientIfNeeded(error: Throwable) {
        if (!shouldDropSftpClient(error)) return
        val failedClient = activeRuntime.client
        activeRuntime.client = null
        activeRuntime.clientServerId = null
        scope.launch { runCatching { failedClient?.close() } }
    }

    fun copyRemotePath(entry: SftpEntry) {
        clipboard.setText(AnnotatedString(entry.path))
        browserStatus = "Copied ${entry.path}"
    }

    fun requestCopyToHost(server: ServerProfile, entry: SftpEntry) {
        val rejection = SftpHostTransferPolicy.unavailableReason(entry)
        if (rejection != null) {
            browserStatus = rejection
            return
        }
        if (activeClientFor(server, "copying") == null) return
        val candidates = servers
            .filter { it.id != server.id && fileCopyDestinationReady(it) }
            .sortedBy { it.name.lowercase() }
        if (candidates.isEmpty()) {
            browserStatus = "Add another ready file destination before copying files between hosts."
            return
        }
        val destination = candidates.first()
        pendingHostTransfer = PendingSftpHostTransfer(
            sourceServer = server,
            sourceEntry = entry,
            destinationServerId = destination.id
        )
        hostTransferDestinationPath = SftpPathResolver.defaultStartPath(destination, bookmarks)
        browserStatus = "Choose a destination host for ${entry.name}"
    }

    fun startHostTransfer(
        transfer: PendingSftpHostTransfer,
        destinationServer: ServerProfile,
        destinationDirectory: String,
        destinationPassphrase: String? = null
    ) {
        val sourceClient = activeClientFor(transfer.sourceServer, "copying") ?: return
        val destinationCredential = credentialFor(destinationServer)
        if (!fileCopyDestinationReady(destinationServer)) {
            browserStatus = when (destinationServer.protocol) {
                ConnectionProtocol.Smb -> "Add a saved SMB password to ${destinationServer.name} before copying."
                ConnectionProtocol.Rclone -> "Finish configuring ${destinationServer.name} before copying."
                else -> "Trust ${destinationServer.name} and save a credential before copying."
            }
            return
        }
        val targetPath = SftpHostTransferPolicy.targetPath(destinationDirectory, transfer.sourceEntry)
        val targetEntry = transfer.sourceEntry.copy(path = targetPath)
        val id = "sftp-copy-${transfer.sourceServer.id}-${destinationServer.id}-${transfer.sourceEntry.path.hashCode()}-${System.currentTimeMillis()}"
        pendingHostTransfer = null
        hostTransferDestinationPath = ""
        browserStatus = "Copying ${transfer.sourceEntry.name} to ${destinationServer.name} via this device"
        activeOperation = "Copying ${transfer.sourceEntry.name} to ${destinationServer.name}"
        upsertTransfer(id, destinationServer, targetEntry, TransferDirection.Upload, 0f, TransferRecordState.Running, "Copy via this device started")
        val job = scope.launch {
            var destinationClient: SftpClient? = null
            val copyRoot = File(context.cacheDir, "sftp-host-copy-${UUID.randomUUID()}").apply { mkdirs() }
            var copiedFiles = 0
            try {
                destinationClient = transport.openSftp(destinationServer, destinationCredential, destinationPassphrase)

                suspend fun copyFile(source: SftpEntry, destinationPath: String, label: String) {
                    copiedFiles += 1
                    check(copiedFiles <= 400) { "Folder copy is limited to 400 files." } // ponytail: replace with queued sync when folder transfers need bulk jobs.
                    val cacheFile = File(copyRoot, "${copiedFiles}-${SftpPathResolver.leafName(source.name.ifBlank { "file" })}")
                    cacheFile.outputStream().use { output ->
                        sourceClient.downloadTo(source.path, source.name, output) { progress ->
                            upsertTransfer(
                                id,
                                destinationServer,
                                targetEntry,
                                TransferDirection.Upload,
                                (progress * 0.5f).coerceIn(0f, 0.49f),
                                TransferRecordState.Running,
                                "$label downloading ${(progress * 100).toInt().coerceIn(1, 99)}%"
                            )
                        }
                    }
                    cacheFile.inputStream().use { input ->
                        destinationClient.uploadFrom(source.name, destinationPath, cacheFile.length(), input) { progress ->
                            upsertTransfer(
                                id,
                                destinationServer,
                                targetEntry,
                                TransferDirection.Upload,
                                (0.5f + progress * 0.5f).coerceIn(0.5f, 0.99f),
                                TransferRecordState.Running,
                                "$label uploading ${(progress * 100).toInt().coerceIn(1, 99)}%"
                            )
                        }
                    }
                    runCatching { cacheFile.delete() }
                }

                suspend fun copyTree(source: SftpEntry, destinationPath: String, depth: Int = 0) {
                    check(depth <= 16) { "Folder copy is limited to 16 levels." }
                    if (!source.directory && source.type != SftpEntryType.Directory) {
                        copyFile(source, destinationPath, "Copy ${source.name}")
                        return
                    }
                    val existingDestination = runCatching { destinationClient.list(destinationPath) }.isSuccess
                    check(!existingDestination) { SftpHostTransferPolicy.destinationFolderExistsMessage(destinationPath) }
                    destinationClient.mkdir(destinationPath)
                    val listedChildren = sourceClient.list(source.path)
                    val unsupportedChildren = SftpHostTransferPolicy.unsupportedChildNames(listedChildren)
                    check(unsupportedChildren.isEmpty()) {
                        "Folder copy skipped unsupported entries: ${unsupportedChildren.take(5).joinToString(", ")}"
                    }
                    val children = listedChildren.filter { it.name != "." && it.name != ".." }
                    for (child in children) {
                        copyTree(child, SftpPathResolver.join(destinationPath, child.name), depth + 1)
                        upsertTransfer(
                            id,
                            destinationServer,
                            targetEntry,
                            TransferDirection.Upload,
                            0.5f,
                            TransferRecordState.Running,
                            "Copied $copiedFiles files from ${transfer.sourceEntry.name}"
                        )
                    }
                }

                copyTree(transfer.sourceEntry, targetPath)
                upsertTransfer(id, destinationServer, targetEntry, TransferDirection.Upload, 1f, TransferRecordState.Complete, "Copied via this device")
                browserStatus = "Copied ${transfer.sourceEntry.name} to ${destinationServer.name}"
            } catch (_: CancellationException) {
                val status = "Copy cancelled: ${transfer.sourceEntry.name}"
                upsertTransfer(id, destinationServer, targetEntry, TransferDirection.Upload, 0f, TransferRecordState.Cancelled, status)
                browserStatus = status
            } catch (error: Exception) {
                if (error.requiresSftpPrivateKeyPassphrase()) {
                    pendingHostTransferPassphrase = PendingSftpHostTransferPassphrase(transfer, destinationServer.id, destinationDirectory)
                    sftpPassphrase = ""
                    upsertTransfer(id, destinationServer, targetEntry, TransferDirection.Upload, 0f, TransferRecordState.Failed, "Destination key passphrase required")
                    browserStatus = "Private-key passphrase required for ${destinationServer.name}."
                    return@launch
                }
                upsertTransfer(id, destinationServer, targetEntry, TransferDirection.Upload, 0f, TransferRecordState.Failed, "Copy failed: ${error.message ?: error::class.java.simpleName}")
                dropActiveSftpClientIfNeeded(error)
                browserStatus = "Copy failed: ${error.message ?: error::class.java.simpleName}"
            } finally {
                runCatching { destinationClient?.close() }
                runCatching { copyRoot.deleteRecursively() }
                TransferCancellationRegistry.unregister(id)
                activeOperation = null
            }
        }
        TransferCancellationRegistry.register(id, job)
    }

    suspend fun refreshCurrentDirectoryAfterOperation(
        client: SftpClient,
        successMessage: String
    ) {
        runCatching {
            allEntries = client.list(currentPath)
            entries = sortedEntries(allEntries)
        }.onSuccess {
            browserStatus = successMessage
        }.onFailure { refreshError ->
            if (shouldDropSftpClient(refreshError)) {
                val failedClient = activeRuntime.client
                activeRuntime.client = null
                activeRuntime.clientServerId = null
                runCatching { failedClient?.close() }
            }
            browserStatus = SftpBrowserOperationPolicy.refreshFailureStatus(successMessage, refreshError)
        }
    }

    val downloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val download = pendingDownload
        pendingDownload = null
        if (uri == null || download == null) return@rememberLauncherForActivityResult
        val client = activeClientFor(download.server, "downloading")
        if (client == null) {
            upsertTransfer(
                transferId(download.server, download.entry, TransferDirection.Download),
                download.server,
                download.entry,
                TransferDirection.Download,
                0f,
                TransferRecordState.Failed,
                "SFTP session is no longer connected to ${download.server.name}. Reconnect, then choose the download destination again."
            )
            return@rememberLauncherForActivityResult
        }
        browserStatus = "Downloading ${download.entry.name}"
        activeOperation = "Downloading ${download.entry.name}"
        val id = transferId(download.server, download.entry, TransferDirection.Download)
        upsertTransfer(id, download.server, download.entry, TransferDirection.Download, 0f, TransferRecordState.Running, "Download started")
        val job = scope.launch {
            try {
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    client.downloadTo(download.entry.path, download.entry.name, output) { progress ->
                        upsertTransfer(
                            id,
                            download.server,
                            download.entry,
                            TransferDirection.Download,
                            progress,
                            TransferRecordState.Running,
                            "Downloading ${(progress * 100).toInt().coerceIn(1, 99)}%"
                        )
                    }
                } ?: error("Unable to open selected destination.")
                upsertTransfer(id, download.server, download.entry, TransferDirection.Download, 1f, TransferRecordState.Complete, "Download saved")
                browserStatus = "Saved ${download.entry.name}"
            } catch (_: CancellationException) {
                val status = transferCancelledStatus("SFTP", TransferDirection.Download, download.entry.name)
                upsertTransfer(id, download.server, download.entry, TransferDirection.Download, 0f, TransferRecordState.Cancelled, status)
                browserStatus = status
            } catch (error: Exception) {
                upsertTransfer(id, download.server, download.entry, TransferDirection.Download, 0f, TransferRecordState.Failed, transferFailureMessage(TransferDirection.Download, error, download.entry.path))
                dropActiveSftpClientIfNeeded(error)
                browserStatus = sftpErrorMessage("Download", error, download.entry.path)
            } finally {
                TransferCancellationRegistry.unregister(id)
                activeOperation = null
            }
        }
        TransferCancellationRegistry.register(id, job)
    }

    fun requestDownload(server: ServerProfile, entry: SftpEntry) {
        if (activeClientFor(server, "downloading") == null) {
            val id = transferId(server, entry, TransferDirection.Download)
            upsertTransfer(
                id,
                server,
                entry,
                TransferDirection.Download,
                0f,
                TransferRecordState.Failed,
                "Reconnect SFTP to ${server.name}, then choose the download destination again."
            )
            return
        }
        pendingDownload = PendingSftpDownload(server, entry)
        downloadPicker.launch(entry.name.ifBlank { "download" })
    }

    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val upload = pendingUpload
        pendingUpload = null
        if (uri == null || upload == null) return@rememberLauncherForActivityResult
        val displayName = displayNameFor(uri)
        val remotePath = SftpPathResolver.join(upload.remoteDirectory, displayName)
        val transferEntry = SftpEntry(displayName, remotePath, false, 0, System.currentTimeMillis())
        val id = transferId(upload.server, transferEntry, TransferDirection.Upload)
        val client = activeClientFor(upload.server, "uploading")
        if (client == null) {
            upsertTransfer(
                id,
                upload.server,
                transferEntry,
                TransferDirection.Upload,
                0f,
                TransferRecordState.Failed,
                "SFTP session is no longer connected to ${upload.server.name}. Reconnect, then choose the local file again."
            )
            return@rememberLauncherForActivityResult
        }
        browserStatus = "Uploading $displayName"
        activeOperation = "Uploading $displayName"
        upsertTransfer(id, upload.server, transferEntry, TransferDirection.Upload, 0f, TransferRecordState.Running, "Upload started")
        val job = scope.launch {
            try {
                val totalBytes = sizeFor(uri)
                val handle = context.contentResolver.openInputStream(uri)?.use { input ->
                    client.uploadFrom(displayName, remotePath, totalBytes, input) { progress ->
                        upsertTransfer(
                            id,
                            upload.server,
                            transferEntry,
                            TransferDirection.Upload,
                            progress,
                            TransferRecordState.Running,
                            "Uploading ${(progress * 100).toInt().coerceIn(1, 99)}%"
                        )
                    }
                } ?: error("Unable to open selected file.")
                upsertTransfer(id, upload.server, transferEntry, TransferDirection.Upload, handle.progress, TransferRecordState.Complete, "Upload complete")
                refreshCurrentDirectoryAfterOperation(client, "Upload complete: $displayName")
            } catch (_: CancellationException) {
                val status = transferCancelledStatus("SFTP", TransferDirection.Upload, displayName)
                upsertTransfer(id, upload.server, transferEntry, TransferDirection.Upload, 0f, TransferRecordState.Cancelled, status)
                browserStatus = status
            } catch (error: Exception) {
                upsertTransfer(id, upload.server, transferEntry, TransferDirection.Upload, 0f, TransferRecordState.Failed, transferFailureMessage(TransferDirection.Upload, error, remotePath))
                dropActiveSftpClientIfNeeded(error)
                browserStatus = sftpErrorMessage("Upload", error, remotePath)
            } finally {
                TransferCancellationRegistry.unregister(id)
                activeOperation = null
            }
        }
        TransferCancellationRegistry.register(id, job)
    }

    fun openTextFile(server: ServerProfile, entry: SftpEntry) {
        val rejection = SftpTextFilePolicy.rejectionReason(entry)
        if (rejection != null) {
            browserStatus = rejection
            return
        }
        val client = activeClientFor(server, "opening text") ?: return
        browserStatus = "Opening ${entry.name}"
        activeOperation = "Opening ${entry.name}"
        scope.launch {
            try {
                val buffer = ByteArrayOutputStream()
                client.downloadTo(entry.path, entry.name, buffer)
                val bytes = buffer.toByteArray()
                if (bytes.size.toLong() > SftpTextFilePolicy.MaxEditableBytes) {
                    browserStatus = "Text editor supports files up to 256 K."
                    return@launch
                }
                val decoded = SftpTextFilePolicy.decodeEditableText(bytes).getOrElse { rejection ->
                    browserStatus = rejection.message ?: "Download this file; it cannot be opened as text."
                    return@launch
                }
                textEditContent = decoded
                textEditOriginalContent = decoded
                confirmTextEditDiscard = false
                pendingTextEdit = PendingSftpTextEdit(server, entry)
                browserStatus = "Editing ${entry.name}"
            } catch (error: Exception) {
                dropActiveSftpClientIfNeeded(error)
                browserStatus = sftpErrorMessage("Download", error, entry.path)
            } finally {
                activeOperation = null
            }
        }
    }

    fun saveTextFile(edit: PendingSftpTextEdit, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        if (bytes.size.toLong() > SftpTextFilePolicy.MaxEditableBytes) {
            browserStatus = "Text editor supports files up to 256 K."
            return
        }
        val client = activeClientFor(edit.server, "saving text") ?: return
        pendingTextEdit = null
        textEditContent = ""
        textEditOriginalContent = ""
        confirmTextEditDiscard = false
        browserStatus = "Saving ${edit.entry.name}"
        activeOperation = "Saving ${edit.entry.name}"
        scope.launch {
            try {
                client.uploadFrom(
                    localDisplayName = edit.entry.name,
                    remotePath = edit.entry.path,
                    totalBytes = bytes.size.toLong(),
                    input = ByteArrayInputStream(bytes)
                )
                refreshCurrentDirectoryAfterOperation(client, "Saved ${edit.entry.name}")
            } catch (error: Exception) {
                dropActiveSftpClientIfNeeded(error)
                browserStatus = sftpErrorMessage("Upload", error, edit.entry.path)
            } finally {
                activeOperation = null
            }
        }
    }

    val scpDownloadPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val download = pendingScpDownload
        pendingScpDownload = null
        if (uri == null || download == null) return@rememberLauncherForActivityResult
        val entry = scpEntry(download.remotePath, download.localDisplayName)
        val id = scpTransferId(download.server, download.remotePath, TransferDirection.Download)
        browserStatus = "SCP downloading ${entry.name}"
        activeOperation = "SCP downloading ${entry.name}"
        upsertTransfer(id, download.server, entry, TransferDirection.Download, 0f, TransferRecordState.Running, "SCP download started")
        val job = scope.launch {
            var client: ScpClient? = null
            try {
                val opened = transport.openScp(download.server, credentialFor(download.server))
                client = opened
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    opened.downloadTo(download.remotePath, entry.name, output) { progress ->
                        upsertTransfer(
                            id,
                            download.server,
                            entry,
                            TransferDirection.Download,
                            progress,
                            TransferRecordState.Running,
                            "SCP downloading ${(progress * 100).toInt().coerceIn(1, 99)}%"
                        )
                    }
                } ?: error("Unable to open selected destination.")
                upsertTransfer(id, download.server, entry, TransferDirection.Download, 1f, TransferRecordState.Complete, "SCP download saved")
                browserStatus = "SCP saved ${entry.name}"
            } catch (_: CancellationException) {
                val status = transferCancelledStatus("SCP", TransferDirection.Download, entry.name)
                upsertTransfer(id, download.server, entry, TransferDirection.Download, 0f, TransferRecordState.Cancelled, status)
                browserStatus = status
            } catch (error: Exception) {
                val status = scpTransferFailureMessage(TransferDirection.Download, error, download.remotePath)
                upsertTransfer(id, download.server, entry, TransferDirection.Download, 0f, TransferRecordState.Failed, status)
                browserStatus = status
            } finally {
                runCatching { client?.close() }
                TransferCancellationRegistry.unregister(id)
                activeOperation = null
            }
        }
        TransferCancellationRegistry.register(id, job)
    }

    val scpUploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val upload = pendingScpUpload
        pendingScpUpload = null
        if (uri == null || upload == null) return@rememberLauncherForActivityResult
        val displayName = displayNameFor(uri)
        val entry = scpEntry(upload.remotePath, displayName)
        val id = scpTransferId(upload.server, upload.remotePath, TransferDirection.Upload)
        browserStatus = "SCP uploading $displayName"
        activeOperation = "SCP uploading $displayName"
        upsertTransfer(id, upload.server, entry, TransferDirection.Upload, 0f, TransferRecordState.Running, "SCP upload started")
        val job = scope.launch {
            var client: ScpClient? = null
            try {
                val opened = transport.openScp(upload.server, credentialFor(upload.server))
                client = opened
                val totalBytes = sizeFor(uri)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    opened.uploadFrom(displayName, upload.remotePath, totalBytes, input) { progress ->
                        upsertTransfer(
                            id,
                            upload.server,
                            entry,
                            TransferDirection.Upload,
                            progress,
                            TransferRecordState.Running,
                            "SCP uploading ${(progress * 100).toInt().coerceIn(1, 99)}%"
                        )
                    }
                } ?: error("Unable to open selected file.")
                upsertTransfer(id, upload.server, entry, TransferDirection.Upload, 1f, TransferRecordState.Complete, "SCP upload complete")
                browserStatus = "SCP uploaded $displayName"
            } catch (_: CancellationException) {
                val status = transferCancelledStatus("SCP", TransferDirection.Upload, displayName)
                upsertTransfer(id, upload.server, entry, TransferDirection.Upload, 0f, TransferRecordState.Cancelled, status)
                browserStatus = status
            } catch (error: Exception) {
                val status = scpTransferFailureMessage(TransferDirection.Upload, error, upload.remotePath)
                upsertTransfer(id, upload.server, entry, TransferDirection.Upload, 0f, TransferRecordState.Failed, status)
                browserStatus = status
            } finally {
                runCatching { client?.close() }
                TransferCancellationRegistry.unregister(id)
                activeOperation = null
            }
        }
        TransferCancellationRegistry.register(id, job)
    }

    fun openPath(
        server: ServerProfile,
        path: String = currentPath,
        allowFallback: Boolean = false,
        privateKeyPassphrase: String? = null,
        recordHistory: Boolean = true
    ) {
        selectedServerId = server.id
        val requestedPath = SftpPathResolver.normalize(path.ifBlank { fileBrowserStartPath(server, bookmarks) })
        currentPath = requestedPath
        val credential = server.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }
        val trusted = trustedFor(server)
        if (server.protocol != ConnectionProtocol.Rclone &&
            (credential?.secretBacked != true || (server.protocol == ConnectionProtocol.Smb && credential.type != CredentialType.Password))
        ) {
            val previousClient = activeRuntime.client
            activeRuntime.client = null
            activeRuntime.clientServerId = null
            scope.launch { runCatching { previousClient?.close() } }
            entries = emptyList()
            allEntries = emptyList()
            browserStatus = when (server.protocol) {
                ConnectionProtocol.Smb -> "Add a saved password to ${server.name}, then reconnect SMB."
                else -> "Add a saved password or private key to ${server.name}, then reconnect SFTP."
            }
            activeOperation = null
            return
        }
        if (!trusted) {
            val previousClient = activeRuntime.client
            activeRuntime.client = null
            activeRuntime.clientServerId = null
            scope.launch { runCatching { previousClient?.close() } }
            entries = emptyList()
            allEntries = emptyList()
            browserStatus = "Review and trust ${server.name}'s host key before browsing files."
            activeOperation = null
            return
        }
        browserStatus = "Opening ${requestedPath.ifBlank { "." }}"
        activeOperation = "Opening ${requestedPath.ifBlank { "." }}"
        scope.launch {
                val previousPath = lastLoadedPath
                val previousEntries = entries
                val previousAllEntries = allEntries
            try {
                suspend fun clearSelectedClient() {
                    val failedClient = activeRuntime.client
                    activeRuntime.client = null
                    activeRuntime.clientServerId = null
                    runCatching { failedClient?.close() }
                }

                suspend fun ensureClient(reconnect: Boolean): SftpClient {
                    val existing = activeRuntime.client
                    return if (!reconnect && existing != null && activeRuntime.clientServerId == server.id) {
                        existing
                    } else {
                        if (existing != null) runCatching { existing.close() }
                        transport.openSftp(server, credential, privateKeyPassphrase).also {
                            activeRuntime.client = it
                            activeRuntime.clientServerId = server.id
                            onSftpConnected(server)
                        }
                    }
                }

                suspend fun listWith(client: SftpClient): Pair<String, List<SftpEntry>> {
                    val candidates = SftpPathResolver.navigationPlan(
                        server = server,
                        requestedPath = requestedPath,
                        bookmarks = bookmarks,
                        allowFallback = allowFallback,
                        hasLoadedPath = lastLoadedPath.isNotBlank()
                    )
                    var loadedPath = requestedPath
                    var lastListError: Throwable? = null
                    val listed = candidates.firstNotNullOfOrNull { candidate ->
                        runCatching {
                            client.list(candidate).also {
                                loadedPath = runCatching { client.realpath(candidate) }.getOrDefault(candidate)
                            }
                        }.onFailure { lastListError = it }.getOrNull()
                    } ?: throw IllegalStateException(
                        "Cannot list '${requestedPath.ifBlank { "." }}' or fallback directories: ${lastListError?.message ?: "no readable directory"}",
                        lastListError
                    )
                    return loadedPath to listed
                }

                val client = ensureClient(reconnect = false)
                val (loadedPath, listed) = runCatching {
                    listWith(client)
                }.getOrElse { firstError ->
                    val lower = (firstError.message ?: firstError::class.java.simpleName).lowercase()
                    if (lower.contains("closed") || lower.contains("eof") || lower.contains("channel")) {
                        clearSelectedClient()
                        browserStatus = "Reconnecting ${server.name}"
                        listWith(ensureClient(reconnect = true))
                    } else {
                        throw firstError
                    }
                }
                currentPath = loadedPath
                if (recordHistory && previousPath.isNotBlank() && previousPath != loadedPath) {
                    pathBackStack = (pathBackStack + previousPath).takeLast(64)
                }
                if (previousPath != loadedPath) filterQuery = ""
                lastLoadedPath = loadedPath
                allEntries = listed
                entries = sortedEntries(listed)
                browserStatus = "${entries.size} item(s) · $currentPath"
            } catch (error: Exception) {
                if (error.requiresSftpPrivateKeyPassphrase()) {
                    pendingPassphrase = PendingSftpPassphrase(server, requestedPath, allowFallback)
                    sftpPassphrase = ""
                    currentPath = previousPath
                    allEntries = previousAllEntries
                    entries = previousEntries
                    browserStatus = "Private-key passphrase required for SFTP."
                    return@launch
                }
                val dropClient = shouldDropSftpClient(error)
                val failedClient = activeRuntime.client.takeIf { dropClient }
                val failedBeforeConnect = activeRuntime.client == null && activeRuntime.clientServerId == null
                if (dropClient) {
                    activeRuntime.client = null
                    activeRuntime.clientServerId = null
                }
                val recovery = sftpOpenFailureRecovery(previousPath, previousEntries, requestedPath)
                currentPath = recovery.path
                allEntries = previousAllEntries
                entries = recovery.entries
                if (dropClient) runCatching { failedClient?.close() }
                browserStatus = sftpErrorMessage("Open", error, requestedPath)
                if (failedBeforeConnect) browserStateKey.takeIf { it != "files" }?.let(onSftpWorkspaceFailed)
            } finally {
                activeOperation = null
            }
        }
    }

    fun mutateEntry(server: ServerProfile, entry: SftpEntry, action: String, renameTarget: String = "") {
        val client = activeClientFor(server, action) ?: return
        browserStatus = "$action ${entry.name}"
        activeOperation = "$action ${entry.name}"
        scope.launch {
            try {
                when (action) {
                    "Rename" -> {
                        val cleanName = SftpFileNamePolicy.normalizeEditableName(renameTarget.ifBlank { "${entry.name}.renamed" })
                            ?: throw IllegalArgumentException(SftpFileNamePolicy.errorMessage("Rename"))
                        val target = SftpPathResolver.join(SftpPathResolver.parent(entry.path), cleanName)
                        client.rename(entry.path, target)
                        browserStatus = "Renamed ${entry.name} to $cleanName"
                    }
                    "Delete" -> {
                        client.delete(entry.path)
                        browserStatus = "Deleted ${entry.name}"
                    }
                    "Upload" -> {
                        val targetDirectory = SftpPathResolver.uploadTargetDirectory(currentPath, entry)
                        pendingUpload = PendingSftpUpload(server, targetDirectory)
                        uploadPicker.launch(arrayOf("*/*"))
                        return@launch
                    }
                    "New Folder" -> {
                        val cleanName = SftpFileNamePolicy.normalizeEditableName(renameTarget.ifBlank { "New Folder" })
                            ?: throw IllegalArgumentException(SftpFileNamePolicy.errorMessage("New folder"))
                        val target = if (entry.path == currentPath) {
                            SftpPathResolver.join(currentPath, cleanName)
                        } else {
                            SftpPathResolver.join(SftpPathResolver.parent(entry.path), cleanName)
                        }
                        client.mkdir(target)
                        browserStatus = "Created folder $cleanName"
                    }
                    "Chmod" -> {
                        val mode = SftpPermissionModePolicy.parseOctalMode(renameTarget)
                            ?: throw IllegalArgumentException("Enter a 3 or 4 digit octal mode, such as 644 or 0755.")
                        client.chmod(entry.path, mode)
                        browserStatus = "Changed ${entry.name} to ${SftpPermissionModePolicy.displayMode(mode)}"
                    }
                    "Public Link" -> {
                        val link = client.publicLink(entry.path)
                        clipboard.setText(AnnotatedString(link))
                        browserStatus = "Copied public link for ${entry.name}"
                    }
                    "Directory Size" -> {
                        val size = client.directorySize(entry.path)
                        browserStatus = "${entry.name}: ${MetricFormatters.bytesLabel(size.bytes)} across ${size.count} file(s)"
                    }
                    "Remote Space" -> {
                        val space = client.remoteSpace(entry.path)
                        browserStatus = "Remote space: used ${space.used.rcloneSpaceLabel()} · free ${space.free.rcloneSpaceLabel()} · total ${space.total.rcloneSpaceLabel()}"
                    }
                }
                if (action != "Public Link" && action != "Directory Size" && action != "Remote Space") {
                    refreshCurrentDirectoryAfterOperation(client, "$action complete")
                }
            } catch (error: Exception) {
                browserStatus = sftpErrorMessage(action, error, entry.path)
                dropActiveSftpClientIfNeeded(error)
            } finally {
                activeOperation = null
            }
        }
    }

    val selectedServer = servers.firstOrNull { it.id == selectedServerId }
    val selectedTrusted = selectedServer?.let(::trustedFor) ?: false

    fun navigateBackInSftpHistory(): Boolean {
        if (pathBackStack.isEmpty() || activeOperation != null) return false
        val server = selectedServer ?: return false
        val previousPath = pathBackStack.last()
        pathBackStack = pathBackStack.dropLast(1)
        openPath(server, previousPath, allowFallback = false, recordHistory = false)
        return true
    }

    fun closeSftpHost(status: String = "SFTP closed") {
        val previousClient = activeRuntime.client
        clearPendingSftpUi()
        hostsExpanded = false
        allEntries = emptyList()
        entries = emptyList()
        pathBackStack = emptyList()
        selectedServerId = null
        activeRuntime.client = null
        activeRuntime.clientServerId = null
        activeOperation = null
        scope.launch { runCatching { previousClient?.close() } }
        browserStatus = status
    }

    fun selectSftpHost(server: ServerProfile) {
        if (selectedServerId == server.id && activeRuntime.clientServerId == server.id && activeRuntime.client != null) {
            closeSftpHost()
            return
        }
        val nextPath = fileBrowserStartPath(server, bookmarks)
        val previousClient = activeRuntime.client.takeIf { activeRuntime.clientServerId != server.id }
        clearPendingSftpUi()
        hostsExpanded = false
        allEntries = emptyList()
        entries = emptyList()
        pathBackStack = emptyList()
        activeRuntime.client = activeRuntime.client.takeIf { activeRuntime.clientServerId == server.id }
        activeRuntime.clientServerId = activeRuntime.clientServerId.takeIf { it == server.id }
        activeOperation = null
        browserStatus = "Opening ${server.name}"
        scope.launch { runCatching { previousClient?.close() } }
        selectedServerId = server.id
        currentPath = nextPath
        openPath(server, nextPath, allowFallback = false)
    }

    fun openSftpHost(server: ServerProfile) {
        val nextPath = if (selectedServerId == server.id && currentPath.isNotBlank()) {
            currentPath
        } else {
            fileBrowserStartPath(server, bookmarks)
        }
        if (selectedServerId != server.id || activeRuntime.clientServerId != server.id || activeRuntime.client == null) {
            val previousClient = activeRuntime.client.takeIf { activeRuntime.clientServerId != server.id }
            clearPendingSftpUi()
            hostsExpanded = false
            allEntries = emptyList()
            entries = emptyList()
            pathBackStack = emptyList()
            activeRuntime.client = activeRuntime.client.takeIf { activeRuntime.clientServerId == server.id }
            activeRuntime.clientServerId = activeRuntime.clientServerId.takeIf { it == server.id }
            activeOperation = null
            browserStatus = "Opening ${server.name}"
            scope.launch { runCatching { previousClient?.close() } }
        }
        selectedServerId = server.id
        currentPath = nextPath
        openPath(server, nextPath, allowFallback = true)
    }

    LaunchedEffect(initialServerId, selectedServer?.id) {
        val server = selectedServer ?: return@LaunchedEffect
        if (initialServerId == server.id && activeRuntime.client == null && activeOperation == null) {
            openSftpHost(server)
        }
    }

    LaunchedEffect(externalBackRequestSerial) {
        if (externalBackRequestSerial == 0) return@LaunchedEffect
        if (!navigateBackInSftpHistory()) onExternalBackFallback()
    }

    BackHandler(enabled = active && fullPage && pathBackStack.isNotEmpty() && activeOperation == null) {
        navigateBackInSftpHistory()
    }

    LaunchedEffect(active, activeOperation, selectedServerId, activeRuntime.clientServerId, activeRuntime.client) {
        val client = activeRuntime.client ?: return@LaunchedEffect
        val server = selectedServer ?: return@LaunchedEffect
        val clientServerId = activeRuntime.clientServerId ?: return@LaunchedEffect
        if (!active || activeOperation != null || clientServerId != server.id) return@LaunchedEffect
        runCatching {
            client.realpath(".")
        }.onFailure { error ->
            if (shouldDropSftpClient(error) && activeRuntime.client === client && activeRuntime.clientServerId == clientServerId) {
                activeRuntime.client = null
                activeRuntime.clientServerId = null
                browserStatus = "SFTP connection closed. Reconnect to continue."
                runCatching { client.close() }
                browserStateKey.takeIf { it != "files" }?.let(onSftpWorkspaceFailed)
            }
        }
    }

    if (runtime == null) {
        val clientForDispose = activeRuntime.client
        DisposableEffect(clientForDispose) {
            onDispose {
                if (clientForDispose != null) {
                    scope.launch { runCatching { clientForDispose.close() } }
                }
            }
        }
    }
    if (selectedServer == null) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .animateContentSize(tween(180)),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            servers.forEach { server ->
                SftpHostListRow(
                    server = server,
                    selected = false,
                    connected = false,
                    enabled = activeOperation == null,
                    onSelect = { selectSftpHost(server) },
                )
            }
        }
        return
    }
    DeckCard(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(tween(180)),
        radius = if (fullPage) 8.dp else 24.dp,
        padding = PaddingValues(if (fullPage) 12.dp else 14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            val visibleServers = if (sftpHostListExpanded(selectedServerId, hostsExpanded)) {
                servers
            } else {
                servers.filter { it.id == selectedServerId }
            }
            visibleServers.forEach { server ->
                SftpHostListRow(
                    server = server,
                    selected = server.id == selectedServerId,
                    connected = activeRuntime.client != null && activeRuntime.clientServerId == server.id,
                    enabled = activeOperation == null,
                    onSelect = { selectSftpHost(server) },
                )
            }
            if (servers.size > 1) {
                HostAction(if (hostsExpanded) "Hide hosts" else "Hosts") { hostsExpanded = !hostsExpanded }
            }
        }
        Spacer(Modifier.height(10.dp))
        val selectedBookmarks = selectedServer?.let { server -> bookmarks.filter { it.serverId == server.id } }.orEmpty()
        val pinnedCurrentPath = selectedBookmarks.firstOrNull { SftpPathResolver.normalize(it.path) == SftpPathResolver.normalize(currentPath) }
        val selectedCredential = selectedServer?.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }
        val activeTransfer = selectedServer?.let { server -> activeTransferFor(transfers, server.id) }
        activeTransfer?.let {
            ActiveTransferBar(it)
            Spacer(Modifier.height(8.dp))
        }
        if (activeRuntime.client != null || activeOperation != null) {
            SftpToolbar(
            path = currentPath.ifBlank { "." },
            connected = activeRuntime.client != null,
            canReconnect = selectedServer != null && activeRuntime.client == null && activeOperation == null && selectedCredential?.secretBacked == true && selectedTrusted,
            canScp = selectedServer != null && selectedCredential?.secretBacked == true && selectedTrusted && activeOperation == null,
            needsIdentity = selectedServer != null && selectedCredential?.secretBacked != true,
            needsTrust = selectedServer != null && !selectedTrusted,
            pinned = pinnedCurrentPath != null,
            sortMode = sortMode,
            sortDescending = sortDescending,
            showHiddenFiles = showHiddenFiles,
            onPathClick = { path -> selectedServer?.let { openPath(it, path) } },
            onRefresh = { selectedServer?.let { openPath(it, currentPath, allowFallback = false) } },
            onSort = { selectedSort ->
                val nextDescending = if (selectedSort == sortMode) {
                    !sortDescending
                } else {
                    false
                }
                sortMode = selectedSort
                sortDescending = nextDescending
                entries = SftpPathResolver.sortForFileManager(
                    SftpPathResolver.visibleForFileManager(allEntries, showHiddenFiles),
                    selectedSort,
                    nextDescending
                )
            },
            onToggleHidden = {
                val nextShowHiddenFiles = !showHiddenFiles
                showHiddenFiles = nextShowHiddenFiles
                entries = SftpPathResolver.sortForFileManager(
                    SftpPathResolver.visibleForFileManager(allEntries, nextShowHiddenFiles),
                    sortMode,
                    sortDescending
                )
            },
            onPin = {
                selectedServer?.let { server ->
                    val cleanPath = currentPath.ifBlank { "." }
                    onSftpBookmarkChanged(
                        SftpBookmark(
                            id = "bookmark-${server.id}-${cleanPath.hashCode()}",
                            serverId = server.id,
                            label = cleanPath.substringAfterLast('/').ifBlank { cleanPath },
                            path = cleanPath,
                            createdAtEpochMillis = System.currentTimeMillis()
                        )
                    )
                    browserStatus = "Pinned $cleanPath"
                }
            },
            onUnpin = {
                pinnedCurrentPath?.let { bookmark ->
                    bookmarkText = bookmark.label.ifBlank { bookmark.path.substringAfterLast('/').ifBlank { bookmark.path } }
                    pendingBookmarkAction = PendingSftpBookmarkAction(bookmark, "Delete")
                }
            },
            onUpload = {
                selectedServer?.let { server ->
                    if (activeClientFor(server, "uploading") == null) return@let
                    pendingUpload = PendingSftpUpload(server, currentPath)
                    uploadPicker.launch(arrayOf("*/*"))
                }
            },
            onNewFolder = {
                selectedServer?.let { server ->
                    if (activeClientFor(server, "creating a folder") == null) return@let
                    actionText = "New Folder"
                    pendingFileAction = PendingSftpAction(server, null, "New Folder")
                }
            },
            onScpUpload = {
                selectedServer?.let { server ->
                    if (!canStartScp(server)) return@let
                    scpPathText = SftpPathResolver.join(currentPath.ifBlank { "~" }, "file")
                    pendingScpPathAction = PendingScpPathAction(server, TransferDirection.Upload, scpPathText)
                }
            },
            onScpDownload = {
                selectedServer?.let { server ->
                    if (!canStartScp(server)) return@let
                    scpPathText = SftpPathResolver.join(currentPath.ifBlank { "~" }, "file")
                    pendingScpPathAction = PendingScpPathAction(server, TransferDirection.Download, scpPathText)
                }
            },
            onReconnect = {
                selectedServer?.let { openPath(it, currentPath.ifBlank { SftpPathResolver.defaultStartPath(it, bookmarks) }, allowFallback = true) }
            },
            onIdentity = { selectedServer?.let(onServerClick) },
            onTrust = { selectedServer?.let(onTrustHost) },
            onTerminal = { selectedServer?.let(onTerminalClick) }
            )
        }
        Spacer(Modifier.height(8.dp))
        if ((activeRuntime.client != null || activeOperation != null) && selectedBookmarks.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                selectedBookmarks.forEach { bookmark ->
                    SoftPill(
                        text = bookmark.label.ifBlank { bookmark.path },
                        selected = bookmark.path == currentPath,
                        color = DeckColors.Orange
                    ) {
                        selectedServer?.let { openPath(it, bookmark.path) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (activeRuntime.client != null && entries.isNotEmpty()) {
            SftpFilterBar(
                query = filterQuery,
                onQueryChange = { filterQuery = it }
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(10.dp))
        val browserActive = activeRuntime.client != null || activeOperation != null
        if (!browserActive) return@DeckCard
        val visibleEntries = displayedEntries()
        val chmodAvailable = selectedServer?.protocol == ConnectionProtocol.Ssh
        val rcloneAdvancedAvailable = selectedServer?.protocol == ConnectionProtocol.Rclone
        if (entries.isEmpty() || visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (fullPage) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(16.dp))
                    .background(DeckColors.SurfaceMuted)
                    .padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        when {
                            selectedServer == null -> "Add a host to browse files."
                            entries.isNotEmpty() && visibleEntries.isEmpty() -> "No matches."
                            activeOperation == null && browserStatus.startsWith("0 item") -> "This folder is empty."
                            else -> browserStatus
                        },
                        color = DeckColors.SecondaryText,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    activeOperation?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = DeckColors.PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (fullPage) {
            selectedServer?.let { server ->
                SftpParentDirectoryRow(
                    enabled = activeRuntime.client != null && activeOperation == null,
                    onClick = { openPath(server, SftpPathResolver.parent(currentPath)) }
                )
            }
            activeOperation?.let { operation ->
                SftpLoadingRow(operation)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(visibleEntries, key = { it.path }) { entry ->
                    SftpEntryRow(
                        entry = entry,
                        chmodAvailable = chmodAvailable,
                        rcloneAdvancedAvailable = rcloneAdvancedAvailable,
                        onOpen = {
                            val server = selectedServer ?: return@SftpEntryRow
                            if (entry.navigable) {
                                openPath(server, entry.path)
                            } else if (SftpTextFilePolicy.canEdit(entry)) {
                                openTextFile(server, entry)
                            } else {
                                requestDownload(server, entry)
                            }
                        },
                        onOpenText = {
                            val server = selectedServer ?: return@SftpEntryRow
                            openTextFile(server, entry)
                        },
                        onDownload = {
                            val server = selectedServer ?: return@SftpEntryRow
                            requestDownload(server, entry)
                        },
                        onUpload = {
                            val server = selectedServer ?: return@SftpEntryRow
                            mutateEntry(server, entry, "Upload")
                        },
                        onChmod = {
                            val server = selectedServer ?: return@SftpEntryRow
                            actionText = SftpPermissionModePolicy.editableModeOrDefault(entry.permissions, entry.directory)
                            pendingFileAction = PendingSftpAction(server, entry, "Chmod")
                        },
                        onRename = {
                            val server = selectedServer ?: return@SftpEntryRow
                            actionText = entry.name
                            pendingFileAction = PendingSftpAction(server, entry, "Rename")
                        },
                        onCopyPath = {
                            copyRemotePath(entry)
                        },
                        onCopyToHost = {
                            val server = selectedServer ?: return@SftpEntryRow
                            requestCopyToHost(server, entry)
                        },
                        onPublicLink = {
                            val server = selectedServer ?: return@SftpEntryRow
                            mutateEntry(server, entry, "Public Link")
                        },
                        onDirectorySize = {
                            val server = selectedServer ?: return@SftpEntryRow
                            mutateEntry(server, entry, "Directory Size")
                        },
                        onRemoteSpace = {
                            val server = selectedServer ?: return@SftpEntryRow
                            mutateEntry(server, entry, "Remote Space")
                        },
                        onDelete = {
                            val server = selectedServer ?: return@SftpEntryRow
                            actionText = ""
                            pendingFileAction = PendingSftpAction(server, entry, "Delete")
                        }
                    )
                }
            }
        } else {
            selectedServer?.let { server ->
                SftpParentDirectoryRow(
                    enabled = activeRuntime.client != null && activeOperation == null,
                    onClick = { openPath(server, SftpPathResolver.parent(currentPath)) }
                )
            }
            activeOperation?.let { operation ->
                SftpLoadingRow(operation)
            }
            visibleEntries.forEach { entry ->
                SftpEntryRow(
                    entry = entry,
                    chmodAvailable = chmodAvailable,
                    rcloneAdvancedAvailable = rcloneAdvancedAvailable,
                    onOpen = {
                        val server = selectedServer ?: return@SftpEntryRow
                        if (entry.navigable) {
                            openPath(server, entry.path)
                        } else if (SftpTextFilePolicy.canEdit(entry)) {
                            openTextFile(server, entry)
                        } else {
                            requestDownload(server, entry)
                        }
                    },
                    onOpenText = {
                        val server = selectedServer ?: return@SftpEntryRow
                        openTextFile(server, entry)
                    },
                    onDownload = {
                        val server = selectedServer ?: return@SftpEntryRow
                        requestDownload(server, entry)
                    },
                    onUpload = {
                        val server = selectedServer ?: return@SftpEntryRow
                        mutateEntry(server, entry, "Upload")
                    },
                    onChmod = {
                        val server = selectedServer ?: return@SftpEntryRow
                        actionText = SftpPermissionModePolicy.editableModeOrDefault(entry.permissions, entry.directory)
                        pendingFileAction = PendingSftpAction(server, entry, "Chmod")
                    },
                    onRename = {
                        val server = selectedServer ?: return@SftpEntryRow
                        actionText = entry.name
                        pendingFileAction = PendingSftpAction(server, entry, "Rename")
                    },
                    onCopyPath = {
                        copyRemotePath(entry)
                    },
                    onCopyToHost = {
                        val server = selectedServer ?: return@SftpEntryRow
                        requestCopyToHost(server, entry)
                    },
                    onPublicLink = {
                        val server = selectedServer ?: return@SftpEntryRow
                        mutateEntry(server, entry, "Public Link")
                    },
                    onDirectorySize = {
                        val server = selectedServer ?: return@SftpEntryRow
                        mutateEntry(server, entry, "Directory Size")
                    },
                    onRemoteSpace = {
                        val server = selectedServer ?: return@SftpEntryRow
                        mutateEntry(server, entry, "Remote Space")
                    },
                    onDelete = {
                        val server = selectedServer ?: return@SftpEntryRow
                        actionText = ""
                        pendingFileAction = PendingSftpAction(server, entry, "Delete")
                    }
                )
            }
        }
    }
    pendingFileAction?.let { action ->
        SftpActionDialog(
            action = action,
            text = actionText,
            onTextChange = { actionText = it },
            onDismiss = {
                pendingFileAction = null
                actionText = ""
            },
            onConfirm = {
                pendingFileAction = null
                val syntheticEntry = action.entry ?: SftpEntry(actionText, currentPath, true, 0, System.currentTimeMillis())
                mutateEntry(action.server, syntheticEntry, action.action, actionText)
                actionText = ""
            }
        )
    }
    pendingTextEdit?.let { edit ->
        fun closeTextEditor() {
            pendingTextEdit = null
            textEditContent = ""
            textEditOriginalContent = ""
            confirmTextEditDiscard = false
        }

        fun requestCloseTextEditor() {
            if (sftpTextEditNeedsDiscardConfirmation(textEditOriginalContent, textEditContent)) {
                confirmTextEditDiscard = true
            } else {
                closeTextEditor()
            }
        }

        SftpTextEditorDialog(
            edit = edit,
            text = textEditContent,
            onTextChange = { textEditContent = it },
            onDismiss = ::requestCloseTextEditor,
            onSave = { saveTextFile(edit, textEditContent) }
        )
        if (confirmTextEditDiscard) {
            SftpDiscardTextEditDialog(
                fileName = edit.entry.name,
                onDismiss = { confirmTextEditDiscard = false },
                onDiscard = ::closeTextEditor
            )
        }
    }
    pendingHostTransfer?.let { transfer ->
        val destinationCandidates = servers
            .filter { it.id != transfer.sourceServer.id && fileCopyDestinationReady(it) }
            .sortedBy { it.name.lowercase() }
        val selectedDestination = destinationCandidates.firstOrNull { it.id == transfer.destinationServerId }
            ?: destinationCandidates.firstOrNull()
        SftpHostTransferDialog(
            transfer = transfer,
            destinations = destinationCandidates,
            selectedDestinationId = selectedDestination?.id.orEmpty(),
            destinationDirectory = hostTransferDestinationPath,
            onDestinationSelected = { destination ->
                pendingHostTransfer = transfer.copy(destinationServerId = destination.id)
                hostTransferDestinationPath = SftpPathResolver.defaultStartPath(destination, bookmarks)
            },
            onDestinationDirectoryChange = { hostTransferDestinationPath = it },
            onDismiss = {
                pendingHostTransfer = null
                hostTransferDestinationPath = ""
            },
            onConfirm = {
                val destination = selectedDestination ?: return@SftpHostTransferDialog
                val directory = hostTransferDestinationPath.trim()
                if (directory.isBlank()) {
                    browserStatus = "Choose a destination folder."
                    return@SftpHostTransferDialog
                }
                startHostTransfer(transfer, destination, directory)
            }
        )
    }
    pendingBookmarkAction?.let { action ->
        SftpBookmarkDialog(
            action = action,
            label = bookmarkText,
            onLabelChange = { bookmarkText = it },
            onDismiss = {
                pendingBookmarkAction = null
                bookmarkText = ""
            },
            onRename = {
                val cleanLabel = bookmarkText.trim()
                if (cleanLabel.isNotBlank()) {
                    onSftpBookmarkChanged(action.bookmark.copy(label = cleanLabel))
                    browserStatus = "Renamed bookmark ${action.bookmark.path}"
                }
                pendingBookmarkAction = null
                bookmarkText = ""
            },
            onDelete = {
                onSftpBookmarkDeleted(action.bookmark)
                browserStatus = "Removed bookmark ${action.bookmark.label.ifBlank { action.bookmark.path }}"
                pendingBookmarkAction = null
                bookmarkText = ""
            }
        )
    }
    pendingScpPathAction?.let { action ->
        ScpPathDialog(
            action = action,
            path = scpPathText,
            onPathChange = { scpPathText = it },
            onDismiss = {
                pendingScpPathAction = null
                scpPathText = ""
            },
            onConfirm = {
                val normalized = ScpTransferPolicy.normalizeRemotePath(scpPathText)
                if (normalized == null) {
                    browserStatus = "Choose a remote file path for SCP."
                    return@ScpPathDialog
                }
                val displayName = ScpTransferPolicy.safeDisplayName("", normalized)
                when (action.direction) {
                    TransferDirection.Upload -> {
                        pendingScpUpload = PendingScpTransfer(action.server, normalized, displayName)
                        scpUploadPicker.launch(arrayOf("*/*"))
                    }
                    TransferDirection.Download -> {
                        pendingScpDownload = PendingScpTransfer(action.server, normalized, displayName)
                        scpDownloadPicker.launch(displayName)
                    }
                }
                pendingScpPathAction = null
                scpPathText = ""
            }
        )
    }
    pendingPassphrase?.let { request ->
        SftpPassphraseDialog(
            passphrase = sftpPassphrase,
            message = "Enter the passphrase for ${request.server.name} to open SFTP.",
            confirmLabel = "Open SFTP",
            onPassphraseChange = { sftpPassphrase = it },
            onDismiss = {
                pendingPassphrase = null
                sftpPassphrase = ""
                activeOperation = null
            },
            onConnect = {
                val passphrase = sftpPassphrase
                pendingPassphrase = null
                sftpPassphrase = ""
                openPath(request.server, request.path, request.allowFallback, passphrase)
            }
        )
    }
    pendingHostTransferPassphrase?.let { request ->
        val destination = servers.firstOrNull { it.id == request.destinationServerId }
        if (destination == null) {
            pendingHostTransferPassphrase = null
            sftpPassphrase = ""
        } else {
            SftpPassphraseDialog(
                passphrase = sftpPassphrase,
                message = "Enter the passphrase for ${destination.name} to copy ${request.transfer.sourceEntry.name}.",
                confirmLabel = "Copy",
                onPassphraseChange = { sftpPassphrase = it },
                onDismiss = {
                    pendingHostTransferPassphrase = null
                    sftpPassphrase = ""
                    activeOperation = null
                },
                onConnect = {
                    val passphrase = sftpPassphrase
                    pendingHostTransferPassphrase = null
                    sftpPassphrase = ""
                    startHostTransfer(request.transfer, destination, request.destinationDirectory, passphrase)
                }
            )
        }
    }
}

@Composable
private fun SftpHostListRow(
    server: ServerProfile,
    selected: Boolean,
    connected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChronoOsLogo(server.osName, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            server.name,
            color = DeckColors.PrimaryText,
            fontSize = 15.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        FileRowAction(
            text = "sftp-open",
            color = if (connected) DeckColors.Green else DeckColors.Cyan,
            contentDescription = "Open SFTP",
            onClick = onSelect
        )
    }
}

@Composable
private fun SftpHostPickerCard(
    server: ServerProfile,
    selected: Boolean,
    connected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .width(188.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChronoOsLogo(server.osName, Modifier.size(26.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        FileRowAction("sftp-open", if (connected) DeckColors.Green else DeckColors.Cyan, contentDescription = "Open SFTP", onClick = onClick)
    }
}

@Composable
private fun SftpFilterBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        label = { Text("Filter", color = DeckColors.SecondaryText) },
        shape = RoundedCornerShape(22.dp),
        colors = sftpTextFieldColors(),
        trailingIcon = {
            if (query.isNotBlank()) {
                Text(
                    "Clear",
                    color = DeckColors.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onQueryChange("") }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun sftpTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
private fun SftpParentDirectoryRow(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("..", color = DeckColors.PrimaryText, fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SftpLoadingRow(operation: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ReadinessPip(active = true, label = "")
        Text(operation, color = DeckColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SftpPathBar(path: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileGlyph(directory = true)
        Spacer(Modifier.width(9.dp))
        Text(
            path.ifBlank { "." },
            color = DeckColors.PrimaryText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SftpToolbar(
    path: String,
    connected: Boolean,
    canReconnect: Boolean,
    canScp: Boolean,
    needsIdentity: Boolean,
    needsTrust: Boolean,
    pinned: Boolean,
    sortMode: SftpSortMode,
    sortDescending: Boolean,
    showHiddenFiles: Boolean,
    onPathClick: (String) -> Unit,
    onRefresh: () -> Unit,
    onSort: (SftpSortMode) -> Unit,
    onToggleHidden: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onScpUpload: () -> Unit,
    onScpDownload: () -> Unit,
    onReconnect: () -> Unit,
    onIdentity: () -> Unit,
    onTrust: () -> Unit,
    onTerminal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FileGlyph(directory = true)
            Column(Modifier.weight(1f)) {
                SftpBreadcrumb(path = path, onPathClick = onPathClick)
            }
            ReadinessPip(active = connected, label = if (connected) "LIVE" else "OFF")
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            when {
                connected -> {
                    SftpToolbarAction("refresh", "Refresh", DeckColors.Cyan, compact = true, onClick = onRefresh)
                    SftpSortMenu(sortMode, sortDescending, onSort)
                    SftpToolbarAction(if (showHiddenFiles) "eye-on" else "eye-off", "Hidden", DeckColors.PrimaryText, compact = true, onClick = onToggleHidden)
                    SftpToolbarAction(if (pinned) "bookmark-on" else "bookmark", if (pinned) "Remove bookmark" else "Bookmark", DeckColors.Orange, compact = true, onClick = if (pinned) onUnpin else onPin)
                    SftpToolbarAction("upload", "Upload", DeckColors.Green, onClick = onUpload)
                    SftpToolbarAction("new-folder", "Folder", DeckColors.PrimaryText, onClick = onNewFolder)
                    if (canScp) {
                        SftpScpMenu(onScpUpload, onScpDownload)
                    }
                    SftpToolbarAction("terminal", "SSH", DeckColors.Cyan, compact = true, onClick = onTerminal)
                }
                canReconnect -> {
                    SftpToolbarAction("sftp-open", "Open SFTP", DeckColors.Cyan, onClick = onReconnect)
                    if (canScp) {
                        SftpScpMenu(onScpUpload, onScpDownload)
                    }
                    SftpToolbarAction("terminal", "SSH", DeckColors.Cyan, compact = true, onClick = onTerminal)
                }
                needsIdentity -> {
                    SftpToolbarAction("identity", "Identity", DeckColors.Orange, onClick = onIdentity)
                    SftpToolbarAction("terminal", "SSH", DeckColors.Cyan, compact = true, onClick = onTerminal)
                }
                needsTrust -> {
                    SftpToolbarAction("trust", "Trust", DeckColors.Orange, onClick = onTrust)
                    SftpToolbarAction("terminal", "SSH", DeckColors.Cyan, compact = true, onClick = onTerminal)
                }
                else -> {
                    SftpToolbarAction("terminal", "SSH", DeckColors.Cyan, compact = true, onClick = onTerminal)
                }
            }
        }
    }
}

@Composable
private fun SftpScpMenu(
    onScpUpload: () -> Unit,
    onScpDownload: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SftpToolbarAction("scp", "SCP", DeckColors.PrimaryText, onClick = { expanded = true })
        SftpDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Upload", color = DeckColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    expanded = false
                    onScpUpload()
                }
            )
            DropdownMenuItem(
                text = { Text("Download", color = DeckColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                onClick = {
                    expanded = false
                    onScpDownload()
                }
            )
        }
    }
}

@Composable
private fun SftpSortMenu(
    sortMode: SftpSortMode,
    sortDescending: Boolean,
    onSort: (SftpSortMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SftpToolbarAction(if (sortDescending) "sort-down" else "sort-up", sftpSortMenuLabel(sortMode, sortDescending), DeckColors.PrimaryText, compact = true, onClick = { expanded = true })
        SftpDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(SftpSortMode.Name, SftpSortMode.Modified, SftpSortMode.Size).forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            sftpSortMenuItemLabel(mode, selected = mode == sortMode, descending = sortDescending),
                            color = DeckColors.PrimaryText,
                            fontSize = 13.sp,
                            fontWeight = if (mode == sortMode) FontWeight.Black else FontWeight.SemiBold
                        )
                    },
                    onClick = {
                        expanded = false
                        onSort(mode)
                    }
                )
            }
        }
    }
}

@Composable
private fun SftpDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        shape = RoundedCornerShape(14.dp),
        containerColor = DeckColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.72f), RoundedCornerShape(14.dp)),
        content = content
    )
}

internal fun sftpSortMenuLabel(mode: SftpSortMode, descending: Boolean): String {
    return "${sftpSortModeLabel(mode)} ${if (descending) "↓" else "↑"}"
}

private fun sftpSortMenuItemLabel(mode: SftpSortMode, selected: Boolean, descending: Boolean): String {
    val label = sftpSortModeLabel(mode)
    return if (selected) "$label, ${if (descending) "descending" else "ascending"}" else label
}

private fun sftpSortModeLabel(mode: SftpSortMode): String {
    return when (mode) {
        SftpSortMode.Name -> "Name"
        SftpSortMode.Modified -> "Modified"
        SftpSortMode.Size -> "Size"
    }
}

@Composable
private fun SftpBreadcrumb(path: String, onPathClick: (String) -> Unit) {
    val segments = remember(path) { sftpCompactBreadcrumbSegments(SftpPathResolver.breadcrumbSegments(path)) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        segments.forEachIndexed { index, segment ->
            Text(
                segment.first,
                color = if (index == segments.lastIndex) DeckColors.PrimaryText else DeckColors.Cyan,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onPathClick(segment.second) }
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
            if (index != segments.lastIndex) {
                Text("/", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SftpToolbarAction(
    symbol: String,
    label: String,
    color: Color,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .then(if (compact) Modifier.width(42.dp) else Modifier)
            .clip(RoundedCornerShape(15.dp))
            .background(DeckColors.SurfaceRaised)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 0.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (symbol in sftpGlyphSymbols) {
            SftpToolbarGlyph(symbol, color, Modifier.size(17.dp))
        } else {
            Text(symbol, color = color, fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
        }
        if (!compact) {
            Spacer(Modifier.width(7.dp))
            Text(label, color = DeckColors.PrimaryText, fontSize = 13.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
        }
    }
}

private data class PendingSftpUpload(
    val server: ServerProfile,
    val remoteDirectory: String
)

private data class PendingSftpDownload(
    val server: ServerProfile,
    val entry: SftpEntry
)

private data class PendingSftpHostTransfer(
    val sourceServer: ServerProfile,
    val sourceEntry: SftpEntry,
    val destinationServerId: String
)

private data class PendingSftpHostTransferPassphrase(
    val transfer: PendingSftpHostTransfer,
    val destinationServerId: String,
    val destinationDirectory: String
)

private data class PendingSftpTextEdit(
    val server: ServerProfile,
    val entry: SftpEntry
)

private data class PendingScpTransfer(
    val server: ServerProfile,
    val remotePath: String,
    val localDisplayName: String
)

private data class PendingScpPathAction(
    val server: ServerProfile,
    val direction: TransferDirection,
    val initialPath: String
)

private data class PendingSftpAction(
    val server: ServerProfile,
    val entry: SftpEntry?,
    val action: String
)

private data class PendingSftpBookmarkAction(
    val bookmark: SftpBookmark,
    val action: String
)

private data class PendingSftpPassphrase(
    val server: ServerProfile,
    val path: String,
    val allowFallback: Boolean
)

@Composable
private fun ScpPathDialog(
    action: PendingScpPathAction,
    path: String,
    onPathChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val cleanPath = ScpTransferPolicy.normalizeRemotePath(path)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (action.direction == TransferDirection.Upload) "SCP upload" else "SCP download",
                color = DeckColors.PrimaryText,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            OutlinedTextField(
                value = path,
                onValueChange = onPathChange,
                singleLine = true,
                isError = path.isNotBlank() && cleanPath == null,
                shape = RoundedCornerShape(18.dp),
                colors = sftpTextFieldColors(),
                label = { Text("Remote file path") }
            )
        },
        confirmButton = {
            TextButton(enabled = cleanPath != null, onClick = onConfirm) {
                Text(if (action.direction == TransferDirection.Upload) "Choose File" else "Save As", fontWeight = FontWeight.Black)
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
private fun SftpPassphraseDialog(
    passphrase: String,
    message: String,
    confirmLabel: String,
    onPassphraseChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConnect: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Private key passphrase", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(message, color = DeckColors.SecondaryText, fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = onPassphraseChange,
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = sftpTextFieldColors(),
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Passphrase") }
                )
            }
        },
        confirmButton = {
            TextButton(enabled = passphrase.isNotBlank(), onClick = onConnect) {
                Text(confirmLabel, fontWeight = FontWeight.Black)
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
private fun SftpTextEditorDialog(
    edit: PendingSftpTextEdit,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val byteCount = text.toByteArray(Charsets.UTF_8).size.toLong()
    val tooLarge = byteCount > SftpTextFilePolicy.MaxEditableBytes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(edit.entry.name, color = DeckColors.PrimaryText, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(edit.entry.path, color = DeckColors.SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                minLines = 10,
                maxLines = 18,
                isError = tooLarge,
                shape = RoundedCornerShape(18.dp),
                colors = sftpTextFieldColors(),
                label = { Text("Remote text") },
                supportingText = {
                    Text(
                        if (tooLarge) "File exceeds 256 K." else "${byteCount.fileSizeLabel()} / 256 K",
                        color = if (tooLarge) DeckColors.Red else DeckColors.SecondaryText
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(enabled = !tooLarge, onClick = onSave) {
                Text("Save", color = if (tooLarge) DeckColors.SecondaryText else DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun SftpHostTransferDialog(
    transfer: PendingSftpHostTransfer,
    destinations: List<ServerProfile>,
    selectedDestinationId: String,
    destinationDirectory: String,
    onDestinationSelected: (ServerProfile) -> Unit,
    onDestinationDirectoryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val selectedDestination = destinations.firstOrNull { it.id == selectedDestinationId }
    val destinationReady = selectedDestination != null && destinationDirectory.trim().isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Copy to host", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black)
                Text("Via this device: ${transfer.sourceEntry.name}", color = DeckColors.SecondaryText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (destinations.isEmpty()) {
                    Text(
                        "No other trusted host has a saved credential.",
                        color = DeckColors.SecondaryText,
                        fontSize = 14.sp
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        destinations.forEach { destination ->
                            SftpHostPickerCard(
                                server = destination,
                                selected = destination.id == selectedDestinationId,
                                connected = false,
                                onClick = { onDestinationSelected(destination) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = destinationDirectory,
                        onValueChange = onDestinationDirectoryChange,
                        singleLine = true,
                        isError = destinationDirectory.trim().isBlank(),
                        shape = RoundedCornerShape(22.dp),
                        colors = sftpTextFieldColors(),
                        label = { Text("Destination folder") },
                        supportingText = {
                            Text(
                                if (destinationDirectory.trim().isBlank()) "Enter a remote folder." else "Target: ${SftpHostTransferPolicy.targetPath(destinationDirectory, transfer.sourceEntry)}",
                                color = if (destinationDirectory.trim().isBlank()) DeckColors.Red else DeckColors.SecondaryText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Files and folders are copied through local cache.",
                        color = DeckColors.SecondaryText,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = destinationReady, onClick = onConfirm) {
                Text("Copy", color = if (destinationReady) DeckColors.Cyan else DeckColors.SecondaryText, fontWeight = FontWeight.Black)
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
private fun SftpDiscardTextEditDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discard changes?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Text(
                "Unsaved edits to $fileName will be lost.",
                color = DeckColors.SecondaryText,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = DeckColors.Red, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep editing")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun SftpActionDialog(
    action: PendingSftpAction,
    text: String,
    onTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val destructive = action.action == "Delete"
    val cleanName = text.trim()
    val nameRequired = action.action == "Rename" || action.action == "New Folder"
    val invalidName = nameRequired && (cleanName.isBlank() || cleanName.contains('/') || cleanName.contains('\\'))
    val invalidMode = action.action == "Chmod" && SftpPermissionModePolicy.parseOctalMode(text) == null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (action.action) {
                    "Rename" -> "Rename ${action.entry?.name ?: "item"}"
                    "Delete" -> "Delete ${action.entry?.name ?: "item"}?"
                    "Chmod" -> "Change permissions"
                    else -> "New Folder"
                },
                color = DeckColors.PrimaryText,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column {
                if (action.action == "Rename" || action.action == "New Folder" || action.action == "Chmod") {
                    OutlinedTextField(
                        value = text,
                        onValueChange = onTextChange,
                        singleLine = true,
                        isError = invalidName || invalidMode,
                        shape = RoundedCornerShape(18.dp),
                        colors = sftpTextFieldColors(),
                        label = {
                            Text(
                                when (action.action) {
                                    "Rename" -> "Name"
                                    "Chmod" -> "Octal mode"
                                    else -> "Folder name"
                                }
                            )
                        },
                        supportingText = if (invalidName || invalidMode) {
                            { Text(if (invalidMode) "Use 3 or 4 octal digits, such as 644 or 0755." else "Enter a name without path separators.") }
                        } else {
                            null
                        }
                    )
                } else {
                    Text("This remote item will be removed from the server.", color = DeckColors.SecondaryText)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !invalidName && !invalidMode, onClick = onConfirm) {
                Text(if (destructive) "Delete" else "Done", color = if (destructive) DeckColors.Red else DeckColors.PrimaryText, fontWeight = FontWeight.Black)
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
private fun SftpBookmarkDialog(
    action: PendingSftpBookmarkAction,
    label: String,
    onLabelChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val cleanLabel = label.trim()
    val invalidLabel = action.action == "Rename" && cleanLabel.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (action.action == "Delete") "Remove bookmark?" else "Rename bookmark",
                color = DeckColors.PrimaryText,
                fontWeight = FontWeight.Black
            )
        },
        text = {
            Column {
                Text(
                    action.bookmark.path,
                    color = DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (action.action == "Rename") {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = onLabelChange,
                        singleLine = true,
                        isError = invalidLabel,
                        shape = RoundedCornerShape(22.dp),
                        colors = sftpTextFieldColors(),
                        label = { Text("Bookmark name") },
                        supportingText = if (invalidLabel) {
                            { Text("Enter a visible bookmark name.") }
                        } else {
                            null
                        }
                    )
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This only removes the shortcut. Remote files are not changed.",
                        color = DeckColors.SecondaryText,
                        fontSize = 13.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !invalidLabel,
                onClick = if (action.action == "Delete") onDelete else onRename
            ) {
                Text(
                    if (action.action == "Delete") "Remove" else "Rename",
                    color = if (action.action == "Delete") DeckColors.Red else DeckColors.Cyan,
                    fontWeight = FontWeight.Black
                )
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (action.action == "Rename") {
                    TextButton(onClick = onDelete) {
                        Text("Remove", color = DeckColors.Red, fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun SftpEntryRow(
    entry: SftpEntry,
    chmodAvailable: Boolean,
    rcloneAdvancedAvailable: Boolean,
    onOpen: () -> Unit,
    onOpenText: () -> Unit,
    onDownload: () -> Unit,
    onUpload: () -> Unit,
    onChmod: () -> Unit,
    onRename: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyToHost: () -> Unit,
    onPublicLink: () -> Unit,
    onDirectorySize: () -> Unit,
    onRemoteSpace: () -> Unit,
    onDelete: () -> Unit
) {
    var actionsOpen by remember(entry.path) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted.copy(alpha = if (entry.navigable) 0.92f else 0.72f))
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileGlyph(entry)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(entry.name, color = DeckColors.PrimaryText, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                entry.fileManagerMeta(),
                color = DeckColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        FileRowAction("more", DeckColors.SecondaryText, onClick = { actionsOpen = true })
    }
    Spacer(Modifier.height(8.dp))
    if (actionsOpen) {
        SftpEntryActionSheet(
            entry = entry,
            chmodAvailable = chmodAvailable,
            rcloneAdvancedAvailable = rcloneAdvancedAvailable,
            onDismiss = { actionsOpen = false },
            onOpenText = {
                actionsOpen = false
                onOpenText()
            },
            onPrimary = {
                actionsOpen = false
                if (entry.navigable) onOpen() else onDownload()
            },
            onDownload = {
                actionsOpen = false
                onDownload()
            },
            onUpload = {
                actionsOpen = false
                onUpload()
            },
            onChmod = {
                actionsOpen = false
                onChmod()
            },
            onRename = {
                actionsOpen = false
                onRename()
            },
            onCopyPath = {
                actionsOpen = false
                onCopyPath()
            },
            onCopyToHost = {
                actionsOpen = false
                onCopyToHost()
            },
            onPublicLink = {
                actionsOpen = false
                onPublicLink()
            },
            onDirectorySize = {
                actionsOpen = false
                onDirectorySize()
            },
            onRemoteSpace = {
                actionsOpen = false
                onRemoteSpace()
            },
            onDelete = {
                actionsOpen = false
                onDelete()
            }
        )
    }
}

@Composable
private fun SftpEntryActionSheet(
    entry: SftpEntry,
    chmodAvailable: Boolean,
    rcloneAdvancedAvailable: Boolean,
    onDismiss: () -> Unit,
    onOpenText: () -> Unit,
    onPrimary: () -> Unit,
    onDownload: () -> Unit,
    onUpload: () -> Unit,
    onChmod: () -> Unit,
    onRename: () -> Unit,
    onCopyPath: () -> Unit,
    onCopyToHost: () -> Unit,
    onPublicLink: () -> Unit,
    onDirectorySize: () -> Unit,
    onRemoteSpace: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {},
        title = {
            Column {
                Text(entry.name, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.fileManagerMeta(), color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!entry.navigable && SftpTextFilePolicy.canEdit(entry)) {
                    SftpSheetAction("Open text", DeckColors.Cyan, onOpenText)
                }
                SftpSheetAction(if (entry.navigable) "Open" else "Download", DeckColors.Cyan, onPrimary)
                if (entry.type == SftpEntryType.Symlink) {
                    SftpSheetAction("Download", DeckColors.PrimaryText, onDownload)
                }
                SftpSheetAction(if (entry.navigable) "Upload here" else "Upload alongside", DeckColors.Green, onUpload)
                if (SftpHostTransferPolicy.canCopy(entry)) {
                    SftpSheetAction("Copy to host", DeckColors.PrimaryText, onCopyToHost)
                }
                if (rcloneAdvancedAvailable) {
                    SftpSheetAction("Public link", DeckColors.Cyan, onPublicLink)
                    if (entry.directory) {
                        SftpSheetAction("Directory size", DeckColors.PrimaryText, onDirectorySize)
                    }
                    SftpSheetAction("Remote space", DeckColors.PrimaryText, onRemoteSpace)
                }
                SftpSheetAction("Copy path", DeckColors.PrimaryText, onCopyPath)
                if (chmodAvailable) {
                    SftpSheetAction("Permissions", DeckColors.PrimaryText, onChmod)
                }
                SftpSheetAction("Rename", DeckColors.PrimaryText, onRename)
                SftpSheetAction("Delete", DeckColors.Red, onDelete)
                SftpSheetAction("Cancel", DeckColors.SecondaryText, onDismiss)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun SftpSheetAction(
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontSize = 14.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
        SftpToolbarGlyph("chevron-right", color, Modifier.size(15.dp))
    }
}

@Composable
private fun FileRowAction(
    text: String,
    color: Color,
    contentDescription: String = text,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(DeckColors.SurfaceRaised)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (text in sftpGlyphSymbols) {
            SftpToolbarGlyph(text, color, Modifier.size(17.dp))
        } else {
            Text(text, color = color, fontSize = 17.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

private val sftpGlyphSymbols = setOf(
    "sftp-open",
    "refresh",
    "eye-on",
    "eye-off",
    "bookmark",
    "bookmark-on",
    "upload",
    "download",
    "new-folder",
    "scp",
    "sort-up",
    "sort-down",
    "terminal",
    "identity",
    "trust",
    "remove",
    "more",
    "chevron-right",
    "chevron-up",
    "chevron-down"
)

@Composable
private fun SftpToolbarGlyph(symbol: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        when (symbol) {
            "sftp-open" -> drawSftpOpenGlyph(color, stroke)
            "refresh" -> {
                drawArc(color, startAngle = 35f, sweepAngle = 285f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.18f), size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.20f), androidx.compose.ui.geometry.Offset(size.width * 0.84f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.20f), androidx.compose.ui.geometry.Offset(size.width * 0.56f, size.height * 0.25f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "eye-on", "eye-off" -> {
                drawOval(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.30f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.40f), style = stroke)
                drawCircle(color, radius = size.minDimension * 0.08f, center = center)
                if (symbol == "eye-off") {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.80f), androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.20f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "bookmark", "bookmark-on" -> {
                val path = Path().apply {
                    moveTo(size.width * 0.30f, size.height * 0.17f)
                    lineTo(size.width * 0.70f, size.height * 0.17f)
                    lineTo(size.width * 0.70f, size.height * 0.84f)
                    lineTo(size.width * 0.50f, size.height * 0.68f)
                    lineTo(size.width * 0.30f, size.height * 0.84f)
                    close()
                }
                if (symbol == "bookmark-on") drawPath(path, color.copy(alpha = 0.22f), style = Fill)
                drawPath(path, color, style = stroke)
                if (symbol == "bookmark-on") {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.44f), androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.52f), androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "upload", "download" -> {
                val upward = symbol == "upload"
                val shaftTop = if (upward) size.height * 0.22f else size.height * 0.18f
                val shaftBottom = if (upward) size.height * 0.78f else size.height * 0.74f
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, shaftTop), androidx.compose.ui.geometry.Offset(center.x, shaftBottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val headY = if (upward) shaftTop else shaftBottom
                val wingY = if (upward) headY + size.height * 0.16f else headY - size.height * 0.16f
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, headY), androidx.compose.ui.geometry.Offset(size.width * 0.34f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, headY), androidx.compose.ui.geometry.Offset(size.width * 0.66f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "new-folder" -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.34f), size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.44f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.34f), androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.46f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.39f, size.height * 0.57f), androidx.compose.ui.geometry.Offset(size.width * 0.61f, size.height * 0.57f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "scp" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.26f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.74f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "sort-up", "sort-down" -> {
                listOf(0.32f, 0.50f, 0.68f).forEachIndexed { index, y ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * y), androidx.compose.ui.geometry.Offset(size.width * (0.52f + index * 0.10f), size.height * y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                val x = size.width * 0.78f
                val top = if (symbol == "sort-up") size.height * 0.26f else size.height * 0.74f
                val bottom = if (symbol == "sort-up") size.height * 0.74f else size.height * 0.26f
                drawLine(color, androidx.compose.ui.geometry.Offset(x, bottom), androidx.compose.ui.geometry.Offset(x, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
                val wingY = if (symbol == "sort-up") top + size.height * 0.13f else top - size.height * 0.13f
                drawLine(color, androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Offset(x - size.width * 0.10f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Offset(x + size.width * 0.10f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "terminal" -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.22f), size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.42f), androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.58f), androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.54f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "identity", "trust" -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.42f), size = androidx.compose.ui.geometry.Size(size.width * 0.52f, size.height * 0.34f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawArc(color, startAngle = 205f, sweepAngle = 130f, useCenter = false, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.33f, size.height * 0.18f), size = androidx.compose.ui.geometry.Size(size.width * 0.34f, size.height * 0.42f), style = stroke)
                if (symbol == "trust") {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.58f), androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.67f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.48f, size.height * 0.67f), androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "remove" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.70f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "more" -> listOf(0.30f, 0.50f, 0.70f).forEach { x ->
                drawCircle(color, radius = size.minDimension * 0.075f, center = androidx.compose.ui.geometry.Offset(size.width * x, center.y))
            }
            "chevron-right" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.24f), androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "chevron-up" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.36f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "chevron-down" -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.64f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun SftpOpenGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        drawSftpOpenGlyph(color, stroke)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSftpOpenGlyph(
    color: Color,
    stroke: androidx.compose.ui.graphics.drawscope.Stroke
) {
    val path = Path().apply {
        moveTo(size.width * 0.50f, size.height * 0.16f)
        lineTo(size.width * 0.80f, size.height * 0.33f)
        lineTo(size.width * 0.80f, size.height * 0.67f)
        lineTo(size.width * 0.50f, size.height * 0.84f)
        lineTo(size.width * 0.20f, size.height * 0.67f)
        lineTo(size.width * 0.20f, size.height * 0.33f)
        close()
    }
    drawPath(path, color, style = stroke)
}

@Composable
private fun FileGlyph(directory: Boolean) {
    val color = if (directory) DeckColors.Cyan else DeckColors.Green
    Canvas(Modifier.size(26.dp)) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        if (directory) {
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, size.height * 0.28f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                style = stroke
            )
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.13f, size.height * 0.28f), androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height * 0.18f), androidx.compose.ui.geometry.Offset(size.width * 0.42f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        } else {
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.12f),
                size = androidx.compose.ui.geometry.Size(size.width * 0.56f, size.height * 0.76f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = stroke
            )
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.42f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.58f), androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun FileGlyph(entry: SftpEntry) {
    Box {
        FileGlyph(directory = entry.directory)
        if (entry.type == SftpEntryType.Symlink) {
            Canvas(Modifier.size(26.dp)) {
                val color = DeckColors.Orange
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.78f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.20f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.61f, size.height * 0.20f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.20f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.20f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

private fun Long.fileSizeLabel(): String {
    return MetricFormatters.bytesLabel(this)
}

private fun SftpEntry.fileManagerMeta(): String {
    val kind = when (type) {
        SftpEntryType.Directory -> "Folder"
        SftpEntryType.Symlink -> "Link"
        SftpEntryType.File -> sizeBytes.fileSizeLabel()
    }
    val modified = modifiedEpochMillis.takeIf { it > 0L }?.remoteDateLabel()
    return listOfNotNull(kind, permissionsLabel(), modified).joinToString(" | ")
}

private fun Long.remoteDateLabel(): String {
    return SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(this))
}

internal fun activeTransferFor(transfers: List<TransferRecord>, serverId: String): TransferRecord? {
    return transfers
        .filter { it.serverId == serverId && (it.state == TransferRecordState.Running || it.state == TransferRecordState.Queued) }
        .maxByOrNull { it.updatedAtEpochMillis }
}

@Composable
private fun ActiveTransferBar(transfer: TransferRecord) {
    val color = if (transfer.direction == TransferDirection.Upload) DeckColors.Orange else DeckColors.Cyan
    val progress = transfer.progress.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DeckColors.TerminalPanel)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (transfer.direction == TransferDirection.Upload) "Uploading" else "Downloading",
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.width(86.dp)
            )
            Text(
                transfer.localDisplayName,
                color = DeckColors.PrimaryText,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DeckColors.Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.takeIf { it > 0f } ?: 0.02f)
                    .background(color)
            )
        }
    }
}

@Composable
private fun QueueSection(
    transfers: List<TransferRecord>,
    onCancelTransfer: (String) -> Unit,
    onClearFinishedTransfers: () -> Unit
) {
    var showAll by remember { mutableStateOf(false) }
    val sortedTransfers = transfers.sortedByDescending { it.updatedAtEpochMillis }
    val visibleTransfers = if (showAll) sortedTransfers else sortedTransfers.take(8)
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Transfer Queue", color = DeckColors.PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (transfers.any { it.state == TransferRecordState.Complete || it.state == TransferRecordState.Failed || it.state == TransferRecordState.Cancelled }) {
                Text(
                    "Clear",
                    color = DeckColors.Cyan,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(DeckColors.SurfaceMuted)
                        .clickable(onClick = onClearFinishedTransfers)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        if (transfers.isEmpty()) {
            EmptyState("No transfers", "Upload, download, or use Copy to host from an SFTP file action.")
        } else {
            visibleTransfers.forEach { transfer ->
                TransferQueueRow(transfer, onCancelTransfer)
                Spacer(Modifier.height(8.dp))
            }
            if (sortedTransfers.size > 8) {
                TextButton(onClick = { showAll = !showAll }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (showAll) "Show latest 8" else "Show all ${sortedTransfers.size}", fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun TransferQueueRow(transfer: TransferRecord, onCancelTransfer: (String) -> Unit) {
    val activeColor = when (transfer.state) {
        TransferRecordState.Complete -> DeckColors.Green
        TransferRecordState.Running -> DeckColors.Cyan
        TransferRecordState.Failed -> DeckColors.Red
        TransferRecordState.Cancelled -> DeckColors.SecondaryText
        TransferRecordState.Queued -> DeckColors.Orange
    }
    val progress = transfer.progress.coerceIn(0f, 1f)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.width(42.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                SftpToolbarGlyph(
                    if (transfer.direction == TransferDirection.Upload) "upload" else "download",
                    activeColor,
                    Modifier.size(18.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(transfer.localDisplayName, color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(transfer.remotePath, color = DeckColors.SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(transfer.state.name, color = activeColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
                if (transfer.state == TransferRecordState.Queued || transfer.state == TransferRecordState.Running) {
                    Text(
                        "Cancel",
                        color = DeckColors.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onCancelTransfer(transfer.id) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(DeckColors.Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.takeIf { it > 0f } ?: if (transfer.state == TransferRecordState.Complete) 1f else 0.02f)
                    .background(activeColor)
            )
        }
        if (transfer.message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(transfer.message, color = DeckColors.SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun CredentialsSection(
    credentials: List<Credential>,
    onDeleteCredential: (Credential) -> Unit,
    onRenameCredential: (Credential, String) -> Unit,
    onUnlinkCredential: (Credential) -> Unit,
    onReplaceCredentialSecret: (Credential, String, String, Boolean) -> Unit,
    onAddPrivateKeyCredential: (String, String, String, Boolean) -> Unit,
    onUpsertCredential: (Credential) -> Unit,
    onLoadCredentialPayload: suspend (Credential) -> String,
    preselectedCredentialId: String? = null,
    onImportCredentialLink: () -> Unit,
    onCopyCredentialLink: (Credential) -> Unit,
    onShareCredentialLink: (Credential) -> Unit,
    onShowCredentialQr: (Credential) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var vaultFilter by remember { mutableStateOf(CredentialVaultFilter.All) }
    val allCredentials = remember(credentials) { visibleCredentialsForVault(credentials, CredentialVaultFilter.All) }
    val visibleCredentials = remember(credentials, vaultFilter) { visibleCredentialsForVault(credentials, vaultFilter) }
    var selectedCredential by remember(preselectedCredentialId, allCredentials) {
        mutableStateOf(preselectedCredentialId?.let { id -> allCredentials.firstOrNull { it.id == id } })
    }
    var revealedPayload by remember { mutableStateOf<String?>(null) }
    var payloadError by remember { mutableStateOf<String?>(null) }
    var pendingExportPayload by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingDeleteCredential by remember { mutableStateOf<Credential?>(null) }
    var pendingRenameCredential by remember { mutableStateOf<Credential?>(null) }
    var pendingMetadataCredential by remember { mutableStateOf<Credential?>(null) }
    var pendingUnlinkCredential by remember { mutableStateOf<Credential?>(null) }
    var pendingReplaceCredential by remember { mutableStateOf<Credential?>(null) }
    var addingPrivateKey by remember { mutableStateOf(false) }
    var pendingSecretAction by remember { mutableStateOf<PendingVaultSecretAction?>(null) }
    var expandedCredentialId by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val export = pendingExportPayload
        pendingExportPayload = null
        if (uri == null || export == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(export.second.toByteArray(Charsets.UTF_8))
            } ?: error("Unable to create export file.")
        }.onFailure { payloadError = "Export failed: ${it.message ?: it::class.java.simpleName}" }
    }

    fun loadPayload(credential: Credential, onLoaded: (String) -> Unit = {}) {
        payloadError = null
        scope.launch {
            try {
                val payload = onLoadCredentialPayload(credential)
                revealedPayload = payload
                onLoaded(payload)
            } catch (error: Exception) {
                payloadError = error.message ?: error::class.java.simpleName
            }
        }
    }

    fun exportSecret(credential: Credential) {
        val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Export)
        loadPayload(credential) { payload ->
            pendingExportPayload = policy.fileName to payload
            exportLauncher.launch(policy.fileName)
        }
    }

    fun shareSecret(credential: Credential) {
        val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Share)
        loadPayload(credential) { payload ->
            val shareDir = File(context.cacheDir, "vault-shares").apply { mkdirs() }
            val shareFile = File(shareDir, policy.fileName).apply { writeText(payload, Charsets.UTF_8) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", shareFile)
            val share = Intent(Intent.ACTION_SEND).apply {
                type = policy.mimeType
                putExtra(Intent.EXTRA_SUBJECT, credential.label)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(share, "Share ${credential.label}"))
        }
    }

    if (allCredentials.isEmpty()) {
        EmptyState("No identities", "Add a private key here, or save a password/private key from a host editor.")
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HostAction("Add key") { addingPrivateKey = true }
            HostAction("Import link") { onImportCredentialLink() }
        }
        if (addingPrivateKey) {
            AddPrivateKeyCredentialDialog(
                onDismiss = { addingPrivateKey = false },
                onConfirm = { label, secret, passphrase, savePassphrase ->
                    addingPrivateKey = false
                    onAddPrivateKeyCredential(label, secret, passphrase, savePassphrase)
                }
            )
        }
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        HostAction("Add key") { addingPrivateKey = true }
        HostAction("Import link") { onImportCredentialLink() }
    }
    Spacer(Modifier.height(10.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CredentialVaultFilter.entries.forEach { filter ->
            SoftPill(filter.label, selected = vaultFilter == filter, color = filter.color()) {
                vaultFilter = filter
                expandedCredentialId = null
                revealedPayload = null
                payloadError = null
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (visibleCredentials.isEmpty()) {
        EmptyState("No matching identities", "Adjust the vault filter to show more saved credentials.")
        Spacer(Modifier.height(12.dp))
    }
    visibleCredentials.forEach { credential ->
        var confirmDelete by remember(credential.id) { mutableStateOf(false) }
        val expanded = expandedCredentialId == credential.id
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 20.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        expandedCredentialId = if (expanded) null else credential.id
                        revealedPayload = null
                        payloadError = null
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 2.dp)
                ) {
                    Text(credential.label, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(credential.type.label(), color = DeckColors.SecondaryText, fontSize = 12.sp)
                }
                SftpToolbarGlyph(if (expanded) "chevron-up" else "chevron-down", DeckColors.SecondaryText, Modifier.size(18.dp))
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text(credential.publicKeyPreview ?: "Saved credential", color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val exportablePublicKey = VaultPublicKeyPolicy.exportablePublicKey(credential.publicKeyPreview)
                    MiniTag(if (credential.passphraseRef != null) "Passphrase saved" else "No saved passphrase", if (credential.passphraseRef != null) DeckColors.Green else DeckColors.SecondaryText)
                    if (!credential.secretBacked) MiniTag("Needs secret", DeckColors.Orange)
                    if (credential.favorite) MiniTag("Favorite", DeckColors.Orange)
                    credential.group.trim().takeIf { it.isNotBlank() }?.let { MiniTag(it, DeckColors.Purple) }
                    credential.tags.take(2).forEach { tag -> MiniTag(tag, DeckColors.SecondaryText) }
                    expandedCredentialActionLabels(credential).forEach { action ->
                        when (action) {
                            "Details" -> HostAction(action) {
                                selectedCredential = credential
                                revealedPayload = null
                                payloadError = null
                            }
                            "Validate" -> HostAction(action) { loadPayload(credential) }
                            "Copy" -> HostAction(action) {
                                val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Copy)
                                if (policy.requiresConfirmation) {
                                    pendingSecretAction = PendingVaultSecretAction(credential, VaultSecretAction.Copy)
                                } else {
                                    loadPayload(credential) { payload -> clipboard.setText(AnnotatedString(payload)) }
                                }
                            }
                            "Copy Pub" -> HostAction(action) {
                                exportablePublicKey?.let { clipboard.setText(AnnotatedString(it)) }
                            }
                            "Export Pub" -> HostAction(action) {
                                exportablePublicKey?.let { publicKey ->
                                    val name = "${credential.label.safeFileName()}.pub"
                                    pendingExportPayload = name to publicKey
                                    exportLauncher.launch(name)
                                }
                            }
                            "Export" -> HostAction(action) {
                                val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Export)
                                if (policy.requiresConfirmation) {
                                    pendingSecretAction = PendingVaultSecretAction(credential, VaultSecretAction.Export)
                                } else {
                                    exportSecret(credential)
                                }
                            }
                            "Share" -> HostAction(action) {
                                val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Share)
                                if (policy.requiresConfirmation) {
                                    pendingSecretAction = PendingVaultSecretAction(credential, VaultSecretAction.Share)
                                } else {
                                    shareSecret(credential)
                                }
                            }
                            "Rename" -> HostAction(action) { pendingRenameCredential = credential }
                            "Organize" -> HostAction(action) { pendingMetadataCredential = credential }
                            "Unlink" -> HostAction(action) { pendingUnlinkCredential = credential }
                            "Replace" -> HostAction(action) { pendingReplaceCredential = credential }
                            "Copy Link" -> HostAction(action) { onCopyCredentialLink(credential) }
                            "Share Link" -> HostAction(action) { onShareCredentialLink(credential) }
                            "QR" -> HostAction(action) { onShowCredentialQr(credential) }
                            "Remove" -> HostAction(action) { confirmDelete = true }
                        }
                    }
                }
            }
        }
        if (confirmDelete) {
            DeleteCredentialDialog(
                credential = credential,
                onDismiss = { confirmDelete = false },
                onConfirm = {
                    confirmDelete = false
                    onDeleteCredential(credential)
                }
            )
        }
        Spacer(Modifier.height(12.dp))
    }
    selectedCredential?.let { credential ->
        val exportablePublicKey = VaultPublicKeyPolicy.exportablePublicKey(credential.publicKeyPreview)
        CredentialDetailDialog(
            credential = credential,
            payload = revealedPayload,
            error = payloadError,
            onDismiss = {
                selectedCredential = null
                revealedPayload = null
                payloadError = null
            },
            onReveal = { loadPayload(credential) },
            onCopy = {
                loadPayload(credential) { payload ->
                    clipboard.setText(AnnotatedString(payload))
                }
            },
            onCopyPublic = exportablePublicKey?.let { publicKey ->
                {
                    clipboard.setText(AnnotatedString(publicKey))
                }
            },
            onExportPublic = exportablePublicKey?.let { publicKey ->
                {
                    val name = "${credential.label.safeFileName()}.pub"
                    pendingExportPayload = name to publicKey
                    exportLauncher.launch(name)
                }
            },
            onExport = {
                val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Export)
                if (policy.requiresConfirmation) {
                    pendingSecretAction = PendingVaultSecretAction(credential, VaultSecretAction.Export)
                } else {
                    exportSecret(credential)
                }
            },
            onShare = {
                val policy = VaultSecretExportPolicy.policyFor(credential, VaultSecretAction.Share)
                if (policy.requiresConfirmation) {
                    pendingSecretAction = PendingVaultSecretAction(credential, VaultSecretAction.Share)
                } else {
                    shareSecret(credential)
                }
            },
            onDelete = {
                selectedCredential = null
                pendingDeleteCredential = credential
            },
            onClearPayload = {
                revealedPayload = null
                payloadError = null
            },
            onRename = {
                selectedCredential = null
                pendingRenameCredential = credential
            },
            onOrganize = {
                selectedCredential = null
                pendingMetadataCredential = credential
            },
            onUnlink = {
                selectedCredential = null
                pendingUnlinkCredential = credential
            },
            onReplace = {
                selectedCredential = null
                pendingReplaceCredential = credential
            }
        )
    }
    pendingRenameCredential?.let { credential ->
        RenameCredentialDialog(
            credential = credential,
            onDismiss = { pendingRenameCredential = null },
            onConfirm = { nextLabel ->
                pendingRenameCredential = null
                onRenameCredential(credential, nextLabel)
            }
        )
    }
    pendingMetadataCredential?.let { credential ->
        CredentialMetadataDialog(
            credential = credential,
            onDismiss = { pendingMetadataCredential = null },
            onConfirm = { group, tags, notes, favorite ->
                pendingMetadataCredential = null
                onUpsertCredential(credential.withMetadata(group, tags, notes, favorite))
            }
        )
    }
    pendingReplaceCredential?.let { credential ->
        ReplaceCredentialSecretDialog(
            credential = credential,
            onDismiss = { pendingReplaceCredential = null },
            onConfirm = { secret, passphrase, savePassphrase ->
                pendingReplaceCredential = null
                onReplaceCredentialSecret(credential, secret, passphrase, savePassphrase)
            }
        )
    }
    if (addingPrivateKey) {
        AddPrivateKeyCredentialDialog(
            onDismiss = { addingPrivateKey = false },
            onConfirm = { label, secret, passphrase, savePassphrase ->
                addingPrivateKey = false
                onAddPrivateKeyCredential(label, secret, passphrase, savePassphrase)
            }
        )
    }
    pendingUnlinkCredential?.let { credential ->
        UnlinkCredentialDialog(
            credential = credential,
            onDismiss = { pendingUnlinkCredential = null },
            onConfirm = {
                pendingUnlinkCredential = null
                onUnlinkCredential(credential)
            }
        )
    }
    pendingSecretAction?.let { action ->
        ConfirmVaultSecretActionDialog(
            action = action,
            onDismiss = { pendingSecretAction = null },
            onConfirm = {
                pendingSecretAction = null
                when (action.action) {
                    VaultSecretAction.Export -> exportSecret(action.credential)
                    VaultSecretAction.Share -> shareSecret(action.credential)
                    VaultSecretAction.Copy -> loadPayload(action.credential) { payload ->
                        clipboard.setText(AnnotatedString(payload))
                    }
                }
            }
        )
    }
    pendingDeleteCredential?.let { credential ->
        DeleteCredentialDialog(
            credential = credential,
            onDismiss = { pendingDeleteCredential = null },
            onConfirm = {
                pendingDeleteCredential = null
                onDeleteCredential(credential)
            }
        )
    }
}

@Composable
private fun AddPrivateKeyCredentialDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var label by remember { mutableStateOf("Private key identity") }
    var secret by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var savePassphrase by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val cleanSecret = secret.trim()
    fun setPrivateKey(nextSecret: String, resetPassphrase: Boolean = false) {
        val keyInfo = KeyMaterialInspector.inspectPrivateKey(nextSecret.trim())
        secret = nextSecret
        if (resetPassphrase) {
            passphrase = ""
            savePassphrase = false
        }
        error = keyInfo.takeUnless { it.valid }?.summary
    }
    val keyImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openInputStream(uri)?.use(KeyMaterialInspector::readPrivateKeyText).orEmpty()
        }.onSuccess { imported ->
            setPrivateKey(imported)
        }.onFailure { failure ->
            error = "Key import failed: ${failure.message ?: failure::class.java.simpleName}"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add private key", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Key name") }
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HostAction("Import") {
                        keyImportLauncher.launch(arrayOf("*/*", "application/octet-stream", "text/plain"))
                    }
                    HostAction("Paste") {
                        val pasted = clipboard.getText()?.text.orEmpty()
                        if (pasted.isBlank()) {
                            error = "Clipboard does not contain a private key."
                        } else {
                            setPrivateKey(pasted)
                        }
                    }
                    HostAction("Ed25519") {
                        setPrivateKey(SshKeyGenerator.ed25519(label.ifBlank { "chronossh" }).privateKeyPem, resetPassphrase = true)
                    }
                    HostAction("RSA") {
                        setPrivateKey(SshKeyGenerator.rsa(label.ifBlank { "chronossh" }).privateKeyPem, resetPassphrase = true)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it
                        error = null
                    },
                    minLines = 6,
                    maxLines = 10,
                    label = { Text("Private key") }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text("Passphrase") },
                    placeholder = { Text("Optional") }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { savePassphrase = !savePassphrase }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (savePassphrase) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
                            .border(1.dp, DeckColors.CardStroke, CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Save passphrase", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DeckColors.Red, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanSecret.isNotBlank(),
                onClick = {
                    val keyInfo = KeyMaterialInspector.inspectPrivateKey(cleanSecret)
                    if (!keyInfo.valid) {
                        error = keyInfo.summary
                    } else {
                        onConfirm(label, cleanSecret, passphrase, savePassphrase)
                    }
                }
            ) {
                Text("Save", color = if (cleanSecret.isBlank()) DeckColors.SecondaryText else DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

private data class PendingVaultSecretAction(
    val credential: Credential,
    val action: VaultSecretAction
)

@Composable
private fun ConfirmVaultSecretActionDialog(
    action: PendingVaultSecretAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val policy = VaultSecretExportPolicy.policyFor(action.credential, action.action)
    val verb = when (action.action) {
        VaultSecretAction.Export -> "Export"
        VaultSecretAction.Share -> "Share"
        VaultSecretAction.Copy -> "Copy"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$verb secret?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(action.credential.label, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(policy.warning, color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 19.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(verb, color = DeckColors.Red, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun CredentialDetailDialog(
    credential: Credential,
    payload: String?,
    error: String?,
    onDismiss: () -> Unit,
    onReveal: () -> Unit,
    onCopy: () -> Unit,
    onCopyPublic: (() -> Unit)?,
    onExportPublic: (() -> Unit)?,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClearPayload: () -> Unit,
    onRename: () -> Unit,
    onOrganize: () -> Unit,
    onUnlink: () -> Unit,
    onReplace: () -> Unit
) {
    val privateKeyInfo = if (credential.type == CredentialType.PrivateKey && payload != null) {
        KeyMaterialInspector.inspectPrivateKey(payload)
    } else {
        null
    }
    val privateKeyUsable = privateKeyInfo?.let { it.valid && !it.summary.contains("FIDO", ignoreCase = true) } != false
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(credential.label, color = DeckColors.PrimaryText, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(credentialDetailSubtitle(credential), color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column {
                val preview = when {
                    error != null -> error
                    payload == null && credential.type == CredentialType.Password -> "Tap Reveal to view the password."
                    payload == null && credential.type == CredentialType.PrivateKey -> credential.publicKeyPreview ?: "Tap Validate to check this private key."
                    payload != null && credential.type == CredentialType.Password -> payload
                    privateKeyInfo != null -> if (privateKeyUsable) {
                        "Private key payload valid: ${privateKeyInfo.summary} (${privateKeyInfo.fingerprint})"
                    } else {
                        "Private key is not usable: ${privateKeyInfo.summary}"
                    }
                    payload != null -> payload.take(420)
                    else -> "No payload available."
                }
                Text(
                    preview,
                    color = if (error != null || !privateKeyUsable) DeckColors.Red else DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 14,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (credential.type == CredentialType.Password) HostAction(if (payload == null) "Reveal" else "Hide") {
                        if (payload == null) onReveal() else onClearPayload()
                    }
                    if (credential.type == CredentialType.PrivateKey) HostAction("Validate") { onReveal() }
                    HostAction("Copy") { onCopy() }
                    if (credential.type == CredentialType.PrivateKey && onCopyPublic != null) {
                        HostAction("Copy Pub") { onCopyPublic() }
                    }
                    if (credential.type == CredentialType.PrivateKey && onExportPublic != null) {
                        HostAction("Export Pub") { onExportPublic() }
                    }
                    HostAction("Rename") { onRename() }
                    HostAction("Organize") { onOrganize() }
                    HostAction("Unlink") { onUnlink() }
                    HostAction("Replace") { onReplace() }
                    HostAction("Export") { onExport() }
                    HostAction("Share") { onShare() }
                    HostAction("Delete") { onDelete() }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun UnlinkCredentialDialog(
    credential: Credential,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unlink identity?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Text(
                "This keeps '${credential.label}' in the vault and removes it from every host using it.",
                color = DeckColors.SecondaryText
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Unlink", color = DeckColors.Orange, fontWeight = FontWeight.Black)
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
private fun ReplaceCredentialSecretDialog(
    credential: Credential,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Boolean) -> Unit
) {
    var secret by remember(credential.id) { mutableStateOf("") }
    var passphrase by remember(credential.id) { mutableStateOf("") }
    var savePassphrase by remember(credential.id) { mutableStateOf(credential.passphraseRef != null) }
    var error by remember(credential.id) { mutableStateOf<String?>(null) }
    val cleanSecret = secret.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Replace secret", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(
                    if (credential.type == CredentialType.PrivateKey) {
                        "Paste the full private key. A public key or file path will be rejected."
                    } else {
                        "Enter the replacement password."
                    },
                    color = DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = secret,
                    onValueChange = {
                        secret = it
                        error = null
                    },
                    minLines = if (credential.type == CredentialType.PrivateKey) 5 else 1,
                    maxLines = if (credential.type == CredentialType.PrivateKey) 9 else 1,
                    visualTransformation = if (credential.type == CredentialType.Password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
                    label = { Text(if (credential.type == CredentialType.PrivateKey) "Private key" else "Password") }
                )
                if (credential.type == CredentialType.PrivateKey) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text("Passphrase") },
                        placeholder = { Text("Optional") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { savePassphrase = !savePassphrase }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (savePassphrase) DeckColors.Cyan else DeckColors.SurfaceMuted)
                                .border(1.dp, DeckColors.CardStroke, CircleShape)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Save passphrase", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DeckColors.Red, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanSecret.isNotBlank(),
                onClick = {
                    if (credential.type == CredentialType.PrivateKey) {
                        val keyInfo = KeyMaterialInspector.inspectPrivateKey(cleanSecret)
                        if (!keyInfo.valid) {
                            error = keyInfo.summary
                            return@TextButton
                        }
                    }
                    onConfirm(cleanSecret, passphrase, savePassphrase)
                }
            ) {
                Text("Replace", color = if (cleanSecret.isBlank()) DeckColors.SecondaryText else DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun CredentialMetadataDialog(
    credential: Credential,
    onDismiss: () -> Unit,
    onConfirm: (group: String, tags: String, notes: String, favorite: Boolean) -> Unit
) {
    var group by remember(credential.id) { mutableStateOf(credential.group) }
    var tags by remember(credential.id) { mutableStateOf(credential.tags.joinToString(", ")) }
    var notes by remember(credential.id) { mutableStateOf(credential.notes) }
    var favorite by remember(credential.id) { mutableStateOf(credential.favorite) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Organize identity", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(credential.label, color = DeckColors.PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = group,
                    onValueChange = { group = it.take(64) },
                    singleLine = true,
                    label = { Text("Group") },
                    placeholder = { Text("Production") }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it.take(240) },
                    singleLine = true,
                    label = { Text("Tags") },
                    placeholder = { Text("prod, deploy, root") }
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it.take(2200) },
                    minLines = 3,
                    maxLines = 5,
                    label = { Text("Notes") },
                    placeholder = { Text("Owner, rotation notes, source") }
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { favorite = !favorite }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                    Spacer(Modifier.width(8.dp))
                    Text("Favorite", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(group, tags, notes, favorite) }) {
                Text("Save", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun RenameCredentialDialog(
    credential: Credential,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var label by remember(credential.id) { mutableStateOf(credential.label) }
    val cleanLabel = label.trim()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename identity", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text(credential.type.label(), color = DeckColors.SecondaryText, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    singleLine = true,
                    label = { Text("Identity name") }
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cleanLabel.isNotBlank(),
                onClick = { onConfirm(cleanLabel) }
            ) {
                Text("Rename", color = if (cleanLabel.isBlank()) DeckColors.SecondaryText else DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

@Composable
private fun DeleteCredentialDialog(
    credential: Credential,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove identity?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Text(
                "This deletes '${credential.label}' from the vault and unlinks it from hosts that use it. The host profiles remain.",
                color = DeckColors.SecondaryText
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Remove", color = DeckColors.Red, fontWeight = FontWeight.Black)
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
private fun KnownHostsSection(
    knownHosts: List<KnownHost>,
    servers: List<ServerProfile>,
    onTrustHost: (ServerProfile) -> Unit,
    onDeleteKnownHost: (KnownHost) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<KnownHost?>(null) }
    if (knownHosts.isEmpty()) {
        EmptyState("No trusted hosts", "Scan a host key from a host card or connection prompt.")
        return
    }
    knownHosts.forEach { knownHost ->
        val server = servers.firstOrNull { it.host == knownHost.host && it.port == knownHost.port }
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${knownHost.host}:${knownHost.port}", color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${knownHost.algorithm} · ${knownHost.fingerprint}", color = DeckColors.SecondaryText, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (server != null && !knownHost.trusted) {
                    HostAction(knownHostTrustActionLabel(knownHost)) { onTrustHost(server) }
                }
                if (knownHost.trusted) {
                    Spacer(Modifier.width(8.dp))
                    RemoveIconButton { pendingDelete = knownHost }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    pendingDelete?.let { knownHost ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove trusted key?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "SSH, SFTP, monitoring, and tunnels will ask you to review ${knownHost.host}:${knownHost.port} again.",
                    color = DeckColors.SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        onDeleteKnownHost(knownHost)
                    }
                ) {
                    Text("Remove", color = DeckColors.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Cancel")
                }
            },
            containerColor = DeckColors.Surface,
            titleContentColor = DeckColors.PrimaryText,
            textContentColor = DeckColors.SecondaryText
        )
    }
}

@Composable
private fun KnownHostAuditDialog(
    knownHost: KnownHost,
    onCopy: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${knownHost.host}:${knownHost.port}", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VaultInfoRow("State", knownHostStatusLabel(knownHost), if (knownHost.trusted) DeckColors.Green else DeckColors.Orange)
                VaultInfoRow("Algorithm", knownHost.algorithm, DeckColors.Cyan)
                VaultInfoRow("Fingerprint", knownHost.fingerprint, DeckColors.SecondaryText)
                VaultInfoRow("First seen", knownHostAuditTimeLabel(knownHost.firstSeenEpochMillis), DeckColors.SecondaryText)
                VaultInfoRow("Last seen", knownHostAuditTimeLabel(knownHost.lastSeenEpochMillis), DeckColors.SecondaryText)
            }
        },
        confirmButton = {
            TextButton(onClick = onCopy) {
                Text("Copy", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}

internal fun knownHostStatusLabel(knownHost: KnownHost): String {
    return when (knownHost.trustState) {
        HostKeyTrustState.Trusted -> "TRUST"
        HostKeyTrustState.Changed -> "CHANGED"
        HostKeyTrustState.Rejected -> "REJECTED"
        HostKeyTrustState.Unknown -> if (knownHost.trusted) "TRUST" else "REVIEW"
    }
}

internal fun knownHostStatusSubtitle(knownHost: KnownHost): String {
    return when (knownHost.trustState) {
        HostKeyTrustState.Changed -> "${knownHost.algorithm} · fingerprint changed"
        HostKeyTrustState.Rejected -> "${knownHost.algorithm} · previously rejected"
        else -> knownHost.algorithm
    }
}

internal fun knownHostTrustActionLabel(knownHost: KnownHost): String {
    return when (knownHost.trustState) {
        HostKeyTrustState.Changed, HostKeyTrustState.Rejected -> "Replace Key"
        else -> "Scan & Trust"
    }
}

internal fun knownHostAuditTimeLabel(epochMillis: Long): String {
    if (epochMillis <= 0L) return "Unknown"
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(epochMillis))
}

internal fun knownHostAuditCopyText(knownHost: KnownHost): String {
    return buildString {
        appendLine("${knownHost.host}:${knownHost.port}")
        appendLine("State: ${knownHostStatusLabel(knownHost)}")
        appendLine("Algorithm: ${knownHost.algorithm}")
        appendLine("Fingerprint: ${knownHost.fingerprint}")
        appendLine("First seen: ${knownHostAuditTimeLabel(knownHost.firstSeenEpochMillis)}")
        append("Last seen: ${knownHostAuditTimeLabel(knownHost.lastSeenEpochMillis)}")
    }
}

internal data class ShareImportStatus(
    val imported: Boolean,
    val message: String
)

internal fun importSnippetShareLink(
    payload: String,
    onUpsertSnippet: (Snippet) -> Result<Snippet>
): ShareImportStatus {
    val snippet = HostShareLinkCodec.decodeSnippet(payload)
        ?: return ShareImportStatus(false, "Clipboard does not contain a valid snippet link.")
    return onUpsertSnippet(snippet).fold(
        onSuccess = { imported -> ShareImportStatus(true, "Imported snippet: ${imported.name}") },
        onFailure = { failure -> ShareImportStatus(false, "Snippet import failed: ${failure.message ?: failure::class.java.simpleName}") }
    )
}

internal fun importCredentialShareLink(
    payload: String,
    credentials: List<Credential>,
    onUpsertCredential: (Credential) -> Unit
): ShareImportStatus {
    val credential = HostShareLinkCodec.decodeCredential(payload)
        ?: return ShareImportStatus(false, "Clipboard does not contain a valid identity link.")
    val existing = credentials.firstOrNull { it.id == credential.id }
    if (existing?.secretBacked == true) {
        return ShareImportStatus(false, "Identity import skipped: ${existing.label} already has a saved secret.")
    }
    onUpsertCredential(credential)
    return ShareImportStatus(true, "Imported identity: ${credential.label}")
}

internal fun importForwardShareLink(
    payload: String,
    servers: List<ServerProfile>,
    onUpsertForward: (PortForwardRule) -> Unit
): ShareImportStatus {
    val forward = HostShareLinkCodec.decodePortForward(payload)
        ?: return ShareImportStatus(false, "Clipboard does not contain a valid tunnel link.")
    if (servers.none { it.id == forward.serverId }) {
        return ShareImportStatus(false, "Tunnel import failed: host profile is missing.")
    }
    onUpsertForward(forward.copy(enabled = false, autoStart = false))
    return ShareImportStatus(true, "Imported tunnel: ${forward.routeLabel()}")
}

@Composable
private fun SnippetsSection(
    snippets: List<Snippet>,
    servers: List<ServerProfile>,
    onUpsertSnippet: (Snippet) -> Result<Snippet>,
    onDeleteSnippet: (Snippet) -> Unit,
    onImportSnippetLink: () -> Unit,
    onCopySnippetLink: (Snippet) -> Unit,
    onShareSnippetLink: (Snippet) -> Unit,
    onShowSnippetQr: (Snippet) -> Unit
) {
    var editingSnippet by remember { mutableStateOf<Snippet?>(null) }
    var deletingSnippet by remember { mutableStateOf<Snippet?>(null) }
    var expandedSnippetId by remember { mutableStateOf<String?>(null) }
    editingSnippet?.let { snippet ->
        SnippetEditorPage(
            initial = snippet,
            servers = servers,
            onDismiss = { editingSnippet = null },
            onSave = { draft ->
                onUpsertSnippet(draft).onSuccess { editingSnippet = null }
            }
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HostAction("Import") { onImportSnippetLink() }
            HostAction("Add") { editingSnippet = defaultSnippet() }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (snippets.isEmpty()) {
        EmptyState("No snippets", "Tap Add to save a command for terminal sessions.")
    } else {
        snippets.sortedWith(compareByDescending<Snippet> { it.favorite }.thenBy { it.group.lowercase() }.thenBy { it.name.lowercase() }).forEach { snippet ->
            val scopedServer = snippet.serverScope?.let { scope -> servers.firstOrNull { it.id == scope } }
            val expanded = expandedSnippetId == snippet.id
            DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(18.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { expandedSnippetId = if (expanded) null else snippet.id },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(snippet.name, color = DeckColors.PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (scopedServer != null) {
                            Text(scopedServer.name, color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    SftpToolbarGlyph(if (expanded) "chevron-up" else "chevron-down", DeckColors.SecondaryText, Modifier.size(18.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(snippet.command, color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 18.sp, maxLines = if (expanded) 5 else 1, overflow = TextOverflow.Ellipsis)
                if (expanded) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HostAction("Copy") { onCopySnippetLink(snippet) }
                        HostAction("QR") { onShowSnippetQr(snippet) }
                        HostAction("Share") { onShareSnippetLink(snippet) }
                        HostAction("Edit") { editingSnippet = snippet }
                        HostAction("Delete") { deletingSnippet = snippet }
                    }
                    Spacer(Modifier.height(10.dp))
                    if (snippet.description.isNotBlank()) {
                        Text(snippet.description, color = DeckColors.PrimaryText, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (snippet.favorite) MiniTag("Favorite", DeckColors.Orange)
                        if (snippet.group.isNotBlank()) MiniTag(snippet.group, DeckColors.Purple)
                        if (snippet.autoRun) MiniTag("Auto run", DeckColors.Green)
                        MiniTag(if (snippet.confirmBeforeRun) "Confirm" else "Quick run", if (snippet.confirmBeforeRun) DeckColors.SecondaryText else DeckColors.Green)
                        snippet.tags.forEach { tag -> MiniTag(tag, DeckColors.Cyan) }
                        snippet.variables.forEach { variable -> MiniTag("{{$variable}}", DeckColors.Orange) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    deletingSnippet?.let { snippet ->
        AlertDialog(
            onDismissRequest = { deletingSnippet = null },
            title = { Text("Delete snippet?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "'${snippet.name}' will be removed from the terminal snippet menu.",
                    color = DeckColors.SecondaryText
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingSnippet = null
                        onDeleteSnippet(snippet)
                    }
                ) {
                    Text("Delete", color = DeckColors.Red, fontWeight = FontWeight.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSnippet = null }) {
                    Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = DeckColors.Surface,
            titleContentColor = DeckColors.PrimaryText,
            textContentColor = DeckColors.SecondaryText
        )
    }
}

@Composable
private fun SnippetEditorPage(
    initial: Snippet,
    servers: List<ServerProfile>,
    onDismiss: () -> Unit,
    onSave: (Snippet) -> Result<Snippet>
) {
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var command by remember(initial.id) { mutableStateOf(initial.command) }
    var group by remember(initial.id) { mutableStateOf(initial.group) }
    var favorite by remember(initial.id) { mutableStateOf(initial.favorite) }
    var confirmBeforeRun by remember(initial.id) { mutableStateOf(initial.confirmBeforeRun) }
    var autoRun by remember(initial.id) { mutableStateOf(initial.autoRun) }
    var tags by remember(initial.id) { mutableStateOf(initial.tags.joinToString(", ")) }
    var variables by remember(initial.id) { mutableStateOf(initial.variables.joinToString(", ")) }
    var serverScope by remember(initial.id, servers) { mutableStateOf(initial.serverScope?.takeIf { id -> servers.any { it.id == id } }) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    val draft = Snippet(
        id = initial.id,
        name = name,
        description = description,
        command = command,
        tags = tags.split(',', ' ').map { it.trim() }.filter { it.isNotBlank() },
        serverScope = serverScope,
        variables = variables.split(',', ' ').map { it.trim() }.filter { it.isNotBlank() },
        group = group,
        favorite = favorite,
        confirmBeforeRun = confirmBeforeRun,
        createdAtEpochMillis = initial.createdAtEpochMillis,
        autoRun = autoRun
    )
    BackHandler(onBack = onDismiss)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HostAction("Back") { onDismiss() }
            Text(
                if (initial.name.isBlank()) "New snippet" else "Edit snippet",
                color = DeckColors.PrimaryText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.weight(1f)
            )
            HostAction("Save") {
                val validation = SnippetValidator.validate(draft)
                val normalized = validation.normalized
                if (!validation.valid || normalized == null) {
                    error = validation.errors.joinToString(" ")
                } else {
                    onSave(normalized)
                        .onFailure { failure -> error = failure.message ?: failure::class.java.simpleName }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(18.dp)) {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, minLines = 2, maxLines = 4, label = { Text("Description") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = command, onValueChange = { command = it }, minLines = 3, maxLines = 8, label = { Text("Command") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = group, onValueChange = { group = it }, singleLine = true, label = { Text("Group") }, placeholder = { Text("deploy") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = tags, onValueChange = { tags = it }, singleLine = true, label = { Text("Tags") }, placeholder = { Text("deploy, docker") })
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = variables, onValueChange = { variables = it }, singleLine = true, label = { Text("Variables") }, placeholder = { Text("service, path") })
                Spacer(Modifier.height(10.dp))
                SnippetSwitchRow("Favorite", favorite, "Pin this snippet above the rest") { favorite = it }
                SnippetSwitchRow("Confirm before run", confirmBeforeRun, "Review variables and command before sending") { confirmBeforeRun = it }
                SnippetSwitchRow("Auto run", autoRun, "Send immediately when selected and no variables are required") { autoRun = it }
                Spacer(Modifier.height(10.dp))
                Text("Scope", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SoftPill("All hosts", selected = serverScope == null, color = DeckColors.Cyan) { serverScope = null }
                    servers.forEach { server ->
                        SoftPill(server.name, selected = serverScope == server.id, color = DeckColors.Green) { serverScope = server.id }
                    }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = DeckColors.Red, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun SnippetSwitchRow(label: String, checked: Boolean, subtitle: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun defaultSnippet(): Snippet {
    return Snippet(
        id = "snippet-new-${System.currentTimeMillis()}",
        name = "",
        command = "",
        tags = emptyList(),
        serverScope = null,
        variables = emptyList(),
        description = "",
        confirmBeforeRun = true
    )
}

@Composable
private fun TunnelsSection(
    forwards: List<PortForwardRule>,
    forwardStatuses: Map<String, ForwardStatus>,
    servers: List<ServerProfile>,
    initialServerId: String?,
    initialDraftRequestKey: Int,
    onToggleForward: (PortForwardRule) -> Unit,
    onUpsertForward: (PortForwardRule) -> Unit,
    onDeleteForward: (PortForwardRule) -> Unit,
    onImportForwardLink: () -> Unit,
    onCopyForwardLink: (PortForwardRule) -> Unit,
    onShareForwardLink: (PortForwardRule) -> Unit,
    onShowForwardQr: (PortForwardRule) -> Unit
) {
    val supportedForwards = forwards
    var editingForward by remember { mutableStateOf<PortForwardRule?>(null) }
    var deletingForward by remember { mutableStateOf<PortForwardRule?>(null) }
    var expandedForwardId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialDraftRequestKey) {
        if (initialDraftRequestKey > 0) {
            editingForward = defaultLocalForward(servers, initialServerId)
        }
    }
    editingForward?.let { forward ->
        ForwardEditorPage(
            initial = forward,
            servers = servers,
            onDismiss = { editingForward = null },
            onSave = {
                editingForward = null
                onUpsertForward(it)
            }
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HostAction("Import") { onImportForwardLink() }
            HostAction("Add") {
                editingForward = defaultLocalForward(servers, initialServerId)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (supportedForwards.isEmpty()) {
        EmptyState(
            "No SSH tunnels",
            if (servers.isEmpty()) "Add a host first." else "Tap Add to save an SSH tunnel."
        )
    } else {
        supportedForwards.forEach { forward ->
            val server = servers.firstOrNull { it.id == forward.serverId }
            val status = forwardStatuses[forward.id]
            val expanded = expandedForwardId == forward.id
            TunnelRuleCard(
                forward = forward,
                server = server,
                status = status,
                expanded = expanded,
                onExpandToggle = { expandedForwardId = if (expanded) null else forward.id },
                onToggleForward = onToggleForward,
                onEdit = { editingForward = forward },
                onDelete = { deletingForward = forward },
                showShareActions = true,
                onCopyForwardLink = onCopyForwardLink,
                onShareForwardLink = onShareForwardLink,
                onShowForwardQr = onShowForwardQr
            )
            Spacer(Modifier.height(12.dp))
        }
    }
    deletingForward?.let { forward ->
        ForwardDeleteDialog(
            forward = forward,
            onDismiss = { deletingForward = null },
            onDelete = {
                deletingForward = null
                onDeleteForward(forward)
            }
        )
    }
}

@Composable
fun PortForwardPage(
    serverId: String,
    forwards: List<PortForwardRule>,
    forwardStatuses: Map<String, ForwardStatus>,
    servers: List<ServerProfile>,
    onBack: () -> Unit,
    onToggleForward: (PortForwardRule) -> Unit,
    onUpsertForward: (PortForwardRule) -> Unit,
    onDeleteForward: (PortForwardRule) -> Unit
) {
    val server = servers.firstOrNull { it.id == serverId }
    val hostForwards = portForwardPageForwards(serverId, forwards)
    var editingForward by remember(serverId) { mutableStateOf<PortForwardRule?>(null) }
    var deletingForward by remember(serverId) { mutableStateOf<PortForwardRule?>(null) }
    var expandedForwardId by remember(serverId) { mutableStateOf<String?>(hostForwards.firstOrNull()?.id) }
    LaunchedEffect(hostForwards) {
        expandedForwardId = portForwardPageExpandedId(expandedForwardId, hostForwards)
    }
    BackHandler(onBack = onBack)
    editingForward?.let { forward ->
        ForwardEditorPage(
            initial = forward,
            servers = servers,
            scrollable = true,
            onDismiss = { editingForward = null },
            onSave = {
                editingForward = null
                onUpsertForward(it)
            }
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HostAction("Back") { onBack() }
            Column(Modifier.weight(1f)) {
                Text(
                    "Port forwards",
                    color = DeckColors.PrimaryText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    server?.name ?: serverId,
                    color = if (server == null) DeckColors.Orange else DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            HostAction("Add") {
                editingForward = defaultLocalForward(servers, serverId)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (hostForwards.isEmpty()) {
            EmptyState("No SSH tunnels", "Tap Add to save a tunnel for this host.")
        } else {
            hostForwards.forEach { forward ->
                val status = forwardStatuses[forward.id]
                val expanded = expandedForwardId == forward.id
                TunnelRuleCard(
                    forward = forward,
                    server = server,
                    status = status,
                    expanded = expanded,
                    onExpandToggle = { expandedForwardId = if (expanded) null else forward.id },
                    onToggleForward = onToggleForward,
                    onEdit = { editingForward = forward },
                    onDelete = { deletingForward = forward },
                    showShareActions = false,
                    onCopyForwardLink = {},
                    onShareForwardLink = {},
                    onShowForwardQr = {}
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
    deletingForward?.let { forward ->
        ForwardDeleteDialog(
            forward = forward,
            onDismiss = { deletingForward = null },
            onDelete = {
                deletingForward = null
                onDeleteForward(forward)
            }
        )
    }
}

@Composable
internal fun ForwardEditorPage(
    initial: PortForwardRule,
    servers: List<ServerProfile>,
    scrollable: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (PortForwardRule) -> Unit
) {
    var selectedServerId by remember(initial.id, servers) {
        mutableStateOf(initial.serverId.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull()?.id.orEmpty())
    }
    var localHost by remember(initial.id) { mutableStateOf(initial.localHost.ifBlank { "127.0.0.1" }) }
    var localPort by remember(initial.id) { mutableStateOf(initial.localPort.takeIf { it > 0 }?.toString() ?: "8080") }
    var remoteHost by remember(initial.id) { mutableStateOf(initial.remoteHost.ifBlank { "127.0.0.1" }) }
    var remotePort by remember(initial.id) { mutableStateOf(initial.remotePort.takeIf { it > 0 }?.toString() ?: "80") }
    var type by remember(initial.id) { mutableStateOf(initial.type) }
    var autoStart by remember(initial.id) { mutableStateOf(initial.autoStart) }
    var label by remember(initial.id) { mutableStateOf(initial.label) }
    var group by remember(initial.id) { mutableStateOf(initial.group) }
    var favorite by remember(initial.id) { mutableStateOf(initial.favorite) }
    var error by remember(initial.id) { mutableStateOf<String?>(null) }
    val draft = PortForwardRule(
        id = initial.id.ifBlank { "forward-${System.currentTimeMillis()}" },
        serverId = selectedServerId,
        type = type,
        localHost = localHost.trim(),
        localPort = localPort.toIntOrNull() ?: 0,
        remoteHost = remoteHost.trim(),
        remotePort = remotePort.toIntOrNull() ?: 0,
        enabled = false,
        autoStart = autoStart,
        label = label,
        group = group,
        favorite = favorite
    )
    BackHandler(onBack = onDismiss)
    val pageModifier = if (scrollable) {
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    } else {
        Modifier.fillMaxWidth()
    }
    Column(modifier = pageModifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HostAction("Back") { onDismiss() }
            Column(Modifier.weight(1f)) {
                Text(
                    tunnelEditorTitle(draft.type),
                    color = DeckColors.PrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black
                )
                Text(draft.routeLabel(), color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            HostAction("Save") {
                val validation = PortForwardValidator.validate(draft)
                val normalized = validation.normalized
                if (servers.isEmpty()) {
                    error = "Add a host before saving a tunnel."
                } else if (!validation.valid || normalized == null) {
                    error = validation.errors.joinToString(" ")
                } else {
                    onSave(normalized.copy(enabled = false, autoStart = autoStart))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(18.dp)) {
            Text("Host", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                servers.forEach { server ->
                    SoftPill(server.name, selected = server.id == selectedServerId, color = DeckColors.Cyan) {
                        selectedServerId = server.id
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Type", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoftPill("Local", selected = draft.type == PortForwardType.Local, color = DeckColors.Cyan) { type = PortForwardType.Local }
                SoftPill("Remote", selected = draft.type == PortForwardType.Remote, color = DeckColors.Purple) { type = PortForwardType.Remote }
                SoftPill("SOCKS", selected = draft.type == PortForwardType.DynamicSocks, color = DeckColors.Green) { type = PortForwardType.DynamicSocks }
            }
        }
        Spacer(Modifier.height(12.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(18.dp)) {
            OutlinedTextField(
                value = localHost,
                onValueChange = { localHost = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (draft.type == PortForwardType.Remote) "Local target address" else "Local bind address") }
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = localPort,
                onValueChange = { localPort = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (draft.type == PortForwardType.Remote) "Local target port" else if (draft.type == PortForwardType.DynamicSocks) "SOCKS port" else "Local port") }
            )
            if (draft.type != PortForwardType.DynamicSocks) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remoteHost,
                    onValueChange = { remoteHost = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (draft.type == PortForwardType.Remote) "Remote bind address" else "Remote address") }
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = remotePort,
                    onValueChange = { remotePort = it.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (draft.type == PortForwardType.Remote) "Remote bind port" else "Remote port") }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(18.dp)) {
            OutlinedTextField(value = label, onValueChange = { label = it.take(80) }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Label") })
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = group, onValueChange = { group = it.take(48) }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Group") })
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SoftPill("Auto-start", selected = autoStart, color = DeckColors.Green) { autoStart = !autoStart }
                SoftPill("Favorite", selected = favorite, color = DeckColors.Orange) { favorite = !favorite }
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = DeckColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun TunnelRuleCard(
    forward: PortForwardRule,
    server: ServerProfile?,
    status: ForwardStatus?,
    expanded: Boolean,
    onExpandToggle: () -> Unit,
    onToggleForward: (PortForwardRule) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showShareActions: Boolean,
    onCopyForwardLink: (PortForwardRule) -> Unit,
    onShareForwardLink: (PortForwardRule) -> Unit,
    onShowForwardQr: (PortForwardRule) -> Unit
) {
    val active = status?.active == true
    val failed = status?.lastError != null
    val starting = status?.lastMessage == "Starting tunnel"
    val missingHost = server == null
    val pipColor = when {
        active -> DeckColors.Green
        starting -> DeckColors.Cyan
        failed -> DeckColors.Red
        else -> DeckColors.SecondaryText
    }
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VaultStatusDot(pipColor)
            Spacer(Modifier.size(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onExpandToggle() }
            ) {
                Text(server?.name ?: forward.serverId, color = DeckColors.PrimaryText, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (server == null) "Host profile missing" else forward.type.displayName() + " tunnel",
                    color = if (server == null) DeckColors.Orange else DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.size(8.dp))
            SftpToolbarGlyph(if (expanded) "chevron-up" else "chevron-down", DeckColors.SecondaryText, Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            TunnelActionButton(
                text = if (active) "Stop" else if (starting) "Starting" else "Start",
                enabled = !starting && !missingHost,
                active = active,
                onClick = { onToggleForward(forward) }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(forward.routeLabel(), color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (showShareActions) {
                    HostAction("Copy") { onCopyForwardLink(forward) }
                    HostAction("QR") { onShowForwardQr(forward) }
                    HostAction("Share") { onShareForwardLink(forward) }
                }
                if (!active && !starting) {
                    HostAction("Edit") { onEdit() }
                    HostAction("Delete") { onDelete() }
                } else {
                    Text(
                        "Stop this tunnel before editing or deleting it.",
                        color = DeckColors.SecondaryText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.lastError ?: it.lastMessage,
                    color = if (it.lastError == null) DeckColors.SecondaryText else DeckColors.Orange,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (status == null && !missingHost) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Stopped until Start is tapped.",
                    color = DeckColors.SecondaryText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ForwardDeleteDialog(
    forward: PortForwardRule,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete tunnel?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = { Text(forward.routeLabel(), color = DeckColors.SecondaryText) },
        confirmButton = {
            TextButton(onClick = onDelete) {
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

internal fun portForwardPageForwards(serverId: String, forwards: List<PortForwardRule>): List<PortForwardRule> {
    return forwards.filter { it.serverId == serverId }
}

internal fun portForwardPageExpandedId(currentId: String?, forwards: List<PortForwardRule>): String? {
    return currentId?.takeIf { id -> forwards.any { it.id == id } } ?: forwards.firstOrNull()?.id
}

internal fun tunnelEditorTitle(type: PortForwardType): String {
    return when (type) {
        PortForwardType.Local -> "Local tunnel"
        PortForwardType.Remote -> "Remote tunnel"
        PortForwardType.DynamicSocks -> "SOCKS tunnel"
    }
}

internal fun defaultLocalForward(servers: List<ServerProfile>, preferredServerId: String? = null): PortForwardRule {
    return PortForwardRule(
        id = "forward-new-${System.currentTimeMillis()}",
        serverId = preferredServerId?.takeIf { id -> servers.any { it.id == id } } ?: servers.firstOrNull()?.id.orEmpty(),
        type = PortForwardType.Local,
        localHost = "127.0.0.1",
        localPort = 8080,
        remoteHost = "127.0.0.1",
        remotePort = 80,
        enabled = false,
        autoStart = false
    )
}

private fun String.shortBookmarkLabel(): String {
    val clean = trim().ifBlank { "." }
    return if (clean.length <= 12) clean else clean.take(10) + "..."
}

@Composable
private fun HostAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = DeckColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RemoveIconButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.Red.copy(alpha = 0.10f))
            .border(1.dp, DeckColors.Red.copy(alpha = 0.24f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        SftpToolbarGlyph("remove", DeckColors.Red, Modifier.size(17.dp))
    }
}

@Composable
private fun TunnelActionButton(
    text: String,
    enabled: Boolean,
    active: Boolean,
    onClick: () -> Unit
) {
    val background = when {
        !enabled -> DeckColors.SurfaceMuted
        active -> DeckColors.Red.copy(alpha = 0.12f)
        else -> DeckColors.PrimaryText
    }
    val content = when {
        !enabled -> DeckColors.SecondaryText
        active -> DeckColors.Red
        else -> DeckColors.Surface
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, if (enabled) content.copy(alpha = 0.26f) else DeckColors.CardStroke, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp)
    ) {
        Text(text, color = content, fontSize = 13.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun VaultStatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, DeckColors.Surface, CircleShape)
    )
}

@Composable
private fun ReadinessPip(active: Boolean, label: String, colorOverride: Color? = null) {
    val color = colorOverride ?: if (active) DeckColors.Green else DeckColors.Orange
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun MiniTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(20.dp)) {
        Text(title, color = DeckColors.PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(body, color = DeckColors.SecondaryText, fontSize = 15.sp, lineHeight = 21.sp)
    }
}

private fun CredentialType.label(): String {
    return when (this) {
        CredentialType.Password -> "Password"
        CredentialType.PrivateKey -> "Private Key"
        CredentialType.HardwareKey -> "Hardware Key"
    }
}

internal enum class CredentialVaultFilter(val label: String) {
    All("All"),
    Keys("Keys"),
    Passwords("Passwords"),
    Hardware("Hardware"),
    Passphrases("Passphrases"),
    NeedsSecret("Needs secret");

    fun color(): Color {
        return when (this) {
            All -> DeckColors.Cyan
            Keys -> DeckColors.Purple
            Passwords -> DeckColors.Green
            Hardware -> DeckColors.Orange
            Passphrases -> DeckColors.Green
            NeedsSecret -> DeckColors.Orange
        }
    }
}

internal fun visibleCredentialsForVault(
    credentials: List<Credential>,
    filter: CredentialVaultFilter
): List<Credential> {
    return credentials
        .filter { credential ->
            when (filter) {
                CredentialVaultFilter.All -> true
                CredentialVaultFilter.Keys -> credential.type == CredentialType.PrivateKey
                CredentialVaultFilter.Passwords -> credential.type == CredentialType.Password
                CredentialVaultFilter.Hardware -> credential.type == CredentialType.HardwareKey
                CredentialVaultFilter.Passphrases -> credential.passphraseRef != null
                CredentialVaultFilter.NeedsSecret -> !credential.secretBacked
            }
        }
        .sortedWith(compareByDescending<Credential> { it.favorite }
            .thenBy { it.group.trim().lowercase() }
            .thenByDescending { it.secretBacked }
            .thenByDescending { it.lastUsedEpochMillis }
            .thenBy { it.label.lowercase() })
}

internal fun Credential.withMetadata(
    group: String,
    tagsText: String,
    notes: String,
    favorite: Boolean
): Credential {
    val cleanTags = tagsText.split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(12)
    return copy(
        group = group.trim().take(48),
        tags = cleanTags,
        notes = notes.trim().take(2000),
        favorite = favorite
    )
}

internal fun credentialDetailSubtitle(credential: Credential): String {
    return listOf(
        credential.type.label(),
        credential.group.trim().takeIf { it.isNotBlank() },
        if (credential.favorite) "Favorite" else null
    ).filterNotNull().joinToString(" · ")
}

internal fun expandedCredentialActionLabels(credential: Credential): List<String> {
    return buildList {
        add("Details")
        if (credential.type == CredentialType.PrivateKey) add("Validate")
        if (credential.secretBacked) {
            add("Copy")
            if (credential.type == CredentialType.PrivateKey && VaultPublicKeyPolicy.exportablePublicKey(credential.publicKeyPreview) != null) {
                add("Copy Pub")
                add("Export Pub")
            }
            add("Export")
            add("Share")
        }
        addAll(listOf("Rename", "Organize", "Unlink", "Replace", "Copy Link", "Share Link", "QR", "Remove"))
    }
}

private fun String.safeFileName(): String {
    return ifBlank { "identity" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .take(64)
}

private fun Throwable.requiresSftpPrivateKeyPassphrase(): Boolean {
    return SshAuthFailureHints.requiresPrivateKeyPassphrase(this)
}

internal fun fileBrowserStartPath(server: ServerProfile, bookmarks: List<SftpBookmark>): String {
    if (server.protocol == ConnectionProtocol.Rclone) {
        val raw = server.startDirectory
            .removePrefix("rclone://")
            .trim()
            .trimStart('/')
        if (raw.isBlank() || raw == ".") return "/"
        val remote = raw.substringBefore(':', missingDelimiterValue = raw.substringBefore('/')).removeSuffix(":")
        val path = if (':' in raw) raw.substringAfter(':').trimStart('/') else raw.substringAfter('/', "")
        return listOf(remote, path).filter { it.isNotBlank() }.joinToString("/", prefix = "/")
    }
    if (server.protocol != ConnectionProtocol.Smb) return SftpPathResolver.defaultStartPath(server, bookmarks)
    if (server.startDirectory.isBlank()) return "."
    return runCatching {
        val target = SmbTargetPolicy.from(server)
        val child = target.initialPath.takeUnless { it == "/" }.orEmpty().trimStart('/')
        listOf(target.share, child).filter { it.isNotBlank() }.joinToString("/", prefix = "/")
    }.getOrDefault(".")
}

internal fun Long.rcloneSpaceLabel(): String {
    return if (this >= 0L) MetricFormatters.bytesLabel(this) else "--"
}

@Composable
private fun VaultHostVisual(osName: String, modifier: Modifier = Modifier) {
    if (vaultHostUsesOsLogo(osName)) {
        ChronoOsLogo(osName, modifier, contentScale = ContentScale.Fit)
    } else {
        GenericHostMark(modifier)
    }
}

internal fun vaultHostUsesOsLogo(osName: String): Boolean = osLogoDrawableOrNull(osName) != null

@Composable
private fun GenericHostMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        val markColor = Color.White
        val pad = size.minDimension * 0.18f
        drawRoundRect(
            color = markColor,
            topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
            size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = stroke
        )
        repeat(3) { i ->
            val y = size.height * (0.32f + i * 0.18f)
            drawLine(markColor, androidx.compose.ui.geometry.Offset(size.width * 0.32f, y), androidx.compose.ui.geometry.Offset(size.width * 0.68f, y), strokeWidth = stroke.width, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        }
    }
}
