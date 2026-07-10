package com.chrono.ssh.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.VnStatPeriodUsage
import com.chrono.ssh.core.model.VnStatRange
import com.chrono.ssh.core.service.MetricFormatters
import com.chrono.ssh.ui.design.CircleIconButton
import com.chrono.ssh.ui.design.DeckCard
import com.chrono.ssh.ui.design.DeckColors
import com.chrono.ssh.ui.design.InterfaceMetricCard
import com.chrono.ssh.ui.design.LargeScreenTitle
import com.chrono.ssh.ui.design.SegmentedPillControl

@Composable
fun InterfacesScreen(
    server: ServerProfile,
    snapshot: MetricSnapshot,
    onClose: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    var range by remember { mutableIntStateOf(0) }
    Column(
        modifier = Modifier
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SegmentedPillControl(listOf("Interfaces", "vnStat"), tab, Modifier.weight(1f)) { tab = it }
            Spacer(Modifier.size(16.dp))
            CircleIconButton("x", "Close", modifier = Modifier.size(60.dp), onClick = onClose)
        }
        Spacer(Modifier.height(34.dp))
        LargeScreenTitle(if (tab == 0) "Interfaces" else "vnStat")
        Text(server.name, color = DeckColors.SecondaryText, fontSize = 18.sp)
        Spacer(Modifier.height(22.dp))
        if (tab == 0) {
            val interfaces = interfaceMetricsForDisplay(snapshot.network)
            if (interfaces.isEmpty()) {
                DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(18.dp)) {
                    Text("No interfaces", color = DeckColors.PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    Text("No interface counters were collected for this host.", color = DeckColors.SecondaryText, fontSize = 15.sp, lineHeight = 20.sp)
                }
            } else {
                interfaces.forEach { metric ->
                    InterfaceMetricCard(metric)
                    Spacer(Modifier.height(18.dp))
                }
            }
        } else {
            val ranges = VnStatRange.entries
            val selectedRange = ranges[range.coerceIn(0, ranges.lastIndex)]
            val vnStat = snapshot.network.history.vnStat
            val usage = vnStat?.forRange(selectedRange)
            SegmentedPillControl(ranges.map { it.name }, range, Modifier.fillMaxWidth()) { range = it }
            Spacer(Modifier.height(14.dp))
            if (vnStat == null) {
                DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
                    Text("No vnStat usage", color = DeckColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No day, week, month, or year usage totals were collected for this host.",
                        color = DeckColors.SecondaryText,
                        fontSize = 15.sp,
                        lineHeight = 20.sp
                    )
                }
            } else {
                VnStatPeriodGrid(
                    usages = ranges.mapNotNull { current -> vnStat.forRange(current)?.let { current to it } },
                    selectedRange = selectedRange,
                    onSelected = { range = it.ordinal }
                )
                Spacer(Modifier.height(14.dp))
                if (usage == null) {
                    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
                        Text("No ${selectedRange.name.lowercase()} total", color = DeckColors.PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "vnStat did not return a usage total for this range.",
                            color = DeckColors.SecondaryText,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                } else {
                    VnStatUsageCard(selectedRange, usage)
                }
            }
        }
    }
}

@Composable
private fun VnStatPeriodGrid(
    usages: List<Pair<VnStatRange, VnStatPeriodUsage>>,
    selectedRange: VnStatRange,
    onSelected: (VnStatRange) -> Unit
) {
    if (usages.isEmpty()) return
    val maxBytes = max(1L, usages.maxOf { it.second.totalBytes })
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 28.dp, padding = PaddingValues(12.dp)) {
        Column {
            usages.chunked(2).forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { (range, usage) ->
                        VnStatPeriodTile(
                            range = range,
                            usage = usage,
                            selected = range == selectedRange,
                            maxBytes = maxBytes,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelected(range) }
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                if (index != usages.chunked(2).lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun VnStatPeriodTile(
    range: VnStatRange,
    usage: VnStatPeriodUsage,
    selected: Boolean,
    maxBytes: Long,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val fill = (usage.totalBytes.toFloat() / maxBytes.toFloat()).coerceIn(0.04f, 1f)
    Column(
        modifier = modifier
            .padding(4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp)
            .then(Modifier),
        horizontalAlignment = Alignment.Start
    ) {
        Text(range.name, color = DeckColors.SecondaryText, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(5.dp))
        Text(usage.totalBytes.bytesLabelUi(), color = DeckColors.PrimaryText, fontSize = 21.sp, lineHeight = 23.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    .fillMaxWidth(fill)
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) DeckColors.MetricNetwork else DeckColors.SecondaryText.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun VnStatUsageCard(range: VnStatRange, usage: VnStatPeriodUsage) {
    DeckCard(modifier = Modifier.fillMaxWidth(), radius = 32.dp, padding = PaddingValues(20.dp)) {
        Text("${range.name} usage", color = DeckColors.SecondaryText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(usage.label, color = DeckColors.SecondaryText, fontSize = 13.sp, lineHeight = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(9.dp))
        Text(usage.totalBytes.bytesLabelUi(), color = DeckColors.PrimaryText, fontSize = 46.sp, lineHeight = 48.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        VnStatLine("Upload", usage.transmittedBytes, usage.totalBytes, DeckColors.MetricDisk)
        Spacer(Modifier.height(9.dp))
        VnStatLine("Download", usage.receivedBytes, usage.totalBytes, DeckColors.MetricNetwork)
    }
}

@Composable
private fun VnStatLine(label: String, bytes: Long, totalBytes: Long, color: androidx.compose.ui.graphics.Color) {
    val share = if (totalBytes <= 0L) 0f else (bytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, color = DeckColors.SecondaryText, fontSize = 16.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(bytes.bytesLabelUi(), color = color, fontSize = 25.sp, lineHeight = 27.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DeckColors.Divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share.coerceAtLeast(0.02f))
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color)
            )
        }
    }
}

private fun Long.bytesLabelUi(): String {
    return MetricFormatters.bytesLabel(this)
}

internal fun interfaceMetricsForDisplay(network: NetworkMetrics): List<NetworkInterfaceMetric> {
    return network.interfaces.ifEmpty {
        listOf(network.primaryInterface).filter { it.hasCollectedData() }
    }
}

private fun NetworkInterfaceMetric.hasCollectedData(): Boolean {
    return address != "--" ||
        uploadRate != "--" ||
        downloadRate != "--" ||
        uploadTotal != "--" ||
        downloadTotal != "--"
}
