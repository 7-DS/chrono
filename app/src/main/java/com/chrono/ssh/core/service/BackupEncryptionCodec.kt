package com.chrono.ssh.core.service

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object BackupEncryptionCodec {
    private const val Header = "CHRONOSSH-ENC-V1"
    private const val SaltBytes = 16
    private const val IvBytes = 12
    private const val KeyBits = 256
    private const val GcmTagBits = 128
    private const val Iterations = 120_000
    private val random = SecureRandom()

    fun isEncrypted(text: String): Boolean = text.trimStart().startsWith(Header)

    fun encryptToString(plainText: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Backup passphrase is required." }
        val salt = ByteArray(SaltBytes).also(random::nextBytes)
        val iv = ByteArray(IvBytes).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(GcmTagBits, iv))
        val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(
            Header,
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(iv),
            Base64.getEncoder().encodeToString(cipherText)
        ).joinToString("\n")
    }

    fun decryptToString(encryptedText: String, passphrase: CharArray): String {
        require(passphrase.isNotEmpty()) { "Backup passphrase is required." }
        val lines = encryptedText.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        require(lines.size == 4 && lines[0] == Header) { "Encrypted backup format is not recognized." }
        val salt = Base64.getDecoder().decode(lines[1])
        val iv = Base64.getDecoder().decode(lines[2])
        val cipherText = Base64.getDecoder().decode(lines[3])
        require(salt.size == SaltBytes && iv.size == IvBytes && cipherText.isNotEmpty()) {
            "Encrypted backup format is not recognized."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(GcmTagBits, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, Iterations, KeyBits)
        val encoded = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return SecretKeySpec(encoded, "AES")
    }
}
