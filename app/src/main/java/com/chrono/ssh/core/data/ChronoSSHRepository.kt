package com.chrono.ssh.core.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.chrono.ssh.core.model.AppSettings
import com.chrono.ssh.core.model.ConnectionCommand
import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.CrashLogEntry
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.CpuMetrics
import com.chrono.ssh.core.model.DiskMetrics
import com.chrono.ssh.core.model.DockerSummary
import com.chrono.ssh.core.model.EternalTerminalConfig
import com.chrono.ssh.core.model.FileProtocolConfig
import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.MemoryMetrics
import com.chrono.ssh.core.model.MetricSnapshot
import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.MoshConfig
import com.chrono.ssh.core.model.NetworkHistory
import com.chrono.ssh.core.model.NetworkInterfaceMetric
import com.chrono.ssh.core.model.NetworkMetrics
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ProcessSummary
import com.chrono.ssh.core.model.ProotProfileConfig
import com.chrono.ssh.core.model.RdpProfileConfig
import com.chrono.ssh.core.model.ReconnectPolicy
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.ServerStatus
import com.chrono.ssh.core.model.ServerCardDiskMode
import com.chrono.ssh.core.model.ServerCardNetworkMode
import com.chrono.ssh.core.model.ServerDetailCard
import com.chrono.ssh.core.model.ServerMetricColorPreset
import com.chrono.ssh.core.model.SftpBookmark
import com.chrono.ssh.core.model.ServiceSummary
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TerminalCursorStyle
import com.chrono.ssh.core.model.TerminalKey
import com.chrono.ssh.core.model.TerminalKeyRow
import com.chrono.ssh.core.model.TerminalProfile
import com.chrono.ssh.core.model.TerminalSessionRecord
import com.chrono.ssh.core.model.TransferRecord
import com.chrono.ssh.core.model.TransferRecordState
import com.chrono.ssh.core.model.VncProfileConfig
import com.chrono.ssh.core.model.WakeOnLanConfig
import com.chrono.ssh.core.service.CredentialPassphraseAction
import com.chrono.ssh.core.service.CredentialDeletePolicy
import com.chrono.ssh.core.service.CredentialHostLinkPolicy
import com.chrono.ssh.core.service.CredentialPassphrasePolicy
import com.chrono.ssh.core.service.CredentialPersistencePolicy
import com.chrono.ssh.core.service.DefaultSnippetCatalog
import com.chrono.ssh.core.service.BackupCredentialPolicy
import com.chrono.ssh.core.service.BackupForwardPolicy
import com.chrono.ssh.core.service.BackupKnownHostPolicy
import com.chrono.ssh.core.service.BackupServerPolicy
import com.chrono.ssh.core.service.BackupSftpBookmarkPolicy
import com.chrono.ssh.core.service.BackupSnippetPolicy
import com.chrono.ssh.core.service.AndroidSshjCompat
import com.chrono.ssh.core.service.AppLockCrashRecoveryPolicy
import com.chrono.ssh.core.service.ConnectionEventPersistencePolicy
import com.chrono.ssh.core.service.ContainerRuntimeActionPolicy
import com.chrono.ssh.core.service.ForwardRuntimePolicy
import com.chrono.ssh.core.service.PortForwardValidator
import com.chrono.ssh.core.service.PinLockPolicy
import com.chrono.ssh.core.service.ProcessActionPolicy
import com.chrono.ssh.core.service.ProxyJumpPolicy
import com.chrono.ssh.core.service.SshFailure
import com.chrono.ssh.core.service.KeyMaterialInspector
import com.chrono.ssh.core.service.MetricSnapshotMergePolicy
import com.chrono.ssh.core.service.MonitoringSkipDiagnosticPolicy
import com.chrono.ssh.core.service.OpenSshConfigHost
import com.chrono.ssh.core.service.OpenSshConfigParser
import com.chrono.ssh.core.service.ForwardStatus
import com.chrono.ssh.core.service.HostKeyDecision
import com.chrono.ssh.core.service.HostKeyApprovalPolicy
import com.chrono.ssh.core.service.HostKeyObservationPolicy
import com.chrono.ssh.core.service.HostCommandSafety
import com.chrono.ssh.core.service.HostEnvironmentPolicy
import com.chrono.ssh.core.service.HostEndpointValidator
import com.chrono.ssh.core.service.HostInfoCommandPolicy
import com.chrono.ssh.core.service.HostUniquenessPolicy
import com.chrono.ssh.core.service.HostOsDetector
import com.chrono.ssh.core.service.KnownHostPersistencePolicy
import com.chrono.ssh.core.service.SshSession
import com.chrono.ssh.core.service.SshTransport
import com.chrono.ssh.core.service.SshMetricsCollector
import com.chrono.ssh.core.service.SshjTransport
import com.chrono.ssh.core.service.ServerStatusRefreshPolicy
import com.chrono.ssh.core.service.SystemdServiceActionPolicy
import com.chrono.ssh.core.service.SftpBookmarkPersistencePolicy
import com.chrono.ssh.core.service.SnippetPersistencePolicy
import com.chrono.ssh.core.service.SnippetValidator
import com.chrono.ssh.core.service.TerminalAccessoryKeyPolicy
import com.chrono.ssh.core.service.TransferCancellationRegistry
import com.chrono.ssh.core.service.TransferCompletionPolicy
import com.chrono.ssh.core.service.TransferPersistencePolicy
import com.chrono.ssh.core.service.TransferStateReducer
import com.chrono.ssh.core.service.WakeOnLanPolicy
import com.chrono.ssh.core.service.CredentialUniquenessPolicy
import com.chrono.ssh.core.service.displayName
import com.chrono.ssh.core.service.routeLabel
import com.chrono.ssh.core.security.AndroidSecretStore
import com.chrono.ssh.ui.design.DeckThemeCatalog
import com.chrono.ssh.ui.design.DeckThemeMode
import com.chrono.ssh.ui.terminal.TerminalCatalog
import java.io.File
import java.util.UUID

private const val MetricHistoryDetailedRetentionMillis = 24L * 60L * 60L * 1000L
private const val MetricHistorySummaryRetentionMillis = 7L * 24L * 60L * 60L * 1000L
private const val MetricHistorySummaryBucketMillis = 60L * 60L * 1000L
private const val METRIC_DETAILS_INTERVAL_MS = 60L * 1000L

internal object MetricDetailsRefreshPolicy {
    fun due(lastEpochMillis: Long?, nowEpochMillis: Long, intervalMillis: Long): Boolean {
        return lastEpochMillis == null || nowEpochMillis - lastEpochMillis >= intervalMillis
    }
}

internal object MetricDetailsCollectionPolicy {
    fun shouldCollect(lastEpochMillis: Long?, nowEpochMillis: Long, intervalMillis: Long): Boolean {
        return MetricDetailsRefreshPolicy.due(lastEpochMillis, nowEpochMillis, intervalMillis)
    }
}

internal fun shouldCollectMetricDetailsDuringRefresh(detailsDue: Boolean, forceDetails: Boolean = false): Boolean {
    return forceDetails || detailsDue
}

internal fun shouldAcceptForwardClosedCallback(activeToken: Long?, callbackToken: Long): Boolean {
    return activeToken == callbackToken
}

internal fun forwardRuleAfterRuntimeClosed(rule: PortForwardRule): PortForwardRule {
    return if (rule.enabled) rule.copy(enabled = false) else rule
}

internal object ForwardAutoStartPolicy {
    fun shouldStart(rule: PortForwardRule, status: ForwardStatus?): Boolean {
        return rule.autoStart && status == null
    }
}

internal object MetricSnapshotSeedPolicy {
    fun seed(serverId: String, history: List<MetricSnapshot>, unavailable: (String) -> MetricSnapshot): MetricSnapshot {
        return history
            .filter { it.serverId == serverId && hasSeedableData(it) }
            .maxByOrNull { it.collectedAtEpochMillis }
            ?: unavailable(serverId)
    }

    fun hasSeedableData(snapshot: MetricSnapshot): Boolean {
        return snapshot.uptime != "--" ||
            snapshot.cpu.usagePercent > 0 ||
            snapshot.cpu.perCore.isNotEmpty() ||
            snapshot.memory.totalMb > 0 ||
            snapshot.disk.totalGb > 0f ||
            snapshot.network.interfaces.isNotEmpty() ||
            snapshot.processes.total > 0 ||
            snapshot.processes.items.isNotEmpty() ||
            snapshot.services.total > 0 ||
            snapshot.services.items.isNotEmpty() ||
            snapshot.services.failedItems.isNotEmpty() ||
            snapshot.docker.containers > 0 ||
            snapshot.docker.items.isNotEmpty() ||
            snapshot.pveResources.isNotEmpty() ||
            snapshot.gpus.isNotEmpty()
    }
}

data class BackupImportReport(
    val valid: Boolean,
    val sections: Map<String, Int>,
    val skippedRows: Int,
    val insertedRows: Int = 0,
    val updatedRows: Int = 0,
    val malformedRows: Int = 0,
    val credentialMetadataRows: Int = 0,
    val message: String
)

data class BackupMergeStats(
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0
) {
    operator fun plus(other: BackupMergeStats): BackupMergeStats {
        return BackupMergeStats(
            inserted = inserted + other.inserted,
            updated = updated + other.updated,
            skipped = skipped + other.skipped
        )
    }
}

internal object BackupImportReportFormatter {
    fun inspected(
        recordCount: Int,
        malformedRows: Int,
        unknownRows: Int,
        credentialMetadataRows: Int
    ): String {
        val issues = buildList {
            if (malformedRows > 0) add("$malformedRows malformed")
            if (unknownRows > 0) add("$unknownRows unknown-section")
        }.joinToString(", ")
        val credentialNote = if (credentialMetadataRows > 0) {
            " $credentialMetadataRows credential identities are metadata-only and will need their secrets replaced in Vault after import."
        } else {
            " Credential payloads are intentionally excluded."
        }
        return "Backup parsed: $recordCount metadata records${if (issues.isBlank()) "" else ", $issues skipped"}.$credentialNote"
    }

    fun imported(credentialMetadataRows: Int): String {
        return if (credentialMetadataRows > 0) {
            "Metadata imported. $credentialMetadataRows credential identities were imported as metadata only; replace their secrets in Vault before use."
        } else {
            "Metadata imported."
        }
    }
}

private val BackupImportKnownSections = setOf(
    "settings",
    "servers",
    "credentials",
    "knownHosts",
    "snippets",
    "forwards",
    "terminalSessions",
    "sftpBookmarks",
    "transfers"
)

internal object BackupImportTextPolicy {
    private const val MaxImportChars = 2_000_000
    private const val MaxImportRows = 5_000

    fun rejectionMessage(text: String): String? {
        if (text.length > MaxImportChars) return "Backup is too large to import."
        if (text.lineSequence().take(MaxImportRows + 1).count() > MaxImportRows) return "Backup has too many rows to import."
        return null
    }
}

internal object MetricHistoryRetentionPolicy {
    fun retain(
        samples: List<MetricSnapshot>,
        nowEpochMillis: Long
    ): List<MetricSnapshot> {
        val unique = samples
            .filter(MetricSnapshotSeedPolicy::hasSeedableData)
            .distinctBy { it.collectedAtEpochMillis }
            .sortedBy { it.collectedAtEpochMillis }
        val detailedCutoff = nowEpochMillis - MetricHistoryDetailedRetentionMillis
        val summaryCutoff = nowEpochMillis - MetricHistorySummaryRetentionMillis
        val detailed = unique.filter { it.collectedAtEpochMillis >= detailedCutoff }
        val summarized = unique
            .filter { it.collectedAtEpochMillis in summaryCutoff until detailedCutoff }
            .groupBy { it.collectedAtEpochMillis / MetricHistorySummaryBucketMillis }
            .values
            .mapNotNull { bucket -> bucket.maxByOrNull { it.collectedAtEpochMillis } }
        return (summarized + detailed)
            .distinctBy { it.collectedAtEpochMillis }
            .sortedBy { it.collectedAtEpochMillis }
    }
}

internal object TerminalSessionPersistencePolicy {
    private const val PreviewMaxChars = 240

    fun normalizeLoaded(record: TerminalSessionRecord): TerminalSessionRecord {
        return when (record.status) {
            ServerStatus.Online,
            ServerStatus.Connecting -> record.copy(status = ServerStatus.Offline)
            else -> record
        }
    }

    fun normalizePersisted(record: TerminalSessionRecord): TerminalSessionRecord? {
        val id = record.id.trim()
        val serverId = record.serverId.trim()
        val tmuxSessionName = record.tmuxSessionName?.trim()?.takeIf { it.isNotBlank() }
        if (id.isBlank() || serverId.isBlank()) return null
        return normalizeLoaded(record).copy(
            id = id,
            serverId = serverId,
            title = record.title.trim().ifBlank { "Shell" },
            startedAtEpochMillis = record.startedAtEpochMillis.coerceAtLeast(0L),
            lastActiveEpochMillis = record.lastActiveEpochMillis.coerceAtLeast(0L),
            transcriptPreview = record.transcriptPreview.trim().takeLast(PreviewMaxChars),
            tmuxSessionName = tmuxSessionName,
            tmuxWindowIndex = tmuxSessionName?.let { record.tmuxWindowIndex?.takeIf { index -> index >= 0 } }
        )
    }
}

internal object TerminalSessionUpdatePolicy {
    fun merge(existing: TerminalSessionRecord?, incoming: TerminalSessionRecord): TerminalSessionRecord {
        if (existing == null) return incoming
        if (incoming.lastActiveEpochMillis < existing.lastActiveEpochMillis) return existing
        val staleLiveUpdateAfterClose = existing.status == ServerStatus.Offline &&
            incoming.status in setOf(ServerStatus.Online, ServerStatus.Connecting) &&
            incoming.lastActiveEpochMillis <= existing.lastActiveEpochMillis
        return if (staleLiveUpdateAfterClose) existing else incoming
    }
}

internal object BackupTerminalSessionPolicy {
    fun sanitizeImportedMetadata(record: TerminalSessionRecord): TerminalSessionRecord? {
        return TerminalSessionPersistencePolicy.normalizePersisted(record)
    }
}

internal data class BackupImportReferencePruneResult(
    val servers: List<ServerProfile>,
    val forwards: List<PortForwardRule>,
    val terminalSessions: List<TerminalSessionRecord>,
    val sftpBookmarks: List<SftpBookmark>,
    val transfers: List<TransferRecord>,
    val prunedRows: Int
)

internal object BackupImportReferencePolicy {
    fun prune(
        servers: List<ServerProfile>,
        credentials: List<Credential>,
        forwards: List<PortForwardRule>,
        terminalSessions: List<TerminalSessionRecord>,
        sftpBookmarks: List<SftpBookmark>,
        transfers: List<TransferRecord>
    ): BackupImportReferencePruneResult {
        val credentialIds = credentials.map { it.id }.toSet()
        val serverIds = servers.map { it.id }.toSet()
        val cleanedServers = servers.map { server ->
            server.copy(
                credentialId = server.credentialId?.takeIf { it in credentialIds },
                proxyJumpHostId = server.proxyJumpHostId?.takeIf { it in serverIds && it != server.id }
            )
        }
        val keptForwards = forwards.filter { it.serverId in serverIds }
        val keptTerminalSessions = terminalSessions.filter { it.serverId in serverIds }
        val keptSftpBookmarks = sftpBookmarks.filter { it.serverId in serverIds }
        val keptTransfers = transfers.filter { it.serverId in serverIds }
        return BackupImportReferencePruneResult(
            servers = cleanedServers,
            forwards = keptForwards,
            terminalSessions = keptTerminalSessions,
            sftpBookmarks = keptSftpBookmarks,
            transfers = keptTransfers,
            prunedRows = (forwards.size - keptForwards.size) +
                (terminalSessions.size - keptTerminalSessions.size) +
                (sftpBookmarks.size - keptSftpBookmarks.size) +
                (transfers.size - keptTransfers.size)
        )
    }
}

internal object BackupImportForwardStatusPolicy {
    fun retainedStatusIds(
        currentStatusIds: Set<String>,
        retainedForwards: List<PortForwardRule>,
        importedForwardIds: Set<String>
    ): Set<String> {
        val retainedForwardIds = retainedForwards.mapTo(mutableSetOf()) { it.id }
        return currentStatusIds.filterTo(mutableSetOf()) { it in retainedForwardIds && it !in importedForwardIds }
    }
}

internal object ForwardPersistencePolicy {
    fun typeFromPersisted(raw: String): PortForwardType? {
        return runCatching { PortForwardType.valueOf(raw) }.getOrNull()
    }

    fun normalizeLoaded(rule: PortForwardRule): PortForwardRule? {
        return PortForwardValidator.validate(rule.copy(enabled = false))
            .normalized
            ?.copy(enabled = false)
    }
}

internal object HostDeletionPolicy {
    fun removeServerAndProxyJumpReferences(
        servers: List<ServerProfile>,
        deletedServerId: String
    ): List<ServerProfile> {
        return servers
            .filterNot { it.id == deletedServerId }
            .map { server ->
                if (server.proxyJumpHostId == deletedServerId) server.copy(proxyJumpHostId = null) else server
            }
    }
}

class ChronoSSHRepository(private val context: Context) {
    private val storeDir = File(context.filesDir, "ChronoSSH-state").apply { mkdirs() }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val secretStore = AndroidSecretStore(context.applicationContext)
    private val serversFile = File(storeDir, "servers.v1.txt")
    private val settingsFile = File(storeDir, "settings.v1.txt")
    private val eventsFile = File(storeDir, "connection-events.v1.txt")
    private val credentialsFile = File(storeDir, "credentials.v1.txt")
    private val knownHostsFile = File(storeDir, "known-hosts.v1.txt")
    private val snippetsFile = File(storeDir, "snippets.v1.txt")
    private val forwardsFile = File(storeDir, "forwards.v1.txt")
    private val terminalSessionsFile = File(storeDir, "terminal-sessions.v1.txt")
    private val sftpBookmarksFile = File(storeDir, "sftp-bookmarks.v1.txt")
    private val transfersFile = File(storeDir, "transfers.v1.txt")

    var terminalProfile = terminalProfileFromSettings(loadSettings())
        private set
    val servers = mutableStateListOf<ServerProfile>()
    val snapshots = mutableStateMapOf<String, MetricSnapshot>()
    val metricHistory = mutableStateMapOf<String, List<MetricSnapshot>>()
    val connectionEvents = mutableStateMapOf<String, List<ConnectionEvent>>()
    val crashLogs = mutableStateListOf<CrashLogEntry>()
    val credentials = mutableStateListOf<Credential>()
    val knownHosts = mutableStateListOf<KnownHost>()
    val snippets = mutableStateListOf<Snippet>()
    val forwards = mutableStateListOf<PortForwardRule>()
    val forwardStatuses = mutableStateMapOf<String, ForwardStatus>()
    val terminalSessions = mutableStateListOf<TerminalSessionRecord>()
    val sftpBookmarks = mutableStateListOf<SftpBookmark>()
    val transfers = mutableStateListOf<TransferRecord>()
    private val metricsCollector = SshMetricsCollector()
    private val collectingMetricsFor = mutableSetOf<String>()
    private val lastMetricsAttemptAt = mutableMapOf<String, Long>()
    private val forwardRuntimeTokens = mutableMapOf<String, Long>()
    private var forwardRuntimeTokenCounter = 0L
    private val lastMetricDetailsAt = mutableMapOf<String, Long>()
    private val lastMetricsSkipDiagnosticAt = mutableMapOf<String, Long>()
    private val lastNoisyEventAt = mutableMapOf<String, Long>()
    val sshTransport: SshTransport = SshjTransport(
        context = context.applicationContext,
        secretStore = secretStore,
        knownHostLookup = { server -> knownHostFor(server) },
        proxyJumpLookup = { server ->
            val resolution = ProxyJumpPolicy.resolveTarget(server, servers, ::credentialFor)
            resolution.error?.let { throw SshFailure.Unsupported(it) }
            resolution.target
        },
        onHostKeySeen = { server, algorithm, fingerprint, state ->
            HostKeyObservationPolicy.changedObservation(
                server = server,
                existing = knownHostFor(server),
                algorithm = algorithm,
                fingerprint = fingerprint,
                nowEpochMillis = System.currentTimeMillis()
            )?.let(::upsertKnownHost)
            appendEvent(
                server.id,
                if (state == HostKeyTrustState.Changed || state == HostKeyTrustState.Rejected) ConnectionEventLevel.Error else ConnectionEventLevel.Info,
                "Host key seen: $algorithm $fingerprint [$state]"
            )
        }
    )

