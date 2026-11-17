package io.bsoft.bconverter.media.ffmpeg

import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import io.bsoft.bconverter.data.font.UrlConnectionFontHttp
import io.bsoft.bconverter.domain.font.CatalogResult
import io.bsoft.bconverter.domain.font.FontCacheStore
import io.bsoft.bconverter.domain.font.FontDownload
import io.bsoft.bconverter.domain.font.FontDownloader
import io.bsoft.bconverter.domain.font.FontResolver
import io.bsoft.bconverter.domain.font.FontVariant
import io.bsoft.bconverter.domain.font.GoogleFontsCatalog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The one test that talks to Google Fonts. It is skipped (not failed) when the device is offline. */
class GoogleFontsLiveTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "google-fonts-live").apply { deleteRecursively(); mkdirs() }
    private val http = UrlConnectionFontHttp(Dispatchers.IO)

    @Test fun a_family_can_be_found_downloaded_resolved_and_drawn() = runBlocking {
        withTimeout(120_000) {
            val catalog = GoogleFontsCatalog(http, File(dir, "catalog.json")).load()
            assumeTrue("offline: skipping", catalog is CatalogResult.Ready && !catalog.fromCacheOnly)
            val lobster = (catalog as CatalogResult.Ready).fonts.firstOrNull { it.family == "Lobster" }
            assertTrue("Lobster missing from the catalog", lobster != null)
            val store = FontCacheStore(File(dir, "fonts"), File(dir, "index.json"))
            val result = FontDownloader(http, store).download(lobster!!, listOf(FontVariant(400)))
            assertTrue("download: $result", result is FontDownload.Done)
            val resolved = FontResolver().resolve(store.list(), "Lobster", 400, false)
            assertTrue(resolved.exact && resolved.file != null)
            val png = File(dir, "lobster.png")
            val s = FFmpegKit.execute("-y -f lavfi -i color=c=black:s=480x160:d=1 -vf drawtext=fontfile=${resolved.file!!.path}:text=Hello:fontcolor=white:fontsize=60 -frames:v 1 ${png.absolutePath}")
            assertTrue(s.allLogsAsString, ReturnCode.isSuccess(s.returnCode)); assertTrue(png.length() > 0)
        }
    }
}
