package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.ProotProfileConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class LocalProotRuntimeTest {
    @Test
    fun androidShellFallbackQuotesHomePath() {
        val command = LocalShellCommand(
            cmd = "/system/bin/sh",
            args = arrayOf("/system/bin/sh", "-c", "cd ${shellQuoteForTerminalStartup("/tmp/chrono ssh")} 2>/dev/null; exec /system/bin/sh -l"),
            env = LocalShellSession.localShellEnvironment("local", "/tmp/chrono ssh", "/tmp"),
            prootBacked = false
        )

        assertFalse(command.prootBacked)
        assertEquals("cd '/tmp/chrono ssh' 2>/dev/null; exec /system/bin/sh -l", command.args[2])
        assertEquals("HOME=/tmp/chrono ssh", command.env.first { it.startsWith("HOME=") })
    }

    @Test
    fun rootfsRequiresReadyMarkerAndShell() {
        val rootPath = createTempDirectory(prefix = "chronossh-rootfs")
        val root = rootPath.toFile()
        try {
            File(root, "bin").mkdirs()
            File(root, "bin/sh").writeText("#!/bin/sh\n")

            assertFalse(LocalProotRuntime.isRootfsReady(root))
            assertEquals(false, LocalProotRuntime.rootfsStatus(root).ready)
            assertEquals(true, LocalProotRuntime.rootfsStatus(root).hasShell)
            assertEquals(false, LocalProotRuntime.rootfsStatus(root).readyMarker)

            File(root, ".chronossh-rootfs-ready").writeText("ready\n")
            assertEquals(true, LocalProotRuntime.isRootfsReady(root))
            assertEquals(true, LocalProotRuntime.rootfsStatus(root).ready)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun configuredRootfsPathWinsWhenAbsoluteAndSafe() {
        val root = createTempDirectory(prefix = "chronossh-rootfs").toFile()
        try {
            val managed = File(root, "managed")
            val custom = File(root, "custom rootfs")

            assertEquals(
                custom.canonicalFile,
                LocalProotRuntime.resolveRootfsDir(managed, ProotProfileConfig(rootfsPath = " ${custom.absolutePath} "))
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun unsafeRootfsPathFallsBackToManagedDistro() {
        val root = createTempDirectory(prefix = "chronossh-rootfs").toFile()
        try {
            val managed = File(root, "managed")

            assertEquals(
                File(managed, "alpine-3.21"),
                LocalProotRuntime.resolveRootfsDir(managed, ProotProfileConfig(distroId = "../escape", rootfsPath = "../rootfs"))
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nonDefaultDistroUsesSeparateManagedRootfs() {
        val root = createTempDirectory(prefix = "chronossh-rootfs").toFile()
        try {
            val managed = File(root, "managed")

            val defaultRoot = LocalProotRuntime.resolveRootfsDir(managed, ProotProfileConfig(distroId = "alpine-3.21"))
            val debianRoot = LocalProotRuntime.resolveRootfsDir(managed, ProotProfileConfig(distroId = "debian-12"))

            assertEquals(File(managed, "alpine-3.21"), defaultRoot)
            assertEquals(File(managed, "debian-12"), debianRoot)
            assertFalse(defaultRoot == debianRoot)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun catalogEntriesAreManagedAndChecksumBacked() {
        val entries = LocalProotRuntime.RootfsCatalog

        assertTrue(entries.isNotEmpty())
        entries.forEach { entry ->
            assertTrue(entry.id.startsWith("alpine-"))
            assertTrue(entry.url.startsWith("https://dl-cdn.alpinelinux.org/alpine/"))
            assertEquals(64, entry.sha256.length)
            assertTrue(entry.sha256.all { it in '0'..'9' || it in 'a'..'f' })
            assertTrue(entry.sizeBytes > 0)
        }
    }
}
