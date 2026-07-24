import { useEffect, useRef, useState } from 'preact/hooks'
import { awaitDeadline } from '../engine/deadlineTicker'
import { SessionEngine } from '../engine/sessionEngine'
import type { Mode } from '../engine/mode'
import type { Playlist } from '../engine/playlist'
import { PLAYING, type SessionState } from '../engine/sessionState'
import type { StopInterval } from '../engine/stopInterval'
import type { Track, TrackRemaining } from '../engine/track'
import { SEEK_STEP_MILLIS, clampSeek } from './seek'
import { SessionOrchestrator, type PlaybackIo } from './sessionOrchestrator'

const AUTO_RESUME_TICK_MILLIS = 1_000

export interface SessionConfig {
  playlist: Playlist
  mode: Mode
  stopInterval: StopInterval
  pauseDurationMillis: number
}

export interface PlaylistPlayer {
  sessionState: SessionState | null
  currentTrack: Track | null
  nextTrack: Track | null
  remainingTracks: TrackRemaining | null
  canSkipPrevious: boolean
  canSkipNext: boolean
  pauseRemainingMillis: number | null
  currentMillis: number
  totalMillis: number
  start: (config: SessionConfig) => void
  stop: () => void
  resume: () => void
  pause: () => void
  resumeFromPause: () => void
  skipToNext: () => void
  skipToPrevious: () => void
  seekBack: () => void
  seekForward: () => void
}

function createTimerScheduler() {
  return {
    scheduleTimer(durationMillis: number, onFire: () => void, onTick?: (remaining: number) => void): unknown {
      const token = { cancelled: false }
      void awaitDeadline({
        durationMillis,
        tickMillis: AUTO_RESUME_TICK_MILLIS,
        isCancelled: () => token.cancelled,
        onTick,
      }).then(() => {
        if (!token.cancelled) onFire()
      })
      return token
    },
    cancelTimer(handle: unknown | null): void {
      if (handle) (handle as { cancelled: boolean }).cancelled = true
    },
  }
}

/** Wraps an `<audio>` element with a SessionOrchestrator driving the full Stop/Resume/Pause game loop. */
export function usePlaylistPlayer(): PlaylistPlayer {
  const audioRef = useRef<HTMLAudioElement>(new Audio())
  const engineRef = useRef<SessionEngine | null>(null)
  const orchestratorRef = useRef<SessionOrchestrator | null>(null)
  const [sessionState, setSessionState] = useState<SessionState | null>(null)
  const [pauseRemainingMillis, setPauseRemainingMillis] = useState<number | null>(null)
  const [currentMillis, setCurrentMillis] = useState(0)
  const [totalMillis, setTotalMillis] = useState(0)
  const [, forceRender] = useState(0)

  const ioRef = useRef<PlaybackIo>({
    playAudio: () => void audioRef.current.play(),
    pauseAudio: () => audioRef.current.pause(),
    getRemainingTrackMillis: () => {
      const audio = audioRef.current
      if (!Number.isFinite(audio.duration)) return Number.MAX_SAFE_INTEGER
      return Math.max(audio.duration * 1000 - audio.currentTime * 1000, 0)
    },
    ...createTimerScheduler(),
  })

  useEffect(() => {
    const audio = audioRef.current
    const onTimeUpdate = () => setCurrentMillis(audio.currentTime * 1000)
    const onLoadedMetadata = () => setTotalMillis(Number.isFinite(audio.duration) ? audio.duration * 1000 : 0)
    const onEnded = () => {
      orchestratorRef.current?.trackEnded()
      syncAudioToCurrentTrack()
      forceRender((n) => n + 1)
    }

    audio.addEventListener('timeupdate', onTimeUpdate)
    audio.addEventListener('loadedmetadata', onLoadedMetadata)
    audio.addEventListener('ended', onEnded)
    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate)
      audio.removeEventListener('loadedmetadata', onLoadedMetadata)
      audio.removeEventListener('ended', onEnded)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  /** Points `<audio>` at the engine's current Track, playing only if the Session is Playing. */
  function syncAudioToCurrentTrack() {
    const engine = engineRef.current
    if (!engine) return
    audioRef.current.src = engine.currentTrack.uri
    if (engine.state.kind === 'playing') void audioRef.current.play()
  }

  function start(config: SessionConfig) {
    const engine = new SessionEngine({
      playlist: config.playlist,
      mode: config.mode,
      stopInterval: config.stopInterval,
      pauseDurationMillis: config.pauseDurationMillis,
    })
    engineRef.current = engine
    const orchestrator = new SessionOrchestrator(engine, ioRef.current, {
      onStateChanged: (state) => setSessionState(state),
      onPauseRemainingChanged: (remaining) => setPauseRemainingMillis(remaining),
    })
    orchestratorRef.current = orchestrator
    setSessionState(PLAYING)
    orchestrator.start()
    syncAudioToCurrentTrack()
  }

  function skipToNext() {
    const engine = engineRef.current
    if (!engine || !engine.canSkipNext) return
    engine.skipToNext()
    orchestratorRef.current?.rescheduleTimersForCurrentState()
    syncAudioToCurrentTrack()
    forceRender((n) => n + 1)
  }

  function skipToPrevious() {
    const engine = engineRef.current
    if (!engine || !engine.canSkipPrevious) return
    engine.skipToPrevious()
    orchestratorRef.current?.rescheduleTimersForCurrentState()
    syncAudioToCurrentTrack()
    forceRender((n) => n + 1)
  }

  function seekBy(deltaMillis: number) {
    const audio = audioRef.current
    audio.currentTime = clampSeek(audio.currentTime * 1000, deltaMillis, totalMillis) / 1000
  }

  const engine = engineRef.current
  return {
    sessionState,
    currentTrack: engine?.currentTrack ?? null,
    nextTrack: engine?.nextTrack ?? null,
    remainingTracks: engine?.remainingTracks ?? null,
    canSkipPrevious: engine?.canSkipPrevious ?? false,
    canSkipNext: engine?.canSkipNext ?? false,
    pauseRemainingMillis,
    currentMillis,
    totalMillis,
    start,
    stop: () => orchestratorRef.current?.stop(),
    resume: () => orchestratorRef.current?.resume(),
    pause: () => orchestratorRef.current?.pause(),
    resumeFromPause: () => orchestratorRef.current?.resumeFromPause(),
    skipToNext,
    skipToPrevious,
    seekBack: () => seekBy(-SEEK_STEP_MILLIS),
    seekForward: () => seekBy(SEEK_STEP_MILLIS),
  }
}
