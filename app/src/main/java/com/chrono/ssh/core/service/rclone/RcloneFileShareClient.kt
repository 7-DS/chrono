package com.chrono.ssh.core.service.rclone

import android.content.Context
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.rclone.RcloneClient
import com.chrono.ssh.core.rclone.RcloneFileEntry
import com.chrono.ssh.core.rclone.RcloneTransferOptions
import com.chrono.ssh.core.service.SftpClient
import com.chrono.ssh.core.service.SftpEntry
import com.chrono.ssh.core.service.SftpEntryType
import com.chrono.ssh.core.service.SftpPathResolver
import com.chrono.ssh.core.service.SshFailure
import com.chrono.ssh.core.service.RcloneDirectorySize
import com.chrono.ssh.core.service.RcloneRemoteSpace
import com.chrono.ssh.core.service.TransferHandle
import com.chrono.ssh.core.service.TransferState
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal data class RcloneTarget(
    val remote: String?,
    val initialPath: String
)

internal object RcloneTargetPolicy {
    fun from(profile: ServerProfile): RcloneTarget {
        val raw = profile.startDirectory
            .removePrefix("rclone://")
            .trim()
            .trimStart('/')
        if (raw.isBlank() || raw == ".") return RcloneTarget(null, "/")
        val remote = raw.substringBefore(':', missingDelimiterValue = raw.substringBefore('/'))
            .trim()
            .removeSuffix(":")
        val path = when {
            ':' in raw -> raw.substringAfter(':').ifBlank { "/" }
            '/' in raw -> raw.substringAfter('/').ifBlank { "/" }
            else -> "/"
        }
        return RcloneTarget(remote.takeIf { it.isNotBlank() }, SftpPathResolver.normalize(path))
    }
}

internal object RclonePathPolicy {
    fun splitRemotePath(target: RcloneTarget, path: String): Pair<String, String> {
        val normalized = normalizeUiPath(path)
        val remote = target.remote ?: normalized.trim('/').substringBefore('/').takeIf { it.isNotBlank() }
        if (remote == null) throw SshFailure.Unsupported("Choose an rclone remote first.")
        return remote to remotePath(remote, normalized)
    }

    fun remotePath(remote: String, uiPath: String): String {
        val clean = normalizeUiPath(uiPath).trim('/')
        return clean.removePrefix(remote).trimStart('/').ifBlank { "/" }
    }

    fun normalizeUiPath(path: String): String = SftpPathResolver.normalize(path).let {
        if (it == ".") "/" else if (it.startsWith("/")) it else "/$it"
    }
}

