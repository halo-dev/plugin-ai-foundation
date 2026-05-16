import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import { findProviderTypeForModel, inferEndpointType } from './model'

describe('findProviderTypeForModel', () => {
  it('matches by provider resource name instead of provider name prefix', () => {
    const model = aiModel('prod-east-gpt-4', 'prod-east')
    const providers = [
      aiProvider('prod-east', 'openailike'),
      aiProvider('openai-prod', 'openai'),
    ]
    const providerTypes = [
      providerTypeInfo('openai', 'OpenAI'),
      providerTypeInfo('openailike', 'OpenAI 兼容'),
    ]

    expect(findProviderTypeForModel(model, providers, providerTypes)?.providerType).toBe(
      'openailike',
    )
  })

  it('returns undefined when provider is missing', () => {
    expect(findProviderTypeForModel(aiModel('m', 'missing'), [], [])).toBeUndefined()
  })
})

describe('inferEndpointType', () => {
  it('prefers embedding endpoint when discovered model has embedding capability', () => {
    expect(
      inferEndpointType(
        discoveredModel('text-embedding-3-small', ['embedding']),
        ['openai-chat', 'openai-embedding'],
      ),
    ).toBe('openai-embedding')
  })

  it('prefers chat endpoint for non-embedding models', () => {
    expect(
      inferEndpointType(discoveredModel('gpt-4.1', ['chat']), [
        'openai-embedding',
        'openai-chat',
      ]),
    ).toBe('openai-chat')
  })

  it('falls back to first supported endpoint and then built-in defaults', () => {
    expect(inferEndpointType(discoveredModel('rerank', ['rerank']), ['custom-rerank'])).toBe(
      'custom-rerank',
    )
    expect(inferEndpointType(discoveredModel('embedding', ['embedding']), [])).toBe(
      'openai-embedding',
    )
    expect(inferEndpointType(discoveredModel('chat', ['chat']), [])).toBe('openai-chat')
  })
})

function aiModel(name: string, providerName: string): AiModel {
  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: { name },
    spec: {
      providerName,
      modelId: name,
      displayName: name,
      enabled: true,
      endpointType: 'openai-chat',
    },
  }
}

function aiProvider(name: string, providerType: string): AiProvider {
  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiProvider',
    metadata: { name },
    spec: {
      providerType,
      displayName: name,
      enabled: true,
    },
  }
}

function providerTypeInfo(providerType: string, displayName: string): ProviderTypeInfo {
  return {
    providerType,
    displayName,
    supportedEndpointTypes: [],
  }
}

function discoveredModel(modelId: string, capabilities: string[]) {
  return {
    modelId,
    displayName: modelId,
    name: '',
    capabilities,
  }
}
