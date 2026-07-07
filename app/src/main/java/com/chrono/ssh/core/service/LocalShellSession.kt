package com.chrono.ssh.core.service

import android.content.Context
import android.os.ParcelFileDescriptor
import com.chrono.ssh.core.model.ProotProfileConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class LocalShellSession(
    override val serverId: String,
    context: Context,
    label: String,
    prootConfig: ProotProfileConfig = ProotProfileConfig()
) : SshSession {
    override val id: String = "local-$serverId-${UUID.randomUUID()}"
    private val transcript = StringBuilder()
    private var outputSink: (ByteArray) -> Unit = {}
    @Volatile private var closeHandler: () -> Unit = {}
    private val closed = AtomicBoolean(false)
    private val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var masterFd = -1
    private var childPid = -1
    private val masterPfd: ParcelFileDescriptor
    private val inputStream: FileInputStream
    private val outputStream: FileOutputStream
    val prootBacked: Boolean

    init {
        val command = LocalProotRuntime.command(context, label, prootConfig)
        prootBacked = command.prootBacked
        val fork = PtyBridge.nativeForkPty(
            cmd = command.cmd,
            args = command.args,
            env = command.env,
            rows = 24,
            cols = 80
        )
        if (fork[0] < 0) throw SshFailure.Network("Local PTY failed to start: errno=${fork[1]}")
        masterFd = fork[0]
        childPid = fork[1]
        masterPfd = ParcelFileDescriptor.adoptFd(masterFd)
        inputStream = FileInputStream(masterPfd.fileDescriptor)
        outputStream = FileOutputStream(masterPfd.fileDescriptor)

        readerScope.launch {
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            try {
                while (!closed.get()) {
                    val read = runCatching { inputStream.read(buffer) }.getOrElse { -1 }
                    if (read <= 0) break
                    val bytes = buffer.copyOf(read)
                    transcript.append(bytes.toString(Charsets.UTF_8))
                    runCatching { outputSink(bytes) }
                }
            } finally {
                if (!closed.get()) {
                    runCatching { PtyBridge.nativeWaitPid(childPid) }
                }
                closePty()
                closeHandler()
                readerScope.cancel()
            }
        }
    }

    override val transcriptPreview: String
        get() = transcript.toString().takeLast(1200)
    override val isConnected: Boolean get() = !closed.get()

    override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult = withContext(Dispatchers.IO) {
        val proc = ProcessBuilder(resolveShell(), "-c", command).redirectErrorStream(false).start()
        val finished = proc.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            return@withContext CommandResult(command, -1, "", "Command timed out after ${timeoutSeconds}s.")
        }
        CommandResult(
            command = command,
            exitCode = proc.exitValue(),
            stdout = proc.inputStream.readBytes().toString(Charsets.UTF_8),
            stderr = proc.errorStream.readBytes().toString(Charsets.UTF_8)
        )
    }

    override suspend fun resizeTerminal(columns: Int, rows: Int) {
        if (!closed.get() && masterFd >= 0) {
            PtyBridge.nativeSetSize(masterFd, rows, columns)
        }
    }

    override suspend fun writeTerminal(input: String) = withContext(Dispatchers.IO) {
        if (closed.get()) throw SshFailure.Network("Local shell is closed.")
        outputStream.write(input.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }

    override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) {
        outputSink = onData
    }

    override fun setTerminalCloseHandler(onClosed: () -> Unit) {
        closeHandler = onClosed
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        closePty()
        readerScope.cancel()
        Unit
    }

    private fun closePty() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { outputStream.close() }
        runCatching { inputStream.close() }
        runCatching { masterPfd.close() }
        if (childPid > 0) runCatching { android.os.Process.killProcess(childPid) }
        masterFd = -1
        childPid = -1
    }

    companion object {
        fun resolveShell(): String = when {
            File("/system/bin/sh").canExecute() -> "/system/bin/sh"
            File("/bin/sh").canExecute() -> "/bin/sh"
            else -> "sh"
        }

        fun localShellEnvironment(label: String, home: String = "/", tmp: String = "/tmp"): Array<String> = arrayOf(
            "TERM=xterm-256color",
            "HOME=$home",
            "PWD=$home",
            "PATH=/system/bin:/system/xbin:/bin:/usr/bin",
            "SHELL=${resolveShell()}",
            "TMPDIR=$tmp",
            "CHRONOSSH_SESSION=$label"
        )
    }
}
