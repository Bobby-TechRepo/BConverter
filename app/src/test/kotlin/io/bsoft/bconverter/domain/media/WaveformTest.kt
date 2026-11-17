package io.bsoft.bconverter.domain.media

import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformTest {
    /** 16-bit little-endian PCM from sample values. */
    private fun pcm(vararg samples: Int): ByteArray = ByteArray(samples.size * 2).also { b ->
        samples.forEachIndexed { i, s -> b[2 * i] = (s and 0xFF).toByte(); b[2 * i + 1] = ((s shr 8) and 0xFF).toByte() }
    }
    private fun reduce(bytes: ByteArray, rate: Int = 100, pps: Int = 10) = WaveformReducer.reduce(ByteArrayInputStream(bytes), rate, pps)

    // ---- reducer ----
    @Test fun empty_audio_gives_an_empty_waveform() {
        val w = reduce(ByteArray(0))
        assertTrue(w.peaks.isEmpty()); assertEquals(0L, w.durationMs)
    }

    @Test fun silence_has_zero_peaks() {
        val w = reduce(pcm(*IntArray(100)))
        assertEquals(List(10) { 0f }, w.peaks)
    }

    @Test fun each_window_keeps_its_loudest_sample_whatever_its_sign() {
        // rate 100, 10 peaks/s -> windows of 10 samples
        val w = reduce(pcm(*IntArray(10) { if (it == 3) 16_384 else 0 }, *IntArray(10) { if (it == 7) -32_768 else 100 }))
        assertEquals(0.5f, w.peaks[0], 0.001f)
        assertEquals(1f, w.peaks[1], 0.001f)
    }

    @Test fun a_short_clip_still_gets_one_peak_for_its_partial_window() {
        val w = reduce(pcm(8_192, 0, 0))
        assertEquals(1, w.peaks.size); assertEquals(0.25f, w.peaks[0], 0.001f)
    }

    @Test fun a_dangling_odd_byte_is_ignored() {
        assertEquals(1, reduce(pcm(100) + byteArrayOf(5)).peaks.size)
    }

    @Test fun a_long_clip_is_reduced_in_one_pass_to_the_right_number_of_peaks() {
        val seconds = 600
        val stream = object : InputStream() { // ten minutes at 8 kHz without allocating it all
            private var left = 8_000L * seconds * 2
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt(); java.util.Arrays.fill(b, off, off + n, 0); left -= n; return n
            }
        }
        val w = WaveformReducer.reduce(stream, 8_000, 20)
        assertEquals(seconds * 20, w.peaks.size); assertEquals(seconds * 1000L, w.durationMs)
    }

    @Test fun peaks_are_always_within_zero_and_one() {
        val w = reduce(pcm(-32_768, 32_767, -1, 0, 12_345))
        assertTrue(w.peaks.all { it in 0f..1f })
    }

    @Test fun invalid_settings_are_rejected() {
        try { reduce(ByteArray(2), rate = 0); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) {}
        try { reduce(ByteArray(2), pps = 0); throw AssertionError("expected failure") } catch (_: IllegalArgumentException) {}
    }

    // ---- bars for drawing ----
    private val wave = Waveform(listOf(0.1f, 0.9f, 0.2f, 0.4f), peaksPerSecond = 10) // 400 ms

    @Test fun bars_take_the_loudest_peak_of_each_slice() {
        assertEquals(listOf(0.9f, 0.4f), wave.bars(2, 0, 400))
    }

    @Test fun bars_of_a_sub_range_use_only_that_range() {
        assertEquals(listOf(0.2f, 0.4f), wave.bars(2, 200, 400))
    }

    @Test fun more_bars_than_peaks_repeat_the_covering_peak() {
        assertEquals(listOf(0.1f, 0.1f, 0.9f, 0.9f), wave.bars(4, 0, 200))
    }

    @Test fun bars_outside_the_audio_are_silent() {
        assertEquals(listOf(0f, 0f), wave.bars(2, 1_000, 2_000))
        assertEquals(listOf(0f), Waveform(emptyList(), 10).bars(1, 0, 100))
    }

    @Test fun nonsense_bar_requests_give_nothing() {
        assertTrue(wave.bars(0, 0, 100).isEmpty()); assertTrue(wave.bars(3, 100, 100).isEmpty())
    }

    // ---- codec ----
    @Test fun a_waveform_survives_encoding() {
        val back = WaveformCodec.decode(WaveformCodec.encode(wave))!!
        assertEquals(wave.peaksPerSecond, back.peaksPerSecond)
        assertEquals(wave.peaks.size, back.peaks.size)
        wave.peaks.zip(back.peaks).forEach { (a, b) -> assertEquals(a, b, 1f / 255f) }
    }

    @Test fun an_empty_waveform_survives_encoding() {
        assertEquals(Waveform(emptyList(), 20), WaveformCodec.decode(WaveformCodec.encode(Waveform(emptyList(), 20))))
    }

    @Test fun damaged_data_is_rejected_not_thrown() {
        assertNull(WaveformCodec.decode(ByteArray(0)))
        assertNull(WaveformCodec.decode(byteArrayOf(9, 0, 10)))      // unknown version
        assertNull(WaveformCodec.decode(byteArrayOf(1, 0, 0)))        // zero resolution
        assertNull(WaveformCodec.decode(byteArrayOf(1)))              // truncated header
    }
}
