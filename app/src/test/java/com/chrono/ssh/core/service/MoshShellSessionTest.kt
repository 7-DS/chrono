package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.MoshPortRange
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoshShellSessionTest {
    @Test
    fun parsesMoshConnectLineFromServerOutput() {
        val parsed = MoshBootstrapParser.parseConnect(
            "MOSH CONNECT 60001 AAECAwQFBgcICQoLDA0ODw\nmosh-server (mosh 1.4.0)"
        )

        assertEquals(60001, parsed?.port)
        assertEquals("AAECAwQFBgcICQoLDA0ODw", parsed?.key)
    }

    @Test
    fun rejectsOutputWithoutMoshConnectLine() {
        assertNull(MoshBootstrapParser.parseConnect("mosh-server: command not found"))
    }

    @Test
    fun decckmStartupBytesMatchHavenMoshFix() {
        assertArrayEquals(
            byteArrayOf(0x1B, '['.code.toByte(), '?'.code.toByte(), '1'.code.toByte(), 'h'.code.toByte()),
            MoshTerminalModes.decckmOn
        )
    }

    @Test
    fun moshServerCommandQuotesStartupCommand() {
        val command = MoshBootstrapCommand.build("tmux new -A -s 'main work'")

        assertEquals(
            "'mosh-server' new -s -c 256 -l LANG='en_US.UTF-8' -- sh -lc 'tmux new -A -s '\"'\"'main work'\"'\"''",
            command
        )
    }

    @Test
    fun moshServerCommandSkipsBlankStartupCommand() {
        assertEquals(
            "'mosh-server' new -s -c 256 -l LANG='en_US.UTF-8'",
            MoshBootstrapCommand.build("   ")
        )
    }

    @Test
    fun moshServerCommandUsesProfileKnobs() {
        assertEquals(
            "'/usr/local/bin/mosh-server' new -s -p 60000:61000 -c 16 -l LANG='C.UTF-8' -- sh -lc 'screen -x'",
            MoshBootstrapCommand.build(
                startupCommand = "screen -x",
                serverCommand = "/usr/local/bin/mosh-server",
                locale = "C.UTF-8",
                colors = 16,
                portRange = MoshPortRange(60000, 61000)
            )
        )
    }

    @Test
    fun moshPortRangeParsesSingleAndRange() {
        assertEquals("60001", MoshPortRange.tryParse(" 60001 ")?.commandValue())
        assertEquals("60000:61000", MoshPortRange.tryParse("60000:61000")?.commandValue())
        assertNull(MoshPortRange.tryParse(""))
        assertNull(MoshPortRange.tryParse("61000:60000"))
        assertNull(MoshPortRange.tryParse("60000:"))
        assertNull(MoshPortRange.tryParse("65536"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun moshServerCommandRejectsInvalidColors() {
        MoshBootstrapCommand.build("", colors = 7)
    }

    @Test
    fun rejectsInvalidBootstrapPortsAndKeys() {
        assertNull(MoshBootstrapParser.parseConnect("MOSH CONNECT 0 AAECAwQFBgcICQoLDA0ODw"))
        assertNull(MoshBootstrapParser.parseConnect("MOSH CONNECT 65536 AAECAwQFBgcICQoLDA0ODw"))
        assertNull(MoshBootstrapParser.parseConnect("MOSH CONNECT 60001 short"))
        assertNull(MoshBootstrapParser.parseConnect("MOSH CONNECT 60001 AAAAAAAAAAAAAAAAAAAAAA"))
    }

    @Test
    fun moshBootstrapFailureExplainsMissingServerBinary() {
        val message = MoshBootstrapFailure.message("sh: mosh-server: command not found")

        assertEquals(true, message.contains("Install mosh-server"))
        assertEquals(true, message.contains("MOSH CONNECT"))
    }
}
