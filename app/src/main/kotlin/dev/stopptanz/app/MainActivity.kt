package dev.stopptanz.app

import android.content.Context
import android.os.Bundle
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
import androidx.media3.exoplayer.ExoPlayer
import dev.stopptanz.app.playlist.PlaylistRepository
import dev.stopptanz.app.playlist.PlaylistSelectionState
import dev.stopptanz.app.session.SessionPlaybackAdapter
import dev.stopptanz.app.session.SessionSettings
import dev.stopptanz.app.settings.SettingsRepository
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.Playlist
import dev.stopptanz.engine.SessionEngine
import dev.stopptanz.engine.SessionState
import dev.stopptanz.engine.StopInterval
import kotlinx.coroutines.CoroutineScope
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

    var sessionState by remember { mutableStateOf<SessionState>(SessionState.Playing) }
    var adapter by remember { mutableStateOf<SessionPlaybackAdapter?>(null) }
    var activeSessionMode by remember { mutableStateOf(Mode.FREEZE_DANCE) }

    val activeAdapter = adapter
    if (activeAdapter == null) {
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

        Button(onClick = {
            activeSessionMode = mode
            adapter = startSession(context, scope, playlist, mode, pauseDurationMillis) { sessionState = it }
        }) {
            Text("Start Session")
        }
    } else {
        when (sessionState) {
            SessionState.Playing -> Button(onClick = { activeAdapter.stop() }) { Text("Stop") }
            SessionState.Stopped -> if (activeSessionMode == Mode.MUSICAL_CHAIRS) {
                Button(onClick = { activeAdapter.resume() }) { Text("Resume") }
            } else {
                Text("Stopped — resuming automatically…")
            }
            SessionState.Finished -> Text("Finished")
        }
    }

    DisposableEffect(Unit) {
        onDispose { adapter?.release() }
    }
}

/** Auto-timer (#8) isn't wired up yet, so Stop Interval is unused. */
private fun startSession(
    context: Context,
    scope: CoroutineScope,
    playlist: Playlist,
    mode: Mode,
    pauseDurationMillis: Int,
    onStateChanged: (SessionState) -> Unit,
): SessionPlaybackAdapter {
    val engine = SessionEngine(
        playlist = playlist,
        mode = mode,
        stopInterval = StopInterval.unused,
        pauseDurationMillis = pauseDurationMillis.toLong(),
    )
    val adapter = SessionPlaybackAdapter(
        player = ExoPlayer.Builder(context).build(),
        engine = engine,
        scope = scope,
        onStateChanged = onStateChanged,
    )
    adapter.start(playlist)
    onStateChanged(engine.state)
    return adapter
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
