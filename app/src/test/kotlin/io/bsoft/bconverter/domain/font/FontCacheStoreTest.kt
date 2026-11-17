package io.bsoft.bconverter.domain.font

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FontCacheStoreTest {
    private lateinit var root: File
    private lateinit var fonts: File
    private var now = 1000L
    private lateinit var store: FontCacheStore
    private fun font(size: Int = 2000) = ByteArray(size).also { byteArrayOf(0, 1, 0, 0).copyInto(it) }

    @Before fun setUp() { root = Files.createTempDirectory("fc").toFile(); fonts = File(root, "fonts"); store = FontCacheStore(fonts, File(root, "index.json")) { now } }
    @After fun tearDown() { root.deleteRecursively() }

    @Test fun a_stored_font_is_listed_with_its_file() {
        assertTrue(store.put("Open Sans", FontVariant(700), FontLicense.OFL, font()))
        val l = store.list().single()
        assertEquals("Open Sans", l.family); assertEquals(FontVariant(700), l.variant); assertEquals(FontSource.DOWNLOADED, l.source); assertEquals(FontLicense.OFL, l.license)
        assertTrue(File(l.path).isFile); assertTrue(File(l.path).name.startsWith("gf-")); assertTrue(store.has("open sans", FontVariant(700)))
    }
    @Test fun something_that_is_not_a_font_is_refused_and_leaves_nothing() {
        assertFalse(store.put("X", FontVariant(400), FontLicense.OFL, ByteArray(5000)))
        assertTrue(store.list().isEmpty()); assertTrue(fonts.listFiles().orEmpty().isEmpty())
    }
    @Test fun storing_again_replaces_instead_of_duplicating() {
        store.put("X", FontVariant(400), FontLicense.OFL, font(2000)); store.put("X", FontVariant(400), FontLicense.OFL, font(3000))
        assertEquals(1, store.list().size); assertEquals(3000L, store.totalBytes())
    }
    @Test fun the_index_survives_a_new_store_object() {
        store.put("X", FontVariant(400), FontLicense.APACHE, font())
        assertEquals(FontLicense.APACHE, FontCacheStore(fonts, File(root, "index.json")).list().single().license)
    }
    @Test fun an_entry_whose_file_vanished_is_dropped() {
        store.put("X", FontVariant(400), FontLicense.OFL, font()); File(store.list().single().path).delete()
        assertTrue(store.list().isEmpty())
    }
    @Test fun a_damaged_index_means_an_empty_cache() { store.put("X", FontVariant(400), FontLicense.OFL, font()); File(root, "index.json").writeText("not json"); assertTrue(store.list().isEmpty()) }
    @Test fun remove_deletes_every_variant_of_a_family() {
        store.put("X", FontVariant(400), FontLicense.OFL, font()); store.put("X", FontVariant(700), FontLicense.OFL, font()); store.put("Y", FontVariant(400), FontLicense.OFL, font())
        store.remove("x"); assertEquals(listOf("Y"), store.list().map { it.family }); assertEquals(1, fonts.listFiles().orEmpty().size)
    }
    @Test fun trim_removes_the_least_recently_used_first() {
        now = 1; store.put("Old", FontVariant(400), FontLicense.OFL, font(3000)); now = 2; store.put("Mid", FontVariant(400), FontLicense.OFL, font(3000)); now = 3; store.put("New", FontVariant(400), FontLicense.OFL, font(3000))
        now = 10; store.touch("Old")
        assertEquals(listOf("Mid"), store.trim(6500)); assertEquals(setOf("Old", "New"), store.list().map { it.family }.toSet())
    }
    @Test fun trim_never_removes_protected_families() {
        now = 1; store.put("Keep", FontVariant(400), FontLicense.OFL, font(3000)); now = 2; store.put("Other", FontVariant(400), FontLicense.OFL, font(3000))
        assertEquals(listOf("Other"), store.trim(1, keep = setOf("keep"))); assertEquals(listOf("Keep"), store.list().map { it.family })
    }
    @Test fun trim_does_nothing_when_within_the_limit() { store.put("X", FontVariant(400), FontLicense.OFL, font()); assertTrue(store.trim(1_000_000).isEmpty()); assertEquals(1, store.list().size) }
    @Test fun clear_removes_all_downloaded_fonts_but_not_other_files_in_the_folder() {
        File(fonts.apply { mkdirs() }, "Roboto-Regular.ttf").writeBytes(font())
        store.put("X", FontVariant(400), FontLicense.OFL, font()); store.clear()
        assertTrue(store.list().isEmpty()); assertEquals(listOf("Roboto-Regular.ttf"), fonts.listFiles().orEmpty().map { it.name })
    }
}
