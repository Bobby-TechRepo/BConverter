package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayImageCleanupTest {
    private val hour = 3_600_000L
    private fun file(name: String, ageMs: Long) = StoredImage(name, modifiedMs = 100 * hour - ageMs)
    private val now = 100 * hour

    @Test fun a_picture_no_project_mentions_is_unused() {
        val out = OverlayImageCleanup.unused(listOf(file("img-a.png", 48 * hour)), listOf("""{"image":{"path":"/x/img-b.png"}}"""), now)
        assertEquals(listOf("img-a.png"), out)
    }

    @Test fun a_picture_a_project_uses_is_kept() {
        val out = OverlayImageCleanup.unused(listOf(file("img-a.png", 48 * hour)), listOf("""{"path":"/data/overlay-images/img-a.png"}"""), now)
        assertEquals(emptyList<String>(), out)
    }

    @Test fun a_recent_picture_is_kept_even_if_unused_because_an_open_editor_may_not_have_saved_it_yet() {
        val out = OverlayImageCleanup.unused(listOf(file("img-a.png", 1 * hour)), emptyList(), now)
        assertEquals(emptyList<String>(), out)
    }

    @Test fun with_no_projects_all_old_pictures_are_unused() {
        val out = OverlayImageCleanup.unused(listOf(file("img-a.png", 30 * hour), file("img-b.png", 25 * hour)), emptyList(), now)
        assertEquals(listOf("img-a.png", "img-b.png"), out)
    }

    @Test fun only_our_own_picture_files_are_ever_listed() {
        val out = OverlayImageCleanup.unused(listOf(file("notes.txt", 48 * hour), file("img-a.png.tmp", 48 * hour)), emptyList(), now)
        assertEquals(emptyList<String>(), out)
    }
}
