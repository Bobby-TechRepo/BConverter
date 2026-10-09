package io.bsoft.bconverter.media.device

import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidCodecInfoProviderTest {
    @Test fun every_android_device_can_decode_h264() {
        val codecs = AndroidCodecInfoProvider().codecs()
        assertTrue(codecs.any { !it.isEncoder && it.supportedTypes.any { t -> t.equals("video/avc", true) } })
    }
}
