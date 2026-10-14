package io.bsoft.bconverter.media.player

import androidx.test.platform.app.InstrumentationRegistry
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

/** Real ExoPlayer on a device. Player calls must happen on the main thread. */
class Media3PlayerEngineTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dir = File(context.cacheDir, "player").apply { mkdirs() }
    private val sample = File(dir, "sample.mp4")
    private lateinit var controller: PlaybackController

    /** A stalled test must fail, not hang the whole device run. */
    @get:Rule val timeout: Timeout = Timeout(90, TimeUnit.SECONDS)

    @Before fun setUp() {
        sample.delete()
        val session = FFmpegKit.executeWithArguments(arrayOf(
            "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=320x240:rate=15", "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "mpeg4", "-c:a", "aac", "-shortest", sample.absolutePath,
        ))
        assertTrue("fixture generation failed: ${session.output}", ReturnCode.isSuccess(session.returnCode))
        instrumentation.runOnMainSync { controller = PlaybackController(Media3PlayerEngine(context)) }
    }

    /** Always release the player, even when a test fails or times out. */
    @After fun tearDown() { onMain { controller.release() } }

    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private val state get() = controller.state.value
    private fun waitFor(what: String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out waiting for $what; state=$state" }
            Thread.sleep(50)
        }
    }

    @Test fun loading_a_file_reports_its_duration() {
        onMain { controller.load(sample) }
        waitFor("ready") { state.status == PlaybackStatus.Ready }
        assertTrue("duration ${state.durationMs}", state.durationMs in 1500..2600)
    }

    @Test fun playing_advances_the_position_and_pausing_stops_it() {
        onMain { controller.load(sample) }
        waitFor("ready") { state.status == PlaybackStatus.Ready }
        onMain { controller.play() }
        waitFor("playing") { state.isPlaying }
        waitFor("position to advance") { state.positionMs > 200 }
        onMain { controller.pause() }
        waitFor("paused") { !state.isPlaying }
    }

    @Test fun seeking_updates_the_position() {
        onMain { controller.load(sample) }
        waitFor("ready") { state.status == PlaybackStatus.Ready }
        onMain { controller.seekTo(1000) }
        assertEquals(1000L, state.positionMs)
    }

    @Test fun playback_ends_and_can_be_replayed() {
        onMain { controller.load(sample) }
        waitFor("ready") { state.status == PlaybackStatus.Ready }
        onMain { controller.play() }
        waitFor("end") { state.status == PlaybackStatus.Ended }
        onMain { controller.play() }
        waitFor("replay") { state.isPlaying }
    }

    @Test fun a_missing_file_is_reported_as_an_error() {
        onMain { controller.load(File(dir, "does-not-exist.mp4")) }
        waitFor("error") { state.status == PlaybackStatus.Error }
        assertEquals(PlaybackError.SourceNotFound, state.error)
    }
}
