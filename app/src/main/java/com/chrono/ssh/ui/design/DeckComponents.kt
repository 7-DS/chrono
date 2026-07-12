package com.chrono.ssh.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.service.MetricFormatters
import kotlin.math.max

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeckColors.Background)
    ) {
        content()
    }
}

@Composable
fun LargeScreenTitle(text: String, modifier: Modifier = Modifier, headingTarget: HeadingFontTarget? = null) {
    Text(
        text = text,
        modifier = modifier,
        color = DeckColors.PrimaryText,
        fontSize = 46.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight.Black,
        style = TextStyle(fontFamily = LocalHeadingFontFamilies.current.forTarget(headingTarget)),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun DeckCard(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    padding: PaddingValues = PaddingValues(24.dp),
    shape: Shape = RoundedCornerShape(radius),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .animateContentSize(animationSpec = tween(220, easing = LinearOutSlowInEasing))
            .clip(shape)
            .background(DeckColors.Surface)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.58f), shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun CircleIconButton(
    symbol: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "circleButtonPress"
    )
    Box(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .clip(RoundedCornerShape(22.dp))
            .background(DeckColors.SurfaceRaised)
            .border(1.dp, DeckColors.CardStroke.copy(alpha = 0.72f), RoundedCornerShape(22.dp))
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (symbol) {
            "host-add", "server-plus" -> PlusGlyph(DeckColors.PrimaryText, Modifier.size(25.dp))
            "host-save", "save" -> CheckGlyph(DeckColors.PrimaryText, Modifier.size(24.dp))
            "<", "back" -> BackGlyph(DeckColors.PrimaryText, Modifier.size(28.dp))
            "x", "close" -> CloseGlyph(DeckColors.PrimaryText, Modifier.size(26.dp))
            "...", "more" -> MoreGlyph(DeckColors.PrimaryText, Modifier.size(28.dp))
            else -> Text(symbol, color = DeckColors.PrimaryText, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PlusGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.25f, center.y), Offset(size.width * 0.75f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(center.x, size.height * 0.25f), Offset(center.x, size.height * 0.75f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun CheckGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.22f, size.height * 0.55f), Offset(size.width * 0.42f, size.height * 0.72f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.42f, size.height * 0.72f), Offset(size.width * 0.80f, size.height * 0.30f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun BackGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.66f, size.height * 0.20f), Offset(size.width * 0.34f, center.y), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.34f, center.y), Offset(size.width * 0.66f, size.height * 0.80f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.26f, size.height * 0.26f), Offset(size.width * 0.74f, size.height * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.74f, size.height * 0.26f), Offset(size.width * 0.26f, size.height * 0.74f), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Composable
private fun MoreGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        listOf(0.30f, 0.50f, 0.70f).forEach { x ->
            drawCircle(color, radius = size.minDimension * 0.075f, center = Offset(size.width * x, center.y))
        }
    }
}

@Composable
fun SoftPill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    color: Color = DeckColors.SecondaryText,
    onClick: () -> Unit
) {
    val background by animateColorAsState(
        targetValue = if (selected) DeckColors.SurfaceRaised else DeckColors.SurfaceMuted,
        animationSpec = tween(160, easing = LinearOutSlowInEasing),
        label = "softPillBackground"
    )
    val markColor = if (selected) color.copy(alpha = 0.72f) else DeckColors.SecondaryText.copy(alpha = 0.48f)
    val borderColor by animateColorAsState(
        targetValue = if (selected) DeckColors.CardStroke else DeckColors.Divider.copy(alpha = 0.35f),
        animationSpec = tween(160, easing = LinearOutSlowInEasing),
        label = "softPillBorder"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) DeckColors.PrimaryText else DeckColors.SecondaryText,
        animationSpec = tween(160, easing = LinearOutSlowInEasing),
        label = "softPillText"
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = tween(120, easing = LinearOutSlowInEasing),
        label = "softPillPress"
    )
    Row(
        modifier = modifier
            .scale(scale)
            .animateContentSize(animationSpec = tween(180, easing = LinearOutSlowInEasing))
            .clip(RoundedCornerShape(24.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(markColor, radius = 4.5.dp.toPx())
        }
        Text(text, color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SegmentedPillControl(
    items: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(DeckColors.SurfaceMuted)
            .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(28.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            val weight = if (selected) 1.05f else 1f
            val tabBackground by animateColorAsState(
                targetValue = if (selected) DeckColors.SurfaceRaised else Color.Transparent,
                animationSpec = tween(160, easing = LinearOutSlowInEasing),
                label = "segmentBackground"
            )
            val tabScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.98f,
                animationSpec = tween(160, easing = LinearOutSlowInEasing),
                label = "segmentScale"
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .scale(tabScale)
                    .clip(RoundedCornerShape(24.dp))
                    .background(tabBackground)
                    .clickable { onSelected(index) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item,
                    color = DeckColors.PrimaryText,
                    fontSize = 17.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun StatusDot(status: ServerStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        ServerStatus.Online -> DeckColors.Green
        ServerStatus.Offline -> DeckColors.Red
        ServerStatus.Connecting -> DeckColors.Orange
        ServerStatus.Unknown -> DeckColors.SecondaryText
    }
    Canvas(modifier.size(16.dp)) {
        drawCircle(color, radius = size.minDimension / 3)
    }
}

@Composable
fun MetricSummaryCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    DeckCard(modifier = modifier.height(126.dp), radius = 28.dp, padding = PaddingValues(18.dp)) {
        Text(label, color = DeckColors.SecondaryText, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Canvas(Modifier.size(18.dp)) {
                drawCircle(color, radius = size.minDimension / 3)
            }
            Text(value, color = DeckColors.PrimaryText, fontSize = 34.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun MetricRing(
    percent: Int,
    color: Color,
    modifier: Modifier = Modifier,
    trackColor: Color = DeckColors.SurfaceMuted,
    valueFontSizeSp: Int = 16,
    animate: Boolean = true
) {
    val displayPercent = if (animate) {
        val animatedPercent by animateFloatAsState(
            targetValue = percent.coerceIn(0, 100).toFloat(),
            animationSpec = tween(durationMillis = 420, easing = LinearOutSlowInEasing),
            label = "metricRing"
        )
        animatedPercent
    } else {
        percent.coerceIn(0, 100).toFloat()
    }
    Box(modifier = modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val inset = 8.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
            drawArc(
                color = color.copy(alpha = 0.82f),
                startAngle = -90f,
                sweepAngle = metricRingSweep(displayPercent),
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke
            )
        }
        Text("${displayPercent.toInt()}%", color = DeckColors.PrimaryText, fontSize = valueFontSizeSp.sp, fontWeight = FontWeight.Black)
    }
}

internal fun metricRingSweep(percent: Float): Float = 360f * percent.coerceIn(0f, 100f) / 100f

@Composable
fun NetworkDonut(
    uploadShare: Float,
    modifier: Modifier = Modifier,
    uploadColor: Color = DeckColors.MetricDisk,
    downloadColor: Color = DeckColors.MetricNetwork
) {
    Canvas(modifier.aspectRatio(1f)) {
        val stroke = Stroke(width = 15.dp.toPx(), cap = StrokeCap.Round)
        val inset = 12.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        val uploadSweep = 330f * uploadShare.coerceIn(0.05f, 0.95f)
        drawArc(
            color = downloadColor.copy(alpha = 0.78f),
            startAngle = 105f,
            sweepAngle = 330f - uploadSweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = uploadColor.copy(alpha = 0.78f),
            startAngle = 105f + 330f - uploadSweep + 12f,
            sweepAngle = uploadSweep - 12f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke
        )
    }
}

@Composable
fun MiniLineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    lineColor: Color = DeckColors.Red
) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(170.dp)
    ) {
        val grid = DeckColors.Divider
        repeat(4) { row ->
            val y = size.height * row / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        repeat(4) { col ->
            val x = size.width * col / 3f
            drawLine(grid.copy(alpha = 0.7f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        if (values.size < 2) return@Canvas
        val maxValue = max(values.maxOrNull() ?: 1f, 0.01f)
        val points = values.mapIndexed { index, value ->
            val x = if (values.lastIndex == 0) 0f else size.width * index / (values.lastIndex)
            val y = size.height - (value / maxValue) * size.height
            Offset(x, y)
        }
        points.zipWithNext().forEach { (from, to) ->
            drawLine(lineColor, from, to, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        values.forEachIndexed { index, value ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.76f)
                        .fillMaxHeight(value.coerceIn(0.08f, 1f))
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(color)
                )
                Spacer(Modifier.height(10.dp))
                Text(labels.getOrElse(index) { "" }, color = DeckColors.SecondaryText, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DirectionMetric(
    upload: Boolean,
    value: String,
    modifier: Modifier = Modifier,
    uploadColor: Color = DeckColors.MetricDisk,
    downloadColor: Color = DeckColors.MetricNetwork
) {
    val color = if (upload) uploadColor else downloadColor
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.84f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (upload) "^" else "v", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Text(value, color = DeckColors.PrimaryText, fontSize = 24.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
fun InterfaceMetricCard(
    metric: NetworkInterfaceMetric,
    modifier: Modifier = Modifier,
    uploadColor: Color = DeckColors.MetricDisk,
    downloadColor: Color = DeckColors.MetricNetwork,
    onClick: () -> Unit = {}
) {
    DeckCard(modifier = modifier.clickable(onClick = onClick), radius = 26.dp, padding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(metric.name, color = DeckColors.PrimaryText, fontSize = 22.sp, lineHeight = 24.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metric.address, color = DeckColors.SecondaryText, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DeckColors.Divider)
        )
        Spacer(Modifier.height(12.dp))
        InterfaceTrafficRow(upload = true, speed = metric.uploadRate, total = metric.uploadTotal, color = uploadColor)
        Spacer(Modifier.height(7.dp))
        InterfaceTrafficRow(upload = false, speed = metric.downloadRate, total = metric.downloadTotal, color = downloadColor)
    }
}

@Composable
private fun InterfaceTrafficRow(upload: Boolean, speed: String, total: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(DeckColors.SurfaceMuted)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.84f)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (upload) "↑" else "↓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(if (upload) "Upload" else "Download", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            InterfaceMetricValue(speed, alignEnd = false)
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text("Total", color = DeckColors.SecondaryText, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            InterfaceMetricValue(total, alignEnd = true)
        }
    }
}

@Composable
private fun InterfaceMetricValue(label: String, alignEnd: Boolean) {
    val (amount, unit) = MetricFormatters.compactAmountAndUnitLabel(label)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        Text(
            amount,
            color = DeckColors.PrimaryText,
            fontSize = 20.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(72.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            unit,
            color = DeckColors.SecondaryText,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = if (alignEnd) TextAlign.Start else TextAlign.Start,
            modifier = Modifier.width(36.dp)
        )
    }
}

@Composable
fun TerminalActionRow(keys: List<String>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        keys.forEach { key ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(DeckColors.SurfaceRaised)
                    .border(1.dp, DeckColors.CardStroke, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(key, color = DeckColors.PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
fun CheckLine(text: String, active: Boolean = true, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Canvas(Modifier.size(18.dp)) {
            drawCircle(if (active) DeckColors.Green else DeckColors.SecondaryText)
            drawLine(Color.White, Offset(size.width * 0.28f, size.height * 0.54f), Offset(size.width * 0.43f, size.height * 0.69f), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawLine(Color.White, Offset(size.width * 0.43f, size.height * 0.69f), Offset(size.width * 0.74f, size.height * 0.34f), strokeWidth = 2.2.dp.toPx(), cap = StrokeCap.Round)
        }
        Text(text, color = DeckColors.SecondaryText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
