package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFormatTest {
    @Test fun formats_follow_the_spec_order() =
        assertEquals(listOf("mp3", "aac", "m4a", "flac", "wav", "opus"), AudioFormat.entries.map { it.extension })

    @Test fun each_format_maps_to_its_codec() = assertEquals(
        listOf(AudioCodec.MP3, AudioCodec.AAC, AudioCodec.AAC, AudioCodec.FLAC, AudioCodec.PCM, AudioCodec.OPUS),
        AudioFormat.entries.map { it.codec },
    )

    @Test fun mime_types() = assertEquals(
        listOf("audio/mpeg", "audio/aac", "audio/mp4", "audio/flac", "audio/x-wav", "audio/ogg"), AudioFormat.entries.map { it.mimeType },
    )

    @Test fun only_flac_and_wav_are_lossless() {
        assertTrue(AudioFormat.FLAC.isLossless); assertTrue(AudioFormat.WAV.isLossless)
        listOf(AudioFormat.MP3, AudioFormat.AAC, AudioFormat.M4A, AudioFormat.OPUS).forEach { assertFalse(it.name, it.isLossless) }
    }
}
