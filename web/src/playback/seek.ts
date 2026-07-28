// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
export const SEEK_STEP_MILLIS = 10_000

/** Seeks by `deltaMillis` from `currentMillis`, clamped to `[0, totalMillis]`. */
export function clampSeek(currentMillis: number, deltaMillis: number, totalMillis: number): number {
  return Math.min(Math.max(currentMillis + deltaMillis, 0), totalMillis)
}
