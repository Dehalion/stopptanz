import { describe, expect, it } from 'vitest'
import { buildPlaylist, isAudioFile, trackNameFromFileName } from './playlistBuilder'

interface FakeFile {
  name: string
  type: string
}

const file = (name: string, type: string): FakeFile => ({ name, type })

const uriFor = (f: FakeFile) => `blob://${f.name}`

describe('playlistBuilder', () => {
  it('builds playlist from audio-mime-type files', () => {
    const playlist = buildPlaylist(
      [file('track1.mp3', 'audio/mpeg'), file('track2.flac', 'audio/flac')],
      uriFor,
    )
    expect(playlist?.tracks).toEqual([
      { uri: 'blob://track1.mp3', name: 'track1' },
      { uri: 'blob://track2.flac', name: 'track2' },
    ])
  })

  it('falls back to extension when mime type missing', () => {
    const playlist = buildPlaylist([file('track1.mp3', '')], uriFor)
    expect(playlist?.tracks).toEqual([{ uri: 'blob://track1.mp3', name: 'track1' }])
  })

  it('track display name strips the extension only', () => {
    expect(trackNameFromFileName('My Song.Final.mp3')).toBe('My Song.Final')
  })

  it('track display name is used as-is when there is no extension', () => {
    expect(trackNameFromFileName('track1')).toBe('track1')
  })

  it('excludes non-audio files', () => {
    const playlist = buildPlaylist([file('cover.jpg', 'image/jpeg'), file('notes.txt', 'text/plain')], uriFor)
    expect(playlist).toBeNull()
  })

  it('empty file list yields null playlist', () => {
    expect(buildPlaylist([], uriFor)).toBeNull()
  })

  it('default shuffle and loop are off', () => {
    const playlist = buildPlaylist([file('track1.mp3', 'audio/mpeg')], uriFor)
    expect(playlist?.shuffle).toBe(false)
    expect(playlist?.loop).toBe(false)
  })

  it('isAudioFile detects by mime type first', () => {
    expect(isAudioFile(file('weird', 'audio/mpeg'))).toBe(true)
  })

  it('isAudioFile falls back to known extensions when mime type is empty', () => {
    expect(isAudioFile(file('track.OGA', ''))).toBe(true)
    expect(isAudioFile(file('track.txt', ''))).toBe(false)
  })
})
