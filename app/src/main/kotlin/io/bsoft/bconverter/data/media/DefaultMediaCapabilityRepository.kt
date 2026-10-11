package io.bsoft.bconverter.data.media

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.Codec
import io.bsoft.bconverter.domain.model.CodecAvailability
import io.bsoft.bconverter.domain.model.CodecCapability
import io.bsoft.bconverter.domain.model.ContainerCompatibility
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec
import io.bsoft.bconverter.domain.repository.MediaCapabilityRepository

/** Snapshot repository: availability reports from any number of sources are merged with OR semantics. */
class DefaultMediaCapabilityRepository(availability: List<CodecAvailability>) : MediaCapabilityRepository {
    private val byCodec: Map<Codec, CodecAvailability> = availability.groupBy { it.codec }.mapValues { (codec, reports) ->
        CodecAvailability(
            codec,
            canDecode = reports.any { it.canDecode },
            canEncode = reports.any { it.canEncode },
            supportsCrf = reports.any { it.supportsCrf },
        )
    }

    private fun canEncode(codec: Codec) = byCodec[codec]?.canEncode == true

    override fun supportsCrf(codec: VideoCodec): Boolean = byCodec[codec]?.let { it.canEncode && it.supportsCrf } == true

    override fun getContainers(): List<ContainerFormat> =
        ContainerFormat.entries.filter { c -> ContainerCompatibility.videoCodecs(c).any { canEncode(it) } }

    override fun getVideoCodecs(container: ContainerFormat): List<VideoCodec> =
        VideoCodec.entries.filter { it in ContainerCompatibility.videoCodecs(container) && canEncode(it) }

    override fun getAudioCodecs(container: ContainerFormat): List<AudioCodec> =
        AudioCodec.entries.filter { it in ContainerCompatibility.audioCodecs(container) && canEncode(it) }

    override fun isCompatible(container: ContainerFormat, videoCodec: VideoCodec?, audioCodec: AudioCodec?): Boolean {
        if (videoCodec == null && audioCodec == null) return false
        val videoOk = videoCodec?.let { it in ContainerCompatibility.videoCodecs(container) && canEncode(it) } ?: true
        val audioOk = audioCodec?.let { it in ContainerCompatibility.audioCodecs(container) && canEncode(it) } ?: true
        return videoOk && audioOk
    }

    /** One entry per known codec, including those no source reported (both flags false). */
    fun getCapabilities(): List<CodecCapability> {
        val all = buildList<Codec> { addAll(VideoCodec.entries); addAll(AudioCodec.entries) }
        return all.map { codec ->
            val a = byCodec[codec]
            CodecCapability(
                codec = codec,
                canDecode = a?.canDecode == true,
                canEncode = a?.canEncode == true,
                supportedContainers = ContainerFormat.entries.filter { ContainerCompatibility.supports(it, codec) }.toSet(),
            )
        }
    }
}
