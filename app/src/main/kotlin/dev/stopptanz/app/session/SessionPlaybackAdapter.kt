package dev.stopptanz.app.session

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.SessionEngine
import dev.stopptanz.engine.SessionState
import dev.stopptanz.engine.StopInterval
import dev.stopptanz.engine.TrackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val POSITION_POLL_INTERVAL_MILLIS = 500L
private const val PAUSE_COUNTDOWN_TICK_MILLIS = 1_000L
private const val SEEK_STEP_MILLIS = 10_000L

/**
 * Thin glue layer wrapping ExoPlayer, driven by [SessionEngine] state.
 * Not a primary test target for v1 (spec: playback adapter is glue, not deep-tested).
 */
class SessionPlaybackAdapter(
    private val player: ExoPlayer,
    private val engine: SessionEngine,
    private val scope: CoroutineScope,
    private val onStateChanged: (SessionState) -> Unit = {},
    private val onTrackChanged: (TrackStatus) -> Unit = {},
    private val onPositionChanged: (PlaybackPosition) -> Unit = {},
    private val onPauseRemainingChanged: (Long?) -> Unit = {},
) {
    private var autoResumeJob: Job? = null
    private var autoStopJob: Job? = null
    private var positionTickerJob: Job? = null
    private var pauseCountdownJob: Job? = null

    /** Latest freeze auto-resume countdown value pushed via [onPauseRemainingChanged]; captured by [pause] when pausing mid-countdown. */
    private var currentPauseRemainingMillis: Long? = null

    init {
        player.repeatMode = if (engine.playlist.loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    autoStopJob?.cancel()
                    stopPositionTicker()
                    engine.onPlaylistEnded()
                    onStateChanged(engine.state)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    engine.onTrackAdvanced()
                    onTrackChanged(engine.trackStatus)
                }
            }
        })
    }

    fun start() {
        player.setMediaItems(engine.orderedTracks.map { MediaItem.fromUri(Uri.parse(it.uri)) })
        player.prepare()
        player.playWhenReady = true
        onStateChanged(engine.state)
        onTrackChanged(engine.trackStatus)
        startPositionTicker()
        scheduleAutoStop()
    }

    fun stop() {
        autoStopJob?.cancel()
        performStop()
    }

    private fun performStop() {
        engine.stop()
        player.pause()
        stopPositionTicker()
        onStateChanged(engine.state)
        if (engine.mode == Mode.FREEZE_DANCE) {
            scheduleAutoResume()
        }
    }

    fun resume() {
        autoResumeJob?.cancel()
        stopPauseCountdown()
        engine.resume()
        player.play()
        startPositionTicker()
        onStateChanged(engine.state)
        scheduleAutoStop()
    }

    /** Suspends the Session from Playing or Stopped, capturing any in-flight freeze auto-resume countdown so [resumeFromPause] can restore it exactly. */
    fun pause() {
        val remainingFreezeMillis = if (engine.state is SessionState.Stopped && engine.mode == Mode.FREEZE_DANCE) {
            currentPauseRemainingMillis
        } else {
            null
        }
        autoStopJob?.cancel()
        autoResumeJob?.cancel()
        stopPositionTicker()
        stopPauseCountdown()
        player.pause()
        engine.pause(remainingFreezeMillis)
        onStateChanged(engine.state)
    }

    /** Restores whichever state was active before [pause]: resumes playback if it was Playing, or resumes the freeze countdown (with whatever time was remaining) if it was Stopped in Freeze Dance mode. */
    fun resumeFromPause() {
        val remainingFreezeMillis = engine.resumeFromPause()
        when {
            engine.state is SessionState.Playing -> {
                player.play()
                startPositionTicker()
                scheduleAutoStop()
            }
            engine.state is SessionState.Stopped && engine.mode == Mode.FREEZE_DANCE -> {
                scheduleAutoResumeWithRemaining(remainingFreezeMillis ?: engine.pauseDurationMillis)
            }
        }
        onStateChanged(engine.state)
    }

    /** Jumps to the previous Track without ending the Session; resets whichever pending timer (auto-stop or auto-resume) applies to the current state. */
    fun skipToPrevious() {
        engine.skipToPrevious()
        seekToCurrentTrackAndRescheduleTimers()
    }

    /** Jumps to the next Track without ending the Session; resets whichever pending timer (auto-stop or auto-resume) applies to the current state. */
    fun skipToNext() {
        engine.skipToNext()
        seekToCurrentTrackAndRescheduleTimers()
    }

    /** Jumps position backward within the current Track by [SEEK_STEP_MILLIS], clamped at the Track start. Leaves any pending Stop-Interval/pause timer untouched. */
    fun seekBackward() {
        seekBy(-SEEK_STEP_MILLIS)
    }

    /** Jumps position forward within the current Track by [SEEK_STEP_MILLIS], clamped at the Track end (does not advance to the next Track). Leaves any pending Stop-Interval/pause timer untouched. */
    fun seekForward() {
        seekBy(SEEK_STEP_MILLIS)
    }

    private fun seekBy(deltaMillis: Long) {
        val duration = player.duration
        val upperBound = if (duration == C.TIME_UNSET) Long.MAX_VALUE else duration
        val target = (player.currentPosition + deltaMillis).coerceIn(0, upperBound)
        player.seekTo(target)
    }

    private fun seekToCurrentTrackAndRescheduleTimers() {
        player.seekTo(engine.currentTrackIndex, 0)
        onTrackChanged(engine.trackStatus)
        when {
            engine.state is SessionState.Playing -> {
                autoStopJob?.cancel()
                scheduleAutoStop()
            }
            engine.state is SessionState.Stopped && engine.mode == Mode.FREEZE_DANCE -> {
                autoResumeJob?.cancel()
                scheduleAutoResume()
            }
        }
    }

    private fun scheduleAutoResume() {
        scheduleAutoResumeWithRemaining(engine.pauseDurationMillis)
    }

    private fun scheduleAutoResumeWithRemaining(remainingMillis: Long) {
        startPauseCountdown(remainingMillis)
        autoResumeJob = scope.launch {
            delay(remainingMillis)
            engine.onPauseElapsed()
            stopPauseCountdown()
            player.play()
            startPositionTicker()
            onStateChanged(engine.state)
            scheduleAutoStop()
        }
    }

    private fun startPauseCountdown(totalMillis: Long) {
        pauseCountdownJob = scope.launch {
            var remaining = totalMillis
            currentPauseRemainingMillis = remaining
            onPauseRemainingChanged(remaining)
            while (remaining > 0) {
                delay(PAUSE_COUNTDOWN_TICK_MILLIS)
                remaining = (remaining - PAUSE_COUNTDOWN_TICK_MILLIS).coerceAtLeast(0)
                currentPauseRemainingMillis = remaining
                onPauseRemainingChanged(remaining)
            }
        }
    }

    private fun stopPauseCountdown() {
        pauseCountdownJob?.cancel()
        pauseCountdownJob = null
        currentPauseRemainingMillis = null
        onPauseRemainingChanged(null)
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (true) {
                onPositionChanged(PlaybackPosition(player.currentPosition, player.duration.coerceAtLeast(0)))
                delay(POSITION_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun stopPositionTicker() {
        positionTickerJob?.cancel()
    }

    fun setStopInterval(stopInterval: StopInterval) {
        engine.setStopInterval(stopInterval)
    }

    fun setPauseDurationMillis(pauseDurationMillis: Long) {
        engine.setPauseDurationMillis(pauseDurationMillis)
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
        stopPauseCountdown()
        stopPositionTicker()
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
