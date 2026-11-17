package io.bsoft.bconverter.media.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.ExperimentalApi
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.transformer.Composition
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import io.bsoft.bconverter.domain.filter.FilterPreview
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.CompositionTimeMap
import io.bsoft.bconverter.domain.timeline.PreviewSegment

/**
 * Step 20 (experimental, Settings → Labs): the video track as one Media3 [Composition] played by [CompositionPlayer].
 * Each clip carries its own effects and speed inside the composition, so cuts are gapless and a clip's look is on its
 * very first frame (the standard engine switches effects when the clip changes, which can show a frame or two of the
 * old look). Added audio clips are still sounded by small separate players, as in the standard engine.
 *
 * The player has one clock for the whole track; [CompositionTimeMap] converts it to and from "segment N at source P".
 */
@androidx.annotation.OptIn(UnstableApi::class, ExperimentalApi::class)
class CompositionTimelinePlayerEngine(private val context: Context, private val audioEnabled: Boolean = true) : VideoPreviewEngine {
    private val composition: CompositionPlayer = CompositionPlayer.Builder(context)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), audioEnabled)
        .build()
    override val player: Player get() = composition

    private val handler = Handler(Looper.getMainLooper())
    private var listener: TimelinePlayerListener? = null
    private var segments: List<PreviewSegment> = emptyList()
    private var map = CompositionTimeMap(emptyList())
    private val audioPlayers = HashMap<String, ExoPlayer>()

    private val positionPoll = object : Runnable {
        override fun run() {
            reportPosition()
            if (composition.isPlaying) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    init {
        composition.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> listener?.onReady()
                    Player.STATE_ENDED -> listener?.onEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
                reportPosition()
                handler.removeCallbacks(positionPoll)
                if (isPlaying) handler.post(positionPoll)
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(PlaybackErrors.fromMedia3(error.errorCode))
            }
        })
        if (!audioEnabled) composition.volume = 0f
    }

    private fun reportPosition() {
        val (index, source) = map.fromCompositionMs(composition.currentPosition.coerceAtLeast(0)) ?: return
        listener?.onVideoPosition(index, source)
    }

    override fun setListener(listener: TimelinePlayerListener?) { this.listener = listener }

    override fun prepare(segments: List<PreviewSegment>) {
        this.segments = segments
        map = CompositionTimeMap(segments)
        val files = segments.map { (it.source as? MediaSource.FileSource)?.file }
        if (segments.isEmpty() || files.any { it == null || !it.canRead() } || segments.any { it.sourceDurationMs <= 0 }) {
            composition.stop()
            handler.post { listener?.onError(PlaybackError.SourceNotFound) }
            return
        }
        val items = segments.mapIndexed { i, s ->
            val media = MediaItem.Builder()
                .setUri(Uri.fromFile(files[i]!!))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(s.sourceStartMs).setEndPositionMs(s.sourceEndMs).build(),
                )
                .build()
            EditedMediaItem.Builder(media)
                .setDurationUs(s.sourceDurationMs * 1000)
                // A detached or silenced clip is heard as silence; volumes between 0 and 1 are not previewed here.
                .setRemoveAudio(s.muted || s.volume <= 0f)
                .setSpeed(ConstantSpeed(s.speed))
                .setEffects(Effects(emptyList(), PreviewVideoEffects.build(FilterPreview.of(s.filter), s.transform)))
                .build()
        }
        composition.setComposition(Composition.Builder(EditedMediaItemSequence.Builder(items).build()).build())
        composition.prepare()
    }

    override fun play() = composition.play()
    override fun pause() = composition.pause()

    override fun seekTo(segmentIndex: Int, sourcePositionMs: Long) {
        if (segmentIndex !in segments.indices) return
        composition.seekTo(map.toCompositionMs(segmentIndex, sourcePositionMs))
    }

    override fun startAudio(clip: AudioClip, sourcePositionMs: Long) {
        if (!audioEnabled) return
        val file = (clip.source as? MediaSource.FileSource)?.file?.takeIf { it.canRead() } ?: return
        stopAudio(clip.id)
        val audio = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), /* handleAudioFocus = */ false)
            .build()
        audio.trackSelectionParameters = audio.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        audio.volume = clip.volume.coerceIn(0f, 1f)
        audio.setPlaybackSpeed(clip.playbackSpeed)
        audio.setMediaItem(
            MediaItem.Builder().setUri(Uri.fromFile(file))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.sourceStartMs).setEndPositionMs(clip.sourceEndMs).build())
                .build(),
        )
        audio.seekTo((sourcePositionMs - clip.sourceStartMs).coerceAtLeast(0))
        audio.playWhenReady = true
        audio.prepare()
        audioPlayers[clip.id] = audio
    }

    override fun stopAudio(clipId: String) { audioPlayers.remove(clipId)?.release() }

    override fun stopAllAudio() {
        audioPlayers.values.forEach { it.release() }
        audioPlayers.clear()
    }

    override fun release() {
        handler.removeCallbacks(positionPoll)
        stopAllAudio()
        composition.release()
    }

    /** One speed for the whole item. */
    private class ConstantSpeed(private val speed: Float) : SpeedProvider {
        override fun getSpeed(timeUs: Long): Float = speed
        override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
    }

    private companion object { const val POLL_INTERVAL_MS = 50L }
}
