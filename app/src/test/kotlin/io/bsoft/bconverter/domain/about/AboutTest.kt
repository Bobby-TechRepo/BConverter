package io.bsoft.bconverter.domain.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutTest {
    private val notices = ThirdPartyNotices.all

    @Test fun every_notice_is_complete() {
        notices.forEach { n ->
            assertTrue("${n.name} name", n.name.isNotBlank()); assertTrue("${n.name} license", n.license.isNotBlank())
            assertTrue("${n.name} purpose", n.purpose.isNotBlank()); assertTrue("${n.name} url", n.url.startsWith("https://"))
        }
    }

    @Test fun names_are_unique() { assertEquals(notices.size, notices.map { it.name }.distinct().size) }

    @Test fun the_media_engine_is_credited_under_the_lgpl() {
        val ffmpeg = notices.first { it.name.startsWith("FFmpeg") && !it.name.contains("Kit") }
        assertTrue(ffmpeg.license.contains("LGPL"))
        assertTrue(notices.any { it.name.contains("FFmpegKit") && it.license.contains("LGPL") })
    }

    @Test fun no_notice_claims_a_plain_gpl_component() {
        // Spec §8: no GPL components. "LGPL" is fine; a bare "GPL" is not.
        notices.forEach { assertFalse("${it.name}: ${it.license}", Regex("(?<!L)GPL").containsMatchIn(it.license)) }
    }

    @Test fun the_app_itself_is_not_listed_as_third_party() { assertFalse(notices.any { it.name == "BConverter" }) }

    @Test fun the_version_line_combines_name_and_code() {
        assertEquals("1.2.3 (45)", AppInfo("1.2.3", 45, "n8.1.2").versionLine)
    }

    @Test fun an_unknown_engine_version_is_reported_as_unknown() {
        assertEquals("unknown", AppInfo("1", 1, null).engineVersionOrUnknown)
        assertEquals("unknown", AppInfo("1", 1, "  ").engineVersionOrUnknown)
        assertEquals("n8.1.2", AppInfo("1", 1, "n8.1.2").engineVersionOrUnknown)
    }

    // Verified against the FFmpegKit fork's README for ffmpeg-kit-full 8.1.7 (LGPL-3.0; the -gpl variants add x264, x265, xvid, vidstab).
    private val bundled = listOf(
        "lame", "opus", "libvorbis", "speex", "opencore-amr", "shine", "soxr", "twolame", "vo-amrwbenc", "libilbc",
        "dav1d", "libvpx", "kvazaar", "libass", "libtheora", "libwebp", "zimg", "freetype", "fontconfig", "fribidi", "snappy", "gmp", "gnutls",
    )
    private val bundledEntry get() = ThirdPartyNotices.all.single { it.name == "Libraries bundled with FFmpegKit" }

    @Test fun every_library_in_the_ffmpegkit_full_package_is_named() {
        val missing = bundled.filterNot { bundledEntry.purpose.contains(it) }
        assertTrue("not named: $missing", missing.isEmpty())
    }

    @Test fun the_gpl_only_libraries_are_not_claimed_as_bundled() {
        listOf("x264", "x265", "xvid", "vidstab").forEach { assertFalse("$it is GPL-only and not in this build", bundledEntry.purpose.substringBefore("(").contains(it)) }
    }
}
