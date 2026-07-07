package com.chrono.ssh.core.data

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.ReconnectPolicy
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostDeletionPolicyTest {
    @Test
    fun removesDeletedServerAndClearsProxyJumpReferences() {
        val target = server("target", proxyJumpHostId = "jump")
        val jump = server("jump")
        val other = server("other")

        val retained = HostDeletionPolicy.removeServerAndProxyJumpReferences(
            listOf(target, jump, other),
            deletedServerId = "jump"
        )

        assertEquals(listOf("target", "other"), retained.map { it.id })
        assertNull(retained.first { it.id == "target" }.proxyJumpHostId)
    }

    private fun server(id: String, proxyJumpHostId: String? = null): ServerProfile {
        return ServerProfile(
            id = id,
            name = id,
            host = "$id.example.test",
            port = 22,
            username = "user",
            group = "All",
            tags = listOf("All"),
            osName = "Linux",
            osVersion = "",
            accent = ServerAccent("test", 0),
            credentialId = null,
            terminalProfileId = "term-default",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false),
            reconnectPolicy = ReconnectPolicy(),
            proxyJumpHostId = proxyJumpHostId
        )
    }
}
