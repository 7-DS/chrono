package com.chrono.ssh.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BackupEncryptionCodecTest {
    @Test
    fun encryptedBackupRoundTripsMetadataText() {
        val plain = "ChronoSSH backup v1\n[settings]\nautoRefreshSeconds=2\n[end]"
        val encrypted = BackupEncryptionCodec.encryptToString(plain, "correct horse".toCharArray())

        assertTrue(BackupEncryptionCodec.isEncrypted(encrypted))
        assertNotEquals(plain, encrypted)
        assertEquals(plain, BackupEncryptionCodec.decryptToString(encrypted, "correct horse".toCharArray()))
    }

    @Test(expected = Exception::class)
    fun encryptedBackupRejectsWrongPassphrase() {
        val encrypted = BackupEncryptionCodec.encryptToString("secret metadata", "right".toCharArray())

        BackupEncryptionCodec.decryptToString(encrypted, "wrong".toCharArray())
    }

    @Test(expected = IllegalArgumentException::class)
    fun encryptedBackupRejectsMalformedSaltAndIvLengths() {
        val malformed = listOf(
            "CHRONOSSH-ENC-V1",
            Base64.getEncoder().encodeToString(ByteArray(1)),
            Base64.getEncoder().encodeToString(ByteArray(1)),
            Base64.getEncoder().encodeToString(ByteArray(16))
        ).joinToString("\n")

        BackupEncryptionCodec.decryptToString(malformed, "passphrase".toCharArray())
    }
}
