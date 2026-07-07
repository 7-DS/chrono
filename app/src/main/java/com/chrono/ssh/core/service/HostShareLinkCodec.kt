package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.MonitoringConfig
import com.chrono.ssh.core.model.PortForwardRule
import com.chrono.ssh.core.model.PortForwardType
import com.chrono.ssh.core.model.Credential
import com.chrono.ssh.core.model.CredentialType
import com.chrono.ssh.core.model.ServerAccent
import com.chrono.ssh.core.model.ServerProfile
import com.chrono.ssh.core.model.Snippet
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object HostShareLinkCodec {
    private const val Scheme = "chronossh"
    private const val HostAuthority = "host"
    private const val IdentityAuthority = "identity"
    private const val SnippetAuthority = "snippet"
    private const val TunnelAuthority = "tunnel"
    private const val MaxPayloadChars = 8_192
    private const val MaxQueryParams = 64
    private const val MaxKeyChars = 64
    private const val MaxValueChars = 2_048

    fun encode(server: ServerProfile): String {
        val params = buildList {
            add("name" to server.name)
            add("host" to server.host)
            add("port" to server.port.toString())
            add("user" to server.username)
            add("group" to server.group)
            server.tags.filterNot { it.equals("All", ignoreCase = true) }.forEach { add("tag" to it) }
            if (server.startupCommand.isNotBlank()) add("startup" to server.startupCommand)
            if (server.startDirectory.isNotBlank()) add("dir" to server.startDirectory)
            if (server.notes.isNotBlank()) add("notes" to server.notes)
            if (server.terminalProfileId.isNotBlank()) add("terminal" to server.terminalProfileId)
            if (server.connectTimeoutSeconds != 10) add("timeout" to server.connectTimeoutSeconds.coerceIn(3, 60).toString())
            if (server.sshCompressionEnabled) add("compression" to "true")
        }
        return "$Scheme://$HostAuthority?" + params.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    fun encode(snippet: Snippet): String {
        val clean = requireNotNull(shareableSnippet(snippet)) { "Snippet is not shareable." }
        val params = buildList {
            add("id" to clean.id)
            add("name" to clean.name)
            add("command" to clean.command)
            clean.description.takeIf { it.isNotBlank() }?.let { add("desc" to it) }
            clean.group.takeIf { it.isNotBlank() }?.let { add("group" to it) }
            if (clean.favorite) add("favorite" to "true")
            if (!clean.confirmBeforeRun) add("confirm" to "false")
            if (clean.autoRun) add("autoRun" to "true")
            clean.createdAtEpochMillis.takeIf { it > 0 }?.let { add("created" to it.toString()) }
            clean.updatedAtEpochMillis.takeIf { it > 0 }?.let { add("updated" to it.toString()) }
            clean.tags.forEach { add("tag" to it) }
            clean.serverScope?.takeIf { it.isNotBlank() }?.let { add("server" to it) }
            clean.variables.forEach { add("var" to it) }
        }
        return link(SnippetAuthority, params)
    }

    fun encode(credential: Credential): String {
        val clean = requireNotNull(BackupCredentialPolicy.sanitizeImportedMetadata(credential)) { "Identity is not shareable." }
        val params = buildList {
            add("id" to clean.id)
            add("label" to clean.label)
            add("type" to clean.type.name)
            clean.publicKeyPreview?.takeIf { it.isNotBlank() }?.let { add("preview" to it) }
            add("created" to clean.createdAtEpochMillis.toString())
            clean.username.takeIf { it.isNotBlank() }?.let { add("user" to it) }
            clean.group.takeIf { it.isNotBlank() }?.let { add("group" to it) }
            clean.tags.forEach { add("tag" to it) }
            clean.notes.takeIf { it.isNotBlank() }?.let { add("notes" to it) }
            if (clean.favorite) add("favorite" to "true")
            clean.importedAtEpochMillis.takeIf { it > 0 }?.let { add("imported" to it.toString()) }
        }
        return link(IdentityAuthority, params)
    }

    fun encode(rule: PortForwardRule): String {
        val clean = requireNotNull(BackupForwardPolicy.sanitizeImportedMetadata(rule)) { "Port forward is not shareable." }
        val params = buildList {
            add("id" to clean.id)
            add("server" to clean.serverId)
            add("type" to clean.type.name)
            add("localHost" to clean.localHost)
            add("localPort" to clean.localPort.toString())
            if (clean.label.isNotBlank()) add("label" to clean.label)
            if (clean.group.isNotBlank()) add("group" to clean.group)
            if (clean.favorite) add("favorite" to "true")
            if (clean.type != PortForwardType.DynamicSocks) {
                add("remoteHost" to clean.remoteHost)
                add("remotePort" to clean.remotePort.toString())
            }
        }
        return link(TunnelAuthority, params)
    }

    fun decode(payload: String): ServerProfile? {
        val params = paramsFor(payload, HostAuthority) ?: return null
        val host = params.first("host") ?: return null
        val username = params.first("user") ?: params.first("username") ?: "root"
        val profile = ServerProfile(
            id = "import-${Integer.toUnsignedString("$username@$host".hashCode(), 16)}",
            name = params.first("name").orEmpty(),
            host = host,
            port = params.first("port")?.toIntOrNull() ?: 22,
            username = username,
            group = params.first("group").orEmpty(),
            tags = params.all("tag"),
            osName = "",
            osVersion = "",
            accent = ServerAccent("cyan", 0xff00ffff),
            credentialId = null,
            terminalProfileId = params.first("terminal").orEmpty(),
            monitoringConfig = MonitoringConfig(enabled = true, pollIntervalSeconds = 2, useOptionalAgent = false),
            startupCommand = params.first("startup").orEmpty(),
            startDirectory = params.first("dir").orEmpty(),
            notes = params.first("notes").orEmpty(),
            connectTimeoutSeconds = params.first("timeout")?.toIntOrNull()?.coerceIn(3, 60) ?: 10,
            sshCompressionEnabled = params.first("compression")?.toBooleanStrictOrNull() ?: false
        )
        return BackupServerPolicy.sanitizeImportedMetadata(profile)?.copy(credentialId = null)
    }

    fun decodeSnippet(payload: String): Snippet? {
        val params = paramsFor(payload, SnippetAuthority) ?: return null
        val snippet = Snippet(
            id = params.first("id").orEmpty(),
            name = params.first("name").orEmpty(),
            command = params.first("command").orEmpty(),
            tags = params.all("tag"),
            serverScope = params.first("server"),
            variables = params.all("var"),
            description = params.first("desc").orEmpty(),
            group = params.first("group").orEmpty(),
            favorite = params.first("favorite")?.toBooleanStrictOrNull() ?: false,
            confirmBeforeRun = params.first("confirm")?.toBooleanStrictOrNull() ?: true,
            autoRun = params.first("autoRun")?.toBooleanStrictOrNull() ?: false,
            createdAtEpochMillis = params.first("created")?.toLongOrNull() ?: 0L,
            updatedAtEpochMillis = params.first("updated")?.toLongOrNull() ?: 0L
        )
        return shareableSnippet(snippet)
    }

    fun decodeCredential(payload: String): Credential? {
        val params = paramsFor(payload, IdentityAuthority) ?: return null
        val type = params.first("type")?.let { raw ->
            CredentialType.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
        } ?: return null
        val credential = Credential(
            id = params.first("id").orEmpty(),
            label = params.first("label").orEmpty(),
            type = type,
            publicKeyPreview = params.first("preview"),
            encryptedPayloadRef = BackupCredentialPolicy.IMPORT_REQUIRED_REF,
            createdAtEpochMillis = params.first("created")?.toLongOrNull() ?: 0L,
            username = params.first("user") ?: params.first("username").orEmpty(),
            group = params.first("group").orEmpty(),
            tags = params.all("tag"),
            notes = params.first("notes").orEmpty(),
            favorite = params.first("favorite")?.toBooleanStrictOrNull() ?: false,
            importedAtEpochMillis = params.first("imported")?.toLongOrNull() ?: 0L
        )
        return BackupCredentialPolicy.sanitizeImportedMetadata(credential)
    }

    fun decodePortForward(payload: String): PortForwardRule? {
        val params = paramsFor(payload, TunnelAuthority) ?: return null
        val type = params.first("type")?.let { raw ->
            PortForwardType.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
        } ?: return null
        val rule = PortForwardRule(
            id = params.first("id").orEmpty(),
            serverId = params.first("server").orEmpty(),
            type = type,
            localHost = params.first("localHost").orEmpty(),
            localPort = params.first("localPort")?.toIntOrNull() ?: 0,
            remoteHost = params.first("remoteHost").orEmpty(),
            remotePort = params.first("remotePort")?.toIntOrNull() ?: 0,
            enabled = false,
            autoStart = false,
            label = params.first("label").orEmpty(),
            group = params.first("group").orEmpty(),
            favorite = params.first("favorite")?.toBooleanStrictOrNull() ?: false
        )
        return BackupForwardPolicy.sanitizeImportedMetadata(rule)
    }

    private fun shareableSnippet(snippet: Snippet): Snippet? {
        return BackupSnippetPolicy.sanitizeImportedMetadata(
            snippet.copy(serverScope = snippet.serverScope?.trim()?.ifBlank { null })
        )
    }

    private fun link(authority: String, params: List<Pair<String, String>>): String {
        return "$Scheme://$authority?" + params.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
    }

    private fun paramsFor(payload: String, expectedAuthority: String): Map<String, List<String>>? {
        val clean = payload.trim()
        if (clean.length > MaxPayloadChars) return null
        if (!clean.startsWith("$Scheme://", ignoreCase = true)) return null
        val withoutScheme = clean.substringAfter("://", missingDelimiterValue = "")
        val authority = withoutScheme.substringBefore("?", missingDelimiterValue = "")
        if (!authority.equals(expectedAuthority, ignoreCase = true)) return null
        return parseQuery(withoutScheme.substringAfter("?", missingDelimiterValue = ""))
    }

    private fun parseQuery(query: String): Map<String, List<String>>? {
        if (query.isBlank()) return emptyMap()
        val tokens = query.split("&")
        if (tokens.size > MaxQueryParams) return null
        return tokens
            .mapNotNull { token ->
                val key = token.substringBefore("=", missingDelimiterValue = "").trim()
                if (key.isBlank()) return@mapNotNull null
                val decodedKey = urlDecode(key) ?: return null
                val decodedValue = urlDecode(token.substringAfter("=", missingDelimiterValue = "")) ?: return null
                if (decodedKey.length > MaxKeyChars || decodedValue.length > MaxValueChars) return null
                decodedKey to decodedValue
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun Map<String, List<String>>.first(key: String): String? = get(key)?.firstOrNull()?.trim()

    private fun Map<String, List<String>>.all(key: String): List<String> = get(key).orEmpty().map { it.trim() }.filter { it.isNotBlank() }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun urlDecode(value: String): String? = runCatching {
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }.getOrNull()
}
