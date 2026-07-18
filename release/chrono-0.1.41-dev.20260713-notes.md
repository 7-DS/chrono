Chrono 0.1.41-dev.20260713

Terminal rendering — fixes the real cause of the jank/glitch

Root cause: every chunk of output from the remote shell was recomposing the entire
terminal screen and re-running the view's setup on each one — including reloading the
terminal font. A busy full-screen program (tmux, grok, htop, anything redrawing
continuously) triggered hundreds of these per second, which is what made the terminal
stutter, flicker, and feel awful.

- Terminal output now repaints the terminal view directly, completely off the Compose
  recomposition path — the same model a native terminal (Termux) uses. Bursts of output
  coalesce into at most one draw per display frame.
- The font is now loaded once and cached, instead of being reloaded on every screen
  update.
- Scroll-position clamping moved into the draw pass so the direct repaint path stays
  thread-safe.

This is a different, more fundamental fix than the previous release's keyboard/resize
work (which stays in). If any glitch remains, tell me exactly which program and what it
looks like.
