# Freeze frame

Select a video clip, put the playhead inside it and tap **Freeze frame**: the clip is split at the playhead and a 2 s freeze clip is put between the halves, showing the picture at that moment, in silence. Select the freeze and use **Freeze length** (0.5–10 s). One undo step per change; saved with the project (`"freezeMs"` on the clip, only when set).

- A freeze is an ordinary video clip with `freezeMs > 0`: it keeps the original clip's filter and crop/rotation, has its own transition slot, and can be moved, deleted and filtered like any clip. It cannot be trimmed, split, sped up, reversed or frozen again (those tools are disabled).
- A reversed clip cannot be frozen.
- Export: `trim=end_frame=1` keeps one picture, which is then repeated with `tpad=stop_mode=clone` and cut to the exact length with `trim=duration`; the clip's sound slot is silence of the same length. (Checked: 2 s at 25 fps gives exactly 50 frames.)
- The freeze's input is a short (200 ms) window of the source at the frame time; near the very end of a clip the picture is taken up to 100 ms earlier so there is always something to read.
- **Preview:** the player cannot hold a still picture, so a freeze previews as 10 % of its length in slow motion (muted). The panel says so. The export is a true still.
- `tpad`, `trim` and `fps` are checked against the device FFmpeg by `FilterAvailabilityTest`.
