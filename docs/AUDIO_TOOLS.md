# Audio tools: volume and fades

Per clip, for a video clip's own sound and for audio clips. One undo step per change; saved with the project (`volume`, `fadeInMs`, `fadeOutMs` on the clip, written only when they differ from the default, so older projects open unchanged; out-of-range values are clamped on load).

- `ClipSound(volume 0–200 %, fadeInMs, fadeOutMs ≤ 10 s)`; `ClipSound.fit` trims fades to the clip (fade-in wins).
- Splitting a clip keeps the fade-in on the first half and the fade-out on the second.
- Export: per clip `volume`, then `afade` in / out (the fade-out starts at the clip's end on the timeline), placed after the clip is cut to length and before any fade-through-black from a transition; audio clips fade before they are delayed into place.
- Preview: a video clip's volume up to 100 % is applied; **fades and volume above 100 % are heard only in the export** (the panel says so).
- The Sound tool sits beside the older Volume tool (audio clips only), which is kept unchanged.
- `volume`, `afade`, `adelay`, `amix`, `atempo` are checked against the device FFmpeg by `FilterAvailabilityTest`.
