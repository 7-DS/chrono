package com.chrono.ssh.ui.design

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeckThemeCatalogTest {
    @Test
    fun appThemeCatalogKeepsDefaultAndFortyPlusFamilies() {
        assertEquals("graphite", DeckThemeCatalog.DEFAULT_FAMILY_ID)
        assertTrue(DeckThemeCatalog.families.size >= 40)
        assertTrue(DeckThemeCatalog.families.any { it.id == "graphite" })
        assertTrue(DeckThemeCatalog.families.any { it.name == "Slate" })
        assertEquals(DeckThemeCatalog.families.map { it.id }, DeckThemeCatalog.families.map { it.id }.distinct())
    }

    @Test
    fun missingThemeFamilyFallsBackSafely() {
        val fallback = DeckThemeCatalog.paletteFor(DeckThemeMode.Light, "missing", systemDark = false)

        assertEquals(DeckThemeCatalog.families.first().light, fallback)
    }

    @Test
    fun themeFamiliesFollowResolvedLightDarkMode() {
        val lightFamilies = DeckThemeCatalog.familiesFor(DeckThemeMode.Light, systemDark = false)
        val darkFamilies = DeckThemeCatalog.familiesFor(DeckThemeMode.Dark, systemDark = false)

        assertTrue(lightFamilies.any { it.id == "monochrome-light" })
        assertTrue(lightFamilies.any { it.id == "comic-ink" })
        assertTrue(lightFamilies.none { it.id == "monochrome-dark" })
        assertTrue(darkFamilies.any { it.id == "monochrome-dark" })
        assertTrue(darkFamilies.none { it.id == "monochrome-light" })
        assertTrue(darkFamilies.none { it.id == "comic-ink" })
    }

    @Test
    fun palettesCarryProfessionalMetricAccents() {
        DeckThemeCatalog.families.forEach { family ->
            listOf(family.light, family.dark).forEach { palette ->
                assertTrue(palette.metricCpu != Color.Unspecified)
                assertTrue(palette.metricMemory != Color.Unspecified)
                assertTrue(palette.metricDisk != Color.Unspecified)
                assertTrue(palette.metricNetwork != Color.Unspecified)
                assertTrue(palette.metricLatency != Color.Unspecified)
            }
        }
        assertEquals(Color(0xFFF2A93B), DeckThemeCatalog.families.first { it.id == "catppuccin" }.dark.metricDisk)
        assertEquals(5, DeckThemeCatalog.families.first { it.id == "comic-ink" }.light.metricColors().distinct().size)
    }

    @Test
    fun generatedAppThemesUseAuthoredMetricAccents() {
        val palettesUsingOldCpuAccent = mutableListOf<String>()
        val palettesUsingOldMetricSet = mutableListOf<String>()

        DeckThemeCatalog.families
            .filterNot { it.id in setOf("aurora", "graphite", "ember", "catppuccin", "rosepine", "monochrome-light", "monochrome-dark", "comic-ink") }
            .forEach { family ->
                listOf(family.light, family.dark).forEach { palette ->
                    if (palette.metricCpu == palette.brandAlt) palettesUsingOldCpuAccent += palette.id
                    if (palette.metricMemory == palette.green && palette.metricDisk == palette.yellow && palette.metricNetwork == palette.purple) {
                        palettesUsingOldMetricSet += palette.id
                    }
                }
            }
        assertTrue("Old CPU accent mapping: $palettesUsingOldCpuAccent", palettesUsingOldCpuAccent.isEmpty())
        assertTrue("Old metric set mapping: $palettesUsingOldMetricSet", palettesUsingOldMetricSet.isEmpty())
    }

    @Test
    fun everyPaletteKeepsMetricsVisibleOnCards() {
        val weak = DeckThemeCatalog.families
            .flatMap { listOf(it.light, it.dark) }
            .flatMap { palette ->
                palette.metricColors().mapIndexedNotNull { index, color ->
                    val role = listOf("cpu", "memory", "disk", "network", "latency")[index]
                    val surfaceDistance = colorDistance(color, palette.surface)
                    val mutedDistance = colorDistance(color, palette.surfaceMuted)
                    if (surfaceDistance < 0.30f || mutedDistance < 0.30f) {
                        "${palette.id}:$role surface=$surfaceDistance muted=$mutedDistance"
                    } else {
                        null
                    }
                }
            }

        assertTrue("Weak metric blends: $weak", weak.isEmpty())
    }

    @Test
    fun everyPaletteKeepsServerDetailAccentsDistinct() {
        val tooClose = DeckThemeCatalog.families
            .flatMap { listOf(it.light, it.dark) }
            .flatMap { palette ->
                val metricColors = listOf(
                    "cpu" to palette.metricCpu,
                    "memory" to palette.metricMemory,
                    "disk" to palette.metricDisk,
                    "network" to palette.metricNetwork,
                    "latency" to palette.metricLatency
                )
                val cpuColors = cpuUsageColorsFor(
                    ServerMetricColors(
                        cpu = palette.metricCpu,
                        memory = palette.metricMemory,
                        disk = palette.metricDisk,
                        network = palette.metricNetwork,
                        latency = palette.metricLatency
                    )
                )
                val chartColors = listOf(
                    "user" to cpuColors.user,
                    "system" to cpuColors.system,
                    "nice" to cpuColors.nice,
                    "iowait" to cpuColors.ioWait,
                    "steal" to cpuColors.steal
                )
                metricColors.closePairsFor(palette.id) + chartColors.closePairsFor("${palette.id}:cpu-chart")
            }

        assertTrue("Server detail accents too similar: $tooClose", tooClose.isEmpty())
    }

    private fun List<Pair<String, Color>>.closePairsFor(scope: String): List<String> =
        flatMapIndexed { index, first ->
            drop(index + 1).mapNotNull { second ->
                val distance = perceptualDistance(first.second, second.second)
                val hue = hueDistance(first.second, second.second)
                if (distance < 0.16f || hue < 28f) "$scope:${first.first}/${second.first}=distance:$distance hue:$hue" else null
            }
        }

    @Test
    fun everyPaletteKeepsPrimaryAccentsVisible() {
        val weak = DeckThemeCatalog.families
            .flatMap { listOf(it.light, it.dark) }
            .flatMap { palette ->
                listOf("cyan" to palette.cyan, "brandAlt" to palette.brandAlt).mapNotNull { (role, color) ->
                    val surfaceDistance = colorDistance(color, palette.surface)
                    val mutedDistance = colorDistance(color, palette.surfaceMuted)
                    if (surfaceDistance < 0.30f || mutedDistance < 0.30f) {
                        "${palette.id}:$role surface=$surfaceDistance muted=$mutedDistance"
                    } else {
                        null
                    }
                }
            }

        assertTrue("Weak primary accents: $weak", weak.isEmpty())
    }

    @Test
    fun everyPaletteKeepsCardStructureVisible() {
        val weak = DeckThemeCatalog.families
            .flatMap { listOf(it.light, it.dark) }
            .flatMap { palette ->
                listOf(
                    "divider" to colorDistance(palette.divider, palette.surface),
                    "cardStroke" to colorDistance(palette.cardStroke, palette.surface)
                ).mapNotNull { (role, distance) ->
                    if (distance < 0.16f) "${palette.id}:$role=$distance" else null
                }
            }

        assertTrue("Weak structural colors: $weak", weak.isEmpty())
    }

    @Test
    fun generatedThemesHaveDistinctElementSurfaces() {
        val generated = DeckThemeCatalog.families
            .filterNot { it.id in setOf("aurora", "graphite", "ember", "catppuccin", "rosepine", "monochrome-light", "monochrome-dark", "comic-ink") }

        val lightSurfaceCount = generated.map { colorKey(it.light.surface) }.distinct().size
        val darkSurfaceCount = generated.map { colorKey(it.dark.surface) }.distinct().size

        assertTrue("Generated light themes reuse too few element surfaces", lightSurfaceCount >= generated.size / 2)
        assertTrue("Generated dark themes reuse too few element surfaces", darkSurfaceCount >= generated.size / 2)
    }
}

