// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import type { Track } from '../engine/track'

/** Pure in-memory reorder/remove edits for the Playlist review screen — never persisted to disk. */

export function moveUp(tracks: Track[], index: number): Track[] {
  if (index < 1 || index > tracks.length - 1) return tracks
  const result = [...tracks]
  ;[result[index - 1], result[index]] = [result[index], result[index - 1]]
  return result
}

export function moveDown(tracks: Track[], index: number): Track[] {
  return moveUp(tracks, index + 1)
}

export function remove(tracks: Track[], index: number): Track[] {
  if (index < 0 || index > tracks.length - 1) return tracks
  return tracks.filter((_, i) => i !== index)
}
