const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms))

/**
 * Resolves once [durationMillis] have elapsed, re-deriving "how much is left" from [nowMillis] on
 * every tick instead of trusting a fixed tick size — a `setTimeout` can fire late (e.g. a
 * throttled background tab), and recomputing against the absolute deadline (rather than
 * subtracting [tickMillis] each time) stops that drift from compounding across ticks. [onTick] is
 * invoked with the remaining time after every tick, including a final call with `0`.
 *
 * If [isCancelled] starts reporting `true`, the loop stops after the tick in progress without a
 * final zero tick — the caller is expected to treat cancellation as "never fired".
 */
export async function awaitDeadline(options: {
  durationMillis: number
  tickMillis: number
  nowMillis?: () => number
  sleep?: (ms: number) => Promise<void>
  isCancelled?: () => boolean
  onTick?: (remainingMillis: number) => void
}): Promise<void> {
  const {
    durationMillis,
    tickMillis,
    nowMillis = Date.now,
    sleep = defaultSleep,
    isCancelled = () => false,
    onTick = () => {},
  } = options
  const deadlineAt = nowMillis() + durationMillis
  let remaining = durationMillis
  while (remaining > 0) {
    await sleep(Math.min(tickMillis, remaining))
    if (isCancelled()) return
    remaining = Math.max(deadlineAt - nowMillis(), 0)
    onTick(remaining)
  }
}
