// Copyright (C) 2026 Dehalion
// SPDX-License-Identifier: GPL-3.0-only
import type { Track } from './track'

export interface Playlist {
  tracks: Track[]
  shuffle?: boolean
  loop?: boolean
}
