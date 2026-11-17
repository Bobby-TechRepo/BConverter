package io.bsoft.bconverter.domain.usecase

import android.net.Uri
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.JobFailure
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.SavedOutput
import io.bsoft.bconverter.domain.repository.DocumentCreator
import io.bsoft.bconverter.fakes.DirectExportScheduler
import io.bsoft.bconverter.fakes.FakeCapabilitySource
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunner
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Converting several files into one folder (implementation plan step 25). */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class BatchConverterTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher)
    private val resolver = FakeInputResolver()
    private val engine = FakeMediaEngine()
    private val publisher = FakeOutputPublisher()
    private val free = FakeFreeSpaceProvider()
    private val registry = ExportRegistry()
    private val folder: Uri = Uri.parse("content://tree/out")
    private val created = mutableListOf<String>()
    private var canCreate = true
    private lateinit var staging: StagingDirectory
    private lateinit var scheduler: DirectExportScheduler

    private fun json(name: String) = checkNotNull(javaClass.getResource("/ffprobe/$name")).readText()

    /** Files named "song…" are audio only; everything else is an H.264/AAC video. */
    private val runner = object : FfprobeRunner {
        override suspend fun run(file: File): FfprobeRunResult =
            FfprobeRunResult.Success(json(if (file.name.contains("song")) "mp3_with_cover.json" else "mp4_h264_aac.json"))
    }
    private val documents = object : DocumentCreator {
        override suspend fun create(folder: Uri, fileName: String, mimeType: String): Uri? =
            if (canCreate) { created += fileName; Uri.parse("content://dest/${created.size}") } else null
    }

    /** content://x/<name> opens as a staged file called <name>. */
    private fun uri(name: String): Uri = Uri.parse("content://x/$name")
    private val inputs = mutableMapOf<String, ResolvedInput>()

    @Before fun setUp() {
        staging = StagingDirectory(File(tmp.root, "staging"))
        scheduler = DirectExportScheduler(scope, ExportJobExecutor(engine, resolver, publisher, registry))
        resolver.resultFor = { source ->
            val name = (source as MediaSource.UriSource).uri.lastPathSegment!!
            if (name == "gone.mp4") ResolveResult.Failure(InputError.NotFound)
            else ResolveResult.Success(
                inputs.getOrPut(name) { ResolvedInput(File(tmp.root, "in-${inputs.size}-$name").apply { createNewFile() }, name, 1_000_000, isStaged = true) },
            )
        }
        engine.onConvert = { it.outputFile.writeText("converted") }
    }

    @After fun tearDown() { scope.cancel() }

    private fun converter() = BatchConverter(
        MediaProber(resolver, runner), FakeCapabilitySource(), resolver, staging, StorageGuard(free, staging),
        ExportJobLauncher(registry, scheduler), documents, scope,
    )

    private fun statuses(c: BatchConverter) = c.run.value!!.items.map { it.status }

    @Test fun every_file_is_converted_into_the_folder_under_its_own_name() = runTest(dispatcher) {
        val c = converter()
        assertTrue(c.start(listOf(uri("a.mp4"), uri("b.mov")), BatchOptions(ConversionPreset.BALANCED, ContainerFormat.MP4), folder))
        runCurrent()
        val run = c.run.value!!
        assertFalse(run.running)
        assertEquals(listOf("a-converted.mp4", "b-converted.mp4"), created)
        assertEquals(2, publisher.published.size)
        assertEquals(
            listOf(
                BatchItemStatus.Saved(SavedOutput(Uri.parse("content://dest/1"), ContainerFormat.MP4.mimeType), "a-converted.mp4"),
                BatchItemStatus.Saved(SavedOutput(Uri.parse("content://dest/2"), ContainerFormat.MP4.mimeType), "b-converted.mp4"),
            ),
            statuses(c),
        )
        assertEquals(listOf("a.mp4", "b.mov"), run.items.map { it.name })
        assertEquals(2, run.savedCount); assertEquals(0, run.problemCount); assertEquals(1f, run.progress, 0f)
        // Each input is released once its file is done; jobs are forgotten once seen.
        assertEquals(2, resolver.released.size); assertTrue(registry.statuses.value.isEmpty())
    }

    @Test fun files_run_one_after_another_with_one_input_open_at_a_time() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val c = converter()
        c.start(listOf(uri("a.mp4"), uri("b.mp4")), BatchOptions(), folder); runCurrent()
        assertTrue(statuses(c)[0] is BatchItemStatus.Converting); assertEquals(BatchItemStatus.Waiting, statuses(c)[1])
        assertEquals(1, resolver.resolved.size); assertEquals(0, c.run.value!!.currentIndex)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
        // The gate is spent, so the second file finishes at once too.
        assertTrue(statuses(c).all { it is BatchItemStatus.Saved })
    }

    @Test fun files_with_the_same_name_get_numbered_outputs() = runTest(dispatcher) {
        val c = converter()
        c.start(listOf(uri("clip.mp4"), uri("Clip.mkv")), BatchOptions(container = ContainerFormat.MP4), folder); runCurrent()
        assertEquals(listOf("clip-converted.mp4", "Clip-converted (2).mp4"), created)
    }

    @Test fun a_file_without_a_picture_or_one_that_cannot_be_read_is_skipped_and_the_rest_go_on() = runTest(dispatcher) {
        val c = converter()
        c.start(listOf(uri("song.mp3"), uri("gone.mp4"), uri("a.mp4")), BatchOptions(), folder); runCurrent()
        val s = statuses(c)
        assertEquals(BatchItemStatus.Skipped(BatchSkip.NO_VIDEO), s[0])
        assertEquals(BatchItemStatus.Skipped(BatchSkip.UNREADABLE), s[1])
        assertTrue(s[2] is BatchItemStatus.Saved)
        assertEquals(listOf("a-converted.mp4"), created)
        assertEquals(2, c.run.value!!.problemCount)
        // The skipped audio file's input is released too.
        assertTrue(resolver.released.any { it.displayName == "song.mp3" })
    }

    @Test fun a_failed_conversion_is_reported_and_the_next_file_still_runs() = runTest(dispatcher) {
        engine.onConvert = { r ->
            r.outputFile.writeText("x")
            engine.processingResult = if (r.input.displayName == "a.mp4") MediaProcessingResult.Failure("boom") else MediaProcessingResult.Success(r.outputFile)
        }
        val c = converter()
        c.start(listOf(uri("a.mp4"), uri("b.mp4")), BatchOptions(), folder); runCurrent()
        assertEquals(BatchItemStatus.Failed(JobFailure.ConversionFailed), statuses(c)[0])
        assertTrue(statuses(c)[1] is BatchItemStatus.Saved)
    }

    @Test fun no_free_space_and_an_uncreatable_file_are_skips_with_reasons() = runTest(dispatcher) {
        free.available = 0
        val c = converter()
        c.start(listOf(uri("a.mp4")), BatchOptions(), folder); runCurrent()
        assertEquals(BatchItemStatus.Skipped(BatchSkip.NO_SPACE), statuses(c)[0])
        c.clear()
        free.available = Long.MAX_VALUE; canCreate = false
        c.start(listOf(uri("b.mp4")), BatchOptions(), folder); runCurrent()
        assertEquals(BatchItemStatus.Skipped(BatchSkip.CANNOT_SAVE), statuses(c)[0])
        assertTrue(engine.converted.isEmpty())
    }

    @Test fun cancelling_stops_the_file_in_progress_and_the_rest() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val c = converter()
        c.start(listOf(uri("a.mp4"), uri("b.mp4"), uri("c.mp4")), BatchOptions(), folder); runCurrent()
        c.cancel(); runCurrent()
        val run = c.run.value!!
        assertFalse(run.running)
        assertEquals(List(3) { BatchItemStatus.Cancelled }, run.items.map { it.status })
        assertEquals(1, engine.converted.size); assertEquals(1, scheduler.cancelled.size)
        assertTrue(publisher.published.isEmpty())
        // The one input that was opened is released.
        assertEquals(1, resolver.released.size)
    }

    @Test fun only_one_batch_runs_at_a_time_and_a_finished_one_can_be_cleared() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val c = converter()
        assertTrue(c.start(listOf(uri("a.mp4")), BatchOptions(), folder)); runCurrent()
        assertFalse(c.start(listOf(uri("b.mp4")), BatchOptions(), folder))
        c.clear(); assertTrue(c.run.value != null) // a running batch is not forgotten
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
        c.clear(); assertEquals(null, c.run.value)
        assertFalse(c.start(emptyList(), BatchOptions(), folder))
    }

    @Test fun the_same_file_picked_twice_is_converted_once() = runTest(dispatcher) {
        val c = converter()
        c.start(listOf(uri("a.mp4"), uri("a.mp4")), BatchOptions(), folder); runCurrent()
        assertEquals(1, c.run.value!!.items.size); assertEquals(1, created.size)
    }

    // ---- names ----
    @Test fun output_names_are_safe_and_never_blank() {
        assertEquals("a_b", BatchNames.baseOf("a:b.mp4"))
        assertEquals("video", BatchNames.baseOf(".mp4"))
        assertEquals("video", BatchNames.baseOf("   "))
        assertEquals("archive.tar", BatchNames.baseOf("archive.tar.gz"))
        assertEquals(120, BatchNames.baseOf("x".repeat(300) + ".mp4").length)
    }

    @Test fun output_names_are_unique_ignoring_case() {
        val used = mutableSetOf<String>()
        assertEquals("a-converted.mkv", BatchNames.fileName("a.mp4", ContainerFormat.MKV, used))
        assertEquals("A-converted (2).mkv", BatchNames.fileName("A.mov", ContainerFormat.MKV, used))
        assertEquals("a-converted (3).mkv", BatchNames.fileName("a.avi", ContainerFormat.MKV, used))
    }

    @Test fun batch_progress_counts_finished_files_whole_and_the_running_one_by_its_progress() {
        val items = listOf(
            BatchItem(uri("a"), BatchItemStatus.Skipped(BatchSkip.NO_VIDEO)),
            BatchItem(uri("b"), BatchItemStatus.Converting(0.5f)),
            BatchItem(uri("c")),
            BatchItem(uri("d")),
        )
        val run = BatchRun(items, BatchOptions(), running = true)
        assertEquals(1.5f / 4, run.progress, 0.0001f); assertEquals(1, run.currentIndex)
    }
}
