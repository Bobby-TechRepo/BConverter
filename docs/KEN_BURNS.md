# Slow zoom or pan (Ken Burns)

A video clip can move slowly over its picture for its whole length. It is in the Crop, rotate and flip panel, under "Slow zoom or pan".

- Modes: None, Zoom in, Zoom out, Pan left/right/up/down. The view moves in the direction named.
- Strength: 110%–200%. Zoom modes go between the whole picture and this zoom; pan modes keep this zoom and slide the view from one edge to the other.
- "Apply motion to all clips" copies it to every clip (freeze frames are skipped; they have no motion).
- Saved with the project (`kenBurns` on each clip: `mode`, `amount`). Undo/redo as one step. A split clip gives both halves the full motion over their own length.

## Export
`KenBurnsFilters` writes `setpts` + `fps` + (`scale` 2× for pictures up to 1920 wide) + `zoompan` after the crop/rotate/flip and before the fit to the canvas, so the motion is on the picture as the user shaped it. `zoompan` keeps every frame the same size.

Zip 134 first cut a window out of a picture scaled to a different size on every frame (`scale eval=frame` + `crop`). That worked on desktop FFmpeg 6.1 but `every_motion_renders_and_keeps_the_picture_size` never finished on the device's FFmpegKit 8.1, so zip 135 replaced it. Pixels of the `zoompan` version were checked against the source on desktop FFmpeg (zoom in, pan). A reversed clip gets its motion written backwards, so it plays in the chosen direction. Speed changes are applied afterwards, so the motion still spans the whole clip.

## Limits
- The preview does not move; only the export does.
- `zoompan` moves the window in whole pixels of the (2× scaled) picture: very slow zooms can look slightly stepped; pictures wider than 1920 are not scaled up first.
- 4K exports are slow.
- The motion is a straight line from start to end (no easing, no custom start/end window).

## Status
Zips 134 (`scale eval=frame` + `crop`) and 135 (`zoompan`) did not finish on a real device, so zip 136 hid the controls and split the device test into one test per mode with a 45 s limit that prints the FFmpeg log. The user reported all tests green on 2026-11-10, so zip 141 shows the controls again (`KenBurnsAvailability.EXPORT_VERIFIED = true`; set it to false to hide them). The device tests check that every mode renders and keeps the picture size, not what the motion looks like: check an exported clip by eye.
