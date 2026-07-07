package com.chrono.ssh.core.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object TerminalSessionRegistry {
    private val sessions = ConcurrentHashMap<String, RegisteredTerminalSession>()
    private val activeSessionCount = MutableStateFlow(0)

    val activeCountFlow: StateFlow<Int> = activeSessionCount

    fun attach(workspaceId: String, session: SshSession) {
        sessions[workspaceId] = RegisteredTerminalSession(
            workspaceId = workspaceId,
            session = session,
            attachedAtEpochMillis = System.currentTimeMillis(),
            lastSeenEpochMillis = System.currentTimeMillis()
        )
        publishActiveCount()
    }

    fun session(workspaceId: String): SshSession? = sessions[workspaceId]?.also { record ->
        if (!record.session.isConnected) {
            detach(workspaceId, record.session)
            return null
        }
        sessions[workspaceId] = record.copy(lastSeenEpochMillis = System.currentTimeMillis())
    }?.session

    fun snapshots(): List<TerminalSessionRegistrySnapshot> {
        pruneDisconnectedSessions()
        return sessions.values.map { record ->
            TerminalSessionRegistrySnapshot(
                workspaceId = record.workspaceId,
                sessionId = record.session.id,
                serverId = record.session.serverId,
                attachedAtEpochMillis = record.attachedAtEpochMillis,
                lastSeenEpochMillis = record.lastSeenEpochMillis,
                transcriptPreview = record.session.transcriptPreview
            )
        }
    }

    fun detach(workspaceId: String, session: SshSession? = null) {
        if (session == null) {
            sessions.remove(workspaceId)
        } else {
            sessions.computeIfPresent(workspaceId) { _, record ->
                if (record.session === session) null else record
            }
        }
        publishActiveCount()
    }

    fun activeCount(): Int {
        pruneDisconnectedSessions()
        return sessions.size
    }

    fun pruneDisconnected() {
        pruneDisconnectedSessions()
    }

    fun clear() {
        sessions.clear()
        publishActiveCount()
    }

    private fun publishActiveCount() {
        activeSessionCount.value = sessions.size
    }

    private fun pruneDisconnectedSessions() {
        val staleKeys = sessions.entries
            .filterNot { it.value.session.isConnected }
            .map { it.key }
        if (staleKeys.isEmpty()) return
        staleKeys.forEach { sessions.remove(it) }
        publishActiveCount()
    }
}

private data class RegisteredTerminalSession(
    val workspaceId: String,
    val session: SshSession,
    val attachedAtEpochMillis: Long,
    val lastSeenEpochMillis: Long
)

data class TerminalSessionRegistrySnapshot(
    val workspaceId: String,
    val sessionId: String,
    val serverId: String,
    val attachedAtEpochMillis: Long,
    val lastSeenEpochMillis: Long,
    val transcriptPreview: String
)
