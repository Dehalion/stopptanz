// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import { describe, expect, it } from 'vitest'
import { SessionEngine } from './sessionEngine'
import type { Playlist } from './playlist'
import type { RandomSource } from './randomSource'
import { createStopInterval } from './stopInterval'
import type { Track } from './track'
import { CLOSED, FINISHED, PAUSED, PLAYING, STOPPED } from './sessionState'

const track = (name: string): Track => ({ uri: `content://${name}.mp3`, name })

const midpointRandom: RandomSource = (min, max) => min + (max - min) / 2

function engine(options: {
  mode: 'FREEZE_DANCE' | 'MUSICAL_CHAIRS'
  minMillis?: number
  maxMillis?: number
  pauseDurationMillis?: number
  playlist?: Playlist
  randomSource?: RandomSource
}) {
  const {
    mode,
    minMillis = 5_000,
    maxMillis = 15_000,
    pauseDurationMillis = 5_000,
    playlist = { tracks: [track('track1'), track('track2')] },
    randomSource = midpointRandom,
  } = options
  return new SessionEngine({
    playlist,
    mode,
    stopInterval: createStopInterval(minMillis, maxMillis),
    pauseDurationMillis,
    randomSource,
  })
}

describe('SessionEngine', () => {
  it('next stop delay is within configured interval bounds', () => {
    const delay = engine({ mode: 'FREEZE_DANCE' }).nextStopDelayMillis()
    expect(delay).not.toBeNull()
    expect(delay!).toBeGreaterThanOrEqual(5_000)
    expect(delay!).toBeLessThanOrEqual(15_000)
  })

  it('nextStopDelayMillis is null when the pick would land within the end-of-track guard', () => {
    const e = engine({ mode: 'FREEZE_DANCE', minMillis: 10_000, maxMillis: 10_000 })
    expect(e.nextStopDelayMillis(15_000)).toBeNull()
  })

  it('nextStopDelayMillis is null for a track shorter than the guard window', () => {
    const e = engine({ mode: 'FREEZE_DANCE', minMillis: 5_000, maxMillis: 5_000 })
    expect(e.nextStopDelayMillis(8_000)).toBeNull()
  })

  it('nextStopDelayMillis returns the pick when it clears the end-of-track guard', () => {
    const e = engine({ mode: 'FREEZE_DANCE', minMillis: 10_000, maxMillis: 10_000 })
    expect(e.nextStopDelayMillis(25_000)).toBe(10_000)
  })

  it('stop transitions from Playing to Stopped', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    expect(e.state).toEqual(STOPPED)
  })

  it('musical chairs mode allows manual resume from Stopped', () => {
    const e = engine({ mode: 'MUSICAL_CHAIRS' })
    e.stop()
    e.resume()
    expect(e.state).toEqual(PLAYING)
  })

  it('freeze dance mode allows manual resume from Stopped', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    e.resume()
    expect(e.state).toEqual(PLAYING)
  })

  it('freeze dance mode auto-resumes on pause elapsed', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    e.onPauseElapsed()
    expect(e.state).toEqual(PLAYING)
  })

  it('musical chairs mode rejects auto-resume', () => {
    const e = engine({ mode: 'MUSICAL_CHAIRS' })
    e.stop()
    expect(() => e.onPauseElapsed()).toThrow()
  })

  it('onPauseElapsed rejects when not Stopped', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    expect(() => e.onPauseElapsed()).toThrow()
  })

  it('pauseDurationMillis is exposed for adapter scheduling', () => {
    const e = engine({ mode: 'FREEZE_DANCE', pauseDurationMillis: 7_000 })
    expect(e.pauseDurationMillis).toBe(7_000)
  })

  it('setStopInterval does not alter a delay already computed, only the next call', () => {
    const e = engine({ mode: 'FREEZE_DANCE', minMillis: 5_000, maxMillis: 5_000 })
    const inFlightDelay = e.nextStopDelayMillis()
    e.setStopInterval(createStopInterval(20_000, 20_000))
    expect(inFlightDelay).toBe(5_000)
    expect(e.nextStopDelayMillis()).toBe(20_000)
  })

  it('setPauseDurationMillis does not alter a value already captured, only the next read', () => {
    const e = engine({ mode: 'FREEZE_DANCE', pauseDurationMillis: 5_000 })
    const capturedForInFlightPause = e.pauseDurationMillis
    e.setPauseDurationMillis(9_000)
    expect(capturedForInFlightPause).toBe(5_000)
    expect(e.pauseDurationMillis).toBe(9_000)
  })

  it('setLoop takes effect immediately, since it is read live rather than baked in at construction', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.canSkipPrevious).toBe(false)
    e.setLoop(true)
    expect(e.canSkipPrevious).toBe(true)
  })

  it('orderedTracks preserves playlist order when shuffle is off', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')], shuffle: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.orderedTracks).toEqual([track('a'), track('b'), track('c')])
  })

  it('orderedTracks is a seeded permutation when shuffle is on', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c'), track('d')], shuffle: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist, randomSource: (min) => min })
    expect(e.orderedTracks).toEqual([track('b'), track('c'), track('d'), track('a')])
  })

  it('onPlaylistEnded transitions to Finished when loop is off', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onPlaylistEnded()
    expect(e.state).toEqual(FINISHED)
  })

  it('onPlaylistEnded stays Playing when loop is on', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onPlaylistEnded()
    expect(e.state).toEqual(PLAYING)
  })

  it('close transitions from Playing to Closed', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.close()
    expect(e.state).toEqual(CLOSED)
  })

  it('close transitions from Paused to Closed', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.pause()
    e.close()
    expect(e.state).toEqual(CLOSED)
  })

  it('close transitions from Stopped to Closed', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    e.close()
    expect(e.state).toEqual(CLOSED)
  })

  it('close transitions from Finished to Closed', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onPlaylistEnded()
    e.close()
    expect(e.state).toEqual(CLOSED)
  })

  it('close rejects when already Closed', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.close()
    expect(() => e.close()).toThrow()
  })

  it('Finished state rejects Stop, Resume, and onPauseElapsed', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: false }
    const e = engine({ mode: 'MUSICAL_CHAIRS', playlist })
    e.onPlaylistEnded()
    expect(() => e.stop()).toThrow()
    expect(() => e.resume()).toThrow()
    expect(() => e.onPauseElapsed()).toThrow()
  })

  it('currentTrack starts at the first orderedTrack', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.currentTrack).toEqual(track('a'))
  })

  it('onTrackAdvanced moves currentTrack to the next orderedTrack', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onTrackAdvanced()
    expect(e.currentTrack).toEqual(track('b'))
  })

  it('onTrackAdvanced rejects advancing past the last orderedTrack when loop is off', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onTrackAdvanced()
    expect(() => e.onTrackAdvanced()).toThrow()
  })

  it('onTrackAdvanced wraps back to the first orderedTrack when looping', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onTrackAdvanced()
    e.onTrackAdvanced()
    expect(e.currentTrack).toEqual(track('a'))
  })

  it('onTrackAdvanced rejects when not Playing', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    expect(() => e.onTrackAdvanced()).toThrow()
  })

  it('nextTrack is the following orderedTrack, null on the last Track', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.nextTrack).toEqual(track('b'))
    e.onTrackAdvanced()
    expect(e.nextTrack).toBeNull()
  })

  it('remainingTracks is a true countdown when loop is off', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.remainingTracks).toEqual({ kind: 'countdown', remaining: 2 })
    e.onTrackAdvanced()
    expect(e.remainingTracks).toEqual({ kind: 'countdown', remaining: 1 })
  })

  it('remainingTracks is a Track-of-N position when loop is on', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.remainingTracks).toEqual({ kind: 'position', current: 1, total: 3 })
    e.onTrackAdvanced()
    expect(e.remainingTracks).toEqual({ kind: 'position', current: 2, total: 3 })
  })

  it('skipToNext moves currentTrack forward', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToNext()
    expect(e.currentTrack).toEqual(track('b'))
  })

  it('skipToPrevious moves currentTrack backward', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToNext()
    e.skipToPrevious()
    expect(e.currentTrack).toEqual(track('a'))
  })

  it('skipToNext is a no-op at the last orderedTrack when loop is off', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToNext()
    e.skipToNext()
    expect(e.currentTrack).toEqual(track('b'))
  })

  it('skipToPrevious is a no-op at the first orderedTrack when loop is off', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToPrevious()
    expect(e.currentTrack).toEqual(track('a'))
  })

  it('skipToNext wraps to the first orderedTrack when looping', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToNext()
    e.skipToNext()
    expect(e.currentTrack).toEqual(track('a'))
  })

  it('skipToPrevious wraps to the last orderedTrack when looping', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToPrevious()
    expect(e.currentTrack).toEqual(track('b'))
  })

  it('skipToNext and skipToPrevious leave state unchanged', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToNext()
    expect(e.state).toEqual(PLAYING)
    e.stop()
    e.skipToPrevious()
    expect(e.state).toEqual(STOPPED)
  })

  it('skipToNext and skipToPrevious work while Stopped', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.stop()
    e.skipToNext()
    expect(e.currentTrack).toEqual(track('b'))
  })

  it('skipToNext and skipToPrevious reject when not Playing or Stopped', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onPlaylistEnded()
    expect(() => e.skipToNext()).toThrow()
    expect(() => e.skipToPrevious()).toThrow()
  })

  it('canSkipPrevious and canSkipNext are false at the ends when loop is off', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.canSkipPrevious).toBe(false)
    expect(e.canSkipNext).toBe(true)
    e.skipToNext()
    e.skipToNext()
    expect(e.canSkipPrevious).toBe(true)
    expect(e.canSkipNext).toBe(false)
  })

  it('canSkipPrevious and canSkipNext are true at the ends when loop is on', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.canSkipPrevious).toBe(true)
    expect(e.canSkipNext).toBe(true)
  })

  it('canSkipPrevious and canSkipNext are false with a single Track regardless of loop', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    expect(e.canSkipPrevious).toBe(false)
    expect(e.canSkipNext).toBe(false)
  })

  it('pause from Playing then resumeFromPause returns to Playing', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.pause()
    expect(e.state).toEqual(PAUSED(PLAYING, null))
    const remaining = e.resumeFromPause()
    expect(e.state).toEqual(PLAYING)
    expect(remaining).toBeNull()
  })

  it('pause from Stopped mid-countdown then resumeFromPause restores Stopped with remaining time', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    e.pause(3_000)
    expect(e.state).toEqual(PAUSED(STOPPED, 3_000))
    const remaining = e.resumeFromPause()
    expect(e.state).toEqual(STOPPED)
    expect(remaining).toBe(3_000)
  })

  it('onPauseElapsed rejects while Paused, so the freeze auto-resume timer cannot fire', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    e.pause(3_000)
    expect(() => e.onPauseElapsed()).toThrow()
  })

  it('pause rejects when not Playing or Stopped', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onPlaylistEnded()
    expect(() => e.pause()).toThrow()
  })

  it('pause rejects when already Paused', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.pause()
    expect(() => e.pause()).toThrow()
  })

  it('resumeFromPause rejects when not Paused', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    expect(() => e.resumeFromPause()).toThrow()
  })

  it("pause from Stopped in Freeze Dance mode requires remainingFreezeMillis so the countdown can't be silently lost", () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    e.stop()
    expect(() => e.pause()).toThrow()
  })

  it('pause rejects remainingFreezeMillis when pausing from Playing', () => {
    const e = engine({ mode: 'FREEZE_DANCE' })
    expect(() => e.pause(3_000)).toThrow()
  })

  it('pause rejects remainingFreezeMillis when pausing from Stopped in Musical Chairs mode', () => {
    const e = engine({ mode: 'MUSICAL_CHAIRS' })
    e.stop()
    expect(() => e.pause(3_000)).toThrow()
  })

  it('skipToNext works during meta-Pause', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')] }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.pause()
    e.skipToNext()
    expect(e.currentTrack).toEqual(track('b'))
    expect(e.state.kind).toBe('paused')
  })

  it('skipToPrevious works during meta-Pause', () => {
    const playlist: Playlist = { tracks: [track('a'), track('b'), track('c')], loop: true }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.skipToNext()
    e.pause()
    e.skipToPrevious()
    expect(e.currentTrack).toEqual(track('a'))
    expect(e.state.kind).toBe('paused')
  })

  it('skipToNext rejects when Finished', () => {
    const playlist: Playlist = { tracks: [track('a')], loop: false }
    const e = engine({ mode: 'FREEZE_DANCE', playlist })
    e.onPlaylistEnded()
    expect(() => e.skipToNext()).toThrow()
  })
})
