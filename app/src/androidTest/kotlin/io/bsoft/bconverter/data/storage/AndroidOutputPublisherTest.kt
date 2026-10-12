package io.bsoft.bconverter.data.storage

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.domain.repository.PublishResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidOutputPublisherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val publisher = AndroidOutputPublisher(context.contentResolver, Dispatchers.IO)
    private val dir = File(context.cacheDir, "publish").apply { mkdirs() }

    @Test fun copies_the_file_into_the_destination() = runBlocking {
        val source = File(dir, "src.bin").apply { writeBytes(ByteArray(300_000) { it.toByte() }) }
        val target = File(dir, "dst.bin").apply { delete() }
        assertEquals(PublishResult.Success, publisher.publish(source, Uri.fromFile(target)))
        assertArrayEquals(source.readBytes(), target.readBytes())
    }

    @Test fun an_unwritable_destination_is_a_failure() = runBlocking {
        val source = File(dir, "src2.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        assertEquals(PublishResult.Failure, publisher.publish(source, Uri.fromFile(File("/nonexistent-dir/out.bin"))))
    }
}
