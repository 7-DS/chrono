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
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import com.chrono.ssh.core.service.TerminalImeInputReducer
import com.chrono.ssh.core.service.TerminalInputRouter
import com.chrono.ssh.core.service.TerminalClipboardPolicy
import com.termux.terminal.TerminalEmulator
import com.termux.view.TerminalRenderer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * ChronoSSH terminal surface.
 *
 * This is a raw [View] (not Termux's TerminalView) because the ChronoSSH engine drives its
 * own [TerminalEmulator] from the remote SSH byte stream rather than a local process. It owns:
 *  - rendering (via Termux's [TerminalRenderer], off the Compose recomposition path — see
 *    [ChronoSSHTerminalEngine.setOnRender]);
 *  - kinetic transcript scrolling with fling ([OverScroller] + [VelocityTracker]);
 *  - alternate-buffer (tmux/grok/less) scroll routing to mouse-wheel or arrow-key sequences;
 *  - the soft-keyboard / IME lifecycle;
 *  - pinch-zoom, URL tap, directional-swipe input, modifier-aware key routing, paste.
 *
 * History: the previous implementation had three device-confirmed bugs that this rewrite
 * targets directly — (1) scrolling in tmux/grok did nothing or was jerky because there was no
 * Scroller/fling and the alt-buffer path bailed out; (2) the soft keyboard could end up
 * "hidden for good" due to fragile show-retry + input-generation gating; (3) text looked
 * "squished" while the keyboard animated in (that half is fixed in TerminalScreen's inset
 * handling, not here). See memory: terminal-view-rewrite.
 */
class ChronoSSHTerminalView(context: Context) : View(context) {
    private var engine: ChronoSSHTerminalEngine? = null
    private var onInput: (String) -> Unit = {}
    private var inputTransform: (String) -> String = { it }
    private var bracketedPaste = true
    private var onBeforePaste: () -> Unit = {}
    private var onKeyboardRequestChanged: (Boolean) -> Unit = {}
    private var onUrlTap: (String) -> Unit = {}
    private var onTextSizeChanged: (Int) -> Unit = {}
    // Whether this session is attached to a tmux the app itself launched (definitive), and
    // whether tmux was seen running on the host at connect time (heuristic). Copy-mode scroll
    // is offered when either holds AND we are actually in the alternate buffer at scroll time,
    // so a plain shell prompt (normal buffer) can never receive a stray copy-mode prefix.
    private var tmuxLaunchedByApp = false
    private var tmuxRunningOnHost = false
    // Invoked when a scroll gesture in the alt buffer with mouse tracking off and tmux
    // copy-mode is available. lines: signed scroll amount (negative = up into history).
    // enterCopyMode: true on the first gesture after a quiet gap, so the host prepends the
    // tmux prefix + '[' to switch the pane into copy-mode before navigating.
    private var onRequestTmuxCopyScroll: (lines: Int, enterCopyMode: Boolean) -> Unit = { _, _ -> }
    // Emitted when a scroll gesture in the alt buffer can't be routed anywhere safe (alt
    // buffer, no mouse tracking, not a known tmux) so the UI can show a one-shot hint
    // instead of silently corrupting the remote input line with synthesized arrow keys.
    private var onAltScrollUnavailable: () -> Unit = {}
    private var keyboardRequested = false
    private var textSizePx = DEFAULT_TEXT_SIZE_PX
    private var terminalZoom = 1f
    private var rendererTextSizePx = terminalZoomedTextSizePx(textSizePx, terminalZoom)
    private var terminalTypeface = Typeface.MONOSPACE
    private var terminalBackground = Color.rgb(7, 10, 18)
    private var contentLeftPaddingPx = 0
    private var contentRightPaddingPx = 0
    private var directionalSwipeEnabled = false
    private var renderer = terminalRendererOrFallback(rendererTextSizePx, terminalTypeface)
    private var topRow = 0
    private var directionalSwipeStartX = 0f
    private var directionalSwipeStartY = 0f
    private var directionalSwipeSequence: String? = null
    private var directionalSwipeRepeat: Runnable? = null
    private var directionalSwipeLastMoveAtMillis = 0L
    private var composingText = ""
    private var inputArmed = false
    private var inputGeneration = 0
    private var lastAppliedColumns = -1
    private var lastAppliedRows = -1
    private var lastAppliedCellWidthPx = -1
    private var lastAppliedCellHeightPx = -1
    private var pendingResize: Runnable? = null
    // Uptime (ms) of the last applied PTY reflow, for the leading-edge resize debounce.
    private var lastResizeApplyUptimeMs = 0L

    // --- scrolling state -----------------------------------------------------------------
    // Kinetic transcript scrolling for the normal buffer. topRow is measured in rows above
    // the bottom (0 = at the prompt, negative = scrolled up into scrollback). The scroller
    // works in whole rows: fling/drag produce a target row that we animate toward, and
    // computeScroll() ticks it. The alternate buffer (tmux/grok/less) never uses topRow —
    // there is no local scrollback there, so scroll gestures are forwarded to the remote.
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private var scrollAccumulatorPx = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity
    private var isDragScrolling = false
    // Coalesces alt-buffer wheel motion into discrete "notches" so a smooth drag becomes a
    // sane number of arrow-key / wheel events instead of one per pixel.
    private var altScrollAccumulatorPx = 0f
    // Uptime (ms) of the last tmux copy-mode scroll routed to the host, used to decide whether
    // the next gesture must (re-)enter copy-mode. Copy-mode is entered on the first scroll
    // after a quiet gap; contiguous scrolling keeps navigating without re-sending the prefix.
    private var lastCopyModeScrollUptimeMs = 0L
    private var altScrollHintShownUptimeMs = 0L

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) scroller.forceFinished(true)
                scrollAccumulatorPx = 0f
                altScrollAccumulatorPx = 0f
                isDragScrolling = false
                return true
            }

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
                if (abs(distanceY) > touchSlop / 2f) isDragScrolling = true
                // Alt-buffer (tmux/grok/less): no local scrollback. Forward the gesture to the
                // remote as mouse-wheel (if it enabled mouse tracking) or arrow keys otherwise,
                // so scrolling actually moves content instead of doing nothing.
                if (isAlternateBufferActive()) {
                    forwardAlternateBufferScroll(e2, distanceY)
                    return true
                }
                // Normal buffer: scroll the local transcript by whole rows.
                scrollAccumulatorPx += distanceY
                val lineSpacing = scaledCellHeightPx().toFloat()
                val rows = (scrollAccumulatorPx / lineSpacing).toInt()
                if (rows != 0) {
                    scrollAccumulatorPx -= rows * lineSpacing
                    scrollTranscriptByRows(rows)
                }
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                // No local fling on the alt buffer — the drag already forwarded wheel/arrow
                // events; a fling there would just spam the remote unpredictably.
                if (isAlternateBufferActive()) return false
                val lineSpacing = scaledCellHeightPx().coerceAtLeast(1)
                val current = engine ?: return false
                val minTop = -current.withTerminalState { it.screen.activeTranscriptRows }
                if (minTop >= 0) return false
                // Work in pixels so the OverScroller physics feel natural, then map to rows in
                // computeScroll(). startY is topRow*lineSpacing; scrolling up (into history) is
                // a downward drag = positive velocityY.
                val startPx = topRow * lineSpacing
                val minPx = 0
                val maxPx = -minTop * lineSpacing
                scroller.forceFinished(true)
                scroller.fling(
                    0, -startPx,
                    0, velocityY.toInt().coerceIn(-maxFlingVelocity, maxFlingVelocity),
                    0, 0,
                    minPx, maxPx
                )
                postInvalidateOnAnimation()
                return true
            }
        }
    )

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setTerminalZoom(terminalZoom * detector.scaleFactor)
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
        this.engine?.clearOnRender(renderListener)
        resetInputState()
        inputGeneration += 1
        inputArmed = false
        topRow = 0
        scrollAccumulatorPx = 0f
        altScrollAccumulatorPx = 0f
        scroller.forceFinished(true)
        this.engine = engine
        this.onInput = engine::sendInput
        engine.setOnRender(renderListener)
        invalidateResizeCache()
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    // Coalesces bursts of PTY output into at most one repaint per display frame,
    // fully off the Compose recomposition path. postInvalidateOnAnimation() is
    // thread-safe and idempotent within a frame, so a flood of output from the
    // engine's IO thread still yields a single draw. The scroll-position clamp
    // happens in onDraw on the UI thread rather than here.
    private val renderListener: () -> Unit = {
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

    /**
     * Declares what we know about tmux for this session, used to decide whether an alt-buffer
     * scroll can be routed to copy-mode.
     * @param launchedByApp the app attached/created this tmux itself (definitive).
     * @param runningOnHost tmux was seen running on the host at connect time (heuristic; only
     *   acted on together with a live alternate-buffer check so a plain shell is never touched).
     */
    fun setTmuxCopyModeState(launchedByApp: Boolean, runningOnHost: Boolean) {
        tmuxLaunchedByApp = launchedByApp
        tmuxRunningOnHost = runningOnHost
    }

    // Copy-mode is a safe scroll target only when we're actually in the alternate buffer AND
    // we have reason to believe the pane is tmux. app-launched tmux is definitive; the
    // host-scan heuristic is gated behind the live alt-buffer check so a normal-buffer shell
    // prompt can never receive a stray copy-mode prefix.
    private fun tmuxCopyModeAvailable(): Boolean =
        isAlternateBufferActive() && (tmuxLaunchedByApp || tmuxRunningOnHost)

    fun setOnRequestTmuxCopyScroll(listener: (lines: Int, enterCopyMode: Boolean) -> Unit) {
        onRequestTmuxCopyScroll = listener
    }

    fun setOnAltScrollUnavailable(listener: () -> Unit) {
        onAltScrollUnavailable = listener
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
        scroller.forceFinished(true)
        val cellWidthPx = scaledCellWidthPx()
        val cellHeightPx = scaledCellHeightPx()
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
        terminalZoom = 1f
        onTextSizeChanged(textSizePx)
        rebuildRenderer()
    }

    fun setTerminalTypeface(typeface: Typeface) {
        if (terminalTypeface == typeface) return
        terminalTypeface = typeface
        rebuildRenderer()
    }

    fun setTerminalBackground(hex: String) {
        val parsed = runCatching { Color.parseColor(hex) }.getOrDefault(terminalBackground)
        if (parsed == terminalBackground) return
        terminalBackground = parsed
        postInvalidateOnAnimation()
    }

    fun setContentLeftPaddingPx(paddingPx: Int) {
        val safePadding = paddingPx.coerceAtLeast(0)
        if (safePadding == contentLeftPaddingPx) return
        contentLeftPaddingPx = safePadding
        invalidateResizeCache()
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun setContentRightPaddingPx(paddingPx: Int) {
        val safePadding = paddingPx.coerceAtLeast(0)
        if (safePadding == contentRightPaddingPx) return
        contentRightPaddingPx = safePadding
        invalidateResizeCache()
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    fun setDirectionalSwipeEnabled(enabled: Boolean) {
        directionalSwipeEnabled = enabled
        scrollAccumulatorPx = 0f
        altScrollAccumulatorPx = 0f
        isDragScrolling = false
        // A gesture in progress when the mode flips routes to the other handler and never hits
        // the ACTION_UP/CANCEL release, so proactively drop any tracker to avoid a leak.
        releaseVelocityTracker()
        if (!enabled) stopDirectionalSwipeRepeat()
    }

    private fun rebuildRenderer() {
        resetInputState()
        rendererTextSizePx = terminalZoomedTextSizePx(textSizePx, terminalZoom)
        renderer = terminalRendererOrFallback(rendererTextSizePx, terminalTypeface)
        invalidateResizeCache()
        updateTerminalSize()
        postInvalidateOnAnimation()
    }

    private fun setTerminalZoom(zoom: Float) {
        val safeZoom = zoom.coerceIn(MIN_TERMINAL_ZOOM, MAX_TERMINAL_ZOOM)
        if (abs(safeZoom - terminalZoom) < 0.01f) return
        terminalZoom = safeZoom
        val nextTextSizePx = terminalZoomedTextSizePx(textSizePx, terminalZoom)
        if (nextTextSizePx != rendererTextSizePx) {
            rebuildRenderer()
        } else {
            updateTerminalSize()
            postInvalidateOnAnimation()
        }
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

    override fun onDetachedFromWindow() {
        engine?.clearOnRender(renderListener)
        pendingResize?.let(::removeCallbacks)
        pendingResize = null
        scroller.forceFinished(true)
        velocityTracker?.recycle()
        velocityTracker = null
        stopDirectionalSwipeRepeat()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            if (!hasFocus()) requestFocus()
            armInputForFocusedTerminal(showKeyboard = keyboardRequested)
        }
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val lineSpacing = scaledCellHeightPx().coerceAtLeast(1)
            // scroller y is stored as -topRow*lineSpacing (see onFling), so recover topRow.
            val nextTop = (-scroller.currY.toFloat() / lineSpacing).roundToInt()
            if (nextTop != topRow) {
                topRow = nextTop
                clampTopRow()
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        clampTopRow()
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
                renderer = terminalRendererOrFallback(rendererTextSizePx, terminalTypeface)
                postInvalidateOnAnimation()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val scaleHandled = scaleDetector.onTouchEvent(event)
        if (scaleDetector.isInProgress) {
            releaseVelocityTracker()
            return true
        }
        if (directionalSwipeEnabled) {
            return handleDirectionalSwipeTouch(event) || scaleHandled
        }
        trackVelocity(event)
        val gestureHandled = gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                maybeFlingFromVelocity()
                scrollAccumulatorPx = 0f
                altScrollAccumulatorPx = 0f
                isDragScrolling = false
                releaseVelocityTracker()
                stopDirectionalSwipeRepeat()
            }
            MotionEvent.ACTION_CANCEL -> {
                scrollAccumulatorPx = 0f
                altScrollAccumulatorPx = 0f
                isDragScrolling = false
                releaseVelocityTracker()
                stopDirectionalSwipeRepeat()
            }
        }
        return scaleHandled || gestureHandled || super.onTouchEvent(event)
    }

    // GestureDetector.onFling isn't fired for every gesture reliably when we also consume
    // scroll, so compute the fling from the tracked velocity on ACTION_UP as well. The
    // gesture detector's own onFling still works; this just guarantees a fling when the
    // finger lifts with velocity after a manual drag.
    private fun trackVelocity(event: MotionEvent) {
        val tracker = velocityTracker ?: VelocityTracker.obtain().also { velocityTracker = it }
        tracker.addMovement(event)
    }

    private fun maybeFlingFromVelocity() {
        if (isAlternateBufferActive()) return
        val tracker = velocityTracker ?: return
        tracker.computeCurrentVelocity(1000, maxFlingVelocity.toFloat())
        val velocityY = tracker.yVelocity
        if (abs(velocityY) < minFlingVelocity) return
        // Only fling if the scroller isn't already running one from the gesture detector.
        if (!scroller.isFinished) return
        val lineSpacing = scaledCellHeightPx().coerceAtLeast(1)
        val current = engine ?: return
        val minTop = -current.withTerminalState { it.screen.activeTranscriptRows }
        if (minTop >= 0) return
        val startPx = topRow * lineSpacing
        scroller.fling(
            0, -startPx,
            0, velocityY.toInt().coerceIn(-maxFlingVelocity, maxFlingVelocity),
            0, 0,
            0, -minTop * lineSpacing
        )
        postInvalidateOnAnimation()
    }

    private fun releaseVelocityTracker() {
        velocityTracker?.recycle()
        velocityTracker = null
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
                // Only re-arm (bumping the generation, which permanently invalidates THIS
                // input connection) when the generation genuinely mismatches — i.e. the
                // engine was rebound under us. Transient invisibility (view momentarily not
                // VISIBLE during a layout/inset pass) must NOT stale a still-valid IC, or a
                // valid connection gets thrown away and keystrokes are dropped for good.
                if (!terminalInputGenerationAccepted(inputGeneration, generation)) {
                    rearmInputAfterTerminalAction(showKeyboard = keyboardRequested)
                    return true
                }
                // Generation matches but the view isn't currently accepting (transient):
                // swallow this event without destroying the connection; the IME keeps using
                // the same IC and the next event after the view settles goes through.
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
                onInput(inputTransform(""))
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

    private fun scrollTranscriptByRows(rows: Int) {
        val current = engine ?: return
        val minTop = -current.withTerminalState { it.screen.activeTranscriptRows }
        topRow = (topRow + rows).coerceIn(minTop, 0)
        postInvalidateOnAnimation()
    }

    private fun isAlternateBufferActive(): Boolean {
        return engine?.withTerminalState { it.isAlternateBufferActive } ?: false
    }

    // Alternate buffer (tmux, grok, less, vim, htop...) has no local scrollback, so a scroll
    // gesture must reach the remote. There is deliberately NO arrow-key synthesis here anymore:
    // captured DECSET modes proved that "alt buffer + no mouse tracking" cannot distinguish an
    // app where arrows scroll (less/man) from one where arrows edit an input line or move
    // history (grok, fzf, any readline-in-alt-buffer) — the old arrow path dumped previous
    // prompts into grok's input. Blind wheel events are just as unsafe: an app not in mouse
    // mode receives the raw SGR bytes as literal input. So the routing is now:
    //   1. mouse tracking on  -> SGR/legacy wheel events (the app asked for mouse; safe).
    //   2. else, app-launched tmux attached -> tmux copy-mode navigation (scrolls the real
    //      pane scrollback, i.e. grok's output), where arrow/PgUp keys ARE the correct input.
    //   3. else -> nothing is sent; surface a one-shot hint. Never corrupt the remote input.
    private fun forwardAlternateBufferScroll(event: MotionEvent, distanceY: Float) {
        altScrollAccumulatorPx += distanceY
        val (notches, remainder) = terminalAltBufferScrollNotches(
            accumulatedPx = altScrollAccumulatorPx,
            cellHeightPx = scaledCellHeightPx(),
            maxNotches = ALT_SCROLL_MAX_NOTCHES_PER_EVENT
        )
        altScrollAccumulatorPx = remainder
        if (notches == 0) return
        // distanceY > 0 (finger up the screen) => positive notches => reveal NEWER content
        // (scroll down), matching the normal-buffer gesture direction.
        val up = notches < 0
        val count = abs(notches)
        val current = engine ?: return
        val mouseTracking = current.withTerminalState { it.isMouseTrackingActive }
        if (mouseTracking) {
            val (column, row) = pointerCell(event)
            val button = if (up) TerminalEmulator.MOUSE_WHEELUP_BUTTON else TerminalEmulator.MOUSE_WHEELDOWN_BUTTON
            repeat(count) {
                current.withTerminalState { emulator ->
                    emulator.sendMouseEvent(button, column + 1, row + 1, true, false, false, false)
                }
            }
            return
        }
        if (tmuxCopyModeAvailable()) {
            // Positive notches = scroll down toward newer content; copy-mode scroll is expressed
            // as signed lines (negative = up into history). Enter copy-mode only when this is the
            // first scroll after a quiet gap — contiguous scrolling keeps navigating without
            // re-sending the prefix (which would reset the copy-mode cursor).
            val nowMs = SystemClock.uptimeMillis()
            val enterCopyMode = (nowMs - lastCopyModeScrollUptimeMs) >= COPY_MODE_REENTRY_GAP_MS
            lastCopyModeScrollUptimeMs = nowMs
            val lines = if (up) -count else count
            onRequestTmuxCopyScroll(lines, enterCopyMode)
            return
        }
        // No safe target: do not synthesize keys. Tell the UI at most once per quiet gap so it
        // can hint the user (e.g. enable tmux mouse) instead of eating their input.
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - altScrollHintShownUptimeMs >= ALT_SCROLL_HINT_GAP_MS) {
            altScrollHintShownUptimeMs = nowMs
            onAltScrollUnavailable()
        }
    }

    private fun pointerCell(event: MotionEvent): Pair<Int, Int> {
        return terminalViewportCellForPoint(
            x = event.x,
            y = event.y,
            cellWidthPx = scaledCellWidthPx(),
            cellHeightPx = scaledCellHeightPx(),
            leftPaddingPx = contentLeftPaddingPx
        )
    }

    private fun updateTerminalSize() {
        if (engine == null) return
        if (width <= 0 || height <= 0) return
        val cellWidthPx = scaledCellWidthPx()
        val cellHeightPx = scaledCellHeightPx()
        val contentWidth = (width - contentLeftPaddingPx - contentRightPaddingPx).coerceAtLeast(cellWidthPx)
        val columns = max(MIN_TERMINAL_COLUMNS, contentWidth / cellWidthPx)
        val rows = max(4, height / cellHeightPx)
        // Dedupe: keyboard/inset animations fire onSizeChanged every frame, but most
        // frames map to the same grid. Never reflow the PTY (remote SIGWINCH) for a
        // grid we already applied — that is what makes TUIs like tmux/grok thrash.
        if (columns == lastAppliedColumns &&
            rows == lastAppliedRows &&
            cellWidthPx == lastAppliedCellWidthPx &&
            cellHeightPx == lastAppliedCellHeightPx
        ) {
            return
        }
        pendingResize?.let(::removeCallbacks)
        pendingResize = null
        val apply = Runnable {
            pendingResize = null
            val engineNow = engine ?: return@Runnable
            if (width <= 0 || height <= 0) return@Runnable
            val cw = scaledCellWidthPx()
            val ch = scaledCellHeightPx()
            val cols = max(MIN_TERMINAL_COLUMNS, (width - contentLeftPaddingPx - contentRightPaddingPx).coerceAtLeast(cw) / cw)
            val rws = max(4, height / ch)
            lastAppliedColumns = cols
            lastAppliedRows = rws
            lastAppliedCellWidthPx = cw
            lastAppliedCellHeightPx = ch
            lastResizeApplyUptimeMs = SystemClock.uptimeMillis()
            engineNow.resize(cols, rws, cw, ch)
            clampTopRow()
            postInvalidateOnAnimation()
        }
        // Leading-edge debounce. The old code applied the FIRST size immediately only on
        // initial layout and delayed every later change by RESIZE_DEBOUNCE_MS. With the
        // TerminalScreen IME inset now driven by imeAnimationTarget, the keyboard produces a
        // SINGLE final-size change (not one per animation frame), so that trailing delay just
        // desynced the grid from the already-resized view for 90ms — the "content shifts, then
        // jumps" artifact. Now: apply immediately when this is the first layout OR enough time
        // has passed since the last reflow (the common case: an isolated size change like the
        // keyboard opening). Only coalesce a genuine BURST of distinct sizes arriving within
        // the debounce window (rotation, split-screen drag) onto the trailing edge, which is
        // what actually protects the remote PTY from a SIGWINCH storm.
        val now = SystemClock.uptimeMillis()
        val immediate = lastAppliedColumns < 0 ||
            (now - lastResizeApplyUptimeMs) >= RESIZE_DEBOUNCE_MS
        if (immediate) apply.run() else {
            pendingResize = apply
            postDelayed(apply, RESIZE_DEBOUNCE_MS)
        }
    }

    private fun invalidateResizeCache() {
        pendingResize?.let(::removeCallbacks)
        pendingResize = null
        lastAppliedColumns = -1
        lastAppliedRows = -1
        lastAppliedCellWidthPx = -1
        lastAppliedCellHeightPx = -1
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
        val cellWidthPx = scaledCellWidthPx()
        val cellHeightPx = scaledCellHeightPx()
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
            cellWidthPx = scaledCellWidthPx(),
            cellHeightPx = scaledCellHeightPx(),
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

    private fun scaledCellWidthPx(): Int {
        return max(1, renderer.fontWidth.roundToInt())
    }

    private fun scaledCellHeightPx(): Int {
        return max(1, renderer.fontLineSpacing)
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
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        val threshold = (scaledCellHeightPx() * 2.5f).coerceAtLeast(72f)
        if (absX < threshold && absY < threshold) return true
        val horizontal = absX > absY
        if (if (horizontal) absX < absY * 1.25f else absY < absX * 1.25f) return true
        // Honour DECCKM (application cursor mode) like the alt-buffer scroll path: vim and
        // many TUIs expect SS3 (ESC O x) rather than CSI (ESC [ x) when the mode is active.
        val appMode = engine?.withTerminalState { it.isCursorKeysApplicationMode } ?: false
        val letter = when {
            horizontal && deltaX < 0f -> 'D'
            horizontal -> 'C'
            deltaY < 0f -> 'A'
            else -> 'B'
        }
        val sequence = terminalDirectionalArrowSequence(letter, appMode)
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
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        post {
            if (!isAttachedToWindow) return@post
            imm.restartInput(this)
            if (showKeyboard) requestSoftInput(imm, attempt = 0)
        }
    }

    // "Keyboard hidden for good" bug: the old path relied solely on
    // showSoftInput(SHOW_IMPLICIT), which the framework silently ignores when the view
    // isn't yet the active input target (common right after focus/resize under
    // edge-to-edge) AND whenever the system thinks an implicit show isn't warranted
    // (e.g. after it decided to hide the IME once). Once that happens, no amount of
    // re-requesting SHOW_IMPLICIT brings it back.
    //
    // Primary path is now WindowInsetsControllerCompat.show(ime()), the API Google
    // recommends for programmatic IME control — it is not subject to the implicit-show
    // heuristics and reliably shows the keyboard for the focused editor. We keep the
    // showSoftInput retry loop as a fallback for OEMs where the insets controller no-ops.
    private fun requestSoftInput(imm: InputMethodManager, attempt: Int) {
        if (!isAttachedToWindow || windowVisibility != VISIBLE || !keyboardRequested) return
        if (!isFocused) requestFocus()
        // Fire the insets-controller show as well — it is not subject to the implicit-show
        // heuristics. But do NOT treat "show() didn't throw" as proof the IME appeared: on
        // OEMs where it silently no-ops (the exact devices this targets), that would wrongly
        // suppress the fallback. The ResultReceiver from showSoftInput is the only real signal
        // of whether the keyboard actually came up, so retries key off THAT alone.
        runCatching {
            androidx.core.view.ViewCompat.getWindowInsetsController(this)
                ?.show(androidx.core.view.WindowInsetsCompat.Type.ime())
        }
        val receiver = object : android.os.ResultReceiver(handler) {
            override fun onReceiveResult(resultCode: Int, resultData: android.os.Bundle?) {
                val shown = resultCode == InputMethodManager.RESULT_SHOWN ||
                    resultCode == InputMethodManager.RESULT_UNCHANGED_SHOWN
                if (!shown && attempt < SOFT_INPUT_MAX_RETRIES) {
                    postDelayed({
                        if (keyboardRequested) requestSoftInput(imm, attempt + 1)
                    }, SOFT_INPUT_RETRY_DELAY_MS)
                }
            }
        }
        // Explicit show (no SHOW_IMPLICIT): explicit shows are honoured even when the
        // system would suppress an implicit one, which is the actual "hidden for good" fix.
        imm.showSoftInput(this, 0, receiver)
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
        const val MIN_TERMINAL_ZOOM = 0.7f
        const val MAX_TERMINAL_ZOOM = 2.4f
        const val DIRECTIONAL_SWIPE_HOLD_REPEAT_DELAY_MS = 650L
        const val DIRECTIONAL_SWIPE_REPEAT_INTERVAL_MS = 140L
        const val RESIZE_DEBOUNCE_MS = 90L
        const val SOFT_INPUT_MAX_RETRIES = 3
        const val SOFT_INPUT_RETRY_DELAY_MS = 120L
        const val ALT_SCROLL_MAX_NOTCHES_PER_EVENT = 6
        // Gap after which a fresh alt-buffer scroll re-enters tmux copy-mode rather than
        // assuming we are still in it (the user may have left copy-mode with q/Enter).
        const val COPY_MODE_REENTRY_GAP_MS = 600L
        // Rate-limit the "can't scroll here" hint so a drag doesn't spam it every notch.
        const val ALT_SCROLL_HINT_GAP_MS = 2500L
    }
}

private fun terminalRendererOrFallback(textSizePx: Int, typeface: Typeface): TerminalRenderer {
    return runCatching { TerminalRenderer(textSizePx, typeface) }
        .getOrElse { TerminalRenderer(textSizePx, Typeface.MONOSPACE) }
}

// Arrow-key escape sequence honouring DECCKM (cursor-keys application mode). In normal mode
// cursor keys send CSI (ESC [), in application mode they send SS3 (ESC O). tmux copy-mode,
// less and man read these to scroll when mouse tracking is off.
internal fun terminalCursorKeySequence(up: Boolean, applicationMode: Boolean): String {
    return terminalDirectionalArrowSequence(if (up) 'A' else 'B', applicationMode)
}

// Arrow-key escape for any of the four directions (A=up, B=down, C=right, D=left), honouring
// DECCKM: CSI (ESC [) in normal mode, SS3 (ESC O) in application cursor mode. Used by both
// alt-buffer scroll routing and directional-swipe input so all arrow emission stays consistent.
internal fun terminalDirectionalArrowSequence(letter: Char, applicationMode: Boolean): String {
    val prefix = if (applicationMode) "\u001BO" else "\u001B["
    return prefix + letter
}

// Converts an accumulated vertical drag (px) into a signed, clamped number of scroll
// "notches" for the alternate buffer, plus the leftover pixels to carry into the next call.
// Positive notches = scroll up (finger moved up the screen). The magnitude is clamped so a
// fast flick can't flood the remote with events. cellHeightPx is the px-per-notch.
internal fun terminalAltBufferScrollNotches(
    accumulatedPx: Float,
    cellHeightPx: Int,
    maxNotches: Int
): Pair<Int, Float> {
    val notchPx = cellHeightPx.coerceAtLeast(1).toFloat()
    val notches = (accumulatedPx / notchPx).toInt()
    if (notches == 0) return 0 to accumulatedPx
    val clamped = notches.coerceIn(-maxNotches, maxNotches)
    // Consume only the pixels for the notches we actually emit and carry the rest forward.
    // Previously the remainder was computed from the UNclamped count, so a fast flick that
    // produced more than maxNotches per frame silently discarded the excess motion — the
    // scroll would "stick then jump", the tmux/grok stutter. Carrying the leftover forward
    // means the next frame(s) drain it, so no drag distance is lost; the per-frame cap only
    // paces how fast events reach the remote. The accumulator is reset on finger up/cancel,
    // so this can never coast on its own.
    val remainder = accumulatedPx - clamped * notchPx
    return clamped to remainder
}

internal fun terminalInputGenerationAccepted(currentGeneration: Int, capturedGeneration: Int): Boolean {
    return currentGeneration == capturedGeneration
}

internal fun terminalPasteKeepsInputGeneration(): Boolean = true

internal fun terminalZoomedTextSizePx(textSizePx: Int, zoom: Float): Int {
    return (textSizePx * zoom).roundToInt().coerceIn(12, 130)
}

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
