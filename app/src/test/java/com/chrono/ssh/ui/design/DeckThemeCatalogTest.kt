package com.chrono.ssh.ui.design

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
}
