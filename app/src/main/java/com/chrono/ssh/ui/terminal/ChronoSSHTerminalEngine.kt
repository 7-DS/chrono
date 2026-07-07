package com.chrono.ssh.ui.terminal

import android.graphics.Color
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.chrono.ssh.core.model.TerminalCursorStyle
import com.chrono.ssh.core.model.TerminalProfile
import com.chrono.ssh.core.service.SshSession
import com.chrono.ssh.core.service.TerminalClipboardPolicy
import com.chrono.ssh.core.service.TerminalEngine
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.util.Base64
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChronoSSHTerminalEngine(
    private val profile: TerminalProfile,
    private val onChanged: () -> Unit,
    private val onSessionDisconnected: (SshSession, Throwable?) -> Unit = { _, _ -> },
    private val applyProfileOnInit: Boolean = true
) : TerminalOutput(), TerminalSessionClient, TerminalEngine {
    private val terminalLock = Any()
    private val transcript = StringBuilder()
    private val transcriptDecoder = TerminalTranscriptDecoder()
    private val transcriptSanitizer = TerminalTranscriptSanitizer()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var session: SshSession? = null
    @Volatile private var disposed = false
    private var terminalColumns = DEFAULT_COLUMNS
    private var terminalRows = DEFAULT_ROWS
    private var copyToClipboard: (String) -> Unit = {}
    private var clipboardTextProvider: () -> String? = { null }
    private var oscEventSink: (TerminalOscEvent) -> Unit = {}
    private val oscHandler = TerminalOscHandler(
        onClipboardSet = { text ->
            copyToClipboard(text)
            oscEventSink(TerminalOscEvent.ClipboardSet(text))
        },
        onHyperlink = { uri -> oscEventSink(TerminalOscEvent.Hyperlink(uri)) },
        onNotification = { title, body -> oscEventSink(TerminalOscEvent.Notification(title, body)) },
        onCwdChanged = { cwd -> oscEventSink(TerminalOscEvent.WorkingDirectory(cwd)) }
    )

    private val emulator = TerminalEmulator(
        this,
        DEFAULT_COLUMNS,
        DEFAULT_ROWS,
        1,
        1,
        profile.scrollbackLines,
        this
    )

    init {
        if (applyProfileOnInit) applyProfile()
    }

    fun emulator(): TerminalEmulator = emulator

    fun <T> withTerminalState(block: (TerminalEmulator) -> T): T = synchronized(terminalLock) {
        block(emulator)
    }

    fun setClipboardHandlers(copyText: (String) -> Unit, pasteText: () -> String?) {
        copyToClipboard = copyText
        clipboardTextProvider = pasteText
    }

    fun setOscEventSink(sink: (TerminalOscEvent) -> Unit) {
        oscEventSink = sink
    }

    override fun load() = Unit

    override fun attach(session: SshSession) {
        if (disposed) return
        if (this.session === session) return
        this.session?.setTerminalOutputSink {}
        this.session = session
        session.setTerminalOutputSink { bytes -> appendIncoming(session, bytes) }
    }

    private fun appendIncoming(source: SshSession, bytes: ByteArray) {
        if (session !== source) return
        appendIncoming(bytes)
    }

    override fun appendIncoming(bytes: ByteArray) {
        if (disposed) return
        if (bytes.isEmpty()) return
        val filtered = oscHandler.process(bytes)
        if (filtered.isEmpty()) return
        synchronized(terminalLock) {
            transcript.append(transcriptSanitizer.sanitize(transcriptDecoder.decode(filtered)))
            if (transcript.length > MAX_TRANSCRIPT_CHARS) {
                transcript.delete(0, transcript.length - MAX_TRANSCRIPT_CHARS)
            }
            emulator.append(filtered, filtered.size)
        }
        onChanged()
    }

    override fun sendInput(input: String) {
        sendBytes(input.toByteArray(StandardCharsets.UTF_8))
    }

    override fun sendBytes(input: ByteArray) {
        if (disposed) return
        val current = session ?: return
        scope.launch {
            runCatching {
                if (session !== current || disposed) return@launch
                current.writeTerminal(input.toString(StandardCharsets.UTF_8))
            }.onFailure { error ->
                detachFailedSession(current, error)
                onChanged()
            }
        }
    }

    fun pasteText(text: String?): Boolean {
        val input = TerminalClipboardPolicy.pasteInput(text, profile.bracketedPaste) ?: return false
        if (disposed || session == null) return false
        sendInput(input)
        return true
    }

    fun pasteFromClipboard(): Boolean = pasteText(clipboardTextProvider())

    override fun resize(columns: Int, rows: Int) {
        if (disposed) return
        terminalColumns = columns.coerceAtLeast(4)
        terminalRows = rows.coerceAtLeast(4)
        synchronized(terminalLock) {
            emulator.resize(terminalColumns, terminalRows, 1, 1)
        }
        val current = session ?: return
        scope.launch {
            runCatching {
                if (session !== current || disposed) return@launch
                current.resizeTerminal(terminalColumns, terminalRows)
            }
                .onFailure { error -> detachFailedSession(current, error) }
        }
        onChanged()
    }

    fun resize(columns: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        if (disposed) return
        terminalColumns = columns.coerceAtLeast(4)
        terminalRows = rows.coerceAtLeast(4)
        synchronized(terminalLock) {
            emulator.resize(terminalColumns, terminalRows, cellWidthPx.coerceAtLeast(1), cellHeightPx.coerceAtLeast(1))
        }
        val current = session ?: return
        scope.launch {
            runCatching {
                if (session !== current || disposed) return@launch
                current.resizeTerminal(terminalColumns, terminalRows)
            }
                .onFailure { error -> detachFailedSession(current, error) }
        }
        onChanged()
    }

    override fun copyTranscript(): String = synchronized(terminalLock) { transcript.toString() }

    fun copyLatestOutputBlock(): String? {
        return TerminalClipboardPolicy.latestOutputBlock(copyTranscript())
    }

    override fun searchTranscript(query: String): List<Int> {
        return transcriptSearchOffsets(copyTranscript(), query)
    }

    override fun detach() {
        session?.setTerminalOutputSink {}
        session = null
    }

    private fun detachFailedSession(failedSession: SshSession, error: Throwable?) {
        if (session !== failedSession) return
        detach()
        onSessionDisconnected(failedSession, error)
    }

    override fun dispose() {
        disposed = true
        detach()
        scope.cancel()
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        sendBytes(data.copyOfRange(offset, offset + count))
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) = Unit

    override fun onCopyTextToClipboard(text: String?) {
        TerminalClipboardPolicy.copyText(text)?.let(copyToClipboard)
    }

    override fun onPasteTextFromClipboard() {
        pasteFromClipboard()
    }

    override fun onBell() = Unit

    override fun onColorsChanged() = Unit

    override fun onTerminalDebug(message: String) = Unit

    override fun onTextChanged(changedSession: TerminalSession) = onChanged()

    override fun onTitleChanged(changedSession: TerminalSession) = Unit

    override fun onSessionFinished(finishedSession: TerminalSession) = Unit

    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        onCopyTextToClipboard(text)
    }

    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        onPasteTextFromClipboard()
    }

    override fun onBell(session: TerminalSession) = Unit

    override fun onColorsChanged(session: TerminalSession) = Unit

    override fun onTerminalCursorStateChange(state: Boolean) = Unit

    private fun applyProfile() {
        synchronized(terminalLock) {
            profile.ansiColorsHex.take(16).forEachIndexed { index, color ->
                emulator.mColors.mCurrentColors[index] = parseColor(color, emulator.mColors.mCurrentColors[index])
            }
            emulator.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND] =
                parseColor(profile.foregroundHex, Color.rgb(232, 237, 248))
            emulator.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_BACKGROUND] =
                parseColor(profile.backgroundHex, Color.rgb(7, 10, 18))
            emulator.mColors.mCurrentColors[com.termux.terminal.TextStyle.COLOR_INDEX_CURSOR] =
                parseColor(profile.cursorHex, Color.rgb(33, 199, 232))
            emulator.setCursorStyle(
                when (profile.cursorStyle) {
                    TerminalCursorStyle.Block -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BLOCK
                    TerminalCursorStyle.Underline -> TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE
                    TerminalCursorStyle.Beam -> TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR
                }
            )
        }
    }

    private fun parseColor(value: String, fallback: Int): Int {
        return runCatching { Color.parseColor(value) }.getOrDefault(fallback)
    }

    private companion object {
        const val DEFAULT_COLUMNS = 96
        const val DEFAULT_ROWS = 32
        const val MAX_TRANSCRIPT_CHARS = 180_000
    }
}