    init {
        recoverStartupSection(serversFile) { servers.addAll(loadServers().ifEmpty { defaultServers() }) }
        recoverStartupSection(credentialsFile) { credentials.addAll(loadCredentials().ifEmpty { defaultCredentials() }) }
        recoverStartupSection(knownHostsFile) { knownHosts.addAll(loadKnownHosts().ifEmpty { defaultKnownHosts() }) }
        recoverStartupSection(snippetsFile) { snippets.addAll(loadSnippets().ifEmpty { defaultSnippets() }) }
        recoverStartupSection(forwardsFile) { forwards.addAll(loadForwards().ifEmpty { defaultForwards() }) }
        recoverStartupSection(terminalSessionsFile) { terminalSessions.addAll(loadTerminalSessions()) }
        recoverStartupSection(sftpBookmarksFile) { sftpBookmarks.addAll(loadSftpBookmarks().ifEmpty { defaultSftpBookmarks() }) }
        recoverStartupSection(transfersFile) { transfers.addAll(loadTransfers()) }
        recoverStartupSection(eventsFile) { connectionEvents.putAll(loadEvents().groupBy { it.serverId }) }
        snapshots.putAll(defaultSnapshots())
        servers.forEach { server -> snapshots.putIfAbsent(server.id, seedSnapshot(server.id)) }
        crashLogs.addAll(CrashLogStore.load(context).sortedByDescending { it.atEpochMillis })
    }

    private fun recoverStartupSection(file: File, load: () -> Unit) {
        runCatching(load).onFailure {
            quarantineCorruptFile(file)
            runCatching(load)
        }
    }

    private fun quarantineCorruptFile(file: File) {
        if (!file.exists()) return
        val target = File(file.parentFile, "${file.name}.corrupt-${System.currentTimeMillis()}")
        runCatching { file.renameTo(target) }.onFailure { runCatching { file.delete() } }
    }

    fun loadSettings(): AppSettings {
        return runCatching {
            if (!settingsFile.exists()) {
                return@runCatching defaultSettings()
            }
            val values = settingsFile.readLines()
                .mapNotNull { line ->
                    val splitAt = line.indexOf('=')
                    if (splitAt <= 0) null else line.take(splitAt) to unescape(line.drop(splitAt + 1))
                }
                .toMap()
            val loadedSettings = AppSettings(
                themeModeName = values["themeModeName"] ?: DeckThemeMode.System.name,
                themeFamilyId = values["themeFamilyId"] ?: DeckThemeCatalog.DEFAULT_FAMILY_ID,
                terminalFontSizeSp = values["terminalFontSizeSp"]?.toIntOrNull()?.coerceIn(10, 24) ?: 14,
                terminalScrollbackLines = values["terminalScrollbackLines"]?.toIntOrNull()?.coerceIn(1000, 50000) ?: 12000,
                terminalCursorStyle = values["terminalCursorStyle"]
                    ?.let { runCatching { TerminalCursorStyle.valueOf(it) }.getOrNull() }
                    ?: TerminalCursorStyle.Block,
                terminalThemeName = values["terminalThemeName"]?.ifBlank { null } ?: "Tokyo Night",
                terminalFontFamily = values["terminalFontFamily"]?.ifBlank { null } ?: "JetBrains Mono",
                terminalBracketedPaste = values["terminalBracketedPaste"]?.toBooleanStrictOrNull() ?: true,
                terminalHapticFeedback = values["terminalHapticFeedback"]?.toBooleanStrictOrNull() ?: true,
                terminalAccessoryKeys = TerminalAccessoryKeyPolicy.normalizeCsv(values["terminalAccessoryKeys"]),
                terminalAccessorySingleRow = values["terminalAccessorySingleRow"]?.toBooleanStrictOrNull() ?: false,
                terminalSideMarginDp = values["terminalSideMarginDp"]?.toIntOrNull()?.coerceIn(0, 8) ?: 2,
                terminalRightMarginDp = values["terminalRightMarginDp"]?.toIntOrNull()?.coerceIn(0, 8) ?: values["terminalSideMarginDp"]?.toIntOrNull()?.coerceIn(0, 8) ?: 2,
                terminalAccessoryPopups = values["terminalAccessoryPopups"]?.toBooleanStrictOrNull() ?: true,
                terminalAccessoryFullScroll = values["terminalAccessoryFullScroll"]?.toBooleanStrictOrNull() ?: false,
                terminalKeepScreenOn = values["terminalKeepScreenOn"]?.toBooleanStrictOrNull() ?: false,
                serverCardNetworkMode = values["serverCardNetworkMode"]?.let { runCatching { ServerCardNetworkMode.valueOf(it) }.getOrNull() } ?: ServerCardNetworkMode.Totals,
                serverCardDiskMode = values["serverCardDiskMode"]?.let { runCatching { ServerCardDiskMode.valueOf(it) }.getOrNull() } ?: ServerCardDiskMode.Usage,
                serverMetricColorPreset = values["serverMetricColorPreset"]?.let { runCatching { ServerMetricColorPreset.valueOf(it) }.getOrNull() } ?: ServerMetricColorPreset.Classic,
                serverDetailCardOrder = ServerDetailCard.sanitizeOrderCsv(values["serverDetailCardOrder"].orEmpty()),
                serverDetailHiddenCards = ServerDetailCard.sanitizeHiddenCsv(values["serverDetailHiddenCards"].orEmpty()),
                homeHeadingFontPath = values["homeHeadingFontPath"]?.ifBlank { null },
                connectionsHeadingFontPath = values["connectionsHeadingFontPath"]?.ifBlank { null },
                filesHeadingFontPath = values["filesHeadingFontPath"]?.ifBlank { null },
                vaultHeadingFontPath = values["vaultHeadingFontPath"]?.ifBlank { null },
                settingsHeadingFontPath = values["settingsHeadingFontPath"]?.ifBlank { null },
                sftpDefaultSortModeName = normalizeSftpSortModeName(values["sftpDefaultSortModeName"]),
                sftpDefaultSortDescending = values["sftpDefaultSortDescending"]?.toBooleanStrictOrNull() ?: false,
                sftpShowHiddenByDefault = values["sftpShowHiddenByDefault"]?.toBooleanStrictOrNull() ?: false,
                autoRefreshSeconds = ServerStatusRefreshPolicy.normalize(values["autoRefreshSeconds"]?.toIntOrNull()),
                appLockPinHash = values["appLockPinHash"]?.ifBlank { null },
                appLockPinSalt = values["appLockPinSalt"]?.ifBlank { null },
                appLockBiometricEnabled = values["appLockBiometricEnabled"]?.toBooleanStrictOrNull() ?: false,
                appLockRenderArmedAtEpochMillis = values["appLockRenderArmedAtEpochMillis"]?.toLongOrNull()?.takeIf { it > 0L }
            )
            val cleanSettings = recoverAppLockAfterCrash(sanitizeLoadedSettings(loadedSettings))
            if (cleanSettings != loadedSettings) saveSettings(cleanSettings)
            cleanSettings
        }.getOrElse {
            val cleanSettings = defaultSettings()
            saveSettings(cleanSettings)
            cleanSettings
        }
    }

    fun saveSettings(settings: AppSettings) {
        val cleanSettings = sanitizeLoadedSettings(settings)
        runCatching {
            settingsFile.writeText(
                listOf(
                    "themeModeName=${escape(cleanSettings.themeModeName)}",
                    "themeFamilyId=${escape(cleanSettings.themeFamilyId)}",
                    "terminalFontSizeSp=${cleanSettings.terminalFontSizeSp.coerceIn(10, 24)}",
                    "terminalScrollbackLines=${cleanSettings.terminalScrollbackLines.coerceIn(1000, 50000)}",
                    "terminalCursorStyle=${escape(cleanSettings.terminalCursorStyle.name)}",
                    "terminalThemeName=${escape(cleanSettings.terminalThemeName)}",
                    "terminalFontFamily=${escape(cleanSettings.terminalFontFamily)}",
                    "terminalBracketedPaste=${cleanSettings.terminalBracketedPaste}",
                    "terminalHapticFeedback=${cleanSettings.terminalHapticFeedback}",
                    "terminalAccessoryKeys=${escape(TerminalAccessoryKeyPolicy.normalizeCsv(cleanSettings.terminalAccessoryKeys))}",
                    "terminalAccessorySingleRow=${cleanSettings.terminalAccessorySingleRow}",
                    "terminalSideMarginDp=${cleanSettings.terminalSideMarginDp.coerceIn(0, 8)}",
                    "terminalRightMarginDp=${cleanSettings.terminalRightMarginDp.coerceIn(0, 8)}",
                    "terminalAccessoryPopups=${cleanSettings.terminalAccessoryPopups}",
                    "terminalAccessoryFullScroll=${cleanSettings.terminalAccessoryFullScroll}",
                    "terminalKeepScreenOn=${cleanSettings.terminalKeepScreenOn}",
                    "serverCardNetworkMode=${escape(cleanSettings.serverCardNetworkMode.name)}",
                    "serverCardDiskMode=${escape(cleanSettings.serverCardDiskMode.name)}",
                    "serverMetricColorPreset=${escape(cleanSettings.serverMetricColorPreset.name)}",
                    "serverDetailCardOrder=${escape(ServerDetailCard.sanitizeOrderCsv(cleanSettings.serverDetailCardOrder))}",
                    "serverDetailHiddenCards=${escape(ServerDetailCard.sanitizeHiddenCsv(cleanSettings.serverDetailHiddenCards))}",
                    "homeHeadingFontPath=${escape(cleanSettings.homeHeadingFontPath.orEmpty())}",
                    "connectionsHeadingFontPath=${escape(cleanSettings.connectionsHeadingFontPath.orEmpty())}",
                    "filesHeadingFontPath=${escape(cleanSettings.filesHeadingFontPath.orEmpty())}",
                    "vaultHeadingFontPath=${escape(cleanSettings.vaultHeadingFontPath.orEmpty())}",
                    "settingsHeadingFontPath=${escape(cleanSettings.settingsHeadingFontPath.orEmpty())}",
                    "sftpDefaultSortModeName=${escape(normalizeSftpSortModeName(cleanSettings.sftpDefaultSortModeName))}",
                    "sftpDefaultSortDescending=${cleanSettings.sftpDefaultSortDescending}",
                    "sftpShowHiddenByDefault=${cleanSettings.sftpShowHiddenByDefault}",
                    "autoRefreshSeconds=${ServerStatusRefreshPolicy.normalize(cleanSettings.autoRefreshSeconds)}",
                    "appLockPinHash=${escape(cleanSettings.appLockPinHash.orEmpty())}",
                    "appLockPinSalt=${escape(cleanSettings.appLockPinSalt.orEmpty())}",
                    "appLockBiometricEnabled=${cleanSettings.appLockBiometricEnabled}",
                    "appLockRenderArmedAtEpochMillis=${cleanSettings.appLockRenderArmedAtEpochMillis ?: 0L}"
                ).joinToString("\n")
            )
        }
        terminalProfile = terminalProfileFromSettings(cleanSettings)
    }

    private fun recoverAppLockAfterCrash(settings: AppSettings): AppSettings {
        if (settings.appLockPinHash.isNullOrBlank() && settings.appLockPinSalt.isNullOrBlank() && !settings.appLockBiometricEnabled) {
            return settings
        }
        return if (
            AppLockCrashRecoveryPolicy.shouldDisableAppLockAfterCrash(CrashLogStore.load(context), System.currentTimeMillis()) ||
            AppLockCrashRecoveryPolicy.renderMarkerExpired(settings.appLockRenderArmedAtEpochMillis, System.currentTimeMillis())
        ) {
            CrashLogStore.clear(context)
            settings.copy(appLockPinHash = null, appLockPinSalt = null, appLockBiometricEnabled = false, appLockRenderArmedAtEpochMillis = null)
        } else {
            settings
        }
    }

    fun upsertServer(server: ServerProfile) {
        requireValidProxyJump(server)
        requireUniqueServerEndpoint(server)
        val index = servers.indexOfFirst { it.id == server.id }
        if (index >= 0) {
            servers[index] = server
        } else {
            servers.add(server)
            snapshots[server.id] = seedSnapshot(server.id)
        }
        saveServers()
        appendEvent(server.id, ConnectionEventLevel.Info, "Host profile saved: ${server.username}@${server.host}:${server.port}")
    }

    fun moveServer(serverId: String, delta: Int) {
        val from = servers.indexOfFirst { it.id == serverId }
        if (from < 0 || delta == 0) return
        val to = (from + delta).coerceIn(0, servers.lastIndex)
        if (from == to) return
        servers.add(to, servers.removeAt(from))
        saveServers()
    }

    fun importOpenSshConfig(text: String): String {
        val imported = OpenSshConfigParser.parse(text)
        val usedNames = servers.map { it.name }.toMutableSet()
        val usedEndpoints = servers.map { openSshImportEndpointKey(it.host, it.port, it.username) }.toMutableSet()
        var duplicateCount = 0
        val importedProfiles = linkedMapOf<OpenSshConfigHost, ServerProfile>()
        imported.forEach { host ->
            val endpointKey = openSshImportEndpointKey(host.hostName, host.port, host.user)
            if (endpointKey in usedEndpoints) {
                duplicateCount += 1
                return@forEach
            }
            usedEndpoints += endpointKey
            val profileName = uniqueOpenSshImportName(host.alias, usedNames)
            usedNames += profileName
            importedProfiles[host] = newServerFrom(
                name = profileName,
                host = host.hostName,
                port = host.port,
                username = host.user,
                group = "Imported",
                osName = "Linux",
                tags = listOf("ssh-config")
            )
        }
        val identityCredentials = mutableMapOf<String, Credential>()
        importedProfiles.keys.mapNotNull { it.identityFile?.trim()?.takeIf(String::isNotBlank) }.distinct().forEach { identityFile ->
            val credential = openSshIdentityCredential(identityFile, credentials.map { it.id }.toSet() + identityCredentials.values.map { it.id })
            identityCredentials[identityFile] = credential
            upsertCredential(credential)
        }
        importedProfiles.forEach { (host, profile) ->
            upsertServer(profile.copy(credentialId = host.identityFile?.trim()?.let { identityCredentials[it]?.id }))
        }
        val aliasToServer = servers.associateBy { it.name }.toMutableMap()
        importedProfiles.forEach { (host, profile) ->
            val savedProfile = servers.firstOrNull { it.id == profile.id } ?: profile
            aliasToServer[host.alias] = savedProfile
            aliasToServer[savedProfile.name] = savedProfile
        }
        var proxyJumpCount = 0
        var skippedProxyJumpCount = 0
        importedProfiles.forEach { (host, profile) ->
            val jumpAlias = host.proxyJumpAlias ?: return@forEach
            val jump = aliasToServer[jumpAlias]
            val savedProfile = servers.firstOrNull { it.id == profile.id } ?: profile
            val candidates = (servers + aliasToServer.values + savedProfile + listOfNotNull(jump)).distinctBy { it.id }
            if (jump == null || openSshProxyJumpImportError(savedProfile, jump, candidates) != null) {
                skippedProxyJumpCount += 1
                return@forEach
            }
            upsertServer(savedProfile.copy(proxyJumpHostId = jump.id))
            proxyJumpCount += 1
        }
        val usedForwardIds = forwards.mapTo(mutableSetOf()) { it.id }
        var forwardCount = 0
        importedProfiles.forEach { (host, profile) ->
            val savedProfile = servers.firstOrNull { it.id == profile.id } ?: profile
            host.forwards.forEachIndexed { index, forward ->
                val rule = PortForwardRule(
                    id = uniqueOpenSshForwardId(savedProfile.id, forward.type.name.lowercase(), index, usedForwardIds),
                    serverId = savedProfile.id,
                    type = forward.type,
                    localHost = forward.localHost,
                    localPort = forward.localPort,
                    remoteHost = forward.remoteHost,
                    remotePort = forward.remotePort,
                    enabled = false,
                    autoStart = false
                )
                val normalized = PortForwardValidator.validate(rule).normalized ?: return@forEachIndexed
                usedForwardIds += normalized.id
                forwards += normalized.copy(enabled = false, autoStart = false)
                forwardCount += 1
            }
        }
        if (forwardCount > 0) saveForwards()
        val identityCount = importedProfiles.keys.count { !it.identityFile.isNullOrBlank() }
        return buildString {
            append("SSH config imported: ${importedProfiles.size} host")
            append(if (importedProfiles.size == 1) "." else "s.")
            if (duplicateCount > 0) append(" $duplicateCount duplicate host${if (duplicateCount == 1) "" else "s"} skipped.")
            if (identityCount > 0) append(" $identityCount identity placeholder${if (identityCount == 1) "" else "s"} added to Vault; replace with key material before connecting.")
            if (proxyJumpCount > 0) append(" $proxyJumpCount ProxyJump route${if (proxyJumpCount == 1) "" else "s"} linked.")
            if (skippedProxyJumpCount > 0) append(" $skippedProxyJumpCount ProxyJump route${if (skippedProxyJumpCount == 1) "" else "s"} need a saved jump-host identity.")
            if (forwardCount > 0) append(" $forwardCount tunnel${if (forwardCount == 1) "" else "s"} imported disabled.")
        }
    }

    suspend fun deleteServer(serverId: String) {
        val removedForwards = forwards.filter { it.serverId == serverId }
        removedForwards.forEach { forward ->
            runCatching { sshTransport.stopForward(forward.id) }
        }
        val retainedServers = HostDeletionPolicy.removeServerAndProxyJumpReferences(servers, serverId)
        servers.clear()
        servers.addAll(retainedServers)
        forwards.removeAll { it.serverId == serverId }
        terminalSessions.removeAll { it.serverId == serverId }
        sftpBookmarks.removeAll { it.serverId == serverId }
        transfers.removeAll { it.serverId == serverId }
        removedForwards.forEach { forwardStatuses.remove(it.id) }
        snapshots.remove(serverId)
        metricHistory.remove(serverId)
        connectionEvents.remove(serverId)
        collectingMetricsFor.remove(serverId)
        lastMetricsAttemptAt.remove(serverId)
        lastMetricsSkipDiagnosticAt.remove(serverId)
        lastNoisyEventAt.keys.removeAll { it.startsWith("$serverId|") }
        saveServers()
        saveForwards()
        saveTerminalSessions()
        saveSftpBookmarks()
        saveTransfers()
        saveEvents()
    }

