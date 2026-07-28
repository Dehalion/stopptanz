// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import { describe, expect, it } from 'vitest'
import { moveDown, moveUp, remove } from './trackReview'
import type { Track } from '../engine/track'

const track = (name: string): Track => ({ uri: `content://${name}`, name })
const tracks = [track('a'), track('b'), track('c')]

describe('trackReview', () => {
  it('moveUp swaps with previous track', () => {
    expect(moveUp(tracks, 1)).toEqual([track('b'), track('a'), track('c')])
  })

  it('moveUp at index 0 is a no-op', () => {
    expect(moveUp(tracks, 0)).toEqual(tracks)
  })

  it('moveUp with out-of-range index is a no-op', () => {
    expect(moveUp(tracks, 3)).toEqual(tracks)
    expect(moveUp(tracks, -1)).toEqual(tracks)
  })

  it('moveDown swaps with next track', () => {
    expect(moveDown(tracks, 1)).toEqual([track('a'), track('c'), track('b')])
  })

  it('moveDown at last index is a no-op', () => {
    expect(moveDown(tracks, 2)).toEqual(tracks)
  })

  it('moveDown with out-of-range index is a no-op', () => {
    expect(moveDown(tracks, 3)).toEqual(tracks)
    expect(moveDown(tracks, -1)).toEqual(tracks)
  })

  it('remove drops the track at index', () => {
    expect(remove(tracks, 1)).toEqual([track('a'), track('c')])
  })

  it('remove with out-of-range index is a no-op', () => {
    expect(remove(tracks, 3)).toEqual(tracks)
    expect(remove(tracks, -1)).toEqual(tracks)
  })

  it('operations on an empty list are no-ops', () => {
    expect(moveUp([], 0)).toEqual([])
    expect(moveDown([], 0)).toEqual([])
    expect(remove([], 0)).toEqual([])
  })
})
