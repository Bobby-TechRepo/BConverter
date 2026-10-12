package io.bsoft.bconverter.feature.converter

import io.bsoft.bconverter.domain.model.AudioCodec
import io.bsoft.bconverter.domain.model.AudioFormat
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.VideoCodec
import java.util.Locale

fun formatDuration(ms: Long): String {
    val total = ms.coerceAtLeast(0) / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

val VideoCodec.displayName: String get() = when (this) {
    VideoCodec.H264 -> "H.264"
    VideoCodec.HEVC -> "H.265 (HEVC)"
    VideoCodec.VP8 -> "VP8"
    VideoCodec.VP9 -> "VP9"
    VideoCodec.AV1 -> "AV1"
    VideoCodec.MPEG4 -> "MPEG-4"
    VideoCodec.MPEG2 -> "MPEG-2"
}

val AudioCodec.displayName: String get() = when (this) {
    AudioCodec.AAC -> "AAC"
    AudioCodec.MP3 -> "MP3"
    AudioCodec.OPUS -> "Opus"
    AudioCodec.VORBIS -> "Vorbis"
    AudioCodec.FLAC -> "FLAC"
    AudioCodec.PCM -> "PCM (WAV)"
    AudioCodec.AC3 -> "AC-3"
}

val ContainerFormat.displayName: String get() = when (this) {
    ContainerFormat.MP4 -> "MP4"
    ContainerFormat.MKV -> "MKV"
    ContainerFormat.THREE_GP -> "3GP"
    ContainerFormat.WEBM -> "WebM"
    ContainerFormat.MOV -> "MOV"
    ContainerFormat.AVI -> "AVI"
    ContainerFormat.MPEG_TS -> "MPEG-TS"
}

val AudioFormat.displayName: String get() = when (this) {
    AudioFormat.MP3 -> "MP3"
    AudioFormat.AAC -> "AAC (ADTS)"
    AudioFormat.M4A -> "M4A (AAC)"
    AudioFormat.FLAC -> "FLAC"
    AudioFormat.WAV -> "WAV"
    AudioFormat.OPUS -> "Opus"
}
