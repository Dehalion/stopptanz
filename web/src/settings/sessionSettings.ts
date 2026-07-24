import type { Mode } from '../engine/mode'

const KEY_MODE = 'stopptanz.mode'
const DEFAULT_MODE: Mode = 'FREEZE_DANCE'
const KEY_SHUFFLE = 'stopptanz.shuffle'
const KEY_LOOP = 'stopptanz.loop'

// Freeze Dance and Musical Chairs keep separate Stop Interval keys/defaults, matching Android's
// SessionSettings — Musical Chairs defaults to a larger interval since there's no auto-resume to
// keep the pace up.
const KEY_STOP_INTERVAL_MIN_SECONDS_FREEZE_DANCE = 'stopptanz.stopIntervalMinSeconds'
const DEFAULT_STOP_INTERVAL_MIN_SECONDS_FREEZE_DANCE = 10
const KEY_STOP_INTERVAL_MAX_SECONDS_FREEZE_DANCE = 'stopptanz.stopIntervalMaxSeconds'
const DEFAULT_STOP_INTERVAL_MAX_SECONDS_FREEZE_DANCE = 25
const KEY_STOP_INTERVAL_MIN_SECONDS_MUSICAL_CHAIRS = 'stopptanz.stopIntervalMinSeconds.musicalChairs'
const DEFAULT_STOP_INTERVAL_MIN_SECONDS_MUSICAL_CHAIRS = 20
const KEY_STOP_INTERVAL_MAX_SECONDS_MUSICAL_CHAIRS = 'stopptanz.stopIntervalMaxSeconds.musicalChairs'
const DEFAULT_STOP_INTERVAL_MAX_SECONDS_MUSICAL_CHAIRS = 45

export interface SessionSettings {
  mode: Mode
  shuffle: boolean
  loop: boolean
  stopIntervalMinSeconds: number
  stopIntervalMaxSeconds: number
}

function readNumber(key: string, defaultValue: number): number {
  const raw = localStorage.getItem(key)
  if (raw === null) return defaultValue
  const value = Number(raw)
  return Number.isFinite(value) ? value : defaultValue
}

function readBoolean(key: string, defaultValue: boolean): boolean {
  const raw = localStorage.getItem(key)
  return raw === null ? defaultValue : raw === 'true'
}

function minKeyAndDefault(mode: Mode): [string, number] {
  return mode === 'FREEZE_DANCE'
    ? [KEY_STOP_INTERVAL_MIN_SECONDS_FREEZE_DANCE, DEFAULT_STOP_INTERVAL_MIN_SECONDS_FREEZE_DANCE]
    : [KEY_STOP_INTERVAL_MIN_SECONDS_MUSICAL_CHAIRS, DEFAULT_STOP_INTERVAL_MIN_SECONDS_MUSICAL_CHAIRS]
}

function maxKeyAndDefault(mode: Mode): [string, number] {
  return mode === 'FREEZE_DANCE'
    ? [KEY_STOP_INTERVAL_MAX_SECONDS_FREEZE_DANCE, DEFAULT_STOP_INTERVAL_MAX_SECONDS_FREEZE_DANCE]
    : [KEY_STOP_INTERVAL_MAX_SECONDS_MUSICAL_CHAIRS, DEFAULT_STOP_INTERVAL_MAX_SECONDS_MUSICAL_CHAIRS]
}

export function loadSessionSettings(): SessionSettings {
  const mode = (localStorage.getItem(KEY_MODE) as Mode | null) ?? DEFAULT_MODE
  const [minKey, minDefault] = minKeyAndDefault(mode)
  const [maxKey, maxDefault] = maxKeyAndDefault(mode)
  return {
    mode,
    shuffle: readBoolean(KEY_SHUFFLE, false),
    loop: readBoolean(KEY_LOOP, false),
    stopIntervalMinSeconds: readNumber(minKey, minDefault),
    stopIntervalMaxSeconds: readNumber(maxKey, maxDefault),
  }
}

export function saveMode(mode: Mode): void {
  localStorage.setItem(KEY_MODE, mode)
}

export function saveShuffle(shuffle: boolean): void {
  localStorage.setItem(KEY_SHUFFLE, String(shuffle))
}

export function saveLoop(loop: boolean): void {
  localStorage.setItem(KEY_LOOP, String(loop))
}

export function saveStopIntervalMinSeconds(mode: Mode, seconds: number): void {
  const [key] = minKeyAndDefault(mode)
  localStorage.setItem(key, String(seconds))
}

export function saveStopIntervalMaxSeconds(mode: Mode, seconds: number): void {
  const [key] = maxKeyAndDefault(mode)
  localStorage.setItem(key, String(seconds))
}
