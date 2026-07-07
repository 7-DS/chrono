package com.chrono.ssh.core.data

import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.model.EternalTerminalConfig
import com.chrono.ssh.core.model.FileProtocolConfig
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.MoshConfig
import com.chrono.ssh.core.model.ProotProfileConfig
import com.chrono.ssh.core.model.RdpProfileConfig
import com.chrono.ssh.core.model.ReconnectPolicy
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerCardDiskMode
import com.chrono.ssh.core.model.ServerCardNetworkMode
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.TerminalCursorStyle
import com.chrono.ssh.core.model.VncProfileConfig
import com.chrono.ssh.core.service.PinLockPolicy
import com.chrono.ssh.core.service.TerminalAccessoryKeyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportReportFormatterTest {
    @Test
    fun inspectedMessageReportsMalformedUnknownAndMetadataOnlyCredentials() {
        val message = BackupImportReportFormatter.inspected(
            recordCount = 12,
            malformedRows = 2,
            unknownRows = 1,
            credentialMetadataRows = 3
        )

        assertTrue(message.contains("12 metadata records"))
        assertTrue(message.contains("2 malformed"))
        assertTrue(message.contains("1 unknown-section"))
        assertTrue(message.contains("3 credential identities"))
        assertTrue(message.contains("replace"))
    }

    @Test
    fun importedMessageExplainsCredentialSecretsNeedVaultReplacement() {
        val message = BackupImportReportFormatter.imported(credentialMetadataRows = 2)

        assertTrue(message.contains("Metadata imported"))
        assertTrue(message.contains("2 credential identities"))
        assertTrue(message.contains("Vault"))
    }

    @Test
    fun backupImportTextPolicyRejectsOversizedImports() {
        assertNull(BackupImportTextPolicy.rejectionMessage("chronoSSH-backup-v1\n[servers]\n"))
        assertEquals("Backup is too large to import.", BackupImportTextPolicy.rejectionMessage("x".repeat(2_000_001)))
        assertEquals("Backup has too many rows to import.", BackupImportTextPolicy.rejectionMessage((1..5_001).joinToString("\n") { "row" }))
    }

    @Test
    fun settingsImportPreservesExistingAppLockPin() {
        val existing = AppSettings(
            themeModeName = "System",
            themeFamilyId = "default",
            appLockPinHash = "hash",
            appLockPinSalt = "salt"
        )
        val imported = BackupSettingsImportPolicy.importedSettings(
            listOf("Dark", "term", "16", "24000", TerminalCursorStyle.Beam.name, "Tokyo Night", "JetBrains Mono", "true", "false", "2"),
            existing
        )

        assertNotNull(imported)
        assertEquals("Dark", imported!!.themeModeName)
        assertEquals("hash", imported.appLockPinHash)
        assertEquals("salt", imported.appLockPinSalt)
        assertEquals(TerminalAccessoryKeyPolicy.DefaultCsv, imported.terminalAccessoryKeys)
    }

    @Test
    fun settingsImportSanitizesUntrustedDisplayTokens() {
        val imported = BackupSettingsImportPolicy.importedSettings(
            listOf(
                " Dark;rm -rf / ",
                "../../evil-theme-with-a-name-that-is-way-too-long-for-a-settings-token-and-keeps-going",
                "99",
                "999999",
                "UnknownCursor",
                "Tokyo Night <script>",
                "JetBrains Mono\nInjected",
                "not-bool",
                "not-bool",
                "Esc,F12,bad",
                "999"
            ),
            AppSettings(themeModeName = "System", themeFamilyId = "default")
        )

        assertNotNull(imported)
        assertEquals("Darkrm -rf", imported!!.themeModeName)
        assertEquals("....evil-theme-with-a-name-that-is-way-too-long-for-a-settings-t", imported.themeFamilyId)
        assertEquals(24, imported.terminalFontSizeSp)
        assertEquals(50000, imported.terminalScrollbackLines)
        assertEquals(TerminalCursorStyle.Block, imported.terminalCursorStyle)
        assertEquals("Tokyo Night script", imported.terminalThemeName)
        assertEquals("JetBrains MonoInjected", imported.terminalFontFamily)
        assertEquals(true, imported.terminalBracketedPaste)
        assertEquals(true, imported.terminalHapticFeedback)
        assertEquals("Esc,F12", imported.terminalAccessoryKeys)
        assertEquals(2, imported.autoRefreshSeconds)
    }

    @Test
    fun settingsImportKeepsAccessoryKeyRowsWhenPresent() {
        val imported = BackupSettingsImportPolicy.importedSettings(
            listOf("Dark", "term", "16", "24000", TerminalCursorStyle.Beam.name, "Tokyo Night", "JetBrains Mono", "true", "false", "Esc,F12,bad", "2"),
            AppSettings(themeModeName = "System", themeFamilyId = "default")
        )

        assertNotNull(imported)
        assertEquals("Esc,F12", imported!!.terminalAccessoryKeys)
        assertEquals(2, imported.autoRefreshSeconds)
    }

    @Test
    fun settingsImportRestoresServerCardMetricModesWhenPresent() {
        val imported = BackupSettingsImportPolicy.importedSettings(
            listOf("Dark", "term", "16", "24000", TerminalCursorStyle.Beam.name, "Tokyo Night", "JetBrains Mono", "true", "false", "Esc,F12", "2", "4", "Rates", "Totals"),
            AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                serverCardNetworkMode = ServerCardNetworkMode.Totals,
                serverCardDiskMode = ServerCardDiskMode.Usage
            )
        )

        assertNotNull(imported)
        assertEquals(4, imported!!.terminalSideMarginDp)
        assertEquals(ServerCardNetworkMode.Rates, imported.serverCardNetworkMode)
        assertEquals(ServerCardDiskMode.Totals, imported.serverCardDiskMode)
    }

    @Test
    fun settingsImportRestoresTerminalKeepScreenOnWhenPresent() {
        val imported = BackupSettingsImportPolicy.importedSettings(
            listOf("Dark", "term", "16", "24000", TerminalCursorStyle.Beam.name, "Tokyo Night", "JetBrains Mono", "true", "false", "Esc,F12", "2", "4", "true", "Rates", "Totals"),
            AppSettings(
                themeModeName = "System",
                themeFamilyId = "default",
                terminalKeepScreenOn = false,
                serverCardNetworkMode = ServerCardNetworkMode.Totals,
                serverCardDiskMode = ServerCardDiskMode.Usage
            )
        )

        assertNotNull(imported)
        assertEquals(true, imported!!.terminalKeepScreenOn)
        assertEquals(ServerCardNetworkMode.Rates, imported.serverCardNetworkMode)
        assertEquals(ServerCardDiskMode.Totals, imported.serverCardDiskMode)
    }

    @Test
    fun loadedSettingsDropCorruptedAppLockPin() {
        val valid = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")

        assertNull(
            sanitizeLoadedSettings(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = "hash",
                    appLockPinSalt = "not-base64",
                    appLockBiometricEnabled = true
                )
            ).appLockPinHash
        )
        assertNull(
            sanitizeLoadedSettings(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = "hash",
                    appLockPinSalt = "",
                    appLockBiometricEnabled = true
                )
            ).appLockPinHash
        )
        assertNull(
            sanitizeLoadedSettings(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = valid.hash,
                    appLockPinSalt = "AA",
                    appLockBiometricEnabled = true
                )
            ).appLockPinHash
        )
        assertEquals(
            valid.hash,
            sanitizeLoadedSettings(
                AppSettings(
                    themeModeName = "System",
                    themeFamilyId = "default",
                    appLockPinHash = valid.hash,
                    appLockPinSalt = valid.salt,
                    appLockBiometricEnabled = true
                )
            ).appLockPinHash
        )
    }

    @Test
    fun persistedServerProfilesDropUnsafeStartupCommands() {
        assertEquals("tmux new -A -s main", PersistedServerProfilePolicy.safeStartupCommand("tmux new -A -s main"))
        assertEquals("", PersistedServerProfilePolicy.safeStartupCommand("sudo reboot"))
        assertEquals("", PersistedServerProfilePolicy.safeStartupCommand("PATH=/usr/bin; pkexec reboot"))
    }

    @Test
    fun persistedServerProfilesNormalizeSafeMetadata() {
        val loaded = PersistedServerProfilePolicy.normalizeLoaded(
            serverProfile().copy(
                id = " server-1 ",
                name = " ",
                host = " ssh.example.test ",
                username = " ",
                group = " ",
                tags = listOf("All", " prod ", ""),
                osName = " ",
                osVersion = " ",
                credentialId = " ",
                terminalProfileId = " ",
                monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 99, useOptionalAgent = true),
                startupCommand = "sudo reboot",
                startDirectory = " /srv/app ",
                notes = " " + "x".repeat(2_100) + " ",
                proxyJumpHostId = " server-1 ",
                reconnectPolicy = ReconnectPolicy(autoReconnect = true, keepAliveSeconds = 2, maxAttempts = 50),
                connectTimeoutSeconds = 120,
                sshCompressionEnabled = true,
                moshConfig = MoshConfig(serverCommand = " /opt/mosh-server ", locale = " ", colors = 999),
                eternalTerminalConfig = EternalTerminalConfig(sshBootstrapPort = 0, etServerPort = 99999, terminalType = " ", serverCommand = " "),
                vncConfig = VncProfileConfig(colorDepthBits = 12, targetFps = 99),
                rdpConfig = RdpProfileConfig(width = 99, height = 99, colorDepth = 12, domain = " corp "),
                fileConfig = FileProtocolConfig(
                    rootPath = " /data ",
                    transferConcurrency = 99,
                    resumeTransfers = false,
                    verifyChecksums = true
                ),
                prootConfig = ProotProfileConfig(distroId = " ", rootfsPath = " /rootfs ")
            )
        )

        assertEquals("server-1", loaded!!.id)
        assertEquals("ssh.example.test", loaded.name)
        assertEquals("ssh.example.test", loaded.host)
        assertEquals("root", loaded.username)
        assertEquals("Ungrouped", loaded.group)
        assertEquals(listOf("All", "prod"), loaded.tags)
        assertEquals("Linux", loaded.osName)
        assertEquals("Unknown", loaded.osVersion)
        assertNull(loaded.credentialId)
        assertEquals("term-default", loaded.terminalProfileId)
        assertEquals(2, loaded.monitoringConfig.pollIntervalSeconds)
        assertTrue(loaded.monitoringConfig.useOptionalAgent)
        assertEquals("", loaded.startupCommand)
        assertEquals("/srv/app", loaded.startDirectory)
        assertEquals(2_000, loaded.notes.length)
        assertEquals("x".repeat(2_000), loaded.notes)
        assertNull(loaded.proxyJumpHostId)
        assertEquals(10, loaded.reconnectPolicy.keepAliveSeconds)
        assertEquals(10, loaded.reconnectPolicy.maxAttempts)
        assertEquals(60, loaded.connectTimeoutSeconds)
        assertTrue(loaded.sshCompressionEnabled)
        assertEquals("/opt/mosh-server", loaded.moshConfig.serverCommand)
        assertEquals("en_US.UTF-8", loaded.moshConfig.locale)
        assertEquals(256, loaded.moshConfig.colors)
        assertEquals(1, loaded.eternalTerminalConfig.sshBootstrapPort)
        assertEquals(65535, loaded.eternalTerminalConfig.etServerPort)
        assertEquals("xterm-256color", loaded.eternalTerminalConfig.terminalType)
        assertEquals(24, loaded.vncConfig.colorDepthBits)
        assertEquals(60, loaded.vncConfig.targetFps)
        assertEquals(640, loaded.rdpConfig.width)
        assertEquals(480, loaded.rdpConfig.height)
        assertEquals(16, loaded.rdpConfig.colorDepth)
        assertEquals("corp", loaded.rdpConfig.domain)
        assertEquals("/data", loaded.fileConfig.rootPath)
        assertEquals(8, loaded.fileConfig.transferConcurrency)
        assertFalse(loaded.fileConfig.resumeTransfers)
        assertTrue(loaded.fileConfig.verifyChecksums)
        assertEquals("alpine-3.21", loaded.prootConfig.distroId)
        assertEquals("/rootfs", loaded.prootConfig.rootfsPath)
    }

    @Test
    fun persistedServerProfilesRejectBlankIdentityAndInvalidHosts() {
        assertNull(PersistedServerProfilePolicy.normalizeLoaded(serverProfile().copy(id = " ")))
        assertNull(PersistedServerProfilePolicy.normalizeLoaded(serverProfile().copy(host = "https://ssh.example.test;rm")))
    }

    private fun serverProfile(): ServerProfile {
        return ServerProfile(
            id = "server-1",
            name = "Example",
            host = "ssh.example.test",
            port = 22,
            username = "root",
            group = "Production",
            tags = listOf("All"),
            osName = "Linux",
            osVersion = "Unknown",
            accent = ServerAccent("Default", 0xFF00AEEF),
            credentialId = null,
            terminalProfileId = "term-default",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false)
        )
    }
}
