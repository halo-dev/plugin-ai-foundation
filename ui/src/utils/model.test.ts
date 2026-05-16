import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import { createModelFromDiscovered, findProviderTypeForModel } from './model'

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

describe('createModelFromDiscovered', () => {
  it('uses backend suggested endpoint type when present', () => {
    expect(
      createModelFromDiscovered(
        'openai-prod',
        discoveredModel('text-embedding-3-small', ['embedding'], 'openai-embedding'),
      ).spec,
    ).toMatchObject({
      providerName: 'openai-prod',
      modelId: 'text-embedding-3-small',
      displayName: 'text-embedding-3-small',
      enabled: true,
      endpointType: 'openai-embedding',
    })
  })

  it('omits endpoint type when backend provides no suggestion', () => {
    expect(createModelFromDiscovered('openai-prod', discoveredModel('rerank', ['rerank'])).spec)
      .not.toHaveProperty('endpointType')
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

function discoveredModel(modelId: string, capabilities: string[], suggestedEndpointType?: string) {
  return {
    modelId,
    displayName: modelId,
    name: '',
    capabilities,
    suggestedEndpointType,
  }
}
