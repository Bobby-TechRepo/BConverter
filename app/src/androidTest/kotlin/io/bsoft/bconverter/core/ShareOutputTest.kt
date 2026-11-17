package io.bsoft.bconverter.core

import android.content.Intent
import android.net.Uri
import io.bsoft.bconverter.core.ui.shareIntent
import io.bsoft.bconverter.domain.model.SavedOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareOutputTest {
    private val uri = Uri.parse("content://flow/out")
    private val chooser = shareIntent(SavedOutput(uri, "video/mp4"), "Share your file")
    private val send: Intent get() = @Suppress("DEPRECATION") chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!

    @Test fun it_opens_the_system_chooser_for_a_send() {
        assertEquals(Intent.ACTION_CHOOSER, chooser.action); assertEquals(Intent.ACTION_SEND, send.action)
    }

    @Test fun it_shares_the_saved_file_by_uri_with_its_type_and_read_permission() {
        assertEquals("video/mp4", send.type)
        assertEquals(uri, @Suppress("DEPRECATION") send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
