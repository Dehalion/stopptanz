import { useEffect, useRef, useState } from 'preact/hooks'
import { awaitDeadline } from '../engine/deadlineTicker'
import { SessionEngine } from '../engine/sessionEngine'
import type { Mode } from '../engine/mode'
import type { Playlist } from '../engine/playlist'
import { PLAYING, type SessionState } from '../engine/sessionState'
import type { StopInterval } from '../engine/stopInterval'
import type { Track, TrackRemaining } from '../engine/track'
import { clearActionHandlers, setActionHandlers, setTrackMetadata } from './mediaSession'
import { SEEK_STEP_MILLIS, clampSeek } from './seek'
import { SessionOrchestrator, type PlaybackIo, type TimerHandle } from './sessionOrchestrator'

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
  setStopInterval: (stopInterval: StopInterval) => void
}

function scheduleDeadlineTimer(
  durationMillis: number,
  onFire: () => void,
  onTick?: (remaining: number) => void,
): TimerHandle {
  const handle: TimerHandle & { cancelled: boolean } = {
    cancelled: false,
    cancel() {
      this.cancelled = true
    },
  }
  void awaitDeadline({
    durationMillis,
    tickMillis: AUTO_RESUME_TICK_MILLIS,
    isCancelled: () => handle.cancelled,
    onTick,
  }).then(() => {
    if (!handle.cancelled) onFire()
  })
  return handle
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
    scheduleTimer: scheduleDeadlineTimer,
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

  /** Points `<audio>` at the engine's current Track, playing only if the Session is Playing — a
   * no-op once the Session has Finished, since the last Track is already loaded and stopped. */
  function syncAudioToCurrentTrack() {
    const engine = engineRef.current
    if (!engine || engine.state.kind === 'finished') return
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

  useEffect(() => {
    if (engine) setTrackMetadata(engine.currentTrack.name)
  }, [engine?.currentTrack.name])

  // Mirrors Android's PlaybackService MediaSession wiring: the OS play/pause control only ever
  // maps to meta-Pause/Resume (not Stop/Resume's freeze mechanic), guarded so a stale/racy OS
  // command can't call into the engine outside the state it expects.
  useEffect(() => {
    setActionHandlers({
      play: () => orchestratorRef.current?.resumeFromPause(),
      pause: () => orchestratorRef.current?.pause(),
      previoustrack: skipToPrevious,
      nexttrack: skipToNext,
    })
    return clearActionHandlers
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionState])
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
    setStopInterval: (stopInterval) => orchestratorRef.current?.setStopInterval(stopInterval),
  }
}
