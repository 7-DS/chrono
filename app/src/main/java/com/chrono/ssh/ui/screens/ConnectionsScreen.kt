package com.chrono.ssh.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.service.ForwardStatus
import com.chrono.ssh.core.service.displayName
import com.chrono.ssh.core.service.routeLabel
import com.chrono.ssh.ui.design.ChronoOsLogo
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.HeadingFontTarget
import com.chrono.ssh.ui.design.LocalHeadingFontFamilies

@Composable
fun ConnectionsScreen(
    servers: List<ServerProfile>,
    workspaces: SnapshotStateMap<String, TerminalWorkspaceState>,
    sftpWorkspaces: List<Pair<String, ServerProfile>> = emptyList(),
    snapshots: Map<String, MetricSnapshot>,
    forwards: List<PortForwardRule> = emptyList(),
    forwardStatuses: Map<String, ForwardStatus> = emptyMap(),
    onOpenWorkspace: (String) -> Unit,
    onOpenTerminal: (ServerProfile) -> Unit = {},
    onOpenSftpWorkspace: (String) -> Unit = {},
    onOpenSftp: (ServerProfile) -> Unit,
    onToggleForward: (PortForwardRule) -> Unit = {},
    onDuplicate: (ServerProfile) -> Unit,
    onClose: (String) -> Unit
) {
    val sessionCards = activeTerminalConnectionCards(servers, workspaces.entries.map { it.key to it.value })
    val tunnelCards = connectionTunnelSummaries(servers, forwards, forwardStatuses)
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Text(
            "Connections",
            color = DeckColors.PrimaryText,
            fontSize = 40.sp,
            lineHeight = 42.sp,
            fontWeight = FontWeight.Black,
            fontFamily = LocalHeadingFontFamilies.current.forTarget(HeadingFontTarget.Connections)
        )
        Spacer(Modifier.height(14.dp))
        if (sessionCards.isEmpty() && sftpWorkspaces.isEmpty() && tunnelCards.isEmpty()) {
            EmptyConnectionsCard()
        } else {
            if (sessionCards.isNotEmpty()) {
                val connectedCount = sessionCards.count { it.third.connected }
                val totalCount = sessionCards.size
                Text(
                    connectionSummaryLabel(connectedCount, totalCount),
                    color = DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(10.dp))
                sessionCards.forEach { (workspaceKey, server, workspace) ->
                    ConnectionCard(
                        server = server,
                        workspace = workspace,
                        snapshot = snapshots[server.id],
                        onPrimary = { onOpenWorkspace(workspaceKey) },
                        onOpenSftp = { onOpenSftp(server) },
                        onDuplicate = { onDuplicate(server) },
                        onClose = { onClose(workspaceKey) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (sftpWorkspaces.isNotEmpty()) {
                Text(
                    "${sftpWorkspaces.size} SFTP tab${if (sftpWorkspaces.size == 1) "" else "s"}",
                    color = DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(10.dp))
                sftpWorkspaces.forEach { (workspaceKey, server) ->
                    SftpHostConnectionCard(
                        server = server,
                        onOpenSftp = { onOpenSftpWorkspace(workspaceKey) },
                        onOpenTerminal = { onOpenTerminal(server) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
            if (tunnelCards.isNotEmpty()) {
                Text(
                    connectionTunnelSummaryLabel(tunnelCards),
                    color = DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(10.dp))
                tunnelCards.forEach { tunnel ->
                    TunnelConnectionCard(
                        tunnel = tunnel,
                        onToggleForward = { onToggleForward(tunnel.rule) }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

internal data class ConnectionTunnelSummary(
    val rule: PortForwardRule,
    val id: String,
    val serverName: String,
    val typeLabel: String,
    val titleLabel: String,
    val groupLabel: String,
    val routeLabel: String,
    val statusLabel: String,
    val active: Boolean,
    val favorite: Boolean,
    val error: String?
)

internal fun activeTerminalConnectionCards(
    servers: List<ServerProfile>,
    workspaces: List<Pair<String, TerminalWorkspaceState>>
): List<Triple<String, ServerProfile, TerminalWorkspaceState>> {
    val serversById = servers.associateBy { it.id }
    return workspaces.mapNotNull { (key, workspace) ->
        if (!workspace.connected) return@mapNotNull null
        serversById[workspace.serverId]?.let { server -> Triple(key, server, workspace) }
    }.sortedBy { it.second.name.lowercase() }
}

internal fun connectionTunnelSummaries(
    servers: List<ServerProfile>,
    forwards: List<PortForwardRule>,
    statuses: Map<String, ForwardStatus>
): List<ConnectionTunnelSummary> {
    val serversById = servers.associateBy { it.id }
    return forwards.mapNotNull { rule ->
        val server = serversById[rule.serverId] ?: return@mapNotNull null
        val status = statuses[rule.id]
        ConnectionTunnelSummary(
            rule = rule,
            id = rule.id,
            serverName = server.name,
            typeLabel = rule.type.displayName(),
            titleLabel = rule.label.ifBlank { rule.type.displayName() },
            groupLabel = rule.group.trim(),
            routeLabel = rule.routeLabel(),
            statusLabel = tunnelStatusLabel(status),
            active = status?.active == true,
            favorite = rule.favorite,
            error = status?.lastError?.let(::connectionUserFacingTunnelError)
        )
    }.sortedWith(
        compareByDescending<ConnectionTunnelSummary> { it.active }
            .thenByDescending { it.favorite }
            .thenBy { it.groupLabel.lowercase() }
            .thenBy { it.serverName.lowercase() }
            .thenBy { it.titleLabel.lowercase() }
            .thenBy { it.routeLabel }
    )
}

internal fun connectionUserFacingTunnelError(raw: String): String {
    val text = raw.trim()
    val lower = text.lowercase()
    return when {
        text.isBlank() -> "Connection interrupted. Reconnect to continue."
        lower.contains("auth") || lower.contains("permission denied") -> text
        lower.contains("host key") || lower.contains("fingerprint") -> text
        lower.contains("failed to connect") ||
            lower.contains("connect failed") ||
            lower.contains("connection reset") ||
            lower.contains("broken pipe") ||
            lower.contains("timeout") ||
            lower.contains("no route to host") -> "Connection interrupted. Reconnect to continue."
        else -> text
    }
}

internal fun connectionTunnelSummaryLabel(tunnels: List<ConnectionTunnelSummary>): String {
    val active = tunnels.count { it.active }
    return "$active active · ${tunnels.size} tunnel${if (tunnels.size == 1) "" else "s"}"
}

internal fun tunnelStatusLabel(status: ForwardStatus?): String {
    return when {
        status == null -> "Stopped"
        status.active -> "Active"
        status.lastError != null -> "Failed"
        else -> status.lastMessage.ifBlank { "Starting" }
    }
}

@Composable
private fun EmptyConnectionsCard() {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(horizontal = 15.dp, vertical = 13.dp)) {
        Text("No active sessions", color = DeckColors.PrimaryText, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text("Start SSH from a server card.", color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun SftpHostConnectionCard(
    server: ServerProfile,
    onOpenSftp: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(24.dp))
            .clickable(onClick = onOpenSftp)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DeckColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            ChronoOsLogo(server.osName, Modifier.size(46.dp))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DeckColors.Green)
                    .border(1.dp, DeckColors.Surface, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("↔", color = Color.White, fontSize = 10.sp, lineHeight = 10.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(sftpConnectionTitle(server.name), color = DeckColors.PrimaryText, fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(DeckColors.SurfaceMuted)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(11.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Text("SFTP", color = DeckColors.PrimaryText, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        IconCircle(DeckColors.Cyan, symbol = "terminal") { onOpenTerminal() }
    }
}

internal fun sftpConnectionTitle(serverName: String): String = serverName

@Composable
private fun TunnelConnectionCard(
    tunnel: ConnectionTunnelSummary,
    onToggleForward: () -> Unit
) {
    val statusColor = when {
        tunnel.active -> DeckColors.Green
        tunnel.error != null -> DeckColors.Red
        tunnel.statusLabel == "Stopped" -> DeckColors.SecondaryText
        else -> DeckColors.Orange
    }
    val starting = tunnel.statusLabel.contains("starting", ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(24.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(statusColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            TunnelGlyph(statusColor, Modifier.size(22.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (tunnel.favorite) "★ ${tunnel.titleLabel}" else tunnel.titleLabel,
                color = DeckColors.PrimaryText,
                fontSize = 17.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            val subtitle = listOf(tunnel.serverName, tunnel.groupLabel, tunnel.routeLabel)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            Text(subtitle, color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            tunnel.error?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, color = DeckColors.Red, fontSize = 12.sp, lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(tunnel.statusLabel, color = statusColor, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        ConnectionStateDot(statusColor)
        Spacer(Modifier.width(8.dp))
        IconCircle(
            color = if (tunnel.active) DeckColors.Red else DeckColors.Green,
            symbol = if (tunnel.active) "stop" else if (starting) "busy" else "start",
            onClick = {
                if (!starting) onToggleForward()
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    server: ServerProfile,
    workspace: TerminalWorkspaceState,
    snapshot: MetricSnapshot?,
    onPrimary: () -> Unit,
    onOpenSftp: () -> Unit,
    onDuplicate: () -> Unit,
    onClose: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val active = workspace.connected
    val statusLabel = connectionStatusLabel(workspace.status, workspace.connected)
    val statusColor = when (connectionStatusTone(statusLabel, workspace.connected, snapshot?.status)) {
        ConnectionStatusTone.Active -> DeckColors.Green
        ConnectionStatusTone.Starting -> DeckColors.Orange
        ConnectionStatusTone.Failed -> DeckColors.Red
        ConnectionStatusTone.Online -> DeckColors.Cyan
        ConnectionStatusTone.Offline -> DeckColors.Red
        ConnectionStatusTone.Idle -> DeckColors.SecondaryText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(28.dp))
            .clickable(onClick = onPrimary)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(DeckColors.SurfaceMuted),
            contentAlignment = Alignment.Center
        ) {
            ChronoOsLogo(server.osName, Modifier.size(50.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(server.name, color = DeckColors.PrimaryText, fontSize = 18.sp, lineHeight = 21.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            statusLabel,
            color = statusColor,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        snapshot?.latencyMs?.let { latency ->
            Text(
                "${latency}ms",
                color = statusColor,
                fontSize = 12.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
            Spacer(Modifier.width(6.dp))
        }
        ConnectionStateDot(statusColor)
        Spacer(Modifier.width(8.dp))
        IconCircle(DeckColors.Cyan, symbol = "terminal") { onPrimary() }
        Spacer(Modifier.width(6.dp))
        IconCircle(DeckColors.Green, symbol = "files") { onOpenSftp() }
        Spacer(Modifier.width(6.dp))
        Box {
            IconCircle(DeckColors.SecondaryText, symbol = "⋮") { menuOpen = true }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                    shape = RoundedCornerShape(18.dp),
                    containerColor = DeckColors.Surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeckColors.Surface)
                        .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                ) {
                DropdownMenuItem(text = { ConnectionMenuText(if (active) "Open terminal" else "Reconnect") }, onClick = {
                    menuOpen = false
                    onPrimary()
                })
                DropdownMenuItem(text = { ConnectionMenuText("Connect via SFTP") }, onClick = {
                    menuOpen = false
                    onOpenSftp()
                })
                if (active) {
                    DropdownMenuItem(text = { ConnectionMenuText("Duplicate") }, onClick = {
                        menuOpen = false
                        onDuplicate()
                    })
                }
                if (active) {
                    DropdownMenuItem(text = { ConnectionMenuText("Close", DeckColors.Red, true) }, onClick = {
                        menuOpen = false
                        onClose()
                    })
                }
            }
        }
    }
}

internal fun connectionSummaryLabel(connectedCount: Int, workspaceCount: Int): String {
    return "$connectedCount active · $workspaceCount terminal${if (workspaceCount == 1) "" else "s"}"
}

internal fun connectionStatusLabel(status: String, connected: Boolean): String {
    val normalized = status.trim()
    return when {
        connected -> "Active"
        normalized.contains("failed", ignoreCase = true) -> "Failed"
        normalized.contains("rejected", ignoreCase = true) -> "Failed"
        normalized.contains("connecting", ignoreCase = true) -> "Starting"
        normalized.contains("opening", ignoreCase = true) -> "Starting"
        normalized.contains("disconnecting", ignoreCase = true) -> "Starting"
        normalized.contains("passphrase", ignoreCase = true) -> "Passphrase"
        normalized.contains("review host key", ignoreCase = true) -> "Review"
        normalized.equals("Shell", ignoreCase = true) -> "Saved"
        normalized.equals("Shell attached", ignoreCase = true) -> "Saved"
        normalized.endsWith("latched", ignoreCase = true) -> "Saved"
        normalized.isNotBlank() && !normalized.equals("Idle", ignoreCase = true) && !normalized.equals("Closed", ignoreCase = true) -> normalized
        else -> "Saved"
    }
}

internal enum class ConnectionStatusTone {
    Active,
    Starting,
    Failed,
    Online,
    Offline,
    Idle
}

internal fun connectionStatusTone(
    statusLabel: String,
    connected: Boolean,
    snapshotStatus: ServerStatus?
): ConnectionStatusTone {
    return when {
        connected -> ConnectionStatusTone.Active
        statusLabel == "Starting" -> ConnectionStatusTone.Starting
        statusLabel == "Passphrase" -> ConnectionStatusTone.Starting
        statusLabel == "Review" -> ConnectionStatusTone.Starting
        statusLabel == "Failed" -> ConnectionStatusTone.Failed
        snapshotStatus == ServerStatus.Online -> ConnectionStatusTone.Online
        snapshotStatus == ServerStatus.Offline -> ConnectionStatusTone.Offline
        else -> ConnectionStatusTone.Idle
    }
}

@Composable
private fun IconCircle(color: Color, symbol: String = "⋮", onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(110, easing = LinearOutSlowInEasing),
        label = "connectionIconPress"
    )
    val background by animateColorAsState(
        targetValue = if (pressed) color.copy(alpha = 0.14f) else DeckColors.SurfaceMuted,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "connectionIconBackground"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(36.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (symbol == "⋮") {
            Canvas(Modifier.size(16.dp)) {
                val radius = 1.55.dp.toPx()
                val centerX = size.width / 2f
                val gap = size.height / 3.4f
                drawCircle(color, radius, androidx.compose.ui.geometry.Offset(centerX, size.height / 2f - gap))
                drawCircle(color, radius, androidx.compose.ui.geometry.Offset(centerX, size.height / 2f))
                drawCircle(color, radius, androidx.compose.ui.geometry.Offset(centerX, size.height / 2f + gap))
            }
        } else if (symbol == "terminal") {
            Canvas(Modifier.size(17.dp)) {
                val stroke = 1.7.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width - 3.dp.toPx(), size.height - 4.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 6.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.5.dp.toPx()),
                    strokeWidth = stroke
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.5.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(5.dp.toPx(), 11.dp.toPx()),
                    strokeWidth = stroke
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(10.dp.toPx(), 11.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(13.dp.toPx(), 11.dp.toPx()),
                    strokeWidth = stroke
                )
            }
        } else if (symbol == "files") {
            Canvas(Modifier.size(17.dp)) {
                val stroke = 1.7.dp.toPx()
                val left = 2.dp.toPx()
                val top = 5.dp.toPx()
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(left, top),
                    size = androidx.compose.ui.geometry.Size(size.width - 4.dp.toPx(), size.height - 7.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(left + 2.dp.toPx(), top),
                    end = androidx.compose.ui.geometry.Offset(left + 5.5.dp.toPx(), 2.8.dp.toPx()),
                    strokeWidth = stroke
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(left + 5.5.dp.toPx(), 2.8.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(left + 9.5.dp.toPx(), 2.8.dp.toPx()),
                    strokeWidth = stroke
                )
            }
        } else if (symbol == "start") {
            Canvas(Modifier.size(16.dp)) {
                val stroke = 1.7.dp.toPx()
                drawArc(
                    color = color,
                    startAngle = 132f,
                    sweepAngle = 276f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 3.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(10.dp.toPx(), 10.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                drawLine(
                    color = color,
                    start = androidx.compose.ui.geometry.Offset(8.dp.toPx(), 2.dp.toPx()),
                    end = androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.dp.toPx()),
                    strokeWidth = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        } else if (symbol == "stop") {
            Canvas(Modifier.size(16.dp)) {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 8.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        } else if (symbol == "busy") {
            Canvas(Modifier.size(16.dp)) {
                val radius = 1.6.dp.toPx()
                drawCircle(color, radius, androidx.compose.ui.geometry.Offset(4.dp.toPx(), 8.dp.toPx()))
                drawCircle(color, radius, androidx.compose.ui.geometry.Offset(8.dp.toPx(), 8.dp.toPx()))
                drawCircle(color, radius, androidx.compose.ui.geometry.Offset(12.dp.toPx(), 8.dp.toPx()))
            }
        } else {
            Text(symbol, color = color, fontSize = 12.sp, lineHeight = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TunnelGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 1.9.dp.toPx()
        val left = androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.5f)
        val right = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.5f)
        drawCircle(color, radius = 3.2.dp.toPx(), center = left, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawCircle(color, radius = 3.2.dp.toPx(), center = right, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke))
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(left.x + 3.2.dp.toPx(), left.y),
            end = androidx.compose.ui.geometry.Offset(right.x - 3.2.dp.toPx(), right.y),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.32f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.68f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
private fun ConnectionMenuText(text: String, color: Color = DeckColors.PrimaryText, heavy: Boolean = false) {
    Text(text, color = color, fontSize = 15.sp, lineHeight = 18.sp, fontWeight = if (heavy) FontWeight.Black else FontWeight.Bold)
}

@Composable
private fun ConnectionStateDot(color: Color) {
    Canvas(Modifier.size(9.dp)) {
        drawCircle(color, radius = size.minDimension / 3f, style = Fill)
    }
}
