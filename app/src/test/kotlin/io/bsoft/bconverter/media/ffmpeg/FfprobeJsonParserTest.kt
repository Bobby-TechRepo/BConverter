package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.ProbeFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FfprobeJsonParserTest {
    private val parser = FfprobeJsonParser()

    private fun fixture(name: String) =
        checkNotNull(javaClass.getResource("/ffprobe/$name")) { "missing fixture $name" }.readText()
    private fun parsed(json: String) = (parser.parse(json) as FfprobeParseResult.Parsed).metadata
    private fun rejected(json: String) = (parser.parse(json) as FfprobeParseResult.Rejected).failure
    private fun inline(
        formatName: String, filename: String = "clip", formatExtra: String = "", streams: String = VIDEO,
    ) = """{"streams":[$streams],"format":{"filename":"$filename","format_name":"$formatName"$formatExtra}}"""
    private fun videoWith(extra: String) =
        """{"index":0,"codec_type":"video","codec_name":"h264","width":1920,"height":1080$extra}"""

    private companion object {
        const val VIDEO = """{"index":0,"codec_name":"h264","codec_type":"video","width":640,"height":360,"avg_frame_rate":"25/1"}"""
    }

    // ---- realistic MP4 ----
    @Test fun mp4_container_duration_size_bitrate_and_stream_count() {
        val m = parsed(fixture("mp4_h264_aac.json"))
        assertEquals(ContainerFormat.MP4, m.container)
        assertEquals(12345L, m.durationMs)
        assertEquals(7340032L, m.sizeBytes)
        assertEquals(4756000L, m.bitrateBps)
        assertEquals(2, m.streamCount)
    }

    @Test fun video_stream_fields() {
        val v = parsed(fixture("mp4_h264_aac.json")).videoStreams.single()
        assertEquals(0, v.index); assertEquals("h264", v.codec)
        assertEquals(1920, v.width); assertEquals(1080, v.height)
        assertEquals(30.0, v.frameRate!!, 0.001)
        assertEquals(4800000L, v.bitrateBps); assertEquals("yuv420p", v.pixelFormat)
        assertEquals(0, v.rotationDegrees)
    }

    @Test fun audio_stream_fields_and_undefined_language_is_null() {
        val a = parsed(fixture("mp4_h264_aac.json")).audioStreams.single()
        assertEquals(1, a.index); assertEquals("aac", a.codec)
        assertEquals(44100, a.sampleRateHz); assertEquals(2, a.channels)
        assertEquals(128000L, a.bitrateBps); assertNull(a.language)
    }

    // ---- audio-only with cover art ----
    @Test fun embedded_cover_art_is_not_a_video_stream() {
        val m = parsed(fixture("mp3_with_cover.json"))
        assertTrue(m.videoStreams.isEmpty()); assertTrue(m.isAudioOnly)
        assertEquals("mp3", m.audioStreams.single().codec)
    }

    @Test fun mp3_has_no_matching_container_enum() = assertNull(parsed(fixture("mp3_with_cover.json")).container)

    // ---- multi-stream MKV ----
    @Test fun mkv_with_multiple_audio_and_subtitle_streams() {
        val m = parsed(fixture("mkv_multi_audio_subs.json"))
        assertEquals(ContainerFormat.MKV, m.container)
        assertEquals(listOf("eng", "jpn"), m.audioStreams.map { it.language })
        assertEquals(listOf("aac", "ac3"), m.audioStreams.map { it.codec })
        assertEquals(6, m.audioStreams.first().channels)
        assertEquals(listOf("eng", null), m.subtitleStreams.map { it.language })
        assertEquals(23.976, m.videoStreams.single().frameRate!!, 0.001)
    }

    // ---- rotation ----
    @Test fun display_matrix_side_data_gives_clockwise_rotation() {
        val v = parsed(fixture("rotated_side_data.json")).videoStreams.single()
        assertEquals(90, v.rotationDegrees); assertEquals(1080, v.displayWidth)
    }

    @Test fun positive_side_data_rotation_is_counter_clockwise() {
        val m = parsed(inline("mp4", streams = videoWith(""","side_data_list":[{"rotation":90}]""")))
        assertEquals(270, m.videoStreams.single().rotationDegrees)
    }

    @Test fun side_data_180_is_normalised() {
        val m = parsed(inline("mp4", streams = videoWith(""","side_data_list":[{"rotation":-180}]""")))
        assertEquals(180, m.videoStreams.single().rotationDegrees)
    }

    @Test fun legacy_rotate_tag_is_clockwise_degrees() {
        val m = parsed(inline("mp4", streams = videoWith(""","tags":{"rotate":"270"}""")))
        assertEquals(270, m.videoStreams.single().rotationDegrees)
    }

    // ---- container detection ----
    @Test fun mp4_brand_without_tags_defaults_to_mp4() = assertEquals(ContainerFormat.MP4, parsed(inline("mov,mp4,m4a,3gp,3g2,mj2")).container)
    @Test fun quicktime_brand_is_mov() = assertEquals(
        ContainerFormat.MOV, parsed(inline("mov,mp4,m4a,3gp,3g2,mj2", formatExtra = ""","tags":{"major_brand":"qt  "}""")).container)
    @Test fun threegp_brand_is_3gp() = assertEquals(
        ContainerFormat.THREE_GP, parsed(inline("mov,mp4,m4a,3gp,3g2,mj2", formatExtra = ""","tags":{"major_brand":"3gp4"}""")).container)
    @Test fun matroska_with_webm_extension_is_webm() = assertEquals(ContainerFormat.WEBM, parsed(inline("matroska,webm", "/x/clip.webm")).container)
    @Test fun matroska_with_mkv_extension_is_mkv() = assertEquals(ContainerFormat.MKV, parsed(inline("matroska,webm", "/x/clip.mkv")).container)
    @Test fun matroska_without_hint_defaults_to_mkv() = assertEquals(ContainerFormat.MKV, parsed(inline("matroska,webm")).container)
    @Test fun avi_is_avi() = assertEquals(ContainerFormat.AVI, parsed(inline("avi")).container)
    @Test fun mpegts_is_mpeg_ts() = assertEquals(ContainerFormat.MPEG_TS, parsed(inline("mpegts")).container)
    @Test fun unknown_format_has_null_container() = assertNull(parsed(inline("flv")).container)

    // ---- frame rate ----
    @Test fun ntsc_fraction_is_converted() =
        assertEquals(29.97, parsed(inline("mp4", streams = videoWith(",\"avg_frame_rate\":\"30000/1001\"")))
            .videoStreams.single().frameRate!!, 0.01)

    @Test fun zero_average_falls_back_to_r_frame_rate() =
        assertEquals(25.0, parsed(inline("mp4", streams = videoWith(",\"avg_frame_rate\":\"0/0\",\"r_frame_rate\":\"25/1\"")))
            .videoStreams.single().frameRate!!, 0.001)

    @Test fun no_valid_frame_rate_is_null() =
        assertNull(parsed(inline("mp4", streams = videoWith(",\"avg_frame_rate\":\"0/0\",\"r_frame_rate\":\"0/0\"")))
            .videoStreams.single().frameRate)

    // ---- duration ----
    @Test fun missing_format_duration_falls_back_to_longest_stream() {
        val streams = """{"index":0,"codec_type":"video","codec_name":"h264","width":1,"height":1,"duration":"3.5"},""" +
            """{"index":1,"codec_type":"audio","codec_name":"aac","duration":"7.25"}"""
        assertEquals(7250L, parsed(inline("matroska,webm", streams = streams)).durationMs)
    }

    @Test fun unknown_duration_everywhere_is_zero() = assertEquals(0L, parsed(inline("mp4")).durationMs)

    // ---- tolerance ----
    @Test fun not_available_values_become_null() {
        val m = parsed(inline("mp4", formatExtra = ""","duration":"N/A","bit_rate":"N/A","size":"N/A"""",
            streams = videoWith(",\"bit_rate\":\"N/A\"")))
        assertNull(m.bitrateBps); assertNull(m.sizeBytes); assertNull(m.videoStreams.single().bitrateBps)
        assertEquals(0L, m.durationMs)
    }

    @Test fun stream_without_codec_name_has_null_codec() {
        val m = parsed(inline("mp4", streams = """{"index":0,"codec_type":"audio","sample_rate":"44100","channels":2}"""))
        assertNull(m.audioStreams.single().codec)
    }

    // ---- invalid output ----
    @Test fun non_json_is_invalid_output() = assertEquals(ProbeFailure.InvalidOutput, rejected("not json"))
    @Test fun blank_is_invalid_output() = assertEquals(ProbeFailure.InvalidOutput, rejected(""))
    @Test fun json_without_format_is_invalid_output() = assertEquals(ProbeFailure.InvalidOutput, rejected("{}"))
    @Test fun no_media_streams_is_reported() =
        assertEquals(ProbeFailure.NoMediaStreams, rejected("""{"streams":[],"format":{"format_name":"mp4"}}"""))

    @Test fun subtitle_streams_keep_title_default_forced_and_kind() {
        val json = """{"format":{"duration":"10"},"streams":[
            {"index":0,"codec_type":"video","codec_name":"h264","width":640,"height":360},
            {"index":2,"codec_type":"subtitle","codec_name":"subrip","tags":{"language":"eng","title":"English SDH"},"disposition":{"default":1,"forced":0}},
            {"index":3,"codec_type":"subtitle","codec_name":"hdmv_pgs_subtitle","tags":{"language":"fra"},"disposition":{"default":0,"forced":1}},
            {"index":4,"codec_type":"subtitle","codec_name":"ass","tags":{"language":"und"}}]}"""
        val subs = parsed(json).subtitleStreams
        assertEquals(listOf(2, 3, 4), subs.map { it.index }); assertEquals(listOf("eng", "fra", null), subs.map { it.language })
        assertEquals(listOf("English SDH", null, null), subs.map { it.title })
        assertEquals(listOf(true, false, false), subs.map { it.isDefault }); assertEquals(listOf(false, true, false), subs.map { it.isForced })
        assertEquals(listOf(io.bsoft.bconverter.domain.model.SubtitleKind.TEXT, io.bsoft.bconverter.domain.model.SubtitleKind.IMAGE, io.bsoft.bconverter.domain.model.SubtitleKind.TEXT), subs.map { it.kind })
    }
}
