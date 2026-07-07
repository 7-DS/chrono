package com.chrono.ssh.core.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import sh.haven.et.EtLogger
import sh.haven.et.transport.EtTransport
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

private const val ET_TAG = "EtShellSession"

class EtShellSession(
    override val serverId: String,
    private val bootstrap: EtBootstrap,
    private val bootstrapConnection: AutoCloseable
) : SshSession {
    override val id: String = "et-$serverId-${UUID.randomUUID()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val transcript = StringBuilder()
    private var outputSink: (ByteArray) -> Unit = {}
    @Volatile private var closeHandler: () -> Unit = {}
    private val transport = EtTransport(
        serverHost = bootstrap.serverHost,
        port = bootstrap.port,
        clientId = bootstrap.clientId,
        passkey = bootstrap.passkey,
        onOutput = { bytes, offset, length ->
            if (!closed.get()) {
                val chunk = bytes.copyOfRange(offset, offset + length)
                transcript.append(chunk.toString(StandardCharsets.UTF_8))
                outputSink(chunk)
            }
        },
        onDisconnect = {
            if (closed.compareAndSet(false, true)) {
                runCatching { bootstrapConnection.close() }
                closeHandler()
                scope.cancel()
            }
        },
        logger = object : EtLogger {
            override fun d(tag: String, msg: String) {
                Log.d(tag, msg)
            }

            override fun e(tag: String, msg: String, throwable: Throwable?) {
                if (throwable == null) Log.e(tag, msg) else Log.e(tag, msg, throwable)
            }
        }
    )

    init {
        transport.start(scope)
    }

    override val transcriptPreview: String
        get() = transcript.toString().takeLast(1200)
    override val isConnected: Boolean get() = !closed.get()

    override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
        return CommandResult(command, -1, "", "Exec commands are not available over Eternal Terminal.")
    }

    override suspend fun resizeTerminal(columns: Int, rows: Int) {
        if (!closed.get()) transport.resize(columns, rows)
    }

    override suspend fun writeTerminal(input: String) {
        if (closed.get()) throw SshFailure.Network("Eternal Terminal session is closed.")
        transport.sendInput(input.toByteArray(StandardCharsets.UTF_8))
    }

    override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) {
        outputSink = onData
    }

    override fun setTerminalCloseHandler(onClosed: () -> Unit) {
        closeHandler = onClosed
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        if (!closed.compareAndSet(false, true)) return@withContext
        transport.close()
        runCatching { bootstrapConnection.close() }
        scope.cancel()
        Unit
    }
}

data class EtBootstrap(
    val serverHost: String,
    val port: Int,
    val clientId: String,
    val passkey: String
) {
    init {
        require(port in 1..65535) { "ET port must be between 1 and 65535." }
    }
}

internal data class ParsedEtIdPasskey(val clientId: String, val passkey: String)

internal object EtBootstrapCommand {
    fun build(proposal: EtBootstrapProposal, terminalType: String, serverCommand: String): String {
        val idPasskey = "${proposal.clientId}/${proposal.passkey}_${terminalType.trim().ifBlank { "xterm-256color" }}"
        return "printf %s ${idPasskey.shellQuote()} | ${serverCommand.trim().ifBlank { "etterminal" }.shellQuote()}"
    }

    private fun String.shellQuote(): String {
        return "'${replace("'", "'\"'\"'")}'"
    }
}

internal object EtBootstrapParser {
    private const val marker = "IDPASSKEY:"
    private val idPasskeyRegex = Regex("""([A-Za-z0-9]{16})/([A-Za-z0-9]{32})(?:\r?\n|$)""")

    fun parseIdPasskey(output: String): ParsedEtIdPasskey? {
        val markerPos = output.indexOf(marker)
        if (markerPos < 0) return null
        val line = output.substring(markerPos + marker.length).trimStart()
        val match = idPasskeyRegex.find(line) ?: return null
        if (match.range.first != 0) return null
        return ParsedEtIdPasskey(match.groupValues[1], match.groupValues[2])
    }
}

internal object EtBootstrapFailure {
    fun message(output: String): String {
        val clean = output.trim().lineSequence().filter { it.isNotBlank() }.take(6).joinToString(" ").take(240)
        val hint = when {
            clean.contains("not found", ignoreCase = true) ||
                clean.contains("No such file", ignoreCase = true) ->
                "Install Eternal Terminal on the host or set the correct etterminal command."
            clean.contains("permission denied", ignoreCase = true) ->
                "etterminal exists but is not executable for this user."
            clean.contains("port", ignoreCase = true) || clean.contains("bind", ignoreCase = true) ->
                "Check the configured ET server port and firewall."
            else -> "Check that etterminal can run on the host and that the ET server port is reachable."
        }
        return "Eternal Terminal bootstrap failed: etterminal did not return IDPASSKEY. $hint Output: ${clean.ifBlank { "empty" }}"
    }
}

internal data class EtBootstrapProposal(val clientId: String, val passkey: String) {
    companion object {
        private const val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        fun create(random: Random = Random.Default): EtBootstrapProposal {
            fun token(length: Int) = buildString(length) {
                repeat(length) { append(chars[random.nextInt(chars.length)]) }
            }
            return EtBootstrapProposal("XXX" + token(13), token(32))
        }
    }
}
