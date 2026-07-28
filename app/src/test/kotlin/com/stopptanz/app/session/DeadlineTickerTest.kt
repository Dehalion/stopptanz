// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
package com.stopptanz.app.session

import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeadlineTickerTest {

    @Test
    fun `awaitDeadline ticks down to zero over the expected number of ticks under a perfect clock`() = runTest {
        val remainingLog = mutableListOf<Long>()
        awaitDeadline(durationMillis = 3_000, tickMillis = 1_000, nowMillis = { currentTime }) { remaining ->
            remainingLog.add(remaining)
        }
        assertEquals(listOf(2_000L, 1_000L, 0L), remainingLog)
    }

    @Test
    fun `awaitDeadline exits early when a wake-up arrives much later than the nominal tick`() = runTest {
        // Simulates a coroutine `delay()` call running long (e.g. Doze/background CPU throttling):
        // the very first wake-up reports a wall-clock jump far past the configured duration. A
        // fixed "remaining -= tickMillis" countdown would ignore this and still take 3 ticks to
        // reach zero; a deadline-recompute loop notices the overshoot and exits after just 1.
        var callCount = 0
        val nowMillis = {
            val value = if (callCount == 0) 0L else 10_000L
            callCount++
            value
        }
        val remainingLog = mutableListOf<Long>()
        awaitDeadline(durationMillis = 3_000, tickMillis = 1_000, nowMillis = nowMillis) { remaining ->
            remainingLog.add(remaining)
        }
        assertEquals(listOf(0L), remainingLog)
    }

    @Test
    fun `awaitDeadline fires onTick with zero exactly once at the end`() = runTest {
        val remainingLog = mutableListOf<Long>()
        awaitDeadline(durationMillis = 500, tickMillis = 1_000, nowMillis = { currentTime }) { remaining ->
            remainingLog.add(remaining)
        }
        assertEquals(listOf(0L), remainingLog)
    }
}