sealed interface TerminalOscEvent {
    data class ClipboardSet(val text: String) : TerminalOscEvent
    data class Hyperlink(val uri: String?) : TerminalOscEvent
    data class Notification(val title: String, val body: String) : TerminalOscEvent
    data class WorkingDirectory(val cwd: String) : TerminalOscEvent
}

internal class TerminalOscHandler(
    private val onClipboardSet: (String) -> Unit = {},
    private val onHyperlink: (String?) -> Unit = {},
    private val onNotification: (String, String) -> Unit = { _, _ -> },
    private val onCwdChanged: (String) -> Unit = {}
) {
    private var state = OscState.Normal
    private val prefix = ByteCollector()
    private val payload = ByteCollector()
    private var oscNumber = 0

    fun process(input: ByteArray): ByteArray {
        val output = ByteCollector(input.size)
        input.forEach { byte ->
            val value = byte.toInt() and 0xff
            when (state) {
                OscState.Normal -> {
                    if (value == ESC) {
                        prefix.reset()
                        prefix.write(value)
                        state = OscState.Escape
                    } else {
                        output.write(value)
                    }
                }
                OscState.Escape -> {
                    prefix.write(value)
                    if (value == ']'.code) {
                        oscNumber = 0
                        state = OscState.Number
                    } else {
                        output.write(prefix)
                        prefix.reset()
                        state = OscState.Normal
                    }
                }
                OscState.Number -> {
                    prefix.write(value)
                    when {
                        value in '0'.code..'9'.code -> {
                            oscNumber = oscNumber * 10 + (value - '0'.code)
                        }
                        value == ';'.code && oscNumber in HandledOsc -> {
                            prefix.reset()
                            payload.reset()
                            state = OscState.Payload
                        }
                        value == ';'.code -> {
                            output.write(prefix)
                            prefix.reset()
                            state = OscState.Passthrough
                        }
                        else -> {
                            output.write(prefix)
                            prefix.reset()
                            state = OscState.Normal
                        }
                    }
                }
                OscState.Payload -> {
                    when {
                        value == BEL -> {
                            dispatch()
                            state = OscState.Normal
                        }
                        value == ESC -> state = OscState.PayloadEscape
                        payload.size >= MaxPayloadBytes -> {
                            output.write(prefix)
                            output.write(payload)
                            output.write(value)
                            prefix.reset()
                            payload.reset()
                            state = OscState.Normal
                        }
                        else -> payload.write(value)
                    }
                }
                OscState.PayloadEscape -> {
                    if (value == '\\'.code) {
                        dispatch()
                        state = OscState.Normal
                    } else {
                        output.write(prefix)
                        output.write(payload)
                        output.write(ESC)
                        output.write(value)
                        prefix.reset()
                        payload.reset()
                        state = OscState.Normal
                    }
                }
                OscState.Passthrough -> {
                    output.write(value)
                    if (value == BEL) state = OscState.Normal
                    if (value == ESC) state = OscState.PassthroughEscape
                }
                OscState.PassthroughEscape -> {
                    output.write(value)
                    state = if (value == '\\'.code) OscState.Normal else OscState.Passthrough
                }
            }
        }
        return output.toByteArray()
    }

    private fun dispatch() {
        val text = payload.toStringUtf8()
        payload.reset()
        when (oscNumber) {
            52 -> dispatchClipboard(text)
            7 -> if (text.isNotBlank()) onCwdChanged(text)
            8 -> onHyperlink(text.substringAfter(';', "").ifBlank { null })
            9 -> if (text.isNotBlank()) onNotification("", text)
            777 -> {
                val parts = text.split(';', limit = 3)
                if (parts.size == 3 && parts[0] == "notify") onNotification(parts[1], parts[2])
            }
        }
    }

    private fun dispatchClipboard(text: String) {
        val encoded = text.substringAfter(';', "")
        if (encoded.isBlank()) return
        val decoded = runCatching { Base64.getMimeDecoder().decode(encoded) }.getOrNull() ?: return
        onClipboardSet(String(decoded, StandardCharsets.UTF_8))
    }

    private enum class OscState {
        Normal,
        Escape,
        Number,
        Payload,
        PayloadEscape,
        Passthrough,
        PassthroughEscape
    }

    private companion object {
        const val ESC = 0x1b
        const val BEL = 0x07
        const val MaxPayloadBytes = 1_048_576
        val HandledOsc = setOf(7, 8, 9, 52, 777)
    }
}

