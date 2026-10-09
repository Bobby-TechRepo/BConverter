package io.bsoft.bconverter.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamModelsTest {
    private fun video(rotation: Int) =
        VideoStream(0, "h264", width = 1920, height = 1080, frameRate = 30.0, rotationDegrees = rotation)

    @Test fun unrotated_video_displays_at_stored_size() {
        listOf(0, 180).forEach { assertEquals(1920 to 1080, video(it).displayWidth to video(it).displayHeight) }
    }

    @Test fun quarter_turn_video_swaps_display_dimensions() {
        listOf(90, 270).forEach { assertEquals(1080 to 1920, video(it).displayWidth to video(it).displayHeight) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rotation_must_be_a_multiple_of_90() { video(45) }

    @Test fun stream_count_includes_all_stream_kinds() {
        val m = MediaMetadata(
            1000, ContainerFormat.MKV,
            videoStreams = listOf(video(0)),
            audioStreams = listOf(AudioStream(1, "aac", 48000, 2), AudioStream(2, "ac3", 48000, 2)),
            subtitleStreams = listOf(SubtitleStream(3, "eng")),
        )
        assertEquals(4, m.streamCount)
    }
}
