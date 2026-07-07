package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.service.ForwardStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionsScreenTest {
    @Test
    fun connectionStatusLabelKeepsRecoverableWorkspacesVisible() {
        assertEquals("Active", connectionStatusLabel("Idle", connected = true))
        assertEquals("Starting", connectionStatusLabel("Connecting", connected = false))
        assertEquals("Starting", connectionStatusLabel("Opening SSH session", connected = false))
        assertEquals("Passphrase", connectionStatusLabel("Passphrase required", connected = false))
        assertEquals("Review", connectionStatusLabel("Review host key", connected = false))
        assertEquals("Failed", connectionStatusLabel("Failed: auth rejected", connected = false))
        assertEquals("Saved", connectionStatusLabel("Shell", connected = false))
        assertEquals("Saved", connectionStatusLabel("Ctrl latched", connected = false))
        assertEquals("Saved", connectionStatusLabel("Idle", connected = false))
        assertEquals("Saved", connectionStatusLabel("Closed", connected = false))
    }

    @Test
    fun connectionSummaryLabelShowsConnectedAndWorkspaceCounts() {
        assertEquals("1 active · 1 terminal", connectionSummaryLabel(1, 1))
        assertEquals("0 active · 2 terminals", connectionSummaryLabel(0, 2))
    }

    @Test
    fun activeTerminalConnectionCardsKeepOnlyConnectedKnownServers() {
        val server = server()
        val deletedServerWorkspace = TerminalWorkspaceState("deleted", { error("engine should be lazy") }).apply {
            session = ThrowingSshSession()
        }
        val connectedWorkspace = TerminalWorkspaceState(server.id, { error("engine should be lazy") }).apply {
            session = ThrowingSshSession()
        }
        val savedWorkspace = TerminalWorkspaceState(server.id, { error("engine should be lazy") })

        val cards = activeTerminalConnectionCards(
            servers = listOf(server),
            workspaces = listOf(
                "deleted" to deletedServerWorkspace,
                "saved" to savedWorkspace,
                "active" to connectedWorkspace
            )
        )

        assertEquals(listOf("active"), cards.map { it.first })
    }

    @Test
    fun sftpConnectionCardKeepsHostNamePrimary() {
        assertEquals("Box", sftpConnectionTitle("Box"))
    }

    @Test
    fun connectionStatusToneMatchesPolishedLabels() {
        assertEquals(ConnectionStatusTone.Active, connectionStatusTone("Active", connected = true, snapshotStatus = null))
        assertEquals(ConnectionStatusTone.Starting, connectionStatusTone("Starting", connected = false, snapshotStatus = null))
        assertEquals(ConnectionStatusTone.Starting, connectionStatusTone("Passphrase", connected = false, snapshotStatus = null))
        assertEquals(ConnectionStatusTone.Starting, connectionStatusTone("Review", connected = false, snapshotStatus = null))
        assertEquals(ConnectionStatusTone.Failed, connectionStatusTone("Failed", connected = false, snapshotStatus = ServerStatus.Online))
        assertEquals(ConnectionStatusTone.Online, connectionStatusTone("Saved", connected = false, snapshotStatus = ServerStatus.Online))
        assertEquals(ConnectionStatusTone.Offline, connectionStatusTone("Saved", connected = false, snapshotStatus = ServerStatus.Offline))
        assertEquals(ConnectionStatusTone.Idle, connectionStatusTone("Saved", connected = false, snapshotStatus = null))
    }

    @Test
    fun connectionTunnelSummariesShowSavedRuntimeAndFailedTunnels() {
        val server = server()
        val active = forward("active", PortForwardType.Local).copy(label = "Database", group = "Prod", favorite = true)
        val failed = forward("failed", PortForwardType.DynamicSocks)
        val stopped = forward("stopped", PortForwardType.Remote)

        val summaries = connectionTunnelSummaries(
            servers = listOf(server),
            forwards = listOf(stopped, failed, active),
            statuses = mapOf(
                active.id to ForwardStatus(active.id, "127.0.0.1:8080", active = true, lastMessage = "Listening"),
                failed.id to ForwardStatus(failed.id, "127.0.0.1:1080", active = false, lastMessage = "Failed", lastError = "Port busy"),
                stopped.id to ForwardStatus(stopped.id, "127.0.0.1:18080", active = false, lastMessage = "Stopped")
            )
        )

        assertEquals(3, summaries.size)
        assertEquals("1 active · 3 tunnels", connectionTunnelSummaryLabel(summaries))
        assertEquals("Active", summaries[0].statusLabel)
        assertEquals("Database", summaries[0].titleLabel)
        assertEquals("Prod", summaries[0].groupLabel)
        assertTrue(summaries[0].favorite)
        assertEquals("127.0.0.1:8080 -> 127.0.0.1:22", summaries[0].routeLabel)
        assertEquals("Failed", summaries[1].statusLabel)
        assertEquals("127.0.0.1:1080 SOCKS5", summaries[1].routeLabel)
        assertEquals("Stopped", summaries[2].statusLabel)
        assertEquals(stopped, summaries[2].rule)
    }

    @Test
    fun tunnelErrorsHideRawSocketDetails() {
        assertEquals(
            "Connection interrupted. Reconnect to continue.",
            connectionUserFacingTunnelError("SSH exec connect failed for 185.163.118.38:22: failed to connect to /192.0.0.2")
        )
        assertEquals(
            "Permission denied",
            connectionUserFacingTunnelError("Permission denied")
        )
    }

    @Test
    fun connectionTunnelSummariesSortFavoritesWithinStatus() {
        val server = server()
        val plain = forward("plain", PortForwardType.Local)
        val favorite = forward("favorite", PortForwardType.Local).copy(label = "Favorite", group = "Ops", favorite = true)

        val summaries = connectionTunnelSummaries(
            servers = listOf(server),
            forwards = listOf(plain, favorite),
            statuses = emptyMap()
        )

        assertEquals(listOf("favorite", "plain"), summaries.map { it.id })
        assertEquals("Local", summaries[1].titleLabel)
    }

    @Test
    fun connectionTunnelSummariesKeepNonActiveTunnelsVisible() {
        val server = server()
        val active = forward("active", PortForwardType.Local)
        val failed = forward("failed", PortForwardType.DynamicSocks)
        val stopped = forward("stopped", PortForwardType.Remote)
        val starting = forward("starting", PortForwardType.Local)
        val summaries = connectionTunnelSummaries(
            servers = listOf(server),
            forwards = listOf(stopped, failed, active, starting),
            statuses = mapOf(
                active.id to ForwardStatus(active.id, "127.0.0.1:8080", active = true, lastMessage = "Listening"),
                failed.id to ForwardStatus(failed.id, "127.0.0.1:1080", active = false, lastMessage = "Failed", lastError = "Port busy"),
                stopped.id to ForwardStatus(stopped.id, "127.0.0.1:18080", active = false, lastMessage = "Stopped"),
                starting.id to ForwardStatus(starting.id, "127.0.0.1:9090", active = false, lastMessage = "Starting")
            )
        )

        assertEquals(listOf("active", "failed", "starting", "stopped"), summaries.map { it.id })
        assertEquals(listOf("Active", "Failed", "Starting", "Stopped"), summaries.map { it.statusLabel })
    }

    private fun server() = ServerProfile(
        id = "server",
        name = "Box",
        host = "box.test",
        port = 22,
        username = "root",
        group = "Lab",
        tags = listOf("All"),
        osName = "Linux",
        osVersion = "",
        accent = ServerAccent("Blue", 0xff0000ff),
        credentialId = null,
        terminalProfileId = "default",
        monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false)
    )

    private fun forward(id: String, type: PortForwardType) = PortForwardRule(
        id = id,
        serverId = "server",
        type = type,
        localHost = "127.0.0.1",
        localPort = if (type == PortForwardType.DynamicSocks) 1080 else 8080,
        remoteHost = "127.0.0.1",
        remotePort = if (type == PortForwardType.Remote) 18080 else 22,
        enabled = true,
        autoStart = false
    )

    private class ThrowingSshSession : com.chrono.ssh.core.service.SshSession {
        override val id: String = "session"
        override val serverId: String = "server"
        override val transcriptPreview: String = ""
        override suspend fun execute(command: String, timeoutSeconds: Long): com.chrono.ssh.core.service.CommandResult = error("unused")
        override suspend fun writeTerminal(input: String) = error("unused")
        override suspend fun resizeTerminal(columns: Int, rows: Int) = error("unused")
        override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
        override fun setTerminalCloseHandler(onClose: () -> Unit) = Unit
        override suspend fun close() = Unit
    }

}
