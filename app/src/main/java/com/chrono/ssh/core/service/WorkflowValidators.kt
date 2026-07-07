package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.HostKeyTrustState
import com.chrono.ssh.core.model.KnownHost
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.ConnectionProtocol
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ConnectionCommand
import com.chrono.ssh.core.model.ConnectionEvent
import com.chrono.ssh.core.model.ConnectionEventLevel
import com.chrono.ssh.core.model.CrashLogEntry
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.SftpBookmark
import com.chrono.ssh.core.model.Snippet
import com.chrono.ssh.core.model.TerminalKey
import com.chrono.ssh.core.model.TransferDirection
import com.chrono.ssh.core.model.TransferRecord
import com.chrono.ssh.core.model.TransferRecordState
import java.security.SecureRandom
import java.util.Base64
import java.net.InetAddress
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlinx.coroutines.Job

object ServerStatusRefreshPolicy {
    const val ServerBoxDefaultSeconds = 2
    const val MinEnabledSeconds = 1
    const val MaxEnabledSeconds = 10
    val PresetSeconds = listOf(1, 2, 5, 10)

    fun normalize(seconds: Int?): Int {
        return when {
            seconds == null -> ServerBoxDefaultSeconds
            seconds < MinEnabledSeconds || seconds > MaxEnabledSeconds -> ServerBoxDefaultSeconds
            else -> seconds
        }
    }

    fun effectiveIntervalSeconds(appSeconds: Int?, hostSeconds: Int?): Int {
        return minOf(normalize(appSeconds), normalize(hostSeconds), ServerBoxDefaultSeconds)
    }

    fun liveLoopSeconds(appSeconds: Int?): Int {
        return minOf(normalize(appSeconds), ServerBoxDefaultSeconds)
    }

    fun presetLabels(): List<String> {
        return PresetSeconds.map { "${it}s" }
    }
}

object MonitoringSkipDiagnosticPolicy {
    const val ThrottleMillis = 30L * 1000L

    fun shouldEmit(lastEpochMillis: Long?, nowEpochMillis: Long): Boolean {
        val last = lastEpochMillis ?: return true
        return nowEpochMillis - last >= ThrottleMillis
    }
}

class MonitoringProbeInFlightRegistry {
    private val runningServerIds = mutableSetOf<String>()

    @Synchronized
    fun tryBegin(serverId: String): Boolean {
        if (serverId in runningServerIds) return false
        runningServerIds += serverId
        return true
    }

    @Synchronized
    fun finish(serverId: String) {
        runningServerIds -= serverId
    }

    @Synchronized
    fun isRunning(serverId: String): Boolean {
        return serverId in runningServerIds
    }
}

object HostCommandSafety {
    private val privilegedCommandPattern = Regex(
        pattern = "(^|[;&|`$()\\s])(sudo|doas|pkexec|su)(\\s|$)",
        option = RegexOption.IGNORE_CASE
    )

    fun isAutomaticCommandSafe(command: String): Boolean {
        return !privilegedCommandPattern.containsMatchIn(command)
    }

    fun unsafeAutomaticCommandMessage(label: String = "Automatic commands"): String {
        return "$label cannot include sudo, doas, pkexec, or su."
    }
}

object SudoPromptDetector {
    private const val TailChars = 2048
    private val ansiPattern = Regex("\\u001B\\[[0-?]*[ -/]*[@-~]")
    private val englishPattern = Regex("(\\[sudo]\\s*)?password\\s*(for\\s+[^:\\n\\r\\uFF1A]+)?\\s*[:\\uFF1A]\\s*$", RegexOption.IGNORE_CASE)
    private val chinesePattern = Regex("(\\u8bf7\\u8f93\\u5165.*)?\\u5bc6\\u7801\\s*[:\\uFF1A]\\s*$")

    fun isPrompt(outputTail: String): Boolean {
        val clean = ansiPattern
            .replace(outputTail.takeLast(TailChars), "")
            .trimEnd()
        if (clean.isBlank()) return false
        val lastLine = clean.lineSequence().lastOrNull()?.trim().orEmpty()
        return englishPattern.containsMatchIn(lastLine) || chinesePattern.containsMatchIn(lastLine)
    }
}

object ContainerRuntimeActionPolicy {
    private val enginePattern = Regex("docker|podman")
    private val actionPattern = Regex("start|stop|restart|delete|force-delete|remove-image")
    private val inspectActionPattern = Regex("logs|inspect|stats")
    private val globalActionPattern = Regex("images|prune-containers|prune-images|prune-volumes|prune-system")
    private val targetPattern = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")

    fun command(engine: String, target: String, action: String): String? {
        val cleanEngine = engine.trim().lowercase()
        val cleanAction = action.trim().lowercase()
        val cleanTarget = target.trim()
        if (!enginePattern.matches(cleanEngine)) return null
        if (!actionPattern.matches(cleanAction)) return null
        if (!targetPattern.matches(cleanTarget)) return null
        return when (cleanAction) {
            "delete" -> "$cleanEngine rm $cleanTarget"
            "force-delete" -> "$cleanEngine rm -f $cleanTarget"
            "remove-image" -> "$cleanEngine rmi -f $cleanTarget"
            else -> "$cleanEngine $cleanAction $cleanTarget"
        }
    }

    fun inspectCommand(engine: String, target: String, action: String): String? {
        val cleanEngine = engine.trim().lowercase()
        val cleanAction = action.trim().lowercase()
        val cleanTarget = target.trim()
        if (!enginePattern.matches(cleanEngine)) return null
        if (!inspectActionPattern.matches(cleanAction)) return null
        if (!targetPattern.matches(cleanTarget)) return null
        return when (cleanAction) {
            "logs" -> "$cleanEngine logs --tail 120 $cleanTarget"
            "inspect" -> "$cleanEngine inspect $cleanTarget"
            else -> "$cleanEngine stats --no-stream $cleanTarget"
        }
    }

    fun actionsFor(state: String): List<String> {
        val inspect = listOf("logs", "inspect", "stats")
        val mutate = if (state.equals("running", ignoreCase = true)) {
            listOf("restart", "stop", "force-delete")
        } else {
            listOf("start", "delete")
        }
        return inspect + mutate
    }

    fun globalCommand(engine: String, action: String): String? {
        val cleanEngine = engine.trim().lowercase()
        val cleanAction = action.trim().lowercase()
        if (!enginePattern.matches(cleanEngine)) return null
        if (!globalActionPattern.matches(cleanAction)) return null
        return when (cleanAction) {
            "images" -> "$cleanEngine images"
            "prune-containers" -> "$cleanEngine container prune -f"
            "prune-images" -> "$cleanEngine image prune -a -f"
            "prune-volumes" -> "$cleanEngine volume prune -f"
            else -> "$cleanEngine system prune -a -f --volumes"
        }
    }

    fun globalActions(): List<String> {
        return listOf("prune-containers", "prune-images", "prune-volumes", "prune-system")
    }
}

object SystemdServiceActionPolicy {
    private val actionPattern = Regex("start|stop|restart")
    private val inspectActionPattern = Regex("status|logs")
    private val unitPattern = Regex("[A-Za-z0-9_.@:-]{1,160}\\.service")

    fun command(unit: String, action: String): String? {
        val cleanUnit = unit.trim()
        val cleanAction = action.trim().lowercase()
        if (!unitPattern.matches(cleanUnit)) return null
        if (!actionPattern.matches(cleanAction)) return null
        return "systemctl $cleanAction $cleanUnit"
    }

    fun inspectCommand(unit: String, action: String): String? {
        val cleanUnit = unit.trim()
        val cleanAction = action.trim().lowercase()
        if (!unitPattern.matches(cleanUnit)) return null
        if (!inspectActionPattern.matches(cleanAction)) return null
        return when (cleanAction) {
            "status" -> "systemctl status $cleanUnit --no-pager -l"
            else -> "journalctl -u $cleanUnit -n 80 --no-pager"
        }
    }

    fun actionsFor(active: String, sub: String): List<String> {
        val mutate = if (active.equals("active", ignoreCase = true) || sub.equals("running", ignoreCase = true)) {
            listOf("restart", "stop")
        } else {
            listOf("restart", "start")
        }
        return listOf("status", "logs") + mutate
    }
}

object ProcessActionPolicy {
    private val actionSignals = mapOf(
        "terminate" to "TERM",
        "force-stop" to "KILL"
    )
    private val priorityActions = mapOf(
        "lower-priority" to 10
    )

    fun command(pid: Int?, action: String): String? {
        val cleanPid = pid?.takeIf { it > 0 } ?: return null
        val cleanAction = action.trim().lowercase()
        priorityActions[cleanAction]?.let { nice -> return "renice -n $nice -p $cleanPid" }
        val signal = actionSignals[cleanAction] ?: return null
        return "kill -s $signal $cleanPid"
    }

    fun actionsFor(pid: Int?): List<String> {
        return if (pid != null && pid > 0) listOf("lower-priority", "terminate", "force-stop") else emptyList()
    }
}

enum class ConnectionLaunchSurface {
    Terminal,
    DesktopViewer,
    FileBrowser
}

object ConnectionLaunchPolicy {
    fun surface(protocol: ConnectionProtocol): ConnectionLaunchSurface {
        return when (protocol) {
            ConnectionProtocol.Ssh,
            ConnectionProtocol.Mosh,
            ConnectionProtocol.EternalTerminal,
            ConnectionProtocol.LocalProot -> ConnectionLaunchSurface.Terminal
            ConnectionProtocol.Vnc,
            ConnectionProtocol.Rdp -> ConnectionLaunchSurface.DesktopViewer
            ConnectionProtocol.Smb,
            ConnectionProtocol.Rclone -> ConnectionLaunchSurface.FileBrowser
        }
    }
}

object HostInfoCommandPolicy {
    val command: String = listOf(
        "printf 'Hostname: '; hostname 2>/dev/null || true",
        "printf 'User: '; whoami 2>/dev/null || true",
        "printf 'Kernel: '; uname -srmo 2>/dev/null || uname -a 2>/dev/null || true",
        "printf 'OS: '; . /etc/os-release 2>/dev/null && printf '%s %s\\n' \"\$NAME\" \"\$VERSION_ID\" || true",
        "printf 'Default route: '; ip route get 1.1.1.1 2>/dev/null | head -n 1 || true",
        "printf 'Listening ports:\\n'; ss -tulpen 2>/dev/null | head -n 12 || netstat -tulpen 2>/dev/null | head -n 12 || true"
    ).joinToString("\n")

    fun display(output: String, maxChars: Int = 12_000): String {
        return output
            .lineSequence()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
            .take(maxChars)
            .ifBlank { "No host info returned." }
    }
}

