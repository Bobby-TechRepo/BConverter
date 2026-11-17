# Version upgrade plan (researched 26 Oct 2026; nothing here has been built)

Current pins (`gradle/libs.versions.toml`): AGP 8.7.3 (run with Gradle 9.3.0 in practice), Kotlin 2.0.21, KSP 2.0.21-1.0.28,
compileSdk/targetSdk 35, Compose BOM 2024.12.01, Hilt 2.53.1, hilt-navigation-compose 1.2.0, Navigation 2.8.5, Media3 1.5.1,
WorkManager 2.10.0, Room 2.6.1, Robolectric 4.14.1.

## What the release pages say (developer.android.com, October 2026)
| Library | Latest stable | Requirement noted on the page |
|---|---|---|
| AGP | 9.4.0 (9.0–9.3 also listed; 8.13 is the last 8.x) | Gradle 9.6.0, Build Tools 36.0.0, JDK 17, max API 37 |
| Compose UI / Material3 | 1.12.1 / 1.4.0 | Compose compileSdk is API 37, i.e. AGP 9.2+ (Navigation and hilt-navigation-compose pages) |
| Media3 | 1.11.1 (Sep 2026) | 1.11 builds with Kotlin 2.2; needs a newer compileSdk than 35 |
| Navigation | 2.10.2 (maintenance mode) | AGP 9.2+ for Compose, minSdk 24 |
| androidx.hilt | 1.4.0 | KGP 2.2.0+, AGP 9.2+ with Compose |
| WorkManager | 2.12.0 | compileSdk 33+, minSdk 24 |
| Room | 2.8.5 (2.7.2 is the last 2.7) | Kotlin 2.0+, KSP (KSP2 recommended), minSdk 23 |

minSdk is 26, so every minSdk requirement above is met.

## Why it is staged
Almost everything newer needs the same toolchain jump: **AGP 9.2+, Gradle 9.6, compileSdk 37, Kotlin 2.2+**. AGP 9 also moves
to built-in Kotlin and a new DSL, which touches `build.gradle.kts` (plugins, `kotlin { sourceSets … }` used for the shared test fakes)
and the Hilt/KSP setup. Robolectric must support the new target SDK. None of this can be checked without a real build, so each
stage should be built and tested before the next.

## Stages
1. **Libraries on the current toolchain** (smallest risk): Room 2.7.2, WorkManager 2.10.x patch, Lifecycle/Activity patch releases. Build + all tests.
2. **Toolchain jump**: Gradle 9.6.0, AGP 9.x (follow "Migrate to built-in Kotlin"), Kotlin 2.2.x + matching KSP, JDK 17, compileSdk/targetSdk 36 or 37,
   Robolectric version that supports it. Fix the build first; run unit tests; then androidTest.
3. **Libraries that need stage 2**: Compose BOM (2026.09.00 is the latest listed), Material3 1.4, Navigation 2.10, androidx.hilt 1.4, Hilt/Dagger,
   Media3 1.11 (re-check the Media3 engine and preview tests), WorkManager 2.12, Room 2.8.
4. **FFmpegKit** stays at 8.1.7 unless the fork releases a newer one; re-check `About.kt` notices on every change.

Re-verify each version on the release pages before applying; the numbers above are the ones listed on the date at the top.

## Progress
- **Stage 1 (applied, awaiting a build):** Room 2.6.1 → 2.7.2 only. 2.7.x needs Kotlin 2.0+ and KSP, both already met, and minSdk 23 (ours is 26). WorkManager was left at 2.10.0 because I could not verify a newer 2.10.x patch.
  Check after the build: `RoomProjectRepositoryTest` and the project save/load flows; no Room schema change is expected.
- **Gradle 9.3 warnings (from your problem reports, 27 Oct):** four deprecation warnings, no failures. (1) "multi-string notation" comes from AGP 8.7.3 itself (lint-gradle, aapt2) and goes away with the AGP upgrade in stage 2. (2) "toolchain auto-provisioning without toolchain repositories" came from `jvmToolchain(17)`; replaced by `compilerOptions { jvmTarget = JVM_17 }` so Gradle uses the JDK it runs on (needs JDK 17+, which AGP 8.7 already requires).