    fun newServerFrom(
        name: String,
        host: String,
        port: Int,
        username: String,
        group: String,
        tags: List<String>,
        osName: String,
        osVersion: String = "Unknown",
        accent: ServerAccent? = null,
        credentialId: String? = null,
        startupCommand: String = "",
        startDirectory: String = "",
        monitoringConfig: MonitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = ServerStatusRefreshPolicy.ServerBoxDefaultSeconds, useOptionalAgent = false),
        reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
        customLogoUri: String? = null,
        favorite: Boolean = false,
        environment: List<ConnectionCommand> = emptyList(),
        proxyJumpHostId: String? = null,
        notes: String = "",
        wakeOnLan: WakeOnLanConfig? = null,
        connectTimeoutSeconds: Int = 10,
        sshCompressionEnabled: Boolean = false,
        protocol: ConnectionProtocol = ConnectionProtocol.Ssh,
        moshConfig: MoshConfig = MoshConfig(),
        eternalTerminalConfig: EternalTerminalConfig = EternalTerminalConfig(),
        vncConfig: VncProfileConfig = VncProfileConfig(),
        rdpConfig: RdpProfileConfig = RdpProfileConfig(),
        fileConfig: FileProtocolConfig = FileProtocolConfig(),
        prootConfig: ProotProfileConfig = ProotProfileConfig()
    ): ServerProfile {
        require(HostCommandSafety.isAutomaticCommandSafe(startupCommand)) {
            HostCommandSafety.unsafeAutomaticCommandMessage("Startup commands")
        }
        val cleanName = name.ifBlank { host.ifBlank { "New Server" } }
        val cleanHost = host.ifBlank { "127.0.0.1" }
        HostEndpointValidator.requireValid(cleanHost)
        val cleanUser = username.ifBlank { "root" }
        val cleanGroup = group.ifBlank { "Ungrouped" }
        val cleanOs = osName.ifBlank { "Linux" }
        val id = stableId(cleanName, cleanHost)
        val profile = ServerProfile(
            id = id,
            name = cleanName,
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            username = cleanUser,
            group = cleanGroup,
            tags = (listOf("All") + tags.filter { it.isNotBlank() }).distinct(),
            osName = cleanOs,
            osVersion = osVersion.trim().ifBlank { "Unknown" },
            accent = accent ?: accentFor(cleanOs),
            credentialId = credentialId,
            terminalProfileId = terminalProfile.id,
            monitoringConfig = monitoringConfig.copy(
                pollIntervalSeconds = ServerStatusRefreshPolicy.normalize(monitoringConfig.pollIntervalSeconds)
            ),
            startupCommand = startupCommand,
            startDirectory = startDirectory,
            reconnectPolicy = reconnectPolicy.copy(
                keepAliveSeconds = reconnectPolicy.keepAliveSeconds.coerceIn(10, 120),
                maxAttempts = reconnectPolicy.maxAttempts.coerceIn(0, 10)
            ),
            customLogoUri = customLogoUri,
            favorite = favorite,
            environment = environment,
            proxyJumpHostId = proxyJumpHostId,
            notes = notes.trim().take(PersistedServerProfilePolicy.MaxNotesChars),
            wakeOnLan = wakeOnLan?.normalizedWakeOnLan(),
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(3, 60),
            sshCompressionEnabled = sshCompressionEnabled,
            protocol = protocol,
            moshConfig = moshConfig.normalized(),
            eternalTerminalConfig = eternalTerminalConfig.normalized(),
            vncConfig = vncConfig.normalized(),
            rdpConfig = rdpConfig.normalized(),
            fileConfig = fileConfig.normalized(),
            prootConfig = prootConfig.normalized()
        )
        requireValidProxyJump(profile)
        return profile
    }

    fun profileFromDraft(
        existing: ServerProfile?,
        name: String,
        host: String,
        port: Int,
        username: String,
        group: String,
        tags: List<String>,
        osName: String,
        osVersion: String = existing?.osVersion ?: "Unknown",
        accent: ServerAccent? = existing?.accent,
        credentialId: String? = existing?.credentialId,
        startupCommand: String = existing?.startupCommand.orEmpty(),
        startDirectory: String = existing?.startDirectory.orEmpty(),
        monitoringConfig: MonitoringConfig = existing?.monitoringConfig
            ?: MonitoringConfig(enabled = true, pollIntervalSeconds = ServerStatusRefreshPolicy.ServerBoxDefaultSeconds, useOptionalAgent = false),
        reconnectPolicy: ReconnectPolicy = existing?.reconnectPolicy ?: ReconnectPolicy(),
        customLogoUri: String? = existing?.customLogoUri,
        favorite: Boolean = existing?.favorite ?: false,
        environment: List<ConnectionCommand> = existing?.environment ?: emptyList(),
        proxyJumpHostId: String? = existing?.proxyJumpHostId,
        notes: String = existing?.notes.orEmpty(),
        wakeOnLan: WakeOnLanConfig? = existing?.wakeOnLan,
        connectTimeoutSeconds: Int = existing?.connectTimeoutSeconds ?: 10,
        sshCompressionEnabled: Boolean = existing?.sshCompressionEnabled ?: false,
        protocol: ConnectionProtocol = existing?.protocol ?: ConnectionProtocol.Ssh,
        moshConfig: MoshConfig = existing?.moshConfig ?: MoshConfig(),
        eternalTerminalConfig: EternalTerminalConfig = existing?.eternalTerminalConfig ?: EternalTerminalConfig(),
        vncConfig: VncProfileConfig = existing?.vncConfig ?: VncProfileConfig(),
        rdpConfig: RdpProfileConfig = existing?.rdpConfig ?: RdpProfileConfig(),
        fileConfig: FileProtocolConfig = existing?.fileConfig ?: FileProtocolConfig(),
        prootConfig: ProotProfileConfig = existing?.prootConfig ?: ProotProfileConfig()
    ): ServerProfile {
        require(HostCommandSafety.isAutomaticCommandSafe(startupCommand)) {
            HostCommandSafety.unsafeAutomaticCommandMessage("Startup commands")
        }
        if (existing == null) {
            return newServerFrom(
                name = name,
                host = host,
                port = port,
                username = username,
                group = group,
                tags = tags,
                osName = osName,
                osVersion = osVersion,
                accent = accent,
                credentialId = credentialId,
                startupCommand = startupCommand,
                startDirectory = startDirectory,
                monitoringConfig = monitoringConfig,
                reconnectPolicy = reconnectPolicy,
                customLogoUri = customLogoUri,
                favorite = favorite,
                environment = environment,
                proxyJumpHostId = proxyJumpHostId,
            notes = notes,
                wakeOnLan = wakeOnLan,
                connectTimeoutSeconds = connectTimeoutSeconds,
                sshCompressionEnabled = sshCompressionEnabled,
                protocol = protocol,
                moshConfig = moshConfig,
                eternalTerminalConfig = eternalTerminalConfig,
                vncConfig = vncConfig,
                rdpConfig = rdpConfig,
                fileConfig = fileConfig,
                prootConfig = prootConfig
        )
        }
        val cleanOs = osName.ifBlank { existing.osName }
        val cleanHost = host.ifBlank { existing.host }
        HostEndpointValidator.requireValid(cleanHost)
        val profile = existing.copy(
            name = name.ifBlank { existing.name },
            host = cleanHost,
            port = port.coerceIn(1, 65535),
            username = username.ifBlank { existing.username },
            group = group.ifBlank { existing.group },
            tags = (listOf("All") + tags.filter { it.isNotBlank() }).distinct(),
            osName = cleanOs,
            osVersion = osVersion.trim().ifBlank { existing.osVersion.ifBlank { "Unknown" } },
            accent = accent ?: accentFor(cleanOs),
            credentialId = credentialId,
            startupCommand = startupCommand,
            startDirectory = startDirectory,
            monitoringConfig = monitoringConfig.copy(
                pollIntervalSeconds = ServerStatusRefreshPolicy.normalize(monitoringConfig.pollIntervalSeconds)
            ),
            reconnectPolicy = reconnectPolicy.copy(
                keepAliveSeconds = reconnectPolicy.keepAliveSeconds.coerceIn(10, 120),
                maxAttempts = reconnectPolicy.maxAttempts.coerceIn(0, 10)
            ),
            customLogoUri = customLogoUri,
            favorite = favorite,
            environment = environment,
            proxyJumpHostId = proxyJumpHostId,
            notes = notes.trim().take(PersistedServerProfilePolicy.MaxNotesChars),
            wakeOnLan = wakeOnLan?.normalizedWakeOnLan(),
            connectTimeoutSeconds = connectTimeoutSeconds.coerceIn(3, 60),
            sshCompressionEnabled = sshCompressionEnabled,
            protocol = protocol,
            moshConfig = moshConfig.normalized(),
            eternalTerminalConfig = eternalTerminalConfig.normalized(),
            vncConfig = vncConfig.normalized(),
            rdpConfig = rdpConfig.normalized(),
            fileConfig = fileConfig.normalized(),
            prootConfig = prootConfig.normalized()
        )
        requireValidProxyJump(profile)
        return profile
    }

    private fun requireValidProxyJump(server: ServerProfile) {
        ProxyJumpPolicy.errorForSelection(server, server.proxyJumpHostId, servers)?.let { error ->
            throw IllegalArgumentException(error)
        }
    }

    private fun requireUniqueServerEndpoint(server: ServerProfile) {
        require(!HostUniquenessPolicy.hasDuplicateEndpoint(servers, server)) { HostUniquenessPolicy.DuplicateMessage }
    }

    suspend fun upsertCredentialDraft(
        existingId: String?,
        label: String,
        type: CredentialType,
        secret: String,
        passphrase: String = "",
        savePassphrase: Boolean = false
    ): Credential? {
        val existing = existingId?.let { id -> credentials.firstOrNull { it.id == id } }
        if (existing == null && secret.isBlank()) return null
        require(existing == null || existing.type == type || secret.isNotBlank()) {
            "Enter and save a ${if (type == CredentialType.PrivateKey) "private key" else "password"} before changing this identity type."
        }
        if (type == CredentialType.PrivateKey && secret.isBlank() && existing?.type == CredentialType.PrivateKey) {
            val storedPreview = existing.publicKeyPreview.orEmpty()
            require(!storedPreview.contains("file path", ignoreCase = true)) {
                "This identity was saved from a temporary key path. Re-import the private key file so ChronoSSH stores the actual key material."
            }
        }
        if (type == CredentialType.PrivateKey && secret.isNotBlank()) {
            val keyInfo = KeyMaterialInspector.inspectPrivateKey(secret)
            require(keyInfo.valid) { keyInfo.summary }
        }

        val cleanLabel = label.ifBlank {
            existing?.label ?: when (type) {
                CredentialType.Password -> "Password identity"
                CredentialType.PrivateKey -> "Private key identity"
                CredentialType.HardwareKey -> "Hardware key identity"
            }
        }
        val encryptedRef = if (secret.isNotBlank()) {
            existing?.encryptedPayloadRef
                ?.takeIf { it.startsWith("secret-") }
                ?.let {
                    try {
                        secretStore.deleteSecret(it)
                    } catch (_: Exception) {
                    }
                }
            secretStore.storeSecret(cleanLabel, secret.toByteArray(Charsets.UTF_8))
        } else {
            existing?.encryptedPayloadRef ?: "pending"
        }
        val nextPassphraseRef = when (
            CredentialPassphrasePolicy.resolve(
                isPrivateKey = type == CredentialType.PrivateKey,
                existingPassphraseRef = existing?.passphraseRef,
                passphrase = passphrase,
                savePassphrase = savePassphrase
            )
        ) {
            CredentialPassphraseAction.StoreNew -> {
                existing?.passphraseRef?.deleteSecretQuietly()
                secretStore.storeSecret("$cleanLabel passphrase", passphrase.toByteArray(Charsets.UTF_8))
            }
            CredentialPassphraseAction.KeepExisting -> existing?.passphraseRef
            CredentialPassphraseAction.DeleteExisting -> {
                existing?.passphraseRef?.deleteSecretQuietly()
                null
            }
            CredentialPassphraseAction.None -> null
        }
        val credential = Credential(
            id = existing?.id ?: stableCredentialId(cleanLabel),
            label = cleanLabel,
            type = type,
            publicKeyPreview = previewFor(type, secret).ifBlank { existing?.publicKeyPreview },
            encryptedPayloadRef = encryptedRef,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: System.currentTimeMillis(),
            passphraseRef = nextPassphraseRef,
            lastUsedEpochMillis = existing?.lastUsedEpochMillis ?: 0L,
            username = existing?.username.orEmpty(),
            group = existing?.group.orEmpty(),
            tags = existing?.tags.orEmpty(),
            notes = existing?.notes.orEmpty(),
            favorite = existing?.favorite ?: false,
            importedAtEpochMillis = existing?.importedAtEpochMillis ?: 0L
        )
        requireUniqueCredentialMaterial(credential)
        upsertCredential(credential)
        return credential
    }

    fun upsertCredential(credential: Credential) {
        requireUniqueCredentialLabel(credential.label, credential.id)
        requireUniqueCredentialMaterial(credential)
        val index = credentials.indexOfFirst { it.id == credential.id }
        if (index >= 0) {
            credentials[index] = credential
        } else {
            credentials.add(credential)
        }
        saveCredentials()
    }

    fun renameCredential(credentialId: String, label: String) {
        val cleanLabel = label.trim()
        require(cleanLabel.isNotBlank()) { "Identity name cannot be blank." }
        val index = credentials.indexOfFirst { it.id == credentialId }
        require(index >= 0) { "Identity no longer exists." }
        requireUniqueCredentialLabel(cleanLabel, credentialId)
        credentials[index] = credentials[index].copy(label = cleanLabel)
        saveCredentials()
    }

    private fun requireUniqueCredentialLabel(label: String, credentialId: String?) {
        val normalized = label.trim().lowercase()
        require(normalized.isNotBlank()) { "Identity name cannot be blank." }
        require(!CredentialUniquenessPolicy.hasDuplicateLabel(credentials, normalized, credentialId)) { CredentialUniquenessPolicy.DuplicateLabelMessage }
    }

    private fun requireUniqueCredentialMaterial(credential: Credential) {
        if (credential.type != CredentialType.PrivateKey) return
        require(!CredentialUniquenessPolicy.hasDuplicatePrivateKey(credentials, credential.publicKeyPreview, credential.id)) {
            CredentialUniquenessPolicy.DuplicatePrivateKeyMessage
        }
    }

    fun unlinkCredentialFromHosts(credentialId: String): Int {
        require(credentials.any { it.id == credentialId }) { "Identity no longer exists." }
        val result = CredentialHostLinkPolicy.unlink(servers, credentialId)
        result.servers.forEachIndexed { index, server ->
            if (servers[index] !== server) servers[index] = server
        }
        if (result.unlinkedCount > 0) saveServers()
        return result.unlinkedCount
    }

    suspend fun deleteCredential(credentialId: String) {
        val credential = credentials.firstOrNull { it.id == credentialId }
        credential?.encryptedPayloadRef
            ?.takeIf { it.startsWith("secret-") }
            ?.let {
                try {
                    secretStore.deleteSecret(it)
                } catch (_: Exception) {
                }
            }
        credential?.passphraseRef?.deleteSecretQuietly()
        val result = CredentialDeletePolicy.delete(credentials, servers, credentialId)
        credentials.clear()
        credentials.addAll(result.credentials)
        result.servers.forEachIndexed { index, server ->
            if (servers[index] !== server) servers[index] = server
        }
        if (result.deleted) saveCredentials()
        if (result.unlinkedCount > 0) saveServers()
    }

    fun credentialFor(server: ServerProfile): Credential? {
        return server.credentialId?.let { id -> credentials.firstOrNull { it.id == id } }
    }

    fun markCredentialUsedFor(server: ServerProfile, atEpochMillis: Long = System.currentTimeMillis()) {
        val credentialId = server.credentialId ?: return
        val index = credentials.indexOfFirst { it.id == credentialId }
        if (index < 0) return
        val current = credentials[index]
        credentials[index] = current.copy(lastUsedEpochMillis = atEpochMillis)
        saveCredentials()
    }

    suspend fun loadCredentialPayload(credential: Credential): String {
        require(credential.encryptedPayloadRef.startsWith("secret-")) { "No saved credential payload is available." }
        return secretStore.loadSecret(credential.encryptedPayloadRef).toString(Charsets.UTF_8)
    }

    fun knownHostFor(server: ServerProfile): KnownHost? {
        return knownHosts.firstOrNull { it.host == server.host && it.port == server.port }
    }

    suspend fun reviewOrTrustKnownHost(server: ServerProfile): KnownHost? {
        val existing = knownHostFor(server)
        val now = System.currentTimeMillis()
        if (HostKeyApprovalPolicy.canApproveStoredFingerprint(existing)) {
            val trustedHost = existing!!.copy(
                trusted = true,
                lastSeenEpochMillis = now,
                trustState = HostKeyTrustState.Trusted
            )
            upsertKnownHost(trustedHost)
            appendEvent(server.id, ConnectionEventLevel.Success, "Known-host fingerprint approved for ${server.host}:${server.port}.")
            return trustedHost
        }

        appendEvent(server.id, ConnectionEventLevel.Info, "Scanning SSH host key for ${server.host}:${server.port}.")
        val scanned = sshTransport.verifyHost(server)
        if (scanned.fingerprint.isRealFingerprint()) {
            val remembered = HostKeyObservationPolicy.remembered(
                server = server,
                existing = existing,
                algorithm = scanned.algorithm,
                fingerprint = scanned.fingerprint,
                nowEpochMillis = now
            )
            upsertKnownHost(remembered)
            appendEvent(
                server.id,
                if (remembered.trusted) ConnectionEventLevel.Success else ConnectionEventLevel.Warning,
                if (remembered.trusted) {
                    "Host key scanned and trusted for ${server.host}:${server.port}."
                } else {
                    "Host key changed for ${server.host}:${server.port}; fingerprint ${scanned.fingerprint} was not trusted."
                }
            )
            return remembered
        }
        upsertKnownHost(scanned.copy(trusted = false, trustState = scanned.trustState))
        appendEvent(
            server.id,
            ConnectionEventLevel.Error,
            "Host-key scan did not complete: ${scanned.fingerprint}"
        )
        return scanned
    }

    fun rememberHostKey(server: ServerProfile, algorithm: String, fingerprint: String) {
        val existing = knownHostFor(server)
        val now = System.currentTimeMillis()
        val knownHost = HostKeyObservationPolicy.remembered(
            server = server,
            existing = existing,
            algorithm = algorithm,
            fingerprint = fingerprint,
            nowEpochMillis = now
        )
        upsertKnownHost(knownHost)
        val changed = knownHost.trustState == HostKeyTrustState.Changed
        appendEvent(
            server.id,
            if (changed) ConnectionEventLevel.Error else ConnectionEventLevel.Success,
            if (changed) "Host key changed for ${server.host}:${server.port}; connection requires review." else "Host key remembered for ${server.host}:${server.port}."
        )
    }

    suspend fun collectMetrics(server: ServerProfile, requireTrustedHost: Boolean = true, forceDetails: Boolean = false) {
        if (!tryBeginMetricCollection(server.id)) return
        try {
            collectMetricsInternal(server, requireTrustedHost, forceDetails)
        } finally {
            synchronized(collectingMetricsFor) {
                collectingMetricsFor.remove(server.id)
            }
        }
    }

    suspend fun collectMetricsFromSession(server: ServerProfile, session: SshSession, forceDetails: Boolean = false) {
        if (!server.monitoringConfig.enabled) {
            noteMetricsRefreshSkipped(
                server,
                ConnectionEventLevel.Info,
                "monitoring is disabled for ${server.name}."
            )
            return
        }
        if (!tryBeginMetricCollection(server.id)) return
        try {
            collectMetricsFromOpenSession(server, session, closeWhenDone = false, forceDetails = forceDetails)
        } finally {
            synchronized(collectingMetricsFor) {
                collectingMetricsFor.remove(server.id)
            }
        }
    }

    suspend fun runContainerAction(server: ServerProfile, engine: String, target: String, action: String): Result<Unit> {
        val command = ContainerRuntimeActionPolicy.command(engine, target, action)
            ?: ContainerRuntimeActionPolicy.globalCommand(engine, action)
            ?: return Result.failure(IllegalArgumentException("Unsupported container action."))
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            credential?.secretBacked != true ->
                return Result.failure(IllegalStateException("Save a password or private key before running container actions."))
            knownHost?.trusted != true ->
                return Result.failure(IllegalStateException("Approve the SSH host key before running container actions."))
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Running container action: $command")
        val session = runCatching { sshTransport.connect(server, credential) }
            .getOrElse { error ->
                appendEvent(server.id, ConnectionEventLevel.Error, "Container action connection failed: ${error.message ?: error::class.java.simpleName}")
                return Result.failure(error)
            }
        return try {
            val result = session.execute(command, timeoutSeconds = 20L)
            if (result.exitCode == 0) {
                appendEvent(server.id, ConnectionEventLevel.Success, "Container action complete: $command")
                collectMetricsFromOpenSession(server, session, closeWhenDone = false, forceDetails = true)
                Result.success(Unit)
            } else {
                val detail = result.stderr.ifBlank { result.stdout }.trim().ifBlank { "exit ${result.exitCode}" }
                appendEvent(server.id, ConnectionEventLevel.Error, "Container action failed: $detail")
                Result.failure(IllegalStateException(detail))
            }
        } catch (error: Exception) {
            appendEvent(server.id, ConnectionEventLevel.Error, "Container action failed: ${error.message ?: error::class.java.simpleName}")
            Result.failure(error)
        } finally {
            session.close()
        }
    }

    suspend fun collectContainerInfo(server: ServerProfile, engine: String, target: String, action: String): Result<String> {
        val command = ContainerRuntimeActionPolicy.inspectCommand(engine, target, action)
            ?: ContainerRuntimeActionPolicy.globalCommand(engine, action)
            ?: return Result.failure(IllegalArgumentException("Unsupported container inspection."))
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            credential?.secretBacked != true ->
                return Result.failure(IllegalStateException("Save a password or private key before inspecting containers."))
            knownHost?.trusted != true ->
                return Result.failure(IllegalStateException("Approve the SSH host key before inspecting containers."))
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Collecting container ${action.trim().lowercase()} for ${target.ifBlank { engine }}.")
        val session = runCatching { sshTransport.connect(server, credential) }
            .getOrElse { error ->
                appendEvent(server.id, ConnectionEventLevel.Error, "Container inspection connection failed: ${error.message ?: error::class.java.simpleName}")
                return Result.failure(error)
            }
        return try {
            val result = session.execute(command, timeoutSeconds = 12L)
            val output = HostInfoCommandPolicy.display(result.stdout.ifBlank { result.stderr })
            if (result.exitCode == 0) {
                appendEvent(server.id, ConnectionEventLevel.Success, "Container inspection complete: ${target.ifBlank { engine }}")
                Result.success(output)
            } else {
                appendEvent(server.id, ConnectionEventLevel.Warning, "Container inspection returned exit ${result.exitCode}: ${target.ifBlank { engine }}")
                Result.success(output.ifBlank { "Command exited ${result.exitCode}." })
            }
        } catch (error: Exception) {
            appendEvent(server.id, ConnectionEventLevel.Error, "Container inspection failed: ${error.message ?: error::class.java.simpleName}")
            Result.failure(error)
        } finally {
            session.close()
        }
    }

    suspend fun runServiceAction(server: ServerProfile, unit: String, action: String): Result<Unit> {
        val command = SystemdServiceActionPolicy.command(unit, action)
            ?: return Result.failure(IllegalArgumentException("Unsupported service action."))
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            credential?.secretBacked != true ->
                return Result.failure(IllegalStateException("Save a password or private key before running service actions."))
            knownHost?.trusted != true ->
                return Result.failure(IllegalStateException("Approve the SSH host key before running service actions."))
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Running service action: $command")
        val session = runCatching { sshTransport.connect(server, credential) }
            .getOrElse { error ->
                appendEvent(server.id, ConnectionEventLevel.Error, "Service action connection failed: ${error.message ?: error::class.java.simpleName}")
                return Result.failure(error)
            }
        return try {
            val result = session.execute(command, timeoutSeconds = 20L)
            if (result.exitCode == 0) {
                appendEvent(server.id, ConnectionEventLevel.Success, "Service action complete: $command")
                collectMetricsFromOpenSession(server, session, closeWhenDone = false, forceDetails = true)
                Result.success(Unit)
            } else {
                val detail = result.stderr.ifBlank { result.stdout }.trim().ifBlank { "exit ${result.exitCode}" }
                appendEvent(server.id, ConnectionEventLevel.Error, "Service action failed: $detail")
                Result.failure(IllegalStateException(detail))
            }
        } catch (error: Exception) {
            appendEvent(server.id, ConnectionEventLevel.Error, "Service action failed: ${error.message ?: error::class.java.simpleName}")
            Result.failure(error)
        } finally {
            session.close()
        }
    }

    suspend fun collectServiceInfo(server: ServerProfile, unit: String, action: String): Result<String> {
        val command = SystemdServiceActionPolicy.inspectCommand(unit, action)
            ?: return Result.failure(IllegalArgumentException("Unsupported service inspection."))
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            credential?.secretBacked != true ->
                return Result.failure(IllegalStateException("Save a password or private key before inspecting services."))
            knownHost?.trusted != true ->
                return Result.failure(IllegalStateException("Approve the SSH host key before inspecting services."))
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Collecting service ${action.trim().lowercase()} for $unit.")
        val session = runCatching { sshTransport.connect(server, credential) }
            .getOrElse { error ->
                appendEvent(server.id, ConnectionEventLevel.Error, "Service inspection connection failed: ${error.message ?: error::class.java.simpleName}")
                return Result.failure(error)
            }
        return try {
            val result = session.execute(command, timeoutSeconds = 12L)
            val output = HostInfoCommandPolicy.display(result.stdout.ifBlank { result.stderr })
            if (result.exitCode == 0) {
                appendEvent(server.id, ConnectionEventLevel.Success, "Service inspection complete: $unit")
                Result.success(output)
            } else {
                appendEvent(server.id, ConnectionEventLevel.Warning, "Service inspection returned exit ${result.exitCode}: $unit")
                Result.success(output.ifBlank { "Command exited ${result.exitCode}." })
            }
        } catch (error: Exception) {
            appendEvent(server.id, ConnectionEventLevel.Error, "Service inspection failed: ${error.message ?: error::class.java.simpleName}")
            Result.failure(error)
        } finally {
            session.close()
        }
    }

    suspend fun runProcessAction(server: ServerProfile, pid: Int?, action: String): Result<Unit> {
        val command = ProcessActionPolicy.command(pid, action)
            ?: return Result.failure(IllegalArgumentException("Unsupported process action."))
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            credential?.secretBacked != true ->
                return Result.failure(IllegalStateException("Save a password or private key before running process actions."))
            knownHost?.trusted != true ->
                return Result.failure(IllegalStateException("Approve the SSH host key before running process actions."))
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Running process action: $command")
        val session = runCatching { sshTransport.connect(server, credential) }
            .getOrElse { error ->
                appendEvent(server.id, ConnectionEventLevel.Error, "Process action connection failed: ${error.message ?: error::class.java.simpleName}")
                return Result.failure(error)
            }
        return try {
            val result = session.execute(command, timeoutSeconds = 10L)
            if (result.exitCode == 0) {
                appendEvent(server.id, ConnectionEventLevel.Success, "Process action complete: $command")
                collectMetricsFromOpenSession(server, session, closeWhenDone = false, forceDetails = true)
                Result.success(Unit)
            } else {
                val detail = result.stderr.ifBlank { result.stdout }.trim().ifBlank { "exit ${result.exitCode}" }
                appendEvent(server.id, ConnectionEventLevel.Error, "Process action failed: $detail")
                Result.failure(IllegalStateException(detail))
            }
        } catch (error: Exception) {
            appendEvent(server.id, ConnectionEventLevel.Error, "Process action failed: ${error.message ?: error::class.java.simpleName}")
            Result.failure(error)
        } finally {
            session.close()
        }
    }

    suspend fun collectHostInfo(server: ServerProfile): Result<String> {
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            credential?.secretBacked != true ->
                return Result.failure(IllegalStateException("Save a password or private key before collecting host info."))
            knownHost?.trusted != true ->
                return Result.failure(IllegalStateException("Approve the SSH host key before collecting host info."))
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Collecting host info over SSH.")
        val session = runCatching { sshTransport.connect(server, credential) }
            .getOrElse { error ->
                appendEvent(server.id, ConnectionEventLevel.Error, "Host info connection failed: ${error.message ?: error::class.java.simpleName}")
                return Result.failure(error)
            }
        return try {
            val result = session.execute(HostInfoCommandPolicy.command, timeoutSeconds = 12L)
            val output = HostInfoCommandPolicy.display(result.stdout.ifBlank { result.stderr })
            if (result.exitCode == 0) {
                appendEvent(server.id, ConnectionEventLevel.Success, "Host info collected.")
                Result.success(output)
            } else {
                appendEvent(server.id, ConnectionEventLevel.Warning, "Host info returned exit ${result.exitCode}.")
                Result.success(output)
            }
        } catch (error: Exception) {
            appendEvent(server.id, ConnectionEventLevel.Error, "Host info failed: ${error.message ?: error::class.java.simpleName}")
            Result.failure(error)
        } finally {
            session.close()
        }
    }

    fun shouldRefreshMetrics(server: ServerProfile, nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        if (!server.monitoringConfig.enabled) return false
        synchronized(collectingMetricsFor) {
            if (server.id in collectingMetricsFor) return false
        }
        val lastAttempt = lastMetricsAttemptAt[server.id]
            ?: snapshots[server.id]?.collectedAtEpochMillis
            ?: 0L
        val intervalMillis = ServerStatusRefreshPolicy.effectiveIntervalSeconds(
            appSeconds = loadSettings().autoRefreshSeconds,
            hostSeconds = server.monitoringConfig.pollIntervalSeconds
        ) * 1000L
        val snapshot = snapshots[server.id]
        if (snapshot == null) return true
        if (nowEpochMillis - lastAttempt < intervalMillis) return false
        if (snapshot.uptime == "--") return true
        return nowEpochMillis - lastAttempt >= intervalMillis
    }

    private fun tryBeginMetricCollection(serverId: String): Boolean {
        return synchronized(collectingMetricsFor) {
            if (serverId in collectingMetricsFor) {
                false
            } else {
                collectingMetricsFor.add(serverId)
                true
            }
        }
    }

    fun isCollectingMetrics(serverId: String): Boolean {
        return synchronized(collectingMetricsFor) { serverId in collectingMetricsFor }
    }

    fun noteMetricsRefreshSkipped(
        server: ServerProfile,
        level: ConnectionEventLevel,
        reason: String,
        nowEpochMillis: Long = System.currentTimeMillis()
    ) {
        val key = "${server.id}|$reason"
        if (!MonitoringSkipDiagnosticPolicy.shouldEmit(lastMetricsSkipDiagnosticAt[key], nowEpochMillis)) return
        lastMetricsSkipDiagnosticAt[key] = nowEpochMillis
        appendEvent(server.id, level, "Metrics skipped: $reason")
    }

    private suspend fun collectMetricsInternal(server: ServerProfile, requireTrustedHost: Boolean, forceDetails: Boolean = false) {
        val credential = credentialFor(server)
        val knownHost = knownHostFor(server)
        when {
            !server.monitoringConfig.enabled -> {
                noteMetricsRefreshSkipped(
                    server,
                    ConnectionEventLevel.Info,
                    "monitoring is disabled for ${server.name}."
                )
                return
            }
            credential?.secretBacked != true -> {
                noteMetricsRefreshSkipped(
                    server,
                    ConnectionEventLevel.Warning,
                    "save a password or private key for ${server.name}."
                )
                return
            }
            requireTrustedHost && knownHost?.trusted != true -> {
                noteMetricsRefreshSkipped(
                    server,
                    ConnectionEventLevel.Warning,
                    "approve the SSH host key for ${server.name}."
                )
                return
            }
        }
        appendEvent(server.id, ConnectionEventLevel.Info, "Collecting Linux metrics over SSH.")
        lastMetricsAttemptAt[server.id] = System.currentTimeMillis()
        val session = try {
            if (requireTrustedHost) {
                sshTransport.connect(server, credential)
            } else {
                sshTransport.connectExec(
                    profile = server,
                    credential = credential,
                    hostKeyDecision = { HostKeyDecision.TrustOnce },
                    privateKeyPassphrase = null
                )
            }
        } catch (error: Exception) {
            val current = snapshots[server.id] ?: seedSnapshot(server.id)
            snapshots[server.id] = current.copy(status = ServerStatus.Offline, collectedAtEpochMillis = System.currentTimeMillis())
            appendEvent(server.id, ConnectionEventLevel.Error, "Metrics connection failed: ${error.message ?: error::class.java.simpleName}")
            return
        }
        markCredentialUsedFor(server)
        collectMetricsFromOpenSession(server, session, closeWhenDone = true, failureStatus = ServerStatus.Offline, forceDetails = forceDetails)
    }

    private suspend fun collectMetricsFromOpenSession(
        server: ServerProfile,
        session: SshSession,
        closeWhenDone: Boolean,
        failureStatus: ServerStatus = ServerStatus.Online,
        forceDetails: Boolean = false
    ) {
        val latency = snapshots[server.id]?.latencyMs
        try {
            lastMetricsAttemptAt[server.id] = System.currentTimeMillis()
            val detectedServer = runCatching { detectAndPersistHostOs(server, session) }
                .onFailure { error ->
                    appendEvent(server.id, ConnectionEventLevel.Warning, "OS detection skipped: ${error.message ?: error::class.java.simpleName}")
                }
                .getOrDefault(server)
            val collected = metricsCollector.collect(detectedServer, session).copy(latencyMs = latency)
            val displaySnapshot = MetricSnapshotMergePolicy.merge(snapshots[server.id], collected)
            snapshots[server.id] = displaySnapshot
            MetricSnapshotMergePolicy.historySample(collected)?.let(::recordMetricSnapshot)
            appendEvent(server.id, ConnectionEventLevel.Info, metricsCollector.lastVnStatDiagnostic)
            metricsCollector.lastCollectionDiagnostic
                .takeIf { it.isNotBlank() }
                ?.let { appendEvent(server.id, ConnectionEventLevel.Warning, it) }
            appendEvent(server.id, ConnectionEventLevel.Success, "Metrics updated from SSH exec.")
            if (shouldCollectMetricDetailsDuringRefresh(shouldRefreshMetricDetails(server.id), forceDetails)) {
                runCatching { metricsCollector.collectDetails(detectedServer, session).copy(latencyMs = latency) }
                    .onSuccess { details ->
                        snapshots[server.id] = MetricSnapshotMergePolicy.merge(snapshots[server.id], details)
                        if (MetricSnapshotMergePolicy.historySample(details) != null || details.hasUsefulInventoryDetails()) {
                            lastMetricDetailsAt[server.id] = System.currentTimeMillis()
                        }
                        appendEvent(server.id, ConnectionEventLevel.Info, metricsCollector.lastVnStatDiagnostic)
                        metricsCollector.lastCollectionDiagnostic
                            .takeIf { it.isNotBlank() }
                            ?.let { appendEvent(server.id, ConnectionEventLevel.Warning, it) }
                    }
                    .onFailure { error ->
                        appendEvent(server.id, ConnectionEventLevel.Warning, "Metric details skipped: ${error.message ?: error::class.java.simpleName}")
                    }
            }
        } catch (error: Exception) {
            val current = snapshots[server.id] ?: seedSnapshot(server.id)
            snapshots[server.id] = current.copy(status = failureStatus, collectedAtEpochMillis = System.currentTimeMillis())
            appendEvent(server.id, ConnectionEventLevel.Error, "Metrics failed: ${error.message ?: error::class.java.simpleName}")
        } finally {
            if (closeWhenDone) session.close()
        }
    }

    private fun MetricSnapshot.hasUsefulInventoryDetails(): Boolean {
        return processes.items.isNotEmpty() ||
            services.items.isNotEmpty() ||
            services.failedItems.isNotEmpty() ||
            docker.items.isNotEmpty() ||
            sensors.isNotEmpty() ||
            batteries.isNotEmpty() ||
            smartDisks.isNotEmpty() ||
            pveResources.isNotEmpty() ||
            gpus.isNotEmpty() ||
            packageUpdates.isNotEmpty()
    }

    private fun shouldRefreshMetricDetails(serverId: String, nowEpochMillis: Long = System.currentTimeMillis()): Boolean {
        val last = lastMetricDetailsAt[serverId]
        return MetricDetailsCollectionPolicy.shouldCollect(last, nowEpochMillis, METRIC_DETAILS_INTERVAL_MS)
    }

    private suspend fun detectAndPersistHostOs(server: ServerProfile, session: com.chrono.ssh.core.service.SshSession): ServerProfile {
        val osRelease = session.execute("cat /etc/os-release /usr/lib/os-release /etc/openwrt_release /etc/lsb-release 2>/dev/null || true").stdout
        val uname = session.execute("uname -srmo 2>/dev/null || uname -a 2>/dev/null || true").stdout.trim()
        val detected = HostOsDetector.parse(osRelease, uname) ?: return server
        val index = servers.indexOfFirst { it.id == server.id }
        if (index < 0) return server
        val current = servers[index]
        val next = current.copy(
            osName = detected.name.withoutSurroundingQuotes(),
            osVersion = detected.version.ifBlank { current.osVersion },
            accent = accentFor(detected.name)
        )
        if (next.osName != current.osName || next.osVersion != current.osVersion) {
            servers[index] = next
            saveServers()
            appendEvent(server.id, ConnectionEventLevel.Success, "Detected remote OS: ${next.osName} ${next.osVersion}".trim())
        }
        return next
    }

    fun upsertKnownHost(knownHost: KnownHost) {
        val index = knownHosts.indexOfFirst { it.id == knownHost.id }
        if (index >= 0) {
            knownHosts[index] = knownHost
        } else {
            knownHosts.add(knownHost)
        }
        saveKnownHosts()
    }

    fun deleteKnownHost(knownHostId: String) {
        val removed = knownHosts.removeAll { it.id == knownHostId }
        if (removed) saveKnownHosts()
    }

    suspend fun toggleForward(ruleId: String) {
        val index = forwards.indexOfFirst { it.id == ruleId }
        if (index < 0) return
        val current = forwards[index]
        val running = forwardStatuses[current.id]?.active == true
        if (!running) {
            val validation = PortForwardValidator.validate(current)
            if (!validation.valid) {
                forwardStatuses[current.id] = ForwardRuntimePolicy.failed(current, validation.errors.joinToString(" "))
                appendEvent(current.serverId, ConnectionEventLevel.Error, "Forward validation failed: ${validation.errors.joinToString(" ")}")
                return
            }
            val next = validation.normalized!!.copy(enabled = false)
            val server = servers.firstOrNull { it.id == next.serverId }
            if (server == null) {
                    setForwardFailed(next, "Host profile no longer exists.")
                    return
            }
            val credential = credentialFor(server)
            val knownHost = knownHostFor(server)
            when {
                credential?.secretBacked != true -> {
                    setForwardFailed(next, "Link a saved password or private key before starting this tunnel.")
                    return
                }
                knownHost?.trusted != true -> {
                    setForwardFailed(next, "Approve this host key before starting a tunnel.")
                    return
                }
            }
            forwards[index] = next
            saveForwards()
            forwardStatuses[next.id] = ForwardRuntimePolicy.starting(next)
            appendEvent(next.serverId, ConnectionEventLevel.Info, "Starting ${next.type.displayName()} forward ${next.routeLabel()}")
            val runtimeToken = nextForwardRuntimeToken(next.id)
            val onClosed = forwardClosedHandler(next, runtimeToken)
            runCatching {
                when (next.type) {
                    PortForwardType.Local -> sshTransport.startLocalForward(server, credential, next, onClosed)
                    PortForwardType.Remote -> sshTransport.startRemoteForward(server, credential, next, onClosed)
                    PortForwardType.DynamicSocks -> sshTransport.startDynamicSocksForward(server, credential, next, onClosed)
                }
            }.onSuccess { status ->
                forwardStatuses[next.id] = status
                appendEvent(next.serverId, ConnectionEventLevel.Success, status.lastMessage)
            }.onFailure { error ->
                setForwardFailed(next, error.message ?: error::class.java.simpleName)
            }
            return
        }
        invalidateForwardRuntime(current.id)
        sshTransport.stopForward(current.id)
        val next = current.copy(enabled = false)
        forwards[index] = next
        saveForwards()
        forwardStatuses[next.id] = ForwardRuntimePolicy.stopped(next)
        appendEvent(next.serverId, ConnectionEventLevel.Info, "Stopped ${next.type.displayName()} forward ${next.routeLabel()}")
    }

    suspend fun startAutoStartForwards() {
        forwards
            .filter { ForwardAutoStartPolicy.shouldStart(it, forwardStatuses[it.id]) }
            .forEach { rule -> toggleForward(rule.id) }
    }

    private fun setForwardFailed(rule: PortForwardRule, message: String) {
        invalidateForwardRuntime(rule.id)
        val index = forwards.indexOfFirst { it.id == rule.id }
        if (index >= 0 && forwards[index].enabled) {
            forwards[index] = forwards[index].copy(enabled = false)
            saveForwards()
        }
        val status = ForwardRuntimePolicy.failed(rule, message)
        forwardStatuses[rule.id] = status
        appendEvent(rule.serverId, ConnectionEventLevel.Error, "Forward failed: ${status.lastError ?: status.lastMessage}")
    }

    private fun nextForwardRuntimeToken(ruleId: String): Long {
        val token = ++forwardRuntimeTokenCounter
        forwardRuntimeTokens[ruleId] = token
        return token
    }

    private fun invalidateForwardRuntime(ruleId: String) {
        forwardRuntimeTokens.remove(ruleId)
    }

    private fun forwardClosedHandler(rule: PortForwardRule, runtimeToken: Long): (ForwardStatus) -> Unit = { closedStatus ->
        mainHandler.post {
            if (!shouldAcceptForwardClosedCallback(forwardRuntimeTokens[rule.id], runtimeToken)) return@post
            invalidateForwardRuntime(rule.id)
            val index = forwards.indexOfFirst { it.id == rule.id }
            if (index >= 0 && forwards[index].enabled) {
                forwards[index] = forwardRuleAfterRuntimeClosed(forwards[index])
                saveForwards()
            }
            forwardStatuses[rule.id] = closedStatus
            val level = if (closedStatus.lastError == null) ConnectionEventLevel.Info else ConnectionEventLevel.Error
            appendEvent(rule.serverId, level, closedStatus.lastError ?: closedStatus.lastMessage)
        }
    }

    suspend fun upsertForward(rule: PortForwardRule): Result<PortForwardRule> {
        val validation = PortForwardValidator.validate(rule.copy(enabled = false))
        val normalized = validation.normalized
        if (!validation.valid || normalized == null) {
            return Result.failure(IllegalArgumentException(validation.errors.joinToString(" ")))
        }
        if (forwards.any { it.id != normalized.id && it.sameForwardRoute(normalized) }) {
            return Result.failure(IllegalArgumentException("A forward with this route already exists."))
        }
        val index = forwards.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            val existing = forwards[index]
            if (forwardStatuses[existing.id]?.active == true) {
                invalidateForwardRuntime(existing.id)
                sshTransport.stopForward(existing.id)
                forwardStatuses[existing.id] = ForwardRuntimePolicy.stopped(existing)
            }
            forwards[index] = normalized.copy(enabled = false)
        } else {
            forwards.add(normalized.copy(enabled = false))
        }
        saveForwards()
        appendEvent(normalized.serverId, ConnectionEventLevel.Info, "Saved ${normalized.type.displayName()} forward ${normalized.routeLabel()}")
        return Result.success(normalized.copy(enabled = false))
    }

    suspend fun deleteForward(ruleId: String) {
        val rule = forwards.firstOrNull { it.id == ruleId } ?: return
        invalidateForwardRuntime(ruleId)
        sshTransport.stopForward(ruleId)
        forwards.removeAll { it.id == ruleId }
        forwardStatuses.remove(ruleId)
        saveForwards()
        appendEvent(rule.serverId, ConnectionEventLevel.Info, "Deleted ${rule.type.displayName()} forward ${rule.routeLabel()}")
    }

    fun upsertSnippet(snippet: Snippet): Result<Snippet> {
        val validation = SnippetValidator.validate(snippet)
        val normalized = validation.normalized
        if (!validation.valid || normalized == null) {
            return Result.failure(IllegalArgumentException(validation.errors.joinToString(" ")))
        }
        if (snippets.any { it.id != normalized.id && it.sameSnippetName(normalized) }) {
            return Result.failure(IllegalArgumentException("A snippet with this name already exists."))
        }
        val index = snippets.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            snippets[index] = normalized
        } else {
            snippets.add(normalized)
        }
        saveSnippets()
        return Result.success(normalized)
    }

    private fun PortForwardRule.sameForwardRoute(other: PortForwardRule): Boolean {
        return serverId == other.serverId &&
            type == other.type &&
            localHost.trim().lowercase() == other.localHost.trim().lowercase() &&
            localPort == other.localPort &&
            remoteHost.trim().lowercase() == other.remoteHost.trim().lowercase() &&
            remotePort == other.remotePort
    }

    private fun Snippet.sameSnippetName(other: Snippet): Boolean {
        return name.trim().lowercase() == other.name.trim().lowercase() &&
            serverScope.orEmpty().trim() == other.serverScope.orEmpty().trim()
    }

    fun deleteSnippet(snippetId: String) {
        if (snippets.removeAll { it.id == snippetId }) {
            saveSnippets()
        }
    }

    fun upsertTerminalSession(record: TerminalSessionRecord) {
        val normalized = TerminalSessionPersistencePolicy.normalizePersisted(record) ?: return
        val index = terminalSessions.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            terminalSessions[index] = TerminalSessionUpdatePolicy.merge(terminalSessions[index], normalized)
        } else {
            terminalSessions.add(TerminalSessionUpdatePolicy.merge(null, normalized))
        }
        saveTerminalSessions()
    }

    fun closeTerminalSession(sessionId: String) {
        val index = terminalSessions.indexOfFirst { it.id == sessionId }
        if (index < 0) return
        terminalSessions[index] = terminalSessions[index].copy(
            status = ServerStatus.Offline,
            lastActiveEpochMillis = System.currentTimeMillis()
        )
        saveTerminalSessions()
    }

    fun upsertSftpBookmark(bookmark: SftpBookmark) {
        val normalized = SftpBookmarkPersistencePolicy.normalizeLoaded(bookmark) ?: return
        val index = sftpBookmarks.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            sftpBookmarks[index] = normalized
        } else {
            sftpBookmarks.add(normalized)
        }
        saveSftpBookmarks()
    }

    fun deleteSftpBookmark(bookmarkId: String) {
        val removed = sftpBookmarks.removeAll { it.id == bookmarkId }
        if (removed) saveSftpBookmarks()
    }

    fun upsertTransfer(record: TransferRecord) {
        val normalized = TransferPersistencePolicy.normalizePersisted(record) ?: return
        val index = transfers.indexOfFirst { it.id == normalized.id }
        val stored = if (index >= 0) {
            TransferStateReducer.reduce(transfers[index], normalized)
        } else {
            normalized
        }
        if (index >= 0) {
            transfers[index] = stored
        } else {
            transfers.add(stored)
        }
        TransferCancellationRegistry.unregisterIfTerminal(stored.id, stored.state)
        saveTransfers()
    }

    fun cancelTransfer(transferId: String) {
        val index = transfers.indexOfFirst { it.id == transferId }
        if (index < 0) return
        val transfer = transfers[index]
        if (TransferCompletionPolicy.isTerminal(transfer.state)) return
        TransferCancellationRegistry.cancel(transferId)
        transfers[index] = transfer.copy(
            state = TransferRecordState.Cancelled,
            progress = transfer.progress.coerceIn(0f, 1f),
            message = "Cancelled",
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        TransferCancellationRegistry.unregister(transferId)
        saveTransfers()
    }

    fun clearFinishedTransfers() {
        val finished = transfers.filter { TransferCompletionPolicy.isTerminal(it.state) }
        finished.forEach { TransferCancellationRegistry.unregister(it.id) }
        transfers.removeAll(finished)
        saveTransfers()
    }

    fun updateProbeResult(serverId: String, status: ServerStatus, latencyMs: Int?, message: String) {
        val current = snapshots[serverId] ?: seedSnapshot(serverId)
        snapshots[serverId] = current.copy(
            status = status,
            latencyMs = latencyMs,
            collectedAtEpochMillis = System.currentTimeMillis()
        )
        val level = when (status) {
            ServerStatus.Online -> ConnectionEventLevel.Success
            ServerStatus.Connecting -> ConnectionEventLevel.Info
            ServerStatus.Unknown -> ConnectionEventLevel.Warning
            ServerStatus.Offline -> ConnectionEventLevel.Error
        }
        appendEvent(serverId, level, message)
    }

    fun markConnecting(serverId: String) {
        val current = snapshots[serverId] ?: seedSnapshot(serverId)
        snapshots[serverId] = current.copy(status = ServerStatus.Connecting, collectedAtEpochMillis = System.currentTimeMillis())
        appendEvent(serverId, ConnectionEventLevel.Info, "Opening TCP probe for ${serverLabel(serverId)}")
    }

    fun appendEvent(serverId: String, level: ConnectionEventLevel, message: String) {
        val now = System.currentTimeMillis()
        if (shouldSuppressNoisyEvent(serverId, message, now)) return
        val event = ConnectionEvent(
            id = UUID.randomUUID().toString(),
            serverId = serverId,
            atEpochMillis = now,
            level = level,
            message = message
        )
        val normalized = ConnectionEventPersistencePolicy.normalizePersisted(event) ?: return
        val next = (connectionEvents[serverId].orEmpty() + normalized).takeLast(80)
        connectionEvents[serverId] = next
        saveEvents()
    }

    private fun shouldSuppressNoisyEvent(serverId: String, message: String, nowEpochMillis: Long): Boolean {
        val bucket = message.noisyConnectionEventBucket() ?: return false
        val key = "$serverId|$bucket"
        val previous = lastNoisyEventAt[key]
        lastNoisyEventAt[key] = nowEpochMillis
        return previous != null && nowEpochMillis - previous < 15_000L
    }

    fun recordCrashContext(message: String) {
        runCatching {
            CrashLogStore.appendMessage(context, message)
            reloadCrashLogs()
        }
    }

    fun recordCrash(error: Throwable) {
        runCatching {
            CrashLogStore.append(context, error)
            reloadCrashLogs()
        }
    }

    fun clearCrashLogs() {
        CrashLogStore.clear(context)
        crashLogs.clear()
    }

    private fun reloadCrashLogs() {
        crashLogs.clear()
        crashLogs.addAll(CrashLogStore.load(context).sortedByDescending { it.atEpochMillis })
    }

    fun snapshotFor(serverId: String): MetricSnapshot = snapshots[serverId] ?: seedSnapshot(serverId)

    fun metricHistoryFor(serverId: String): List<MetricSnapshot> = metricHistory[serverId].orEmpty()

    private fun seedSnapshot(serverId: String): MetricSnapshot {
        return MetricSnapshotSeedPolicy.seed(serverId, metricHistory[serverId].orEmpty(), ::unavailableSnapshot)
    }

    private fun recordMetricSnapshot(snapshot: MetricSnapshot) {
        if (!MetricSnapshotSeedPolicy.hasSeedableData(snapshot)) return
        metricHistory[snapshot.serverId] = MetricHistoryRetentionPolicy.retain(
            samples = metricHistory[snapshot.serverId].orEmpty() + snapshot,
            nowEpochMillis = snapshot.collectedAtEpochMillis
        )
    }

    fun exportBackupPreview(): String {
        return buildString {
            appendLine("ChronoSSH backup preview v1")
            appendLine("createdAt=${System.currentTimeMillis()}")
            appendLine("hosts=${servers.size}")
            servers.forEach { server ->
                appendLine("host|${server.id}|${server.name}|${server.username}@${server.host}:${server.port}|${server.group}|${server.tags.joinToString(",")}")
            }
            appendLine("credentials=${credentials.size}")
            credentials.forEach { credential ->
                appendLine("credential|${credential.id}|${credential.label}|${credential.type}|ready=${credential.secretBacked}")
            }
            appendLine("knownHosts=${knownHosts.size}")
            knownHosts.forEach { knownHost ->
                appendLine("knownHost|${knownHost.host}:${knownHost.port}|${knownHost.algorithm}|${knownHost.fingerprint}|${knownHost.trustState}")
            }
            appendLine("forwards=${forwards.size}")
            forwards.forEach { forward ->
                appendLine("forward|${forward.id}|${forward.serverId}|${forward.type}|${forward.localHost}:${forward.localPort}|${forward.remoteHost}:${forward.remotePort}|enabled=${forward.enabled}")
            }
            appendLine("snippets=${snippets.size}")
            snippets.forEach { snippet ->
                appendLine("snippet|${snippet.id}|${snippet.name}|${snippet.tags.joinToString(",")}")
            }
            appendLine("sftpBookmarks=${sftpBookmarks.size}")
            sftpBookmarks.forEach { bookmark ->
                appendLine("bookmark|${bookmark.id}|${bookmark.serverId}|${bookmark.label}|${bookmark.path}")
            }
            appendLine("transfers=${transfers.size}")
            transfers.takeLast(20).forEach { transfer ->
                appendLine("transfer|${transfer.id}|${transfer.serverId}|${transfer.direction}|${transfer.remotePath}|${transfer.state}|${transfer.progress}")
            }
            appendLine("terminalSessions=${terminalSessions.size}")
            terminalSessions.takeLast(20).forEach { session ->
                appendLine("terminal|${session.id}|${session.serverId}|${session.title}|${session.status}|${session.lastActiveEpochMillis}")
            }
            appendLine("note=Credential payloads are excluded from this metadata export.")
        }
    }

    fun exportMetadataBackup(): String {
        val settings = loadSettings()
        return buildString {
            appendLine("chronoSSH-backup-v1")
            appendLine("createdAt=${System.currentTimeMillis()}")
            appendLine("[settings]")
            appendLine(
                listOf(
                    settings.themeModeName,
                    settings.themeFamilyId,
                    settings.terminalFontSizeSp.toString(),
                    settings.terminalScrollbackLines.toString(),
                    settings.terminalCursorStyle.name,
                    settings.terminalThemeName,
                    settings.terminalFontFamily,
                    settings.terminalBracketedPaste.toString(),
                    settings.terminalHapticFeedback.toString(),
                    TerminalAccessoryKeyPolicy.normalizeCsv(settings.terminalAccessoryKeys),
                    settings.terminalAccessorySingleRow.toString(),
                    settings.autoRefreshSeconds.toString(),
                    settings.terminalSideMarginDp.toString(),
                    settings.terminalRightMarginDp.toString(),
                    settings.terminalAccessoryPopups.toString(),
                    settings.terminalAccessoryFullScroll.toString(),
                    settings.terminalKeepScreenOn.toString(),
                    settings.serverCardNetworkMode.name,
                    settings.serverCardDiskMode.name,
                    settings.appLockBiometricEnabled.toString(),
                    settings.serverMetricColorPreset.name,
                    normalizeSftpSortModeName(settings.sftpDefaultSortModeName),
                    settings.sftpDefaultSortDescending.toString(),
                    settings.sftpShowHiddenByDefault.toString(),
                    ServerDetailCard.sanitizeOrderCsv(settings.serverDetailCardOrder),
                    ServerDetailCard.sanitizeHiddenCsv(settings.serverDetailHiddenCards)
                ).joinToString("|") { escape(it) }
            )
            appendLine("[servers]")
            servers.forEach { appendLine(encodeServer(it)) }
            appendLine("[credentials]")
            credentials.forEach { credential ->
                appendLine(encodeCredential(credential.copy(encryptedPayloadRef = "excluded", passphraseRef = null)))
            }
            appendLine("[knownHosts]")
            knownHosts.forEach { appendLine(encodeKnownHost(it)) }
            appendLine("[snippets]")
            snippets.forEach { appendLine(encodeSnippet(it)) }
            appendLine("[forwards]")
            forwards.forEach { appendLine(encodeForward(it)) }
            appendLine("[terminalSessions]")
            terminalSessions.takeLast(80).forEach { appendLine(encodeTerminalSession(it)) }
            appendLine("[sftpBookmarks]")
            sftpBookmarks.forEach { appendLine(encodeSftpBookmark(it)) }
            appendLine("[transfers]")
            transfers.takeLast(120).forEach { appendLine(encodeTransfer(it)) }
            appendLine("[end]")
        }
    }

    fun inspectBackupImport(text: String): BackupImportReport {
        BackupImportTextPolicy.rejectionMessage(text)?.let { message ->
            return BackupImportReport(valid = false, sections = emptyMap(), skippedRows = 0, message = message)
        }
        if (!text.lineSequence().firstOrNull().orEmpty().startsWith("chronoSSH-backup-v1")) {
            return BackupImportReport(valid = false, sections = emptyMap(), skippedRows = 0, message = "This is not a ChronoSSH metadata backup.")
        }
        val sections = linkedMapOf<String, Int>()
        var current: String? = null
        var skipped = 0
        text.lineSequence().drop(1).forEach { raw ->
            val line = raw.trim()
            when {
                line.isBlank() -> Unit
                current == null && line.startsWith("createdAt=") -> Unit
                line.startsWith("[") && line.endsWith("]") -> current = line.trim('[', ']')
                current != null -> sections[current.orEmpty()] = (sections[current.orEmpty()] ?: 0) + 1
                else -> skipped += 1
            }
        }
        val parsedSections = parseBackupSections(text)
        val unknownRows = parsedSections
            .filterKeys { it !in BackupImportKnownSections && it != "end" }
            .values
            .sumOf { it.size }
        val malformedRows = countMalformedBackupRows(parsedSections)
        val credentialMetadataRows = parsedSections["credentials"].orEmpty().count { decodeCredential(it) != null }
        val visibleSections = sections.filterKeys { it in BackupImportKnownSections && it != "end" }
        return BackupImportReport(
            valid = true,
            sections = visibleSections,
            skippedRows = skipped + unknownRows,
            malformedRows = malformedRows,
            credentialMetadataRows = credentialMetadataRows,
            message = BackupImportReportFormatter.inspected(
                recordCount = visibleSections.values.sum(),
                malformedRows = malformedRows,
                unknownRows = unknownRows,
                credentialMetadataRows = credentialMetadataRows
            )
        )
    }

    fun importMetadataBackup(text: String): BackupImportReport {
        val inspected = inspectBackupImport(text)
        if (!inspected.valid) return inspected
        val sections = parseBackupSections(text)
        var skipped = inspected.skippedRows
        var mergeStats = BackupMergeStats()
        var stagedSettings: AppSettings? = null
        val stagedServers = servers.toMutableList()
        val stagedCredentials = credentials.toMutableList()
        val stagedKnownHosts = knownHosts.toMutableList()
        val stagedSnippets = snippets.toMutableList()
        val stagedForwards = forwards.toMutableList()
        val stagedTerminalSessions = terminalSessions.toMutableList()
        val stagedSftpBookmarks = sftpBookmarks.toMutableList()
        val stagedTransfers = transfers.toMutableList()

        sections["settings"]?.firstOrNull()?.let { line ->
            val fields = splitEscaped(line, '|')
            val importedSettings = BackupSettingsImportPolicy.importedSettings(fields, loadSettings())
            if (importedSettings != null) {
                stagedSettings = importedSettings
                mergeStats += BackupMergeStats(updated = 1)
            } else {
                skipped += 1
                mergeStats += BackupMergeStats(skipped = 1)
            }
        }

        mergeStats += mergeDecoded(sections["servers"].orEmpty(), ::decodeServer) { record ->
            val sanitized = BackupServerPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            stagedServers.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        mergeStats += mergeDecoded(sections["credentials"].orEmpty(), ::decodeCredential) { record ->
            val sanitized = BackupCredentialPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            stagedCredentials.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        mergeStats += mergeDecoded(sections["knownHosts"].orEmpty(), ::decodeKnownHost) { record ->
            val sanitized = BackupKnownHostPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            stagedKnownHosts.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        mergeStats += mergeDecoded(sections["snippets"].orEmpty(), ::decodeSnippet) { record ->
            val sanitized = BackupSnippetPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            stagedSnippets.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        val importedForwardIds = mutableSetOf<String>()
        mergeStats += mergeDecoded(sections["forwards"].orEmpty(), ::decodeForward) { record ->
            val sanitized = BackupForwardPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            importedForwardIds += sanitized.id
            stagedForwards.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        mergeStats += mergeDecoded(sections["terminalSessions"].orEmpty(), ::decodeTerminalSession) { record ->
            val sanitized = BackupTerminalSessionPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            stagedTerminalSessions.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        mergeStats += mergeDecoded(sections["sftpBookmarks"].orEmpty(), ::decodeSftpBookmark) { record ->
            val sanitized = BackupSftpBookmarkPolicy.sanitizeImportedMetadata(record)
                ?: return@mergeDecoded BackupMergeStats(skipped = 1)
            stagedSftpBookmarks.upsertByIdWithStats(sanitized.id, sanitized) { it.id }
        }
        mergeStats += mergeDecoded(sections["transfers"].orEmpty(), ::decodeTransfer) { record ->
            stagedTransfers.upsertByIdWithStats(record.id, record) { it.id }
        }
        val referencePrune = BackupImportReferencePolicy.prune(
            servers = stagedServers,
            credentials = stagedCredentials,
            forwards = stagedForwards,
            terminalSessions = stagedTerminalSessions,
            sftpBookmarks = stagedSftpBookmarks,
            transfers = stagedTransfers
        )
        stagedServers.clear()
        stagedServers.addAll(referencePrune.servers)
        stagedForwards.clear()
        stagedForwards.addAll(referencePrune.forwards)
        stagedTerminalSessions.clear()
        stagedTerminalSessions.addAll(referencePrune.terminalSessions)
        stagedSftpBookmarks.clear()
        stagedSftpBookmarks.addAll(referencePrune.sftpBookmarks)
        stagedTransfers.clear()
        stagedTransfers.addAll(referencePrune.transfers)
        val retainedForwardStatusIds = BackupImportForwardStatusPolicy.retainedStatusIds(
            currentStatusIds = forwardStatuses.keys,
            retainedForwards = referencePrune.forwards,
            importedForwardIds = importedForwardIds
        )
        servers.clear()
        servers.addAll(stagedServers)
        credentials.clear()
        credentials.addAll(stagedCredentials)
        knownHosts.clear()
        knownHosts.addAll(stagedKnownHosts)
        snippets.clear()
        snippets.addAll(stagedSnippets)
        forwards.clear()
        forwards.addAll(stagedForwards)
        terminalSessions.clear()
        terminalSessions.addAll(stagedTerminalSessions)
        sftpBookmarks.clear()
        sftpBookmarks.addAll(stagedSftpBookmarks)
        transfers.clear()
        transfers.addAll(stagedTransfers)
        forwardStatuses.keys.retainAll(retainedForwardStatusIds)
        val retainedServerIds = servers.map { it.id }.toSet()
        snapshots.keys.retainAll(retainedServerIds)
        servers.forEach { server -> snapshots.putIfAbsent(server.id, seedSnapshot(server.id)) }
        mergeStats += BackupMergeStats(skipped = referencePrune.prunedRows)
        skipped += mergeStats.skipped
        val credentialMetadataRows = sections["credentials"].orEmpty().count { decodeCredential(it) != null }

        stagedSettings?.let(::saveSettings)
        saveServers()
        saveCredentials()
        saveKnownHosts()
        saveSnippets()
        saveForwards()
        saveTerminalSessions()
        saveSftpBookmarks()
        saveTransfers()

        return inspectBackupImport(text).copy(
            skippedRows = skipped,
            insertedRows = mergeStats.inserted,
            updatedRows = mergeStats.updated,
            credentialMetadataRows = credentialMetadataRows,
            message = BackupImportReportFormatter.imported(credentialMetadataRows)
        )
    }

    private fun countMalformedBackupRows(sections: Map<String, List<String>>): Int {
        var malformed = 0
        sections["settings"]?.let { lines ->
            if (lines.isNotEmpty()) {
                val firstFields = splitEscaped(lines.first(), '|')
                if (firstFields.size < 8) malformed += 1
                malformed += (lines.size - 1).coerceAtLeast(0)
            }
        }
        malformed += sections["servers"].orEmpty().count { decodeServer(it) == null }
        malformed += sections["credentials"].orEmpty().count { decodeCredential(it) == null }
        malformed += sections["knownHosts"].orEmpty().count { decodeKnownHost(it) == null }
        malformed += sections["snippets"].orEmpty().count { decodeSnippet(it) == null }
        malformed += sections["forwards"].orEmpty().count { decodeForward(it) == null }
        malformed += sections["terminalSessions"].orEmpty().count { decodeTerminalSession(it) == null }
        malformed += sections["sftpBookmarks"].orEmpty().count { decodeSftpBookmark(it) == null }
        malformed += sections["transfers"].orEmpty().count { decodeTransfer(it) == null }
        return malformed
    }

    private fun serverLabel(serverId: String): String {
        return servers.firstOrNull { it.id == serverId }?.let { "${it.host}:${it.port}" } ?: serverId
    }

    private fun saveServers() {
        serversFile.writeText(servers.joinToString("\n") { encodeServer(it) })
    }

    private fun loadServers(): List<ServerProfile> {
        return loadRecords(serversFile, ::decodeServer)
    }

    private fun saveEvents() {
        val events = connectionEvents.values.flatten().sortedBy { it.atEpochMillis }.takeLast(220)
        eventsFile.writeText(events.joinToString("\n") { encodeEvent(it) })
    }

    private fun loadEvents(): List<ConnectionEvent> {
        return loadRecords(eventsFile, ::decodeEvent)
    }

    private fun saveCredentials() {
        credentialsFile.writeText(credentials.joinToString("\n") { encodeCredential(it) })
    }

    private fun loadCredentials(): List<Credential> {
        return loadRecords(credentialsFile, ::decodeCredential)
    }

    private fun saveKnownHosts() {
        knownHostsFile.writeText(knownHosts.joinToString("\n") { encodeKnownHost(it) })
    }

    private fun loadKnownHosts(): List<KnownHost> {
        return loadRecords(knownHostsFile, ::decodeKnownHost)
    }

    private fun saveSnippets() {
        snippetsFile.writeText(snippets.joinToString("\n") { encodeSnippet(it) })
    }

    private fun loadSnippets(): List<Snippet> {
        return loadRecords(snippetsFile, ::decodeSnippet)
    }

    private fun saveForwards() {
        forwardsFile.writeText(forwards.joinToString("\n") { encodeForward(it) })
    }

    private fun loadForwards(): List<PortForwardRule> {
        return loadRecords(forwardsFile, ::decodeForward)
    }

    private fun saveTerminalSessions() {
        terminalSessionsFile.writeText(terminalSessions.joinToString("\n") { encodeTerminalSession(it) })
    }

    private fun loadTerminalSessions(): List<TerminalSessionRecord> {
        return loadRecords(terminalSessionsFile, ::decodeTerminalSession)
    }

    private fun saveSftpBookmarks() {
        sftpBookmarksFile.writeText(sftpBookmarks.joinToString("\n") { encodeSftpBookmark(it) })
    }

    private fun loadSftpBookmarks(): List<SftpBookmark> {
        return loadRecords(sftpBookmarksFile, ::decodeSftpBookmark)
    }

    private fun saveTransfers() {
        transfersFile.writeText(transfers.takeLast(120).joinToString("\n") { encodeTransfer(it) })
    }

    private fun loadTransfers(): List<TransferRecord> {
        return loadRecords(transfersFile, ::decodeTransfer).map(TransferPersistencePolicy::normalizeLoaded)
    }

    private fun <T> loadRecords(file: File, decode: (String) -> T?): List<T> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .filter { it.isNotBlank() }
                .mapNotNull { line -> runCatching { decode(line) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    private fun encodeServer(server: ServerProfile): String {
        return listOf(
            server.id,
            server.name,
            server.host,
            server.port.toString(),
            server.username,
            server.group,
            server.tags.joinToString(","),
            server.osName,
            server.osVersion,
            server.accent.name,
            server.accent.argb.toString(),
            server.credentialId.orEmpty(),
            server.terminalProfileId,
            server.monitoringConfig.enabled.toString(),
            server.monitoringConfig.pollIntervalSeconds.toString(),
            server.monitoringConfig.useOptionalAgent.toString(),
            server.favorite.toString(),
            server.startupCommand,
            server.startDirectory,
            server.proxyJumpHostId.orEmpty(),
            server.reconnectPolicy.autoReconnect.toString(),
            server.reconnectPolicy.keepAliveSeconds.toString(),
            server.reconnectPolicy.maxAttempts.toString(),
            server.customLogoUri.orEmpty(),
            HostEnvironmentPolicy.serialize(server.environment),
            server.notes,
            server.wakeOnLan?.macAddress.orEmpty(),
            server.wakeOnLan?.broadcastAddress.orEmpty(),
            server.wakeOnLan?.secureOnPassword.orEmpty(),
            server.connectTimeoutSeconds.coerceIn(3, 60).toString(),
            server.sshCompressionEnabled.toString(),
            server.protocol.name,
            encodeMoshConfig(server.moshConfig),
            encodeEtConfig(server.eternalTerminalConfig),
            encodeVncConfig(server.vncConfig),
            encodeRdpConfig(server.rdpConfig),
            encodeFileConfig(server.fileConfig),
            encodeProotConfig(server.prootConfig)
        ).joinToString("|") { escape(it) }
    }

    private fun decodeServer(line: String): ServerProfile? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 17) return null
        return PersistedServerProfilePolicy.normalizeLoaded(ServerProfile(
            id = fields[0],
            name = fields[1],
            host = fields[2],
            port = fields[3].toIntOrNull()?.coerceIn(1, 65535) ?: 22,
            username = fields[4],
            group = fields[5],
            tags = fields[6].split(",").filter { it.isNotBlank() }.ifEmpty { listOf("All") },
            osName = fields[7],
            osVersion = fields[8],
            accent = ServerAccent(fields[9], fields[10].toLongOrNull() ?: accentFor(fields[7]).argb),
            credentialId = fields[11].ifBlank { null },
            terminalProfileId = fields[12].ifBlank { terminalProfile.id },
            monitoringConfig = MonitoringConfig(
                enabled = fields[13].toBooleanStrictOrNull() ?: true,
                pollIntervalSeconds = ServerStatusRefreshPolicy.normalize(fields[14].toIntOrNull()),
                useOptionalAgent = fields[15].toBooleanStrictOrNull() ?: false
            ),
            favorite = fields[16].toBooleanStrictOrNull() ?: false,
            startupCommand = PersistedServerProfilePolicy.safeStartupCommand(fields.getOrNull(17).orEmpty()),
            startDirectory = fields.getOrNull(18).orEmpty(),
            proxyJumpHostId = fields.getOrNull(19)?.ifBlank { null },
            reconnectPolicy = ReconnectPolicy(
                autoReconnect = fields.getOrNull(20)?.toBooleanStrictOrNull() ?: true,
                keepAliveSeconds = fields.getOrNull(21)?.toIntOrNull()?.coerceIn(5, 300) ?: 30,
                maxAttempts = fields.getOrNull(22)?.toIntOrNull()?.coerceIn(0, 20) ?: 3
            ),
            customLogoUri = fields.getOrNull(23)?.ifBlank { null },
            environment = fields.getOrNull(24)?.let { HostEnvironmentPolicy.parse(it).entries } ?: emptyList(),
            notes = fields.getOrNull(25).orEmpty(),
            wakeOnLan = fields.getOrNull(26)?.takeIf { it.isNotBlank() }?.let { mac ->
                WakeOnLanConfig(
                    macAddress = mac,
                    broadcastAddress = fields.getOrNull(27).orEmpty(),
                    secureOnPassword = fields.getOrNull(28)?.ifBlank { null }
                )
            },
            connectTimeoutSeconds = fields.getOrNull(29)?.toIntOrNull()?.coerceIn(3, 60) ?: 10,
            sshCompressionEnabled = fields.getOrNull(30)?.toBooleanStrictOrNull() ?: false,
            protocol = fields.getOrNull(31).toConnectionProtocol(),
            moshConfig = decodeMoshConfig(fields.getOrNull(32)),
            eternalTerminalConfig = decodeEtConfig(fields.getOrNull(33)),
            vncConfig = decodeVncConfig(fields.getOrNull(34)),
            rdpConfig = decodeRdpConfig(fields.getOrNull(35)),
            fileConfig = decodeFileConfig(fields.getOrNull(36)),
            prootConfig = decodeProotConfig(fields.getOrNull(37))
        ))
    }

    private fun encodeEvent(event: ConnectionEvent): String {
        return listOf(
            event.id,
            event.serverId,
            event.atEpochMillis.toString(),
            event.level.name,
            event.message
        ).joinToString("|") { escape(it) }
    }

    private fun decodeEvent(line: String): ConnectionEvent? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 5) return null
        val level = ConnectionEventPersistencePolicy.levelFromPersisted(fields[3]) ?: return null
        return ConnectionEventPersistencePolicy.normalizePersisted(ConnectionEvent(
            id = fields[0],
            serverId = fields[1],
            atEpochMillis = fields[2].toLongOrNull() ?: 0L,
            level = level,
            message = fields[4]
        ))
    }

    private fun encodeCredential(credential: Credential): String {
        return listOf(
            credential.id,
            credential.label,
            credential.type.name,
            credential.publicKeyPreview.orEmpty(),
            credential.encryptedPayloadRef,
            credential.createdAtEpochMillis.toString(),
            credential.passphraseRef.orEmpty(),
            credential.lastUsedEpochMillis.toString(),
            credential.username,
            credential.group,
            credential.tags.joinToString(","),
            credential.notes,
            credential.favorite.toString(),
            credential.importedAtEpochMillis.toString()
        ).joinToString("|") { escape(it) }
    }

    private fun decodeCredential(line: String): Credential? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 6) return null
        val type = runCatching { CredentialType.valueOf(fields[2]) }.getOrNull() ?: return null
        return CredentialPersistencePolicy.normalizeLoaded(Credential(
            id = fields[0],
            label = fields[1],
            type = type,
            publicKeyPreview = fields[3].ifBlank { null },
            encryptedPayloadRef = fields[4],
            createdAtEpochMillis = fields[5].toLongOrNull() ?: 0L,
            passphraseRef = fields.getOrNull(6)?.ifBlank { null },
            lastUsedEpochMillis = fields.getOrNull(7)?.toLongOrNull() ?: 0L,
            username = fields.getOrNull(8).orEmpty(),
            group = fields.getOrNull(9).orEmpty(),
            tags = fields.getOrNull(10).orEmpty().split(',').filter { it.isNotBlank() },
            notes = fields.getOrNull(11).orEmpty(),
            favorite = fields.getOrNull(12)?.toBooleanStrictOrNull() ?: false,
            importedAtEpochMillis = fields.getOrNull(13)?.toLongOrNull() ?: 0L
        ))
    }

    private fun encodeKnownHost(knownHost: KnownHost): String {
        return listOf(
            knownHost.id,
            knownHost.host,
            knownHost.port.toString(),
            knownHost.algorithm,
            knownHost.fingerprint,
            knownHost.trusted.toString(),
            knownHost.firstSeenEpochMillis.toString(),
            knownHost.lastSeenEpochMillis.toString(),
            knownHost.trustState.name
        ).joinToString("|") { escape(it) }
    }

    private fun decodeKnownHost(line: String): KnownHost? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 8) return null
        val trusted = fields[5].toBooleanStrictOrNull() ?: false
        return KnownHostPersistencePolicy.normalizeLoaded(
            KnownHost(
                id = fields[0],
                host = fields[1],
                port = fields[2].toIntOrNull()?.coerceIn(1, 65535) ?: 22,
                algorithm = fields[3],
                fingerprint = fields[4],
                trusted = trusted,
                firstSeenEpochMillis = fields[6].toLongOrNull() ?: 0L,
                lastSeenEpochMillis = fields[7].toLongOrNull() ?: 0L,
                trustState = KnownHostPersistencePolicy.trustStateFromPersisted(fields.getOrNull(8), trusted)
            )
        )
    }

    private fun encodeTerminalSession(record: TerminalSessionRecord): String {
        return listOf(
            record.id,
            record.serverId,
            record.title,
            record.status.name,
            record.startedAtEpochMillis.toString(),
            record.lastActiveEpochMillis.toString(),
            record.transcriptPreview,
            record.tmuxSessionName.orEmpty(),
            record.tmuxWindowIndex?.toString().orEmpty()
        ).joinToString("|") { escape(it) }
    }

    private fun decodeTerminalSession(line: String): TerminalSessionRecord? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 6) return null
        return TerminalSessionPersistencePolicy.normalizePersisted(
            TerminalSessionRecord(
                id = fields[0],
                serverId = fields[1],
                title = fields[2],
                status = runCatching { ServerStatus.valueOf(fields[3]) }.getOrDefault(ServerStatus.Unknown),
                startedAtEpochMillis = fields[4].toLongOrNull() ?: 0L,
                lastActiveEpochMillis = fields[5].toLongOrNull() ?: 0L,
                transcriptPreview = fields.getOrNull(6).orEmpty(),
                tmuxSessionName = fields.getOrNull(7),
                tmuxWindowIndex = fields.getOrNull(8)?.toIntOrNull()
            )
        )
    }

    private fun encodeSftpBookmark(bookmark: SftpBookmark): String {
        return listOf(
            bookmark.id,
            bookmark.serverId,
            bookmark.label,
            bookmark.path,
            bookmark.createdAtEpochMillis.toString()
        ).joinToString("|") { escape(it) }
    }

    private fun decodeSftpBookmark(line: String): SftpBookmark? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 5) return null
        return SftpBookmarkPersistencePolicy.normalizeLoaded(SftpBookmark(
            id = fields[0],
            serverId = fields[1],
            label = fields[2],
            path = fields[3],
            createdAtEpochMillis = fields[4].toLongOrNull() ?: 0L
        ))
    }

    private fun encodeTransfer(record: TransferRecord): String {
        return listOf(
            record.id,
            record.serverId,
            record.direction.name,
            record.remotePath,
            record.localDisplayName,
            record.progress.toString(),
            record.state.name,
            record.message,
            record.updatedAtEpochMillis.toString()
        ).joinToString("|") { escape(it) }
    }

    private fun decodeTransfer(line: String): TransferRecord? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 9) return null
        val direction = TransferPersistencePolicy.directionFromPersisted(fields[2]) ?: return null
        val state = TransferPersistencePolicy.stateFromPersisted(fields[6]) ?: return null
        return TransferPersistencePolicy.normalizePersisted(
            TransferRecord(
            id = fields[0],
            serverId = fields[1],
            direction = direction,
            remotePath = fields[3],
            localDisplayName = fields[4],
            progress = fields[5].toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f,
            state = state,
            message = fields[7],
            updatedAtEpochMillis = fields[8].toLongOrNull() ?: 0L
            )
        )
    }

    private fun encodeSnippet(snippet: Snippet): String {
        return listOf(
            snippet.id,
            snippet.name,
            snippet.command,
            snippet.tags.joinToString(","),
            snippet.serverScope.orEmpty(),
            snippet.variables.joinToString(","),
            snippet.description,
            snippet.group,
            snippet.favorite.toString(),
            snippet.confirmBeforeRun.toString(),
            snippet.createdAtEpochMillis.toString(),
            snippet.updatedAtEpochMillis.toString(),
            snippet.autoRun.toString()
        ).joinToString("|") { escape(it) }
    }

    private fun decodeSnippet(line: String): Snippet? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 6) return null
        return SnippetPersistencePolicy.normalizeLoaded(Snippet(
            id = fields[0],
            name = fields[1],
            command = fields[2],
            tags = fields[3].split(",").filter { it.isNotBlank() },
            serverScope = fields[4].ifBlank { null },
            variables = fields[5].split(",").filter { it.isNotBlank() },
            description = fields.getOrNull(6).orEmpty(),
            group = fields.getOrNull(7).orEmpty(),
            favorite = fields.getOrNull(8)?.toBooleanStrictOrNull() ?: false,
            confirmBeforeRun = fields.getOrNull(9)?.toBooleanStrictOrNull() ?: true,
            createdAtEpochMillis = fields.getOrNull(10)?.toLongOrNull() ?: 0L,
            updatedAtEpochMillis = fields.getOrNull(11)?.toLongOrNull() ?: 0L,
            autoRun = fields.getOrNull(12)?.toBooleanStrictOrNull() ?: false
        ))
    }

    private fun encodeForward(forward: PortForwardRule): String {
        return listOf(
            forward.id,
            forward.serverId,
            forward.type.name,
            forward.localHost,
            forward.localPort.toString(),
            forward.remoteHost,
            forward.remotePort.toString(),
            forward.enabled.toString(),
            forward.autoStart.toString(),
            forward.label,
            forward.group,
            forward.favorite.toString()
        ).joinToString("|") { escape(it) }
    }

    private fun decodeForward(line: String): PortForwardRule? {
        val fields = splitEscaped(line, '|')
        if (fields.size < 9) return null
        val type = ForwardPersistencePolicy.typeFromPersisted(fields[2]) ?: return null
        return ForwardPersistencePolicy.normalizeLoaded(
            PortForwardRule(
                id = fields[0],
                serverId = fields[1],
                type = type,
                localHost = fields[3],
                localPort = fields[4].toIntOrNull()?.coerceIn(1, 65535) ?: 8080,
                remoteHost = fields[5],
                remotePort = fields[6].toIntOrNull()?.coerceIn(0, 65535) ?: 0,
                enabled = fields[7].toBooleanStrictOrNull() ?: false,
                autoStart = fields[8].toBooleanStrictOrNull() ?: false,
                label = fields.getOrNull(9).orEmpty(),
                group = fields.getOrNull(10).orEmpty(),
                favorite = fields.getOrNull(11)?.toBooleanStrictOrNull() ?: false
            )
        )
    }

    private fun stableId(name: String, host: String): String {
        val base = "${name}-${host}"
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "server" }
        var candidate = base
        var suffix = 2
        while (servers.any { it.id == candidate }) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun stableCredentialId(label: String): String {
        val base = label
            .lowercase()
            .map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("")
            .replace(Regex("-+"), "-")
            .trim('-')
            .ifBlank { "credential" }
        var candidate = base
        var suffix = 2
        while (credentials.any { it.id == candidate }) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }

    private fun previewFor(type: CredentialType, secret: String): String {
        if (secret.isBlank()) return ""
        return when (type) {
            CredentialType.Password -> "Password saved"
            CredentialType.PrivateKey -> AndroidSshjCompat.authorizedPublicKeyLine(secret)
                ?: KeyMaterialInspector.publicPreviewForSecret(type.name, secret)
            CredentialType.HardwareKey -> "Hardware key metadata"
        }
    }

    private fun String.isRealFingerprint(): Boolean = startsWith("SHA256:") && !contains("pending") && !contains("Unavailable")

    private suspend fun String.deleteSecretQuietly() {
        if (!startsWith("secret-")) return
        try {
            secretStore.deleteSecret(this)
        } catch (_: Exception) {
        }
    }
}

