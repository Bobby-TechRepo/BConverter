package io.bsoft.bconverter.domain.media

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cached, de-duplicated waveform access (spec §28). A source that cannot be analysed is not retried on every request. */
class WaveformService(private val generator: WaveformGenerator, private val store: WaveformStore) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val failed = ConcurrentHashMap<String, WaveformFailure>()

    suspend fun waveform(source: File): WaveformResult {
        if (!source.isFile) return WaveformResult.Failure(WaveformFailure.SourceMissing)
        val key = stamp(source)
        failed[key]?.let { return WaveformResult.Failure(it) }
        val lock = locks.getOrPut(key) { Mutex() }
        try {
            return lock.withLock {
                store.get(key)?.let { return@withLock WaveformResult.Success(it) }
                failed[key]?.let { return@withLock WaveformResult.Failure(it) }
                analyse(source, key)
            }
        } finally {
            locks.remove(key, lock)
        }
    }

    fun forget(source: File) { failed.remove(stamp(source)) }

    private suspend fun analyse(source: File, key: String): WaveformResult = try {
        when (val r = generator.generate(source)) {
            is WaveformResult.Success -> { runCatching { store.put(key, r.waveform) }; r }
            is WaveformResult.Failure -> { if (r.reason == WaveformFailure.Unreadable) failed[key] = r.reason; r }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        WaveformResult.Failure(WaveformFailure.Unknown)
    }

    private fun stamp(source: File) = "${source.absolutePath}|${source.lastModified()}|${source.length()}"
}
