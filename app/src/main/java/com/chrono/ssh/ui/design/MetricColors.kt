package com.chrono.ssh.ui.design

import androidx.compose.ui.graphics.Color
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.model.ServerMetricColorPreset

data class ServerMetricColors(
    val cpu: Color,
    val memory: Color,
    val disk: Color,
    val network: Color,
    val latency: Color
)

data class ServerMetricColorOverrides(
    val cpuHex: String? = null,
    val memoryHex: String? = null,
    val diskHex: String? = null,
    val networkHex: String? = null,
    val latencyHex: String? = null
)

fun metricColorOverridesFrom(settings: AppSettings): ServerMetricColorOverrides =
    ServerMetricColorOverrides(
        cpuHex = settings.serverMetricCpuColorHex,
        memoryHex = settings.serverMetricMemoryColorHex,
        diskHex = settings.serverMetricDiskColorHex,
        networkHex = settings.serverMetricNetworkColorHex,
        latencyHex = settings.serverMetricLatencyColorHex
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
            network = DeckColors.Cyan,
            latency = DeckColors.Green
        )
        ServerMetricColorPreset.Calm -> ServerMetricColors(
            cpu = Color(0xff6f8fa8),
            memory = Color(0xff78927a),
            disk = Color(0xffa48864),
            network = Color(0xff6f9298),
            latency = Color(0xff78927a)
        )
        ServerMetricColorPreset.Graphite -> ServerMetricColors(
            cpu = Color(0xffa7b0bd),
            memory = Color(0xff8fa3a3),
            disk = Color(0xffb3a491),
            network = Color(0xff93a4b7),
            latency = Color(0xff8fa3a3)
        )
        ServerMetricColorPreset.HighContrast -> ServerMetricColors(
            cpu = Color(0xff2f6fbb),
            memory = Color(0xff4d8f61),
            disk = Color(0xffb87534),
            network = Color(0xff4a8aa8),
            latency = Color(0xff4d8f61)
        )
        ServerMetricColorPreset.Ocean -> ServerMetricColors(
            cpu = Color(0xff3f6f8f),
            memory = Color(0xff4f8f7a),
            disk = Color(0xffa77f4f),
            network = Color(0xff4f8fa3),
            latency = Color(0xff4f8f7a)
        )
        ServerMetricColorPreset.Forest -> ServerMetricColors(
            cpu = Color(0xff6f814a),
            memory = Color(0xff5f8062),
            disk = Color(0xff9a7a45),
            network = Color(0xff5f8380),
            latency = Color(0xff5f8062)
        )
        ServerMetricColorPreset.Ember -> ServerMetricColors(
            cpu = Color(0xffaa664d),
            memory = Color(0xff768a62),
            disk = Color(0xffa98245),
            network = Color(0xff7d8d9a),
            latency = Color(0xff768a62)
        )
        ServerMetricColorPreset.Aurora -> ServerMetricColors(
            cpu = Color(0xff7aa2f7),
            memory = Color(0xff9ece6a),
            disk = Color(0xffe0af68),
            network = Color(0xff7dcfff),
            latency = Color(0xff73daca)
        )
        ServerMetricColorPreset.Orchid -> ServerMetricColors(
            cpu = Color(0xff8f7aa8),
            memory = Color(0xffa06f8a),
            disk = Color(0xffa98a55),
            network = Color(0xff6f929a),
            latency = Color(0xff75907a)
        )
        ServerMetricColorPreset.Nordic -> ServerMetricColors(
            cpu = Color(0xff5e81ac),
            memory = Color(0xffa3be8c),
            disk = Color(0xffd08770),
            network = Color(0xff88c0d0),
            latency = Color(0xffa3be8c)
        )
        ServerMetricColorPreset.Solar -> ServerMetricColors(
            cpu = Color(0xff268bd2),
            memory = Color(0xff859900),
            disk = Color(0xffb58900),
            network = Color(0xff2aa198),
            latency = Color(0xff2aa198)
        )
        ServerMetricColorPreset.Circuit -> ServerMetricColors(
            cpu = Color(0xff4d8b83),
            memory = Color(0xff7d9461),
            disk = Color(0xffa18445),
            network = Color(0xff6c75a8),
            latency = Color(0xff7d9461)
        )
        ServerMetricColorPreset.Harvest -> ServerMetricColors(
            cpu = Color(0xff9a7046),
            memory = Color(0xff78804d),
            disk = Color(0xffa18445),
            network = Color(0xff5f817a),
            latency = Color(0xff78804d)
        )
        ServerMetricColorPreset.Lagoon -> ServerMetricColors(
            cpu = Color(0xff4b83a1),
            memory = Color(0xff4f8f7a),
            disk = Color(0xff8370a0),
            network = Color(0xff4f8fa3),
            latency = Color(0xff4f8f7a)
        )
        ServerMetricColorPreset.Metro -> ServerMetricColors(
            cpu = Color(0xff5d7fa8),
            memory = Color(0xffa06f8a),
            disk = Color(0xffa9744f),
            network = Color(0xff6b8f70),
            latency = Color(0xff5f8f98)
        )
        ServerMetricColorPreset.Mono -> ServerMetricColors(
            cpu = Color(0xffc2c8cf),
            memory = Color(0xff9aa4ad),
            disk = Color(0xff7d8790),
            network = Color(0xffaeb6bf),
            latency = Color(0xffd6dbe0)
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
        network = overrides.networkHex?.toColorOrNull() ?: base.network,
        latency = overrides.latencyHex?.toColorOrNull() ?: base.latency
    )
}

private fun themeMetricColors(): ServerMetricColors {
    return ServerMetricColors(
        cpu = DeckColors.MetricCpu,
        memory = DeckColors.MetricMemory,
        disk = DeckColors.MetricDisk,
        network = DeckColors.MetricNetwork,
        latency = DeckColors.MetricLatency
    )
}

private fun String.toColorOrNull(): Color? {
    val clean = trim().removePrefix("#")
    if (clean.length != 6 || clean.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
    return Color((0xff000000L or clean.toLong(16)).toInt())
}
