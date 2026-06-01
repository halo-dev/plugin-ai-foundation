import { describe, expect, it } from '@rstest/core'
import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('renders basic markdown', () => {
    expect(renderMarkdown('**OK**')).toContain('<strong>OK</strong>')
  })

  it('renders progressively accumulated markdown', () => {
    expect(renderMarkdown('1. one\n2. two')).toContain('<li>one</li>')
    expect(renderMarkdown('`partial')).toContain('partial')
  })

  it('sanitizes unsafe html', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>')

    expect(html).not.toContain('<img')
    expect(html).not.toContain('<script')
  })
})
