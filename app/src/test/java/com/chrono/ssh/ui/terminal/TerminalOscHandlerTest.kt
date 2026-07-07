package com.chrono.ssh.ui.terminal

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalOscHandlerTest {
    @Test
    fun stripsOsc52AndSetsClipboardAcrossChunks() {
        val copied = mutableListOf<String>()
        val handler = TerminalOscHandler(onClipboardSet = copied::add)
        val encoded = Base64.getEncoder().encodeToString("hello".toByteArray())

        assertEquals("before", String(handler.process("before\u001b]52;c;$encoded".toByteArray())))
        assertEquals("after", String(handler.process("\u0007after".toByteArray())))
        assertEquals(listOf("hello"), copied)
    }

    @Test
    fun stripsHandledOscEventsAndKeepsPlainOutput() {
        val links = mutableListOf<String?>()
        val notifications = mutableListOf<Pair<String, String>>()
        val cwd = mutableListOf<String>()
        val handler = TerminalOscHandler(
            onHyperlink = links::add,
            onNotification = { title, body -> notifications += title to body },
            onCwdChanged = cwd::add
        )

        val output = handler.process(
            "a\u001b]7;file:///home/me\u0007b\u001b]8;;https://x.test\u001b\\c\u001b]9;done\u0007".toByteArray()
        )

        assertEquals("abc", String(output))
        assertEquals(listOf("file:///home/me"), cwd)
        assertEquals(listOf("https://x.test"), links)
        assertEquals(listOf("" to "done"), notifications)
    }

    @Test
    fun passesUnhandledOscThrough() {
        val input = "\u001b]0;title\u0007text"

        assertEquals(input, String(TerminalOscHandler().process(input.toByteArray())))
    }
}
