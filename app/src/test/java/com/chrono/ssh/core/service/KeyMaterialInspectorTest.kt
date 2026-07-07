package com.chrono.ssh.core.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64

class KeyMaterialInspectorTest {
    @Test
    fun readsPrivateKeyImportWithinLimit() {
        val text = "-----BEGIN PRIVATE KEY-----\nZm9v\n-----END PRIVATE KEY-----"

        assertEquals(
            text,
            KeyMaterialInspector.readPrivateKeyText(ByteArrayInputStream(text.toByteArray()))
        )
    }

    @Test
    fun rejectsOversizedPrivateKeyImportBeforeStoringText() {
        val failure = runCatching {
            KeyMaterialInspector.readPrivateKeyText(
                ByteArrayInputStream("abcdef".toByteArray()),
                maxChars = 5
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("too large"))
    }

    @Test
    fun rejectsOversizedPastedPrivateKeyText() {
        val info = KeyMaterialInspector.inspectPrivateKey("x".repeat(KeyMaterialInspector.MaxPrivateKeyChars + 1))

        assertFalse(info.valid)
        assertTrue(info.summary.contains("too large"))
    }

    @Test
    fun acceptsOpenSshPrivateKeyEnvelope() {
        val publicBlob = sshString("ssh-ed25519") + sshString(ByteArray(32) { 7 })
        val privateBlob = byteArrayOf(0x12, 0x34, 0x56, 0x78) +
            byteArrayOf(0x12, 0x34, 0x56, 0x78) +
            sshString("ssh-ed25519") +
            sshString(ByteArray(32) { 7 }) +
            sshString(ByteArray(64) { 9 }) +
            sshString("chrono-test")
        val body = Base64.getEncoder().encodeToString(
            "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
                sshString("none") +
                sshString("none") +
                sshString(ByteArray(0)) +
                byteArrayOf(0, 0, 0, 1) +
                sshString(publicBlob) +
                sshString(privateBlob)
        )
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $body
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertTrue(info.valid)
        assertFalse(info.encrypted)
        assertTrue(info.fingerprint.startsWith("SHA256:"))
        assertTrue(info.summary.contains("OPENSSH"))
    }

    @Test
    fun rejectsOpenSshEnvelopeWithOnlyMagicAndNoKeySections() {
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAA=
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertFalse(info.valid)
        assertTrue(info.summary.contains("required key sections"))
    }

    @Test
    fun rejectsOpenSshEnvelopeWithWrongDecodedMagic() {
        val body = Base64.getEncoder().encodeToString(
            "not-an-openssh-private-key-payload".toByteArray(Charsets.UTF_8)
        )
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $body
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertFalse(info.valid)
        assertTrue(info.summary.contains("openssh-key-v1"))
    }

    @Test
    fun rejectsUnencryptedOpenSshEnvelopeWithMismatchedCheckInts() {
        val publicBlob = sshString("ssh-ed25519") + sshString(ByteArray(32) { 7 })
        val privateBlob = byteArrayOf(0x12, 0x34, 0x56, 0x78) +
            byteArrayOf(0x87.toByte(), 0x65, 0x43, 0x21) +
            sshString("ssh-ed25519") +
            sshString(ByteArray(32) { 7 }) +
            sshString(ByteArray(64) { 9 }) +
            sshString("chrono-test")
        val body = Base64.getEncoder().encodeToString(
            "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
                sshString("none") +
                sshString("none") +
                sshString(ByteArray(0)) +
                byteArrayOf(0, 0, 0, 1) +
                sshString(publicBlob) +
                sshString(privateBlob)
        )
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $body
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertFalse(info.valid)
        assertTrue(info.summary.contains("checkints"))
    }

    @Test
    fun rejectsPrivateKeyWrappedInExtraPastedText() {
        val body = Base64.getEncoder().encodeToString(
            "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
                sshString("none") +
                sshString("none") +
                sshString(ByteArray(0)) +
                byteArrayOf(0, 0, 0, 1) +
                sshString("public-key-placeholder") +
                sshString("private-key-placeholder")
        )
        val key = """
            copied from notes:
            -----BEGIN OPENSSH PRIVATE KEY-----
            $body
            -----END OPENSSH PRIVATE KEY-----
            done
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertFalse(info.valid)
        assertTrue(info.summary.contains("remove text before BEGIN or after END"))
    }

    @Test
    fun detectsEncryptedPemPrivateKey() {
        val key = """
            -----BEGIN RSA PRIVATE KEY-----
            Proc-Type: 4,ENCRYPTED
            DEK-Info: AES-128-CBC,00000000000000000000000000000000
            ZW5jcnlwdGVkLXByaXZhdGUta2V5LXBheWxvYWQtc2FtcGxl
            -----END RSA PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertTrue(info.valid)
        assertTrue(info.encrypted)
    }

    @Test
    fun detectsEncryptedOpenSshPrivateKeyCipher() {
        val body = Base64.getEncoder().encodeToString(
            "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
                sshString("aes256-ctr") +
                sshString("bcrypt") +
                sshString("salt-and-rounds") +
                byteArrayOf(0, 0, 0, 1) +
                sshString("public-key-placeholder") +
                sshString("private-key-placeholder")
        )
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $body
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertTrue(info.valid)
        assertTrue(info.encrypted)
        assertTrue(info.summary.contains("aes256-ctr"))
    }

    @Test
    fun rejectsEncryptedOpenSshEnvelopeWithNoKeySections() {
        val body = Base64.getEncoder().encodeToString(
            "openssh-key-v1\u0000".toByteArray(Charsets.UTF_8) +
                sshString("aes256-ctr")
        )
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            $body
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertFalse(info.valid)
        assertTrue(info.encrypted)
        assertTrue(info.summary.contains("required key sections"))
    }

    @Test
    fun rejectsMalformedPrivateKeyText() {
        val info = KeyMaterialInspector.inspectPrivateKey("ssh-rsa AAAAB3Nza public-key-only")

        assertFalse(info.valid)
        assertTrue(info.summary.contains("public key"))
    }

    @Test
    fun rejectsPuttyPrivateKeyWithConversionHint() {
        val info = KeyMaterialInspector.inspectPrivateKey(
            """
            PuTTY-User-Key-File-3: ssh-rsa
            Encryption: none
            Comment: imported-key
            Public-Lines: 1
            AAAAB3NzaC1yc2EAAAADAQABAAABAQ==
            Private-Lines: 1
            AAAAB3NzaC1yc2EAAAADAQABAAABAQ==
            Private-MAC: 0000000000000000000000000000000000000000
            """.trimIndent()
        )

        assertFalse(info.valid)
        assertTrue(info.summary.contains("PuTTY"))
        assertTrue(info.summary.contains("OpenSSH"))
    }

    @Test
    fun rejectsSavedCachePathInsteadOfPrivateKeyPayload() {
        val info = KeyMaterialInspector.inspectPrivateKey(
            "/data/user/0/com.chrono.ssh/cache/ssh-keys/chrono-uu-5957772498405076834.key"
        )

        assertFalse(info.valid)
        assertTrue(info.summary.contains("file path instead of key contents"))
    }

    @Test
    fun detectsWindowsKeyPathInsteadOfPrivateKeyPayload() {
        assertTrue(
            KeyMaterialInspector.looksLikePrivateKeyPath(
                "C:\\Users\\me\\.ssh\\id_ed25519"
            )
        )
    }

    @Test
    fun detectsAndroidTemporaryKeyPathInsteadOfPrivateKeyPayload() {
        assertTrue(
            KeyMaterialInspector.looksLikePrivateKeyPath(
                "/data/user/0/com.chrono.ssh/cache/ssh-keys/chrono-ui7-123.key"
            )
        )
    }

    @Test
    fun detectsCommonPrivateKeyPathVariants() {
        assertTrue(KeyMaterialInspector.looksLikePrivateKeyPath("/home/aldr/.ssh/id_ecdsa"))
        assertTrue(KeyMaterialInspector.looksLikePrivateKeyPath("/home/aldr/.ssh/id_dsa"))
        assertTrue(KeyMaterialInspector.looksLikePrivateKeyPath("/sdcard/Download/server.ppk"))
        assertTrue(KeyMaterialInspector.looksLikePrivateKeyPath("/data/user/0/com.chrono.ssh/cache/ssh-keys/chrono ui7.key"))
    }

    @Test
    fun flagsSecurityKeyPrivateMaterialAsUnsupported() {
        val key = """
            -----BEGIN OPENSSH PRIVATE KEY-----
            c2stc3NoLWVkMjU1MTlAb3BlbnNzaC5jb20=
            -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

        val info = KeyMaterialInspector.inspectPrivateKey(key)

        assertFalse(info.valid)
        assertTrue(info.summary.contains("FIDO"))
    }

    private fun sshString(value: String): ByteArray {
        return sshString(value.toByteArray(Charsets.UTF_8))
    }

    private fun sshString(bytes: ByteArray): ByteArray {
        return byteArrayOf(
            ((bytes.size ushr 24) and 0xFF).toByte(),
            ((bytes.size ushr 16) and 0xFF).toByte(),
            ((bytes.size ushr 8) and 0xFF).toByte(),
            (bytes.size and 0xFF).toByte()
        ) + bytes
    }
}
