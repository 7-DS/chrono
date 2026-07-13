package com.chrono.ssh.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.chrono.ssh.R

enum class DeckThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark")
}

data class DeckPalette(
    val id: String,
    val name: String,
    val dark: Boolean,
    val background: Color,
    val backgroundAlt: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceMuted: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val tertiaryText: Color,
    val divider: Color,
    val cardStroke: Color,
    val cyan: Color,
    val cyanSoft: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val purple: Color,
    val purpleSoft: Color,
    val terminal: Color,
    val terminalPanel: Color,
    val terminalAccent: Color,
    val navSurface: Color,
    val brand: Color,
    val brandAlt: Color,
    val metricCpu: Color = brandAlt,
    val metricMemory: Color = green,
    val metricDisk: Color = yellow,
    val metricNetwork: Color = purple,
    val metricLatency: Color = metricMemory
)

data class DeckThemeFamily(
    val id: String,
    val name: String,
    val description: String,
    val light: DeckPalette,
    val dark: DeckPalette,
    val modes: Set<DeckThemeMode> = setOf(DeckThemeMode.Light, DeckThemeMode.Dark)
)

private data class MetricAccentSet(
    val cpu: Color,
    val memory: Color,
    val disk: Color,
    val network: Color,
    val latency: Color = memory
)

private val professionalLightMetrics = MetricAccentSet(
    cpu = Color(0xFF005BBB),
    memory = Color(0xFF148A43),
    disk = Color(0xFF9A6400),
    network = Color(0xFF8A3FFC),
    latency = Color(0xFFC2185B)
)

private val professionalDarkMetrics = MetricAccentSet(
    cpu = Color(0xFF54B4FF),
    memory = Color(0xFF7BD88F),
    disk = Color(0xFFF2A93B),
    network = Color(0xFFC084FC),
    latency = Color(0xFFFF5C70)
)

private data class SurfaceLines(
    val divider: Color,
    val cardStroke: Color
)

private fun DeckPalette.polished(): DeckPalette {
    val lines = surfaceLines(surface, primaryText, dark)
    val metrics = if (dark) professionalDarkMetrics else professionalLightMetrics
    return copy(
        divider = lines.divider,
        cardStroke = lines.cardStroke,
        cyan = visibleAccent(cyan, primaryText, surface, surfaceMuted),
        brandAlt = visibleAccent(brandAlt, primaryText, surface, surfaceMuted),
        metricCpu = metrics.cpu,
        metricMemory = metrics.memory,
        metricDisk = metrics.disk,
        metricNetwork = metrics.network,
        metricLatency = metrics.latency
    )
}

private fun surfaceLines(surface: Color, text: Color, dark: Boolean): SurfaceLines {
    val mix = if (dark) 0.22f else 0.18f
    val strokeMix = if (dark) 0.34f else 0.28f
    return SurfaceLines(
        divider = surface.blendToward(text, mix),
        cardStroke = surface.blendToward(text, strokeMix)
    )
}

private fun visibleAccent(color: Color, text: Color, firstBackground: Color, secondBackground: Color): Color {
    var tuned = color
    repeat(3) {
        if (tuned.distanceTo(firstBackground) >= 0.30f && tuned.distanceTo(secondBackground) >= 0.30f) return tuned
        tuned = if (firstBackground.relativeLuminance() < 0.42f || secondBackground.relativeLuminance() < 0.42f) {
            tuned.blendToward(Color.White, 0.22f)
        } else {
            tuned.blendToward(text, 0.20f)
        }
    }
    return tuned
}

private fun Color.blendToward(target: Color, amount: Float): Color {
    val clean = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (target.red - red) * clean,
        green = green + (target.green - green) * clean,
        blue = blue + (target.blue - blue) * clean,
        alpha = alpha + (target.alpha - alpha) * clean
    )
}

private fun Color.distanceTo(other: Color): Float {
    val redDelta = red - other.red
    val greenDelta = green - other.green
    val blueDelta = blue - other.blue
    return kotlin.math.sqrt(redDelta * redDelta + greenDelta * greenDelta + blueDelta * blueDelta)
}

private fun Color.relativeLuminance(): Float {
    fun channel(value: Float): Float =
        if (value <= 0.03928f) value / 12.92f else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    return 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)
}

object DeckThemeCatalog {
    const val DEFAULT_FAMILY_ID = "graphite"

    private val auroraLight = DeckPalette(
        id = "aurora-light",
        name = "Aurora Light",
        dark = false,
        background = Color(0xFFF3F2F8),
        backgroundAlt = Color(0xFFEDECF5),
        surface = Color.White,
        surfaceRaised = Color(0xFFFBFBFD),
        surfaceMuted = Color(0xFFEDECF2),
        primaryText = Color(0xFF050505),
        secondaryText = Color(0xFF85858D),
        tertiaryText = Color(0xFFA7A6AE),
        divider = Color(0xFFD8D8DE),
        cardStroke = Color(0xFFE9E8EF),
        cyan = Color(0xFF21C7E8),
        cyanSoft = Color(0xFFBDECF6),
        green = Color(0xFF20D56B),
        red = Color(0xFFFF4056),
        orange = Color(0xFFFF9A32),
        yellow = Color(0xFFF8C533),
        purple = Color(0xFF7567D9),
        purpleSoft = Color(0xFFE5E0FF),
        terminal = Color(0xFF101218),
        terminalPanel = Color(0xFF080B10),
        terminalAccent = Color(0xFF8BD5FF),
        navSurface = Color.White,
        brand = Color(0xFF12141C),
        brandAlt = Color(0xFF21C7E8),
        metricCpu = Color(0xFF1987D2),
        metricMemory = Color(0xFFD946A6),
        metricDisk = Color(0xFFB48A1F),
        metricNetwork = Color(0xFF5B6EE1),
        metricLatency = Color(0xFF168A72)
    )

    private val auroraDark = DeckPalette(
        id = "aurora-dark",
        name = "Aurora Dark",
        dark = true,
        background = Color(0xFF101219),
        backgroundAlt = Color(0xFF171A22),
        surface = Color(0xFF1B1F29),
        surfaceRaised = Color(0xFF242A36),
        surfaceMuted = Color(0xFF151922),
        primaryText = Color(0xFFF7F8FC),
        secondaryText = Color(0xFFABB2C2),
        tertiaryText = Color(0xFF777F91),
        divider = Color(0xFF343A48),
        cardStroke = Color(0xFF2E3543),
        cyan = Color(0xFF36D9F2),
        cyanSoft = Color(0xFF173D48),
        green = Color(0xFF33E17A),
        red = Color(0xFFFF5B70),
        orange = Color(0xFFFFA94D),
        yellow = Color(0xFFF6CF57),
        purple = Color(0xFF9B8DFF),
        purpleSoft = Color(0xFF2D284E),
        terminal = Color(0xFF080B10),
        terminalPanel = Color(0xFF05070B),
        terminalAccent = Color(0xFF8BD5FF),
        navSurface = Color(0xFF181C26),
        brand = Color(0xFFF7F8FC),
        brandAlt = Color(0xFF36D9F2),
        metricCpu = Color(0xFF36D9F2),
        metricMemory = Color(0xFFFF6EC7),
        metricDisk = Color(0xFFF6CF57),
        metricNetwork = Color(0xFF9B8DFF),
        metricLatency = Color(0xFF55D6BE)
    )

    private val graphiteLight = auroraLight.copy(
        id = "graphite-light",
        name = "Graphite Light",
        background = Color(0xFFF4F5F2),
        backgroundAlt = Color(0xFFE9ECE8),
        cyan = Color(0xFF25B8C7),
        green = Color(0xFF48B36D),
        orange = Color(0xFFE9993F),
        purple = Color(0xFF626B7C),
        purpleSoft = Color(0xFFE1E4E9),
        brandAlt = Color(0xFF25B8C7),
        metricCpu = Color(0xFF256D8C),
        metricMemory = Color(0xFF3F7A45),
        metricDisk = Color(0xFF9A642C),
        metricNetwork = Color(0xFF7057A6),
        metricLatency = Color(0xFFB22828)
    )

    private val graphiteDark = auroraDark.copy(
        id = "graphite-dark",
        name = "Graphite Dark",
        background = Color(0xFF0E1114),
        backgroundAlt = Color(0xFF151A1D),
        surface = Color(0xFF1A2024),
        surfaceRaised = Color(0xFF222A2F),
        surfaceMuted = Color(0xFF11171A),
        cyan = Color(0xFF4FC3D0),
        green = Color(0xFF7BD88F),
        orange = Color(0xFFECA34C),
        purple = Color(0xFF9AA5B3),
        purpleSoft = Color(0xFF29313A),
        brandAlt = Color(0xFF4FC3D0),
        metricCpu = Color(0xFF78C6E8),
        metricMemory = Color(0xFF8ED176),
        metricDisk = Color(0xFFE4AC5B),
        metricNetwork = Color(0xFFB59AE8),
        metricLatency = Color(0xFF68C8B4)
    )

    private val emberLight = auroraLight.copy(
        id = "ember-light",
        name = "Ember Light",
        background = Color(0xFFF8F3F0),
        backgroundAlt = Color(0xFFF0E8E1),
        cyan = Color(0xFF13BFD2),
        cyanSoft = Color(0xFFC1EEF2),
        green = Color(0xFF38C978),
        orange = Color(0xFFFF7C3D),
        yellow = Color(0xFFF0C038),
        purple = Color(0xFF7F63D6),
        brandAlt = Color(0xFFFF7C3D),
        metricCpu = Color(0xFF256D8C),
        metricMemory = Color(0xFF3F7A45),
        metricDisk = Color(0xFF9A642C),
        metricNetwork = Color(0xFF7057A6),
        metricLatency = Color(0xFFB22828)
    )

    private val emberDark = auroraDark.copy(
        id = "ember-dark",
        name = "Ember Dark",
        background = Color(0xFF15100F),
        backgroundAlt = Color(0xFF211816),
        surface = Color(0xFF241B19),
        surfaceRaised = Color(0xFF302320),
        surfaceMuted = Color(0xFF1A1211),
        cyan = Color(0xFF35D5E4),
        cyanSoft = Color(0xFF143D41),
        green = Color(0xFF57E28D),
        orange = Color(0xFFFF8B4D),
        yellow = Color(0xFFFFD35A),
        purple = Color(0xFFA993FF),
        purpleSoft = Color(0xFF35274B),
        brandAlt = Color(0xFFFF8B4D),
        metricCpu = Color(0xFF78C6E8),
        metricMemory = Color(0xFF8ED176),
        metricDisk = Color(0xFFE4AC5B),
        metricNetwork = Color(0xFFB59AE8),
        metricLatency = Color(0xFF68C8B4)
    )

    private val catppuccinLight = auroraLight.copy(
        id = "catppuccin-light",
        name = "Catppuccin Latte",
        background = Color(0xFFEFF1F5),
        backgroundAlt = Color(0xFFE6E9EF),
        surface = Color(0xFFFFFFFF),
        surfaceMuted = Color(0xFFE6E9EF),
        primaryText = Color(0xFF4C4F69),
        secondaryText = Color(0xFF7C7F93),
        cyan = Color(0xFF04A5E5),
        green = Color(0xFF40A02B),
        red = Color(0xFFD20F39),
        orange = Color(0xFFFE640B),
        yellow = Color(0xFFDF8E1D),
        purple = Color(0xFF8839EF),
        purpleSoft = Color(0xFFE6E0F8),
        terminal = Color(0xFF303446),
        terminalPanel = Color(0xFF232634),
        terminalAccent = Color(0xFF8CAAEE),
        brandAlt = Color(0xFF1E66F5),
        metricCpu = Color(0xFF1E66F5),
        metricMemory = Color(0xFF40A02B),
        metricDisk = Color(0xFFFE640B),
        metricNetwork = Color(0xFF8839EF),
        metricLatency = Color(0xFF179299)
    )

