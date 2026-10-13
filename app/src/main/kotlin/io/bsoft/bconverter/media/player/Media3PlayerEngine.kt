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
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

/** ExoPlayer behind [PlayerEngine]. Create and use on the main thread. */
class Media3PlayerEngine(context: Context) : PlayerEngine {
    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            /* handleAudioFocus = */ true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private var listener: PlayerEngineListener? = null

    private val positionPoll = object : Runnable {
        override fun run() {
            listener?.onPosition(exoPlayer.currentPosition)
            if (exoPlayer.isPlaying) handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> listener?.onReady(knownDuration())
                    Player.STATE_ENDED -> listener?.onEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                listener?.onIsPlayingChanged(isPlaying)
                listener?.onPosition(exoPlayer.currentPosition)
                handler.removeCallbacks(positionPoll)
                if (isPlaying) handler.post(positionPoll)
            }

            override fun onPlayerError(error: PlaybackException) {
                listener?.onError(PlaybackErrors.fromMedia3(error.errorCode))
            }
        })
    }

    private fun knownDuration(): Long = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it >= 0 } ?: 0L

    override fun setListener(listener: PlayerEngineListener?) { this.listener = listener }

    override fun prepare(file: File) {
        exoPlayer.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        exoPlayer.prepare()
    }

    override fun play() = exoPlayer.play()
    override fun pause() = exoPlayer.pause()
    override fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs)

    override fun release() {
        handler.removeCallbacks(positionPoll)
        exoPlayer.release()
    }

    private companion object { const val POLL_INTERVAL_MS = 200L }
}
