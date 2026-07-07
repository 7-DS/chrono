package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.ServerProfile
import java.io.InputStream
import java.io.OutputStream

interface SshTransport {
    suspend fun connect(profile: ServerProfile, credential: Credential?): SshSession
    suspend fun connectExec(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String? = null
    ): SshSession {
        return connectShell(profile, credential, hostKeyDecision, privateKeyPassphrase)
    }
    suspend fun connectShell(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String? = null
    ): SshSession
    suspend fun openSftp(
        profile: ServerProfile,
        credential: Credential?,
        privateKeyPassphrase: String? = null
    ): SftpClient
    suspend fun openScp(
        profile: ServerProfile,
        credential: Credential?,
        privateKeyPassphrase: String? = null
    ): ScpClient {
        throw SshFailure.Unsupported("SCP is not available in this transport.")
    }
    suspend fun verifyHost(profile: ServerProfile): KnownHost
    suspend fun startLocalForward(
        profile: ServerProfile,
        credential: Credential?,
        rule: PortForwardRule,
        onClosed: (ForwardStatus) -> Unit = {}
    ): ForwardStatus {
        throw SshFailure.Unsupported("Local port forwarding is not available in this transport.")
    }
    suspend fun startRemoteForward(
        profile: ServerProfile,
        credential: Credential?,
        rule: PortForwardRule,
        onClosed: (ForwardStatus) -> Unit = {}
    ): ForwardStatus {
        throw SshFailure.Unsupported("Remote port forwarding is not available in this transport.")
    }
    suspend fun startDynamicSocksForward(
        profile: ServerProfile,
        credential: Credential?,
        rule: PortForwardRule,
        onClosed: (ForwardStatus) -> Unit = {}
    ): ForwardStatus {
        throw SshFailure.Unsupported("Dynamic SOCKS forwarding is not available in this transport.")
    }
    suspend fun stopForward(ruleId: String) = Unit
}

interface MoshCapableTransport {
    suspend fun connectMosh(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String? = null
    ): SshSession
}

interface EtCapableTransport {
    suspend fun connectEt(
        profile: ServerProfile,
        credential: Credential?,
        hostKeyDecision: suspend (HostKeyPrompt) -> HostKeyDecision,
        privateKeyPassphrase: String? = null
    ): SshSession
}

data class HostKeyPrompt(
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    val state: HostKeyTrustState,
    val message: String
)

data class ProxyJumpTarget(
    val profile: ServerProfile,
    val credential: Credential?
)

enum class HostKeyDecision {
    TrustOnce,
    TrustAndRemember,
    Reject
}

sealed class SshFailure(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Authentication(message: String) : SshFailure(message)
    class HostKeyRejected(message: String) : SshFailure(message)
    class Network(message: String, cause: Throwable? = null) : SshFailure(message, cause)
    class Unsupported(message: String) : SshFailure(message)
}

interface SshSession {
    val id: String
    val serverId: String
    val transcriptPreview: String
    val isConnected: Boolean get() = true
    suspend fun execute(command: String, timeoutSeconds: Long = 12L): CommandResult
    suspend fun resizeTerminal(columns: Int, rows: Int)
    suspend fun writeTerminal(input: String)
    fun setTerminalOutputSink(onData: (ByteArray) -> Unit)
    fun setTerminalCloseHandler(onClosed: () -> Unit) = Unit
    suspend fun close()
}

data class CommandResult(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
)

interface TerminalEngine {
    fun load()
    fun attach(session: SshSession)
    fun appendIncoming(bytes: ByteArray)
    fun sendInput(input: String)
    fun sendBytes(input: ByteArray)
    fun resize(columns: Int, rows: Int)
    fun copyTranscript(): String
    fun searchTranscript(query: String): List<Int>
    fun detach()
    fun dispose()
}

interface MetricsCollector {
    suspend fun collect(profile: ServerProfile, session: SshSession): MetricSnapshot
}

