package io.bsoft.bconverter.domain.render

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilterGraphBuilderTest {
    private val canvas = Canvas(1280, 720, 30.0)
    private fun input(name: String, seek: Long = 0, dur: Long = 10_000) = RenderInput(File("/x/$name"), seek, dur)
    private fun plan(
        inputs: List<RenderInput>, video: List<VideoSegment>, original: List<OriginalAudioSegment>? = null,
        overlays: List<OverlayAudio> = emptyList(), format: AudioFormatSpec? = AudioFormatSpec(48_000, 2), c: Canvas = canvas,
    ) = RenderPlan(c, inputs, video, original, overlays, format.takeIf { original != null || overlays.isNotEmpty() }, 10_000)
    private fun graph(p: RenderPlan) = FilterGraphBuilder.build(p)
    private fun FilterGraph.chain(output: String) = chains.single { output in it.outputLabels }

    private val clip = plan(listOf(input("a")), listOf(VideoSegment(0, 1f)))

    // ---- video ----
    @Test fun a_clip_is_scaled_padded_retimed_and_converted_to_a_common_format() {
        val c = graph(clip).chain("v0")
        assertEquals(listOf("0:v"), c.inputLabels)
        assertEquals(
            listOf("scale=1280:720:force_original_aspect_ratio=decrease", "pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=black", "setsar=1",
                "setpts=PTS-STARTPTS", "fps=30", "format=yuv420p"),
            c.filters,
        )
    }

    @Test fun video_clips_are_joined_by_concat_and_a_single_clip_passes_through() {
        assertEquals(FilterChain(listOf("v0"), listOf("null"), listOf("vout")), graph(clip).chain("vout"))
        val two = graph(plan(listOf(input("a"), input("b")), listOf(VideoSegment(0, 1f), VideoSegment(1, 1f))))
        assertEquals(FilterChain(listOf("v0", "v1"), listOf("concat=n=2:v=1:a=0"), listOf("vout")), two.chain("vout"))
        assertEquals("vout", two.videoOutput)
    }

    @Test fun speed_divides_the_timestamps() {
        val g = graph(plan(listOf(input("a"), input("b"), input("c")), listOf(VideoSegment(0, 2f), VideoSegment(1, 0.5f), VideoSegment(2, 1.5f))))
        assertEquals("setpts=(PTS-STARTPTS)/2", g.chain("v0").filters[3])
        assertEquals("setpts=(PTS-STARTPTS)/0.5", g.chain("v1").filters[3])
        assertEquals("setpts=(PTS-STARTPTS)/1.5", g.chain("v2").filters[3])
    }

    @Test fun fractional_frame_rates_are_kept() =
        assertEquals("fps=29.97", graph(plan(listOf(input("a")), listOf(VideoSegment(0, 1f)), c = Canvas(1280, 720, 29.97))).chain("v0").filters[4])

    @Test fun the_whole_graph_renders_as_one_filter_complex_string() = assertEquals(
        "[0:v]scale=1280:720:force_original_aspect_ratio=decrease,pad=1280:720:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1," +
            "setpts=PTS-STARTPTS,fps=30,format=yuv420p[v0];[v0]null[vout]",
        graph(clip).render(),
    )

    @Test fun no_audio_means_no_audio_output_and_no_audio_chains() {
        val g = graph(clip); assertNull(g.audioOutput); assertEquals(2, g.chains.size)
    }

    // ---- original audio ----
    private val withOriginal = plan(
        listOf(input("a"), input("b")), listOf(VideoSegment(0, 1f), VideoSegment(1, 2f)),
        original = listOf(OriginalAudioSegment(0, 1f, 10_000), OriginalAudioSegment(null, 2f, 5_000)),
    )

    @Test fun a_clip_with_audio_is_resampled_retimed_and_fitted_to_its_exact_length() = assertEquals(
        FilterChain(
            listOf("0:a"),
            listOf("aformat=sample_rates=48000:channel_layouts=stereo", "asetpts=PTS-STARTPTS", "apad=whole_dur=10", "atrim=duration=10"),
            listOf("a0"),
        ),
        graph(withOriginal).chain("a0"),
    )

    @Test fun a_clip_without_audio_becomes_generated_silence() = assertEquals(
        FilterChain(emptyList(), listOf("anullsrc=r=48000:cl=stereo", "atrim=duration=5"), listOf("a1")), graph(withOriginal).chain("a1"),
    )

    @Test fun original_audio_segments_are_concatenated_and_passed_to_the_output() {
        val g = graph(withOriginal)
        assertEquals(FilterChain(listOf("a0", "a1"), listOf("concat=n=2:v=0:a=1"), listOf("aorig")), g.chain("aorig"))
        assertEquals(FilterChain(listOf("aorig"), listOf("anull"), listOf("aout")), g.chain("aout")); assertEquals("aout", g.audioOutput)
    }

    @Test fun a_single_original_segment_needs_no_concat() {
        val g = graph(plan(listOf(input("a")), listOf(VideoSegment(0, 1f)), original = listOf(OriginalAudioSegment(0, 1f, 10_000))))
        assertEquals(FilterChain(listOf("a0"), listOf("anull"), listOf("aorig")), g.chain("aorig"))
    }

    @Test fun sped_up_audio_gets_tempo_filters_before_it_is_fitted() {
        val g = graph(plan(listOf(input("a")), listOf(VideoSegment(0, 2f)), original = listOf(OriginalAudioSegment(0, 2f, 5_000))))
        assertEquals(
            listOf("aformat=sample_rates=48000:channel_layouts=stereo", "asetpts=PTS-STARTPTS", "atempo=2", "apad=whole_dur=5", "atrim=duration=5"),
            g.chain("a0").filters,
        )
        val slow = graph(plan(listOf(input("a")), listOf(VideoSegment(0, 0.25f)), original = listOf(OriginalAudioSegment(0, 0.25f, 40_000))))
        assertEquals(listOf("atempo=0.5", "atempo=0.5"), slow.chain("a0").filters.filter { it.startsWith("atempo") })
    }

    // ---- overlays and mixing ----
    private fun overlayPlan(vararg overlays: OverlayAudio, original: Boolean = false) = plan(
        listOf(input("a"), input("m"), input("n")), listOf(VideoSegment(0, 1f)),
        original = if (original) listOf(OriginalAudioSegment(0, 1f, 10_000)) else null, overlays = overlays.toList(),
    )

    @Test fun an_overlay_is_volume_adjusted_and_delayed() = assertEquals(
        FilterChain(
            listOf("1:a"),
            listOf("aformat=sample_rates=48000:channel_layouts=stereo", "asetpts=PTS-STARTPTS", "volume=0.5", "adelay=2000|2000"),
            listOf("o0"),
        ),
        graph(overlayPlan(OverlayAudio(1, 0.5f, 2_000))).chain("o0"),
    )

    @Test fun a_neutral_overlay_needs_no_volume_or_delay_filters() =
        assertEquals(2, graph(overlayPlan(OverlayAudio(1, 1f, 0))).chain("o0").filters.size)

    @Test fun original_audio_and_overlays_are_mixed_without_rescaling() {
        val g = graph(overlayPlan(OverlayAudio(1, 1f, 0), OverlayAudio(2, 1f, 0), original = true))
        assertEquals(
            FilterChain(listOf("aorig", "o0", "o1"), listOf("amix=inputs=3:duration=longest:normalize=0"), listOf("aout")), g.chain("aout"),
        )
    }

    @Test fun a_lone_overlay_passes_straight_to_the_output() =
        assertEquals(FilterChain(listOf("o0"), listOf("anull"), listOf("aout")), graph(overlayPlan(OverlayAudio(1, 1f, 0))).chain("aout"))

    @Test fun mono_projects_use_a_mono_layout_and_a_single_delay_value() {
        val c = graph(plan(listOf(input("a"), input("m")), listOf(VideoSegment(0, 1f)), overlays = listOf(OverlayAudio(1, 1f, 500)), format = AudioFormatSpec(44_100, 1))).chain("o0")
        assertEquals(listOf("aformat=sample_rates=44100:channel_layouts=mono", "asetpts=PTS-STARTPTS", "adelay=500"), c.filters)
    }

    // ---- tempo ----
    @Test fun tempo_chains_stay_within_the_range_each_atempo_supports() {
        mapOf(
            1f to emptyList(), 2f to listOf("atempo=2"), 4f to listOf("atempo=2", "atempo=2"), 0.5f to listOf("atempo=0.5"),
            0.25f to listOf("atempo=0.5", "atempo=0.5"), 1.5f to listOf("atempo=1.5"), 3f to listOf("atempo=2", "atempo=1.5"),
            0.3f to listOf("atempo=0.5", "atempo=0.6"),
        ).forEach { (speed, expected) -> assertEquals("x$speed", expected, AudioTempo.filters(speed)) }
    }
}
