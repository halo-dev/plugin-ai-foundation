import type { ModelOption } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  modelOptionDisplayName,
  modelOptionUnavailableReasonLabel,
  nextActiveModelName,
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

  it('keeps a valid active option or falls back to the selected/first selectable model', () => {
    const models = [
      model({ name: 'first' }),
      model({ name: 'selected' }),
      model({ name: 'active' }),
    ]

    expect(nextActiveModelName(models, 'selected', 'active')).toBe('active')
    expect(nextActiveModelName(models, 'selected', 'missing')).toBe('selected')
    expect(nextActiveModelName(models, 'missing', undefined)).toBe('first')
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
