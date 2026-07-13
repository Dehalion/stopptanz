package dev.stopptanz.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import dev.stopptanz.app.playlist.PlaylistRepository
import dev.stopptanz.app.playlist.PlaylistSelectionState
import dev.stopptanz.app.session.PlaybackService
import dev.stopptanz.app.session.SessionSettings
import dev.stopptanz.app.settings.SettingsRepository
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.Playlist
import dev.stopptanz.engine.SessionState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        val playlistRepository = PlaylistRepository(applicationContext, settings)
        val sessionSettings = SessionSettings(settings)
        setContent {
            StopptanzApp(playlistRepository, sessionSettings)
        }
    }
}

@Composable
fun StopptanzApp(playlistRepository: PlaylistRepository, sessionSettings: SessionSettings) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PlaylistSelectionState>(PlaylistSelectionState.Loading) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            state = PlaylistSelectionState.PickerCancelled
        } else {
            scope.launch { state = playlistRepository.selectFolder(uri) }
        }
    }

    LaunchedEffect(Unit) {
        state = playlistRepository.loadPersistedSelection()
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Stopptanz")
                    Text(state.statusText(), Modifier.padding(vertical = 8.dp))
                    Button(onClick = { pickFolder.launch(null) }) {
                        Text("Pick music folder")
                    }

                    val selected = state as? PlaylistSelectionState.Selected
                    if (selected != null) {
                        SessionSection(selected.playlist, sessionSettings)
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionSection(playlist: Playlist, sessionSettings: SessionSettings) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pauseDurationMillis by sessionSettings.pauseDurationMillisFlow().collectAsState(initial = 5_000)
    val mode by sessionSettings.modeFlow().collectAsState(initial = Mode.FREEZE_DANCE)
    val stopIntervalMinMillis by sessionSettings.stopIntervalMinMillisFlow().collectAsState(initial = 5_000)
    val stopIntervalMaxMillis by sessionSettings.stopIntervalMaxMillisFlow().collectAsState(initial = 15_000)
    val shuffle by sessionSettings.shuffleFlow().collectAsState(initial = false)
    val loop by sessionSettings.loopFlow().collectAsState(initial = false)

    var sessionState by remember { mutableStateOf<SessionState?>(null) }
    var boundService by remember { mutableStateOf<PlaybackService?>(null) }
    var serviceConnection by remember { mutableStateOf<ServiceConnection?>(null) }
    var activeSessionMode by remember { mutableStateOf(Mode.FREEZE_DANCE) }

    val requestNotificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: absence just means no visible notification, FGS still runs */ }

    val activeService = boundService
    if (activeService == null || sessionState == null) {
        Text("Mode: ${mode.label()}", Modifier.padding(vertical = 4.dp))
        Button(onClick = { scope.launch { sessionSettings.setMode(Mode.FREEZE_DANCE) } }) { Text("Freeze Dance") }
        Button(onClick = { scope.launch { sessionSettings.setMode(Mode.MUSICAL_CHAIRS) } }) { Text("Musical Chairs") }

        Text("Pause: ${pauseDurationMillis / 1000}s", Modifier.padding(vertical = 4.dp))
        Button(onClick = {
            scope.launch { sessionSettings.setPauseDurationMillis((pauseDurationMillis - 1_000).coerceIn(1_000, 30_000)) }
        }) { Text("-1s") }
        Button(onClick = {
            scope.launch { sessionSettings.setPauseDurationMillis((pauseDurationMillis + 1_000).coerceIn(1_000, 30_000)) }
        }) { Text("+1s") }

        Text("Stop Interval: ${stopIntervalMinMillis / 1000}s–${stopIntervalMaxMillis / 1000}s", Modifier.padding(vertical = 4.dp))
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMinMillis((stopIntervalMinMillis - 1_000).coerceIn(1_000, stopIntervalMaxMillis - 1_000))
            }
        }) { Text("Min -1s") }
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMinMillis((stopIntervalMinMillis + 1_000).coerceIn(1_000, stopIntervalMaxMillis - 1_000))
            }
        }) { Text("Min +1s") }
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMaxMillis((stopIntervalMaxMillis - 1_000).coerceIn(stopIntervalMinMillis + 1_000, 60_000))
            }
        }) { Text("Max -1s") }
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMaxMillis((stopIntervalMaxMillis + 1_000).coerceIn(stopIntervalMinMillis + 1_000, 60_000))
            }
        }) { Text("Max +1s") }

        Text("Shuffle: ${if (shuffle) "On" else "Off"}", Modifier.padding(vertical = 4.dp))
        Button(onClick = { scope.launch { sessionSettings.setShuffle(!shuffle) } }) { Text("Toggle Shuffle") }

        Text("Loop: ${if (loop) "On" else "Off"}", Modifier.padding(vertical = 4.dp))
        Button(onClick = { scope.launch { sessionSettings.setLoop(!loop) } }) { Text("Toggle Loop") }

        Button(onClick = onClick@{
            if (serviceConnection != null) return@onClick
            activeSessionMode = mode
            val sessionPlaylist = playlist.copy(shuffle = shuffle, loop = loop)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    val service = (binder as PlaybackService.LocalBinder).service
                    boundService = service
                    service.startSession(sessionPlaylist, mode, pauseDurationMillis, stopIntervalMinMillis, stopIntervalMaxMillis)
                    scope.launch {
                        service.sessionState.collect { sessionState = it }
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    boundService = null
                    serviceConnection = null
                }
            }
            serviceConnection = connection
            val intent = Intent(context, PlaybackService::class.java)
            ContextCompat.startForegroundService(context, intent)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }) {
            Text("Start Session")
        }
    } else {
        when (sessionState) {
            SessionState.Playing -> Button(onClick = { activeService.stop() }) { Text("Stop") }
            SessionState.Stopped -> if (activeSessionMode == Mode.MUSICAL_CHAIRS) {
                Button(onClick = { activeService.resume() }) { Text("Resume") }
            } else {
                Text("Stopped — resuming automatically…")
            }
            SessionState.Finished -> {
                Text("Finished", Modifier.padding(vertical = 4.dp))
                Button(onClick = {
                    activeService.acknowledgeFinished()
                    serviceConnection?.let { context.unbindService(it) }
                    boundService = null
                    serviceConnection = null
                }) { Text("Done") }
            }
            null -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            serviceConnection?.let { context.unbindService(it) }
        }
    }
}

private fun Mode.label(): String = when (this) {
    Mode.FREEZE_DANCE -> "Freeze Dance"
    Mode.MUSICAL_CHAIRS -> "Musical Chairs"
}

private fun PlaylistSelectionState.statusText(): String = when (this) {
    PlaylistSelectionState.Loading -> "Loading…"
    PlaylistSelectionState.NotSelected -> "No folder selected yet."
    PlaylistSelectionState.PickerCancelled -> "Folder access is needed to pick music. Please try again."
    is PlaylistSelectionState.PermissionUnavailable -> if (folderName != null) {
        "Folder \"$folderName\" is no longer accessible. Please pick it again."
    } else {
        "Folder access is needed to pick music. Please try again."
    }
    is PlaylistSelectionState.Empty -> "Folder \"$folderName\" has no audio files."
    is PlaylistSelectionState.Selected -> "Folder: $folderName (${playlist.tracks.size} tracks)"
}