    private val catppuccinDark = auroraDark.copy(
        id = "catppuccin-dark",
        name = "Catppuccin Mocha",
        background = Color(0xFF1E1E2E),
        backgroundAlt = Color(0xFF181825),
        surface = Color(0xFF313244),
        surfaceRaised = Color(0xFF3B3C51),
        surfaceMuted = Color(0xFF292A3A),
        primaryText = Color(0xFFCDD6F4),
        secondaryText = Color(0xFFA6ADC8),
        cyan = Color(0xFF89DCEB),
        green = Color(0xFFA6E3A1),
        red = Color(0xFFF38BA8),
        orange = Color(0xFFFAB387),
        yellow = Color(0xFFF9E2AF),
        purple = Color(0xFFCBA6F7),
        purpleSoft = Color(0xFF45385F),
        terminal = Color(0xFF11111B),
        terminalPanel = Color(0xFF181825),
        terminalAccent = Color(0xFF89B4FA),
        navSurface = Color(0xFF313244),
        brandAlt = Color(0xFF89B4FA),
        metricCpu = Color(0xFF89B4FA),
        metricMemory = Color(0xFFA6E3A1),
        metricDisk = Color(0xFFFAB387),
        metricNetwork = Color(0xFFCBA6F7),
        metricLatency = Color(0xFF94E2D5)
    )

    private val rosePineLight = auroraLight.copy(
        id = "rosepine-light",
        name = "Rosé Pine Dawn",
        background = Color(0xFFFAF4ED),
        backgroundAlt = Color(0xFFF2E9E1),
        surface = Color(0xFFFFFAF3),
        surfaceMuted = Color(0xFFF2E9E1),
        primaryText = Color(0xFF575279),
        secondaryText = Color(0xFF797593),
        cyan = Color(0xFF56949F),
        green = Color(0xFF286983),
        red = Color(0xFFB4637A),
        orange = Color(0xFFEA9D34),
        yellow = Color(0xFFD7827E),
        purple = Color(0xFF907AA9),
        purpleSoft = Color(0xFFE8E0EA),
        terminal = Color(0xFF232136),
        terminalPanel = Color(0xFF2A273F),
        terminalAccent = Color(0xFFC4A7E7),
        brandAlt = Color(0xFF56949F),
        metricCpu = Color(0xFF286983),
        metricMemory = Color(0xFF56949F),
        metricDisk = Color(0xFFEA9D34),
        metricNetwork = Color(0xFF907AA9),
        metricLatency = Color(0xFF3F7A45)
    )

    private val rosePineDark = auroraDark.copy(
        id = "rosepine-dark",
        name = "Rosé Pine",
        background = Color(0xFF191724),
        backgroundAlt = Color(0xFF1F1D2E),
        surface = Color(0xFF26233A),
        surfaceRaised = Color(0xFF2D2943),
        surfaceMuted = Color(0xFF211F31),
        primaryText = Color(0xFFE0DEF4),
        secondaryText = Color(0xFF908CAA),
        cyan = Color(0xFF9CCFD8),
        green = Color(0xFF31748F),
        red = Color(0xFFEB6F92),
        orange = Color(0xFFF6C177),
        yellow = Color(0xFFEBBCBA),
        purple = Color(0xFFC4A7E7),
        purpleSoft = Color(0xFF403454),
        terminal = Color(0xFF191724),
        terminalPanel = Color(0xFF1F1D2E),
        terminalAccent = Color(0xFFC4A7E7),
        navSurface = Color(0xFF26233A),
        brandAlt = Color(0xFF9CCFD8),
        metricCpu = Color(0xFF3E8FB0),
        metricMemory = Color(0xFF9CCFD8),
        metricDisk = Color(0xFFF6C177),
        metricNetwork = Color(0xFFC4A7E7),
        metricLatency = Color(0xFF908CAA)
    )