internal class RcloneFileShareClient private constructor(
    private val client: RcloneClient,
    private val target: RcloneTarget,
    private val downloadDir: File,
    private val transferOptions: RcloneTransferOptions
) : SftpClient {
    override suspend fun realpath(path: String): String = normalizeUiPath(path)

    override suspend fun list(path: String): List<SftpEntry> = withContext(Dispatchers.IO) {
        val normalized = normalizeUiPath(path.ifBlank { target.initialPath })
        val remote = target.remote ?: normalized.trim('/').substringBefore('/').takeIf { it.isNotBlank() }
        if (remote == null) {
            return@withContext client.listRemotes().map { name ->
                SftpEntry(
                    name = name.removeSuffix(":"),
                    path = "/${name.removeSuffix(":")}",
                    directory = true,
                    sizeBytes = 0,
                    modifiedEpochMillis = 0,
                    type = SftpEntryType.Directory
                )
            }.sortedBy { it.name.lowercase() }
        }
        val remotePath = remotePath(remote, normalized)
        client.listDirectory(remote, remotePath)
            .map { it.toEntry(remote, remotePath) }
            .sortedWith(compareByDescending<SftpEntry> { it.directory }.thenBy { it.name.lowercase() })
    }

    override suspend fun download(remotePath: String, localDisplayName: String, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val targetFile = File(downloadDir, safeName(localDisplayName.ifBlank { remotePath }))
            targetFile.outputStream().use { output -> downloadTo(remotePath, targetFile.name, output, onProgress) }
            TransferHandle("rclone-download-${remotePath.hashCode()}-${System.currentTimeMillis()}", targetFile.name, 1f, TransferState.Complete, targetFile.absolutePath)
        }

    override suspend fun downloadTo(remotePath: String, localDisplayName: String, output: OutputStream, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val (remote, path) = splitRemotePath(remotePath)
            val tempDir = File(downloadDir, "stream-${System.nanoTime()}").apply { mkdirs() }
            val fileName = safeName(localDisplayName.ifBlank { path.substringAfterLast('/') })
            try {
                client.copyFileWithProgress(remote, path, tempDir.absolutePath, fileName, onProgress)
                File(tempDir, fileName).inputStream().use { it.copyTo(output) }
            } finally {
                tempDir.deleteRecursively()
            }
            onProgress(1f)
            TransferHandle("rclone-download-${remotePath.hashCode()}-${System.currentTimeMillis()}", fileName, 1f, TransferState.Complete)
        }

    override suspend fun upload(localDisplayName: String, remotePath: String, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val localFile = File(localDisplayName)
            require(localFile.exists()) { "Selected local file is no longer available." }
            localFile.inputStream().use { input -> uploadFrom(localFile.name, remotePath, localFile.length(), input, onProgress) }
        }

    override suspend fun uploadFrom(localDisplayName: String, remotePath: String, totalBytes: Long, input: InputStream, onProgress: (Float) -> Unit): TransferHandle =
        withContext(Dispatchers.IO) {
            val (remote, path) = splitRemotePath(remotePath)
            val tempDir = File(downloadDir, "upload-${System.nanoTime()}").apply { mkdirs() }
            val fileName = safeName(localDisplayName)
            try {
                File(tempDir, fileName).outputStream().use { input.copyTo(it) }
                client.copyFileWithProgress(tempDir.absolutePath, fileName, remote, path, onProgress)
            } finally {
                tempDir.deleteRecursively()
            }
            onProgress(1f)
            TransferHandle("rclone-upload-${remotePath.hashCode()}-${System.currentTimeMillis()}", fileName, 1f, TransferState.Complete)
        }

    override suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val (remote, fromPath) = splitRemotePath(from)
        val (toRemote, toPath) = splitRemotePath(to)
        client.moveFile(remote, fromPath, toRemote, toPath)
    }

    override suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        val (remote, remotePath) = splitRemotePath(path)
        val entry = runCatching { client.listDirectory(remote, remotePath.substringBeforeLast('/', "")) }
            .getOrDefault(emptyList())
            .firstOrNull { it.path.trim('/') == remotePath.trim('/') }
        if (entry?.isDir == true) client.deleteDir(remote, remotePath) else client.deleteFile(remote, remotePath)
    }

    override suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        val (remote, remotePath) = splitRemotePath(path)
        client.mkdir(remote, remotePath)
    }

    override suspend fun chmod(path: String, mode: Int) {
        throw SshFailure.Unsupported("rclone remotes do not support Unix chmod.")
    }

    override suspend fun publicLink(path: String): String = withContext(Dispatchers.IO) {
        val (remote, remotePath) = splitRemotePath(path)
        client.publicLink(remote, remotePath)
    }

    override suspend fun directorySize(path: String): RcloneDirectorySize = withContext(Dispatchers.IO) {
        val (remote, remotePath) = splitRemotePath(path)
        client.directorySize(remote, remotePath).let { RcloneDirectorySize(it.count, it.bytes) }
    }

    override suspend fun remoteSpace(path: String): RcloneRemoteSpace = withContext(Dispatchers.IO) {
        val (remote, _) = splitRemotePath(path)
        client.about(remote).let { RcloneRemoteSpace(used = it.used, free = it.free, total = it.total) }
    }

    private fun RcloneFileEntry.toEntry(remote: String, parentPath: String): SftpEntry {
        val childPath = path.ifBlank {
            parentPath.trimEnd('/').let { parent -> if (parent.isBlank()) name else "$parent/$name" }
        }
        return SftpEntry(
            name = name,
            path = "/$remote/${childPath.trim('/')}",
            directory = isDir,
            sizeBytes = size,
            modifiedEpochMillis = runCatching { Instant.parse(modTime).toEpochMilli() }.getOrDefault(0),
            type = if (isDir) SftpEntryType.Directory else SftpEntryType.File
        )
    }

    private fun splitRemotePath(path: String): Pair<String, String> = RclonePathPolicy.splitRemotePath(target, path)

    private fun remotePath(remote: String, uiPath: String): String = RclonePathPolicy.remotePath(remote, uiPath)

    private fun normalizeUiPath(path: String): String = RclonePathPolicy.normalizeUiPath(path)

    private fun safeName(name: String): String = name.substringAfterLast('/')
        .ifBlank { "rclone-file" }
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")

    private suspend fun RcloneClient.copyFileWithProgress(
        srcRemote: String,
        srcPath: String,
        dstRemote: String,
        dstPath: String,
        onProgress: (Float) -> Unit
    ) {
        resetStats()
        onProgress(0f)
        val jobId = copyFileAsync(srcRemote, srcPath, dstRemote, dstPath, transferOptions)
        while (true) {
            val stats = runCatching { getStats() }.getOrNull()
            val status = getJobStatus(jobId)
            onProgress(
                when {
                    status.finished && status.success -> 1f
                    stats != null -> RcloneTransferProgressPolicy.progress(stats).coerceIn(0f, 0.99f)
                    else -> 0f
                }
            )
            if (status.finished) {
                if (!status.success) {
                    throw SshFailure.Network(status.error ?: stats?.lastError?.takeIf { it.isNotBlank() } ?: "rclone copy failed.")
                }
                return
            }
            delay(250L)
        }
    }

    companion object {
        suspend fun open(context: Context, profile: ServerProfile, downloadDir: File): SftpClient = withContext(Dispatchers.IO) {
            val client = RcloneClient(context)
            client.initialize()
            RcloneFileShareClient(
                client = client,
                target = RcloneTargetPolicy.from(profile),
                downloadDir = downloadDir.apply { mkdirs() },
                transferOptions = RcloneTransferOptions(
                    transferConcurrency = profile.fileConfig.transferConcurrency,
                    verifyChecksums = profile.fileConfig.verifyChecksums,
                    resumeTransfers = profile.fileConfig.resumeTransfers
                )
            )
        }
    }
}

internal object RcloneTransferProgressPolicy {
    fun progress(stats: com.chrono.ssh.core.rclone.TransferStats): Float {
        return when {
            stats.totalBytes > 0L -> stats.fraction
            stats.totalTransfers > 0 -> (stats.transfers.toFloat() / stats.totalTransfers.toFloat()).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}
