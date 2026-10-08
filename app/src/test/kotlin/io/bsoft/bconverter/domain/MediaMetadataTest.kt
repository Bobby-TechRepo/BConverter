package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaMetadataTest {
    private val video = VideoStream(index = 0, codec = "h264", width = 1920, height = 1080, frameRate = 30.0)
    private val audio = AudioStream(index = 1, codec = "aac", sampleRateHz = 44100, channels = 2)

    @Test(expected = IllegalArgumentException::class)
    fun negative_duration_is_rejected() {
        MediaMetadata(durationMs = -1, container = ContainerFormat.MP4)
    }

    @Test fun zero_duration_is_allowed() {
        MediaMetadata(durationMs = 0, container = null)
    }

    @Test fun has_video_and_audio_when_both_streams_exist() {
        val m = MediaMetadata(1000, ContainerFormat.MP4, listOf(video), listOf(audio))
        assertTrue(m.hasVideo); assertTrue(m.hasAudio); assertFalse(m.isAudioOnly)
    }

    @Test fun audio_only_media_is_detected() {
        val m = MediaMetadata(1000, ContainerFormat.MPEG_TS, audioStreams = listOf(audio))
        assertTrue(m.isAudioOnly); assertFalse(m.hasVideo)
    }

    @Test fun video_without_audio_has_no_audio() {
        val m = MediaMetadata(1000, ContainerFormat.MKV, videoStreams = listOf(video))
        assertFalse(m.hasAudio)
    }

    @Test fun media_without_any_stream_is_not_audio_only() {
        assertFalse(MediaMetadata(1000, null).isAudioOnly)
    }
}
