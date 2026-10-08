package io.bsoft.bconverter.fakes

import android.net.Uri
import io.bsoft.bconverter.core.storage.ContentReader
import io.bsoft.bconverter.domain.model.ContentInfo
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** In-memory ContentReader. Lets tests script metadata, bytes, errors and mid-stream failures. */
class FakeContentReader : ContentReader {
    class Entry(
        val info: ContentInfo,
        val bytes: ByteArray = ByteArray(0),
        val infoError: Exception? = null,
        val openError: Exception? = null,
        val failAfterBytes: Int? = null,
        val onRead: (() -> Unit)? = null,
    )

    private val entries = mutableMapOf<Uri, Entry>()
    var openCount = 0
        private set

    fun put(uri: Uri, entry: Entry) { entries[uri] = entry }

    override fun info(uri: Uri): ContentInfo {
        val e = entries[uri] ?: throw FileNotFoundException(uri.toString())
        e.infoError?.let { throw it }
        return e.info
    }

    override fun open(uri: Uri): InputStream {
        openCount++
        val e = entries[uri] ?: throw FileNotFoundException(uri.toString())
        e.openError?.let { throw it }
        return object : InputStream() {
            private var pos = 0
            override fun read(): Int = if (pos < e.bytes.size) e.bytes[pos++].toInt() and 0xFF else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                e.onRead?.invoke()
                val limit = e.failAfterBytes
                if (limit != null && pos >= limit) throw IOException("simulated read failure")
                if (pos >= e.bytes.size) return -1
                val end = minOf(pos + len, e.bytes.size, limit ?: Int.MAX_VALUE)
                val n = end - pos
                System.arraycopy(e.bytes, pos, b, off, n)
                pos = end
                return n
            }
        }
    }
}