    private val monochromeLight = auroraLight.copy(
        id = "monochrome-light",
        name = "Monochrome Light",
        background = Color(0xFFFFFFFF),
        backgroundAlt = Color(0xFFF2F2F2),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFFAFAFA),
        surfaceMuted = Color(0xFFEDEDED),
        primaryText = Color(0xFF000000),
        secondaryText = Color(0xFF525252),
        tertiaryText = Color(0xFF7A7A7A),
        divider = Color(0xFFD4D4D4),
        cardStroke = Color(0xFFBDBDBD),
        cyan = Color(0xFF111111),
        cyanSoft = Color(0xFFE6E6E6),
        green = Color(0xFF222222),
        red = Color(0xFF000000),
        orange = Color(0xFF3A3A3A),
        yellow = Color(0xFF5C5C5C),
        purple = Color(0xFF1A1A1A),
        purpleSoft = Color(0xFFE0E0E0),
        terminal = Color(0xFFFFFFFF),
        terminalPanel = Color(0xFFF7F7F7),
        terminalAccent = Color(0xFF000000),
        navSurface = Color(0xFFFFFFFF),
        brand = Color(0xFF000000),
        brandAlt = Color(0xFF000000),
        metricCpu = Color(0xFF005BBB),
        metricMemory = Color(0xFF148A43),
        metricDisk = Color(0xFFC46A00),
        metricNetwork = Color(0xFF6F42C1),
        metricLatency = Color(0xFFB22828)
    )

    private val monochromeDark = auroraDark.copy(
        id = "monochrome-dark",
        name = "Monochrome Dark",
        background = Color(0xFF000000),
        backgroundAlt = Color(0xFF090909),
        surface = Color(0xFF0F0F0F),
        surfaceRaised = Color(0xFF171717),
        surfaceMuted = Color(0xFF050505),
        primaryText = Color(0xFFFFFFFF),
        secondaryText = Color(0xFFBDBDBD),
        tertiaryText = Color(0xFF8F8F8F),
        divider = Color(0xFF2A2A2A),
        cardStroke = Color(0xFF363636),
        cyan = Color(0xFFFFFFFF),
        cyanSoft = Color(0xFF2A2A2A),
        green = Color(0xFFEDEDED),
        red = Color(0xFFFFFFFF),
        orange = Color(0xFFD8D8D8),
        yellow = Color(0xFFB8B8B8),
        purple = Color(0xFFFFFFFF),
        purpleSoft = Color(0xFF242424),
        terminal = Color(0xFF000000),
        terminalPanel = Color(0xFF080808),
        terminalAccent = Color(0xFFFFFFFF),
        navSurface = Color(0xFF0A0A0A),
        brand = Color(0xFFFFFFFF),
        brandAlt = Color(0xFFFFFFFF),
        metricCpu = Color(0xFF72C8F0),
        metricMemory = Color(0xFF8ED176),
        metricDisk = Color(0xFFE4AC5B),
        metricNetwork = Color(0xFFB59AE8),
        metricLatency = Color(0xFF68C8B4)
    )

    private val comicLight = monochromeLight.copy(
        id = "comic-light",
        name = "Comic Ink",
        background = Color(0xFFFFFFFF),
        backgroundAlt = Color(0xFFF8F8F8),
        surface = Color(0xFFFFFFFF),
        surfaceRaised = Color(0xFFFFFFFF),
        surfaceMuted = Color(0xFFF0F0F0),
        primaryText = Color(0xFF000000),
        secondaryText = Color(0xFF222222),
        tertiaryText = Color(0xFF555555),
        divider = Color(0xFF000000),
        cardStroke = Color(0xFF000000),
        cyan = Color(0xFF000000),
        cyanSoft = Color(0xFFEDEDED),
        green = Color(0xFF111111),
        red = Color(0xFF000000),
        orange = Color(0xFF111111),
        yellow = Color(0xFF333333),
        purple = Color(0xFF000000),
        purpleSoft = Color(0xFFE8E8E8),
        terminal = Color(0xFFFFFFFF),
        terminalPanel = Color(0xFFFFFFFF),
        terminalAccent = Color(0xFF000000),
        navSurface = Color(0xFFFFFFFF),
        brand = Color(0xFF000000),
        brandAlt = Color(0xFF000000),
        metricCpu = Color(0xFF005BBB),
        metricMemory = Color(0xFF148A43),
        metricDisk = Color(0xFFC46A00),
        metricNetwork = Color(0xFF6F42C1),
        metricLatency = Color(0xFFB22828)
    )

    private fun appThemeFamily(
        id: String,
        name: String,
        description: String,
        lightBackground: Color,
        lightBackgroundAlt: Color,
        lightSurface: Color,
        lightSurfaceRaised: Color,
        lightSurfaceMuted: Color,
        lightPrimaryText: Color,
        lightSecondaryText: Color,
        lightAccent: Color,
        lightAccentSoft: Color,
        lightRed: Color,
        darkBackground: Color,
        darkBackgroundAlt: Color,
        darkSurface: Color,
        darkSurfaceRaised: Color,
        darkSurfaceMuted: Color,
        darkPrimaryText: Color,
        darkSecondaryText: Color,
        darkAccent: Color,
        darkAccentSoft: Color,
        darkRed: Color,
        lightMetrics: MetricAccentSet,
        darkMetrics: MetricAccentSet
    ): DeckThemeFamily {
        val lightLines = surfaceLines(lightSurface, lightPrimaryText, dark = false)
        val darkLines = surfaceLines(darkSurface, darkPrimaryText, dark = true)

        return DeckThemeFamily(
            id = id,
            name = name,
            description = description,
            light = auroraLight.copy(
            id = "$id-light",
            name = "$name Light",
            background = lightBackground,
            backgroundAlt = lightBackgroundAlt,
            surface = lightSurface,
            surfaceRaised = lightSurfaceRaised,
            surfaceMuted = lightSurfaceMuted,
            primaryText = lightPrimaryText,
            secondaryText = lightSecondaryText,
            tertiaryText = lightSecondaryText.copy(alpha = 0.72f),
            divider = lightLines.divider,
            cardStroke = lightLines.cardStroke,
            cyan = visibleAccent(lightAccent, lightPrimaryText, lightSurface, lightSurfaceMuted),
            cyanSoft = lightAccentSoft,
            green = lightMetrics.memory,
            red = lightRed,
            orange = lightMetrics.disk,
            yellow = lightMetrics.latency,
            purple = lightMetrics.network,
            purpleSoft = lightSurfaceMuted.blendToward(lightMetrics.network, 0.16f),
            navSurface = lightSurface,
            brand = lightPrimaryText,
            brandAlt = visibleAccent(lightAccent, lightPrimaryText, lightSurface, lightSurfaceMuted),
            metricCpu = professionalLightMetrics.cpu,
            metricMemory = professionalLightMetrics.memory,
            metricDisk = professionalLightMetrics.disk,
            metricNetwork = professionalLightMetrics.network,
            metricLatency = professionalLightMetrics.latency
        ),
        dark = auroraDark.copy(
            id = "$id-dark",
            name = "$name Dark",
            background = darkBackground,
            backgroundAlt = darkBackgroundAlt,
            surface = darkSurface,
            surfaceRaised = darkSurfaceRaised,
            surfaceMuted = darkSurfaceMuted,
            primaryText = darkPrimaryText,
            secondaryText = darkSecondaryText,
            tertiaryText = darkSecondaryText.copy(alpha = 0.72f),
            divider = darkLines.divider,
            cardStroke = darkLines.cardStroke,
            cyan = visibleAccent(darkAccent, darkPrimaryText, darkSurface, darkSurfaceMuted),
            cyanSoft = darkAccentSoft,
            green = darkMetrics.memory,
            red = darkRed,
            orange = darkMetrics.disk,
            yellow = darkMetrics.latency,
            purple = darkMetrics.network,
            purpleSoft = darkSurfaceMuted.blendToward(darkMetrics.network, 0.18f),
            navSurface = darkSurface,
            brand = darkPrimaryText,
            brandAlt = visibleAccent(darkAccent, darkPrimaryText, darkSurface, darkSurfaceMuted),
            metricCpu = professionalDarkMetrics.cpu,
            metricMemory = professionalDarkMetrics.memory,
            metricDisk = professionalDarkMetrics.disk,
            metricNetwork = professionalDarkMetrics.network,
            metricLatency = professionalDarkMetrics.latency
        )
        )
    }

    private val appThemeFamilies = listOf(
        appThemeFamily(
            id = "slate",
            name = "Slate",
            description = "Cool neutral panels with blue status accents.",
            lightBackground = Color(0xFFF4F7FA),
            lightBackgroundAlt = Color(0xFFE6ECF2),
            lightSurface = Color(0xFFFCFEFF),
            lightSurfaceRaised = Color(0xFFFAFCFE),
            lightSurfaceMuted = Color(0xFFEAF0F5),
            lightPrimaryText = Color(0xFF17202A),
            lightSecondaryText = Color(0xFF667789),
            lightAccent = Color(0xFF2D7FBF),
            lightAccentSoft = Color(0xFFD7E9F6),
            lightRed = Color(0xFFC9535E),
            darkBackground = Color(0xFF10161C),
            darkBackgroundAlt = Color(0xFF18212A),
            darkSurface = Color(0xFF1D2731),
            darkSurfaceRaised = Color(0xFF263240),
            darkSurfaceMuted = Color(0xFF151E27),
            darkPrimaryText = Color(0xFFE8EEF4),
            darkSecondaryText = Color(0xFFA8B6C5),
            darkAccent = Color(0xFF6CB4E4),
            darkAccentSoft = Color(0xFF1C3B50),
            darkRed = Color(0xFFE17884),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF4D7672)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE8B872), network = Color(0xFFCEADE3), latency = Color(0xFF90B9B8))
        ),
        appThemeFamily(
            id = "moss",
            name = "Moss",
            description = "Soft green-grey workspace for calm operational review.",
            lightBackground = Color(0xFFF3F6EF),
            lightBackgroundAlt = Color(0xFFE5EBDE),
            lightSurface = Color(0xFFFEFFF9),
            lightSurfaceRaised = Color(0xFFFAFCF5),
            lightSurfaceMuted = Color(0xFFE9EEE3),
            lightPrimaryText = Color(0xFF1D261C),
            lightSecondaryText = Color(0xFF697462),
            lightAccent = Color(0xFF4E8D67),
            lightAccentSoft = Color(0xFFDCEBDF),
            lightRed = Color(0xFFC86262),
            darkBackground = Color(0xFF11170F),
            darkBackgroundAlt = Color(0xFF1A2217),
            darkSurface = Color(0xFF202A1C),
            darkSurfaceRaised = Color(0xFF293424),
            darkSurfaceMuted = Color(0xFF171F14),
            darkPrimaryText = Color(0xFFE9F0E2),
            darkSecondaryText = Color(0xFFB2BEA7),
            darkAccent = Color(0xFF8BC79A),
            darkAccentSoft = Color(0xFF263F2B),
            darkRed = Color(0xFFE48686),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF257B6C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF89D180), disk = Color(0xFFD0C06E), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "harbor",
            name = "Harbor",
            description = "Blue-green navigation tones with crisp surfaces.",
            lightBackground = Color(0xFFF1F7F8),
            lightBackgroundAlt = Color(0xFFE0ECEF),
            lightSurface = Color(0xFFFAFFFF),
            lightSurfaceRaised = Color(0xFFF8FCFD),
            lightSurfaceMuted = Color(0xFFE5F0F2),
            lightPrimaryText = Color(0xFF142428),
            lightSecondaryText = Color(0xFF60797F),
            lightAccent = Color(0xFF24899A),
            lightAccentSoft = Color(0xFFD1EBEF),
            lightRed = Color(0xFFC75867),
            darkBackground = Color(0xFF0E171A),
            darkBackgroundAlt = Color(0xFF162327),
            darkSurface = Color(0xFF1B2B30),
            darkSurfaceRaised = Color(0xFF24373E),
            darkSurfaceMuted = Color(0xFF132024),
            darkPrimaryText = Color(0xFFE5F1F3),
            darkSecondaryText = Color(0xFFA5BCC1),
            darkAccent = Color(0xFF73C7D2),
            darkAccentSoft = Color(0xFF1D4148),
            darkRed = Color(0xFFE37C8B),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF1F7F8B), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7057A6), latency = Color(0xFFB22828)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF70C5B0))
        ),
        appThemeFamily(
            id = "steel",
            name = "Steel",
            description = "Industrial greys with restrained teal guidance.",
            lightBackground = Color(0xFFF5F6F6),
            lightBackgroundAlt = Color(0xFFE7EAEA),
            lightSurface = Color(0xFFFDFEFE),
            lightSurfaceRaised = Color(0xFFFBFCFC),
            lightSurfaceMuted = Color(0xFFECEFEF),
            lightPrimaryText = Color(0xFF1D2326),
            lightSecondaryText = Color(0xFF6D777C),
            lightAccent = Color(0xFF3C8C99),
            lightAccentSoft = Color(0xFFD9E9EC),
            lightRed = Color(0xFFC65B64),
            darkBackground = Color(0xFF111416),
            darkBackgroundAlt = Color(0xFF1A2023),
            darkSurface = Color(0xFF20272B),
            darkSurfaceRaised = Color(0xFF2A3338),
            darkSurfaceMuted = Color(0xFF171D20),
            darkPrimaryText = Color(0xFFE9EDEE),
            darkSecondaryText = Color(0xFFADB7BB),
            darkAccent = Color(0xFF7BC4CF),
            darkAccentSoft = Color(0xFF244147),
            darkRed = Color(0xFFE17F88),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF517B75)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFFA0C8C2))
        ),
        appThemeFamily(
            id = "plum",
            name = "Plum",
            description = "Muted violet controls balanced by neutral panels.",
            lightBackground = Color(0xFFF7F3F8),
            lightBackgroundAlt = Color(0xFFECE5EF),
            lightSurface = Color(0xFFFFFCFF),
            lightSurfaceRaised = Color(0xFFFCF8FD),
            lightSurfaceMuted = Color(0xFFF0E9F2),
            lightPrimaryText = Color(0xFF281F2B),
            lightSecondaryText = Color(0xFF77677D),
            lightAccent = Color(0xFF8363A5),
            lightAccentSoft = Color(0xFFE9DEF1),
            lightRed = Color(0xFFC65D70),
            darkBackground = Color(0xFF171119),
            darkBackgroundAlt = Color(0xFF221A25),
            darkSurface = Color(0xFF2A2130),
            darkSurfaceRaised = Color(0xFF352A3C),
            darkSurfaceMuted = Color(0xFF1E1622),
            darkPrimaryText = Color(0xFFF0E8F2),
            darkSecondaryText = Color(0xFFC0B0C7),
            darkAccent = Color(0xFFC5A0DB),
            darkAccentSoft = Color(0xFF402D4B),
            darkRed = Color(0xFFE48699),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF646E5D), disk = Color(0xFF9E682D), network = Color(0xFF7057A6), latency = Color(0xFFB22828)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF95B1B9))
        ),
        appThemeFamily(
            id = "olive",
            name = "Olive",
            description = "Earthy olive accents with compact contrast.",
            lightBackground = Color(0xFFF6F6EF),
            lightBackgroundAlt = Color(0xFFEAEADB),
            lightSurface = Color(0xFFFFFFFA),
            lightSurfaceRaised = Color(0xFFFCFCF4),
            lightSurfaceMuted = Color(0xFFEDEDE0),
            lightPrimaryText = Color(0xFF252416),
            lightSecondaryText = Color(0xFF74725C),
            lightAccent = Color(0xFF7F8E45),
            lightAccentSoft = Color(0xFFE6EBCF),
            lightRed = Color(0xFFC96360),
            darkBackground = Color(0xFF15150E),
            darkBackgroundAlt = Color(0xFF202014),
            darkSurface = Color(0xFF282819),
            darkSurfaceRaised = Color(0xFF333222),
            darkSurfaceMuted = Color(0xFF1B1B12),
            darkPrimaryText = Color(0xFFEFEEDC),
            darkSecondaryText = Color(0xFFBEBB9E),
            darkAccent = Color(0xFFB8C36C),
            darkAccentSoft = Color(0xFF3A3F24),
            darkRed = Color(0xFFE58683),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF5D7944), disk = Color(0xFF944C37), network = Color(0xFF7B6098), latency = Color(0xFF1F6A60)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFD6B35D), network = Color(0xFFCEADE3), latency = Color(0xFF6EC8AE))
        ),
        appThemeFamily(
            id = "copper",
            name = "Copper",
            description = "Warm metal accents for incident triage.",
            lightBackground = Color(0xFFF8F3EE),
            lightBackgroundAlt = Color(0xFFEFE4DA),
            lightSurface = Color(0xFFFFFCF8),
            lightSurfaceRaised = Color(0xFFFCF7F2),
            lightSurfaceMuted = Color(0xFFF1E8DE),
            lightPrimaryText = Color(0xFF2B211A),
            lightSecondaryText = Color(0xFF7B6B5E),
            lightAccent = Color(0xFFB56F43),
            lightAccentSoft = Color(0xFFF1DFD2),
            lightRed = Color(0xFFC95E5F),
            darkBackground = Color(0xFF18120E),
            darkBackgroundAlt = Color(0xFF251B15),
            darkSurface = Color(0xFF2D221B),
            darkSurfaceRaised = Color(0xFF392B22),
            darkSurfaceMuted = Color(0xFF201712),
            darkPrimaryText = Color(0xFFF1E8DF),
            darkSecondaryText = Color(0xFFC5B5A6),
            darkAccent = Color(0xFFE2A06D),
            darkAccentSoft = Color(0xFF4A3324),
            darkRed = Color(0xFFE58486),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF5D7443), disk = Color(0xFF944C37), network = Color(0xFF7B6098), latency = Color(0xFF1F6A60)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFF7FD0B4), network = Color(0xFFCEADE3), latency = Color(0xFFB0AB81))
        ),
        appThemeFamily(
            id = "fjord",
            name = "Fjord",
            description = "Cold blue-grey shell with clear cyan affordances.",
            lightBackground = Color(0xFFF1F6F9),
            lightBackgroundAlt = Color(0xFFE1EAF0),
            lightSurface = Color(0xFFFBFEFF),
            lightSurfaceRaised = Color(0xFFF8FBFD),
            lightSurfaceMuted = Color(0xFFE6EEF3),
            lightPrimaryText = Color(0xFF17252D),
            lightSecondaryText = Color(0xFF627784),
            lightAccent = Color(0xFF3A8DB7),
            lightAccentSoft = Color(0xFFD9EAF3),
            lightRed = Color(0xFFC85B67),
            darkBackground = Color(0xFF0E151A),
            darkBackgroundAlt = Color(0xFF17212A),
            darkSurface = Color(0xFF1D2A34),
            darkSurfaceRaised = Color(0xFF263542),
            darkSurfaceMuted = Color(0xFF131D24),
            darkPrimaryText = Color(0xFFE5EEF4),
            darkSecondaryText = Color(0xFFA5B8C5),
            darkAccent = Color(0xFF75BEE1),
            darkAccentSoft = Color(0xFF1E3D50),
            darkRed = Color(0xFFE27F8B),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF8A8FA3), network = Color(0xFF6C5486), latency = Color(0xFF4A838A)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFC7B8B1), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "sage",
            name = "Sage",
            description = "Low-saturation greens for long sessions.",
            lightBackground = Color(0xFFF5F7F1),
            lightBackgroundAlt = Color(0xFFE8ECE0),
            lightSurface = Color(0xFFFFFFFB),
            lightSurfaceRaised = Color(0xFFFBFCF7),
            lightSurfaceMuted = Color(0xFFEBEFE4),
            lightPrimaryText = Color(0xFF20261E),
            lightSecondaryText = Color(0xFF6E7868),
            lightAccent = Color(0xFF6F9472),
            lightAccentSoft = Color(0xFFE1EBDD),
            lightRed = Color(0xFFC96367),
            darkBackground = Color(0xFF121710),
            darkBackgroundAlt = Color(0xFF1B2318),
            darkSurface = Color(0xFF222B1F),
            darkSurfaceRaised = Color(0xFF2C3728),
            darkSurfaceMuted = Color(0xFF181F15),
            darkPrimaryText = Color(0xFFEAF0E4),
            darkSecondaryText = Color(0xFFB5C0AD),
            darkAccent = Color(0xFFA4C89C),
            darkAccentSoft = Color(0xFF31422D),
            darkRed = Color(0xFFE5868B),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF6D8B60), disk = Color(0xFF845626), network = Color(0xFF7057A6), latency = Color(0xFF3D766C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF8ACEB6))
        ),
        appThemeFamily(
            id = "ink",
            name = "Ink",
            description = "Paper-white light mode with inky dark contrast.",
            lightBackground = Color(0xFFF7F8F8),
            lightBackgroundAlt = Color(0xFFE8EBEC),
            lightSurface = Color(0xFFFAFCFC),
            lightSurfaceRaised = Color(0xFFFCFDFD),
            lightSurfaceMuted = Color(0xFFEEF1F1),
            lightPrimaryText = Color(0xFF14191C),
            lightSecondaryText = Color(0xFF657277),
            lightAccent = Color(0xFF2C7F8E),
            lightAccentSoft = Color(0xFFD7E9EC),
            lightRed = Color(0xFFC85A64),
            darkBackground = Color(0xFF0C1012),
            darkBackgroundAlt = Color(0xFF151B1E),
            darkSurface = Color(0xFF1A2226),
            darkSurfaceRaised = Color(0xFF242D32),
            darkSurfaceMuted = Color(0xFF11171A),
            darkPrimaryText = Color(0xFFE9EEF0),
            darkSecondaryText = Color(0xFFAAB5B9),
            darkAccent = Color(0xFF72C3D0),
            darkAccentSoft = Color(0xFF214147),
            darkRed = Color(0xFFE27E87),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF1D3945), memory = Color(0xFF2E8062), disk = Color(0xFF845626), network = Color(0xFF7057A6), latency = Color(0xFF4B5560)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF92BCB7))
        ),
        appThemeFamily(
            id = "basalt",
            name = "Basalt",
            description = "Dense neutral structure with cool green signals.",
            lightBackground = Color(0xFFF5F5F3),
            lightBackgroundAlt = Color(0xFFE7E8E4),
            lightSurface = Color(0xFFFCFBF8),
            lightSurfaceRaised = Color(0xFFFAFBF9),
            lightSurfaceMuted = Color(0xFFECEDEA),
            lightPrimaryText = Color(0xFF202321),
            lightSecondaryText = Color(0xFF6F7671),
            lightAccent = Color(0xFF4D8E7A),
            lightAccentSoft = Color(0xFFDCEAE5),
            lightRed = Color(0xFFC75E63),
            darkBackground = Color(0xFF101211),
            darkBackgroundAlt = Color(0xFF1A1D1B),
            darkSurface = Color(0xFF202522),
            darkSurfaceRaised = Color(0xFF2A302D),
            darkSurfaceMuted = Color(0xFF161A18),
            darkPrimaryText = Color(0xFFE9ECE9),
            darkSecondaryText = Color(0xFFB0B8B2),
            darkAccent = Color(0xFF87C8B1),
            darkAccentSoft = Color(0xFF294138),
            darkRed = Color(0xFFE28389),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF4D7F6D)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF6CC8B1))
        ),
        appThemeFamily(
            id = "storm",
            name = "Storm",
            description = "Weathered blue-grey surfaces with steady contrast.",
            lightBackground = Color(0xFFF3F5F7),
            lightBackgroundAlt = Color(0xFFE4E9EE),
            lightSurface = Color(0xFFFAFCFF),
            lightSurfaceRaised = Color(0xFFFAFCFD),
            lightSurfaceMuted = Color(0xFFE9EEF2),
            lightPrimaryText = Color(0xFF1A222A),
            lightSecondaryText = Color(0xFF677783),
            lightAccent = Color(0xFF547FA5),
            lightAccentSoft = Color(0xFFDDE8F1),
            lightRed = Color(0xFFC75B65),
            darkBackground = Color(0xFF10151B),
            darkBackgroundAlt = Color(0xFF191F28),
            darkSurface = Color(0xFF202834),
            darkSurfaceRaised = Color(0xFF2A3441),
            darkSurfaceMuted = Color(0xFF161C24),
            darkPrimaryText = Color(0xFFE8EEF4),
            darkSecondaryText = Color(0xFFAAB6C3),
            darkAccent = Color(0xFF94BCE0),
            darkAccentSoft = Color(0xFF2C3D4F),
            darkRed = Color(0xFFE17F89),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF4D7978)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFD5C879), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "wine",
            name = "Wine",
            description = "Muted red-violet cues on warm neutral panels.",
            lightBackground = Color(0xFFF8F2F4),
            lightBackgroundAlt = Color(0xFFEFE3E8),
            lightSurface = Color(0xFFFFFBFC),
            lightSurfaceRaised = Color(0xFFFCF7F9),
            lightSurfaceMuted = Color(0xFFF2E8EC),
            lightPrimaryText = Color(0xFF2B1E23),
            lightSecondaryText = Color(0xFF7C6670),
            lightAccent = Color(0xFFA85F78),
            lightAccentSoft = Color(0xFFF0DDE5),
            lightRed = Color(0xFFC45667),
            darkBackground = Color(0xFF171013),
            darkBackgroundAlt = Color(0xFF24191E),
            darkSurface = Color(0xFF2C2026),
            darkSurfaceRaised = Color(0xFF372932),
            darkSurfaceMuted = Color(0xFF1F1519),
            darkPrimaryText = Color(0xFFF1E6EA),
            darkSecondaryText = Color(0xFFC5B0BA),
            darkAccent = Color(0xFFD99AB0),
            darkAccentSoft = Color(0xFF492F3A),
            darkRed = Color(0xFFE37B8C),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF53737B)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "sandstone",
            name = "Sandstone",
            description = "Warm stone neutrals with restrained amber accents.",
            lightBackground = Color(0xFFF7F3EA),
            lightBackgroundAlt = Color(0xFFEDE5D6),
            lightSurface = Color(0xFFFFFCF5),
            lightSurfaceRaised = Color(0xFFFCF7EE),
            lightSurfaceMuted = Color(0xFFF1E8DA),
            lightPrimaryText = Color(0xFF2A2318),
            lightSecondaryText = Color(0xFF796F5D),
            lightAccent = Color(0xFFA9823D),
            lightAccentSoft = Color(0xFFEDE3CC),
            lightRed = Color(0xFFC76260),
            darkBackground = Color(0xFF16130E),
            darkBackgroundAlt = Color(0xFF221D15),
            darkSurface = Color(0xFF2A241A),
            darkSurfaceRaised = Color(0xFF352E22),
            darkSurfaceMuted = Color(0xFF1D1912),
            darkPrimaryText = Color(0xFFF0E9DC),
            darkSecondaryText = Color(0xFFC1B7A4),
            darkAccent = Color(0xFFD5B16D),
            darkAccentSoft = Color(0xFF443722),
            darkRed = Color(0xFFE48684),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF4D7842), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF1F6A60)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFCDB465), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "mint",
            name = "Mint",
            description = "Fresh green-cyan accenting without terminal tinting.",
            lightBackground = Color(0xFFF1F8F5),
            lightBackgroundAlt = Color(0xFFE0EEE9),
            lightSurface = Color(0xFFFCFFFE),
            lightSurfaceRaised = Color(0xFFF8FCFA),
            lightSurfaceMuted = Color(0xFFE5F1EC),
            lightPrimaryText = Color(0xFF162722),
            lightSecondaryText = Color(0xFF607A72),
            lightAccent = Color(0xFF3A9B83),
            lightAccentSoft = Color(0xFFD6ECE6),
            lightRed = Color(0xFFC75C66),
            darkBackground = Color(0xFF0F1714),
            darkBackgroundAlt = Color(0xFF17231F),
            darkSurface = Color(0xFF1D2B26),
            darkSurfaceRaised = Color(0xFF263730),
            darkSurfaceMuted = Color(0xFF14201C),
            darkPrimaryText = Color(0xFFE5F1ED),
            darkSecondaryText = Color(0xFFA6BDB5),
            darkAccent = Color(0xFF7CCDB8),
            darkAccentSoft = Color(0xFF22443B),
            darkRed = Color(0xFFE27F89),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7A76A5), latency = Color(0xFF44A06E)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8CD17A), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF6BC9B1))
        ),
        appThemeFamily(
            id = "denim",
            name = "Denim",
            description = "Practical blue controls over neutral work surfaces.",
            lightBackground = Color(0xFFF2F5F9),
            lightBackgroundAlt = Color(0xFFE2E8F0),
            lightSurface = Color(0xFFF9FBFF),
            lightSurfaceRaised = Color(0xFFF9FBFE),
            lightSurfaceMuted = Color(0xFFE7EDF4),
            lightPrimaryText = Color(0xFF172233),
            lightSecondaryText = Color(0xFF64748A),
            lightAccent = Color(0xFF4F77A8),
            lightAccentSoft = Color(0xFFDDE7F3),
            lightRed = Color(0xFFC75A65),
            darkBackground = Color(0xFF0F141C),
            darkBackgroundAlt = Color(0xFF181F2B),
            darkSurface = Color(0xFF1E2836),
            darkSurfaceRaised = Color(0xFF273343),
            darkSurfaceMuted = Color(0xFF151C27),
            darkPrimaryText = Color(0xFFE6EDF6),
            darkSecondaryText = Color(0xFFA8B6C8),
            darkAccent = Color(0xFF8DB7E3),
            darkAccentSoft = Color(0xFF2B3E54),
            darkRed = Color(0xFFE17E89),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF7B82A8), network = Color(0xFF604B8F), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFC4AFAE), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "berry",
            name = "Berry",
            description = "Subdued magenta accents for high-signal controls.",
            lightBackground = Color(0xFFF8F2F7),
            lightBackgroundAlt = Color(0xFFEFE3ED),
            lightSurface = Color(0xFFFFFBFE),
            lightSurfaceRaised = Color(0xFFFCF7FB),
            lightSurfaceMuted = Color(0xFFF2E8F0),
            lightPrimaryText = Color(0xFF2B1E2A),
            lightSecondaryText = Color(0xFF7B6678),
            lightAccent = Color(0xFFA85F93),
            lightAccentSoft = Color(0xFFF0DDEA),
            lightRed = Color(0xFFC65672),
            darkBackground = Color(0xFF171018),
            darkBackgroundAlt = Color(0xFF241923),
            darkSurface = Color(0xFF2C2030),
            darkSurfaceRaised = Color(0xFF37293C),
            darkSurfaceMuted = Color(0xFF1F151F),
            darkPrimaryText = Color(0xFFF1E6EF),
            darkSecondaryText = Color(0xFFC5B0C0),
            darkAccent = Color(0xFFD99BC8),
            darkAccentSoft = Color(0xFF492F45),
            darkRed = Color(0xFFE37B99),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF726C56), disk = Color(0xFF8D69A5), network = Color(0xFF604B8F), latency = Color(0xFF3D766C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF97ABAA))
        ),
        appThemeFamily(
            id = "spruce",
            name = "Spruce",
            description = "Deep evergreen cues with measured contrast.",
            lightBackground = Color(0xFFF2F7F3),
            lightBackgroundAlt = Color(0xFFE2EDE4),
            lightSurface = Color(0xFFFCFFFD),
            lightSurfaceRaised = Color(0xFFF8FCF9),
            lightSurfaceMuted = Color(0xFFE6F0E8),
            lightPrimaryText = Color(0xFF17271C),
            lightSecondaryText = Color(0xFF627867),
            lightAccent = Color(0xFF3D8F61),
            lightAccentSoft = Color(0xFFD8EBDD),
            lightRed = Color(0xFFC75F64),
            darkBackground = Color(0xFF0F1711),
            darkBackgroundAlt = Color(0xFF17231A),
            darkSurface = Color(0xFF1D2B21),
            darkSurfaceRaised = Color(0xFF26372A),
            darkSurfaceMuted = Color(0xFF142018),
            darkPrimaryText = Color(0xFFE5F1E8),
            darkSecondaryText = Color(0xFFA6BDAE),
            darkAccent = Color(0xFF78C990),
            darkAccentSoft = Color(0xFF244332),
            darkRed = Color(0xFFE28288),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF4D995B)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF89D07C), disk = Color(0xFFC7B968), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "aqua",
            name = "Aqua",
            description = "Clean cyan affordances over quiet neutral panels.",
            lightBackground = Color(0xFFF0F8F8),
            lightBackgroundAlt = Color(0xFFDFEEEE),
            lightSurface = Color(0xFFFCFFFF),
            lightSurfaceRaised = Color(0xFFF8FCFC),
            lightSurfaceMuted = Color(0xFFE4F1F1),
            lightPrimaryText = Color(0xFF142626),
            lightSecondaryText = Color(0xFF5F7A7B),
            lightAccent = Color(0xFF2D99A0),
            lightAccentSoft = Color(0xFFD3EEF0),
            lightRed = Color(0xFFC75B66),
            darkBackground = Color(0xFF0E1717),
            darkBackgroundAlt = Color(0xFF162323),
            darkSurface = Color(0xFF1B2B2C),
            darkSurfaceRaised = Color(0xFF243738),
            darkSurfaceMuted = Color(0xFF132020),
            darkPrimaryText = Color(0xFFE4F1F1),
            darkSecondaryText = Color(0xFFA3BDBE),
            darkAccent = Color(0xFF72CDD2),
            darkAccentSoft = Color(0xFF1F4548),
            darkRed = Color(0xFFE27F89),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "pewter",
            name = "Pewter",
            description = "Balanced grey-green controls for dense dashboards.",
            lightBackground = Color(0xFFF4F6F4),
            lightBackgroundAlt = Color(0xFFE6EBE7),
            lightSurface = Color(0xFFFAFFFB),
            lightSurfaceRaised = Color(0xFFFAFCFA),
            lightSurfaceMuted = Color(0xFFEAEFEC),
            lightPrimaryText = Color(0xFF1F2422),
            lightSecondaryText = Color(0xFF6B7671),
            lightAccent = Color(0xFF5D8F7E),
            lightAccentSoft = Color(0xFFDFEAE6),
            lightRed = Color(0xFFC75F66),
            darkBackground = Color(0xFF111412),
            darkBackgroundAlt = Color(0xFF1A201D),
            darkSurface = Color(0xFF202723),
            darkSurfaceRaised = Color(0xFF2A322E),
            darkSurfaceMuted = Color(0xFF171C19),
            darkPrimaryText = Color(0xFFE9EDEB),
            darkSecondaryText = Color(0xFFAFB9B4),
            darkAccent = Color(0xFF94C9BA),
            darkAccentSoft = Color(0xFF2E413C),
            darkRed = Color(0xFFE2838B),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF547B73)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF81C2B2))
        ),
        appThemeFamily(
            id = "navy",
            name = "Navy",
            description = "Dark-ready blue accents with light-mode restraint.",
            lightBackground = Color(0xFFF3F5FA),
            lightBackgroundAlt = Color(0xFFE3E8F2),
            lightSurface = Color(0xFFF9FAFF),
            lightSurfaceRaised = Color(0xFFFAFBFE),
            lightSurfaceMuted = Color(0xFFE8EDF5),
            lightPrimaryText = Color(0xFF172033),
            lightSecondaryText = Color(0xFF64728A),
            lightAccent = Color(0xFF526FA5),
            lightAccentSoft = Color(0xFFDDE5F3),
            lightRed = Color(0xFFC75B66),
            darkBackground = Color(0xFF0F1320),
            darkBackgroundAlt = Color(0xFF181E2F),
            darkSurface = Color(0xFF1E263A),
            darkSurfaceRaised = Color(0xFF283149),
            darkSurfaceMuted = Color(0xFF151A2A),
            darkPrimaryText = Color(0xFFE7ECF6),
            darkSecondaryText = Color(0xFFAAB5C8),
            darkAccent = Color(0xFF91ADE2),
            darkAccentSoft = Color(0xFF2C3855),
            darkRed = Color(0xFFE17F8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "clay",
            name = "Clay",
            description = "Soft red-brown warmth with operational contrast.",
            lightBackground = Color(0xFFF8F1EE),
            lightBackgroundAlt = Color(0xFFF0E2DC),
            lightSurface = Color(0xFFFFFBF8),
            lightSurfaceRaised = Color(0xFFFCF6F2),
            lightSurfaceMuted = Color(0xFFF2E7E1),
            lightPrimaryText = Color(0xFF2B211D),
            lightSecondaryText = Color(0xFF7C6A62),
            lightAccent = Color(0xFFB46A55),
            lightAccentSoft = Color(0xFFF1DDD6),
            lightRed = Color(0xFFC65D5D),
            darkBackground = Color(0xFF18110F),
            darkBackgroundAlt = Color(0xFF251A17),
            darkSurface = Color(0xFF2D211E),
            darkSurfaceRaised = Color(0xFF392A26),
            darkSurfaceMuted = Color(0xFF201613),
            darkPrimaryText = Color(0xFFF1E7E2),
            darkSecondaryText = Color(0xFFC5B3AB),
            darkAccent = Color(0xFFE29A86),
            darkAccentSoft = Color(0xFF4A3029),
            darkRed = Color(0xFFE48686),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF4E7943), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF1F6A60)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFCBB76F), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "cobalt",
            name = "Cobalt",
            description = "Clear blue accents with quiet neutral scaffolding.",
            lightBackground = Color(0xFFF2F6FB),
            lightBackgroundAlt = Color(0xFFE1EAF4),
            lightSurface = Color(0xFFF8FBFF),
            lightSurfaceRaised = Color(0xFFF9FCFE),
            lightSurfaceMuted = Color(0xFFE7EEF6),
            lightPrimaryText = Color(0xFF162233),
            lightSecondaryText = Color(0xFF62758C),
            lightAccent = Color(0xFF3577B8),
            lightAccentSoft = Color(0xFFD8E7F5),
            lightRed = Color(0xFFC75A66),
            darkBackground = Color(0xFF0E1420),
            darkBackgroundAlt = Color(0xFF172033),
            darkSurface = Color(0xFF1D2940),
            darkSurfaceRaised = Color(0xFF263450),
            darkSurfaceMuted = Color(0xFF131C2C),
            darkPrimaryText = Color(0xFFE5EDF8),
            darkSecondaryText = Color(0xFFA6B7CD),
            darkAccent = Color(0xFF7DB5EA),
            darkAccentSoft = Color(0xFF243D5A),
            darkRed = Color(0xFFE17E8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFB8A9C4), network = Color(0xFFE0A05F), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "juniper",
            name = "Juniper",
            description = "Green-blue controls for infrastructure views.",
            lightBackground = Color(0xFFF2F7F6),
            lightBackgroundAlt = Color(0xFFE1EEEC),
            lightSurface = Color(0xFFFCFFFE),
            lightSurfaceRaised = Color(0xFFF8FCFB),
            lightSurfaceMuted = Color(0xFFE6F1EF),
            lightPrimaryText = Color(0xFF172622),
            lightSecondaryText = Color(0xFF617971),
            lightAccent = Color(0xFF408E78),
            lightAccentSoft = Color(0xFFD9EBE5),
            lightRed = Color(0xFFC75D66),
            darkBackground = Color(0xFF0F1715),
            darkBackgroundAlt = Color(0xFF17231F),
            darkSurface = Color(0xFF1D2B27),
            darkSurfaceRaised = Color(0xFF263731),
            darkSurfaceMuted = Color(0xFF14201C),
            darkPrimaryText = Color(0xFFE5F1EE),
            darkSecondaryText = Color(0xFFA6BDB6),
            darkAccent = Color(0xFF7BCBB6),
            darkAccentSoft = Color(0xFF25443D),
            darkRed = Color(0xFFE2818A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF459B6B)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8CD17A), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF6BC9B1))
        ),
        appThemeFamily(
            id = "amethyst",
            name = "Amethyst",
            description = "Measured violet focus states on neutral surfaces.",
            lightBackground = Color(0xFFF6F3FA),
            lightBackgroundAlt = Color(0xFFE9E4F2),
            lightSurface = Color(0xFFFFFCFF),
            lightSurfaceRaised = Color(0xFFFBF8FE),
            lightSurfaceMuted = Color(0xFFEEE9F5),
            lightPrimaryText = Color(0xFF241F2D),
            lightSecondaryText = Color(0xFF70667F),
            lightAccent = Color(0xFF7664A8),
            lightAccentSoft = Color(0xFFE5DFF2),
            lightRed = Color(0xFFC65C6D),
            darkBackground = Color(0xFF14111A),
            darkBackgroundAlt = Color(0xFF1F1A28),
            darkSurface = Color(0xFF272130),
            darkSurfaceRaised = Color(0xFF322A3D),
            darkSurfaceMuted = Color(0xFF1B1623),
            darkPrimaryText = Color(0xFFEDE8F4),
            darkSecondaryText = Color(0xFFBAB0C8),
            darkAccent = Color(0xFFB9A4DD),
            darkAccentSoft = Color(0xFF3A304E),
            darkRed = Color(0xFFE38595),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF756B81), disk = Color(0xFF845626), network = Color(0xFFB88A35), latency = Color(0xFF3D766C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "smoke",
            name = "Smoke",
            description = "Plain grey UI with low-chroma blue signals.",
            lightBackground = Color(0xFFF5F6F7),
            lightBackgroundAlt = Color(0xFFE7EAEC),
            lightSurface = Color(0xFFFBFCFE),
            lightSurfaceRaised = Color(0xFFFBFCFD),
            lightSurfaceMuted = Color(0xFFECEFF1),
            lightPrimaryText = Color(0xFF1F2327),
            lightSecondaryText = Color(0xFF6C747C),
            lightAccent = Color(0xFF5A8199),
            lightAccentSoft = Color(0xFFDFE9EE),
            lightRed = Color(0xFFC75D65),
            darkBackground = Color(0xFF111315),
            darkBackgroundAlt = Color(0xFF1B1E21),
            darkSurface = Color(0xFF22272B),
            darkSurfaceRaised = Color(0xFF2C3237),
            darkSurfaceMuted = Color(0xFF181B1E),
            darkPrimaryText = Color(0xFFEAEDEE),
            darkSecondaryText = Color(0xFFB1B8BE),
            darkAccent = Color(0xFF98C4D8),
            darkAccentSoft = Color(0xFF30424B),
            darkRed = Color(0xFFE2838A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6494), latency = Color(0xFF4B7672)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF97BEBA))
        ),
        appThemeFamily(
            id = "forest",
            name = "Forest",
            description = "Deeper green hierarchy with warm warnings.",
            lightBackground = Color(0xFFF2F6F0),
            lightBackgroundAlt = Color(0xFFE3EBDF),
            lightSurface = Color(0xFFFCFFFA),
            lightSurfaceRaised = Color(0xFFF8FCF5),
            lightSurfaceMuted = Color(0xFFE7EEE2),
            lightPrimaryText = Color(0xFF1B2618),
            lightSecondaryText = Color(0xFF667761),
            lightAccent = Color(0xFF4F8B55),
            lightAccentSoft = Color(0xFFDDE9D9),
            lightRed = Color(0xFFC86261),
            darkBackground = Color(0xFF10170E),
            darkBackgroundAlt = Color(0xFF192315),
            darkSurface = Color(0xFF1F2B1B),
            darkSurfaceRaised = Color(0xFF293724),
            darkSurfaceMuted = Color(0xFF161F13),
            darkPrimaryText = Color(0xFFE7F1E2),
            darkSecondaryText = Color(0xFFADBEA5),
            darkAccent = Color(0xFF8AC982),
            darkAccentSoft = Color(0xFF2D4329),
            darkRed = Color(0xFFE48684),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF277B6B)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF89D07C), disk = Color(0xFFC7B965), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "lagoon",
            name = "Lagoon",
            description = "Soft teal-blue accents for scan-heavy screens.",
            lightBackground = Color(0xFFF1F8FA),
            lightBackgroundAlt = Color(0xFFE0EDF2),
            lightSurface = Color(0xFFFCFEFF),
            lightSurfaceRaised = Color(0xFFF8FCFD),
            lightSurfaceMuted = Color(0xFFE5F0F4),
            lightPrimaryText = Color(0xFF14252B),
            lightSecondaryText = Color(0xFF607983),
            lightAccent = Color(0xFF348BA4),
            lightAccentSoft = Color(0xFFD6EAF0),
            lightRed = Color(0xFFC75A66),
            darkBackground = Color(0xFF0E161A),
            darkBackgroundAlt = Color(0xFF16222A),
            darkSurface = Color(0xFF1B2A33),
            darkSurfaceRaised = Color(0xFF243541),
            darkSurfaceMuted = Color(0xFF131E25),
            darkPrimaryText = Color(0xFFE4EFF4),
            darkSecondaryText = Color(0xFFA3B8C4),
            darkAccent = Color(0xFF75C2DD),
            darkAccentSoft = Color(0xFF1F3F50),
            darkRed = Color(0xFFE17F8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "ruby",
            name = "Ruby",
            description = "Controlled red accents without alarm-heavy surfaces.",
            lightBackground = Color(0xFFF8F2F2),
            lightBackgroundAlt = Color(0xFFEFE3E3),
            lightSurface = Color(0xFFFFFBFB),
            lightSurfaceRaised = Color(0xFFFCF7F7),
            lightSurfaceMuted = Color(0xFFF2E8E8),
            lightPrimaryText = Color(0xFF2B1E1F),
            lightSecondaryText = Color(0xFF7B6668),
            lightAccent = Color(0xFFB65D67),
            lightAccentSoft = Color(0xFFF1DDE0),
            lightRed = Color(0xFFB65D67),
            darkBackground = Color(0xFF171010),
            darkBackgroundAlt = Color(0xFF241919),
            darkSurface = Color(0xFF2C2021),
            darkSurfaceRaised = Color(0xFF372929),
            darkSurfaceMuted = Color(0xFF1F1516),
            darkPrimaryText = Color(0xFFF1E6E6),
            darkSecondaryText = Color(0xFFC5B0B2),
            darkAccent = Color(0xFFE28F99),
            darkAccentSoft = Color(0xFF4A2E33),
            darkRed = Color(0xFFE28F99),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF74609B), latency = Color(0xFF5F6A66)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF95B5B5))
        ),
        appThemeFamily(
            id = "cedar",
            name = "Cedar",
            description = "Grounded brown-green accents for admin flows.",
            lightBackground = Color(0xFFF7F3ED),
            lightBackgroundAlt = Color(0xFFEDE5D8),
            lightSurface = Color(0xFFFFFCF7),
            lightSurfaceRaised = Color(0xFFFCF7F0),
            lightSurfaceMuted = Color(0xFFF1E8DC),
            lightPrimaryText = Color(0xFF2A231A),
            lightSecondaryText = Color(0xFF796D5E),
            lightAccent = Color(0xFF8F7A45),
            lightAccentSoft = Color(0xFFE9E2CD),
            lightRed = Color(0xFFC76260),
            darkBackground = Color(0xFF16130F),
            darkBackgroundAlt = Color(0xFF221D16),
            darkSurface = Color(0xFF2A241C),
            darkSurfaceRaised = Color(0xFF352E24),
            darkSurfaceMuted = Color(0xFF1D1913),
            darkPrimaryText = Color(0xFFF0E9DE),
            darkSecondaryText = Color(0xFFC1B7A7),
            darkAccent = Color(0xFFC5B16E),
            darkAccentSoft = Color(0xFF403823),
            darkRed = Color(0xFFE48684),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF657045), disk = Color(0xFF944C37), network = Color(0xFF7B6098), latency = Color(0xFF1F6A60)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFCAB76D), network = Color(0xFFCEADE3), latency = Color(0xFF71C2AC))
        ),
        appThemeFamily(
            id = "ice",
            name = "Ice",
            description = "Pale blue clarity with restrained dark mode.",
            lightBackground = Color(0xFFF3F8FB),
            lightBackgroundAlt = Color(0xFFE2EEF5),
            lightSurface = Color(0xFFF8FDFF),
            lightSurfaceRaised = Color(0xFFF9FCFE),
            lightSurfaceMuted = Color(0xFFE7F1F7),
            lightPrimaryText = Color(0xFF172530),
            lightSecondaryText = Color(0xFF637988),
            lightAccent = Color(0xFF4B8DB5),
            lightAccentSoft = Color(0xFFDDEBF4),
            lightRed = Color(0xFFC75B66),
            darkBackground = Color(0xFF0F1519),
            darkBackgroundAlt = Color(0xFF18212A),
            darkSurface = Color(0xFF1E2A34),
            darkSurfaceRaised = Color(0xFF283542),
            darkSurfaceMuted = Color(0xFF151D24),
            darkPrimaryText = Color(0xFFE6EFF4),
            darkSecondaryText = Color(0xFFA8BAC5),
            darkAccent = Color(0xFF8CC4E3),
            darkAccentSoft = Color(0xFF2A4050),
            darkRed = Color(0xFFE17F8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF7B8FA8), network = Color(0xFF6C5486), latency = Color(0xFF4B8284)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFC3B0AE), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "lichen",
            name = "Lichen",
            description = "Greyed green theme for quiet status monitoring.",
            lightBackground = Color(0xFFF4F7F0),
            lightBackgroundAlt = Color(0xFFE6ECDF),
            lightSurface = Color(0xFFFFFFFB),
            lightSurfaceRaised = Color(0xFFFAFCF6),
            lightSurfaceMuted = Color(0xFFEAEFE3),
            lightPrimaryText = Color(0xFF20271C),
            lightSecondaryText = Color(0xFF6D7964),
            lightAccent = Color(0xFF78925B),
            lightAccentSoft = Color(0xFFE4EBD8),
            lightRed = Color(0xFFC86364),
            darkBackground = Color(0xFF12170F),
            darkBackgroundAlt = Color(0xFF1C2317),
            darkSurface = Color(0xFF232B1D),
            darkSurfaceRaised = Color(0xFF2D3726),
            darkSurfaceMuted = Color(0xFF181F14),
            darkPrimaryText = Color(0xFFEAF0E2),
            darkSecondaryText = Color(0xFFB6C0A8),
            darkAccent = Color(0xFFAFC887),
            darkAccentSoft = Color(0xFF37422B),
            darkRed = Color(0xFFE58689),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF64985F), disk = Color(0xFF845626), network = Color(0xFF735B9C), latency = Color(0xFF3D766C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF6EC8AF))
        ),
        appThemeFamily(
            id = "midnight",
            name = "Midnight",
            description = "Night-forward app chrome with blue-grey daytime pair.",
            lightBackground = Color(0xFFF3F5F8),
            lightBackgroundAlt = Color(0xFFE3E8EF),
            lightSurface = Color(0xFFF9FBFE),
            lightSurfaceRaised = Color(0xFFFAFCFD),
            lightSurfaceMuted = Color(0xFFE8EDF3),
            lightPrimaryText = Color(0xFF192231),
            lightSecondaryText = Color(0xFF66758A),
            lightAccent = Color(0xFF4D76A6),
            lightAccentSoft = Color(0xFFDDE7F2),
            lightRed = Color(0xFFC75A66),
            darkBackground = Color(0xFF0B1018),
            darkBackgroundAlt = Color(0xFF121A26),
            darkSurface = Color(0xFF182334),
            darkSurfaceRaised = Color(0xFF212E43),
            darkSurfaceMuted = Color(0xFF101724),
            darkPrimaryText = Color(0xFFE5ECF6),
            darkSecondaryText = Color(0xFFA4B2C6),
            darkAccent = Color(0xFF86B5E4),
            darkAccentSoft = Color(0xFF263B55),
            darkRed = Color(0xFFE17E8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF8A6FB0), network = Color(0xFF604B8F), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFC3AEAE), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "pearl",
            name = "Pearl",
            description = "Soft high-key surfaces with blue-green accents.",
            lightBackground = Color(0xFFF7F8F6),
            lightBackgroundAlt = Color(0xFFE9ECE8),
            lightSurface = Color(0xFFFCFDF9),
            lightSurfaceRaised = Color(0xFFFDFDFC),
            lightSurfaceMuted = Color(0xFFEDEFEA),
            lightPrimaryText = Color(0xFF20231F),
            lightSecondaryText = Color(0xFF6E756B),
            lightAccent = Color(0xFF5C8F86),
            lightAccentSoft = Color(0xFFDFEAE7),
            lightRed = Color(0xFFC75F65),
            darkBackground = Color(0xFF111412),
            darkBackgroundAlt = Color(0xFF1A1F1D),
            darkSurface = Color(0xFF202623),
            darkSurfaceRaised = Color(0xFF2A312D),
            darkSurfaceMuted = Color(0xFF171C19),
            darkPrimaryText = Color(0xFFE9EDEB),
            darkSecondaryText = Color(0xFFAFB9B4),
            darkAccent = Color(0xFF95C9C0),
            darkAccentSoft = Color(0xFF2E413E),
            darkRed = Color(0xFFE2838A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF4B7A7C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "orchid",
            name = "Orchid",
            description = "Soft purple accents with operational status colors.",
            lightBackground = Color(0xFFF7F2F9),
            lightBackgroundAlt = Color(0xFFECE2F0),
            lightSurface = Color(0xFFFFFBFF),
            lightSurfaceRaised = Color(0xFFFCF7FD),
            lightSurfaceMuted = Color(0xFFF0E7F3),
            lightPrimaryText = Color(0xFF281E2D),
            lightSecondaryText = Color(0xFF78657F),
            lightAccent = Color(0xFF9362A3),
            lightAccentSoft = Color(0xFFEBDDF1),
            lightRed = Color(0xFFC65D70),
            darkBackground = Color(0xFF161019),
            darkBackgroundAlt = Color(0xFF221925),
            darkSurface = Color(0xFF2A2030),
            darkSurfaceRaised = Color(0xFF35293C),
            darkSurfaceMuted = Color(0xFF1E1522),
            darkPrimaryText = Color(0xFFF0E6F2),
            darkSecondaryText = Color(0xFFC0AFC7),
            darkAccent = Color(0xFFCFA0DB),
            darkAccentSoft = Color(0xFF432D4B),
            darkRed = Color(0xFFE48699),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF6B7063), disk = Color(0xFF9A642C), network = Color(0xFF7057A6), latency = Color(0xFF36685F)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF96B2BA))
        ),
        appThemeFamily(
            id = "bay",
            name = "Bay",
            description = "Muted coastal blues with green success states.",
            lightBackground = Color(0xFFF1F7F7),
            lightBackgroundAlt = Color(0xFFE0EDEE),
            lightSurface = Color(0xFFFCFFFF),
            lightSurfaceRaised = Color(0xFFF8FCFC),
            lightSurfaceMuted = Color(0xFFE5F1F1),
            lightPrimaryText = Color(0xFF142626),
            lightSecondaryText = Color(0xFF607979),
            lightAccent = Color(0xFF3D8F99),
            lightAccentSoft = Color(0xFFD9ECEF),
            lightRed = Color(0xFFC75D66),
            darkBackground = Color(0xFF0F1717),
            darkBackgroundAlt = Color(0xFF172323),
            darkSurface = Color(0xFF1D2B2B),
            darkSurfaceRaised = Color(0xFF263737),
            darkSurfaceMuted = Color(0xFF142020),
            darkPrimaryText = Color(0xFFE5F1F1),
            darkSecondaryText = Color(0xFFA6BDBD),
            darkAccent = Color(0xFF7BC9D1),
            darkAccentSoft = Color(0xFF254247),
            darkRed = Color(0xFFE2828B),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF6F8A8E), network = Color(0xFF604B8F), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFA5C3C5), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "hazel",
            name = "Hazel",
            description = "Olive-brown accents over readable neutral surfaces.",
            lightBackground = Color(0xFFF7F4ED),
            lightBackgroundAlt = Color(0xFFEDE7DA),
            lightSurface = Color(0xFFFFFCF7),
            lightSurfaceRaised = Color(0xFFFCF8F0),
            lightSurfaceMuted = Color(0xFFF1EADC),
            lightPrimaryText = Color(0xFF282419),
            lightSecondaryText = Color(0xFF77705E),
            lightAccent = Color(0xFF8C8247),
            lightAccentSoft = Color(0xFFE9E5CF),
            lightRed = Color(0xFFC76260),
            darkBackground = Color(0xFF16140F),
            darkBackgroundAlt = Color(0xFF222016),
            darkSurface = Color(0xFF2A271C),
            darkSurfaceRaised = Color(0xFF353224),
            darkSurfaceMuted = Color(0xFF1D1A13),
            darkPrimaryText = Color(0xFFF0EBDD),
            darkSecondaryText = Color(0xFFC1BBA7),
            darkAccent = Color(0xFFC5BB72),
            darkAccentSoft = Color(0xFF403C24),
            darkRed = Color(0xFFE48684),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF5F7543), disk = Color(0xFF944C37), network = Color(0xFF7B6098), latency = Color(0xFF1F6A60)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFCAB76D), network = Color(0xFFCEADE3), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "blueprint",
            name = "Blueprint",
            description = "Technical blue accents with clean app surfaces.",
            lightBackground = Color(0xFFF2F6FC),
            lightBackgroundAlt = Color(0xFFE1EAF6),
            lightSurface = Color(0xFFF7FAFF),
            lightSurfaceRaised = Color(0xFFF9FCFF),
            lightSurfaceMuted = Color(0xFFE7EFF8),
            lightPrimaryText = Color(0xFF162334),
            lightSecondaryText = Color(0xFF62768E),
            lightAccent = Color(0xFF3F78B5),
            lightAccentSoft = Color(0xFFD9E8F6),
            lightRed = Color(0xFFC75A66),
            darkBackground = Color(0xFF0E1420),
            darkBackgroundAlt = Color(0xFF172033),
            darkSurface = Color(0xFF1D2940),
            darkSurfaceRaised = Color(0xFF263550),
            darkSurfaceMuted = Color(0xFF131C2C),
            darkPrimaryText = Color(0xFFE5EDF8),
            darkSecondaryText = Color(0xFFA6B7CD),
            darkAccent = Color(0xFF82B6EA),
            darkAccentSoft = Color(0xFF263E5A),
            darkRed = Color(0xFFE17E8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF8F6ED0), latency = Color(0xFF247B70)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF70C1EA), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        ),
        appThemeFamily(
            id = "willow",
            name = "Willow",
            description = "Gentle green-yellow accents with neutral content areas.",
            lightBackground = Color(0xFFF5F7EF),
            lightBackgroundAlt = Color(0xFFE8EDDC),
            lightSurface = Color(0xFFFFFFFA),
            lightSurfaceRaised = Color(0xFFFBFCF5),
            lightSurfaceMuted = Color(0xFFEBF0E1),
            lightPrimaryText = Color(0xFF222719),
            lightSecondaryText = Color(0xFF707A61),
            lightAccent = Color(0xFF7E9652),
            lightAccentSoft = Color(0xFFE5ECD7),
            lightRed = Color(0xFFC86364),
            darkBackground = Color(0xFF13170F),
            darkBackgroundAlt = Color(0xFF1D2317),
            darkSurface = Color(0xFF242B1D),
            darkSurfaceRaised = Color(0xFF2E3726),
            darkSurfaceMuted = Color(0xFF191F14),
            darkPrimaryText = Color(0xFFEBF0E2),
            darkSecondaryText = Color(0xFFB8C0A8),
            darkAccent = Color(0xFFB5C889),
            darkAccentSoft = Color(0xFF39432C),
            darkRed = Color(0xFFE58689),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF64995F), disk = Color(0xFF845626), network = Color(0xFF735A9C), latency = Color(0xFF3D766C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFD0C06F), network = Color(0xFFCEADE3), latency = Color(0xFF6EC8AF))
        ),
        appThemeFamily(
            id = "garnet",
            name = "Garnet",
            description = "Dark red accents tuned for app chrome, not terminals.",
            lightBackground = Color(0xFFF8F2F3),
            lightBackgroundAlt = Color(0xFFEFE3E5),
            lightSurface = Color(0xFFFFFBFC),
            lightSurfaceRaised = Color(0xFFFCF7F8),
            lightSurfaceMuted = Color(0xFFF2E8EA),
            lightPrimaryText = Color(0xFF2B1E21),
            lightSecondaryText = Color(0xFF7B666B),
            lightAccent = Color(0xFFA95E6C),
            lightAccentSoft = Color(0xFFF0DDE2),
            lightRed = Color(0xFFC55764),
            darkBackground = Color(0xFF171012),
            darkBackgroundAlt = Color(0xFF24191C),
            darkSurface = Color(0xFF2C2024),
            darkSurfaceRaised = Color(0xFF37292D),
            darkSurfaceMuted = Color(0xFF1F1518),
            darkPrimaryText = Color(0xFFF1E6E8),
            darkSecondaryText = Color(0xFFC5B0B6),
            darkAccent = Color(0xFFDA96A4),
            darkAccentSoft = Color(0xFF492F37),
            darkRed = Color(0xFFE27D8A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF616D6B)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF94B5B4))
        ),
        appThemeFamily(
            id = "seagrass",
            name = "Seagrass",
            description = "Muted aqua-green organization for repeated workflows.",
            lightBackground = Color(0xFFF1F8F6),
            lightBackgroundAlt = Color(0xFFE0EEEA),
            lightSurface = Color(0xFFFCFFFE),
            lightSurfaceRaised = Color(0xFFF8FCFB),
            lightSurfaceMuted = Color(0xFFE5F1ED),
            lightPrimaryText = Color(0xFF142722),
            lightSecondaryText = Color(0xFF607A72),
            lightAccent = Color(0xFF3E947F),
            lightAccentSoft = Color(0xFFD8ECE6),
            lightRed = Color(0xFFC75D66),
            darkBackground = Color(0xFF0F1714),
            darkBackgroundAlt = Color(0xFF17231F),
            darkSurface = Color(0xFF1D2B26),
            darkSurfaceRaised = Color(0xFF263730),
            darkSurfaceMuted = Color(0xFF14201C),
            darkPrimaryText = Color(0xFFE5F1ED),
            darkSecondaryText = Color(0xFFA6BDB5),
            darkAccent = Color(0xFF7BCBB5),
            darkAccentSoft = Color(0xFF25443B),
            darkRed = Color(0xFFE2818A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF459D6E)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8CD17A), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF6BC9B1))
        ),
        appThemeFamily(
            id = "granite",
            name = "Granite",
            description = "Neutral stone surfaces with blue-green highlights.",
            lightBackground = Color(0xFFF5F5F4),
            lightBackgroundAlt = Color(0xFFE8E9E6),
            lightSurface = Color(0xFFFBFBF9),
            lightSurfaceRaised = Color(0xFFFBFBFA),
            lightSurfaceMuted = Color(0xFFEDEEEB),
            lightPrimaryText = Color(0xFF212321),
            lightSecondaryText = Color(0xFF707570),
            lightAccent = Color(0xFF568A82),
            lightAccentSoft = Color(0xFFDEE8E6),
            lightRed = Color(0xFFC75F65),
            darkBackground = Color(0xFF111312),
            darkBackgroundAlt = Color(0xFF1B1E1C),
            darkSurface = Color(0xFF222624),
            darkSurfaceRaised = Color(0xFF2C312E),
            darkSurfaceMuted = Color(0xFF181B19),
            darkPrimaryText = Color(0xFFEAEDEB),
            darkSecondaryText = Color(0xFFB1B8B3),
            darkAccent = Color(0xFF91C6BD),
            darkAccentSoft = Color(0xFF2E403D),
            darkRed = Color(0xFFE2838A),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF245E9A), memory = Color(0xFF36693B), disk = Color(0xFF845626), network = Color(0xFF7B6098), latency = Color(0xFF517870)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFCEADE3), latency = Color(0xFF97BEB3))
        ),
        appThemeFamily(
            id = "dusk",
            name = "Dusk",
            description = "Blue-violet evening tones with readable surfaces.",
            lightBackground = Color(0xFFF5F3FA),
            lightBackgroundAlt = Color(0xFFE7E4F2),
            lightSurface = Color(0xFFFFFCFF),
            lightSurfaceRaised = Color(0xFFFAF8FE),
            lightSurfaceMuted = Color(0xFFECE9F5),
            lightPrimaryText = Color(0xFF211F2D),
            lightSecondaryText = Color(0xFF6B6680),
            lightAccent = Color(0xFF6868A8),
            lightAccentSoft = Color(0xFFE1E1F2),
            lightRed = Color(0xFFC65C6D),
            darkBackground = Color(0xFF12111A),
            darkBackgroundAlt = Color(0xFF1B1A29),
            darkSurface = Color(0xFF222132),
            darkSurfaceRaised = Color(0xFF2C2A3F),
            darkSurfaceMuted = Color(0xFF181723),
            darkPrimaryText = Color(0xFFEAE8F4),
            darkSecondaryText = Color(0xFFB4B0C8),
            darkAccent = Color(0xFFA9A8DE),
            darkAccentSoft = Color(0xFF34334F),
            darkRed = Color(0xFFE38595),
            lightMetrics = MetricAccentSet(cpu = Color(0xFF205388), memory = Color(0xFF6F7382), disk = Color(0xFF845626), network = Color(0xFFB88442), latency = Color(0xFF3D766C)),
            darkMetrics = MetricAccentSet(cpu = Color(0xFF72B8EF), memory = Color(0xFF8ED176), disk = Color(0xFFE4AC5B), network = Color(0xFFB59AE8), latency = Color(0xFF68C8B4))
        )
    )

    val families = listOf(
        DeckThemeFamily(
            id = "aurora",
            name = "Aurora",
            description = "Bright command-center clarity with cyan signal accents.",
            light = auroraLight.polished(),
            dark = auroraDark.polished()
        ),
        DeckThemeFamily(
            id = "graphite",
            name = "Graphite",
            description = "Quiet operations console with restrained metal tones.",
            light = graphiteLight.polished(),
            dark = graphiteDark.polished()
        ),
        DeckThemeFamily(
            id = "ember",
            name = "Ember",
            description = "Warmer incident-room contrast for night work.",
            light = emberLight.polished(),
            dark = emberDark.polished()
        ),
        DeckThemeFamily(
            id = "catppuccin",
            name = "Catppuccin",
            description = "Soft terminal palette with calm blue and pastel status colors.",
            light = catppuccinLight.polished(),
            dark = catppuccinDark.polished()
        ),
        DeckThemeFamily(
            id = "rosepine",
            name = "Rosé Pine",
            description = "Muted rose, pine, and gold tones for a less clinical console.",
            light = rosePineLight.polished(),
            dark = rosePineDark.polished()
        ),
        DeckThemeFamily(
            id = "monochrome-light",
            name = "Monochrome Light",
            description = "Pure black-on-white interface and terminal accents.",
            light = monochromeLight.polished(),
            dark = monochromeDark.polished(),
            modes = setOf(DeckThemeMode.Light)
        ),
        DeckThemeFamily(
            id = "monochrome-dark",
            name = "Monochrome Dark",
            description = "Pure white-on-black interface and terminal accents.",
            light = monochromeLight.polished(),
            dark = monochromeDark.polished(),
            modes = setOf(DeckThemeMode.Dark)
        ),
        DeckThemeFamily(
            id = "comic-ink",
            name = "Comic Ink",
            description = "Sharp black-on-white panels with inked borders.",
            light = comicLight.polished(),
            dark = comicLight.polished(),
            modes = setOf(DeckThemeMode.Light)
        )
    ) + appThemeFamilies

    fun paletteFor(mode: DeckThemeMode, familyId: String, systemDark: Boolean): DeckPalette {
        val useDark = when (mode) {
            DeckThemeMode.System -> systemDark
            DeckThemeMode.Light -> false
            DeckThemeMode.Dark -> true
        }
        val resolvedMode = if (useDark) DeckThemeMode.Dark else DeckThemeMode.Light
        val family = familiesFor(mode, systemDark).firstOrNull { it.id == familyId }
            ?: families.firstOrNull { resolvedMode in it.modes }
            ?: families.first()
        return if (useDark) family.dark else family.light
    }

    fun familiesFor(mode: DeckThemeMode, systemDark: Boolean): List<DeckThemeFamily> {
        val resolvedMode = when (mode) {
            DeckThemeMode.System -> if (systemDark) DeckThemeMode.Dark else DeckThemeMode.Light
            DeckThemeMode.Light -> DeckThemeMode.Light
            DeckThemeMode.Dark -> DeckThemeMode.Dark
        }
        return families.filter { resolvedMode in it.modes }
    }

    fun customFamily(
        id: String,
        name: String,
        description: String,
        lightAccent: Color,
        darkAccent: Color = lightAccent
    ): DeckThemeFamily {
        return DeckThemeFamily(
            id = id,
            name = name,
            description = description,
            light = auroraLight.copy(
                id = "$id-light",
                name = "$name Light",
                cyan = lightAccent,
                brandAlt = lightAccent
            ),
            dark = auroraDark.copy(
                id = "$id-dark",
                name = "$name Dark",
                cyan = darkAccent,
                brandAlt = darkAccent
            )
        )
    }
}

