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

## Open decision (blocks the real FfprobeRunner and Phase 5)
Which FFmpeg distribution to ship — see the project thread.
