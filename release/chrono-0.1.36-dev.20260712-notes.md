Chrono 0.1.36-dev.20260712

Animation & performance
- The Home fleet dashboard no longer stutters while metrics are polling. Per-tick
  work (server rows, online/offline counts, untrusted-host lookup, metric colors)
  is now memoized instead of recomputed on every frame, so cards stay smooth even
  with many hosts loading at once.
- The bottom navigation bar now animates: the selection indicator slides between
  tabs with a spring, and the icon/label colors cross-fade with a subtle icon
  scale, instead of snapping instantly.

Files / SFTP
- The Files tab (and its section pill) now read "Files" instead of "SFTP", matching
  the other primary pages.
- The SFTP browser's top-left back button now exits the browser as expected, rather
  than climbing one folder up the path. Folder navigation up is still available via
  the new "Up to parent folder" row, the breadcrumb, and the system back gesture.
- Added a Home toolbar button that jumps straight to the starting directory (you no
  longer have to climb one folder at a time).
- The parent-directory row now shows a clear up-arrow and "Up to parent folder"
  label instead of a bare "..".
- Redrew the confusing SCP button glyph as two opposing transfer arrows.
- File sizes now display with standard, precise units (B / KB / MB / GB / TB) instead
  of ambiguous single-letter suffixes.

Vault
- Editing is clearer: the "Replace" action is now "Edit". Editing a password prefills
  the current value so it behaves like a real edit, and the dialog reads "Edit
  password" / "Edit private key".
- Duplicate password identities are cleaned up automatically. On launch, password
  entries whose content is byte-for-byte identical (same fields and same stored
  secret) are merged into a single entry; any host that referenced a removed copy is
  re-pointed to the survivor, so nothing unique is lost.

Settings
- Fixed text being slightly clipped inside rounded bordered chips (metric-color
  preview chips, section badges, and the shared segmented control) by giving their
  labels enough horizontal padding to clear the rounded corners.