object DeckColors {
    var Background = DeckThemeCatalog.families.first().light.background
    var BackgroundAlt = DeckThemeCatalog.families.first().light.backgroundAlt
    var Surface = DeckThemeCatalog.families.first().light.surface
    var SurfaceRaised = DeckThemeCatalog.families.first().light.surfaceRaised
    var SurfaceMuted = DeckThemeCatalog.families.first().light.surfaceMuted
    var PrimaryText = DeckThemeCatalog.families.first().light.primaryText
    var SecondaryText = DeckThemeCatalog.families.first().light.secondaryText
    var TertiaryText = DeckThemeCatalog.families.first().light.tertiaryText
    var Divider = DeckThemeCatalog.families.first().light.divider
    var CardStroke = DeckThemeCatalog.families.first().light.cardStroke
    var Cyan = DeckThemeCatalog.families.first().light.cyan
    var CyanSoft = DeckThemeCatalog.families.first().light.cyanSoft
    var Green = DeckThemeCatalog.families.first().light.green
    var Red = DeckThemeCatalog.families.first().light.red
    var Orange = DeckThemeCatalog.families.first().light.orange
    var Yellow = DeckThemeCatalog.families.first().light.yellow
    var Purple = DeckThemeCatalog.families.first().light.purple
    var PurpleSoft = DeckThemeCatalog.families.first().light.purpleSoft
    var Terminal = DeckThemeCatalog.families.first().light.terminal
    var TerminalPanel = DeckThemeCatalog.families.first().light.terminalPanel
    var TerminalAccent = DeckThemeCatalog.families.first().light.terminalAccent
    var NavSurface = DeckThemeCatalog.families.first().light.navSurface
    var Brand = DeckThemeCatalog.families.first().light.brand
    var BrandAlt = DeckThemeCatalog.families.first().light.brandAlt
    var Accent = DeckThemeCatalog.families.first().light.cyan
    var MetricCpu = DeckThemeCatalog.families.first().light.metricCpu
    var MetricMemory = DeckThemeCatalog.families.first().light.metricMemory
    var MetricDisk = DeckThemeCatalog.families.first().light.metricDisk
    var MetricNetwork = DeckThemeCatalog.families.first().light.metricNetwork
    var MetricLatency = DeckThemeCatalog.families.first().light.metricLatency

