import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import {
  AiModelSpecAdapterTypeEnum,
  AiModelSpecDiscoveryConfidenceEnum,
  AiModelSpecDiscoverySourceEnum,
  AiModelSpecFeaturesEnum,
  AiModelSpecModelTypeEnum,
  DiscoveredModelItemAdapterTypeEnum,
  DiscoveredModelItemConfidenceEnum,
  DiscoveredModelItemSourceEnum,
  LanguageCapabilityInputSourcesEnum,
  ModelCapabilitySourcesLanguageEnum,
  ProviderTypeInfoSupportedFeaturesEnum,
  ProviderTypeInfoSupportedModelTypesEnum,
} from '@/api/generated'
import { describe, expect, it } from '@rstest/core'
import {
  createModelFromDiscovered,
  defaultModelTypeForProviderType,
  filterModelFeaturesForProviderType,
  findProviderTypeForModel,
  groupDiscoveredModels,
  modelFeatureOptionsForProviderType,
  modelImportFailureMessage,
  modelTypeOptionsForProviderType,
  summarizeModelImportResults,
  syncDiscoveredModelProfiles,
} from './model'

describe('findProviderTypeForModel', () => {
  it('matches by provider resource name instead of provider name prefix', () => {
    const model = aiModel('prod-east-gpt-4', 'prod-east')
    const providers = [aiProvider('prod-east', 'openailike'), aiProvider('openai-prod', 'openai')]
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
        discoveredModel(
          'text-embedding-3-small',
          AiModelSpecModelTypeEnum.Embedding,
          [AiModelSpecFeaturesEnum.Streaming],
          DiscoveredModelItemAdapterTypeEnum.OpenaiEmbedding,
        ),
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

  it('persists discovered capabilities when importing a model', () => {
    expect(
      createModelFromDiscovered(
        'openai-prod',
        discoveredModel(
          'vision-model',
          AiModelSpecModelTypeEnum.Language,
          [AiModelSpecFeaturesEnum.Vision],
          DiscoveredModelItemAdapterTypeEnum.OpenaiChat,
          {
            capabilities: {
              language: {
                imageInput: true,
                inputMediaTypes: ['image/*'],
                inputSources: [LanguageCapabilityInputSourcesEnum.Data],
              },
              sources: {
                language: ModelCapabilitySourcesLanguageEnum.Remote,
              },
            },
            capabilitySources: {
              language: ModelCapabilitySourcesLanguageEnum.Remote,
            },
          },
        ),
      ).spec,
    ).toMatchObject({
      capabilities: {
        language: {
          imageInput: true,
          inputMediaTypes: ['image/*'],
          inputSources: [LanguageCapabilityInputSourcesEnum.Data],
        },
        sources: {
          language: ModelCapabilitySourcesLanguageEnum.Remote,
        },
      },
      capabilitySources: {
        language: ModelCapabilitySourcesLanguageEnum.Remote,
      },
    })
  })

  it('drops stale discovered capability domains when admin changes model type before import', () => {
    expect(
      createModelFromDiscovered(
        'openai-prod',
        discoveredModel(
          'vision-model',
          AiModelSpecModelTypeEnum.Language,
          [AiModelSpecFeaturesEnum.Vision],
          DiscoveredModelItemAdapterTypeEnum.OpenaiChat,
          {
            capabilities: {
              language: {
                imageInput: true,
                inputMediaTypes: ['image/*'],
                inputSources: [LanguageCapabilityInputSourcesEnum.Data],
              },
            },
            capabilitySources: {
              language: ModelCapabilitySourcesLanguageEnum.Remote,
            },
          },
        ),
        {
          modelType: AiModelSpecModelTypeEnum.ImageGeneration,
          features: [],
        },
      ).spec,
    ).not.toHaveProperty('capabilities')
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

describe('discovered model profiles', () => {
  it('syncs discovered profiles without keeping stale entries', () => {
    const providerType = providerTypeInfo('openailike', 'OpenAI 兼容', {
      supportedModelTypes: [ProviderTypeInfoSupportedModelTypesEnum.Language],
      supportedFeatures: [ProviderTypeInfoSupportedFeaturesEnum.Streaming],
    })

    const profiles = syncDiscoveredModelProfiles([discoveredModel('candidate')], providerType, {
      candidate: {
        modelType: AiModelSpecModelTypeEnum.ImageGeneration,
        features: [AiModelSpecFeaturesEnum.Streaming, AiModelSpecFeaturesEnum.Vision],
      },
      stale: {
        modelType: AiModelSpecModelTypeEnum.Language,
        features: [],
      },
    })

    expect(Object.keys(profiles)).toEqual(['candidate'])
    expect(profiles.candidate).toEqual({
      modelType: AiModelSpecModelTypeEnum.Language,
      features: [AiModelSpecFeaturesEnum.Streaming],
    })
  })
})

describe('discovered model groups', () => {
  it('groups discovered models by model type without confirmation sections', () => {
    const groups = groupDiscoveredModels([
      discoveredModel('maybe-chat'),
      discoveredModel(
        'remote-embedding',
        AiModelSpecModelTypeEnum.Embedding,
        [],
        DiscoveredModelItemAdapterTypeEnum.OpenaiEmbedding,
        {
          source: DiscoveredModelItemSourceEnum.Remote,
          confidence: DiscoveredModelItemConfidenceEnum.High,
        },
      ),
      discoveredModel(
        'remote-chat',
        AiModelSpecModelTypeEnum.Language,
        [AiModelSpecFeaturesEnum.Streaming],
        DiscoveredModelItemAdapterTypeEnum.OpenaiChat,
        {
          source: DiscoveredModelItemSourceEnum.Remote,
          confidence: DiscoveredModelItemConfidenceEnum.High,
        },
      ),
    ])

    expect(groups.map((group) => group.key)).toEqual([
      AiModelSpecModelTypeEnum.Language,
      AiModelSpecModelTypeEnum.Embedding,
    ])
    expect(groups[0].models.map((model) => model.modelId)).toEqual(['maybe-chat', 'remote-chat'])
    expect(groups[1].models.map((model) => model.modelId)).toEqual(['remote-embedding'])
  })
})

describe('summarizeModelImportResults', () => {
  it('keeps failed result messages aligned with the original model', () => {
    const error = new Error('already exists')
    const summary = summarizeModelImportResults(
      [
        discoveredModel('first-model'),
        discoveredModel('second-model'),
        discoveredModel('third-model'),
      ],
      [
        { status: 'fulfilled', value: 'first-model' },
        { status: 'rejected', reason: error },
        { status: 'fulfilled', value: 'third-model' },
      ],
    )

    expect(summary.succeeded).toBe(2)
    expect(summary.failed).toEqual([{ modelId: 'second-model', reason: error }])
    expect(modelImportFailureMessage(summary.failed[0])).toBe('second-model: already exists')
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
  evidence: {
    source?: DiscoveredModelItemSourceEnum
    confidence?: DiscoveredModelItemConfidenceEnum
    capabilities?: AiModel['spec']['capabilities']
    capabilitySources?: AiModel['spec']['capabilitySources']
  } = {},
) {
  return {
    modelId,
    displayName: modelId,
    name: '',
    modelType,
    features,
    adapterType,
    source: evidence.source || DiscoveredModelItemSourceEnum.Rule,
    confidence: evidence.confidence || DiscoveredModelItemConfidenceEnum.Low,
    capabilities: evidence.capabilities,
    capabilitySources: evidence.capabilitySources,
  }
}
