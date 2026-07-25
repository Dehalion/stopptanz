import { useState } from 'preact/hooks'
import { buildPlaylist } from './playlist/playlistBuilder'
import { moveDown, moveUp, remove } from './playlist/trackReview'
import { formatCurrent, formatTotal } from './engine/playbackPosition'
import type { Mode } from './engine/mode'
import type { SessionState } from './engine/sessionState'
import { createStopInterval } from './engine/stopInterval'
import { usePlaylistPlayer } from './playback/usePlaylistPlayer'
import type { Track } from './engine/track'
import {
  loadSessionSettings,
  saveLoop,
  saveMode,
  saveShuffle,
  saveStopIntervalMaxSeconds,
  saveStopIntervalMinSeconds,
} from './settings/sessionSettings'

const STATE_LABEL: Record<SessionState['kind'], string> = {
  playing: 'Playing',
  stopped: 'Freeze!',
  paused: 'Paused',
  finished: 'Finished',
  closed: 'Closed',
}

const MODE_LABEL: Record<Mode, string> = {
  FREEZE_DANCE: 'Freeze Dance',
  MUSICAL_CHAIRS: 'Musical Chairs',
}

export function App() {
  const player = usePlaylistPlayer()
  const initialSettings = loadSessionSettings()
  const [shuffle, setShuffle] = useState(initialSettings.shuffle)
  const [loop, setLoop] = useState(initialSettings.loop)
  const [mode, setMode] = useState<Mode>(initialSettings.mode)
  const [stopMinSeconds, setStopMinSeconds] = useState(initialSettings.stopIntervalMinSeconds)
  const [stopMaxSeconds, setStopMaxSeconds] = useState(initialSettings.stopIntervalMaxSeconds)
  const [pauseDurationSeconds, setPauseDurationSeconds] = useState(4)
  const [reviewTracks, setReviewTracks] = useState<Track[] | null>(null)
  const [settingsExpanded, setSettingsExpanded] = useState(false)
  const [showEndConfirm, setShowEndConfirm] = useState(false)

  const state = player.sessionState
  const sessionActive = state !== null

  function onFilesSelected(e: Event) {
    const files = Array.from((e.target as HTMLInputElement).files ?? [])
    const playlist = buildPlaylist(files, (file) => URL.createObjectURL(file))
    setReviewTracks(playlist?.tracks ?? null)
  }

  function onModeChange(newMode: Mode) {
    setMode(newMode)
    saveMode(newMode)
    const settingsForMode = loadSessionSettings()
    setStopMinSeconds(settingsForMode.stopIntervalMinSeconds)
    setStopMaxSeconds(settingsForMode.stopIntervalMaxSeconds)
  }

  function onShuffleChange(value: boolean) {
    setShuffle(value)
    saveShuffle(value)
  }

  function onLoopChange(value: boolean) {
    setLoop(value)
    saveLoop(value)
    if (sessionActive) player.setLoop(value)
  }

  const stopIntervalValid = stopMinSeconds >= 0 && stopMaxSeconds >= stopMinSeconds

  function onStopMinChange(value: number) {
    setStopMinSeconds(value)
    saveStopIntervalMinSeconds(mode, value)
    applyStopIntervalIfValid(value, stopMaxSeconds)
  }

  function onStopMaxChange(value: number) {
    setStopMaxSeconds(value)
    saveStopIntervalMaxSeconds(mode, value)
    applyStopIntervalIfValid(stopMinSeconds, value)
  }

  /** Stop Interval changes apply to the running Session immediately, matching Android; Mode and
   * Shuffle only take effect on the next Session since they're baked into the SessionEngine/Playlist
   * at start() — unlike Loop, which is read live and so can also apply immediately. */
  function applyStopIntervalIfValid(minSeconds: number, maxSeconds: number) {
    if (minSeconds >= 0 && maxSeconds >= minSeconds && sessionActive) {
      player.setStopInterval(createStopInterval(minSeconds * 1000, maxSeconds * 1000))
    }
  }

  function startPlayback() {
    if (!reviewTracks || !stopIntervalValid) return
    player.start({
      playlist: { tracks: reviewTracks, shuffle, loop },
      mode,
      stopInterval: createStopInterval(stopMinSeconds * 1000, stopMaxSeconds * 1000),
      pauseDurationMillis: pauseDurationSeconds * 1000,
    })
  }

  function requestEndSession() {
    if (state?.kind === 'playing') {
      setShowEndConfirm(true)
    } else {
      endSession()
    }
  }

  function endSession() {
    player.end()
    setReviewTracks(null)
    setShowEndConfirm(false)
  }

  const settingsSummary =
    mode === 'FREEZE_DANCE'
      ? `${MODE_LABEL[mode]} · Pause ${pauseDurationSeconds}s · Stop ${stopMinSeconds}–${stopMaxSeconds}s`
      : `${MODE_LABEL[mode]} · Stop ${stopMinSeconds}–${stopMaxSeconds}s`

  const position = { currentMillis: player.currentMillis, totalMillis: player.totalMillis }

  return (
    <main>
      <h1 class="app__title">Stopptanz</h1>

      {!sessionActive && (
        <label class="picker">
          <span class="picker__icon" aria-hidden="true">🎵</span>
          <span class="picker__label">{reviewTracks ? 'Choose different music' : 'Choose your music'}</span>
          <p class="picker__hint">Select one or more audio files from your device</p>
          <input type="file" accept="audio/*" multiple onChange={onFilesSelected} />
        </label>
      )}

      <section class="section settings-card">
        <button
          type="button"
          class="settings-toggle"
          aria-expanded={settingsExpanded}
          onClick={() => setSettingsExpanded((v) => !v)}
        >
          <span class="settings-toggle__summary">{settingsSummary}</span>
          <span class="settings-toggle__chevron" aria-hidden="true">
            {settingsExpanded ? '▲' : '▼'}
          </span>
        </button>

        {settingsExpanded && (
          <div class="settings-body">
            {!sessionActive && (
              <fieldset>
                <legend class="section__title">Mode</legend>
                <div class="mode-row">
                  <label class="mode">
                    <input
                      type="radio"
                      name="mode"
                      checked={mode === 'FREEZE_DANCE'}
                      onChange={() => onModeChange('FREEZE_DANCE')}
                    />
                    <span class="mode__name">Freeze Dance</span>
                    <span class="mode__hint">Auto-resumes after a pause</span>
                  </label>
                  <label class="mode">
                    <input
                      type="radio"
                      name="mode"
                      checked={mode === 'MUSICAL_CHAIRS'}
                      onChange={() => onModeChange('MUSICAL_CHAIRS')}
                    />
                    <span class="mode__name">Musical Chairs</span>
                    <span class="mode__hint">Stays stopped until you resume</span>
                  </label>
                </div>
              </fieldset>
            )}

            <div class="field-row">
              <label class="field">
                Stop interval min (s)
                <input
                  type="number"
                  min={0}
                  value={stopMinSeconds}
                  onInput={(e) => onStopMinChange(Number((e.target as HTMLInputElement).value))}
                />
              </label>
              <label class="field">
                Stop interval max (s)
                <input
                  type="number"
                  min={stopMinSeconds}
                  value={stopMaxSeconds}
                  onInput={(e) => onStopMaxChange(Number((e.target as HTMLInputElement).value))}
                />
              </label>
              {mode === 'FREEZE_DANCE' && (
                <label class="field">
                  Freeze pause (s)
                  <input
                    type="number"
                    min={0}
                    value={pauseDurationSeconds}
                    onInput={(e) => setPauseDurationSeconds(Number((e.target as HTMLInputElement).value))}
                  />
                </label>
              )}
            </div>
            {!stopIntervalValid && <p class="field-error">Stop interval max must be at least min.</p>}
          </div>
        )}
      </section>

      <div class="toggle-row">
        <label class={`toggle${sessionActive ? ' toggle--disabled' : ''}`}>
          <input
            type="checkbox"
            checked={shuffle}
            disabled={sessionActive}
            onChange={(e) => onShuffleChange((e.target as HTMLInputElement).checked)}
          />
          Shuffle
        </label>
        <label class="toggle">
          <input type="checkbox" checked={loop} onChange={(e) => onLoopChange((e.target as HTMLInputElement).checked)} />
          Loop
        </label>
      </div>

      {reviewTracks && !sessionActive && (
        <section class="section">
          <h2 class="section__title">Playlist</h2>
          <ol class="playlist">
            {reviewTracks.map((track, index) => (
              <li class="playlist__item" key={track.uri}>
                <span class="playlist__name">{track.name}</span>
                <button
                  type="button"
                  class="icon-btn"
                  aria-label="Move up"
                  onClick={() => setReviewTracks(moveUp(reviewTracks, index))}
                  disabled={index === 0}
                >
                  ↑
                </button>
                <button
                  type="button"
                  class="icon-btn"
                  aria-label="Move down"
                  onClick={() => setReviewTracks(moveDown(reviewTracks, index))}
                  disabled={index === reviewTracks.length - 1}
                >
                  ↓
                </button>
                <button
                  type="button"
                  class="icon-btn icon-btn--danger"
                  aria-label="Remove"
                  onClick={() => setReviewTracks(remove(reviewTracks, index))}
                >
                  ✕
                </button>
              </li>
            ))}
          </ol>
          <button type="button" class="btn btn--block" onClick={startPlayback} disabled={reviewTracks.length === 0 || !stopIntervalValid}>
            Play
          </button>
        </section>
      )}

      {player.currentTrack && state && (
        <section class="now-playing">
          <p class="now-playing__track">{player.currentTrack.name}</p>
          <p class="now-playing__time">
            {formatCurrent(position)} / {formatTotal(position)}
          </p>
          {player.nextTrack && (
            <p class="now-playing__next">
              <span class="now-playing__next-label">Next</span> {player.nextTrack.name}
            </p>
          )}

          <span class={`state-badge state-badge--${state.kind}`}>{STATE_LABEL[state.kind]}</span>
          {state.kind === 'finished' && <p class="finished-note">Playlist finished — nice moves.</p>}

          <div class="transport">
            <button type="button" class="icon-btn" aria-label="Previous track" onClick={player.skipToPrevious} disabled={!player.canSkipPrevious}>
              ⏮
            </button>
            <button type="button" class="icon-btn" aria-label="Seek back 10 seconds" onClick={player.seekBack}>
              -10s
            </button>
            <button type="button" class="icon-btn" aria-label="Seek forward 10 seconds" onClick={player.seekForward}>
              +10s
            </button>
            <button type="button" class="icon-btn" aria-label="Next track" onClick={player.skipToNext} disabled={!player.canSkipNext}>
              ⏭
            </button>
          </div>

          <div class="primary-action">
            {state.kind === 'playing' && (
              <button type="button" class="btn btn--block" onClick={player.stop}>
                Stop
              </button>
            )}
            {state.kind === 'stopped' && (
              <button type="button" class="btn btn--block" onClick={player.resume}>
                {player.pauseRemainingMillis !== null
                  ? `Resume (${Math.ceil(player.pauseRemainingMillis / 1000)}s)`
                  : 'Resume'}
              </button>
            )}
            {(state.kind === 'playing' || state.kind === 'stopped') && (
              <button type="button" class="btn btn--block btn--ghost" onClick={player.pause}>
                Pause
              </button>
            )}
            {state.kind === 'paused' && (
              <button type="button" class="btn btn--block" onClick={player.resumeFromPause}>
                Resume from Pause
              </button>
            )}
          </div>

          <button type="button" class="btn btn--block btn--ghost" onClick={requestEndSession}>
            End Session
          </button>
        </section>
      )}

      {showEndConfirm && (
        <div class="dialog-overlay" role="presentation">
          <div class="dialog" role="alertdialog" aria-modal="true" aria-label="End session">
            <p class="dialog__message">Music is still playing. End the session anyway?</p>
            <div class="dialog__actions">
              <button type="button" class="btn btn--ghost" onClick={() => setShowEndConfirm(false)}>
                Cancel
              </button>
              <button type="button" class="btn" onClick={endSession}>
                End Session
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  )
}