interface SftpClient {
    suspend fun realpath(path: String): String = path
    suspend fun list(path: String): List<SftpEntry>
    suspend fun download(
        remotePath: String,
        localDisplayName: String,
        onProgress: (Float) -> Unit = {}
    ): TransferHandle
    suspend fun downloadTo(
        remotePath: String,
        localDisplayName: String,
        output: OutputStream,
        onProgress: (Float) -> Unit = {}
    ): TransferHandle {
        throw SshFailure.Unsupported("Direct SFTP download streaming is not available in this client.")
    }
    suspend fun upload(
        localDisplayName: String,
        remotePath: String,
        onProgress: (Float) -> Unit = {}
    ): TransferHandle
    suspend fun uploadFrom(
        localDisplayName: String,
        remotePath: String,
        totalBytes: Long,
        input: InputStream,
        onProgress: (Float) -> Unit = {}
    ): TransferHandle {
        throw SshFailure.Unsupported("Direct SFTP upload streaming is not available in this client.")
    }
    suspend fun rename(from: String, to: String)
    suspend fun delete(path: String)
    suspend fun mkdir(path: String)
    suspend fun chmod(path: String, mode: Int)
    suspend fun publicLink(path: String): String {
        throw SshFailure.Unsupported("Public links are not available in this client.")
    }
    suspend fun directorySize(path: String): RcloneDirectorySize {
        throw SshFailure.Unsupported("Directory sizing is not available in this client.")
    }
    suspend fun remoteSpace(path: String): RcloneRemoteSpace {
        throw SshFailure.Unsupported("Remote space is not available in this client.")
    }
    suspend fun close() = Unit
}

data class RcloneDirectorySize(
    val count: Long,
    val bytes: Long
)

data class RcloneRemoteSpace(
    val used: Long,
    val free: Long,
    val total: Long
)

interface ScpClient {
    suspend fun downloadTo(
        remotePath: String,
        localDisplayName: String,
        output: OutputStream,
        onProgress: (Float) -> Unit = {}
    ): TransferHandle
    suspend fun uploadFrom(
        localDisplayName: String,
        remotePath: String,
        totalBytes: Long,
        input: InputStream,
        onProgress: (Float) -> Unit = {}
    ): TransferHandle
    suspend fun close() = Unit
}

data class SftpEntry(
    val name: String,
    val path: String,
    val directory: Boolean,
    val sizeBytes: Long,
    val modifiedEpochMillis: Long,
    val type: SftpEntryType = if (directory) SftpEntryType.Directory else SftpEntryType.File,
    val permissions: String? = null,
    val owner: String? = null,
    val group: String? = null
) {
    val navigable: Boolean get() = directory || type == SftpEntryType.Symlink

    fun permissionsLabel(): String? {
        val mode = permissions?.takeIf { it.isNotBlank() }
        val ownerGroup = listOfNotNull(
            owner?.takeIf { it.isNotBlank() },
            group?.takeIf { it.isNotBlank() }
        ).joinToString(":").takeIf { it.isNotBlank() }
        return listOfNotNull(mode, ownerGroup).joinToString(" ").takeIf { it.isNotBlank() }
    }
}

enum class SftpEntryType {
    File,
    Directory,
    Symlink
}

data class TransferHandle(
    val id: String,
    val title: String,
    val progress: Float,
    val state: TransferState,
    val localPath: String? = null
)

enum class TransferState {
    Queued,
    Running,
    Complete,
    Failed,
    Cancelled
}

interface SecretStore {
    suspend fun storeSecret(label: String, clearText: ByteArray): String
    suspend fun loadSecret(ref: String): ByteArray
    suspend fun deleteSecret(ref: String)
}

interface BackupCodec {
    suspend fun exportEncrypted(passphrase: CharArray): ByteArray
    suspend fun importEncrypted(payload: ByteArray, passphrase: CharArray)
}

interface ConnectionLogStore {
    suspend fun append(serverId: String, message: String)
    suspend fun recent(serverId: String, limit: Int = 200): List<String>
}

interface PortForwardManager {
    suspend fun start(rule: PortForwardRule): ForwardStatus
    suspend fun stop(ruleId: String)
    suspend fun active(): List<ForwardStatus>
}

data class ForwardStatus(
    val ruleId: String,
    val boundAddress: String,
    val active: Boolean,
    val lastMessage: String,
    val lastError: String? = null
)
