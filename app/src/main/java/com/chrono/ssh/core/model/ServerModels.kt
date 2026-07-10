package com.chrono.ssh.core.model

enum class ServerStatus {
    Online,
    Offline,
    Connecting,
    Unknown
}

enum class CredentialType {
    Password,
    PrivateKey,
    HardwareKey
}

enum class ConnectionProtocol {
    Ssh,
    Mosh,
    EternalTerminal,
    Vnc,
    Rdp,
    Smb,
    Rclone,
    LocalProot
}

enum class PortForwardType {
    Local,
    Remote,
    DynamicSocks
}

enum class TerminalCursorStyle {
    Block,
    Underline,
    Beam
}

enum class ServerCardNetworkMode {
    Totals,
    Rates
}

enum class ServerCardDiskMode {
    Usage,
    Rates,
    Totals
}

enum class ServerMetricColorPreset {
    Theme,
    Custom,
    Classic,
    Calm,
    Graphite,
    HighContrast,
    Ocean,
    Forest,
    Ember,
    Aurora,
    Orchid,
    Nordic,
    Solar,
    Circuit,
    Harvest,
    Lagoon,
    Metro,
    Mono
}

enum class HostKeyTrustState {
    Unknown,
    Trusted,
    Changed,
    Rejected
}

enum class TransferDirection {
    Upload,
    Download
}

enum class TransferRecordState {
    Queued,
    Running,
    Complete,
    Failed,
    Cancelled
}

enum class ConnectionEventLevel {
    Info,
    Success,
    Warning,
    Error
}

enum class ServerDetailCard(val id: String, val label: String) {
    Uptime("uptime", "Uptime"),
    CpuUsage("cpu_usage", "CPU Usage"),
    CpuLoad("cpu_load", "CPU Load"),
    System("system", "System"),
    FailedServices("failed_services", "Failed Services"),
    Resources("resources", "Resources"),
    Filesystems("filesystems", "Filesystems"),
    Processes("processes", "Processes"),
    Systemd("systemd", "Systemd"),
    Network("network", "Network"),
    Containers("containers", "Containers"),
    Gpus("gpus", "GPUs"),
    Proxmox("proxmox", "Proxmox"),
    Battery("battery", "Battery"),
    SmartDisks("smart_disks", "SMART Disks"),
    Sensors("sensors", "Sensors");

    companion object {
        fun defaultOrder(): List<ServerDetailCard> = entries.toList()
        fun defaultOrderCsv(): String = defaultOrder().joinToString(",") { it.id }
        fun ordered(csv: String): List<ServerDetailCard> {
            val byId = entries.associateBy { it.id }
            val selected = csv.split(',')
                .mapNotNull { byId[it.trim()] }
                .distinct()
            return selected + defaultOrder().filterNot { it in selected }
        }
        fun hiddenSet(csv: String): Set<ServerDetailCard> {
            val byId = entries.associateBy { it.id }
            return csv.split(',').mapNotNull { byId[it.trim()] }.toSet()
        }
        fun sanitizeOrderCsv(csv: String): String = ordered(csv).joinToString(",") { it.id }
        fun sanitizeHiddenCsv(csv: String): String = hiddenSet(csv).joinToString(",") { it.id }
    }
}