private class ByteCollector(initialCapacity: Int = 256) {
    private var buffer = ByteArray(initialCapacity.coerceAtLeast(1))
    var size = 0
        private set

    fun write(value: Int) {
        ensure(size + 1)
        buffer[size++] = value.toByte()
    }

    fun write(other: ByteCollector) {
        ensure(size + other.size)
        System.arraycopy(other.buffer, 0, buffer, size, other.size)
        size += other.size
    }

    fun reset() {
        size = 0
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)

    fun toStringUtf8(): String = String(buffer, 0, size, StandardCharsets.UTF_8)

    private fun ensure(required: Int) {
        if (required <= buffer.size) return
        var nextSize = buffer.size * 2
        while (nextSize < required) nextSize *= 2
        buffer = buffer.copyOf(nextSize)
    }
}

internal class TerminalTranscriptDecoder {
    private val decoder = StandardCharsets.UTF_8
        .newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)
    private var pending = ByteArray(0)

    fun decode(bytes: ByteArray): String {
        val inputBytes = if (pending.isEmpty()) bytes else pending + bytes
        pending = ByteArray(0)
        val input = ByteBuffer.wrap(inputBytes)
        val output = CharBuffer.allocate((inputBytes.size * 2).coerceAtLeast(8))
        val text = StringBuilder()
        while (true) {
            val result = decoder.decode(input, output, false)
            output.flip()
            text.append(output)
            output.clear()
            if (result.isOverflow) continue
            break
        }
        if (input.hasRemaining()) {
            pending = ByteArray(input.remaining()).also(input::get)
        }
        return text.toString()
    }
}

