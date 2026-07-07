package com.chrono.ssh.ui.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.chrono.ssh.core.service.TerminalImeInputReducer
import com.chrono.ssh.core.service.TerminalInputRouter
import com.chrono.ssh.core.service.TerminalClipboardPolicy
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlin.math.max

class ChronoSSHTerminalView(context: Context) : View(context) {
    private var engine: ChronoSSHTerminalEngine? = null
    private var onInput: (String) -> Unit = {}
    private var inputTransform: (String) -> String = { it }
    private var bracketedPaste = true
    private var onBeforePaste: () -> Unit = {}
    private var onKeyboardRequestChanged: (Boolean) -> Unit = {}
    private var onUrlTap: (String) -> Unit = {}
    private var onTextSizeChanged: (Int) -> Unit = {}
    private var keyboardRequested = false
    private var textSizePx = DEFAULT_TEXT_SIZE_PX
    private var terminalTypeface = Typeface.MONOSPACE
    private var terminalBackground = Color.rgb(7, 10, 18)
    private var contentLeftPaddingPx = 0
    private var contentRightPaddingPx = 0
    private var directionalSwipeEnabled = false
    private var renderer = terminalRendererOrFallback(textSizePx, terminalTypeface)
    private var topRow = 0
    private var scrollRemainder = 0f
    private var directionalSwipeStartX = 0f
    private var directionalSwipeStartY = 0f
    private var directionalSwipeSequence: String? = null
    private var directionalSwipeRepeat: Runnable? = null
    private var directionalSwipeLastMoveAtMillis = 0L
    private var composingText = ""
    private var inputArmed = false
    private var inputGeneration = 0

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                sendTerminalMouseEvent(e, TerminalEmulator.MOUSE_LEFT_BUTTON)?.let { handled ->
                    if (handled) performClick()
                    return handled
                }
                tappedUrl(e)?.let { url ->
                    onUrlTap(url)
                    performClick()
                    return true
                }
                requestFocus()
                showKeyboard()
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val current = engine ?: return false
                val mouseButton = when {
                    distanceY > 0f -> TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
                    distanceY < 0f -> TerminalEmulator.MOUSE_WHEELUP_BUTTON
                    else -> null
                }
                mouseButton?.let { button ->
                    sendTerminalMouseEvent(e2, button)?.let { handled -> return handled }
                }
                if (current.withTerminalState { it.isAlternateBufferActive }) return false
                scrollRemainder += distanceY
                val rows = (scrollRemainder / renderer.fontLineSpacing.coerceAtLeast(1)).toInt()
                if (rows == 0) return true
                scrollRemainder -= rows * renderer.fontLineSpacing
                val minTop = -current.withTerminalState { it.screen.activeTranscriptRows }
                topRow = (topRow + rows).coerceIn(minTop, 0)
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setTerminalTextSizePx((textSizePx * detector.scaleFactor).toInt())
                onTextSizeChanged(textSizePx)
                return true
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    fun bind(engine: ChronoSSHTerminalEngine) {
        if (this.engine === engine) return
        resetInputState()
        inputGeneration += 1
        inputArmed = false
        topRow = 0
        scrollRemainder = 0f
        this.engine = engine
        this.onInput = engine::sendInput
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun setInputTransform(transform: (String) -> String) {
        inputTransform = transform
    }

    fun setBracketedPaste(enabled: Boolean) {
        bracketedPaste = enabled
    }

    fun setOnBeforePaste(listener: () -> Unit) {
        onBeforePaste = listener
    }

    fun setOnKeyboardRequestChanged(listener: (Boolean) -> Unit) {
        onKeyboardRequestChanged = listener
    }

    fun setOnUrlTap(listener: (String) -> Unit) {
        onUrlTap = listener
    }

    fun setOnTextSizeChanged(listener: (Int) -> Unit) {
        onTextSizeChanged = listener
    }

    fun syncKeyboardRequestState() {
        onKeyboardRequestChanged(keyboardRequested)
    }

    fun requestKeyboard() {
        requestFocus()
        showKeyboard()
    }

    fun reactivateInputAfterResume() {
        post {
            clearFocus()
            requestFocus()
            armInputForFocusedTerminal(showKeyboard = keyboardRequested)
        }
        postDelayed({
            if (hasFocus()) armInputForFocusedTerminal(showKeyboard = keyboardRequested)
        }, 160L)
    }

    fun rearmInputAfterTerminalAction(showKeyboard: Boolean = false) {
        resetInputState()
        inputGeneration += 1
        inputArmed = true
        if (!hasFocus()) requestFocus()
        reactivateInputConnection(showKeyboard)
        postDelayed({
            if (hasFocus()) reactivateInputConnection(showKeyboard)
        }, 80L)
    }

    fun clearKeyboardRequest() {
        resetInputState()
        inputArmed = false
        hideKeyboard()
    }

    fun noteKeyboardHidden() {
        if (keyboardRequested) {
            keyboardRequested = false
            onKeyboardRequestChanged(false)
        }
    }

    fun isKeyboardRequested(): Boolean = keyboardRequested

    fun onTerminalUpdated() {
        clampTopRow()
        postInvalidateOnAnimation()
    }

    fun scrollToTranscriptOffset(offset: Int) {
        val current = engine ?: return
        val cellWidthPx = max(1, renderer.fontWidth.toInt())
        val cellHeightPx = max(1, renderer.fontLineSpacing)
        val columns = max(MIN_TERMINAL_COLUMNS, width / cellWidthPx)
        val visibleRows = max(4, height / cellHeightPx)
        topRow = terminalSearchTopRowForOffset(
            transcript = current.copyTranscript(),
            columns = columns,
            visibleRows = visibleRows,
            activeTranscriptRows = current.withTerminalState { it.screen.activeTranscriptRows },
            offset = offset
        )
        clampTopRow()
        postInvalidateOnAnimation()
    }

    fun setTerminalTextSizePx(size: Int) {
        val safeSize = size.coerceIn(16, 54)
        if (safeSize == textSizePx) return
        textSizePx = safeSize
        rebuildRenderer()
    }

    fun setTerminalTypeface(typeface: Typeface) {
        if (terminalTypeface == typeface) return
        terminalTypeface = typeface
        rebuildRenderer()
    }

    fun setTerminalBackground(hex: String) {
        terminalBackground = runCatching { Color.parseColor(hex) }.getOrDefault(Color.rgb(7, 10, 18))
        postInvalidateOnAnimation()
    }

    fun setContentLeftPaddingPx(paddingPx: Int) {
        val safePadding = paddingPx.coerceAtLeast(0)
        if (safePadding == contentLeftPaddingPx) return
        contentLeftPaddingPx = safePadding
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun setContentRightPaddingPx(paddingPx: Int) {
        val safePadding = paddingPx.coerceAtLeast(0)
        if (safePadding == contentRightPaddingPx) return
        contentRightPaddingPx = safePadding
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun setDirectionalSwipeEnabled(enabled: Boolean) {
        directionalSwipeEnabled = enabled
        scrollRemainder = 0f
        if (!enabled) stopDirectionalSwipeRepeat()
    }

    private fun rebuildRenderer() {
        resetInputState()
        renderer = terminalRendererOrFallback(textSizePx, terminalTypeface)
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize()
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            armInputForFocusedTerminal(showKeyboard = keyboardRequested)
        } else {
            resetInputState()
            inputArmed = false
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (hasFocus()) armInputForFocusedTerminal(showKeyboard = keyboardRequested)
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            if (!hasFocus()) requestFocus()
            armInputForFocusedTerminal(showKeyboard = keyboardRequested)
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(terminalBackground)
        runCatching {
            engine?.withTerminalState { emulator ->
                val checkpoint = canvas.save()
                try {
                    canvas.translate(contentLeftPaddingPx.toFloat(), 0f)
                    renderer.render(emulator, canvas, topRow, -1, -1, -1, -1)
                } finally {
                    canvas.restoreToCount(checkpoint)
                }
            }
        }.onFailure {
            if (terminalTypeface != Typeface.MONOSPACE) {
                terminalTypeface = Typeface.MONOSPACE
                renderer = terminalRendererOrFallback(textSizePx, terminalTypeface)
                postInvalidateOnAnimation()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        if (directionalSwipeEnabled && !scaleDetector.isInProgress) {
            return handleDirectionalSwipeTouch(event) || scaleHandled
        }
        val gestureHandled = gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            scrollRemainder = 0f
            stopDirectionalSwipeRepeat()
        }
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK &&
            keyboardRequested
        ) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                clearKeyboardRequest()
                clearFocus()
            }
            return true
        }
        return super.dispatchKeyEventPreIme(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        TerminalInputRouter.sequenceForAndroidKeyEvent(
            keyCode = keyCode,
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed
        )?.let { sequence ->
            if (acceptTerminalInput()) onInput(inputTransform(sequence))
            return true
        }
        TerminalInputRouter.printableForAndroidKeyEvent(
            unicodeChar = event.getUnicodeChar(event.metaState),
            ctrl = event.isCtrlPressed,
            alt = event.isAltPressed,
            shift = event.isShiftPressed
        )?.let { printable ->
            if (acceptTerminalInput()) onInput(inputTransform(printable))
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        inputArmed = true
        val generation = inputGeneration
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or
            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        return object : BaseInputConnection(this, false) {
            private fun rejectStaleInput(): Boolean {
                if (acceptTerminalInput(generation)) return false
                rearmInputAfterTerminalAction(showKeyboard = keyboardRequested)
                return true
            }

            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (rejectStaleInput()) return true
                val incoming = text?.toString().orEmpty()
                if (incoming.length > 1 || incoming.any { it == '\n' || it == '\r' }) {
                    onBeforePaste()
                    resetInputState()
                    TerminalClipboardPolicy.pasteInput(incoming, bracketedPaste)?.let(onInput)
                    rearmInputAfterPaste(showKeyboard = keyboardRequested)
                    return true
                }
                val edit = TerminalImeInputReducer.commit(composingText, incoming)
                composingText = edit.composingText
                edit.output.takeIf { it.isNotEmpty() }?.let { onInput(inputTransform(it)) }
                return true
            }

            override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (rejectStaleInput()) return true
                val edit = TerminalImeInputReducer.setComposing(composingText, text?.toString().orEmpty())
                composingText = edit.composingText
                edit.output.takeIf { it.isNotEmpty() }?.let { onInput(inputTransform(it)) }
                return true
            }

            override fun finishComposingText(): Boolean {
                if (!terminalInputGenerationAccepted(inputGeneration, generation)) return true
                resetInputState()
                return true
            }

            override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence = ""

            override fun getTextAfterCursor(length: Int, flags: Int): CharSequence = ""

            override fun getSelectedText(flags: Int): CharSequence = ""

            override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
                return ExtractedText().apply {
                    text = ""
                    partialEndOffset = 0
                    partialStartOffset = 0
                    selectionStart = 0
                    selectionEnd = 0
                    startOffset = 0
                }
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (rejectStaleInput()) return true
                if (composingText.isNotEmpty()) {
                    composingText = composingText.dropLast(beforeLength.coerceAtLeast(1))
                    return true
                }
                onInput(inputTransform("\u007F"))
                return true
            }

            override fun sendKeyEvent(event: android.view.KeyEvent): Boolean {
                if (event.action == android.view.KeyEvent.ACTION_DOWN) {
                    TerminalInputRouter.sequenceForAndroidKeyEvent(
                        keyCode = event.keyCode,
                        ctrl = event.isCtrlPressed,
                        alt = event.isAltPressed,
                        shift = event.isShiftPressed
                    )?.let { sequence ->
                        if (rejectStaleInput()) return true
                        onInput(inputTransform(sequence))
                        return true
                    }
                    val printable = TerminalInputRouter.printableForAndroidKeyEvent(
                        unicodeChar = event.getUnicodeChar(event.metaState),
                        ctrl = event.isCtrlPressed,
                        alt = event.isAltPressed,
                        shift = event.isShiftPressed
                    )
                    if (printable != null) {
                        if (rejectStaleInput()) return true
                        onInput(inputTransform(printable))
                        return true
                    }
                }
                return super.sendKeyEvent(event)
            }

            override fun performContextMenuAction(id: Int): Boolean {
                return when (id) {
                    android.R.id.paste,
                    android.R.id.pasteAsPlainText -> {
                        if (!acceptTerminalInput()) {
                            rearmInputAfterTerminalAction(showKeyboard = keyboardRequested)
                            return true
                        }
                        onBeforePaste()
                        engine?.pasteFromClipboard()
                        rearmInputAfterPaste(showKeyboard = keyboardRequested)
                        true
                    }
                    else -> super.performContextMenuAction(id)
                }
            }
        }
    }

