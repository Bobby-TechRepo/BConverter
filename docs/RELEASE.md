# Release build check

Release builds minify with R8 (`proguard-rules.pro` keeps FFmpegKit's JNI classes; Hilt/Room/WorkManager/Media3 bring their own rules).

## Build
    gradlew assembleRelease -PreleaseWithDebugKey
(`-PreleaseWithDebugKey` signs with the debug key so the APK installs; a real release needs your own keystore.)
APK: `app/build/outputs/apk/release/app-release.apk`. Install on a physical arm64 phone (FFmpegKit is arm64-only).
Also read the build output for R8 "Missing class" warnings and send them to me if any.

## Smoke test (minified build, a real phone)
1. App starts; Home shows six entries; Settings and About open (About lists the notices incl. FFmpegKit and Glyphs).
2. Convert Video: pick a video, preset Small file, convert to MP4 — completes, file saved, Share sheet opens.
3. Convert Video: MKV + HEVC (or any encoder the list offers) — completes.
4. Extract Audio: copy mode and an MP3/AAC re-encode — both complete.
5. Editor: add two clips, split, trim, undo/redo, preview plays, Export — completes in the background (leave the app during it).
6. Projects: the editor project appears, reopens with the same clips, rename, duplicate, delete.
7. Rotate / tablet check if you have one.
8. Editor timeline: the playhead stays centred while you scroll; scrolling past the last clip shows a black picture; Add video appends after the last clip.
9. Transitions: set Crossfade and Fade through black on the second clip, export, and watch the result (the blend, the dip to black, matching audio, total length shorter by the crossfade).
10. Settings > Language: switch to French, then Japanese, then System default; the UI updates and the choice survives a restart.
If something crashes only in this build, send `adb logcat -d | findstr /i "AndroidRuntime FATAL"` (or the stack trace) — a `ClassNotFoundException`/`NoSuchMethodError` means a missing keep rule.

## Release signing (step 4 of the implementation plan)
Create `keystore.properties` in the project root (git-ignored):

    storeFile=release.jks
    storePassword=…
    keyAlias=bconverter
    keyPassword=…

or set `BCONVERTER_STORE_FILE`, `BCONVERTER_STORE_PASSWORD`, `BCONVERTER_KEY_ALIAS`, `BCONVERTER_KEY_PASSWORD` on CI. With none set,
release builds are unsigned (use `-PreleaseWithDebugKey` for a local smoke test). Enrol in Play App Signing and keep the
upload key out of the repository. Release builds now also shrink unused resources.

## CI
`.github/workflows/android.yml` runs unit tests (JDK 21: Robolectric needs it for SDK 36), lint and a minified release build on every
push, and fails if the Room schema in `app/schemas` was changed by the build but not committed.
