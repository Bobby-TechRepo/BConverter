# Subtitles and text overlays — Definition of Done check (zip 100)

Evidence: **U** = JVM unit tests, **D** = device (instrumented) tests, **F** = real FFmpeg run (device test or ffmpeg 6.1 on desktop), **R** = reasoning only / not yet verified on a device.
As of zip 92 the user confirmed all unit and UI tests green; later zips (93–100) added and fixed tests, most of which were also reported green. Items marked ⚠ need a device run of the newest zip.

| Requirement | Status | Evidence |
|---|---|---|
| Extract supported subtitle streams from videos | Done (text streams to SRT/ASS/WebVTT, image streams copied as SUP/MKS) | U, D, F |
| Image subtitles → text (OCR) | **Not implemented**, by design; UI says so, extension point exists (`UnavailableOcr`); image subtitles are never offered as SRT | U |
| Import external subtitle files | Done | U, D |
| Edit subtitle content and timing | Done (cue text, retime, move, split, merge, duplicate, delete) | U, D |
| Synchronise subtitle tracks | Done (track offset; cues never rewritten) | U |
| Preview subtitle styling | Done | U, D |
| Embed selectable subtitles (soft) | Done: MKV (SRT/ASS), MP4 (mov_text), WebM (WebVTT), language/title/default/forced | U, F |
| Burn subtitles into frames | Done (libass); frame comparison on device | U, F |
| Multiple text overlays; position, rotate, scale, align | Done | U, D |
| Fonts, sizes, colours, weights | Done | U, D |
| Browse and use system fonts | Done | U, D |
| Search, preview, download, use Google Fonts | Done (on request, https to Google only; offline cache) | U; live test skips offline |
| Every font preview uses the correct typeface | Done in the Fonts screen and the text panel; fresh downloads refresh the preview | D ⚠ for the refresh case |
| Outline, shadow, glow, background | Done in preview and export | U, D, F |
| Timing and layer order | Done (timeline rows, panel, front/back) | U, D |
| Preview and export text overlays | Done; export draws each overlay as a cached PNG (this FFmpegKit has no `drawtext`) | U, F, D ⚠ for animated export |
| Animations | Fade, slide ×4, scale; export fades are linear (preview easing differs for fades only) | U, F (desktop), D ⚠ |
| Projects keep all changes after restart | Done (optional JSON fields; no Room migration needed; old projects open) | U |
| Undo/redo | One order across timeline, subtitles and text | U, VM tests |
| Phone and tablet UI tests | Subtitle/text tools on both layouts added in zip 100 | D ⚠ (new) |
| Existing functionality intact | Regression suites green on zip 92 | U, D |
| Performance | Generous-limit tests with 500–1000 overlays; layer cache | U |

## Known limitations and open items
- OCR is not implemented.
- Preview text (Compose) and exported text (Android text engine) can differ by a pixel or so; export fades are linear.
- `Media3PlayerEngineTest.playback_can_be_replayed_after_it_ends` is `@Ignore`d because it stalled the device run; cause unknown.
- TalkBack walk-through and 30+ minute 4K media checks are still to be done on a device.