internal fun sanitizeTerminalTranscriptText(text: String): String {
    return TerminalTranscriptSanitizer().sanitize(text)
}

internal class TerminalTranscriptSanitizer {
    private var state = State.Normal

    fun sanitize(text: String): String {
    val cleaned = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val char = text[index]
            when (state) {
                State.Normal -> {
                    if (char == '\u001B') {
                        state = State.Escape
                    } else if (char == '\r' || char == '\n' || char == '\t' || !char.isISOControl()) {
                        cleaned.append(char)
                    }
                }
                State.Escape -> {
                    state = when (char) {
                        '[' -> State.Csi
                        ']' -> State.Osc
                        else -> State.Normal
                    }
                }
                State.Csi -> {
                    if (char in '@'..'~') state = State.Normal
                }
                State.Osc -> {
                    if (char == '\u0007') {
                        state = State.Normal
                    } else if (char == '\u001B') {
                        state = State.OscEscape
                    }
                }
                State.OscEscape -> {
                    state = if (char == '\\') State.Normal else State.Osc
                }
            }
            index += 1
        }
        return cleaned.toString()
    }

    private enum class State {
        Normal,
        Escape,
        Csi,
        Osc,
        OscEscape
    }
}

internal fun transcriptSearchOffsets(transcript: String, query: String): List<Int> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val matches = mutableListOf<Int>()
    var index = transcript.indexOf(needle, ignoreCase = true)
    while (index >= 0) {
        matches += index
        index = transcript.indexOf(needle, startIndex = index + needle.length, ignoreCase = true)
    }
    return matches
}

internal enum class TerminalSearchDirection {
    Next,
    Previous
}

internal data class TerminalSearchSelection(
    val index: Int,
    val offset: Int,
    val total: Int
)

internal data class TerminalTranscriptCell(
    val row: Int,
    val column: Int
)

internal fun terminalSearchNavigate(
    offsets: List<Int>,
    selectedIndex: Int?,
    direction: TerminalSearchDirection
): TerminalSearchSelection? {
    if (offsets.isEmpty()) return null
    val current = selectedIndex?.takeIf { it in offsets.indices } ?: when (direction) {
        TerminalSearchDirection.Next -> -1
        TerminalSearchDirection.Previous -> 0
    }
    val nextIndex = when (direction) {
        TerminalSearchDirection.Next -> (current + 1).floorMod(offsets.size)
        TerminalSearchDirection.Previous -> (current - 1).floorMod(offsets.size)
    }
    return TerminalSearchSelection(nextIndex, offsets[nextIndex], offsets.size)
}

