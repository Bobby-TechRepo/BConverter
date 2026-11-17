package io.bsoft.bconverter.media.thumbnail

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.IntegrationFixtures
import io.bsoft.bconverter.domain.media.ThumbnailFailure
import io.bsoft.bconverter.domain.media.ThumbnailResult
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataThumbnailGeneratorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val generator = MediaMetadataThumbnailGenerator()
    private val dir = File(context.cacheDir, "thumb-test").apply { mkdirs() }
    private fun target(name: String) = File(dir, name).apply { delete() }

    @Test fun a_frame_is_saved_as_a_jpeg_of_the_requested_width() = runBlocking {
        val out = target("ok.jpg")
        val r = generator.generate(IntegrationFixtures.sampleMp4(context), 1_000, 64, out)
        assertEquals(ThumbnailResult.Success(out), r)
        val bitmap = BitmapFactory.decodeFile(out.path)
        assertNotNull(bitmap); assertEquals(64, bitmap.width); assertTrue(bitmap.height > 0)
    }

    @Test fun a_position_past_the_end_still_gives_a_frame() = runBlocking {
        val r = generator.generate(IntegrationFixtures.sampleMp4(context), 60_000, 64, target("late.jpg"))
        assertTrue(r is ThumbnailResult.Success)
    }

    @Test fun a_missing_file_is_reported() = runBlocking {
        assertEquals(ThumbnailResult.Failure(ThumbnailFailure.SourceMissing), generator.generate(File(dir, "none.mp4"), 0, 64, target("x.jpg")))
    }

    @Test fun a_corrupt_file_is_reported_not_thrown() = runBlocking {
        val bad = File(dir, "bad.mp4").apply { writeBytes(ByteArray(2_000) { (it * 31).toByte() }) }
        assertEquals(ThumbnailResult.Failure(ThumbnailFailure.Unreadable), generator.generate(bad, 0, 64, target("bad.jpg")))
    }
}
