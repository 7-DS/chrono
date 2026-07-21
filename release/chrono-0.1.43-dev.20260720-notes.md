Chrono 0.1.43-dev.20260720

Terminal — accessory bar order, tmux/grok scroll, keyboard-launch squish

- Accessory bar: the Keyboard toggle now sits to the LEFT of Tmux (order is now
  Pointer · Keyboard · Tmux · Scroll), matching the Termius-style layout you asked for.

- Scrolling in grok/tmux (alternate-buffer programs) no longer drops most of a fast
  flick. The code that turns a drag into scroll "notches" was clamping a fast flick to a
  max of 6 notches per frame but throwing away the pixels for everything beyond that
  clamp — so roughly 70% of a quick scroll silently vanished, which is what made it feel
  stuttery and slow compared to Termius. Unsent motion now carries forward into the next
  frame instead of being discarded.

- Keyboard-launch "squish" (content shifts, then jumps): when the keyboard opened, the
  terminal's layout box snapped to its final height on the first frame, but the actual
  grid reflow was held back by a fixed 90ms delay. For those 90ms the old grid was drawn
  into the already-shrunk box (the shift), then it snapped to the corrected layout (the
  jump). The reflow for a single size change (the keyboard opening) now happens
  immediately; the 90ms coalescing is kept only for genuine rapid-fire size changes
  (rotation, split-screen resize) so the remote shell still isn't spammed with resize
  events.

Reference: unpacked Termius for comparison, but it is closed-source (obfuscated), so the
scroll behaviour was matched against Termux (the open-source terminal this app already
builds on).

Note: the keyboard-launch squish fix is verified by code review + build + unit tests. It
is NOT yet confirmed on a physical device (the emulator can't capture the keyboard
animation frames). If any shift/jump remains when the keyboard opens, tell me and I'll
keep digging.