    private fun updateTerminalSize() {
        val current = engine ?: return
        if (width <= 0 || height <= 0) return
        val cellWidthPx = max(1, renderer.fontWidth.toInt())
        val cellHeightPx = max(1, renderer.fontLineSpacing)
        val contentWidth = (width - contentLeftPaddingPx - contentRightPaddingPx).coerceAtLeast(cellWidthPx)
        current.resize(max(MIN_TERMINAL_COLUMNS, contentWidth / cellWidthPx), max(4, height / cellHeightPx), cellWidthPx, cellHeightPx)
        clampTopRow()
    }

    private fun clampTopRow() {
        val current = engine ?: return
        topRow = current.withTerminalState { emulator ->
            topRow.coerceIn(-emulator.screen.activeTranscriptRows, 0)
        }
    }

    private fun emulator(): TerminalEmulator? = engine?.emulator()

    private fun tappedUrl(event: MotionEvent): String? {
        val current = engine ?: return null
        val cellWidthPx = max(1, renderer.fontWidth.toInt())
        val cellHeightPx = max(1, renderer.fontLineSpacing)
        val (column, row) = terminalViewportCellForPoint(
            x = event.x,
            y = event.y,
            cellWidthPx = cellWidthPx,
            cellHeightPx = cellHeightPx,
            leftPaddingPx = contentLeftPaddingPx
        )
        val columns = max(MIN_TERMINAL_COLUMNS, (width - contentLeftPaddingPx - contentRightPaddingPx).coerceAtLeast(cellWidthPx) / cellWidthPx)
        val visibleRows = max(4, height / cellHeightPx)
        return terminalUrlAtViewportCell(
            transcript = current.copyTranscript(),
            columns = columns,
            visibleRows = visibleRows,
            topRow = topRow,
            row = row,
            column = column
        )?.url
    }