object DefaultSnippetCatalog {
    val snippets = listOf(
        Snippet("snip-uptime", "Show uptime", "uptime", listOf("diagnostics"), null, emptyList()),
        Snippet("snip-docker", "Docker status", "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Ports}}'", listOf("docker"), null, emptyList()),
        Snippet("snip-tail", "Tail service logs", "journalctl -u {{service}} -f", listOf("systemd"), null, listOf("service")),
        Snippet("snip-tmux-main", "Restore tmux main", "tmux new -A -s main", listOf("session", "tmux"), null, emptyList()),
        Snippet("snip-zellij-main", "Restore zellij main", "zellij attach main --create", listOf("session", "zellij"), null, emptyList()),
        Snippet("snip-screen-main", "Restore screen main", "screen -xRR main", listOf("session", "screen"), null, emptyList())
    ).filter { HostCommandSafety.isAutomaticCommandSafe(it.command) }
}

data class HostEnvironmentValidation(
    val valid: Boolean,
    val entries: List<ConnectionCommand>,
    val errors: List<String>
)

object HostEnvironmentPolicy {
    private val keyPattern = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun parse(text: String): HostEnvironmentValidation {
        val errors = mutableListOf<String>()
        val entries = text
            .split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .mapNotNull { token ->
                val key = token.substringBefore("=", missingDelimiterValue = "").trim()
                val value = token.substringAfter("=", missingDelimiterValue = "").trim()
                when {
                    key.isBlank() || value.isBlank() -> {
                        errors += "Environment entries must use KEY=value."
                        null
                    }
                    !key.matches(keyPattern) -> {
                        errors += "Environment key '$key' must start with a letter or underscore and contain only letters, numbers, and underscores."
                        null
                    }
                    value.length > 512 -> {
                        errors += "Environment value for '$key' is too long."
                        null
                    }
                    else -> ConnectionCommand(key, value)
                }
            }
            .distinctBy { it.key }
            .take(16)
        return HostEnvironmentValidation(errors.isEmpty(), entries, errors.distinct())
    }

    fun serialize(entries: List<ConnectionCommand>): String {
        return entries.joinToString(", ") { "${it.key}=${it.value}" }
    }
}

object HostEndpointValidator {
    fun errorFor(host: String): String? {
        val clean = host.trim()
        return when {
            clean.isBlank() -> "Host is required."
            clean.startsWith("http://", ignoreCase = true) ||
                clean.startsWith("https://", ignoreCase = true) ->
                "Enter only the hostname or IP address, not a URL."
            clean.any { it.isWhitespace() } ||
                clean.contains('/') ||
                clean.contains('\\') ||
                clean.contains(';') ||
                clean.contains('|') ||
                clean.contains('`') ->
                "Host must be a hostname or IP address, not a command or path."
            else -> null
        }
    }

    fun requireValid(host: String) {
        errorFor(host)?.let { throw IllegalArgumentException(it) }
    }
}

object ProxyJumpPolicy {
    fun validSelection(target: ServerProfile, jump: ServerProfile?): Boolean {
        return jump == null || (jump.id != target.id && jump.credentialId != null)
    }

    fun resolveTarget(
        target: ServerProfile,
        candidates: List<ServerProfile>,
        credentialFor: (ServerProfile) -> Credential?
    ): ProxyJumpResolution {
        val cleanJumpHostId = target.proxyJumpHostId?.trim()?.takeIf { it.isNotBlank() }
            ?: return ProxyJumpResolution(null, null)
        val jump = candidates.firstOrNull { it.id == cleanJumpHostId }
            ?: return ProxyJumpResolution(null, "Selected jump host is no longer available.")
        errorForSelection(target, jump.id, candidates)?.let { return ProxyJumpResolution(null, it) }
        val credential = credentialFor(jump)
            ?: return ProxyJumpResolution(null, "Jump host identity is no longer available.")
        return ProxyJumpResolution(ProxyJumpTarget(jump, credential), null)
    }

    fun errorForSelection(target: ServerProfile, jumpHostId: String?, candidates: List<ServerProfile>): String? {
        val cleanJumpHostId = jumpHostId?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val jump = candidates.firstOrNull { it.id == cleanJumpHostId }
            ?: return "Selected jump host is no longer available."
        return errorFor(target, jump) ?: if (createsCycle(target, jump, candidates)) {
            "ProxyJump chain cannot loop back to this host."
        } else {
            null
        }
    }

    fun errorFor(target: ServerProfile, jump: ServerProfile?): String? {
        return when {
            jump == null -> null
            jump.id == target.id -> "A host cannot use itself as a jump host."
            jump.credentialId == null -> "Jump host needs a saved identity."
            else -> null
        }
    }

    fun createsCycle(target: ServerProfile, jump: ServerProfile, candidates: List<ServerProfile>): Boolean {
        val byId = candidates.associateBy { it.id }
        val seen = mutableSetOf(target.id)
        var cursor: ServerProfile? = jump
        while (cursor != null) {
            if (!seen.add(cursor.id)) return true
            val nextId = cursor.proxyJumpHostId?.trim()?.takeIf { it.isNotBlank() } ?: return false
            cursor = byId[nextId]
        }
        return false
    }
}

object WakeOnLanPolicy {
    private val macPattern = Regex("^[0-9A-Fa-f]{2}([:-]?[0-9A-Fa-f]{2}){5}$")
    private val secureOnPattern = Regex("^[0-9A-Fa-f]{2}([:-]?[0-9A-Fa-f]{2}){5}$")

    fun normalizeMac(value: String): String? {
        val clean = value.trim()
        if (clean.isBlank()) return null
        if (!macPattern.matches(clean)) return null
        return clean.hexBytesOrNull()?.joinToString(":") { "%02X".format(it) }
    }

    fun normalizeBroadcast(value: String): String? {
        val clean = value.trim().ifBlank { "255.255.255.255" }
        return runCatching { InetAddress.getByName(clean).hostAddress }.getOrNull()
    }

    fun normalizeSecureOn(value: String?): String? {
        val clean = value?.trim().orEmpty()
        if (clean.isBlank()) return null
        if (!secureOnPattern.matches(clean)) return null
        return clean.hexBytesOrNull()?.joinToString(":") { "%02X".format(it) }
    }

    fun errorFor(macAddress: String, broadcastAddress: String, secureOnPassword: String?): String? {
        if (macAddress.isBlank() && secureOnPassword.isNullOrBlank()) return null
        return when {
            normalizeMac(macAddress) == null -> "Wake-on-LAN MAC must be 6 hex bytes."
            normalizeBroadcast(broadcastAddress) == null -> "Wake-on-LAN broadcast address is invalid."
            secureOnPassword?.isNotBlank() == true && normalizeSecureOn(secureOnPassword) == null -> "Wake-on-LAN SecureON password must be 6 hex bytes."
            else -> null
        }
    }

    private fun String.hexBytesOrNull(): ByteArray? {
        val hex = filter { it != ':' && it != '-' }
        if (hex.length != 12) return null
        return runCatching {
            ByteArray(6) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }
}

data class ProxyJumpResolution(
    val target: ProxyJumpTarget?,
    val error: String?
)

data class PinHashResult(
    val hash: String,
    val salt: String
)

object PinLockPolicy {
    private const val Iterations = 120_000
    private const val KeyBits = 256
    private const val MaxPersistedHashChars = 128
    private const val MaxPersistedSaltChars = 128
    private val pinPattern = Regex("\\d{6,12}")

    fun validatePin(pin: String): String? {
        return if (pin.matches(pinPattern)) null else "PIN must be 6-12 digits."
    }

    fun hashPin(pin: String, salt: String = newSalt()): PinHashResult {
        require(validatePin(pin) == null) { "PIN must be 6-12 digits." }
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, Iterations, KeyBits)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return PinHashResult(Base64.getEncoder().withoutPadding().encodeToString(bytes), salt)
    }

    fun verify(pin: String, hash: String?, salt: String?): Boolean {
        if (validatePin(pin) != null || hash.isNullOrBlank() || salt.isNullOrBlank()) return false
        return runCatching { hashPin(pin, salt).hash == hash }.getOrDefault(false)
    }

    fun persistedPinUsable(hash: String?, salt: String?): Boolean {
        if (hash.isNullOrBlank() || salt.isNullOrBlank()) return false
        if (hash.length > MaxPersistedHashChars || salt.length > MaxPersistedSaltChars) return false
        return runCatching {
            val saltBytes = Base64.getDecoder().decode(salt)
            Base64.getDecoder().decode(hash).size == KeyBits / 8 &&
                saltBytes.size in 8..64
        }.getOrDefault(false)
    }

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().withoutPadding().encodeToString(bytes)
    }
}

data class SnippetValidation(
    val valid: Boolean,
    val normalized: Snippet?,
    val errors: List<String>
)

object SnippetValidator {
    private const val NameMaxChars = 80
    private const val DescriptionMaxChars = 240
    private const val TagMaxChars = 32
    private const val GroupMaxChars = 48
    private val variablePattern = Regex("""\{\{\s*([A-Za-z][A-Za-z0-9_]*)\s*\}\}""")

