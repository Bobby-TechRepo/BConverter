package io.bsoft.bconverter.data.font

import io.bsoft.bconverter.domain.font.FontHttp
import io.bsoft.bconverter.domain.font.GoogleFonts
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/** Plain HTTPS GETs to Google's font hosts only. Every failure is an [IOException]. */
class UrlConnectionFontHttp(private val io: CoroutineContext) : FontHttp {
    override suspend fun getText(url: String): String = String(fetch(url, TEXT_LIMIT), Charsets.UTF_8)

    override suspend fun getBytes(url: String, maxBytes: Int): ByteArray = fetch(url, maxBytes)

    private suspend fun fetch(url: String, limit: Int): ByteArray = withContext(io) {
        if (!GoogleFonts.isAllowedUrl(url)) throw IOException("blocked url")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 20_000
            // An unknown agent makes Google answer with plain TrueType links instead of web-only formats.
            c.setRequestProperty("User-Agent", "BConverter")
            if (c.responseCode != 200) throw IOException("http ${c.responseCode}")
            if (!GoogleFonts.isAllowedUrl(c.url.toString())) throw IOException("redirected away")
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream(); val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = input.read(buf); if (n < 0) break
                    if (out.size() + n > limit) throw IOException("too large")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            }
        } catch (e: IOException) { throw e } catch (e: RuntimeException) { throw IOException(e) } finally { c.disconnect() }
    }

    private companion object { const val TEXT_LIMIT = 8 * 1024 * 1024 }
}
