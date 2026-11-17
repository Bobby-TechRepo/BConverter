package io.bsoft.bconverter.data.storage

import android.net.Uri
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.domain.model.ContentInfo
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.fakes.FakeContentReader
import io.bsoft.bconverter.fakes.FakeContentReader.Entry
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DefaultInputResolverTest {
    @get:Rule val tmp = TemporaryFolder()
    private val reader = FakeContentReader()
    private val free = FakeFreeSpaceProvider()
    private lateinit var staging: StagingDirectory
    private val uri: Uri = Uri.parse("content://media/external/video/media/1")
    private val source = MediaSource.UriSource(uri)

    @Before fun setUp() { staging = StagingDirectory(File(tmp.root, "staging")) }

    private fun TestScope.resolver(buffer: Int = 4) = DefaultInputResolver(
        reader = reader, staging = staging, freeSpace = free,
        ioDispatcher = StandardTestDispatcher(testScheduler), bufferSize = buffer,
    )

    private fun video(bytes: ByteArray, size: Long? = bytes.size.toLong(), mime: String = "video/mp4", name: String = "clip.mp4") =
        Entry(ContentInfo(name, mime, size), bytes)

    private fun failure(r: ResolveResult) = (r as ResolveResult.Failure).error

    // ---- content:// sources ----
    @Test fun stages_content_uri_with_identical_bytes_inside_staging_dir() = runTest {
        val data = ByteArray(10) { it.toByte() }
        reader.put(uri, video(data))
        val input = (resolver().resolve(source) as ResolveResult.Success).input
        assertTrue(input.isStaged)
        assertEquals(staging.root, input.file.parentFile)
        assertArrayEquals(data, input.file.readBytes())
    }

    @Test fun result_carries_display_name_and_staged_size() = runTest {
        reader.put(uri, video(ByteArray(10)))
        val input = (resolver().resolve(source) as ResolveResult.Success).input
        assertEquals("clip.mp4", input.displayName); assertEquals(10L, input.sizeBytes)
    }

    @Test fun unknown_size_is_still_staged() = runTest {
        reader.put(uri, video(ByteArray(6), size = null))
        val input = (resolver().resolve(source) as ResolveResult.Success).input
        assertEquals(6L, input.sizeBytes)
    }

    @Test fun missing_content_is_not_found() = runTest {
        assertEquals(InputError.NotFound, failure(resolver().resolve(source)))
    }

    @Test fun security_exception_on_info_is_permission_denied() = runTest {
        reader.put(uri, Entry(ContentInfo("a.mp4", "video/mp4", 1), infoError = SecurityException("no grant")))
        assertEquals(InputError.PermissionDenied, failure(resolver().resolve(source)))
    }

    @Test fun security_exception_on_open_is_permission_denied_and_leaves_no_file() = runTest {
        reader.put(uri, Entry(ContentInfo("a.mp4", "video/mp4", 1), openError = SecurityException("revoked")))
        assertEquals(InputError.PermissionDenied, failure(resolver().resolve(source)))
        assertTrue(staging.listFiles().isEmpty())
    }

    @Test fun io_exception_on_open_is_unreadable() = runTest {
        reader.put(uri, Entry(ContentInfo("a.mp4", "video/mp4", 1), openError = IOException("io")))
        assertEquals(InputError.Unreadable, failure(resolver().resolve(source)))
    }

    @Test fun stream_failing_mid_copy_is_unreadable_and_partial_file_is_deleted() = runTest {
        reader.put(uri, Entry(ContentInfo("a.mp4", "video/mp4", 10), ByteArray(10), failAfterBytes = 4))
        assertEquals(InputError.Unreadable, failure(resolver().resolve(source)))
        assertTrue(staging.listFiles().isEmpty())
    }

    @Test fun unsupported_type_is_rejected_before_any_staging() = runTest {
        reader.put(uri, video(ByteArray(3), mime = "image/png", name = "a.png"))
        assertEquals(InputError.UnsupportedType("image/png"), failure(resolver().resolve(source)))
        assertEquals(0, reader.openCount)
    }

    @Test fun empty_content_is_rejected() = runTest {
        reader.put(uri, video(ByteArray(0), size = 0))
        assertEquals(InputError.Empty, failure(resolver().resolve(source)))
    }

    @Test fun insufficient_storage_is_reported_before_copying() = runTest {
        free.available = 5
        reader.put(uri, video(ByteArray(10)))
        assertEquals(InputError.InsufficientStorage(10, 5), failure(resolver().resolve(source)))
        assertEquals(0, reader.openCount)
    }

    @Test fun cancellation_mid_copy_deletes_partial_file() = runTest {
        lateinit var job: Job
        reader.put(uri, Entry(ContentInfo("a.mp4", "video/mp4", 10), ByteArray(10), onRead = { job.cancel() }))
        val r = resolver()
        job = launch { r.resolve(source) }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(staging.listFiles().isEmpty())
    }

    // ---- file sources ----
    @Test fun readable_file_is_used_in_place_without_staging() = runTest {
        val f = tmp.newFile("clip.mp4").apply { writeBytes(ByteArray(3)) }
        val input = (resolver().resolve(MediaSource.FileSource(f)) as ResolveResult.Success).input
        assertEquals(f, input.file); assertFalse(input.isStaged); assertEquals(3L, input.sizeBytes)
        assertTrue(staging.listFiles().isEmpty())
    }

    @Test fun missing_file_is_not_found() = runTest {
        assertEquals(InputError.NotFound, failure(resolver().resolve(MediaSource.FileSource(File(tmp.root, "nope.mp4")))))
    }

    @Test fun directory_is_unreadable() = runTest {
        assertEquals(InputError.Unreadable, failure(resolver().resolve(MediaSource.FileSource(tmp.newFolder("d.mp4")))))
    }

    @Test fun unsupported_file_extension_is_rejected() = runTest {
        val f = tmp.newFile("notes.txt").apply { writeBytes(ByteArray(3)) }
        assertEquals(InputError.UnsupportedType(null), failure(resolver().resolve(MediaSource.FileSource(f))))
    }

    @Test fun empty_file_is_rejected() = runTest {
        assertEquals(InputError.Empty, failure(resolver().resolve(MediaSource.FileSource(tmp.newFile("e.mp4")))))
    }

    // ---- release ----
    @Test fun release_deletes_staged_file() = runTest {
        reader.put(uri, video(ByteArray(4)))
        val r = resolver()
        val input = (r.resolve(source) as ResolveResult.Success).input
        r.release(input)
        assertFalse(input.file.exists())
    }

    @Test fun release_never_deletes_a_users_own_file() = runTest {
        val f = tmp.newFile("mine.mp4").apply { writeBytes(ByteArray(3)) }
        val r = resolver()
        r.release((r.resolve(MediaSource.FileSource(f)) as ResolveResult.Success).input)
        assertTrue(f.exists())
    }

    // ---- reading in place (implementation plan step 15) ----
    private class FakeInPlace(private val works: Boolean) : io.bsoft.bconverter.core.storage.InPlaceOpener {
        val handles = mutableListOf<Handle>()
        class Handle(override val file: File) : io.bsoft.bconverter.core.storage.InPlaceHandle {
            var closed = false
            override fun close() { closed = true; file.delete() }
        }
        override fun open(uri: Uri, link: File): io.bsoft.bconverter.core.storage.InPlaceHandle? {
            if (!works) return null
            link.parentFile?.mkdirs(); link.writeText("in place")
            return Handle(link).also { handles += it }
        }
    }

    private fun TestScope.inPlaceResolver(opener: FakeInPlace) = DefaultInputResolver(
        reader = reader, staging = staging, freeSpace = free,
        ioDispatcher = StandardTestDispatcher(testScheduler), bufferSize = 4, inPlace = opener,
    )

    @Test fun a_document_that_can_be_read_in_place_is_not_copied() = runTest {
        reader.put(uri, video(ByteArray(10)))
        val opener = FakeInPlace(works = true)
        val input = (inPlaceResolver(opener).resolve(source) as ResolveResult.Success).input
        assertEquals("in place", input.file.readText()) // the link, not a copy of the 10 bytes
        assertEquals(staging.root, input.file.parentFile); assertEquals(10L, input.sizeBytes); assertTrue(input.isStaged)
    }

    @Test fun reading_in_place_needs_no_free_space_for_a_copy() = runTest {
        reader.put(uri, video(ByteArray(10)))
        free.available = 0
        assertTrue(inPlaceResolver(FakeInPlace(works = true)).resolve(source) is ResolveResult.Success)
    }

    @Test fun when_reading_in_place_is_not_possible_the_document_is_copied() = runTest {
        val data = ByteArray(10) { it.toByte() }
        reader.put(uri, video(data))
        val input = (inPlaceResolver(FakeInPlace(works = false)).resolve(source) as ResolveResult.Success).input
        assertArrayEquals(data, input.file.readBytes())
    }

    @Test fun releasing_an_in_place_input_closes_it_and_removes_the_link() = runTest {
        reader.put(uri, video(ByteArray(10)))
        val opener = FakeInPlace(works = true)
        val resolver = inPlaceResolver(opener)
        val input = (resolver.resolve(source) as ResolveResult.Success).input
        resolver.release(input)
        assertTrue(opener.handles.single().closed); assertFalse(input.file.exists())
    }
}
