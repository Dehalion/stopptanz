import type { Mode } from './mode'
import type { Playlist } from './playlist'
import { defaultRandomSource, type RandomSource } from './randomSource'
import { CLOSED, FINISHED, PAUSED, PLAYING, STOPPED, type SessionState } from './sessionState'
import type { StopInterval } from './stopInterval'
import type { Track, TrackRemaining, TrackStatus } from './track'

/** No Stop is ever scheduled to land within this many milliseconds of a Track's natural end. */
const END_OF_TRACK_GUARD_MILLIS = 10_000

export class SessionEngine {
  readonly playlist: Playlist
  readonly mode: Mode
  private readonly randomSource: RandomSource

  private _state: SessionState = PLAYING
  private stopInterval: StopInterval
  private _pauseDurationMillis: number
  private _orderedTracks: Track[] | null = null
  private _currentTrackIndex = 0

  constructor(options: {
    playlist: Playlist
    mode: Mode
    stopInterval: StopInterval
    pauseDurationMillis: number
    randomSource?: RandomSource
  }) {
    this.playlist = options.playlist
    this.mode = options.mode
    this.stopInterval = options.stopInterval
    this._pauseDurationMillis = options.pauseDurationMillis
    this.randomSource = options.randomSource ?? defaultRandomSource
  }

  get state(): SessionState {
    return this._state
  }

  get pauseDurationMillis(): number {
    return this._pauseDurationMillis
  }

  /** Playlist tracks in playback order: shuffled once per Session if `Playlist.shuffle` is set. */
  get orderedTracks(): Track[] {
    if (this._orderedTracks === null) {
      this._orderedTracks = this.playlist.shuffle ? this.shuffledTracks() : this.playlist.tracks
    }
    return this._orderedTracks
  }

  get currentTrackIndex(): number {
    return this._currentTrackIndex
  }

  get currentTrack(): Track {
    return this.orderedTracks[this._currentTrackIndex]
  }

  /** The Track that follows `currentTrack` in `orderedTracks`; `null` if `currentTrack` is last. */
  get nextTrack(): Track | null {
    return this.orderedTracks[this._currentTrackIndex + 1] ?? null
  }

  /** Position when `Playlist.loop` is on ("Track X of N"); a true countdown of Tracks left otherwise. */
  get remainingTracks(): TrackRemaining {
    if (this.playlist.loop) {
      return { kind: 'position', current: this._currentTrackIndex + 1, total: this.orderedTracks.length }
    }
    return { kind: 'countdown', remaining: this.orderedTracks.length - this._currentTrackIndex - 1 }
  }

  /** Current Track, next Track, and remaining count, bundled for the adapter/UI to observe together. */
  get trackStatus(): TrackStatus {
    return {
      current: this.currentTrack,
      next: this.nextTrack,
      remaining: this.remainingTracks,
      canSkipPrevious: this.canSkipPrevious,
      canSkipNext: this.canSkipNext,
    }
  }

  /** Whether `skipToPrevious` would move `currentTrackIndex`: false at the first Track unless Loop is on, and always false with a single Track. */
  get canSkipPrevious(): boolean {
    return this.orderedTracks.length > 1 && (Boolean(this.playlist.loop) || this._currentTrackIndex > 0)
  }

  /** Whether `skipToNext` would move `currentTrackIndex`: false at the last Track unless Loop is on, and always false with a single Track. */
  get canSkipNext(): boolean {
    return (
      this.orderedTracks.length > 1 &&
      (Boolean(this.playlist.loop) || this._currentTrackIndex < this.orderedTracks.length - 1)
    )
  }

  /** Called by the playback adapter each time playback moves on to the next Track. */
  onTrackAdvanced(): void {
    if (this._state.kind !== 'playing') throw new Error(`Cannot advance Track from ${this._state.kind}`)
    const next = this._currentTrackIndex + 1
    if (this.playlist.loop) {
      this._currentTrackIndex = next % this.orderedTracks.length
    } else {
      if (next >= this.orderedTracks.length) {
        throw new Error('Cannot advance Track past the end of a non-looping Playlist')
      }
      this._currentTrackIndex = next
    }
  }

  /**
   * Picks the next Stop delay, or `null` if that pick would land within the last
   * `END_OF_TRACK_GUARD_MILLIS` of the current Track — in which case the caller should let the
   * Track play out to its natural end instead of scheduling a Stop.
   */
  nextStopDelayMillis(remainingTrackMillis = Number.MAX_SAFE_INTEGER): number | null {
    const delay = this.randomSource(this.stopInterval.minMillis, this.stopInterval.maxMillis)
    return remainingTrackMillis - delay < END_OF_TRACK_GUARD_MILLIS ? null : delay
  }

