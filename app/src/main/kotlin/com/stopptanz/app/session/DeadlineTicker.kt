// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.session

import kotlinx.coroutines.delay

/**
 * Suspends until [durationMillis] have elapsed, re-deriving "how much is left" from
 * [System.currentTimeMillis] on every tick instead of trusting a fixed tick size — a coroutine
 * `delay()` can run long under Doze/background CPU throttling, and recomputing against the
 * absolute deadline (rather than subtracting [tickMillis] each time) stops that drift from
 * compounding across ticks. [onTick] is invoked with the remaining time after every tick,
 * including a final call with `0`.
 */
suspend fun awaitDeadline(
    durationMillis: Long,
    tickMillis: Long,
    nowMillis: () -> Long = System::currentTimeMillis,
    onTick: (remainingMillis: Long) -> Unit = {},
) {
    val deadlineAt = nowMillis() + durationMillis
    var remaining = durationMillis
    while (remaining > 0) {
        delay(minOf(tickMillis, remaining))
        remaining = (deadlineAt - nowMillis()).coerceAtLeast(0)
        onTick(remaining)
    }
}
