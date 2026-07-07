package com.chrono.ssh.core.service

import net.schmizz.sshj.common.Buffer
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.Base64

object KeyMaterialInspector {
    const val MaxPrivateKeyChars = 64 * 1024
    private const val OPENSSH_HEADER = "-----BEGIN OPENSSH PRIVATE KEY-----"
    private const val OPENSSH_MAGIC = "openssh-key-v1\u0000"
    private val supportedHeaders = listOf(
        "-----BEGIN OPENSSH PRIVATE KEY-----",
        "-----BEGIN RSA PRIVATE KEY-----",
        "-----BEGIN EC PRIVATE KEY-----",
        "-----BEGIN DSA PRIVATE KEY-----",
        "-----BEGIN PRIVATE KEY-----",
        "-----BEGIN ENCRYPTED PRIVATE KEY-----"
    )

    fun inspectPrivateKey(input: String): KeyMaterialInfo {
        if (input.length > MaxPrivateKeyChars) {
            return KeyMaterialInfo(
                valid = false,
                encrypted = false,
                format = "unknown",
                fingerprint = "",
                summary = "Private key file is too large. Import a normal OpenSSH or PEM private key under 64 KB."
            )
        }
        val normalized = input.trim()
        val pathLike = looksLikePrivateKeyPath(normalized)
        val publicKeyOnly = looksLikePublicKeyOnly(normalized)
        val puttyPrivateKey = looksLikePuttyPrivateKey(normalized)
        val header = supportedHeaders.firstOrNull { normalized.startsWith(it) || normalized.contains("\n$it") }
        val securityKeyPrivate = normalized.contains("sk-ssh-ed25519@", ignoreCase = true) ||
            normalized.contains("sk-ecdsa-sha2-", ignoreCase = true) ||
            openSshContainsSecurityKeyType(normalized)
        val openSshCipher = if (header == OPENSSH_HEADER) openSshCipherName(normalized) else null
        val openSshEncrypted = header == OPENSSH_HEADER && openSshCipher?.let { it != "none" } == true
        val encrypted = normalized.contains("ENCRYPTED", ignoreCase = true) ||
            normalized.contains("Proc-Type: 4,ENCRYPTED", ignoreCase = true) ||
            openSshEncrypted
        val matchingEnd = header?.let { begin ->
            val keyType = begin.removePrefix("-----BEGIN ").removeSuffix("-----")
            normalized.contains("-----END $keyType-----")
        } == true
        val cleanEnvelope = header?.let { begin ->
            val keyType = begin.removePrefix("-----BEGIN ").removeSuffix("-----")
            normalized.startsWith(begin) && normalized.endsWith("-----END $keyType-----")
        } == true
        val base64Body = normalized.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("-----BEGIN ") || it.startsWith("-----END ") }
            .filterNot { it.contains(":") }
            .joinToString("")
        val base64BodyPresent = base64Body.length >= 32 &&
            base64Body.all { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }
        val openSshStructure = if (header == OPENSSH_HEADER) openSshStructureState(base64Body) else OpenSshStructureState.NotOpenSsh
        val openSshMagicValid = header != OPENSSH_HEADER || openSshStructure != OpenSshStructureState.BadMagic
        val openSshStructureValid = header != OPENSSH_HEADER ||
            openSshStructure == OpenSshStructureState.Valid ||
            (openSshEncrypted && openSshStructure == OpenSshStructureState.BadCheckInts)
        val valid = header != null && matchingEnd && cleanEnvelope && base64BodyPresent && openSshMagicValid && openSshStructureValid && !securityKeyPrivate
        val fingerprint = if (normalized.isBlank()) "" else sha256(normalized)
        return KeyMaterialInfo(
            valid = valid,
            encrypted = encrypted,
            format = header
                ?.removePrefix("-----BEGIN ")
                ?.removeSuffix("-----")
                ?.lowercase()
                ?.replace('_', ' ')
                ?: "unknown",
            fingerprint = fingerprint,
            summary = when {
                pathLike -> "Private-key import saved a file path instead of key contents. Re-import the key file from Edit Host or paste the full key text."
                publicKeyOnly -> "This is a public key, not a private key. Import the matching private key that starts with BEGIN ... PRIVATE KEY."
                puttyPrivateKey -> "PuTTY .ppk private keys are not supported directly. Convert the key to OpenSSH or PEM format, then import it again."
                securityKeyPrivate -> "Security-key/FIDO private keys need hardware-key support and cannot be used as normal private keys."
                header == null -> "Unsupported private-key format"
                !matchingEnd -> "Malformed private key: END marker does not match BEGIN marker"
                !cleanEnvelope -> "Malformed private key: remove text before BEGIN or after END marker"
                !base64BodyPresent -> "Malformed private key: encoded key body is missing or invalid"
                !openSshMagicValid -> "Malformed OpenSSH private key: decoded payload does not start with openssh-key-v1"
                !openSshEncrypted && openSshStructure == OpenSshStructureState.BadCheckInts -> "Malformed OpenSSH private key: private key checkints do not match"
                !openSshStructureValid -> "Malformed OpenSSH private key: decoded payload is missing required key sections"
                openSshEncrypted -> "Encrypted OpenSSH key using ${openSshCipher ?: "unknown"} cipher"
                encrypted -> "Encrypted ${header.formatLabel()} key"
                else -> "${header.formatLabel()} key"
            }
        )
    }

    fun readPrivateKeyText(input: InputStream, maxChars: Int = MaxPrivateKeyChars): String {
        val reader = InputStreamReader(input, Charsets.UTF_8)
        val output = StringBuilder()
        val buffer = CharArray(4096)
        while (true) {
            val read = reader.read(buffer)
            if (read == -1) return output.toString()
            if (output.length + read > maxChars) {
                throw IllegalArgumentException("Private key file is too large. Import a normal OpenSSH or PEM private key under 64 KB.")
            }
            output.append(buffer, 0, read)
        }
    }

    fun publicPreviewForSecret(typeName: String, secret: String): String {
        if (secret.isBlank()) return ""
        return if (typeName == "PrivateKey") {
            val info = inspectPrivateKey(secret)
            if (info.valid) "${info.summary} (${info.fingerprint})" else info.summary
        } else {
            "Password saved"
        }
    }

    fun looksLikePrivateKeyPath(input: String): Boolean {
        val value = input.trim()
        if (value.isBlank() || value.contains("-----BEGIN ")) return false
        val lower = value.lowercase()
        val hasPathSeparator = value.contains('/') || value.contains('\\')
        val filename = lower.substringAfterLast('/').substringAfterLast('\\')
        val hasKeyName = filename.endsWith(".key") ||
            filename.endsWith(".pem") ||
            filename.endsWith(".ppk") ||
            filename in setOf("id_rsa", "id_ed25519", "id_ecdsa", "id_dsa") ||
            filename.startsWith("identity") ||
            filename.startsWith("chrono-") ||
            lower.contains("/ssh-keys/") ||
            lower.contains("\\ssh-keys\\")
        return hasPathSeparator && hasKeyName && value.lineSequence().count() <= 2
    }

    private fun looksLikePublicKeyOnly(input: String): Boolean {
        val first = input.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return first.startsWith("ssh-rsa ") ||
            first.startsWith("ssh-ed25519 ") ||
            first.startsWith("ecdsa-sha2-") ||
            first.startsWith("sk-ssh-") ||
            first.startsWith("sk-ecdsa-")
    }

    private fun looksLikePuttyPrivateKey(input: String): Boolean {
        val first = input.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return first.startsWith("PuTTY-User-Key-File-", ignoreCase = true) ||
            input.contains("\nPrivate-Lines:", ignoreCase = true) ||
            input.contains("\nPrivate-MAC:", ignoreCase = true)
    }

    private fun String.formatLabel(): String {
        return removePrefix("-----BEGIN ")
            .removeSuffix("-----")
            .replace("PRIVATE KEY", "")
            .replace("  ", " ")
            .trim()
            .ifBlank { "private" }
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return "SHA256:${Base64.getEncoder().withoutPadding().encodeToString(digest)}"
    }

    private fun openSshCipherName(key: String): String? {
        val body = key.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("-----BEGIN ") || it.startsWith("-----END ") }
            .joinToString("")
        return runCatching {
            val bytes = Base64.getDecoder().decode(body)
            if (!bytes.startsWithOpenSshMagic()) return null
            Buffer.PlainBuffer(bytes.copyOfRange(OPENSSH_MAGIC.length, bytes.size)).readString()
        }.getOrNull()
    }

    private enum class OpenSshStructureState {
        NotOpenSsh,
        BadMagic,
        BadCheckInts,
        MissingSections,
        Valid
    }

    private fun openSshStructureState(base64Body: String): OpenSshStructureState {
        return runCatching {
            val bytes = Base64.getDecoder().decode(base64Body)
            if (!bytes.startsWithOpenSshMagic()) return OpenSshStructureState.BadMagic
            val reader = OpenSshKeyReader(bytes, OPENSSH_MAGIC.length)
            reader.readString()
            reader.readString()
            reader.readString()
            val keyCount = reader.readUInt32()
            if (keyCount <= 0) return OpenSshStructureState.MissingSections
            repeat(keyCount) { reader.readString() }
            val privateBlob = reader.readString()
            if (privateBlob.size < 12) return OpenSshStructureState.MissingSections
            val privateReader = OpenSshKeyReader(privateBlob, 0)
            val checkInt1 = privateReader.readUInt32()
            val checkInt2 = privateReader.readUInt32()
            if (checkInt1 != checkInt2) OpenSshStructureState.BadCheckInts else OpenSshStructureState.Valid
        }.getOrDefault(OpenSshStructureState.MissingSections)
    }

    private fun ByteArray.startsWithOpenSshMagic(): Boolean {
        val magic = OPENSSH_MAGIC.toByteArray(Charsets.UTF_8)
        if (size < magic.size) return false
        return magic.indices.all { index -> this[index] == magic[index] }
    }

    private fun openSshContainsSecurityKeyType(key: String): Boolean {
        val body = key.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("-----BEGIN ") || it.startsWith("-----END ") }
            .joinToString("")
        return runCatching {
            val decoded = Base64.getDecoder().decode(body).toString(Charsets.ISO_8859_1)
            decoded.contains("sk-ssh-ed25519@openssh.com") ||
                decoded.contains("sk-ecdsa-sha2-nistp256@openssh.com")
        }.getOrDefault(false)
    }

    private class OpenSshKeyReader(
        private val data: ByteArray,
        private var position: Int
    ) {
        fun readUInt32(): Int {
            check(position + 4 <= data.size)
            val value = ((data[position].toInt() and 0xFF) shl 24) or
                ((data[position + 1].toInt() and 0xFF) shl 16) or
                ((data[position + 2].toInt() and 0xFF) shl 8) or
                (data[position + 3].toInt() and 0xFF)
            position += 4
            return value
        }

        fun readString(): ByteArray {
            val length = readUInt32()
            check(length >= 0 && position + length <= data.size)
            return data.copyOfRange(position, position + length).also { position += length }
        }
    }
}

data class KeyMaterialInfo(
    val valid: Boolean,
    val encrypted: Boolean,
    val format: String,
    val fingerprint: String,
    val summary: String
)
