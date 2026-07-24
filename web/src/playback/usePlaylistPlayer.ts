import { useEffect, useRef, useState } from 'preact/hooks'
import { SessionEngine } from '../engine/sessionEngine'
import type { Playlist } from '../engine/playlist'
import { unusedStopInterval } from '../engine/stopInterval'
import type { Track, TrackRemaining } from '../engine/track'
import { SEEK_STEP_MILLIS, clampSeek } from './seek'

export interface PlaylistPlayer {
  currentTrack: Track | null
  nextTrack: Track | null
  remainingTracks: TrackRemaining | null
  canSkipPrevious: boolean
  canSkipNext: boolean
  isPlaying: boolean
  isFinished: boolean
  currentMillis: number
  totalMillis: number
  load: (playlist: Playlist) => void
  togglePlayPause: () => void
  skipToNext: () => void
  skipToPrevious: () => void
  seekBack: () => void
  seekForward: () => void
}

/** Wraps an `<audio>` element with the SessionEngine's Playlist ordering/skip semantics — continuous
 * playback only, no Stops (that's wired in a later ticket). */
export function usePlaylistPlayer(): PlaylistPlayer {
  const audioRef = useRef<HTMLAudioElement>(new Audio())
  const engineRef = useRef<SessionEngine | null>(null)
  const [, forceRender] = useState(0)
  const [isPlaying, setIsPlaying] = useState(false)
  const [isFinished, setIsFinished] = useState(false)
  const [currentMillis, setCurrentMillis] = useState(0)
  const [totalMillis, setTotalMillis] = useState(0)

  useEffect(() => {
    const audio = audioRef.current
    const onTimeUpdate = () => setCurrentMillis(audio.currentTime * 1000)
    const onLoadedMetadata = () => setTotalMillis(Number.isFinite(audio.duration) ? audio.duration * 1000 : 0)
    const onPlay = () => setIsPlaying(true)
    const onPause = () => setIsPlaying(false)
    const onEnded = () => advance()

    audio.addEventListener('timeupdate', onTimeUpdate)
    audio.addEventListener('loadedmetadata', onLoadedMetadata)
    audio.addEventListener('play', onPlay)
    audio.addEventListener('pause', onPause)
    audio.addEventListener('ended', onEnded)
    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate)
      audio.removeEventListener('loadedmetadata', onLoadedMetadata)
      audio.removeEventListener('play', onPlay)
      audio.removeEventListener('pause', onPause)
      audio.removeEventListener('ended', onEnded)
    }
  }, [])

  /** Plays `engine.currentTrack` and re-renders to reflect the engine's post-mutation state. */
  function syncToEngine() {
    const engine = engineRef.current
    if (!engine) return
    forceRender((n) => n + 1)
    audioRef.current.src = engine.currentTrack.uri
    void audioRef.current.play()
  }

  function advance() {
    const engine = engineRef.current
    if (!engine) return
    if (engine.nextTrack === null && !engine.playlist.loop) {
      engine.onPlaylistEnded()
      setIsFinished(true)
      forceRender((n) => n + 1)
      return
    }
    engine.onTrackAdvanced()
    syncToEngine()
  }

  function load(playlist: Playlist) {
    engineRef.current = new SessionEngine({
      playlist,
      mode: 'FREEZE_DANCE',
      stopInterval: unusedStopInterval,
      pauseDurationMillis: 0,
    })
    setIsFinished(false)
    syncToEngine()
  }

  function togglePlayPause() {
    if (isFinished) return
    const audio = audioRef.current
    if (audio.paused) void audio.play()
    else audio.pause()
  }

  function skipToNext() {
    const engine = engineRef.current
    if (!engine || isFinished || !engine.canSkipNext) return
    engine.skipToNext()
    syncToEngine()
  }

  function skipToPrevious() {
    const engine = engineRef.current
    if (!engine || isFinished || !engine.canSkipPrevious) return
    engine.skipToPrevious()
    syncToEngine()
  }

  function seekBy(deltaMillis: number) {
    const audio = audioRef.current
    audio.currentTime = clampSeek(audio.currentTime * 1000, deltaMillis, totalMillis) / 1000
  }

  const engine = engineRef.current
  return {
    currentTrack: engine?.currentTrack ?? null,
    nextTrack: engine?.nextTrack ?? null,
    remainingTracks: engine?.remainingTracks ?? null,
    canSkipPrevious: engine?.canSkipPrevious ?? false,
    canSkipNext: engine?.canSkipNext ?? false,
    isPlaying,
    isFinished,
    currentMillis,
    totalMillis,
    load,
    togglePlayPause,
    skipToNext,
    skipToPrevious,
    seekBack: () => seekBy(-SEEK_STEP_MILLIS),
    seekForward: () => seekBy(SEEK_STEP_MILLIS),
  }
}
