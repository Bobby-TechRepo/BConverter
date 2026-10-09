package io.bsoft.bconverter.media.device

import android.media.MediaCodecList

class AndroidCodecInfoProvider : CodecInfoProvider {
    override fun codecs(): List<CodecInfo> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.map {
            CodecInfo(it.name, it.supportedTypes.toList(), it.isEncoder)
        }
}