    fun validate(snippet: Snippet): SnippetValidation {
        val errors = mutableListOf<String>()
        val name = cleanDisplayText(snippet.name).take(NameMaxChars)
        val description = cleanDisplayText(snippet.description).take(DescriptionMaxChars)
        val group = cleanDisplayText(snippet.group).take(GroupMaxChars)
        val command = snippet.command.trim()
        if (name.isBlank()) errors += "Snippet name is required."
        if (command.isBlank()) errors += "Command is required."
        if (command.length > 4000) errors += "Command is too long."
        if (!HostCommandSafety.isAutomaticCommandSafe(command)) {
            errors += HostCommandSafety.unsafeAutomaticCommandMessage("Snippets")
        }
        val tags = snippet.tags
            .flatMap { it.split(',', ' ') }
            .map { cleanDisplayText(it).lowercase().take(TagMaxChars) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(8)
        val variables = (snippet.variables + variablePattern.findAll(command).map { it.groupValues[1] })
            .map { it.trim() }
            .filter { it.matches(Regex("[A-Za-z][A-Za-z0-9_]*")) }
            .distinct()
            .take(12)
        val normalized = snippet.copy(
            id = snippet.id.ifBlank { "snippet-${System.currentTimeMillis()}" },
            name = name,
            description = description,
            group = group,
            command = command,
            tags = tags,
            variables = variables,
            serverScope = snippet.serverScope?.takeIf { it.isNotBlank() },
            createdAtEpochMillis = snippet.createdAtEpochMillis.takeIf { it > 0 } ?: System.currentTimeMillis(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        return SnippetValidation(errors.isEmpty(), normalized.takeIf { errors.isEmpty() }, errors)
    }

    private fun cleanDisplayText(value: String): String {
        return value.trim().map { if (it.isISOControl()) ' ' else it }.joinToString("")
    }
}

object SnippetPersistencePolicy {
    fun normalizeLoaded(snippet: Snippet): Snippet? {
        val id = snippet.id.trim()
        if (id.isBlank()) return null
        return SnippetValidator.validate(
            snippet.copy(
                id = id,
                serverScope = snippet.serverScope?.trim()?.ifBlank { null }
            )
        ).normalized
    }
}

data class PortForwardValidation(
    val valid: Boolean,
    val normalized: PortForwardRule?,
    val errors: List<String>
)

object PortForwardValidator {
    fun validate(rule: PortForwardRule): PortForwardValidation {
        val errors = mutableListOf<String>()
        val localHost = rule.localHost.ifBlank { "127.0.0.1" }
        val remoteHost = rule.remoteHost.ifBlank { "127.0.0.1" }
        val label = rule.label.trim().take(80)
        val group = rule.group.trim().take(48)
        if (localHost.isInvalidForwardHost()) errors += "Local address must be a host or IP address, not a URL or command."
        if (rule.type != PortForwardType.DynamicSocks && remoteHost.isInvalidForwardHost()) {
            errors += "Remote address must be a host or IP address, not a URL or command."
        }
        if (rule.type in setOf(PortForwardType.Local, PortForwardType.DynamicSocks) && rule.localPort !in 1024..65535) {
            errors += "Local port must be between 1024 and 65535 on Android."
        }
        if (rule.type == PortForwardType.Remote && rule.localPort !in 1..65535) {
            errors += "Local target port must be between 1 and 65535."
        }
        if (rule.type == PortForwardType.Local && rule.remotePort !in 1..65535) {
            errors += "Remote port must be between 1 and 65535."
        }
        if (rule.type == PortForwardType.Remote && rule.remotePort !in 0..65535) {
            errors += "Remote port must be between 0 and 65535."
        }
        if (rule.id.isBlank()) errors += "Forward id is required."
        if (rule.serverId.isBlank()) errors += "Forward must be attached to a host."
        val normalized = rule.copy(
            id = rule.id.trim(),
            serverId = rule.serverId.trim(),
            localHost = localHost,
            remoteHost = if (rule.type == PortForwardType.DynamicSocks) "" else remoteHost,
            remotePort = if (rule.type == PortForwardType.DynamicSocks) 0 else rule.remotePort,
            label = label,
            group = group
        )
        return PortForwardValidation(errors.isEmpty(), normalized.takeIf { errors.isEmpty() }, errors)
    }
}

private fun String.isInvalidForwardHost(): Boolean {
    return isBlank() ||
        any { it.isWhitespace() } ||
        contains('/') ||
        contains('\\') ||
        contains(';') ||
        contains('|') ||
        contains('`') ||
        startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)
}

object ForwardRuntimePolicy {
    fun starting(rule: PortForwardRule): ForwardStatus {
        return ForwardStatus(
            ruleId = rule.id,
            boundAddress = rule.boundAddress(),
            active = false,
            lastMessage = "Starting tunnel"
        )
    }

    fun stopped(rule: PortForwardRule): ForwardStatus {
        return ForwardStatus(
            ruleId = rule.id,
            boundAddress = rule.boundAddress(),
            active = false,
            lastMessage = "Stopped"
        )
    }

    fun running(
        rule: PortForwardRule,
        boundAddress: String = rule.boundAddress(),
        lastMessage: String = "Running"
    ): ForwardStatus {
        return ForwardStatus(
            ruleId = rule.id,
            boundAddress = boundAddress,
            active = true,
            lastMessage = lastMessage
        )
    }

    fun failed(rule: PortForwardRule, message: String): ForwardStatus {
        val cleanMessage = ForwardErrorMapper.message(message, rule.type)
        return ForwardStatus(
            ruleId = rule.id,
            boundAddress = rule.boundAddress(),
            active = false,
            lastMessage = "Failed",
            lastError = cleanMessage
        )
    }

    private fun PortForwardRule.boundAddress(): String {
        return when (type) {
            PortForwardType.Local, PortForwardType.DynamicSocks -> "${localHost.ifBlank { "127.0.0.1" }}:${localPort}"
            PortForwardType.Remote -> "${remoteHost.ifBlank { "127.0.0.1" }}:${remotePort}"
        }
    }
}

object ForwardErrorMapper {
    fun message(raw: String, type: PortForwardType? = null): String {
        val detail = raw.ifBlank { "Tunnel failed." }
        val lower = detail.lowercase()
        return when {
            lower.contains("address already in use") || lower.contains("eaddrinuse") ->
                if (type == PortForwardType.Remote) {
                    "Remote bind port is already in use on the SSH server. Stop the other tunnel or choose another remote port."
                } else {
                    "Local port is already in use. Stop the other app or choose another local port."
                }
            lower.contains("permission denied") || lower.contains("eacces") ->
                if (type == PortForwardType.Remote) {
                    "Remote bind port cannot be bound. Check GatewayPorts, AllowTcpForwarding, and server-side port permissions."
                } else {
                    "Local port cannot be bound. Use a port above 1024 or choose a different bind address."
                }
            lower.contains("host key") || lower.contains("trust") ->
                "Approve this host key before starting the tunnel."
            lower.contains("enter its passphrase") || lower.contains("no passphrase") ||
                (lower.contains("private key") && lower.contains("encrypted")) ->
                "Enter the private-key passphrase before starting the tunnel."
            lower.contains("private-key auth failed") || lower.contains("private key") && lower.contains("could not be loaded") ->
                "Private-key auth failed. Check the selected key, username, passphrase, authorized_keys, and server PubkeyAuthentication settings."
            lower.contains("credential") || lower.contains("authentication") || lower.contains("auth") || lower.contains("exhausted") ->
                "Authentication failed. Check the selected identity, username, passphrase, authorized_keys, and server forwarding policy."
            lower.contains("administratively prohibited") || lower.contains("open failed") && lower.contains("connect failed") ->
                "The SSH server refused the remote target. Check AllowTcpForwarding, PermitOpen, and whether the remote host/port is reachable from the server."
            lower.contains("enetunreach") ||
                lower.contains("network is unreachable") ||
                lower.contains("no route") ||
                lower.contains("connection refused") ||
                lower.contains("timed out") ->
                "Network path failed. Check Wi-Fi/VPN, VM network mode, subnet/firewall rules, and whether this Android device can reach the host."
            else -> detail
        }
    }
}

fun PortForwardType.displayName(): String {
    return when (this) {
        PortForwardType.Local -> "Local"
        PortForwardType.Remote -> "Remote"
        PortForwardType.DynamicSocks -> "Dynamic SOCKS"
    }
}

fun PortForwardRule.routeLabel(): String {
    return when (type) {
        PortForwardType.Local -> "${localHost.ifBlank { "127.0.0.1" }}:$localPort -> $remoteHost:$remotePort"
        PortForwardType.Remote -> "${remoteHost.ifBlank { "127.0.0.1" }}:$remotePort -> ${localHost.ifBlank { "127.0.0.1" }}:$localPort"
        PortForwardType.DynamicSocks -> "${localHost.ifBlank { "127.0.0.1" }}:$localPort SOCKS5"
    }
}

object HostKeyTrustEvaluator {
    fun evaluate(observed: KnownHost, stored: KnownHost?): HostKeyTrustState {
        if (stored == null) return HostKeyTrustState.Unknown
        if (stored.trustState in setOf(HostKeyTrustState.Changed, HostKeyTrustState.Rejected)) {
            return stored.trustState
        }
        if (stored.fingerprint != observed.fingerprint || stored.algorithm != observed.algorithm) {
            return HostKeyTrustState.Changed
        }
        return if (stored.trusted) HostKeyTrustState.Trusted else HostKeyTrustState.Unknown
    }
}

object HostKeyApprovalPolicy {
    fun canApproveStoredFingerprint(knownHost: KnownHost?): Boolean {
        val fingerprint = knownHost?.fingerprint.orEmpty()
        val lowerFingerprint = fingerprint.lowercase()
        return fingerprint.startsWith("SHA256:") &&
            !lowerFingerprint.contains("pending") &&
            !lowerFingerprint.contains("unavailable") &&
            knownHost?.trustState !in setOf(HostKeyTrustState.Changed, HostKeyTrustState.Rejected)
    }
}

object HostKeyObservationPolicy {
    fun remembered(
        server: ServerProfile,
        existing: KnownHost?,
        algorithm: String,
        fingerprint: String,
        nowEpochMillis: Long
    ): KnownHost {
        val observed = observedRecord(server, existing, algorithm, fingerprint, nowEpochMillis)
        val state = HostKeyTrustEvaluator.evaluate(observed, existing)
        val trusted = state !in setOf(HostKeyTrustState.Changed, HostKeyTrustState.Rejected)
        return observed.copy(
            trusted = trusted,
            trustState = if (trusted) HostKeyTrustState.Trusted else state
        )
    }

    fun changedObservation(
        server: ServerProfile,
        existing: KnownHost?,
        algorithm: String,
        fingerprint: String,
        nowEpochMillis: Long
    ): KnownHost? {
        val observed = observedRecord(server, existing, algorithm, fingerprint, nowEpochMillis)
        val state = HostKeyTrustEvaluator.evaluate(observed, existing)
        return observed.copy(trusted = false, trustState = state)
            .takeIf { state in setOf(HostKeyTrustState.Changed, HostKeyTrustState.Rejected) }
    }

    private fun observedRecord(
        server: ServerProfile,
        existing: KnownHost?,
        algorithm: String,
        fingerprint: String,
        nowEpochMillis: Long
    ): KnownHost {
        return KnownHost(
            id = existing?.id ?: "known-${server.id}",
            host = server.host,
            port = server.port,
            algorithm = algorithm,
            fingerprint = fingerprint,
            trusted = false,
            firstSeenEpochMillis = existing?.firstSeenEpochMillis ?: nowEpochMillis,
            lastSeenEpochMillis = nowEpochMillis,
            trustState = HostKeyTrustState.Unknown
        )
    }
}

object TerminalInputRouter {
    fun sequenceForAndroidKeyCode(keyCode: Int): String? {
        return when (keyCode) {
            111 -> "\u001B" // KEYCODE_ESCAPE
            61 -> "\t" // KEYCODE_TAB
            66 -> "\r" // KEYCODE_ENTER
            67 -> "\u007F" // KEYCODE_DEL
            112 -> "\u001B[3~" // KEYCODE_FORWARD_DEL
            19 -> "\u001B[A" // KEYCODE_DPAD_UP
            20 -> "\u001B[B" // KEYCODE_DPAD_DOWN
            22 -> "\u001B[C" // KEYCODE_DPAD_RIGHT
            21 -> "\u001B[D" // KEYCODE_DPAD_LEFT
            122 -> "\u001B[H" // KEYCODE_MOVE_HOME
            123 -> "\u001B[F" // KEYCODE_MOVE_END
            92 -> "\u001B[5~" // KEYCODE_PAGE_UP
            93 -> "\u001B[6~" // KEYCODE_PAGE_DOWN
            131 -> "\u001BOP" // KEYCODE_F1
            132 -> "\u001BOQ" // KEYCODE_F2
            133 -> "\u001BOR" // KEYCODE_F3
            134 -> "\u001BOS" // KEYCODE_F4
            135 -> "\u001B[15~" // KEYCODE_F5
            136 -> "\u001B[17~" // KEYCODE_F6
            137 -> "\u001B[18~" // KEYCODE_F7
            138 -> "\u001B[19~" // KEYCODE_F8
            139 -> "\u001B[20~" // KEYCODE_F9
            140 -> "\u001B[21~" // KEYCODE_F10
            141 -> "\u001B[23~" // KEYCODE_F11
            142 -> "\u001B[24~" // KEYCODE_F12
            else -> null
        }
    }

    fun sequenceForAndroidKeyEvent(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): String? {
        val base = sequenceForAndroidKeyCode(keyCode) ?: return null
        return TerminalModifierRouter.apply(base, ctrl, alt, shift).output
    }

    fun printableForAndroidKeyEvent(unicodeChar: Int, ctrl: Boolean, alt: Boolean, shift: Boolean): String? {
        if (unicodeChar <= 0) return null
        val char = unicodeChar.toChar()
        if (char.isISOControl()) return null
        return TerminalModifierRouter.apply(char.toString(), ctrl, alt, shift).output
    }

    fun sequenceFor(key: TerminalKey): String {
        sequenceForComboLabel(key.label)?.let { return it }
        return when (key.label.trim().lowercase()) {
            "esc" -> "\u001B"
            "tab" -> "\t"
            "enter" -> "\r"
            "bksp", "backspace" -> "\u007F"
            "del", "delete" -> "\u001B[3~"
            "ins", "insert" -> "\u001B[2~"
            "up", "arrowup", "arrow up", "↑" -> "\u001B[A"
            "down", "arrowdown", "arrow down", "↓" -> "\u001B[B"
            "right", "arrowright", "arrow right", "→" -> "\u001B[C"
            "left", "arrowleft", "arrow left", "←" -> "\u001B[D"
            "home" -> "\u001B[H"
            "end" -> "\u001B[F"
            "pgup", "pageup", "page up" -> "\u001B[5~"
            "pgdn", "pagedown", "page down" -> "\u001B[6~"
            "f1" -> "\u001BOP"
            "f2" -> "\u001BOQ"
            "f3" -> "\u001BOR"
            "f4" -> "\u001BOS"
            "f5" -> "\u001B[15~"
            "f6" -> "\u001B[17~"
            "f7" -> "\u001B[18~"
            "f8" -> "\u001B[19~"
            "f9" -> "\u001B[20~"
            "f10" -> "\u001B[21~"
            "f11" -> "\u001B[23~"
            "f12" -> "\u001B[24~"
            "ctrl" -> "<ctrl>"
            "alt" -> "<alt>"
            "altgr" -> "<altgr>"
            "shift" -> "<shift>"
            else -> key.sequence.ifBlank { key.label.takeIf { it.isSinglePrintableTerminalKey() }.orEmpty() }
        }
    }

    private fun sequenceForComboLabel(label: String): String? {
        val parts = label.split('-').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val keyLabel = parts.last()
        val modifiers = parts.dropLast(1).map { it.lowercase() }.toSet()
        if (modifiers.any { it !in setOf("ctrl", "alt", "altgr", "shift") }) return null
        val base = sequenceFor(TerminalKey(keyLabel, "")).takeIf { it.isNotBlank() && it != "<ctrl>" && it != "<alt>" && it != "<shift>" }
            ?: keyLabel.takeIf { it.length == 1 }?.lowercase()
            ?: return null
        return TerminalModifierRouter.apply(
            input = base,
            ctrl = "ctrl" in modifiers,
            alt = "alt" in modifiers || "altgr" in modifiers,
            shift = "shift" in modifiers
        ).output
    }

    private fun String.isSinglePrintableTerminalKey(): Boolean {
        return length == 1 && this[0].code in 0x20..0x7e
    }

    fun paste(text: String, bracketed: Boolean): String {
        return if (bracketed) "\u001B[200~$text\u001B[201~" else text
    }

    fun ctrl(letter: Char): String {
        val normalized = letter.uppercaseChar()
        return when (normalized) {
            in 'A'..'Z' -> ((normalized.code - 'A'.code) + 1).toChar().toString()
            '@' -> "\u0000"
            '[' -> "\u001B"
            '\\' -> "\u001C"
            ']' -> "\u001D"
            '^' -> "\u001E"
            '_' -> "\u001F"
            '?' -> "\u007F"
            else -> throw IllegalArgumentException("Ctrl key must be A-Z, @, [, \\, ], ^, _, or ?.")
        }
    }
}

object TerminalAccessoryKeyPolicy {
    const val DefaultCsv = "Esc,Tab,Ctrl,Alt,/,|,~,Up,Down"
    private val allowedLabels = listOf(
        "Esc", "Tab", "Ctrl", "Alt", "AltGr", "Shift", "Enter", "Bksp", "Ins", "Del",
        "Up", "Down", "Left", "Right", "Home", "End", "PgUp", "PgDn",
        "F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12",
        "/", "\\", "-", "_", "|", "~", ".", ":", ";", "(", ")", "[", "]", "{", "}", "<", ">"
    )
    private val labelByKey = allowedLabels.associateBy { it.lowercase() }

    fun normalizeCsv(value: String?): String {
        return labels(value).joinToString(",")
    }

    fun labels(value: String?): List<String> {
        val parsed = value.orEmpty()
            .split(',', '\n')
            .mapNotNull { token -> labelForToken(token.trim()) }
            .distinct()
            .take(16)
        return parsed.ifEmpty { labels(DefaultCsv) }
    }

    fun keys(value: String?): List<TerminalKey> {
        return labels(value).map { label -> TerminalKey(label, TerminalInputRouter.sequenceFor(TerminalKey(label, ""))) }
    }

    private fun labelForToken(token: String): String? {
        labelByKey[token.lowercase()]?.let { return it }
        val parts = token.split('-').map { it.trim() }.filter { it.isNotBlank() }
        if (parts.size < 2) return null
        val key = labelByKey[parts.last().lowercase()] ?: parts.last().takeIf { it.length == 1 }?.uppercase()
        val modifiers = parts.dropLast(1).map { it.lowercase() }
        if (key == null || modifiers.any { it !in setOf("ctrl", "alt", "altgr", "shift") }) return null
        val canonical = modifiers.distinct().map {
            when (it) {
                "ctrl" -> "Ctrl"
                "alt" -> "Alt"
                "altgr" -> "AltGr"
                else -> "Shift"
            }
        } + key
        return canonical.joinToString("-").takeIf { TerminalInputRouter.sequenceFor(TerminalKey(it, "")).isNotBlank() }
    }
}

object TerminalClipboardPolicy {
    const val MaxPasteChars = 32_768

    fun copyText(text: String?): String? = text?.takeIf { it.isNotEmpty() }

    fun pasteInput(text: String?, bracketed: Boolean): String? {
        val clean = text.orEmpty()
            .take(MaxPasteChars)
            .stripBracketedPasteDelimiters()
            .normalizeLineEndings()
            .stripUnsafeControls()
        if (clean.isEmpty()) return null
        return TerminalInputRouter.paste(clean, bracketed)
    }

    fun latestOutputBlock(transcript: String?, maxChars: Int = 4_000): String? {
        val lines = transcript.orEmpty()
            .lineSequence()
            .map { it.trimEnd() }
            .dropWhile { it.isBlank() }
            .toList()
            .asReversed()
            .dropWhile { it.isBlank() || it.isPromptLike() }
            .takeWhile { it.isNotBlank() && !it.isPromptLike() }
            .asReversed()
        return lines.joinToString("\n").trim().takeLast(maxChars).ifBlank { null }
    }

    private fun String.isPromptLike(): Boolean {
        val clean = trim()
        return clean.length <= 160 && Regex("([\\w.@:/~\\-]+\\s*)?[#$>]\\s*.*").matches(clean)
    }

    private fun String.stripBracketedPasteDelimiters(): String {
        return replace("\u001B[200~", "").replace("\u001B[201~", "")
    }

    private fun String.normalizeLineEndings(): String {
        return replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun String.stripUnsafeControls(): String {
        return filter { ch ->
            ch == '\n' || ch == '\r' || ch == '\t' || !ch.isISOControl()
        }
    }
}

enum class TerminalBackAction {
    DismissSnippet,
    CloseTransientMenus,
    HideKeyboard,
    NavigateBack
}

object TerminalBackPolicy {
    fun action(
        hasPendingSnippet: Boolean,
        snippetsOpen: Boolean,
        moreKeysOpen: Boolean,
        sessionMenuOpen: Boolean,
        keyboardOpen: Boolean,
        imeVisible: Boolean,
        terminalRequestedKeyboard: Boolean
    ): TerminalBackAction {
        return when {
            hasPendingSnippet -> TerminalBackAction.DismissSnippet
            snippetsOpen || moreKeysOpen || sessionMenuOpen -> TerminalBackAction.CloseTransientMenus
            keyboardOpen || imeVisible || terminalRequestedKeyboard -> TerminalBackAction.HideKeyboard
            else -> TerminalBackAction.NavigateBack
        }
    }
}

data class TerminalImeEdit(
    val output: String,
    val composingText: String
)

data class TerminalModifiedInput(
    val output: String,
    val consumeCtrl: Boolean,
    val consumeAlt: Boolean,
    val consumeShift: Boolean
)

object TerminalModifierRouter {
    fun apply(
        input: String,
        ctrl: Boolean,
        alt: Boolean,
        shift: Boolean
    ): TerminalModifiedInput {
        if (input.isEmpty()) {
            return TerminalModifiedInput("", consumeCtrl = false, consumeAlt = false, consumeShift = false)
        }
        modifiedControlSequence(input, ctrl, alt, shift)?.let { sequence ->
            return TerminalModifiedInput(
                output = sequence,
                consumeCtrl = ctrl,
                consumeAlt = alt,
                consumeShift = shift
            )
        }
        var output = input
        var consumeCtrl = false
        var consumeAlt = false
        var consumeShift = false
        if (shift && input.length == 1) {
            output = input.uppercase()
            consumeShift = true
        }
        if (ctrl && output == "/") {
            output = "\u001F"
            consumeCtrl = true
            consumeShift = consumeShift || shift
        } else if (ctrl && output.length == 1 && output[0] in "@[\\]^_") {
            output = (output[0].code and 0x1F).toChar().toString()
            consumeCtrl = true
            consumeShift = consumeShift || shift
        }
        if (ctrl && output.length == 1 && output[0].isCtrlChar()) {
            output = TerminalInputRouter.ctrl(output[0])
            consumeCtrl = true
            consumeShift = consumeShift || shift
        }
        if (alt) {
            output = "\u001B$output"
            consumeAlt = true
        }
        return TerminalModifiedInput(output, consumeCtrl, consumeAlt, consumeShift)
    }

    private fun modifiedControlSequence(input: String, ctrl: Boolean, alt: Boolean, shift: Boolean): String? {
        if (!ctrl && !alt && !shift) return null
        val modifier = 1 +
            (if (shift) 1 else 0) +
            (if (alt) 2 else 0) +
            (if (ctrl) 4 else 0)
        return when (input) {
            "\u001B[A" -> "\u001B[1;${modifier}A"
            "\u001B[B" -> "\u001B[1;${modifier}B"
            "\u001B[C" -> "\u001B[1;${modifier}C"
            "\u001B[D" -> "\u001B[1;${modifier}D"
            "\u001B[H" -> "\u001B[1;${modifier}H"
            "\u001B[F" -> "\u001B[1;${modifier}F"
            "\u001B[5~" -> "\u001B[5;${modifier}~"
            "\u001B[6~" -> "\u001B[6;${modifier}~"
            "\u001BOP" -> "\u001B[1;${modifier}P"
            "\u001BOQ" -> "\u001B[1;${modifier}Q"
            "\u001BOR" -> "\u001B[1;${modifier}R"
            "\u001BOS" -> "\u001B[1;${modifier}S"
            "\u001B[15~" -> "\u001B[15;${modifier}~"
            "\u001B[17~" -> "\u001B[17;${modifier}~"
            "\u001B[18~" -> "\u001B[18;${modifier}~"
            "\u001B[19~" -> "\u001B[19;${modifier}~"
            "\u001B[20~" -> "\u001B[20;${modifier}~"
            "\u001B[21~" -> "\u001B[21;${modifier}~"
            "\u001B[23~" -> "\u001B[23;${modifier}~"
            "\u001B[24~" -> "\u001B[24;${modifier}~"
            else -> null
        }
    }

    private fun Char.isCtrlChar(): Boolean = uppercaseChar() in 'A'..'Z' || this in "@[\\]^_?"
}

object TerminalImeInputReducer {
    private const val MaxAutomaticImeCommitChars = 256

    fun setComposing(previous: String, next: String): TerminalImeEdit {
        if (next.isUnsafeAutomaticImePayload()) return TerminalImeEdit("", previous)
        if (next.isEmpty()) return TerminalImeEdit("", "")
        val output = if (next.startsWith(previous)) {
            next.removePrefix(previous)
        } else {
            "\u007F".repeat(previous.length) + next
        }
        return TerminalImeEdit(output, next)
    }

    fun commit(previous: String, text: String): TerminalImeEdit {
        if (text.isUnsafeAutomaticImePayload()) return TerminalImeEdit("", "")
        if (text.isEmpty()) return TerminalImeEdit("", "")
        if (previous.isEmpty()) return TerminalImeEdit(text, "")
        val output = when {
            text == previous -> ""
            text.startsWith(previous) -> text.removePrefix(previous)
            else -> "\u007F".repeat(previous.length) + text
        }
        return TerminalImeEdit(output, "")
    }

    private fun String.isUnsafeAutomaticImePayload(): Boolean {
        return length > MaxAutomaticImeCommitChars ||
            any { it.isISOControl() }
    }
}

object SftpPathResolver {
    private const val LeafNameMaxChars = 160

    fun normalize(path: String): String {
        val trimmed = path.trim()
            .replace('\\', '/')
            .replace(Regex("/{2,}"), "/")
        return when {
            trimmed.isBlank() -> "."
            trimmed == "~" -> "~"
            trimmed.startsWith("~/") -> normalizeSegments(trimmed.removePrefix("~/"), "~")
            trimmed == "/" -> "/"
            trimmed.startsWith("/") -> normalizeSegments(trimmed.removePrefix("/"), "/")
            trimmed == "." -> "."
            else -> normalizeSegments(trimmed, "")
        }
    }

    private fun normalizeSegments(path: String, prefix: String): String {
        val segments = ArrayDeque<String>()
        path.split('/')
            .filter { it.isNotBlank() && it != "." }
            .forEach { segment ->
                when (segment) {
                    ".." -> if (segments.isNotEmpty()) segments.removeLast()
                    else -> segments.addLast(segment)
                }
            }
        val joined = segments.joinToString("/")
        return when (prefix) {
            "/" -> if (joined.isBlank()) "/" else "/$joined"
            "~" -> if (joined.isBlank()) "~" else "~/$joined"
            else -> joined.ifBlank { "." }
        }
    }

    fun join(directory: String, name: String): String {
        val cleanName = leafName(name)
        val cleanDirectory = normalize(directory)
        return when {
            cleanDirectory == "/" -> "/$cleanName"
            cleanDirectory == "." -> cleanName
            cleanDirectory == "~" -> "~/$cleanName"
            else -> "${cleanDirectory.trimEnd('/')}/$cleanName"
        }
    }

    fun leafName(name: String): String {
        return name
            .trim()
            .replace('\\', '/')
            .trim('/')
            .substringAfterLast('/')
            .map { if (it.isISOControl()) '_' else it }
            .joinToString("")
            .take(LeafNameMaxChars)
            .takeUnless { it == "." || it == ".." }
            ?.ifBlank { null }
            ?: "untitled"
    }

    fun parent(path: String): String {
        val clean = normalize(path).trimEnd('/')
        if (clean == "/" || clean == "." || clean == "~") return clean
        val parent = clean.substringBeforeLast('/', "")
        return when {
            parent.isBlank() && clean.startsWith("/") -> "/"
            parent.isBlank() -> "."
            parent == "~" -> "~"
            else -> parent
        }
    }

    fun breadcrumbSegments(path: String): List<Pair<String, String>> {
        val clean = normalize(path)
        if (clean == ".") return listOf("." to ".")
        if (clean == "/") return listOf("/" to "/")
        if (clean == "~") return listOf("~" to "~")

        val root = when {
            clean.startsWith("~/") -> "~"
            clean.startsWith("/") -> "/"
            else -> "."
        }
        val body = when (root) {
            "~" -> clean.removePrefix("~/")
            "/" -> clean.removePrefix("/")
            else -> clean
        }
        val result = mutableListOf(root to root)
        var cursor = root
        body.split('/')
            .filter { it.isNotBlank() }
            .forEach { segment ->
                cursor = join(cursor, segment)
                result += segment to cursor
            }
        return result
    }

    fun defaultStartPath(server: ServerProfile, bookmarks: List<SftpBookmark>): String {
        return bookmarks.firstOrNull { it.serverId == server.id }?.path
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalize)
            ?: "."
    }

    fun listCandidates(
        server: ServerProfile,
        requestedPath: String,
        bookmarks: List<SftpBookmark>
    ): List<String> {
        val userHome = when {
            server.username.equals("root", ignoreCase = true) -> "/root"
            server.username.isNotBlank() -> "/home/${server.username}"
            else -> ""
        }
        return listOf(
            requestedPath.ifBlank { "." },
            defaultStartPath(server, bookmarks),
            ".",
            "~",
            userHome,
            "/"
        )
            .map(::normalize)
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun listCandidatesForNavigation(
        server: ServerProfile,
        requestedPath: String,
        bookmarks: List<SftpBookmark>,
        allowFallback: Boolean
    ): List<String> {
        val normalized = normalize(requestedPath.ifBlank { defaultStartPath(server, bookmarks) })
        return if (allowFallback) {
            listCandidates(server, normalized, bookmarks)
        } else {
            listOf(normalized)
        }
    }

    fun navigationPlan(
        server: ServerProfile,
        requestedPath: String,
        bookmarks: List<SftpBookmark>,
        allowFallback: Boolean,
        hasLoadedPath: Boolean
    ): List<String> {
        val normalized = normalize(requestedPath.ifBlank { defaultStartPath(server, bookmarks) })
        val shouldUseFallback = allowFallback && !hasLoadedPath
        return if (shouldUseFallback) {
            listCandidates(server, normalized, bookmarks)
        } else {
            listOf(normalized)
        }
    }

    fun sortForFileManager(
        entries: List<SftpEntry>,
        mode: SftpSortMode = SftpSortMode.Name,
        descending: Boolean = false
    ): List<SftpEntry> {
        val nameComparator = compareBy<SftpEntry> { it.name.lowercase() }
        val valueComparator = when (mode) {
            SftpSortMode.Name -> if (descending) nameComparator.reversed() else nameComparator
            SftpSortMode.Modified -> if (descending) {
                compareByDescending<SftpEntry> { it.modifiedEpochMillis }.then(nameComparator)
            } else {
                compareBy<SftpEntry> { it.modifiedEpochMillis }.then(nameComparator)
            }
            SftpSortMode.Size -> if (descending) {
                compareByDescending<SftpEntry> { it.sizeBytes }.then(nameComparator)
            } else {
                compareBy<SftpEntry> { it.sizeBytes }.then(nameComparator)
            }
        }
        return entries.sortedWith(compareByDescending<SftpEntry> { it.directory }.then(valueComparator))
    }

    fun visibleForFileManager(entries: List<SftpEntry>, showHidden: Boolean): List<SftpEntry> {
        if (showHidden) return entries
        return entries.filterNot { entry ->
            entry.name.startsWith(".") && entry.name != "." && entry.name != ".."
        }
    }

    fun filterForFileManager(entries: List<SftpEntry>, query: String): List<SftpEntry> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return entries
        return entries.filter { entry ->
            entry.name.lowercase().contains(needle) || entry.path.lowercase().contains(needle)
        }
    }

    fun uploadTargetDirectory(currentPath: String, entry: SftpEntry?): String {
        if (entry == null) return normalize(currentPath)
        return if (entry.directory) {
            normalize(entry.path)
        } else {
            val parent = parent(entry.path)
            if (parent == ".") normalize(currentPath) else parent
        }
    }
}

enum class SftpSortMode {
    Name,
    Modified,
    Size
}

object SftpTextFilePolicy {
    const val MaxEditableBytes: Long = 256L * 1024L
    private const val BinaryProbeBytes = 4096
    private val TextExtensions = setOf(
        "bash",
        "bashrc",
        "conf",
        "config",
        "css",
        "csv",
        "env",
        "gitignore",
        "go",
        "gradle",
        "ini",
        "java",
        "js",
        "json",
        "kt",
        "kts",
        "log",
        "md",
        "profile",
        "properties",
        "py",
        "rb",
        "rs",
        "service",
        "sh",
        "sql",
        "toml",
        "txt",
        "xml",
        "yaml",
        "yml",
        "zshrc"
    )
    private val TextFileNames = setOf("dockerfile", "makefile", "readme", "license")

    fun canEdit(entry: SftpEntry): Boolean {
        return rejectionReason(entry) == null
    }

    fun decodeEditableText(bytes: ByteArray): Result<String> {
        contentRejectionReason(bytes)?.let { rejection ->
            return Result.failure(IllegalArgumentException(rejection))
        }
        return runCatching {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(bytes))
                .toString()
        }.fold(
            onSuccess = { Result.success(it) },
            onFailure = { Result.failure(IllegalArgumentException("This file is not valid UTF-8; download it instead.")) }
        )
    }

    fun contentRejectionReason(bytes: ByteArray): String? {
        if (bytes.size.toLong() > MaxEditableBytes) return "Text editor supports files up to 256 K."
        if (looksBinary(bytes)) return "This file appears to be binary; download it instead."
        return null
    }

    fun rejectionReason(entry: SftpEntry): String? {
        if (entry.directory) return "Folders cannot be opened as text."
        if (entry.sizeBytes > MaxEditableBytes) return "Text editor supports files up to 256 K."
        if (!hasTextExtension(entry.name)) return "Download this file; its extension is not treated as text."
        return null
    }

    fun hasTextExtension(name: String): Boolean {
        val clean = name.substringAfterLast('/').trim().lowercase()
        if (clean in TextFileNames) return true
        val extension = clean.substringAfterLast('.', missingDelimiterValue = "")
        return extension in TextExtensions
    }

    private fun looksBinary(bytes: ByteArray): Boolean {
        return bytes.take(BinaryProbeBytes).any { it == 0.toByte() }
    }
}

object SftpFileNamePolicy {
    fun normalizeEditableName(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.any { it == '/' || it == '\\' }) return null
        val cleaned = trimmed
            .map { if (it.isISOControl()) '_' else it }
            .joinToString("")
            .take(160)
            .trim()
        return cleaned.takeUnless { it.isBlank() || it == "." || it == ".." }
    }

