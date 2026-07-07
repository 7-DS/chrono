package com.chrono.ssh.core.service.rclone

import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.rclone.TransferStats
import com.chrono.ssh.core.rclone.RcloneTransferOptions
import com.chrono.ssh.core.rclone.transferConfigEntries
import org.junit.Assert.assertEquals
import org.junit.Test

class RcloneFileShareClientTest {
    @Test
    fun pathPolicySplitsRootBrowserPathsByRemote() {
        assertEquals("drive" to "docs/a.txt", RclonePathPolicy.splitRemotePath(RcloneTarget(null, "/"), "/drive/docs/a.txt"))
        assertEquals("backup" to "archive/a.txt", RclonePathPolicy.splitRemotePath(RcloneTarget(null, "/"), "/backup/archive/a.txt"))
    }

    @Test
    fun pathPolicyKeepsConfiguredRemoteScoped() {
        assertEquals("drive" to "docs/a.txt", RclonePathPolicy.splitRemotePath(RcloneTarget("drive", "/"), "/docs/a.txt"))
        assertEquals("drive" to "backup/archive/a.txt", RclonePathPolicy.splitRemotePath(RcloneTarget("drive", "/"), "/backup/archive/a.txt"))
    }

    @Test
    fun targetPolicyParsesUrlRemoteAndRootPath() {
        assertEquals(RcloneTarget("drive", "/"), RcloneTargetPolicy.from(profile("rclone://drive:")))
        assertEquals(RcloneTarget("drive", "/docs"), RcloneTargetPolicy.from(profile("rclone://drive:/docs")))
    }

    @Test
    fun targetPolicyTreatsBlankStartDirectoryAsRemoteBrowserRoot() {
        assertEquals(RcloneTarget(null, "/"), RcloneTargetPolicy.from(profile(" ")))
        assertEquals("/", RclonePathPolicy.normalizeUiPath("."))
    }

    @Test
    fun progressPolicyUsesStatsFraction() {
        assertEquals(
            0.25f,
            RcloneTransferProgressPolicy.progress(TransferStats(bytes = 25, totalBytes = 100, speed = 0.0, transfers = 1, totalTransfers = 1, errors = 0)),
            0.0001f
        )
        assertEquals(
            1f,
            RcloneTransferProgressPolicy.progress(TransferStats(bytes = 25, totalBytes = 0, speed = 0.0, transfers = 1, totalTransfers = 1, errors = 0)),
            0.0001f
        )
        assertEquals(
            0.25f,
            RcloneTransferProgressPolicy.progress(TransferStats(bytes = 0, totalBytes = 0, speed = 0.0, transfers = 1, totalTransfers = 4, errors = 0)),
            0.0001f
        )
        assertEquals(
            1f,
            RcloneTransferProgressPolicy.progress(TransferStats(bytes = 0, totalBytes = 0, speed = 0.0, transfers = 5, totalTransfers = 4, errors = 0)),
            0.0001f
        )
    }

    @Test
    fun transferOptionsClampConcurrencyAndEnableChecksum() {
        val entries = RcloneTransferOptions(transferConcurrency = 99, verifyChecksums = true).transferConfigEntries()

        assertEquals(mapOf("Transfers" to 8, "Checksum" to true, "NoTraverse" to true), entries)
    }

    @Test
    fun transferOptionsCanDisableResumeOptimization() {
        val entries = RcloneTransferOptions(resumeTransfers = false).transferConfigEntries()

        assertEquals(mapOf("Transfers" to 2), entries)
    }

    private fun profile(startDirectory: String) = ServerProfile(
        id = "server-1",
        name = "Rclone",
        host = "localhost",
        port = 0,
        username = "",
        group = "Files",
        tags = emptyList(),
        osName = "Local",
        osVersion = "",
        accent = ServerAccent("Blue", 0xff0000ff),
        credentialId = null,
        terminalProfileId = "terminal-default",
        monitoringConfig = MonitoringConfig(enabled = false, pollIntervalSeconds = 30, useOptionalAgent = false),
        startDirectory = startDirectory,
        protocol = ConnectionProtocol.Rclone
    )
}
