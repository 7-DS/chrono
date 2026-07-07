package com.chrono.ssh.core.service

import android.content.Context
import android.system.Os
import com.chrono.ssh.core.model.ProotProfileConfig
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.GZIPInputStream

data class ProotRootfsCatalogEntry(
    val id: String,
    val label: String,
    val arch: String,
    val version: String,
    val url: String,
    val sha256: String,
    val sizeBytes: Long
)

data class LocalShellCommand(
    val cmd: String,
    val args: Array<String>,
    val env: Array<String>,
    val prootBacked: Boolean
)

data class LocalProotRootfsStatus(
    val path: String,
    val hasShell: Boolean,
    val readyMarker: Boolean
) {
    val ready: Boolean get() = hasShell && readyMarker
}

object LocalProotRuntime {
    const val DefaultDistroId = "alpine-3.21"
    private const val ReadyMarker = ".chronossh-rootfs-ready"
    val RootfsCatalog: List<ProotRootfsCatalogEntry> = listOf(
        ProotRootfsCatalogEntry(
            id = "alpine-3.24-aarch64",
            label = "Alpine 3.24.1",
            arch = "aarch64",
            version = "3.24.1",
            url = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.1-aarch64.tar.gz",
            sha256 = "f55a90f69052c5bd6f92cb09a8f47065970830b194c917a006fb94028e721259",
            sizeBytes = 4_023_732L
        ),
        ProotRootfsCatalogEntry(
            id = "alpine-3.24-x86_64",
            label = "Alpine 3.24.1",
            arch = "x86_64",
            version = "3.24.1",
            url = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/x86_64/alpine-minirootfs-3.24.1-x86_64.tar.gz",
            sha256 = "41f73e3cf5fa919b8aa5ca6b30dc48f0da2720776d7423e2a7748211456fe081",
            sizeBytes = 3_698_422L
        )
    )

    fun command(
        context: Context,
        label: String,
        config: ProotProfileConfig = ProotProfileConfig()
    ): LocalShellCommand {
        val proot = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
        val loader = File(context.applicationInfo.nativeLibraryDir, "libproot_loader.so")
        val rootfs = rootfsDir(context, config)
        return if (proot.canExecute() && loader.exists() && isRootfsReady(rootfs)) {
            prootCommand(context, proot, loader, rootfs, label, config.mountHome)
        } else {
            androidShellCommand(context, label)
        }
    }

    fun rootfsDir(context: Context, distroId: String = DefaultDistroId): File =
        File(File(context.filesDir, "proot/rootfs"), distroId.safeRootfsName())

    fun rootfsDir(context: Context, config: ProotProfileConfig): File =
        resolveRootfsDir(File(context.filesDir, "proot/rootfs"), config)

    internal fun resolveRootfsDir(managedRoot: File, config: ProotProfileConfig): File {
        val configured = config.rootfsPath.trim()
        val customRootfs = configured.takeIf { it.isNotBlank() }?.let(::File)
        return if (customRootfs?.isSafeCustomRootfsPath() == true) {
            runCatching { customRootfs.canonicalFile }.getOrElse { customRootfs.absoluteFile.normalizeSegments() }
        } else {
            File(managedRoot, config.distroId.safeRootfsName())
        }
    }

    fun rootfsStatus(rootfs: File): LocalProotRootfsStatus =
        LocalProotRootfsStatus(
            path = rootfs.absolutePath,
            hasShell = File(rootfs, "bin/sh").isFile,
            readyMarker = File(rootfs, ReadyMarker).isFile
        )

    fun rootfsStatus(context: Context, config: ProotProfileConfig = ProotProfileConfig()): LocalProotRootfsStatus =
        rootfsStatus(rootfsDir(context, config))

    fun isRootfsReady(rootfs: File): Boolean = rootfsStatus(rootfs).ready

