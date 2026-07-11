package com.chrono.ssh.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import java.util.Locale

@Composable
fun UptimeScreen(
    servers: List<ServerProfile>,
    snapshots: Map<String, MetricSnapshot>,
    metricHistory: Map<String, List<MetricSnapshot>>,
    backgroundMonitoringEnabled: Boolean,
    onBackgroundMonitoringChange: (Boolean) -> Unit,
    onHostMonitoringChange: (ServerProfile, Boolean) -> Unit,
    onBack: () -> Unit,
    onServerClick: (ServerProfile) -> Unit,
    onRefresh: (ServerProfile) -> Unit
) {
    val rows = uptimeRows(servers, snapshots, metricHistory)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            UptimeBackButton(onBack)
            Column(Modifier.weight(1f)) {
                Text("Uptime", color = DeckColors.PrimaryText, fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.Black)
                Text("${rows.size} hosts tracked from collected checks", color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        UptimeSummaryRow(rows)
        Spacer(Modifier.height(10.dp))
        UptimeToggleRow(
            title = "Background uptime checks",
            detail = if (backgroundMonitoringEnabled) "Checks continue after leaving the app" else "Checks run while chronoSSH is open",
            checked = backgroundMonitoringEnabled,
            onCheckedChange = onBackgroundMonitoringChange
        )
        Spacer(Modifier.height(14.dp))
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            UptimeHostCard(
                row = row,
                onClick = { onServerClick(row.server) },
                onRefresh = { onRefresh(row.server) },
                onMonitoringChange = { onHostMonitoringChange(row.server, it) }
            )
        }
        if (rows.isEmpty()) {
            DeckCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(18.dp)) {
                Text("No hosts yet", color = DeckColors.PrimaryText, fontSize = 20.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(6.dp))
                Text("Add a host from Servers to populate uptime.", color = DeckColors.SecondaryText, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun UptimeSummaryRow(rows: List<UptimeRow>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        UptimeChip("Up", rows.count { it.status == ServerStatus.Online }, DeckColors.Green, Modifier.weight(1f))
        UptimeChip("Down", rows.count { it.status == ServerStatus.Offline }, DeckColors.Red, Modifier.weight(1f))
        UptimeChip("Unknown", rows.count { it.status == ServerStatus.Unknown || it.status == ServerStatus.Connecting }, DeckColors.SecondaryText, Modifier.weight(1f))
    }
}

@Composable
private fun UptimeChip(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    DeckCard(modifier = modifier, radius = 18.dp, padding = PaddingValues(12.dp)) {
        Text(label, color = color, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(count.toString(), color = DeckColors.PrimaryText, fontSize = 28.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, maxLines = 1)
    }
}

@Composable
private fun UptimeHostCard(row: UptimeRow, onClick: () -> Unit, onRefresh: () -> Unit, onMonitoringChange: (Boolean) -> Unit) {
    val color = uptimeStatusColor(row.status)
    DeckCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        radius = 24.dp,
        padding = PaddingValues(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(color)
            )
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text(row.server.name, color = DeckColors.PrimaryText, fontSize = 18.sp, lineHeight = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${row.server.username}@${row.server.host}:${row.server.port}", color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                UptimeBars(row.buckets)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    UptimeMeta("Status", row.statusLabel, color, Modifier.weight(1f))
                    UptimeMeta("24h", row.uptime24hLabel, DeckColors.Cyan, Modifier.weight(1f))
                    UptimeMeta("7d", row.uptime7dLabel, DeckColors.Purple, Modifier.weight(1f))
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                UptimeSwitch(row.monitoringEnabled, onMonitoringChange)
                UptimeRefreshButton(onRefresh)
            }
        }
    }
}

@Composable
private fun UptimeToggleRow(title: String, detail: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 18.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, color = DeckColors.PrimaryText, fontSize = 15.sp, lineHeight = 17.sp, fontWeight = FontWeight.Black)
                Text(detail, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            UptimeSwitch(checked, onCheckedChange)
        }
    }
}

@Composable
private fun UptimeSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(99.dp))
            .background(if (checked) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
            .clickable { onCheckedChange(!checked) }
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(if (checked) DeckColors.Green else DeckColors.SecondaryText)
        )
    }
}

@Composable
private fun UptimeBars(buckets: List<UptimeBucketStatus>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        buckets.forEach { status ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
                    .background(uptimeBucketColor(status))
            )
        }
    }
}

@Composable
private fun UptimeMeta(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, color = DeckColors.SecondaryText, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(value, color = color, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun UptimeBackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DeckColors.Surface)
            .clickable(onClick = onBack),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(20.dp)) {
            val stroke = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(DeckColors.PrimaryText, androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.20f), androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.50f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(DeckColors.PrimaryText, androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.50f), androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
    Spacer(Modifier.size(12.dp))
}

