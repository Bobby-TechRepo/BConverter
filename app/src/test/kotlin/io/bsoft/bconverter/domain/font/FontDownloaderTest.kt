package io.bsoft.bconverter.domain.font

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FontDownloaderTest {
    private class FakeHttp(val texts: MutableMap<String, String> = mutableMapOf(), val bytes: MutableMap<String, ByteArray> = mutableMapOf(), var offline: Boolean = false) : FontHttp {
        val requested = mutableListOf<String>()
        override suspend fun getText(url: String): String { requested += url; if (offline) throw IOException("offline"); return texts[url] ?: throw IOException("404") }
        override suspend fun getBytes(url: String, maxBytes: Int): ByteArray { requested += url; if (offline) throw IOException("offline"); return bytes[url] ?: throw IOException("404") }
    }
    private lateinit var root: File
    private lateinit var store: FontCacheStore
    private val http = FakeHttp()
    private val font = CatalogFont("Open Sans", "sans serif", listOf(FontVariant(400), FontVariant(700), FontVariant(400, true)), FontLicense.OFL)
    private fun ttf() = ByteArray(2000).also { byteArrayOf(0, 1, 0, 0).copyInto(it) }
    private fun css(vararg v: Pair<String, String>) = v.joinToString("\n") { (w, u) -> "@font-face { font-style: normal; font-weight: $w; src: url($u) format('truetype'); }" }

    @Before fun setUp() { root = Files.createTempDirectory("fd").toFile(); store = FontCacheStore(File(root, "fonts"), File(root, "i.json")) }
    @After fun tearDown() { root.deleteRecursively() }

    @Test fun the_chosen_variants_are_downloaded_and_stored() = runBlocking {
        http.texts[GoogleFonts.cssUrl("Open Sans", listOf(FontVariant(400), FontVariant(700)))] = css("400" to "https://fonts.gstatic.com/a.ttf", "700" to "https://fonts.gstatic.com/b.ttf")
        http.bytes["https://fonts.gstatic.com/a.ttf"] = ttf(); http.bytes["https://fonts.gstatic.com/b.ttf"] = ttf()
        val r = FontDownloader(http, store).download(font, listOf(FontVariant(400), FontVariant(700)))
        assertEquals(FontDownload.Done(listOf(FontVariant(400), FontVariant(700))), r); assertEquals(2, store.list().size); assertEquals(FontLicense.OFL, store.list().first().license)
    }
    @Test fun variants_the_family_does_not_have_are_ignored() = runBlocking {
        assertEquals(FontDownload.NothingToDownload, FontDownloader(http, store).download(font, listOf(FontVariant(900)))); assertTrue(http.requested.isEmpty())
    }
    @Test fun offline_is_reported_and_nothing_is_stored() = runBlocking {
        http.offline = true
        assertEquals(FontDownload.Offline, FontDownloader(http, store).download(font, listOf(FontVariant(400)))); assertTrue(store.list().isEmpty())
    }
    @Test fun a_link_to_another_host_is_never_fetched() = runBlocking {
        http.texts[GoogleFonts.cssUrl("Open Sans", listOf(FontVariant(400)))] = css("400" to "https://evil.example/a.ttf")
        assertEquals(FontDownload.BadResponse, FontDownloader(http, store).download(font, listOf(FontVariant(400))))
        assertTrue(http.requested.none { it.contains("evil") })
    }
    @Test fun a_response_that_is_not_a_font_is_rejected() = runBlocking {
        http.texts[GoogleFonts.cssUrl("Open Sans", listOf(FontVariant(400)))] = css("400" to "https://fonts.gstatic.com/a.ttf"); http.bytes["https://fonts.gstatic.com/a.ttf"] = "<html>".toByteArray().copyOf(3000)
        assertEquals(FontDownload.BadResponse, FontDownloader(http, store).download(font, listOf(FontVariant(400)))); assertTrue(store.list().isEmpty())
    }
    @Test fun css_without_links_is_a_bad_response() = runBlocking {
        http.texts[GoogleFonts.cssUrl("Open Sans", listOf(FontVariant(400)))] = "nothing"
        assertEquals(FontDownload.BadResponse, FontDownloader(http, store).download(font, listOf(FontVariant(400))))
    }
    @Test fun losing_the_connection_midway_keeps_what_was_already_stored() = runBlocking {
        http.texts[GoogleFonts.cssUrl("Open Sans", listOf(FontVariant(400), FontVariant(700)))] = css("400" to "https://fonts.gstatic.com/a.ttf", "700" to "https://fonts.gstatic.com/b.ttf")
        http.bytes["https://fonts.gstatic.com/a.ttf"] = ttf()
        assertEquals(FontDownload.Done(listOf(FontVariant(400))), FontDownloader(http, store).download(font, listOf(FontVariant(400), FontVariant(700))))
    }
    @Test fun the_preview_variant_is_regular_when_there_is_one() {
        assertEquals(FontVariant(400), FontDownloader(http, store).previewVariant(font))
        assertEquals(FontVariant(100), FontDownloader(http, store).previewVariant(font.copy(variants = listOf(FontVariant(100), FontVariant(900)))))
    }
}
