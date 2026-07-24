import { render } from 'preact'
import { describe, expect, it } from 'vitest'
import { App } from './app'

describe('App', () => {
  it('renders the Stopptanz heading', () => {
    const container = document.createElement('div')
    render(<App />, container)
    expect(container.textContent).toContain('Stopptanz')
  })
})
