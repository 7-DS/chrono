package com.chrono.ssh.ui.screens

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.ServerCardDiskMode
import com.chrono.ssh.core.model.ServerCardNetworkMode
import com.chrono.ssh.core.model.ServerMetricColorPreset
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.service.MetricFormatters
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.HeadingFontTarget
import com.chrono.ssh.ui.design.LocalHeadingFontFamilies
import com.chrono.ssh.ui.design.ChronoOsLogo
import com.chrono.ssh.ui.design.MetricRing
import com.chrono.ssh.ui.design.ServerMetricColors
import com.chrono.ssh.ui.design.ServerMetricColorOverrides
import com.chrono.ssh.ui.design.SoftPill
import com.chrono.ssh.ui.design.StatusDot
import com.chrono.ssh.ui.design.metricColorsFor

private object HomeCardLayout {
    val cardHorizontalPadding = 18.dp
    val cardVerticalPadding = 13.dp
    val metricHeight = 82.dp
    val metricTitleHeight = 18.dp
    val ringSize = 64.dp
    val metricTitleSize = 13.sp
    val metricValueSize = 20.sp
    val metricUnitSize = 11.sp
    val metricIconSize = 21.dp
    val metricIconSlotWidth = 26.dp
    val metricAmountWidth = 52.dp
    val metricUnitWidth = 24.dp
    val metricBodyHeight = 64.dp
    val metricRowHeight = 24.dp
    val metricFixedGap = 8.dp
    val ringColumnShare = 0.22f
    val textColumnShare = 0.34f
}

@Composable
fun HomeScreen(
    servers: List<ServerProfile>,
    snapshots: Map<String, MetricSnapshot>,
    knownHosts: List<KnownHost>,
    networkMode: ServerCardNetworkMode = ServerCardNetworkMode.Totals,
    diskMode: ServerCardDiskMode = ServerCardDiskMode.Usage,
    metricColorPreset: ServerMetricColorPreset = ServerMetricColorPreset.Theme,
    metricColorOverrides: ServerMetricColorOverrides = ServerMetricColorOverrides(),
    onAddServer: () -> Unit,
    onTrustHost: (ServerProfile) -> Unit,
    onUptimeClick: () -> Unit = {},
    onServerClick: (ServerProfile) -> Unit,
    onTerminalClick: (ServerProfile) -> Unit,
    onProbeClick: (ServerProfile) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var dismissedTrustPromptFor by remember { mutableStateOf<Set<String>>(emptySet()) }
    var trustPromptServer by remember { mutableStateOf<ServerProfile?>(null) }
    val filters = homeFleetFilters(servers)
    val serverRows = homeServerRows(servers, snapshots, selectedFilter)
    val onlineCount = servers.count { snapshots[it.id]?.status == ServerStatus.Online }
    val offlineCount = servers.count { snapshots[it.id]?.status == ServerStatus.Offline }
    val untrustedServer = servers.firstOrNull { server ->
        knownHosts.none { it.host == server.host && it.port == server.port && it.trusted } &&
            server.id !in dismissedTrustPromptFor
    }

    LaunchedEffect(untrustedServer?.id) {
        if (untrustedServer != null && trustPromptServer == null) {
            trustPromptServer = untrustedServer
        }
    }
    LaunchedEffect(filters) {
        if (selectedFilter !in filters) selectedFilter = "All"
    }
    val listState = rememberLazyListState()
    var showCompactTopBar by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        var previousScroll = 0
        snapshotFlow { listState.firstVisibleItemIndex * 100_000 + listState.firstVisibleItemScrollOffset }
            .collect { scroll ->
                showCompactTopBar = compactAddHostVisibility(showCompactTopBar, previousScroll, scroll)
                previousScroll = scroll
            }
    }

    Box {
        LazyColumn(
            state = listState,
            modifier = Modifier.statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
        ) {
            item {
                HomeTitleRow(onUptimeClick = onUptimeClick, onAddServer = onAddServer)
                Spacer(Modifier.height(18.dp))
            }
            item {
                HomeFilterRow(filters, selectedFilter) { selectedFilter = it }
                Spacer(Modifier.height(12.dp))
            }
            item {
                FleetCounters(total = servers.size, online = onlineCount, offline = offlineCount)
                Spacer(Modifier.height(18.dp))
            }
            if (serverRows.isEmpty()) {
                item {
                    EmptyFleetState(
                        selectedFilter = selectedFilter,
                        hasServers = servers.isNotEmpty(),
                        onAddServer = onAddServer
                    )
                }
            } else {
                items(serverRows, key = { it.first.id }) { (server, snapshot) ->
                    ServerOverviewCard(
                        server = server,
                        snapshot = snapshot,
                        networkMode = networkMode,
                        diskMode = diskMode,
                        metricColors = metricColorsFor(metricColorPreset, metricColorOverrides),
                        onClick = { onServerClick(server) },
                        onTerminalClick = { onTerminalClick(server) },
                        onProbeClick = { onProbeClick(server) }
                    )
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
        AnimatedVisibility(
            visible = showCompactTopBar,
            enter = slideInVertically(tween(260, easing = FastOutSlowInEasing)) { -it / 3 } + fadeIn(tween(220, easing = FastOutSlowInEasing)),
            exit = slideOutVertically(tween(190, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(160, easing = FastOutSlowInEasing)),
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            HomeCompactTopBar(onAddServer = onAddServer)
        }
    }

    trustPromptServer?.let { server ->
        TrustHostDialog(
            server = server,
            onDismiss = {
                dismissedTrustPromptFor = dismissedTrustPromptFor + server.id
                trustPromptServer = null
            },
            onReview = {
                dismissedTrustPromptFor = dismissedTrustPromptFor + server.id
                trustPromptServer = null
                onTrustHost(server)
            }
        )
    }
}

@Composable
private fun HomeTitleRow(
    onUptimeClick: () -> Unit,
    onAddServer: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = "chronoSSH",
                color = DeckColors.PrimaryText,
                fontSize = 39.sp,
                lineHeight = 41.sp,
                fontWeight = FontWeight.Black,
                fontFamily = LocalHeadingFontFamilies.current.forTarget(HeadingFontTarget.Home),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(onClick = onUptimeClick)
            )
        }
        QuickAction("host-add", null, DeckColors.BrandAlt, onAddServer, modifier = Modifier.size(50.dp))
    }
}

