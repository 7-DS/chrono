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
    fun palettesCarryThemeSpecificMetricAccents() {
        DeckThemeCatalog.families.forEach { family ->
            listOf(family.light, family.dark).forEach { palette ->
                assertTrue(palette.metricCpu != Color.Unspecified)
                assertTrue(palette.metricMemory != Color.Unspecified)
                assertTrue(palette.metricDisk != Color.Unspecified)
                assertTrue(palette.metricNetwork != Color.Unspecified)
            }
        }
        assertEquals(Color(0xFFF5B971), DeckThemeCatalog.families.first { it.id == "catppuccin" }.dark.metricDisk)
        assertEquals(4, DeckThemeCatalog.families.first { it.id == "comic-ink" }.light.metricColors().distinct().size)
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
                    val role = listOf("cpu", "memory", "disk", "network")[index]
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
}

private fun DeckPalette.metricColors(): List<Color> = listOf(metricCpu, metricMemory, metricDisk, metricNetwork)

private fun colorDistance(first: Color, second: Color): Float {
    val red = first.red - second.red
    val green = first.green - second.green
    val blue = first.blue - second.blue
    return kotlin.math.sqrt(red * red + green * green + blue * blue)
}
