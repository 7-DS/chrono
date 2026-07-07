package com.chrono.ssh.core.service

data class HostOsInfo(
    val name: String,
    val version: String,
    val kernel: String
)

object HostOsDetector {
    private val idAliases = mapOf(
        "almalinux" to "AlmaLinux",
        "amazon" to "Amazon Linux",
        "amzn" to "Amazon Linux",
        "alpine" to "Alpine Linux",
        "anolis" to "Anolis OS",
        "arch" to "Arch Linux",
        "artix" to "Artix Linux",
        "armbian" to "Armbian",
        "clearlinux" to "Clear Linux",
        "centos" to "CentOS",
        "coreelec" to "CoreELEC",
        "debian" to "Debian",
        "devuan" to "Devuan",
        "dietpi" to "DietPi",
        "elementary" to "Elementary OS",
        "endeavouros" to "EndeavourOS",
        "fedora" to "Fedora",
        "freebsd" to "FreeBSD",
        "garuda" to "Garuda Linux",
        "gentoo" to "Gentoo",
        "istoreos" to "iStoreOS",
        "kali" to "Kali Linux",
        "kdeneon" to "KDE neon",
        "lede" to "OpenWrt",
        "linuxmint" to "Linux Mint",
        "manjaro" to "Manjaro",
        "mx" to "MX Linux",
        "netbsd" to "NetBSD",
        "nixos" to "NixOS",
        "ol" to "Oracle Linux",
        "openbsd" to "OpenBSD",
        "openeuler" to "openEuler",
        "openmediavault" to "OpenMediaVault",
        "opensuse" to "openSUSE",
        "opensuseleap" to "openSUSE Leap",
        "opensusetumbleweed" to "openSUSE Tumbleweed",
        "openwrt" to "OpenWrt",
        "parrot" to "Parrot OS",
        "pop" to "Pop!_OS",
        "proxmox" to "Proxmox VE",
        "raspbian" to "Raspberry Pi OS",
        "rpi" to "Raspberry Pi OS",
        "rhel" to "Red Hat Enterprise Linux",
        "rocky" to "Rocky Linux",
        "sles" to "SUSE Linux Enterprise Server",
        "slackware" to "Slackware",
        "synology" to "Synology DSM",
        "truenas" to "TrueNAS",
        "unraid" to "Unraid",
        "ubuntu" to "Ubuntu",
        "void" to "Void Linux"
    )

    fun parse(osRelease: String, uname: String): HostOsInfo? {
        val values = parseOsRelease(osRelease)
        val versionValue = values["VERSION_ID"]
            ?: values["DISTRIB_RELEASE"]
            ?: values["VERSION"]
            ?: values["BUILD_ID"]
            ?: values["DISTRIB_CODENAME"]
        val name = values["PRETTY_NAME"]
            ?: values["DISTRIB_DESCRIPTION"]
            ?: values["NAME"]?.withVersion(versionValue)
            ?: values["DISTRIB_ID"]?.withVersion(versionValue)
            ?: values["ID"]?.displayNameFromId()
            ?: fallbackNameFromUname(uname)
        val cleanName = name?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val version = versionValue
            ?: fallbackVersionFromUname(uname)
            ?: ""
        return HostOsInfo(
            name = cleanName,
            version = version.trim(),
            kernel = uname.trim()
        )
    }

    fun parseOsRelease(output: String): Map<String, String> {
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val splitAt = line.indexOf('=')
                if (splitAt <= 0) return@mapNotNull null
                line.take(splitAt) to decodeValue(line.drop(splitAt + 1))
            }
            .toMap()
    }

    private fun decodeValue(raw: String): String {
        val trimmed = raw.trim()
        val quoted = trimmed.length >= 2 &&
            ((trimmed.first() == '"' && trimmed.last() == '"') || (trimmed.first() == '\'' && trimmed.last() == '\''))
        val body = if (quoted) trimmed.substring(1, trimmed.length - 1) else trimmed
        return body
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun String.withVersion(version: String?): String {
        val cleanVersion = version?.trim()?.takeIf { it.isNotBlank() } ?: return this
        return if (contains(cleanVersion)) this else "$this $cleanVersion"
    }

    private fun String.displayNameFromId(): String {
        val normalized = lowercase().replace("_", "").replace("-", "")
        return idAliases[normalized] ?: replace('-', ' ').replaceFirstChar { it.uppercase() }
    }

    private fun fallbackNameFromUname(uname: String): String? {
        val clean = uname.trim()
        if (clean.isBlank()) return null
        val lower = clean.lowercase()
        return when {
            lower.startsWith("freebsd") -> "FreeBSD"
            lower.startsWith("openbsd") -> "OpenBSD"
            lower.startsWith("netbsd") -> "NetBSD"
            lower.startsWith("darwin") -> "macOS"
            lower.startsWith("linux") -> "Linux"
            lower.contains("routeros") -> "MikroTik RouterOS"
            else -> clean.substringBefore(' ').takeIf { it.isNotBlank() }
        }
    }

    private fun fallbackVersionFromUname(uname: String): String? {
        val clean = uname.trim()
        if (clean.startsWith("Linux", ignoreCase = true)) return clean.takeIf { it.isNotBlank() }
        val parts = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return clean.takeIf { it.isNotBlank() }
        return parts.drop(1).joinToString(" ")
    }
}
