import { useState } from 'preact/hooks'
import { buildPlaylist } from './playlist/playlistBuilder'
import { moveDown, moveUp, remove } from './playlist/trackReview'
import { formatCurrent, formatTotal } from './engine/playbackPosition'
import type { Mode } from './engine/mode'
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

  /** Stop Interval changes apply to the running Session immediately, matching Android; other
   * settings (Mode, Shuffle, Loop) only take effect on the next Session since they're baked into
   * the SessionEngine/Playlist at start(). */
  function applyStopIntervalIfValid(minSeconds: number, maxSeconds: number) {
    if (minSeconds >= 0 && maxSeconds >= minSeconds && player.sessionState) {
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

  const position = { currentMillis: player.currentMillis, totalMillis: player.totalMillis }
  const state = player.sessionState

  return (
    <main>
      <h1>Stopptanz</h1>

      <input type="file" accept="audio/*" multiple onChange={onFilesSelected} />

      <label>
        <input
          type="checkbox"
          checked={shuffle}
          onChange={(e) => onShuffleChange((e.target as HTMLInputElement).checked)}
        />
        Shuffle
      </label>
      <label>
        <input type="checkbox" checked={loop} onChange={(e) => onLoopChange((e.target as HTMLInputElement).checked)} />
        Loop
      </label>

      <fieldset>
        <legend>Mode</legend>
        <label>
          <input type="radio" name="mode" checked={mode === 'FREEZE_DANCE'} onChange={() => onModeChange('FREEZE_DANCE')} />
          Freeze Dance
        </label>
        <label>
          <input
            type="radio"
            name="mode"
            checked={mode === 'MUSICAL_CHAIRS'}
            onChange={() => onModeChange('MUSICAL_CHAIRS')}
          />
          Musical Chairs
        </label>
      </fieldset>

      <label>
        Stop interval min (s)
        <input
          type="number"
          min={0}
          value={stopMinSeconds}
          onInput={(e) => onStopMinChange(Number((e.target as HTMLInputElement).value))}
        />
      </label>
      <label>
        Stop interval max (s)
        <input
          type="number"
          min={stopMinSeconds}
          value={stopMaxSeconds}
          onInput={(e) => onStopMaxChange(Number((e.target as HTMLInputElement).value))}
        />
      </label>
      {mode === 'FREEZE_DANCE' && (
        <label>
          Freeze pause (s)
          <input
            type="number"
            min={0}
            value={pauseDurationSeconds}
            onInput={(e) => setPauseDurationSeconds(Number((e.target as HTMLInputElement).value))}
          />
        </label>
      )}

      {reviewTracks && (
        <section>
          <h2>Playlist</h2>
          <ol>
            {reviewTracks.map((track, index) => (
              <li key={track.uri}>
                {track.name}
                <button type="button" onClick={() => setReviewTracks(moveUp(reviewTracks, index))} disabled={index === 0}>
                  Up
                </button>
                <button
                  type="button"
                  onClick={() => setReviewTracks(moveDown(reviewTracks, index))}
                  disabled={index === reviewTracks.length - 1}
                >
                  Down
                </button>
                <button type="button" onClick={() => setReviewTracks(remove(reviewTracks, index))}>
                  Remove
                </button>
              </li>
            ))}
          </ol>
          <button type="button" onClick={startPlayback} disabled={reviewTracks.length === 0 || !stopIntervalValid}>
            Play
          </button>
          {!stopIntervalValid && <p>Stop interval max must be at least min.</p>}
        </section>
      )}

      {player.currentTrack && state && (
        <section>
          <p>{player.currentTrack.name}</p>
          <p>
            {formatCurrent(position)} / {formatTotal(position)}
          </p>
          <p>Session: {state.kind}</p>
          {player.pauseRemainingMillis !== null && <p>Resuming in {Math.ceil(player.pauseRemainingMillis / 1000)}s</p>}

          <button type="button" onClick={player.skipToPrevious} disabled={!player.canSkipPrevious}>
            Previous
          </button>
          <button type="button" onClick={player.seekBack}>
            -10s
          </button>
          <button type="button" onClick={player.seekForward}>
            +10s
          </button>
          <button type="button" onClick={player.skipToNext} disabled={!player.canSkipNext}>
            Next
          </button>

          {state.kind === 'playing' && (
            <button type="button" onClick={player.stop}>
              Stop
            </button>
          )}
          {state.kind === 'stopped' && (
            <button type="button" onClick={player.resume}>
              Resume
            </button>
          )}
          {(state.kind === 'playing' || state.kind === 'stopped') && (
            <button type="button" onClick={player.pause}>
              Pause
            </button>
          )}
          {state.kind === 'paused' && (
            <button type="button" onClick={player.resumeFromPause}>
              Resume from Pause
            </button>
          )}

          {state.kind === 'finished' && <p>Playlist finished.</p>}
        </section>
      )}
    </main>
  )
}
