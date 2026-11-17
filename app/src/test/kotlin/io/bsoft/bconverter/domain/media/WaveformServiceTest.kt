package io.bsoft.bconverter.domain.media

import io.bsoft.bconverter.data.storage.DiskWaveformStore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformServiceTest {
    private class FakeWaveforms(var delayMs: Long = 0, var result: () -> WaveformResult = { WaveformResult.Success(Waveform(listOf(0.5f, 1f), 10)) }) : WaveformGenerator {
        val calls = AtomicInteger()
        override suspend fun generate(source: File): WaveformResult { calls.incrementAndGet(); if (delayMs > 0) delay(delayMs); return result() }
    }

    private val dir = Files.createTempDirectory("wave").toFile().also { it.deleteOnExit() }
    private val source = File(dir, "a.mp3").apply { writeBytes(ByteArray(10)) }
    private val gen = FakeWaveforms()
    private fun service() = WaveformService(gen, DiskWaveformStore(File(dir, "cache")))

    private fun peaks(r: WaveformResult) = (r as WaveformResult.Success).waveform.peaks

    @Test fun a_waveform_is_generated() = runBlocking {
        assertEquals(2, peaks(service().waveform(source)).size)
    }

    @Test fun the_second_request_comes_from_the_cache_even_for_a_new_service() = runBlocking {
        service().waveform(source)
        val again = service().waveform(source)
        assertEquals(1, gen.calls.get())
        assertEquals(1f, peaks(again)[1], 0.01f)
    }

    @Test fun a_changed_source_is_analysed_again() = runBlocking {
        val s = service(); s.waveform(source)
        source.writeBytes(ByteArray(30)); source.setLastModified(source.lastModified() + 5_000)
        s.waveform(source)
        assertEquals(2, gen.calls.get())
    }

    @Test fun empty_audio_is_a_valid_cached_result() = runBlocking {
        gen.result = { WaveformResult.Success(Waveform(emptyList(), 10)) }
        val s = service(); s.waveform(source); val r = s.waveform(source)
        assertTrue(peaks(r).isEmpty()); assertEquals(1, gen.calls.get())
    }

    @Test fun an_invalid_source_fails_without_calling_the_generator() = runBlocking {
        assertEquals(WaveformResult.Failure(WaveformFailure.SourceMissing), service().waveform(File(dir, "gone.mp3")))
        assertEquals(0, gen.calls.get())
    }

    @Test fun a_failure_is_reported_and_not_retried_for_the_same_source() = runBlocking {
        gen.result = { WaveformResult.Failure(WaveformFailure.Unreadable) }
        val s = service()
        val r = listOf(s.waveform(source), s.waveform(source))
        assertTrue(r.all { it == WaveformResult.Failure(WaveformFailure.Unreadable) })
        assertEquals(1, gen.calls.get())
    }

    @Test fun forgetting_allows_another_attempt() = runBlocking {
        gen.result = { WaveformResult.Failure(WaveformFailure.Unreadable) }
        val s = service(); s.waveform(source); s.forget(source); s.waveform(source)
        assertEquals(2, gen.calls.get())
    }

    @Test fun a_generator_that_throws_is_reported_not_propagated() = runBlocking {
        gen.result = { throw IllegalStateException("boom") }
        assertEquals(WaveformResult.Failure(WaveformFailure.Unknown), service().waveform(source))
    }

    @Test fun a_damaged_cache_entry_is_replaced() = runBlocking {
        val store = DiskWaveformStore(File(dir, "cache"))
        service().waveform(source)
        File(dir, "cache").listFiles()!!.forEach { it.writeBytes(byteArrayOf(7)) }
        val r = WaveformService(gen, store).waveform(source)
        assertEquals(2, peaks(r).size); assertEquals(2, gen.calls.get())
    }

    @Test fun concurrent_requests_for_one_source_analyse_it_once() = runBlocking {
        gen.delayMs = 40
        val s = service()
        (1..5).map { async { s.waveform(source) } }.awaitAll()
        assertEquals(1, gen.calls.get())
    }
}
