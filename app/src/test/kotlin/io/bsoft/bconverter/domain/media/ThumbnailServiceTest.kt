package io.bsoft.bconverter.domain.media

import io.bsoft.bconverter.data.storage.DiskThumbnailStore
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailServiceTest {
    private class FakeGenerator(var delayMs: Long = 0, var result: (Long) -> ThumbnailResult? = { null }) : ThumbnailGenerator {
        val calls = AtomicInteger(); val running = AtomicInteger(); var maxRunning = 0
        val positions = mutableListOf<Long>()
        override suspend fun generate(source: File, positionMs: Long, widthPx: Int, target: File): ThumbnailResult {
            calls.incrementAndGet(); synchronized(positions) { positions += positionMs }
            val now = running.incrementAndGet(); synchronized(this) { maxRunning = maxOf(maxRunning, now) }
            try {
                if (delayMs > 0) delay(delayMs)
                result(positionMs)?.let { return it }
                target.writeBytes(byteArrayOf(1, 2, 3))
                return ThumbnailResult.Success(target)
            } finally { running.decrementAndGet() }
        }
    }

    private val dir = Files.createTempDirectory("svc").toFile().also { it.deleteOnExit() }
    private val source = File(dir, "video.mp4").apply { writeBytes(ByteArray(10)) }
    private val gen = FakeGenerator()
    private fun service(maxConcurrent: Int = 2) = ThumbnailService(gen, DiskThumbnailStore(File(dir, "cache"), 1_000_000), maxConcurrent)

    @Test fun a_thumbnail_is_generated_and_returned_as_a_file() = runBlocking {
        val r = service().thumbnail(source, 1_000, 120)
        assertTrue(r is ThumbnailResult.Success)
        assertTrue((r as ThumbnailResult.Success).file.length() > 0)
        assertFalse(File(r.file.path + ".part").exists())
    }

    @Test fun a_second_request_is_served_from_the_cache() = runBlocking {
        val s = service()
        s.thumbnail(source, 1_000, 120); s.thumbnail(source, 1_000, 120)
        assertEquals(1, gen.calls.get())
    }

    @Test fun another_position_or_size_is_a_different_thumbnail() = runBlocking {
        val s = service()
        s.thumbnail(source, 1_000, 120); s.thumbnail(source, 1_500, 120); s.thumbnail(source, 1_000, 240)
        assertEquals(3, gen.calls.get())
    }

    @Test fun a_changed_source_gets_new_thumbnails() = runBlocking {
        val s = service()
        s.thumbnail(source, 1_000, 120)
        source.writeBytes(ByteArray(20)); source.setLastModified(source.lastModified() + 5_000)
        s.thumbnail(source, 1_000, 120)
        assertEquals(2, gen.calls.get())
    }

    @Test fun a_missing_source_fails_without_calling_the_generator() = runBlocking {
        val r = service().thumbnail(File(dir, "gone.mp4"), 0, 120)
        assertEquals(ThumbnailResult.Failure(ThumbnailFailure.SourceMissing), r)
        assertEquals(0, gen.calls.get())
    }

    @Test fun a_source_that_cannot_be_decoded_is_not_retried_for_every_tile() = runBlocking {
        gen.result = { ThumbnailResult.Failure(ThumbnailFailure.Unreadable) }
        val s = service()
        val results = listOf(0L, 500L, 1_000L).map { s.thumbnail(source, it, 120) }
        assertTrue(results.all { it == ThumbnailResult.Failure(ThumbnailFailure.Unreadable) })
        assertEquals(1, gen.calls.get())
    }

    @Test fun forgetting_a_source_allows_another_attempt() = runBlocking {
        gen.result = { ThumbnailResult.Failure(ThumbnailFailure.Unreadable) }
        val s = service()
        s.thumbnail(source, 0, 120); s.forget(source); s.thumbnail(source, 0, 120)
        assertEquals(2, gen.calls.get())
    }

    @Test fun a_generator_that_throws_is_reported_not_propagated() = runBlocking {
        gen.result = { throw IllegalStateException("boom") }
        val r = service().thumbnail(source, 0, 120)
        assertEquals(ThumbnailResult.Failure(ThumbnailFailure.Unknown), r)
    }

    @Test fun a_failed_attempt_leaves_no_partial_file() = runBlocking {
        gen.result = { ThumbnailResult.Failure(ThumbnailFailure.Unreadable) }
        service().thumbnail(source, 0, 120)
        assertEquals(0, File(dir, "cache").listFiles().orEmpty().size)
    }

    @Test fun identical_concurrent_requests_generate_once() = runBlocking {
        gen.delayMs = 50
        val s = service()
        val all = (1..5).map { async { s.thumbnail(source, 1_000, 120) } }.awaitAll()
        assertTrue(all.all { it is ThumbnailResult.Success })
        assertEquals(1, gen.calls.get())
    }

    @Test fun only_a_few_thumbnails_are_generated_at_once() = runBlocking {
        gen.delayMs = 30
        val s = service(maxConcurrent = 2)
        (1..8).map { async { s.thumbnail(source, it * 1_000L, 120) } }.awaitAll()
        assertEquals(8, gen.calls.get())
        assertTrue("max running was ${gen.maxRunning}", gen.maxRunning <= 2)
    }
}
