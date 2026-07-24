/** Live playback position for the current Track, polled from the player. */
export interface PlaybackPosition {
  currentMillis: number
  totalMillis: number
}

function format(millis: number): string {
  const totalSeconds = Math.max(Math.floor(millis / 1000), 0)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

export function formatCurrent(position: PlaybackPosition): string {
  return format(position.currentMillis)
}

export function formatTotal(position: PlaybackPosition): string {
  return format(position.totalMillis)
}
