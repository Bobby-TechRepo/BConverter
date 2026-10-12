package io.bsoft.bconverter.feature.converter

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test fun durations_are_minutes_and_seconds_with_hours_when_needed() {
        assertEquals("0:00", formatDuration(0)); assertEquals("0:12", formatDuration(12_345)); assertEquals("1:01", formatDuration(61_000))
        assertEquals("59:59", formatDuration(3_599_999)); assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test fun negative_durations_show_zero() = assertEquals("0:00", formatDuration(-5))

    @Test fun video_codec_names() = assertEquals(
        listOf("H.264", "H.265 (HEVC)", "VP8", "VP9", "AV1", "MPEG-4", "MPEG-2"), VideoCodec.entries.map { it.displayName },
    )

    @Test fun audio_codec_names() = assertEquals(
        listOf("AAC", "MP3", "Opus", "Vorbis", "FLAC", "PCM (WAV)", "AC-3"), AudioCodec.entries.map { it.displayName },
    )

    @Test fun container_names() = assertEquals(
        listOf("MP4", "MKV", "3GP", "WebM", "MOV", "AVI", "MPEG-TS"), ContainerFormat.entries.map { it.displayName },
    )

    @Test fun audio_format_names() = assertEquals(
        listOf("MP3", "AAC (ADTS)", "M4A (AAC)", "FLAC", "WAV", "Opus"),
        io.bsoft.bconverter.domain.model.AudioFormat.entries.map { it.displayName },
    )
}
