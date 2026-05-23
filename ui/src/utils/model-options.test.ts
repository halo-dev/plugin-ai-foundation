import type { ModelOption } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  groupModelOptionsByProvider,
  modelOptionLabel,
  modelOptionProviderLabel,
} from './model-options'

describe('model option labels', () => {
  it('builds display labels from model and provider metadata', () => {
    expect(modelOptionLabel(modelOption('gpt-4o', 'prod-openai', 'Production OpenAI'))).toBe(
      'gpt-4o / Production OpenAI',
    )
    expect(
      modelOptionProviderLabel(modelOption('gpt-4o', 'prod-openai', 'Production OpenAI')),
    ).toBe('Production OpenAI (OpenAI)')
  })
})

describe('groupModelOptionsByProvider', () => {
  it('groups options by provider while preserving option order', () => {
    const groups = groupModelOptionsByProvider([
      modelOption('gpt-4o', 'prod-openai', 'Production OpenAI'),
      modelOption('gpt-4o-mini', 'prod-openai', 'Production OpenAI'),
      modelOption('deepseek-chat', 'deepseek', 'DeepSeek', 'DeepSeek'),
    ])

    expect(groups.map((group) => group.label)).toEqual([
      'Production OpenAI (OpenAI)',
      'DeepSeek (DeepSeek)',
    ])
    expect(groups[0].models.map((model) => model.name)).toEqual(['gpt-4o', 'gpt-4o-mini'])
    expect(groups[1].models.map((model) => model.name)).toEqual(['deepseek-chat'])
  })
})

function modelOption(
  name: string,
  providerName: string,
  providerDisplayName: string,
  providerTypeDisplayName = 'OpenAI',
): ModelOption {
  return {
    name,
    modelId: name,
    displayName: name,
    modelType: 'language',
    available: true,
    enabled: true,
    provider: {
      name: providerName,
      displayName: providerDisplayName,
      providerType: providerName,
      providerTypeDisplayName,
      enabled: true,
    },
  }
}
