package io.bsoft.bconverter.domain.media

import java.io.File
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/** Loudness over time: one peak (0..1) per 1/[peaksPerSecond] second. Empty for empty audio. */
data class Waveform(val peaks: List<Float>, val peaksPerSecond: Int) {
    init { require(peaksPerSecond > 0) { "peaksPerSecond must be > 0" } }

    val durationMs: Long get() = peaks.size * 1000L / peaksPerSecond

    /** [count] bars for the audio between [fromMs] and [toMs]; each bar is the loudest peak in its slice, silence outside the audio. */
    fun bars(count: Int, fromMs: Long, toMs: Long): List<Float> {
        if (count <= 0 || toMs <= fromMs) return emptyList()
        val span = toMs - fromMs
        return List(count) { i ->
            val t0 = fromMs + span * i / count
            val t1 = fromMs + span * (i + 1) / count
            val start = (t0 * peaksPerSecond / 1000).coerceAtLeast(0)
            val end = maxOf((t1 * peaksPerSecond + 999) / 1000, start + 1)
            var best = 0f
            var p = start
            while (p < end && p < peaks.size) { if (peaks[p.toInt()] > best) best = peaks[p.toInt()]; p++ }
            best
        }
    }
}

enum class WaveformFailure { SourceMissing, Unreadable, Unknown }

sealed interface WaveformResult {
    data class Success(val waveform: Waveform) : WaveformResult
    data class Failure(val reason: WaveformFailure) : WaveformResult
}

/** Analyses an audio (or video) file's sound (spec §28). Must report problems as a [WaveformResult.Failure], never throw. */
interface WaveformGenerator {
    suspend fun generate(source: File): WaveformResult
}

interface WaveformStore {
    fun get(key: String): Waveform?
    fun put(key: String, waveform: Waveform)
}

/** Turns 16-bit little-endian mono PCM into peaks, in one streaming pass (so long audio never sits in memory). */
object WaveformReducer {
    fun reduce(input: InputStream, sampleRate: Int, peaksPerSecond: Int): Waveform {
        require(sampleRate > 0) { "sampleRate must be > 0" }
        require(peaksPerSecond > 0) { "peaksPerSecond must be > 0" }
        val window = maxOf(sampleRate / peaksPerSecond, 1)
        val peaks = ArrayList<Float>()
        val buffer = ByteArray(64 * 1024)
        var inWindow = 0
        var loudest = 0
        var low = -1 // a pending low byte when a read ended mid-sample
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            var i = 0
            while (i < n) {
                val b = buffer[i++].toInt()
                if (low < 0) { low = b and 0xFF; continue }
                val sample = (b shl 8) or low // sign comes from the high byte
                low = -1
                loudest = maxOf(loudest, abs(sample.toShort().toInt()))
                if (++inWindow == window) { peaks += loudest / 32768f; inWindow = 0; loudest = 0 }
            }
        }
        if (inWindow > 0) peaks += loudest / 32768f
        return Waveform(peaks, peaksPerSecond)
    }
}

/** Compact on-disk form: version, resolution, then one byte per peak. */
object WaveformCodec {
    private const val VERSION = 1

    fun encode(w: Waveform): ByteArray {
        val out = ByteArray(3 + w.peaks.size)
        out[0] = VERSION.toByte(); out[1] = (w.peaksPerSecond shr 8).toByte(); out[2] = w.peaksPerSecond.toByte()
        w.peaks.forEachIndexed { i, p -> out[3 + i] = (p.coerceIn(0f, 1f) * 255).roundToInt().toByte() }
        return out
    }

    fun decode(bytes: ByteArray): Waveform? {
        if (bytes.size < 3 || bytes[0].toInt() != VERSION) return null
        val pps = ((bytes[1].toInt() and 0xFF) shl 8) or (bytes[2].toInt() and 0xFF)
        if (pps == 0) return null
        return Waveform(List(bytes.size - 3) { (bytes[3 + it].toInt() and 0xFF) / 255f }, pps)
    }
}
