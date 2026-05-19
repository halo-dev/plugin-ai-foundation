import {
  AiModelSpecAdapterTypeEnum,
  AiModelSpecDiscoveryConfidenceEnum,
  AiModelSpecDiscoverySourceEnum,
  AiModelSpecFeaturesEnum,
  AiModelSpecModelTypeEnum,
  DiscoveredModelItemAdapterTypeEnum,
  DiscoveredModelItemConfidenceEnum,
  DiscoveredModelItemSourceEnum,
  ProviderTypeInfoSupportedFeaturesEnum,
  ProviderTypeInfoSupportedModelTypesEnum,
} from '@/api/generated'
import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  createModelFromDiscovered,
  defaultModelTypeForProviderType,
  filterModelFeaturesForProviderType,
  findProviderTypeForModel,
  modelFeatureOptionsForProviderType,
  modelTypeOptionsForProviderType,
} from './model'

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
  it('uses backend adapter recommendation when present', () => {
    expect(
      createModelFromDiscovered(
        'openai-prod',
        discoveredModel('text-embedding-3-small', AiModelSpecModelTypeEnum.Embedding, [
          AiModelSpecFeaturesEnum.Streaming,
        ], DiscoveredModelItemAdapterTypeEnum.OpenaiEmbedding),
      ).spec,
    ).toMatchObject({
      providerName: 'openai-prod',
      modelId: 'text-embedding-3-small',
      displayName: 'text-embedding-3-small',
      enabled: true,
      modelType: AiModelSpecModelTypeEnum.Embedding,
      features: [AiModelSpecFeaturesEnum.Streaming],
      adapterType: AiModelSpecAdapterTypeEnum.OpenaiEmbedding,
      discoverySource: AiModelSpecDiscoverySourceEnum.Rule,
      discoveryConfidence: AiModelSpecDiscoveryConfidenceEnum.Low,
    })
  })

  it('allows admin profile override before import', () => {
    expect(
      createModelFromDiscovered(
        'openai-prod',
        discoveredModel('maybe-image', AiModelSpecModelTypeEnum.Language),
        {
          modelType: AiModelSpecModelTypeEnum.ImageGeneration,
          features: [],
        },
      ).spec,
    ).toMatchObject({
      modelType: AiModelSpecModelTypeEnum.ImageGeneration,
      features: [],
    })
  })
})

describe('provider type model options', () => {
  it('limits model types and features to the selected provider type', () => {
    const providerType = providerTypeInfo('openailike', 'OpenAI 兼容', {
      supportedModelTypes: [
        ProviderTypeInfoSupportedModelTypesEnum.Language,
        ProviderTypeInfoSupportedModelTypesEnum.Embedding,
      ],
      supportedFeatures: [ProviderTypeInfoSupportedFeaturesEnum.Streaming],
    })

    expect(modelTypeOptionsForProviderType(providerType).map((item) => item.value)).toEqual([
      AiModelSpecModelTypeEnum.Language,
      AiModelSpecModelTypeEnum.Embedding,
    ])
    expect(modelFeatureOptionsForProviderType(providerType).map((item) => item.value)).toEqual([
      AiModelSpecFeaturesEnum.Streaming,
    ])
    expect(
      defaultModelTypeForProviderType(providerType, AiModelSpecModelTypeEnum.ImageGeneration),
    ).toBe(AiModelSpecModelTypeEnum.Language)
    expect(
      filterModelFeaturesForProviderType(providerType, [
        AiModelSpecFeaturesEnum.Streaming,
        AiModelSpecFeaturesEnum.Vision,
      ]),
    ).toEqual([AiModelSpecFeaturesEnum.Streaming])
  })

  it('shows all options until provider type metadata is loaded', () => {
    expect(modelTypeOptionsForProviderType(undefined).map((item) => item.value)).toContain(
      AiModelSpecModelTypeEnum.ImageGeneration,
    )
    expect(modelFeatureOptionsForProviderType(undefined).map((item) => item.value)).toContain(
      AiModelSpecFeaturesEnum.ToolCall,
    )
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
      modelType: AiModelSpecModelTypeEnum.Language,
      features: [AiModelSpecFeaturesEnum.Streaming],
      adapterType: AiModelSpecAdapterTypeEnum.OpenaiChat,
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

function providerTypeInfo(
  providerType: string,
  displayName: string,
  overrides: Partial<ProviderTypeInfo> = {},
): ProviderTypeInfo {
  return {
    providerType,
    displayName,
    supportedModelTypes: [],
    supportedFeatures: [],
    supportedAdapterTypes: [],
    ...overrides,
  }
}

function discoveredModel(
  modelId: string,
  modelType: AiModel['spec']['modelType'] = AiModelSpecModelTypeEnum.Language,
  features = [] as AiModelSpecFeaturesEnum[],
  adapterType?: DiscoveredModelItemAdapterTypeEnum,
) {
  return {
    modelId,
    displayName: modelId,
    name: '',
    modelType,
    features,
    adapterType,
    source: DiscoveredModelItemSourceEnum.Rule,
    confidence: DiscoveredModelItemConfidenceEnum.Low,
  }
}
