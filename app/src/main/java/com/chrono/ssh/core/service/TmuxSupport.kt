package com.chrono.ssh.core.service

object TmuxCommandBuilder {
    private const val UTF8_ENV = "env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8'"

    const val findTmux: String = "command -v tmux || command -v /usr/bin/tmux || command -v /opt/bin/tmux || command -v /home/linuxbrew/.linuxbrew/bin/tmux"

    fun escapeArg(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun exactTarget(sessionName: String, windowIndex: Int? = null): String {
        val target = windowIndex?.let { "$sessionName:$it" } ?: sessionName
        return "=$target"
    }

    fun attachSession(sessionName: String, tmuxBin: String = "tmux"): String =
        interactiveAttachExistingCommand(sessionName, null, tmuxBin)

    fun attachSessionWindow(sessionName: String, windowIndex: Int, tmuxBin: String = "tmux"): String =
        interactiveAttachExistingCommand(sessionName, windowIndex, tmuxBin)

    fun newSession(sessionName: String, tmuxBin: String = "tmux"): String =
        "$UTF8_ENV $tmuxBin new-session -s ${escapeArg(sessionName)}"

    fun newSessionOrAttach(sessionName: String, tmuxBin: String = "tmux"): String =
        "if [ -n \"\$TMUX\" ]; then $UTF8_ENV $tmuxBin switch-client -t ${escapeArg(exactTarget(sessionName))}; else exec $UTF8_ENV $tmuxBin new-session -A -s ${escapeArg(sessionName)}; fi"

    fun killSession(sessionName: String, tmuxBin: String = "tmux"): String =
        "$UTF8_ENV $tmuxBin kill-session -t ${escapeArg(sessionName)}"

    fun listSessionsCmd(tmuxBin: String = "tmux"): String =
        "$UTF8_ENV $tmuxBin list-sessions -F \"#{session_name}|#{session_windows}|#{session_attached}|#{session_created_string}|#{session_last_attached_time}\""

    fun listWindows(sessionName: String, tmuxBin: String = "tmux"): String =
        "$UTF8_ENV $tmuxBin list-windows -t ${escapeArg(sessionName)} -F \"#{window_index}|#{window_name}|#{window_active}|#{pane_active}|#{window_activity}\""

    fun listClients(tmuxBin: String = "tmux"): String =
        "$UTF8_ENV $tmuxBin list-clients -F \"#{client_tty}|#{client_session}|#{window_index}|#{client_activity}\""

    fun switchClient(clientTty: String, target: String, tmuxBin: String = "tmux"): String =
        "$UTF8_ENV $tmuxBin switch-client -c ${escapeArg(clientTty)} -t ${escapeArg(target)}"

    private fun interactiveAttachExistingCommand(sessionName: String, windowIndex: Int?, tmuxBin: String): String {
        val target = windowIndex?.let { "$sessionName:$it" } ?: sessionName
        val quotedTarget = escapeArg(target)
        val quotedExactTarget = escapeArg(exactTarget(sessionName, windowIndex))
        return "if [ -n \"\$TMUX\" ]; then $UTF8_ENV $tmuxBin switch-client -t $quotedExactTarget; " +
            "elif $UTF8_ENV $tmuxBin has-session -t $quotedExactTarget 2>/dev/null; then exec $UTF8_ENV $tmuxBin attach-session -t $quotedExactTarget; " +
            "else printf 'tmux session %s is no longer available\\n' $quotedTarget; exec \"\${SHELL:-/bin/sh}\" -l; fi"
    }
}

object TerminalSessionRestoreCommandBuilder {
    private const val UTF8_ENV = "env LANG='en_US.UTF-8' LC_CTYPE='en_US.UTF-8' LC_ALL='en_US.UTF-8'"

    fun zellijAttachOrCreate(sessionName: String, zellijBin: String = "zellij"): String =
        "exec $UTF8_ENV $zellijBin attach ${TmuxCommandBuilder.escapeArg(sessionName)} --create"

