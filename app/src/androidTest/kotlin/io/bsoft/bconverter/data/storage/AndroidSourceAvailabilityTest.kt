package io.bsoft.bconverter.data.storage

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.bsoft.bconverter.domain.model.MediaSource
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidSourceAvailabilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val availability = AndroidSourceAvailability(context.contentResolver)

    private fun tempFile() = File.createTempFile("src", ".mp4", context.cacheDir).also { it.deleteOnExit() }

    @Test fun an_existing_file_is_available() {
        assertTrue(availability.isAvailable(MediaSource.FileSource(tempFile())))
    }

    @Test fun a_deleted_file_is_not_available() {
        val f = tempFile().also { it.delete() }
        assertFalse(availability.isAvailable(MediaSource.FileSource(f)))
    }

    @Test fun a_content_uri_nobody_serves_is_not_available() {
        assertFalse(availability.isAvailable(MediaSource.UriSource(Uri.parse("content://io.bsoft.nonexistent/1"))))
    }

    @Test fun a_readable_file_uri_is_available() {
        assertTrue(availability.isAvailable(MediaSource.UriSource(Uri.fromFile(tempFile()))))
    }
}
