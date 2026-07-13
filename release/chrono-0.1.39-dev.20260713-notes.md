Chrono 0.1.39-dev.20260713

Animations
- Fixed the sharp-corner glitch when cards expand/collapse. DeckCard (used across
  the app) animated its size before clipping to its rounded shape, so content
  briefly spilled past the corners mid-animation. It now clips first and animates
  within the rounded shape using a smooth spring — expand/collapse is clean
  everywhere (vault, SFTP, tunnels, settings rows, etc.).
- Expand/collapse chevrons now rotate smoothly instead of snapping.
- Removed a redundant second size-animation stacked on the SFTP browser card that
  made its transitions look abrupt/glitchy.

Vault
- Added Copy (private key) and Copy Pub (public key) to the main button row for
  keys, so both are one tap away without opening More.
- Replaced the cramped "More" dropdown with a clean grouped action sheet
  (icon + label rows in a single container) that looks and behaves properly.

SFTP
- Redesigned the file/folder three-dot menu: actions are now flat icon+label rows
  inside one rounded container instead of each option having its own ugly border.
