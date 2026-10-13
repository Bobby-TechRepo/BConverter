package io.bsoft.bconverter.feature.converter

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.DefaultMediaCapabilityRepository
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioPlan
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.VideoQuality
import io.bsoft.bconverter.domain.repository.PublishResult
import io.bsoft.bconverter.fakes.DirectExportScheduler
import io.bsoft.bconverter.fakes.FakeCapabilitySource
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ConverterViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val engine = FakeMediaEngine()
    private val capabilities = FakeCapabilitySource()
    private val publisher = FakeOutputPublisher()
    private val free = FakeFreeSpaceProvider()
    private val registry = ExportRegistry()
    private val scope = CoroutineScope(dispatcher)
    private lateinit var staging: StagingDirectory
    private lateinit var staged: ResolvedInput
    private val uri: Uri = Uri.parse("content://x/1")
    private val destination: Uri = Uri.parse("content://dest/1")
    private fun json(name: String) = checkNotNull(javaClass.getResource("/ffprobe/$name")).readText()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        staging = StagingDirectory(File(tmp.root, "staging"))
        staged = ResolvedInput(tmp.newFile("in.mp4"), "holiday.mp4", 7_340_032, isStaged = true)
        resolver.result = ResolveResult.Success(staged)
        runner.result = FfprobeRunResult.Success(json("mp4_h264_aac.json"))
    }
    @After fun tearDown() { scope.cancel(); Dispatchers.resetMain() }

    private fun vm() = ConverterViewModel(
        MediaProber(resolver, runner), capabilities, resolver, staging, StorageGuard(free, staging),
        ExportJobLauncher(registry, DirectExportScheduler(scope, ExportJobExecutor(engine, resolver, publisher, registry))),
    )
    private fun TestScope.opened(): ConverterViewModel = vm().also { it.onInputPicked(uri); runCurrent() }
    private fun ConverterViewModel.video() = (state.value.settings!!.video as VideoPlan.Encode).settings
    private fun ConverterViewModel.audio() = (state.value.settings!!.audio as AudioPlan.Encode).settings

    // ---- picking and probing ----
    @Test fun starts_by_asking_for_an_input() { assertEquals(ConverterUiState(), vm().state.value) }

    @Test fun a_dismissed_picker_is_ignored() = runTest(dispatcher) {
        val vm = vm(); vm.onInputPicked(null); runCurrent()
        assertEquals(ConverterStage.SelectInput, vm.state.value.stage); assertTrue(resolver.resolved.isEmpty())
    }

    @Test fun shows_probing_while_the_file_is_analysed() = runTest(dispatcher) {
        resolver.gate = CompletableDeferred()
        val vm = vm(); vm.onInputPicked(uri); runCurrent()
        assertEquals(ConverterStage.Probing, vm.state.value.stage)
        resolver.gate!!.complete(Unit); runCurrent()
        assertEquals(ConverterStage.Configure, vm.state.value.stage)
    }

    @Test fun a_probed_video_opens_the_form_with_defaults() = runTest(dispatcher) {
        val s = opened().state.value
        assertEquals(ConverterStage.Configure, s.stage)
        assertEquals("holiday.mp4", s.source!!.displayName); assertEquals(7_340_032L, s.source!!.sizeBytes)
        assertEquals(12_345L, s.source!!.metadata.durationMs)
        assertEquals(ConverterForm(ConversionPreset.BALANCED, ContainerFormat.MP4, null, null, "holiday-converted"), s.form)
        assertEquals(capabilities.repo.getContainers(), s.containers)
        assertEquals(capabilities.repo.getVideoCodecs(ContainerFormat.MP4), s.videoCodecs)
        assertEquals(capabilities.repo.getAudioCodecs(ContainerFormat.MP4), s.audioCodecs)
        assertEquals(ContainerFormat.MP4, s.settings!!.container)
        assertTrue(resolver.released.isEmpty())
    }

    @Test fun an_input_problem_returns_to_selection_with_the_reason() = runTest(dispatcher) {
        resolver.result = ResolveResult.Failure(InputError.NotFound)
        val s = opened().state.value
        assertEquals(ConverterStage.SelectInput, s.stage); assertEquals(ConverterMessage.InputProblem(InputError.NotFound), s.message)
    }

    @Test fun a_tool_failure_is_reported_and_the_staged_input_released() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Failure(1)
        val s = opened().state.value
        assertEquals(ConverterMessage.ProbeFailed, s.message); assertEquals(listOf(staged), resolver.released)
    }

    @Test fun an_audio_only_file_is_rejected_and_released() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(json("mp3_with_cover.json"))
        val s = opened().state.value
        assertEquals(ConverterStage.SelectInput, s.stage); assertEquals(ConverterMessage.NoVideoStream, s.message)
        assertEquals(listOf(staged), resolver.released)
    }

    @Test fun no_available_encoders_is_reported_and_the_input_released() = runTest(dispatcher) {
        capabilities.repo = DefaultMediaCapabilityRepository(emptyList())
        val s = opened().state.value
        assertEquals(ConverterMessage.NoEncodersAvailable, s.message); assertEquals(listOf(staged), resolver.released)
    }

    // ---- editing the form ----
    @Test fun choosing_a_preset_recomputes_the_settings() = runTest(dispatcher) {
        val vm = opened(); vm.onPresetSelected(ConversionPreset.SMALL_FILE)
        assertEquals(ConversionPreset.SMALL_FILE, vm.state.value.form!!.preset)
        assertEquals(VideoQuality.Crf(28), vm.video().quality)
    }

    @Test fun choosing_a_container_recomputes_settings_and_the_codec_options() = runTest(dispatcher) {
        val vm = opened(); vm.onContainerSelected(ContainerFormat.WEBM)
        assertEquals(ContainerFormat.WEBM, vm.state.value.settings!!.container); assertEquals(VideoCodec.VP9, vm.video().codec)
        assertEquals(capabilities.repo.getVideoCodecs(ContainerFormat.WEBM), vm.state.value.videoCodecs)
    }

    @Test fun chosen_codecs_are_applied() = runTest(dispatcher) {
        val vm = opened(); vm.onVideoCodecSelected(VideoCodec.HEVC); vm.onAudioCodecSelected(AudioCodec.MP3)
        assertEquals(VideoCodec.HEVC, vm.video().codec); assertEquals(AudioCodec.MP3, vm.audio().codec)
    }

    @Test fun choosing_automatic_clears_a_codec_choice() = runTest(dispatcher) {
        val vm = opened(); vm.onVideoCodecSelected(VideoCodec.HEVC); vm.onVideoCodecSelected(null)
        assertNull(vm.state.value.form!!.videoCodec); assertEquals(VideoCodec.H264, vm.video().codec)
    }

    @Test fun a_codec_choice_that_no_longer_fits_the_container_is_dropped() = runTest(dispatcher) {
        val vm = opened(); vm.onVideoCodecSelected(VideoCodec.HEVC); vm.onContainerSelected(ContainerFormat.WEBM)
        assertNull(vm.state.value.form!!.videoCodec); assertEquals(VideoCodec.VP9, vm.video().codec)
    }

    @Test fun a_codec_the_container_cannot_hold_is_ignored() = runTest(dispatcher) {
        val vm = opened(); vm.onContainerSelected(ContainerFormat.WEBM); vm.onVideoCodecSelected(VideoCodec.H264)
        assertNull(vm.state.value.form!!.videoCodec)
    }

    @Test fun the_output_name_can_be_edited() = runTest(dispatcher) {
        val vm = opened(); vm.onOutputNameChanged("Trip 2024"); assertEquals("Trip 2024", vm.state.value.form!!.outputName)
    }

    // ---- starting ----
    @Test fun an_invalid_name_shows_issues_and_asks_for_no_destination() = runTest(dispatcher) {
        val events = mutableListOf<ConverterEvent>()
        val vm = opened(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.onOutputNameChanged("a/b"); vm.onStartRequested(); runCurrent()
        assertEquals(listOf<ConversionIssue>(ConversionIssue.InvalidOutputName), vm.state.value.issues); assertTrue(events.isEmpty())
    }

    @Test fun a_valid_configuration_asks_for_a_destination_with_name_and_mime_type() = runTest(dispatcher) {
        val events = mutableListOf<ConverterEvent>()
        val vm = opened(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.onOutputNameChanged("  clip  "); vm.onStartRequested(); runCurrent()
        assertEquals(listOf<ConverterEvent>(ConverterEvent.RequestDestination("clip.mp4", "video/mp4")), events)
        assertTrue(vm.state.value.issues.isEmpty())
    }

    @Test fun a_dismissed_destination_dialog_starts_nothing() = runTest(dispatcher) {
        val vm = opened(); vm.onStartRequested(); vm.onDestinationChosen(null); runCurrent()
        assertEquals(ConverterStage.Configure, vm.state.value.stage); assertTrue(engine.converted.isEmpty())
    }

    // ---- converting ----
    private fun TestScope.converting(vm: ConverterViewModel) { vm.onStartRequested(); vm.onDestinationChosen(destination); runCurrent() }

    @Test fun a_successful_conversion_is_published_and_cleaned_up() = runTest(dispatcher) {
        engine.onConvert = { it.outputFile.writeText("converted") }
        val vm = opened(); val settings = vm.state.value.settings
        converting(vm)
        val request = engine.converted.single()
        assertEquals(settings, request.settings); assertEquals(staged, request.input)
        assertEquals(staging.root, request.outputFile.parentFile); assertTrue(request.outputFile.name.endsWith(".mp4"))
        val published = publisher.published.single()
        assertEquals(destination, published.destination); assertEquals("converted", published.content)
        assertFalse(request.outputFile.exists())
        assertEquals(ConverterStage.Completed, vm.state.value.stage); assertEquals(listOf(staged), resolver.released)
    }

    @Test fun progress_is_visible_while_converting() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = opened(); converting(vm)
        assertEquals(ConverterStage.Converting, vm.state.value.stage)
        engine.emitProgress(engine.converted.single().jobId, MediaProcessingProgress(4000, 12_345, 0.4f, 1.0, 8000)); runCurrent()
        assertEquals(ProcessingState.Running(0.4f, "Converting"), vm.state.value.processing)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
    }

    @Test fun a_failed_conversion_returns_to_the_form_and_keeps_the_input() = runTest(dispatcher) {
        engine.processingResult = MediaProcessingResult.Failure("boom")
        val vm = opened(); converting(vm)
        val s = vm.state.value
        assertEquals(ConverterStage.Configure, s.stage); assertEquals(ConverterMessage.ConversionFailed, s.message)
        assertEquals(ProcessingState.Idle, s.processing)
        assertTrue(publisher.published.isEmpty()); assertTrue(resolver.released.isEmpty())
    }

    @Test fun after_a_failure_the_user_can_try_again() = runTest(dispatcher) {
        engine.processingResult = MediaProcessingResult.Failure("boom")
        val vm = opened(); converting(vm)
        engine.processingResult = MediaProcessingResult.Success(File("x")); engine.onConvert = { it.outputFile.writeText("ok") }
        converting(vm)
        assertEquals(ConverterStage.Completed, vm.state.value.stage); assertNull(vm.state.value.message)
    }

    @Test fun cancelling_stops_the_engine_and_returns_to_the_form_without_an_error() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = opened(); converting(vm)
        vm.onCancel(); runCurrent()
        assertEquals(listOf(engine.converted.single().jobId), engine.cancelledJobIds)
        val s = vm.state.value
        assertEquals(ConverterStage.Configure, s.stage); assertNull(s.message)
        assertTrue(resolver.released.isEmpty()); assertTrue(publisher.published.isEmpty())
    }

    @Test fun a_failed_save_is_reported_and_the_temp_file_removed() = runTest(dispatcher) {
        engine.onConvert = { it.outputFile.writeText("converted") }
        publisher.result = PublishResult.Failure
        val vm = opened(); converting(vm)
        assertEquals(ConverterStage.Configure, vm.state.value.stage); assertEquals(ConverterMessage.SaveFailed, vm.state.value.message)
        assertFalse(engine.converted.single().outputFile.exists()); assertTrue(resolver.released.isEmpty())
    }

    // ---- leaving ----
    @Test fun converting_another_video_starts_over() = runTest(dispatcher) {
        engine.onConvert = { it.outputFile.writeText("converted") }
        val vm = opened(); converting(vm); vm.onConvertAnother()
        assertEquals(ConverterUiState(), vm.state.value)
    }

    @Test fun clearing_the_view_model_releases_the_staged_input() = runTest(dispatcher) {
        val vm = opened()
        val store = ViewModelStore()
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })[ConverterViewModel::class.java]
        store.clear()
        assertEquals(listOf(staged), resolver.released)
    }

    // ---- storage preflight ----
    @Test fun a_conversion_that_cannot_fit_is_blocked_with_a_storage_issue() = runTest(dispatcher) {
        free.available = 1
        val events = mutableListOf<ConverterEvent>()
        val vm = opened(); backgroundScope.launch { vm.events.collect { events += it } }
        vm.onStartRequested(); runCurrent()
        assertTrue(vm.state.value.issues.any { it is ConversionIssue.InsufficientStorage }); assertTrue(events.isEmpty())
    }

    // ---- jobs outlive the screen ----
    @Test fun leaving_the_screen_mid_conversion_lets_the_job_finish_and_release_the_input() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred(); engine.onConvert = { it.outputFile.writeText("c") }
        val vm = opened(); converting(vm)
        val store = ViewModelStore()
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })[ConverterViewModel::class.java]
        store.clear()
        assertTrue(registry.isDetached(engine.converted.single().jobId)); assertTrue(resolver.released.isEmpty())
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
        assertEquals(listOf(staged), resolver.released); assertEquals(1, publisher.published.size)
    }

    @Test fun the_job_is_handed_to_the_scheduler_under_its_own_id() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = opened(); converting(vm)
        assertEquals(engine.converted.single().jobId, registry.job(engine.converted.single().jobId)!!.id)
        assertEquals("holiday.mp4", registry.job(engine.converted.single().jobId)!!.title)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
    }

    // ---- preview ----
    @Test fun the_staged_file_is_offered_for_preview() = runTest(dispatcher) {
        assertEquals(staged.file, opened().state.value.source!!.previewFile)
    }
}
