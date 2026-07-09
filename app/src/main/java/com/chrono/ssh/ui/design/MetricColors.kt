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
    return ServerMetricColors(
        cpu = DeckColors.MetricCpu,
        memory = DeckColors.MetricMemory,
        disk = DeckColors.MetricDisk,
        network = DeckColors.MetricNetwork
    )
}

private fun String.toColorOrNull(): Color? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6 || clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return Color((0xff000000L or clean.toLong(16)).toInt())
}