private fun defaultTerminalProfile() = TerminalProfile(
    id = "term-default",
    name = "ChronoSSH Dark",
    fontSizeSp = 14,
    fontFamily = "JetBrains Mono",
    themeName = "Tokyo Night",
    cursorStyle = TerminalCursorStyle.Block,
    scrollbackLines = 12000,
    keyRows = listOf(
        TerminalKeyRow(
            id = "core",
            keys = listOf(
                TerminalKey("Esc", "\u001B"),
                TerminalKey("Tab", "\t"),
                TerminalKey("Ctrl", "<ctrl>"),
                TerminalKey("Alt", "<alt>"),
                TerminalKey("/", "/"),
                TerminalKey("|", "|"),
                TerminalKey("~", "~"),
                TerminalKey("Up", "\u001B[A"),
                TerminalKey("Down", "\u001B[B")
            )
        )
    )
)

private fun defaultSettings() = AppSettings(
    themeModeName = DeckThemeMode.System.name,
    themeFamilyId = DeckThemeCatalog.DEFAULT_FAMILY_ID,
    autoRefreshSeconds = ServerStatusRefreshPolicy.ServerBoxDefaultSeconds
)

internal fun sanitizeLoadedSettings(settings: AppSettings): AppSettings {
    val clean = settings.copy(
        homeHeadingFontPath = sanitizeHeadingFontPath(settings.homeHeadingFontPath),
        connectionsHeadingFontPath = sanitizeHeadingFontPath(settings.connectionsHeadingFontPath),
        filesHeadingFontPath = sanitizeHeadingFontPath(settings.filesHeadingFontPath),
        vaultHeadingFontPath = sanitizeHeadingFontPath(settings.vaultHeadingFontPath),
        settingsHeadingFontPath = sanitizeHeadingFontPath(settings.settingsHeadingFontPath),
        serverDetailCardOrder = ServerDetailCard.sanitizeOrderCsv(settings.serverDetailCardOrder),
        serverDetailHiddenCards = ServerDetailCard.sanitizeHiddenCsv(settings.serverDetailHiddenCards)
    )
    return if (PinLockPolicy.persistedPinUsable(clean.appLockPinHash, clean.appLockPinSalt)) {
        clean
    } else {
        clean.copy(appLockPinHash = null, appLockPinSalt = null, appLockBiometricEnabled = false, appLockRenderArmedAtEpochMillis = null)
    }
}

