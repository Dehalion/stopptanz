// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import { describe, expect, it } from 'vitest'
import { SessionEngine } from '../engine/sessionEngine'
import { createStopInterval } from '../engine/stopInterval'
import type { Track } from '../engine/track'
import { SessionOrchestrator, type PlaybackIo, type TimerHandle } from './sessionOrchestrator'

const track = (name: string): Track => ({ uri: `content://${name}`, name })

interface FakeTimer extends TimerHandle {
  durationMillis: number
  onFire: () => void
  onTick?: (remaining: number) => void
  cancelled: boolean
}

class FakeIo implements PlaybackIo {
  playing = false
  timers: FakeTimer[] = []
  remainingTrackMillis = Number.MAX_SAFE_INTEGER

  playAudio(): void {
    this.playing = true
  }

  pauseAudio(): void {
    this.playing = false
  }

  getRemainingTrackMillis(): number {
    return this.remainingTrackMillis
  }

  scheduleTimer(durationMillis: number, onFire: () => void, onTick?: (remaining: number) => void): FakeTimer {
    const timer: FakeTimer = {
      durationMillis,
      onFire,
      onTick,
      cancelled: false,
      cancel() {
        this.cancelled = true
      },
    }
    this.timers.push(timer)
    return timer
  }

  /** Test helper: simulates a scheduled timer reaching its deadline. */
  fire(timer: FakeTimer) {
    if (!timer.cancelled) timer.onFire()
  }

  get latestTimer(): FakeTimer {
    return this.timers[this.timers.length - 1]
  }
}

function setup(mode: 'FREEZE_DANCE' | 'MUSICAL_CHAIRS', tracks: Track[] = [track('a'), track('b')]) {
  const engine = new SessionEngine({
    playlist: { tracks },
    mode,
    stopInterval: createStopInterval(5_000, 5_000),
    pauseDurationMillis: 4_000,
    randomSource: (min) => min,
  })
  const io = new FakeIo()
  const stateLog: string[] = []
  const orchestrator = new SessionOrchestrator(engine, io, {
    onStateChanged: (s) => stateLog.push(s.kind),
  })
  return { engine, io, orchestrator, stateLog }
}

describe('SessionOrchestrator', () => {
  it('schedules an auto-Stop within the configured interval on start', () => {
    const { io, orchestrator } = setup('FREEZE_DANCE')
    orchestrator.start()
    expect(io.timers).toHaveLength(1)
    expect(io.latestTimer.durationMillis).toBe(5_000)
  })

  it('firing the auto-Stop timer Stops playback', () => {
    const { io, orchestrator, engine } = setup('FREEZE_DANCE')
    orchestrator.start()
    io.fire(io.latestTimer)
    expect(engine.state.kind).toBe('stopped')
    expect(io.playing).toBe(false)
  })

  it('Freeze Dance mode schedules an auto-resume after Stop', () => {
    const { io, orchestrator } = setup('FREEZE_DANCE')
    orchestrator.start()
    io.fire(io.latestTimer) // auto-stop fires
    expect(io.timers).toHaveLength(2)
    expect(io.latestTimer.durationMillis).toBe(4_000) // pauseDurationMillis
  })

  it('firing the auto-resume timer resumes playback and reschedules auto-Stop', () => {
    const { io, orchestrator, engine } = setup('FREEZE_DANCE')
    orchestrator.start()
    io.fire(io.latestTimer) // auto-stop
    io.fire(io.latestTimer) // auto-resume
    expect(engine.state.kind).toBe('playing')
    expect(io.playing).toBe(true)
    expect(io.timers).toHaveLength(3)
  })

  it('manual resume during the freeze countdown cancels the pending auto-resume', () => {
    const { io, orchestrator, engine } = setup('FREEZE_DANCE')
    orchestrator.start()
    io.fire(io.latestTimer) // auto-stop -> Stopped, auto-resume scheduled
    const autoResumeTimer = io.latestTimer
    orchestrator.resume()
    expect(engine.state.kind).toBe('playing')
    io.fire(autoResumeTimer) // late-arriving auto-resume must be a no-op
    expect(engine.state.kind).toBe('playing')
  })

  it('Musical Chairs mode stays Stopped after auto-Stop, with no auto-resume scheduled', () => {
    const { io, orchestrator, engine } = setup('MUSICAL_CHAIRS')
    orchestrator.start()
    io.fire(io.latestTimer)
    expect(engine.state.kind).toBe('stopped')
    expect(io.timers).toHaveLength(1) // only the auto-stop, no auto-resume
  })

  it('Musical Chairs mode requires an explicit resume', () => {
    const { io, orchestrator, engine } = setup('MUSICAL_CHAIRS')
    orchestrator.start()
    io.fire(io.latestTimer)
    orchestrator.resume()
    expect(engine.state.kind).toBe('playing')
  })

  it('manual stop cancels the pending auto-Stop and Stops immediately', () => {
    const { io, orchestrator, engine } = setup('MUSICAL_CHAIRS')
    orchestrator.start()
    const autoStopTimer = io.latestTimer
    orchestrator.stop()
    expect(engine.state.kind).toBe('stopped')
    expect(autoStopTimer.cancelled).toBe(true)
  })

  it('pause from Playing suspends, resumeFromPause restores Playing and reschedules auto-Stop', () => {
    const { io, orchestrator, engine } = setup('FREEZE_DANCE')
    orchestrator.start()
    orchestrator.pause()
    expect(engine.state).toEqual({ kind: 'paused', resumedState: { kind: 'playing' }, remainingFreezeMillis: null })
    expect(io.playing).toBe(false)
    orchestrator.resumeFromPause()
    expect(engine.state.kind).toBe('playing')
    expect(io.playing).toBe(true)
  })

  it('pause mid freeze-countdown captures the remaining time and resumeFromPause restores the countdown', () => {
    const { io, orchestrator, engine } = setup('FREEZE_DANCE')
    orchestrator.start()
    io.fire(io.latestTimer) // auto-stop -> Stopped, auto-resume scheduled with onTick
    const autoResumeTimer = io.latestTimer
    autoResumeTimer.onTick?.(2_500) // simulate one tick of the countdown
    orchestrator.pause()
    expect(engine.state).toMatchObject({ kind: 'paused', remainingFreezeMillis: 2_500 })
    expect(autoResumeTimer.cancelled).toBe(true)

    orchestrator.resumeFromPause()
    expect(engine.state.kind).toBe('stopped')
    expect(io.latestTimer.durationMillis).toBe(2_500)
  })

  it('reaching the end of a non-looping Playlist Finishes the Session and schedules no further timers', () => {
    const { io, orchestrator, engine } = setup('FREEZE_DANCE', [track('a')])
    orchestrator.start()
    orchestrator.trackEnded()
    expect(engine.state.kind).toBe('finished')
    expect(io.timers.filter((t) => !t.cancelled)).toHaveLength(0)
  })
})