    fun errorMessage(action: String): String {
        return "$action name must be a single file or folder name without slashes."
    }
}

object SftpPermissionModePolicy {
    fun parseOctalMode(text: String): Int? {
        val clean = text.trim()
        if (clean.length !in 3..4 || clean.any { it !in '0'..'7' }) return null
        return clean.toInt(8)
    }

    fun displayMode(mode: Int): String = mode.toString(8).padStart(3, '0')

    fun editableMode(permissions: String?): String {
        val clean = permissions.orEmpty().trim()
        return clean.takeIf { parseOctalMode(it) != null }.orEmpty()
    }

    fun editableModeOrDefault(permissions: String?, directory: Boolean): String {
        return editableMode(permissions).ifBlank { if (directory) "755" else "644" }
    }
}

object SftpErrorMapper {
    fun message(action: String, path: String, error: Throwable): String {
        val detail = error.message ?: error::class.java.simpleName
        val lower = detail.lowercase()
        val cleanAction = actionPhrase(action)
        val reason = when {
            lower.contains("permission denied") || lower.contains("access denied") ->
                "Permission denied. Check ownership, mode bits, and whether this SSH account can access the target."
            (lower.contains("no such file") || lower.contains("enoent")) && lower.contains("ssh-keys") ->
                "The selected private key was saved as a temporary file path instead of key material. Re-import the key into Vault."
            lower.contains("no such file") || lower.contains("not found") || lower.contains("does not exist") ->
                "Path not found. Refresh the folder or verify the remote path still exists."
            lower.contains("failure") && action.equals("list", ignoreCase = true) ->
                "The server could not list this folder. It may be a restricted directory or the SFTP subsystem returned a generic failure."
            lower.contains("subsystem") || lower.contains("sftp") && lower.contains("not available") ->
                "SFTP subsystem is unavailable on this server."
            lower.contains("enter its passphrase") || lower.contains("no passphrase") ||
                (lower.contains("private key") && lower.contains("encrypted")) ->
                "Enter the private-key passphrase to open SFTP."
            lower.contains("private-key auth failed") || lower.contains("private key") && lower.contains("could not be loaded") ->
                "Private-key auth failed. Check the selected key, username, passphrase, authorized_keys, and the server's PubkeyAuthentication settings."
            lower.contains("exhausted") || lower.contains("auth") || lower.contains("credential") ->
                "Authentication failed. Check the selected identity, username, passphrase, authorized_keys, and the server's allowed methods."
            lower.contains("host key") || lower.contains("trust") ->
                "Host key approval is required before SFTP."
            lower.contains("closed") || lower.contains("disconnect") || lower.contains("connection reset") ->
                "SFTP connection closed. Reconnect and retry the operation."
            lower.contains("enetunreach") ||
                lower.contains("network is unreachable") ||
                lower.contains("no route") ||
                lower.contains("broken pipe") ||
                lower.contains("connection refused") ||
                lower.contains("timed out") ->
                "Network path failed. Check Wi-Fi/VPN, VM network mode, subnet/firewall rules, and whether this Android device can reach the host."
            else -> detail
        }
        return "Cannot $cleanAction '$path': $reason"
    }

