// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import { describe, expect, it } from 'vitest'
import { formatCurrent, formatTotal } from './playbackPosition'

describe('playbackPosition', () => {
  it('formats sub-minute duration', () => {
    expect(formatCurrent({ currentMillis: 5_000, totalMillis: 60_000 })).toBe('0:05')
  })

  it('formats minutes and seconds', () => {
    expect(formatCurrent({ currentMillis: 65_000, totalMillis: 200_000 })).toBe('1:05')
  })

  it('pads seconds below ten', () => {
    expect(formatCurrent({ currentMillis: 123_000, totalMillis: 200_000 })).toBe('2:03')
  })

  it('formats total alongside current', () => {
    expect(formatTotal({ currentMillis: 0, totalMillis: 201_000 })).toBe('3:21')
  })

  it('negative total (unknown duration) formats as zero', () => {
    expect(formatTotal({ currentMillis: 0, totalMillis: -1 })).toBe('0:00')
  })
})
