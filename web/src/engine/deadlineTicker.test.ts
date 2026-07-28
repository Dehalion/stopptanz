// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import { describe, expect, it } from 'vitest'
import { awaitDeadline } from './deadlineTicker'

describe('deadlineTicker', () => {
  it('ticks down to zero over the expected number of ticks under a perfect clock', async () => {
    const remainingLog: number[] = []
    let now = 0
    const nowMillis = () => now
    const sleep = async (ms: number) => {
      now += ms
    }
    await awaitDeadline({
      durationMillis: 3_000,
      tickMillis: 1_000,
      nowMillis,
      sleep,
      onTick: (remaining) => remainingLog.push(remaining),
    })
    expect(remainingLog).toEqual([2_000, 1_000, 0])
  })

  it('exits early when a wake-up arrives much later than the nominal tick', async () => {
    // Simulates a timer firing late (e.g. background tab throttling): the very first wake-up
    // reports a wall-clock jump far past the configured duration. A fixed "remaining -=
    // tickMillis" countdown would ignore this and still take 3 ticks to reach zero; a
    // deadline-recompute loop notices the overshoot and exits after just 1.
    let callCount = 0
    const nowMillis = () => {
      const value = callCount === 0 ? 0 : 10_000
      callCount++
      return value
    }
    const sleep = async () => {}
    const remainingLog: number[] = []
    await awaitDeadline({
      durationMillis: 3_000,
      tickMillis: 1_000,
      nowMillis,
      sleep,
      onTick: (remaining) => remainingLog.push(remaining),
    })
    expect(remainingLog).toEqual([0])
  })

  it('fires onTick with zero exactly once at the end', async () => {
    let now = 0
    const nowMillis = () => now
    const sleep = async (ms: number) => {
      now += ms
    }
    const remainingLog: number[] = []
    await awaitDeadline({
      durationMillis: 500,
      tickMillis: 1_000,
      nowMillis,
      sleep,
      onTick: (remaining) => remainingLog.push(remaining),
    })
    expect(remainingLog).toEqual([0])
  })

  it('stops ticking once isCancelled reports true, without a final zero tick', async () => {
    let now = 0
    let cancelled = false
    const nowMillis = () => now
    const sleep = async (ms: number) => {
      now += ms
      if (now >= 2_000) cancelled = true
    }
    const remainingLog: number[] = []
    await awaitDeadline({
      durationMillis: 3_000,
      tickMillis: 1_000,
      nowMillis,
      sleep,
      isCancelled: () => cancelled,
      onTick: (remaining) => remainingLog.push(remaining),
    })
    expect(remainingLog).toEqual([2_000])
  })
})
