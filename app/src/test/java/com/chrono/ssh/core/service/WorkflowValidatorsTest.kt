package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ReconnectPolicy
import com.chrono.ssh.core.model.SftpBookmark
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TerminalKey
import com.chrono.ssh.core.model.TransferDirection
import com.chrono.ssh.core.model.TransferRecord
import com.chrono.ssh.core.model.TransferRecordState
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowValidatorsTest {
    @Test
    fun serverStatusRefreshPolicyMatchesServerBoxCadence() {
        assertEquals(2, ServerStatusRefreshPolicy.normalize(null))
        assertEquals(2, ServerStatusRefreshPolicy.normalize(0))
        assertEquals(1, ServerStatusRefreshPolicy.normalize(1))
        assertEquals(2, ServerStatusRefreshPolicy.normalize(2))
        assertEquals(10, ServerStatusRefreshPolicy.normalize(10))
        assertEquals(2, ServerStatusRefreshPolicy.normalize(11))
        assertEquals(listOf("1s", "2s", "5s", "10s"), ServerStatusRefreshPolicy.presetLabels())
    }

    @Test
    fun serverStatusRefreshPolicyUsesFastestEnabledInterval() {
        assertEquals(2, ServerStatusRefreshPolicy.effectiveIntervalSeconds(appSeconds = 3, hostSeconds = 2))
        assertEquals(2, ServerStatusRefreshPolicy.effectiveIntervalSeconds(appSeconds = 3, hostSeconds = 30))
        assertEquals(1, ServerStatusRefreshPolicy.effectiveIntervalSeconds(appSeconds = 1, hostSeconds = 10))
        assertEquals(2, ServerStatusRefreshPolicy.effectiveIntervalSeconds(appSeconds = 10, hostSeconds = 10))
        assertEquals(2, ServerStatusRefreshPolicy.liveLoopSeconds(10))
    }

    @Test
    fun monitoringProbeInFlightRegistrySkipsOnlyDuplicateServerProbes() {
        val registry = MonitoringProbeInFlightRegistry()

        assertTrue(registry.tryBegin("server-a"))
        assertFalse(registry.tryBegin("server-a"))
        assertTrue(registry.isRunning("server-a"))
        assertTrue(registry.tryBegin("server-b"))

        registry.finish("server-a")

        assertFalse(registry.isRunning("server-a"))
        assertTrue(registry.tryBegin("server-a"))
    }

    @Test
    fun monitoringSkipDiagnosticsAreThrottled() {
        assertTrue(MonitoringSkipDiagnosticPolicy.shouldEmit(null, nowEpochMillis = 1_000L))
        assertFalse(MonitoringSkipDiagnosticPolicy.shouldEmit(1_000L, nowEpochMillis = 20_000L))
        assertTrue(MonitoringSkipDiagnosticPolicy.shouldEmit(1_000L, nowEpochMillis = 31_000L))
    }

    @Test
    fun scpTransferPolicyRequiresFilePathsAndSanitizesNames() {
        assertEquals("/tmp/app.log", ScpTransferPolicy.normalizeRemotePath("/tmp/app.log"))
        assertEquals("./app.log", ScpTransferPolicy.normalizeRemotePath("~/app.log"))
        assertNull(ScpTransferPolicy.normalizeRemotePath(""))
        assertNull(ScpTransferPolicy.normalizeRemotePath("/tmp/"))
        assertEquals("name.txt", ScpTransferPolicy.safeDisplayName("bad/name.txt", "/tmp/ignored"))
        assertEquals("app.log", ScpTransferPolicy.safeDisplayName("", "/var/log/app.log"))
        assertEquals("scp-transfer", ScpTransferPolicy.safeDisplayName("..", "/var/log/app.log"))
        assertEquals("bad_name.txt", ScpTransferPolicy.safeDisplayName("bad\u0000name.txt", "/tmp/ignored"))
        assertEquals(160, ScpTransferPolicy.safeDisplayName("${"a".repeat(220)}.pem", "/tmp/ignored").length)
        assertEquals("key.pem", ScpTransferPolicy.safeDisplayName("nested/key.pem", "/tmp/ignored"))
    }

    @Test
    fun scpTransferPolicyReportsBoundedProgress() {
        assertEquals(0.05f, ScpTransferPolicy.progress(42, 0), 0.0001f)
        assertEquals(0.5f, ScpTransferPolicy.progress(50, 100), 0.0001f)
        assertEquals(1f, ScpTransferPolicy.progress(150, 100), 0.0001f)
    }

    @Test
    fun hostCommandSafetyRejectsPrivilegedAutomaticCommands() {
        assertTrue(HostCommandSafety.isAutomaticCommandSafe("uptime"))
        assertTrue(HostCommandSafety.isAutomaticCommandSafe("cat /proc/stat && vnstat --json"))
        assertTrue(HostCommandSafety.isAutomaticCommandSafe("docker ps --format '{{.State}}'"))

        assertFalse(HostCommandSafety.isAutomaticCommandSafe("sudo apt update"))
        assertFalse(HostCommandSafety.isAutomaticCommandSafe("PATH=/usr/bin; doas rc-status"))
        assertFalse(HostCommandSafety.isAutomaticCommandSafe("pkexec systemctl restart ssh"))
        assertFalse(HostCommandSafety.isAutomaticCommandSafe("su -c 'systemctl restart ssh'"))
    }

    @Test
    fun sudoPromptDetectorHandlesCommonPromptForms() {
        assertTrue(SudoPromptDetector.isPrompt("[sudo] password for deploy:"))
        assertTrue(SudoPromptDetector.isPrompt("\u001B[31mPassword:\u001B[0m"))
        assertTrue(SudoPromptDetector.isPrompt("Password for root\uFF1A"))
        assertTrue(SudoPromptDetector.isPrompt("\u8bf7\u8f93\u5165\u5bc6\u7801\uFF1A"))
        assertTrue(SudoPromptDetector.isPrompt("${"x".repeat(2_100)}\n[sudo] password for deploy:"))

        assertFalse(SudoPromptDetector.isPrompt("password updated successfully"))
        assertFalse(SudoPromptDetector.isPrompt("Permission denied, please try again."))
    }

    @Test
    fun defaultSnippetCatalogIncludesSafeSessionRestoreHelpers() {
        val snippets = DefaultSnippetCatalog.snippets

        assertTrue(snippets.any { it.id == "snip-tmux-main" && it.command == "tmux new -A -s main" })
        assertTrue(snippets.any { it.id == "snip-zellij-main" && it.command == "zellij attach main --create" })
        assertTrue(snippets.any { it.id == "snip-screen-main" && it.command == "screen -xRR main" })
        assertTrue(snippets.all { HostCommandSafety.isAutomaticCommandSafe(it.command) })
        assertEquals(snippets.size, snippets.map { it.id }.distinct().size)
    }

    @Test
    fun terminalAccessoryKeysMapCommonAliasesAndModifiers() {
        assertEquals("\u001B[A", TerminalInputRouter.sequenceFor(TerminalKey("Arrow Up", "")))
        assertEquals("\u001B[B", TerminalInputRouter.sequenceFor(TerminalKey("↓", "")))
        assertEquals("\u001B[5~", TerminalInputRouter.sequenceFor(TerminalKey("Page Up", "")))
        assertEquals("<shift>", TerminalInputRouter.sequenceFor(TerminalKey("Shift", "")))
        assertEquals("<altgr>", TerminalInputRouter.sequenceFor(TerminalKey("AltGr", "")))
        assertEquals("\u001B/", TerminalInputRouter.sequenceFor(TerminalKey("Alt-/", "")))
        assertEquals("\u001C", TerminalInputRouter.sequenceFor(TerminalKey("Ctrl-\\", "")))
    }

    @Test
    fun hostEnvironmentPolicyParsesStrictKeyValueEntries() {
        val result = HostEnvironmentPolicy.parse("TERM=xterm-256color, LANG=en_US.UTF-8\nLC_ALL=C")

        assertTrue(result.valid)
        assertEquals(
            listOf(
                com.chrono.ssh.core.model.ConnectionCommand("TERM", "xterm-256color"),
                com.chrono.ssh.core.model.ConnectionCommand("LANG", "en_US.UTF-8"),
                com.chrono.ssh.core.model.ConnectionCommand("LC_ALL", "C")
            ),
            result.entries
        )
        assertEquals("TERM=xterm-256color, LANG=en_US.UTF-8, LC_ALL=C", HostEnvironmentPolicy.serialize(result.entries))
    }

    @Test
    fun hostEnvironmentPolicyRejectsMalformedEntries() {
        assertFalse(HostEnvironmentPolicy.parse("1BAD=value").valid)
        assertFalse(HostEnvironmentPolicy.parse("TERM").valid)
        assertFalse(HostEnvironmentPolicy.parse("TERM=").valid)
    }

    @Test
    fun hostEndpointValidatorRejectsUrlsPathsAndCommands() {
        assertNull(HostEndpointValidator.errorFor("ssh.example.test"))
        assertNull(HostEndpointValidator.errorFor("192.168.1.20"))
        assertNull(HostEndpointValidator.errorFor("2001:db8::10"))

        assertTrue(HostEndpointValidator.errorFor("https://ssh.example.test").orEmpty().contains("not a URL"))
        assertTrue(HostEndpointValidator.errorFor("ssh.example.test/root").orEmpty().contains("not a command"))
        assertTrue(HostEndpointValidator.errorFor("ssh.example.test;rm -rf").orEmpty().contains("not a command"))
        assertTrue(HostEndpointValidator.errorFor("").orEmpty().contains("required"))
    }

    @Test
    fun backupServerPolicyNormalizesSafeImportedHosts() {
        val sanitized = BackupServerPolicy.sanitizeImportedMetadata(
            server(username = " ").copy(
                id = " server-1 ",
                name = " ",
                host = " ssh.example.test ",
                group = " ",
                tags = listOf("All", " prod ", ""),
                osName = "",
                osVersion = "",
                credentialId = " identity-1 ",
                terminalProfileId = " term-tokyo ",
                monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 99, useOptionalAgent = true),
                startupCommand = "sudo apt update",
                proxyJumpHostId = " server-1 ",
                reconnectPolicy = ReconnectPolicy(autoReconnect = true, keepAliveSeconds = 2, maxAttempts = 50),
                connectTimeoutSeconds = 1,
                sshCompressionEnabled = true
            )
        )

        checkNotNull(sanitized)
        assertEquals("server-1", sanitized.id)
        assertEquals("ssh.example.test", sanitized.name)
        assertEquals("ssh.example.test", sanitized.host)
        assertEquals("root", sanitized.username)
        assertEquals("Ungrouped", sanitized.group)
        assertEquals(listOf("All", "prod"), sanitized.tags)
        assertEquals("Linux", sanitized.osName)
        assertEquals("Unknown", sanitized.osVersion)
        assertEquals("identity-1", sanitized.credentialId)
        assertEquals("term-tokyo", sanitized.terminalProfileId)
        assertEquals(2, sanitized.monitoringConfig.pollIntervalSeconds)
        assertTrue(sanitized.monitoringConfig.useOptionalAgent)
        assertEquals("", sanitized.startupCommand)
        assertNull(sanitized.proxyJumpHostId)
        assertEquals(10, sanitized.reconnectPolicy.keepAliveSeconds)
        assertEquals(10, sanitized.reconnectPolicy.maxAttempts)
        assertEquals(3, sanitized.connectTimeoutSeconds)
        assertTrue(sanitized.sshCompressionEnabled)
    }

    @Test
    fun backupServerPolicyRejectsInvalidImportedHosts() {
        val sanitized = BackupServerPolicy.sanitizeImportedMetadata(
            server(username = "root").copy(host = "https://ssh.example.test;rm")
        )

        assertNull(sanitized)
    }

    @Test
    fun hostShareLinkRoundTripsSafeHostMetadataWithoutCredentialRefs() {
        val source = server(username = "deploy").copy(
            name = "Prod Box",
            host = "ssh.example.test",
            port = 2222,
            group = "Prod",
            tags = listOf("All", "db"),
            credentialId = "secret-credential",
            startupCommand = "tmux new -A -s main",
            startDirectory = "/srv/app",
            terminalProfileId = "tokyo",
            sshCompressionEnabled = true
        )

        val decoded = HostShareLinkCodec.decode(HostShareLinkCodec.encode(source))

        checkNotNull(decoded)
        assertEquals("Prod Box", decoded.name)
        assertEquals("ssh.example.test", decoded.host)
        assertEquals(2222, decoded.port)
        assertEquals("deploy", decoded.username)
        assertEquals("Prod", decoded.group)
        assertEquals(listOf("All", "db"), decoded.tags)
        assertEquals(null, decoded.credentialId)
        assertEquals("tmux new -A -s main", decoded.startupCommand)
        assertEquals("/srv/app", decoded.startDirectory)
        assertEquals("tokyo", decoded.terminalProfileId)
        assertTrue(decoded.sshCompressionEnabled)
    }

    @Test
    fun hostShareLinkRejectsInvalidHostsAndStripsUnsafeStartupCommand() {
        assertNull(HostShareLinkCodec.decode("chronossh://host?host=https%3A%2F%2Fssh.example.test&user=root"))

        val decoded = HostShareLinkCodec.decode("chronossh://host?id=server-1&host=ssh.example.test&user=root&startup=sudo+reboot")

        checkNotNull(decoded)
        assertTrue(decoded.id.startsWith("import-"))
        assertEquals("", decoded.startupCommand)
    }

    @Test
    fun hostShareLinkDecodeHandlesCaseAndMalformedEscapes() {
        val decoded = HostShareLinkCodec.decode("ChronoSSH://HOST?host=ssh.example.test&user=deploy")

        checkNotNull(decoded)
        assertEquals("ssh.example.test", decoded.host)
        assertEquals("deploy", decoded.username)
        assertNull(HostShareLinkCodec.decode("chronossh://host?host=ssh.example.test%ZZ&user=root"))
    }

    @Test
    fun hostShareLinkDecodeRejectsOversizedPayloadsAndParams() {
        assertNull(HostShareLinkCodec.decode("chronossh://host?host=${"a".repeat(2_049)}&user=root"))
        assertNull(HostShareLinkCodec.decode("chronossh://host?host=ssh.example.test&" + (1..65).joinToString("&") { "tag=t$it" }))
        assertNull(HostShareLinkCodec.decode("chronossh://host?host=ssh.example.test&user=root&pad=${"x".repeat(8_200)}"))
    }

    @Test
    fun snippetShareLinkRoundTripsDescription() {
        val source = Snippet(
            id = "snippet-1",
            name = "Deploy",
            command = "systemctl restart --user app",
            tags = listOf("ops"),
            serverScope = null,
            variables = emptyList(),
            description = "Restart the user service after a release.",
            group = "Deploy",
            favorite = true,
            confirmBeforeRun = false,
            autoRun = true
        )

        val decoded = HostShareLinkCodec.decodeSnippet(HostShareLinkCodec.encode(source))

        checkNotNull(decoded)
        assertEquals(source.id, decoded.id)
        assertEquals(source.name, decoded.name)
        assertEquals(source.command, decoded.command)
        assertEquals(source.tags, decoded.tags)
        assertEquals(source.description, decoded.description)
        assertEquals(source.group, decoded.group)
        assertEquals(source.favorite, decoded.favorite)
        assertEquals(source.confirmBeforeRun, decoded.confirmBeforeRun)
        assertEquals(source.autoRun, decoded.autoRun)
        assertTrue(decoded.createdAtEpochMillis > 0L)
        assertTrue(decoded.updatedAtEpochMillis > 0L)
    }

    @Test
    fun proxyJumpPolicyAllowsCredentialBackedJumpChainsWithoutCycles() {
        val target = server(username = "deploy").copy(id = "target")
        val jump = server(username = "bastion").copy(id = "jump", credentialId = "identity-1")
        val chained = jump.copy(proxyJumpHostId = "edge")
        val loop = jump.copy(proxyJumpHostId = "target")

        assertTrue(ProxyJumpPolicy.validSelection(target, null))
        assertTrue(ProxyJumpPolicy.validSelection(target, jump))
        assertFalse(ProxyJumpPolicy.validSelection(target, target))
        assertFalse(ProxyJumpPolicy.validSelection(target, jump.copy(credentialId = null)))
        assertTrue(ProxyJumpPolicy.validSelection(target, chained))
        assertTrue(ProxyJumpPolicy.errorForSelection(target, "missing", listOf(target, jump)).orEmpty().contains("no longer available"))
        assertTrue(ProxyJumpPolicy.errorFor(target, target).orEmpty().contains("itself"))
        assertNull(ProxyJumpPolicy.errorFor(target, chained))
        assertTrue(ProxyJumpPolicy.errorForSelection(target, "jump", listOf(target, loop)).orEmpty().contains("loop"))
        assertTrue(ProxyJumpPolicy.errorFor(target, jump.copy(credentialId = null)).orEmpty().contains("identity"))
    }

    @Test
    fun proxyJumpPolicyResolvesMissingJumpAndMissingIdentityAsErrors() {
        val target = server(username = "deploy").copy(id = "target", proxyJumpHostId = "jump")
        val jump = server(username = "bastion").copy(id = "jump", credentialId = "identity-1")
        val identity = Credential(
            id = "identity-1",
            label = "Bastion",
            type = CredentialType.Password,
            publicKeyPreview = "Password saved",
            encryptedPayloadRef = "secret-identity-1",
            createdAtEpochMillis = 1L
        )

        val resolved = ProxyJumpPolicy.resolveTarget(target, listOf(target, jump)) { identity }
        val missingJump = ProxyJumpPolicy.resolveTarget(target, listOf(target)) { identity }
        val missingIdentity = ProxyJumpPolicy.resolveTarget(target, listOf(target, jump)) { null }

        assertEquals(jump, resolved.target?.profile)
        assertEquals(identity, resolved.target?.credential)
        assertNull(resolved.error)
        assertTrue(missingJump.error.orEmpty().contains("no longer available"))
        assertTrue(missingIdentity.error.orEmpty().contains("identity"))
    }

    @Test
    fun pinLockPolicyHashesAndVerifiesStrictNumericPins() {
        val result = PinLockPolicy.hashPin("123456", salt = "MTIzNDU2Nzg5MGFiY2RlZg")

        assertTrue(PinLockPolicy.verify("123456", result.hash, result.salt))
        assertFalse(PinLockPolicy.verify("123457", result.hash, result.salt))
        assertFalse(PinLockPolicy.verify("123456", result.hash, "not-base64!"))
        assertFalse(PinLockPolicy.verify("123456", "not-a-valid-hash", result.salt))
        assertTrue(PinLockPolicy.persistedPinUsable(result.hash, result.salt))
        assertFalse(PinLockPolicy.persistedPinUsable(result.hash, "not-base64!"))
        assertFalse(PinLockPolicy.persistedPinUsable("aGFzaA", result.salt))
        assertFalse(PinLockPolicy.persistedPinUsable(null, result.salt))
        assertFalse(PinLockPolicy.persistedPinUsable("A".repeat(10_000_000), result.salt))
        assertNull(PinLockPolicy.validatePin("123456"))
        assertTrue(PinLockPolicy.validatePin("12345").orEmpty().contains("6-12"))
        assertTrue(PinLockPolicy.validatePin("12345a").orEmpty().contains("6-12"))
    }

    @Test
    fun appLockCrashRecoveryOnlyTargetsRecentLockCrashes() {
        val now = 10_000_000L
        val lockCrash = com.chrono.ssh.core.model.CrashLogEntry(
            id = "1",
            atEpochMillis = now - 1_000L,
            threadName = "main",
            throwableClass = "java.lang.IllegalStateException",
            message = "AppLock failed",
            stackTrace = "com.chrono.ssh.ui.AppLockScreen"
        )
        val oldLockCrash = lockCrash.copy(atEpochMillis = now - 11L * 60L * 1000L)
        val unrelatedCrash = lockCrash.copy(message = "Terminal failed", stackTrace = "TerminalScreen")
        val launchCrash = lockCrash.copy(message = "Launch failed", stackTrace = "com.chrono.ssh.ui.ChronoSSHApp")
        val oldLaunchCrash = launchCrash.copy(atEpochMillis = now - 11L * 60L * 1000L)
        val oldPinPolicyCrash = lockCrash.copy(
            atEpochMillis = now - 60L * 60L * 1000L,
            message = "Illegal base64 appLockPinHash",
            stackTrace = "com.chrono.ssh.core.service.PinLockPolicy"
        )
        val maskedLockCrashLoop = listOf(
            lockCrash.copy(atEpochMillis = now - 5_000L),
            launchCrash.copy(id = "2", atEpochMillis = now - 1_000L, message = "Compose startup failed")
        )
        val composeRuntimeCrash = lockCrash.copy(
            throwableClass = "java.lang.ArrayIndexOutOfBoundsException",
            message = "length=320; index=-2",
            stackTrace = "androidx.compose.runtime.IntStack.peek2\nandroidx.compose.runtime.ComposerImpl.end"
        )

        assertTrue(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(lockCrash), now))
        assertFalse(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(oldLockCrash), now))
        assertFalse(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(oldPinPolicyCrash), now))
        assertFalse(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(launchCrash), now))
        assertTrue(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(composeRuntimeCrash), now))
        assertTrue(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(maskedLockCrashLoop, now))
        assertFalse(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(oldLaunchCrash), now))
        assertFalse(AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(listOf(unrelatedCrash), now))
    }

    @Test
    fun appLockRenderMarkerExpiresOnlyAfterFuseWindow() {
        assertFalse(AppLockCrashRecoveryPolicy.renderMarkerExpired(null, nowEpochMillis = 20_000L))
        assertFalse(AppLockCrashRecoveryPolicy.renderMarkerExpired(11_000L, nowEpochMillis = 20_000L))
        assertTrue(AppLockCrashRecoveryPolicy.renderMarkerExpired(10_000L, nowEpochMillis = 20_000L))
    }

    @Test
    fun snippetValidatorNormalizesTagsAndCommandVariables() {
        val longName = "Restart service ".repeat(10)
        val longTag = "DeployTag".repeat(8)
        val result = SnippetValidator.validate(
            Snippet(
                id = "",
                name = "  $longName\u0000  ",
                description = "  ${"Deploy note ".repeat(30)}\u0000  ",
                command = "systemctl restart --user {{ service_name }}",
                tags = listOf("Systemd, $longTag", longTag),
                serverScope = "",
                variables = listOf("manualVar"),
                group = "  Deploy\u0000Ops  ",
                favorite = true,
                confirmBeforeRun = false
            )
        )

        assertTrue(result.valid)
        val normalized = result.normalized!!
        assertTrue(normalized.id.startsWith("snippet-"))
        assertEquals(80, normalized.name.length)
        assertEquals(240, normalized.description.length)
        assertFalse(normalized.name.any { it.isISOControl() })
        assertFalse(normalized.description.any { it.isISOControl() })
        assertEquals(listOf("systemd", longTag.lowercase().take(32)), normalized.tags)
        assertEquals(listOf("manualVar", "service_name"), normalized.variables)
        assertNull(normalized.serverScope)
        assertEquals("Deploy Ops", normalized.group)
        assertTrue(normalized.favorite)
        assertFalse(normalized.confirmBeforeRun)
        assertTrue(normalized.createdAtEpochMillis > 0L)
        assertTrue(normalized.updatedAtEpochMillis > 0L)
    }

    @Test
    fun snippetValidatorRejectsBlankSnippetWithoutRegexCrash() {
        val result = SnippetValidator.validate(
            Snippet(
                id = "",
                name = "",
                command = "",
                tags = emptyList(),
                serverScope = null,
                variables = emptyList()
            )
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("name", ignoreCase = true) })
        assertTrue(result.errors.any { it.contains("Command", ignoreCase = true) })
    }

    @Test
    fun snippetValidatorRejectsPrivilegedCommands() {
        val result = SnippetValidator.validate(
            Snippet(
                id = "snip-root",
                name = "Root update",
                command = "sudo apt update",
                tags = emptyList(),
                serverScope = null,
                variables = emptyList()
            )
        )

        assertFalse(result.valid)
        assertNull(result.normalized)
        assertTrue(result.errors.joinToString(" ").contains("sudo"))
    }

    @Test
    fun snippetPersistencePolicyNormalizesSafePersistedSnippets() {
        val loaded = SnippetPersistencePolicy.normalizeLoaded(
            Snippet(
                id = " snippet-1 ",
                name = "  Tail logs  ",
                command = " tail -f {{ log_path }} ",
                tags = listOf("Logs, Ops"),
                serverScope = " server-1 ",
                variables = emptyList(),
                group = " Ops ",
                favorite = true,
                confirmBeforeRun = false,
                autoRun = true,
                createdAtEpochMillis = 123L
            )
        )

        checkNotNull(loaded)
        assertEquals("snippet-1", loaded.id)
        assertEquals("Tail logs", loaded.name)
        assertEquals("tail -f {{ log_path }}", loaded.command)
        assertEquals(listOf("logs", "ops"), loaded.tags)
        assertEquals("server-1", loaded.serverScope)
        assertEquals(listOf("log_path"), loaded.variables)
        assertEquals("Ops", loaded.group)
        assertTrue(loaded.favorite)
        assertFalse(loaded.confirmBeforeRun)
        assertTrue(loaded.autoRun)
        assertEquals(123L, loaded.createdAtEpochMillis)
    }

    @Test
    fun snippetPersistencePolicyRejectsBlankIdentityAndUnsafeCommands() {
        val valid = Snippet(
            id = "snippet-1",
            name = "Logs",
            command = "tail -f /var/log/syslog",
            tags = emptyList(),
            serverScope = null,
            variables = emptyList()
        )

        assertNull(SnippetPersistencePolicy.normalizeLoaded(valid.copy(id = " ")))
        assertNull(SnippetPersistencePolicy.normalizeLoaded(valid.copy(command = "sudo apt update")))
    }

    @Test
    fun backupSnippetPolicyNormalizesSafeImportedSnippets() {
        val sanitized = BackupSnippetPolicy.sanitizeImportedMetadata(
            Snippet(
                id = "",
                name = "  Tail logs  ",
                command = "tail -f {{ log_path }}",
                tags = listOf("Logs, Ops"),
                serverScope = "",
                variables = emptyList()
            )
        )

        checkNotNull(sanitized)
        assertTrue(sanitized.id.startsWith("snippet-"))
        assertEquals("Tail logs", sanitized.name)
        assertEquals(listOf("logs", "ops"), sanitized.tags)
        assertEquals(listOf("log_path"), sanitized.variables)
        assertNull(sanitized.serverScope)
    }

    @Test
    fun backupSnippetPolicyRejectsUnsafeImportedSnippets() {
        val sanitized = BackupSnippetPolicy.sanitizeImportedMetadata(
            Snippet(
                id = "snip-root",
                name = "Root update",
                command = "sudo apt update",
                tags = emptyList(),
                serverScope = null,
                variables = emptyList()
            )
        )

        assertNull(sanitized)
    }

    @Test
    fun hostKeyEvaluatorDetectsUnknownTrustedChangedAndRejected() {
        val observed = knownHost("SHA256:new", trusted = false)
        assertEquals(HostKeyTrustState.Unknown, HostKeyTrustEvaluator.evaluate(observed, null))
        assertEquals(HostKeyTrustState.Trusted, HostKeyTrustEvaluator.evaluate(observed, knownHost("SHA256:new", trusted = true)))
        assertEquals(HostKeyTrustState.Changed, HostKeyTrustEvaluator.evaluate(observed, knownHost("SHA256:old", trusted = true)))
        assertEquals(
            HostKeyTrustState.Changed,
            HostKeyTrustEvaluator.evaluate(observed, knownHost("SHA256:new", trusted = false, state = HostKeyTrustState.Changed))
        )
        assertEquals(
            HostKeyTrustState.Changed,
            HostKeyTrustEvaluator.evaluate(observed, knownHost("SHA256:new", trusted = true).copy(algorithm = "ssh-rsa"))
        )
        assertEquals(
            HostKeyTrustState.Rejected,
            HostKeyTrustEvaluator.evaluate(observed, knownHost("SHA256:new", trusted = false, state = HostKeyTrustState.Rejected))
        )
    }

    @Test
    fun hostKeyApprovalPolicyBlocksChangedAndRejectedKeys() {
        assertTrue(HostKeyApprovalPolicy.canApproveStoredFingerprint(knownHost("SHA256:new", trusted = false)))
        assertTrue(HostKeyApprovalPolicy.canApproveStoredFingerprint(knownHost("SHA256:new", trusted = true)))
        assertFalse(
            HostKeyApprovalPolicy.canApproveStoredFingerprint(
                knownHost("SHA256:new", trusted = false, state = HostKeyTrustState.Changed)
            )
        )
        assertFalse(
            HostKeyApprovalPolicy.canApproveStoredFingerprint(
                knownHost("SHA256:new", trusted = false, state = HostKeyTrustState.Rejected)
            )
        )
        assertFalse(HostKeyApprovalPolicy.canApproveStoredFingerprint(knownHost("Unavailable until network handshake succeeds", trusted = false)))
        assertFalse(HostKeyApprovalPolicy.canApproveStoredFingerprint(knownHost("SHA256:unavailable-until-network-handshake-succeeds", trusted = false)))
        assertFalse(HostKeyApprovalPolicy.canApproveStoredFingerprint(knownHost("SHA256:PENDING", trusted = false)))
    }

    @Test
    fun hostKeyObservationPolicyDoesNotTrustChangedOrRejectedKeys() {
        val server = server("root")
        val changed = HostKeyObservationPolicy.remembered(
            server = server,
            existing = knownHost("SHA256:old", trusted = true),
            algorithm = "ssh-ed25519",
            fingerprint = "SHA256:new",
            nowEpochMillis = 3
        )
        val rejected = HostKeyObservationPolicy.remembered(
            server = server,
            existing = knownHost("SHA256:new", trusted = false, state = HostKeyTrustState.Rejected),
            algorithm = "ssh-ed25519",
            fingerprint = "SHA256:new",
            nowEpochMillis = 3
        )
        val changedAlreadyStored = HostKeyObservationPolicy.remembered(
            server = server,
            existing = knownHost("SHA256:new", trusted = false, state = HostKeyTrustState.Changed),
            algorithm = "ssh-ed25519",
            fingerprint = "SHA256:new",
            nowEpochMillis = 3
        )
        val algorithmChanged = HostKeyObservationPolicy.changedObservation(
            server = server,
            existing = knownHost("SHA256:new", trusted = true).copy(algorithm = "ssh-rsa"),
            algorithm = "ssh-ed25519",
            fingerprint = "SHA256:new",
            nowEpochMillis = 3
        )

        assertFalse(changed.trusted)
        assertEquals(HostKeyTrustState.Changed, changed.trustState)
        assertFalse(rejected.trusted)
        assertEquals(HostKeyTrustState.Rejected, rejected.trustState)
        assertFalse(changedAlreadyStored.trusted)
        assertEquals(HostKeyTrustState.Changed, changedAlreadyStored.trustState)
        assertEquals(HostKeyTrustState.Changed, algorithmChanged?.trustState)
    }

    @Test
    fun portForwardValidatorNormalizesDynamicSocksForward() {
        val result = PortForwardValidator.validate(
            PortForwardRule("f1", "server", PortForwardType.DynamicSocks, "", 1080, "ignored", 22, false, false)
        )

        assertTrue(result.valid)
        assertEquals("127.0.0.1", result.normalized?.localHost)
        assertEquals(1080, result.normalized?.localPort)
        assertEquals("", result.normalized?.remoteHost)
        assertEquals(0, result.normalized?.remotePort)
    }

    @Test
    fun portForwardValidatorNormalizesRemoteForward() {
        val result = PortForwardValidator.validate(
            PortForwardRule("f1", "server", PortForwardType.Remote, "", 8080, "127.0.0.1", 80, false, false)
        )

        assertTrue(result.valid)
        assertEquals("127.0.0.1", result.normalized?.localHost)
        assertEquals(8080, result.normalized?.localPort)
        assertEquals("127.0.0.1", result.normalized?.remoteHost)
        assertEquals(80, result.normalized?.remotePort)
    }

    @Test
    fun portForwardValidatorAllowsRemoteAutoAssignedBindPort() {
        val result = PortForwardValidator.validate(
            PortForwardRule("f1", "server", PortForwardType.Remote, "", 8080, "127.0.0.1", 0, false, false)
        )

        assertTrue(result.valid)
        assertEquals(0, result.normalized?.remotePort)
    }

    @Test
    fun portForwardValidatorNormalizesLocalForward() {
        val result = PortForwardValidator.validate(
            PortForwardRule(" f1 ", " server ", PortForwardType.Local, "", 8080, "", 80, false, false, "  DB tunnel  ", " Ops ", true)
        )

        assertTrue(result.valid)
        assertEquals("f1", result.normalized?.id)
        assertEquals("server", result.normalized?.serverId)
        assertEquals("127.0.0.1", result.normalized?.localHost)
        assertEquals("127.0.0.1", result.normalized?.remoteHost)
        assertEquals(80, result.normalized?.remotePort)
        assertEquals("DB tunnel", result.normalized?.label)
        assertEquals("Ops", result.normalized?.group)
        assertEquals(true, result.normalized?.favorite)
    }

    @Test
    fun portForwardValidatorRejectsInvalidPorts() {
        val result = PortForwardValidator.validate(
            PortForwardRule("f1", "server", PortForwardType.Local, "127.0.0.1", 70000, "db", 0, false, false)
        )

        assertFalse(result.valid)
        assertNull(result.normalized)
        assertEquals(2, result.errors.size)
    }

    @Test
    fun portForwardValidatorRejectsPrivilegedLocalPortsOnAndroid() {
        val result = PortForwardValidator.validate(
            PortForwardRule("f1", "server", PortForwardType.Local, "127.0.0.1", 80, "db", 5432, false, false)
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("1024") })
    }

    @Test
    fun portForwardValidatorRejectsUrlOrCommandLikeHosts() {
        val result = PortForwardValidator.validate(
            PortForwardRule("f1", "server", PortForwardType.Local, "127.0.0.1", 8080, "https://db.example.test;rm", 5432, false, false)
        )

        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("Remote address") })
    }

    @Test
    fun forwardRuntimePolicyNeverMarksTransitionalOrFailedStatesActive() {
        val rule = PortForwardRule("f1", "server", PortForwardType.Local, "", 8080, "db", 5432, true, false)

        val starting = ForwardRuntimePolicy.starting(rule)
        val stopped = ForwardRuntimePolicy.stopped(rule)
        val failed = ForwardRuntimePolicy.failed(rule, "Permission denied")

        assertFalse(starting.active)
        assertFalse(stopped.active)
        assertFalse(failed.active)
        assertEquals("127.0.0.1:8080", starting.boundAddress)
        assertEquals("Starting tunnel", starting.lastMessage)
        assertEquals("Stopped", stopped.lastMessage)
        assertEquals("Failed", failed.lastMessage)
        assertTrue(failed.lastError.orEmpty().contains("above 1024"))
    }

    @Test
    fun forwardRuntimePolicyMarksRunningStateActive() {
        val rule = PortForwardRule("f1", "server", PortForwardType.Local, "", 8080, "db", 5432, true, false)

        val running = ForwardRuntimePolicy.running(rule)

        assertTrue(running.active)
        assertEquals("127.0.0.1:8080", running.boundAddress)
        assertEquals("Running", running.lastMessage)
        assertNull(running.lastError)
    }

    @Test
    fun forwardRuntimePolicyUsesRemoteBindAddressForRemoteForward() {
        val rule = PortForwardRule("f1", "server", PortForwardType.Remote, "127.0.0.1", 8080, "0.0.0.0", 18080, false, false)

        assertEquals("0.0.0.0:18080", ForwardRuntimePolicy.starting(rule).boundAddress)
        assertEquals("0.0.0.0:18080", ForwardRuntimePolicy.stopped(rule).boundAddress)
    }

    @Test
    fun forwardRuntimePolicyMapsRemoteBindFailuresToRemoteGuidance() {
        val rule = PortForwardRule("f1", "server", PortForwardType.Remote, "127.0.0.1", 8080, "0.0.0.0", 80, false, false)

        assertTrue(ForwardRuntimePolicy.failed(rule, "Permission denied").lastError.orEmpty().contains("Remote bind port"))
        assertTrue(ForwardRuntimePolicy.failed(rule, "Address already in use").lastError.orEmpty().contains("SSH server"))
    }

    @Test
    fun portForwardRouteLabelMatchesTunnelDirection() {
        assertEquals(
            "127.0.0.1:8080 -> db:5432",
            PortForwardRule("local", "server", PortForwardType.Local, "", 8080, "db", 5432, false, false).routeLabel()
        )
        assertEquals(
            "0.0.0.0:18080 -> 127.0.0.1:8080",
            PortForwardRule("remote", "server", PortForwardType.Remote, "127.0.0.1", 8080, "0.0.0.0", 18080, false, false).routeLabel()
        )
        assertEquals(
            "127.0.0.1:1080 SOCKS5",
            PortForwardRule("socks", "server", PortForwardType.DynamicSocks, "", 1080, "", 0, false, false).routeLabel()
        )
    }

    @Test
    fun socks5ProtocolParsesNoAuthConnectRequests() {
        val request = byteArrayOf(
            0x05, 0x01, 0x00,
            0x05, 0x01, 0x00, 0x03, 0x0b,
            'e'.code.toByte(), 'x'.code.toByte(), 'a'.code.toByte(), 'm'.code.toByte(), 'p'.code.toByte(), 'l'.code.toByte(), 'e'.code.toByte(), '.'.code.toByte(), 'c'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(),
            0x01, 0xbb.toByte()
        )
        val output = java.io.ByteArrayOutputStream()

        val parsed = Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(request), output)
        Socks5Protocol.writeSuccess(output)

        assertEquals("example.com", parsed.host)
        assertEquals(443, parsed.port)
        assertEquals(12, output.toByteArray().size)
        assertEquals(0x00, output.toByteArray()[1].toInt())
    }

    @Test
    fun socks5ProtocolRejectsUnsupportedAuthMethods() {
        val output = java.io.ByteArrayOutputStream()
        val error = runCatching {
            Socks5Protocol.readConnectRequest(
                java.io.ByteArrayInputStream(byteArrayOf(0x05, 0x01, 0x02)),
                output
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("no no-auth"))
        assertEquals(listOf(0x05, 0xff), output.toByteArray().map { it.toInt() and 0xff })
    }

    @Test
    fun socks5ProtocolRejectsUnsupportedAddressTypesAndCommands() {
        val unsupportedAddress = byteArrayOf(
            0x05, 0x01, 0x00,
            0x05, 0x01, 0x00, 0x09,
            0x00, 0x50
        )
        val unsupportedAddressOutput = java.io.ByteArrayOutputStream()
        val addressError = runCatching {
            Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(unsupportedAddress), unsupportedAddressOutput)
        }.exceptionOrNull()

        assertTrue(addressError is IllegalArgumentException)
        assertTrue(addressError?.message.orEmpty().contains("address type"))
        assertEquals(0x08, unsupportedAddressOutput.toByteArray().lastReplyCode())

        val bindRequest = byteArrayOf(
            0x05, 0x01, 0x00,
            0x05, 0x02, 0x00, 0x01,
            127, 0, 0, 1,
            0x00, 0x50
        )
        val bindOutput = java.io.ByteArrayOutputStream()
        val commandError = runCatching {
            Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(bindRequest), bindOutput)
        }.exceptionOrNull()

        assertTrue(commandError is IllegalArgumentException)
        assertTrue(commandError?.message.orEmpty().contains("CONNECT"))
        assertEquals(0x07, bindOutput.toByteArray().lastReplyCode())
    }

    @Test
    fun socks5ProtocolRejectsInvalidReservedByte() {
        val request = byteArrayOf(
            0x05, 0x01, 0x00,
            0x05, 0x01, 0x7f, 0x01,
            127, 0, 0, 1,
            0x00, 0x50
        )

        val error = runCatching {
            Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(request), java.io.ByteArrayOutputStream())
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("reserved"))
    }

    @Test
    fun socks5ProtocolRejectsPathLikeDomainTargets() {
        val target = "example.com/root"
        val request = byteArrayOf(0x05, 0x01, 0x00, 0x05, 0x01, 0x00, 0x03, target.length.toByte()) +
            target.toByteArray() +
            byteArrayOf(0x00, 0x50)

        val error = runCatching {
            Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(request), java.io.ByteArrayOutputStream())
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("hostname or IP address"))
    }

    @Test
    fun socks5ProtocolRejectsEmptyDomainPortZeroAndTruncatedRequests() {
        val emptyDomain = byteArrayOf(
            0x05, 0x01, 0x00,
            0x05, 0x01, 0x00, 0x03, 0x00,
            0x00, 0x50
        )
        val emptyDomainError = runCatching {
            Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(emptyDomain), java.io.ByteArrayOutputStream())
        }.exceptionOrNull()

        assertTrue(emptyDomainError is IllegalArgumentException)
        assertTrue(emptyDomainError?.message.orEmpty().lowercase().contains("host"))

        val portZero = byteArrayOf(
            0x05, 0x01, 0x00,
            0x05, 0x01, 0x00, 0x01,
            127, 0, 0, 1,
            0x00, 0x00
        )
        val portZeroError = runCatching {
            Socks5Protocol.readConnectRequest(java.io.ByteArrayInputStream(portZero), java.io.ByteArrayOutputStream())
        }.exceptionOrNull()

        assertTrue(portZeroError is IllegalArgumentException)
        assertTrue(portZeroError?.message.orEmpty().contains("port"))

        val truncatedError = runCatching {
            Socks5Protocol.readConnectRequest(
                java.io.ByteArrayInputStream(byteArrayOf(0x05, 0x01, 0x00, 0x05, 0x01)),
                java.io.ByteArrayOutputStream()
            )
        }.exceptionOrNull()

        assertTrue(truncatedError is java.io.EOFException)
    }

    @Test
    fun forwardRuntimePolicyUsesClearFallbackForBlankFailureMessage() {
        val rule = PortForwardRule("f1", "server", PortForwardType.Local, "0.0.0.0", 8080, "db", 5432, false, false)

        val failed = ForwardRuntimePolicy.failed(rule, "")

        assertFalse(failed.active)
        assertEquals("0.0.0.0:8080", failed.boundAddress)
        assertEquals("Tunnel failed.", failed.lastError)
    }

    private fun ByteArray.lastReplyCode(): Int {
        assertTrue("Expected a SOCKS reply", size >= 2)
        return this[size - 9].toInt() and 0xff
    }

    @Test
    fun forwardRuntimePolicyMapsCommonRuntimeFailures() {
        val rule = PortForwardRule("f1", "server", PortForwardType.Local, "127.0.0.1", 8080, "db", 5432, false, false)

        assertTrue(
            ForwardRuntimePolicy.failed(rule, "Address already in use").lastError.orEmpty()
                .contains("already in use")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "Permission denied").lastError.orEmpty()
                .contains("above 1024")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "Host key rejected").lastError.orEmpty()
                .contains("Approve this host key")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "Exhausted available authentication methods").lastError.orEmpty()
                .contains("authorized_keys")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "Private key 'prod' is encrypted. Enter its passphrase to connect.").lastError.orEmpty()
                .contains("passphrase")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "Private-key auth failed for 'prod'").lastError.orEmpty()
                .contains("PubkeyAuthentication")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "open failed: administratively prohibited: connect failed").lastError.orEmpty()
                .contains("AllowTcpForwarding")
        )
        assertTrue(
            ForwardRuntimePolicy.failed(rule, "ENETUNREACH Network is unreachable").lastError.orEmpty()
                .contains("VM network mode")
        )
    }

    @Test
    fun terminalInputRouterMapsSpecialKeysAndPaste() {
        assertEquals("\u001B[A", TerminalInputRouter.sequenceFor(TerminalKey("Up", "")))
        assertEquals("\u001B[A", TerminalInputRouter.sequenceFor(TerminalKey("↑", "")))
        assertEquals("\u001B", TerminalInputRouter.sequenceFor(TerminalKey("Esc", "")))
        assertEquals("\u007F", TerminalInputRouter.sequenceFor(TerminalKey("Bksp", "")))
        assertEquals("\u001B[5~", TerminalInputRouter.sequenceFor(TerminalKey("PgUp", "")))
        assertEquals("\u001B[24~", TerminalInputRouter.sequenceFor(TerminalKey("F12", "")))
        assertEquals("\u0001", TerminalInputRouter.sequenceFor(TerminalKey("Ctrl-A", "")))
        assertEquals("\u001BX", TerminalInputRouter.sequenceFor(TerminalKey("Alt-X", "")))
        assertEquals("\u001B[1;6D", TerminalInputRouter.sequenceFor(TerminalKey("Ctrl-Shift-Left", "")))
        assertEquals("/", TerminalInputRouter.sequenceFor(TerminalKey("/", "")))
        assertEquals("|", TerminalInputRouter.sequenceFor(TerminalKey("|", "")))
        assertEquals("\u0001", TerminalInputRouter.printableForAndroidKeyEvent('a'.code, ctrl = true, alt = false, shift = false))
        assertEquals("\u001Bx", TerminalInputRouter.printableForAndroidKeyEvent('x'.code, ctrl = false, alt = true, shift = false))
        assertEquals(null, TerminalInputRouter.printableForAndroidKeyEvent(0, ctrl = false, alt = false, shift = false))
        assertEquals("\u0003", TerminalInputRouter.ctrl('c'))
        assertEquals("\u001B", TerminalInputRouter.ctrl('['))
        assertEquals("\u007F", TerminalInputRouter.ctrl('?'))
        assertEquals("\u001B[200~hello\u001B[201~", TerminalInputRouter.paste("hello", bracketed = true))
        assertEquals("hello", TerminalInputRouter.paste("hello", bracketed = false))
        assertEquals("copy me", TerminalClipboardPolicy.copyText("copy me"))
        assertNull(TerminalClipboardPolicy.copyText(""))
        assertEquals("\u001B[200~line 1\nline 2\u001B[201~", TerminalClipboardPolicy.pasteInput("line 1\nline 2", bracketed = true))
        assertEquals("\u001B[200~start  end\u001B[201~", TerminalClipboardPolicy.pasteInput("start \u001B[201~ end", bracketed = true))
        assertNull(TerminalClipboardPolicy.pasteInput("", bracketed = true))
    }

    @Test
    fun terminalClipboardPolicyCapsPasteAndStripsUnsafeControls() {
        assertEquals("abc\n\tdef", TerminalClipboardPolicy.pasteInput("a\u0000b\u001Bc\n\tdef", bracketed = false))
        assertEquals("abc", TerminalClipboardPolicy.pasteInput("\u0000a\u0001b\u001Bc", bracketed = false))
        assertEquals("alpha\nbeta\ngamma", TerminalClipboardPolicy.pasteInput("alpha\r\nbeta\rgamma", bracketed = false))
        assertEquals(
            TerminalClipboardPolicy.MaxPasteChars,
            TerminalClipboardPolicy.pasteInput("x".repeat(TerminalClipboardPolicy.MaxPasteChars + 20), bracketed = false)?.length
        )
        assertNull(TerminalClipboardPolicy.pasteInput("\u0000\u001B", bracketed = false))
    }

    @Test
    fun terminalClipboardPolicyCopiesLatestOutputBlockWithoutPrompt() {
        val transcript = """
            root@example:~# uptime
             10:30:00 up 1 day,  2 users,  load average: 0.12, 0.20, 0.18
            
            root@example:~# df -h
            Filesystem      Size  Used Avail Use% Mounted on
            /dev/sda1        50G   22G   28G  45% /
            root@example:~# 
        """.trimIndent()

        assertEquals(
            "Filesystem      Size  Used Avail Use% Mounted on\n/dev/sda1        50G   22G   28G  45% /",
            TerminalClipboardPolicy.latestOutputBlock(transcript)
        )
        assertNull(TerminalClipboardPolicy.latestOutputBlock("root@example:~# "))
    }

    @Test
    fun connectionLaunchPolicyIsExhaustiveForBackedProtocols() {
        assertEquals(ConnectionLaunchSurface.Terminal, ConnectionLaunchPolicy.surface(ConnectionProtocol.Ssh))
        assertEquals(ConnectionLaunchSurface.Terminal, ConnectionLaunchPolicy.surface(ConnectionProtocol.Mosh))
        assertEquals(ConnectionLaunchSurface.Terminal, ConnectionLaunchPolicy.surface(ConnectionProtocol.EternalTerminal))
        assertEquals(ConnectionLaunchSurface.Terminal, ConnectionLaunchPolicy.surface(ConnectionProtocol.LocalProot))
        assertEquals(ConnectionLaunchSurface.DesktopViewer, ConnectionLaunchPolicy.surface(ConnectionProtocol.Vnc))
        assertEquals(ConnectionLaunchSurface.DesktopViewer, ConnectionLaunchPolicy.surface(ConnectionProtocol.Rdp))
        assertEquals(ConnectionLaunchSurface.FileBrowser, ConnectionLaunchPolicy.surface(ConnectionProtocol.Smb))
        assertEquals(ConnectionLaunchSurface.FileBrowser, ConnectionLaunchPolicy.surface(ConnectionProtocol.Rclone))
        assertEquals(ConnectionProtocol.entries.size, ConnectionProtocol.entries.map { ConnectionLaunchPolicy.surface(it) }.size)
    }

    @Test
    fun terminalAccessoryKeyPolicyNormalizesKnownLabelsOnly() {
        assertEquals(
            listOf("Esc", "Ctrl", "AltGr", "F12", "|", "Ctrl-A", "Alt-X", "Ctrl-Shift-Left"),
            TerminalAccessoryKeyPolicy.labels(" esc, Ctrl, altgr, sudo, F12, |, Ctrl, ctrl-a, alt-x, ctrl-shift-left ")
        )
        assertEquals(TerminalAccessoryKeyPolicy.DefaultCsv, TerminalAccessoryKeyPolicy.normalizeCsv("bad,unknown"))
        assertEquals("\u001B[24~", TerminalAccessoryKeyPolicy.keys("F12").single().sequence)
        assertEquals("\u0001", TerminalAccessoryKeyPolicy.keys("Ctrl-A").single().sequence)
        assertEquals("\u001B[2~", TerminalAccessoryKeyPolicy.keys("Ins").single().sequence)
        assertEquals("\u001B[3~", TerminalAccessoryKeyPolicy.keys("Del").single().sequence)
        assertEquals("\u007F", TerminalAccessoryKeyPolicy.keys("Bksp").single().sequence)
        assertEquals("\r", TerminalAccessoryKeyPolicy.keys("Enter").single().sequence)
        assertEquals("<altgr>", TerminalAccessoryKeyPolicy.keys("AltGr").single().sequence)
    }

    @Test
    fun terminalBackPolicyOrdersTransientDismissalsBeforeNavigation() {
        assertEquals(
            TerminalBackAction.DismissSnippet,
            TerminalBackPolicy.action(
                hasPendingSnippet = true,
                snippetsOpen = true,
                moreKeysOpen = true,
                sessionMenuOpen = true,
                keyboardOpen = true,
                imeVisible = true,
                terminalRequestedKeyboard = true
            )
        )
        assertEquals(
            TerminalBackAction.CloseTransientMenus,
            TerminalBackPolicy.action(
                hasPendingSnippet = false,
                snippetsOpen = false,
                moreKeysOpen = true,
                sessionMenuOpen = false,
                keyboardOpen = true,
                imeVisible = true,
                terminalRequestedKeyboard = false
            )
        )
        assertEquals(
            TerminalBackAction.HideKeyboard,
            TerminalBackPolicy.action(
                hasPendingSnippet = false,
                snippetsOpen = false,
                moreKeysOpen = false,
                sessionMenuOpen = false,
                keyboardOpen = false,
                imeVisible = true,
                terminalRequestedKeyboard = false
            )
        )
        assertEquals(
            TerminalBackAction.NavigateBack,
            TerminalBackPolicy.action(
                hasPendingSnippet = false,
                snippetsOpen = false,
                moreKeysOpen = false,
                sessionMenuOpen = false,
                keyboardOpen = false,
                imeVisible = false,
                terminalRequestedKeyboard = false
            )
        )
    }

    @Test
    fun terminalModifierRouterAppliesCtrlAltAndShiftToInput() {
        val ctrlC = TerminalModifierRouter.apply("c", ctrl = true, alt = false, shift = false)
        val altX = TerminalModifierRouter.apply("x", ctrl = false, alt = true, shift = false)
        val ctrlAltC = TerminalModifierRouter.apply("c", ctrl = true, alt = true, shift = false)
        val shiftA = TerminalModifierRouter.apply("a", ctrl = false, alt = false, shift = true)
        val ctrlBracket = TerminalModifierRouter.apply("[", ctrl = true, alt = false, shift = false)
        val ctrlLeft = TerminalModifierRouter.apply("\u001B[D", ctrl = true, alt = false, shift = false)
        val altRight = TerminalModifierRouter.apply("\u001B[C", ctrl = false, alt = true, shift = false)
        val shiftCtrlPageUp = TerminalModifierRouter.apply("\u001B[5~", ctrl = true, alt = false, shift = true)
        val ctrlAltF1 = TerminalModifierRouter.apply("\u001BOP", ctrl = true, alt = true, shift = false)
        val ctrlBackslash = TerminalModifierRouter.apply("\\", ctrl = true, alt = false, shift = false)
        val ctrlQuestion = TerminalModifierRouter.apply("?", ctrl = true, alt = false, shift = false)
        val ctrlSlash = TerminalModifierRouter.apply("/", ctrl = true, alt = false, shift = false)
        val ctrlRightBracket = TerminalModifierRouter.apply("]", ctrl = true, alt = false, shift = false)

        assertEquals("\u0003", ctrlC.output)
        assertTrue(ctrlC.consumeCtrl)
        assertEquals("\u001Bx", altX.output)
        assertTrue(altX.consumeAlt)
        assertEquals("\u001B\u0003", ctrlAltC.output)
        assertTrue(ctrlAltC.consumeCtrl)
        assertTrue(ctrlAltC.consumeAlt)
        assertEquals("A", shiftA.output)
        assertTrue(shiftA.consumeShift)
        assertEquals("\u001B", ctrlBracket.output)
        assertTrue(ctrlBracket.consumeCtrl)
        assertEquals("\u001B[1;5D", ctrlLeft.output)
        assertTrue(ctrlLeft.consumeCtrl)
        assertEquals("\u001B[1;3C", altRight.output)
        assertTrue(altRight.consumeAlt)
        assertEquals("\u001B[5;6~", shiftCtrlPageUp.output)
        assertTrue(shiftCtrlPageUp.consumeCtrl)
        assertTrue(shiftCtrlPageUp.consumeShift)
        assertEquals("\u001B[1;7P", ctrlAltF1.output)
        assertTrue(ctrlAltF1.consumeCtrl)
        assertTrue(ctrlAltF1.consumeAlt)
        assertEquals("\u001C", ctrlBackslash.output)
        assertTrue(ctrlBackslash.consumeCtrl)
        assertEquals("\u007F", ctrlQuestion.output)
        assertTrue(ctrlQuestion.consumeCtrl)
        assertEquals("\u001F", ctrlSlash.output)
        assertTrue(ctrlSlash.consumeCtrl)
        assertEquals("\u001D", ctrlRightBracket.output)
        assertTrue(ctrlRightBracket.consumeCtrl)
    }

    @Test
    fun terminalInputRouterMapsAndroidHardwareKeys() {
        assertEquals("\u001B", TerminalInputRouter.sequenceForAndroidKeyCode(111))
        assertEquals("\u001B[A", TerminalInputRouter.sequenceForAndroidKeyCode(19))
        assertEquals("\u001B[B", TerminalInputRouter.sequenceForAndroidKeyCode(20))
        assertEquals("\u001B[D", TerminalInputRouter.sequenceForAndroidKeyCode(21))
        assertEquals("\u001B[C", TerminalInputRouter.sequenceForAndroidKeyCode(22))
        assertEquals("\u007F", TerminalInputRouter.sequenceForAndroidKeyCode(67))
        assertEquals("\u001B[3~", TerminalInputRouter.sequenceForAndroidKeyCode(112))
        assertEquals("\u001B[H", TerminalInputRouter.sequenceForAndroidKeyCode(122))
        assertEquals("\u001B[F", TerminalInputRouter.sequenceForAndroidKeyCode(123))
        assertEquals("\u001B[5~", TerminalInputRouter.sequenceForAndroidKeyCode(92))
        assertEquals("\u001B[6~", TerminalInputRouter.sequenceForAndroidKeyCode(93))
        assertEquals("\u001BOP", TerminalInputRouter.sequenceForAndroidKeyCode(131))
        assertEquals("\u001B[24~", TerminalInputRouter.sequenceForAndroidKeyCode(142))
        assertNull(TerminalInputRouter.sequenceForAndroidKeyCode(9999))
        assertEquals("\u001B[1;5D", TerminalInputRouter.sequenceForAndroidKeyEvent(21, ctrl = true, alt = false, shift = false))
        assertEquals("\u001B[1;3C", TerminalInputRouter.sequenceForAndroidKeyEvent(22, ctrl = false, alt = true, shift = false))
        assertEquals("\u001B[5;2~", TerminalInputRouter.sequenceForAndroidKeyEvent(92, ctrl = false, alt = false, shift = true))
        assertEquals("\u001B[1;7P", TerminalInputRouter.sequenceForAndroidKeyEvent(131, ctrl = true, alt = true, shift = false))
    }

    @Test
    fun terminalImeInputReducerDoesNotDuplicateCommittedComposingText() {
        val first = TerminalImeInputReducer.setComposing("", "h")
        val second = TerminalImeInputReducer.setComposing(first.composingText, "he")
        val commit = TerminalImeInputReducer.commit(second.composingText, "he")

        assertEquals("h", first.output)
        assertEquals("e", second.output)
        assertEquals("", commit.output)
        assertEquals("", commit.composingText)
    }

    @Test
    fun terminalImeInputReducerReplacesChangedComposition() {
        val edit = TerminalImeInputReducer.setComposing("abc", "xy")
        val commit = TerminalImeInputReducer.commit(edit.composingText, "xyz")

        assertEquals("\u007F\u007F\u007Fxy", edit.output)
        assertEquals("z", commit.output)
        assertEquals("", commit.composingText)
    }

    @Test
    fun terminalImeInputReducerDropsOversizedAutomaticCommits() {
        val huge = "x".repeat(600)
        val composing = TerminalImeInputReducer.setComposing("abc", huge)
        val commit = TerminalImeInputReducer.commit("", huge)

        assertEquals("", composing.output)
        assertEquals("abc", composing.composingText)
        assertEquals("", commit.output)
        assertEquals("", commit.composingText)
    }

    @Test
    fun terminalImeInputReducerDropsMultilineOrEscapeAutomaticPayloads() {
        val multiline = TerminalImeInputReducer.commit("", "ls\nwhoami")
        val escaped = TerminalImeInputReducer.setComposing("", "\u001B[31mprompt")
        val nul = TerminalImeInputReducer.commit("", "who\u0000ami")

        assertEquals("", multiline.output)
        assertEquals("", escaped.output)
        assertEquals("", nul.output)
    }

    @Test
    fun sftpPathResolverNormalizesJoinsAndParentsRemotePaths() {
        assertEquals(".", SftpPathResolver.normalize(""))
        assertEquals("/", SftpPathResolver.normalize("//"))
        assertEquals("~", SftpPathResolver.normalize("~/"))
        assertEquals("~", SftpPathResolver.normalize("~//"))
        assertEquals("~/logs", SftpPathResolver.normalize("~/logs/"))
        assertEquals("var/log", SftpPathResolver.normalize("var//log/"))
        assertEquals("/var/tmp", SftpPathResolver.normalize("/var/log/../tmp"))
        assertEquals("logs", SftpPathResolver.normalize("./logs"))
        assertEquals("~/app", SftpPathResolver.normalize("~/logs/../app"))
        assertEquals("/", SftpPathResolver.normalize("/../../"))
        assertEquals("/var/log/syslog", SftpPathResolver.join("/var/log", "syslog"))
        assertEquals("upload.txt", SftpPathResolver.join(".", "upload.txt"))
        assertEquals("~/upload.txt", SftpPathResolver.join("~", "upload.txt"))
        assertEquals("/var/log/evil.txt", SftpPathResolver.join("/var/log", "../evil.txt"))
        assertEquals("/var/log/evil.txt", SftpPathResolver.join("/var/log", "nested/evil.txt"))
        assertEquals("/var/log/untitled", SftpPathResolver.join("/var/log", ".."))
        assertEquals("/var", SftpPathResolver.parent("/var/log"))
        assertEquals(".", SftpPathResolver.parent("relative"))
    }

    @Test
    fun sftpPathResolverBuildsStableFallbackCandidates() {
        val server = server(username = "aldr")
        val bookmarks = listOf(
            SftpBookmark("b1", server.id, "Work", "/srv/app", 1L),
            SftpBookmark("b2", "other", "Other", "/tmp", 1L)
        )

        val candidates = SftpPathResolver.listCandidates(server, "", bookmarks)

        assertEquals(listOf(".", "/srv/app", "~", "/home/aldr", "/"), candidates)
    }

    @Test
    fun sftpPathResolverUsesExactCandidateForFolderNavigation() {
        val server = server(username = "aldr")
        val bookmarks = listOf(SftpBookmark("b1", server.id, "Work", "/srv/app", 1L))

        val exact = SftpPathResolver.listCandidatesForNavigation(
            server = server,
            requestedPath = "/var/www",
            bookmarks = bookmarks,
            allowFallback = false
        )
        val fallback = SftpPathResolver.listCandidatesForNavigation(
            server = server,
            requestedPath = "",
            bookmarks = bookmarks,
            allowFallback = true
        )

        assertEquals(listOf("/var/www"), exact)
        assertEquals(listOf("/srv/app", ".", "~", "/home/aldr", "/"), fallback)
    }

    @Test
    fun sftpPathResolverOnlyUsesFallbacksBeforeFirstLoadedDirectory() {
        val server = server(username = "aldr")
        val bookmarks = listOf(SftpBookmark("b1", server.id, "Work", "/srv/app", 1L))

        val firstOpen = SftpPathResolver.navigationPlan(
            server = server,
            requestedPath = "",
            bookmarks = bookmarks,
            allowFallback = true,
            hasLoadedPath = false
        )
        val folderClick = SftpPathResolver.navigationPlan(
            server = server,
            requestedPath = "/srv/app/releases",
            bookmarks = bookmarks,
            allowFallback = true,
            hasLoadedPath = true
        )

        assertEquals(listOf("/srv/app", ".", "~", "/home/aldr", "/"), firstOpen)
        assertEquals(listOf("/srv/app/releases"), folderClick)
    }

    @Test
    fun sftpPathResolverUsesRootHomeForRootLoginFallbacks() {
        val server = server(username = "root")

        val candidates = SftpPathResolver.listCandidates(server, "", emptyList())

        assertEquals(listOf(".", "~", "/root", "/"), candidates)
    }

    @Test
    fun sftpPathResolverChoosesCorrectUploadTargetDirectory() {
        val folder = SftpEntry("logs", "/var/log", true, 0, 1L)
        val file = SftpEntry("syslog", "/var/log/syslog", false, 1024, 1L)
        val relativeFile = SftpEntry("local.txt", "local.txt", false, 1024, 1L)
        val symlink = SftpEntry("current", "/srv/current", false, 0, 1L, SftpEntryType.Symlink)

        assertEquals("/srv/app", SftpPathResolver.uploadTargetDirectory("/srv/app", null))
        assertEquals("/var/log", SftpPathResolver.uploadTargetDirectory("/srv/app", folder))
        assertEquals("/var/log", SftpPathResolver.uploadTargetDirectory("/srv/app", file))
        assertEquals("/srv/app", SftpPathResolver.uploadTargetDirectory("/srv/app", relativeFile))
        assertEquals("/srv", SftpPathResolver.uploadTargetDirectory("/srv/app", symlink))
        assertTrue(symlink.navigable)
    }

    @Test
    fun sftpHostTransferPolicyBuildsSafeFileAndFolderTargets() {
        val file = SftpEntry("app.log", "/var/log/app.log", false, 1024, 1L)
        val nestedName = SftpEntry("nested/evil.txt", "/tmp/evil.txt", false, 5, 1L)
        val folder = SftpEntry("logs", "/var/log", true, 0, 1L)
        val symlink = SftpEntry("current", "/srv/current", false, 0, 1L, SftpEntryType.Symlink)

        assertTrue(SftpHostTransferPolicy.canCopy(file))
        assertEquals("/srv/app.log", SftpHostTransferPolicy.targetPath("/srv", file))
        assertEquals("/srv/evil.txt", SftpHostTransferPolicy.targetPath("/srv", nestedName))
        assertTrue(SftpHostTransferPolicy.canCopy(folder))
        assertEquals("/srv/logs", SftpHostTransferPolicy.targetPath("/srv", folder))
        assertFalse(SftpHostTransferPolicy.canCopy(symlink))
        assertEquals("Host-to-host copy supports files and folders only.", SftpHostTransferPolicy.unavailableReason(symlink))
        assertEquals("Destination folder already exists: /srv/logs", SftpHostTransferPolicy.destinationFolderExistsMessage("/srv/logs"))
    }

    @Test
    fun sftpHostTransferPolicyReportsUnsupportedFolderChildren() {
        val entries = listOf(
            SftpEntry(".", "/srv/.", true, 0, 1L),
            SftpEntry("..", "/srv/..", true, 0, 1L),
            SftpEntry("app.log", "/srv/app.log", false, 1024, 1L),
            SftpEntry("releases", "/srv/releases", true, 0, 1L),
            SftpEntry("current", "/srv/current", false, 0, 1L, SftpEntryType.Symlink),
            SftpEntry("", "/srv/socket", false, 0, 1L, SftpEntryType.Symlink)
        )

        assertEquals(listOf("current", "/srv/socket"), SftpHostTransferPolicy.unsupportedChildNames(entries))
    }

    @Test
    fun sftpEntryFormatsPermissionMetadataWhenAvailable() {
        assertEquals(
            "755 1000:1000",
            SftpEntry(
                name = "app",
                path = "/srv/app",
                directory = true,
                sizeBytes = 0,
                modifiedEpochMillis = 1L,
                permissions = "755",
                owner = "1000",
                group = "1000"
            ).permissionsLabel()
        )
        assertEquals("644", SftpEntry("app.conf", "/srv/app.conf", false, 1, 1L, permissions = "644").permissionsLabel())
        assertEquals(null, SftpEntry("app.conf", "/srv/app.conf", false, 1, 1L).permissionsLabel())
    }

    @Test
    fun sftpPermissionModePolicyParsesOnlyOctalModes() {
        assertEquals(0b110100100, SftpPermissionModePolicy.parseOctalMode("644"))
        assertEquals(0b111101101, SftpPermissionModePolicy.parseOctalMode("0755"))
        assertEquals(1021, SftpPermissionModePolicy.parseOctalMode("1775"))
        assertEquals("644", SftpPermissionModePolicy.displayMode(420))
        assertEquals("1775", SftpPermissionModePolicy.editableMode("1775"))
        assertEquals("", SftpPermissionModePolicy.editableMode("rwxr-xr-x"))
        assertEquals("755", SftpPermissionModePolicy.editableModeOrDefault("rwxr-xr-x", directory = true))
        assertEquals("644", SftpPermissionModePolicy.editableModeOrDefault(null, directory = false))
        assertNull(SftpPermissionModePolicy.parseOctalMode(""))
        assertNull(SftpPermissionModePolicy.parseOctalMode("12"))
        assertNull(SftpPermissionModePolicy.parseOctalMode("888"))
        assertNull(SftpPermissionModePolicy.parseOctalMode("10000"))
    }

    @Test
    fun containerRuntimeActionPolicyBuildsOnlySafeCommands() {
        assertEquals("docker restart web-1", ContainerRuntimeActionPolicy.command("docker", "web-1", "restart"))
        assertEquals("podman start api_1", ContainerRuntimeActionPolicy.command(" Podman ", "api_1", " START "))
        assertEquals("docker logs --tail 120 web-1", ContainerRuntimeActionPolicy.inspectCommand("docker", "web-1", "logs"))
        assertEquals("podman inspect api_1", ContainerRuntimeActionPolicy.inspectCommand(" Podman ", "api_1", " INSPECT "))
        assertEquals("docker stats --no-stream web-1", ContainerRuntimeActionPolicy.inspectCommand("docker", "web-1", "stats"))
        assertEquals("docker rm web-1", ContainerRuntimeActionPolicy.command("docker", "web-1", "delete"))
        assertEquals("docker rm -f web-1", ContainerRuntimeActionPolicy.command("docker", "web-1", "force-delete"))
        assertEquals("podman rmi -f sha256:abc123", ContainerRuntimeActionPolicy.command("podman", "sha256:abc123", "remove-image"))
        assertEquals("docker images", ContainerRuntimeActionPolicy.globalCommand("docker", "images"))
        assertEquals("podman container prune -f", ContainerRuntimeActionPolicy.globalCommand(" Podman ", " prune-containers "))
        assertEquals("docker image prune -a -f", ContainerRuntimeActionPolicy.globalCommand("docker", "prune-images"))
        assertEquals("docker volume prune -f", ContainerRuntimeActionPolicy.globalCommand("docker", "prune-volumes"))
        assertEquals("docker system prune -a -f --volumes", ContainerRuntimeActionPolicy.globalCommand("docker", "prune-system"))
        assertEquals(listOf("logs", "inspect", "stats", "restart", "stop", "force-delete"), ContainerRuntimeActionPolicy.actionsFor("running"))
        assertEquals(listOf("logs", "inspect", "stats", "start", "delete"), ContainerRuntimeActionPolicy.actionsFor("exited"))
        assertEquals(listOf("prune-containers", "prune-images", "prune-volumes", "prune-system"), ContainerRuntimeActionPolicy.globalActions())

        assertNull(ContainerRuntimeActionPolicy.command("nerdctl", "web", "restart"))
        assertNull(ContainerRuntimeActionPolicy.command("docker", "web;rm", "restart"))
        assertNull(ContainerRuntimeActionPolicy.command("docker", "web", "exec"))
        assertNull(ContainerRuntimeActionPolicy.inspectCommand("nerdctl", "web", "logs"))
        assertNull(ContainerRuntimeActionPolicy.inspectCommand("docker", "web;rm", "logs"))
        assertNull(ContainerRuntimeActionPolicy.inspectCommand("docker", "web", "prune"))
        assertNull(ContainerRuntimeActionPolicy.globalCommand("nerdctl", "images"))
        assertNull(ContainerRuntimeActionPolicy.globalCommand("docker", "rm-all"))
    }

    @Test
    fun systemdServiceActionPolicyBuildsOnlySafeCommands() {
        assertEquals("systemctl restart ssh.service", SystemdServiceActionPolicy.command("ssh.service", " RESTART "))
        assertEquals("systemctl start app@blue.service", SystemdServiceActionPolicy.command("app@blue.service", "start"))
        assertEquals("systemctl status ssh.service --no-pager -l", SystemdServiceActionPolicy.inspectCommand("ssh.service", "status"))
        assertEquals("journalctl -u ssh.service -n 80 --no-pager", SystemdServiceActionPolicy.inspectCommand("ssh.service", "logs"))
        assertEquals(listOf("status", "logs", "restart", "stop"), SystemdServiceActionPolicy.actionsFor("active", "running"))
        assertEquals(listOf("status", "logs", "restart", "start"), SystemdServiceActionPolicy.actionsFor("failed", "failed"))

        assertNull(SystemdServiceActionPolicy.command("ssh", "restart"))
        assertNull(SystemdServiceActionPolicy.command("ssh.service;rm", "restart"))
        assertNull(SystemdServiceActionPolicy.command("ssh.service", "reload"))
        assertNull(SystemdServiceActionPolicy.inspectCommand("ssh.service;rm", "logs"))
        assertNull(SystemdServiceActionPolicy.inspectCommand("ssh.service", "cat"))
    }

    @Test
    fun processActionPolicyBuildsOnlySafeCommands() {
        assertEquals("kill -s TERM 42", ProcessActionPolicy.command(42, " TERMINATE "))
        assertEquals("kill -s KILL 42", ProcessActionPolicy.command(42, "force-stop"))
        assertEquals("renice -n 10 -p 42", ProcessActionPolicy.command(42, "lower-priority"))
        assertEquals(listOf("lower-priority", "terminate", "force-stop"), ProcessActionPolicy.actionsFor(42))
        assertEquals(emptyList<String>(), ProcessActionPolicy.actionsFor(null))

        assertNull(ProcessActionPolicy.command(null, "terminate"))
        assertNull(ProcessActionPolicy.command(0, "terminate"))
        assertNull(ProcessActionPolicy.command(42, "renice"))
        assertNull(ProcessActionPolicy.command(42, "lower-priority;rm"))
    }

    @Test
    fun hostInfoCommandPolicyFormatsBoundedOutput() {
        assertTrue(HostCommandSafety.isAutomaticCommandSafe(HostInfoCommandPolicy.command))
        assertEquals("No host info returned.", HostInfoCommandPolicy.display(" \n "))
        assertEquals("Hostname: box\nUser: root", HostInfoCommandPolicy.display("\nHostname: box  \nUser: root\n"))
        assertEquals(8, HostInfoCommandPolicy.display("x".repeat(20), maxChars = 8).length)
    }

    @Test
    fun sftpPathResolverSortsBySelectedModeWithDirectoriesFirst() {
        val entries = listOf(
            SftpEntry("z.log", "/z.log", false, 10, 10L),
            SftpEntry("src", "/src", true, 0, 1L),
            SftpEntry("a.bin", "/a.bin", false, 99, 5L),
            SftpEntry("bin", "/bin", true, 0, 9L)
        )

        assertEquals(
            listOf("bin", "src", "a.bin", "z.log"),
            SftpPathResolver.sortForFileManager(entries, SftpSortMode.Name, descending = false).map { it.name }
        )
        assertEquals(
            listOf("bin", "src", "z.log", "a.bin"),
            SftpPathResolver.sortForFileManager(entries, SftpSortMode.Modified, descending = true).map { it.name }
        )
        assertEquals(
            listOf("bin", "src", "z.log", "a.bin"),
            SftpPathResolver.sortForFileManager(entries, SftpSortMode.Size, descending = false).map { it.name }
        )
    }

    @Test
    fun sftpPathResolverFiltersHiddenEntriesWhenRequested() {
        val entries = listOf(
            SftpEntry(".", "/srv/.", true, 0, 1L),
            SftpEntry("..", "/srv/..", true, 0, 1L),
            SftpEntry(".env", "/srv/.env", false, 10, 1L),
            SftpEntry("app.conf", "/srv/app.conf", false, 10, 1L)
        )

        assertEquals(
            listOf(".", "..", "app.conf"),
            SftpPathResolver.visibleForFileManager(entries, showHidden = false).map { it.name }
        )
        assertEquals(
            listOf(".", "..", ".env", "app.conf"),
            SftpPathResolver.visibleForFileManager(entries, showHidden = true).map { it.name }
        )
    }

    @Test
    fun sftpPathResolverFiltersEntriesByNameOrPath() {
        val entries = listOf(
            SftpEntry("app.log", "/srv/app.log", false, 10, 1L),
            SftpEntry("nginx.conf", "/etc/nginx/nginx.conf", false, 10, 1L),
            SftpEntry("bin", "/usr/local/bin", true, 0, 1L)
        )

        assertEquals(listOf("app.log"), SftpPathResolver.filterForFileManager(entries, "APP").map { it.name })
        assertEquals(listOf("nginx.conf"), SftpPathResolver.filterForFileManager(entries, "/etc").map { it.name })
        assertEquals(entries, SftpPathResolver.filterForFileManager(entries, " "))
    }

    @Test
    fun sftpPathResolverSanitizesLeafNamesForDisplayAndTempFiles() {
        assertEquals("app.log", SftpPathResolver.leafName("/var/log/app.log"))
        assertEquals("evil.txt", SftpPathResolver.leafName("nested/evil.txt"))
        assertEquals("bad_name.txt", SftpPathResolver.leafName("bad\u0000name.txt"))
        assertEquals(160, SftpPathResolver.leafName("${"a".repeat(200)}.txt").length)
        assertEquals("untitled", SftpPathResolver.leafName("../.."))
    }

    @Test
    fun sftpTextFilePolicyRejectsBinaryAndInvalidUtf8Content() {
        assertTrue(SftpTextFilePolicy.canEdit(SftpEntry("app.conf", "/etc/app.conf", false, 512, 1L)))
        assertTrue(SftpTextFilePolicy.canEdit(SftpEntry("Dockerfile", "/srv/Dockerfile", false, 0, 1L)))
        assertTrue(SftpTextFilePolicy.canEdit(SftpEntry(".bashrc", "/home/me/.bashrc", false, 128, 1L)))
        assertFalse(SftpTextFilePolicy.canEdit(SftpEntry("bin", "/usr/bin/bin", false, 512, 1L)))
        assertFalse(SftpTextFilePolicy.canEdit(SftpEntry("logs", "/var/log", true, 0, 1L)))
        assertFalse(SftpTextFilePolicy.canEdit(SftpEntry("huge.log", "/var/log/huge.log", false, SftpTextFilePolicy.MaxEditableBytes + 1, 1L)))

        assertEquals("hello", SftpTextFilePolicy.decodeEditableText("hello".toByteArray()).getOrThrow())
        assertTrue(SftpTextFilePolicy.decodeEditableText(byteArrayOf(0x66, 0x00, 0x6f)).exceptionOrNull()?.message.orEmpty().contains("binary"))
        assertTrue(SftpTextFilePolicy.decodeEditableText(byteArrayOf(0x66, 0xC3.toByte(), 0x28)).exceptionOrNull()?.message.orEmpty().contains("UTF-8"))
    }

    @Test
    fun sftpPathResolverBuildsBreadcrumbSegments() {
        assertEquals(
            listOf("/" to "/", "var" to "/var", "log" to "/var/log"),
            SftpPathResolver.breadcrumbSegments("/var/log")
        )
        assertEquals(
            listOf("~" to "~", "deploy" to "~/deploy", "app" to "~/deploy/app"),
            SftpPathResolver.breadcrumbSegments("~/deploy/app")
        )
        assertEquals(
            listOf("." to ".", "relative" to "relative", "logs" to "relative/logs"),
            SftpPathResolver.breadcrumbSegments("relative/logs")
        )
    }

    @Test
    fun sftpErrorMapperMakesCommonFailuresActionable() {
        assertTrue(
            SftpErrorMapper.message("list", "/root", IllegalStateException("Permission denied"))
                .contains("Check ownership")
        )
        assertTrue(
            SftpErrorMapper.message("open", "/root", IllegalStateException("Permission denied"))
                .startsWith("Cannot list '/root'")
        )
        assertTrue(
            SftpErrorMapper.message("download", "/tmp/missing.txt", IllegalStateException("No such file"))
                .contains("Path not found")
        )
        assertTrue(
            SftpErrorMapper.message("list", "/", IllegalStateException("SFTP subsystem not available"))
                .contains("SFTP subsystem is unavailable")
        )
        assertTrue(
            SftpErrorMapper.message("upload to", "/srv/app/file.txt", IllegalStateException("connection reset"))
                .contains("Reconnect")
        )
        assertTrue(
            SftpErrorMapper.message("New Folder", "/srv/app/reports", IllegalStateException("Failure"))
                .startsWith("Cannot create folder '/srv/app/reports'")
        )
    }

    @Test
    fun sftpFileNamePolicyRejectsPathLikeMutationNames() {
        assertEquals("release.txt", SftpFileNamePolicy.normalizeEditableName(" release.txt "))
        assertEquals("bad_name.txt", SftpFileNamePolicy.normalizeEditableName("bad\u0000name.txt"))
        assertNull(SftpFileNamePolicy.normalizeEditableName("../release.txt"))
        assertNull(SftpFileNamePolicy.normalizeEditableName("nested/release.txt"))
        assertNull(SftpFileNamePolicy.normalizeEditableName(".."))
        assertTrue(SftpFileNamePolicy.errorMessage("Rename").contains("without slashes"))
    }

    @Test
    fun sftpErrorMapperExplainsCredentialAndNetworkFailures() {
        assertTrue(
            SftpErrorMapper.message(
                "open",
                ".",
                IllegalStateException("/data/user/0/com.chrono.ssh/cache/ssh-keys/chrono.key: open failed: ENOENT")
            ).contains("Re-import the key")
        )
        assertTrue(
            SftpErrorMapper.message("open", ".", IllegalStateException("Exhausted available authentication methods"))
                .contains("authorized_keys")
        )
        assertTrue(
            SftpErrorMapper.message("open", ".", IllegalStateException("Private-key auth failed for 'prod'"))
                .contains("PubkeyAuthentication")
        )
        assertTrue(
            SftpErrorMapper.message("open", ".", IllegalStateException("The key is encrypted and no passphrase was provided."))
                .contains("private-key passphrase")
        )
        assertTrue(
            SftpErrorMapper.message("open", ".", IllegalStateException("Host key is not trusted yet"))
                .contains("Host key approval")
        )
        assertTrue(
            SftpErrorMapper.message("open", ".", IllegalStateException("ENETUNREACH (Network is unreachable)"))
                .contains("VM network mode")
        )
    }

    @Test
    fun sftpDeletePolicyRetriesDirectorySpecificFileDeleteFailures() {
        assertTrue(SftpDeletePolicy.shouldRetryAsDirectory(IllegalStateException("Failure: is a directory")))
        assertTrue(SftpDeletePolicy.shouldRetryAsDirectory(IllegalStateException("Cannot remove directory with rm")))
        assertTrue(SftpDeletePolicy.shouldRetryAsDirectory(IllegalStateException("Directory target is not regular file")))
        assertFalse(SftpDeletePolicy.shouldRetryAsDirectory(IllegalStateException("Permission denied")))
        assertFalse(SftpDeletePolicy.shouldRetryAsDirectory(IllegalStateException("No such file")))
    }

    @Test
    fun sftpAtomicUploadPolicyCreatesSiblingTempPaths() {
        assertEquals(
            "/srv/app/.release.tar.gz.chronossh-abc123.tmp",
            SftpAtomicUploadPolicy.tempPathFor("/srv/app/release.tar.gz", "abc-123")
        )
        assertEquals(
            ".local.txt.chronossh-upload.tmp",
            SftpAtomicUploadPolicy.tempPathFor("local.txt", "!!!")
        )
        assertEquals(
            "~/deploy/.app.jar.chronossh-run42.tmp",
            SftpAtomicUploadPolicy.tempPathFor("~/deploy/app.jar", "run42")
        )
    }

    @Test
    fun sftpUploadFallbackPolicyRetriesServerCompatibilityFailuresOnly() {
        assertTrue(SftpUploadFallbackPolicy.shouldTryDirectUpload(IllegalStateException("Failure during rename")))
        assertTrue(SftpUploadFallbackPolicy.shouldTryDirectUpload(IllegalStateException("Operation unsupported")))
        assertTrue(SftpUploadFallbackPolicy.shouldTryDirectUpload(IllegalStateException(".release.chronossh-abcd.tmp rejected")))

        assertFalse(SftpUploadFallbackPolicy.shouldTryDirectUpload(IllegalStateException("Permission denied")))
        assertFalse(SftpUploadFallbackPolicy.shouldTryDirectUpload(IllegalStateException("No such file")))
        assertFalse(SftpUploadFallbackPolicy.shouldTryDirectUpload(IllegalStateException("connection reset by peer")))
    }

    @Test
    fun sftpBrowserOperationPolicyKeepsRecoverableRefreshFailuresInPlace() {
        assertEquals(
            "Delete complete. Refresh failed: Permission denied",
            SftpBrowserOperationPolicy.refreshFailureStatus(
                "Delete complete",
                IllegalStateException("Permission denied")
            )
        )
        assertEquals(
            "Upload complete. SFTP connection closed during refresh; reconnect to continue.",
            SftpBrowserOperationPolicy.refreshFailureStatus(
                "Upload complete",
                IllegalStateException("Socket closed during refresh")
            )
        )
        assertEquals(
            "Open complete. Refresh failed: Host key approval required",
            SftpBrowserOperationPolicy.refreshFailureStatus(
                "Open complete",
                IllegalStateException("Host key approval required")
            )
        )
    }

    @Test
    fun sftpBrowserOperationPolicyExplainsPickerReconnectActions() {
        assertEquals(
            "Reconnect SFTP to staging, then choose the upload file again.",
            SftpBrowserOperationPolicy.missingClientMessage("staging", "uploading")
        )
        assertEquals(
            "Reconnect SFTP to staging, then choose the download destination again.",
            SftpBrowserOperationPolicy.missingClientMessage("staging", "downloading")
        )
        assertEquals(
            "Reconnect SFTP to staging, then try rename again.",
            SftpBrowserOperationPolicy.missingClientMessage("staging", "Rename")
        )
    }

    @Test
    fun sftpClientHealthDropsOnlyConnectionBreakingFailures() {
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("channel closed")))
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("connection reset by peer")))
        assertFalse(SftpClientHealth.shouldDropClient(IllegalStateException("Permission denied")))
        assertFalse(SftpClientHealth.shouldDropClient(IllegalStateException("No such file")))
        assertFalse(SftpClientHealth.shouldDropClient(IllegalStateException("Host key approval required")))
        assertFalse(SftpClientHealth.shouldDropClient(IllegalStateException("Authentication failed")))
        assertFalse(SftpClientHealth.shouldDropClient(IllegalStateException("Missing credential")))
    }

    @Test
    fun sshAuthFailureHintsExplainPrivateKeyServerMethodMismatch() {
        val keyInfo = KeyMaterialInfo(
            valid = true,
            encrypted = false,
            format = "openssh private key",
            fingerprint = "SHA256:test",
            summary = "OpenSSH key"
        )

        assertTrue(
            SshAuthFailureHints.privateKeyRejected("not reported", keyInfo, passphraseProvided = false)
                .contains("did not report allowed methods")
        )
        assertTrue(
            SshAuthFailureHints.privateKeyRejected("password,keyboard-interactive", keyInfo, passphraseProvided = false)
                .contains("did not advertise publickey")
        )
        assertTrue(
            SshAuthFailureHints.privateKeyRejected("publickey,password", keyInfo, passphraseProvided = false)
                .contains("authorized_keys")
        )
    }

    @Test
    fun sshAuthFailureHintsCallOutMissingPrivateKeyPassphrase() {
        val keyInfo = KeyMaterialInfo(
            valid = true,
            encrypted = true,
            format = "openssh private key",
            fingerprint = "SHA256:test",
            summary = "Encrypted OpenSSH key"
        )

        assertTrue(
            SshAuthFailureHints.privateKeyRejected("publickey", keyInfo, passphraseProvided = false)
                .contains("no passphrase")
        )
        assertTrue(
            SshAuthFailureHints.requiresPrivateKeyPassphrase(
                SshFailure.Authentication("Private key 'prod' is encrypted. Enter its passphrase to connect.")
            )
        )
        assertTrue(
            SshAuthFailureHints.requiresPrivateKeyPassphrase(
                SshFailure.Authentication("The key is encrypted and no passphrase was provided.")
            )
        )
        assertFalse(
            SshAuthFailureHints.requiresPrivateKeyPassphrase(
                SshFailure.Authentication("Authentication failed. Check authorized_keys.")
            )
        )
    }

    @Test
    fun sshConnectionErrorClassifierExplainsAlgorithmNegotiationFailures() {
        val kex = SshConnectionErrorClassifier.classify(
            message = "SSH connect failed for example.test:22",
            detail = "Unable to negotiate; no matching key exchange method found. Their offer: curve25519-sha256",
            cause = null
        )
        val cipher = SshConnectionErrorClassifier.classify(
            message = "SSH connect failed for example.test:22",
            detail = "No matching cipher found",
            cause = null
        )

        assertTrue(kex is SshFailure.Unsupported)
        assertTrue(kex!!.message.orEmpty().contains("key-exchange"))
        assertTrue(kex.message.orEmpty().contains("curve25519"))
        assertTrue(cipher is SshFailure.Unsupported)
        assertTrue(cipher!!.message.orEmpty().contains("cipher"))
    }

    @Test
    fun sshConnectionErrorClassifierIgnoresPlainAuthFailures() {
        val classified = SshConnectionErrorClassifier.classify(
            message = "SSH connect failed",
            detail = "Exhausted available authentication methods",
            cause = null
        )

        assertNull(classified)
    }

    @Test
    fun transferStateReducerKeepsCancelledTransferCancelledAfterLateProgress() {
        val cancelled = transfer(TransferRecordState.Cancelled, progress = 0.42f, message = "Cancelled")
        val lateRunning = transfer(TransferRecordState.Running, progress = 0.86f, message = "Uploading 86%")
        val lateComplete = transfer(TransferRecordState.Complete, progress = 1f, message = "Upload complete")

        assertEquals(cancelled, TransferStateReducer.reduce(cancelled, lateRunning))
        assertEquals(cancelled, TransferStateReducer.reduce(cancelled, lateComplete))
    }

    @Test
    fun transferStateReducerAllowsNormalTransferProgression() {
        val running = transfer(TransferRecordState.Running, progress = 0.2f, message = "Uploading 20%")
        val complete = transfer(TransferRecordState.Complete, progress = 1f, message = "Upload complete")

        assertEquals(complete, TransferStateReducer.reduce(running, complete))
        assertEquals(running, TransferStateReducer.reduce(null, running))
    }

    @Test
    fun transferPersistencePolicyFailsStaleActiveTransfers() {
        val queued = transfer(TransferRecordState.Queued, progress = 0f, message = "Queued")
        val running = transfer(TransferRecordState.Running, progress = 0.4f, message = "Downloading")
        val complete = transfer(TransferRecordState.Complete, progress = 1f, message = "Done")

        assertEquals(TransferRecordState.Failed, TransferPersistencePolicy.normalizeLoaded(queued).state)
        assertEquals(TransferRecordState.Failed, TransferPersistencePolicy.normalizeLoaded(running).state)
        assertEquals("Interrupted before completion.", TransferPersistencePolicy.normalizeLoaded(running).message)
        assertEquals(complete, TransferPersistencePolicy.normalizeLoaded(complete))
    }

    @Test
    fun transferPersistencePolicyKeepsRuntimeActiveTransfersPersistable() {
        val running = transfer(TransferRecordState.Running, progress = 0.4f, message = "Downloading")

        assertEquals(TransferRecordState.Running, TransferPersistencePolicy.normalizePersisted(running)!!.state)
    }

    @Test
    fun transferPersistencePolicyRejectsUnknownPersistedEnums() {
        assertNull(TransferPersistencePolicy.directionFromPersisted("Sideways"))
        assertNull(TransferPersistencePolicy.stateFromPersisted("AlmostDone"))
    }

    @Test
    fun transferPersistencePolicyParsesValidPersistedEnums() {
        assertEquals(TransferDirection.Download, TransferPersistencePolicy.directionFromPersisted("Download"))
        assertEquals(TransferDirection.Upload, TransferPersistencePolicy.directionFromPersisted("Upload"))
        assertEquals(TransferRecordState.Complete, TransferPersistencePolicy.stateFromPersisted("Complete"))
    }

    @Test
    fun transferPersistencePolicyNormalizesSafePersistedMetadata() {
        val longName = "x".repeat(200)
        val longMessage = "m".repeat(350)
        val loaded = TransferPersistencePolicy.normalizePersisted(
            transfer(TransferRecordState.Complete, progress = 1.2f, message = "Done").copy(
                id = " transfer-1 ",
                serverId = " server-1 ",
                remotePath = " /srv/app/file.txt ",
                localDisplayName = " $longName\u0000 ",
                message = "\u0007$longMessage",
                updatedAtEpochMillis = -1
            )
        )

        assertEquals("transfer-1", loaded!!.id)
        assertEquals("server-1", loaded.serverId)
        assertEquals("/srv/app/file.txt", loaded.remotePath)
        assertEquals(160, loaded.localDisplayName.length)
        assertFalse(loaded.localDisplayName.any { it.isISOControl() })
        assertEquals(300, loaded.message.length)
        assertFalse(loaded.message.any { it.isISOControl() })
        assertEquals(1f, loaded.progress)
        assertEquals(0L, loaded.updatedAtEpochMillis)
    }

    @Test
    fun transferPersistencePolicyRejectsBlankPersistedIdentityAndPath() {
        val valid = transfer(TransferRecordState.Complete, progress = 1f, message = "Done")

        assertNull(TransferPersistencePolicy.normalizePersisted(valid.copy(id = " ")))
        assertNull(TransferPersistencePolicy.normalizePersisted(valid.copy(serverId = " ")))
        assertNull(TransferPersistencePolicy.normalizePersisted(valid.copy(remotePath = " ")))
    }

    @Test
    fun transferCompletionPolicyIdentifiesTerminalStates() {
        assertFalse(TransferCompletionPolicy.isTerminal(TransferRecordState.Queued))
        assertFalse(TransferCompletionPolicy.isTerminal(TransferRecordState.Running))
        assertTrue(TransferCompletionPolicy.isTerminal(TransferRecordState.Complete))
        assertTrue(TransferCompletionPolicy.isTerminal(TransferRecordState.Failed))
        assertTrue(TransferCompletionPolicy.isTerminal(TransferRecordState.Cancelled))
    }

    @Test
    fun connectionEventPersistencePolicyRejectsUnknownPersistedLevels() {
        assertNull(ConnectionEventPersistencePolicy.levelFromPersisted("Notice"))
        assertEquals(ConnectionEventLevel.Warning, ConnectionEventPersistencePolicy.levelFromPersisted("Warning"))
    }

    @Test
    fun connectionEventPersistencePolicyNormalizesSafeMetadata() {
        val longMessage = "m".repeat(600)
        val loaded = ConnectionEventPersistencePolicy.normalizePersisted(
            connectionEvent().copy(
                id = " event-1 ",
                serverId = " server-1 ",
                atEpochMillis = -1,
                message = " Metrics\u0000 updated. $longMessage "
            )
        )

        assertEquals("event-1", loaded!!.id)
        assertEquals("server-1", loaded.serverId)
        assertEquals(0L, loaded.atEpochMillis)
        assertEquals(500, loaded.message.length)
        assertFalse(loaded.message.any { it.isISOControl() })
        assertTrue(loaded.message.startsWith("Metrics  updated."))
    }

    @Test
    fun connectionEventPersistencePolicyRejectsBlankIdentityAndMessage() {
        val valid = connectionEvent()

        assertNull(ConnectionEventPersistencePolicy.normalizePersisted(valid.copy(id = " ")))
        assertNull(ConnectionEventPersistencePolicy.normalizePersisted(valid.copy(serverId = " ")))
        assertNull(ConnectionEventPersistencePolicy.normalizePersisted(valid.copy(message = " ")))
    }

    @Test
    fun transferCancellationRegistryCancelsActiveJobsOnly() {
        val active = Job()
        val completed = Job().also { it.complete() }

        TransferCancellationRegistry.register("active-transfer-test", active)
        TransferCancellationRegistry.register("completed-transfer-test", completed)

        assertTrue(TransferCancellationRegistry.cancel("active-transfer-test"))
        assertTrue(active.isCancelled)
        assertFalse(TransferCancellationRegistry.cancel("completed-transfer-test"))
        assertFalse(TransferCancellationRegistry.cancel("missing-transfer-test"))
    }

    @Test
    fun transferCancellationRegistryDropsTerminalTransfersWithoutCancellingThem() {
        val completeJob = Job()
        val runningJob = Job()

        TransferCancellationRegistry.register("terminal-transfer-test", completeJob)
        TransferCancellationRegistry.unregisterIfTerminal("terminal-transfer-test", TransferRecordState.Complete)
        assertFalse(TransferCancellationRegistry.cancel("terminal-transfer-test"))
        assertFalse(completeJob.isCancelled)

        TransferCancellationRegistry.register("running-transfer-test", runningJob)
        TransferCancellationRegistry.unregisterIfTerminal("running-transfer-test", TransferRecordState.Running)
        assertTrue(TransferCancellationRegistry.cancel("running-transfer-test"))
        assertTrue(runningJob.isCancelled)
    }

    @Test
    fun sftpClientHealthDropsNetworkBrokenPipeAndRouteFailures() {
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("Broken pipe")))
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("Pipe closed")))
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("Socket closed during refresh")))
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("No route to host")))
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("Connection refused")))
        assertTrue(SftpClientHealth.shouldDropClient(IllegalStateException("Network is unreachable")))
    }

    @Test
    fun sftpErrorMapperExplainsBrokenNetworkPaths() {
        val message = SftpErrorMapper.message("download", "/srv/file.log", IllegalStateException("Broken pipe"))

        assertTrue(message.contains("Network path failed"))
        assertTrue(message.contains("/srv/file.log"))
    }

    @Test
    fun credentialPassphrasePolicyClearsSavedPassphraseWhenAskPassphraseIsSelected() {
        assertEquals(
            CredentialPassphraseAction.DeleteExisting,
            CredentialPassphrasePolicy.resolve(
                isPrivateKey = true,
                existingPassphraseRef = "secret-key-passphrase",
                passphrase = "",
                savePassphrase = false
            )
        )
    }

    @Test
    fun credentialPassphrasePolicyKeepsOrStoresPrivateKeyPassphraseIntentionally() {
        assertEquals(
            CredentialPassphraseAction.KeepExisting,
            CredentialPassphrasePolicy.resolve(
                isPrivateKey = true,
                existingPassphraseRef = "secret-key-passphrase",
                passphrase = "",
                savePassphrase = true
            )
        )
        assertEquals(
            CredentialPassphraseAction.StoreNew,
            CredentialPassphrasePolicy.resolve(
                isPrivateKey = true,
                existingPassphraseRef = "secret-key-passphrase",
                passphrase = "new-passphrase",
                savePassphrase = true
            )
        )
    }

    @Test
    fun privateKeyPassphraseResolverLoadsSavedSecretRef() = runBlocking {
        var loadedRef: String? = null
        assertEquals(
            "saved-passphrase",
            resolvePrivateKeyPassphrase(
                privateKeyPassphrase = null,
                savedPassphraseRef = " secret-key-passphrase ",
                loadSecret = { ref ->
                    loadedRef = ref
                    "saved-passphrase".toByteArray()
                }
            )
        )
        assertEquals("secret-key-passphrase", loadedRef)
    }

    @Test
    fun privateKeyPassphraseResolverPrefersTypedPassphraseOverSavedRef() = runBlocking {
        assertEquals(
            "typed-passphrase",
            resolvePrivateKeyPassphrase(
                privateKeyPassphrase = "typed-passphrase",
                savedPassphraseRef = "secret-key-passphrase",
                loadSecret = { error("Saved passphrase should not be loaded when a typed passphrase is provided.") }
            )
        )
    }

    @Test
    fun credentialPassphrasePolicyDeletesPassphraseForNonKeyCredentials() {
        assertEquals(
            CredentialPassphraseAction.DeleteExisting,
            CredentialPassphrasePolicy.resolve(
                isPrivateKey = false,
                existingPassphraseRef = "secret-key-passphrase",
                passphrase = "",
                savePassphrase = true
            )
        )
        assertEquals(
            CredentialPassphraseAction.None,
            CredentialPassphrasePolicy.resolve(
                isPrivateKey = false,
                existingPassphraseRef = null,
                passphrase = "",
                savePassphrase = false
            )
        )
    }

    @Test
    fun backupCredentialPolicyKeepsMetadataButRemovesSecretRefs() {
        val imported = Credential(
            id = "identity-1",
            label = "Deploy key",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "OpenSSH key SHA256:abc",
            encryptedPayloadRef = "secret-old",
            createdAtEpochMillis = 42L,
            passphraseRef = "secret-passphrase",
            lastUsedEpochMillis = 99L
        )

        val sanitized = BackupCredentialPolicy.sanitizeImportedMetadata(imported)

        checkNotNull(sanitized)
        assertEquals("identity-1", sanitized.id)
        assertEquals("Deploy key", sanitized.label)
        assertEquals(CredentialType.PrivateKey, sanitized.type)
        assertEquals("OpenSSH key SHA256:abc", sanitized.publicKeyPreview)
        assertEquals(BackupCredentialPolicy.IMPORT_REQUIRED_REF, sanitized.encryptedPayloadRef)
        assertNull(sanitized.passphraseRef)
        assertEquals(42L, sanitized.createdAtEpochMillis)
        assertEquals(0L, sanitized.lastUsedEpochMillis)
        assertFalse(sanitized.secretBacked)
    }

    @Test
    fun credentialPersistencePolicyNormalizesSafePersistedMetadata() {
        val loaded = CredentialPersistencePolicy.normalizeLoaded(
            Credential(
                id = " identity-1 ",
                label = " ",
                type = CredentialType.PrivateKey,
                publicKeyPreview = " OpenSSH key SHA256:abc ",
                encryptedPayloadRef = " secret-key ",
                createdAtEpochMillis = -1L,
                passphraseRef = " secret-passphrase ",
                lastUsedEpochMillis = -2L
            )
        )

        checkNotNull(loaded)
        assertEquals("identity-1", loaded.id)
        assertEquals("Private key identity", loaded.label)
        assertEquals("OpenSSH key SHA256:abc", loaded.publicKeyPreview)
        assertEquals("secret-key", loaded.encryptedPayloadRef)
        assertEquals("secret-passphrase", loaded.passphraseRef)
        assertEquals(0L, loaded.createdAtEpochMillis)
        assertEquals(0L, loaded.lastUsedEpochMillis)
    }

    @Test
    fun credentialPersistencePolicyRejectsUnusablePersistedCredentials() {
        val valid = Credential(
            id = "identity-1",
            label = "Password",
            type = CredentialType.Password,
            publicKeyPreview = null,
            encryptedPayloadRef = "secret-password",
            createdAtEpochMillis = 1L
        )

        assertNull(CredentialPersistencePolicy.normalizeLoaded(valid.copy(id = " ")))
        assertNull(CredentialPersistencePolicy.normalizeLoaded(valid.copy(encryptedPayloadRef = " ")))
        assertEquals(CredentialType.HardwareKey, CredentialPersistencePolicy.normalizeLoaded(valid.copy(type = CredentialType.HardwareKey))?.type)
    }

    @Test
    fun backupCredentialPolicyRejectsUnsupportedImportedIdentities() {
        assertNull(
            BackupCredentialPolicy.sanitizeImportedMetadata(
                Credential(
                    id = "",
                    label = "Missing id",
                    type = CredentialType.Password,
                    publicKeyPreview = null,
                    encryptedPayloadRef = "secret-old",
                    createdAtEpochMillis = 0L
                )
            )
        )
        assertEquals(
            CredentialType.HardwareKey,
            BackupCredentialPolicy.sanitizeImportedMetadata(
                Credential(
                    id = "hardware-1",
                    label = "Security key",
                    type = CredentialType.HardwareKey,
                    publicKeyPreview = "sk-ssh-ed25519@openssh.com AAAA",
                    encryptedPayloadRef = "secret-old",
                    createdAtEpochMillis = 0L
                )
            )?.type
        )
    }

    @Test
    fun backupKnownHostPolicyKeepsFingerprintButRequiresReview() {
        val imported = knownHost("SHA256:backup", trusted = true, state = HostKeyTrustState.Trusted)

        val sanitized = BackupKnownHostPolicy.sanitizeImportedMetadata(imported)

        checkNotNull(sanitized)
        assertEquals("SHA256:backup", sanitized.fingerprint)
        assertFalse(sanitized.trusted)
        assertEquals(HostKeyTrustState.Unknown, sanitized.trustState)
    }

    @Test
    fun knownHostPersistencePolicyRejectsInvalidMetadata() {
        assertNull(KnownHostPersistencePolicy.normalizeLoaded(knownHost("SHA256:ok", trusted = false).copy(id = "")))
        assertNull(KnownHostPersistencePolicy.normalizeLoaded(knownHost("SHA256:ok", trusted = false).copy(host = "https://ssh.example.test;rm")))
        assertNull(KnownHostPersistencePolicy.normalizeLoaded(knownHost("", trusted = false).copy(fingerprint = "")))
        assertNull(BackupKnownHostPolicy.sanitizeImportedMetadata(knownHost("Unavailable until network handshake succeeds", trusted = false)))
    }

    @Test
    fun knownHostPersistencePolicyNormalizesUntrustedPlaceholders() {
        val loaded = KnownHostPersistencePolicy.normalizeLoaded(
            knownHost("Unavailable until network handshake succeeds", trusted = true, state = HostKeyTrustState.Trusted)
        )

        checkNotNull(loaded)
        assertFalse(loaded.trusted)
        assertEquals(HostKeyTrustState.Unknown, loaded.trustState)
    }

    @Test
    fun knownHostPersistencePolicyBoundsImportedDisplayMetadata() {
        val loaded = KnownHostPersistencePolicy.normalizeLoaded(
            knownHost("SHA256:${"a".repeat(300)}", trusted = false).copy(
                algorithm = "ssh-ed25519-${"x".repeat(100)}"
            )
        )

        checkNotNull(loaded)
        assertEquals(64, loaded.algorithm.length)
        assertEquals(160, loaded.fingerprint.length)
        assertFalse(loaded.trusted)
    }

    @Test
    fun knownHostPersistencePolicyParsesMalformedTrustStateFailClosed() {
        assertEquals(HostKeyTrustState.Trusted, KnownHostPersistencePolicy.trustStateFromPersisted(null, trusted = true))
        assertEquals(HostKeyTrustState.Unknown, KnownHostPersistencePolicy.trustStateFromPersisted("DefinitelyTrusted", trusted = true))
        assertEquals(HostKeyTrustState.Rejected, KnownHostPersistencePolicy.trustStateFromPersisted("Rejected", trusted = true))
    }

    @Test
    fun credentialHostLinkPolicyUnlinksIdentityWithoutDeletingHosts() {
        val first = server(username = "aldr").copy(id = "server-1", credentialId = "identity-1")
        val second = server(username = "root").copy(id = "server-2", credentialId = "identity-2")
        val third = server(username = "deploy").copy(id = "server-3", credentialId = "identity-1")

        val result = CredentialHostLinkPolicy.unlink(listOf(first, second, third), "identity-1")

        assertEquals(3, result.servers.size)
        assertEquals(2, result.unlinkedCount)
        assertNull(result.servers[0].credentialId)
        assertEquals("identity-2", result.servers[1].credentialId)
        assertNull(result.servers[2].credentialId)
    }

    @Test
    fun credentialDeletePolicyDeletesIdentityAndUnlinksHosts() {
        val first = server(username = "aldr").copy(id = "server-1", credentialId = "identity-1")
        val second = server(username = "root").copy(id = "server-2", credentialId = "identity-2")
        val credentials = listOf(
            Credential(id = "identity-1", label = "Deploy key", type = CredentialType.PrivateKey, publicKeyPreview = "ssh-ed25519 AAAA", encryptedPayloadRef = "secret-1", createdAtEpochMillis = 1L),
            Credential(id = "identity-2", label = "Root password", type = CredentialType.Password, publicKeyPreview = null, encryptedPayloadRef = "secret-2", createdAtEpochMillis = 2L)
        )

        val result = CredentialDeletePolicy.delete(credentials, listOf(first, second), "identity-1")

        assertTrue(result.deleted)
        assertEquals(listOf("identity-2"), result.credentials.map { it.id })
        assertEquals(1, result.unlinkedCount)
        assertNull(result.servers[0].credentialId)
        assertEquals("identity-2", result.servers[1].credentialId)
    }

    @Test
    fun vaultSecretExportPolicyRequiresConfirmationForRawSecrets() {
        val key = Credential(
            id = "identity-1",
            label = "${"Deploy/key".repeat(20)}\u0000",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "ssh-ed25519 AAAA test",
            encryptedPayloadRef = "secret-key",
            createdAtEpochMillis = 42L
        )
        val password = key.copy(
            id = "identity-2",
            label = "Root password",
            type = CredentialType.Password,
            publicKeyPreview = "Password saved",
            encryptedPayloadRef = "secret-password"
        )

        val keyExport = VaultSecretExportPolicy.policyFor(key, VaultSecretAction.Export)
        val keyShare = VaultSecretExportPolicy.policyFor(key, VaultSecretAction.Share)
        val passwordExport = VaultSecretExportPolicy.policyFor(password, VaultSecretAction.Export)
        val keyCopy = VaultSecretExportPolicy.policyFor(key, VaultSecretAction.Copy)

        assertTrue(keyExport.requiresConfirmation)
        assertTrue(keyShare.requiresConfirmation)
        assertTrue(passwordExport.requiresConfirmation)
        assertFalse(keyCopy.requiresConfirmation)
        assertEquals(84, keyExport.fileName.length)
        assertTrue(keyExport.fileName.endsWith(".key"))
        assertFalse(keyExport.fileName.any { it.isISOControl() })
        assertEquals("application/octet-stream", keyExport.mimeType)
        assertEquals("Root password.txt", passwordExport.fileName)
        assertEquals("text/plain", passwordExport.mimeType)
    }

    @Test
    fun vaultPublicKeyPolicyOnlyExportsAuthorizedKeyLines() {
        val authorized = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMockPublicKeyPayload user@example"

        assertEquals(authorized, VaultPublicKeyPolicy.exportablePublicKey(authorized))
        assertNull(VaultPublicKeyPolicy.exportablePublicKey("OpenSSH key SHA256:abc"))
        assertNull(VaultPublicKeyPolicy.exportablePublicKey("Password saved"))
        assertNull(VaultPublicKeyPolicy.exportablePublicKey("ssh-ed25519 short"))
    }

    @Test
    fun credentialNotReadyMessageExplainsMissingLinkedIdentity() {
        val message = credentialNotReadyMessage(server(username = "aldr"), null)

        assertTrue(message.contains("Credential for Test is not ready"))
        assertTrue(message.contains("Link a password or private-key identity"))
    }

    @Test
    fun credentialNotReadyMessageExplainsMetadataOnlyBackupIdentity() {
        val credential = Credential(
            id = "identity-1",
            label = "Deploy key",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "OpenSSH key SHA256:abc",
            encryptedPayloadRef = BackupCredentialPolicy.IMPORT_REQUIRED_REF,
            createdAtEpochMillis = 42L
        )

        val message = credentialNotReadyMessage(server(username = "aldr"), credential)

        assertTrue(message.contains("metadata only"))
        assertTrue(message.contains("replace/re-import 'Deploy key'"))
    }

    @Test
    fun credentialNotReadyMessageExplainsBrokenSecretReference() {
        val credential = Credential(
            id = "identity-1",
            label = "Copied key",
            type = CredentialType.PrivateKey,
            publicKeyPreview = null,
            encryptedPayloadRef = "/data/user/0/com.chrono.ssh/cache/ssh-keys/copied.key",
            createdAtEpochMillis = 42L
        )

        val message = credentialNotReadyMessage(server(username = "aldr"), credential)

        assertTrue(message.contains("Replace 'Copied key'"))
        assertTrue(message.contains("fresh secret payload"))
    }

    @Test
    fun sftpBookmarkPersistencePolicyNormalizesSafePersistedMetadata() {
        val longLabel = "l".repeat(180)
        val loaded = SftpBookmarkPersistencePolicy.normalizeLoaded(
            SftpBookmark(
                id = " bookmark-1 ",
                serverId = " server-1 ",
                label = " $longLabel\u0000 ",
                path = "/var/log/../tmp/",
                createdAtEpochMillis = -1L
            )
        )

        assertEquals("bookmark-1", loaded!!.id)
        assertEquals("server-1", loaded.serverId)
        assertEquals(120, loaded.label.length)
        assertFalse(loaded.label.any { it.isISOControl() })
        assertEquals("/var/tmp", loaded.path)
        assertEquals(0L, loaded.createdAtEpochMillis)
    }

    @Test
    fun sftpBookmarkPersistencePolicyFallsBackToLeafLabel() {
        val loaded = SftpBookmarkPersistencePolicy.normalizeLoaded(
            SftpBookmark(
                id = "bookmark-1",
                serverId = "server-1",
                label = " ",
                path = "/var/log/../tmp/",
                createdAtEpochMillis = 10L
            )
        )

        assertEquals("tmp", loaded!!.label)
    }

    @Test
    fun sftpBookmarkPersistencePolicyRejectsBlankPersistedIdentity() {
        val bookmark = SftpBookmark(
            id = "bookmark-1",
            serverId = "server-1",
            label = "Logs",
            path = "/var/log",
            createdAtEpochMillis = 10L
        )

        assertNull(SftpBookmarkPersistencePolicy.normalizeLoaded(bookmark.copy(id = " ")))
        assertNull(SftpBookmarkPersistencePolicy.normalizeLoaded(bookmark.copy(serverId = " ")))
    }

    @Test
    fun backupSftpBookmarkPolicyNormalizesImportedMetadata() {
        val sanitized = BackupSftpBookmarkPolicy.sanitizeImportedMetadata(
            SftpBookmark(
                id = " ",
                serverId = " server-1 ",
                label = " ",
                path = "\\var//log/../www/",
                createdAtEpochMillis = 10L
            )
        )

        checkNotNull(sanitized)
        assertEquals("server-1", sanitized.serverId)
        assertEquals("www", sanitized.label)
        assertEquals("/var/www", sanitized.path)
        assertTrue(sanitized.id.startsWith("bookmark-server-1-"))
    }

    @Test
    fun backupSftpBookmarkPolicyRejectsBlankServerId() {
        val sanitized = BackupSftpBookmarkPolicy.sanitizeImportedMetadata(
            SftpBookmark(
                id = "bookmark-1",
                serverId = " ",
                label = "Logs",
                path = "/var/log",
                createdAtEpochMillis = 10L
            )
        )

        assertNull(sanitized)
    }

    @Test
    fun backupForwardPolicyRejectsInvalidImportedForwards() {
        val sanitized = BackupForwardPolicy.sanitizeImportedMetadata(
            PortForwardRule(
                id = "forward-1",
                serverId = "",
                type = PortForwardType.Local,
                localHost = "127.0.0.1",
                localPort = 8080,
                remoteHost = "https://db.example.test;rm",
                remotePort = 5432,
                enabled = true,
                autoStart = true
            )
        )

        assertNull(sanitized)
        assertNull(
            BackupForwardPolicy.sanitizeImportedMetadata(
                PortForwardRule(
                    id = " ",
                    serverId = "server-1",
                    type = PortForwardType.Local,
                    localHost = "127.0.0.1",
                    localPort = 8080,
                    remoteHost = "db.example.test",
                    remotePort = 5432,
                    enabled = true,
                    autoStart = true
                )
            )
        )
    }

    @Test
    fun backupForwardPolicyNormalizesImportedSocksForwardsAsStopped() {
        val sanitized = BackupForwardPolicy.sanitizeImportedMetadata(
            PortForwardRule(
                id = "forward-1",
                serverId = "server-1",
                type = PortForwardType.DynamicSocks,
                localHost = "",
                localPort = 1080,
                remoteHost = "ignored.example.test",
                remotePort = 22,
                enabled = true,
                autoStart = true
            )
        )

        checkNotNull(sanitized)
        assertEquals("127.0.0.1", sanitized.localHost)
        assertEquals("", sanitized.remoteHost)
        assertEquals(0, sanitized.remotePort)
        assertFalse(sanitized.enabled)
        assertFalse(sanitized.autoStart)
    }

    @Test
    fun keyLoaderErrorSanitizerRedactsTemporaryKeyPaths() {
        val android = sanitizeKeyLoaderError(
            "/data/user/0/com.chrono.ssh/cache/ssh-keys/chrono-ui7-123.key: open failed: ENOENT"
        )
        val windows = sanitizeKeyLoaderError(
            "C:\\Users\\aldr\\AppData\\Local\\Temp\\cache\\ssh-keys\\chrono-ui7-123.key: bad permissions"
        )

        assertFalse(android.contains("chrono-ui7-123.key"))
        assertTrue(android.contains("<temporary-key-file>"))
        assertFalse(windows.contains("chrono-ui7-123.key"))
        assertTrue(windows.contains("<temporary-key-file>"))
    }

    @Test
    fun credentialDraftValidatorRejectsMissingIdentityForNewHost() {
        val result = CredentialDraftValidator.validate(
            existing = null,
            selectedCredentialId = null,
            type = CredentialType.Password,
            secret = "",
            label = ""
        )

        assertFalse(result.valid)
        assertTrue(result.message.orEmpty().contains("Choose a saved identity"))
    }

    @Test
    fun credentialDraftValidatorAllowsExistingIdentityWithoutReenteringSecret() {
        val existing = Credential(
            id = "identity-1",
            label = "Saved password",
            type = CredentialType.Password,
            publicKeyPreview = "Password saved",
            encryptedPayloadRef = "secret-password",
            createdAtEpochMillis = 1L
        )

        val result = CredentialDraftValidator.validate(
            existing = existing,
            selectedCredentialId = existing.id,
            type = CredentialType.Password,
            secret = "",
            label = existing.label
        )

        assertTrue(result.valid)
    }

    @Test
    fun credentialDraftValidatorRejectsTypeChangeWithoutReplacementSecret() {
        val existing = Credential(
            id = "identity-1",
            label = "Saved password",
            type = CredentialType.Password,
            publicKeyPreview = "Password saved",
            encryptedPayloadRef = "secret-password",
            createdAtEpochMillis = 1L
        )

        val result = CredentialDraftValidator.validate(
            existing = existing,
            selectedCredentialId = existing.id,
            type = CredentialType.PrivateKey,
            secret = "",
            label = existing.label
        )

        assertFalse(result.valid)
        assertTrue(result.message.orEmpty().contains("private key"))
    }

    @Test
    fun hostUniquenessIncludesUserProtocolHostAndPort() {
        val existing = server("root")

        assertTrue(HostUniquenessPolicy.hasDuplicateEndpoint(listOf(existing), existing.copy(id = "server-2", host = "EXAMPLE.test.")))
        assertFalse(HostUniquenessPolicy.hasDuplicateEndpoint(listOf(existing), existing.copy(id = "server-2", username = "ubuntu")))
        assertFalse(HostUniquenessPolicy.hasDuplicateEndpoint(listOf(existing), existing.copy(id = "server-2", protocol = ConnectionProtocol.Smb)))
    }

    @Test
    fun credentialUniquenessRejectsSameLabelCaseInsensitively() {
        val existing = Credential(
            id = "identity-1",
            label = "Production Key",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "ssh-ed25519 AAAA",
            encryptedPayloadRef = "secret-key",
            createdAtEpochMillis = 1L
        )

        assertTrue(CredentialUniquenessPolicy.hasDuplicateLabel(listOf(existing), " production key ", null, CredentialType.PrivateKey))
        assertFalse(CredentialUniquenessPolicy.hasDuplicateLabel(listOf(existing), " production key ", existing.id, CredentialType.PrivateKey))
    }

    @Test
    fun credentialUniquenessIgnoresPasswordNames() {
        val existingPassword = Credential(
            id = "identity-1",
            label = "root@example.test",
            type = CredentialType.Password,
            encryptedPayloadRef = "secret-pass",
            createdAtEpochMillis = 1L
        )
        val existingKey = Credential(
            id = "identity-2",
            label = "root@example.test",
            type = CredentialType.PrivateKey,
            publicKeyPreview = "ssh-ed25519 AAAA",
            encryptedPayloadRef = "secret-key",
            createdAtEpochMillis = 1L
        )

        // A new password identity may reuse any name (host label, another password's name, even a key's name).
        assertFalse(CredentialUniquenessPolicy.hasDuplicateLabel(listOf(existingPassword), "root@example.test", null, CredentialType.Password))
        assertFalse(CredentialUniquenessPolicy.hasDuplicateLabel(listOf(existingKey), "root@example.test", null, CredentialType.Password))
        // A new key must not collide with an existing key name, but ignores same-named password identities.
        assertTrue(CredentialUniquenessPolicy.hasDuplicateLabel(listOf(existingKey), "root@example.test", null, CredentialType.PrivateKey))
        assertFalse(CredentialUniquenessPolicy.hasDuplicateLabel(listOf(existingPassword), "root@example.test", null, CredentialType.PrivateKey))
    }

    private fun server(username: String): ServerProfile {
        return ServerProfile(
            id = "server-1",
            name = "Test",
            host = "example.test",
            port = 22,
            username = username,
            group = "",
            tags = emptyList(),
            osName = "Linux",
            osVersion = "",
            accent = ServerAccent("Blue", 0xff0000ff),
            credentialId = null,
            terminalProfileId = "default",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 10, useOptionalAgent = false)
        )
    }

    private fun knownHost(
        fingerprint: String,
        trusted: Boolean,
        state: HostKeyTrustState = if (trusted) HostKeyTrustState.Trusted else HostKeyTrustState.Unknown
    ): KnownHost {
        return KnownHost(
            id = "known-test",
            host = "example.test",
            port = 22,
            algorithm = "ssh-ed25519",
            fingerprint = fingerprint,
            trusted = trusted,
            firstSeenEpochMillis = 1,
            lastSeenEpochMillis = 2,
            trustState = state
        )
    }

    private fun transfer(
        state: TransferRecordState,
        progress: Float,
        message: String
    ): TransferRecord {
        return TransferRecord(
            id = "transfer-1",
            serverId = "server-1",
            direction = TransferDirection.Upload,
            remotePath = "/srv/app/file.txt",
            localDisplayName = "file.txt",
            progress = progress,
            state = state,
            message = message,
            updatedAtEpochMillis = 1
        )
    }

    private fun connectionEvent(): ConnectionEvent {
        return ConnectionEvent(
            id = "event-1",
            serverId = "server-1",
            atEpochMillis = 1,
            level = ConnectionEventLevel.Info,
            message = "Connected"
        )
    }
}
