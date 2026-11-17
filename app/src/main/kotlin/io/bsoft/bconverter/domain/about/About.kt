package io.bsoft.bconverter.domain.about

data class AppInfo(val versionName: String, val versionCode: Long, val engineVersion: String?) {
    val versionLine: String get() = "$versionName ($versionCode)"
    val engineVersionOrUnknown: String get() = engineVersion?.takeIf { it.isNotBlank() } ?: "unknown"
}

data class ThirdPartyNotice(val name: String, val license: String, val purpose: String, val url: String)

/**
 * What the app is built from (spec §8: license information and third-party notices). The FFmpeg build must stay LGPL-only;
 * `AboutTest` fails if a plain GPL licence is ever listed. The bundled-libraries entry follows the FFmpegKit fork's README for
 * ffmpeg-kit-full (checked October 2026); re-check it, and the AAR's own notice files, when upgrading FFmpegKit (docs/FFMPEG.md).
 */
object ThirdPartyNotices {
    val all: List<ThirdPartyNotice> = listOf(
        ThirdPartyNotice("FFmpeg", "LGPL-2.1-or-later", "Reads, converts and renders audio and video.", "https://ffmpeg.org/legal.html"),
        ThirdPartyNotice(
            "FFmpegKit (community fork)", "LGPL-3.0", "Android build and Java interface for FFmpeg.",
            "https://github.com/ffmpegkit-maintained/ffmpeg",
        ),
        ThirdPartyNotice(
            "Libraries bundled with FFmpegKit", "Various (LGPL, BSD, Apache, ISC and similar)",
            "Audio codecs and effects (lame, opus, libvorbis, speex, opencore-amr, shine, soxr, twolame, vo-amrwbenc, libilbc), " +
                "video codecs and image tools (dav1d, libvpx, kvazaar, libass, libtheora, libwebp, zimg, snappy), " +
                "text rendering (freetype, fontconfig, fribidi) and secure connections (gmp, gnutls). " +
                "(LGPL-only build: the GPL libraries x264, x265, xvid and vidstab are not included.)",
            "https://github.com/ffmpegkit-maintained/ffmpeg",
        ),
        ThirdPartyNotice("AndroidX Media3", "Apache-2.0", "Plays the editor preview.", "https://github.com/androidx/media"),
        ThirdPartyNotice("Jetpack Compose and Material 3", "Apache-2.0", "The user interface.", "https://developer.android.com/jetpack/compose"),
        ThirdPartyNotice("AndroidX Room and WorkManager", "Apache-2.0", "Saves projects and runs exports in the background.", "https://developer.android.com/jetpack"),
        ThirdPartyNotice("Hilt and Dagger", "Apache-2.0", "Dependency injection.", "https://dagger.dev"),
        ThirdPartyNotice("Kotlin and kotlinx.coroutines", "Apache-2.0", "The language and its concurrency library.", "https://kotlinlang.org"),
        ThirdPartyNotice("smart-exception-java", "Apache-2.0", "Error reporting helper required by FFmpegKit.", "https://github.com/tanersener/smart-exception"),
        ThirdPartyNotice(
            "Google Fonts (downloaded on request)", "OFL-1.1, Apache-2.0 or Ubuntu Font License (per family)",
            "Fonts you choose to download for text and subtitles. They are not bundled with the app; each family's licence is shown when you download it.",
            "https://fonts.google.com/about",
        ),
        ThirdPartyNotice("Glyphs icons", "MIT", "Outline icons by Goran Spasojevic, distributed through Iconify.", "https://icon-sets.iconify.design/glyphs/"),
        ThirdPartyNotice("Plus Jakarta Sans", "OFL-1.1", "The app's typeface (bundled). Copyright the Plus Jakarta Sans Project Authors.", "https://github.com/tokotype/PlusJakartaSans"),
    )
}

/** A licence whose full text ships inside the app ([assetFiles] under `assets/licenses/`, shown in order). */
data class BundledLicense(val title: String, val appliesTo: String, val assetFiles: List<String>)

/**
 * Full licence texts shipped with the app (implementation plan step 5). The LGPL requires the licence text to accompany the
 * library, and the LGPL 3.0 incorporates the GPL 3.0, so both are shipped; the OFL requires its text with the font.
 */
object BundledLicenses {
    val all: List<BundledLicense> = listOf(
        BundledLicense("GNU Lesser General Public License 2.1", "FFmpeg", listOf("lgpl-2.1.txt")),
        BundledLicense("GNU Lesser General Public License 3.0", "FFmpegKit (incorporates the GNU GPL 3.0, included below it)", listOf("lgpl-3.0.txt", "gpl-3.0.txt")),
        BundledLicense("Apache License 2.0", "AndroidX, Jetpack Compose, Media3, Room, WorkManager, Hilt and Dagger, Kotlin, smart-exception-java", listOf("apache-2.0.txt")),
        BundledLicense("SIL Open Font License 1.1", "Plus Jakarta Sans", listOf("ofl-1.1.txt")),
        BundledLicense("MIT License", "Glyphs icons", listOf("mit.txt")),
    )
}
