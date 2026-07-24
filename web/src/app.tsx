import { useState } from 'preact/hooks'
import { buildPlaylist } from './playlist/playlistBuilder'
import { moveDown, moveUp, remove } from './playlist/trackReview'
import { formatCurrent, formatTotal } from './engine/playbackPosition'
import { usePlaylistPlayer } from './playback/usePlaylistPlayer'
import type { Track } from './engine/track'

export function App() {
  const player = usePlaylistPlayer()
  const [shuffle, setShuffle] = useState(false)
  const [loop, setLoop] = useState(false)
  const [reviewTracks, setReviewTracks] = useState<Track[] | null>(null)

  function onFilesSelected(e: Event) {
    const files = Array.from((e.target as HTMLInputElement).files ?? [])
    const playlist = buildPlaylist(files, (file) => URL.createObjectURL(file))
    setReviewTracks(playlist?.tracks ?? null)
  }

  function startPlayback() {
    if (reviewTracks) player.load({ tracks: reviewTracks, shuffle, loop })
  }

  const position = { currentMillis: player.currentMillis, totalMillis: player.totalMillis }

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

      {player.currentTrack && (
        <section>
          <p>{player.currentTrack.name}</p>
          <p>
            {formatCurrent(position)} / {formatTotal(position)}
          </p>

          <button type="button" onClick={player.skipToPrevious} disabled={!player.canSkipPrevious}>
            Previous
          </button>
          <button type="button" onClick={player.seekBack}>
            -10s
          </button>
          <button type="button" onClick={player.togglePlayPause}>
            {player.isPlaying ? 'Pause' : 'Play'}
          </button>
          <button type="button" onClick={player.seekForward}>
            +10s
          </button>
          <button type="button" onClick={player.skipToNext} disabled={!player.canSkipNext}>
            Next
          </button>

          {player.isFinished && <p>Playlist finished.</p>}
        </section>
      )}
    </main>
  )
}
