package io.bsoft.bconverter.domain.project

import org.junit.Assert.assertEquals
import org.junit.Test

class UriGrantCleanupTest {
    private val clip = "content://com.android.providers.media.documents/document/video%3A42"
    private val music = "content://com.android.externalstorage.documents/document/primary%3AMusic%2Fsong.mp3"

    @Test fun a_grant_no_project_mentions_is_released() =
        assertEquals(listOf(music), UriGrantCleanup.unused(listOf(clip, music), listOf("""{"source":"$clip"}""")))

    @Test fun json_escaped_slashes_still_count_as_a_mention() {
        val escaped = clip.replace("/", "\\/")
        assertEquals(emptyList<String>(), UriGrantCleanup.unused(listOf(clip), listOf("""{"source":"$escaped"}""")))
    }

    @Test fun with_no_saved_projects_every_grant_is_unused() =
        assertEquals(listOf(clip, music), UriGrantCleanup.unused(listOf(clip, music), emptyList()))

    @Test fun nothing_granted_means_nothing_to_release() =
        assertEquals(emptyList<String>(), UriGrantCleanup.unused(emptyList(), listOf("""{"source":"$clip"}""")))
}