private fun DeckPalette.metricColors(): List<Color> = listOf(metricCpu, metricMemory, metricDisk, metricNetwork, metricLatency)

private fun colorDistance(first: Color, second: Color): Float {
    val red = first.red - second.red
    val green = first.green - second.green
    val blue = first.blue - second.blue
    return kotlin.math.sqrt(red * red + green * green + blue * blue)
}

private fun colorKey(color: Color): String =
    "${(color.red * 255).toInt()}:${(color.green * 255).toInt()}:${(color.blue * 255).toInt()}"

private fun perceptualDistance(first: Color, second: Color): Float {
    val a = first.toOklab()
    val b = second.toOklab()
    val l = a[0] - b[0]
    val m = a[1] - b[1]
    val s = a[2] - b[2]
    return kotlin.math.sqrt(l * l + m * m + s * s)
}

private fun hueDistance(first: Color, second: Color): Float {
    val firstHue = first.hue()
    val secondHue = second.hue()
    val delta = kotlin.math.abs(firstHue - secondHue)
    return kotlin.math.min(delta, 360f - delta)
}

private fun Color.hue(): Float {
    val max = maxOf(red, green, blue)
    val min = minOf(red, green, blue)
    val delta = max - min
    if (delta == 0f) return 0f
    val raw = when (max) {
        red -> ((green - blue) / delta) % 6f
        green -> (blue - red) / delta + 2f
        else -> (red - green) / delta + 4f
    } * 60f
    return if (raw < 0f) raw + 360f else raw
}

private fun Color.toOklab(): FloatArray {
    fun linear(value: Float): Float =
        if (value <= 0.04045f) value / 12.92f else Math.pow(((value + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()

    val r = linear(red)
    val g = linear(green)
    val b = linear(blue)
    val l = Math.cbrt((0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b).toDouble()).toFloat()
    val m = Math.cbrt((0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b).toDouble()).toFloat()
    val s = Math.cbrt((0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b).toDouble()).toFloat()
    return floatArrayOf(
        0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
        1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
        0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
    )
}
