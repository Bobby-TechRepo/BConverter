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
