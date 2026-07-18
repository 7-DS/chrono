Chrono 0.1.40-dev.20260713

Terminal keyboard & resizing — robustness pass
- Autoscaling: the terminal now shrinks to the space above the on-screen keyboard
  (imePadding on the terminal surface). Previously, under edge-to-edge, the
  keyboard floated over the bottom rows and hid the input line — especially bad
  for full-screen TUIs like tmux or grok. Now content always stays visible above
  the keyboard and grows back smoothly when it closes.
- No more glitch/thrash on keyboard show/hide. The keyboard open/close animation
  fires a size change on every frame; the terminal used to reflow the remote PTY
  (send a window-resize / SIGWINCH) on each one, making TUIs redraw frantically.
  Resizes are now deduped by grid size and debounced, so the remote shell gets a
  single clean resize once the animation settles.
- Fixed "keyboard stays hidden for good". Showing the soft keyboard could silently
  no-op when the view wasn't yet the active input target (common right after focus
  or a resize under edge-to-edge). It now guarantees focus and retries until the
  keyboard actually appears.
- Cleaned up pending resize callbacks on detach to avoid stray work after teardown.
