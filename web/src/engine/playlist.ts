import type { Track } from './track'

export interface Playlist {
  tracks: Track[]
  shuffle?: boolean
  loop?: boolean
}