internal fun sanitizeHeadingFontPath(path: String?): String? {
    val clean = path?.trim()?.takeIf { it.length <= 512 } ?: return null
    val lower = clean.lowercase()
    if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) return null
    if (".." in clean || "\u0000" in clean) return null
    return clean
}

private fun terminalProfileFromSettings(settings: AppSettings): TerminalProfile {
    val theme = TerminalCatalog.theme(settings.terminalThemeName.ifBlank { "Tokyo Night" })
    val font = TerminalCatalog.font(settings.terminalFontFamily.ifBlank { "JetBrains Mono" })
    return defaultTerminalProfile().copy(
        fontSizeSp = settings.terminalFontSizeSp.coerceIn(10, 24),
        fontFamily = font.name,
        scrollbackLines = settings.terminalScrollbackLines.coerceIn(1000, 50000),
        cursorStyle = settings.terminalCursorStyle,
        themeName = theme.name,
        foregroundHex = theme.foregroundHex,
        backgroundHex = theme.backgroundHex,
        cursorHex = theme.cursorHex,
        selectionHex = theme.selectionHex,
        ansiColorsHex = theme.ansiColorsHex,
        keyRows = listOf(TerminalKeyRow("custom", TerminalAccessoryKeyPolicy.keys(settings.terminalAccessoryKeys))),
        bracketedPaste = settings.terminalBracketedPaste,
        hapticFeedback = settings.terminalHapticFeedback,
        accessorySingleRow = settings.terminalAccessorySingleRow,
        sideMarginDp = settings.terminalSideMarginDp.coerceIn(0, 8),
        rightMarginDp = settings.terminalRightMarginDp.coerceIn(0, 8),
        accessoryPopups = settings.terminalAccessoryPopups,
        accessoryFullScroll = settings.terminalAccessoryFullScroll,
        keepScreenOn = settings.terminalKeepScreenOn
    )
}

