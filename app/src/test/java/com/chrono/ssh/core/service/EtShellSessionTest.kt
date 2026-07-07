package com.chrono.ssh.core.service

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EtShellSessionTest {
    @Test
    fun parsesIdPasskeyFromEtTerminalOutput() {
        val parsed = EtBootstrapParser.parseIdPasskey(
            "noise\nIDPASSKEY:XXXabcdefghijklm/12345678901234567890123456789012\n"
        )

        assertNotNull(parsed)
        assertEquals("XXXabcdefghijklm", parsed!!.clientId)
        assertEquals("12345678901234567890123456789012", parsed.passkey)
    }

    @Test
    fun rejectsOutputWithoutIdPasskey() {
        assertNull(EtBootstrapParser.parseIdPasskey("etterminal: command not found"))
    }

    @Test
    fun rejectsIdPasskeyWithTrailingJunk() {
        assertNull(
            EtBootstrapParser.parseIdPasskey(
                "IDPASSKEY:XXXabcdefghijklm/12345678901234567890123456789012extra"
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun etBootstrapRejectsInvalidPort() {
        EtBootstrap("example.com", 0, "XXXabcdefghijklm", "12345678901234567890123456789012")
    }

    @Test
    fun proposalMatchesHavenEtBootstrapShape() {
        val proposal = EtBootstrapProposal.create(Random(7))

        assertEquals(16, proposal.clientId.length)
        assertEquals("XXX", proposal.clientId.take(3))
        assertEquals(32, proposal.passkey.length)
    }

    @Test
    fun bootstrapCommandQuotesProfileValues() {
        val proposal = EtBootstrapProposal("XXXabcdefghijklm", "12345678901234567890123456789012")

        assertEquals(
            "printf %s 'XXXabcdefghijklm/12345678901234567890123456789012_xterm-256color' | '/opt/et terminal'",
            EtBootstrapCommand.build(proposal, "xterm-256color", "/opt/et terminal")
        )
    }

    @Test
    fun bootstrapCommandEscapesSingleQuotes() {
        val proposal = EtBootstrapProposal("XXXabcdefghijklm", "12345678901234567890123456789012")

        assertEquals(
            "printf %s 'XXXabcdefghijklm/12345678901234567890123456789012_xterm'\"'\"'bad' | 'et'\"'\"'terminal'",
            EtBootstrapCommand.build(proposal, "xterm'bad", "et'terminal")
        )
    }

    @Test
    fun etBootstrapFailureExplainsMissingServerBinary() {
        val message = EtBootstrapFailure.message("sh: etterminal: not found")

        assertEquals(true, message.contains("Install Eternal Terminal"))
        assertEquals(true, message.contains("IDPASSKEY"))
    }
}
