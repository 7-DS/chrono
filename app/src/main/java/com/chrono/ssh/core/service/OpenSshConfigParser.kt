package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.PortForwardType

data class OpenSshConfigHost(
    val alias: String,
    val hostName: String,
    val user: String,
    val port: Int,
    val identityFile: String?,
    val proxyJumpAlias: String? = null,
    val forwards: List<OpenSshConfigForward> = emptyList()
)

data class OpenSshConfigForward(
    val type: PortForwardType,
    val localHost: String,
    val localPort: Int,
    val remoteHost: String,
    val remotePort: Int
)

object OpenSshConfigParser {
    private const val MaxConfigChars = 256 * 1024
    private const val MaxHosts = 128

    fun parse(text: String): List<OpenSshConfigHost> {
        if (text.length > MaxConfigChars) return emptyList()
        val hosts = mutableListOf<OpenSshConfigHost>()
        var currentHosts = emptyList<MutableHost>()
        var currentDefaults: MutableHost? = null
        val defaults = MutableHost(alias = "*")
        text.lineSequence().forEach { rawLine ->
            val tokens = shellTokens(rawLine)
            if (tokens.isEmpty()) return@forEach
            val keyword = tokens.first().lowercase()
            val values = tokens.drop(1)
            if (keyword == "host") {
                hosts += currentHosts.mapNotNull { it.toHost() }
                val concreteAliases = values
                    .filter { it.isConcreteAlias() }
                currentDefaults = if (concreteAliases.isEmpty() && values.any { it == "*" }) defaults else null
                currentHosts = concreteAliases.map { MutableHost.fromDefaults(it, defaults) }
                return@forEach
            }
            val targets = currentHosts.takeIf { it.isNotEmpty() } ?: currentDefaults?.let { listOf(it) } ?: return@forEach
            val value = values.joinToString(" ").takeIf { it.isNotBlank() } ?: return@forEach
            when (keyword) {
                "hostname" -> targets.forEach { it.hostName = value }
                "user" -> targets.forEach { it.user = value }
                "port" -> targets.forEach { it.port = value.toIntOrNull()?.takeIf { port -> port in 1..65535 } ?: 22 }
                "identityfile" -> targets.forEach { it.identityFile = value.take(256) }
                "proxyjump" -> targets.forEach { it.proxyJumpAlias = proxyJumpAlias(value) }
                "localforward" -> parseTcpForward(PortForwardType.Local, values)?.let { forward ->
                    targets.forEach { it.forwards += forward }
                }
                "remoteforward" -> parseTcpForward(PortForwardType.Remote, values)?.let { forward ->
                    targets.forEach { it.forwards += forward }
                }
                "dynamicforward" -> parseDynamicForward(values)?.let { forward ->
                    targets.forEach { it.forwards += forward }
                }
            }
        }
        hosts += currentHosts.mapNotNull { it.toHost() }
        return hosts.take(MaxHosts)
    }

    private data class MutableHost(
        val alias: String,
        var hostName: String? = null,
        var user: String = "root",
        var port: Int = 22,
        var identityFile: String? = null,
        var proxyJumpAlias: String? = null,
        var forwards: List<OpenSshConfigForward> = emptyList()
    ) {
        companion object {
            fun fromDefaults(alias: String, defaults: MutableHost): MutableHost {
                return MutableHost(
                    alias = alias,
                    hostName = null,
                    user = defaults.user,
                    port = defaults.port,
                    identityFile = defaults.identityFile,
                    proxyJumpAlias = defaults.proxyJumpAlias,
                    forwards = defaults.forwards
                )
            }
        }

        fun toHost(): OpenSshConfigHost? {
            val cleanHost = hostName?.trim()?.takeIf { HostEndpointValidator.errorFor(it) == null } ?: return null
            return OpenSshConfigHost(alias.trim(), cleanHost, user.ifBlank { "root" }, port, identityFile, proxyJumpAlias, forwards)
        }
    }

    private data class ListenSpec(val host: String, val port: Int)
    private data class TargetSpec(val host: String, val port: Int)

    private fun parseTcpForward(type: PortForwardType, values: List<String>): OpenSshConfigForward? {
        val listen = parseListenSpec(values.getOrNull(0) ?: return null) ?: return null
        val target = when (values.size) {
            2 -> parseTargetSpec(values[1])
            3 -> parseHostPort(values[1], values[2])
            else -> null
        } ?: return null
        return when (type) {
            PortForwardType.Local -> OpenSshConfigForward(type, listen.host, listen.port, target.host, target.port)
            PortForwardType.Remote -> OpenSshConfigForward(type, target.host, target.port, listen.host, listen.port)
            PortForwardType.DynamicSocks -> null
        }
    }

    private fun parseDynamicForward(values: List<String>): OpenSshConfigForward? {
        if (values.size != 1) return null
        val listen = parseListenSpec(values.first()) ?: return null
        return OpenSshConfigForward(PortForwardType.DynamicSocks, listen.host, listen.port, "", 0)
    }

    private fun parseListenSpec(value: String): ListenSpec? {
        val clean = value.trim()
        val host = clean.substringBeforeLast(':', missingDelimiterValue = "127.0.0.1")
        val portText = clean.substringAfterLast(':')
        val port = portText.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return ListenSpec(host.ifBlank { "127.0.0.1" }, port)
    }

    private fun parseTargetSpec(value: String): TargetSpec? {
        val clean = value.trim()
        if (!clean.contains(':')) return null
        return parseHostPort(clean.substringBeforeLast(':'), clean.substringAfterLast(':'))
    }

    private fun parseHostPort(host: String, portText: String): TargetSpec? {
        val cleanHost = host.trim().takeIf { it.isNotBlank() && HostEndpointValidator.errorFor(it) == null } ?: return null
        val port = portText.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        return TargetSpec(cleanHost, port)
    }

    private fun proxyJumpAlias(value: String): String? {
        val clean = value.trim().takeIf { it.isNotBlank() && !it.equals("none", ignoreCase = true) } ?: return null
        if (clean.contains(',') || clean.any { it.isISOControl() || it.isWhitespace() }) return null
        val withoutUser = clean.substringAfterLast('@')
        val alias = withoutUser.substringBefore(':').trim()
        return alias.takeIf { it.isConcreteAlias() }?.take(128)
    }

    private fun String.isConcreteAlias(): Boolean {
        return isNotBlank() && none { it == '*' || it == '?' || it.isISOControl() }
    }

    private fun shellTokens(line: String): List<String> {
        val out = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var escaped = false
        for (char in line.trim()) {
            when {
                escaped -> {
                    token.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                quote != null && char == quote -> quote = null
                quote != null -> token.append(char)
                char == '"' || char == '\'' -> quote = char
                char == '#' && token.isEmpty() -> break
                char.isWhitespace() -> {
                    if (token.isNotEmpty()) {
                        out += token.toString()
                        token.clear()
                    }
                }
                else -> token.append(char)
            }
        }
        if (token.isNotEmpty()) out += token.toString()
        return out
    }
}