private fun defaultServers(): List<ServerProfile> = emptyList()

private fun defaultSnapshots(): Map<String, MetricSnapshot> = emptyMap()

private fun unavailableSnapshot(serverId: String) = MetricSnapshot(
    serverId = serverId,
    status = ServerStatus.Unknown,
    latencyMs = null,
    uptime = "--",
    cpu = CpuMetrics(0, 0, "Unavailable until metrics are collected", 0, 0, 0, 0, 0, 0f, 0f, 0f, emptyList()),
    memory = MemoryMetrics(0, 0, 0, 0),
    disk = DiskMetrics(0f, 0f, "--", "--"),
    network = NetworkMetrics(
        primaryInterface = NetworkInterfaceMetric("eth0", "--", "--", "--", "--", "--", 0.50f),
        interfaces = emptyList(),
        history = NetworkHistory("--", "--", emptyList(), emptyList(), emptyList())
    ),
    processes = ProcessSummary(0, 0, "--"),
    services = ServiceSummary(0, 0),
    docker = DockerSummary(0, 0),
    collectedAtEpochMillis = System.currentTimeMillis()
)

private fun defaultCredentials(): List<Credential> = emptyList()

private fun defaultKnownHosts(): List<KnownHost> = emptyList()

private fun defaultSnippets() = DefaultSnippetCatalog.snippets

