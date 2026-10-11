# FFmpeg integration (spec §8)

| Item | Value |
|---|---|
| Distribution | FFmpegKit community fork, `dev.ffmpegkit-maintained:ffmpeg-kit-full` (Maven Central) |
| Pinned version | `8.1.7` (FFmpeg 8.1 LTS line; published 2026-07-12, 0 known vulnerabilities on mvnrepository at selection time) |
| Do NOT use | `dev.ffmpegkit-maintained:ffmpeg` (older single "free tier" artifact with no extra encoders) and any `-gpl` artifact |
| License | **LGPL-3.0** (no GPL components: no x264/x265/xvid/vidstab) |
| ABI | `arm64-v8a` only (the fork publishes no other ABI) — emulators must be arm64 |
| minSdk / targetSdk | 24+ (app uses 26) / 35 |
| 16 KB pages | Enforced by the fork's CI |
| Java API | Unchanged `com.arthenica.ffmpegkit` package; used via argument arrays, never shell strings |

## Libraries in `ffmpeg-kit-full` (per the fork's compatibility matrix)
audio: lame, opus, libvorbis, speex, opencore-amr, shine, soxr, twolame, vo-amrwbenc, libilbc ·
video: dav1d (AV1 *decode*), libvpx (VP8/VP9), kvazaar (H.265 encode), libass, libtheora, libwebp, zimg, freetype, fontconfig, fribidi, snappy ·
https: gmp, gnutls · plus Android `MediaCodec` hardware acceleration and zlib.

## Encoders — decided at runtime, not assumed
H.264 *decode* is native; **H.264 encode is only possible through `h264_mediacodec`** (x264 is GPL). Wrapper encoders
are bitrate-only, and exist only if the device has the hardware. `CapabilityProvider` therefore reads
`ffmpeg -encoders/-decoders` at runtime, cross-checks `*_mediacodec` wrappers against `MediaCodecList`,
and records per codec whether CRF is supported. The UI only offers what this reports.

## Third-party notices (verify against the AAR's own notices before release)
FFmpeg (LGPL-2.1+), FFmpegKit (LGPL-3.0), and the libraries above under their own licenses (mostly BSD/LGPL/Apache).
LGPL obligations: ship the license texts, keep the native libraries dynamically linked/replaceable (they are separate `.so`
files in the AAR), and offer the corresponding source (https://github.com/ffmpegkit-maintained/ffmpeg). Not legal advice.

## Supply-chain notes
The fork is community-run by a small team. Pin exact versions (never `+`), review upgrades, and consider mirroring the
verified AAR internally. Verified-by-docs only so far: encoder presence will be confirmed by the instrumented tests.
