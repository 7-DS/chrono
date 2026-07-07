package com.chrono.ssh.core.rclone

import org.junit.Assert.assertTrue
import org.junit.Test

class RcloneConfigParserTest {
    @Test
    fun encryptedConfigIsDetectedBeforeIniParsing() {
        assertTrue(
            RcloneConfigParser.parse(
            """
            # Encrypted rclone configuration File

            RCLONE_ENCRYPT_V0:
            abc123
            """.trimIndent()
            ) is RcloneConfigParseResult.Encrypted
        )
    }
}
