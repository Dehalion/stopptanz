package dev.stopptanz.app

import android.net.Uri
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.stopptanz.app.playlist.PlaylistRepository
import dev.stopptanz.app.playlist.PlaylistSelectionState
import dev.stopptanz.app.settings.SettingsRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = SettingsRepository(applicationContext)
        val playlistRepository = PlaylistRepository(applicationContext, settings)
        setContent {
            StopptanzApp(playlistRepository)
        }
    }
}

@Composable
fun StopptanzApp(playlistRepository: PlaylistRepository) {
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<PlaylistSelectionState>(PlaylistSelectionState.Loading) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
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
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Stopptanz")
                    Text(
                        text = state.statusText(),
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Button(onClick = { pickFolder.launch(null) }) {
                        Text("Pick music folder")
                    }
                }
            }
        }
    }
}

private fun PlaylistSelectionState.statusText(): String = when (this) {
    PlaylistSelectionState.Loading -> "Loading…"
    PlaylistSelectionState.NotSelected -> "No folder selected yet."
    PlaylistSelectionState.PickerCancelled -> "Folder access is needed to pick music. Please try again."
    is PlaylistSelectionState.PermissionUnavailable -> if (folderName != null) {
        "Folder \"$folderName\" is no longer accessible. Please pick it again."
    } else {
        "Couldn't get access to that folder. Please try again."
    }
    is PlaylistSelectionState.Empty -> "Folder \"$folderName\" has no audio files."
    is PlaylistSelectionState.Selected -> "Folder: $folderName (${playlist.tracks.size} tracks)"
}
