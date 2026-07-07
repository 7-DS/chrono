package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ServerProfile
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.EnumSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class SmbTarget(
    val host: String,
    val port: Int,
    val share: String,
    val initialPath: String,
    val username: String,
    val domain: String
)

internal object SmbTargetPolicy {
    fun from(profile: ServerProfile): SmbTarget {
        val rawStart = profile.startDirectory.ifBlank { profile.host }.trim()
        val hasSmbUrl = rawStart.startsWith("smb://", ignoreCase = true)
        val raw = rawStart
            .removePrefix("smb://")
            .removePrefix("SMB://")
            .replace('\\', '/')
            .trim()
        val uncHost = raw
            .takeIf { it.startsWith("//") }
            ?.removePrefix("//")
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
        val urlHost = raw
            .takeIf { hasSmbUrl && !it.startsWith("//") }
            ?.substringBefore('/')
            ?.takeIf { it.isNotBlank() }
        val noHost = when {
            uncHost != null -> raw.removePrefix("//$uncHost").trimStart('/')
            urlHost != null -> raw.removePrefix(urlHost).trimStart('/')
            else -> raw
                .removePrefix("//${profile.host}/")
                .removePrefix("${profile.host}/")
        }.trim('/')
        val parts = noHost.split('/').filter { it.isNotBlank() }
        val share = parts.firstOrNull()
            ?: throw SshFailure.Unsupported("SMB needs a share in the host start folder, for example 'Documents' or '//${profile.host}/Documents'.")
        val userParts = profile.username.split('\\', limit = 2)
        val (domain, user) = if (userParts.size == 2) userParts[0] to userParts[1] else "" to profile.username
        return SmbTarget(
            host = uncHost ?: urlHost ?: profile.host.substringBefore('/'),
            port = profile.port.takeIf { it > 0 } ?: 445,
            share = share,
            initialPath = "/" + parts.drop(1).joinToString("/"),
            username = user.ifBlank { "guest" },
            domain = domain
        )
    }
}

