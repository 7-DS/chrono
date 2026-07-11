package com.chrono.ssh.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.CpuCoreMetrics
import com.chrono.ssh.core.model.FilesystemMetric
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.BatteryMetric
import com.chrono.ssh.core.model.ContainerImageMetric
import com.chrono.ssh.core.model.ContainerMetric
import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.GpuMetric
import com.chrono.ssh.core.model.SensorMetric
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.SmartDiskMetric
import com.chrono.ssh.core.model.PveResourceMetric
import com.chrono.ssh.core.model.ProcessMetric
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ServerDetailCard
import com.chrono.ssh.core.service.ContainerRuntimeActionPolicy
import com.chrono.ssh.core.model.ServiceMetric
import com.chrono.ssh.core.model.ServiceSummary
import com.chrono.ssh.core.model.ServerMetricColorPreset
import com.chrono.ssh.core.service.MetricFormatters
import com.chrono.ssh.core.service.ProcessActionPolicy
import com.chrono.ssh.core.service.SystemdServiceActionPolicy
import com.chrono.ssh.ui.design.ChronoOsLogo
import com.chrono.ssh.ui.design.CircleIconButton
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.InterfaceMetricCard
import com.chrono.ssh.ui.design.MetricRing
import com.chrono.ssh.ui.design.ServerCpuUsageColors
import com.chrono.ssh.ui.design.ServerMetricColors
import com.chrono.ssh.ui.design.ServerMetricColorOverrides
import com.chrono.ssh.ui.design.cpuUsageColorsFor
import com.chrono.ssh.ui.design.metricColorsFor
import kotlin.math.ceil
import kotlin.math.max