internal data class TerminalUrlSpan(
    val url: String,
    val start: Int,
    val end: Int
)

internal fun terminalUrlSpans(transcript: String): List<TerminalUrlSpan> {
    return TerminalUrlPattern.findAll(transcript).mapNotNull { match ->
        val raw = match.value.trimEnd('.', ',', ';', ':', ')', ']', '}')
        if (raw == "http://" || raw == "https://") return@mapNotNull null
        TerminalUrlSpan(raw, match.range.first, match.range.first + raw.length)
    }.toList()
}

internal fun terminalLatestUrl(transcript: String): String? = terminalUrlSpans(transcript).lastOrNull()?.url

internal fun terminalUrlAtViewportCell(
    transcript: String,
    columns: Int,
    visibleRows: Int,
    topRow: Int,
    row: Int,
    column: Int
): TerminalUrlSpan? {
    if (visibleRows <= 0 || row < 0 || row >= visibleRows) return null
    val totalRows = terminalTranscriptCellAtOffset(transcript, columns, transcript.length)?.row?.plus(1) ?: return null
    val bottomStartRow = (totalRows - visibleRows).coerceAtLeast(0)
    val viewportStartRow = (bottomStartRow + topRow.coerceAtMost(0)).coerceAtLeast(0)
    return terminalUrlAtCell(transcript, columns, viewportStartRow + row, column)
}

internal fun terminalTranscriptOffsetAtCell(
    transcript: String,
    columns: Int,
    row: Int,
    column: Int
): Int? {
    if (columns <= 0 || row < 0 || column < 0 || column >= columns) return null
    var visualRow = 0
    var visualColumn = 0
    transcript.forEachIndexed { index, char ->
        if (visualRow == row && visualColumn == column) return index
        if (char == '\n') {
            visualRow += 1
            visualColumn = 0
        } else {
            visualColumn += 1
            if (visualColumn >= columns) {
                visualRow += 1
                visualColumn = 0
            }
        }
    }
    return if (visualRow == row && visualColumn == column) transcript.length else null
}

internal fun terminalTranscriptCellAtOffset(
    transcript: String,
    columns: Int,
    offset: Int
): TerminalTranscriptCell? {
    if (columns <= 0 || offset < 0 || offset > transcript.length) return null
    var visualRow = 0
    var visualColumn = 0
    transcript.forEachIndexed { index, char ->
        if (index == offset) return TerminalTranscriptCell(visualRow, visualColumn)
        if (char == '\n') {
            visualRow += 1
            visualColumn = 0
        } else {
            visualColumn += 1
            if (visualColumn >= columns) {
                visualRow += 1
                visualColumn = 0
            }
        }
    }
    return TerminalTranscriptCell(visualRow, visualColumn).takeIf { offset == transcript.length }
}

internal fun terminalSearchTopRowForOffset(
    transcript: String,
    columns: Int,
    visibleRows: Int,
    activeTranscriptRows: Int,
    offset: Int
): Int {
    if (columns <= 0 || visibleRows <= 0 || activeTranscriptRows <= 0) return 0
    val cell = terminalTranscriptCellAtOffset(transcript, columns, offset) ?: return 0
    val totalRows = terminalTranscriptCellAtOffset(transcript, columns, transcript.length)?.row?.plus(1) ?: return 0
    val bottomStartRow = (totalRows - visibleRows).coerceAtLeast(0)
    val desiredStartRow = (cell.row - visibleRows / 2).coerceAtLeast(0)
    return (desiredStartRow - bottomStartRow).coerceIn(-activeTranscriptRows, 0)
}

internal fun terminalUrlAtCell(
    transcript: String,
    columns: Int,
    row: Int,
    column: Int
): TerminalUrlSpan? {
    val offset = terminalTranscriptOffsetAtCell(transcript, columns, row, column) ?: return null
    return terminalUrlSpans(transcript).firstOrNull { offset in it.start until it.end }
}

private val TerminalUrlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