@Composable
private fun UptimeRefreshButton(onRefresh: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DeckColors.SurfaceMuted)
            .clickable(onClick = onRefresh),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(19.dp)) {
            val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
            drawArc(DeckColors.Cyan, startAngle = 35f, sweepAngle = 285f, useCenter = false, style = stroke)
            drawLine(DeckColors.Cyan, androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.12f), androidx.compose.ui.geometry.Offset(size.width * 0.92f, size.height * 0.13f), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(DeckColors.Cyan, androidx.compose.ui.geometry.Offset(size.width * 0.76f, size.height * 0.12f), androidx.compose.ui.geometry.Offset(size.width * 0.78f, size.height * 0.28f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}

internal data class UptimeRow(
    val server: ServerProfile,
    val status: ServerStatus,
    val statusLabel: String,
    val monitoringEnabled: Boolean,
    val uptime24hLabel: String,
    val uptime7dLabel: String,
    val buckets: List<UptimeBucketStatus>
)

internal fun uptimeRows(
    servers: List<ServerProfile>,
    snapshots: Map<String, MetricSnapshot>,
    metricHistory: Map<String, List<MetricSnapshot>>
): List<UptimeRow> {
    return servers.sortedBy { it.name.lowercase() }.map { server ->
        val snapshot = snapshots[server.id]
        val status = snapshot?.status ?: ServerStatus.Unknown
        val samples = uptimeSamples(server.id, snapshots, metricHistory)
        UptimeRow(
            server = server,
            status = status,
            statusLabel = statusLabel(status),
            monitoringEnabled = server.monitoringConfig.enabled,
            uptime24hLabel = formatUptimePercent(uptimePercent(samples, UptimeWindow24hMillis)),
            uptime7dLabel = formatUptimePercent(uptimePercent(samples, UptimeWindow7dMillis)),
            buckets = uptimeBuckets(samples)
        )
    }
}

internal enum class UptimeBucketStatus {
    NoData,
    Up,
    Down,
    Unverified
}

private const val UptimeWindow24hMillis = 24L * 60L * 60L * 1000L
private const val UptimeWindow7dMillis = 7L * UptimeWindow24hMillis
private const val UptimeBucketMillis = 30L * 60L * 1000L

private fun uptimeSamples(
    serverId: String,
    snapshots: Map<String, MetricSnapshot>,
    metricHistory: Map<String, List<MetricSnapshot>>
): List<MetricSnapshot> {
    val current = snapshots[serverId]
    return (metricHistory[serverId].orEmpty() + listOfNotNull(current))
        .distinctBy { it.collectedAtEpochMillis }
        .sortedBy { it.collectedAtEpochMillis }
}

internal fun uptimeBuckets(samples: List<MetricSnapshot>): List<UptimeBucketStatus> {
    if (samples.isEmpty()) return List(48) { UptimeBucketStatus.NoData }
    val sorted = samples.sortedBy { it.collectedAtEpochMillis }
    val now = sorted.last().collectedAtEpochMillis
    val windowStart = now - UptimeWindow24hMillis
    return List(48) { index ->
        val bucketEnd = windowStart + ((index + 1) * UptimeBucketMillis)
        when (sorted.lastOrNull { it.collectedAtEpochMillis <= bucketEnd }?.status) {
            ServerStatus.Online -> UptimeBucketStatus.Up
            ServerStatus.Offline -> UptimeBucketStatus.Down
            ServerStatus.Unknown,
            ServerStatus.Connecting -> UptimeBucketStatus.Unverified
            null -> UptimeBucketStatus.NoData
        }
    }
}

internal fun uptimePercent(samples: List<MetricSnapshot>, windowMillis: Long): Double? {
    if (samples.isEmpty()) return null
    val now = samples.maxOf { it.collectedAtEpochMillis }
    val windowStart = now - windowMillis
    val verified = samples.filter {
        it.collectedAtEpochMillis >= windowStart &&
            (it.status == ServerStatus.Online || it.status == ServerStatus.Offline)
    }
    if (verified.isEmpty()) return null
    val up = verified.count { it.status == ServerStatus.Online }
    return (up.toDouble() / verified.size.toDouble()) * 100.0
}

private fun formatUptimePercent(value: Double?): String {
    return value?.let { String.format(Locale.US, "%.1f%%", it) } ?: "--"
}

private fun statusLabel(status: ServerStatus): String {
    return when (status) {
        ServerStatus.Online -> "Up"
        ServerStatus.Offline -> "Down"
        ServerStatus.Connecting -> "Checking"
        ServerStatus.Unknown -> "Unknown"
    }
}

private fun uptimeStatusColor(status: ServerStatus): Color = when (status) {
    ServerStatus.Online -> DeckColors.Green
    ServerStatus.Offline -> DeckColors.Red
    ServerStatus.Connecting -> DeckColors.Orange
    ServerStatus.Unknown -> DeckColors.SecondaryText
}

private fun uptimeBucketColor(status: UptimeBucketStatus): Color = when (status) {
    UptimeBucketStatus.Up -> DeckColors.Green.copy(alpha = 0.9f)
    UptimeBucketStatus.Down -> DeckColors.Red.copy(alpha = 0.9f)
    UptimeBucketStatus.Unverified -> DeckColors.SecondaryText.copy(alpha = 0.42f)
    UptimeBucketStatus.NoData -> DeckColors.SurfaceMuted
}
