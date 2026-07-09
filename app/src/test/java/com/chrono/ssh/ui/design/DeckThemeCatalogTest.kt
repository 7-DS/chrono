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
        assertEquals(DeckThemeCatalog.families.first { it.id == "catppuccin" }.dark.metricCpu, DeckThemeCatalog.families.first { it.id == "catppuccin" }.dark.brandAlt)
        assertEquals(DeckThemeCatalog.families.first { it.id == "comic-ink" }.light.metricCpu, DeckThemeCatalog.families.first { it.id == "comic-ink" }.light.metricDisk)
    }
}