    fun apply(palette: DeckPalette) {
        Background = palette.background
        BackgroundAlt = palette.backgroundAlt
        Surface = palette.surface
        SurfaceRaised = palette.surfaceRaised
        SurfaceMuted = palette.surfaceMuted
        PrimaryText = palette.primaryText
        SecondaryText = palette.secondaryText
        TertiaryText = palette.tertiaryText
        Divider = palette.divider
        CardStroke = palette.cardStroke
        Cyan = palette.cyan
        CyanSoft = palette.cyanSoft
        Green = palette.green
        Red = palette.red
        Orange = palette.orange
        Yellow = palette.yellow
        Purple = palette.purple
        PurpleSoft = palette.purpleSoft
        Terminal = palette.terminal
        TerminalPanel = palette.terminalPanel
        TerminalAccent = palette.terminalAccent
        NavSurface = palette.navSurface
        Brand = palette.brand
        BrandAlt = palette.brandAlt
        Accent = palette.cyan
        MetricCpu = palette.metricCpu
        MetricMemory = palette.metricMemory
        MetricDisk = palette.metricDisk
        MetricNetwork = palette.metricNetwork
        MetricLatency = palette.metricLatency
    }
}

/**
 * Selectable font for text boxes / input fields and general UI body text.
 * "nunito" is the default so text boxes match the rest of the app out of the box.
 * "system" opts back into the platform default typeface.
 */
