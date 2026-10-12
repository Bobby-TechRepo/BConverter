package io.bsoft.bconverter.domain.repository

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec

/** Answers "what may the UI offer?" — only things that can actually be encoded (spec §11–13). */
interface MediaCapabilityRepository {
    /** Containers that can hold at least one encodable video codec. */
    fun getContainers(): List<ContainerFormat>

    fun getVideoCodecs(container: ContainerFormat): List<VideoCodec>

    fun getAudioCodecs(container: ContainerFormat): List<AudioCodec>

    /**
     * True when the codecs are valid for [container] AND encoders exist for them.
     * A null codec means "no such stream"; both null is never compatible.
     */
    fun isCompatible(container: ContainerFormat, videoCodec: VideoCodec?, audioCodec: AudioCodec?): Boolean

    /** Whether the installed encoder for [codec] accepts CRF; otherwise quality must be set as a bitrate. */
    fun supportsCrf(codec: VideoCodec): Boolean

    /** Whether an audio codec can be encoded, independent of any video container (used for audio-only output). */
    fun canEncodeAudio(codec: AudioCodec): Boolean
}
