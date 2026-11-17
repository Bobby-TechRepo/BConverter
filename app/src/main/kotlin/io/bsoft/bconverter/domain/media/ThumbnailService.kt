package io.bsoft.bconverter.domain.media

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Cached, de-duplicated, throttled thumbnail access (spec §27). Safe to call from many tiles at once:
 * identical requests share one decode, at most [maxConcurrent] decodes run together, a cached file is reused, and a source
 * that cannot be decoded is not retried for every tile.
 */
class ThumbnailService(
    private val generator: ThumbnailGenerator,
    private val store: ThumbnailStore,
    maxConcurrent: Int = 2,
) {
    private val permits = Semaphore(maxConcurrent)
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val unreadable = ConcurrentHashMap.newKeySet<String>()

    suspend fun thumbnail(source: File, positionMs: Long, widthPx: Int): ThumbnailResult {
        if (!source.isFile) return ThumbnailResult.Failure(ThumbnailFailure.SourceMissing)
        val stamp = stamp(source)
        if (stamp in unreadable) return ThumbnailResult.Failure(ThumbnailFailure.Unreadable)
        val key = "$stamp|$positionMs|$widthPx"
        val target = store.fileFor(key)
        val lock = locks.getOrPut(key) { Mutex() }
        try {
            return lock.withLock {
                if (target.length() > 0) return@withLock ThumbnailResult.Success(target)
                if (stamp in unreadable) return@withLock ThumbnailResult.Failure(ThumbnailFailure.Unreadable)
                permits.withPermit { generate(source, stamp, positionMs, widthPx, target) }
            }
        } finally {
            locks.remove(key, lock)
        }
    }

    /** Forget what is known about a source (e.g. the user replaced the file), so it is tried again. */
    fun forget(source: File) { unreadable.remove(stamp(source)) }

    private suspend fun generate(source: File, stamp: String, positionMs: Long, widthPx: Int, target: File): ThumbnailResult {
        val temp = File(target.path + ".part").also { it.delete() }
        return try {
            when (val r = generator.generate(source, positionMs, widthPx, temp)) {
                is ThumbnailResult.Success ->
                    if (temp.length() > 0 && publish(temp, target)) ThumbnailResult.Success(target)
                    else ThumbnailResult.Failure(ThumbnailFailure.Unknown)
                is ThumbnailResult.Failure -> {
                    if (r.reason == ThumbnailFailure.Unreadable) unreadable += stamp
                    r
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ThumbnailResult.Failure(ThumbnailFailure.Unknown)
        } finally {
            temp.delete()
        }
    }

    private fun publish(temp: File, target: File): Boolean { target.delete(); return temp.renameTo(target) }

    private fun stamp(source: File) = "${source.absolutePath}|${source.lastModified()}|${source.length()}"
}