@Composable
private fun HomeCompactTopBar(
    onAddServer: () -> Unit
) {
    Box(
        modifier = Modifier
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .background(DeckColors.Surface.copy(alpha = 0.96f))
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.64f), androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        QuickAction("host-add", null, DeckColors.BrandAlt, onAddServer, modifier = Modifier.size(40.dp))
    }
}

@Composable
private fun HomeFilterRow(
    filters: List<String>,
    selectedFilter: String,
    onFilterSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            .background(DeckColors.Surface)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filters.forEach { filter ->
            SoftPill(
                text = filter,
                selected = filter == selectedFilter,
                color = if (filter == "All") DeckColors.Cyan else DeckColors.SecondaryText,
                onClick = { onFilterSelected(filter) }
            )
        }
    }
}

@Composable
private fun FleetStatusStrip(
    fleetScore: Int,
    onlineCount: Int,
    serverCount: Int,
    activeForwards: Int
) {
    val statusColor = if (fleetScore >= 80) DeckColors.Green else DeckColors.Orange
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 24.dp, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.size(16.dp)) {
                drawCircle(statusColor, radius = size.minDimension / 3)
            }
            Column(Modifier.weight(1f)) {
                Text("Fleet health", color = DeckColors.PrimaryText, fontSize = 18.sp, fontWeight = FontWeight.Black)
                Text(
                    "$onlineCount/$serverCount live · $activeForwards tunnels · ${if (fleetScore >= 80) "low risk" else "needs review"}",
                    color = DeckColors.SecondaryText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text("$fleetScore%", color = statusColor, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun CompactCounter(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .background(DeckColors.Surface)
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(label, color = DeckColors.SecondaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Canvas(Modifier.size(9.dp)) {
                drawCircle(color, radius = size.minDimension / 3)
            }
            Text(value, color = DeckColors.PrimaryText, fontSize = 23.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
private fun FleetCounters(total: Int, online: Int, offline: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(21.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke, androidx.compose.foundation.shape.RoundedCornerShape(21.dp))
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CounterSegment("Total", total.toString(), DeckColors.Cyan, Modifier.weight(1f))
        CounterDivider()
        CounterSegment("Online", online.toString(), DeckColors.Green, Modifier.weight(1f))
        CounterDivider()
        CounterSegment("Offline", offline.toString(), DeckColors.Red, Modifier.weight(1f))
    }
}

@Composable
private fun CounterSegment(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Canvas(Modifier.size(7.dp)) {
            drawCircle(color, radius = size.minDimension / 3)
        }
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = DeckColors.SecondaryText,
            fontSize = 12.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(5.dp))
        Text(
            value,
            color = DeckColors.PrimaryText,
            fontSize = 16.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1
        )
    }
}

@Composable
private fun CounterDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(18.dp)
            .background(DeckColors.Divider)
    )
}

@Composable
private fun CounterDot(color: Color) {
    Canvas(Modifier.size(8.dp)) {
        drawCircle(color, radius = size.minDimension / 3)
    }
}

@Composable
private fun ServerOverviewCard(
    server: ServerProfile,
    snapshot: MetricSnapshot?,
    networkMode: ServerCardNetworkMode,
    diskMode: ServerCardDiskMode,
    metricColors: ServerMetricColors,
    onClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onProbeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), androidx.compose.foundation.shape.RoundedCornerShape(32.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = HomeCardLayout.cardHorizontalPadding, vertical = HomeCardLayout.cardVerticalPadding)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServerCardGlyph(server)
            Spacer(Modifier.width(13.dp))
            Text(
                server.name,
                color = DeckColors.PrimaryText,
                fontSize = 24.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            StatusDot(snapshot?.status ?: ServerStatus.Unknown, Modifier.size(12.dp))
            Spacer(Modifier.width(6.dp))
            snapshot?.latencyMs?.let { latency ->
                Box(
                    modifier = Modifier
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
                        .clickable(onClick = onProbeClick)
                        .padding(horizontal = 4.dp, vertical = 3.dp)
                ) {
                    Text(
                        "$latency ms",
                        color = if (snapshot.status == ServerStatus.Online) metricColors.latency else DeckColors.SecondaryText,
                        fontSize = 18.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            SshGlyphButton(onTerminalClick)
        }
        val hasCpu = snapshot?.hasCpuMetrics() == true
        val hasMemory = snapshot?.hasMemoryMetrics() == true
        val hasDisk = snapshot?.hasDiskMetrics() == true
        val hasNetwork = snapshot?.hasNetworkMetrics() == true
        val cpu = snapshot?.cpu
        val memory = snapshot?.memory
        val disk = snapshot?.disk
        val network = snapshot?.network?.primaryInterface
        val metricPlaceholderColor = DeckColors.SecondaryText.copy(alpha = 0.56f)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            TinyMeta(MetricSymbol.Cpu, if (hasCpu && cpu != null) "${cpu.cores} Cores" else "--", available = hasCpu && cpu != null)
            TinyMeta(MetricSymbol.Memory, if (hasMemory && memory != null) MetricFormatters.megaBytesLabel(memory.usedMb.toFloat()) else "--", available = hasMemory && memory != null)
            TinyMeta(MetricSymbol.Disk, if (hasDisk && disk != null) disk.homeUsageLabel() else "-- / --", available = hasDisk && disk != null)
            val uptimeText = snapshot?.uptime?.takeIf { it != "--" }?.compactUptime() ?: "--"
            TinyMeta(MetricSymbol.Uptime, uptimeText, available = uptimeText != "--")
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DeckColors.Divider)
        )
        Spacer(Modifier.height(9.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val usableWidth = maxWidth - HomeCardLayout.metricFixedGap * 3
            val ringWidth = usableWidth * HomeCardLayout.ringColumnShare
            val textWidth = usableWidth * HomeCardLayout.textColumnShare
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HomeCardLayout.metricFixedGap),
                modifier = Modifier.fillMaxWidth()
            ) {
                HomeRingMetric(
                    title = "CPU",
                    percent = if (hasCpu && cpu != null) cpu.usagePercent else 0,
                    color = if (hasCpu && cpu != null) metricColors.cpu else metricPlaceholderColor,
                    modifier = Modifier.width(ringWidth),
                    available = hasCpu && cpu != null
                )
                HomeRingMetric(
                    title = "RAM",
                    percent = if (hasMemory && memory != null) memory.usagePercent else 0,
                    color = if (hasMemory && memory != null) metricColors.memory else metricPlaceholderColor,
                    modifier = Modifier.width(ringWidth),
                    available = hasMemory && memory != null
                )
                if (hasDisk && disk != null && disk.homeShouldUseUsageRing(diskMode)) {
                    HomeRingMetric(
                        title = "Disk",
                        percent = disk.usagePercent,
                        color = metricColors.disk,
                        modifier = Modifier.width(ringWidth),
                        caption = disk.homeUsageLabel(),
                        available = true
                    )
                } else {
                    HomeTextMetric(
                        title = "Disk",
                        firstIcon = MetricSymbol.DiskRead,
                        firstValue = if (hasDisk && disk != null) disk.homeReadLabel(diskMode) else "--",
                        secondIcon = MetricSymbol.DiskWrite,
                        secondValue = if (hasDisk && disk != null) disk.homeWriteLabel(diskMode) else "--",
                        color = if (hasDisk && disk != null) metricColors.disk else metricPlaceholderColor,
                        modifier = Modifier.width(ringWidth),
                        available = hasDisk && disk != null
                    )
                }
                HomeTextMetric(
                    title = "Network",
                    firstIcon = MetricSymbol.NetworkUp,
                    firstValue = if (hasNetwork && network != null) network.homeUploadLabel(networkMode) else "--",
                    secondIcon = MetricSymbol.NetworkDown,
                    secondValue = if (hasNetwork && network != null) network.homeDownloadLabel(networkMode) else "--",
                    color = if (hasNetwork && network != null) metricColors.network else metricPlaceholderColor,
                    modifier = Modifier.width(textWidth),
                    available = hasNetwork && network != null
                )
            }
        }
    }
}