private fun defaultForwards(): List<PortForwardRule> = emptyList()

private fun defaultSftpBookmarks(): List<SftpBookmark> = emptyList()

private fun accentFor(osName: String): ServerAccent {
    val normalized = osName.lowercase()
    return when {
        "ubuntu" in normalized -> ServerAccent("Ubuntu Orange", 0xFFFF6B1A)
        "debian" in normalized -> ServerAccent("Debian Rose", 0xFFE84A5F)
        "alma" in normalized || "almalinux" in normalized -> ServerAccent("AlmaLinux Emerald", 0xFF1DB954)
        "rocky" in normalized -> ServerAccent("Rocky Green", 0xFF10B981)
        "centos" in normalized -> ServerAccent("CentOS Purple", 0xFF7C3AED)
        "oracle" in normalized -> ServerAccent("Oracle Red", 0xFFE5484D)
        "red hat" in normalized || "rhel" in normalized -> ServerAccent("Enterprise Red", 0xFFE5484D)
        "alpine" in normalized -> ServerAccent("Alpine Blue", 0xFF28B6F6)
        "fedora" in normalized -> ServerAccent("Fedora Blue", 0xFF3A7BFF)
        "arch" in normalized || "manjaro" in normalized -> ServerAccent("Arch Cyan", 0xFF21C7E8)
        "nixos" in normalized || "nix os" in normalized -> ServerAccent("NixOS Blue", 0xFF5277C3)
        "gentoo" in normalized -> ServerAccent("Gentoo Purple", 0xFF7A5FA1)
        "slackware" in normalized -> ServerAccent("Slackware Blue", 0xFF315B9A)
        "void" in normalized -> ServerAccent("Void Green", 0xFF478061)
        "zorin" in normalized -> ServerAccent("Zorin Blue", 0xFF1A8CFF)
        "elementary" in normalized -> ServerAccent("Elementary Gray", 0xFF64748B)
        "deepin" in normalized -> ServerAccent("Deepin Blue", 0xFF1677FF)
        "endeavour" in normalized || "endeavor" in normalized -> ServerAccent("Endeavour Violet", 0xFF7C3AED)
        "garuda" in normalized -> ServerAccent("Garuda Blue", 0xFF2563EB)
        "mageia" in normalized -> ServerAccent("Mageia Blue", 0xFF00A2DF)
        "mx linux" in normalized || normalized == "mx" -> ServerAccent("MX Blue", 0xFF2563EB)
        "openmandriva" in normalized || "open mandriva" in normalized -> ServerAccent("OpenMandriva Pink", 0xFFD946EF)
        "parrot" in normalized -> ServerAccent("Parrot Green", 0xFF00A887)
        "raspbian" in normalized || "raspios" in normalized || "raspberry pi os" in normalized -> ServerAccent("Raspberry Red", 0xFFC51A4A)
        "solus" in normalized -> ServerAccent("Solus Blue", 0xFF5294E2)
        "tails" in normalized -> ServerAccent("Tails Purple", 0xFF56347C)
        "clear linux" in normalized || normalized == "clear" -> ServerAccent("Clear Blue", 0xFF0068B5)
        "freebsd" in normalized || "openbsd" in normalized || "netbsd" in normalized -> ServerAccent("BSD Red", 0xFFB91C1C)
        else -> ServerAccent("ChronoSSH Cyan", 0xFF21C7E8)
    }
}

private fun escape(value: String): String {
    return buildString {
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '|' -> append("\\p")
                else -> append(char)
            }
        }
    }
}

private fun unescape(value: String): String {
    return buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == '\\' && index + 1 < value.length) {
                when (val next = value[index + 1]) {
                    '\\' -> append('\\')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    'p' -> append('|')
                    else -> append(next)
                }
                index += 2
            } else {
                append(char)
                index += 1
            }
        }
    }
}

private fun splitEscaped(value: String, separator: Char): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    value.forEach { char ->
        when {
            escaped -> {
                current.append('\\')
                current.append(char)
                escaped = false
            }
            char == '\\' -> escaped = true
            char == separator -> {
                parts.add(unescape(current.toString()))
                current.clear()
            }
            else -> current.append(char)
        }
    }
    if (escaped) current.append('\\')
    parts.add(unescape(current.toString()))
    return parts
}

private fun parseBackupSections(text: String): Map<String, List<String>> {
    val sections = linkedMapOf<String, MutableList<String>>()
    var current: String? = null
    text.lineSequence().drop(1).forEach { raw ->
        val line = raw.trim()
        when {
            line.isBlank() -> Unit
            line.startsWith("[") && line.endsWith("]") -> {
                current = line.trim('[', ']')
                sections.getOrPut(current.orEmpty()) { mutableListOf() }
            }
            current != null && current != "end" -> sections.getOrPut(current.orEmpty()) { mutableListOf() }.add(raw)
        }
    }
    return sections
}

