package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class SmbTargetPolicyTest {
    @Test
    fun parsesShareAndDomainUserFromUncStartDirectory() {
        val target = SmbTargetPolicy.from(
            profile(startDirectory = "//nas/media/movies", username = "WORKGROUP\\alice")
        )

        assertEquals("nas", target.host)
        assertEquals(445, target.port)
        assertEquals("media", target.share)
        assertEquals("/movies", target.initialPath)
        assertEquals("WORKGROUP", target.domain)
        assertEquals("alice", target.username)
    }

    @Test
    fun parsesSmbUrlAndExplicitPort() {
        val target = SmbTargetPolicy.from(
            profile(startDirectory = "smb://files/docs/team", username = "alice", port = 1445)
        )

        assertEquals("files", target.host)
        assertEquals(1445, target.port)
        assertEquals("docs", target.share)
        assertEquals("/team", target.initialPath)
    }

    @Test(expected = SshFailure.Unsupported::class)
    fun missingShareIsRejected() {
        SmbTargetPolicy.from(profile(startDirectory = "//nas"))
    }

    private fun profile(startDirectory: String, username: String = "user", port: Int = 445) = ServerProfile(
        id = "server-1",
        name = "NAS",
        host = "nas",
        port = port,
        username = username,
        group = "Files",
        tags = emptyList(),
        osName = "Windows",
        osVersion = "",
        accent = ServerAccent("Blue", 0xff0000ff),
        credentialId = "cred-1",
        terminalProfileId = "terminal-default",
        monitoringConfig = MonitoringConfig(enabled = false, pollIntervalSeconds = 30, useOptionalAgent = false),
        startDirectory = startDirectory,
        protocol = ConnectionProtocol.Smb
    )
}
