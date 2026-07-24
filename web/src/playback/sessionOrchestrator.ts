import type { SessionEngine } from '../engine/sessionEngine'
import type { SessionState } from '../engine/sessionState'
import type { StopInterval } from '../engine/stopInterval'

export interface PlaybackIo {
  playAudio(): void
  pauseAudio(): void
  /** Milliseconds left in the current Track; `Number.MAX_SAFE_INTEGER` (or similar) if unknown. */
  getRemainingTrackMillis(): number
  scheduleTimer(durationMillis: number, onFire: () => void, onTick?: (remainingMillis: number) => void): unknown
  cancelTimer(handle: unknown | null): void
}

export interface SessionOrchestratorCallbacks {
  onStateChanged?: (state: SessionState) => void
  onPauseRemainingChanged?: (remainingMillis: number | null) => void
}

/**
 * Glue layer driving Stop/Resume timing off a [SessionEngine], mirroring the Android
 * SessionPlaybackAdapter — not a primary test target for depth, but the acceptance-critical
 * transitions (auto-Stop, Freeze Dance auto-resume + cancellation, Pause/Resume) are covered.
 */
export class SessionOrchestrator {
  private readonly engine: SessionEngine
  private readonly io: PlaybackIo
  private readonly callbacks: SessionOrchestratorCallbacks
  private autoStopHandle: unknown | null = null
  private autoResumeHandle: unknown | null = null
  private pauseRemainingMillis: number | null = null

  constructor(engine: SessionEngine, io: PlaybackIo, callbacks: SessionOrchestratorCallbacks = {}) {
    this.engine = engine
    this.io = io
    this.callbacks = callbacks
  }

  start(): void {
    this.callbacks.onStateChanged?.(this.engine.state)
    this.scheduleAutoStop()
  }

  stop(): void {
    this.io.cancelTimer(this.autoStopHandle)
    this.performStop()
  }

  private performStop(): void {
    this.engine.stop()
    this.io.pauseAudio()
    this.callbacks.onStateChanged?.(this.engine.state)
    if (this.engine.mode === 'FREEZE_DANCE') {
      this.scheduleAutoResume()
    }
  }

  resume(): void {
    this.io.cancelTimer(this.autoResumeHandle)
    this.clearPauseRemaining()
    this.engine.resume()
    this.io.playAudio()
    this.callbacks.onStateChanged?.(this.engine.state)
    this.scheduleAutoStop()
  }

  /** Suspends the Session, capturing any in-flight freeze auto-resume countdown so `resumeFromPause` restores it exactly. */
  pause(): void {
    const remainingFreezeMillis =
      this.engine.state.kind === 'stopped' && this.engine.mode === 'FREEZE_DANCE' ? this.pauseRemainingMillis : null
    this.io.cancelTimer(this.autoStopHandle)
    this.io.cancelTimer(this.autoResumeHandle)
    this.clearPauseRemaining()
    this.io.pauseAudio()
    this.engine.pause(remainingFreezeMillis)
    this.callbacks.onStateChanged?.(this.engine.state)
  }

  resumeFromPause(): void {
    const remainingFreezeMillis = this.engine.resumeFromPause()
    if (this.engine.state.kind === 'playing') {
      this.io.playAudio()
      this.scheduleAutoStop()
    } else if (this.engine.state.kind === 'stopped' && this.engine.mode === 'FREEZE_DANCE') {
      this.scheduleAutoResumeWithRemaining(remainingFreezeMillis ?? this.engine.pauseDurationMillis)
    }
    this.callbacks.onStateChanged?.(this.engine.state)
  }

  /** Called once the current Track has naturally ended (audio `ended` event). */
  trackEnded(): void {
    this.io.cancelTimer(this.autoStopHandle)
    if (this.engine.nextTrack === null && !this.engine.playlist.loop) {
      this.engine.onPlaylistEnded()
      this.callbacks.onStateChanged?.(this.engine.state)
      return
    }
    this.engine.onTrackAdvanced()
    this.scheduleAutoStop()
  }

  /** Reschedules whichever pending timer applies to the current state after a host-driven Skip. */
  rescheduleTimersForCurrentState(): void {
    if (this.engine.state.kind === 'playing') {
      this.io.cancelTimer(this.autoStopHandle)
      this.scheduleAutoStop()
    } else if (this.engine.state.kind === 'stopped' && this.engine.mode === 'FREEZE_DANCE') {
      this.io.cancelTimer(this.autoResumeHandle)
      this.scheduleAutoResume()
    }
  }

  setStopInterval(stopInterval: StopInterval): void {
    this.engine.setStopInterval(stopInterval)
  }

  setPauseDurationMillis(pauseDurationMillis: number): void {
    this.engine.setPauseDurationMillis(pauseDurationMillis)
  }

  close(): void {
    this.io.cancelTimer(this.autoStopHandle)
    this.io.cancelTimer(this.autoResumeHandle)
    this.clearPauseRemaining()
    this.engine.close()
    this.io.pauseAudio()
    this.callbacks.onStateChanged?.(this.engine.state)
  }

  private scheduleAutoStop(): void {
    const delayMillis = this.engine.nextStopDelayMillis(this.io.getRemainingTrackMillis())
    if (delayMillis === null) return
    this.autoStopHandle = this.io.scheduleTimer(delayMillis, () => this.performStop())
  }

  private scheduleAutoResume(): void {
    this.scheduleAutoResumeWithRemaining(this.engine.pauseDurationMillis)
  }

  private scheduleAutoResumeWithRemaining(remainingMillis: number): void {
    this.setPauseRemaining(remainingMillis)
    this.autoResumeHandle = this.io.scheduleTimer(
      remainingMillis,
      () => {
        this.engine.onPauseElapsed()
        this.clearPauseRemaining()
        this.io.playAudio()
        this.callbacks.onStateChanged?.(this.engine.state)
        this.scheduleAutoStop()
      },
      (remaining) => this.setPauseRemaining(remaining),
    )
  }

  private setPauseRemaining(remainingMillis: number): void {
    this.pauseRemainingMillis = remainingMillis
    this.callbacks.onPauseRemainingChanged?.(remainingMillis)
  }

  private clearPauseRemaining(): void {
    this.pauseRemainingMillis = null
    this.callbacks.onPauseRemainingChanged?.(null)
  }
}
