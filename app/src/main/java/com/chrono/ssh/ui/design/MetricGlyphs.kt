package com.chrono.ssh.ui.design

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Shared processor/CPU chip glyph, drawn from a single source so every metric surface
 * (home cards, server-detail titles) renders the exact same icon.
 *
 * Recreated from the "chip processor line icon" reference: a rounded-square die with five
 * pins on every side and interior circuit traces ending in node pads. Geometry is defined on a
 * normalized 0..1 grid and scaled to the canvas, and the stroke is clamped so the icon stays
 * crisp and seamless from ~14dp up to card-title sizes without blobbing or clipping.
 */
fun DrawScope.drawCpuChipGlyph(color: Color) {
    val dim = size.minDimension
    val w = size.width
    val h = size.height

    // Stroke scales with the icon but never gets thinner than a hairline that still reads.
    val strokePx = (dim * 0.06f).coerceAtLeast(1.2.dp.toPx())
    val nodeRadius = (dim * 0.042f).coerceAtLeast(strokePx * 0.55f)

    val bodyStroke = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val pinStroke = Stroke(width = strokePx, cap = StrokeCap.Round)

    // Die body inset from the canvas edges; the gap left over holds the pins.
    val inset = 0.24f
    val left = w * inset
    val top = h * inset
    val dieW = w * (1f - inset * 2f)
    val dieH = h * (1f - inset * 2f)

    drawRoundRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(dieW, dieH),
        cornerRadius = CornerRadius(dim * 0.11f, dim * 0.11f),
        style = bodyStroke
    )

    // Five pins per side, evenly distributed along the die span.
    val pinOuter = inset - 0.15f   // outer tip of each pin
    val pinInner = inset - 0.02f   // stops just short of the die wall
    for (i in 0 until 5) {
        val t = inset + (1f - inset * 2f) * ((i + 0.5f) / 5f)
        // top & bottom pins (vertical)
        drawLine(color, Offset(w * t, h * pinOuter), Offset(w * t, h * pinInner), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(w * t, h * (1f - pinOuter)), Offset(w * t, h * (1f - pinInner)), strokeWidth = strokePx, cap = StrokeCap.Round)
        // left & right pins (horizontal)
        drawLine(color, Offset(w * pinOuter, h * t), Offset(w * pinInner, h * t), strokeWidth = strokePx, cap = StrokeCap.Round)
        drawLine(color, Offset(w * (1f - pinOuter), h * t), Offset(w * (1f - pinInner), h * t), strokeWidth = strokePx, cap = StrokeCap.Round)
    }

    // Interior circuit traces (normalized full-canvas coords), each ending in a node pad.
    fun trace(vararg pts: Pair<Float, Float>) {
        if (pts.size < 2) return
        val path = Path().apply {
            moveTo(w * pts[0].first, h * pts[0].second)
            for (k in 1 until pts.size) lineTo(w * pts[k].first, h * pts[k].second)
        }
        drawPath(path, color, style = bodyStroke)
    }

    fun node(x: Float, y: Float) {
        drawCircle(color, radius = nodeRadius, center = Offset(w * x, h * y))
    }

    // Left-anchored traces
    trace(0.24f to 0.40f, 0.42f to 0.40f); node(0.45f, 0.40f)
    trace(0.24f to 0.56f, 0.36f to 0.56f, 0.42f to 0.62f); node(0.45f, 0.62f)
    // Top-anchored traces
    trace(0.57f to 0.24f, 0.57f to 0.44f); node(0.57f, 0.47f)
    trace(0.66f to 0.24f, 0.66f to 0.36f); node(0.66f, 0.36f)
    // Middle trace stepping up toward the right wall
    node(0.50f, 0.55f); trace(0.50f to 0.55f, 0.62f to 0.55f, 0.68f to 0.49f, 0.76f to 0.49f)
    // Bottom-anchored stems
    node(0.60f, 0.66f); trace(0.60f to 0.66f, 0.60f to 0.76f)
    node(0.40f, 0.68f); trace(0.40f to 0.68f, 0.40f to 0.76f)
}
