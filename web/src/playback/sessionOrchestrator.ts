import type { SessionEngine } from '../engine/sessionEngine'
import type { SessionState } from '../engine/sessionState'
import type { StopInterval } from '../engine/stopInterval'

/** Indirection so TS re-widens `engine.state`'s type after a mutating call, instead of keeping it
 * narrowed from an earlier guard — a known TS limitation with control-flow narrowing through
 * getter chains across statements. */
function currentState(engine: SessionEngine): SessionState {
  return engine.state
}

export interface TimerHandle {
  cancel(): void
}

export interface PlaybackIo {
  playAudio(): void
  pauseAudio(): void
  /** Milliseconds left in the current Track; `Number.MAX_SAFE_INTEGER` (or similar) if unknown. */
  getRemainingTrackMillis(): number
  scheduleTimer(durationMillis: number, onFire: () => void, onTick?: (remainingMillis: number) => void): TimerHandle
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
  private autoStopHandle: TimerHandle | null = null
  private autoResumeHandle: TimerHandle | null = null
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

  /** No-ops outside Playing, guarding against a stale/racy external command (e.g. a lock-screen
   * control) rather than requiring every caller to check state first. */
  stop(): void {
    if (this.engine.state.kind !== 'playing') return
    this.autoStopHandle?.cancel()
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

  /** No-ops outside Stopped, guarding against a stale/racy external command. */
  resume(): void {
    if (this.engine.state.kind !== 'stopped') return
    this.autoResumeHandle?.cancel()
    this.clearPauseRemaining()
    this.engine.resume()
    this.io.playAudio()
    this.callbacks.onStateChanged?.(this.engine.state)
    this.scheduleAutoStop()
  }

  /** Suspends the Session, capturing any in-flight freeze auto-resume countdown so `resumeFromPause`
   * restores it exactly. No-ops outside Playing/Stopped, guarding against a stale/racy external command. */
  pause(): void {
    if (this.engine.state.kind !== 'playing' && this.engine.state.kind !== 'stopped') return
    const remainingFreezeMillis =
      this.engine.state.kind === 'stopped' && this.engine.mode === 'FREEZE_DANCE' ? this.pauseRemainingMillis : null
    this.autoStopHandle?.cancel()
    this.autoResumeHandle?.cancel()
    this.clearPauseRemaining()
    this.io.pauseAudio()
    this.engine.pause(remainingFreezeMillis)
    this.callbacks.onStateChanged?.(this.engine.state)
  }

  /** No-ops outside Paused, guarding against a stale/racy external command. */
  resumeFromPause(): void {
    if (this.engine.state.kind !== 'paused') return
    const remainingFreezeMillis = this.engine.resumeFromPause()
    const newState = currentState(this.engine)
    if (newState.kind === 'playing') {
      this.io.playAudio()
      this.scheduleAutoStop()
    } else if (newState.kind === 'stopped' && this.engine.mode === 'FREEZE_DANCE') {
      this.scheduleAutoResumeWithRemaining(remainingFreezeMillis ?? this.engine.pauseDurationMillis)
    }
    this.callbacks.onStateChanged?.(newState)
  }

  /** Called once the current Track has naturally ended (audio `ended` event). */
  trackEnded(): void {
    this.autoStopHandle?.cancel()
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
      this.autoStopHandle?.cancel()
      this.scheduleAutoStop()
    } else if (this.engine.state.kind === 'stopped' && this.engine.mode === 'FREEZE_DANCE') {
      this.autoResumeHandle?.cancel()
      this.scheduleAutoResume()
    }
  }

  setStopInterval(stopInterval: StopInterval): void {
    this.engine.setStopInterval(stopInterval)
  }

  setPauseDurationMillis(pauseDurationMillis: number): void {
    this.engine.setPauseDurationMillis(pauseDurationMillis)
  }

  setLoop(loop: boolean): void {
    this.engine.setLoop(loop)
  }

  close(): void {
    this.autoStopHandle?.cancel()
    this.autoResumeHandle?.cancel()
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
