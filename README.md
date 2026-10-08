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

## Next milestone
File selection: `InputResolver` (content URI -> staged file), SAF picker contract. Tests first.
