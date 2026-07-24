export interface MediaSessionActions {
  play?: () => void
  pause?: () => void
  previoustrack?: () => void
  nexttrack?: () => void
}

const ACTION_TYPES: (keyof MediaSessionActions)[] = ['play', 'pause', 'previoustrack', 'nexttrack']

export function isMediaSessionSupported(): boolean {
  return typeof navigator !== 'undefined' && 'mediaSession' in navigator && Boolean(navigator.mediaSession)
}

/** Sets the OS media UI's track title — a no-op in browsers without MediaSession support. */
export function setTrackMetadata(title: string): void {
  if (!isMediaSessionSupported() || typeof MediaMetadata === 'undefined') return
  navigator.mediaSession.metadata = new MediaMetadata({ title })
}

/** Registers action handlers for whichever of play/pause/previoustrack/nexttrack are provided —
 * unset actions are left untouched. Some browsers throw on action types they don't support
 * (e.g. Safari on `previoustrack`); each registration is tolerated independently so one
 * unsupported action doesn't stop the rest from being wired up. No-ops entirely when
 * MediaSession isn't supported at all. */
export function setActionHandlers(actions: MediaSessionActions): void {
  if (!isMediaSessionSupported()) return
  for (const action of ACTION_TYPES) {
    const handler = actions[action]
    if (!handler) continue
    trySetActionHandler(action, handler)
  }
}

/** Clears all four action handlers — a no-op in browsers without MediaSession support. */
export function clearActionHandlers(): void {
  if (!isMediaSessionSupported()) return
  for (const action of ACTION_TYPES) {
    trySetActionHandler(action, null)
  }
}

function trySetActionHandler(action: keyof MediaSessionActions, handler: MediaSessionActionHandler | null): void {
  try {
    navigator.mediaSession.setActionHandler(action, handler)
  } catch {
    // Browser doesn't support this action type — leave it unregistered.
  }
}
