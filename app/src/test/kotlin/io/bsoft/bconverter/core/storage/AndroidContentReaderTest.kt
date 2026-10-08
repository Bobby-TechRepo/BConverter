package io.bsoft.bconverter.core.storage

import android.content.ContentResolver
import android.net.Uri
import io.bsoft.bconverter.domain.model.ContentInfo
import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AndroidContentReaderTest {
    private val authority = "io.bsoft.bconverter.test"
    private lateinit var resolver: ContentResolver
    private lateinit var reader: AndroidContentReader
    private fun uri(path: String) = Uri.parse("content://$authority$path")

    @Before fun setUp() {
        TestMediaProvider.rows.clear(); TestMediaProvider.types.clear()
        Robolectric.setupContentProvider(TestMediaProvider::class.java, authority)
        resolver = RuntimeEnvironment.getApplication().contentResolver
        reader = AndroidContentReader(resolver)
    }

    @Test fun info_reads_display_name_size_and_mime() {
        TestMediaProvider.rows["/video/1"] = "holiday.mp4" to 1234L
        TestMediaProvider.types["/video/1"] = "video/mp4"
        assertEquals(ContentInfo("holiday.mp4", "video/mp4", 1234L), reader.info(uri("/video/1")))
    }

    @Test fun info_falls_back_to_last_path_segment_when_provider_has_no_row() =
        assertEquals(ContentInfo("clip.mkv", null, null), reader.info(uri("/video/clip.mkv")))

    @Test fun unknown_size_is_reported_as_null() {
        TestMediaProvider.rows["/v"] = "a.mp4" to null
        assertEquals(null, reader.info(uri("/v")).sizeBytes)
    }

    @Test fun open_returns_the_content_bytes() {
        val u = uri("/video/2"); val bytes = byteArrayOf(1, 2, 3, 4)
        shadowOf(resolver).registerInputStream(u, ByteArrayInputStream(bytes))
        assertArrayEquals(bytes, reader.open(u).use { it.readBytes() })
    }
}