## Stage 2 research (27 Oct 2026) — nothing applied yet
Facts from the AGP 9.0 release notes, the "built-in Kotlin" migration guide and the KSP / Dagger / Kotlin release pages:
- **AGP 9.0 minimums:** Gradle 9.1, Kotlin Gradle plugin 2.2.10, KSP 2.2.10-2.0.2 (AGP raises lower versions itself), JDK 17, Build Tools 36.0.0, max API 36.1. AGP 9.4: Gradle 9.6, max API 37.
- **Built-in Kotlin is on by default:** remove the `org.jetbrains.kotlin.android` plugin (module file, root file, version catalog). The Compose compiler plugin and KSP stay. `android.newDsl` is true by default.
- **`kotlin.sourceSets` is not supported.** The shared test fakes in `app/build.gradle.kts` (`kotlin.sourceSets.getByName("androidTest").kotlin.srcDir(...)`) must become `android.sourceSets.named("androidTest") { kotlin.directories += "src/test/kotlin/io/bsoft/bconverter/fakes" }`. `kotlin { compilerOptions { jvmTarget … } }` stays valid.
- **Changed defaults to watch:** `targetSdk` defaults to compileSdk (keep it explicit); non-final R class (`switch` on R fields breaks); strict R8 keep rules (`-keep class A` no longer keeps constructors; check proguard rules for Hilt/Room/FFmpegKit); unit tests only for the tested build type.
- **KSP** now has independent versions (2.3.x; latest listed 2.3.11). 2.3.5–2.3.10 contain AGP 9 built-in-Kotlin fixes, so use 2.3.10 or newer.
- **Hilt / Dagger:** the Hilt Gradle plugin needs AGP 9.0+ from 2.59.1 on; latest is 2.60.1 (Jul 2026), built with Kotlin 2.3.21.
- **Kotlin:** latest stable listed is 2.4.21 (Oct 2026). Proposed for stage 2: Kotlin 2.3.21 (what Hilt 2.60 uses), KSP 2.3.11.
- **Stage 2 applied (awaiting a build):** AGP 9.2.0, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, compileSdk 36 (targetSdk stays 35, so Robolectric 4.14.1 keeps working), `kotlin-android` plugin removed, test-fakes source dir moved to `sourceSets.named("androidTest") { kotlin.directories += … }`. AGP 9.2.0 requires Gradle 9.4.1 or newer (reported by the build), so the wrapper is now 9.4.1. Room stays 2.7.2 for now: if the Room KSP step complains about Kotlin 2.3 metadata, move Room to 2.8.5 early (stage 3 item).
- **Release-build risk found while checking R8 rules:** `proguard-rules.pro` is empty and release builds minify. FFmpegKit (JNI), Media3 and Room usually ship consumer rules in their AARs, but a release build has not been tried yet; do a release build and a smoke test (convert, export) before shipping.
- **Still to verify before editing:** Robolectric version that supports the chosen target SDK; whether FFmpegKit 8.1.7 and Media3 1.5.1 work with compileSdk 36 (they should); that Build Tools 36.0.0 and the SDK 36 platform are installed on the build machine.
- **Stage 2 unit-test failure (28 Oct):** 334 Robolectric tests failed with `IllegalArgumentException: Unsupported class file major version 69` (ASM inside Robolectric 4.14.1 cannot read JDK 25 classes; Gradle is running on JDK 25). Fix applied: Robolectric 4.14.1 → 4.17 (latest, Sep). If it still fails, set Android Studio's Gradle JDK (Settings → Build Tools → Gradle) to JDK 21 or 17; AGP 9 and Robolectric both support those.
- **Stage 2 result (28 Oct):** builds and all unit tests green with AGP 9.2.0, Kotlin 2.3.21, KSP 2.3.11, Hilt 2.60.1, Gradle 9.4.1, Robolectric 4.17. Android (instrumented) tests still to be run before stage 3.
- **Stage 3 applied (28 Oct), awaiting a build:** Compose BOM 2026.09.00 (Material3 1.4.0 via the BOM), Navigation 2.10.2, androidx.hilt 1.4.0, Media3 1.11.1, WorkManager 2.12.0, Room 2.8.5, compileSdk 37 (targetSdk stays 35). Needs the SDK 37 platform installed. Watch: Media3 API changes (engine/preview), Room 2.8 KSP, deprecated Compose APIs. UI tests are paused by request until the UI rework; unit tests are the gate.
- **Icons (28 Oct):** `material-icons-core` is no longer pulled in transitively by the new Compose/Material3, so `androidx.compose.material.icons` is gone. Decision: no icon dependency; icons are vector drawables in `res/drawable` (`ic_arrow_back`, `ic_play`, `ic_pause`, `ic_more_vertical`, `ic_delete`) used with `painterResource`. They are placeholders in the Glyphs outline style until you run `python tools/icons/fetch_glyphs.py`, which downloads the real Glyphs set icons (Goran Spasojevic, MIT, via Iconify), preferring the `-outline` variant, and converts them to VectorDrawables. `--search <word>` lists Glyphs names; edit `tools/icons/icons.json` for new icons.
