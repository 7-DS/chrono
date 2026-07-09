package com.chrono.ssh.core.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshjTransportTest {
    @Test
    fun terminalStartupDirectoryIsShellQuoted() {
        assertEquals("'/srv/my app'", shellQuoteForTerminalStartup("/srv/my app"))
        assertEquals("'/srv/it'\\''s-live'", shellQuoteForTerminalStartup("/srv/it's-live"))
    }

    @Test
    fun shellTerminalGuardRequiresConnectedOpenNonEofSession() {
        assertTrue(sshjShellCanUseTerminal(clientConnected = true, shellOpen = true, shellEof = false))
        assertFalse(sshjShellCanUseTerminal(clientConnected = false, shellOpen = true, shellEof = false))
        assertFalse(sshjShellCanUseTerminal(clientConnected = true, shellOpen = false, shellEof = false))
        assertFalse(sshjShellCanUseTerminal(clientConnected = true, shellOpen = true, shellEof = true))
    }

    @Test
    fun transcriptBufferKeepsOnlyRecentOutput() {
        val transcript = StringBuilder()

        appendBoundedTranscript(transcript, "a".repeat(SSH_TRANSCRIPT_MAX_CHARS))
        appendBoundedTranscript(transcript, "tail")

        assertEquals(SSH_TRANSCRIPT_MAX_CHARS, transcript.length)
        assertTrue(transcript.endsWith("tail"))
    }
}
