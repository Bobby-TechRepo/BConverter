package io.bsoft.bconverter.domain.share

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading what another app shared (step 25). */
class ShareIntentsTest {
    private val send = ShareIntents.ACTION_SEND
    private val multiple = ShareIntents.ACTION_SEND_MULTIPLE

    @Test fun a_single_share_gives_its_stream() {
        assertEquals(listOf("a"), ShareIntents.items(send, "a", null, null))
    }

    @Test fun a_single_share_without_a_stream_falls_back_to_the_clip_data() {
        assertEquals(listOf("c"), ShareIntents.items(send, null, null, listOf("c")))
    }

    @Test fun a_multiple_share_keeps_the_order_and_drops_repeats_and_blanks() {
        assertEquals(listOf("b", "a"), ShareIntents.items(multiple, null, listOf("b", null, "a", "b"), null))
    }

    @Test fun a_multiple_share_with_no_streams_uses_the_clip_data() {
        assertEquals(listOf("x", "y"), ShareIntents.items(multiple, null, listOf(null), listOf("x", "y")))
    }

    @Test fun anything_else_is_not_a_share() {
        assertEquals(emptyList<String>(), ShareIntents.items("android.intent.action.MAIN", "a", listOf("b"), listOf("c")))
        assertEquals(emptyList<String>(), ShareIntents.items(null, "a", null, null))
        assertEquals(emptyList<String>(), ShareIntents.items<String>(send, null, null, null))
    }

    @Test fun a_huge_share_is_cut_to_the_limit() {
        val many = (1..80).map { "v$it" }
        assertEquals(many.take(ShareIntents.MAX_ITEMS), ShareIntents.items(multiple, null, many, null))
    }

    @Test fun one_video_offers_every_tool_and_several_offer_editing_and_batch_conversion() {
        assertEquals(
            listOf(ShareAction.EDIT, ShareAction.CONVERT, ShareAction.EXTRACT_AUDIO, ShareAction.EXTRACT_SUBTITLES),
            ShareIntents.actionsFor(1),
        )
        assertEquals(listOf(ShareAction.EDIT, ShareAction.CONVERT_ALL), ShareIntents.actionsFor(3))
        assertEquals(emptyList<ShareAction>(), ShareIntents.actionsFor(0))
    }
}
