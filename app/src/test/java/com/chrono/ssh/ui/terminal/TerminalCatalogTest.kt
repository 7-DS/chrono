package com.chrono.ssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalCatalogTest {
    @Test
    fun terminalThemeCatalogIncludesMainstreamAnsiPalettes() {
        assertTrue(TerminalCatalog.themes.size >= 48)
        assertTrue(TerminalCatalog.themes.any { it.name == "Termius Dark" })
        assertTrue(TerminalCatalog.themes.any { it.name == "Tokyo Night" })
        assertTrue(TerminalCatalog.themes.any { it.name == "Catppuccin Mocha" })
        assertTrue(TerminalCatalog.themes.any { it.name == "Xterm Classic" })
        assertTrue(TerminalCatalog.themes.any { it.name == "Tango Dark" })
        assertTrue(TerminalCatalog.themes.any { it.name == "Tomorrow Night" })
        assertTrue(TerminalCatalog.themes.any { it.name == "Base16 Default Dark" })
        TerminalCatalog.themes.forEach { theme ->
            assertEquals(theme.name, 16, theme.ansiColorsHex.size)
        }
    }

    @Test
    fun terminalThemeCatalogNamesAreUnique() {
        assertEquals(
            TerminalCatalog.themes.map { it.name }.sorted(),
            TerminalCatalog.themes.map { it.name }.distinct().sorted()
        )
    }

    @Test
    fun terminalThemeCatalogUsesValidHexColors() {
        val hexColor = Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?")
        TerminalCatalog.themes.forEach { theme ->
            val colors = listOf(
                theme.foregroundHex,
                theme.backgroundHex,
                theme.cursorHex,
                theme.selectionHex
            ) + theme.ansiColorsHex
            colors.forEach { color ->
                assertTrue("${theme.name} has invalid color $color", hexColor.matches(color))
            }
        }
    }

    @Test
    fun terminalFontCatalogOnlyExposesBundledFonts() {
        assertEquals(
            listOf("JetBrains Mono", "Fira Code", "Hack", "Geist Mono", "Atkinson Mono Nerd"),
            TerminalCatalog.fonts.map { it.name }
        )
        assertEquals(TerminalCatalog.fonts.map { it.name }, TerminalCatalog.fonts.map { it.name }.distinct())
    }
}