data class AppSettings(
    val themeModeName: String,
    val themeFamilyId: String,
    val terminalFontSizeSp: Int = 14,
    val terminalScrollbackLines: Int = 12000,
    val terminalCursorStyle: TerminalCursorStyle = TerminalCursorStyle.Block,
    val terminalThemeName: String = "Tokyo Night",
    val terminalFontFamily: String = "JetBrains Mono",
    val terminalBracketedPaste: Boolean = true,
    val terminalHapticFeedback: Boolean = true,
    val terminalAccessoryKeys: String = "Esc,Tab,Ctrl,Alt,/,|,~,Up,Down",
    val terminalAccessorySingleRow: Boolean = false,
    val terminalSideMarginDp: Int = 2,
    val terminalRightMarginDp: Int = 2,
    val terminalAccessoryPopups: Boolean = true,
    val terminalAccessoryFullScroll: Boolean = false,
    val terminalKeepScreenOn: Boolean = false,
    val serverCardNetworkMode: ServerCardNetworkMode = ServerCardNetworkMode.Totals,
    val serverCardDiskMode: ServerCardDiskMode = ServerCardDiskMode.Usage,
    val serverMetricColorPreset: ServerMetricColorPreset = ServerMetricColorPreset.Theme,
    val serverMetricCpuColorHex: String? = null,
    val serverMetricMemoryColorHex: String? = null,
    val serverMetricDiskColorHex: String? = null,
    val serverMetricNetworkColorHex: String? = null,
    val serverMetricLatencyColorHex: String? = null,
    val serverDetailCardOrder: String = ServerDetailCard.defaultOrderCsv(),
    val serverDetailHiddenCards: String = "",
    val homeHeadingFontPath: String? = null,
    val connectionsHeadingFontPath: String? = null,
    val filesHeadingFontPath: String? = null,
    val vaultHeadingFontPath: String? = null,
    val settingsHeadingFontPath: String? = null,
    val sftpDefaultSortModeName: String = "Name",
    val sftpDefaultSortDescending: Boolean = false,
    val sftpShowHiddenByDefault: Boolean = false,
    val autoRefreshSeconds: Int = 2,
    val appLockPinHash: String? = null,
    val appLockPinSalt: String? = null,
    val appLockBiometricEnabled: Boolean = false,
    val appLockRenderArmedAtEpochMillis: Long? = null
)

data class ConnectionEvent(
    val id: String,
    val serverId: String,
    val atEpochMillis: Long,
    val level: ConnectionEventLevel,
    val message: String
)

data class CrashLogEntry(
    val id: String,
    val atEpochMillis: Long,
    val threadName: String,
    val throwableClass: String,
    val message: String,
    val stackTrace: String
)

data class ServerProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val group: String,
    val tags: List<String>,
    val osName: String,
    val osVersion: String,
    val accent: ServerAccent,
    val credentialId: String?,
    val terminalProfileId: String,
    val monitoringConfig: MonitoringConfig,
    val favorite: Boolean = false,
    val startupCommand: String = "",
    val startDirectory: String = "",
    val environment: List<ConnectionCommand> = emptyList(),
    val proxyJumpHostId: String? = null,
    val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    val customLogoUri: String? = null,
    val notes: String = "",
    val wakeOnLan: WakeOnLanConfig? = null,
    val connectTimeoutSeconds: Int = 10,
    val sshCompressionEnabled: Boolean = false,
    val protocol: ConnectionProtocol = ConnectionProtocol.Ssh,
    val moshConfig: MoshConfig = MoshConfig(),
    val eternalTerminalConfig: EternalTerminalConfig = EternalTerminalConfig(),
    val vncConfig: VncProfileConfig = VncProfileConfig(),
    val rdpConfig: RdpProfileConfig = RdpProfileConfig(),
    val fileConfig: FileProtocolConfig = FileProtocolConfig(),
    val prootConfig: ProotProfileConfig = ProotProfileConfig()
)

data class MoshConfig(
    val serverCommand: String = "mosh-server",
    val locale: String = "en_US.UTF-8",
    val colors: Int = 256,
    val predictionMode: String = "adaptive"
)

data class EternalTerminalConfig(
    val sshBootstrapPort: Int = 22,
    val etServerPort: Int = 2022,
    val terminalType: String = "xterm-256color",
    val serverCommand: String = "etterminal"
)

data class VncProfileConfig(
    val colorDepthBits: Int = 24,
    val shared: Boolean = true,
    val viewOnly: Boolean = false,
    val targetFps: Int = 30,
    val tunnelOverSsh: Boolean = false,
    val sshBootstrapPort: Int = 22
)

