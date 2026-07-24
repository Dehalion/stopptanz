export type SessionState =
  | { kind: 'playing' }
  | { kind: 'stopped' }
  | { kind: 'finished' }
  | { kind: 'closed' }
  | { kind: 'paused'; resumedState: SessionState; remainingFreezeMillis: number | null }

export const PLAYING: SessionState = { kind: 'playing' }
export const STOPPED: SessionState = { kind: 'stopped' }
export const FINISHED: SessionState = { kind: 'finished' }
export const CLOSED: SessionState = { kind: 'closed' }

/**
 * `resumedState` is the state to restore on `SessionEngine.resumeFromPause` (`PLAYING` or
 * `STOPPED`). `remainingFreezeMillis` is the freeze auto-resume countdown remaining at the moment
 * of pausing, `null` unless `resumedState` is `STOPPED`.
 */
export function PAUSED(resumedState: SessionState, remainingFreezeMillis: number | null = null): SessionState {
  return { kind: 'paused', resumedState, remainingFreezeMillis }
}
