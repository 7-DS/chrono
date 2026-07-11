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

data class ServerCpuUsageColors(
    val user: Color,
    val system: Color,
    val nice: Color,
    val ioWait: Color,
    val steal: Color
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
            network = DeckColors.Purple,
            latency = DeckColors.Red
        )
        ServerMetricColorPreset.Calm -> ServerMetricColors(
            cpu = Color(0xff4f7fa6),
            memory = Color(0xff5e8a63),
            disk = Color(0xffa1764a),
            network = Color(0xff7a669e),
            latency = Color(0xffa45f5f)
        )
        ServerMetricColorPreset.Graphite -> ServerMetricColors(
            cpu = Color(0xff5f86ad),
            memory = Color(0xff6f9277),
            disk = Color(0xffa88455),
            network = Color(0xff826fa8),
            latency = Color(0xffaa6670)
        )
        ServerMetricColorPreset.HighContrast -> ServerMetricColors(
            cpu = Color(0xff2f6fbb),
            memory = Color(0xff4d8f61),
            disk = Color(0xffb87534),
            network = Color(0xff7958b7),
            latency = Color(0xffb33a4a)
        )
        ServerMetricColorPreset.Ocean -> ServerMetricColors(
            cpu = Color(0xff2f72a2),
            memory = Color(0xff3f8b72),
            disk = Color(0xffa77a45),
            network = Color(0xff6e63a8),
            latency = Color(0xffad5f68)
        )
        ServerMetricColorPreset.Forest -> ServerMetricColors(
            cpu = Color(0xff4f78a2),
            memory = Color(0xff4f845c),
            disk = Color(0xff9a7a45),
            network = Color(0xff7562a0),
            latency = Color(0xffa15f4f)
        )
        ServerMetricColorPreset.Ember -> ServerMetricColors(
            cpu = Color(0xffaa664d),
            memory = Color(0xff768a62),
            disk = Color(0xffa98245),
            network = Color(0xff7d8d9a),
            latency = Color(0xffa0526b)
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
            network = Color(0xffb48ead),
            latency = Color(0xffbf616a)
        )
        ServerMetricColorPreset.Solar -> ServerMetricColors(
            cpu = Color(0xff268bd2),
            memory = Color(0xff859900),
            disk = Color(0xffb58900),
            network = Color(0xff2aa198),
            latency = Color(0xffdc322f)
        )
        ServerMetricColorPreset.Circuit -> ServerMetricColors(
            cpu = Color(0xff4d7fb0),
            memory = Color(0xff6e945f),
            disk = Color(0xffa18445),
            network = Color(0xff6c75a8),
            latency = Color(0xffa35a64)
        )
        ServerMetricColorPreset.Harvest -> ServerMetricColors(
            cpu = Color(0xff527fa3),
            memory = Color(0xff78804d),
            disk = Color(0xffa18445),
            network = Color(0xff7c63a0),
            latency = Color(0xffa45f55)
        )
        ServerMetricColorPreset.Lagoon -> ServerMetricColors(
            cpu = Color(0xff4b83a1),
            memory = Color(0xff4f8f7a),
            disk = Color(0xff8370a0),
            network = Color(0xff4f8fa3),
            latency = Color(0xffaa6570)
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

fun cpuUsageColorsFor(colors: ServerMetricColors): ServerCpuUsageColors =
    ServerCpuUsageColors(
        user = colors.cpu,
        system = colors.network,
        nice = colors.memory,
        ioWait = colors.latency,
        steal = colors.disk
    )

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
