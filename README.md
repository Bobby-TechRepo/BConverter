# BConverter
Spec-driven, TDD-first Android video converter/editor (`io.bsoft.bconverter`).

## Setup
1. Open in Android Studio (JDK 17). Run `gradle wrapper` once to generate the Gradle wrapper.
2. Unit tests:         `./gradlew :app:testDebugUnitTest`
3. Instrumented tests: `./gradlew :app:connectedDebugAndroidTest`

## TDD log
- Commit 1 = RED (tests only; compilation fails because production types don't exist).
- Commit 2 = GREEN (minimum production code).
Milestone 1 scope: AppConfig, OrientationPolicy, MediaSource, MediaMetadata, ProcessingState machine,
MediaEngine interface + FakeMediaEngine, Hilt DI, Navigation, Home screen.

## Phase 2 — File management (done)
`InputResolver` (content:// -> staged File, FileSource used in place), `MediaFileValidator`,
`StagingDirectory` (sanitised names, stale cleanup), `AndroidContentReader`, SAF picker flow
(`PickInputScreen`, wired to Convert Video / Extract Audio), typed `InputError`s with user-facing messages.

## Phase 3 — Media probing (parser done; native runner pending)
`FfprobeJsonParser` (container/brand detection, rotation, cover-art exclusion, frame-rate and duration fallbacks,
"N/A" tolerance), extended stream models, `MediaProber` (resolve -> ffprobe -> parse, releasing staged input on every failure path).
Fixtures in `app/src/test/resources/ffprobe/` are hand-authored to ffprobe's documented JSON schema;
replace/add real captures once the native integration test runs.

## Phase 4 — Codec capability system (done)
`VideoCodec`/`AudioCodec`, `ContainerCompatibility` (conservative container/codec table), `MediaCapabilityRepository`
(spec §13) with `DefaultMediaCapabilityRepository` merging availability reports (OR), `MediaCodecCapabilitySource`
(device MediaCodec) and `FfmpegCapabilities` (parses `ffmpeg -encoders/-decoders`; experimental encoders excluded).
Not yet wired into DI/UI: it needs the FFmpeg runner to supply the FFmpeg report (Phase 5).

## Phase 5a — Conversion logic (done; native execution pending)
`ConversionSettings` model (stream copy / encode plans, CRF or bitrate), `ConversionPresetFactory`
(Small/Balanced/High/Original — see table in its KDoc), `ConversionRequestValidator` (reports all issues),
structured `DefaultFfmpegCommandBuilder` (+ `EncoderNames` overridable per build), `FfmpegProgressTracker`
(`-progress` blocks and classic status lines), `ConversionSession` (state machine, cancellation, cleanup).
Not yet done: converter screens/ViewModel, real FFmpeg execution, integration tests (need the distribution decision).

## FFmpeg decision (resolved)
FFmpegKit community fork, free LGPL artifact `ffmpeg-kit-full:8.1.7` — details, licensing and caveats in `docs/FFMPEG.md`.
arm64-v8a only: run instrumented/native tests on an arm64 device or emulator.

## Phase 5b — Real engine (verified on device)
`FfmpegMediaEngine`, `CapabilityProvider` (runtime encoder discovery, CRF awareness, MediaCodec cross-check), FFmpegKit glue.

## Phase 5c — Converter flow (done)
`ConverterViewModel` (pick -> probe -> configure -> destination -> convert -> save; retry after failure keeps the staged input),
`ConverterScreen` (presets, container/codec dropdowns, file name, progress, cancel), `AndroidOutputPublisher`
(temp file -> user-chosen SAF document, streamed). Advanced numeric settings (resolution, fps, bitrate/CRF, audio bitrate,
sample rate, channels) are supported by the model/validator/builder but not yet exposed in the UI.

## Phase 6 — Audio extraction (verified on device)
`AudioFormat`, `AudioExtractionPlanner` (copy when the codec matches, else encode), validator, `buildAudioExtractionCommand`,
`AudioExtractorViewModel` + screen.

## Phase 13 (+ §36) — Background export and storage checks (done, needs device verification)
- Jobs are `ExportJob`s in an app-wide `ExportRegistry`; `ExportJobExecutor` runs engine -> temp file -> publish, always cleans the
  temp file, and releases the staged input only if the screen has detached. A job therefore survives leaving the screen.
- `ExportWorker` (WorkManager, expedited) runs the executor as foreground work with a progress notification and a Cancel action;
  foreground type is `mediaProcessing` on Android 15+, `dataSync` on 10-14 (both limited to 6 h / 24 h by the OS).
  After process death the job cannot resume (FFmpeg runs are not resumable): the worker reports it as interrupted.
- `StorageEstimator` / `StorageGuard`: bitrate x duration (+5 %) estimate vs free space on the cache volume, blocking start with
  a readable message. The final copy to the destination volume is not checked.
- Notification permission (Android 13+) is requested when the options screen opens; jobs run without it.
Not done: re-attaching a new screen to a running job, retry-from-notification, advanced settings UI, Phase 7 (preview).

## Phase 7 — Media3 preview (done, needs device verification)
`PlaybackController` (pure state machine: play only when ready, clamped seeking, replay after the end, errors) in front of a
`PlayerEngine` seam; `Media3PlayerEngine` (ExoPlayer, audio focus, position polling) is the real implementation.
`PreviewPlayer` + `PreviewControls` show the staged source on both options screens (pause on app stop, released on leave).
Media3 is pinned to 1.5.1 to match compileSdk 35; bump with compileSdk later. This is also the base for the editor's preview.
Next: timeline model and operations (split/trim/delete/move/speed) with undo/redo, then rendering.

## Phases 8-10 — Editor core (done, pure logic)
`domain/timeline`: immutable `Timeline` (gap-free video track + overlapping-capable audio tracks, derived clip durations),
`TimelineOperations` (split, trim, delete, insert, move, speed, volume, replace; typed `EditError`s), command objects with
snapshot undo/redo and a size-limited `CommandHistory`, and `DefaultTimelineEditor` (selection, playhead, undo/redo state).
No Android or FFmpeg dependencies. Next: timeline-to-preview mapping (Phase 11), the Compose timeline UI, project persistence,
and rendering (Phase 12: RenderPlanner -> FFmpeg filter graph).

## Phase 12 — Rendering (done, needs device verification)
`domain/render`: `RenderPlanner` (project -> `RenderPlan`: inputs with seek/duration, canvas, audio layout, overlays),
`FilterGraphBuilder` (per-clip scale/pad/setpts/fps chains + concat; per-clip audio with chained `atempo`, generated silence for clips
without audio, overlays with volume/delay, `amix` without rescaling), `DefaultFfmpegCommandBuilder.buildRenderCommand`,
`FfmpegMediaEngine.render` (project id = job id). Audio running past the video is cut; sources must be staged files.
Device tests render real projects (trim + speed + overlay, slow-down, audio replacement, silent) and verify with ffprobe.
Next: export job for projects (WorkManager), timeline-to-preview mapping (Phase 11), timeline UI, project persistence.

## Phase 13b — Project export jobs (done)
`ExportTask.Render` runs a project through the same registry/executor/WorkManager path as conversions (foreground notification,
cancel, temp-file clean-up, releases every staged input when detached). `StorageEstimator.estimateRender` and `ProjectExporter`
(plan check + storage check + queue) are what the editor's Export button will call. Next: editor ViewModel, preview mapping
(Phase 11), timeline UI, project persistence.

## Editor core wiring (done, no screen yet)
`SourceLibrary` (clips reference original content URIs; staged copies serve preview/render; one staging per file),
`Timeline.mapSources`, `ExportSettingsFactory` (Small/Balanced/High, CRF or bitrate to match the build, canvas cap),
`UriPermissionPersister`, and `EditorViewModel` (import video/audio, replace, every edit, undo/redo, export as a background
job that outlives the screen). Next: timeline-to-preview mapping (Phase 11) + the Compose timeline screen, then project
persistence (Room), recent projects, and tablet layouts.

## Phase 11 — Timeline → preview mapping (done, pure logic; verified on JVM)
`PreviewMapper`: `positionAt` (timeline time -> clip + source time, speed-aware, boundary belongs to the later clip),
`timelineTimeOf` (inverse, clamped), `segments` (ordered clipped/speed-adjusted pieces for the player), `activeAudioAt`.
Next: feed `segments` to Media3 (playlist of clipped items, playback speed per item) behind `PlayerEngine`, then the Compose timeline screen,
Room persistence + recent projects, tablet layouts, Settings/About, thumbnails/waveforms (§27/§28, not started).

## Phase 14 — Persistence (domain verified on JVM; Room layer needs device verification)
`domain/project`: `StoredProject`, `ProjectRepository` (atomic `save`), `ProjectSerializer` (versioned JSON; decoding never throws —
bad/truncated/unknown-version/invalid-clip data is `Corrupt`), `ProjectAutosaver` (skips unchanged saves, keeps creation time, failed saves are
retried with the previous version intact), `ProjectOpener` + `MissingMediaChecker` (a project with missing media still opens and reports which clips are affected).
Probe results are not persisted; sources are probed again on open. `data/database`: Room `ProjectEntity`/`ProjectDao`/`AppDatabase` (summary columns so the list
never decodes payloads; a corrupt row stays listed so it can be deleted), `RoomProjectRepository`, `AndroidSourceAvailability`, `ProjectModule` (Hilt).
Instrumented tests: `RoomProjectRepositoryTest`, `AndroidSourceAvailabilityTest`.
Next: Recent Projects screen + ViewModel, wire autosave/open into `EditorViewModel`, then the Compose editor screen.

## Phase 14b — Recent Projects + editor autosave/reopen (JVM-verified: `TimelineEditor.load`; the rest needs a device/Robolectric run)
- `TimelineEditor.load(timeline)`: replaces the timeline and resets selection, playhead and undo history.
- `EditorViewModel`: autosaves after every successful edit, undo/redo, rename and export-choice change (newest pending save only, one at a time;
  an untouched new project is never stored; failures show `ProjectSaveFailed` and the next change retries). `openProject(id)` restores timeline,
  name, id and container, re-stages the sources, lists unreadable ones in `missingSources` (cleared when the clip is replaced), and reports
  `ProjectNotFound` / `ProjectCorrupt`. The export preset is saved inside the settings but is not restored on reopen (only the container is).
- `feature/projects`: `ProjectsViewModel` + `ProjectsScreen` (loading / empty / load-failed with retry / list with clip count, duration, date / delete with confirmation).
- Navigation: `editor?project={id}` (optional argument; the screen is still a placeholder) and `projects`.
Next: the Compose editor screen (phone portrait first, then tablet landscape): preview fed by `PreviewMapper.segments`, timeline lanes, tools, missing-media banner, export.

## Phase 15 — Editor screen (pure logic JVM-verified; Compose/Media3/ViewModel need a device run)
- `TimelineScale` (zoom, ruler ticks, clip blocks, hit-testing), `EditorTools.availability`, `EditorLayoutMode` — pure, tested.
- `TimelinePlaybackController` over a `TimelinePlayerEngine` seam (31 tests with a fake engine); `Media3TimelinePlayerEngine` is the real ExoPlayer implementation (video playlist + per-clip audio players).
- UI: `EditorScreen` (phone stacked / tablet with project panel), `EditorTimeline`, `EditorDialogs` (rename, trim, speed, volume, export), `EditorMessages`.
- Routing: `editor?project={id}` opens a saved project; missing media is flagged and blocks export.
- Tests to run: `EditorContentTest` (androidTest), `EditorViewModelTest`, then `connectedDebugAndroidTest`.
- Known limits: preview volume caps at 100%; audio beyond the video end is not previewed (same as export); `keepOriginalAudio` ignored in preview; no playhead dragging yet (tap-to-seek, transport slider, zoom buttons); export preset not restored on reopen.

## Phase 16 — Thumbnails and waveforms (spec §27/§28; logic JVM-verified, Android pieces need a device run)
- Thumbnails: `ThumbnailPlanner` (which frames a clip block needs, bucketed to 500 ms, only tiles near the viewport), `ThumbnailService` (disk cache, identical requests share one decode, max 2 at once, undecodable sources are not retried per tile), `DiskThumbnailStore` (size-budgeted JPEG cache), `MediaMetadataThumbnailGenerator` (platform decoder).
- Waveforms: `WaveformReducer` (streaming PCM → peaks), `Waveform.bars`, `WaveformCodec`, `WaveformService` + `DiskWaveformStore`, `FfmpegWaveformGenerator` (8 kHz mono PCM through the FFmpeg seam, temp file always removed).
- All failures (missing, corrupt, FFmpeg error, generator exception) become results; a clip with no decodable media simply stays a plain block.
- The timeline draws frames on video clips and a waveform on audio clips via `TimelineMedia` (injected through `TimelineMediaViewModel`).
- Test fixture: the sample MP4 is now a bundled asset (`androidTest/assets/sample-160x120.mp4`) instead of being generated by FFmpeg.

## Phase 16b — Pinch zoom and video sound
- Pinch is detected before the scroller sees the touches and is not restarted per zoom step; the timeline exposes its zoom as semantics ("Zoom 100%").
- A video clip's own sound is drawn as a strip along the bottom of its block.
- Tests: `EditorContentTest` covers pinch out/in, the zoom readout, and selecting a clip whose block carries the strip.

## Planned (not started)
- **Detach audio**: show a video's own sound as a separate clip in the audio section (with the video muted), as CapCut-style editors do. Needs a per-clip "original audio muted/detached" state in the timeline model, `RenderPlanner`/preview changes, and an undoable edit. Deferred by agreement.
- Converter advanced-settings UI; tablet/phone polish and accessibility tests; FFmpeg third-party notices check; Gradle wrapper; version upgrades.

## Phase 17 — Settings and About (logic JVM-verified; screens and DI need a device run)
- Settings: theme (follow system / light / dark), dynamic colours (Android 12+), and clearing the regenerable preview/waveform cache with its size. Settings persist in SharedPreferences behind a `SettingsStore` seam; the theme is applied in `MainActivity`.
- The cache clear only touches `thumbnails`, `waveforms` and `waveform-tmp`; staged copies of imported media are never deleted.
- About: app version, FFmpeg build version, the LGPL statement and a list of third-party components with their licences (opens each project page). `AboutTest` fails if a plain GPL licence is ever listed (spec §8).
- Before release: verify the "Libraries bundled with FFmpegKit" entry against the FFmpegKit AAR's own notices (docs/FFMPEG.md).
- Tests: `SettingsTest`, `CacheManagerTest`, `ByteFormatTest`, `AboutTest` (pure), `SettingsViewModelTest`, `AboutViewModelTest`, androidTest `SettingsContentTest`, `AboutContentTest`.

## Phase 18 — Converter advanced settings (spec §18; logic JVM-verified, UI/VM need a device run)
- `AdvancedOptions` (overrides on top of a preset), `AdvancedChoices` (what may be offered for the current source/codec: CRF only when the encoder honours it, heights below the source, frame rates up to the source, profiles/levels/pixel formats per codec, audio bitrate/sample rate/channels per codec), `AdvancedOptionsApplier`.
- Copied streams are never changed; overrides the codec/container no longer offers are dropped (`sanitized`); choosing a preset starts afresh.
- UI: a collapsed "Advanced settings" section in the converter form. Every control starts at "Preset default".
- Caveat: profile/level are passed straight to the encoder. Hardware (MediaCodec) H.264/HEVC encoders may reject some values on some devices; the existing failure path reports it.
- Tests: `AdvancedOptionsTest` (20, pure), new `ConverterViewModelTest` cases, androidTest `AdvancedSettingsSectionTest`.

## Phase 19 — critical-flow UI tests (spec §42)

Two journeys run the real ViewModels and screens against the fake media tools (no FFmpeg, no pickers):

- `flows/ConverterFlowTest` — select video → choose container and codec → preset → start → "Converting…" shown → completion shown; plus cancelling.
- `flows/EditorFlowTest` — add video → split → trim → delete → add audio → speed → undo → redo → export → "Export finished" shown.

The androidTest source set reuses `src/test/kotlin/.../fakes` and `src/test/resources` (see `app/build.gradle.kts`), so the fakes exist once.
System dialogs are replaced by direct calls (the picked Uri is passed straight to the ViewModel). Run with `./gradlew connectedDebugAndroidTest`.

## Phase 20 — Accessibility: timeline without gestures (spec §43; logic JVM-verified, semantics need a device run)

Audit result: content descriptions, 48 dp tool buttons, live regions for processing/export status and non-colour cues (selected/missing are also spelled out) were already in place. The gap was the timeline, which could only be seeked and selected by tapping.

- `domain/timeline/PlayheadNavigator` (pure, tested): step the playhead, jump to the previous/next clip edge, select the previous/next clip.
- The playhead node now exposes its position as an adjustable value (`ProgressBarRangeInfo` + `setProgress`) and TalkBack custom actions: back/forward 1 s, previous/next clip edge, previous/next clip. Very short clips (narrower than a touch target) can be selected this way.
- Compose tests in `EditorContentTest` cover the range, set-progress, the spoken position and each action.

Still open in §43: a contrast review of the theme colours and a TalkBack walk-through on a device.

## Phase 21 — Wrapper, label colours, notice check

- Gradle wrapper added (`./gradlew`), pinned to Gradle 9.3.0, the version the project has been built and tested with so far. AGP 8.7.3 officially supports older Gradle versions, so upgrading AGP is on the version-upgrade list.
- Clip labels on the timeline now use the matching "on" colour of their background (spec §43 contrast); all colours come from Material 3 schemes.
- **FFmpegKit notices (checked against the fork's README; AAR contents still worth a glance).** The "Libraries bundled with FFmpegKit" entry in `About.kt` is generic. After a Gradle sync, find `ffmpeg-kit-full-8.1.7.aar` in `~/.gradle/caches/modules-2/files-2.1/dev.ffmpegkit-maintained`, unzip it, and compare its bundled licence/notice files and the library list in its POM or README with that entry; update `ThirdPartyNotices` accordingly.

## Phase 22 — Tablet layouts (spec §37; Compose only, needs a device run)

- `core/ui/ReadableWidth.kt`: `Modifier.readableWidth()` caps content at 640 dp and centres it; used by Home, Projects, Settings, About, input picker, audio extractor and the converter's non-form stages. No effect on phones.
- Converter form on a tablet (smallest width ≥ 600 dp): two panes, source and preview on the left, options and Convert on the right, each scrolling on its own. Phones keep the single column.
- Editor already had its own tablet layout. Orientation lock (phone portrait, tablet landscape) was already in `MainActivity`.
- Tests: two layout tests in `ConverterContentTest` force the smallest width and compare positions.

## Phase 23 — Share (spec Phase 16; ViewModel/UI tests need a device run)

- `domain/model/SavedOutput(uri, mimeType)` is recorded when a conversion, audio extraction or export completes (`ConverterUiState.saved`, `AudioExtractorUiState.saved`, `EditorUiState.savedExport`); cleared when another export starts or the banner is dismissed.
- The completion screens show a **Share** button (the editor's "export finished" banner too). `core/ui/ShareOutput.kt` builds an `ACTION_SEND` chooser with the file's content Uri, its MIME type and read permission, so the file is never copied.
- Tests: new ViewModel tests (written first), UI tests on all three screens, `ShareOutputTest` for the intent.

## Phase 24 — Error-handling audit (spec §46; needs a device run)

Audit of the §46 list (unsupported codec, corrupt media, missing audio, invalid container, insufficient storage, permission failure, invalid URI, FFmpeg failure, cancellation, missing source media): every condition already has a unit or ViewModel test. The gap was proof that each error *reaches the user as plain language*. `core/ErrorMessagesTest` renders every `InputError`, `ConverterMessage` and `EditorMessage` variant (including every `EditError`, `ProbeFailure` and `ExportRejection`) and fails on blank text or technical names/stack-trace fragments. New error variants must be added to its lists.

## Phase 25 — File management and notices

- **Rename and duplicate projects** (`domain/project/ProjectManager`, pure and tested): ⋮ menu on each Recent Projects row. Duplicate saves a copy ("Holiday copy", "Holiday copy 2", …) with a new id and the same timeline; failures (invalid name, missing/damaged project, save error) are results shown in plain language, never exceptions. Delete was already there.
- **FFmpegKit notice**: the "Libraries bundled with FFmpegKit" entry now names the actual libraries of `ffmpeg-kit-full` (LGPL-3.0) per the fork's README, and `AboutTest` fails if one goes missing or if a GPL-only library (x264, x265, xvid, vidstab) is claimed as bundled. Re-check on every FFmpegKit upgrade.

## Phase 26 — Upgrade stage 1

Room 2.6.1 → 2.7.2 (see `docs/UPGRADE.md` for the full staged plan and why the rest waits for the toolchain jump). Needs a build and the full test run.

## Phase 27 — Upgrade stage 2 (toolchain)

AGP 9.2.0, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, compileSdk 36, built-in Kotlin (no `kotlin-android` plugin). See `docs/UPGRADE.md` for what changed and what to do if the build complains. Needs a full build and test run; send any build errors.

## Phase 28 — Subtitles and text overlays

Nine stages (details, limits and test status in `docs/SUBTITLES.md` and `docs/TEXT_OVERLAYS.md`): subtitle extraction from videos (text and image tracks; OCR is designed, not implemented), importing, editing and styling subtitles in the editor, export as soft tracks (MKV, MP4, WebM) or burned in, text overlays (fonts installed or downloaded from Google Fonts on request, outline/shadow/glow/background, drag/pinch/twist in the preview, numeric panel, timeline text rows, fade/slide/scale animations), rendering overlays into exports as cached transparent pictures laid over the video with FFmpeg's `overlay` filter (the bundled FFmpegKit has no `drawtext`), and one undo/redo order across timeline, subtitles and text.

## Phase 29 — Filters and image overlays

Per-clip colour filters with presets and sliders (`docs/FILTERS.md`), and image overlays that work like text overlays and share their layer order (`docs/IMAGE_OVERLAYS.md`).

## Phase 30 — Crop, rotate and flip
Per-clip crop (edge sliders and shapes), quarter turns and mirroring; preview via Media3 effects, export via `crop`/`transpose`/`hflip`/`vflip`; canvas follows the first clip's transformed size. See `docs/CROP_ROTATE_FLIP.md`. Logic is unit-tested locally; panel, preview effects and device renders need a device run (`ClipTransformPanelTest`, `ClipTransformRenderIntegrationTest`).

## Phase 31 — Audio tools
Per-clip volume (video's own sound too) and fade in/out; export via `volume`/`afade`. See `docs/AUDIO_TOOLS.md`. Logic is unit-tested locally; the panel and device audio filters need a device run (`ClipSoundPanelTest`, `FilterAvailabilityTest`).

## Phase 32 — Reverse clip
Reverse a video clip (picture and sound) in the export; memory-bounded by `ReversePolicy`; preview plays forward. See `docs/REVERSE.md`.

## Phase 33 — Freeze frame
Freeze the picture at the playhead for 0.5–10 s (a freeze clip between the split halves); export holds a true still, preview shows slow motion. See `docs/FREEZE_FRAME.md`.

## Phase 34 — Picture in picture
Stage 1: model, undo, persistence and export of a second video laid over the main one. Stage 2: add from the picker, timeline lane, layout panel and live preview with drag/pinch. Stage 3 (Phase 36): fade in/out and a coloured border for overlay clips. Stage 4 (Phase 37): chroma key (green screen) with a keyed still-frame preview. See `docs/PICTURE_IN_PICTURE.md`.

Phase 50: the export quality and size are saved with the project (`exportChoices`, optional) and the "Made for" chip that matches the current shape/size/quality shows as picked. Phase 49: timeline markers for text/picture/overlay-video keyframes. Phase 48: "Made for" presets in the export dialog (YouTube 16:9, Reels/Shorts/TikTok 9:16, Instagram post 4:5, Square 1:1, WhatsApp status 9:16 small): one tap sets the canvas shape (one undo step), the export size and the quality; fit and background are left alone; size and quality are not saved with the project. Phase 47: slow zoom or pan on a video clip (Ken Burns; export only, see `docs/KEN_BURNS.md`). Phase 46: Export size (Automatic, Original, 1080p, 720p, 480p on the shorter side; never enlarges; chosen in the export dialog, not saved with the project). Phase 45: drag/pinch edits the keyframe at the playhead (overlay videos, text and pictures). Phase 44: smooth (eased) steps between keyframes. Phase 43: keyframes for text and picture overlays (position, zoom, opacity). Phase 42: keyframes for overlay videos (position, size, opacity through up to 16 points). Phase 41: overlay size change while gliding. Phase 40: overlay shapes (rounded, circle). Phase 39: overlay speed (0.25×–4×, sound follows). Phase 38: overlay glide (straight-line move to a second position, optional easing). Text, image and overlay layers are also clipped to the output frame in the preview.

## Phase 35 — Canvas and background
Output shape (Auto, 16:9, 9:16, 1:1, 4:5), Fit or Fill, and bar colour or blurred background; Canvas tool, framed preview, export, save/load, undo. See `docs/CANVAS.md`.

## Running the heavy FFmpeg instrumented tests
`ExportEndToEndTest`, `RenderIntegrationTest`, `FfmpegKitIntegrationTest` and `FfmpegWaveformGeneratorIntegrationTest` run real FFmpeg and are skipped on emulators by default (`HeavyMediaRule`): the FFmpegKit build is arm64-only, and on an emulator it can run so slowly that it starves the host machine (an IDE freeze was observed). Run them on a real arm64 device, or force them on an emulator with
`./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.heavyMedia=true`.

## Phase 51 — blurred-background preview
With a blurred canvas background the preview frame shows a soft still of the clip under the playhead behind the player (`BlurredBackdrop`); real blur on Android 12+. Device tests in `EditorContentTest`.

## Phase 52 — tidy imported pictures
At app start, imported overlay pictures that no saved project mentions and that are older than 24 h are deleted (`OverlayImageCleanup`, `OverlayImageCleaner`, `ProjectDao.payloads`). Newer ones are kept for editors that have not saved yet.

## Phase 53 — polish pass
Checked: no hard-coded UI strings in `feature/`; every icon-only control has a description. Found and fixed: the transition marker on the timeline was a 32 dp tap target; its tap area is now 48 dp (the drawn pill stays 32 dp). Device test in `EditorContentTest`.

Polish pass, part 2 (zip 146): editing a freeze frame (trim, speed, split, reverse, motion) used to say only "That change is not possible." Now it says why, and a wrong-kind-of-clip edit has its own message too (`msg_edit_freeze_frame`, `msg_edit_wrong_clip_type`, 5 languages, 612 strings). `ErrorMessagesTest` covers the freeze-frame case.

Polish pass, part 3 (zip 147): the "copy without re-encoding" switch row in the audio extractor was about 32 dp tall; it is now at least 48 dp (device test added). Other custom tap targets checked: timeline clip blocks fill the 48 dp lane; Settings, subtitle extractor and text-overlay rows already have 48 dp minimums; the canvas colour chips are 48 dp.

## Phase 54 — rotation keyframes (zip 148)
Text and picture overlay keyframes can turn the overlay (Turn slider, two-finger twist, preview, export via `rotate`). See `docs/TEXT_OVERLAYS.md`. New export filter: its device test has a 90 s limit; the first on-device run is the real check.

## Phase 55 — Premium "Studio" redesign
New design system (signature gradient, Plus Jakarta Sans, studio-dark editor), redesigned Home, Projects, editor chrome and transport controls, adaptive launcher icon and splash. Brand colours are now the default (dynamic colour opt-in). Fixed a compile error in `AboutScreen` (`IconBadge` was passed a `Modifier` in its `size` slot). See `docs/UI_DESIGN.md` (Pass 4). Needs a build and a device run.

## Phase 56 — Implementation plan, batch 1 (steps 1–4, 7–12)
- **API 36**: `targetSdk = 36`. Phones stay portrait; large screens are no longer locked (Android 16 ignores locks there; `OrientationPolicy.FOLLOW_DEVICE`). Predictive back is on by default for API 36 apps.
- **Room**: schemas exported to `app/schemas` (commit the generated JSON after the first build); migration policy in `AppDatabase` KDoc.
- **CI**: `.github/workflows/android.yml` (JDK 21 for Robolectric on SDK 36): unit tests, lint, minified release build, schema check.
- **Signing**: `keystore.properties` or `BCONVERTER_*` env vars; release builds also shrink resources.
- **Startup**: housekeeping runs on an `@ApplicationScope` coroutine scope instead of raw threads + `runBlocking`.
- **URI grants**: `UriGrantCleanup` / `AndroidUriGrantCleaner` release persisted grants no saved project uses, at app start.
- **Imports**: picks are queued (`Mutex`) instead of being dropped while another file is being added; subtitle imports share the queue.
- **Diagnostics**: `FfmpegKitExecutor` keeps the last 8 KB of each session's log (`LogTail`); failures carry it and are written to `files/diagnostics/ffmpeg-failures.log` (`FfmpegFailureLog`, 64 KB cap, on device only) and Logcat.
- **Process death**: `EditorViewModel` keeps the stored project id in `SavedStateHandle` and reopens it; an export destination delivered after the process was killed rebuilds the export (waiting for the reopen).
- **Localised defaults**: "Untitled project", "Subtitles N" and new text overlays come from resources (5 languages).
- Tests: `OrientationPolicyTest` (updated), `UriGrantCleanupTest`, `LogTailTest`, `FfmpegMediaEngineTest` (+1), `EditorViewModelTest` (+6). Needs a build and the full test run.

## Phase 57 — Implementation plan, batch 2 (steps 13, 14, 5)
- **Virtualised timeline (step 13, fixes B1 and P5)**: the timeline no longer lays out one child as wide as the whole project inside `horizontalScroll`. `TimelineScrollState` (a `ScrollableState` whose range the timeline sets) drives `Modifier.scrollable`; clips are composed only within ±1200 dp of the viewport (`TimelineWindow.COMPOSE_MARGIN_DP`), cut to that window (`TimelineWindow.clip`, so a 30-minute clip at maximum zoom is a few thousand dp wide, not a million), and placed from the scroll position in the layout phase, so scrolling repositions without recomposing. The ruler draws only visible ticks. Scroll semantics (`horizontalScrollAxisRange`, `scrollBy`) are kept, so TalkBack and `performScrollTo` still work. Labels now stick to the visible start of a long clip.
- **Stable editor callbacks (step 14, B2/P4)**: `EditorRoute` builds `EditorActions` once (`remember`) and reads the playhead and state through `rememberUpdatedState`, so children that take the actions skip recomposition during playback. The preview and timeline still update per frame, as they must.
- **Licence texts in the app (step 5)**: About → "Licence texts" lists LGPL 2.1, LGPL 3.0 (+ GPL 3.0 it incorporates), Apache 2.0, OFL 1.1 and MIT; each opens its full text (`assets/licenses/`, from the SPDX list and the font package).
- Tests: `TimelineWindowTest`, `BundledLicensesTest`. Needs a build and a device run: scroll, fling, pinch, drag clips, tap to seek, and a 30-minute file at full zoom.

## Phase 58 — Timeline fix, Settings redesign, implementation plan batch 3 (steps 15, 16)
- **Fix (step 13 follow-up)**: clips stopped at the screen edge because each lane was a `Box` as wide as the viewport, which squeezed its children to that width. Lanes are now `TimelineLane`, a layout that measures clips with no width limit, so a clip runs past the edge at its real length. Device test `a_clip_longer_than_the_screen_runs_past_the_edge`.
- **Settings redesign**: grouped tonal cards; theme picked from three live miniatures (system / light / dark) with a gradient ring; wallpaper colours as a card row; languages as a list with each language's own name and a check; Fonts and Storage rows with icon tiles. New icons `ic_palette`, `ic_language`, `ic_storage`. Texts and semantics unchanged.
- **Read media in place (step 15, P1/B5)**: `DefaultInputResolver` first asks `AndroidInPlaceOpener` for the document's own bytes: the file descriptor stays open and a per-session symlink in `cache/staging` points at `/proc/self/fd/<fd>`, which FFmpeg, ExoPlayer and the thumbnail decoder open in-process like a copy. Only seekable regular files that can be reopened qualify; anything else (cloud documents, providers that forbid it) is copied as before. Release closes the descriptor and removes the link. No extra storage, no wait on import.
- **Crash reporting seam (step 16, B10)**: `AppLog` (pure, pluggable sink; Logcat now, Crashlytics/Sentry later), `CrashRecorder` (last crash to `files/diagnostics/last-crash.txt`, chained to the previous handler), About → Diagnostics → "Copy report" (`DiagnosticsReport`: build/device line, last crash, FFmpeg failure log). Silent failures in export notification, publishing and overlay rendering now go through `AppLog.nonFatal`.
- Tests: `DefaultInputResolverTest` (+4), `DiagnosticsTest` (4), androidTest `AndroidInPlaceOpenerTest` (2), `EditorContentTest` (+1).

## Phase 59 — Implementation plan, batch 4 (steps 22, 17, 21)
- **Photo Picker, several videos at once (step 22)**: the editor's "Add video" uses the system Photo Picker (`PickMultipleVisualMedia`, up to 20, video only); the clips are appended in the order picked. Picture-in-picture still picks one. No storage permission needed.
- **Exports that survive the app closing (step 17)**: every export is recorded in Room (`export_jobs`, database version 2, hand-written `MIGRATION_1_2`). `ExportRegistry` tells a listener (`RoomJobHistory`) when a job starts and ends; at start-up, jobs still "running" from an earlier process are marked interrupted. New **Exports** screen (Home top bar): live progress with Cancel, Share for saved results (type asked of the provider at share time), outcome for each finished job, "Clear finished".
- **Adaptive layouts (step 21)**: `WindowLayout` gives Material width/height classes for the app's *window* (`LocalWindowLayout`, provided in `MainActivity`). The editor's side-by-side mode, the converter's two panes, a two-column Home (new work left, recent work right) and an adaptive project grid now follow the window, so split screen, foldables and rotation each get the layout that fits. The orientation policy still follows the device.
- Tests: `JobItemsTest` (6), `JobsViewModelTest` (5), `RoomJobHistoryTest` (3), `ExportRegistryTest` (+3), `WindowLayoutTest` (4), `EditorToolsTest` (+1), `EditorViewModelTest` (+1); androidTest `MigrationTest`, `JobsScreenTest` (3), `HomeScreenTest` (+2).

## Phase 60 — Implementation plan, batch 5 (steps 18, 19)
- **Long projects render in parts (step 18, P2)**: one FFmpeg run over the whole timeline opens a decoder per clip, so memory grew with the clip count. Projects with more than 6 clips are now rendered in parts (`SegmentedRender`): each part (≤ 6 clips, cut only at plain cuts, never inside a transition) goes to a high-quality MKV at the final canvas size and frame rate with PCM sound; the parts are joined without re-encoding (`FfmpegConcat`); a final pass over the joined file adds audio clips, picture-in-picture, text and subtitles. Progress spans both halves, cancellation works between runs, intermediates are always deleted. The storage check allows for the extra space.
- **Hardware export where it is exact (step 19)**: `HardwareRender` decides whether Media3 Transformer can reproduce a project exactly (plain cuts between unmodified clips, scaled into the canvas with black bars, MP4 with H.264/HEVC + AAC, no overlays, subtitles, PiP or audio clips). `RoutingMediaEngine` tries it first (device encoders, any ABI, little memory) and falls back to FFmpeg if it fails; encoder fallback is off so the output format never changes silently. New dependency `media3-transformer`.
- Tests: `SegmentedRenderTest` (9), `FfmpegMediaEngineTest` (+3), `HardwareRenderTest` (5), `RoutingMediaEngineTest` (5).

## Phase 61 — Implementation plan, batch 6 (step 20)
- **Composition preview engine (step 20, Labs)**: `CompositionTimelinePlayerEngine` plays the video track as one Media3 `Composition` through `CompositionPlayer`: each clip carries its own effects (`PreviewVideoEffects`, now shared with the standard engine) and speed, so cuts are gapless and a clip's look is right from its first frame. `CompositionTimeMap` converts the player's single clock to and from "segment N at source P", so `TimelinePlaybackController` is unchanged. Both engines implement `VideoPreviewEngine` (a `TimelinePlayerEngine` with a `player` for `PlayerView`). Off by default: Settings → Labs → "New preview engine", read when the editor opens. `PreviewSegment` now carries the source length (the composition player needs it).
- Tests: `CompositionTimeMapTest` (6), `SettingsTest` (+1).

## Phase 62 — Implementation plan, batch 7 (step 24, step 23 part 1)
- **Trim handles (step 24)**: the selected clip (video, audio or picture-in-picture) shows a handle on each edge; dragging one trims live and applies on release as one undo step (`EdgeTrim`, `TrimEdgeCommand`). The drag is a timeline distance turned into source time at the clip's speed; reversed clips trim from the other end of their source; the left edge of an audio or overlay clip moves its start too; edges stop at the source, at 0.1 s and at audio neighbours. Freeze frames have no handles.
- **Magnetic snapping (step 24)**: dragged edges, and audio / overlay clips being moved, stick to other clips' edges, the playhead and time zero within 12 dp, with a haptic tick (`Snapping`).
- **Ripple delete (step 24)**: deletes the selected clip and pulls later audio and overlay clips back by the same amount, so music stays in step with the picture (`RippleDelete`). Text overlays and subtitles keep their times.
- **Select several (step 24)**: "Select" in the toolbar starts picking; taps add or remove clips; the toolbar shows the count with Delete, Ripple delete and Done; one undo step (`DeleteClipsCommand`).
- **Contextual toolbar (step 23, part 1)**: only the tools that apply now are shown (undo, redo and adding media always are). With nothing selected: adding text, subtitles, pictures, overlays and the canvas; with a clip selected: that clip's tools. Inline tool panels (replacing the dialogs) are next.
- Tests: `TimelineGesturesTest` (14), `EditorViewModelTest` (+4), `EditorContentTest` (+3, four updated for hidden tools).

## Phase 63 — Implementation plan, batch 8 (step 23 part 2)
- **Tool panels inside the editor (step 23)**: every tool now opens a panel that is part of the editor's layout instead of a pop-up: trim, speed, transition, volume, sound, filters, crop, freeze length, picture-in-picture, canvas, text and picture overlays, the subtitle tracks and a subtitle cue. On phones the panel sits between the timeline and the tools bar (at most 34 % of the screen height, scrolling inside); on wide windows it takes the project panel's place beside the preview, so the timeline keeps its height. The preview stays visible and the timeline stays usable while a setting is changed. Rename and Export stay dialogs.
- **Behaviour**: the tools bar stays under the panel; the open tool is lit (and "selected" for TalkBack); tapping it again closes the panel, another tool replaces it. Back closes the panel first. Picking another clip shows that clip's settings in the same panel when the tool applies to it, and closes the panel when it does not (`EditorPanels.shown`). A cue tapped on the timeline is edited on top of the open panel; Cancel or Apply brings the panel back. While the keyboard is up for a panel's text field the tools bar steps aside.
- **No Apply step any more**: trim applies when a handle is let go, speed and transition kinds when tapped, volume and transition length when the slider is let go (one undo step each), as the other panels already did. Trim's Done also applies a change made with TalkBack's adjust actions. The cue panel keeps Apply / Cancel, since text and times are applied together.
- Code: `EditorPanels.kt` (which panel shows, pure), `EditorToolPanels.kt` (`OverlayPanel` is now inline; `ToolPanelHost`), `TrimPanel` / `SpeedPanel` / `TransitionPanel` / `VolumePanel` (`EditorDialogs.kt`), `SubtitlesPanel` / `CuePanel`. Two new strings in five languages.
- Tests: `EditorPanelsTest` (9, JVM), `EditorContentTest` (+7: panel opens and toggles, phone placement, tablet placement, trim on release, closes on another kind of clip, Back, cue panel), `EditorFlowTest` updated (trim has no Apply). Needs a build and a device run.

## Phase 64 — Implementation plan, batch 9 (step 25)
- **Share to BConverter (step 25)**: BConverter now appears in the system share sheet for videos (`SEND` and `SEND_MULTIPLE`, `video/*`). A share opens "Open with BConverter", which lists what was shared and offers, for one video: edit in a new project, convert, extract audio, extract subtitles; for several: edit in a new project (in the order shared) or convert them all. The share is handed over once (`ShareInbox`): the tool chosen takes it, Back drops it. Intent reading is pure and tested (`ShareIntents`), including apps that fill only the clip data; at most 50 files are taken.
- **Batch conversion (step 25)**: Converter → "Convert several videos" (or "Convert all" from a share). Pick any number of videos (up to 50), choose one quality and one format, then a folder; each video is saved there as "name-converted.ext" (numbered when names clash). `BatchConverter` opens and converts one file at a time, each as an ordinary export job (it shows in Exports with its own notification), with codecs chosen per file. A file that cannot be opened, has no picture, cannot be converted with these settings, does not fit, or cannot be created in the folder is skipped with its reason; a failed conversion is reported and the rest carry on. Stop ends the file in progress and the rest. The batch runs on the app scope, so leaving the screen does not stop it, and reopening the screen shows it.
- Code: `domain/share/SharedMedia.kt` (`ShareIntents`, `ShareAction`), `feature/share/` (`ShareInbox`, `ShareViewModel`, `ShareContent`), `domain/usecase/BatchConverter.kt` (`BatchConverter`, `BatchNames`, `BatchRun`), `feature/batch/`, routes `share`, `shared/{tool}` and `batch` in `AppNavHost`, intent filters in the manifest, `MainActivity.receiveShare`. Converter, audio and subtitle extractors and the editor take an optional shared input. 31 new strings and 3 plurals in five languages.
- Tests: `BatchConverterTest` (12), `ShareIntentsTest` (7), `ShareInboxTest` (3), `BatchConvertContentTest` (5, device), `ShareContentTest` (3, device). Needs a build and a device run.
- Known limits: a project started from shared videos keeps working while the app runs, but apps that share without a lasting grant (most galleries) mean the project may report missing media after the app is restarted; a batch cannot survive the app process being killed (the file in progress is reported as interrupted in Exports, the rest never start).