enum class InputFontChoice(val id: String, val label: String) {
    Nunito("nunito", "Nunito"),
    System("system", "System"),
    JetBrainsMono("jetbrains_mono", "JetBrains Mono"),
    FiraCode("fira_code", "Fira Code");

    companion object {
        val DEFAULT = Nunito

        fun fromId(id: String?): InputFontChoice =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}

/**
 * Resolved [FontFamily] applied to input fields (and body text). Any text field that sets an
 * explicit `textStyle` without a `fontFamily` can read this local to stay in sync with the
 * user's choice, and Material's [LocalTextStyle] is also seeded with it inside [ChronoSSHTheme].
 * `null` means "use the platform default typeface" (the System choice).
 */
val LocalInputFontFamily = staticCompositionLocalOf<FontFamily?> { null }

/** Builds the [FontFamily] for a given [InputFontChoice], or `null` for the platform default. */
fun inputFontFamilyFor(choice: InputFontChoice): FontFamily? = when (choice) {
    InputFontChoice.System -> null
    InputFontChoice.Nunito -> FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.nunito_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.nunito_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.nunito_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.nunito_black, FontWeight.Black, FontStyle.Normal)
    )
    InputFontChoice.JetBrainsMono -> FontFamily(Font(R.font.jetbrains_mono_regular))
    InputFontChoice.FiraCode -> FontFamily(Font(R.font.fira_code_regular))
}

