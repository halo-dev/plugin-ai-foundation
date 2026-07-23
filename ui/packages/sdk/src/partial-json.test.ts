import { describe, expect, it } from '@rstest/core'
import { fixJson, parsePartialJson } from './partial-json'

describe('partial JSON', () => {
  it.each([
    ['{"name":"Halo', { name: 'Halo' }],
    ['[1,2', [1, 2]],
    ['"Halo', 'Halo'],
    ['{"escaped":"line\\nnext', { escaped: 'line\nnext' }],
    ['tru', true],
    ['nul', null],
    ['1e', 1],
  ])('repairs incomplete JSON %s', (input, expected) => {
    expect(parsePartialJson(input)).toEqual(expected)
  })

  it('preserves complete JSON and returns undefined for unrepairable input', () => {
    expect(parsePartialJson('{"ok":true}')).toEqual({ ok: true })
    expect(parsePartialJson('}{')).toBeUndefined()
    expect(parsePartialJson(undefined)).toBeUndefined()
  })

  it('drops an incomplete object field until its value becomes available', () => {
    expect(fixJson('{"complete":1,"pending"')).toBe('{"complete":1}')
    expect(parsePartialJson('{"complete":1,"pending"')).toEqual({ complete: 1 })
  })
})
