// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import type { Mode } from '../engine/mode'

const KEY_MODE = 'stopptanz.mode'
const DEFAULT_MODE: Mode = 'FREEZE_DANCE'
const KEY_SHUFFLE = 'stopptanz.shuffle'
const KEY_LOOP = 'stopptanz.loop'

// Freeze Dance and Musical Chairs keep separate Stop Interval keys/defaults, matching Android's
// SessionSettings — Musical Chairs defaults to a larger interval since there's no auto-resume to
// keep the pace up.
const STOP_INTERVAL_DEFAULTS: Record<Mode, { min: number; max: number }> = {
  FREEZE_DANCE: { min: 10, max: 25 },
  MUSICAL_CHAIRS: { min: 20, max: 45 },
}

function stopIntervalKey(mode: Mode, bound: 'min' | 'max'): string {
  const suffix = mode === 'FREEZE_DANCE' ? '' : '.musicalChairs'
  return `stopptanz.stopInterval${bound === 'min' ? 'Min' : 'Max'}Seconds${suffix}`
}

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

export function loadSessionSettings(): SessionSettings {
  const mode = (localStorage.getItem(KEY_MODE) as Mode | null) ?? DEFAULT_MODE
  const defaults = STOP_INTERVAL_DEFAULTS[mode]
  return {
    mode,
    shuffle: readBoolean(KEY_SHUFFLE, false),
    loop: readBoolean(KEY_LOOP, false),
    stopIntervalMinSeconds: readNumber(stopIntervalKey(mode, 'min'), defaults.min),
    stopIntervalMaxSeconds: readNumber(stopIntervalKey(mode, 'max'), defaults.max),
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
  localStorage.setItem(stopIntervalKey(mode, 'min'), String(seconds))
}

export function saveStopIntervalMaxSeconds(mode: Mode, seconds: number): void {
  localStorage.setItem(stopIntervalKey(mode, 'max'), String(seconds))
}