    private fun actionPhrase(action: String): String {
        return when (action.lowercase().trim()) {
            "open", "refresh" -> "list"
            "upload", "uploading", "upload to" -> "upload to"
            "download", "downloading" -> "download"
            "rename" -> "rename"
            "delete" -> "delete"
            "new folder", "mkdir", "create folder" -> "create folder"
            "chmod" -> "change permissions on"
            else -> action.lowercase().trim().ifBlank { "access" }
        }
    }
}

object SftpDeletePolicy {
    fun shouldRetryAsDirectory(error: Throwable): Boolean {
        val lower = (error.message ?: error::class.java.simpleName).lowercase()
        return lower.contains("is a directory") ||
            lower.contains("cannot remove directory") ||
            (lower.contains("directory") && lower.contains("not regular"))
    }
}

object SftpAtomicUploadPolicy {
    fun tempPathFor(destination: String, token: String): String {
        val cleanDestination = SftpPathResolver.normalize(destination)
        val cleanToken = token.filter { it.isLetterOrDigit() }.take(16).ifBlank { "upload" }
        val fileName = SftpPathResolver.leafName(cleanDestination)
        val parent = SftpPathResolver.parent(cleanDestination)
        val tempName = ".${fileName}.chronossh-${cleanToken}.tmp"
        return SftpPathResolver.join(parent, tempName)
    }
}

