package dev.stopptanz.app.session

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.stopptanz.engine.Mode
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
    private var autoStopJob: Job? = null

    init {
        player.repeatMode = if (engine.playlist.loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    autoStopJob?.cancel()
                    engine.onPlaylistEnded()
                    onStateChanged(engine.state)
                }
            }
        })
    }

    fun start() {
        player.setMediaItems(engine.orderedTracks.map { MediaItem.fromUri(Uri.parse(it)) })
        player.prepare()
        player.playWhenReady = true
        onStateChanged(engine.state)
        scheduleAutoStop()
    }

    fun stop() {
        autoStopJob?.cancel()
        performStop()
    }

    private fun performStop() {
        engine.stop()
        player.pause()
        onStateChanged(engine.state)
        if (engine.mode == Mode.FREEZE_DANCE) {
            autoResumeJob = scope.launch {
                delay(engine.pauseDurationMillis)
                engine.onPauseElapsed()
                player.play()
                onStateChanged(engine.state)
                scheduleAutoStop()
            }
        }
    }

    fun resume() {
        autoResumeJob?.cancel()
        engine.resume()
        player.play()
        onStateChanged(engine.state)
        scheduleAutoStop()
    }

    private fun scheduleAutoStop() {
        autoStopJob = scope.launch {
            delay(engine.nextStopDelayMillis())
            performStop()
        }
    }

    /** Cancels pending auto-Stop/auto-resume timers without releasing the player. */
    fun cancelJobs() {
        autoResumeJob?.cancel()
        autoStopJob?.cancel()
    }

    /** Stops playback and releases the held media/decoder resources; the player itself stays reusable for the next Session. */
    fun close() {
        cancelJobs()
        engine.close()
        player.stop()
        player.clearMediaItems()
        onStateChanged(engine.state)
    }

    fun release() {
        cancelJobs()
        player.release()
    }
}
