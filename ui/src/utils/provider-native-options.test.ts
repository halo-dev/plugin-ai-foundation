import { describe, expect, it } from 'vitest'
import { formatProviderNativeOptions, parseProviderNativeOptions } from './provider-native-options'

describe('provider native options', () => {
  it('parses administrator-owned JSON objects', () => {
    expect(parseProviderNativeOptions('{"thinking_budget":4096}')).toEqual({
      thinking_budget: 4096,
    })
  })

  it('treats blank and empty objects as unset', () => {
    expect(parseProviderNativeOptions('')).toBeUndefined()
    expect(parseProviderNativeOptions('  ')).toBeUndefined()
    expect(parseProviderNativeOptions('{}')).toBeUndefined()
  })

  it('rejects values that are not JSON objects', () => {
    expect(() => parseProviderNativeOptions('[]')).toThrow('供应商原生参数必须是 JSON 对象。')
    expect(() => parseProviderNativeOptions('null')).toThrow('供应商原生参数必须是 JSON 对象。')
  })

  it('formats populated objects for editing', () => {
    expect(formatProviderNativeOptions({ quality: 'hd' })).toBe('{\n  "quality": "hd"\n}')
    expect(formatProviderNativeOptions()).toBe('')
  })
})
