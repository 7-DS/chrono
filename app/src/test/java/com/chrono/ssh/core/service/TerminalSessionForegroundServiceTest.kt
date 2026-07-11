package com.chrono.ssh.core.service

import android.content.Intent
import android.app.Service
import com.chrono.ssh.ui.foregroundServiceConnectionCount
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalSessionForegroundServiceTest {
    @Test
    fun notificationReopensExistingAppTask() {
        val flags = TerminalSessionForegroundService.notificationActivityFlags()

        assertTrue(flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun registryTracksLiveSessionByWorkspace() {
        val session = RegistryFakeSshSession("s1", "server1")

        TerminalSessionRegistry.clear()
        TerminalSessionRegistry.attach("workspace1", session)

        assertTrue(TerminalSessionRegistry.activeCount() == 1)
        assertTrue(TerminalSessionRegistry.session("workspace1") === session)

        TerminalSessionRegistry.detach("workspace1", session)

        assertTrue(TerminalSessionRegistry.activeCount() == 0)
    }

    @Test
    fun registryPublishesActiveCountChanges() {
        val session = RegistryFakeSshSession("s1", "server1")

        TerminalSessionRegistry.clear()
        assertEquals(0, TerminalSessionRegistry.activeCountFlow.value)

        TerminalSessionRegistry.attach("workspace1", session)
        assertEquals(1, TerminalSessionRegistry.activeCountFlow.value)

        TerminalSessionRegistry.detach("workspace1", session)
        assertEquals(0, TerminalSessionRegistry.activeCountFlow.value)

        TerminalSessionRegistry.attach("workspace1", session)
        TerminalSessionRegistry.clear()
        assertEquals(0, TerminalSessionRegistry.activeCountFlow.value)
    }

    @Test
    fun registryPrunesDisconnectedSessionsFromActiveCount() {
        val session = RegistryFakeSshSession("s1", "server1")

        TerminalSessionRegistry.clear()
        TerminalSessionRegistry.attach("workspace1", session)
        session.connected = false

        assertEquals(0, TerminalSessionRegistry.activeCount())
        assertEquals(0, TerminalSessionRegistry.activeCountFlow.value)
        assertEquals(null, TerminalSessionRegistry.session("workspace1"))
        assertEquals(emptyList<TerminalSessionRegistrySnapshot>(), TerminalSessionRegistry.snapshots())
    }

    @Test
    fun registrySnapshotsExposeLiveSessionMetadata() {
        val session = RegistryFakeSshSession("s1", "server1", "preview")

        TerminalSessionRegistry.clear()
        TerminalSessionRegistry.attach("workspace1", session)

        val snapshot = TerminalSessionRegistry.snapshots().single()
        assertEquals("workspace1", snapshot.workspaceId)
        assertEquals("s1", snapshot.sessionId)
        assertEquals("server1", snapshot.serverId)
        assertEquals("preview", snapshot.transcriptPreview)
        assertTrue(snapshot.attachedAtEpochMillis > 0L)
        assertTrue(snapshot.lastSeenEpochMillis >= snapshot.attachedAtEpochMillis)

        TerminalSessionRegistry.clear()
    }

    @Test
    fun serviceCountKeepsRegisteredSessionsAliveDuringUiRecreation() {
        assertEquals(2, foregroundServiceConnectionCount(activeConnectionCount = 0, registeredTerminalSessionCount = 2))
        assertEquals(3, foregroundServiceConnectionCount(activeConnectionCount = 3, registeredTerminalSessionCount = 1))
    }

    @Test
    fun startCommandUsesRealConnectionCountOnly() {
        assertEquals(0, TerminalSessionForegroundService.startCommandConnectionCount(null, registeredTerminalSessionCount = 0))
        assertEquals(0, TerminalSessionForegroundService.startCommandConnectionCount(-1, registeredTerminalSessionCount = -2))
        assertEquals(2, TerminalSessionForegroundService.startCommandConnectionCount(null, registeredTerminalSessionCount = 2))
        assertEquals(3, TerminalSessionForegroundService.startCommandConnectionCount(3, registeredTerminalSessionCount = 1))
    }

    @Test
    fun staleTerminalOnlyStartCommandDoesNotFakeRestartAfterRegistryEmpty() {
        assertEquals(
            0,
            TerminalSessionForegroundService.startCommandConnectionCount(
                intentConnectionCount = 1,
                intentNonTerminalConnectionCount = 0,
                registeredTerminalSessionCount = 0
            )
        )
    }

    @Test
    fun liveRegistrySessionsSurviveStaleIntentCount() {
        assertEquals(
            2,
            TerminalSessionForegroundService.startCommandConnectionCount(
                intentConnectionCount = 1,
                intentNonTerminalConnectionCount = 0,
                registeredTerminalSessionCount = 2
            )
        )
    }

    @Test
    fun staleTerminalStartCommandStillKeepsLiveNonTerminalWork() {
        assertEquals(
            1,
            TerminalSessionForegroundService.startCommandConnectionCount(
                intentConnectionCount = 2,
                intentNonTerminalConnectionCount = 1,
                registeredTerminalSessionCount = 0
            )
        )
    }

    @Test
    fun foregroundServiceRestartsOnlyWhileConnectionsExist() {
        assertEquals(Service.START_NOT_STICKY, TerminalSessionForegroundService.serviceRestartMode(0))
        assertEquals(Service.START_STICKY, TerminalSessionForegroundService.serviceRestartMode(1))
    }
}

private class RegistryFakeSshSession(
    override val id: String,
    override val serverId: String,
    override val transcriptPreview: String = ""
) : SshSession {
    var connected: Boolean = true
    override val isConnected: Boolean get() = connected
    override suspend fun execute(command: String, timeoutSeconds: Long): CommandResult = CommandResult(command, 0, "", "")
    override suspend fun resizeTerminal(columns: Int, rows: Int) = Unit
    override suspend fun writeTerminal(input: String) = Unit
    override fun setTerminalOutputSink(onData: (ByteArray) -> Unit) = Unit
    override suspend fun close() = Unit
}
