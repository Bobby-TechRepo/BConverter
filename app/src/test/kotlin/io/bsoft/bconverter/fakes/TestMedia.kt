package io.bsoft.bconverter.fakes

import io.bsoft.bconverter.data.media.DefaultMediaCapabilityRepository
import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioStream
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.model.VideoStream

object TestMedia {
    val ALL: List<Codec> = buildList { addAll(VideoCodec.entries); addAll(AudioCodec.entries) }

    fun video(codec: String? = "h264", w: Int = 1920, h: Int = 1080, rotation: Int = 0, bitrate: Long? = 4_800_000) =
        VideoStream(0, codec, w, h, 30.0, bitrate, rotation)

    fun audio(codec: String? = "aac") = AudioStream(1, codec, 44100, 2, 128_000)

    fun metadata(video: VideoStream? = video(), audio: AudioStream? = audio()) =
        MediaMetadata(10_000, ContainerFormat.MP4, listOfNotNull(video), listOfNotNull(audio))

    private val CRF_CODECS = setOf<Codec>(VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9, VideoCodec.AV1)

    /** A build whose H.264/HEVC/VP9/AV1 encoders support CRF (e.g. libx264). */
    fun repo(vararg codecs: Codec) =
        DefaultMediaCapabilityRepository(codecs.map { CodecAvailability(it, true, true, supportsCrf = it in CRF_CODECS) })

    /** A build whose encoders are bitrate-only (e.g. MediaCodec wrappers, Kvazaar). */
    fun repoBitrateOnly(vararg codecs: Codec) = DefaultMediaCapabilityRepository(codecs.map { CodecAvailability(it, true, true) })
    fun allEncoders() = repo(*ALL.toTypedArray())
}