object SftpUploadFallbackPolicy {
    fun shouldTryDirectUpload(error: Throwable): Boolean {
        val lower = (error.message ?: error::class.java.simpleName).lowercase()
        return lower.contains("rename") ||
            lower.contains("failure") ||
            lower.contains("file already exists") ||
            lower.contains("cannot overwrite") ||
            lower.contains("operation unsupported") ||
            lower.contains("unsupported operation") ||
            lower.contains(".chronossh-")
    }
}

object SftpBrowserOperationPolicy {
    fun missingClientMessage(serverName: String, action: String): String {
        return when (action.lowercase()) {
            "uploading" -> "Reconnect SFTP to $serverName, then choose the upload file again."
            "downloading" -> "Reconnect SFTP to $serverName, then choose the download destination again."
            else -> "Reconnect SFTP to $serverName, then try ${action.lowercase()} again."
        }
    }

    fun refreshFailureStatus(successMessage: String, error: Throwable): String {
        return if (SftpClientHealth.shouldDropClient(error)) {
            "$successMessage. SFTP connection closed during refresh; reconnect to continue."
        } else {
            "$successMessage. Refresh failed: ${error.message ?: error::class.java.simpleName}"
        }
    }
}

object SftpHostTransferPolicy {
    fun canCopy(entry: SftpEntry): Boolean {
        return entry.type == SftpEntryType.File || entry.type == SftpEntryType.Directory || entry.directory
    }

    fun unsupportedChildNames(entries: List<SftpEntry>): List<String> {
        return entries
            .filterNot { it.name == "." || it.name == ".." || canCopy(it) }
            .map { it.name.ifBlank { it.path } }
    }

    fun targetPath(destinationDirectory: String, sourceEntry: SftpEntry): String {
        return SftpPathResolver.join(destinationDirectory, SftpPathResolver.leafName(sourceEntry.name.ifBlank { sourceEntry.path }))
    }

    fun destinationFolderExistsMessage(path: String): String {
        return "Destination folder already exists: $path"
    }

    fun unavailableReason(entry: SftpEntry): String? {
        return if (canCopy(entry)) null else "Host-to-host copy supports files and folders only."
    }
}

object SftpClientHealth {
    fun shouldDropClient(error: Throwable): Boolean {
        val lower = (error.message ?: error::class.java.simpleName).lowercase()
        return lower.contains("closed") ||
            lower.contains("disconnect") ||
            lower.contains("connection reset") ||
            lower.contains("reset by peer") ||
            lower.contains("broken pipe") ||
            lower.contains("eof") ||
            lower.contains("channel") ||
            lower.contains("socket") ||
            lower.contains("network") ||
            lower.contains("unreachable") ||
            lower.contains("no route") ||
            lower.contains("connection refused") ||
            lower.contains("timed out")
    }
}

object SshAuthFailureHints {
    fun privateKeyRejected(
        allowedMethods: String,
        keyInfo: KeyMaterialInfo,
        passphraseProvided: Boolean
    ): String {
        val methodHint = when {
            allowedMethods == "not reported" ->
                "The server did not report allowed methods after rejecting the key."
            allowedMethods.split(",").none { it.trim().equals("publickey", ignoreCase = true) } ->
                "The server did not advertise publickey auth for this account."
            keyInfo.encrypted && !passphraseProvided ->
                "The key is encrypted and no passphrase was provided. Enter its passphrase to connect."
            else ->
                "The server advertises publickey auth but rejected this key. Check username, authorized_keys, key passphrase, and server PubkeyAuthentication settings."
        }
        return methodHint
    }

    fun requiresPrivateKeyPassphrase(error: Throwable): Boolean {
        if (error !is SshFailure.Authentication) return false
        val lower = error.message.orEmpty().lowercase()
        return lower.contains("passphrase") &&
            (lower.contains("enter") || lower.contains("no passphrase") || lower.contains("encrypted"))
    }
}

