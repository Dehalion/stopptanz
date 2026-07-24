export interface StopInterval {
  minMillis: number
  maxMillis: number
}

export function createStopInterval(minMillis: number, maxMillis: number): StopInterval {
  if (minMillis < 0) throw new Error('minMillis must be >= 0')
  if (maxMillis < minMillis) throw new Error('maxMillis must be >= minMillis')
  return { minMillis, maxMillis }
}

/** Placeholder for callers that don't yet trigger auto Stops (no Stop Interval UI/timer wired up). */
export const unusedStopInterval: StopInterval = { minMillis: 0, maxMillis: 0 }
