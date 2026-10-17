package io.bsoft.bconverter.domain.timeline

import io.bsoft.bconverter.domain.model.AudioSettings
import io.bsoft.bconverter.domain.model.ContainerFormat
import io.bsoft.bconverter.domain.model.MediaMetadata
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.model.VideoSettings

/**
 * How to export a project. [video] supplies the codec, quality and (optionally) the canvas size and frame rate;
 * [audio] null means a silent file. With [keepOriginalAudio] false the video clips' own sound is dropped, so only
 * the added audio clips are heard (audio replacement).
 */
data class ExportSettings(
    val container: ContainerFormat,
    val video: VideoSettings,
    val audio: AudioSettings?,
    val keepOriginalAudio: Boolean = true,
)

/** A timeline plus what is needed to render it. [sourceMetadata] holds the probe result of every video source. */
data class VideoProject(
    val id: String,
    val name: String,
    val timeline: Timeline,
    val export: ExportSettings,
    val sourceMetadata: Map<MediaSource, MediaMetadata> = emptyMap(),
)