@Composable
fun ChronoSSHTheme(
    palette: DeckPalette = DeckThemeCatalog.paletteFor(
        mode = DeckThemeMode.System,
        familyId = DeckThemeCatalog.DEFAULT_FAMILY_ID,
        systemDark = isSystemInDarkTheme()
    ),
    headingFonts: HeadingFontFamilies? = null,
    accentOverrideHex: String? = null,
    inputFontId: String? = InputFontChoice.DEFAULT.id,
    content: @Composable () -> Unit
) {
    DeckColors.apply(palette)
    accentOverrideHex?.toDeckColorOrNull()?.let { DeckColors.Accent = it }
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.cyan,
            onPrimary = palette.primaryText,
            background = palette.background,
            onBackground = palette.primaryText,
            surface = palette.surface,
            onSurface = palette.primaryText,
            secondary = palette.orange,
            tertiary = palette.green
        )
    } else {
        lightColorScheme(
            primary = palette.cyan,
            onPrimary = palette.primaryText,
            background = palette.background,
            onBackground = palette.primaryText,
            surface = palette.surface,
            onSurface = palette.primaryText,
            secondary = palette.orange,
            tertiary = palette.green
        )
    }

    val chronoHeadingFont = FontFamily(
        Font(R.font.nimbus_sans_l_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.nimbus_sans_l_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.nimbus_sans_l_bold, FontWeight.Black, FontStyle.Normal)
    )
    val chronoUiFont = FontFamily(
        Font(R.font.nunito_regular, FontWeight.Normal, FontStyle.Normal),
        Font(R.font.nunito_medium, FontWeight.Medium, FontStyle.Normal),
        Font(R.font.nunito_semibold, FontWeight.SemiBold, FontStyle.Normal),
        Font(R.font.nunito_bold, FontWeight.Bold, FontStyle.Normal),
        Font(R.font.nunito_extrabold, FontWeight.ExtraBold, FontStyle.Normal),
        Font(R.font.nunito_black, FontWeight.Black, FontStyle.Normal)
    )

    // Resolve the user-selected input font. Defaults to Nunito so text boxes match the app out
    // of the box; `null` means the platform default typeface (the "System" choice).
    val inputFontChoice = InputFontChoice.fromId(inputFontId)
    val inputFontFamily: FontFamily? = inputFontFamilyFor(inputFontChoice)

    val appTypography = Typography().let { base ->
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = chronoHeadingFont, fontWeight = FontWeight.Black),
            displayMedium = base.displayMedium.copy(fontFamily = chronoHeadingFont, fontWeight = FontWeight.Black),
            displaySmall = base.displaySmall.copy(fontFamily = chronoHeadingFont, fontWeight = FontWeight.Black),
            headlineLarge = base.headlineLarge.copy(fontFamily = chronoHeadingFont, fontWeight = FontWeight.Black),
            headlineMedium = base.headlineMedium.copy(fontFamily = chronoHeadingFont, fontWeight = FontWeight.Black),
            headlineSmall = base.headlineSmall.copy(fontFamily = chronoHeadingFont, fontWeight = FontWeight.Bold),
            titleLarge = base.titleLarge.copy(fontFamily = chronoUiFont, fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontFamily = chronoUiFont, fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontFamily = chronoUiFont, fontWeight = FontWeight.Medium),
            bodyLarge = base.bodyLarge.copy(fontFamily = inputFontFamily, fontWeight = FontWeight.Normal),
            bodyMedium = base.bodyMedium.copy(fontFamily = inputFontFamily, fontWeight = FontWeight.Normal),
            bodySmall = base.bodySmall.copy(fontFamily = inputFontFamily, fontWeight = FontWeight.Normal),
            labelLarge = base.labelLarge.copy(fontFamily = inputFontFamily, fontWeight = FontWeight.SemiBold),
            labelMedium = base.labelMedium.copy(fontFamily = chronoUiFont, fontWeight = FontWeight.Medium),
            labelSmall = base.labelSmall.copy(fontFamily = chronoUiFont, fontWeight = FontWeight.Medium)
        )
    }

    // Text fields (OutlinedTextField/TextField) default their `textStyle` to LocalTextStyle.current,
    // which Material3 otherwise leaves as an empty TextStyle (no fontFamily) — so they fall back to
    // the platform typeface even though typography.bodyLarge is Nunito. Seed LocalTextStyle with the
    // chosen font family so text boxes (and any default-styled Text) inherit it. Only the family is
    // provided to avoid overriding per-widget font sizes/spacing. For the "System" choice this is a
    // null family, i.e. back to the platform default.
    val inputTextStyle = androidx.compose.ui.text.TextStyle(fontFamily = inputFontFamily)

    CompositionLocalProvider(
        LocalHeadingFontFamilies provides (headingFonts?.copy(fallback = chronoHeadingFont) ?: HeadingFontFamilies(chronoHeadingFont)),
        LocalInputFontFamily provides inputFontFamily
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography
        ) {
            CompositionLocalProvider(LocalTextStyle provides inputTextStyle, content = content)
        }
    }
}

private fun String.toDeckColorOrNull(): Color? {
    val value = trim()
    if (!Regex("^#[0-9A-Fa-f]{6}$").matches(value)) return null
    return Color(value.drop(1).toLong(16) or 0xff000000)
}
