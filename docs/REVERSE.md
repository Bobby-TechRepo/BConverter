# Reverse clip

A video clip can be played backwards (picture and its own sound). One undo step; saved with the project (`"reversed": true`, written only when set).

- Export: `reverse` on the scaled picture and `areverse` on the sound, before any speed change.
- **Memory limit:** FFmpeg's `reverse` keeps every frame of the clip in memory. `ReversePolicy` allows a clip only while its frames at the export's picture size fit 256 MB — about 2.9 s at 1080p/30 fps, about 6.5 s at 720p. A longer clip is refused (message with the allowed length: trim first). The same check runs when exporting.
- Splitting a reversed clip puts the later part of the source first, so the pieces still play in the right order.
- **Preview plays forward** (the player cannot play backwards); the export is reversed.
- `reverse` and `areverse` are checked against the device by `FilterAvailabilityTest`.
