import { describe, expect, it } from '@rstest/core'
import {
  buildTestMediaContent,
  numberOrUndefined,
  parseStringMapJson,
} from './model-test-workbench-request'

describe('model test workbench request helpers', () => {
  it('normalizes finite numbers and rejects non-finite values', () => {
    expect(numberOrUndefined(0)).toBe(0)
    expect(numberOrUndefined(Number.NaN)).toBeUndefined()
    expect(numberOrUndefined(Number.POSITIVE_INFINITY)).toBeUndefined()
  })

  it('parses request headers and stringifies primitive values', () => {
    expect(parseStringMapJson('{"X-Trace":"trace-1","X-Retry":2}')).toEqual({
      value: { 'X-Trace': 'trace-1', 'X-Retry': '2' },
    })
    expect(parseStringMapJson('[]')).toEqual({ error: 'Headers 必须是 JSON 对象' })
    expect(parseStringMapJson('{')).toEqual({ error: 'Headers 不是有效的 JSON' })
  })

  it('builds URL and base64 media content while rejecting ambiguous input', () => {
    expect(
      buildTestMediaContent({
        url: ' https://example.com/image.png ',
        data: '',
        mediaType: ' image/png ',
        label: '参考图',
      }),
    ).toEqual({ value: { url: 'https://example.com/image.png', mediaType: 'image/png' } })
    expect(
      buildTestMediaContent({ url: '', data: 'aGVsbG8=', mediaType: '', label: 'Mask' }),
    ).toEqual({ value: { data: 'aGVsbG8=', mediaType: 'image/png' } })
    expect(
      buildTestMediaContent({
        url: 'https://example.com',
        data: 'data',
        mediaType: '',
        label: 'Mask',
      }),
    ).toEqual({ error: 'Mask 只能填写 URL 或 base64 其中一种' })
  })
})
