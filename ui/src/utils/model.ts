import { AiModelSpecDiscoveryConfidenceEnum, AiModelSpecDiscoverySourceEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import type { DiscoveredModel } from '@/composables/use-models-fetch'
import { MODEL_FEATURE_OPTIONS, MODEL_TYPE_OPTIONS } from '@/types'

export function findProviderTypeForModel(
  model: AiModel,
  providers: AiProvider[] | undefined,
  providerTypes: ProviderTypeInfo[] | undefined,
) {
  const provider = providers?.find((p) => p.metadata.name === model.spec.providerName)
  return providerTypes?.find((type) => type.providerType === provider?.spec.providerType)
}

export function modelTypeOptionsForProviderType(providerType: ProviderTypeInfo | undefined) {
  if (!providerType) {
    return [...MODEL_TYPE_OPTIONS]
  }
  const supportedTypes = providerType.supportedModelTypes || []
  return MODEL_TYPE_OPTIONS.filter((item) => supportedTypes.includes(item.value))
}

export function modelFeatureOptionsForProviderType(providerType: ProviderTypeInfo | undefined) {
  if (!providerType) {
    return [...MODEL_FEATURE_OPTIONS]
  }
  const supportedFeatures = providerType.supportedFeatures || []
  return MODEL_FEATURE_OPTIONS.filter((item) => supportedFeatures.includes(item.value))
}

export function defaultModelTypeForProviderType(
  providerType: ProviderTypeInfo | undefined,
  candidate?: string,
) {
  const options = modelTypeOptionsForProviderType(providerType)
  const matched = options.find((item) => item.value === candidate)
  return matched?.value || options[0]?.value || AiModelSpecModelTypeEnum.Language
}

export function filterModelFeaturesForProviderType(
  providerType: ProviderTypeInfo | undefined,
  features: string[] = [],
) {
  const allowedFeatures = new Set<string>(
    modelFeatureOptionsForProviderType(providerType).map((item) => item.value),
  )
  return features.filter((feature) => allowedFeatures.has(feature)) as NonNullable<
    AiModel['spec']['features']
  >
}

export interface DiscoveredModelProfileOverride {
  modelType?: AiModel['spec']['modelType']
  features?: NonNullable<AiModel['spec']['features']>
}

export function createModelFromDiscovered(
  providerName: string,
  model: DiscoveredModel,
  override?: DiscoveredModelProfileOverride,
): AiModel {
  const spec = {
    providerName,
    modelId: model.modelId,
    displayName: model.displayName || model.modelId,
    enabled: true,
    modelType: override?.modelType || model.modelType || AiModelSpecModelTypeEnum.Language,
    features: override?.features || model.features || [],
    discoverySource: model.source || AiModelSpecDiscoverySourceEnum.Rule,
    discoveryConfidence: model.confidence || AiModelSpecDiscoveryConfidenceEnum.Low,
    ...(model.adapterType ? { adapterType: model.adapterType } : {}),
  } as AiModel['spec']

  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: {
      name: '',
    },
    spec,
  }
}
