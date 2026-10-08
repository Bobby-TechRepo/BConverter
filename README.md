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

## Next milestone
Phase 3 — Media probing: FFprobe JSON fixtures -> `MediaMetadata` parser (tests first), then the real FFprobe integration.
