package dev.stopptanz.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.Playlist
import dev.stopptanz.engine.SessionEngine
import dev.stopptanz.engine.SessionState
import dev.stopptanz.engine.StopInterval
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
    private lateinit var mediaSession: MediaSession
    private var adapter: SessionPlaybackAdapter? = null

    private val _sessionState = MutableStateFlow<SessionState?>(null)
    val sessionState: StateFlow<SessionState?> = _sessionState

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build()
        mediaSession = MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                // Interactive transport controls are #11's job (MediaSession lock-screen
                // controls); a default Play/Pause here would bypass SessionEngine and
                // desync engine state from the player.
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                ): MediaSession.ConnectionResult {
                    val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                        .remove(Player.COMMAND_PLAY_PAUSE)
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
            onStateChanged = { _sessionState.value = it },
        ).also { it.start() }

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(mode),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )
    }

    private fun buildNotification(mode: Mode): Notification {
        val modeLabel = when (mode) {
            Mode.FREEZE_DANCE -> "Freeze Dance"
            Mode.MUSICAL_CHAIRS -> "Musical Chairs"
        }
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Stopptanz")
            .setContentText("$modeLabel Session running")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    fun stop() {
        adapter?.stop()
    }

    fun resume() {
        adapter?.resume()
    }

    /** Session acknowledged Finished (host tapped Done) — tear down and give up the foreground notification. */
    fun acknowledgeFinished() {
        adapter?.cancelJobs()
        adapter = null
        _sessionState.value = null
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
