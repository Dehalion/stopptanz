// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
/** A single audio file within a Playlist. `name` is the display name shown to the host.
 *  `missing` marks a Playlist File entry that didn't resolve to a file; excluded from playback. */
export interface Track {
  uri: string
  name: string
  missing?: boolean
}

/** How many Tracks remain in the Playlist, shaped differently depending on `Playlist.loop`. */
export type TrackRemaining =
  | { kind: 'position'; current: number; total: number }
  | { kind: 'countdown'; remaining: number }

export interface TrackStatus {
  current: Track
  next: Track | null
  remaining: TrackRemaining
  canSkipPrevious: boolean
  canSkipNext: boolean
}
