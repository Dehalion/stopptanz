const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

/**
 * Resolves once [durationMillis] have elapsed, re-deriving "how much is left" from [nowMillis] on
 * every tick instead of trusting a fixed tick size — a `setTimeout` can fire late (e.g. a
 * throttled background tab), and recomputing against the absolute deadline (rather than
 * subtracting [tickMillis] each time) stops that drift from compounding across ticks. [onTick] is
 * invoked with the remaining time after every tick, including a final call with `0`.
 */
export async function awaitDeadline(options: {
  durationMillis: number
  tickMillis: number
  nowMillis?: () => number
  sleep?: (ms: number) => Promise<void>
  onTick?: (remainingMillis: number) => void
}): Promise<void> {
  const { durationMillis, tickMillis, nowMillis = Date.now, sleep = defaultSleep, onTick = () => {} } = options
  const deadlineAt = nowMillis() + durationMillis
  let remaining = durationMillis
  while (remaining > 0) {
    await sleep(Math.min(tickMillis, remaining))
    remaining = Math.max(deadlineAt - nowMillis(), 0)
    onTick(remaining)
  }
}
