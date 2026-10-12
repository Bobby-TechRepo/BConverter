package io.bsoft.bconverter.feature.audioextractor

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.DefaultMediaCapabilityRepository
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ConversionIssue
import io.bsoft.bconverter.domain.model.InputError
import io.bsoft.bconverter.domain.model.ProcessingState
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.repository.PublishResult
import io.bsoft.bconverter.feature.converter.ConverterEvent
import io.bsoft.bconverter.feature.converter.ConverterMessage
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.fakes.FakeCapabilitySource
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.MediaProcessingProgress
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
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
class AudioExtractorViewModelTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dispatcher = StandardTestDispatcher()
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val engine = FakeMediaEngine()
    private val capabilities = FakeCapabilitySource()
    private val publisher = FakeOutputPublisher()
    private lateinit var staging: StagingDirectory
    private lateinit var staged: ResolvedInput
    private val uri: Uri = Uri.parse("content://x/1")
    private val destination: Uri = Uri.parse("content://dest/1")
    private fun json(name: String) = checkNotNull(javaClass.getResource("/ffprobe/$name")).readText()
    private val videoOnly = """{"streams":[{"index":0,"codec_type":"video","codec_name":"h264","width":640,"height":360}],"format":{"filename":"x.mp4","format_name":"mov,mp4,m4a,3gp,3g2,mj2","duration":"5.0"}}"""
    private val vorbisOnly = """{"streams":[{"index":0,"codec_type":"audio","codec_name":"vorbis","sample_rate":"44100","channels":2}],"format":{"filename":"x.mkv","format_name":"matroska,webm","duration":"5.0"}}"""

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        staging = StagingDirectory(File(tmp.root, "staging"))
        staged = ResolvedInput(tmp.newFile("in.mp4"), "holiday.mp4", 7_340_032, isStaged = true)
        resolver.result = ResolveResult.Success(staged)
        runner.result = FfprobeRunResult.Success(json("mp4_h264_aac.json"))
    }
    @After fun tearDown() { Dispatchers.resetMain() }

    private fun vm() = AudioExtractorViewModel(MediaProber(resolver, runner), capabilities, engine, resolver, staging, publisher)
    private fun TestScope.opened(): AudioExtractorViewModel = vm().also { it.onInputPicked(uri); runCurrent() }
    private fun TestScope.extracting(vm: AudioExtractorViewModel) { vm.onStartRequested(); vm.onDestinationChosen(destination); runCurrent() }

    // ---- picking and probing ----
    @Test fun starts_by_asking_for_an_input() { assertEquals(AudioExtractorUiState(), vm().state.value) }

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

    @Test fun a_probed_file_opens_the_form_with_copy_as_the_default() = runTest(dispatcher) {
        val s = opened().state.value
        assertEquals("holiday.mp4", s.source!!.displayName)
        assertEquals(ExtractorForm(0, AudioFormat.M4A, true, "holiday-audio"), s.form)
        assertEquals(AudioExtractionSettings(AudioFormat.M4A, 0, AudioExtractionMode.Copy), s.settings)
        assertEquals(AudioFormat.entries, s.formats); assertTrue(s.canCopy)
        assertTrue(resolver.released.isEmpty())
    }

    @Test fun an_input_problem_returns_to_selection_with_the_reason() = runTest(dispatcher) {
        resolver.result = ResolveResult.Failure(InputError.NotFound)
        val s = opened().state.value
        assertEquals(ConverterStage.SelectInput, s.stage); assertEquals(ConverterMessage.InputProblem(InputError.NotFound), s.message)
    }

    @Test fun a_tool_failure_is_reported_and_the_input_released() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Failure(1)
        assertEquals(ConverterMessage.ProbeFailed, opened().state.value.message); assertEquals(listOf(staged), resolver.released)
    }

    @Test fun a_file_without_audio_is_rejected_and_released() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(videoOnly)
        val s = opened().state.value
        assertEquals(ConverterMessage.NoAudioStream, s.message); assertEquals(ConverterStage.SelectInput, s.stage)
        assertEquals(listOf(staged), resolver.released)
    }

    @Test fun copy_still_works_when_the_build_has_no_encoders() = runTest(dispatcher) {
        capabilities.repo = DefaultMediaCapabilityRepository(emptyList())
        val s = opened().state.value
        assertEquals(ConverterStage.Configure, s.stage); assertEquals(listOf(AudioFormat.AAC, AudioFormat.M4A), s.formats)
        assertEquals(AudioExtractionMode.Copy, s.settings!!.mode)
    }

    @Test fun nothing_possible_is_reported_and_the_input_released() = runTest(dispatcher) {
        capabilities.repo = DefaultMediaCapabilityRepository(emptyList()); runner.result = FfprobeRunResult.Success(vorbisOnly)
        val s = opened().state.value
        assertEquals(ConverterMessage.NoEncodersAvailable, s.message); assertEquals(listOf(staged), resolver.released)
    }

    // ---- editing ----
    @Test fun choosing_another_format_re_encodes() = runTest(dispatcher) {
        val vm = opened(); vm.onFormatSelected(AudioFormat.MP3)
        assertEquals(AudioExtractionSettings(AudioFormat.MP3, 0, AudioExtractionMode.Encode(bitrateKbps = 192)), vm.state.value.settings)
        assertFalse(vm.state.value.canCopy)
    }

    @Test fun a_format_that_is_not_offered_is_ignored() = runTest(dispatcher) {
        capabilities.repo = DefaultMediaCapabilityRepository(emptyList())
        val vm = opened(); vm.onFormatSelected(AudioFormat.MP3)
        assertEquals(AudioFormat.M4A, vm.state.value.form!!.format)
    }

    @Test fun copy_can_be_switched_off_and_on() = runTest(dispatcher) {
        val vm = opened(); vm.onPreferCopyChanged(false)
        assertEquals(AudioExtractionMode.Encode(bitrateKbps = 192), vm.state.value.settings!!.mode)
        vm.onPreferCopyChanged(true)
        assertEquals(AudioExtractionMode.Copy, vm.state.value.settings!!.mode)
    }

    @Test fun choosing_another_audio_track_updates_the_settings() = runTest(dispatcher) {
        runner.result = FfprobeRunResult.Success(json("mkv_multi_audio_subs.json"))
        val vm = opened(); vm.onStreamSelected(1)
        val settings = vm.state.value.settings!!
        assertEquals(1, settings.streamOrdinal); assertTrue(settings.mode is AudioExtractionMode.Encode)
    }

    @Test fun a_track_that_does_not_exist_is_ignored() = runTest(dispatcher) {
        val vm = opened(); vm.onStreamSelected(5)
        assertEquals(0, vm.state.value.form!!.streamOrdinal)
    }

    @Test fun the_output_name_can_be_edited() = runTest(dispatcher) {
        val vm = opened(); vm.onOutputNameChanged("My song"); assertEquals("My song", vm.state.value.form!!.outputName)
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
        vm.onOutputNameChanged("  song "); vm.onStartRequested(); runCurrent()
        assertEquals(listOf<ConverterEvent>(ConverterEvent.RequestDestination("song.m4a", "audio/mp4")), events)
    }

    @Test fun a_dismissed_destination_dialog_starts_nothing() = runTest(dispatcher) {
        val vm = opened(); vm.onStartRequested(); vm.onDestinationChosen(null); runCurrent()
        assertEquals(ConverterStage.Configure, vm.state.value.stage); assertTrue(engine.extracted.isEmpty())
    }

    // ---- extracting ----
    @Test fun a_successful_extraction_is_published_and_cleaned_up() = runTest(dispatcher) {
        engine.onExtract = { it.outputFile.writeText("audio") }
        val vm = opened(); val settings = vm.state.value.settings
        extracting(vm)
        val request = engine.extracted.single()
        assertEquals(settings, request.settings); assertEquals(staged, request.input)
        assertEquals(staging.root, request.outputFile.parentFile); assertTrue(request.outputFile.name.endsWith(".m4a"))
        val published = publisher.published.single()
        assertEquals(destination, published.destination); assertEquals("audio", published.content)
        assertFalse(request.outputFile.exists())
        assertEquals(ConverterStage.Completed, vm.state.value.stage); assertEquals(listOf(staged), resolver.released)
    }

    @Test fun progress_is_visible_while_extracting() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = opened(); extracting(vm)
        assertEquals(ConverterStage.Extracting, vm.state.value.stage)
        engine.emitProgress(engine.extracted.single().jobId, MediaProcessingProgress(4000, 12_345, 0.4f, 1.0, 8000)); runCurrent()
        assertEquals(ProcessingState.Running(0.4f, "Converting"), vm.state.value.processing)
        engine.convertGate!!.complete(MediaProcessingResult.Success(File("x"))); runCurrent()
    }

    @Test fun a_failed_extraction_returns_to_the_form_and_can_be_retried() = runTest(dispatcher) {
        engine.processingResult = MediaProcessingResult.Failure("boom")
        val vm = opened(); extracting(vm)
        assertEquals(ConverterStage.Configure, vm.state.value.stage); assertEquals(ConverterMessage.ConversionFailed, vm.state.value.message)
        assertTrue(resolver.released.isEmpty())
        engine.processingResult = MediaProcessingResult.Success(File("x")); engine.onExtract = { it.outputFile.writeText("ok") }
        extracting(vm)
        assertEquals(ConverterStage.Completed, vm.state.value.stage); assertNull(vm.state.value.message)
    }

    @Test fun cancelling_returns_to_the_form_without_an_error() = runTest(dispatcher) {
        engine.convertGate = CompletableDeferred()
        val vm = opened(); extracting(vm)
        vm.onCancel(); runCurrent()
        assertEquals(listOf(engine.extracted.single().jobId), engine.cancelledJobIds)
        assertEquals(ConverterStage.Configure, vm.state.value.stage); assertNull(vm.state.value.message)
        assertTrue(resolver.released.isEmpty())
    }

    @Test fun a_failed_save_is_reported_and_the_temp_file_removed() = runTest(dispatcher) {
        engine.onExtract = { it.outputFile.writeText("audio") }; publisher.result = PublishResult.Failure
        val vm = opened(); extracting(vm)
        assertEquals(ConverterMessage.SaveFailed, vm.state.value.message); assertFalse(engine.extracted.single().outputFile.exists())
    }

    // ---- leaving ----
    @Test fun extracting_from_another_file_starts_over() = runTest(dispatcher) {
        engine.onExtract = { it.outputFile.writeText("audio") }
        val vm = opened(); extracting(vm); vm.onExtractAnother()
        assertEquals(AudioExtractorUiState(), vm.state.value)
    }

    @Test fun clearing_the_view_model_releases_the_staged_input() = runTest(dispatcher) {
        val vm = opened()
        val store = ViewModelStore()
        ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
        })[AudioExtractorViewModel::class.java]
        store.clear()
        assertEquals(listOf(staged), resolver.released)
    }
}
