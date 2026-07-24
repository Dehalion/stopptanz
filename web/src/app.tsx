import { useState } from 'preact/hooks'
import { buildPlaylist } from './playlist/playlistBuilder'
import { moveDown, moveUp, remove } from './playlist/trackReview'
import { formatCurrent, formatTotal } from './engine/playbackPosition'
import type { Mode } from './engine/mode'
import { createStopInterval } from './engine/stopInterval'
import { usePlaylistPlayer } from './playback/usePlaylistPlayer'
import type { Track } from './engine/track'

export function App() {
  const player = usePlaylistPlayer()
  const [shuffle, setShuffle] = useState(false)
  const [loop, setLoop] = useState(false)
  const [mode, setMode] = useState<Mode>('FREEZE_DANCE')
  const [stopMinSeconds, setStopMinSeconds] = useState(10)
  const [stopMaxSeconds, setStopMaxSeconds] = useState(25)
  const [pauseDurationSeconds, setPauseDurationSeconds] = useState(4)
  const [reviewTracks, setReviewTracks] = useState<Track[] | null>(null)

  function onFilesSelected(e: Event) {
    const files = Array.from((e.target as HTMLInputElement).files ?? [])
    const playlist = buildPlaylist(files, (file) => URL.createObjectURL(file))
    setReviewTracks(playlist?.tracks ?? null)
  }

  function startPlayback() {
    if (!reviewTracks) return
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
        <input type="checkbox" checked={shuffle} onChange={(e) => setShuffle((e.target as HTMLInputElement).checked)} />
        Shuffle
      </label>
      <label>
        <input type="checkbox" checked={loop} onChange={(e) => setLoop((e.target as HTMLInputElement).checked)} />
        Loop
      </label>

      <fieldset>
        <legend>Mode</legend>
        <label>
          <input type="radio" name="mode" checked={mode === 'FREEZE_DANCE'} onChange={() => setMode('FREEZE_DANCE')} />
          Freeze Dance
        </label>
        <label>
          <input type="radio" name="mode" checked={mode === 'MUSICAL_CHAIRS'} onChange={() => setMode('MUSICAL_CHAIRS')} />
          Musical Chairs
        </label>
      </fieldset>

      <label>
        Stop interval min (s)
        <input
          type="number"
          min={0}
          value={stopMinSeconds}
          onInput={(e) => setStopMinSeconds(Number((e.target as HTMLInputElement).value))}
        />
      </label>
      <label>
        Stop interval max (s)
        <input
          type="number"
          min={stopMinSeconds}
          value={stopMaxSeconds}
          onInput={(e) => setStopMaxSeconds(Number((e.target as HTMLInputElement).value))}
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
          <button type="button" onClick={startPlayback} disabled={reviewTracks.length === 0}>
            Play
          </button>
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
