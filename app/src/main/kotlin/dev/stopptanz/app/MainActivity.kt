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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import dev.stopptanz.app.playlist.PlaylistSaveResult
import dev.stopptanz.app.playlist.PlaylistSelectionState
import dev.stopptanz.app.playlist.ScannedFile
import dev.stopptanz.app.playlist.SelectionKind
import dev.stopptanz.app.playlist.TrackReview
import dev.stopptanz.app.session.PlaybackPosition
import dev.stopptanz.app.session.PlaybackService
import dev.stopptanz.app.session.SessionSettings
import dev.stopptanz.app.settings.SettingsRepository
import dev.stopptanz.app.ui.components.NeonCard
import dev.stopptanz.app.ui.components.NeonCollapsibleCard
import dev.stopptanz.app.ui.components.NeonLabel
import dev.stopptanz.app.ui.components.NeonOutlineButton
import dev.stopptanz.app.ui.components.NeonPrimaryButton
import dev.stopptanz.app.ui.components.NeonSubtext
import dev.stopptanz.app.ui.components.NeonTimerText
import dev.stopptanz.app.ui.components.NeonTitle
import dev.stopptanz.app.ui.components.NeonValue
import dev.stopptanz.app.ui.theme.NeonBackgroundBrush
import dev.stopptanz.app.ui.theme.StopptanzTheme
import dev.stopptanz.engine.Mode
import dev.stopptanz.engine.Playlist
import dev.stopptanz.engine.SessionState
import dev.stopptanz.engine.Track
import dev.stopptanz.engine.TrackRemaining
import dev.stopptanz.engine.TrackStatus
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
    var reviewedPlaylist by remember { mutableStateOf<Playlist?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) {
            state = PlaylistSelectionState.PickerCancelled
        } else {
            reviewedPlaylist = null
            scope.launch { state = playlistRepository.selectFolder(uri) }
        }
    }

    val onPickRawFolderScan: (String) -> Unit = { folderUriString ->
        reviewedPlaylist = null
        scope.launch { state = playlistRepository.chooseRawFolderScan(folderUriString) }
    }
    val onPickPlaylistFile: (String, ScannedFile) -> Unit = { folderUriString, file ->
        reviewedPlaylist = null
        scope.launch { state = playlistRepository.choosePlaylistFile(folderUriString, file) }
    }

    val pickTrack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            state = PlaylistSelectionState.PickerCancelled
        } else {
            reviewedPlaylist = null
            scope.launch { state = playlistRepository.selectTrack(uri) }
        }
    }

    LaunchedEffect(Unit) {
        state = playlistRepository.loadPersistedSelection()
    }

    StopptanzTheme {
        Box(Modifier.fillMaxSize().background(NeonBackgroundBrush)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                NeonTitle(stringResource(R.string.app_name), Modifier.fillMaxWidth().padding(bottom = 4.dp))
                NeonSubtext(state.statusText(), Modifier.padding(bottom = 20.dp))

                val folderChoice = state as? PlaylistSelectionState.FolderChoice
                val selected = state as? PlaylistSelectionState.Selected
                val reviewGatedKinds = setOf(SelectionKind.FOLDER, SelectionKind.PLAYLIST_FILE)
                if (folderChoice != null) {
                    PlaylistFileChoiceScreen(
                        folderName = folderChoice.folderName,
                        playlistFiles = folderChoice.playlistFiles,
                        onPickRawFolderScan = { onPickRawFolderScan(folderChoice.folderUriString) },
                        onPickPlaylistFile = { file -> onPickPlaylistFile(folderChoice.folderUriString, file) },
                    )
                } else if (selected != null) {
                    val needsReview = selected.kind in reviewGatedKinds && reviewedPlaylist == null
                    if (needsReview) {
                        PlaylistReviewScreen(
                            tracks = selected.playlist.tracks,
                            folderUriString = selected.folderUriString,
                            repository = playlistRepository,
                            onConfirm = { edited -> reviewedPlaylist = selected.playlist.copy(tracks = edited.filter { !it.missing }) },
                        )
                    } else {
                        val playlistToShow = if (selected.kind in reviewGatedKinds) requireNotNull(reviewedPlaylist) else selected.playlist
                        SessionSection(
                            playlistToShow,
                            sessionSettings,
                            onPickFolder = { pickFolder.launch(null) },
                            onPickTrack = { pickTrack.launch(arrayOf("audio/*")) },
                        )
                    }
                } else {
                    NeonOutlineButton(stringResource(R.string.pick_music_folder), onClick = { pickFolder.launch(null) }, modifier = Modifier.padding(bottom = 10.dp))
                    NeonOutlineButton(stringResource(R.string.pick_track), onClick = { pickTrack.launch(arrayOf("audio/*")) })
                }
            }
        }
    }
}

