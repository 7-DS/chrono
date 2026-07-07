package com.chrono.ssh.core.service

import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.KeyType
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringWriter
import java.io.StringReader
import java.security.KeyPairGenerator
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPrivateCrtKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

class AndroidSshjCompatTest {
    @Test
    fun loadsUnencryptedOpenSshEd25519PrivateKey() {
        val seed = ByteArray(32) { index -> (index + 1).toByte() }
        val publicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        val pem = openSshEd25519Pem(seed, publicKey)

        val provider = AndroidSshjCompat.loadOpenSshIdentity(
            client = SSHClient(),
            privateKeyMaterial = pem,
            passphrase = null
        )

        assertNotNull(provider)
        assertEquals(KeyType.ED25519, provider!!.type)
        assertArrayEquals(publicKey, provider.public.encoded.takeLast(32).toByteArray())
    }

    @Test
    fun loadsUnencryptedOpenSshRsaPrivateKey() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val publicKey = pair.public as RSAPublicKey
        val privateKey = pair.private as RSAPrivateCrtKey
        val pem = openSshRsaPem(publicKey, privateKey)

        val provider = AndroidSshjCompat.loadOpenSshIdentity(
            client = SSHClient(),
            privateKeyMaterial = pem,
            passphrase = null
        )

        assertNotNull(provider)
        assertEquals(KeyType.RSA, provider!!.type)
        assertEquals(publicKey.modulus, (provider.public as RSAPublicKey).modulus)
        assertEquals(publicKey.publicExponent, (provider.public as RSAPublicKey).publicExponent)
    }

    @Test
    fun loadsUnencryptedOpenSshEcdsaPrivateKey() {
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val publicKey = pair.public as ECPublicKey
        val privateKey = pair.private as ECPrivateKey
        val pem = openSshEcdsaPem("nistp256", "ecdsa-sha2-nistp256", publicKey, privateKey)

        val provider = AndroidSshjCompat.loadOpenSshIdentity(
            client = SSHClient(),
            privateKeyMaterial = pem,
            passphrase = null
        )

        assertNotNull(provider)
        assertEquals(KeyType.ECDSA256, provider!!.type)
        assertEquals(publicKey.w, (provider.public as ECPublicKey).w)
    }

    @Test
    fun exportsAuthorizedPublicKeyLineForOpenSshEd25519PrivateKey() {
        val seed = ByteArray(32) { index -> (index + 1).toByte() }
        val publicKey = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        val pem = openSshEd25519Pem(seed, publicKey)

        val line = AndroidSshjCompat.authorizedPublicKeyLine(pem, comment = "main identity")

        assertNotNull(line)
        assertTrue(line!!.startsWith("ssh-ed25519 "))
        assertTrue(line.endsWith(" main-identity"))
        assertArrayEquals(sshString("ssh-ed25519") + sshString(publicKey), authorizedBlob(line))
    }

    @Test
    fun generatedEd25519KeyLoadsAndExportsAuthorizedLine() {
        val generated = SshKeyGenerator.ed25519("generated key")

        val provider = AndroidSshjCompat.loadOpenSshIdentity(
            client = SSHClient(),
            privateKeyMaterial = generated.privateKeyPem,
            passphrase = null
        )
        val line = AndroidSshjCompat.authorizedPublicKeyLine(generated.privateKeyPem, comment = "generated key")

        assertNotNull(provider)
        assertEquals(KeyType.ED25519, provider!!.type)
        assertEquals(generated.authorizedPublicKey, line)
    }

    @Test
    fun generatedEd25519KeyLoadsWithNonAlignedCommentPadding() {
        val generated = SshKeyGenerator.ed25519("a")

        val provider = AndroidSshjCompat.loadOpenSshIdentity(
            client = SSHClient(),
            privateKeyMaterial = generated.privateKeyPem,
            passphrase = null
        )

        assertNotNull(provider)
        assertEquals(KeyType.ED25519, provider!!.type)
        assertEquals(0, generated.privateKeyPem.openSshPrivateSection().size % 8)
    }

    @Test
    fun generatedRsaKeyLoadsAndExportsAuthorizedLine() {
        val generated = SshKeyGenerator.rsa("generated rsa", bits = 2048)

        val provider = AndroidSshjCompat.loadOpenSshIdentity(
            client = SSHClient(),
            privateKeyMaterial = generated.privateKeyPem,
            passphrase = null
        )
        val line = AndroidSshjCompat.authorizedPublicKeyLine(generated.privateKeyPem, comment = "generated rsa")

        assertNotNull(provider)
        assertEquals(KeyType.RSA, provider!!.type)
        assertEquals(generated.authorizedPublicKey, line)
        assertEquals(0, generated.privateKeyPem.openSshPrivateSection().size % 8)
    }

    @Test
    fun exportsAuthorizedPublicKeyLineForOpenSshRsaPrivateKey() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val pem = openSshRsaPem(pair.public as RSAPublicKey, pair.private as RSAPrivateCrtKey)

        val line = AndroidSshjCompat.authorizedPublicKeyLine(pem, comment = "chrono rsa")

        assertNotNull(line)
        assertTrue(line!!.startsWith("ssh-rsa "))
        assertTrue(line.endsWith(" chrono-rsa"))
    }

    @Test
    fun exportsAuthorizedPublicKeyLineForOpenSshEcdsaPrivateKey() {
        val pair = KeyPairGenerator.getInstance("EC").apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        val pem = openSshEcdsaPem("nistp256", "ecdsa-sha2-nistp256", pair.public as ECPublicKey, pair.private as ECPrivateKey)

        val line = AndroidSshjCompat.authorizedPublicKeyLine(pem, comment = "chrono ecdsa")

        assertNotNull(line)
        assertTrue(line!!.startsWith("ecdsa-sha2-nistp256 "))
        assertTrue(line.endsWith(" chrono-ecdsa"))
    }


    @Test
    fun loadsPemRsaPrivateKeyInMemory() {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val publicKey = pair.public as RSAPublicKey
        val pem = StringWriter().use { writer ->
            JcaPEMWriter(writer).use { it.writeObject(pair) }
            writer.toString()
        }

        val provider = AndroidSshjCompat.loadPemRsaIdentity(
            client = SSHClient(),
            privateKeyMaterial = pem,
            passphrase = null
        )

        assertNotNull(provider)
        assertEquals(KeyType.RSA, provider!!.type)
        assertEquals(publicKey.modulus, (provider.public as RSAPublicKey).modulus)
        assertEquals(publicKey.publicExponent, (provider.public as RSAPublicKey).publicExponent)
    }

    @Test
    fun preparesRsaSha2BeforeLegacySshRsa() {
        val config = DefaultConfig()

        AndroidSshjCompat.prepare(config)

        val names = config.keyAlgorithms.map { it.name }
        assertTrue(names.indexOf("rsa-sha2-512") in 0 until names.indexOf("ssh-rsa"))
        assertTrue(names.indexOf("rsa-sha2-256") in 0 until names.indexOf("ssh-rsa"))
    }

    @Test
    fun sshjLoadsEncryptedOpenSshV1KeyWithPassphrase() {
        assumeTrue("ssh-keygen is required for this compatibility test", sshKeygenAvailable())
        val dir = createTempDir(prefix = "chronossh-key-test-")
        try {
            val keyFile = File(dir, "id_ed25519")
            val passphrase = "chrono-passphrase"
            val generated = ProcessBuilder(
                "ssh-keygen",
                "-q",
                "-t",
                "ed25519",
                "-N",
                passphrase,
                "-C",
                "chronossh-test",
                "-f",
                keyFile.absolutePath
            ).redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
            assumeTrue("ssh-keygen could not create an encrypted OpenSSH key", generated.exitValue() == 0)

            val encryptedKey = keyFile.readText()
            val provider = OpenSSHKeyV1KeyFile().apply {
                init(StringReader(encryptedKey), null as java.io.Reader?, TestPasswordFinder(passphrase))
            }

            assertEquals(KeyType.ED25519, provider.type)
            assertNotNull(provider.public)
            assertNotNull(provider.private)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun exportsAuthorizedPublicKeyLineForEncryptedOpenSshKeyWithoutPassphrase() {
        assumeTrue("ssh-keygen is required for this compatibility test", sshKeygenAvailable())
        val dir = createTempDir(prefix = "chronossh-key-test-")
        try {
            val keyFile = File(dir, "id_ed25519")
            val passphrase = "chrono-passphrase"
            val generated = ProcessBuilder(
                "ssh-keygen",
                "-q",
                "-t",
                "ed25519",
                "-N",
                passphrase,
                "-C",
                "chronossh-test",
                "-f",
                keyFile.absolutePath
            ).redirectErrorStream(true)
                .start()
                .also { it.waitFor() }
            assumeTrue("ssh-keygen could not create an encrypted OpenSSH key", generated.exitValue() == 0)

            val line = AndroidSshjCompat.authorizedPublicKeyLine(keyFile.readText(), comment = "encrypted key")

            assertEquals(keyFile.resolveSibling("id_ed25519.pub").readText().trim().substringBeforeLast(" ") + " encrypted-key", line)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun openSshEd25519Pem(seed: ByteArray, publicKey: ByteArray): String {
        val publicBlob = sshString("ssh-ed25519") + sshString(publicKey)
        val privateBlob = uint32(0x12345678) +
            uint32(0x12345678) +
            sshString("ssh-ed25519") +
            sshString(publicKey) +
            sshString(seed + publicKey) +
            sshString("chrono-test")
        val body = "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
            sshString("none") +
            sshString("none") +
            sshString(ByteArray(0)) +
            uint32(1) +
            sshString(publicBlob) +
            sshString(privateBlob)
        val encoded = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(body)
        return """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $encoded
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
    }

    private fun openSshRsaPem(publicKey: RSAPublicKey, privateKey: RSAPrivateCrtKey): String {
        val publicBlob = sshString("ssh-rsa") +
            sshMpInt(publicKey.publicExponent) +
            sshMpInt(publicKey.modulus)
        val privateBlob = uint32(0x12345678) +
            uint32(0x12345678) +
            sshString("ssh-rsa") +
            sshMpInt(publicKey.modulus) +
            sshMpInt(publicKey.publicExponent) +
            sshMpInt(privateKey.privateExponent) +
            sshMpInt(privateKey.crtCoefficient) +
            sshMpInt(privateKey.primeP) +
            sshMpInt(privateKey.primeQ) +
            sshString("chrono-rsa-test")
        val body = "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
            sshString("none") +
            sshString("none") +
            sshString(ByteArray(0)) +
            uint32(1) +
            sshString(publicBlob) +
            sshString(privateBlob)
        val encoded = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(body)
        return """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $encoded
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
    }

    private fun openSshEcdsaPem(
        curveName: String,
        keyType: String,
        publicKey: ECPublicKey,
        privateKey: ECPrivateKey
    ): String {
        val publicPoint = ecPoint(publicKey)
        val publicBlob = sshString(keyType) +
            sshString(curveName) +
            sshString(publicPoint)
        val privateBlob = uint32(0x12345678) +
            uint32(0x12345678) +
            sshString(keyType) +
            sshString(curveName) +
            sshString(publicPoint) +
            sshMpInt(privateKey.s) +
            sshString("chrono-ecdsa-test")
        val body = "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
            sshString("none") +
            sshString("none") +
            sshString(ByteArray(0)) +
            uint32(1) +
            sshString(publicBlob) +
            sshString(privateBlob)
        val encoded = Base64.getMimeEncoder(70, "\n".toByteArray()).encodeToString(body)
        return """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $encoded
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()
    }

    private fun ecPoint(publicKey: ECPublicKey): ByteArray {
        val fieldBytes = (publicKey.params.curve.field.fieldSize + 7) / 8
        return byteArrayOf(0x04) +
            publicKey.w.affineX.toFixedBytes(fieldBytes) +
            publicKey.w.affineY.toFixedBytes(fieldBytes)
    }

    private fun java.math.BigInteger.toFixedBytes(length: Int): ByteArray {
        val raw = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray((length - raw.size).coerceAtLeast(0)) + raw.takeLast(length).toByteArray()
    }

    private fun sshString(value: String): ByteArray = sshString(value.toByteArray(Charsets.UTF_8))

    private fun sshString(value: ByteArray): ByteArray = uint32(value.size) + value

    private fun sshMpInt(value: java.math.BigInteger): ByteArray = sshString(value.toByteArray())

    private fun uint32(value: Int): ByteArray {
        val out = ByteArrayOutputStream(4)
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
        return out.toByteArray()
    }

    private fun authorizedBlob(line: String): ByteArray {
        return Base64.getDecoder().decode(line.split(Regex("\\s+"))[1])
    }

    private fun String.openSshPrivateSection(): ByteArray {
        val body = lineSequence()
            .filterNot { it.startsWith("-----") }
            .joinToString("")
            .let { Base64.getDecoder().decode(it) }
        var offset = "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8).size
        offset = body.skipSshString(offset)
        offset = body.skipSshString(offset)
        offset = body.skipSshString(offset)
        offset += 4
        offset = body.skipSshString(offset)
        return body.readSshString(offset)
    }

    private fun ByteArray.skipSshString(offset: Int): Int = offset + 4 + readUInt32(offset)

    private fun ByteArray.readSshString(offset: Int): ByteArray {
        val size = readUInt32(offset)
        return copyOfRange(offset + 4, offset + 4 + size)
    }

    private fun ByteArray.readUInt32(offset: Int): Int {
        return ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }

    private fun sshKeygenAvailable(): Boolean {
        return runCatching {
            ProcessBuilder("ssh-keygen", "-V").redirectErrorStream(true).start().also { it.waitFor() }
            true
        }.getOrDefault(false)
    }

    private class TestPasswordFinder(private val password: String) : PasswordFinder {
        override fun reqPassword(resource: Resource<*>?): CharArray = password.toCharArray()
        override fun shouldRetry(resource: Resource<*>?): Boolean = false
    }
}