data class RdpProfileConfig(
    val width: Int = 1600,
    val height: Int = 900,
    val colorDepth: Int = 16,
    val domain: String = "",
    val useNla: Boolean = true,
    val tunnelOverSsh: Boolean = false,
    val sshBootstrapPort: Int = 22
)

data class FileProtocolConfig(
    val rootPath: String = "",
    val transferConcurrency: Int = 2,
    val resumeTransfers: Boolean = true,
    val verifyChecksums: Boolean = false
)

data class ProotProfileConfig(
    val distroId: String = "alpine-3.21",
    val rootfsPath: String = "",
    val mountHome: Boolean = true
)

data class WakeOnLanConfig(
    val macAddress: String,
    val broadcastAddress: String = "255.255.255.255",
    val secureOnPassword: String? = null
)

data class ServerAccent(
    val name: String,
    val argb: Long
)

data class MonitoringConfig(
    val enabled: Boolean,
    val pollIntervalSeconds: Int,
    val useOptionalAgent: Boolean
)

data class ReconnectPolicy(
    val autoReconnect: Boolean = true,
    val keepAliveSeconds: Int = 30,
    val maxAttempts: Int = 3
)

data class ConnectionCommand(
    val key: String,
    val value: String
)

data class Credential(
    val id: String,
    val label: String,
    val type: CredentialType,
    val publicKeyPreview: String?,
    val encryptedPayloadRef: String,
    val createdAtEpochMillis: Long,
    val passphraseRef: String? = null,
    val lastUsedEpochMillis: Long = 0L,
    val username: String = "",
    val group: String = "",
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val favorite: Boolean = false,
    val importedAtEpochMillis: Long = 0L
) {
    val secretBacked: Boolean
        get() = encryptedPayloadRef.startsWith("secret-")
}

data class KnownHost(
    val id: String,
    val host: String,
    val port: Int,
    val algorithm: String,
    val fingerprint: String,
    val trusted: Boolean,
    val firstSeenEpochMillis: Long,
    val lastSeenEpochMillis: Long,
    val trustState: HostKeyTrustState = if (trusted) HostKeyTrustState.Trusted else HostKeyTrustState.Unknown
)

data class TerminalProfile(
    val id: String,
    val name: String,
    val fontSizeSp: Int,
    val fontFamily: String,
    val themeName: String,
    val cursorStyle: TerminalCursorStyle,
    val scrollbackLines: Int,
    val keyRows: List<TerminalKeyRow>,
    val foregroundHex: String = "#E8EDF8",
    val backgroundHex: String = "#070A12",
    val cursorHex: String = "#21C7E8",
    val selectionHex: String = "#334155",
    val ansiColorsHex: List<String> = emptyList(),
    val bracketedPaste: Boolean = true,
    val hapticFeedback: Boolean = true,
    val accessorySingleRow: Boolean = false,
    val sideMarginDp: Int = 2,
    val rightMarginDp: Int = 2,
    val accessoryPopups: Boolean = true,
    val accessoryFullScroll: Boolean = false,
    val keepScreenOn: Boolean = false
)

data class TerminalKeyRow(
    val id: String,
    val keys: List<TerminalKey>
)

data class TerminalKey(
    val label: String,
    val sequence: String
)

data class TerminalSessionRecord(
    val id: String,
    val serverId: String,
    val title: String,
    val status: ServerStatus,
    val startedAtEpochMillis: Long,
    val lastActiveEpochMillis: Long,
    val transcriptPreview: String = "",
    val tmuxSessionName: String? = null,
    val tmuxWindowIndex: Int? = null
)

data class PortForwardRule(
    val id: String,
    val serverId: String,
    val type: PortForwardType,
    val localHost: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int,
    val enabled: Boolean,
    val autoStart: Boolean,
    val label: String = "",
    val group: String = "",
    val favorite: Boolean = false
)

