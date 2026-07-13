package dev.stopptanz.app.session

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.Playlist
import dev.stopptanz.engine.SessionEngine
import dev.stopptanz.engine.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Thin glue layer wrapping ExoPlayer, driven by [SessionEngine] state.
 * Not a primary test target for v1 (spec: playback adapter is glue, not deep-tested).
 */
class SessionPlaybackAdapter(
    private val player: ExoPlayer,
    private val engine: SessionEngine,
    private val scope: CoroutineScope,
    private val onStateChanged: (SessionState) -> Unit = {},
) {
    private var autoResumeJob: Job? = null

    fun start(playlist: Playlist) {
        player.setMediaItems(playlist.tracks.map { MediaItem.fromUri(Uri.parse(it)) })
        player.prepare()
        player.playWhenReady = true
        onStateChanged(engine.state)
    }

    fun stop() {
        engine.stop()
        player.pause()
        onStateChanged(engine.state)
        if (engine.mode == Mode.FREEZE_DANCE) {
            autoResumeJob = scope.launch {
                delay(engine.pauseDurationMillis)
                engine.onPauseElapsed()
                player.play()
                onStateChanged(engine.state)
            }
        }
    }

    fun resume() {
        engine.resume()
        player.play()
        onStateChanged(engine.state)
    }

    fun release() {
        autoResumeJob?.cancel()
        player.release()
    }
}
