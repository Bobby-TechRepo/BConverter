# Subtitles and text overlays — progress

## Stage 1: subtitle foundation (done)
Package `domain/subtitle`:
- Models: `SubtitleTrack` (cues, named styles, language, track `offsetMs`, source format, play resolution), `SubtitleCue` (start/end ms validated, text with `<i> <b> <u>` markup, style name, kept ASS override tags), `SubtitleStyle`, `SubtitleAlignment` (numpad 1–9).
- `SubtitleCodecs`: `detect` (by content, then extension), `parse` (SRT, WebVTT, ASS, SSA, TTML; malformed blocks are skipped with warnings, an unusable file fails with `Empty` / `UnknownFormat` / `Malformed`), `serialize` (applies the track offset; cues sorted), `lostInConversion` (named styles and positioning that SRT/WebVTT/TTML cannot hold, to show before converting).
- ASS/SSA: styles, colours (alpha inverted), alignment (SSA numbering converted), centisecond timing, `\N`, inline `\i \b \u` as markup, leading override tags kept per cue (`\an8\pos(..)`), other inline tags dropped with a `TAGS_SIMPLIFIED` warning.
- TTML: clock and offset times, `dur`, `<br/>`, italic/bold/underline spans; frame-based times are rejected (no frame rate known).
- Timing: `SubtitleTiming.validate`; offsets never rewrite cues, `effectiveCues()` applies them (trimming at zero, dropping cues that end before it).

## Stage 2: subtitle extraction (done; device tests pending)
- FFprobe now reports each subtitle stream's index, codec, language, title, default/forced flags and kind (text / image / unsupported).
- `SubtitleExtractionPlanner` decides outputs: text -> SRT, ASS, WebVTT; PGS -> SUP or MKS; DVD/DVB bitmaps -> MKS. Same codec is stream-copied, otherwise re-encoded. Converting ASS to SRT/WebVTT warns that styling is lost.
- SSA output is not offered (FFmpeg writes ASS); TTML extraction is not offered.
- Image subtitles are saved as image files. OCR is designed (`SubtitleOcr`, `UnavailableOcr` default) but no engine ships, so text conversion of image subtitles is NOT available.
- Several tracks are saved to a folder you choose, one job each, named `<name>.<lang>[.forced].<ext>` (duplicates get `.2`, `.3`). A failed track does not stop the others. Cancel stops the current track.
- Tests: JVM unit tests for model/planner/validator/names/batch/command/engine/executor/probe parsing; `SubtitleExtractionIntegrationTest` (device) builds an MKV with SRT+ASS and checks outputs with FFprobe.

## Stage 3: editor subtitles (done; device and UI tests pending)
- `SubtitleEditor`: add, edit text, move, retime, duplicate, delete, split, merge, track offset. Pure, unit-tested.
- `SubtitleImporter`: reads SRT/ASS/SSA/WebVTT/TTML (also by content when the extension is wrong), handles UTF-8/UTF-16 BOMs and falls back to Latin-1, refuses files over 5 MB.
- Tracks are saved inside the project (`subtitleTracks` in the project JSON; old projects open with none) and restored on reopen.
- Editor: a Subtitles tool (import, new empty track, rename, shift whole track, add cue at playhead, remove track), one timeline lane per track (tap a cue to edit, long-press and drag to move), a cue dialog (text, start, end, split at playhead, merge with next, duplicate, delete). Several tracks are supported. Cue times are stored times; the track offset is applied when shown, so shifting never rewrites cues.
- Not part of undo/redo yet (subtitle edits are saved immediately but cannot be undone).
- Subtitles are NOT shown in the preview and NOT exported yet (Stage 4 and 8).

## Stage 4: styling and export (done; device tests pending)
- **Styling:** six presets (classic, bold yellow, boxed, cinematic, top, large) and an Appearance dialog per track: size, bold/italic/underline, box, text and outline colours, outline, shadow, position (9 places), distance from the edge. ASS styles round-trip; imported ASS styles are kept.
- **Export mode per track:** separate track (soft), burned into the picture, or left out. A track is exactly one of these, so nothing is exported twice.
- **Soft subtitles by container:** MP4, MOV, 3GP use mov_text; MKV uses SRT, or ASS when the track is styled; WebM uses WebVTT. AVI and MPEG-TS cannot carry text subtitles: export is refused with a message (burn the track, switch it off or change the container). Styling that a container cannot hold is announced in the export dialog before exporting.
- Language (2- or 3-letter, written as ISO 639-2), title, default and forced flags are written to each embedded track. Only one track can be default.
- **Burned subtitles:** each track is written as an ASS file and drawn with the `subtitles` filter after all clip scaling, fades and crossfades, so it is never scaled or faded with the clips. Paths are escaped for both FFmpeg parsing levels (checked against real FFmpeg). Fonts come from `/system/fonts` for now; the font manager (Stage 6) will add downloaded fonts.
- Temporary subtitle files live in the staging directory and are deleted with the job.
- Tests: planner, language codes, style editing, command construction, exporter file writing, serializer. `SubtitleRenderIntegrationTest` (device) renders MKV/MP4/WebM with soft tracks, flags, two tracks, styled ASS, and compares frames for burned subtitles.
- Limits: burned text depends on the fonts FFmpegKit/libass can find on the device; if a preset font is missing libass falls back to its default. Check on device.

## Not yet done
Stages 5-8 (text overlays, fonts, overlay editor, preview and render of overlays, animations), 9 (persistence/regression/UI tests/performance). Subtitles are still not drawn in the editor preview (Stage 8).

## Subtitle panels — step 23 (batch 8)
The Subtitles tool and the cue editor are now panels inside the editor rather than dialogs, so a line can be read against the picture while it is written. A cue tapped on the timeline opens on top of whatever panel is open; Apply or Cancel brings that panel back (adding a cue from the Subtitles panel returns to it). The cue panel keeps Apply / Cancel because text and times are applied together. The per-track Appearance settings are still a dialog.
