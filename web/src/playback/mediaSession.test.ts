import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearActionHandlers, isMediaSessionSupported, setActionHandlers, setTrackMetadata } from './mediaSession'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('mediaSession', () => {
  it('reports unsupported when navigator has no mediaSession', () => {
    vi.stubGlobal('navigator', {})
    expect(isMediaSessionSupported()).toBe(false)
  })

  it('reports supported when navigator.mediaSession exists', () => {
    vi.stubGlobal('navigator', { mediaSession: {} })
    expect(isMediaSessionSupported()).toBe(true)
  })

  it('setTrackMetadata no-ops without throwing when unsupported', () => {
    vi.stubGlobal('navigator', {})
    expect(() => setTrackMetadata('My Track')).not.toThrow()
  })

  it('setTrackMetadata sets a MediaMetadata with the track title when supported', () => {
    const mediaSession: { metadata: unknown } = { metadata: null }
    vi.stubGlobal('navigator', { mediaSession })
    vi.stubGlobal(
      'MediaMetadata',
      class {
        title: string
        constructor(init: { title: string }) {
          this.title = init.title
        }
      },
    )
    setTrackMetadata('My Track')
    expect((mediaSession.metadata as { title: string }).title).toBe('My Track')
  })

  it('setActionHandlers no-ops without throwing when unsupported', () => {
    vi.stubGlobal('navigator', {})
    expect(() => setActionHandlers({ play: () => {}, pause: () => {} })).not.toThrow()
  })

  it('setActionHandlers registers each provided handler when supported', () => {
    const registered = new Map<string, unknown>()
    vi.stubGlobal('navigator', {
      mediaSession: {
        setActionHandler: (action: string, handler: unknown) => registered.set(action, handler),
      },
    })
    const play = () => {}
    const pause = () => {}
    setActionHandlers({ play, pause })
    expect(registered.get('play')).toBe(play)
    expect(registered.get('pause')).toBe(pause)
  })

  it('setActionHandlers tolerates a browser throwing on an unsupported action type', () => {
    vi.stubGlobal('navigator', {
      mediaSession: {
        setActionHandler: (action: string) => {
          if (action === 'previoustrack') throw new TypeError('unsupported action')
        },
      },
    })
    expect(() => setActionHandlers({ previoustrack: () => {}, nexttrack: () => {} })).not.toThrow()
  })

  it('clearActionHandlers clears all four handlers when supported', () => {
    const registered = new Map<string, unknown>()
    vi.stubGlobal('navigator', {
      mediaSession: {
        setActionHandler: (action: string, handler: unknown) => registered.set(action, handler),
      },
    })
    clearActionHandlers()
    expect(registered.get('play')).toBeNull()
    expect(registered.get('pause')).toBeNull()
    expect(registered.get('previoustrack')).toBeNull()
    expect(registered.get('nexttrack')).toBeNull()
  })

  it('clearActionHandlers no-ops without throwing when unsupported', () => {
    vi.stubGlobal('navigator', {})
    expect(() => clearActionHandlers()).not.toThrow()
  })
})
