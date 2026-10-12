package io.bsoft.bconverter.media.ffmpeg

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioExtractionMode
import io.bsoft.bconverter.domain.model.AudioExtractionSettings
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ResolvedInput
import io.bsoft.bconverter.fakes.TestMedia
import io.bsoft.bconverter.media.api.AudioExtractionRequest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioExtractionCommandTest {
    private val inFile = File("/data/cache/staging/my clip.mp4")
    private val outFile = File("/storage/emulated/0/Music/out file.m4a")

    private fun build(
        format: AudioFormat = AudioFormat.MP3, mode: AudioExtractionMode = AudioExtractionMode.Encode(), stream: Int = 0,
        builder: DefaultFfmpegCommandBuilder = DefaultFfmpegCommandBuilder(),
    ) = builder.buildAudioExtractionCommand(
        AudioExtractionRequest("job", ResolvedInput(inFile, "my clip.mp4", 1, true), TestMedia.metadata(), outFile,
            AudioExtractionSettings(format, stream, mode)),
    )
    private fun FfmpegCommand.opt(name: String) = output.options.value(name)

    @Test fun paths_are_single_arguments() {
        val cmd = build(); val args = cmd.toArguments()
        assertEquals(inFile.absolutePath, args[args.indexOf("-i") + 1]); assertEquals(outFile.absolutePath, args.last())
    }

    @Test fun global_options_keep_stats_enabled() {
        val g = build().globalOptions
        assertTrue("-y" in g); assertFalse("-nostats" in g); assertFalse("-progress" in g)
    }

    @Test fun container_format_per_audio_format() {
        mapOf(AudioFormat.MP3 to "mp3", AudioFormat.AAC to "adts", AudioFormat.M4A to "ipod", AudioFormat.FLAC to "flac",
            AudioFormat.WAV to "wav", AudioFormat.OPUS to "opus").forEach { (f, name) -> assertEquals(name, build(f).opt("-f")) }
    }

    @Test fun maps_the_chosen_audio_stream_and_drops_everything_else() {
        val o = build(stream = 2).output.options
        assertEquals(listOf("0:a:2"), o.values("-map"))
        listOf("-vn", "-sn", "-dn").forEach { assertTrue(it, o.has(it)) }
    }

    @Test fun tags_are_carried_over() = assertEquals("0", build().opt("-map_metadata"))

    @Test fun copy_has_no_encoding_options() {
        val o = build(AudioFormat.M4A, AudioExtractionMode.Copy).output.options
        assertEquals("copy", o.value("-c:a")); listOf("-b:a", "-ar", "-ac").forEach { assertFalse(it, o.has(it)) }
    }

    @Test fun encode_options_are_applied_when_set() {
        val cmd = build(AudioFormat.MP3, AudioExtractionMode.Encode(192, 44100, 2))
        assertEquals("libmp3lame", cmd.opt("-c:a")); assertEquals("192k", cmd.opt("-b:a"))
        assertEquals("44100", cmd.opt("-ar")); assertEquals("2", cmd.opt("-ac"))
    }

    @Test fun unset_encode_options_are_omitted() {
        val o = build(AudioFormat.FLAC).output.options
        listOf("-b:a", "-ar", "-ac").forEach { assertFalse(it, o.has(it)) }
    }

    @Test fun encoder_names_per_format() {
        mapOf(AudioFormat.MP3 to "libmp3lame", AudioFormat.AAC to "aac", AudioFormat.M4A to "aac", AudioFormat.FLAC to "flac",
            AudioFormat.WAV to "pcm_s16le", AudioFormat.OPUS to "libopus").forEach { (f, name) -> assertEquals(name, build(f).opt("-c:a")) }
    }

    @Test fun encoder_names_can_be_overridden() {
        val custom = DefaultFfmpegCommandBuilder(EncoderNames.Default.copy(audio = EncoderNames.Default.audio + (AudioCodec.MP3 to "libshine")))
        assertEquals("libshine", build(builder = custom).opt("-c:a"))
    }
}