@Composable
private fun ServerCardGlyph(server: ServerProfile) {
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
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (customLogo != null) {
            Image(
                bitmap = customLogo,
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            ChronoOsLogo(server.osName, Modifier.size(34.dp), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun SshGlyphButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "sshGlyphButtonPress"
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .size(32.dp)
            .clip(CircleShape)
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(16.dp)) {
            val stroke = 1.6.dp.toPx()
            drawRoundRect(
                color = DeckColors.Cyan,
                topLeft = androidx.compose.ui.geometry.Offset(1.5.dp.toPx(), 2.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(size.width - 3.dp.toPx(), size.height - 4.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = stroke)
            )
            drawLine(
                color = DeckColors.Cyan,
                start = androidx.compose.ui.geometry.Offset(4.8.dp.toPx(), 6.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(7.5.dp.toPx(), 8.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = DeckColors.Cyan,
                start = androidx.compose.ui.geometry.Offset(7.5.dp.toPx(), 8.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(4.8.dp.toPx(), 10.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = DeckColors.Cyan,
                start = androidx.compose.ui.geometry.Offset(9.5.dp.toPx(), 10.4.dp.toPx()),
                end = androidx.compose.ui.geometry.Offset(12.5.dp.toPx(), 10.4.dp.toPx()),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun HomeRingMetric(
    title: String,
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier,
    caption: String? = null,
    available: Boolean = true
) {
    Column(
        modifier = modifier.height(HomeCardLayout.metricHeight),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.height(HomeCardLayout.metricTitleHeight), contentAlignment = Alignment.Center) {
            Text(title, color = DeckColors.SecondaryText, fontSize = HomeCardLayout.metricTitleSize, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Box(Modifier.size(HomeCardLayout.ringSize), contentAlignment = Alignment.Center) {
            MetricRing(
                percent = percent.coerceIn(0, 100),
                color = if (available) color else DeckColors.SecondaryText,
                modifier = Modifier.matchParentSize(),
                trackColor = DeckColors.SurfaceMuted,
                valueFontSizeSp = 17,
                animate = false
            )
            if (!available) {
                Text("--", color = DeckColors.SecondaryText, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.Black)
            }
        }
        if (caption != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                caption,
                color = DeckColors.SecondaryText,
                fontSize = 10.sp,
                lineHeight = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun HomeTextMetric(
    title: String,
    firstIcon: MetricSymbol,
    firstValue: String,
    secondIcon: MetricSymbol,
    secondValue: String,
    color: Color,
    modifier: Modifier = Modifier,
    available: Boolean = true
) {
    val valueColor = if (available) DeckColors.PrimaryText else DeckColors.SecondaryText.copy(alpha = 0.72f)
    val unitColor = if (available) DeckColors.SecondaryText else DeckColors.SecondaryText.copy(alpha = 0.56f)
    Column(
        modifier = modifier.height(HomeCardLayout.metricHeight),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.height(HomeCardLayout.metricTitleHeight), contentAlignment = Alignment.Center) {
            Text(
                title,
                color = DeckColors.SecondaryText,
                fontSize = HomeCardLayout.metricTitleSize,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Column(
            modifier = Modifier
                .height(HomeCardLayout.metricBodyHeight)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HomeValueLine(firstIcon, firstValue, color, valueColor, unitColor)
            Spacer(Modifier.height(4.dp))
            HomeValueLine(secondIcon, secondValue, color, valueColor, unitColor)
        }
    }
}

@Composable
private fun HomeValueLine(symbol: MetricSymbol, value: String, color: Color, valueColor: Color, unitColor: Color) {
    val (amount, unit) = value.metricAmountAndUnit()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(HomeCardLayout.metricRowHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Row(
            modifier = Modifier.width(
                HomeCardLayout.metricIconSlotWidth +
                    HomeCardLayout.metricAmountWidth +
                    4.dp +
                    HomeCardLayout.metricUnitWidth
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(Modifier.width(HomeCardLayout.metricIconSlotWidth), contentAlignment = Alignment.Center) {
                MetricIcon(symbol, color, Modifier.size(HomeCardLayout.metricIconSize))
            }
            Text(
                amount,
                color = valueColor,
                fontSize = HomeCardLayout.metricValueSize,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.width(HomeCardLayout.metricAmountWidth)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                color = unitColor,
                fontSize = HomeCardLayout.metricUnitSize,
                lineHeight = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.width(HomeCardLayout.metricUnitWidth)
            )
        }
    }
}

@Composable
private fun ReferenceMetricLine(
    symbol: MetricSymbol,
    value: String,
    suffix: String,
    color: Color,
    alignEnd: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (!alignEnd) {
            MetricCircleIcon(symbol, color)
            Spacer(Modifier.width(8.dp))
        }
        Text(value, color = DeckColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.width(3.dp))
        Text(suffix, color = DeckColors.SecondaryText, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        if (alignEnd) {
            Spacer(Modifier.width(8.dp))
            MetricCircleIcon(symbol, color)
        }
    }
}

@Composable
private fun MetricCircleIcon(symbol: MetricSymbol, color: Color) {
    Box(
        modifier = Modifier
            .size(25.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center
    ) {
        MetricIcon(symbol, color, Modifier.size(15.dp))
    }
}

@Composable
private fun NetworkShareRing(uploadShare: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val strokeWidth = 13.dp.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f)
        drawArc(
            color = DeckColors.Orange.copy(alpha = 0.74f),
            startAngle = -88f,
            sweepAngle = 360f * uploadShare.coerceIn(0.06f, 0.94f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = DeckColors.Cyan.copy(alpha = 0.74f),
            startAngle = -88f + 360f * uploadShare.coerceIn(0.06f, 0.94f) + 8f,
            sweepAngle = 360f * (1f - uploadShare.coerceIn(0.06f, 0.94f)) - 16f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun MetricBar(
    symbol: MetricSymbol,
    percent: Int,
    color: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricIcon(symbol, color, Modifier.size(14.dp))
            Text("${percent.coerceIn(0, 100)}%", color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(DeckColors.SurfaceMuted)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                    .height(8.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun CpuPillGrid(percent: Int) {
    val filled = percent.coerceIn(0, 100)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricIcon(MetricSymbol.Cpu, DeckColors.Green, Modifier.size(14.dp))
            Text("$filled%", color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(15.dp)
        ) {
            val columns = 50
            val rows = 2
            val gap = 2.dp.toPx()
            val rowGap = 3.dp.toPx()
            val cellWidth = ((size.width - gap * (columns - 1)) / columns).coerceAtLeast(1f)
            val cellHeight = ((size.height - rowGap * (rows - 1)) / rows).coerceAtLeast(1f)
            repeat(rows) { row ->
                repeat(columns) { column ->
                    val cell = row * columns + column + 1
                    drawRoundRect(
                        color = if (cell <= filled) DeckColors.Green else DeckColors.SurfaceMuted,
                        topLeft = androidx.compose.ui.geometry.Offset(column * (cellWidth + gap), row * (cellHeight + rowGap)),
                        size = androidx.compose.ui.geometry.Size(cellWidth, cellHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cellHeight / 2f, cellHeight / 2f),
                        style = Fill
                    )
                }
            }
        }
    }
}

@Composable
private fun TinyMeta(symbol: MetricSymbol, text: String, available: Boolean = true) {
    val color = if (available) DeckColors.SecondaryText else DeckColors.SecondaryText.copy(alpha = 0.56f)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricIcon(symbol, color, Modifier.size(18.dp))
        Text(text, color = color, fontSize = 15.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private enum class MetricSymbol {
    Cpu,
    Memory,
    Disk,
    Uptime,
    NetworkUp,
    NetworkDown,
    DiskRead,
    DiskWrite
}

private fun Int.twoDecimals(): String = "%.2f".format(this.toFloat())

private fun Float.twoDecimals(): String = "%.2f".format(this)

private fun String.compactUptime(): String {
    return replace(" days", "d")
        .replace(" day", "d")
        .replace(" hours", "h")
        .replace(" hour", "h")
        .replace(" minutes", "m")
        .replace(" minute", "m")
        .take(8)
}

private fun String.shortMetricLabel(): String {
    return replace("/s", "")
        .replace("B", "b")
        .replace(" ", "")
        .take(7)
}

private fun String.metricAmountAndUnit(): Pair<String, String> {
    return MetricFormatters.compactAmountAndUnitLabel(this)
}

internal fun homeFleetFilters(servers: List<ServerProfile>): List<String> {
    val favorites = if (servers.any { it.favorite }) listOf("Favorites") else emptyList()
    val groups = servers.map { it.group.trim() }.filter { it.isNotBlank() && it != "Ungrouped" }
    val tags = servers.flatMap { it.tags }.map { it.trim() }.filter { it.isNotBlank() && it != "All" }
    return (listOf("All") + favorites + groups + tags).distinct().take(12)
}

internal fun filterServersByHomeFilter(
    servers: List<ServerProfile>,
    selectedFilter: String
): List<ServerProfile> {
    if (selectedFilter == "All") return servers
    if (selectedFilter == "Favorites") return servers.filter { it.favorite }
    return servers.filter { server ->
        server.group == selectedFilter || selectedFilter in server.tags
    }
}

internal fun homeServerRows(
    servers: List<ServerProfile>,
    snapshots: Map<String, MetricSnapshot>,
    selectedFilter: String
): List<Pair<ServerProfile, MetricSnapshot?>> {
    return filterServersByHomeFilter(servers, selectedFilter).map { server ->
        server to snapshots[server.id]
    }
}

internal fun compactAddHostVisibility(currentlyVisible: Boolean, previousScroll: Int, currentScroll: Int): Boolean {
    val delta = currentScroll - previousScroll
    return when {
        delta > 8 -> true
        delta < -8 -> false
        else -> currentlyVisible
    }
}

internal fun com.chrono.ssh.core.model.NetworkInterfaceMetric.homeUploadLabel(mode: ServerCardNetworkMode): String {
    return when (mode) {
        ServerCardNetworkMode.Totals -> uploadTotal
        ServerCardNetworkMode.Rates -> uploadRate
    }.takeIf { it.isUsefulMetricLabel() }
        ?: uploadTotal.takeIf { it.isUsefulMetricLabel() }
        ?: uploadRate.takeIf { it.isUsefulMetricLabel() }
        ?: "--"
}

internal fun com.chrono.ssh.core.model.NetworkInterfaceMetric.homeDownloadLabel(mode: ServerCardNetworkMode): String {
    return when (mode) {
        ServerCardNetworkMode.Totals -> downloadTotal
        ServerCardNetworkMode.Rates -> downloadRate
    }.takeIf { it.isUsefulMetricLabel() }
        ?: downloadTotal.takeIf { it.isUsefulMetricLabel() }
        ?: downloadRate.takeIf { it.isUsefulMetricLabel() }
        ?: "--"
}

internal fun com.chrono.ssh.core.model.DiskMetrics.homeReadLabel(mode: ServerCardDiskMode): String {
    val selected = when (mode) {
        ServerCardDiskMode.Usage -> MetricFormatters.gigaBytesLabel(usedGb)
        ServerCardDiskMode.Rates -> readPerSecond
        ServerCardDiskMode.Totals -> readTotal
    }
    if (mode == ServerCardDiskMode.Rates) return selected.takeIf { it.isUsefulMetricLabel() } ?: "--"
    return selected.takeIf { it.isUsefulMetricLabel() }
        ?: readTotal.takeIf { it.isUsefulMetricLabel() }
        ?: readPerSecond.takeIf { it.isUsefulMetricLabel() }
        ?: usedGb.takeIf { totalGb > 0f }?.let { MetricFormatters.gigaBytesLabel(it) }
        ?: "--"
}

internal fun com.chrono.ssh.core.model.DiskMetrics.homeWriteLabel(mode: ServerCardDiskMode): String {
    val selected = when (mode) {
        ServerCardDiskMode.Usage -> MetricFormatters.gigaBytesLabel(totalGb)
        ServerCardDiskMode.Rates -> writePerSecond
        ServerCardDiskMode.Totals -> writeTotal
    }
    if (mode == ServerCardDiskMode.Rates) return selected.takeIf { it.isUsefulMetricLabel() } ?: "--"
    return selected.takeIf { it.isUsefulMetricLabel() }
        ?: writeTotal.takeIf { it.isUsefulMetricLabel() }
        ?: writePerSecond.takeIf { it.isUsefulMetricLabel() }
        ?: totalGb.takeIf { it > 0f }?.let { MetricFormatters.gigaBytesLabel(it) }
        ?: "--"
}

internal fun com.chrono.ssh.core.model.DiskMetrics.homeUsageLabel(): String {
    if (totalGb <= 0f) return "-- / --"
    return "${MetricFormatters.gigaBytesLabel(usedGb)} / ${MetricFormatters.gigaBytesLabel(totalGb)}"
}

internal fun com.chrono.ssh.core.model.DiskMetrics.homeShouldUseUsageRing(mode: ServerCardDiskMode): Boolean {
    return mode == ServerCardDiskMode.Usage && totalGb > 0f
}

internal fun MetricSnapshot.hasCollectedMetrics(): Boolean {
    return hasCpuMetrics() ||
        hasMemoryMetrics() ||
        hasDiskMetrics() ||
        hasNetworkMetrics() ||
        processes.total > 0 ||
        processes.running > 0 ||
        (processes.topProcess.isNotBlank() && processes.topProcess != "--") ||
        processes.items.isNotEmpty() ||
        services.total > 0 ||
        services.failed > 0 ||
        services.items.isNotEmpty() ||
        services.failedItems.isNotEmpty() ||
        docker.containers > 0 ||
        docker.running > 0 ||
        docker.items.isNotEmpty() ||
        disk.filesystems.isNotEmpty() ||
        sensors.isNotEmpty() ||
        batteries.isNotEmpty() ||
        smartDisks.isNotEmpty() ||
        pveResources.isNotEmpty() ||
        gpus.isNotEmpty() ||
        packageUpdates.isNotEmpty()
}

internal fun MetricSnapshot.hasCpuMetrics(): Boolean {
    if (cpu.perCore.isNotEmpty() || cpu.recentLoad.any { it > 0f }) return true
    return cpu.model.isNotBlank() &&
        cpu.model != "Linux CPU" &&
        !cpu.model.contains("Unavailable", ignoreCase = true)
}

internal fun MetricSnapshot.hasMemoryMetrics(): Boolean = memory.totalMb > 0

internal fun MetricSnapshot.hasDiskMetrics(): Boolean {
    return disk.totalGb > 0f ||
        disk.readPerSecond.isUsefulMetricLabel() ||
        disk.writePerSecond.isUsefulMetricLabel() ||
        disk.readTotal.isUsefulMetricLabel() ||
        disk.writeTotal.isUsefulMetricLabel()
}

internal fun MetricSnapshot.hasNetworkMetrics(): Boolean {
    val primary = network.primaryInterface
    return primary.uploadRate.isUsefulMetricLabel() ||
        primary.downloadRate.isUsefulMetricLabel() ||
        primary.uploadTotal.isUsefulMetricLabel() ||
        primary.downloadTotal.isUsefulMetricLabel()
}

private fun String.isUsefulMetricLabel(): Boolean = isNotBlank() && this != "--" && this != "0 B/s" && this != "0.00 B/s" && this != "0 B" && this != "0.00 B"

@Composable
private fun MetricIcon(symbol: MetricSymbol, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 1.25.dp.toPx(), cap = StrokeCap.Round)
        when (symbol) {
            MetricSymbol.Cpu -> {
                val pad = size.minDimension * 0.28f
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(pad, pad),
                    size = androidx.compose.ui.geometry.Size(size.width - pad * 2, size.height - pad * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                repeat(4) { i ->
                    val pos = size.minDimension * (0.2f + i * 0.2f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(pos, size.height * 0.1f), androidx.compose.ui.geometry.Offset(pos, pad * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(pos, size.height * 0.9f), androidx.compose.ui.geometry.Offset(pos, size.height - pad * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.1f, pos), androidx.compose.ui.geometry.Offset(pad * 0.86f, pos), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.9f, pos), androidx.compose.ui.geometry.Offset(size.width - pad * 0.86f, pos), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            MetricSymbol.Memory -> {
                val y = size.height * 0.32f
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.08f, y),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.84f, size.height * 0.36f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = stroke
                )
                repeat(5) { i ->
                    val x = size.width * (0.18f + i * 0.16f)
                    drawLine(color, androidx.compose.ui.geometry.Offset(x, y - size.height * 0.14f), androidx.compose.ui.geometry.Offset(x, y), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(color, androidx.compose.ui.geometry.Offset(x, y + size.height * 0.36f), androidx.compose.ui.geometry.Offset(x, y + size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
            MetricSymbol.Disk -> {
                val top = size.height * 0.26f
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, top),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.48f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = stroke
                )
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.24f, size.height * 0.58f), androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.58f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.055f, center = androidx.compose.ui.geometry.Offset(size.width * 0.72f, size.height * 0.42f))
            }
            MetricSymbol.Uptime -> {
                val center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.56f)
                drawCircle(color, radius = size.minDimension * 0.34f, center = center, style = stroke)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.05f), androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.22f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, center, androidx.compose.ui.geometry.Offset(size.width * 0.5f, size.height * 0.34f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, center, androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.56f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            MetricSymbol.NetworkUp, MetricSymbol.NetworkDown -> {
                val upward = symbol == MetricSymbol.NetworkUp
                val shaftTop = if (upward) size.height * 0.18f else size.height * 0.34f
                val shaftBottom = if (upward) size.height * 0.66f else size.height * 0.82f
                val headY = if (upward) size.height * 0.18f else size.height * 0.82f
                val wingY = if (upward) size.height * 0.38f else size.height * 0.62f
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, shaftTop), androidx.compose.ui.geometry.Offset(size.width * 0.5f, shaftBottom), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, headY), androidx.compose.ui.geometry.Offset(size.width * 0.28f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, headY), androidx.compose.ui.geometry.Offset(size.width * 0.72f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(size.width * 0.23f, size.height * 0.86f), androidx.compose.ui.geometry.Offset(size.width * 0.77f, size.height * 0.86f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            }
            MetricSymbol.DiskRead, MetricSymbol.DiskWrite -> {
                val read = symbol == MetricSymbol.DiskRead
                drawRoundRect(
                    color = color,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.54f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.76f, size.height * 0.28f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                    style = stroke
                )
                val arrowStartY = if (read) size.height * 0.18f else size.height * 0.40f
                val arrowEndY = if (read) size.height * 0.42f else size.height * 0.16f
                val wingY = if (read) size.height * 0.30f else size.height * 0.28f
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, arrowStartY), androidx.compose.ui.geometry.Offset(size.width * 0.5f, arrowEndY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, arrowEndY), androidx.compose.ui.geometry.Offset(size.width * 0.32f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.5f, arrowEndY), androidx.compose.ui.geometry.Offset(size.width * 0.68f, wingY), strokeWidth = stroke.width, cap = StrokeCap.Round)
                drawCircle(color, radius = size.minDimension * 0.045f, center = androidx.compose.ui.geometry.Offset(size.width * 0.73f, size.height * 0.68f))
            }
        }
    }
}

@Composable
private fun QuickAction(symbol: String, text: String?, accent: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "quickActionPress"
    )
    val background by animateColorAsState(
        targetValue = if (pressed) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "quickActionBackground"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .background(background)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = if (text == null) 0.dp else 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (text == null) Arrangement.Center else Arrangement.spacedBy(6.dp)
    ) {
        if (symbol == "host-add") {
            HostAddQuickGlyph(accent, Modifier.size(if (text == null) 22.dp else 18.dp))
        } else {
            Text(symbol, color = accent, fontSize = if (text == null) 18.sp else 14.sp, fontWeight = FontWeight.Black)
        }
        text?.let {
            Text(it, color = DeckColors.PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun HostAddQuickGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.45.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(size.width * 0.29f, center.y), androidx.compose.ui.geometry.Offset(size.width * 0.71f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, androidx.compose.ui.geometry.Offset(center.x, size.height * 0.29f), androidx.compose.ui.geometry.Offset(center.x, size.height * 0.71f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun EmptyFleetState(
    selectedFilter: String,
    hasServers: Boolean,
    onAddServer: () -> Unit
) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 30.dp, padding = PaddingValues(20.dp)) {
        Text(
            if (hasServers) "No hosts in $selectedFilter" else "No hosts yet",
            color = DeckColors.PrimaryText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (hasServers) "Switch filters or tag a host for this workspace."
            else "Add a server to start building your chronoSSH deck.",
            color = DeckColors.SecondaryText,
            fontSize = 15.sp,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(14.dp))
        QuickAction("host-add", "Add Host", DeckColors.BrandAlt, onAddServer)
    }
}

@Composable
private fun TrustHostDialog(
    server: ServerProfile,
    onDismiss: () -> Unit,
    onReview: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trust host key?", color = DeckColors.PrimaryText, fontWeight = FontWeight.Black) },
        text = {
            Text(
                "${server.name} has not been trusted yet. Scan its SSH fingerprint and trust it before opening terminal, SFTP, or monitoring.",
                color = DeckColors.SecondaryText,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onReview) {
                Text("Scan & Trust", color = DeckColors.Cyan, fontWeight = FontWeight.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Later", color = DeckColors.SecondaryText)
            }
        },
        containerColor = DeckColors.Surface,
        titleContentColor = DeckColors.PrimaryText,
        textContentColor = DeckColors.SecondaryText
    )
}