object SshConnectionErrorClassifier {
    fun classify(message: String, detail: String, cause: Throwable? = null): SshFailure? {
        val combined = buildString {
            append(detail)
            var cursor = cause?.cause
            while (cursor != null) {
                append(' ')
                append(cursor.message ?: cursor::class.java.simpleName)
                cursor = cursor.cause
            }
        }
        val lower = combined.lowercase()
        return when {
            lower.contains("no matching") &&
                (lower.contains("key exchange") || lower.contains("kex") || lower.contains("cipher") || lower.contains("mac") || lower.contains("host key")) ->
                SshFailure.Unsupported(
                    "$message: SSH algorithm negotiation failed. The server and Android client could not agree on a compatible ${algorithmCategory(lower)}. Check the server's KexAlgorithms, Ciphers, MACs, and HostKeyAlgorithms; hosts that only allow curve25519 may need an additional Android-compatible KEX provider."
                )
            lower.contains("algorithm negotiation fail") || lower.contains("could not settle") ->
                SshFailure.Unsupported(
                    "$message: SSH algorithm negotiation failed. Check server/client KEX, cipher, MAC, and host-key algorithm compatibility."
                )
            lower.contains("curve25519") && (lower.contains("unsupported") || lower.contains("not available") || lower.contains("no such algorithm")) ->
                SshFailure.Unsupported(
                    "$message: this host appears to require curve25519 key exchange, which is not available in this Android-compatible SSHJ configuration."
                )
            else -> null
        }
    }

    private fun algorithmCategory(lower: String): String {
        return when {
            lower.contains("key exchange") || lower.contains("kex") -> "key-exchange algorithm"
            lower.contains("cipher") -> "cipher"
            lower.contains("mac") -> "MAC"
            lower.contains("host key") -> "host-key algorithm"
            else -> "algorithm"
        }
    }
}

object ScpTransferPolicy {
    fun normalizeRemotePath(path: String): String? {
        val trimmed = path.trim()
        if (trimmed.isBlank() || trimmed.endsWith("/")) return null
        return when (val normalized = SftpPathResolver.normalize(trimmed)) {
            ".", "~", "~/" -> null
            else -> normalized.replace(Regex("^~/(?=.)"), "./")
        }
    }

    fun safeDisplayName(name: String, remotePath: String): String {
        val raw = name.trim().ifBlank { remotePath }
        if (raw == "." || raw == "..") return "scp-transfer"
        val candidate = SftpPathResolver.leafName(raw)
        val safe = candidate.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
        return safe.takeUnless { it.isBlank() || it == "." || it == ".." } ?: "scp-transfer"
    }

    fun progress(doneBytes: Long, totalBytes: Long): Float {
        return if (totalBytes <= 0L) 0.05f else (doneBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
    }
}

object TransferStateReducer {
    fun reduce(current: TransferRecord?, incoming: TransferRecord): TransferRecord {
        if (current?.state != TransferRecordState.Cancelled) return incoming
        if (incoming.state == TransferRecordState.Cancelled) return incoming
        return current
    }
}

object TransferCompletionPolicy {
    fun isTerminal(state: TransferRecordState): Boolean {
        return state == TransferRecordState.Complete ||
            state == TransferRecordState.Failed ||
            state == TransferRecordState.Cancelled
    }
}

object TransferPersistencePolicy {
    private const val DisplayNameMaxChars = 160
    private const val MessageMaxChars = 300
    private val activeStates = setOf(TransferRecordState.Queued, TransferRecordState.Running)

    fun directionFromPersisted(raw: String): TransferDirection? {
        return runCatching { TransferDirection.valueOf(raw) }.getOrNull()
    }

    fun stateFromPersisted(raw: String): TransferRecordState? {
        return runCatching { TransferRecordState.valueOf(raw) }.getOrNull()
    }

    fun normalizePersisted(record: TransferRecord): TransferRecord? {
        val id = record.id.trim()
        val serverId = record.serverId.trim()
        val remotePath = record.remotePath.trim()
        if (id.isBlank() || serverId.isBlank() || remotePath.isBlank()) return null
        val displayName = cleanText(record.localDisplayName).ifBlank {
            remotePath.substringAfterLast('/').ifBlank { "transfer" }
        }.takeLast(DisplayNameMaxChars)
        return record.copy(
            id = id,
            serverId = serverId,
            remotePath = remotePath,
            localDisplayName = displayName,
            progress = record.progress.coerceIn(0f, 1f),
            message = cleanText(record.message).take(MessageMaxChars),
            updatedAtEpochMillis = record.updatedAtEpochMillis.coerceAtLeast(0L)
        )
    }

    fun normalizeLoaded(record: TransferRecord): TransferRecord {
        if (record.state !in activeStates) return record
        return record.copy(
            state = TransferRecordState.Failed,
            progress = record.progress.coerceIn(0f, 1f),
            message = "Interrupted before completion."
        )
    }

    private fun cleanText(value: String): String {
        return value.trim().map { if (it.isISOControl()) ' ' else it }.joinToString("")
    }
}

object ConnectionEventPersistencePolicy {
    private const val MessageMaxChars = 500

    fun levelFromPersisted(raw: String): ConnectionEventLevel? {
        return runCatching { ConnectionEventLevel.valueOf(raw) }.getOrNull()
    }

    fun normalizePersisted(event: ConnectionEvent): ConnectionEvent? {
        val id = event.id.trim()
        val serverId = event.serverId.trim()
        val message = event.message.trim()
        if (id.isBlank() || serverId.isBlank() || message.isBlank()) return null
        return event.copy(
            id = id,
            serverId = serverId,
            atEpochMillis = event.atEpochMillis.coerceAtLeast(0L),
            message = message.map { if (it.isISOControl()) ' ' else it }.joinToString("").take(MessageMaxChars)
        )
    }
}

object TransferCancellationRegistry {
    private val jobs = mutableMapOf<String, Job>()

    @Synchronized
    fun register(transferId: String, job: Job) {
        jobs[transferId]?.cancel()
        if (!job.isCompleted) jobs[transferId] = job
    }

    @Synchronized
    fun unregister(transferId: String) {
        jobs.remove(transferId)
    }

    @Synchronized
    fun unregisterIfTerminal(transferId: String, state: TransferRecordState) {
        if (TransferCompletionPolicy.isTerminal(state)) jobs.remove(transferId)
    }

    @Synchronized
    fun cancel(transferId: String): Boolean {
        val job = jobs[transferId] ?: return false
        if (job.isCompleted) {
            jobs.remove(transferId)
            return false
        }
        job.cancel()
        return true
    }
}

enum class CredentialPassphraseAction {
    KeepExisting,
    StoreNew,
    DeleteExisting,
    None
}

object CredentialPassphrasePolicy {
    fun resolve(
        isPrivateKey: Boolean,
        existingPassphraseRef: String?,
        passphrase: String,
        savePassphrase: Boolean
    ): CredentialPassphraseAction {
        if (!isPrivateKey) {
            return if (existingPassphraseRef?.startsWith("secret-") == true) {
                CredentialPassphraseAction.DeleteExisting
            } else {
                CredentialPassphraseAction.None
            }
        }
        return when {
            passphrase.isNotBlank() && savePassphrase -> CredentialPassphraseAction.StoreNew
            savePassphrase -> CredentialPassphraseAction.KeepExisting
            existingPassphraseRef?.startsWith("secret-") == true -> CredentialPassphraseAction.DeleteExisting
            else -> CredentialPassphraseAction.None
        }
    }
}

object BackupCredentialPolicy {
    const val IMPORT_REQUIRED_REF = "import-required"

    fun sanitizeImportedMetadata(credential: Credential): Credential? {
        return CredentialPersistencePolicy.normalizeLoaded(
            credential.copy(
                encryptedPayloadRef = IMPORT_REQUIRED_REF,
                passphraseRef = null,
                lastUsedEpochMillis = 0L
            )
        )
    }
}

object AppLockCrashRecoveryPolicy {
    fun renderMarkerExpired(armedAtEpochMillis: Long?, nowEpochMillis: Long): Boolean {
        val armedAt = armedAtEpochMillis ?: return false
        return armedAt > 0L && nowEpochMillis - armedAt >= 10_000L
    }

    fun shouldDisableAppLockAfterCrash(crashes: List<CrashLogEntry>, nowEpochMillis: Long): Boolean {
        val ordered = crashes.sortedByDescending { it.atEpochMillis }
        val latest = ordered.firstOrNull() ?: return false
        val recentWindowMillis = 10L * 60L * 1000L
        val recent = ordered.filter { nowEpochMillis - it.atEpochMillis <= recentWindowMillis }
        val lockNeedles = listOf(
            "applock",
            "app lock",
            "pinlock",
            "pinlockpolicy",
            "appLockPinHash".lowercase(),
            "appLockPinSalt".lowercase(),
            "pbkdf2withhmacsha256",
            "pbekeyspec",
            "illegal base64",
            "invalidkeyspecexception",
            "biometric",
            "fingerprint",
            "app-lock"
        )
        if (recent.any { crash ->
                val signature = "${crash.throwableClass}\n${crash.message}\n${crash.stackTrace}".lowercase()
                lockNeedles.any(signature::contains)
            }
        ) return true
        if (recent.any { crash ->
                val signature = "${crash.throwableClass}\n${crash.message}\n${crash.stackTrace}".lowercase()
                signature.contains("androidx.compose.runtime.intstack.peek2") &&
                    signature.contains("androidx.compose.runtime.composerimpl.end")
            }
        ) return true
        if (nowEpochMillis - latest.atEpochMillis > recentWindowMillis) return false
        return false
    }
}

object CredentialPersistencePolicy {
    private const val MaxNotesChars = 2_000

    fun normalizeLoaded(credential: Credential): Credential? {
        val id = credential.id.trim()
        val payloadRef = credential.encryptedPayloadRef.trim()
        if (id.isBlank() || payloadRef.isBlank()) return null
        val tags = credential.tags
            .flatMap { it.split(',') }
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(16)
        return credential.copy(
            id = id,
            label = credential.label.trim().ifBlank {
                when (credential.type) {
                    CredentialType.Password -> "Password identity"
                    CredentialType.PrivateKey -> "Private key identity"
                    CredentialType.HardwareKey -> "Hardware key identity"
                }
            },
            publicKeyPreview = credential.publicKeyPreview?.trim()?.ifBlank { null },
            encryptedPayloadRef = payloadRef,
            passphraseRef = credential.passphraseRef?.trim()?.takeIf { it.startsWith("secret-") },
            createdAtEpochMillis = credential.createdAtEpochMillis.coerceAtLeast(0L),
            lastUsedEpochMillis = credential.lastUsedEpochMillis.coerceAtLeast(0L),
            username = credential.username.trim().take(64),
            group = credential.group.trim().take(64),
            tags = tags,
            notes = credential.notes.trim().take(MaxNotesChars),
            importedAtEpochMillis = credential.importedAtEpochMillis.coerceAtLeast(0L)
        )
    }
}

object BackupKnownHostPolicy {
    fun sanitizeImportedMetadata(knownHost: KnownHost): KnownHost? {
        return KnownHostPersistencePolicy.normalizeLoaded(knownHost)
            ?.takeIf { HostKeyApprovalPolicy.canApproveStoredFingerprint(it) }
            ?.copy(trusted = false, trustState = HostKeyTrustState.Unknown)
    }
}

object KnownHostPersistencePolicy {
    private const val AlgorithmMaxChars = 64
    private const val FingerprintMaxChars = 160