@Composable
private fun SessionSection(
    playlist: Playlist,
    sessionSettings: SessionSettings,
    onPickFolder: () -> Unit,
    onPickTrack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pauseDurationMillis by sessionSettings.pauseDurationMillisFlow().collectAsState(initial = 4_000)
    val mode by sessionSettings.modeFlow().collectAsState(initial = Mode.FREEZE_DANCE)
    val stopIntervalMinMillis by sessionSettings.stopIntervalMinMillisFlow().collectAsState(initial = 10_000)
    val stopIntervalMaxMillis by sessionSettings.stopIntervalMaxMillisFlow().collectAsState(initial = 25_000)
    val shuffle by sessionSettings.shuffleFlow().collectAsState(initial = false)
    val loop by sessionSettings.loopFlow().collectAsState(initial = false)

    var sessionState by remember { mutableStateOf<SessionState?>(null) }
    var trackStatus by remember { mutableStateOf<TrackStatus?>(null) }
    var playbackPosition by remember { mutableStateOf<PlaybackPosition?>(null) }
    var pauseRemainingMillis by remember { mutableStateOf<Long?>(null) }
    var boundService by remember { mutableStateOf<PlaybackService?>(null) }
    var serviceConnection by remember { mutableStateOf<ServiceConnection?>(null) }
    var activeSessionMode by remember { mutableStateOf(Mode.FREEZE_DANCE) }
    var showEndSessionConfirmation by remember { mutableStateOf(false) }

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
                scope.launch { service.trackStatus.collect { trackStatus = it } }
                scope.launch { service.playbackPosition.collect { playbackPosition = it } }
                scope.launch { service.pauseRemainingMillis.collect { pauseRemainingMillis = it } }
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
        NeonCard(Modifier.padding(bottom = 14.dp)) {
            NeonLabel(stringResource(R.string.pick_music_folder))
            Spacer(Modifier.height(10.dp))
            NeonOutlineButton(stringResource(R.string.pick_music_folder), onClick = onPickFolder, modifier = Modifier.padding(bottom = 8.dp))
            NeonOutlineButton(stringResource(R.string.pick_track), onClick = onPickTrack)
        }

        Spacer(Modifier.height(8.dp))

        NeonPrimaryButton(stringResource(R.string.button_start_session), onClick@{
            val service = boundService ?: return@onClick
            if (sessionState != null) return@onClick
            activeSessionMode = mode
            val sessionPlaylist = playlist.copy(shuffle = shuffle, loop = loop)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
            service.startSession(sessionPlaylist, mode, pauseDurationMillis, stopIntervalMinMillis, stopIntervalMaxMillis)
        })

        NeonLabel(stringResource(R.string.label_mode, mode.label()), Modifier.fillMaxWidth().padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonOutlineButton(
                stringResource(R.string.mode_freeze_dance),
                onClick = { scope.launch { sessionSettings.setMode(Mode.FREEZE_DANCE) } },
                modifier = Modifier.weight(1f),
                active = mode == Mode.FREEZE_DANCE,
            )
            NeonOutlineButton(
                stringResource(R.string.mode_musical_chairs),
                onClick = { scope.launch { sessionSettings.setMode(Mode.MUSICAL_CHAIRS) } },
                modifier = Modifier.weight(1f),
                active = mode == Mode.MUSICAL_CHAIRS,
            )
        }

        NeonCollapsibleCard(
            summaryText = pauseStopIntervalSummary(pauseDurationMillis, stopIntervalMinMillis, stopIntervalMaxMillis),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            PauseDurationControls(pauseDurationMillis) { millis ->
                scope.launch { sessionSettings.setPauseDurationMillis(millis) }
            }
            Spacer(Modifier.height(14.dp))
            StopIntervalControls(
                stopIntervalMinMillis = stopIntervalMinMillis,
                stopIntervalMaxMillis = stopIntervalMaxMillis,
                onMinChange = { millis -> scope.launch { sessionSettings.setStopIntervalMinMillis(millis) } },
                onMaxChange = { millis -> scope.launch { sessionSettings.setStopIntervalMaxMillis(millis) } },
            )
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NeonOutlineButton(
                stringResource(R.string.label_shuffle, if (shuffle) onState else offState),
                onClick = { scope.launch { sessionSettings.setShuffle(!shuffle) } },
                modifier = Modifier.weight(1f),
                active = shuffle,
            )
            NeonOutlineButton(
                stringResource(R.string.label_loop, if (loop) onState else offState),
                onClick = { scope.launch { sessionSettings.setLoop(!loop) } },
                modifier = Modifier.weight(1f),
                active = loop,
            )
        }

    } else {
        when (sessionState) {
            SessionState.Playing -> NeonPrimaryButton(stringResource(R.string.button_stop), onClick = { activeService.stop() }, modifier = Modifier.padding(top = 8.dp))
            SessionState.Stopped -> {
                val remaining = pauseRemainingMillis
                val resumeLabel = if (activeSessionMode == Mode.FREEZE_DANCE && remaining != null) {
                    val remainingSeconds = ((remaining + 999) / 1000).toInt()
                    stringResource(R.string.button_resume_countdown, remainingSeconds)
                } else {
                    stringResource(R.string.button_resume)
                }
                NeonPrimaryButton(resumeLabel, onClick = { activeService.resume() }, modifier = Modifier.padding(top = 8.dp))
            }
            SessionState.Finished -> {
                NeonLabel(stringResource(R.string.label_finished), Modifier.padding(vertical = 4.dp))
                NeonPrimaryButton(stringResource(R.string.button_done), onClick = { activeService.acknowledgeFinished() })
            }
            is SessionState.Paused -> Unit
            SessionState.Closed, null -> Unit
        }

        if (sessionState == SessionState.Playing || sessionState == SessionState.Stopped) {
            NeonOutlineButton(
                stringResource(R.string.button_pause_session),
                onClick = { activeService.pause() },
                modifier = Modifier.padding(top = 8.dp),
            )
        } else if (sessionState is SessionState.Paused) {
            NeonPrimaryButton(
                stringResource(R.string.button_resume_from_pause),
                onClick = { activeService.resumeFromPause() },
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        NeonOutlineButton(
            stringResource(R.string.button_end_session),
            onClick = {
                if (sessionState == SessionState.Playing) {
                    showEndSessionConfirmation = true
                } else {
                    activeService.closeSession()
                }
            },
            modifier = Modifier.padding(top = 16.dp),
        )

        if (showEndSessionConfirmation) {
            AlertDialog(
                onDismissRequest = { showEndSessionConfirmation = false },
                title = { Text(stringResource(R.string.dialog_end_session_title)) },
                text = { Text(stringResource(R.string.dialog_end_session_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showEndSessionConfirmation = false
                        activeService.closeSession()
                    }) { Text(stringResource(R.string.dialog_end_session_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showEndSessionConfirmation = false }) {
                        Text(stringResource(R.string.dialog_end_session_cancel))
                    }
                },
            )
        }

        if (sessionState == SessionState.Playing || sessionState == SessionState.Stopped || sessionState is SessionState.Paused) {
            NeonCollapsibleCard(
                summaryText = pauseStopIntervalSummary(pauseDurationMillis, stopIntervalMinMillis, stopIntervalMaxMillis),
                modifier = Modifier.padding(vertical = 14.dp),
            ) {
                PauseDurationControls(pauseDurationMillis) { millis ->
                    scope.launch { sessionSettings.setPauseDurationMillis(millis) }
                    activeService.setPauseDurationMillis(millis)
                }
                Spacer(Modifier.height(14.dp))
                StopIntervalControls(
                    stopIntervalMinMillis = stopIntervalMinMillis,
                    stopIntervalMaxMillis = stopIntervalMaxMillis,
                    onMinChange = { millis ->
                        scope.launch { sessionSettings.setStopIntervalMinMillis(millis) }
                        activeService.setStopInterval(millis, stopIntervalMaxMillis)
                    },
                    onMaxChange = { millis ->
                        scope.launch { sessionSettings.setStopIntervalMaxMillis(millis) }
                        activeService.setStopInterval(stopIntervalMinMillis, millis)
                    },
                )
            }
        }

        if (sessionState == SessionState.Playing || sessionState == SessionState.Stopped || sessionState is SessionState.Paused) {
            trackStatus?.let {
                TrackStatusDisplay(it, playbackPosition)
                SkipControls(
                    it,
                    onSkipPrevious = { activeService.skipToPrevious() },
                    onSeekBackward = { activeService.seekBackward() },
                    onSeekForward = { activeService.seekForward() },
                    onSkipNext = { activeService.skipToNext() },
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            serviceConnection?.let { context.unbindService(it) }
        }
    }
}

@Composable
private fun PlaylistFileChoiceScreen(
    folderName: String,
    playlistFiles: List<ScannedFile>,
    onPickRawFolderScan: () -> Unit,
    onPickPlaylistFile: (ScannedFile) -> Unit,
) {
    NeonCard(Modifier.padding(bottom = 14.dp)) {
        NeonLabel(stringResource(R.string.label_choose_playlist_source, folderName), Modifier.padding(bottom = 8.dp))
        NeonOutlineButton(
            stringResource(R.string.button_raw_folder_scan),
            onClick = onPickRawFolderScan,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        playlistFiles.forEach { file ->
            NeonOutlineButton(file.displayName, onClick = { onPickPlaylistFile(file) }, modifier = Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun PlaylistReviewScreen(
    tracks: List<Track>,
    folderUriString: String?,
    repository: PlaylistRepository,
    onConfirm: (List<Track>) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editedTracks by remember(tracks) { mutableStateOf(tracks) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var filenameInput by remember { mutableStateOf("") }
    var pendingOverwriteFilename by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }

    NeonCard(Modifier.padding(bottom = 14.dp)) {
        NeonLabel(stringResource(R.string.label_review_tracks), Modifier.padding(bottom = 8.dp))
        editedTracks.forEachIndexed { index, track ->
            TrackReviewRow(
                name = track.name,
                missing = track.missing,
                canMoveUp = !track.missing && index > 0,
                canMoveDown = !track.missing && index < editedTracks.lastIndex,
                onMoveUp = { editedTracks = TrackReview.moveUp(editedTracks, index) },
                onMoveDown = { editedTracks = TrackReview.moveDown(editedTracks, index) },
                onRemove = { editedTracks = TrackReview.remove(editedTracks, index) },
            )
        }
    }

    NeonPrimaryButton(
        stringResource(R.string.button_review_continue),
        onClick = { onConfirm(editedTracks) },
        enabled = editedTracks.any { !it.missing },
        modifier = Modifier.padding(bottom = 8.dp),
    )

    val folderUri = folderUriString
    if (folderUri != null && editedTracks.any { !it.missing }) {
        NeonOutlineButton(
            stringResource(R.string.button_save_playlist_file),
            onClick = {
                filenameInput = ""
                saveMessage = null
                showSaveDialog = true
            },
        )
        saveMessage?.let { NeonSubtext(it, Modifier.padding(top = 8.dp)) }

        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text(stringResource(R.string.dialog_save_playlist_title)) },
                text = {
                    TextField(
                        value = filenameInput,
                        onValueChange = { filenameInput = it },
                        label = { Text(stringResource(R.string.dialog_save_playlist_filename_label)) },
                    )
                },
                confirmButton = {
                    TextButton(
                        enabled = filenameInput.isNotBlank(),
                        onClick = {
                            showSaveDialog = false
                            val toSave = editedTracks.filter { !it.missing }
                            val name = filenameInput
                            scope.launch {
                                when (val result = repository.savePlaylistFile(folderUri, name, toSave, overwrite = false)) {
                                    is PlaylistSaveResult.Saved ->
                                        saveMessage = context.getString(R.string.status_save_playlist_success, result.filename)
                                    is PlaylistSaveResult.AlreadyExists -> pendingOverwriteFilename = result.filename
                                    PlaylistSaveResult.Failed ->
                                        saveMessage = context.getString(R.string.status_save_playlist_failed)
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.dialog_save_playlist_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) { Text(stringResource(R.string.dialog_save_playlist_cancel)) }
                },
            )
        }

        val overwriteFilename = pendingOverwriteFilename
        if (overwriteFilename != null) {
            AlertDialog(
                onDismissRequest = { pendingOverwriteFilename = null },
                title = { Text(stringResource(R.string.dialog_overwrite_title)) },
                text = { Text(stringResource(R.string.dialog_overwrite_message, overwriteFilename)) },
                confirmButton = {
                    TextButton(onClick = {
                        pendingOverwriteFilename = null
                        val toSave = editedTracks.filter { !it.missing }
                        scope.launch {
                            saveMessage = when (val result = repository.savePlaylistFile(folderUri, overwriteFilename, toSave, overwrite = true)) {
                                is PlaylistSaveResult.Saved -> context.getString(R.string.status_save_playlist_success, result.filename)
                                else -> context.getString(R.string.status_save_playlist_failed)
                            }
                        }
                    }) { Text(stringResource(R.string.dialog_overwrite_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { pendingOverwriteFilename = null }) { Text(stringResource(R.string.dialog_overwrite_cancel)) }
                },
            )
        }
    }
}

@Composable
private fun TrackReviewRow(
    name: String,
    missing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (missing) {
            NeonSubtext(stringResource(R.string.label_missing_track, name), Modifier.weight(1f))
        } else {
            NeonValue(name, Modifier.weight(1f))
        }
        if (!missing) {
            TextButton(onClick = onMoveUp, enabled = canMoveUp) { Text(stringResource(R.string.button_move_up)) }
            TextButton(onClick = onMoveDown, enabled = canMoveDown) { Text(stringResource(R.string.button_move_down)) }
        }
        TextButton(onClick = onRemove) { Text(stringResource(R.string.button_remove_track)) }
    }
}

@Composable
private fun TrackStatusDisplay(trackStatus: TrackStatus, playbackPosition: PlaybackPosition?) {
    NeonCard {
        NeonLabel(stringResource(R.string.label_now_playing))
        NeonValue(trackStatus.current.name, Modifier.padding(bottom = 8.dp))
        playbackPosition?.let {
            NeonTimerText(it.formatCurrent(), Modifier.fillMaxWidth())
            NeonSubtext(
                stringResource(R.string.label_track_duration, it.formatCurrent(), it.formatTotal()),
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        NeonLabel(stringResource(R.string.label_next_track_title))
        NeonValue(
            trackStatus.next?.name ?: stringResource(R.string.label_next_track_placeholder),
            Modifier.padding(bottom = 8.dp),
        )
        NeonSubtext(trackStatus.remaining.displayText())
    }
}

@Composable
private fun SkipControls(
    trackStatus: TrackStatus,
    onSkipPrevious: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSkipNext: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonOutlineButton(
            stringResource(R.string.button_skip_previous),
            onClick = onSkipPrevious,
            modifier = Modifier.weight(1f),
            enabled = trackStatus.canSkipPrevious,
        )
        NeonOutlineButton(
            stringResource(R.string.button_seek_backward),
            onClick = onSeekBackward,
            modifier = Modifier.weight(1f),
        )
        NeonOutlineButton(
            stringResource(R.string.button_seek_forward),
            onClick = onSeekForward,
            modifier = Modifier.weight(1f),
        )
        NeonOutlineButton(
            stringResource(R.string.button_skip_next),
            onClick = onSkipNext,
            modifier = Modifier.weight(1f),
            enabled = trackStatus.canSkipNext,
        )
    }
}

@Composable
private fun TrackRemaining.displayText(): String = when (this) {
    is TrackRemaining.Position -> stringResource(R.string.label_track_position, current, total)
    is TrackRemaining.Countdown -> pluralStringResource(R.plurals.label_tracks_remaining, remaining, remaining)
}

@Composable
private fun pauseStopIntervalSummary(pauseDurationMillis: Int, stopIntervalMinMillis: Int, stopIntervalMaxMillis: Int): String =
    stringResource(
        R.string.label_pause_stop_interval_summary,
        pauseDurationMillis / 1000,
        stopIntervalMinMillis / 1000,
        stopIntervalMaxMillis / 1000,
    )

@Composable
private fun PauseDurationControls(pauseDurationMillis: Int, onChange: (Int) -> Unit) {
    NeonLabel(stringResource(R.string.label_pause, pauseDurationMillis / 1000), Modifier.padding(bottom = 8.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonOutlineButton(stringResource(R.string.button_decrement_1s), onClick = { onChange((pauseDurationMillis - 1_000).coerceIn(1_000, 30_000)) }, modifier = Modifier.weight(1f))
        NeonOutlineButton(stringResource(R.string.button_increment_1s), onClick = { onChange((pauseDurationMillis + 1_000).coerceIn(1_000, 30_000)) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StopIntervalControls(
    stopIntervalMinMillis: Int,
    stopIntervalMaxMillis: Int,
    onMinChange: (Int) -> Unit,
    onMaxChange: (Int) -> Unit,
) {
    NeonLabel(
        stringResource(R.string.label_stop_interval, stopIntervalMinMillis / 1000, stopIntervalMaxMillis / 1000),
        Modifier.padding(bottom = 8.dp),
    )
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonOutlineButton(stringResource(R.string.button_min_decrement), onClick = { onMinChange((stopIntervalMinMillis - 1_000).coerceIn(1_000, stopIntervalMaxMillis - 1_000)) }, modifier = Modifier.weight(1f))
        NeonOutlineButton(stringResource(R.string.button_min_increment), onClick = { onMinChange((stopIntervalMinMillis + 1_000).coerceIn(1_000, stopIntervalMaxMillis - 1_000)) }, modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        NeonOutlineButton(stringResource(R.string.button_max_decrement), onClick = { onMaxChange((stopIntervalMaxMillis - 1_000).coerceIn(stopIntervalMinMillis + 1_000, 60_000)) }, modifier = Modifier.weight(1f))
        NeonOutlineButton(stringResource(R.string.button_max_increment), onClick = { onMaxChange((stopIntervalMaxMillis + 1_000).coerceIn(stopIntervalMinMillis + 1_000, 60_000)) }, modifier = Modifier.weight(1f))
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
    PlaylistSelectionState.NotSelected -> stringResource(R.string.status_no_selection)
    PlaylistSelectionState.PickerCancelled -> stringResource(R.string.status_access_needed)
    is PlaylistSelectionState.PermissionUnavailable -> if (displayName != null) {
        when (kind) {
            SelectionKind.FOLDER -> stringResource(R.string.status_folder_inaccessible, displayName)
            SelectionKind.TRACK -> stringResource(R.string.status_track_inaccessible, displayName)
            SelectionKind.PLAYLIST_FILE -> stringResource(R.string.status_folder_inaccessible, displayName)
        }
    } else {
        stringResource(R.string.status_access_needed)
    }
    is PlaylistSelectionState.Empty -> when (kind) {
        SelectionKind.FOLDER -> stringResource(R.string.status_folder_empty, displayName)
        SelectionKind.TRACK -> stringResource(R.string.status_track_empty, displayName)
        SelectionKind.PLAYLIST_FILE -> stringResource(R.string.status_playlist_file_empty, displayName)
    }
    is PlaylistSelectionState.Selected -> when (kind) {
        SelectionKind.FOLDER -> pluralStringResource(
            R.plurals.status_folder_selected,
            playlist.tracks.size,
            displayName,
            playlist.tracks.size,
        )
        SelectionKind.TRACK -> stringResource(R.string.status_track_selected, displayName)
        SelectionKind.PLAYLIST_FILE -> pluralStringResource(
            R.plurals.status_playlist_file_selected,
            playlist.tracks.size,
            displayName,
            playlist.tracks.size,
        )
    }
    is PlaylistSelectionState.FolderChoice -> stringResource(R.string.status_choose_playlist_source, folderName)
}
