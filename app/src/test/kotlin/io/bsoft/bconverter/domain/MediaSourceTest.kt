package io.bsoft.bconverter.domain.model

import android.net.Uri
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaSourceTest {
    @Test fun content_uri_source_requires_staging() {
        val source = MediaSource.UriSource(Uri.parse("content://media/external/video/media/42"))
        assertTrue(source.requiresStaging)
    }

    @Test fun file_source_does_not_require_staging() {
        assertFalse(MediaSource.FileSource(File("/tmp/a.mp4")).requiresStaging)
    }

    @Test fun uri_sources_with_same_uri_are_equal() {
        val a = MediaSource.UriSource(Uri.parse("content://x/1"))
        val b = MediaSource.UriSource(Uri.parse("content://x/1"))
        assertEquals(a, b)
    }
}
