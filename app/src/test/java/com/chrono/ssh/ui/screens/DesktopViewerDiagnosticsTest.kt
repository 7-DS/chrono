package com.chrono.ssh.ui.screens

import com.chrono.ssh.core.vnc.ColorDepth
import java.util.concurrent.ConcurrentLinkedQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DesktopViewerDiagnosticsTest {
    @Test
    fun viewerDiagnosticsAppendAndCapText() {
        assertEquals("one\ntwo", appendViewerDiagnostics("one", "two"))
        assertEquals("2\n345", appendViewerDiagnostics("12", "345", maxChars = 5))
        assertEquals("old", appendViewerDiagnostics("old", "   "))
    }

    @Test
    fun viewerDiagnosticsDrainQueueOnce() {
        val queue = ConcurrentLinkedQueue(listOf("first", "second"))

        assertEquals("first\nsecond", drainViewerDiagnostics(queue))
        assertNull(drainViewerDiagnostics(queue))
    }

    @Test
    fun vncBandwidthHintNamesSuggestedDepth() {
        assertEquals("Slow VNC link detected. Try 8-bit colour.", vncBandwidthHint(ColorDepth.BPP_8_INDEXED))
    }
}