data class SftpBookmark(
    val id: String,
    val serverId: String,
    val label: String,
    val path: String,
    val createdAtEpochMillis: Long
)

data class TransferRecord(
    val id: String,
    val serverId: String,
    val direction: TransferDirection,
    val remotePath: String,
    val localDisplayName: String,
    val progress: Float,
    val state: TransferRecordState,
    val message: String,
    val updatedAtEpochMillis: Long
)

data class Snippet(
    val id: String,
    val name: String,
    val command: String,
    val tags: List<String>,
    val serverScope: String?,
    val variables: List<String>,
    val description: String = "",
    val group: String = "",
    val favorite: Boolean = false,
    val confirmBeforeRun: Boolean = true,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val autoRun: Boolean = false
)

data class MetricSnapshot(
    val serverId: String,
    val status: ServerStatus,
    val latencyMs: Int?,
    val uptime: String,
    val cpu: CpuMetrics,
    val memory: MemoryMetrics,
    val disk: DiskMetrics,
    val network: NetworkMetrics,
    val processes: ProcessSummary,
    val services: ServiceSummary,
    val docker: DockerSummary,
    val collectedAtEpochMillis: Long,
    val sensors: List<SensorMetric> = emptyList(),
    val batteries: List<BatteryMetric> = emptyList(),
    val smartDisks: List<SmartDiskMetric> = emptyList(),
    val pveResources: List<PveResourceMetric> = emptyList(),
    val gpus: List<GpuMetric> = emptyList(),
    val packageUpdates: List<PackageUpdateMetric> = emptyList()
)

data class PackageUpdateMetric(
    val manager: String,
    val name: String,
    val currentVersion: String,
    val candidateVersion: String
)

data class SensorMetric(
    val device: String,
    val adapter: String,
    val label: String,
    val value: String
)

data class BatteryMetric(
    val name: String,
    val status: String,
    val capacityPercent: Int?,
    val health: String,
    val technology: String,
    val timeToEmptySeconds: Long? = null,
    val timeToFullSeconds: Long? = null
)

data class SmartDiskMetric(
    val device: String,
    val model: String? = null,
    val serial: String? = null,
    val healthy: Boolean? = null,
    val selfTestStatus: String? = null,
    val temperatureCelsius: Int?,
    val powerOnHours: Int?,
    val powerCycleCount: Int?,
    val lifeLeftPercent: Int?,
    val lifetimeWritesGiB: Long?,
    val lifetimeReadsGiB: Long?,
    val unsafeShutdowns: Int?
)

data class PveResourceMetric(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val node: String,
    val vmId: Int?,
    val cpuUsagePercent: Int?,
    val memoryUsedBytes: Long?,
    val memoryMaxBytes: Long?,
    val diskUsedBytes: Long?,
    val diskMaxBytes: Long?,
    val uptimeSeconds: Long?
)

data class GpuMetric(
    val id: String,
    val name: String,
    val vendor: String,
    val utilizationPercent: Int?,
    val memoryUsedMiB: Int?,
    val memoryTotalMiB: Int?,
    val temperatureCelsius: Int?,
    val powerDrawWatts: Float?,
    val powerLimitWatts: Float?,
    val fanSpeed: String?,
    val clockMhz: Int?
)

data class CpuMetrics(
    val usagePercent: Int,
    val cores: Int,
    val model: String,
    val userPercent: Int,
    val systemPercent: Int,
    val nicePercent: Int,
    val ioWaitPercent: Int,
    val stealPercent: Int,
    val load1: Float,
    val load5: Float,
    val load15: Float,
    val recentLoad: List<Float>,
    val perCore: List<CpuCoreMetrics> = emptyList()
)

data class CpuCoreMetrics(
    val index: Int,
    val usagePercent: Int,
    val userPercent: Int,
    val systemPercent: Int,
    val nicePercent: Int,
    val ioWaitPercent: Int,
    val stealPercent: Int
)

