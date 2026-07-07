package com.chrono.ssh.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalTranscriptSearchTest {
    @Test
    fun transcriptDecoderPreservesUtf8SplitAcrossChunks() {
        val decoder = TerminalTranscriptDecoder()

        assertEquals("hi ", decoder.decode(byteArrayOf(0x68, 0x69, 0x20, 0xF0.toByte(), 0x9F.toByte())))
        assertEquals("😀!", decoder.decode(byteArrayOf(0x98.toByte(), 0x80.toByte(), 0x21)))
    }

    @Test
    fun transcriptSanitizerRemovesAnsiOscAndUnsafeControls() {
        val text = "\u001B[31mred\u001B[0m \u001B]52;c;secret\u0007https://x.test\u0000\t\n"

        assertEquals("red https://x.test\t\n", sanitizeTerminalTranscriptText(text))
    }

    @Test
    fun transcriptSanitizerDropsEscapeSequencesSplitAcrossChunks() {
        val sanitizer = TerminalTranscriptSanitizer()

        assertEquals("red ", sanitizer.sanitize("red \u001B"))
        assertEquals(" ok", sanitizer.sanitize("]52;c;secret\u0007 ok"))
    }

    @Test
    fun trimsQueryAndMatchesCaseInsensitively() {
        assertEquals(
            listOf(0, 11),
            transcriptSearchOffsets("Alpha beta ALPHA", " alpha ")
        )
    }

    @Test
    fun ignoresBlankQuery() {
        assertEquals(emptyList<Int>(), transcriptSearchOffsets("Alpha beta", "   "))
    }

    @Test
    fun searchNavigationWrapsForwardAndBackward() {
        val offsets = listOf(2, 8, 14)

        assertEquals(TerminalSearchSelection(0, 2, 3), terminalSearchNavigate(offsets, null, TerminalSearchDirection.Next))
        assertEquals(TerminalSearchSelection(1, 8, 3), terminalSearchNavigate(offsets, 0, TerminalSearchDirection.Next))
        assertEquals(TerminalSearchSelection(0, 2, 3), terminalSearchNavigate(offsets, 2, TerminalSearchDirection.Next))
        assertEquals(TerminalSearchSelection(2, 14, 3), terminalSearchNavigate(offsets, 0, TerminalSearchDirection.Previous))
    }

    @Test
    fun searchNavigationHandlesNoMatchesAndStaleIndex() {
        assertEquals(null, terminalSearchNavigate(emptyList(), null, TerminalSearchDirection.Next))
        assertEquals(TerminalSearchSelection(0, 4, 1), terminalSearchNavigate(listOf(4), 9, TerminalSearchDirection.Next))
    }

    @Test
    fun detectsHttpUrlsAndTrimsTrailingPunctuation() {
        assertEquals(
            listOf(
                TerminalUrlSpan("https://example.test/path?q=1", 6, 35),
                TerminalUrlSpan("http://host.local:8080", 43, 65)
            ),
            terminalUrlSpans("open (https://example.test/path?q=1), then http://host.local:8080.")
        )
    }

    @Test
    fun ignoresIncompleteUrlSchemes() {
        assertEquals(emptyList<TerminalUrlSpan>(), terminalUrlSpans("curl http:// https://"))
    }

    @Test
    fun latestUrlReturnsLastDetectedUrl() {
        assertEquals(
            "https://second.example/path",
            terminalLatestUrl("first http://first.example then https://second.example/path)")
        )
        assertEquals(null, terminalLatestUrl("no links here"))
    }

    @Test
    fun transcriptOffsetAtCellAccountsForNewlinesAndSoftWrap() {
        val transcript = "abcdEF\nhi"

        assertEquals(0, terminalTranscriptOffsetAtCell(transcript, columns = 4, row = 0, column = 0))
        assertEquals(4, terminalTranscriptOffsetAtCell(transcript, columns = 4, row = 1, column = 0))
        assertEquals(7, terminalTranscriptOffsetAtCell(transcript, columns = 4, row = 2, column = 0))
        assertEquals(null, terminalTranscriptOffsetAtCell(transcript, columns = 4, row = 0, column = 4))
    }

    @Test
    fun transcriptCellAtOffsetInvertsCellOffsetMapping() {
        val transcript = "abcdEF\nhi"

        assertEquals(TerminalTranscriptCell(0, 0), terminalTranscriptCellAtOffset(transcript, columns = 4, offset = 0))
        assertEquals(TerminalTranscriptCell(1, 0), terminalTranscriptCellAtOffset(transcript, columns = 4, offset = 4))
        assertEquals(TerminalTranscriptCell(2, 0), terminalTranscriptCellAtOffset(transcript, columns = 4, offset = 7))
        assertEquals(null, terminalTranscriptCellAtOffset(transcript, columns = 4, offset = -1))
    }

    @Test
    fun searchTopRowCentersOldMatchesAndKeepsRecentMatchesAtBottom() {
        val transcript = (0 until 20).joinToString("\n") { "row$it" }

        assertEquals(
            -10,
            terminalSearchTopRowForOffset(
                transcript = transcript,
                columns = 20,
                visibleRows = 5,
                activeTranscriptRows = 10,
                offset = transcript.indexOf("row2")
            )
        )
        assertEquals(
            0,
            terminalSearchTopRowForOffset(
                transcript = transcript,
                columns = 20,
                visibleRows = 5,
                activeTranscriptRows = 10,
                offset = transcript.indexOf("row18")
            )
        )
    }

    @Test
    fun urlAtCellFindsWrappedUrlSpan() {
        val transcript = "go https://example.test/path now"

        assertEquals(
            TerminalUrlSpan("https://example.test/path", 3, 28),
            terminalUrlAtCell(transcript, columns = 10, row = 1, column = 2)
        )
        assertEquals(null, terminalUrlAtCell(transcript, columns = 10, row = 0, column = 0))
    }

    @Test
    fun urlAtViewportCellUsesBottomRowsWhenTerminalIsAtLiveBottom() {
        val url = "https://bottom.example/path"
        val transcript = (0 until 20).joinToString("\n") { row ->
            if (row == 18) "open $url" else "row$row"
        }

        assertEquals(
            TerminalUrlSpan(url, transcript.indexOf(url), transcript.indexOf(url) + url.length),
            terminalUrlAtViewportCell(transcript, columns = 80, visibleRows = 5, topRow = 0, row = 3, column = 8)
        )
        assertEquals(null, terminalUrlAtViewportCell(transcript, columns = 80, visibleRows = 5, topRow = 0, row = 0, column = 8))
    }

    @Test
    fun urlAtViewportCellUsesScrolledTopRow() {
        val url = "https://old.example/path"
        val transcript = (0 until 20).joinToString("\n") { row ->
            if (row == 6) "see $url" else "row$row"
        }

        assertEquals(
            TerminalUrlSpan(url, transcript.indexOf(url), transcript.indexOf(url) + url.length),
            terminalUrlAtViewportCell(transcript, columns = 80, visibleRows = 5, topRow = -10, row = 1, column = 6)
        )
        assertEquals(null, terminalUrlAtViewportCell(transcript, columns = 80, visibleRows = 5, topRow = 0, row = 1, column = 6))
    }

    @Test
    fun inputGenerationRejectsRetainedStaleImeConnection() {
        assertEquals(true, terminalInputGenerationAccepted(currentGeneration = 2, capturedGeneration = 2))
        assertEquals(false, terminalInputGenerationAccepted(currentGeneration = 2, capturedGeneration = 1))
    }

    @Test
    fun pasteRearmKeepsActiveImeGeneration() {
        assertEquals(true, terminalPasteKeepsInputGeneration())
    }

    @Test
    fun viewportPointMapsToClampedTerminalCell() {
        assertEquals(2 to 3, terminalViewportCellForPoint(x = 21f, y = 31f, cellWidthPx = 10, cellHeightPx = 10))
        assertEquals(1 to 3, terminalViewportCellForPoint(x = 21f, y = 31f, cellWidthPx = 10, cellHeightPx = 10, leftPaddingPx = 8))
        assertEquals(0 to 0, terminalViewportCellForPoint(x = -2f, y = -8f, cellWidthPx = 10, cellHeightPx = 10))
        assertEquals(5 to 7, terminalViewportCellForPoint(x = 5f, y = 7f, cellWidthPx = 0, cellHeightPx = 0))
    }
}