  /** Applies prospectively: a countdown already in flight captured its value on return and is unaffected. */
  setStopInterval(stopInterval: StopInterval): void {
    this.stopInterval = stopInterval
  }

  /** Applies prospectively: a countdown already in flight captured its value on return and is unaffected. */
  setPauseDurationMillis(pauseDurationMillis: number): void {
    this._pauseDurationMillis = pauseDurationMillis
  }

  /** Unlike `shuffle` (baked into `orderedTracks` at construction), `loop` is read live on every
   * advance check, so it can be toggled mid-Session. */
  setLoop(loop: boolean): void {
    this.playlist.loop = loop
  }

  /** Called by the playback adapter when the Playlist reaches its end. */
  onPlaylistEnded(): void {
    if (this._state.kind !== 'playing') throw new Error(`Cannot end Playlist from ${this._state.kind}`)
    if (!this.playlist.loop) {
      this._state = FINISHED
    }
  }

  private shuffledTracks(): Track[] {
    const tracks = [...this.playlist.tracks]
    for (let i = tracks.length - 1; i > 0; i--) {
      const j = this.randomSource(0, i)
      const tmp = tracks[i]
      tracks[i] = tracks[j]
      tracks[j] = tmp
    }
    return tracks
  }

  stop(): void {
    if (this._state.kind !== 'playing') throw new Error(`Cannot Stop from ${this._state.kind}`)
    this._state = STOPPED
  }

  resume(): void {
    if (this._state.kind !== 'stopped') throw new Error(`Cannot Resume from ${this._state.kind}`)
    this._state = PLAYING
  }

  /** Host-driven jump to the previous Track; wraps when Loop is on, a no-op at the first Track otherwise. Leaves `state` unchanged. */
  skipToPrevious(): void {
    if (this._state.kind !== 'playing' && this._state.kind !== 'stopped' && this._state.kind !== 'paused') {
      throw new Error(`Cannot Skip from ${this._state.kind}`)
    }
    const previous = this._currentTrackIndex - 1
    this._currentTrackIndex = this.playlist.loop
      ? (previous + this.orderedTracks.length) % this.orderedTracks.length
      : Math.max(previous, 0)
  }

  /** Host-driven jump to the next Track; wraps when Loop is on, a no-op at the last Track otherwise. Leaves `state` unchanged. */
  skipToNext(): void {
    if (this._state.kind !== 'playing' && this._state.kind !== 'stopped' && this._state.kind !== 'paused') {
      throw new Error(`Cannot Skip from ${this._state.kind}`)
    }
    const next = this._currentTrackIndex + 1
    this._currentTrackIndex = this.playlist.loop ? next % this.orderedTracks.length : Math.min(next, this.orderedTracks.length - 1)
  }

  /** Called by the playback adapter once `pauseDurationMillis` has elapsed since a Stop. */
  onPauseElapsed(): void {
    if (this._state.kind !== 'stopped') throw new Error(`Cannot auto-resume from ${this._state.kind}`)
    if (this.mode !== 'FREEZE_DANCE') throw new Error('Auto-resume only applies in Freeze Dance mode')
    this._state = PLAYING
  }

  /**
   * Suspends the Session from Playing or Stopped; resumable to exactly that state via
   * `resumeFromPause`. `remainingFreezeMillis` is required when pausing mid-countdown in Freeze
   * Dance mode (so the countdown can't be silently lost), and must be omitted otherwise.
   */
  pause(remainingFreezeMillis: number | null = null): void {
    const current = this._state
    if (current.kind !== 'playing' && current.kind !== 'stopped') {
      throw new Error(`Cannot Pause from ${current.kind}`)
    }
    const pausingMidCountdown = current.kind === 'stopped' && this.mode === 'FREEZE_DANCE'
    if (pausingMidCountdown) {
      if (remainingFreezeMillis === null) {
        throw new Error('remainingFreezeMillis is required when pausing mid-countdown in Freeze Dance mode')
      }
    } else if (remainingFreezeMillis !== null) {
      throw new Error('remainingFreezeMillis only applies when pausing mid-countdown in Freeze Dance mode')
    }
    this._state = PAUSED(current, remainingFreezeMillis)
  }

  /** Restores whichever state was active before `pause`; returns the freeze countdown remaining at pause time, if any. */
  resumeFromPause(): number | null {
    const current = this._state
    if (current.kind !== 'paused') throw new Error(`Cannot Resume from Pause from ${current.kind}`)
    this._state = current.resumedState
    return current.remainingFreezeMillis
  }

  close(): void {
    const kind = this._state.kind
    if (kind !== 'playing' && kind !== 'stopped' && kind !== 'finished' && kind !== 'paused') {
      throw new Error(`Cannot Close from ${kind}`)
    }
    this._state = CLOSED
  }
}
