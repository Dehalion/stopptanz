package dev.stopptanz.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import dev.stopptanz.app.R
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.Playlist
import dev.stopptanz.engine.SessionEngine
import dev.stopptanz.engine.SessionState
import dev.stopptanz.engine.StopInterval
import dev.stopptanz.engine.TrackStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private const val NOTIFICATION_CHANNEL_ID = "playback"
private const val NOTIFICATION_ID = 1

/**
 * Foreground Service hosting playback so a Session survives screen-off and backgrounding.
 * Owns the ExoPlayer/SessionEngine/SessionPlaybackAdapter for the lifetime of a Session
 * instead of the Compose tree (spec #10 — these were previously tied to MainActivity's
 * lifecycle and died with the screen/Activity).
 */
class PlaybackService : MediaSessionService() {

    inner class LocalBinder : Binder() {
        val service: PlaybackService get() = this@PlaybackService
    }

    private val localBinder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Main)

    private lateinit var player: ExoPlayer
    private lateinit var routedPlayer: ForwardingPlayer
    private lateinit var mediaSession: MediaSession
    private var adapter: SessionPlaybackAdapter? = null

    private val _sessionState = MutableStateFlow<SessionState?>(null)
    val sessionState: StateFlow<SessionState?> = _sessionState

    private val _currentMode = MutableStateFlow<Mode?>(null)
    /** Mode of the in-progress Session, if any — lets a rebinding Activity recover which Mode's UI to show. */
    val currentMode: StateFlow<Mode?> = _currentMode

    private val _trackStatus = MutableStateFlow<TrackStatus?>(null)
    val trackStatus: StateFlow<TrackStatus?> = _trackStatus

    private val _playbackPosition = MutableStateFlow<PlaybackPosition?>(null)
    val playbackPosition: StateFlow<PlaybackPosition?> = _playbackPosition

    private val _pauseRemainingMillis = MutableStateFlow<Long?>(null)
    /** Remaining pause milliseconds while a Freeze Dance auto-resume job is pending; null otherwise. */
    val pauseRemainingMillis: StateFlow<Long?> = _pauseRemainingMillis

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build()
        // The system-rendered lock screen/notification Play-Pause control operates on the
        // MediaSession's player, not the notification's own actions. Routing it through
        // stop()/resume() (rather than the wrapped player's raw play()/pause()) keeps every
        // Stop/Resume in sync with SessionEngine, exactly like the in-app button.
        routedPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                resume()
            }

            override fun pause() {
                stop()
            }

            // The raw transport COMMAND_STOP (distinct from our Stop/Resume business logic)
            // must never reach the real ExoPlayer from an external controller — it clears
            // the player down to STATE_IDLE, permanently desyncing it from SessionEngine.
            // COMMAND_STOP is also stripped from available player commands below as
            // defense-in-depth, but override it here too in case any bridge calls it directly.
            override fun stop() = Unit
        }
        mediaSession = MediaSession.Builder(this, routedPlayer)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    // Seek (issue #29) is an in-app-only action, driven through PlaybackService's
                    // own seekBackward()/seekForward() methods rather than the MediaSession's
                    // player commands — strip every command an external controller (lock
                    // screen/Bluetooth/Android Auto) could use to seek within the current item.
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(Player.COMMAND_STOP)
                        .remove(Player.COMMAND_SEEK_TO_NEXT)
                        .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .remove(Player.COMMAND_SEEK_BACK)
                        .remove(Player.COMMAND_SEEK_FORWARD)
                        .remove(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                        .remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                        .build()
                    return MediaSession.ConnectionResult.accept(
                        MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS,
                        playerCommands,
                    )
                }

            })
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action == SERVICE_INTERFACE) return super.onBind(intent)
        return localBinder
    }

    fun startSession(
        playlist: Playlist,
        mode: Mode,
        pauseDurationMillis: Int,
        stopIntervalMinMillis: Int,
        stopIntervalMaxMillis: Int,
    ) {
        adapter?.cancelJobs()
        _currentMode.value = mode
        val engine = SessionEngine(
            playlist = playlist,
            mode = mode,
            stopInterval = StopInterval(stopIntervalMinMillis.toLong(), stopIntervalMaxMillis.toLong()),
            pauseDurationMillis = pauseDurationMillis.toLong(),
        )
        adapter = SessionPlaybackAdapter(
            player = player,
            engine = engine,
            scope = serviceScope,
            onStateChanged = { state ->
                _sessionState.value = state
                updateNotification()
            },
            onTrackChanged = { status -> _trackStatus.value = status },
            onPositionChanged = { position -> _playbackPosition.value = position },
            onPauseRemainingChanged = { remaining -> _pauseRemainingMillis.value = remaining },
        ).also { it.start() }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(mode, _sessionState.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun updateNotification() {
        val mode = _currentMode.value ?: return
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(mode, _sessionState.value))
    }

    private fun buildNotification(mode: Mode, state: SessionState?): Notification {
        val modeLabel = when (mode) {
            Mode.FREEZE_DANCE -> getString(R.string.mode_freeze_dance)
            Mode.MUSICAL_CHAIRS -> getString(R.string.mode_musical_chairs)
        }
        val statusText = when (state) {
            SessionState.Playing -> getString(R.string.notification_session_running, modeLabel)
            SessionState.Stopped -> getString(R.string.notification_session_stopped, modeLabel)
            SessionState.Finished -> getString(R.string.notification_session_finished, modeLabel)
            SessionState.Closed, null -> getString(R.string.notification_session_running, modeLabel)
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession))
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_playback),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    // Guarded against invalid-state calls: SessionEngine.stop()/resume() throw when called
    // outside their expected state (Playing/Stopped). The in-app button never hits this
    // because Compose only renders the button matching the current state, but the system
    // lock-screen/notification Play-Pause control can dispatch a stale/racy command (e.g.
    // rapid double-tap) that the in-app button never could, which previously crashed the
    // whole process.
    fun stop() {
        if (_sessionState.value == SessionState.Playing) {
            adapter?.stop()
        }
    }

    fun resume() {
        if (_sessionState.value == SessionState.Stopped) {
            adapter?.resume()
        }
    }

    /** Jumps to the previous Track in the Playlist; a no-op while Finished/Closed, matching stop()/resume()'s state guard. */
    fun skipToPrevious() {
        if (_sessionState.value == SessionState.Playing || _sessionState.value == SessionState.Stopped) {
            adapter?.skipToPrevious()
        }
    }

    /** Jumps to the next Track in the Playlist; a no-op while Finished/Closed, matching stop()/resume()'s state guard. */
    fun skipToNext() {
        if (_sessionState.value == SessionState.Playing || _sessionState.value == SessionState.Stopped) {
            adapter?.skipToNext()
        }
    }

    /** Jumps position backward within the current Track by a fixed step, clamped at the Track start; a no-op while Finished/Closed. Not exposed to external MediaSession controllers — in-app UI only. */
    fun seekBackward() {
        if (_sessionState.value == SessionState.Playing || _sessionState.value == SessionState.Stopped) {
            adapter?.seekBackward()
        }
    }

    /** Jumps position forward within the current Track by a fixed step, clamped at the Track end; a no-op while Finished/Closed. Not exposed to external MediaSession controllers — in-app UI only. */
    fun seekForward() {
        if (_sessionState.value == SessionState.Playing || _sessionState.value == SessionState.Stopped) {
            adapter?.seekForward()
        }
    }

    /** Live-adjusts the Stop Interval for the in-progress Session; applies from the next Stop cycle onward. */
    fun setStopInterval(minMillis: Int, maxMillis: Int) {
        adapter?.setStopInterval(StopInterval(minMillis.toLong(), maxMillis.toLong()))
    }

    /** Live-adjusts the pause duration for the in-progress Session; applies from the next pause cycle onward. */
    fun setPauseDurationMillis(millis: Int) {
        adapter?.setPauseDurationMillis(millis.toLong())
    }

    /** Session acknowledged Finished (host tapped Done) — tear down and give up the foreground notification. */
    fun acknowledgeFinished() {
        adapter?.cancelJobs()
        endSession()
    }

    /** Host tapped End Session — tears the Session down from any active state and returns to Playlist setup. */
    fun closeSession() {
        if (_sessionState.value == null) return
        adapter?.close()
        endSession()
    }

    private fun endSession() {
        adapter = null
        _currentMode.value = null
        _sessionState.value = null
        _trackStatus.value = null
        _playbackPosition.value = null
        _pauseRemainingMillis.value = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        adapter?.cancelJobs()
        player.release()
        mediaSession.release()
        serviceJob.cancel()
        super.onDestroy()
    }
}
