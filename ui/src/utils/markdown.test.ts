import { describe, expect, it } from '@rstest/core'
import { renderMarkdown } from './markdown'

describe('renderMarkdown', () => {
  it('renders basic markdown', () => {
    expect(renderMarkdown('**OK**')).toContain('<strong>OK</strong>')
  })

  it('sanitizes unsafe html', () => {
    const html = renderMarkdown('<img src=x onerror=alert(1)>')

    expect(html).not.toContain('<img')
    expect(html).not.toContain('<script')
  })
})
