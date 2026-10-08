package io.bsoft.bconverter.feature.pickinput

import org.junit.Assert.assertEquals
import org.junit.Test

class PickKindTest {
    @Test fun video_picker_filters_on_video_mime() = assertEquals(listOf("video/*"), PickKind.VIDEO.mimeTypes)
    @Test fun audio_picker_filters_on_audio_mime() = assertEquals(listOf("audio/*"), PickKind.AUDIO.mimeTypes)
}
