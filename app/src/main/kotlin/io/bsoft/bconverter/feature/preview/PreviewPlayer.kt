package io.bsoft.bconverter.feature.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.ui.PlayerView
import io.bsoft.bconverter.media.player.Media3PlayerEngine
import io.bsoft.bconverter.media.player.PlaybackController
import java.io.File

/** Plays [file] with our own controls. The player lives only while this composable does, and pauses when the app stops. */
@Composable
fun PreviewPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val engine = remember(file) { Media3PlayerEngine(context) }
    val controller = remember(file) { PlaybackController(engine).also { it.load(file) } }
    DisposableEffect(controller) { onDispose { controller.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.pause() }
    val state by controller.state.collectAsState()

    Column(modifier) {
        AndroidView(
            factory = { PlayerView(it).apply { useController = false; player = engine.exoPlayer } },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        )
        PreviewControls(state, onPlayPause = controller::togglePlayPause, onSeek = controller::seekTo)
    }
}