data class MemoryMetrics(
    val usedMb: Int,
    val totalMb: Int,
    val swapUsedMb: Int,
    val swapTotalMb: Int
) {
    val usagePercent: Int
        get() = if (totalMb == 0) 0 else ((usedMb.toFloat() / totalMb) * 100).toInt()
}

data class DiskMetrics(
    val usedGb: Float,
    val totalGb: Float,
    val readPerSecond: String,
    val writePerSecond: String,
    val readTotal: String = "--",
    val writeTotal: String = "--",
    val filesystems: List<FilesystemMetric> = emptyList()
) {
    val usagePercent: Int
        get() = if (totalGb == 0f) 0 else ((usedGb / totalGb) * 100).toInt()
}

data class FilesystemMetric(
    val mountPoint: String,
    val filesystem: String,
    val usedGb: Float,
    val totalGb: Float,
    val sourcePath: String = ""
) {
    val usagePercent: Int
        get() = if (totalGb == 0f) 0 else ((usedGb / totalGb) * 100).toInt()
}

data class NetworkMetrics(
    val primaryInterface: NetworkInterfaceMetric,
    val interfaces: List<NetworkInterfaceMetric>,
    val history: NetworkHistory
)

data class NetworkInterfaceMetric(
    val name: String,
    val address: String,
    val uploadRate: String,
    val downloadRate: String,
    val uploadTotal: String,
    val downloadTotal: String,
    val uploadShare: Float
)

data class NetworkHistory(
    val uploadLabel: String,
    val downloadLabel: String,
    val uploadBars: List<Float>,
    val downloadBars: List<Float>,
    val labels: List<String>,
    val vnStat: VnStatUsage? = null
)

data class VnStatUsage(
    val day: VnStatPeriodUsage? = null,
    val week: VnStatPeriodUsage? = null,
    val month: VnStatPeriodUsage? = null,
    val year: VnStatPeriodUsage? = null
) {
    fun forRange(range: VnStatRange): VnStatPeriodUsage? {
        return when (range) {
            VnStatRange.Day -> day
            VnStatRange.Week -> week
            VnStatRange.Month -> month
            VnStatRange.Year -> year
        }
    }
}

enum class VnStatRange {
    Day,
    Week,
    Month,
    Year
}

data class VnStatPeriodUsage(
    val receivedBytes: Long,
    val transmittedBytes: Long,
    val totalBytes: Long,
    val label: String
)

data class ProcessSummary(
    val total: Int,
    val running: Int,
    val topProcess: String,
    val items: List<ProcessMetric> = emptyList()
)

data class ProcessMetric(
    val state: String,
    val cpuPercent: Float?,
    val memoryPercent: Float?,
    val command: String,
    val pid: Int? = null,
    val user: String = "",
    val parentPid: Int? = null,
    val rssKb: Long? = null,
    val virtualSizeKb: Long? = null,
    val elapsed: String = ""
)

data class ServiceSummary(
    val total: Int,
    val failed: Int,
    val failedItems: List<ServiceMetric> = emptyList(),
    val items: List<ServiceMetric> = emptyList()
)

data class ServiceMetric(
    val unit: String,
    val load: String,
    val active: String,
    val sub: String,
    val description: String
)

data class DockerSummary(
    val containers: Int,
    val running: Int,
    val items: List<ContainerMetric> = emptyList(),
    val images: List<ContainerImageMetric> = emptyList()
)

data class ContainerMetric(
    val id: String,
    val name: String,
    val image: String,
    val status: String,
    val state: String,
    val engine: String,
    val cpuPercent: Float? = null,
    val memoryPercent: Float? = null,
    val memoryUsedBytes: Long? = null,
    val memoryMaxBytes: Long? = null,
    val networkIo: String = "",
    val blockIo: String = ""
)

data class ContainerImageMetric(
    val engine: String,
    val repository: String,
    val tag: String,
    val id: String,
    val created: String,
    val size: String
)
