package io.bsoft.bconverter.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class OverlayLayerCacheTest {
    private lateinit var dir: File
    private var now = 1000L

    private class Fake(var bytes: Int = 100, var result: RasterizedOverlay? = RasterizedOverlay(40, 20), var throws: Boolean = false) : OverlayRasterizer {
        var calls = 0
        override fun render(request: OverlayRasterRequest, out: File): RasterizedOverlay? {
            calls++
            if (throws) throw IllegalStateException("boom")
            out.writeBytes(ByteArray(bytes) { 1 })
            return result
        }
    }

    @Before fun setUp() { dir = Files.createTempDirectory("olc").toFile() }
    private fun cache(f: OverlayRasterizer, max: Long = 1_000_000) = OverlayLayerCache(dir, f, max) { now++ }
    private fun req(text: String = "Hi") = OverlayRasterRequest(TextOverlay("a", text, 0, 1000), 1280, 720, null, 0)

    @Test fun draws_then_names_the_file_with_key_and_size() {
        val f = Fake(); val c = cache(f).layerFor(req())!!
        assertEquals(40, c.width); assertEquals(20, c.height)
        assertEquals("ol-${OverlayLayerKey.of(req())}-40x20.png", c.file.name)
        assertTrue(c.file.isFile); assertEquals(1, f.calls)
    }
    @Test fun second_request_reuses_the_file_without_drawing() {
        val f = Fake(); val c = cache(f)
        val a = c.layerFor(req())!!; val b = c.layerFor(req())!!
        assertEquals(a.file, b.file); assertEquals(40, b.width); assertEquals(1, f.calls)
    }
    @Test fun different_text_is_drawn_again() {
        val f = Fake(); val c = cache(f); c.layerFor(req("A")); c.layerFor(req("B"))
        assertEquals(2, f.calls)
    }
    @Test fun rasterizer_returning_null_gives_null_and_leaves_nothing() {
        val c = cache(Fake(result = null))
        assertNull(c.layerFor(req())); assertEquals(0, dir.listFiles()!!.size)
    }
    @Test fun rasterizer_throwing_gives_null_and_leaves_nothing() {
        assertNull(cache(Fake(throws = true)).layerFor(req())); assertEquals(0, dir.listFiles()!!.size)
    }
    @Test fun empty_output_counts_as_failure() {
        assertNull(cache(Fake(bytes = 0)).layerFor(req())); assertEquals(0, dir.listFiles()!!.size)
    }
    @Test fun empty_cached_file_is_not_reused() {
        val f = Fake(); val c = cache(f); val a = c.layerFor(req())!!
        a.file.writeBytes(ByteArray(0))
        assertNotNull(c.layerFor(req())); assertEquals(2, f.calls)
    }
    @Test fun trim_deletes_least_recently_used_first() {
        val f = Fake(bytes = 100); val c = cache(f, max = 250)
        val a = c.layerFor(req("A"))!!; val b = c.layerFor(req("B"))!!; val d = c.layerFor(req("C"))!!
        c.layerFor(req("A")) // A is now the most recently used
        c.trim()
        assertTrue(a.file.exists()); assertFalse(b.file.exists()); assertTrue(d.file.exists())
    }
    @Test fun trim_never_deletes_files_in_use() {
        val c = cache(Fake(bytes = 100), max = 50)
        val a = c.layerFor(req("A"))!!; val b = c.layerFor(req("B"))!!
        c.trim(keep = setOf(a.file, b.file))
        assertTrue(a.file.exists()); assertTrue(b.file.exists())
    }
    @Test fun trim_removes_leftover_part_files() {
        val part = File(dir, "ol-abc.part").also { it.writeBytes(ByteArray(10)) }
        cache(Fake()).trim()
        assertFalse(part.exists())
    }
    @Test fun trim_ignores_unrelated_files() {
        val other = File(dir, "notes.txt").also { it.writeBytes(ByteArray(10_000)) }
        cache(Fake(), max = 10).trim()
        assertTrue(other.exists())
    }
    @Test fun clear_empties_the_folder() {
        val c = cache(Fake()); c.layerFor(req()); c.clear()
        assertEquals(0, dir.listFiles()!!.size)
    }
}
