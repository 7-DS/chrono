package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.CrashLogEntry
import com.chrono.ssh.core.model.ServerCardDiskMode
import com.chrono.ssh.core.model.ServerCardNetworkMode
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.data.sanitizeColorHex
import com.chrono.ssh.core.data.sanitizeHeadingFontPath
import com.chrono.ssh.core.model.ServerMetricColorPreset
import com.chrono.ssh.ui.design.ServerMetricColorOverrides
import com.chrono.ssh.ui.design.metricColorsFor
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SettingsScreenTest {
    @Test
    fun monitoringSettingsSummaryMentionsServerCardModes() {
        assertEquals(
            "2s · net speed · disk usage · Theme",
            monitoringSettingsSummary(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "graphite",
                    autoRefreshSeconds = 2,
                    serverCardNetworkMode = ServerCardNetworkMode.Rates,
                    serverCardDiskMode = ServerCardDiskMode.Usage
                )
            )
        )
    }

    @Test
    fun metricColorPresetLabelsCoverAllChoices() {
        assertEquals(
            "Theme, Custom, Blue / Green, Steel / Sage, Graphite, Cobalt / Lime, Ocean Teal, Olive / Teal, Clay / Moss, Aurora, Orchid, Nordic, Solar, Circuit, Harvest, Lagoon, Metro, Mono",
            ServerMetricColorPreset.entries.joinToString { it.label() }
        )
    }

    @Test
    fun metricColorPresetRoleSummariesCoverAllChoices() {
        assertEquals(
            "Uses the active app theme accent colors | Manual CPU, RAM, disk and network colors | CPU/Net blue · RAM green · Disk amber | CPU steel · RAM sage · Disk tan · Net teal | Muted neutral rings for low-contrast themes | Bright CPU, RAM, disk and network separation | Blue CPU · green RAM · amber disk · cyan network | Olive CPU · teal RAM/network · warm disk | Clay CPU · moss RAM · brass disk · cool network | Blue CPU · emerald RAM · orange disk · violet network | Violet CPU · magenta RAM · ochre disk · cyan network | Frost blue CPU · sage RAM · copper disk · ice network | Blue CPU · green RAM · yellow disk · red network | Teal CPU · lime RAM · amber disk · indigo network | Ochre CPU · olive RAM · brass disk · teal network | Sky CPU · emerald RAM · violet disk · cyan network | Blue CPU · magenta RAM · orange disk · green network | Single-family neutral rings",
            ServerMetricColorPreset.entries.joinToString(" | ") { it.roleSummary() }
        )
    }

    @Test
    fun metricPresetSelectionContentDescriptionDistinguishesSelectedState() {
        assertEquals("Selected metric color preset", metricPresetSelectionContentDescription(true))
        assertEquals("Metric color preset", metricPresetSelectionContentDescription(false))
    }

    @Test
    fun metricColorPresetPaletteCoversAllChoices() {
        ServerMetricColorPreset.entries.forEach { preset ->
            assertNotNull(metricColorsFor(preset))
        }
    }

    @Test
    fun themeMetricPresetUsesVisibleAppAccentRoles() {
        val colors = metricColorsFor(ServerMetricColorPreset.Theme)

        assertNotNull(colors.cpu)
        assertNotNull(colors.memory)
        assertNotNull(colors.disk)
        assertNotNull(colors.network)
    }

    @Test
    fun customMetricPresetUsesIndependentOverrides() {
        val colors = metricColorsFor(
            ServerMetricColorPreset.Custom,
            ServerMetricColorOverrides(cpuHex = "#111111", memoryHex = "#222222", diskHex = "#333333", networkHex = "#444444")
        )

        assertEquals(Color(0xff111111), colors.cpu)
        assertEquals(Color(0xff222222), colors.memory)
        assertEquals(Color(0xff333333), colors.disk)
        assertEquals(Color(0xff444444), colors.network)
    }

    @Test
    fun customMetricSelectionSeedsThemeColorsWhenEmpty() {
        val seeded = customMetricColorOverridesForSelection(ServerMetricColorOverrides())

        assertNotNull(seeded.cpuHex)
        assertNotNull(seeded.memoryHex)
        assertNotNull(seeded.diskHex)
        assertNotNull(seeded.networkHex)
    }

    @Test
    fun customMetricSelectionPreservesExistingOverrides() {
        val seeded = customMetricColorOverridesForSelection(ServerMetricColorOverrides(cpuHex = "#111111"))

        assertEquals("#111111", seeded.cpuHex)
        assertNotNull(seeded.memoryHex)
        assertNotNull(seeded.diskHex)
        assertNotNull(seeded.networkHex)
    }

    @Test
    fun metricColorSelectionSeedsEmptyCustomOverridesOnSave() {
        val result = settingsAfterSelection(
            page = SettingsSelectionPage.MetricColors,
            settings = AppSettings(themeModeName = "System", themeFamilyId = "graphite"),
            appThemeId = "graphite",
            terminalThemeName = "Tokyo Night",
            terminalFontFamily = "JetBrains Mono",
            metricColorPreset = ServerMetricColorPreset.Custom,
            metricColorOverrides = ServerMetricColorOverrides()
        )

        assertEquals(ServerMetricColorPreset.Custom, result.settings?.serverMetricColorPreset)
        assertNotNull(result.settings?.serverMetricCpuColorHex)
        assertNotNull(result.settings?.serverMetricMemoryColorHex)
        assertNotNull(result.settings?.serverMetricDiskColorHex)
        assertNotNull(result.settings?.serverMetricNetworkColorHex)
    }

    @Test
    fun metricColorSelectionPersistsCustomOverrides() {
        val result = settingsAfterSelection(
            page = SettingsSelectionPage.MetricColors,
            settings = AppSettings(themeModeName = "System", themeFamilyId = "graphite"),
            appThemeId = "graphite",
            terminalThemeName = "Tokyo Night",
            terminalFontFamily = "JetBrains Mono",
            metricColorPreset = ServerMetricColorPreset.Custom,
            metricColorOverrides = ServerMetricColorOverrides(
                cpuHex = "#111111",
                memoryHex = "#222222",
                diskHex = "#333333",
                networkHex = "#444444"
            )
        )

        assertEquals(ServerMetricColorPreset.Custom, result.settings?.serverMetricColorPreset)
        assertEquals("#111111", result.settings?.serverMetricCpuColorHex)
        assertEquals("#222222", result.settings?.serverMetricMemoryColorHex)
        assertEquals("#333333", result.settings?.serverMetricDiskColorHex)
        assertEquals("#444444", result.settings?.serverMetricNetworkColorHex)
    }

    @Test
    fun appAndTerminalThemeSelectionsStaySeparate() {
        val settings = AppSettings(
            themeModeName = "System",
            themeFamilyId = "graphite",
            terminalThemeName = "Tokyo Night",
            terminalFontFamily = "JetBrains Mono",
            serverMetricColorPreset = ServerMetricColorPreset.Classic
        )

        val appTheme = settingsAfterSelection(
            page = SettingsSelectionPage.AppTheme,
            settings = settings,
            appThemeId = "github-light",
            terminalThemeName = "Dracula",
            terminalFontFamily = "Hack",
            metricColorPreset = ServerMetricColorPreset.Mono
        )
        val terminalTheme = settingsAfterSelection(
            page = SettingsSelectionPage.TerminalTheme,
            settings = settings,
            appThemeId = "github-light",
            terminalThemeName = "Dracula",
            terminalFontFamily = "Hack",
            metricColorPreset = ServerMetricColorPreset.Mono
        )

        assertEquals("github-light", appTheme.themeFamilyId)
        assertEquals(null, appTheme.settings)
        assertEquals(null, terminalTheme.themeFamilyId)
        assertEquals("Dracula", terminalTheme.settings?.terminalThemeName)
        assertEquals("graphite", terminalTheme.settings?.themeFamilyId)
    }

    @Test
    fun connectionDiagnosticsCopyTextIncludesExactEvents() {
        val events = listOf(
            event("server-1", ConnectionEventLevel.Error, 10, "Auth failed"),
            event("server-2", ConnectionEventLevel.Warning, 20, "vnStat: missing")
        )

        assertEquals(
            "Error\nserver-1\n10\nAuth failed\n\nWarning\nserver-2\n20\nvnStat: missing",
            connectionDiagnosticsCopyText(events)
        )
    }

    @Test
    fun connectionDiagnosticCopyTextKeepsMultilineMessage() {
        assertEquals(
            "Info\nserver-1\n30\nfirst\nsecond",
            event("server-1", ConnectionEventLevel.Info, 30, "first\nsecond").copyText()
        )
    }

    @Test
    fun backupPassphraseConfirmationOnlyRequiredForEncryptedExport() {
        assertEquals(false, backupPassphraseConfirmEnabled("", "", requireConfirmation = true))
        assertEquals(false, backupPassphraseConfirmEnabled("secret", "", requireConfirmation = true))
        assertEquals(false, backupPassphraseConfirmEnabled("secret", "different", requireConfirmation = true))
        assertEquals(true, backupPassphraseConfirmEnabled("secret", "secret", requireConfirmation = true))
        assertEquals(true, backupPassphraseConfirmEnabled("secret", "", requireConfirmation = false))
    }

    @Test
    fun biometricToggleOnlyEnablesUnavailableBiometricsForTurnOff() {
        assertEquals(false, biometricToggleEnabled(currentlyEnabled = false, biometricAvailable = false))
        assertEquals(true, biometricToggleEnabled(currentlyEnabled = true, biometricAvailable = false))
        assertEquals(true, biometricToggleEnabled(currentlyEnabled = false, biometricAvailable = true))
    }

    @Test
    fun biometricUnavailableMessageOnlyShowsForEnabledBiometricUnlock() {
        assertEquals(false, showBiometricUnavailableMessage(appLockEnabled = false, biometricEnabled = true, biometricAvailable = false))
        assertEquals(false, showBiometricUnavailableMessage(appLockEnabled = true, biometricEnabled = false, biometricAvailable = false))
        assertEquals(false, showBiometricUnavailableMessage(appLockEnabled = true, biometricEnabled = true, biometricAvailable = true))
        assertEquals(true, showBiometricUnavailableMessage(appLockEnabled = true, biometricEnabled = true, biometricAvailable = false))
    }

    @Test
    fun pendingSecuritySettingsStayVisibleOnlyForSecurityChanges() {
        val committed = AppSettings("System", "default")
        val pending = committed.copy(appLockPinHash = "hash", appLockPinSalt = "salt")

        assertEquals(true, settingsSecurityStateCanStayVisible(pending, committed))
        assertEquals(false, settingsSecurityStateCanStayVisible(pending, pending))
        assertEquals(true, settingsSecurityStateCanStayVisible(pending, committed.copy(themeFamilyId = "other")))
    }

    @Test
    fun headingFontPathSanitizerOnlyKeepsFontFiles() {
        assertEquals("/tmp/home.ttf", sanitizeHeadingFontPath(" /tmp/home.ttf "))
        assertEquals("/tmp/home.otf", sanitizeHeadingFontPath("/tmp/home.otf"))
        assertEquals(null, sanitizeHeadingFontPath("/tmp/home.zip"))
        assertEquals(null, sanitizeHeadingFontPath("../home.ttf"))
    }

    @Test
    fun metricColorHexSanitizerKeepsOnlySixDigitHex() {
        assertEquals("#AABBCC", sanitizeColorHex(" aabbcc "))
        assertEquals("#AABBCC", sanitizeColorHex("#AaBbCc"))
        assertEquals(null, sanitizeColorHex("#abcd"))
        assertEquals(null, sanitizeColorHex("#gggggg"))
    }

    @Test
    fun crashLogCopyTextIncludesFullMultilineDetails() {
        assertEquals(
            "java.lang.ExceptionInInitializerError\nmain\n1782874683691\nterminal failed\n\nline1\nline2",
            crashLogCopyText(
                CrashLogEntry(
                    id = "crash-1",
                    atEpochMillis = 1782874683691L,
                    threadName = "main",
                    throwableClass = "java.lang.ExceptionInInitializerError",
                    message = "terminal failed",
                    stackTrace = "line1\nline2"
                )
            )
        )
    }

    private fun event(
        serverId: String,
        level: ConnectionEventLevel,
        at: Long,
        message: String
    ): ConnectionEvent {
        return ConnectionEvent(
            id = "$serverId-$at",
            serverId = serverId,
            atEpochMillis = at,
            level = level,
            message = message
        )
    }
}