    fun screenAttachOrCreate(sessionName: String, screenBin: String = "screen"): String =
        "exec $UTF8_ENV $screenBin -xRR ${TmuxCommandBuilder.escapeArg(sessionName)}"
}

data class TmuxSessionInfo(
    val name: String,
    val windows: Int = 0,
    val attached: Boolean = false,
    val createdAt: String? = null,
    val lastAttached: String? = null
) {
    val displayName: String
        get() = buildList {
            add(name)
            if (windows > 0) add("$windows window${if (windows == 1) "" else "s"}")
            if (attached) add("(attached)")
        }.joinToString(" · ")

    companion object {
        fun tryParse(line: String): TmuxSessionInfo? {
            val parts = line.trim().split('|')
            val name = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: return null
            return TmuxSessionInfo(
                name = name,
                windows = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                attached = parts.getOrNull(2) == "1",
                createdAt = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                lastAttached = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            )
        }
    }
}

data class TmuxWindowInfo(
    val index: Int,
    val name: String,
    val active: Boolean,
    val paneActive: Boolean,
    val activity: String? = null
) {
    companion object {
        fun tryParse(line: String): TmuxWindowInfo? {
            val parts = line.trim().split('|')
            val index = parts.getOrNull(0)?.toIntOrNull() ?: return null
            return TmuxWindowInfo(
                index = index,
                name = parts.getOrNull(1).orEmpty(),
                active = parts.getOrNull(2) == "1",
                paneActive = parts.getOrNull(3) == "1",
                activity = parts.getOrNull(4)?.takeIf { it.isNotBlank() }
            )
        }
    }
}

data class TmuxRestoreState(
    val sessionName: String? = null,
    val windowIndex: Int? = null
) {
    val hasSession: Boolean get() = !sessionName.isNullOrBlank()
}

fun resolveTmuxRestoreState(
    argsSession: String? = null,
    argsWindow: Int? = null,
    restorableSession: String? = null,
    restorableWindow: Int? = null
): TmuxRestoreState {
    val session = argsSession?.takeIf { it.isNotBlank() } ?: restorableSession?.takeIf { it.isNotBlank() }
    return TmuxRestoreState(sessionName = session, windowIndex = if (session == null) null else argsWindow ?: restorableWindow)
}

sealed interface TmuxAttachChoice {
    val sessionName: String
    val windowIndex: Int?
}

data class TmuxAttachExisting(
    override val sessionName: String,
    override val windowIndex: Int? = null
) : TmuxAttachChoice

data class TmuxAttachNew(
    override val sessionName: String,
    override val windowIndex: Int? = null
) : TmuxAttachChoice

data class TmuxLaunchPlan(
    val shouldLaunchTmux: Boolean,
    val command: String?,
    val sessionName: String?,
    val windowIndex: Int?
)

fun buildRestoredTmuxLaunchPlan(
    state: TmuxRestoreState,
    sessions: List<TmuxSessionInfo>,
    tmuxBin: String = "tmux"
): TmuxLaunchPlan {
    val sessionName = state.sessionName ?: return TmuxLaunchPlan(false, null, null, null)
    val exists = sessions.any { it.name == sessionName }
    if (!exists) return TmuxLaunchPlan(false, null, null, null)
    val command = state.windowIndex?.let { TmuxCommandBuilder.attachSessionWindow(sessionName, it, tmuxBin) }
        ?: TmuxCommandBuilder.attachSession(sessionName, tmuxBin)
    return TmuxLaunchPlan(true, command, sessionName, state.windowIndex)
}

fun buildChosenTmuxLaunchPlan(choice: TmuxAttachChoice, tmuxBin: String = "tmux"): TmuxLaunchPlan {
    val command = when (choice) {
        is TmuxAttachExisting -> choice.windowIndex?.let { TmuxCommandBuilder.attachSessionWindow(choice.sessionName, it, tmuxBin) }
            ?: TmuxCommandBuilder.attachSession(choice.sessionName, tmuxBin)
        is TmuxAttachNew -> TmuxCommandBuilder.newSessionOrAttach(choice.sessionName, tmuxBin)
    }
    return TmuxLaunchPlan(true, command, choice.sessionName, choice.windowIndex)
}

fun parseTmuxSessions(output: String): List<TmuxSessionInfo> =
    output.lineSequence().mapNotNull(TmuxSessionInfo::tryParse).toList()

fun parseTmuxWindows(output: String): List<TmuxWindowInfo> =
    output.lineSequence().mapNotNull(TmuxWindowInfo::tryParse).toList()

class TmuxSessionScanner(private val session: SshSession) {
    suspend fun listSessions(): List<TmuxSessionInfo> {
        val tmuxBin = resolveTmuxBinary()
        val result = session.execute(TmuxCommandBuilder.listSessionsCmd(tmuxBin))
        return if (result.exitCode == 0) parseTmuxSessions(result.stdout) else emptyList()
    }

    suspend fun listWindows(sessionName: String): List<TmuxWindowInfo> {
        val tmuxBin = resolveTmuxBinary()
        val result = session.execute(TmuxCommandBuilder.listWindows(sessionName, tmuxBin))
        return if (result.exitCode == 0) parseTmuxWindows(result.stdout) else emptyList()
    }

    private suspend fun resolveTmuxBinary(): String {
        val result = session.execute(TmuxCommandBuilder.findTmux)
        return result.stdout.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().ifBlank { "tmux" }
    }
}
