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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.app_name))
                    Text(state.statusText(), Modifier.padding(vertical = 8.dp))
                    Button(onClick = { pickFolder.launch(null) }) {
                        Text(stringResource(R.string.pick_music_folder))
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

    // Bind once for the Activity's whole lifetime so PlaybackService — not this Compose
    // tree — stays the source of truth for Session state across rotation/recreation.
    LaunchedEffect(Unit) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                val service = (binder as PlaybackService.LocalBinder).service
                boundService = service
                scope.launch { service.sessionState.collect { sessionState = it } }
                scope.launch { service.currentMode.collect { it?.let { mode -> activeSessionMode = mode } } }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                boundService = null
            }
        }
        serviceConnection = connection
        // Explicitly started (not just bound) so the service outlives the brief unbind/rebind
        // gap during Activity recreation instead of being torn down when bindings hit zero.
        val intent = Intent(context, PlaybackService::class.java)
        context.startService(intent)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    val onState = stringResource(R.string.toggle_state_on)
    val offState = stringResource(R.string.toggle_state_off)

    val activeService = boundService
    if (activeService == null || sessionState == null) {
        Text(stringResource(R.string.label_mode, mode.label()), Modifier.padding(vertical = 4.dp))
        Button(onClick = { scope.launch { sessionSettings.setMode(Mode.FREEZE_DANCE) } }) { Text(stringResource(R.string.mode_freeze_dance)) }
        Button(onClick = { scope.launch { sessionSettings.setMode(Mode.MUSICAL_CHAIRS) } }) { Text(stringResource(R.string.mode_musical_chairs)) }

        Text(stringResource(R.string.label_pause, pauseDurationMillis / 1000), Modifier.padding(vertical = 4.dp))
        Button(onClick = {
            scope.launch { sessionSettings.setPauseDurationMillis((pauseDurationMillis - 1_000).coerceIn(1_000, 30_000)) }
        }) { Text(stringResource(R.string.button_decrement_1s)) }
        Button(onClick = {
            scope.launch { sessionSettings.setPauseDurationMillis((pauseDurationMillis + 1_000).coerceIn(1_000, 30_000)) }
        }) { Text(stringResource(R.string.button_increment_1s)) }

        Text(
            stringResource(R.string.label_stop_interval, stopIntervalMinMillis / 1000, stopIntervalMaxMillis / 1000),
            Modifier.padding(vertical = 4.dp),
        )
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMinMillis((stopIntervalMinMillis - 1_000).coerceIn(1_000, stopIntervalMaxMillis - 1_000))
            }
        }) { Text(stringResource(R.string.button_min_decrement)) }
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMinMillis((stopIntervalMinMillis + 1_000).coerceIn(1_000, stopIntervalMaxMillis - 1_000))
            }
        }) { Text(stringResource(R.string.button_min_increment)) }
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMaxMillis((stopIntervalMaxMillis - 1_000).coerceIn(stopIntervalMinMillis + 1_000, 60_000))
            }
        }) { Text(stringResource(R.string.button_max_decrement)) }
        Button(onClick = {
            scope.launch {
                sessionSettings.setStopIntervalMaxMillis((stopIntervalMaxMillis + 1_000).coerceIn(stopIntervalMinMillis + 1_000, 60_000))
            }
        }) { Text(stringResource(R.string.button_max_increment)) }

        Text(stringResource(R.string.label_shuffle, if (shuffle) onState else offState), Modifier.padding(vertical = 4.dp))
        Button(onClick = { scope.launch { sessionSettings.setShuffle(!shuffle) } }) { Text(stringResource(R.string.button_toggle_shuffle)) }

        Text(stringResource(R.string.label_loop, if (loop) onState else offState), Modifier.padding(vertical = 4.dp))
        Button(onClick = { scope.launch { sessionSettings.setLoop(!loop) } }) { Text(stringResource(R.string.button_toggle_loop)) }

        Button(onClick = onClick@{
            val service = boundService ?: return@onClick
            if (sessionState != null) return@onClick
            activeSessionMode = mode
            val sessionPlaylist = playlist.copy(shuffle = shuffle, loop = loop)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            service.startSession(sessionPlaylist, mode, pauseDurationMillis, stopIntervalMinMillis, stopIntervalMaxMillis)
        }) {
            Text(stringResource(R.string.button_start_session))
        }
    } else {
        when (sessionState) {
            SessionState.Playing -> Button(onClick = { activeService.stop() }) { Text(stringResource(R.string.button_stop)) }
            SessionState.Stopped -> if (activeSessionMode == Mode.MUSICAL_CHAIRS) {
                Button(onClick = { activeService.resume() }) { Text(stringResource(R.string.button_resume)) }
            } else {
                Text(stringResource(R.string.status_stopped_resuming))
            }
            SessionState.Finished -> {
                Text(stringResource(R.string.label_finished), Modifier.padding(vertical = 4.dp))
                Button(onClick = { activeService.acknowledgeFinished() }) { Text(stringResource(R.string.button_done)) }
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

@Composable
private fun Mode.label(): String = when (this) {
    Mode.FREEZE_DANCE -> stringResource(R.string.mode_freeze_dance)
    Mode.MUSICAL_CHAIRS -> stringResource(R.string.mode_musical_chairs)
}

@Composable
private fun PlaylistSelectionState.statusText(): String = when (this) {
    PlaylistSelectionState.Loading -> stringResource(R.string.status_loading)
    PlaylistSelectionState.NotSelected -> stringResource(R.string.status_no_folder_selected)
    PlaylistSelectionState.PickerCancelled -> stringResource(R.string.status_folder_access_needed)
    is PlaylistSelectionState.PermissionUnavailable -> if (folderName != null) {
        stringResource(R.string.status_folder_inaccessible, folderName)
    } else {
        stringResource(R.string.status_folder_access_needed)
    }
    is PlaylistSelectionState.Empty -> stringResource(R.string.status_folder_empty, folderName)
    is PlaylistSelectionState.Selected -> pluralStringResource(
        R.plurals.status_folder_selected,
        playlist.tracks.size,
        folderName,
        playlist.tracks.size,
    )
}
