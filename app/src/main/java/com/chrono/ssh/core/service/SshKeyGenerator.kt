package com.chrono.ssh.core.service

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters

data class GeneratedSshKey(
    val privateKeyPem: String,
    val authorizedPublicKey: String
)

object SshKeyGenerator {
    fun ed25519(comment: String = "chronossh", random: SecureRandom = SecureRandom()): GeneratedSshKey {
        val privateKey = Ed25519PrivateKeyParameters(random)
        val seed = privateKey.encoded
        val publicKey = privateKey.generatePublicKey().encoded
        val safeComment = safeComment(comment)
        val publicBlob = sshString("ssh-ed25519") + sshString(publicKey)
        val privatePayload = uint32(0x0f0e0d0c) +
            uint32(0x0f0e0d0c) +
            sshString("ssh-ed25519") +
            sshString(publicKey) +
            sshString(seed + publicKey) +
            sshString(safeComment)
        val privateBody = privatePayload + openSshPadding(privatePayload.size)
        val body = "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
            sshString("none") +
            sshString("none") +
            sshString(ByteArray(0)) +
            uint32(1) +
            sshString(publicBlob) +
            sshString(privateBody)
        val encoded = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(body)
        return GeneratedSshKey(
            privateKeyPem = "-----BEGIN OPENSSH PRIVATE KEY-----\n$encoded\n-----END OPENSSH PRIVATE KEY-----",
            authorizedPublicKey = "ssh-ed25519 ${Base64.getEncoder().encodeToString(publicBlob)} $safeComment"
        )
    }

    fun rsa(comment: String = "chronossh", bits: Int = 3072, random: SecureRandom = SecureRandom()): GeneratedSshKey {
        val keySize = bits.coerceIn(2048, 8192)
        val pair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(keySize, random)
        }.generateKeyPair()
        val publicKey = pair.public as RSAPublicKey
        val privateKey = pair.private as RSAPrivateCrtKey
        val safeComment = safeComment(comment)
        val publicBlob = sshString("ssh-rsa") +
            sshMpInt(publicKey.publicExponent) +
            sshMpInt(publicKey.modulus)
        val privatePayload = uint32(0x0f0e0d0c) +
            uint32(0x0f0e0d0c) +
            sshString("ssh-rsa") +
            sshMpInt(publicKey.modulus) +
            sshMpInt(publicKey.publicExponent) +
            sshMpInt(privateKey.privateExponent) +
            sshMpInt(privateKey.crtCoefficient) +
            sshMpInt(privateKey.primeP) +
            sshMpInt(privateKey.primeQ) +
            sshString(safeComment)
        val privateBody = privatePayload + openSshPadding(privatePayload.size)
        val body = "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
            sshString("none") +
            sshString("none") +
            sshString(ByteArray(0)) +
            uint32(1) +
            sshString(publicBlob) +
            sshString(privateBody)
        val encoded = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(body)
        return GeneratedSshKey(
            privateKeyPem = "-----BEGIN OPENSSH PRIVATE KEY-----\n$encoded\n-----END OPENSSH PRIVATE KEY-----",
            authorizedPublicKey = "ssh-rsa ${Base64.getEncoder().encodeToString(publicBlob)} $safeComment"
        )
    }

    private fun safeComment(comment: String): String {
        return comment.trim().replace(Regex("\\s+"), "-").ifBlank { "chronossh" }
    }

    private fun openSshPadding(payloadSize: Int, blockSize: Int = 8): ByteArray {
        val paddingLength = blockSize - (payloadSize % blockSize)
        return ByteArray(paddingLength) { index -> (index + 1).toByte() }
    }

    private fun sshString(value: String): ByteArray = sshString(value.toByteArray(Charsets.UTF_8))

    private fun sshString(value: ByteArray): ByteArray = uint32(value.size) + value

    private fun sshMpInt(value: BigInteger): ByteArray {
        val bytes = value.toByteArray()
        return sshString(bytes)
    }

    private fun uint32(value: Int): ByteArray {
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte()
        )
    }
}
