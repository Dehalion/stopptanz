import type { Playlist } from '../engine/playlist'

const audioExtensions = new Set(['mp3', 'm4a', 'flac', 'wav', 'ogg', 'oga', 'aac', 'opus'])

interface PickedFile {
  name: string
  type: string
}

export function trackNameFromFileName(fileName: string): string {
  const dot = fileName.lastIndexOf('.')
  return dot === -1 ? fileName : fileName.slice(0, dot)
}

export function isAudioFile(file: PickedFile): boolean {
  if (file.type) return file.type.startsWith('audio/')
  const dot = file.name.lastIndexOf('.')
  const extension = dot === -1 ? '' : file.name.slice(dot + 1).toLowerCase()
  return audioExtensions.has(extension)
}

export function buildPlaylist<F extends PickedFile>(files: F[], uriFor: (file: F) => string): Playlist | null {
  const tracks = files
    .filter(isAudioFile)
    .map((file) => ({ uri: uriFor(file), name: trackNameFromFileName(file.name) }))
  return tracks.length === 0 ? null : { tracks, shuffle: false, loop: false }
}
