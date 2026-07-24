import { describe, expect, it } from 'vitest'
import { SEEK_STEP_MILLIS, clampSeek } from './seek'

describe('clampSeek', () => {
  it('seeks forward by the fixed step', () => {
    expect(clampSeek(5_000, SEEK_STEP_MILLIS, 60_000)).toBe(15_000)
  })

  it('seeks backward by the fixed step', () => {
    expect(clampSeek(15_000, -SEEK_STEP_MILLIS, 60_000)).toBe(5_000)
  })

  it('clamps to zero when seeking before the start', () => {
    expect(clampSeek(5_000, -SEEK_STEP_MILLIS, 60_000)).toBe(0)
  })

  it('clamps to the track end when seeking past it', () => {
    expect(clampSeek(55_000, SEEK_STEP_MILLIS, 60_000)).toBe(60_000)
  })
})