    fun installRootfsArchive(
        context: Context,
        archiveName: String,
        input: InputStream,
        distroId: String = DefaultDistroId
    ): String {
        val safeDistroId = distroId.safeRootfsName()
        val rootfs = rootfsDir(context, distroId)
        val staging = File(rootfs.parentFile, "$safeDistroId.installing")
        staging.deleteRecursively()
        staging.mkdirs()
        runCatching {
            input.use { raw -> extractTar(raw.archiveStream(archiveName), staging) }
            val payloadRoot = collapseSingleDirectory(staging)
            if (!File(payloadRoot, "bin/sh").isFile) {
                error("Rootfs archive must contain bin/sh.")
            }
            File(payloadRoot, "etc").mkdirs()
            File(payloadRoot, "etc/resolv.conf").takeIf { !it.exists() }?.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
            File(payloadRoot, ReadyMarker).writeText("ready\n")
            rootfs.deleteRecursively()
            rootfs.parentFile?.mkdirs()
            if (!payloadRoot.renameTo(rootfs)) {
                payloadRoot.copyRecursively(rootfs, overwrite = true)
                payloadRoot.deleteRecursively()
            }
        }.onFailure { failure ->
            staging.deleteRecursively()
            throw failure
        }
        staging.deleteRecursively()
        return rootfs.absolutePath
    }