    fun trustStateFromPersisted(raw: String?, trusted: Boolean): HostKeyTrustState {
        if (raw.isNullOrBlank()) return if (trusted) HostKeyTrustState.Trusted else HostKeyTrustState.Unknown
        return runCatching { HostKeyTrustState.valueOf(raw) }.getOrDefault(HostKeyTrustState.Unknown)
    }

    fun normalizeLoaded(knownHost: KnownHost): KnownHost? {
        val id = knownHost.id.trim()
        val host = knownHost.host.trim()
        val algorithm = knownHost.algorithm.trim()
        val fingerprint = knownHost.fingerprint.trim()
        if (id.isBlank() || algorithm.isBlank() || fingerprint.isBlank() || HostEndpointValidator.errorFor(host) != null) {
            return null
        }
        val safeAlgorithm = algorithm.take(AlgorithmMaxChars)
        val safeFingerprint = fingerprint.take(FingerprintMaxChars)
        val isTrusted = knownHost.trusted &&
            safeFingerprint.startsWith("SHA256:") &&
            knownHost.trustState == HostKeyTrustState.Trusted
        return knownHost.copy(
            id = id,
            host = host,
            algorithm = safeAlgorithm,
            fingerprint = safeFingerprint,
            trusted = isTrusted,
            trustState = when {
                isTrusted -> HostKeyTrustState.Trusted
                knownHost.trustState == HostKeyTrustState.Trusted -> HostKeyTrustState.Unknown
                else -> knownHost.trustState
            }
        )
    }
}

object SftpBookmarkPersistencePolicy {
    private const val LabelMaxChars = 120

    fun normalizeLoaded(bookmark: SftpBookmark): SftpBookmark? {
        val id = bookmark.id.trim()
        val serverId = bookmark.serverId.trim()
        if (id.isBlank() || serverId.isBlank()) return null
        val path = SftpPathResolver.normalize(bookmark.path)
        val label = bookmark.label.trim()
            .map { if (it.isISOControl()) ' ' else it }
            .joinToString("")
            .ifBlank {
            if (path == "/" || path == "~" || path == ".") path else SftpPathResolver.leafName(path)
            }
            .take(LabelMaxChars)
        return bookmark.copy(
            id = id,
            serverId = serverId,
            label = label,
            path = path,
            createdAtEpochMillis = bookmark.createdAtEpochMillis.coerceAtLeast(0L)
        )
    }
}

object BackupSftpBookmarkPolicy {
    fun sanitizeImportedMetadata(bookmark: SftpBookmark): SftpBookmark? {
        val serverId = bookmark.serverId.trim()
        if (serverId.isBlank()) return null
        val path = SftpPathResolver.normalize(bookmark.path)
        val id = bookmark.id.trim().ifBlank {
            val safeServer = serverId
                .map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
                .joinToString("")
                .trim('-')
                .ifBlank { "server" }
            "bookmark-$safeServer-${Integer.toHexString(path.hashCode())}"
        }
        return SftpBookmarkPersistencePolicy.normalizeLoaded(bookmark.copy(
            id = id,
            serverId = serverId,
            path = path
        ))
    }
}

object BackupForwardPolicy {
    fun sanitizeImportedMetadata(rule: PortForwardRule): PortForwardRule? {
        val validation = PortForwardValidator.validate(rule.copy(enabled = false, autoStart = false))
        return validation.normalized?.copy(enabled = false, autoStart = false)
    }
}

object BackupSnippetPolicy {
    fun sanitizeImportedMetadata(snippet: Snippet): Snippet? {
        val validation = SnippetValidator.validate(snippet)
        return validation.normalized
    }
}

object BackupServerPolicy {
    fun sanitizeImportedMetadata(server: ServerProfile): ServerProfile? {
        val id = server.id.trim()
        val host = server.host.trim()
        if (id.isBlank() || HostEndpointValidator.errorFor(host) != null) return null
        val tags = (listOf("All") + server.tags.map { it.trim() }.filter { it.isNotBlank() })
            .distinct()
        return server.copy(
            id = id,
            name = server.name.trim().ifBlank { host },
            host = host,
            port = server.port.coerceIn(1, 65535),
            username = server.username.trim().ifBlank { "root" },
            group = server.group.trim().ifBlank { "Ungrouped" },
            tags = tags,
            osName = server.osName.trim().ifBlank { "Linux" },
            osVersion = server.osVersion.trim().ifBlank { "Unknown" },
            credentialId = server.credentialId?.trim()?.takeIf { it.isNotBlank() },
            terminalProfileId = server.terminalProfileId.trim().ifBlank { "term-default" },
            monitoringConfig = server.monitoringConfig.copy(
                pollIntervalSeconds = ServerStatusRefreshPolicy.normalize(server.monitoringConfig.pollIntervalSeconds)
            ),
            startupCommand = server.startupCommand.takeIf(HostCommandSafety::isAutomaticCommandSafe).orEmpty(),
            startDirectory = server.startDirectory.trim(),
            proxyJumpHostId = server.proxyJumpHostId?.trim()?.takeIf { it.isNotBlank() }?.takeUnless { it == id },
            reconnectPolicy = server.reconnectPolicy.copy(
                keepAliveSeconds = server.reconnectPolicy.keepAliveSeconds.coerceIn(10, 120),
                maxAttempts = server.reconnectPolicy.maxAttempts.coerceIn(0, 10)
            ),
            connectTimeoutSeconds = server.connectTimeoutSeconds.coerceIn(3, 60),
            sshCompressionEnabled = server.sshCompressionEnabled
        )
    }
}

data class CredentialHostUnlinkResult(
    val servers: List<ServerProfile>,
    val unlinkedCount: Int
)

object CredentialHostLinkPolicy {
    fun unlink(servers: List<ServerProfile>, credentialId: String): CredentialHostUnlinkResult {
        var changed = 0
        val next = servers.map { server ->
            if (server.credentialId == credentialId) {
                changed += 1
                server.copy(credentialId = null)
            } else {
                server
            }
        }
        return CredentialHostUnlinkResult(next, changed)
    }
}

enum class VaultSecretAction {
    Export,
    Share,
    Copy
}

data class VaultSecretActionPolicy(
    val requiresConfirmation: Boolean,
    val fileName: String,
    val mimeType: String,
    val warning: String
)

object VaultSecretExportPolicy {
    private const val FileNameStemMaxChars = 80

    fun policyFor(credential: Credential, action: VaultSecretAction): VaultSecretActionPolicy {
        val safeName = credential.label
            .trim()
            .ifBlank { credential.type.name.lowercase() }
            .map { if (it.isISOControl()) '_' else it }
            .joinToString("")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .take(FileNameStemMaxChars)
            .trim('.', ' ')
            .ifBlank { credential.type.name.lowercase() }
        val isPrivateKey = credential.type == CredentialType.PrivateKey
        val isPassword = credential.type == CredentialType.Password
        val warning = when {
            isPrivateKey -> "This exposes the full private key outside ChronoSSH. Continue only on a trusted device or app."
            isPassword -> "This exposes the saved password outside ChronoSSH. Continue only on a trusted device or app."
            else -> "This exposes vault material outside ChronoSSH."
        }
        return VaultSecretActionPolicy(
            requiresConfirmation = action != VaultSecretAction.Copy && (isPrivateKey || isPassword),
            fileName = when {
                isPrivateKey -> "$safeName.key"
                else -> "$safeName.txt"
            },
            mimeType = if (isPrivateKey) "application/octet-stream" else "text/plain",
            warning = warning
        )
    }
}

object VaultPublicKeyPolicy {
    fun exportablePublicKey(preview: String?): String? {
        val clean = preview?.trim().orEmpty()
        val firstLine = clean.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        val parts = firstLine.split(Regex("\\s+"))
        val supported = parts.firstOrNull() in setOf("ssh-rsa", "ssh-ed25519") ||
            parts.firstOrNull()?.startsWith("ecdsa-sha2-") == true
        return firstLine.takeIf { supported && parts.size >= 2 && parts[1].length >= 16 }
    }
}

data class CredentialDraftValidation(
    val valid: Boolean,
    val message: String? = null
)

object HostUniquenessPolicy {
    const val DuplicateMessage = "A host with this login target already exists."

    fun hasDuplicateEndpoint(existing: List<ServerProfile>, draft: ServerProfile): Boolean {
        return existing.any { candidate ->
            candidate.id != draft.id &&
                candidate.host.normalizedHostKey() == draft.host.normalizedHostKey() &&
                candidate.port == draft.port &&
                candidate.username.trim().lowercase() == draft.username.trim().lowercase() &&
                candidate.protocol == draft.protocol
        }
    }

    private fun String.normalizedHostKey(): String = trim().trimEnd('.').lowercase()
}

object CredentialUniquenessPolicy {
    const val DuplicateLabelMessage = "An identity with this name already exists."

    fun hasDuplicateLabel(existing: List<Credential>, label: String, credentialId: String? = null): Boolean {
        val normalized = label.trim().lowercase()
        return normalized.isNotBlank() && existing.any { it.id != credentialId && it.label.trim().lowercase() == normalized }
    }
}

object CredentialDraftValidator {
    fun validate(
        existing: Credential?,
        selectedCredentialId: String?,
        type: CredentialType,
        secret: String,
        label: String
    ): CredentialDraftValidation {
        val cleanSecret = secret.trim()
        if (selectedCredentialId != null && existing == null) {
            return CredentialDraftValidation(false, "Selected identity was not found. Choose an identity again or enter a new one.")
        }
        if (existing != null && existing.type != type && cleanSecret.isBlank()) {
            return CredentialDraftValidation(
                false,
                "Enter and save a ${type.displayName().lowercase()} before changing this identity type."
            )
        }
        if (existing == null && cleanSecret.isBlank()) {
            return CredentialDraftValidation(false, "Choose a saved identity or enter a password/private key before saving this host.")
        }
        if (type == CredentialType.PrivateKey && cleanSecret.isNotBlank()) {
            val keyInfo = KeyMaterialInspector.inspectPrivateKey(secret)
            if (!keyInfo.valid) return CredentialDraftValidation(false, keyInfo.summary)
            if (keyInfo.encrypted) {
                return CredentialDraftValidation(
                    true,
                    "Encrypted key imported. Enter its passphrase before connecting, or save the passphrase intentionally."
                )
            }
        }
        if (type == CredentialType.PrivateKey && existing == null && label.isBlank()) {
            return CredentialDraftValidation(false, "Key name is required for a saved private-key identity.")
        }
        return CredentialDraftValidation(true)
    }

    private fun CredentialType.displayName(): String {
        return when (this) {
            CredentialType.Password -> "password"
            CredentialType.PrivateKey -> "private key"
            CredentialType.HardwareKey -> "hardware key"
        }
    }
}
