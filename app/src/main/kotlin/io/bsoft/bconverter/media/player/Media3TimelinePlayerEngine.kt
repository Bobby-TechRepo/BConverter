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
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import io.bsoft.bconverter.domain.filter.FilterPreview
import io.bsoft.bconverter.domain.filter.PreviewLook
import io.bsoft.bconverter.domain.transform.ClipTransform
import io.bsoft.bconverter.domain.model.MediaSource
import io.bsoft.bconverter.domain.timeline.AudioClip
import io.bsoft.bconverter.domain.timeline.PreviewSegment

/**
 * ExoPlayer behind [TimelinePlayerEngine]. The video track is one playlist of clipped items (each item's speed is applied
 * when it becomes current); every sounding audio clip gets its own small audio-only player, released when it stops.
 * Create and use on the main thread. With [audioEnabled] false nothing is ever sounded (device tests).
 *
 * Clip filters: a colour grade is approximated with the player's stock effects, switched when the current clip changes. The effect
 * pipeline is only switched on for projects that have a previewable look, so unfiltered projects play exactly as before.
 *
 * Preview limits: ExoPlayer's volume tops out at 1.0, so audio clip volumes above 100 % are previewed at 100 %; the exported
 * file does apply them.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class Media3TimelinePlayerEngine(private val context: Context, private val audioEnabled: Boolean = true) : VideoPreviewEngine {
    override val player: Player get() = exoPlayer

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ audioEnabled,
        )
        .setHandleAudioBecomingNoisy(audioEnabled)
        .build()
        .also { player ->
            if (!audioEnabled) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            }
        }

    private val handler = Handler(Looper.getMainLooper())
    private var listener: TimelinePlayerListener? = null
    private var segments: List<PreviewSegment> = emptyList()
    private val audioPlayers = HashMap<String, ExoPlayer>()

    private val positionPoll = object : Runnable {
        override fun run() {
            reportPosition()
            if (exoPlayer.isPlaying) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> listener?.onReady()
                    Player.STATE_ENDED -> listener?.onEnded()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                applySpeed()
                reportPosition()
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
    }

    private fun currentSegment(): PreviewSegment? = segments.getOrNull(exoPlayer.currentMediaItemIndex)

    private var effectsInstalled = false
    private var applied: PreviewEffects? = null

    /** What the preview shows of one clip: its colour approximation and its crop / turn / mirror. */
    private data class PreviewEffects(val look: PreviewLook?, val transform: ClipTransform)

    private fun effectsOf(s: PreviewSegment) = PreviewEffects(FilterPreview.of(s.filter), s.transform)
    private fun PreviewEffects.isPlain() = look == null && transform.isIdentity

    private fun effectsFor(e: PreviewEffects?): List<Effect> = if (e == null) emptyList() else PreviewVideoEffects.build(e.look, e.transform)

    /** Switches the preview effects to the current clip's; does nothing while no clip has any. */
    private fun applyLook() {
        val now = currentSegment()?.let(::effectsOf)
        if (!effectsInstalled && (now == null || now.isPlain())) return
        if (effectsInstalled && now == applied) return
        exoPlayer.setVideoEffects(effectsFor(now))
        effectsInstalled = true
        applied = now
    }

    private fun applySpeed() {
        applyLook()
        currentSegment()?.let {
            exoPlayer.setPlaybackSpeed(it.speed)
            exoPlayer.volume = if (it.muted) 0f else it.volume.coerceIn(0f, 1f)
        }
    }

    private fun reportPosition() {
        val segment = currentSegment() ?: return
        // A clipped item's position starts at 0 at the clip's first frame.
        listener?.onVideoPosition(exoPlayer.currentMediaItemIndex, segment.sourceStartMs + exoPlayer.currentPosition.coerceAtLeast(0))
    }

    private fun clipped(file: java.io.File, startMs: Long, endMs: Long): MediaItem = MediaItem.Builder()
        .setUri(Uri.fromFile(file))
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build())
        .build()

    override fun setListener(listener: TimelinePlayerListener?) { this.listener = listener }

    override fun prepare(segments: List<PreviewSegment>) {
        this.segments = segments
        val files = segments.map { (it.source as? MediaSource.FileSource)?.file }
        if (files.any { it == null || !it.canRead() }) {
            // Fail fast rather than sit in the loader's retry/backoff.
            exoPlayer.stop()
            handler.post { listener?.onError(PlaybackError.SourceNotFound) }
            return
        }
        // Effects must be set before prepare; only projects with a previewable look (or one used earlier) need the pipeline.
        if (effectsInstalled || segments.any { !effectsOf(it).isPlain() }) {
            val first = segments.firstOrNull()?.let(::effectsOf)
            exoPlayer.setVideoEffects(effectsFor(first))
            effectsInstalled = true
            applied = first
        }
        exoPlayer.setMediaItems(segments.mapIndexed { i, s -> clipped(files[i]!!, s.sourceStartMs, s.sourceEndMs) })
        applySpeed()
        exoPlayer.prepare()
    }

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()

    override fun seekTo(segmentIndex: Int, sourcePositionMs: Long) {
        val segment = segments.getOrNull(segmentIndex) ?: return
        exoPlayer.seekTo(segmentIndex, (sourcePositionMs - segment.sourceStartMs).coerceAtLeast(0))
        applySpeed()
    }

    override fun startAudio(clip: AudioClip, sourcePositionMs: Long) {
        if (!audioEnabled) return
        val file = (clip.source as? MediaSource.FileSource)?.file?.takeIf { it.canRead() } ?: return
        stopAudio(clip.id)
        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), /* handleAudioFocus = */ false)
            .build()
        // The sound may come from a video file: only its audio is needed.
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true).build()
        player.volume = clip.volume.coerceIn(0f, 1f)
        player.setPlaybackSpeed(clip.playbackSpeed)
        player.setMediaItem(clipped(file, clip.sourceStartMs, clip.sourceEndMs))
        player.seekTo((sourcePositionMs - clip.sourceStartMs).coerceAtLeast(0))
        player.playWhenReady = true
        player.prepare()
        audioPlayers[clip.id] = player
    }

    override fun stopAudio(clipId: String) { audioPlayers.remove(clipId)?.release() }

    override fun stopAllAudio() {
        audioPlayers.values.forEach { it.release() }
        audioPlayers.clear()
    }

    override fun release() {
        handler.removeCallbacks(positionPoll)
        stopAllAudio()
        exoPlayer.release()
    }

    private companion object { const val POLL_INTERVAL_MS = 50L }
}