    private fun sendTerminalMouseEvent(event: MotionEvent, button: Int): Boolean? {
        val current = engine ?: return null
        val mouseTracking = current.withTerminalState { it.isMouseTrackingActive }
        if (!mouseTracking) return null
        val (column, row) = terminalViewportCellForPoint(
            x = event.x,
            y = event.y,
            cellWidthPx = max(1, renderer.fontWidth.toInt()),
            cellHeightPx = max(1, renderer.fontLineSpacing),
            leftPaddingPx = contentLeftPaddingPx
        )
        current.withTerminalState { emulator ->
            emulator.sendMouseEvent(
                button,
                column + 1,
                row + 1,
                true,
                event.metaState.hasMeta(KeyEvent.META_CTRL_ON),
                event.metaState.hasMeta(KeyEvent.META_ALT_ON),
                event.metaState.hasMeta(KeyEvent.META_SHIFT_ON)
            )
        }
        return true
    }

    private fun handleDirectionalSwipeTouch(event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                directionalSwipeStartX = event.x
                directionalSwipeStartY = event.y
                stopDirectionalSwipeRepeat()
                true
            }
            MotionEvent.ACTION_MOVE -> sendDirectionalSwipe(event.x, event.y)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                stopDirectionalSwipeRepeat()
                true
            }
            else -> true
        }
    }

    private fun sendDirectionalSwipe(currentX: Float, currentY: Float): Boolean {
        if (directionalSwipeSequence != null) {
            directionalSwipeLastMoveAtMillis = SystemClock.uptimeMillis()
            return true
        }
        val deltaX = currentX - directionalSwipeStartX
        val deltaY = currentY - directionalSwipeStartY
        val absX = kotlin.math.abs(deltaX)
        val absY = kotlin.math.abs(deltaY)
        val threshold = (renderer.fontLineSpacing * 2.5f).coerceAtLeast(72f)
        if (absX < threshold && absY < threshold) return true
        val horizontal = absX > absY
        if (if (horizontal) absX < absY * 1.25f else absY < absX * 1.25f) return true
        val sequence = when {
            horizontal && deltaX < 0f -> "\u001B[D"
            horizontal -> "\u001B[C"
            deltaY < 0f -> "\u001B[A"
            else -> "\u001B[B"
        }
        directionalSwipeSequence = sequence
        directionalSwipeLastMoveAtMillis = SystemClock.uptimeMillis()
        sendDirectionalSwipeSequence(sequence)
        startDirectionalSwipeRepeat(sequence)
        return true
    }

    private fun sendDirectionalSwipeSequence(sequence: String) {
        if (acceptTerminalInput()) onInput(inputTransform(sequence))
    }

    private fun startDirectionalSwipeRepeat(sequence: String) {
        directionalSwipeRepeat?.let(::removeCallbacks)
        directionalSwipeRepeat = null
        val repeat = object : Runnable {
            override fun run() {
                if (directionalSwipeSequence != sequence) return
                val waitForHold = DIRECTIONAL_SWIPE_HOLD_REPEAT_DELAY_MS - (SystemClock.uptimeMillis() - directionalSwipeLastMoveAtMillis)
                if (waitForHold > 0L) {
                    postDelayed(this, waitForHold.coerceAtMost(DIRECTIONAL_SWIPE_HOLD_REPEAT_DELAY_MS))
                    return
                }
                sendDirectionalSwipeSequence(sequence)
                postDelayed(this, DIRECTIONAL_SWIPE_REPEAT_INTERVAL_MS)
            }
        }
        directionalSwipeRepeat = repeat
        postDelayed(repeat, DIRECTIONAL_SWIPE_HOLD_REPEAT_DELAY_MS)
    }

    private fun stopDirectionalSwipeRepeat() {
        directionalSwipeRepeat?.let(::removeCallbacks)
        directionalSwipeRepeat = null
        directionalSwipeSequence = null
        directionalSwipeLastMoveAtMillis = 0L
    }

    private fun showKeyboard() {
        inputArmed = true
        if (!keyboardRequested) {
            keyboardRequested = true
            onKeyboardRequestChanged(true)
        }
        reactivateInputConnection(showKeyboard = true)
    }

    private fun hideKeyboard() {
        if (keyboardRequested) {
            keyboardRequested = false
            onKeyboardRequestChanged(false)
        }
        resetInputState()
        inputArmed = false
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    private fun reactivateInputConnection(showKeyboard: Boolean) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        post {
            imm?.restartInput(this)
            if (showKeyboard) imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun rearmInputAfterPaste(showKeyboard: Boolean) {
        resetInputState()
        inputArmed = true
        if (!hasFocus()) requestFocus()
        reactivateInputConnection(showKeyboard)
    }

    private fun armInputForFocusedTerminal(showKeyboard: Boolean) {
        if (!isAttachedToWindow || visibility != VISIBLE || windowVisibility != VISIBLE) return
        inputArmed = true
        reactivateInputConnection(showKeyboard)
    }

    private fun acceptTerminalInput(): Boolean {
        return acceptTerminalInput(inputGeneration)
    }

    private fun acceptTerminalInput(generation: Int): Boolean {
        if (!terminalInputGenerationAccepted(inputGeneration, generation)) return false
        if (!isAttachedToWindow || visibility != VISIBLE || windowVisibility != VISIBLE) return false
        if (inputArmed) return true
        inputArmed = true
        return true
    }

    private fun resetInputState() {
        composingText = ""
    }

    private companion object {
        const val DEFAULT_TEXT_SIZE_PX = 26
        const val MIN_TERMINAL_COLUMNS = 80
        const val DIRECTIONAL_SWIPE_HOLD_REPEAT_DELAY_MS = 650L
        const val DIRECTIONAL_SWIPE_REPEAT_INTERVAL_MS = 140L
    }
}

private fun terminalRendererOrFallback(textSizePx: Int, typeface: Typeface): TerminalRenderer {
    return runCatching { TerminalRenderer(textSizePx, typeface) }
        .getOrElse { TerminalRenderer(textSizePx, Typeface.MONOSPACE) }
}

internal fun terminalInputGenerationAccepted(currentGeneration: Int, capturedGeneration: Int): Boolean {
    return currentGeneration == capturedGeneration
}

internal fun terminalPasteKeepsInputGeneration(): Boolean = true

internal fun terminalViewportCellForPoint(
    x: Float,
    y: Float,
    cellWidthPx: Int,
    cellHeightPx: Int,
    leftPaddingPx: Int = 0
): Pair<Int, Int> {
    return ((x - leftPaddingPx.coerceAtLeast(0)) / cellWidthPx.coerceAtLeast(1)).toInt().coerceAtLeast(0) to
        (y / cellHeightPx.coerceAtLeast(1)).toInt().coerceAtLeast(0)
}

private fun Int.hasMeta(mask: Int): Boolean = this and mask != 0
