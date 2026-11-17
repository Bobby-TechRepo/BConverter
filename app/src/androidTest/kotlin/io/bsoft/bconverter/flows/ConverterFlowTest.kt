package io.bsoft.bconverter.flows

import android.net.Uri
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import io.bsoft.bconverter.R
import io.bsoft.bconverter.core.storage.StagingDirectory
import io.bsoft.bconverter.data.media.MediaProber
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ConversionPreset
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoPlan
import io.bsoft.bconverter.domain.model.ResolveResult
import io.bsoft.bconverter.domain.usecase.ExportJobExecutor
import io.bsoft.bconverter.domain.usecase.ExportJobLauncher
import io.bsoft.bconverter.domain.usecase.ExportRegistry
import io.bsoft.bconverter.domain.usecase.StorageGuard
import io.bsoft.bconverter.fakes.DirectExportScheduler
import io.bsoft.bconverter.fakes.FakeCapabilitySource
import io.bsoft.bconverter.fakes.FakeFfprobeRunner
import io.bsoft.bconverter.fakes.FakeFreeSpaceProvider
import io.bsoft.bconverter.fakes.FakeInputResolver
import io.bsoft.bconverter.fakes.FakeMediaEngine
import io.bsoft.bconverter.fakes.FakeOutputPublisher
import io.bsoft.bconverter.feature.converter.ConverterActions
import io.bsoft.bconverter.feature.converter.ConverterContent
import io.bsoft.bconverter.feature.converter.ConverterEvent
import io.bsoft.bconverter.feature.converter.ConverterStage
import io.bsoft.bconverter.feature.converter.ConverterViewModel
import io.bsoft.bconverter.feature.converter.displayName
import io.bsoft.bconverter.media.api.MediaProcessingResult
import io.bsoft.bconverter.media.ffmpeg.FfprobeRunResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Spec §42, converter flow: Convert Video → select media → choose container → choose codec → configure encoding → start →
 * processing displayed → completion displayed. The real ViewModel and screen run against fake media tools, so only the
 * user-visible journey is checked here; the logic has its own unit tests.
 */
class ConverterFlowTest {
    @get:Rule val rule = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun s(id: Int, vararg a: Any) = context.getString(id, *a)

    private val dir = File(context.cacheDir, "flow-converter").apply { deleteRecursively(); mkdirs() }
    private val resolver = FakeInputResolver()
    private val runner = FakeFfprobeRunner()
    private val engine = FakeMediaEngine()
    private val publisher = FakeOutputPublisher()
    private val registry = ExportRegistry()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val staging = StagingDirectory(File(dir, "staging"))
    private val picked: Uri = Uri.parse("content://flow/video")
    private val destination: Uri = Uri.parse("content://flow/out")
    private lateinit var vm: ConverterViewModel

    @Before fun setUp() {
        val input = ResolvedInput(File(dir, "holiday.mp4").apply { writeText("video") }, "holiday.mp4", 7_340_032, isStaged = true)
        resolver.result = ResolveResult.Success(input)
        runner.result = FfprobeRunResult.Success(checkNotNull(javaClass.getResource("/ffprobe/mp4_h264_aac.json")).readText())
        engine.onConvert = { it.outputFile.writeText("converted") }
        vm = ConverterViewModel(
            MediaProber(resolver, runner), FakeCapabilitySource(), resolver, staging, StorageGuard(FakeFreeSpaceProvider(), staging),
            ExportJobLauncher(registry, DirectExportScheduler(scope, ExportJobExecutor(engine, resolver, publisher, registry))),
        )
    }

    @After fun tearDown() { scope.cancel(); dir.deleteRecursively() }

    private fun pick(label: Int, option: String) {
        rule.onNodeWithText(s(label)).performScrollTo().performClick()
        rule.onNode(hasText(option) and hasAnyAncestor(isPopup())).performClick()
    }