@Composable
fun ServerDetailScreen(
    server: ServerProfile,
    snapshot: MetricSnapshot,
    metricHistory: List<MetricSnapshot> = emptyList(),
    onBack: () -> Unit,
    onInterfaces: () -> Unit,
    onProbe: () -> Unit,
    onEdit: () -> Unit,
    onTerminal: () -> Unit,
    onSftp: () -> Unit = {},
    onPortForward: () -> Unit = {},
    onActivity: () -> Unit = {},
    onHostInfo: () -> Unit = {},
    onContainerAction: (ContainerMetric, String) -> Unit = { _, _ -> },
    onContainerOutput: (ContainerMetric, String, (String) -> Unit) -> Unit = { _, _, _ -> },
    onProcessAction: (ProcessMetric, String) -> Unit = { _, _ -> },
    onServiceAction: (ServiceMetric, String) -> Unit = { _, _ -> },
    onDetailToolOpen: () -> Unit = {},
    onWake: (() -> Unit)? = null,
    metricColorPreset: ServerMetricColorPreset = ServerMetricColorPreset.Theme,
    metricColorOverrides: ServerMetricColorOverrides = ServerMetricColorOverrides(),
    serverDetailCardOrder: String = ServerDetailCard.defaultOrderCsv(),
    serverDetailHiddenCards: String = ""
) {
    var showMore by remember(server.id) { mutableStateOf(false) }
    var focusedTool by remember(server.id) { mutableStateOf<DetailTool?>(null) }
    val hasMetrics = shouldRenderServerDetailMetrics(snapshot)
    val metricColors = metricColorsFor(metricColorPreset, metricColorOverrides)
    BackHandler(enabled = focusedTool != null) {
        focusedTool = null
    }
    focusedTool?.let { tool ->
        DetailToolScreen(
            server = server,
            snapshot = snapshot,
            tool = tool,
            metricColors = metricColors,
            onBack = { focusedTool = null },
            onContainerAction = onContainerAction,
            onContainerOutput = onContainerOutput,
            onProcessAction = onProcessAction,
            onServiceAction = onServiceAction
        )
        return
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton("<", "Back", modifier = Modifier.size(64.dp), onClick = onBack)
            Text(
                server.name,
                modifier = Modifier.weight(1f),
                color = DeckColors.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                CircleIconButton("...", "Host actions", modifier = Modifier.size(64.dp), onClick = { showMore = true })
                HostActionsMenu(
                    expanded = showMore,
                    onDismiss = { showMore = false },
                    onTerminal = {
                        showMore = false
                        onTerminal()
                    },
                    onInterfaces = {
                        showMore = false
                        onInterfaces()
                    },
                    onHostInfo = {
                        showMore = false
                        onHostInfo()
                    },
                    onActivity = {
                        showMore = false
                        onActivity()
                    },
                    onEdit = {
                        showMore = false
                        onEdit()
                    },
                    onWake = onWake?.let { wake ->
                        {
                            showMore = false
                            wake()
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                ChronoOsLogo(server.osName, Modifier.size(58.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(server.name, color = DeckColors.PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(DeckColors.SurfaceRaised)
                        .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("${server.osName} ${server.osVersion}", color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (server.notes.isNotBlank()) {
            DeckCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Text("Notes", color = DeckColors.SecondaryText, fontSize = 13.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text(server.notes, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 21.sp)
            }
            Spacer(Modifier.height(14.dp))
        }
        DetailActionTiles(
            onTerminal = onTerminal,
            onSftp = onSftp,
            onProcesses = {
                focusedTool = DetailTool.Processes
                onDetailToolOpen()
            },
            onContainers = {
                focusedTool = DetailTool.Containers
                onDetailToolOpen()
            },
            onSystemd = {
                focusedTool = DetailTool.Systemd
                onDetailToolOpen()
            },
            onPortForward = onPortForward
        )
        Spacer(Modifier.height(14.dp))
        if (hasMetrics) {
            val hiddenCards = ServerDetailCard.hiddenSet(serverDetailHiddenCards)
            ServerDetailCard.ordered(serverDetailCardOrder)
                .filterNot { it in hiddenCards }
                .forEach { card ->
                    val rendered = renderServerDetailCard(
                        card = card,
                        snapshot = snapshot,
                        metricHistory = metricHistory,
                        metricColors = metricColors,
                        onInterfaces = onInterfaces,
                        onContainerAction = onContainerAction,
                        onProcessAction = onProcessAction,
                        onServiceAction = onServiceAction,
                        onOpenProcesses = {
                            focusedTool = DetailTool.Processes
                            onDetailToolOpen()
                        },
                        onOpenSystemd = {
                            focusedTool = DetailTool.Systemd
                            onDetailToolOpen()
                        }
                    )
                    if (rendered) Spacer(Modifier.height(14.dp))
                }
            Spacer(Modifier.height(12.dp))
        } else if (shouldRenderServerDetailMetricUnavailableState(snapshot)) {
            MetricsStatusCard("Metrics unavailable", "Metrics are still pending or the last refresh timed out. Host details and quick actions remain available.")
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun renderServerDetailCard(
    card: ServerDetailCard,
    snapshot: MetricSnapshot,
    metricHistory: List<MetricSnapshot>,
    metricColors: ServerMetricColors,
    onInterfaces: () -> Unit,
    onContainerAction: (ContainerMetric, String) -> Unit,
    onProcessAction: (ProcessMetric, String) -> Unit,
    onServiceAction: (ServiceMetric, String) -> Unit,
    onOpenProcesses: () -> Unit,
    onOpenSystemd: () -> Unit
): Boolean {
    return when (card) {
        ServerDetailCard.Uptime -> {
            UptimeHistoryCard(snapshot, metricHistory)
            true
        }
        ServerDetailCard.CpuUsage -> {
            CpuUsageCard(snapshot, metricColors)
            true
        }
        ServerDetailCard.CpuLoad -> {
            CpuLoadCard(snapshot, metricHistory, metricColors)
            true
        }
        ServerDetailCard.System -> {
            SystemSummaryCard(snapshot, metricColors)
            systemSummaryRows(snapshot).isNotEmpty()
        }
        ServerDetailCard.FailedServices -> {
            FailedServicesCard(snapshot.services.failedItems, onServiceAction, showActions = false)
            snapshot.services.failedItems.isNotEmpty()
        }
        ServerDetailCard.Resources -> {
            ResourceGrid(snapshot, metricColors)
            true
        }
        ServerDetailCard.Filesystems -> {
            FilesystemsCard(snapshot.disk.filesystems, metricColors)
            snapshot.disk.filesystems.isNotEmpty()
        }
        ServerDetailCard.Processes -> {
            ProcessesCard(snapshot.processes.items, onProcessAction, metricColors, summary = snapshot.processes, onOpenAll = onOpenProcesses, showPendingWhenEmpty = true, showActions = false)
            snapshot.hasCollectedMetrics() || snapshot.processes.hasProcessSummary() || snapshot.processes.items.isNotEmpty()
        }
        ServerDetailCard.Systemd -> {
            SystemdServicesPage(snapshot.services, onServiceAction, metricColors, limit = 6, onOpenAll = onOpenSystemd, showActions = false)
            snapshot.hasCollectedMetrics() || snapshot.services.shouldShowSystemdCard()
        }
        ServerDetailCard.Network -> {
            NetworkSummaryCard(snapshot, metricColors, onInterfaces)
            snapshot.hasServerDetailNetworkSummary()
        }
        ServerDetailCard.Containers -> {
            ContainersCard(snapshot.docker, onContainerAction, metricColors, showActions = false)
            snapshot.docker.shouldShowContainerCard()
        }
        ServerDetailCard.Gpus -> {
            GpusCard(snapshot.gpus, metricColors)
            snapshot.gpus.isNotEmpty()
        }
        ServerDetailCard.Proxmox -> {
            ProxmoxResourcesCard(snapshot.pveResources, metricColors)
            snapshot.pveResources.isNotEmpty()
        }
        ServerDetailCard.Battery -> {
            BatteryCard(snapshot.batteries, metricColors)
            snapshot.batteries.isNotEmpty()
        }
        ServerDetailCard.SmartDisks -> {
            SmartDisksCard(snapshot.smartDisks, metricColors)
            snapshot.smartDisks.isNotEmpty()
        }
        ServerDetailCard.Sensors -> {
            SensorsCard(snapshot.sensors, metricColors)
            snapshot.sensors.isNotEmpty()
        }
    }
}

@Composable
fun ServerActivityScreen(
    server: ServerProfile,
    events: List<ConnectionEvent>,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton("<", "Back", modifier = Modifier.size(64.dp), onClick = onBack)
            Text(
                "Activity",
                modifier = Modifier.weight(1f),
                color = DeckColors.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(64.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(server.name, color = DeckColors.SecondaryText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        ActivityCard(events)
    }
}

internal fun shouldRenderServerDetailMetrics(snapshot: MetricSnapshot): Boolean = snapshot.hasCollectedMetrics()

internal fun shouldRenderServerDetailMetricUnavailableState(snapshot: MetricSnapshot): Boolean {
    return snapshot.collectedAtEpochMillis > 0L
}

internal enum class ServerDetailPrimaryMetricSection {
    Filesystems,
    Processes,
    Systemd,
    Network
}

internal fun serverDetailPrimaryMetricSections(snapshot: MetricSnapshot): List<ServerDetailPrimaryMetricSection> {
    return listOfNotNull(
        ServerDetailPrimaryMetricSection.Filesystems.takeIf { snapshot.disk.filesystems.isNotEmpty() },
        ServerDetailPrimaryMetricSection.Processes.takeIf { snapshot.hasCollectedMetrics() || snapshot.processes.hasProcessSummary() || snapshot.processes.items.isNotEmpty() },
        ServerDetailPrimaryMetricSection.Systemd.takeIf { snapshot.hasCollectedMetrics() || snapshot.services.shouldShowSystemdCard() },
        ServerDetailPrimaryMetricSection.Network.takeIf { snapshot.hasServerDetailNetworkSummary() }
    )
}

private enum class DetailTool(val title: String) {
    Containers("Containers"),
    Processes("Processes"),
    Systemd("Systemd")
}

@Composable
private fun DetailToolScreen(
    server: ServerProfile,
    snapshot: MetricSnapshot,
    tool: DetailTool,
    metricColors: ServerMetricColors,
    onBack: () -> Unit,
    onContainerAction: (ContainerMetric, String) -> Unit,
    onContainerOutput: (ContainerMetric, String, (String) -> Unit) -> Unit,
    onProcessAction: (ProcessMetric, String) -> Unit,
    onServiceAction: (ServiceMetric, String) -> Unit
) {
    var pendingAction by remember(tool, server.id) { mutableStateOf<RuntimePendingAction?>(null) }
    var outputPage by remember(tool, server.id) { mutableStateOf<ContainerOutputRequest?>(null) }
    val guardedContainerAction: (ContainerMetric, String) -> Unit = { container, action ->
        if (runtimeActionNeedsConfirmation(action)) {
            pendingAction = RuntimePendingAction(
                title = runtimeActionDescription(action),
                target = container.name.ifBlank { container.id },
                action = { onContainerAction(container, action) }
            )
        } else {
            onContainerAction(container, action)
        }
    }
    val guardedProcessAction: (ProcessMetric, String) -> Unit = { process, action ->
        if (runtimeActionNeedsConfirmation(action)) {
            pendingAction = RuntimePendingAction(
                title = runtimeActionDescription(action),
                target = process.command.ifBlank { process.pid?.toString().orEmpty() },
                action = { onProcessAction(process, action) }
            )
        } else {
            onProcessAction(process, action)
        }
    }
    val guardedServiceAction: (ServiceMetric, String) -> Unit = { service, action ->
        if (runtimeActionNeedsConfirmation(action)) {
            pendingAction = RuntimePendingAction(
                title = runtimeActionDescription(action),
                target = service.unit,
                action = { onServiceAction(service, action) }
            )
        } else {
            onServiceAction(service, action)
        }
    }
    outputPage?.let { request ->
        ContainerOutputPage(
            title = runtimeActionDescription(request.action),
            subtitle = request.container.name.ifBlank { request.container.id.ifBlank { request.container.engine } },
            container = request.container,
            action = request.action,
            metricColors = metricColors,
            onBack = { outputPage = null },
            onLoad = onContainerOutput
        )
        return
    }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton("<", "Back", modifier = Modifier.size(64.dp), onClick = onBack)
            Text(
                tool.title,
                modifier = Modifier.weight(1f),
                color = DeckColors.PrimaryText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.size(64.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(server.name, color = DeckColors.SecondaryText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        when (tool) {
            DetailTool.Containers -> {
                ContainersToolPage(
                    summary = snapshot.docker,
                    onContainerAction = guardedContainerAction,
                    onContainerOutput = { container, action -> outputPage = ContainerOutputRequest(container, action) },
                    metricColors = metricColors
                )
            }
            DetailTool.Processes -> {
                ProcessesPage(snapshot.processes, guardedProcessAction, metricColors)
                if (snapshot.processes.items.isEmpty() && !snapshot.processes.hasProcessSummary()) {
                    EmptyDetailToolCard("No process data", "Refresh metrics to populate running processes.")
                }
            }
            DetailTool.Systemd -> {
                SystemdServicesPage(snapshot.services, guardedServiceAction, metricColors)
                if (!snapshot.services.shouldShowSystemdCard()) {
                    EmptyDetailToolCard("No systemd data", "Systemd services will appear here after metrics refresh.")
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
    pendingAction?.let { pending ->
        RuntimeActionConfirmDialog(
            pending = pending,
            onDismiss = { pendingAction = null },
            onConfirm = {
                pendingAction = null
                pending.action()
            }
        )
    }
}

private data class RuntimePendingAction(
    val title: String,
    val target: String,
    val action: () -> Unit
)

@Composable
private fun RuntimeActionConfirmDialog(
    pending: RuntimePendingAction,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeckColors.Surface,
        title = { Text(pending.title, color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = { Text("Run this action on ${pending.target}?", color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 20.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Run", color = DeckColors.Red, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = DeckColors.SecondaryText, fontWeight = FontWeight.Bold)
            }
        }
    )
}

@Composable
private fun EmptyDetailToolCard(title: String, detail: String) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
        Text(title, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun MetricsStatusCard(title: String, detail: String) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
        Text(title, color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text(detail, color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 19.sp)
    }
}

internal data class ActivityItem(
    val level: String,
    val message: String,
    val atEpochMillis: Long,
    val colorName: String
)

internal fun recentActivityItems(events: List<ConnectionEvent>, limit: Int = 6): List<ActivityItem> {
    return events
        .sortedByDescending { it.atEpochMillis }
        .take(limit.coerceAtLeast(0))
        .map { event ->
            ActivityItem(
                level = event.level.label(),
                message = event.message.lines().joinToString(" ").ifBlank { "Event" },
                atEpochMillis = event.atEpochMillis,
                colorName = event.level.colorName()
            )
        }
}

private fun ConnectionEventLevel.label(): String {
    return when (this) {
        ConnectionEventLevel.Info -> "Info"
        ConnectionEventLevel.Success -> "Success"
        ConnectionEventLevel.Warning -> "Warning"
        ConnectionEventLevel.Error -> "Error"
    }
}

private fun ConnectionEventLevel.colorName(): String {
    return when (this) {
        ConnectionEventLevel.Info -> "cyan"
        ConnectionEventLevel.Success -> "green"
        ConnectionEventLevel.Warning -> "orange"
        ConnectionEventLevel.Error -> "red"
    }
}

private fun activityColor(colorName: String): Color {
    return when (colorName) {
        "green" -> DeckColors.Green
        "orange" -> DeckColors.Orange
        "red" -> DeckColors.Red
        else -> DeckColors.Cyan
    }
}

@Composable
private fun ActivityCard(events: List<ConnectionEvent>) {
    val items = recentActivityItems(events)
    if (items.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
        Text("Activity", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(12.dp))
        items.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Canvas(Modifier.padding(top = 5.dp).size(8.dp)) {
                    drawCircle(activityColor(item.colorName))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.message, color = DeckColors.PrimaryText, fontSize = 14.sp, lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text("${item.level} · ${item.atEpochMillis}", color = DeckColors.SecondaryText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (index != items.lastIndex) Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun HostActionsMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onTerminal: () -> Unit,
    onInterfaces: () -> Unit,
    onHostInfo: () -> Unit,
    onActivity: () -> Unit,
    onEdit: () -> Unit,
    onWake: (() -> Unit)? = null
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = DeckColors.Surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(14.dp))
    ) {
        DropdownMenuItem(
            text = { HostMenuText("Terminal") },
            onClick = onTerminal
        )
        DropdownMenuItem(
            text = { HostMenuText("Network") },
            onClick = onInterfaces
        )
        DropdownMenuItem(
            text = { HostMenuText("Info") },
            onClick = onHostInfo
        )
        DropdownMenuItem(
            text = { HostMenuText("Activity") },
            onClick = onActivity
        )
        if (onWake != null) {
            DropdownMenuItem(
                text = { HostMenuText("Wake") },
                onClick = onWake
            )
        }
        DropdownMenuItem(
            text = { HostMenuText("Edit host") },
            onClick = onEdit
        )
    }
}

@Composable
private fun HostMenuText(title: String) {
    Text(title, color = DeckColors.PrimaryText, fontSize = 15.sp, fontWeight = FontWeight.Black)
}

@Composable
private fun DetailActionTiles(
    onTerminal: () -> Unit,
    onSftp: () -> Unit,
    onProcesses: () -> Unit,
    onContainers: () -> Unit,
    onSystemd: () -> Unit,
    onPortForward: () -> Unit
) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 22.dp, padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            DetailActionTile(DetailTileIcon.Terminal, "Terminal", DeckColors.Orange, Modifier.weight(1f), onTerminal)
            DetailActionTile(DetailTileIcon.Sftp, "SFTP", DeckColors.Cyan, Modifier.weight(1f), onSftp)
            DetailActionTile(DetailTileIcon.Process, "Process", DeckColors.Yellow, Modifier.weight(1f), onProcesses)
            DetailActionTile(DetailTileIcon.Container, "Container", DeckColors.Green, Modifier.weight(1f), onContainers)
            DetailActionTile(DetailTileIcon.Service, "Systemd", DeckColors.Purple, Modifier.weight(1f), onSystemd)
            DetailActionTile(DetailTileIcon.Forward, "Forward", DeckColors.Orange, Modifier.weight(1f), onPortForward)
        }
    }
}

private enum class DetailTileIcon {
    Terminal,
    Sftp,
    Process,
    Container,
    Service,
    Forward
}

@Composable
private fun DetailActionTile(
    icon: DetailTileIcon,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)?
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted)
            .semantics { contentDescription = detailActionDescription(label) }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        DetailActionGlyph(icon, color, Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = DeckColors.SecondaryText, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun detailActionDescription(label: String): String {
    return when (label) {
        "Terminal" -> "Open terminal"
        "SFTP" -> "Open SFTP"
        "Process" -> "Open processes"
        "Container" -> "Open containers"
        "Systemd" -> "Open systemd"
        "Forward" -> "Open port forwards"
        else -> label
    }
}

@Composable
private fun DetailActionGlyph(icon: DetailTileIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.7.dp.toPx(), cap = StrokeCap.Round)
        when (icon) {
            DetailTileIcon.Terminal -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.20f), size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.40f), androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.60f), androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            DetailTileIcon.Sftp -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.34f), size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.34f), androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.24f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.24f), androidx.compose.ui.geometry.Offset(size.width * 0.56f, size.height * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            DetailTileIcon.Process -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.46f), androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.56f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.56f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.08f, center = androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.28f), style = stroke)
            }
            DetailTileIcon.Container -> {
                drawRoundRect(color, topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.20f), size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.60f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.42f), androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            DetailTileIcon.Service -> {
                drawCircle(color, radius = size.minDimension * 0.19f, center = center, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.18f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.66f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.34f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.66f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.82f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            DetailTileIcon.Forward -> {
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.26f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.38f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, size.height * 0.74f), androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun CpuUsageCard(snapshot: MetricSnapshot, metricColors: ServerMetricColors) {
    val cpuUsageColors = cpuUsageColorsFor(metricColors)
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricTitle(icon = "chip", title = "CPU Usage", color = cpuUsageColors.user, compact = true)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            CpuUsagePercent(snapshot.cpu.usagePercent, Modifier.weight(0.31f))
            Spacer(Modifier.width(12.dp))
            Text(
                snapshot.cpu.model.ifBlank { "${snapshot.cpu.cores} cores" },
                color = DeckColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(0.69f)
            )
        }
        Spacer(Modifier.height(10.dp))
        CpuUsageRows(snapshot, cpuUsageColors)
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DeckColors.Divider)
        )
        Spacer(Modifier.height(10.dp))
        CpuStatFlow(snapshot, cpuUsageColors)
    }
}

@Composable
private fun CpuStatFlow(snapshot: MetricSnapshot, cpuUsageColors: ServerCpuUsageColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.Top
    ) {
        CpuStat("Cores", snapshot.cpu.cores.toString(), DeckColors.SecondaryText, Modifier.weight(1f), showIndicator = false)
        CpuStat("User", "${snapshot.cpu.userPercent}%", cpuUsageColors.user, Modifier.weight(1f))
        CpuStat("System", "${snapshot.cpu.systemPercent}%", cpuUsageColors.system, Modifier.weight(1f))
        CpuStat("Nice", "${snapshot.cpu.nicePercent}%", cpuUsageColors.nice, Modifier.weight(1f))
        CpuStat("IOWait", "${snapshot.cpu.ioWaitPercent}%", cpuUsageColors.ioWait, Modifier.weight(1f))
        CpuStat("Steal", "${snapshot.cpu.stealPercent}%", cpuUsageColors.steal, Modifier.weight(1f))
    }
}

@Composable
private fun CpuUsagePercent(percent: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            percent.coerceIn(0, 100).toString(),
            color = DeckColors.PrimaryText,
            fontSize = 40.sp,
            lineHeight = 43.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Text(
            "%",
            color = DeckColors.SecondaryText,
            fontSize = 16.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 5.dp)
        )
    }
}

@Composable
private fun CpuUsageRows(snapshot: MetricSnapshot, cpuUsageColors: ServerCpuUsageColors) {
    val rows = snapshot.cpu.perCore.takeIf { it.isNotEmpty() } ?: List(snapshot.cpu.cores.coerceIn(1, 32)) { core ->
        CpuCoreMetrics(
            index = core,
            usagePercent = snapshot.cpu.usagePercent,
            userPercent = snapshot.cpu.userPercent,
            systemPercent = snapshot.cpu.systemPercent,
            nicePercent = snapshot.cpu.nicePercent,
            ioWaitPercent = snapshot.cpu.ioWaitPercent,
            stealPercent = snapshot.cpu.stealPercent
        )
    }
    val visibleRows = rows.take(32)
    val coreCount = visibleRows.size.coerceAtLeast(1)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val pillHeight = when {
            coreCount <= 8 -> 18.dp
            coreCount <= 16 -> 13.dp
            coreCount <= 24 -> 8.dp
            else -> 5.dp
        }
        val rowGap = when {
            coreCount <= 8 -> 5.dp
            coreCount <= 16 -> 4.dp
            else -> 3.dp
        }
        val showRowPercentages = coreCount <= 12
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(pillHeight * coreCount + rowGap * (coreCount - 1).coerceAtLeast(0))
        ) {
            val columns = 32
            val gap = when {
                coreCount <= 8 -> 4.dp
                coreCount <= 16 -> 3.dp
                else -> 2.dp
            }.toPx()
            val rowGapPx = rowGap.toPx()
            val cellWidth = ((size.width - gap * (columns - 1)) / columns).coerceIn(3f, 6.dp.toPx())
            val cellHeight = pillHeight.toPx()
            visibleRows.forEachIndexed { row, core ->
                val filledCells = ((core.usagePercent / 100f) * columns).toInt().coerceIn(0, columns)
                val userCells = ((core.userPercent / 100f) * columns).toInt().coerceIn(0, filledCells)
                val systemCells = ((core.systemPercent / 100f) * columns).toInt().coerceIn(0, (filledCells - userCells).coerceAtLeast(0))
                val niceCells = ((core.nicePercent / 100f) * columns).toInt().coerceIn(0, (filledCells - userCells - systemCells).coerceAtLeast(0))
                val ioWaitCells = ((core.ioWaitPercent / 100f) * columns).toInt().coerceIn(0, (filledCells - userCells - systemCells - niceCells).coerceAtLeast(0))
                val stealCells = (filledCells - userCells - systemCells - niceCells - ioWaitCells).coerceAtLeast(0)
                repeat(columns) { column ->
                    val color = when {
                        column >= filledCells -> DeckColors.Divider
                        column < userCells.coerceAtLeast(if (filledCells > 0) 1 else 0) -> cpuUsageColors.user
                        column < userCells + systemCells.coerceAtLeast(if (filledCells > 1) 1 else 0) -> cpuUsageColors.system
                        column < userCells + systemCells + niceCells -> cpuUsageColors.nice
                        column < userCells + systemCells + niceCells + ioWaitCells -> cpuUsageColors.ioWait
                        column < userCells + systemCells + niceCells + ioWaitCells + stealCells -> cpuUsageColors.steal
                        else -> cpuUsageColors.steal
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = androidx.compose.ui.geometry.Offset(column * (cellWidth + gap), row * (cellHeight + rowGapPx)),
                        size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellHeight / 2f, cellHeight / 2f),
                        style = Fill
                    )
                }
            }
        }
        if (showRowPercentages) {
            Column(verticalArrangement = Arrangement.spacedBy(rowGap), horizontalAlignment = Alignment.End) {
                visibleRows.forEach { core ->
                    Box(
                        modifier = Modifier.height(pillHeight),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        Text("${core.usagePercent}%", color = DeckColors.PrimaryText, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

private fun normalizedSeries(values: List<Float>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val max = values.maxOrNull()?.coerceAtLeast(0.6f) ?: 0.6f
    return values.map { (it / max).coerceIn(0f, 1f) }
}

private fun DrawScope.drawCpuLine(
    values: List<Float>,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    color: Color
) {
    if (values.isEmpty()) return
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1)
        val y = bottom - (bottom - top) * value.coerceIn(0f, 1f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
        drawPath(path, color = color, style = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawLoadTrace(
    values: List<Float>,
    maxValue: Float,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    color: Color,
    widthDp: Float
) {
    if (values.isEmpty()) return
    val path = Path()
    values.forEachIndexed { index, raw ->
        val x = if (values.size == 1) left else left + (right - left) * index / (values.size - 1)
        val y = bottom - (bottom - top) * (raw / maxValue).coerceIn(0f, 1f)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = widthDp.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.drawLoadArea(
    values: List<Float>,
    maxValue: Float,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
    color: Color
) {
    if (values.size < 2) return
    val path = Path()
    values.forEachIndexed { index, raw ->
        val x = left + (right - left) * index / (values.size - 1)
        val y = bottom - (bottom - top) * (raw / maxValue).coerceIn(0f, 1f)
        if (index == 0) path.moveTo(x, bottom) else Unit
        path.lineTo(x, y)
    }
    path.lineTo(right, bottom)
    path.close()
    drawPath(path, color = color.copy(alpha = 0.08f), style = Fill)
}

@Composable
private fun CpuStat(label: String, value: String, color: Color, modifier: Modifier = Modifier, horizontalAlignment: Alignment.Horizontal = Alignment.Start, showIndicator: Boolean = true) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (showIndicator) {
                Canvas(Modifier.size(7.dp)) { drawCircle(color) }
            }
            Text(label, color = DeckColors.SecondaryText, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CpuLoadCard(snapshot: MetricSnapshot, metricHistory: List<MetricSnapshot>, metricColors: ServerMetricColors) {
    val loadColors = cpuLoadLegendColors(metricColors)
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "pulse", title = "CPU Load", color = metricColors.cpu, compact = true)
        Spacer(Modifier.height(10.dp))
        CpuLoadChart(snapshot, metricHistory, loadColors)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Legend("1m", loadColors.oneMinute)
            Legend("5m", loadColors.fiveMinute)
            Legend("15m", loadColors.fifteenMinute)
        }
        Spacer(Modifier.height(11.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Current Load", color = DeckColors.PrimaryText, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("${snapshot.cpu.load1.twoDecimals()} / ${snapshot.cpu.load5.twoDecimals()} / ${snapshot.cpu.load15.twoDecimals()}", color = DeckColors.SecondaryText, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
private fun MetricTitle(icon: String, title: String, color: Color, compact: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp)) {
        MetricGlyph(icon, color, Modifier.size(if (compact) 18.dp else 24.dp))
        Text(title, color = color, fontSize = if (compact) 20.sp else 25.sp, lineHeight = if (compact) 22.sp else 28.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SensorsCard(sensors: List<SensorMetric>, metricColors: ServerMetricColors) {
    if (sensors.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "sensor", title = "Sensors", color = metricColors.latency, compact = true)
        Spacer(Modifier.height(10.dp))
        sensors.take(6).forEachIndexed { index, sensor ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(sensor.device, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${sensor.adapter} / ${sensor.label}", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(sensor.value, color = metricColors.latency, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BatteryCard(batteries: List<BatteryMetric>, metricColors: ServerMetricColors) {
    if (batteries.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "battery", title = "Battery", color = metricColors.disk, compact = true)
        Spacer(Modifier.height(10.dp))
        batteries.take(4).forEachIndexed { index, battery ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(battery.name, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(batteryDetailLabel(battery), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(battery.capacityPercent?.let { "$it%" } ?: "--", color = metricColors.disk, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

internal fun batteryDetailLabel(battery: BatteryMetric): String {
    val time = battery.timeToEmptySeconds?.let { "Empty in ${batteryTimeLabel(it)}" }
        ?: battery.timeToFullSeconds?.let { "Full in ${batteryTimeLabel(it)}" }
    return listOf(battery.status, battery.health, battery.technology, time)
        .filter { !it.isNullOrBlank() && it != "--" }
        .joinToString(" / ")
        .ifBlank { "--" }
}

private fun batteryTimeLabel(seconds: Long): String {
    val minutes = (seconds / 60).coerceAtLeast(1)
    val hours = minutes / 60
    val remainder = minutes % 60
    return when {
        hours > 0 && remainder > 0 -> "${hours}h ${remainder}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

@Composable
private fun FilesystemsCard(filesystems: List<FilesystemMetric>, metricColors: ServerMetricColors) {
    if (filesystems.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "disk", title = "Filesystems", color = metricColors.disk, compact = true)
        Spacer(Modifier.height(10.dp))
        filesystems.take(6).forEachIndexed { index, filesystem ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(filesystem.sourcePath.ifBlank { filesystem.mountPoint }, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(filesystemDetailLabel(filesystem), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text("${filesystem.usagePercent}%", color = metricColors.disk, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

internal fun filesystemDetailLabel(filesystem: FilesystemMetric): String {
    val path = listOf(filesystem.filesystem, filesystem.mountPoint)
        .filter { it.isNotBlank() }
        .joinToString(" on ")
    val free = (filesystem.totalGb - filesystem.usedGb).coerceAtLeast(0f)
    val usedLabel = if (filesystem.totalGb > 0f) "Used ${MetricFormatters.gigaBytesLabel(filesystem.usedGb)}" else ""
    val totalLabel = if (filesystem.totalGb > 0f) "Total ${MetricFormatters.gigaBytesLabel(filesystem.totalGb)}" else ""
    val freeLabel = if (filesystem.totalGb > 0f) "Free ${MetricFormatters.gigaBytesLabel(free)}" else ""
    val capacity = listOf(
        usedLabel.takeIf { it.isNotBlank() },
        totalLabel.takeIf { it.isNotBlank() },
        freeLabel
    ).filterNotNull().joinToString(" / ")
    return listOf(capacity, path).filter { it.isNotBlank() }.joinToString(" - ")
}

@Composable
private fun SmartDisksCard(disks: List<SmartDiskMetric>, metricColors: ServerMetricColors) {
    if (disks.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "disk", title = "SMART", color = metricColors.disk, compact = true)
        Spacer(Modifier.height(10.dp))
        disks.take(4).forEachIndexed { index, disk ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(smartDiskTitle(disk), color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(smartDiskSummary(disk), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                Text(smartDiskBadge(disk), color = if (disk.healthy == false) DeckColors.Red else metricColors.disk, fontSize = 20.sp, lineHeight = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
    }
}

internal fun smartDiskTitle(disk: SmartDiskMetric): String {
    return listOfNotNull(disk.device, disk.model).joinToString(" - ")
}

internal fun smartDiskBadge(disk: SmartDiskMetric): String {
    return when (disk.healthy) {
        true -> "OK"
        false -> "Fail"
        null -> disk.lifeLeftPercent?.let { "$it%" } ?: disk.temperatureCelsius?.let { "${it}C" } ?: "--"
    }
}

internal fun smartDiskSummary(disk: SmartDiskMetric): String {
    return listOfNotNull(
        disk.serial?.let { "SN $it" },
        disk.selfTestStatus?.let { "Self-test: $it" },
        disk.temperatureCelsius?.let { "${it}C" },
        disk.powerOnHours?.let { "${it}h" },
        disk.powerCycleCount?.let { "$it cycles" },
        disk.unsafeShutdowns?.let { "$it unsafe" }
    ).ifEmpty { listOf("--") }.joinToString(" / ")
}

@Composable
private fun ProcessesCard(
    processes: List<ProcessMetric>,
    onProcessAction: (ProcessMetric, String) -> Unit,
    metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme),
    summary: ProcessSummary? = null,
    limit: Int = 6,
    onOpenAll: (() -> Unit)? = null,
    showPendingWhenEmpty: Boolean = false,
    showActions: Boolean = true
) {
    if (processes.isEmpty() && summary?.hasProcessSummary() != true && !showPendingWhenEmpty) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onOpenAll != null) Modifier.clickable(onClick = onOpenAll) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricTitle(icon = "pulse", title = "Top Processes", color = metricColors.cpu, compact = true)
            if (onOpenAll != null) {
                Spacer(Modifier.weight(1f))
                DetailChevronGlyph(DeckColors.SecondaryText, Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        summary?.takeIf { it.hasProcessSummary() }?.let {
            Text(processSummaryLabel(it), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (processes.isNotEmpty()) Spacer(Modifier.height(9.dp))
        }
        processes.take(limit).forEachIndexed { index, process ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            ProcessMetricRow(process, onProcessAction, showActions, metricColors)
        }
        if (processes.isEmpty()) {
            Text(processEmptyStatus(summary), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
        }
    }
}

private fun ProcessSummary.hasProcessSummary(): Boolean {
    return total > 0 || running > 0 || (topProcess.isNotBlank() && topProcess != "--")
}

private fun processSummaryLabel(summary: ProcessSummary): String {
    val counts = if (summary.total > 0) "${summary.running}/${summary.total} running" else ""
    val top = summary.topProcess.ifBlank { "" }.takeIf { it != "--" }?.let { "Top: $it" }.orEmpty()
    return listOf(counts, top).filter { it.isNotBlank() }.joinToString(" - ")
}

internal fun processEmptyStatus(summary: ProcessSummary?): String {
    return if (summary?.hasProcessSummary() == true) {
        "Process totals are available; detailed rows are pending refresh."
    } else {
        "No process data yet. Metrics may still be pending or the last refresh timed out."
    }
}

internal enum class ProcessSortMode(val label: String) {
    Cpu("CPU"),
    Memory("RAM"),
    Pid("PID")
}

internal data class ProcessPageMetric(
    val label: String,
    val value: String,
    val colorName: String
)

internal fun processPageMetrics(summary: ProcessSummary?, processes: List<ProcessMetric>): List<ProcessPageMetric> {
    val running = summary?.running?.takeIf { it > 0 } ?: processes.count { it.state.startsWith("R", ignoreCase = true) }
    val total = summary?.total?.takeIf { it > 0 } ?: processes.size
    val cpu = processes.mapNotNull { it.cpuPercent }.maxOrNull()
    val memory = processes.mapNotNull { it.memoryPercent }.maxOrNull()
    return listOf(
        ProcessPageMetric("Running", if (total > 0) "$running/$total" else "--", "green"),
        ProcessPageMetric("Top CPU", cpu?.let { "${it.cleanNumber()}%" } ?: "--", "orange"),
        ProcessPageMetric("Top RAM", memory?.let { "${it.cleanNumber()}%" } ?: "--", "cyan")
    )
}

internal fun sortedProcesses(processes: List<ProcessMetric>, sortMode: ProcessSortMode): List<ProcessMetric> {
    return when (sortMode) {
        ProcessSortMode.Cpu -> processes.sortedWith(compareByDescending<ProcessMetric> { it.cpuPercent ?: -1f }.thenBy { it.command })
        ProcessSortMode.Memory -> processes.sortedWith(compareByDescending<ProcessMetric> { it.memoryPercent ?: -1f }.thenBy { it.command })
        ProcessSortMode.Pid -> processes.sortedWith(compareBy<ProcessMetric> { it.pid ?: Int.MAX_VALUE }.thenBy { it.command })
    }
}

@Composable
private fun ProcessesPage(
    summary: ProcessSummary,
    onProcessAction: (ProcessMetric, String) -> Unit,
    metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme)
) {
    var sortMode by remember { mutableStateOf(ProcessSortMode.Cpu) }
    if (summary.items.isEmpty() && !summary.hasProcessSummary()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "pulse", title = "Processes", color = metricColors.cpu, compact = true)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            processPageMetrics(summary, summary.items).forEach { metric ->
                ProcessPageMetricChip(metric, metricColors, Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ProcessSortMode.entries.forEach { mode ->
                ProcessSortChip(mode, selected = sortMode == mode, metricColors = metricColors, modifier = Modifier.weight(1f)) {
                    sortMode = mode
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ProcessesCard(sortedProcesses(summary.items, sortMode), onProcessAction, metricColors, summary = summary, limit = Int.MAX_VALUE)
}

@Composable
private fun ProcessPageMetricChip(metric: ProcessPageMetric, metricColors: ServerMetricColors, modifier: Modifier = Modifier) {
    val color = when (metric.colorName) {
        "green" -> DeckColors.Green
        "orange" -> metricColors.cpu
        else -> metricColors.memory
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(metric.label, color = DeckColors.SecondaryText, fontSize = 11.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(metric.value, color = color, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ProcessSortChip(mode: ProcessSortMode, selected: Boolean, metricColors: ServerMetricColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (selected) metricColors.cpu else DeckColors.SecondaryText
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = if (selected) 0.16f else 0.08f))
            .border(1.dp, color.copy(alpha = if (selected) 0.34f else 0.18f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(mode.label, color = color, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun DetailChevronGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.24f), androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.38f, size.height * 0.76f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun ProcessMetricRow(process: ProcessMetric, onProcessAction: (ProcessMetric, String) -> Unit, showActions: Boolean = true, metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme)) {
    Column(Modifier.fillMaxWidth()) {
        DetailMetricRow(
            title = process.command,
            subtitle = processSummary(process),
            value = process.cpuPercent?.let { "${it.cleanNumber()}%" } ?: process.state,
            color = if (process.state.startsWith("R")) metricColors.cpu else DeckColors.SecondaryText
        )
        val actions = ProcessActionPolicy.actionsFor(process.pid)
        if (showActions && actions.isNotEmpty()) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { action ->
                    RuntimeActionButton(action) {
                        onProcessAction(process, action)
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedServicesCard(
    services: List<ServiceMetric>,
    onServiceAction: (ServiceMetric, String) -> Unit,
    limit: Int = 6,
    showActions: Boolean = true
) {
    if (services.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "pulse", title = "Failed Services", color = DeckColors.Orange, compact = true)
        Spacer(Modifier.height(10.dp))
        services.take(limit).forEachIndexed { index, service ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            ServiceMetricRow(service, onServiceAction, showActions)
        }
    }
}

@Composable
private fun SystemdServicesPage(
    summary: ServiceSummary,
    onServiceAction: (ServiceMetric, String) -> Unit,
    metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme),
    limit: Int = Int.MAX_VALUE,
    onOpenAll: (() -> Unit)? = null,
    showActions: Boolean = true,
    showPendingWhenEmpty: Boolean = false
) {
    val services = summary.items.ifEmpty { summary.failedItems }
    if (!summary.shouldShowSystemdCard() && !showPendingWhenEmpty) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onOpenAll != null) Modifier.clickable(onClick = onOpenAll) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricTitle(icon = "service", title = "Systemd Services", color = metricColors.latency, compact = true)
            if (onOpenAll != null) {
                Spacer(Modifier.weight(1f))
                DetailChevronGlyph(DeckColors.SecondaryText, Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(systemdSummaryLabel(summary), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
        if (services.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(systemdEmptyStatus(summary), color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
        } else {
            Spacer(Modifier.height(10.dp))
            services.take(limit).forEachIndexed { index, service ->
                if (index > 0) Spacer(Modifier.height(9.dp))
                ServiceMetricRow(service, onServiceAction, showActions)
            }
        }
    }
}

internal fun systemdSummaryLabel(summary: ServiceSummary): String {
    return if (summary.shouldShowSystemdCard()) "${summary.total} services · ${summary.failed} failed" else "Service status unavailable"
}

internal fun systemdEmptyStatus(summary: ServiceSummary): String {
    return if (summary.shouldShowSystemdCard()) {
        "Service totals are available; detailed rows are pending refresh."
    } else {
        "No systemd data yet. Metrics may still be pending or this host may not use systemd."
    }
}

@Composable
private fun ServiceMetricRow(service: ServiceMetric, onServiceAction: (ServiceMetric, String) -> Unit, showActions: Boolean = true) {
    Column(Modifier.fillMaxWidth()) {
        DetailMetricRow(
            title = service.unit,
            subtitle = service.description,
            value = service.sub.ifBlank { service.active },
            color = serviceStateColor(service)
        )
        if (showActions) {
            Spacer(Modifier.height(7.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemdServiceActionPolicy.actionsFor(service.active, service.sub).forEach { action ->
                    RuntimeActionButton(action) {
                        onServiceAction(service, action)
                    }
                }
            }
        }
    }
}

private data class ContainerOutputRequest(val container: ContainerMetric, val action: String)

@Composable
private fun ContainersCard(
    summary: DockerSummary,
    onContainerAction: (ContainerMetric, String) -> Unit,
    metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme),
    onContainerOutput: (ContainerMetric, String) -> Unit = onContainerAction,
    limit: Int = 3,
    showActions: Boolean = true
) {
    if (!summary.shouldShowContainerCard()) return
    ContainerSectionCard(
        title = "Container",
        subtitle = containerSummaryLabel(summary),
        color = metricColors.network,
        expanded = true,
        onToggle = {}
    ) {
        val rows = summary.items.take(limit)
        if (rows.isEmpty()) {
            containerEmptyStatus(summary).takeIf { it.isNotBlank() }?.let { status ->
                Text(status, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
            }
        } else {
            rows.forEachIndexed { index, container ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                if (showActions) {
                    ContainerMetricRow(container, onContainerAction, onContainerOutput, metricColors)
                } else {
                    DetailMetricRow(
                        title = container.name.ifBlank { container.id },
                        subtitle = containerSummary(container),
                        value = container.state.ifBlank { "--" },
                        color = if (container.state.equals("running", ignoreCase = true)) DeckColors.Green else DeckColors.Orange
                    )
                }
            }
        }
    }
}

@Composable
private fun ContainersToolPage(
    summary: DockerSummary,
    onContainerAction: (ContainerMetric, String) -> Unit,
    onContainerOutput: (ContainerMetric, String) -> Unit,
    metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme)
) {
    var containersExpanded by remember { mutableStateOf(true) }
    var imagesExpanded by remember { mutableStateOf(true) }
    var pruneExpanded by remember { mutableStateOf(false) }
    ContainerSectionCard(
        title = "Container",
        subtitle = containerSummaryLabel(summary),
        color = metricColors.network,
        expanded = containersExpanded,
        onToggle = { containersExpanded = !containersExpanded }
    ) {
        if (summary.items.isEmpty()) {
            containerEmptyStatus(summary).takeIf { it.isNotBlank() }?.let { status ->
                Text(status, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
            }
        } else {
            summary.items.forEachIndexed { index, container ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                ContainerMetricRow(container, onContainerAction, onContainerOutput, metricColors)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    ContainerSectionCard(
        title = "Images",
        subtitle = "${summary.images.size} images",
        color = metricColors.disk,
        expanded = imagesExpanded,
        onToggle = { imagesExpanded = !imagesExpanded }
    ) {
        if (summary.images.isEmpty()) {
            Text("No images found. Metrics may still be pending or Docker/Podman may be unavailable.", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 15.sp)
        } else {
            summary.images.forEachIndexed { index, image ->
                if (index > 0) Spacer(Modifier.height(10.dp))
            ContainerImageRow(image, metricColors, onImageAction = { selected, action ->
                    onContainerAction(ContainerMetric(selected.id, selected.repository, "", "", "", selected.engine), action)
                })
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    if (summary.shouldShowContainerCard()) {
        ContainerPruneCard(summary, pruneExpanded, onToggle = { pruneExpanded = !pruneExpanded }, onContainerAction)
    }
}

@Composable
private fun ContainerSectionCard(
    title: String,
    subtitle: String,
    color: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                MetricTitle(icon = "container", title = title, color = color, compact = true)
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (expanded) "^" else "v", color = DeckColors.SecondaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
        if (expanded) {
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ContainerImagesCard(images: List<ContainerImageMetric>, onImageAction: (ContainerImageMetric, String) -> Unit, metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme)) {
    ContainerSectionCard(
        title = "Images",
        subtitle = "${images.size} images",
        color = metricColors.disk,
        expanded = true,
        onToggle = {}
    ) {
        images.forEachIndexed { index, image ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            ContainerImageRow(image, metricColors, onImageAction)
        }
    }
}

@Composable
private fun ContainerActionMenu(actions: List<String>, onAction: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        CircleIconButton("...", "Actions", modifier = Modifier.size(42.dp), onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(14.dp),
            containerColor = DeckColors.Surface,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(DeckColors.Surface)
                .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(14.dp))
        ) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { HostMenuText(runtimeActionDescription(action)) },
                    onClick = {
                        expanded = false
                        onAction(action)
                    }
                )
            }
        }
    }
}

@Composable
private fun ContainerImageRow(image: ContainerImageMetric, metricColors: ServerMetricColors, onImageAction: (ContainerImageMetric, String) -> Unit) {
    var expanded by remember(image.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted.copy(alpha = 0.45f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(image.repository.substringAfterLast('/').ifBlank { image.id }, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${image.repository}:${image.tag}", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(image.size.ifBlank { "--" }, color = metricColors.disk, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            ContainerActionMenu(listOf("remove-image")) { onImageAction(image, it) }
        }
        if (expanded) {
            Spacer(Modifier.height(9.dp))
            ContainerInfoGrid(
                listOf(
                    "Engine" to image.engine,
                    "Tag" to image.tag,
                    "ID" to image.id,
                    "Created" to image.created
                )
            )
        }
    }
}

@Composable
private fun ContainerPruneCard(
    summary: DockerSummary,
    expanded: Boolean,
    onToggle: () -> Unit,
    onContainerAction: (ContainerMetric, String) -> Unit
) {
    val engine = summary.items.firstOrNull()?.engine ?: summary.images.firstOrNull()?.engine ?: "docker"
    val target = ContainerMetric("", engine, "", "", "", engine)
    ContainerSectionCard(
        title = "Prune",
        subtitle = "Clean unused runtime objects",
        color = DeckColors.Orange,
        expanded = expanded,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ContainerRuntimeActionPolicy.globalActions().forEach { action ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(DeckColors.SurfaceMuted.copy(alpha = 0.45f))
                        .clickable { onContainerAction(target, action) }
                        .padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(runtimeActionDescription(action), color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("...", color = DeckColors.SecondaryText, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

internal fun containerSummaryLabel(summary: DockerSummary): String {
    return if (summary.shouldShowContainerCard()) "${summary.running}/${summary.containers} running" else "Container status unavailable"
}

internal fun containerEmptyStatus(summary: DockerSummary): String {
    return if (summary.shouldShowContainerCard()) {
        "Container totals are available; detailed rows are pending refresh."
    } else {
        "No container data yet. Metrics may still be pending or Docker/Podman may be unavailable."
    }
}

internal fun ServiceSummary.shouldShowSystemdCard(): Boolean = total > 0 || failed > 0 || items.isNotEmpty() || failedItems.isNotEmpty()

internal fun DockerSummary.shouldShowContainerCard(): Boolean = containers > 0 || running > 0 || items.isNotEmpty() || images.isNotEmpty()

@Composable
private fun ContainerMetricRow(
    container: ContainerMetric,
    onContainerAction: (ContainerMetric, String) -> Unit,
    onContainerOutput: (ContainerMetric, String) -> Unit = onContainerAction,
    metricColors: ServerMetricColors = metricColorsFor(ServerMetricColorPreset.Theme)
) {
    var expanded by remember(container.engine, container.id, container.name) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted.copy(alpha = 0.45f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(container.name.ifBlank { container.id }, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(container.image.ifBlank { container.status.ifBlank { container.engine } }, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(container.state.ifBlank { "--" }, color = if (container.state.equals("running", ignoreCase = true)) metricColors.memory else metricColors.disk, fontSize = 13.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            ContainerActionMenu(ContainerRuntimeActionPolicy.actionsFor(container.state)) { action ->
                if (action == "logs" || action == "inspect" || action == "stats") {
                    onContainerOutput(container, action)
                } else {
                    onContainerAction(container, action)
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.height(9.dp))
            ContainerInfoGrid(
                listOf(
                    "CPU" to container.cpuPercent?.let { "${it.twoDecimals()}%" }.orEmpty(),
                    "Mem" to containerMemoryLabel(container),
                    "Net" to container.networkIo,
                    "Disk" to container.blockIo,
                    "Engine" to container.engine,
                    "ID" to container.id,
                    "Status" to container.status,
                    "Image" to container.image
                )
            )
        }
    }
}

@Composable
private fun ContainerInfoGrid(items: List<Pair<String, String>>) {
    val visible = items.filter { it.second.isNotBlank() && it.second != "--" }
    if (visible.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        visible.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = DeckColors.SecondaryText, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(58.dp), maxLines = 1)
                Text(value, color = DeckColors.PrimaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun containerMemoryLabel(container: ContainerMetric): String {
    val percent = container.memoryPercent?.let { "${it.twoDecimals()}%" }
    val used = container.memoryUsedBytes?.let { MetricFormatters.bytesLabel(it) }
    val max = container.memoryMaxBytes?.let { MetricFormatters.bytesLabel(it) }
    return listOfNotNull(percent, if (used != null && max != null) "$used / $max" else used).joinToString(" ")
}

@Composable
private fun ContainerOutputPage(
    title: String,
    subtitle: String,
    container: ContainerMetric,
    action: String,
    metricColors: ServerMetricColors,
    onBack: () -> Unit,
    onLoad: (ContainerMetric, String, (String) -> Unit) -> Unit
) {
    var body by remember(container.id, action) { mutableStateOf("Loading...") }
    LaunchedEffect(container.id, action) {
        onLoad(container, action) { body = it.ifBlank { "No output." } }
    }
    BackHandler(onBack = onBack)
    val inspectSummary = remember(body, action) { if (action == "inspect") containerInspectSummary(body) else null }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton("<", "Back", modifier = Modifier.size(64.dp), onClick = onBack)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = DeckColors.PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = DeckColors.SecondaryText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.size(64.dp))
        }
        Spacer(Modifier.height(18.dp))
        if (inspectSummary != null) {
            ContainerInspectView(inspectSummary, body, metricColors)
        } else {
            ContainerTerminalOutput(body)
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun ContainerTerminalOutput(body: String) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
        SelectionContainer {
            Text(
                body,
                color = DeckColors.PrimaryText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
                softWrap = false,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun ContainerInspectView(summary: ContainerInspectSummary, raw: String, metricColors: ServerMetricColors) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
            MetricTitle(icon = "container", title = summary.name.ifBlank { "Inspect" }, color = metricColors.network, compact = true)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ContainerInspectStat("State", summary.state.ifBlank { "--" }, DeckColors.Green, Modifier.weight(1f))
                ContainerInspectStat("Image", summary.image.substringAfterLast('/').ifBlank { "--" }, metricColors.disk, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ContainerInspectStat("Restart", summary.restartPolicy.ifBlank { "--" }, metricColors.latency, Modifier.weight(1f))
                ContainerInspectStat("Network", summary.networkMode.ifBlank { "--" }, metricColors.network, Modifier.weight(1f))
            }
        }
        listOf(
            "Identity" to listOf("ID" to summary.id, "Created" to summary.created, "Command" to summary.command),
            "Runtime" to listOf("Status" to summary.status, "Started" to summary.startedAt, "Finished" to summary.finishedAt),
            "Limits" to listOf("CPUs" to summary.nanoCpus, "Memory" to summary.memory, "Swap" to summary.memorySwap),
            "Network" to listOf("Hostname" to summary.hostname, "IP" to summary.ipAddress, "Gateway" to summary.gateway)
        ).forEach { (title, items) ->
            DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
                Text(title, color = DeckColors.PrimaryText, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                ContainerInfoGrid(items)
            }
        }
        DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(16.dp)) {
            Text("Raw", color = DeckColors.PrimaryText, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(10.dp))
            SelectionContainer {
                Text(
                    raw,
                    color = DeckColors.SecondaryText,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            }
        }
    }
}

@Composable
private fun ContainerInspectStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.24f), RoundedCornerShape(16.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(label, color = DeckColors.SecondaryText, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(5.dp))
        Text(value, color = DeckColors.PrimaryText, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

private data class ContainerInspectSummary(
    val id: String = "",
    val name: String = "",
    val image: String = "",
    val created: String = "",
    val command: String = "",
    val state: String = "",
    val status: String = "",
    val startedAt: String = "",
    val finishedAt: String = "",
    val restartPolicy: String = "",
    val networkMode: String = "",
    val hostname: String = "",
    val ipAddress: String = "",
    val gateway: String = "",
    val nanoCpus: String = "",
    val memory: String = "",
    val memorySwap: String = ""
)

private fun containerInspectSummary(raw: String): ContainerInspectSummary {
    fun value(key: String): String {
        val match = Regex(""""$key"\s*:\s*("([^"]*)"|[-0-9.]+|true|false|null)""").find(raw) ?: return ""
        return match.groupValues.getOrNull(2).orEmpty().ifBlank { match.groupValues[1].trim('"') }.takeUnless { it == "null" }.orEmpty()
    }
    fun objectValue(objectKey: String, key: String): String {
        val start = raw.indexOf(""""$objectKey"""")
        if (start < 0) return ""
        val end = raw.indexOf("\n  },", start).takeIf { it > start } ?: (start + 3000).coerceAtMost(raw.length)
        return valueIn(raw.substring(start, end), key)
    }
    fun bytes(rawValue: String): String = rawValue.toLongOrNull()?.takeIf { it > 0L }?.let { MetricFormatters.bytesLabel(it) }.orEmpty()
    val name = value("Name").trim('/').ifBlank { value("Hostname") }
    return ContainerInspectSummary(
        id = value("Id").take(12),
        name = name,
        image = value("Image"),
        created = value("Created"),
        command = value("Path") + value("Args").takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty(),
        state = objectValue("State", "Status").ifBlank { value("State") },
        status = objectValue("State", "Status"),
        startedAt = objectValue("State", "StartedAt"),
        finishedAt = objectValue("State", "FinishedAt"),
        restartPolicy = objectValue("RestartPolicy", "Name"),
        networkMode = objectValue("HostConfig", "NetworkMode"),
        hostname = objectValue("Config", "Hostname"),
        ipAddress = value("IPAddress"),
        gateway = value("Gateway"),
        nanoCpus = objectValue("HostConfig", "NanoCpus").toLongOrNull()?.takeIf { it > 0L }?.let { "%.2f".format(it / 1_000_000_000.0) }.orEmpty(),
        memory = bytes(objectValue("HostConfig", "Memory")),
        memorySwap = bytes(objectValue("HostConfig", "MemorySwap"))
    )
}

private fun valueIn(source: String, key: String): String {
    val match = Regex(""""$key"\s*:\s*("([^"]*)"|[-0-9.]+|true|false|null)""").find(source) ?: return ""
    return match.groupValues.getOrNull(2).orEmpty().ifBlank { match.groupValues[1].trim('"') }.takeUnless { it == "null" }.orEmpty()
}

@Composable
private fun RuntimeActionButton(action: String, onClick: () -> Unit) {
    val tone = runtimeActionTone(action)
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(tone.copy(alpha = 0.14f))
            .border(1.dp, tone.copy(alpha = 0.34f), RoundedCornerShape(14.dp))
            .semantics { contentDescription = runtimeActionDescription(action) }
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        RuntimeActionGlyph(action, tone, Modifier.size(18.dp))
    }
}

@Composable
private fun RuntimeActionGlyph(action: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.9.dp.toPx(), cap = StrokeCap.Round)
        when (action.trim().lowercase()) {
            "start" -> {
                val path = Path().apply {
                    moveTo(size.width * 0.36f, size.height * 0.24f)
                    lineTo(size.width * 0.74f, size.height * 0.50f)
                    lineTo(size.width * 0.36f, size.height * 0.76f)
                    close()
                }
                drawPath(path, color)
            }
            "stop", "force-stop" -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.28f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.44f, size.height * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = Fill
                )
                if (action.trim().equals("force-stop", ignoreCase = true)) {
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.22f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.22f), androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.78f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "restart" -> {
                drawArc(
                    color = color,
                    startAngle = 42f,
                    sweepAngle = 292f,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.20f, size.height * 0.20f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.60f, size.height * 0.60f),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.22f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.42f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.22f), androidx.compose.ui.geometry.Offset(size.width * 0.52f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "status" -> {
                drawCircle(color, radius = size.minDimension * 0.32f, center = center, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.46f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.68f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.045f, center = androidx.compose.ui.geometry.Offset(center.x, size.height * 0.32f))
            }
            "logs" -> {
                listOf(0.30f, 0.50f, 0.70f).forEach { y ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * y), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "inspect" -> {
                drawCircle(color, radius = size.minDimension * 0.32f, center = center, style = stroke)
                drawCircle(color, radius = size.minDimension * 0.045f, center = center)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.68f), androidx.compose.ui.geometry.Offset(size.width * 0.82f, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "stats" -> {
                listOf(0.32f, 0.50f, 0.68f).forEachIndexed { index, y ->
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * y), androidx.compose.ui.geometry.Offset(size.width * (0.52f + index * 0.10f), size.height * y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            "lower-priority" -> {
                drawRoundRect(
                    color = color.copy(alpha = 0.16f),
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.20f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.72f, size.height * 0.60f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Fill
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.38f), androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.64f), androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.46f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.035f, center = androidx.compose.ui.geometry.Offset(size.width * 0.44f, size.height * 0.36f))
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.52f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.52f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            else -> {
                drawCircle(color, radius = size.minDimension * 0.22f, center = center)
            }
        }
    }
}

internal fun runtimeActionDescription(action: String): String {
    return when (action.trim().lowercase()) {
        "start" -> "Start"
        "stop" -> "Stop"
        "restart" -> "Restart"
        "status" -> "Status"
        "logs" -> "Logs"
        "inspect" -> "Inspect"
        "stats" -> "Stats"
        "images" -> "Images"
        "delete" -> "Delete"
        "force-delete" -> "Force delete"
        "remove-image" -> "Remove image"
        "prune-containers" -> "Prune containers"
        "prune-images" -> "Prune images"
        "prune-volumes" -> "Prune volumes"
        "prune-system" -> "Prune system"
        "lower-priority" -> "Lower priority"
        "terminate" -> "Terminate"
        "force-stop" -> "Force stop"
        else -> "Run action"
    }
}

internal fun runtimeActionNeedsConfirmation(action: String): Boolean {
    return when (action.trim().lowercase()) {
        "stop", "restart", "terminate", "force-stop", "delete", "force-delete", "remove-image", "prune-containers", "prune-images", "prune-volumes", "prune-system" -> true
        else -> false
    }
}

private fun runtimeActionTone(action: String): Color {
    return when (action.trim().lowercase()) {
        "start" -> DeckColors.Green
        "stop", "terminate", "delete", "remove-image" -> DeckColors.Orange
        "force-stop", "force-delete" -> DeckColors.Red
        "restart" -> DeckColors.Cyan
        "status", "logs", "inspect", "stats", "images" -> DeckColors.SecondaryText
        "prune-containers", "prune-images", "prune-volumes", "prune-system" -> DeckColors.Orange
        "lower-priority" -> DeckColors.Purple
        else -> DeckColors.PrimaryText
    }
}

@Composable
private fun GpusCard(gpus: List<GpuMetric>, metricColors: ServerMetricColors) {
    if (gpus.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "gpu", title = "GPU", color = metricColors.memory, compact = true)
        Spacer(Modifier.height(10.dp))
        gpus.take(6).forEachIndexed { index, gpu ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            DetailMetricRow(
                title = gpu.name.ifBlank { gpu.id },
                subtitle = gpuSummary(gpu),
                value = gpu.utilizationPercent?.let { "$it%" } ?: "--",
                color = metricColors.memory
            )
        }
    }
}

@Composable
private fun ProxmoxResourcesCard(resources: List<PveResourceMetric>, metricColors: ServerMetricColors) {
    if (resources.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        MetricTitle(icon = "cluster", title = "Proxmox", color = metricColors.network, compact = true)
        Spacer(Modifier.height(10.dp))
        resources.take(8).forEachIndexed { index, resource ->
            if (index > 0) Spacer(Modifier.height(9.dp))
            DetailMetricRow(
                title = resource.name.ifBlank { resource.id },
                subtitle = pveResourceSummary(resource),
                value = resource.status.ifBlank { "--" },
                color = if (resource.status.equals("running", ignoreCase = true) || resource.status.equals("online", ignoreCase = true)) DeckColors.Green else DeckColors.SecondaryText
            )
        }
    }
}

internal fun serviceStateColor(service: ServiceMetric): Color {
    val active = service.active.trim().lowercase()
    val sub = service.sub.trim().lowercase()
    return when {
        sub == "auto-restart" -> DeckColors.PrimaryText
        sub == "exited" -> DeckColors.SecondaryText
        active == "active" && sub == "running" -> DeckColors.Cyan
        active == "active" -> DeckColors.Green
        active == "failed" || sub in setOf("failed", "dead") -> DeckColors.Red
        active == "inactive" || active == "deactivating" || active == "activating" -> DeckColors.SecondaryText
        else -> DeckColors.SecondaryText
    }
}

@Composable
private fun DetailMetricRow(title: String, subtitle: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        Text(value, color = color, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun containerSummary(container: ContainerMetric): String {
    return listOfNotNull(
        container.engine,
        container.image,
        container.status,
        container.cpuPercent?.let { "${it.cleanNumber()}% CPU" },
        container.memoryUsedBytes?.let { used ->
            container.memoryMaxBytes?.let { total -> "${MetricFormatters.bytesLabel(used)} / ${MetricFormatters.bytesLabel(total)} RAM" }
        } ?: container.memoryPercent?.let { "${it.cleanNumber()}% RAM" }
    )
        .filter { it.isNotBlank() }
        .ifEmpty { listOf("--") }
        .joinToString(" / ")
}

internal fun processSummary(process: ProcessMetric): String {
    return listOfNotNull(
        process.pid?.let { "PID $it" },
        process.parentPid?.let { "PPID $it" },
        process.user.takeIf { it.isNotBlank() },
        process.state.takeIf { it.isNotBlank() },
        process.memoryPercent?.let { "${it.cleanNumber()}% RAM" },
        process.rssKb?.let { "${MetricFormatters.bytesLabel(it * 1024)} RSS" },
        process.virtualSizeKb?.let { "${MetricFormatters.bytesLabel(it * 1024)} VSZ" },
        process.elapsed.takeIf { it.isNotBlank() }?.let { "Age $it" }
    ).ifEmpty { listOf("--") }.joinToString(" / ")
}

internal fun gpuSummary(gpu: GpuMetric): String {
    return listOfNotNull(
        gpu.vendor.takeIf { it.isNotBlank() },
        gpu.memoryUsedMiB?.let { used -> gpu.memoryTotalMiB?.let { total -> "${used}/${total} MiB" } },
        gpu.temperatureCelsius?.let { "${it}C" },
        gpu.powerDrawWatts?.let { power -> gpu.powerLimitWatts?.let { limit -> "${power.cleanNumber()}/${limit.cleanNumber()} W" } ?: "${power.cleanNumber()} W" },
        gpu.fanSpeed?.takeIf { it.isNotBlank() },
        gpu.clockMhz?.let { "${it} MHz" }
    ).ifEmpty { listOf("--") }.joinToString(" / ")
}

internal fun pveResourceSummary(resource: PveResourceMetric): String {
    return listOfNotNull(
        resource.type.takeIf { it.isNotBlank() },
        resource.node.takeIf { it.isNotBlank() },
        resource.cpuUsagePercent?.let { "$it% CPU" },
        resource.memoryUsedBytes?.let { used -> resource.memoryMaxBytes?.let { total -> "${MetricFormatters.bytesLabel(used)} / ${MetricFormatters.bytesLabel(total)} RAM" } },
        resource.diskUsedBytes?.let { used -> resource.diskMaxBytes?.let { total -> "${MetricFormatters.bytesLabel(used)} / ${MetricFormatters.bytesLabel(total)} disk" } }
    ).ifEmpty { listOf("--") }.joinToString(" / ")
}

private fun Float.cleanNumber(): String {
    return if (this % 1f == 0f) toInt().toString() else "%.1f".format(this)
}

@Composable
private fun MetricGlyph(icon: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
        when (icon) {
            "pulse" -> {
                val mid = size.height * 0.55f
                val path = Path().apply {
                    moveTo(0f, mid)
                    lineTo(size.width * 0.2f, mid)
                    lineTo(size.width * 0.3f, size.height * 0.18f)
                    lineTo(size.width * 0.42f, size.height * 0.86f)
                    lineTo(size.width * 0.52f, mid)
                    lineTo(size.width, mid)
                }
                drawPath(path, color, style = stroke)
            }
            "disk" -> {
                val pad = size.minDimension * 0.16f
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
                    size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.28f, size.height * 0.62f), androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.62f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.045f, center = androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.38f), style = Fill)
            }
            "container" -> {
                val left = size.width * 0.2f
                val top = size.height * 0.24f
                val right = size.width * 0.8f
                val bottom = size.height * 0.76f
                drawLine(color, androidx.compose.ui.geometry.Offset(left, top), androidx.compose.ui.geometry.Offset(right, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(right, top), androidx.compose.ui.geometry.Offset(right, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(right, bottom), androidx.compose.ui.geometry.Offset(left, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(left, bottom), androidx.compose.ui.geometry.Offset(left, top), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.36f, top), androidx.compose.ui.geometry.Offset(size.width * 0.36f, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, top), androidx.compose.ui.geometry.Offset(size.width * 0.64f, bottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "service" -> {
                drawCircle(color, radius = size.minDimension * 0.2f, center = center, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.14f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.31f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.69f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.14f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.31f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.69f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.86f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "sensor" -> {
                drawCircle(color, radius = size.minDimension * 0.12f, center = center, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.18f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.64f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.82f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.36f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.64f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.82f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.40f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.30f), androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.40f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.30f, size.height * 0.70f), androidx.compose.ui.geometry.Offset(size.width * 0.40f, size.height * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.70f, size.height * 0.70f), androidx.compose.ui.geometry.Offset(size.width * 0.60f, size.height * 0.60f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "battery" -> {
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.16f, size.height * 0.28f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.height * 0.44f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.42f), androidx.compose.ui.geometry.Offset(size.width * 0.80f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            "gpu" -> {
                val pad = size.minDimension * 0.24f
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
                    size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.36f), androidx.compose.ui.geometry.Offset(pad, size.height * 0.36f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.18f, size.height * 0.64f), androidx.compose.ui.geometry.Offset(pad, size.height * 0.64f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width - pad, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.86f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.065f, center = center, style = stroke)
            }
            "cluster" -> {
                val a = androidx.compose.ui.geometry.Offset(center.x, size.height * 0.22f)
                val b = androidx.compose.ui.geometry.Offset(size.width * 0.26f, size.height * 0.72f)
                val c = androidx.compose.ui.geometry.Offset(size.width * 0.74f, size.height * 0.72f)
                drawLine(color, a, b, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, b, c, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, c, a, strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.075f, center = a, style = Fill)
                drawCircle(color, radius = size.minDimension * 0.075f, center = b, style = Fill)
                drawCircle(color, radius = size.minDimension * 0.075f, center = c, style = Fill)
            }
            else -> {
                val pad = size.minDimension * 0.28f
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
                    size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = stroke
                )
                repeat(4) { i ->
                    val pos = size.minDimension * (0.2f + i * 0.2f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(pos, size.height * 0.1f), androidx.compose.ui.geometry.Offset(pos, pad * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(pos, size.height * 0.9f), androidx.compose.ui.geometry.Offset(pos, size.height - pad * 0.84f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.1f, pos), androidx.compose.ui.geometry.Offset(pad * 0.84f, pos), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.9f, pos), androidx.compose.ui.geometry.Offset(size.width - pad * 0.84f, pos), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
        }
    }
}

@Composable
private fun CpuLoadChart(snapshot: MetricSnapshot, metricHistory: List<MetricSnapshot>, loadColors: CpuLoadLegendColors) {
    val series = cpuLoadSeries(snapshot, metricHistory)
    val line1 = series.load1
    val line5 = series.load5
    val line15 = series.load15
    val maxValue = niceLoadMax((line1 + line5 + line15).maxOrNull() ?: 0.6f, snapshot.cpu.cores)
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val left = 0f
        val right = size.width - 38.dp.toPx()
        val top = 8.dp.toPx()
        val bottom = size.height - 30.dp.toPx()
        val chartHeight = bottom - top
        val grid = DeckColors.Divider
        repeat(4) { i ->
            val y = top + chartHeight * i / 3f
            drawLine(grid, androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y), strokeWidth = 1.dp.toPx())
        }
        repeat(4) { i ->
            val x = left + right * (i + 1) / 4f
            drawLine(grid.copy(alpha = 0.75f), androidx.compose.ui.geometry.Offset(x, top), androidx.compose.ui.geometry.Offset(x, bottom), strokeWidth = 1.dp.toPx())
        }
        drawLoadArea(line15, maxValue, left, right, top, bottom, loadColors.fifteenMinute)
        drawLoadArea(line5, maxValue, left, right, top, bottom, loadColors.fiveMinute)
        drawLoadTrace(line1, maxValue, left, right, top, bottom, loadColors.oneMinute, 1.05f)
        drawLoadTrace(line5, maxValue, left, right, top, bottom, loadColors.fiveMinute, 0.95f)
        drawLoadTrace(line15, maxValue, left, right, top, bottom, loadColors.fifteenMinute, 0.95f)
        listOf(maxValue, maxValue * 0.66f, maxValue * 0.33f, 0f).forEachIndexed { index, value ->
            drawContext.canvas.nativeCanvas.drawText(
                value.loadAxisLabel(),
                right + 12.dp.toPx(),
                top + chartHeight * index / 3f + 7.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(125, 126, 132)
                    textSize = 14.sp.toPx()
                    isAntiAlias = true
                }
            )
        }
        series.axisLabels.forEachIndexed { index, label ->
            drawContext.canvas.nativeCanvas.drawText(
                label,
                left + right * (index + 0.65f) / 4f,
                size.height - 2.dp.toPx(),
                android.graphics.Paint().apply {
                    color = android.graphics.Color.rgb(125, 126, 132)
                    textSize = 13.sp.toPx()
                    isAntiAlias = true
                }
            )
        }
    }
}

internal data class CpuLoadSeries(
    val load1: List<Float>,
    val load5: List<Float>,
    val load15: List<Float>,
    val axisLabels: List<String>
)

internal fun cpuLoadSeries(snapshot: MetricSnapshot, metricHistory: List<MetricSnapshot>): CpuLoadSeries {
    val samples = (metricHistory + snapshot)
        .distinctBy { it.collectedAtEpochMillis }
        .sortedBy { it.collectedAtEpochMillis }
        .takeLast(64)
    val now = samples.lastOrNull()?.collectedAtEpochMillis ?: snapshot.collectedAtEpochMillis
    val oldest = samples.firstOrNull()?.collectedAtEpochMillis ?: now
    val spanMinutes = ((now - oldest).coerceAtLeast(0L) / 60_000f).coerceAtLeast(0f)
    return CpuLoadSeries(
        load1 = samples.map { it.cpu.load1.coerceAtLeast(0f) },
        load5 = samples.map { it.cpu.load5.coerceAtLeast(0f) },
        load15 = samples.map { it.cpu.load15.coerceAtLeast(0f) },
        axisLabels = loadAxisTimeLabels(spanMinutes)
    )
}

internal fun niceLoadMax(maxSeen: Float, cores: Int): Float {
    val floor = max(0.6f, cores.coerceAtLeast(1).toFloat())
    if (maxSeen <= floor) return floor
    return (ceil((maxSeen * 1.12f) * 2f) / 2f).coerceAtLeast(floor)
}

private fun loadAxisTimeLabels(spanMinutes: Float): List<String> {
    if (spanMinutes < 1f) return listOf("-45s", "-30s", "-15s", "now")
    val span = ceil(spanMinutes).toInt().coerceAtLeast(1)
    return listOf(
        "-${span}m",
        "-${ceil(span * 2f / 3f).toInt()}m",
        "-${ceil(span / 3f).toInt()}m",
        "now"
    )
}

private data class CpuLoadLegendColors(
    val oneMinute: Color,
    val fiveMinute: Color,
    val fifteenMinute: Color
)

private fun cpuLoadLegendColors(metricColors: ServerMetricColors): CpuLoadLegendColors {
    val usage = cpuUsageColorsFor(metricColors)
    return CpuLoadLegendColors(
        oneMinute = usage.user,
        fiveMinute = usage.system,
        fifteenMinute = usage.ioWait
    )
}

private fun Float.loadAxisLabel(): String {
    return if (this == 0f) "0" else "%.1f".format(this)
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(width = 34.dp, height = 4.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Text(label, color = DeckColors.SecondaryText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ResourceGrid(snapshot: MetricSnapshot, metricColors: ServerMetricColors) {
    val showMemory = snapshot.memory.totalMb > 0
    val showDisk = snapshot.hasServerDetailDiskCapacity()
    if (!showMemory && !showDisk) return
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        if (showMemory) {
            val freeMb = (snapshot.memory.totalMb - snapshot.memory.usedMb).coerceAtLeast(0)
            ResourceRingCard(
                title = "Memory",
                percent = snapshot.memory.usagePercent,
                value = "${MetricFormatters.megaBytesLabel(snapshot.memory.usedMb.toFloat())} / ${MetricFormatters.megaBytesLabel(snapshot.memory.totalMb.toFloat())}",
                detailRows = listOf(
                    "Free" to MetricFormatters.megaBytesLabel(freeMb.toFloat()),
                    "Swap" to if (snapshot.memory.swapTotalMb > 0) {
                        "${MetricFormatters.megaBytesLabel(snapshot.memory.swapUsedMb.toFloat())} / ${MetricFormatters.megaBytesLabel(snapshot.memory.swapTotalMb.toFloat())}"
                    } else "--",
                    "Proc" to if (snapshot.processes.total > 0) "${snapshot.processes.running}/${snapshot.processes.total}" else "--"
                ),
                color = metricColors.memory,
                trackColor = metricColors.memory.copy(alpha = 0.14f),
                modifier = Modifier.weight(1f)
            )
        }
        if (showDisk) {
            ResourceRingCard(
                title = "Disk",
                percent = snapshot.disk.usagePercent,
                value = "${MetricFormatters.gigaBytesLabel(snapshot.disk.usedGb)} / ${MetricFormatters.gigaBytesLabel(snapshot.disk.totalGb)}",
                detailRows = listOf(
                    "Free" to MetricFormatters.gigaBytesLabel((snapshot.disk.totalGb - snapshot.disk.usedGb).coerceAtLeast(0f)),
                    "R / W" to "${snapshot.disk.readPerSecond} / ${snapshot.disk.writePerSecond}"
                ),
                color = metricColors.disk,
                trackColor = metricColors.disk.copy(alpha = 0.14f),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

internal fun MetricSnapshot.hasServerDetailDiskCapacity(): Boolean = disk.totalGb > 0f

internal fun MetricSnapshot.hasServerDetailNetworkSummary(): Boolean {
    val network = network
    val interfaces = network.interfaces.takeIf { it.isNotEmpty() } ?: listOf(network.primaryInterface)
    fun String.usefulNetworkLabel(): Boolean = isNotBlank() && this != "--" && this != "0 B" && this != "0.00 B" && this != "0.00 B/s"
    return interfaces.any { metric ->
        metric.address.usefulNetworkLabel() ||
            metric.uploadRate.usefulNetworkLabel() ||
            metric.downloadRate.usefulNetworkLabel() ||
            metric.uploadTotal.usefulNetworkLabel() ||
            metric.downloadTotal.usefulNetworkLabel()
    } || network.history.uploadLabel.usefulNetworkLabel() ||
        network.history.downloadLabel.usefulNetworkLabel() ||
        network.history.uploadBars.any { it > 0f } ||
        network.history.downloadBars.any { it > 0f }
}

@Composable
private fun NetworkSummaryCard(snapshot: MetricSnapshot, metricColors: ServerMetricColors, onInterfaces: () -> Unit) {
    val primary = snapshot.network.primaryInterface
    val interfaces = snapshot.network.interfaces.takeIf { it.isNotEmpty() } ?: listOf(primary)
    if (!snapshot.hasServerDetailNetworkSummary()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(horizontal = 17.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricTitle(icon = "pulse", title = "Network", color = metricColors.network, compact = true)
            Spacer(Modifier.weight(1f))
            Text(
                "${interfaces.size} iface",
                color = DeckColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(10.dp))
        InterfaceMetricCard(
            primary,
            modifier = Modifier.fillMaxWidth(),
            uploadColor = metricColors.disk,
            downloadColor = metricColors.network,
            onClick = onInterfaces
        )
    }
}

@Composable
private fun SystemSummaryCard(snapshot: MetricSnapshot, metricColors: ServerMetricColors) {
    val rows = systemSummaryRows(snapshot)
    if (rows.isEmpty()) return
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        MetricTitle(icon = "chip", title = "System", color = metricColors.latency, compact = true)
        Spacer(Modifier.height(12.dp))
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            SystemSummaryRow(row, systemSummaryColor(row, metricColors))
        }
    }
}

@Composable
private fun SystemSummaryRow(row: SystemSummaryItem, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Canvas(Modifier.size(9.dp)) { drawCircle(color) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold)
            Text(row.detail, color = DeckColors.PrimaryText, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(row.value, color = color, fontSize = 17.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

private fun systemSummaryColor(row: SystemSummaryItem, metricColors: ServerMetricColors): Color {
    return when (row.label) {
        "Processes" -> metricColors.cpu
        "Services" -> if (row.value.startsWith("0/")) DeckColors.Green else DeckColors.Red
        "Docker" -> metricColors.network
        "GPU" -> metricColors.memory
        "Proxmox" -> metricColors.network
        else -> metricColors.memory
    }
}

internal data class SystemSummaryItem(
    val label: String,
    val value: String,
    val detail: String
)

internal fun systemSummaryRows(snapshot: MetricSnapshot): List<SystemSummaryItem> {
    return buildList {
        if (snapshot.processes.total > 0) {
            add(
                SystemSummaryItem(
                    label = "Processes",
                    value = "${snapshot.processes.running}/${snapshot.processes.total}",
                    detail = "Top: ${snapshot.processes.topProcess.ifBlank { "--" }}"
                )
            )
        }
        if (snapshot.services.total > 0 || snapshot.services.failed > 0) {
            add(
                SystemSummaryItem(
                    label = "Services",
                    value = "${snapshot.services.failed}/${snapshot.services.total}",
                    detail = if (snapshot.services.failed == 0) "No failed services" else "Failed services need attention"
                )
            )
        }
        if (snapshot.docker.containers > 0 || snapshot.docker.running > 0) {
            add(
                SystemSummaryItem(
                    label = "Docker",
                    value = "${snapshot.docker.running}/${snapshot.docker.containers}",
                    detail = "Containers running"
                )
            )
        }
    } + pveSummaryRow(snapshot) + gpuSummaryRow(snapshot)
}

private fun pveSummaryRow(snapshot: MetricSnapshot): List<SystemSummaryItem> {
    if (snapshot.pveResources.isEmpty()) return emptyList()
    val guests = snapshot.pveResources.filter { it.type.equals("qemu", ignoreCase = true) || it.type.equals("lxc", ignoreCase = true) }
    val runningGuests = guests.count { it.status.equals("running", ignoreCase = true) }
    val nodes = snapshot.pveResources.count { it.type.equals("node", ignoreCase = true) }
    val storage = snapshot.pveResources.count { it.type.equals("storage", ignoreCase = true) }
    return listOf(
        SystemSummaryItem(
            label = "Proxmox",
            value = "$runningGuests/${guests.size}",
            detail = "$nodes nodes · $storage storage"
        )
    )
}

private fun gpuSummaryRow(snapshot: MetricSnapshot): List<SystemSummaryItem> {
    if (snapshot.gpus.isEmpty()) return emptyList()
    val active = snapshot.gpus.count { (it.utilizationPercent ?: 0) > 0 }
    val hottest = snapshot.gpus.mapNotNull { it.temperatureCelsius }.maxOrNull()
    return listOf(
        SystemSummaryItem(
            label = "GPU",
            value = "$active/${snapshot.gpus.size}",
            detail = hottest?.let { "Peak ${it}C" } ?: "Detected accelerators"
        )
    )
}

@Composable
private fun UptimeHistoryCard(snapshot: MetricSnapshot, metricHistory: List<MetricSnapshot>) {
    val summary = uptimeHistorySummary(snapshot, metricHistory)
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 26.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MetricTitle(icon = "pulse", title = "Uptime", color = DeckColors.Green, compact = true)
            Spacer(Modifier.weight(1f))
            Text(
                "${summary.onlineSamples}/${summary.totalSamples} online samples",
                color = DeckColors.SecondaryText,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            UptimeHistoryStat("Current", summary.currentUptime, DeckColors.Green, Modifier.weight(1f))
            UptimeHistoryStat("Available", "${summary.availabilityPercent}%", DeckColors.Cyan, Modifier.weight(1f))
            UptimeHistoryStat("History", summary.historyWindow, DeckColors.Orange, Modifier.weight(1f))
        }
    }
}

@Composable
private fun UptimeHistoryStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.SurfaceMuted)
            .padding(horizontal = 9.dp, vertical = 8.dp)
    ) {
        Canvas(Modifier.size(7.dp)) { drawCircle(color) }
        Spacer(Modifier.height(5.dp))
        Text(value, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = DeckColors.SecondaryText, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal data class UptimeHistorySummary(
    val currentUptime: String,
    val availabilityPercent: Int,
    val onlineSamples: Int,
    val totalSamples: Int,
    val historyWindow: String
)

internal fun uptimeHistorySummary(snapshot: MetricSnapshot, metricHistory: List<MetricSnapshot>): UptimeHistorySummary {
    val samples = (metricHistory + snapshot)
        .distinctBy { it.collectedAtEpochMillis }
        .sortedBy { it.collectedAtEpochMillis }
    val total = samples.size.coerceAtLeast(1)
    val online = samples.count { it.status == ServerStatus.Online }
    val spanMillis = (samples.lastOrNull()?.collectedAtEpochMillis ?: 0L) - (samples.firstOrNull()?.collectedAtEpochMillis ?: 0L)
    return UptimeHistorySummary(
        currentUptime = snapshot.uptime.takeIf { it.isNotBlank() && it != "--" } ?: snapshot.status.name,
        availabilityPercent = ((online * 100f) / total).toInt().coerceIn(0, 100),
        onlineSamples = online,
        totalSamples = total,
        historyWindow = historyWindowLabel(spanMillis)
    )
}

private fun historyWindowLabel(millis: Long): String {
    if (millis <= 0L) return "latest"
    val minutes = millis / 60_000L
    if (minutes < 60L) return "${minutes.coerceAtLeast(1L)}m"
    val hours = millis / 3_600_000L
    if (hours < 48L) return "${hours.coerceAtLeast(1L)}h"
    return "${(millis / 86_400_000L).coerceAtLeast(1L)}d"
}

@Composable
private fun ResourceRingCard(
    title: String,
    percent: Int,
    value: String,
    secondaryValue: String? = null,
    tertiaryValue: String? = null,
    detailRows: List<Pair<String, String>> = emptyList(),
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    DeckCard(modifier.heightIn(min = 282.dp), radius = 26.dp, padding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) {
        Text(
            title,
            color = DeckColors.SecondaryText,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp),
            contentAlignment = Alignment.Center
        ) {
            MetricRing(percent, color, Modifier.size(130.dp), trackColor = trackColor, valueFontSizeSp = 24)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 86.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            Text(
                value,
                color = DeckColors.PrimaryText,
                fontSize = 22.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            secondaryValue?.let {
                Spacer(Modifier.height(5.dp))
                Text(
                    it,
                    color = DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            tertiaryValue?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    color = DeckColors.SecondaryText,
                    fontSize = 14.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            detailRows.forEach { (label, rowValue) ->
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(rowValue, color = DeckColors.PrimaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun Float.twoDecimals(): String = "%.2f".format(this)

@Composable
private fun DiskRate(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Canvas(Modifier.size(8.dp)) { drawCircle(color) }
        Text("$label $value", color = DeckColors.SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
