package io.bsoft.bconverter.domain.font

import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GoogleFontsCatalogTest {
    private val json = """{"familyMetadataList":[{"family":"Lobster","category":"Display","fonts":{"400":{}}}]}"""
    private var offline = false
    private var calls = 0
    private val http = object : FontHttp {
        override suspend fun getText(url: String): String { calls++; if (offline) throw IOException("offline"); return json }
        override suspend fun getBytes(url: String, maxBytes: Int) = ByteArray(0)
    }
    private lateinit var dir: File
    private lateinit var cache: File
    private var now = 10_000_000_000L
    private fun catalog() = GoogleFontsCatalog(http, cache, { now }, maxAgeMs = 1000)

    @Before fun setUp() { dir = Files.createTempDirectory("gc").toFile(); cache = File(dir, "catalog.json") }
    @After fun tearDown() { dir.deleteRecursively() }

    @Test fun the_first_load_downloads_and_caches() = runBlocking {
        val r = catalog().load() as CatalogResult.Ready
        assertEquals(listOf("Lobster"), r.fonts.map { it.family }); assertEquals(false, r.fromCacheOnly); assertEquals(1, calls); assertTrue(cache.isFile)
    }
    @Test fun a_fresh_cache_is_used_without_the_network() = runBlocking {
        catalog().load(); cache.setLastModified(now - 10)
        catalog().load(); assertEquals(1, calls)
    }
    @Test fun a_stale_cache_is_refreshed() = runBlocking { catalog().load(); cache.setLastModified(now - 5000); catalog().load(); assertEquals(2, calls) }
    @Test fun offline_with_a_stale_cache_still_works_and_says_so() = runBlocking {
        catalog().load(); cache.setLastModified(now - 5000); offline = true
        val r = catalog().load() as CatalogResult.Ready; assertEquals(true, r.fromCacheOnly); assertEquals(1, r.fonts.size)
    }
    @Test fun offline_with_no_cache_is_unavailable() = runBlocking { offline = true; assertEquals(CatalogResult.Unavailable, catalog().load()) }
    @Test fun refresh_can_be_forced() = runBlocking { catalog().load(); cache.setLastModified(now - 10); catalog().load(forceRefresh = true); assertEquals(2, calls) }
    @Test fun a_garbage_response_does_not_replace_a_good_cache() = runBlocking {
        catalog().load(); cache.setLastModified(now - 5000)
        val bad = GoogleFontsCatalog(object : FontHttp { override suspend fun getText(url: String) = "<html>"; override suspend fun getBytes(url: String, maxBytes: Int) = ByteArray(0) }, cache, { now }, 1000)
        val r = bad.load() as CatalogResult.Ready; assertEquals(true, r.fromCacheOnly); assertEquals("Lobster", r.fonts.single().family)
    }
}
