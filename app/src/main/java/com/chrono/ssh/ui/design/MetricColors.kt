package com.chrono.ssh.ui.design

import androidx.compose.ui.graphics.Color
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.model.ServerMetricColorPreset

data class ServerMetricColors(
    val cpu: Color,
    val memory: Color,
    val disk: Color,
    val network: Color
)

data class ServerMetricColorOverrides(
    val cpuHex: String? = null,
    val memoryHex: String? = null,
    val diskHex: String? = null,
    val networkHex: String? = null
)

fun metricColorOverridesFrom(settings: AppSettings): ServerMetricColorOverrides =
    ServerMetricColorOverrides(
        cpuHex = settings.serverMetricCpuColorHex,
        memoryHex = settings.serverMetricMemoryColorHex,
        diskHex = settings.serverMetricDiskColorHex,
        networkHex = settings.serverMetricNetworkColorHex
    )

fun metricColorsFor(settings: AppSettings): ServerMetricColors =
    metricColorsFor(settings.serverMetricColorPreset, metricColorOverridesFrom(settings))

fun metricColorsFor(
    preset: ServerMetricColorPreset,
    overrides: ServerMetricColorOverrides = ServerMetricColorOverrides()
): ServerMetricColors {
    return when (preset) {
        ServerMetricColorPreset.Theme -> themeMetricColors()
        ServerMetricColorPreset.Custom -> customMetricColors(overrides)
        ServerMetricColorPreset.Classic -> ServerMetricColors(
            cpu = DeckColors.Cyan,
            memory = DeckColors.Green,
            disk = DeckColors.Orange,
            network = DeckColors.Cyan
        )
        ServerMetricColorPreset.Calm -> ServerMetricColors(
            cpu = Color(0xff5b8def),
            memory = Color(0xff6aa88f),
            disk = Color(0xffb58b5a),
            network = Color(0xff5d9aa6)
        )
        ServerMetricColorPreset.Graphite -> ServerMetricColors(
            cpu = Color(0xffa7b0bd),
            memory = Color(0xff8fa3a3),
            disk = Color(0xffb3a491),
            network = Color(0xff93a4b7)
        )
        ServerMetricColorPreset.HighContrast -> ServerMetricColors(
            cpu = Color(0xff4da3ff),
            memory = Color(0xff35c76f),
            disk = Color(0xffffa24a),
            network = Color(0xff7cc7ff)
        )
        ServerMetricColorPreset.Ocean -> ServerMetricColors(
            cpu = Color(0xff2f80ed),
            memory = Color(0xff00a676),
            disk = Color(0xfff2994a),
            network = Color(0xff27a9e1)
        )
        ServerMetricColorPreset.Forest -> ServerMetricColors(
            cpu = Color(0xff6b8f3e),
            memory = Color(0xff2f8f6b),
            disk = Color(0xffb68d40),
            network = Color(0xff4f8f8a)
        )
        ServerMetricColorPreset.Ember -> ServerMetricColors(
            cpu = Color(0xffd36b4b),
            memory = Color(0xff7aa36f),
            disk = Color(0xffc7953d),
            network = Color(0xff8fa6b8)
        )
        ServerMetricColorPreset.Aurora -> ServerMetricColors(
            cpu = Color(0xff3b82f6),
            memory = Color(0xff10b981),
            disk = Color(0xfff97316),
            network = Color(0xff8b5cf6)
        )
        ServerMetricColorPreset.Orchid -> ServerMetricColors(
            cpu = Color(0xff7c3aed),
            memory = Color(0xffdb2777),
            disk = Color(0xffca8a04),
            network = Color(0xff0891b2)
        )
        ServerMetricColorPreset.Nordic -> ServerMetricColors(
            cpu = Color(0xff5e81ac),
            memory = Color(0xffa3be8c),
            disk = Color(0xffd08770),
            network = Color(0xff88c0d0)
        )
        ServerMetricColorPreset.Solar -> ServerMetricColors(
            cpu = Color(0xff2563eb),
            memory = Color(0xff16a34a),
            disk = Color(0xffeab308),
            network = Color(0xffdc2626)
        )
        ServerMetricColorPreset.Circuit -> ServerMetricColors(
            cpu = Color(0xff0f766e),
            memory = Color(0xff84cc16),
            disk = Color(0xfff59e0b),
            network = Color(0xff6366f1)
        )
        ServerMetricColorPreset.Harvest -> ServerMetricColors(
            cpu = Color(0xffb45309),
            memory = Color(0xff4d7c0f),
            disk = Color(0xffa16207),
            network = Color(0xff0f766e)
        )
        ServerMetricColorPreset.Lagoon -> ServerMetricColors(
            cpu = Color(0xff0284c7),
            memory = Color(0xff059669),
            disk = Color(0xff7c3aed),
            network = Color(0xff0891b2)
        )
        ServerMetricColorPreset.Metro -> ServerMetricColors(
            cpu = Color(0xff2563eb),
            memory = Color(0xffdb2777),
            disk = Color(0xffea580c),
            network = Color(0xff16a34a)
        )
        ServerMetricColorPreset.Mono -> ServerMetricColors(
            cpu = Color(0xffc2c8cf),
            memory = Color(0xff9aa4ad),
            disk = Color(0xff7d8790),
            network = Color(0xffaeb6bf)
        )
    }
}