internal class SmbFileShareClient private constructor(
    private val target: SmbTarget,
    private val password: CharArray,
    private var client: SMBClient,
    private var connection: Connection,
    private var session: Session,
    private var share: DiskShare,
    private val downloadDir: File
) : SftpClient {
    private val uiRoot = "/${target.share}"
    private val reconnectLock = Any()

    override suspend fun realpath(path: String): String = withContext(Dispatchers.IO) {
        toUiPath(toSharePath(path))
    }

    override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
        val sharePath = toSharePath(path.ifBlank { target.initialPath })
        withStaleShareRetry { activeShare ->
            activeShare.list(toSmbPath(sharePath))
                .filterNot { it.fileName == "." || it.fileName == ".." }
                .map { it.toEntry(sharePath) }
                .sortedWith(compareByDescending<SftpEntry> { it.directory }.thenBy { it.name.lowercase() })
        }
    }

    override suspend fun download(remotePath: String, localDisplayName: String, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val safeName = SftpPathResolver.leafName(localDisplayName.ifBlank { remotePath })
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val targetFile = File(downloadDir, "${System.currentTimeMillis()}-$safeName")
            runCatching {
                targetFile.outputStream().use { output -> downloadTo(remotePath, safeName, output, onProgress) }
            }.recoverCatching { error ->
                if (!isStaleSmbSession(error)) throw error
                targetFile.delete()
                reconnectShare()
                targetFile.outputStream().use { output -> downloadTo(remotePath, safeName, output, onProgress) }
            }.getOrThrow()
            TransferHandle("smb-download-${remotePath.hashCode()}-${System.currentTimeMillis()}", targetFile.name, 1f, TransferState.Complete, targetFile.absolutePath)
        }

    override suspend fun downloadTo(remotePath: String, localDisplayName: String, output: OutputStream, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val file = share.openFile(
                toSmbPath(toSharePath(remotePath)),
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            file.use {
                val total = it.fileInformation.standardInformation.endOfFile
                it.inputStream.copyToWithProgress(output, total, onProgress)
            }
            TransferHandle("smb-download-${remotePath.hashCode()}-${System.currentTimeMillis()}", localDisplayName, 1f, TransferState.Complete)
        }

    override suspend fun upload(localDisplayName: String, remotePath: String, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val localFile = File(localDisplayName)
            require(localFile.exists()) { "Selected local file is no longer available." }
            localFile.inputStream().use { input -> uploadFrom(localFile.name, remotePath, localFile.length(), input, onProgress) }
        }

    override suspend fun uploadFrom(localDisplayName: String, remotePath: String, totalBytes: Long, input: InputStream, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val file = share.openFile(
                toSmbPath(toSharePath(remotePath)),
                EnumSet.of(AccessMask.GENERIC_WRITE),
                EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OVERWRITE_IF,
                EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            file.use { opened -> input.use { it.copyToWithProgress(opened.outputStream, totalBytes, onProgress) } }
            TransferHandle("smb-upload-${remotePath.hashCode()}-${System.currentTimeMillis()}", localDisplayName, 1f, TransferState.Complete)
        }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val file = share.openFile(
            toSmbPath(toSharePath(from)),
            EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        file.use { it.rename(toSmbPath(toSharePath(to))) }
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val smbPath = toSmbPath(toSharePath(path))
        runCatching { share.rm(smbPath) }.getOrElse { fileError ->
            runCatching { share.rmdir(smbPath, false) }.getOrElse { throw fileError }
        }
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        share.mkdir(toSmbPath(toSharePath(path)))
    }

    override suspend fun chmod(path: String, mode: Int) {
        throw SshFailure.Unsupported("SMB does not support Unix chmod.")
    }

    override suspend fun close(): Unit = withContext(Dispatchers.IO) {
        closeHandles()
        Unit
    }

    private inline fun <T> withStaleShareRetry(operation: (DiskShare) -> T): T {
        return runCatching { operation(share) }.recoverCatching { error ->
            if (!isStaleSmbSession(error)) throw error
            operation(reconnectShare())
        }.getOrThrow()
    }

    private fun reconnectShare(): DiskShare = synchronized(reconnectLock) {
        closeHandles()
        val nextClient = SMBClient()
        val nextConnection = nextClient.connect(target.host, target.port)
        try {
            val nextSession = nextConnection.authenticate(AuthenticationContext(target.username, password.copyOf(), target.domain))
            val nextShare = nextSession.connectShare(target.share) as DiskShare
            client = nextClient
            connection = nextConnection
            session = nextSession
            share = nextShare
            nextShare
        } catch (error: Exception) {
            runCatching { nextConnection.close() }
            runCatching { nextClient.close() }
            throw error
        }
    }

    private fun closeHandles() {
        runCatching { share.close() }
        runCatching { session.close() }
        runCatching { connection.close() }
        runCatching { client.close() }
    }

    private fun FileIdBothDirectoryInformation.toEntry(parentPath: String): SftpEntry {
        val isDirectory = fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value != 0L
        val shareChild = parentPath.trimEnd('/').let { parent ->
            if (parent.isBlank() || parent == "/") "/$fileName" else "$parent/$fileName"
        }
        return SftpEntry(
            name = fileName,
            path = toUiPath(shareChild),
            directory = isDirectory,
            sizeBytes = endOfFile,
            modifiedEpochMillis = lastWriteTime.toEpochMillis(),
            type = if (isDirectory) SftpEntryType.Directory else SftpEntryType.File
        )
    }

    private fun toSharePath(uiPath: String): String {
        val clean = SftpPathResolver.normalize(uiPath).replace('\\', '/')
        val withoutShare = clean
            .removePrefix(uiRoot)
            .removePrefix(target.share)
            .trim('/')
        return if (withoutShare.isBlank()) "/" else "/$withoutShare"
    }

    private fun toUiPath(sharePath: String): String {
        val clean = sharePath.trim('/')
        return if (clean.isBlank()) uiRoot else "$uiRoot/$clean"
    }

    private fun toSmbPath(path: String): String = path.trim('/').replace('/', '\\')

    private suspend fun InputStream.copyToWithProgress(output: OutputStream, totalBytes: Long, onProgress: (Float) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var copied = 0L
        onProgress(0f)
        while (true) {
            coroutineContext.ensureActive()
            val read = read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            copied += read
            onProgress(smbTransferProgress(copied, totalBytes))
        }
        output.flush()
        onProgress(1f)
    }

    companion object {
        suspend fun open(
            profile: ServerProfile,
            credential: Credential?,
            secretStore: SecretStore,
            downloadDir: File
        ): SftpClient = withContext(Dispatchers.IO) {
            if (credential?.type != CredentialType.Password || !credential.secretBacked) {
                throw SshFailure.Authentication("SMB requires a saved password identity.")
            }
            val target = SmbTargetPolicy.from(profile)
            val password = secretStore.loadSecret(credential.encryptedPayloadRef).toString(Charsets.UTF_8).toCharArray()
            val client = SMBClient()
            val connection = client.connect(target.host, target.port)
            try {
                val session = connection.authenticate(AuthenticationContext(target.username, password.copyOf(), target.domain))
                val share = session.connectShare(target.share) as DiskShare
                SmbFileShareClient(target, password, client, connection, session, share, downloadDir.apply { mkdirs() })
            } catch (error: Exception) {
                runCatching { connection.close() }
                runCatching { client.close() }
                throw SshFailure.Network("SMB connect failed for \\\\${target.host}\\${target.share}: ${error.message.orEmpty()}", error)
            }
        }
    }
}

internal fun isStaleSmbSession(error: Throwable): Boolean {
    return generateSequence(error) { it.cause }.any { cause ->
        when (cause) {
            is SMBApiException -> cause.status in staleSmbStatuses
            is TransportException -> cause.message.isClosedTransportMessage()
            is java.io.IOException -> cause.message.isClosedTransportMessage()
            else -> false
        }
    }
}

private val staleSmbStatuses = setOf(
    NtStatus.STATUS_USER_SESSION_DELETED,
    NtStatus.STATUS_NETWORK_SESSION_EXPIRED,
    NtStatus.STATUS_NETWORK_NAME_DELETED,
    NtStatus.STATUS_CONNECTION_DISCONNECTED,
    NtStatus.STATUS_CONNECTION_RESET
)

private fun String?.isClosedTransportMessage(): Boolean {
    val lower = this?.lowercase().orEmpty()
    return listOf("connection closed", "connection reset", "broken pipe", "socket closed").any { it in lower }
}

private fun smbTransferProgress(done: Long, total: Long): Float {
    return if (total <= 0L) 0f else (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}
