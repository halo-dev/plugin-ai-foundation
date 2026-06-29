import type { ModelOption } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  capabilitySummaryLabels,
  capabilityUnavailableDetailsLabel,
  normalizeRequiredCapabilities,
} from './capabilities'

describe('capability helpers', () => {
  it('normalizes structured required capabilities into JSON', () => {
    expect(
      normalizeRequiredCapabilities({
        language: {
          imageInput: true,
          inputMediaTypes: ['image/*'],
          inputSources: ['data'],
        },
      }),
    ).toBe(
      JSON.stringify({
        language: {
          imageInput: true,
          inputMediaTypes: ['image/*'],
          inputSources: ['data'],
        },
      }),
    )
  })

  it('omits unknown capability values from summaries', () => {
    expect(
      capabilitySummaryLabels({
        language: {
          imageInput: true,
          fileInput: undefined,
          inputSources: ['url'],
        },
        imageGeneration: {
          textToImage: true,
          imageToImage: false,
          maskInput: undefined,
        },
      }),
    ).toEqual(['图片识别', 'URL 输入', '文生图'])
  })

  it('summarizes audio media input as audio recognition', () => {
    expect(
      capabilitySummaryLabels({
        language: {
          fileInput: true,
          inputMediaTypes: ['audio/*'],
        },
      }),
    ).toEqual(['音频识别'])
  })

  it('formats unavailable capability details', () => {
    expect(
      capabilityUnavailableDetailsLabel({
        unavailableDetails: [
          { path: 'language.imageInput', expected: true, actual: false },
          { path: 'language.inputSources', expected: ['data'], actual: ['url'] },
        ],
      } as ModelOption),
    ).toBe('缺少能力：支持图片识别、支持输入来源')
  })
})
