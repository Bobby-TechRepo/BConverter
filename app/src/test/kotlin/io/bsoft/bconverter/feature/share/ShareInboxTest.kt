package io.bsoft.bconverter.feature.share

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A share is handed from the activity to exactly one tool (step 25). */
@RunWith(RobolectricTestRunner::class)
class ShareInboxTest {
    private val a = Uri.parse("content://x/a")
    private val b = Uri.parse("content://x/b")

    @Test fun a_share_is_pending_until_a_tool_takes_it_once() {
        val inbox = ShareInbox()
        inbox.deliver(listOf(a, b))
        assertEquals(listOf(a, b), inbox.pending.value!!.uris)
        assertEquals(listOf(a, b), inbox.take())
        assertNull(inbox.pending.value); assertNull(inbox.take())
    }

    @Test fun an_empty_share_changes_nothing_and_backing_out_clears_it() {
        val inbox = ShareInbox()
        inbox.deliver(emptyList()); assertNull(inbox.pending.value)
        inbox.deliver(listOf(a)); inbox.clear(); assertNull(inbox.take())
    }

    @Test fun sharing_the_same_files_again_is_a_new_share() {
        val inbox = ShareInbox()
        inbox.deliver(listOf(a)); val first = inbox.pending.value!!.id
        inbox.deliver(listOf(a))
        assertNotEquals(first, inbox.pending.value!!.id)
    }
}
