Chrono 0.1.44-dev.20260722

Terminal — alternate-buffer scroll routing (tmux/grok)

- Scroll gestures in the alternate screen buffer no longer synthesize bare arrow keys.
  Arrow-key scroll is correct for pagers (less, man, vim) but corrupts interactive
  alt-buffer programs such as grok, where arrow keys drive command history and cursor
  movement — a scroll would inject previous prompts into the input line. Sending mouse
  wheel bytes unconditionally is equally unsafe: a program not in mouse-reporting mode
  receives the raw sequence as literal input. There is no terminal mode that distinguishes
  the two cases, so the routing is now:
  - Mouse tracking on: SGR mouse-wheel events (htop, btop, tmux with mouse on, and any
    program that requested mouse reporting).
  - tmux session (attached via the app, or detected running on the host) in the alternate
    buffer: routed to tmux copy-mode, which scrolls the real pane scrollback. Copy-mode is
    entered once per scroll burst and navigates by line; a quiet gap re-enters it.
  - Otherwise: nothing is sent to the remote, and a status hint is shown, so the input line
    is never corrupted.
- Copy-mode routing is gated on the alternate buffer being active, so a normal shell prompt
  never receives a copy-mode prefix.

Verified: compileFullDebugKotlin, assembleFullDebug/assembleLiteDebug, and unit tests
(TmuxSupportTest, TerminalTranscriptSearchTest, TerminalScreenTest) pass. On-device scroll
behaviour with grok not yet hardware-tested.

Debug-signed APKs (full + lite; universal / arm64-v8a / x86_64).
