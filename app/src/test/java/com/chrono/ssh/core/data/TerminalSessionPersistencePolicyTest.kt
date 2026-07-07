package com.chrono.ssh.core.data

import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.SftpBookmark
import com.chrono.ssh.core.model.TerminalSessionRecord
import com.chrono.ssh.core.model.TransferDirection
import com.chrono.ssh.core.model.TransferRecord
import com.chrono.ssh.core.model.TransferRecordState
import com.chrono.ssh.core.service.ForwardRuntimePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalSessionPersistencePolicyTest {
    @Test
    fun normalizesPersistedLiveStatesToOffline() {
        listOf(ServerStatus.Online, ServerStatus.Connecting).forEach { status ->
            val loaded = TerminalSessionPersistencePolicy.normalizeLoaded(record(status))

            assertEquals(ServerStatus.Offline, loaded.status)
            assertEquals("session", loaded.id)
            assertEquals("server", loaded.serverId)
            assertEquals("Shell", loaded.title)
            assertEquals(100L, loaded.startedAtEpochMillis)
            assertEquals(200L, loaded.lastActiveEpochMillis)
            assertEquals("last command", loaded.transcriptPreview)
        }
    }

    @Test
    fun preservesNonLivePersistedStates() {
        listOf(ServerStatus.Offline, ServerStatus.Unknown).forEach { status ->
            assertEquals(status, TerminalSessionPersistencePolicy.normalizeLoaded(record(status)).status)
        }
    }

    @Test
    fun normalizesSafePersistedTerminalSessionMetadata() {
        val longPreview = "x".repeat(300)
        val loaded = TerminalSessionPersistencePolicy.normalizePersisted(
            record(ServerStatus.Online).copy(
                id = " session-1 ",
                serverId = " server-1 ",
                title = " ",
                startedAtEpochMillis = -1L,
                lastActiveEpochMillis = -2L,
                transcriptPreview = " $longPreview "
            )
        )

        checkNotNull(loaded)
        assertEquals("session-1", loaded.id)
        assertEquals("server-1", loaded.serverId)
        assertEquals("Shell", loaded.title)
        assertEquals(ServerStatus.Offline, loaded.status)
        assertEquals(0L, loaded.startedAtEpochMillis)
        assertEquals(0L, loaded.lastActiveEpochMillis)
        assertEquals(240, loaded.transcriptPreview.length)
        assertEquals("x".repeat(240), loaded.transcriptPreview)
        assertEquals("work", loaded.tmuxSessionName)
        assertEquals(2, loaded.tmuxWindowIndex)
    }

    @Test
    fun dropsInvalidTmuxRestoreMetadata() {
        val blankSession = TerminalSessionPersistencePolicy.normalizePersisted(
            record(ServerStatus.Offline).copy(tmuxSessionName = " ", tmuxWindowIndex = 2)
        )
        val negativeWindow = TerminalSessionPersistencePolicy.normalizePersisted(
            record(ServerStatus.Offline).copy(tmuxSessionName = "main", tmuxWindowIndex = -1)
        )

        checkNotNull(blankSession)
        checkNotNull(negativeWindow)
        assertNull(blankSession.tmuxSessionName)
        assertNull(blankSession.tmuxWindowIndex)
        assertEquals("main", negativeWindow.tmuxSessionName)
        assertNull(negativeWindow.tmuxWindowIndex)
    }

    @Test
    fun rejectsBlankPersistedTerminalSessionIdentity() {
        assertNull(TerminalSessionPersistencePolicy.normalizePersisted(record(ServerStatus.Offline).copy(id = "")))
        assertNull(TerminalSessionPersistencePolicy.normalizePersisted(record(ServerStatus.Offline).copy(serverId = " ")))
    }

    @Test
    fun terminalSessionUpdatePolicyKeepsClosedStateOverStaleLiveUpdate() {
        val closed = record(ServerStatus.Offline).copy(lastActiveEpochMillis = 300L)
        val staleLive = record(ServerStatus.Online).copy(lastActiveEpochMillis = 300L)

        assertEquals(closed, TerminalSessionUpdatePolicy.merge(closed, staleLive))
    }

    @Test
    fun terminalSessionUpdatePolicyAcceptsNewerRuntimeUpdates() {
        val closed = record(ServerStatus.Offline).copy(lastActiveEpochMillis = 300L)
        val newerLive = record(ServerStatus.Online).copy(lastActiveEpochMillis = 301L)

        assertEquals(newerLive, TerminalSessionUpdatePolicy.merge(closed, newerLive))
        assertEquals(closed, TerminalSessionUpdatePolicy.merge(record(ServerStatus.Online).copy(lastActiveEpochMillis = 250L), closed))
    }

    @Test
    fun backupTerminalSessionPolicyNormalizesSafeImportedMetadata() {
        val loaded = BackupTerminalSessionPolicy.sanitizeImportedMetadata(
            record(ServerStatus.Online).copy(
                id = " session-1 ",
                serverId = " server-1 ",
                title = " ",
                startedAtEpochMillis = -1L,
                lastActiveEpochMillis = -2L
            )
        )

        checkNotNull(loaded)
        assertEquals("session-1", loaded.id)
        assertEquals("server-1", loaded.serverId)
        assertEquals("Shell", loaded.title)
        assertEquals(ServerStatus.Offline, loaded.status)
        assertEquals(0L, loaded.startedAtEpochMillis)
        assertEquals(0L, loaded.lastActiveEpochMillis)
    }

    @Test
    fun backupTerminalSessionPolicyRejectsBlankIdentityFields() {
        assertNull(BackupTerminalSessionPolicy.sanitizeImportedMetadata(record(ServerStatus.Offline).copy(id = "")))
        assertNull(BackupTerminalSessionPolicy.sanitizeImportedMetadata(record(ServerStatus.Offline).copy(serverId = " ")))
    }

    @Test
    fun normalizesPersistedForwardsAsStopped() {
        val loaded = ForwardPersistencePolicy.normalizeLoaded(
            forward(enabled = true, autoStart = true, localHost = "", remoteHost = "").copy(label = "  DB  ", group = " Ops ", favorite = true)
        )

        checkNotNull(loaded)
        assertFalse(loaded.enabled)
        assertTrue(loaded.autoStart)
        assertEquals("127.0.0.1", loaded.localHost)
        assertEquals("127.0.0.1", loaded.remoteHost)
        assertEquals("DB", loaded.label)
        assertEquals("Ops", loaded.group)
        assertTrue(loaded.favorite)
    }

    @Test
    fun rejectsInvalidPersistedForwards() {
        val loaded = ForwardPersistencePolicy.normalizeLoaded(
            forward(remoteHost = "https://db.example.test;rm")
        )

        assertNull(loaded)
    }

    @Test
    fun rejectsUnknownPersistedForwardTypes() {
        assertNull(ForwardPersistencePolicy.typeFromPersisted("LegacyTunnel"))
        assertEquals(PortForwardType.Remote, ForwardPersistencePolicy.typeFromPersisted("Remote"))
    }

    @Test
    fun backupImportReferencePolicyPrunesDanglingImportedRecords() {
        val result = BackupImportReferencePolicy.prune(
            servers = listOf(
                server("server-1", credentialId = "missing-credential", proxyJumpHostId = "server-2"),
                server("server-2", credentialId = "credential-1", proxyJumpHostId = "server-2")
            ),
            credentials = listOf(credential("credential-1")),
            forwards = listOf(forward(serverId = "server-1"), forward(id = "missing-forward", serverId = "missing-server")),
            terminalSessions = listOf(record(ServerStatus.Offline).copy(serverId = "server-1"), record(ServerStatus.Offline).copy(id = "missing-session", serverId = "missing-server")),
            sftpBookmarks = listOf(bookmark("bookmark-1", "server-2"), bookmark("missing-bookmark", "missing-server")),
            transfers = listOf(transfer("transfer-1", "server-1"), transfer("missing-transfer", "missing-server"))
        )

        assertNull(result.servers.first { it.id == "server-1" }.credentialId)
        assertEquals("server-2", result.servers.first { it.id == "server-1" }.proxyJumpHostId)
        assertEquals("credential-1", result.servers.first { it.id == "server-2" }.credentialId)
        assertNull(result.servers.first { it.id == "server-2" }.proxyJumpHostId)
        assertEquals(listOf("forward"), result.forwards.map { it.id })
        assertEquals(listOf("session"), result.terminalSessions.map { it.id })
        assertEquals(listOf("bookmark-1"), result.sftpBookmarks.map { it.id })
        assertEquals(listOf("transfer-1"), result.transfers.map { it.id })
        assertEquals(4, result.prunedRows)
    }

    @Test
    fun persistedServerProfilesDefaultToSshProtocol() {
        val loaded = PersistedServerProfilePolicy.normalizeLoaded(server("server-1", null, null))

        checkNotNull(loaded)
        assertEquals(ConnectionProtocol.Ssh, loaded.protocol)
    }

    @Test
    fun backupImportForwardStatusPolicyDropsImportedAndPrunedForwardStatuses() {
        val retained = BackupImportForwardStatusPolicy.retainedStatusIds(
            currentStatusIds = setOf("imported-forward", "existing-forward", "missing-forward"),
            retainedForwards = listOf(
                forward(id = "imported-forward", serverId = "server-1"),
                forward(id = "existing-forward", serverId = "server-1")
            ),
            importedForwardIds = setOf("imported-forward")
        )

        assertEquals(setOf("existing-forward"), retained)
    }

    @Test
    fun forwardClosedCallbackPolicyRejectsStaleRuntimeTokens() {
        assertEquals(true, shouldAcceptForwardClosedCallback(activeToken = 2L, callbackToken = 2L))
        assertEquals(false, shouldAcceptForwardClosedCallback(activeToken = 3L, callbackToken = 2L))
        assertEquals(false, shouldAcceptForwardClosedCallback(activeToken = null, callbackToken = 2L))
    }

    @Test
    fun forwardRuntimeClosureClearsPersistedEnabledFlag() {
        val closed = forwardRuleAfterRuntimeClosed(forward(enabled = true, autoStart = true))

        assertEquals(false, closed.enabled)
        assertEquals(true, closed.autoStart)
    }

    @Test
    fun forwardAutoStartPolicyOnlyStartsUnattemptedRules() {
        val rule = forward(autoStart = true)

        assertTrue(ForwardAutoStartPolicy.shouldStart(rule, null))
        assertFalse(ForwardAutoStartPolicy.shouldStart(rule.copy(autoStart = false), null))
        assertFalse(ForwardAutoStartPolicy.shouldStart(rule, ForwardRuntimePolicy.starting(rule)))
        assertFalse(ForwardAutoStartPolicy.shouldStart(rule, ForwardRuntimePolicy.failed(rule, "Permission denied")))
        assertFalse(ForwardAutoStartPolicy.shouldStart(rule, ForwardRuntimePolicy.running(rule)))
    }

    private fun record(status: ServerStatus): TerminalSessionRecord {
        return TerminalSessionRecord(
            id = "session",
            serverId = "server",
            title = "Shell",
            status = status,
            startedAtEpochMillis = 100L,
            lastActiveEpochMillis = 200L,
            transcriptPreview = "last command",
            tmuxSessionName = " work ",
            tmuxWindowIndex = 2
        )
    }

    private fun forward(
        id: String = "forward",
        serverId: String = "server",
        enabled: Boolean = false,
        autoStart: Boolean = false,
        localHost: String = "127.0.0.1",
        remoteHost: String = "db",
    ): PortForwardRule {
        return PortForwardRule(
            id = id,
            serverId = serverId,
            type = PortForwardType.Local,
            localHost = localHost,
            localPort = 8022,
            remoteHost = remoteHost,
            remotePort = 22,
            enabled = enabled,
            autoStart = autoStart
        )
    }

    private fun server(id: String, credentialId: String?, proxyJumpHostId: String?): ServerProfile {
        return ServerProfile(
            id = id,
            name = id,
            host = "$id.example.test",
            port = 22,
            username = "root",
            group = "Production",
            tags = listOf("All"),
            osName = "Linux",
            osVersion = "Unknown",
            accent = ServerAccent("Default", 0xFF00AEEF),
            credentialId = credentialId,
            terminalProfileId = "term-default",
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false),
            proxyJumpHostId = proxyJumpHostId
        )
    }

    private fun credential(id: String): Credential {
        return Credential(
            id = id,
            label = id,
            type = CredentialType.Password,
            publicKeyPreview = null,
            encryptedPayloadRef = "secret-$id",
            createdAtEpochMillis = 1L
        )
    }

    private fun bookmark(id: String, serverId: String): SftpBookmark {
        return SftpBookmark(id, serverId, "Home", "/home/root", 1L)
    }

    private fun transfer(id: String, serverId: String): TransferRecord {
        return TransferRecord(
            id = id,
            serverId = serverId,
            direction = TransferDirection.Download,
            remotePath = "/tmp/file",
            localDisplayName = "file",
            progress = 1f,
            state = TransferRecordState.Complete,
            message = "Done",
            updatedAtEpochMillis = 1L
        )
    }
}
