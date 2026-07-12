package com.chrono.ssh.core.service

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chrono.ssh.core.model.MoshPortRange
import sh.haven.mosh.MoshLogger
import sh.haven.mosh.transport.MoshTransport
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val MOSH_TAG = "MoshShellSession"

class MoshShellSession(
    override val serverId: String,
    private val bootstrap: MoshBootstrap,
    private val bootstrapConnection: AutoCloseable
) : SshSession {
    override val id: String = "mosh-$serverId-${UUID.randomUUID()}"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val closed = AtomicBoolean(false)
    private val transcript = StringBuilder()
    private var outputSink: (ByteArray) -> Unit = {}
    @Volatile private var closeHandler: () -> Unit = {}
    private val transport = MoshTransport(
        serverIp = bootstrap.serverIp,
        port = bootstrap.port,
        key = bootstrap.key,
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
        logger = object : MoshLogger {
            override fun d(tag: String, msg: String) {
                Log.d(tag, msg)
            }
            override fun e(tag: String, msg: String, throwable: Throwable?) {
                if (throwable == null) Log.e(tag, msg) else Log.e(tag, msg, throwable)
            }
        }
    )

    init {
        outputSink(MoshTerminalModes.decckmOn)
        transport.start(scope)
        scope.launch {
            transport.secondsUntilDisconnect.collectLatest { seconds ->
                if (seconds != null) Log.d(MOSH_TAG, "Mosh disconnect countdown for $id: ${seconds}s")
            }
        }
    }

    override val transcriptPreview: String
        get() = transcript.toString().takeLast(1200)
    override val isConnected: Boolean get() = !closed.get()

    override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult {
        return CommandResult(command, -1, "", "Exec commands are not available over Mosh.")
    }

    override suspend fun resizeTerminal(columns: Int, rows: Int) {
        if (!closed.get()) transport.resize(columns, rows)
    }

    override suspend fun writeTerminal(input: String) {
        if (closed.get()) throw SshFailure.Network("Mosh session is closed.")
        transport.sendInput(input.toByteArray(StandardCharsets.UTF_8))
    }

    override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) {
        outputSink = onData
        if (!closed.get()) onData(MoshTerminalModes.decckmOn)
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

data class MoshBootstrap(
    val serverIp: String,
    val port: Int,
    val key: String
) {
    init {
        require(port in 1..65535) { "Mosh port must be between 1 and 65535." }
        require(MoshBootstrapParser.isValidKey(key)) { "Mosh key is malformed or weak." }
    }
}

internal object MoshTerminalModes {
    val decckmOn: ByteArray = byteArrayOf(0x1B, '['.code.toByte(), '?'.code.toByte(), '1'.code.toByte(), 'h'.code.toByte())
}

internal object MoshBootstrapCommand {
    fun build(
        startupCommand: String,
        serverCommand: String = "mosh-server",
        locale: String = "en_US.UTF-8",
        colors: Int = 256,
        portRange: MoshPortRange? = null
    ): String {
        require(colors in 8..256) { "Mosh colors must be 8-256." }
        val command = startupCommand.trim()
        return buildString {
            append(serverCommand.trim().ifBlank { "mosh-server" }.shellQuote())
            append(" new -s")
            if (portRange != null) {
                append(" -p ")
                append(portRange.commandValue())
            }
            append(" -c ")
            append(colors)
            append(" -l LANG=")
            append(locale.trim().ifBlank { "en_US.UTF-8" }.shellQuote())
            if (command.isNotBlank()) {
                append(" -- sh -lc ")
                append(command.shellQuote())
            }
        }
    }

    private fun String.shellQuote(): String {
        return "'${replace("'", "'\"'\"'")}'"
    }
}

internal object MoshBootstrapParser {
    private val connectRegex = Regex("""MOSH CONNECT\s+(\d+)\s+([A-Za-z0-9+/]+)(?:\s|$)""")

    fun parseConnect(output: String): ParsedMoshConnect? {
        val match = connectRegex.find(output) ?: return null
        val port = match.groupValues[1].toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val key = match.groupValues[2].takeIf(::isValidKey) ?: return null
        return ParsedMoshConnect(port, key)
    }

    fun isValidKey(key: String): Boolean {
        if (!Regex("""[A-Za-z0-9+/]{22}""").matches(key)) return false
        val decoded = runCatching { Base64.getDecoder().decode("$key==") }.getOrNull() ?: return false
        return decoded.size == 16 && decoded.any { it != 0.toByte() } && decoded.toSet().size > 1
    }
}

internal data class ParsedMoshConnect(val port: Int, val key: String)

internal object MoshBootstrapFailure {
    fun message(output: String): String {
        val clean = output.trim().lineSequence().filter { it.isNotBlank() }.take(6).joinToString(" ").take(240)
        val hint = when {
            clean.contains("not found", ignoreCase = true) ||
                clean.contains("No such file", ignoreCase = true) ->
                "Install mosh-server on the host or set the correct server command."
            clean.contains("permission denied", ignoreCase = true) ->
                "mosh-server exists but is not executable for this user."
            clean.contains("locale", ignoreCase = true) || clean.contains("LANG", ignoreCase = true) ->
                "Check the Mosh locale setting for this host."
            else -> "Check that mosh-server can run on the host and that UDP from the device to the server is allowed."
        }
        return "Mosh bootstrap failed: mosh-server did not return MOSH CONNECT. $hint Output: ${clean.ifBlank { "empty" }}"
    }
}
