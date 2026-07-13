Chrono 0.1.38-dev.20260713

SFTP fixes
- Fixed jittery/buggy resize animation in the SFTP file browser. The full-page
  browser card animated its height on every file-list change, causing a jerky
  reflow as folders loaded; that animation is now off for the full-page view.
- Files-tab SFTP sessions now persist and appear in the Connections tab. A
  connection opened from the Files tab used to be ephemeral and vanish when you
  navigated away; it now registers as a Connections workspace (deduped per host)
  and survives navigation.
