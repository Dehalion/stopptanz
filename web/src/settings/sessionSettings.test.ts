import { beforeEach, describe, expect, it } from 'vitest'
import {
  loadSessionSettings,
  saveLoop,
  saveMode,
  saveShuffle,
  saveStopIntervalMaxSeconds,
  saveStopIntervalMinSeconds,
} from './sessionSettings'

beforeEach(() => {
  localStorage.clear()
})

describe('sessionSettings', () => {
  it('defaults to Freeze Dance, shuffle/loop off, and mode-specific Stop Interval defaults', () => {
    const settings = loadSessionSettings()
    expect(settings.mode).toBe('FREEZE_DANCE')
    expect(settings.shuffle).toBe(false)
    expect(settings.loop).toBe(false)
    expect(settings.stopIntervalMinSeconds).toBe(10)
    expect(settings.stopIntervalMaxSeconds).toBe(25)
  })

  it('Musical Chairs has its own, larger default Stop Interval', () => {
    saveMode('MUSICAL_CHAIRS')
    const settings = loadSessionSettings()
    expect(settings.stopIntervalMinSeconds).toBe(20)
    expect(settings.stopIntervalMaxSeconds).toBe(45)
  })

  it('persists mode across loads', () => {
    saveMode('MUSICAL_CHAIRS')
    expect(loadSessionSettings().mode).toBe('MUSICAL_CHAIRS')
  })

  it('persists shuffle and loop independently', () => {
    saveShuffle(true)
    saveLoop(true)
    const settings = loadSessionSettings()
    expect(settings.shuffle).toBe(true)
    expect(settings.loop).toBe(true)
  })

  it('persists Stop Interval min/max per mode without cross-contamination', () => {
    saveMode('FREEZE_DANCE')
    saveStopIntervalMinSeconds('FREEZE_DANCE', 3)
    saveStopIntervalMaxSeconds('FREEZE_DANCE', 8)
    saveStopIntervalMinSeconds('MUSICAL_CHAIRS', 30)
    saveStopIntervalMaxSeconds('MUSICAL_CHAIRS', 60)

    saveMode('FREEZE_DANCE')
    expect(loadSessionSettings()).toMatchObject({ stopIntervalMinSeconds: 3, stopIntervalMaxSeconds: 8 })

    saveMode('MUSICAL_CHAIRS')
    expect(loadSessionSettings()).toMatchObject({ stopIntervalMinSeconds: 30, stopIntervalMaxSeconds: 60 })
  })

  it('survives being reloaded from a fresh read of localStorage', () => {
    saveMode('MUSICAL_CHAIRS')
    saveShuffle(true)
    saveStopIntervalMinSeconds('MUSICAL_CHAIRS', 15)
    const reloaded = loadSessionSettings()
    expect(reloaded).toEqual({
      mode: 'MUSICAL_CHAIRS',
      shuffle: true,
      loop: false,
      stopIntervalMinSeconds: 15,
      stopIntervalMaxSeconds: 45,
    })
  })
})
