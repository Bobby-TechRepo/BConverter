package io.bsoft.bconverter.data.storage

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import io.bsoft.bconverter.core.storage.InPlaceHandle
import io.bsoft.bconverter.core.storage.InPlaceOpener
import java.io.File
import java.io.RandomAccessFile

/**
 * Reads a picked document where it is. The document's file descriptor stays open and a symlink in the staging directory
 * points at `/proc/self/fd/<fd>`: FFmpeg, ExoPlayer and the thumbnail decoder all open files inside this process, so they
 * read through the link as if it were a copy, with no copying and no extra storage.
 *
 * Only regular, seekable files qualify (cloud or streamed documents are pipes); the link must also be reopenable, which
 * some storage providers forbid. Anything else returns null and the caller falls back to copying. Each link path is unique
 * per session, so caches keyed by path never confuse two documents.
 */
class AndroidInPlaceOpener(private val resolver: ContentResolver) : InPlaceOpener {
    override fun open(uri: Uri, link: File): InPlaceHandle? {
        val pfd = try { resolver.openFileDescriptor(uri, "r") } catch (_: Exception) { null } ?: return null
        if (!isSeekableRegularFile(pfd)) return null.also { closeQuietly(pfd) }
        return try {
            link.parentFile?.mkdirs()
            link.delete()
            Os.symlink("/proc/self/fd/${pfd.fd}", link.path)
            if (canRead(link)) Handle(link, pfd) else null.also { link.delete(); closeQuietly(pfd) }
        } catch (_: Exception) {
            link.delete(); closeQuietly(pfd); null
        }
    }

    private fun isSeekableRegularFile(pfd: ParcelFileDescriptor): Boolean = try {
        pfd.statSize >= 0 && Os.lseek(pfd.fileDescriptor, 0, OsConstants.SEEK_SET) == 0L
    } catch (_: Exception) { false }

    private fun canRead(link: File): Boolean = try {
        RandomAccessFile(link, "r").use { it.read() >= 0 }
    } catch (_: Exception) { false }

    private fun closeQuietly(pfd: ParcelFileDescriptor) { runCatching { pfd.close() } }

    private class Handle(override val file: File, private val pfd: ParcelFileDescriptor) : InPlaceHandle {
        override fun close() {
            file.delete()
            runCatching { pfd.close() }
        }
    }
}