    @Test fun a_video_is_converted_from_picking_it_to_the_finished_screen() {
        rule.setContent {
            val state by vm.state.collectAsState()
            // The system "save as" dialog is answered immediately, as a user who picks a location would.
            LaunchedEffect(vm) { vm.events.collect { if (it is ConverterEvent.RequestDestination) vm.onDestinationChosen(destination) } }
            ConverterContent(
                state,
                ConverterActions(
                    onSelectInput = { vm.onInputPicked(picked) },
                    onPreset = vm::onPresetSelected, onContainer = vm::onContainerSelected,
                    onVideoCodec = vm::onVideoCodecSelected, onAudioCodec = vm::onAudioCodecSelected,
                    onAdvanced = vm::onAdvancedChanged, onOutputName = vm::onOutputNameChanged,
                    onStart = vm::onStartRequested, onCancel = vm::onCancel, onConvertAnother = vm::onConvertAnother,
                ),
            )
        }

        // Select media.
        rule.onNodeWithText(s(R.string.select_video)).assertIsDisplayed().performClick()
        rule.waitUntil(5_000) { vm.state.value.form != null }
        rule.onNodeWithText("holiday.mp4").assertIsDisplayed()

        // Choose container and codec.
        pick(R.string.converter_container, ContainerFormat.MKV.displayName)
        pick(R.string.converter_video_codec, VideoCodec.HEVC.displayName)
        assertEquals(ContainerFormat.MKV, vm.state.value.settings!!.container)
        assertEquals(VideoCodec.HEVC, (vm.state.value.settings!!.video as VideoPlan.Encode).settings.codec)

        // Configure encoding.
        rule.onNodeWithText(s(R.string.preset_small_file)).performScrollTo().performClick()
        assertEquals(ConversionPreset.SMALL_FILE, vm.state.value.form!!.preset)

        // Start; hold the engine so the processing screen can be seen.
        engine.convertGate = CompletableDeferred()
        rule.onNodeWithText(s(R.string.converter_convert)).performScrollTo().performClick()
        rule.waitUntil(5_000) { engine.converted.isNotEmpty() }
        rule.onNodeWithText(s(R.string.converter_converting, 0)).assertIsDisplayed()
        rule.onNodeWithText(s(R.string.converter_cancel)).assertIsDisplayed()

        // The engine finishes: completion is displayed and the file was saved to the chosen place.
        engine.convertGate!!.complete(MediaProcessingResult.Success(File(dir, "unused")))
        rule.waitUntil(5_000) { publisher.published.isNotEmpty() }
        // Publishing finishes a moment before the screen switches to Completed, so wait for the screen itself.
        rule.waitUntil(5_000) { rule.onAllNodesWithText(s(R.string.converter_saved)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(s(R.string.converter_saved)).assertIsDisplayed()
        assertEquals(destination, publisher.published.single().destination)
        assertTrue(engine.converted.single().outputFile.name.endsWith(".mkv"))
        rule.onNodeWithText(s(R.string.converter_convert_another)).assertIsDisplayed()
    }

    @Test fun cancelling_a_running_conversion_returns_to_the_form() {
        rule.setContent {
            val state by vm.state.collectAsState()
            LaunchedEffect(vm) { vm.events.collect { if (it is ConverterEvent.RequestDestination) vm.onDestinationChosen(destination) } }
            ConverterContent(
                state,
                ConverterActions(
                    onSelectInput = { vm.onInputPicked(picked) }, onStart = vm::onStartRequested, onCancel = vm::onCancel,
                    onOutputName = vm::onOutputNameChanged,
                ),
            )
        }
        engine.convertGate = CompletableDeferred()
        rule.onNodeWithText(s(R.string.select_video)).performClick()
        rule.waitUntil(5_000) { vm.state.value.form != null }
        rule.onNodeWithText(s(R.string.converter_convert)).performScrollTo().performClick()
        rule.waitUntil(5_000) { engine.converted.isNotEmpty() }
        rule.onNodeWithText(s(R.string.converter_cancel)).performClick()
        rule.waitUntil(5_000) { vm.state.value.stage == ConverterStage.Configure }
        rule.onNodeWithText(s(R.string.converter_convert)).performScrollTo().assertIsDisplayed()
        assertTrue(publisher.published.isEmpty())
    }
}
