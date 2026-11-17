# Accessibility and performance review (spec §43–45)

## Contrast (brand palettes, computed WCAG 2.x ratios)
Studio palette (Pass 4). Text pairs (on-colour / colour) all pass 4.5:1: light 5.3–17.2, dark 6.3–16.7 (primary, secondary, tertiary, error, their containers, surface, surface variant, highest container). White on each signature-gradient stop: 5.1 / 5.1 / 4.9.
Non-text outlines pass 3:1 on the surface (light 3.9, dark 4.5). Clip-block outlines on the timeline are decorative (blocks are distinguished by fill and label).
Not covered: dynamic (wallpaper) colours on Android 12+, which Material generates with the same contrast rules; timeline clip blocks (use the matching on-colour, see `EditorTimeline`).
Device checks still worth doing: TalkBack walk-through of Home → Convert → Editor (timeline custom actions exist for stepping the playhead and selection), font scale 200%, and high-contrast text.

## Performance
- Thumbnails: disk-cached JPEGs (`DiskThumbnailStore`, trimmed at start), at most 2 decodes at once, identical requests de-duplicated, undecodable sources not retried.
- NEW: decoded timeline pictures are kept in a 24 MB in-memory LRU (`LruMemoryCache`) so scrolling tiles out and back in does not decode JPEGs again; waveforms in a 4 MB LRU.
- Waveforms: FFmpeg decodes to 8 kHz mono PCM (16 KB/s of audio, temporary file) and the stream is reduced to 20 peaks/s; stored on disk.
- Timeline draws only tiles inside the visible window (`ThumbnailPlanner.visible`).
- Export runs in WorkManager with progress throttled by `FfmpegProgressTracker`; filter graphs scale/pad each clip to one canvas, so memory is bounded by the canvas size, not source size.
Device checks worth doing: a 30+ minute 4K file in the editor (scroll/zoom smoothness, memory in Android Studio Profiler), and export of the same.
