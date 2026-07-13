import type { ModelParameterMappings, ParameterMappingTemplateInfo } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  definitionsForModelType,
  effectiveSelection,
  mappingsForModelType,
  templatesForParameter,
  validateReasoningMappings,
  writeSelection,
} from './parameter-mappings'

const maxTokens = definitionsForModelType('language').find(
  (item) => item.parameter === 'MAX_OUTPUT_TOKENS',
)!
const reasoning = definitionsForModelType('language').find(
  (item) => item.parameter === 'REASONING',
)!

describe('parameter mapping controls', () => {
  it('filters templates by parameter, model type, and adapter', () => {
    const templates: ParameterMappingTemplateInfo[] = [
      {
        id: 'openai.max-tokens',
        parameter: 'MAX_OUTPUT_TOKENS',
        modelType: 'language',
        adapterTypes: ['openai-chat'],
      },
      {
        id: 'ollama.num-predict',
        parameter: 'MAX_OUTPUT_TOKENS',
        modelType: 'language',
        adapterTypes: ['ollama-chat'],
      },
    ]
    expect(
      templatesForParameter(templates, maxTokens, 'openai-chat').map((item) => item.id),
    ).toEqual(['openai.max-tokens'])
  })

  it('resolves model over provider over built-in and supports unsupported overrides', () => {
    const provider = writeSelection(undefined, maxTokens, {
      mode: 'TEMPLATE',
      template: 'openai.max-completion-tokens',
    })
    expect(
      effectiveSelection('model', maxTokens, undefined, provider, {
        MAX_OUTPUT_TOKENS: { mode: 'TEMPLATE', template: 'openai.max-tokens' },
      }),
    ).toMatchObject({
      source: '继承 Provider',
      selection: { template: 'openai.max-completion-tokens' },
    })

    const model = writeSelection(undefined, maxTokens, { mode: 'UNSUPPORTED' })
    expect(effectiveSelection('model', maxTokens, model, provider, {}).selection?.mode).toBe(
      'UNSUPPORTED',
    )
  })

  it('drops mappings from the previous model type', () => {
    const mappings: ModelParameterMappings = {
      language: { maxOutputTokens: { mode: 'UNSUPPORTED' } },
      embedding: { dimensions: { mode: 'UNSUPPORTED' } },
    }
    expect(mappingsForModelType(mappings, 'embedding')).toEqual({
      embedding: { dimensions: { mode: 'UNSUPPORTED' } },
    })
  })

  it('validates typed reasoning intent values', () => {
    const invalid = writeSelection(undefined, reasoning, {
      mode: 'TEMPLATE',
      template: 'reasoning.thinking-budget',
      reasoningMapping: {
        low: { field: 'thinking_budget', valueType: 'INTEGER', value: 'invalid' },
      },
    })
    expect(validateReasoningMappings(invalid)).toHaveLength(1)
    const valid = writeSelection(undefined, reasoning, {
      mode: 'TEMPLATE',
      template: 'reasoning.thinking-budget',
      reasoningMapping: {
        enabled: { field: 'enable_thinking', valueType: 'BOOLEAN', value: 'true' },
        low: { field: 'thinking_budget', valueType: 'INTEGER', value: '256' },
      },
    })
    expect(validateReasoningMappings(valid)).toEqual([])
  })

  it('preserves native field overrides in typed selections', () => {
    const mappings = writeSelection(undefined, maxTokens, {
      mode: 'TEMPLATE',
      template: 'openai.max-tokens',
      field: 'generation.max_tokens',
    })
    expect(mappings?.language?.maxOutputTokens?.field).toBe('generation.max_tokens')
  })
})