    fun installCatalogRootfs(
        context: Context,
        entry: ProotRootfsCatalogEntry,
        onProgress: (Float) -> Unit = {}
    ): String {
        require(entry in RootfsCatalog) { "Unknown PRoot catalog entry." }
        val cacheFile = File(context.cacheDir, "proot-${entry.id}.tar.gz")
        cacheFile.parentFile?.mkdirs()
        cacheFile.outputStream().use { output ->
            val connection = URL(entry.url).openConnection()
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            val expectedBytes = connection.contentLengthLong.takeIf { it > 0 } ?: entry.sizeBytes
            connection.getInputStream().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (expectedBytes > 0L) onProgress((copied.toFloat() / expectedBytes.toFloat()).coerceIn(0f, 0.95f))
                }
            }
        }
        val digest = cacheFile.sha256Hex()
        check(digest.equals(entry.sha256, ignoreCase = true)) {
            "Rootfs checksum mismatch for ${entry.label} ${entry.arch}."
        }
        return cacheFile.inputStream().use { input ->
            installRootfsArchive(context, cacheFile.name, input, entry.id).also { onProgress(1f) }
        }
    }

    fun clearRootfs(context: Context, distroId: String = DefaultDistroId) {
        rootfsDir(context, distroId).deleteRecursively()
    }

    private fun prootCommand(
        context: Context,
        proot: File,
        loader: File,
        rootfs: File,
        label: String,
        mountHome: Boolean
    ): LocalShellCommand {
        val devShm = File(context.cacheDir, "proot-dev-shm").apply { mkdirs() }
        val resolvConf = File(rootfs, "etc/resolv.conf")
        if (!resolvConf.exists() || resolvConf.length() == 0L) {
            resolvConf.parentFile?.mkdirs()
            resolvConf.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\n")
        }
        val homeBind = if (mountHome) arrayOf("-b", "${context.filesDir.absolutePath}:/root/chronoSSH") else emptyArray()
        return LocalShellCommand(
            cmd = proot.absolutePath,
            args = arrayOf(
                proot.absolutePath,
                "-0",
                "--link2symlink",
                "-r", rootfs.absolutePath,
                "-b", "/dev",
                "-b", "/dev/urandom:/dev/random",
                "-b", "${devShm.absolutePath}:/dev/shm",
                "-b", "/proc/self/fd:/dev/fd",
                "-b", "/proc/self/fd/0:/dev/stdin",
                "-b", "/proc/self/fd/1:/dev/stdout",
                "-b", "/proc/self/fd/2:/dev/stderr",
                "-b", "/proc",
                "-b", "/sys",
                "-b", "/storage",
                "-b", "/storage/emulated/0:/sdcard",
                "-b", "${context.cacheDir.absolutePath}:/tmp",
                *homeBind,
                "-w", "/root",
                "/bin/sh",
                "-l"
            ),
            env = arrayOf(
                "HOME=/root",
                "USER=root",
                "TERM=xterm-256color",
                "LANG=en_US.UTF-8",
                "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
                "SHELL=/bin/sh",
                "XDG_RUNTIME_DIR=/tmp",
                "XDG_DATA_HOME=/root/.local/share",
                "XDG_DATA_DIRS=/usr/local/share:/usr/share",
                "XDG_CONFIG_HOME=/root/.config",
                "XDG_CACHE_HOME=/root/.cache",
                "PROOT_TMP_DIR=${context.cacheDir.absolutePath}",
                "PROOT_LOADER=${loader.absolutePath}",
                "CHRONOSSH_SESSION=$label"
            ),
            prootBacked = true
        )
    }

    fun androidShellCommand(context: Context, label: String): LocalShellCommand {
        val shell = LocalShellSession.resolveShell()
        val home = context.filesDir.absolutePath
        return LocalShellCommand(
            cmd = shell,
            args = arrayOf(shell, "-c", "cd ${shellQuoteForTerminalStartup(home)} 2>/dev/null; exec $shell -l"),
            env = LocalShellSession.localShellEnvironment(label, home, context.cacheDir.absolutePath),
            prootBacked = false
        )
    }

    private fun InputStream.archiveStream(name: String): InputStream {
        val buffered = if (this.markSupported()) this else BufferedInputStream(this)
        val lower = name.lowercase(Locale.US)
        return if (lower.endsWith(".gz") || lower.endsWith(".tgz")) GZIPInputStream(buffered) else buffered
    }

    private fun extractTar(input: InputStream, target: File) {
        val header = ByteArray(512)
        while (true) {
            val read = input.readFullBlock(header)
            if (read == 0 || header.all { it == 0.toByte() }) return
            if (read != 512) error("Invalid tar header.")
            val name = tarString(header, 0, 100)
            val prefix = tarString(header, 345, 155)
            val path = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
            val size = tarOctal(header, 124, 12)
            val mode = tarOctal(header, 100, 8).toInt()
            val type = header[156].toInt().toChar()
            val linkTarget = tarString(header, 157, 100)
            val output = safeOutputFile(target, path)
            when (type) {
                '5' -> output.mkdirs()
                '2' -> {
                    output.parentFile?.mkdirs()
                    runCatching { Os.symlink(linkTarget, output.absolutePath) }
                }
                else -> {
                    output.parentFile?.mkdirs()
                    output.outputStream().use { out -> input.copyExactly(out, size) }
                    output.applyMode(mode)
                }
            }
            val padding = (512 - (size % 512)) % 512
            input.skipExactly(padding)
        }
    }

    private fun safeOutputFile(root: File, path: String): File {
        val normalized = path.removePrefix("./")
        require(normalized.isNotBlank() && !normalized.startsWith("/") && !normalized.split('/').contains("..")) {
            "Unsafe tar entry: $path"
        }
        return File(root, normalized)
    }

    private fun String.safeRootfsName(): String =
        trim().takeIf { name ->
            name.isNotBlank() && name.length <= 64 && name.all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
        } ?: DefaultDistroId

    private fun File.isSafeCustomRootfsPath(): Boolean =
        isAbsolute && path.split(File.separatorChar).none { it == ".." }

    private fun File.normalizeSegments(): File =
        File(path.split(File.separatorChar).filter { it.isNotBlank() && it != "." }.joinToString(File.separator, prefix = File.separator))

    private fun collapseSingleDirectory(staging: File): File {
        val entries = staging.listFiles().orEmpty().filterNot { it.name == ReadyMarker }
        return entries.singleOrNull()?.takeIf { it.isDirectory && File(it, "bin/sh").isFile } ?: staging
    }

    private fun tarString(header: ByteArray, offset: Int, length: Int): String {
        val end = (offset until offset + length).firstOrNull { header[it] == 0.toByte() } ?: (offset + length)
        return header.copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun tarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val raw = tarString(header, offset, length).trim()
        return raw.ifBlank { "0" }.toLong(8)
    }

    private fun InputStream.readFullBlock(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) return total
            total += read
        }
        return total
    }

    private fun InputStream.copyExactly(out: java.io.OutputStream, byteCount: Long) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var remaining = byteCount
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) error("Unexpected end of tar entry.")
            out.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.skipExactly(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0) {
            val skipped = skip(remaining)
            if (skipped <= 0) {
                if (read() < 0) error("Unexpected end of tar padding.")
                remaining--
            } else {
                remaining -= skipped
            }
        }
    }

    private fun File.applyMode(mode: Int) {
        setReadable(true, false)
        setWritable(mode and 0b010_000_000 != 0, true)
        setExecutable(mode and 0b001_001_001 != 0, false)
    }

    private fun File.sha256Hex(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