fun metricColorHex(color: Color): String {
    return "#%02X%02X%02X".format(
        (color.red * 255).toInt().coerceIn(0, 255),
        (color.green * 255).toInt().coerceIn(0, 255),
        (color.blue * 255).toInt().coerceIn(0, 255)
    )
}

private fun customMetricColors(overrides: ServerMetricColorOverrides): ServerMetricColors {
    val base = themeMetricColors()
    return ServerMetricColors(
        cpu = overrides.cpuHex?.toColorOrNull() ?: base.cpu,
        memory = overrides.memoryHex?.toColorOrNull() ?: base.memory,
        disk = overrides.diskHex?.toColorOrNull() ?: base.disk,
        network = overrides.networkHex?.toColorOrNull() ?: base.network
    )
}

private fun themeMetricColors(): ServerMetricColors {
    val background = DeckColors.Surface
    val rawCandidates = listOf(
        DeckColors.Brand,
        DeckColors.BrandAlt,
        DeckColors.Cyan,
        DeckColors.Green,
        DeckColors.Yellow,
        DeckColors.Orange,
        DeckColors.Purple,
        DeckColors.Red,
        DeckColors.PrimaryText,
        DeckColors.SecondaryText
    )
    val candidates = rawCandidates.map { it.ensureVisibleOn(background) }
    val neutralTheme = rawCandidates.take(8).maxOf { it.chromaApprox() } < 0.08f
    val picked = if (neutralTheme && DeckColors.Background.luminanceApprox() > 0.72f && DeckColors.PrimaryText.luminanceApprox() < 0.18f) {
        listOf(Color(0xff111111), Color(0xff444444), Color(0xff777777), Color(0xff000000))
    } else if (neutralTheme && DeckColors.Background.luminanceApprox() < 0.12f && DeckColors.PrimaryText.luminanceApprox() > 0.82f) {
        listOf(Color(0xfff4f4f4), Color(0xffc9c9c9), Color(0xff9c9c9c), Color(0xffffffff))
    } else {
        candidates + listOf(Color(0xff4da3ff), Color(0xff35c76f), Color(0xffffb24a), Color(0xffb68cff))
    }
    return ServerMetricColors(
        cpu = picked.getOrElse(0) { DeckColors.BrandAlt },
        memory = picked.getOrElse(1) { DeckColors.Green },
        disk = picked.getOrElse(2) { DeckColors.Orange },
        network = picked.getOrElse(3) { DeckColors.Cyan }
    )
}

private fun String.toColorOrNull(): Color? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6 || clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return Color((0xff000000L or clean.toLong(16)).toInt())
}

private fun Color.ensureVisibleOn(background: Color): Color {
    val delta = kotlin.math.abs(luminanceApprox() - background.luminanceApprox())
    if (delta >= 0.18f) return this
    return if (background.luminanceApprox() < 0.5f) mix(Color.White, 0.38f) else mix(Color.Black, 0.32f)
}

private fun Color.mix(other: Color, amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * a,
        green = green + (other.green - green) * a,
        blue = blue + (other.blue - blue) * a,
        alpha = alpha
    )
}

private fun Color.luminanceApprox(): Float = 0.299f * red + 0.587f * green + 0.114f * blue

private fun Color.chromaApprox(): Float = maxOf(red, green, blue) - minOf(red, green, blue)
