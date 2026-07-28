// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
export type RandomSource = (min: number, max: number) => number

export const defaultRandomSource: RandomSource = (min, max) => {
  if (min === max) return min
  return min + Math.floor(Math.random() * (max - min + 1))
}
