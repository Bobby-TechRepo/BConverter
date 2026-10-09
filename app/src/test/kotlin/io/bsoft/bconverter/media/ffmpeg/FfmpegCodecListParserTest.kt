package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegCodecListParserTest {
    private val legend = """
Encoders:
 V..... = Video
 A..... = Audio
 S..... = Subtitle
 .F.... = Frame-level multithreading
 ..S... = Slice-level multithreading
 ...X.. = Codec is experimental
 ....B. = Supports draw_horiz_band
 .....D = Supports direct rendering method 1
 ------
"""
    private fun listing(vararg lines: String) = legend + lines.joinToString("\n") { " $it" } + "\n"
    private fun List<CodecAvailability>.of(c: Codec) = singleOrNull { it.codec == c }

    // ---- parser ----
    @Test fun parses_names_and_kinds_after_the_separator() {
        val entries = FfmpegCodecListParser.parse(listing(
            "V....D libx264              libx264 H.264 / AVC / MPEG-4 AVC / MPEG-4 part 10 (codec h264)",
            "A....D aac                  AAC (Advanced Audio Coding)",
            "S..... srt                  SubRip subtitle",
        ))
        assertEquals(listOf("libx264", "aac", "srt"), entries.map { it.name })
        assertEquals(listOf(FfmpegCodecKind.VIDEO, FfmpegCodecKind.AUDIO, FfmpegCodecKind.SUBTITLE), entries.map { it.kind })
    }

    @Test fun legend_lines_are_not_mistaken_for_codecs() = assertTrue(FfmpegCodecListParser.parse(listing()).isEmpty())

    @Test fun experimental_flag_is_detected() {
        val entries = FfmpegCodecListParser.parse(listing("A..X.D opus                 Opus", "A....D flac  FLAC"))
        assertEquals(listOf(true, false), entries.map { it.experimental })
    }

    @Test fun blank_or_garbage_output_yields_no_entries() {
        assertTrue(FfmpegCodecListParser.parse("").isEmpty())
        assertTrue(FfmpegCodecListParser.parse("ffmpeg: command not found").isEmpty())
    }

    // ---- availability ----
    @Test fun encoder_listing_makes_codecs_encodable_but_not_decodable() {
        val a = FfmpegCapabilities.fromListings(listing("V....D libx265 libx265 H.265"), listing()).of(VideoCodec.HEVC)!!
        assertTrue(a.canEncode); assertFalse(a.canDecode)
    }

    @Test fun decoder_listing_makes_codecs_decodable_but_not_encodable() {
        val a = FfmpegCapabilities.fromListings(listing(), listing("V....D libdav1d dav1d AV1")).of(VideoCodec.AV1)!!
        assertTrue(a.canDecode); assertFalse(a.canEncode)
    }

    @Test fun experimental_encoders_are_not_offered() {
        val list = FfmpegCapabilities.fromListings(listing("A..X.D opus Opus"), listing("A....D opus Opus"))
        assertFalse(list.of(AudioCodec.OPUS)!!.canEncode); assertTrue(list.of(AudioCodec.OPUS)!!.canDecode)
    }

    @Test fun a_stable_encoder_wins_over_an_experimental_one() {
        val list = FfmpegCapabilities.fromListings(listing("A..X.D opus Opus", "A....D libopus libopus Opus"), listing())
        assertTrue(list.of(AudioCodec.OPUS)!!.canEncode)
    }

    @Test fun every_pcm_variant_maps_to_pcm() {
        val list = FfmpegCapabilities.fromListings(listing("A....D pcm_s16le PCM signed 16-bit"), listing())
        assertTrue(list.of(AudioCodec.PCM)!!.canEncode)
    }

    @Test fun mediacodec_wrappers_count_as_encoders() {
        val list = FfmpegCapabilities.fromListings(listing("V....D h264_mediacodec H.264 Android MediaCodec"), listing())
        assertTrue(list.of(VideoCodec.H264)!!.canEncode)
    }

    @Test fun unknown_and_subtitle_entries_are_ignored() {
        val list = FfmpegCapabilities.fromListings(listing("V....D prores_ks ProRes", "S..... srt SubRip"), listing())
        assertTrue(list.isEmpty())
    }

    @Test fun common_encoder_names_map_to_their_codecs() {
        val names = mapOf(
            "libvpx" to VideoCodec.VP8, "libvpx-vp9" to VideoCodec.VP9, "libaom-av1" to VideoCodec.AV1, "libsvtav1" to VideoCodec.AV1,
            "mpeg4" to VideoCodec.MPEG4, "mpeg2video" to VideoCodec.MPEG2, "libx264" to VideoCodec.H264,
            "aac" to AudioCodec.AAC, "libmp3lame" to AudioCodec.MP3, "libvorbis" to AudioCodec.VORBIS,
            "flac" to AudioCodec.FLAC, "ac3" to AudioCodec.AC3,
        )
        names.forEach { (name, codec) ->
            val kind = if (codec is VideoCodec) "V" else "A"
            val list = FfmpegCapabilities.fromListings(listing("$kind....D $name description"), listing())
            assertTrue("$name -> $codec", list.of(codec)?.canEncode == true)
        }
    }
}
