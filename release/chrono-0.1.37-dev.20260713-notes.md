Chrono 0.1.37-dev.20260713

Metrics
- Fixed the server dashboard metrics appearing frozen. Home stat cards now
  update live on the 2-second refresh cadence again (the derived values were
  being cached against a snapshot map that never changed reference; they now
  use derivedStateOf so they recompute when the underlying metrics change).

vnStat / network stats
- Fixed wrong traffic numbers. Modern vnStat JSON reports bytes, but the parser
  was multiplying plain rx/tx counters by 1024 whenever a version field was
  present — inflating every day/week/month/year total by 1024x. Totals are now
  correct.
- Removed the combined day/week/month/year card that made the page hard to read.
  The Day/Week/Month/Year selector remains; picking a period now shows just that
  period, with a new download-vs-upload split-bar graph.

Files / SFTP
- Reworked the SFTP glyphs to standard Material icons (toolbar, file/folder
  icons, and the three-dot menu) for a cleaner, less "hand-drawn" look.
- Reworked the file/folder three-dot action menu into a clean icon + label list
  that scrolls when long.

Port forwarding
- The add-tunnel page no longer lists every host as tall pills. It now shows the
  current host in a compact chip with a "Change" button that opens a host picker
  dialog — much better with many hosts.

Vault
- Decluttered the credential card: primary actions (Details, Copy, Edit,
  Validate) stay prominent; secondary actions (Export, Share, Rename, Organize,
  Unlink, links, QR, Remove) moved into a "More" overflow menu.
- Stopped showing "No saved passphrase"; only "Passphrase saved" is shown, and
  only when a passphrase is actually saved.

Add / Edit Host
- Fixed several UI issues: bigger password-visibility touch target, text no
  longer clipping inside rounded buttons/pills, and the rootfs catalog and
  identity picker dialogs now scroll instead of overflowing.

Text / fonts
- Text boxes now render in Nunito by default instead of the system font, and the
  input font is configurable in Settings → Appearance (Nunito / System /
  JetBrains Mono / Fira Code).

Docs
- Added a full "Connection Types" guide to the README with prerequisites,
  step-by-step setup, and tips for SSH, Mosh, Eternal Terminal, VNC, RDP, SMB,
  rclone, and Local PRoot.