object BackupSettingsImportPolicy {
    fun importedSettings(fields: List<String>, existing: AppSettings): AppSettings? {
        if (fields.size < 8) return null
        val hasTerminalFont = fields.size >= 9
        val hapticIndex = if (hasTerminalFont) 8 else 7
        val hasAccessoryKeys = fields.size >= hapticIndex + 3
        val accessoryIndex = hapticIndex + 1
        val terminalAccessorySingleRowIndex = accessoryIndex + 1
        val hasAccessorySingleRow = fields.getOrNull(terminalAccessorySingleRowIndex)?.toBooleanStrictOrNull() != null
        val autoRefreshIndex = when {
            hasAccessorySingleRow -> terminalAccessorySingleRowIndex + 1
            hasAccessoryKeys -> accessoryIndex + 1
            else -> hapticIndex + 1
        }
        val terminalSideMarginIndex = autoRefreshIndex + 1
        val maybeRightMarginIndex = terminalSideMarginIndex + 1
        val hasRightMargin = fields.getOrNull(maybeRightMarginIndex)?.toIntOrNull() != null &&
            fields.getOrNull(maybeRightMarginIndex + 1)?.toBooleanStrictOrNull() != null &&
            fields.getOrNull(maybeRightMarginIndex + 2)?.toBooleanStrictOrNull() != null
        val terminalRightMarginIndex = if (hasRightMargin) maybeRightMarginIndex else -1
        val terminalAccessoryPopupsIndex = if (hasRightMargin) maybeRightMarginIndex + 1 else -1
        val terminalAccessoryFullScrollIndex = if (hasRightMargin) maybeRightMarginIndex + 2 else -1
        val terminalKeepScreenOnIndex = terminalSideMarginIndex + if (hasRightMargin) 4 else 1
        val hasTerminalKeepScreenOn = fields.getOrNull(terminalKeepScreenOnIndex)?.toBooleanStrictOrNull() != null
        val serverCardNetworkModeIndex = terminalKeepScreenOnIndex + if (hasTerminalKeepScreenOn) 1 else 0
        val serverCardDiskModeIndex = serverCardNetworkModeIndex + 1
        val appLockBiometricIndex = serverCardNetworkModeIndex + 2
        val serverMetricColorPresetIndex = serverCardNetworkModeIndex + 3
        val sftpDefaultSortModeNameIndex = serverCardNetworkModeIndex + 4
        val sftpDefaultSortDescendingIndex = serverCardNetworkModeIndex + 5
        val sftpShowHiddenByDefaultIndex = serverCardNetworkModeIndex + 6
        val serverDetailCardOrderIndex = serverCardNetworkModeIndex + 7
        val serverDetailHiddenCardsIndex = serverCardNetworkModeIndex + 8
        return AppSettings(
            themeModeName = safeSettingToken(fields[0], "System"),
            themeFamilyId = safeSettingToken(fields[1], "default"),
            terminalFontSizeSp = fields[2].toIntOrNull()?.coerceIn(10, 24) ?: 14,
            terminalScrollbackLines = fields[3].toIntOrNull()?.coerceIn(1000, 50000) ?: 12000,
            terminalCursorStyle = runCatching { TerminalCursorStyle.valueOf(fields[4]) }.getOrDefault(TerminalCursorStyle.Block),
            terminalThemeName = safeSettingToken(fields[5], "Tokyo Night"),
            terminalFontFamily = if (hasTerminalFont) safeSettingToken(fields[6], "JetBrains Mono") else "JetBrains Mono",
            terminalBracketedPaste = fields[if (hasTerminalFont) 7 else 6].toBooleanStrictOrNull() ?: true,
            terminalHapticFeedback = fields[hapticIndex].toBooleanStrictOrNull() ?: true,
            terminalAccessoryKeys = TerminalAccessoryKeyPolicy.normalizeCsv(fields.getOrNull(accessoryIndex).takeIf { hasAccessoryKeys }),
            terminalAccessorySingleRow = fields.getOrNull(terminalAccessorySingleRowIndex)?.toBooleanStrictOrNull() ?: existing.terminalAccessorySingleRow,
            terminalSideMarginDp = fields.getOrNull(terminalSideMarginIndex)?.toIntOrNull()?.coerceIn(0, 8) ?: existing.terminalSideMarginDp,
            terminalRightMarginDp = fields.getOrNull(terminalRightMarginIndex)?.toIntOrNull()?.coerceIn(0, 8) ?: existing.terminalRightMarginDp,
            terminalAccessoryPopups = fields.getOrNull(terminalAccessoryPopupsIndex)?.toBooleanStrictOrNull() ?: existing.terminalAccessoryPopups,
            terminalAccessoryFullScroll = fields.getOrNull(terminalAccessoryFullScrollIndex)?.toBooleanStrictOrNull() ?: existing.terminalAccessoryFullScroll,
            terminalKeepScreenOn = fields.getOrNull(terminalKeepScreenOnIndex)?.toBooleanStrictOrNull() ?: existing.terminalKeepScreenOn,
            serverCardNetworkMode = fields.getOrNull(serverCardNetworkModeIndex)?.let { runCatching { ServerCardNetworkMode.valueOf(it) }.getOrNull() } ?: existing.serverCardNetworkMode,
            serverCardDiskMode = fields.getOrNull(serverCardDiskModeIndex)?.let { runCatching { ServerCardDiskMode.valueOf(it) }.getOrNull() } ?: existing.serverCardDiskMode,
            serverMetricColorPreset = fields.getOrNull(serverMetricColorPresetIndex)?.let { runCatching { ServerMetricColorPreset.valueOf(it) }.getOrNull() } ?: existing.serverMetricColorPreset,
            sftpDefaultSortModeName = normalizeSftpSortModeName(fields.getOrNull(sftpDefaultSortModeNameIndex) ?: existing.sftpDefaultSortModeName),
            sftpDefaultSortDescending = fields.getOrNull(sftpDefaultSortDescendingIndex)?.toBooleanStrictOrNull() ?: existing.sftpDefaultSortDescending,
            sftpShowHiddenByDefault = fields.getOrNull(sftpShowHiddenByDefaultIndex)?.toBooleanStrictOrNull() ?: existing.sftpShowHiddenByDefault,
            serverDetailCardOrder = ServerDetailCard.sanitizeOrderCsv(fields.getOrNull(serverDetailCardOrderIndex) ?: existing.serverDetailCardOrder),
            serverDetailHiddenCards = ServerDetailCard.sanitizeHiddenCsv(fields.getOrNull(serverDetailHiddenCardsIndex) ?: existing.serverDetailHiddenCards),
            autoRefreshSeconds = ServerStatusRefreshPolicy.normalize(fields.getOrNull(autoRefreshIndex)?.toIntOrNull()),
            appLockPinHash = existing.appLockPinHash,
            appLockPinSalt = existing.appLockPinSalt,
            appLockBiometricEnabled = fields.getOrNull(appLockBiometricIndex)?.toBooleanStrictOrNull() ?: existing.appLockBiometricEnabled,
            appLockRenderArmedAtEpochMillis = null
        )
    }

    private fun safeSettingToken(value: String, fallback: String): String {
        return value.trim()
            .filter { it.isLetterOrDigit() || it in setOf(' ', '-', '_', '.') }
            .take(64)
            .trim()
            .ifBlank { fallback }
    }
}

object PersistedServerProfilePolicy {
    const val MaxNotesChars = 2_000

    fun normalizeLoaded(server: ServerProfile): ServerProfile? {
        val id = server.id.trim()
        val host = server.host.trim()
        if (id.isBlank() || HostEndpointValidator.errorFor(host) != null) return null
        val tags = (listOf("All") + server.tags.map { it.trim() }.filter { it.isNotBlank() })
            .distinct()
        return server.copy(
            id = id,
            name = server.name.trim().ifBlank { host },
            host = host,
            username = server.username.trim().ifBlank { "root" },
            group = server.group.trim().ifBlank { "Ungrouped" },
            tags = tags,
            osName = server.osName.trim().ifBlank { "Linux" },
            osVersion = server.osVersion.trim().ifBlank { "Unknown" },
            credentialId = server.credentialId?.trim()?.ifBlank { null },
            terminalProfileId = server.terminalProfileId.trim().ifBlank { "term-default" },
            monitoringConfig = server.monitoringConfig.copy(
                pollIntervalSeconds = ServerStatusRefreshPolicy.normalize(server.monitoringConfig.pollIntervalSeconds)
            ),
            startupCommand = safeStartupCommand(server.startupCommand),
            startDirectory = server.startDirectory.trim(),
            notes = server.notes.trim().take(MaxNotesChars),
            wakeOnLan = server.wakeOnLan?.normalizedWakeOnLan(),
            proxyJumpHostId = server.proxyJumpHostId?.trim()?.takeUnless { it == id }?.ifBlank { null },
            reconnectPolicy = server.reconnectPolicy.copy(
                keepAliveSeconds = server.reconnectPolicy.keepAliveSeconds.coerceIn(10, 120),
                maxAttempts = server.reconnectPolicy.maxAttempts.coerceIn(0, 10)
            ),
            connectTimeoutSeconds = server.connectTimeoutSeconds.coerceIn(3, 60),
            sshCompressionEnabled = server.sshCompressionEnabled,
            moshConfig = server.moshConfig.normalized(),
            eternalTerminalConfig = server.eternalTerminalConfig.normalized(),
            vncConfig = server.vncConfig.normalized(),
            rdpConfig = server.rdpConfig.normalized(),
            fileConfig = server.fileConfig.normalized(),
            prootConfig = server.prootConfig.normalized()
        )
    }

    fun safeStartupCommand(command: String): String {
        return command.takeIf(HostCommandSafety::isAutomaticCommandSafe).orEmpty()
    }
}

private fun String?.toConnectionProtocol(): ConnectionProtocol {
    return ConnectionProtocol.entries.firstOrNull { it.name.equals(this.orEmpty(), ignoreCase = true) }
        ?: ConnectionProtocol.Ssh
}

private fun MoshConfig.normalized(): MoshConfig = copy(
    serverCommand = serverCommand.trim().ifBlank { "mosh-server" },
    locale = locale.trim().ifBlank { "en_US.UTF-8" },
    colors = colors.coerceIn(8, 256),
    predictionMode = predictionMode.trim().lowercase().ifBlank { "adaptive" }
)

private fun EternalTerminalConfig.normalized(): EternalTerminalConfig = copy(
    sshBootstrapPort = sshBootstrapPort.coerceIn(1, 65535),
    etServerPort = etServerPort.coerceIn(1, 65535),
    terminalType = terminalType.trim().ifBlank { "xterm-256color" },
    serverCommand = serverCommand.trim().ifBlank { "etterminal" }
)

private fun VncProfileConfig.normalized(): VncProfileConfig = copy(
    colorDepthBits = when (colorDepthBits) {
        8, 16, 24 -> colorDepthBits
        else -> 24
    },
    targetFps = targetFps.coerceIn(5, 60),
    sshBootstrapPort = sshBootstrapPort.coerceIn(1, 65535)
)

private fun RdpProfileConfig.normalized(): RdpProfileConfig = copy(
    width = width.coerceIn(640, 3840),
    height = height.coerceIn(480, 2160),
    colorDepth = when (colorDepth) {
        15, 16, 24, 32 -> colorDepth
        else -> 16
    },
    domain = domain.trim().take(128),
    sshBootstrapPort = sshBootstrapPort.coerceIn(1, 65535)
)

private fun FileProtocolConfig.normalized(): FileProtocolConfig = copy(
    rootPath = rootPath.trim().take(512),
    transferConcurrency = transferConcurrency.coerceIn(1, 8)
)

private fun ProotProfileConfig.normalized(): ProotProfileConfig = copy(
    distroId = distroId.trim().ifBlank { "alpine-3.21" }.take(80),
    rootfsPath = rootfsPath.trim().take(512)
)

private fun encodeMoshConfig(config: MoshConfig): String = with(config.normalized()) {
    listOf(serverCommand, locale, colors.toString(), predictionMode).joinToString(",") { escape(it) }
}

private fun decodeMoshConfig(value: String?): MoshConfig {
    val fields = splitEscaped(value.orEmpty(), ',')
    return MoshConfig(
        serverCommand = fields.getOrNull(0).orEmpty().ifBlank { "mosh-server" },
        locale = fields.getOrNull(1).orEmpty().ifBlank { "en_US.UTF-8" },
        colors = fields.getOrNull(2)?.toIntOrNull() ?: 256,
        predictionMode = fields.getOrNull(3).orEmpty().ifBlank { "adaptive" }
    ).normalized()
}

private fun encodeEtConfig(config: EternalTerminalConfig): String = with(config.normalized()) {
    listOf(sshBootstrapPort.toString(), etServerPort.toString(), terminalType, serverCommand).joinToString(",") { escape(it) }
}

private fun decodeEtConfig(value: String?): EternalTerminalConfig {
    val fields = splitEscaped(value.orEmpty(), ',')
    return EternalTerminalConfig(
        sshBootstrapPort = fields.getOrNull(0)?.toIntOrNull() ?: 22,
        etServerPort = fields.getOrNull(1)?.toIntOrNull() ?: 2022,
        terminalType = fields.getOrNull(2).orEmpty().ifBlank { "xterm-256color" },
        serverCommand = fields.getOrNull(3).orEmpty().ifBlank { "etterminal" }
    ).normalized()
}

private fun encodeVncConfig(config: VncProfileConfig): String = with(config.normalized()) {
    listOf(
        colorDepthBits.toString(),
        shared.toString(),
        viewOnly.toString(),
        targetFps.toString(),
        tunnelOverSsh.toString(),
        sshBootstrapPort.toString()
    ).joinToString(",") { escape(it) }
}

private fun decodeVncConfig(value: String?): VncProfileConfig {
    val fields = splitEscaped(value.orEmpty(), ',')
    return VncProfileConfig(
        colorDepthBits = fields.getOrNull(0)?.toIntOrNull() ?: 24,
        shared = fields.getOrNull(1)?.toBooleanStrictOrNull() ?: true,
        viewOnly = fields.getOrNull(2)?.toBooleanStrictOrNull() ?: false,
        targetFps = fields.getOrNull(3)?.toIntOrNull() ?: 30,
        tunnelOverSsh = fields.getOrNull(4)?.toBooleanStrictOrNull() ?: false,
        sshBootstrapPort = fields.getOrNull(5)?.toIntOrNull() ?: 22
    ).normalized()
}

private fun encodeRdpConfig(config: RdpProfileConfig): String = with(config.normalized()) {
    listOf(
        width.toString(),
        height.toString(),
        colorDepth.toString(),
        domain,
        useNla.toString(),
        tunnelOverSsh.toString(),
        sshBootstrapPort.toString()
    ).joinToString(",") { escape(it) }
}

private fun decodeRdpConfig(value: String?): RdpProfileConfig {
    val fields = splitEscaped(value.orEmpty(), ',')
    return RdpProfileConfig(
        width = fields.getOrNull(0)?.toIntOrNull() ?: 1600,
        height = fields.getOrNull(1)?.toIntOrNull() ?: 900,
        colorDepth = fields.getOrNull(2)?.toIntOrNull() ?: 16,
        domain = fields.getOrNull(3).orEmpty(),
        useNla = fields.getOrNull(4)?.toBooleanStrictOrNull() ?: true,
        tunnelOverSsh = fields.getOrNull(5)?.toBooleanStrictOrNull() ?: false,
        sshBootstrapPort = fields.getOrNull(6)?.toIntOrNull() ?: 22
    ).normalized()
}

private fun encodeFileConfig(config: FileProtocolConfig): String = with(config.normalized()) {
    listOf(rootPath, transferConcurrency.toString(), resumeTransfers.toString(), verifyChecksums.toString()).joinToString(",") { escape(it) }
}

private fun decodeFileConfig(value: String?): FileProtocolConfig {
    val fields = splitEscaped(value.orEmpty(), ',')
    return FileProtocolConfig(
        rootPath = fields.getOrNull(0).orEmpty(),
        transferConcurrency = fields.getOrNull(1)?.toIntOrNull() ?: 2,
        resumeTransfers = fields.getOrNull(2)?.toBooleanStrictOrNull() ?: true,
        verifyChecksums = fields.getOrNull(3)?.toBooleanStrictOrNull() ?: false
    ).normalized()
}

private fun encodeProotConfig(config: ProotProfileConfig): String = with(config.normalized()) {
    listOf(distroId, rootfsPath, mountHome.toString()).joinToString(",") { escape(it) }
}

private fun decodeProotConfig(value: String?): ProotProfileConfig {
    val fields = splitEscaped(value.orEmpty(), ',')
    return ProotProfileConfig(
        distroId = fields.getOrNull(0).orEmpty().ifBlank { "alpine-3.21" },
        rootfsPath = fields.getOrNull(1).orEmpty(),
        mountHome = fields.getOrNull(2)?.toBooleanStrictOrNull() ?: true
    ).normalized()
}

private fun WakeOnLanConfig.normalizedWakeOnLan(): WakeOnLanConfig? {
    val mac = WakeOnLanPolicy.normalizeMac(macAddress) ?: return null
    val broadcast = WakeOnLanPolicy.normalizeBroadcast(broadcastAddress) ?: return null
    val secureOn = WakeOnLanPolicy.normalizeSecureOn(secureOnPassword)
    if (!secureOnPassword.isNullOrBlank() && secureOn == null) return null
    return WakeOnLanConfig(macAddress = mac, broadcastAddress = broadcast, secureOnPassword = secureOn)
}

private fun <T> mergeDecoded(lines: List<String>, decode: (String) -> T?, merge: (T) -> BackupMergeStats): BackupMergeStats {
    var stats = BackupMergeStats()
    lines.forEach { line ->
        val record = runCatching { decode(line) }.getOrNull()
        if (record == null) {
            stats += BackupMergeStats(skipped = 1)
        } else {
            stats += merge(record)
        }
    }
    return stats
}

internal fun shouldSkipOpenSshImport(existing: List<ServerProfile>, host: OpenSshConfigHost): Boolean {
    val endpointKey = openSshImportEndpointKey(host.hostName, host.port, host.user)
    return existing.any { server -> openSshImportEndpointKey(server.host, server.port, server.username) == endpointKey }
}

internal fun openSshProxyJumpImportError(target: ServerProfile, jump: ServerProfile?, candidates: List<ServerProfile>): String? {
    val jumpId = jump?.id ?: return "ProxyJump host is not imported."
    return ProxyJumpPolicy.errorForSelection(target, jumpId, candidates)
}

internal fun openSshImportEndpointKey(host: String, port: Int, username: String): String {
    return "${host.trim().lowercase()}:$port:${username.trim()}"
}

internal fun uniqueOpenSshImportName(alias: String, usedNames: Set<String>): String {
    val base = alias.trim().ifBlank { "Imported Host" }
    if (base !in usedNames) return base
    var index = 2
    while (true) {
        val candidate = "$base ($index)"
        if (candidate !in usedNames) return candidate
        index += 1
    }
}

internal fun openSshIdentityCredential(identityFile: String, usedIds: Set<String>, now: Long = System.currentTimeMillis()): Credential {
    val clean = identityFile.trim().take(256)
    return Credential(
        id = uniqueOpenSshIdentityCredentialId(clean, usedIds),
        label = "OpenSSH ${clean.substringAfterLast('/').ifBlank { "identity" }}",
        type = CredentialType.PrivateKey,
        publicKeyPreview = "IdentityFile $clean",
        encryptedPayloadRef = "pending",
        createdAtEpochMillis = now
    )
}

internal fun uniqueOpenSshIdentityCredentialId(identityFile: String, usedIds: Set<String>): String {
    val base = "openssh-${identityFile.trim().substringAfterLast('/').ifBlank { "identity" }}"
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "openssh-identity" }
    if (base !in usedIds) return base
    var index = 2
    while (true) {
        val candidate = "$base-$index"
        if (candidate !in usedIds) return candidate
        index += 1
    }
}

internal fun uniqueOpenSshForwardId(serverId: String, type: String, index: Int, usedIds: Set<String>): String {
    val base = "openssh-forward-$serverId-$type-${index + 1}"
        .lowercase()
        .map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifBlank { "openssh-forward" }
    if (base !in usedIds) return base
    var suffix = 2
    while (true) {
        val candidate = "$base-$suffix"
        if (candidate !in usedIds) return candidate
        suffix += 1
    }
}

private fun <T> MutableList<T>.upsertById(id: String, record: T, idOf: (T) -> String) {
    val index = indexOfFirst { idOf(it) == id }
    if (index >= 0) {
        this[index] = record
    } else {
        add(record)
    }
}

private fun <T> MutableList<T>.upsertByIdWithStats(id: String, record: T, idOf: (T) -> String): BackupMergeStats {
    val index = indexOfFirst { idOf(it) == id }
    return if (index >= 0) {
        this[index] = record
        BackupMergeStats(updated = 1)
    } else {
        add(record)
        BackupMergeStats(inserted = 1)
    }
}

private fun String.withoutSurroundingQuotes(): String {
    return trim().trim('"').trim('\'')
}

private fun normalizeSftpSortModeName(value: String?): String {
    return when (value) {
        "Modified", "Size" -> value
        else -> "Name"
    }
}

internal fun String.noisyConnectionEventBucket(): String? {
    return when {
        startsWith("Collecting Linux metrics") -> "metrics-collecting"
        startsWith("Metrics updated from SSH exec.") -> "metrics-updated"
        startsWith("vnStat: skipped during fast refresh") -> "vnstat-fast-skip"
        startsWith("Metrics connection failed:") -> "metrics-connection-failed"
        startsWith("Metrics failed:") -> "metrics-failed"
        startsWith("TCP probe failed") -> "tcp-probe-failed"
        startsWith("TCP reachable") -> "tcp-reachable"
        startsWith("Metrics skipped: refresh already running") -> "metrics-refresh-running"
        else -> null
    }
}
