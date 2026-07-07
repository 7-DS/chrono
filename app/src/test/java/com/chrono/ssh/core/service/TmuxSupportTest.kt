package com.chrono.ssh.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class TmuxSupportTest {
    @Test
    fun commandBuilderEscapesAndBuildsReferenceCommands() {
        assertEquals("'it'\\''s'", TmuxCommandBuilder.escapeArg("it's"))
        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux switch-client -t '=main'; " +
                "elif env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux has-session -t '=main' 2>/dev/null; then exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux attach-session -t '=main'; " +
                "else printf 'tmux session %s is no longer available\\n' 'main'; exec \"\${SHELL:-/bin/sh}\" -l; fi",
            TmuxCommandBuilder.attachSession("main")
        )
        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux switch-client -t '=main:2'; " +
                "elif env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux has-session -t '=main:2' 2>/dev/null; then exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux attach-session -t '=main:2'; " +
                "else printf 'tmux session %s is no longer available\\n' 'main:2'; exec \"\${SHELL:-/bin/sh}\" -l; fi",
            TmuxCommandBuilder.attachSessionWindow("main", 2)
        )
        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux switch-client -t '=server_box'; else exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux new-session -A -s 'server_box'; fi",
            TmuxCommandBuilder.newSessionOrAttach("server_box")
        )
        assertTrue(TmuxCommandBuilder.listSessionsCmd().contains("#{session_name}"))
        assertTrue(TmuxCommandBuilder.findTmux.contains("command -v tmux"))
    }

    @Test
    fun sessionInfoParsesDisplayNamesAndLists() {
        val info = TmuxSessionInfo.tryParse("main|3|1|2024-01-01|2024-01-02")

        assertNotNull(info)
        assertEquals("main", info!!.name)
        assertEquals(3, info.windows)
        assertTrue(info.attached)
        assertEquals("main · 3 windows · (attached)", info.displayName)
        assertEquals("dev · 1 window", TmuxSessionInfo("dev", windows = 1).displayName)
        assertEquals("empty", TmuxSessionInfo("empty").displayName)
        assertNull(TmuxSessionInfo.tryParse(""))
        assertEquals(0, TmuxSessionInfo.tryParse("test|abc|0")!!.windows)

        val sessions = parseTmuxSessions("main|3|1|created|attached\ndev|1|0|created|attached")
        assertEquals(listOf("main", "dev"), sessions.map { it.name })
    }

    @Test
    fun windowInfoParsesListWindowsOutput() {
        val windows = parseTmuxWindows("0|shell|1|1|activity\nbad\n2|logs|0|0|")

        assertEquals(2, windows.size)
        assertEquals(0, windows.first().index)
        assertEquals("shell", windows.first().name)
        assertTrue(windows.first().active)
        assertEquals(2, windows.last().index)
        assertFalse(windows.last().active)
    }

    @Test
    fun restoreStatePrefersArgsAndDropsWindowWithoutSession() {
        assertEquals(
            TmuxRestoreState("restored-from-tab", 3),
            resolveTmuxRestoreState(argsSession = "restored-from-tab", argsWindow = 3, restorableSession = "stale", restorableWindow = 1)
        )
        assertEquals(TmuxRestoreState("page-bucket", 2), resolveTmuxRestoreState(restorableSession = "page-bucket", restorableWindow = 2))
        assertEquals(TmuxRestoreState(), resolveTmuxRestoreState(restorableWindow = 7))
    }

    @Test
    fun launchPlansAttachExistingOrCreateNewSessions() {
        val restored = buildRestoredTmuxLaunchPlan(
            TmuxRestoreState(sessionName = "main", windowIndex = 2),
            listOf(TmuxSessionInfo(name = "main", windows = 3, attached = true))
        )
        assertTrue(restored.shouldLaunchTmux)
        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux switch-client -t '=main:2'; " +
                "elif env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux has-session -t '=main:2' 2>/dev/null; then exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux attach-session -t '=main:2'; " +
                "else printf 'tmux session %s is no longer available\\n' 'main:2'; exec \"\${SHELL:-/bin/sh}\" -l; fi",
            restored.command
        )

        val missing = buildRestoredTmuxLaunchPlan(TmuxRestoreState(sessionName = "ghost"), listOf(TmuxSessionInfo("main")))
        assertFalse(missing.shouldLaunchTmux)
        assertNull(missing.command)

        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux switch-client -t '=dev'; " +
                "elif env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux has-session -t '=dev' 2>/dev/null; then exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' tmux attach-session -t '=dev'; " +
                "else printf 'tmux session %s is no longer available\\n' 'dev'; exec \"\${SHELL:-/bin/sh}\" -l; fi",
            buildChosenTmuxLaunchPlan(TmuxAttachExisting("dev")).command
        )
        assertEquals(
            "if [ -n \"\$TMUX\" ]; then env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' /home/linuxbrew/.linuxbrew/bin/tmux switch-client -t '=codex:0'; " +
                "elif env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' /home/linuxbrew/.linuxbrew/bin/tmux has-session -t '=codex:0' 2>/dev/null; then exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' /home/linuxbrew/.linuxbrew/bin/tmux attach-session -t '=codex:0'; " +
                "else printf 'tmux session %s is no longer available\\n' 'codex:0'; exec \"\${SHELL:-/bin/sh}\" -l; fi",
            buildChosenTmuxLaunchPlan(TmuxAttachExisting("codex", 0), tmuxBin = "/home/linuxbrew/.linuxbrew/bin/tmux").command
        )
    }

    @Test
    fun terminalSessionRestoreCommandsCoverZellijAndScreen() {
        assertEquals(
            "exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' zellij attach 'main' --create",
            TerminalSessionRestoreCommandBuilder.zellijAttachOrCreate("main")
        )
        assertEquals(
            "exec env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8' screen -xRR 'work'\\''s'",
            TerminalSessionRestoreCommandBuilder.screenAttachOrCreate("work's")
        )
    }

    @Test
    fun scannerResolvesTmuxBinaryBeforeListing() = runBlocking {
        val session = FakeSshSession(
            CommandResult(TmuxCommandBuilder.findTmux, 0, "/opt/bin/tmux\n", ""),
            CommandResult("list", 0, "main|2|1|created|attached\n", ""),
            CommandResult(TmuxCommandBuilder.findTmux, 0, "/opt/bin/tmux\n", ""),
            CommandResult("windows", 0, "0|shell|1|1|activity\n", "")
        )
        val scanner = TmuxSessionScanner(session)

        assertEquals("main", scanner.listSessions().single().name)
        assertEquals(0, scanner.listWindows("main").single().index)
        assertTrue(session.commands[0].contains("command -v tmux"))
        assertTrue(session.commands[1].contains("/opt/bin/tmux list-sessions"))
        assertTrue(session.commands[3].contains("/opt/bin/tmux list-windows -t 'main'"))
    }
}

private class FakeSshSession(vararg private val responses: CommandResult) : SshSession {
    private var index = 0
    val commands = mutableListOf<String>()
    override val id: String = "fake"
    override val serverId: String = "server"
    override val transcriptPreview: String = ""

    override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
        commands += command
        return responses[index++].copy(command = command)
    }

    override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
    override suspend fun writeTerminal(input: String) = Unit
    override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
    override suspend fun close() = Unit
}
