package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TerminalCursorStyle
import com.chrono.ssh.core.model.TerminalKey
import com.chrono.ssh.core.model.TerminalKeyRow
import com.chrono.ssh.core.model.TerminalProfile
import com.chrono.ssh.core.service.CommandResult
import com.chrono.ssh.core.service.SshSession
import com.chrono.ssh.core.service.TmuxSessionInfo
import com.chrono.ssh.core.service.TmuxWindowInfo
import com.chrono.ssh.ui.terminal.ChronoSSHTerminalEngine
import com.chrono.ssh.ui.terminal.TerminalSearchSelection
import androidx.compose.ui.unit.dp
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TerminalScreenTest {
    @Test
    fun snippetVariablesComeFromDeclarationAndCommand() {
        val snippet = Snippet(
            id = "s1",
            name = "Logs",
            command = "journalctl -u {{ service }} --since {{since}} --format '{{.GoTemplate}}'",
            tags = emptyList(),
            serverScope = null,
            variables = listOf("service", "lines")
        )

        assertEquals(listOf("service", "lines", "since"), snippetVariableNames(snippet))
        assertEquals(
            "journalctl -u ssh --since today --format '{{.GoTemplate}}'",
            renderSnippetCommand(snippet, server(), mapOf("service" to "ssh", "since" to "today"))
        )
    }

    @Test
    fun renderSnippetCommandUsesServerDefaultsAndUserValues() {
        val server = server()
        val snippet = Snippet(
            id = "s1",
            name = "Deploy",
            command = "ssh {{user}}@{{host}} -p {{port}} 'tail -n {{lines}} /var/log/{{service}}.log'",
            tags = emptyList(),
            serverScope = null,
            variables = listOf("lines", "service")
        )

        assertEquals(
            "ssh root@box.test -p 2222 'tail -n 200 /var/log/nginx.log'",
            renderSnippetCommand(snippet, server, mapOf("lines" to "200", "service" to "nginx"))
        )
    }

    @Test
    fun renderSnippetCommandFlagsUnsafeVariableValues() {
        val server = server()
        val snippet = Snippet(
            id = "s1",
            name = "Logs",
            command = "journalctl -u {{service}}",
            tags = emptyList(),
            serverScope = null,
            variables = listOf("service")
        )

        val result = renderSnippetCommandResult(snippet, server, mapOf("service" to "ssh; sudo reboot"))

        assertEquals("journalctl -u ssh; sudo reboot", result.command)
        assertEquals("Snippet variable 'service' contains shell control characters.", result.error)
    }

    @Test
    fun terminalVisibleSnippetsFiltersByServerScopeAndQuery() {
        val global = snippet("global", "Docker status", "docker ps", listOf("containers"), null)
        val scoped = snippet("scoped", "Tail logs", "journalctl -u nginx", listOf("logs"), "server-1").copy(group = "ops")
        val other = snippet("other", "Other logs", "journalctl -u ssh", listOf("logs"), "server-2")
        val favorite = snippet("favorite", "Audit", "last", listOf("logs"), null).copy(favorite = true)

        assertEquals(
            listOf(scoped, favorite, global),
            terminalVisibleSnippets(listOf(global, other, favorite, scoped), "server-1", "", limit = 10)
        )
        assertEquals(
            listOf(scoped),
            terminalVisibleSnippets(listOf(global, other, scoped), "server-1", "nginx logs", limit = 10)
        )
    }

    @Test
    fun snippetRenderPolicyAllowsCommonArgumentValues() {
        assertEquals(false, SnippetRenderPolicy.unsafeValue("nginx.service"))
        assertEquals(false, SnippetRenderPolicy.unsafeValue("/var/log/nginx/access.log"))
        assertEquals(true, SnippetRenderPolicy.unsafeValue("nginx && reboot"))
    }

    @Test
    fun terminalWorkspaceSelectionCreatesMissingWorkspaceForSelectedServer() {
        val server = server()
        val selection = terminalWorkspaceSelection(
            selectedServerId = server.id,
            servers = listOf(server),
            workspaces = emptyList()
        )

        assertEquals(server.id, selection!!.workspaceKey)
        assertEquals(server, selection.server)
        assertEquals(false, selection.exists)
    }

    @Test
    fun terminalWorkspaceSelectionKeepsRequestedDuplicateWorkspaceKey() {
        val server = server()
        val selection = terminalWorkspaceSelection(
            selectedServerId = "server-1|duplicate",
            servers = listOf(server),
            workspaces = listOf(TerminalWorkspaceSummary("server-1|duplicate", server.id, connected = true))
        )

        assertEquals("server-1|duplicate", selection!!.workspaceKey)
        assertEquals(server, selection.server)
        assertEquals(true, selection.exists)
    }

    @Test
    fun terminalWorkspaceSelectionUsesClickedWorkspaceKeyExactly() {
        val server = server()
        val duplicate = TerminalWorkspaceSummary("server-1|duplicate", server.id, connected = true)
        val original = TerminalWorkspaceSummary("server-1", server.id, connected = true)

        val selection = terminalWorkspaceSelection(
            selectedServerId = duplicate.key,
            servers = listOf(server),
            workspaces = listOf(original, duplicate)
        )

        assertEquals(duplicate.key, selection!!.workspaceKey)
        assertEquals(true, selection.exists)
    }

    @Test
    fun terminalWorkspaceSelectionIgnoresWorkspacesForDeletedServers() {
        val server = server()
        val selection = terminalWorkspaceSelection(
            selectedServerId = "deleted|duplicate",
            servers = listOf(server),
            workspaces = listOf(TerminalWorkspaceSummary("deleted|duplicate", "deleted", connected = true))
        )

        assertEquals(server.id, selection!!.workspaceKey)
        assertEquals(server, selection.server)
        assertEquals(false, selection.exists)
    }

    @Test
    fun terminalWorkspaceStateDoesNotCreateEngineUntilNeeded() {
        var created = false
        val workspace = TerminalWorkspaceState(
            serverId = "server-1",
            engineFactory = {
                created = true
                throw IllegalStateException("engine should be lazy")
            }
        )

        assertEquals(false, created)
        assertEquals(false, workspace.connected)
        workspace.detachEngineIfInitialized()
        workspace.disposeEngineIfInitialized()
        assertEquals(false, created)
    }

    @Test
    fun terminalWorkspaceConnectGenerationRejectsStaleAttempts() {
        val workspace = TerminalWorkspaceState(
            serverId = "server-1",
            engineFactory = { throw IllegalStateException("engine should be lazy") }
        )

        val first = workspace.nextConnectGeneration()
        val second = workspace.nextConnectGeneration()

        assertEquals(false, workspace.isCurrentConnectGeneration(first))
        assertEquals(true, workspace.isCurrentConnectGeneration(second))
        workspace.invalidateConnectGeneration()
        assertEquals(false, workspace.isCurrentConnectGeneration(second))
    }

    @Test
    fun terminalEngineReportsWriteDisconnects() {
        val failedSession = ThrowingSshSession()
        val disconnected = CountDownLatch(1)
        var reportedSession: SshSession? = null
        val engine = ChronoSSHTerminalEngine(
            profile = terminalProfile(),
            onChanged = {},
            onSessionDisconnected = { session, _ ->
                reportedSession = session
                disconnected.countDown()
            },
            applyProfileOnInit = false
        )

        engine.attach(failedSession)
        engine.sendInput("ls\n")

        assertTrue(disconnected.await(2, TimeUnit.SECONDS))
        assertSame(failedSession, reportedSession)
        engine.dispose()
    }

    @Test
    fun terminalEngineRebindsOutputAndInputToCurrentSessionOnly() {
        val first = RecordingSshSession("ssh-1")
        val second = RecordingSshSession("ssh-2")
        val engine = ChronoSSHTerminalEngine(
            profile = terminalProfile(),
            onChanged = {},
            applyProfileOnInit = false
        )

        engine.attach(first)
        first.emit("first\n")
        engine.attach(second)
        first.emit("stale\n")
        second.emit("second\n")
        assertEquals(true, engine.pasteText("pwd\n"))

        assertEquals("first\nsecond\n", engine.copyTranscript())
        assertTrue(second.written.await(2, TimeUnit.SECONDS))
        assertEquals(emptyList<String>(), first.writes)
        assertEquals(listOf("\u001B[200~pwd\n\u001B[201~"), second.writes)
        engine.dispose()
    }

    @Test
    fun terminalEngineIgnoresRetainedSinkOutputFromStaleSessionAfterRebind() {
        val first = RetainedSinkSshSession("ssh-1")
        val second = RetainedSinkSshSession("ssh-2")
        val engine = ChronoSSHTerminalEngine(
            profile = terminalProfile(),
            onChanged = {},
            applyProfileOnInit = false
        )

        engine.attach(first)
        engine.attach(second)
        first.emitFromFirstSink("stale")
        second.emitFromFirstSink("fresh")

        assertEquals("fresh", engine.copyTranscript())
        engine.dispose()
    }


    @Test
    fun terminalEngineRejectsPasteWithoutAttachedSession() {
        val engine = ChronoSSHTerminalEngine(
            profile = terminalProfile(),
            onChanged = {},
            applyProfileOnInit = false
        )

        assertEquals(false, engine.pasteText("pwd\n"))
        engine.dispose()
    }

    @Test
    fun terminalTopStripSessionsKeepsSwitchableWorkspaceKeys() {
        val server = server()
        val other = server.copy(id = "server-2", name = "Other")
        val sessions = terminalTopStripSessions(
            selectedWorkspaceKey = "server-1",
            selectedServer = server,
            activeSessions = listOf(
                "server-1" to server,
                "server-1|duplicate" to server.copy(name = "Box duplicate"),
                "server-2" to other
            )
        )

        assertEquals(listOf("server-1", "server-1|duplicate", "server-2"), sessions.map { it.first })
    }

    @Test
    fun terminalTopStripSessionsDoesNotDuplicateSelectedActiveWorkspace() {
        val server = server()
        val sessions = terminalTopStripSessions(
            selectedWorkspaceKey = "server-1",
            selectedServer = server,
            activeSessions = listOf("server-1" to server)
        )

        assertEquals(listOf("server-1"), sessions.map { it.first })
    }

    @Test
    fun terminalTopStripSessionsUsesExistingServerWorkspaceWhenSelectedKeyDiffers() {
        val server = server()
        val sessions = terminalTopStripSessions(
            selectedWorkspaceKey = "server-1",
            selectedServer = server,
            activeSessions = listOf("server-1|duplicate" to server)
        )

        assertEquals(listOf("server-1|duplicate"), sessions.map { it.first })
    }

    @Test
    fun terminalTopStripSessionsKeepsRealDuplicateWorkspaces() {
        val server = server()
        val sessions = terminalTopStripSessions(
            selectedWorkspaceKey = "server-1|duplicate",
            selectedServer = server,
            activeSessions = listOf("server-1" to server, "server-1|duplicate" to server.copy(name = "Box duplicate"))
        )

        assertEquals(listOf("server-1|duplicate", "server-1"), sessions.map { it.first })
    }

    @Test
    fun terminalTopStripSessionsKeepsSelectedWorkspaceFirst() {
        val server = server()
        val other = server.copy(id = "server-2", name = "Other")
        val sessions = terminalTopStripSessions(
            selectedWorkspaceKey = "server-1",
            selectedServer = server,
            activeSessions = listOf("server-2" to other, "server-1" to server)
        )

        assertEquals(listOf("server-1", "server-2"), sessions.map { it.first })
    }

    @Test
    fun terminalUserFacingErrorHidesSocketDetails() {
        assertEquals(
            "Connection interrupted. Reconnect to continue.",
            terminalUserFacingError(IOException("failed to connect to /192.0.0.2 (port 22) after 10000ms"))
        )
        assertEquals(
            "Connection interrupted. Reconnect to continue.",
            terminalUserFacingError(IOException("Broken pipe"))
        )
        assertEquals(
            "Connection interrupted. Reconnect to continue.",
            terminalUserFacingError(IOException(""))
        )
        assertEquals(
            "Connection interrupted. Reconnect to continue.",
            terminalUserFacingError(IOException("SSH exec connect failed for 185.163.118.38:22: failed to connect to /192.0.0.2"))
        )
        assertEquals(
            "Permission denied",
            terminalUserFacingError(IOException("Permission denied"))
        )
    }

    @Test
    fun terminalReconnectOnResumeOnlyReconnectsInterruptedSessions() {
        assertEquals(
            true,
            terminalShouldReconnectOnResume(
                hasSession = false,
                hasPendingPassphrase = false,
                hasPendingHostKey = false,
                status = "Disconnected",
                autoReconnect = true
            )
        )
        assertEquals(
            true,
            terminalShouldReconnectOnResume(
                hasSession = false,
                hasPendingPassphrase = false,
                hasPendingHostKey = false,
                status = "Connection lost",
                autoReconnect = true
            )
        )
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = true, hasPendingPassphrase = false, hasPendingHostKey = false, status = "Shell", autoReconnect = true))
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = false, hasPendingPassphrase = true, hasPendingHostKey = false, status = "Passphrase required", autoReconnect = true))
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = false, hasPendingPassphrase = false, hasPendingHostKey = true, status = "Review host key", autoReconnect = true))
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = false, hasPendingPassphrase = false, hasPendingHostKey = false, status = "Connecting", autoReconnect = true))
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = false, hasPendingPassphrase = false, hasPendingHostKey = false, status = "Closed", autoReconnect = true))
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = false, hasPendingPassphrase = false, hasPendingHostKey = false, status = "Idle", autoReconnect = true))
        assertEquals(false, terminalShouldReconnectOnResume(hasSession = false, hasPendingPassphrase = false, hasPendingHostKey = false, status = "Failed", autoReconnect = false))
    }

    @Test
    fun terminalReconnectDelayBacksOffThenCaps() {
        assertEquals(750L, terminalReconnectDelayMillis(0))
        assertEquals(750L, terminalReconnectDelayMillis(1))
        assertEquals(1_500L, terminalReconnectDelayMillis(2))
        assertEquals(3_000L, terminalReconnectDelayMillis(3))
        assertEquals(5_000L, terminalReconnectDelayMillis(4))
        assertEquals(5_000L, terminalReconnectDelayMillis(99))
    }

    @Test
    fun terminalSftpStripSessionsKeepsSwitchableWorkspaceKeys() {
        val server = server()
        val other = server.copy(id = "server-2", name = "Other")
        val sessions = terminalSftpStripSessions(
            selectedSftpWorkspaceKey = null,
            listOf(
                "sftp-1" to server,
                "sftp-1|duplicate" to server.copy(name = "Box duplicate"),
                "sftp-2" to other
            )
        )

        assertEquals(listOf("sftp-1", "sftp-1|duplicate", "sftp-2"), sessions.map { it.first })
    }

    @Test
    fun terminalSftpStripSessionsKeepsSelectedWorkspaceFirst() {
        val server = server()
        val other = server.copy(id = "server-2", name = "Other")
        val sessions = terminalSftpStripSessions(
            selectedSftpWorkspaceKey = "sftp-1",
            listOf("sftp-2" to other, "sftp-1" to server)
        )

        assertEquals(listOf("sftp-1", "sftp-2"), sessions.map { it.first })
    }

    @Test
    fun terminalSessionChipLabelKeepsDuplicateHostsReadable() {
        assertEquals("Box", terminalSessionChipLabel("Box", 1))
        assertEquals("Box #2", terminalSessionChipLabel("Box", 2))
    }

    @Test
    fun terminalSftpChipLabelStaysDistinctFromShellSession() {
        assertEquals("Box", terminalSftpChipLabel("Box", 1))
        assertEquals("Box #2", terminalSftpChipLabel("Box", 2))
    }

    @Test
    fun terminalSearchStatusReportsReadyEmptyTotalAndSelection() {
        assertEquals("Ready", terminalSearchStatus(" ", emptyList(), null))
        assertEquals("0", terminalSearchStatus("root", emptyList(), null))
        assertEquals("3", terminalSearchStatus("root", listOf(1, 3, 5), null))
        assertEquals("2/3", terminalSearchStatus("root", listOf(1, 3, 5), TerminalSearchSelection(1, 3, 3)))
        assertEquals("3", terminalSearchStatus("root", listOf(1, 3, 5), TerminalSearchSelection(9, 0, 3)))
    }

    @Test
    fun tmuxPickerHelpersUseCompactSafeLabels() {
        assertEquals("prod_api_01", defaultTmuxSessionName(server().copy(name = "Prod API 01")))
        assertEquals("chrono", defaultTmuxSessionName(server().copy(name = "!!!")))
        assertEquals(
            "2 windows · attached · last attached yesterday",
            tmuxSessionSubtitle(TmuxSessionInfo("main", windows = 2, attached = true, lastAttached = "yesterday"))
        )
        assertEquals(
            "active · active pane · activity now",
            tmuxWindowSubtitle(TmuxWindowInfo(index = 1, name = "logs", active = true, paneActive = true, activity = "now"))
        )
    }

    @Test
    fun moshStartupUsesExplicitCommandBeforeTmuxRestore() {
        assertEquals(
            "htop",
            terminalMoshStartupCommand(
                startupCommand = "htop",
                tmuxSessionName = "main",
                tmuxWindowIndex = 1
            )
        )
    }

    @Test
    fun moshStartupCanRestorePersistedTmuxTarget() {
        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux switch-client -t '=main:1'; " +
                "elif env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux has-session -t '=main:1' 2>/dev/null; then exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux attach-session -t '=main:1'; " +
                "else printf 'tmux session %s is no longer available\\n' 'main:1'; exec \"\${SHELL:-/bin/sh}\" -l; fi",
            terminalMoshStartupCommand(
                startupCommand = "",
                tmuxSessionName = "main",
                tmuxWindowIndex = 1
            )
        )
    }

    @Test
    fun moshStartupSkipsBlankTmuxRestore() {
        assertEquals(
            "",
            terminalMoshStartupCommand(
                startupCommand = "",
                tmuxSessionName = " ",
                tmuxWindowIndex = null
            )
        )
    }

    @Test
    fun terminalHostKeySingleTrustActionRejectsChangedKeys() {
        assertEquals(true, terminalHostKeyCanTrust(HostKeyTrustState.Unknown))
        assertEquals(true, terminalHostKeyCanTrust(HostKeyTrustState.Trusted))
        assertEquals(false, terminalHostKeyCanTrust(HostKeyTrustState.Changed))
        assertEquals(false, terminalHostKeyCanTrust(HostKeyTrustState.Rejected))
    }

    @Test
    fun terminalActionRowUsesConfiguredKeysWhenCollapsed() {
        val profile = terminalProfile().copy(
            keyRows = listOf(TerminalKeyRow("custom", listOf(TerminalKey("Esc", "\u001B"), TerminalKey("F12", "\u001B[24~"))))
        )

        assertEquals(listOf("Esc", "F12"), terminalActionRowLabels(profile, expanded = false))
        assertTrue(terminalActionRowLabels(profile, expanded = true).take(2) == listOf("Esc", "F12"))
    }

    @Test
    fun terminalExpandedKeysIncludeUtilityKeys() {
        val labels = terminalActionRowLabels(terminalProfile(), expanded = true)

        assertTrue(labels.containsAll(listOf("Swipe", "AltGr", "Ins", "Del", "F5", "F12")))
        assertEquals(1, labels.count { it == "Swipe" })
        assertEquals("<altgr>", defaultTerminalAccessorySequence("AltGr"))
        assertEquals("\u001B[2~", defaultTerminalAccessorySequence("Ins"))
        assertEquals("\u001B[3~", defaultTerminalAccessorySequence("Del"))
        assertEquals("\u001B[A", defaultTerminalAccessorySequence("Up"))
        assertEquals("\u001B[B", defaultTerminalAccessorySequence("Down"))
        assertEquals("\u001B[15~", defaultTerminalAccessorySequence("F5"))
        assertEquals("\u001B[24~", defaultTerminalAccessorySequence("F12"))
    }

    @Test
    fun terminalAccessoryRepeatPolicyMatchesNavigationKeys() {
        assertTrue(terminalAccessoryKeyRepeats("↑"))
        assertTrue(terminalAccessoryKeyRepeats("Bksp"))
        assertFalse(terminalAccessoryKeyRepeats("Esc"))
        assertFalse(terminalAccessoryKeyRepeats("/"))
    }

    @Test
    fun terminalDefaultCollapsedKeysExposeSwipePad() {
        val labels = terminalActionRowLabels(terminalProfile(), expanded = false)

        assertEquals("Swipe", labels.first())
        assertEquals(1, labels.count { it == "Swipe" })
    }

    @Test
    fun terminalExpandedAccessoryLayoutDoesNotClipKnownKeys() {
        val labels = terminalActionRowLabels(terminalProfile(), expanded = true)
        val maxLines = terminalAccessoryExpandedMaxLines(labels)
        val columns = terminalAccessoryExpandedColumnCount(labels)

        assertTrue(labels.size <= maxLines * 10)
        assertTrue(maxLines <= 6)
        assertEquals(labels.size, labels.chunked(columns).sumOf { it.size })
        assertTrue(columns in 6..9)
        val packedRows = terminalAccessoryPackedRows(labels)
        assertEquals(labels.size, packedRows.sumOf { it.size })
        assertEquals(labels, packedRows.flatten())
        assertTrue(packedRows.all { it.size <= columns })
        assertTrue(packedRows.maxOf { it.size } - packedRows.minOf { it.size } <= 1)
        assertEquals(34.dp, terminalAccessoryKeyMinWidth("|"))
        assertEquals(48.dp, terminalAccessoryKeyMinWidth("Enter"))
        assertEquals(56.dp, terminalAccessoryKeyMinWidth("Ctrl-A"))
    }

    @Test
    fun terminalSwipePadMapsDirectionalSwipes() {
        assertNull(terminalDirectionForSwipe(5f, 5f))
        assertEquals("→", terminalDirectionForSwipe(40f, 4f))
        assertEquals("←", terminalDirectionForSwipe(-40f, 4f))
        assertEquals("↓", terminalDirectionForSwipe(4f, 40f))
        assertEquals("↑", terminalDirectionForSwipe(4f, -40f))
    }

    @Test
    fun terminalQuickActionsStayAvailableExceptDuringSearch() {
        assertEquals(true, terminalQuickActionsVisible(keyboardVisible = true, panelOpen = false, searchOpen = false))
        assertEquals(true, terminalQuickActionsVisible(keyboardVisible = false, panelOpen = true, searchOpen = false))
        assertEquals(false, terminalQuickActionsVisible(keyboardVisible = true, panelOpen = true, searchOpen = true))
        assertEquals(false, terminalQuickActionsVisible(keyboardVisible = false, panelOpen = false, searchOpen = false))
    }

    @Test
    fun terminalLatestOutputBlockCopiesLastNonEmptyChunk() {
        assertEquals("line 2\nline 3", terminalLatestOutputBlock("old\n\nline 2\nline 3\n"))
        assertEquals("cde", terminalLatestOutputBlock("abcde", maxChars = 3))
        assertNull(terminalLatestOutputBlock("  \n\n"))
    }

    @Test
    fun terminalAccessoryKeysRouteDirectly() {
        assertEquals(true, terminalAccessoryShouldSendLiteral("/"))
        assertEquals(true, terminalAccessoryShouldSendLiteral("|"))
        assertEquals(false, terminalAccessoryShouldSendLiteral("\u001B[A"))
        assertEquals("/", terminalAccessorySendResult("/", ctrl = false, alt = false, shift = false).output)
        assertEquals("|", terminalAccessorySendResult("|", ctrl = false, alt = false, shift = false).output)
        assertEquals("\u001B", terminalAccessorySendResult("\u001B", ctrl = false, alt = false, shift = false).output)
        assertEquals("\u001B[A", terminalAccessorySendResult("\u001B[A", ctrl = false, alt = false, shift = false).output)
        val altUp = terminalAccessorySendResult("\u001B[A", ctrl = false, alt = true, shift = false)
        assertEquals("\u001B[1;3A", altUp.output)
        assertEquals(true, altUp.consumeAlt)
        val ctrlSlash = terminalAccessorySendResult("/", ctrl = true, alt = false, shift = false)
        assertEquals("\u001F", ctrlSlash.output)
        assertEquals(true, ctrlSlash.consumeCtrl)
        assertEquals("\u001B", terminalAccessorySendResult("[", ctrl = true, alt = false, shift = false).output)
        assertEquals("\u001C", terminalAccessorySendResult("\\", ctrl = true, alt = false, shift = false).output)
        assertEquals("\u001E", terminalAccessorySendResult("^", ctrl = true, alt = false, shift = false).output)
        assertEquals("\u001F", terminalAccessorySendResult("_", ctrl = true, alt = false, shift = false).output)
    }

    @Test
    fun terminalDropReconnectPolicySkipsInteractivePrompts() {
        assertEquals(true, terminalShouldRequestReconnectAfterDrop(autoReconnect = true, hasPendingPassphrase = false, hasPendingHostKey = false))
        assertEquals(false, terminalShouldRequestReconnectAfterDrop(autoReconnect = false, hasPendingPassphrase = false, hasPendingHostKey = false))
        assertEquals(false, terminalShouldRequestReconnectAfterDrop(autoReconnect = true, hasPendingPassphrase = true, hasPendingHostKey = false))
        assertEquals(false, terminalShouldRequestReconnectAfterDrop(autoReconnect = true, hasPendingPassphrase = false, hasPendingHostKey = true))
    }

    private fun snippet(id: String, name: String, command: String, tags: List<String>, serverScope: String?) = Snippet(
        id = id,
        name = name,
        command = command,
        tags = tags,
        serverScope = serverScope,
        variables = emptyList()
    )

    private fun server() = ServerProfile(
        id = "server-1",
        name = "Box",
        host = "box.test",
        port = 2222,
        username = "root",
        group = "",
        tags = emptyList(),
        osName = "linux",
        osVersion = "",
        accent = ServerAccent("cyan", 0xff00ffff),
        credentialId = null,
        terminalProfileId = "default",
        monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false)
    )

    private fun terminalProfile() = TerminalProfile(
        id = "term",
        name = "Default",
        fontSizeSp = 14,
        fontFamily = "monospace",
        themeName = "dark",
        cursorStyle = TerminalCursorStyle.Block,
        scrollbackLines = 100,
        keyRows = emptyList()
    )

    private class ThrowingSshSession : SshSession {
        override val id = "ssh-1"
        override val serverId = "server-1"
        override val transcriptPreview = ""
        override suspend fun execute(command: String, timeoutSeconds: Long) = CommandResult(command, 1, "", "failed")
        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
        override suspend fun writeTerminal(input: String) {
            throw IOException("socket closed")
        }
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
        override suspend fun close() = Unit
    }

    private class RecordingSshSession(override val id: String) : SshSession {
        override val serverId = "server-1"
        override val transcriptPreview = ""
        val writes = mutableListOf<String>()
        val written = CountDownLatch(1)
        private var sink: (ByteArray) -> Unit = {}

        override suspend fun execute(command: String, timeoutSeconds: Long) = CommandResult(command, 0, "", "")
        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
        override suspend fun writeTerminal(input: String) {
            writes += input
            written.countDown()
        }
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) {
            sink = onData
        }
        override suspend fun close() = Unit

        fun emit(text: String) = sink(text.toByteArray())
    }

    private class RetainedSinkSshSession(override val id: String) : SshSession {
        override val serverId = "server-1"
        override val transcriptPreview = ""
        private val sinks = mutableListOf<(ByteArray) -> Unit>()

        override suspend fun execute(command: String, timeoutSeconds: Long) = CommandResult(command, 0, "", "")
        override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
        override suspend fun writeTerminal(input: String) = Unit
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) {
            sinks += onData
        }
        override suspend fun close() = Unit

        fun emitFromFirstSink(text: String) {
            sinks.first().invoke(text.toByteArray())
        }
    }
}
