import { ModelOptionFeaturesEnum, type ModelOption } from '@/api/generated'
import { describe, expect, it } from 'vitest'
import {
  modelCapabilityLabels,
  modelDetailLabels,
  modelOptionDisplayName,
  modelOptionUnavailableReasonLabel,
  selectedModelDisplayName,
  shouldShowModelDetails,
  shouldShowModelId,
} from './ai-model-selector'

describe('ai model selector helpers', () => {
  it('formats selected and option display names with the existing fallback order', () => {
    const current = model({ name: 'current', displayName: 'Current model', modelId: 'gpt-4o' })
    const snapshot = model({ name: 'snapshot', modelId: 'snapshot-id' })

    expect(modelOptionDisplayName(current)).toBe('Current model')
    expect(selectedModelDisplayName(undefined, snapshot, 'missing')).toBe('snapshot-id')
    expect(selectedModelDisplayName(undefined, undefined, 'raw-value')).toBe('raw-value')
  })

  it('keeps model id and detail visibility rules outside the Vue template', () => {
    expect(
      shouldShowModelId(
        model({ name: 'internal', displayName: 'Display', modelId: 'provider-id' }),
      ),
    ).toBe(true)
    expect(shouldShowModelId(model({ name: 'same', modelId: 'same' }))).toBe(false)
    expect(shouldShowModelDetails(model({ name: 'disabled', available: false }))).toBe(true)
    expect(
      shouldShowModelDetails(
        model({
          name: 'visual',
          capabilities: { language: { imageInput: true } },
        }),
      ),
    ).toBe(true)
    expect(shouldShowModelDetails(model({ name: 'plain' }))).toBe(false)
  })

  it('does not repeat capability labels already shown as feature labels', () => {
    const capabilities = { language: { imageInput: true } }

    expect(
      modelCapabilityLabels(
        model({
          name: 'vision',
          features: [ModelOptionFeaturesEnum.Vision],
          capabilities,
        }),
      ),
    ).not.toContain('图片识别')
    expect(modelCapabilityLabels(model({ name: 'capability-only', capabilities }))).toContain(
      '图片识别',
    )
    expect(
      modelDetailLabels(
        model({
          name: 'vision',
          modelType: 'language',
          features: [ModelOptionFeaturesEnum.Vision],
          capabilities,
        }),
      ),
    ).toEqual(['语言', '图片识别'])
  })

  it('labels capability mismatch as unavailable reason', () => {
    expect(modelOptionUnavailableReasonLabel('capability-unsupported')).toBe('能力不满足')
  })
})

function model(overrides: Partial<ModelOption>): ModelOption {
  return {
    name: '',
    displayName: undefined,
    modelId: undefined,
    modelType: undefined,
    features: [],
    available: true,
    unavailableReason: undefined,
    provider: undefined,
    ...overrides,
  } as ModelOption
}
