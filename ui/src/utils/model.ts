import { AiModelSpecDiscoveryConfidenceEnum, AiModelSpecDiscoverySourceEnum, AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel, AiProvider, ProviderTypeInfo } from '@/api/generated'
import type { DiscoveredModel } from '@/composables/use-models-fetch'

export function findProviderTypeForModel(
  model: AiModel,
  providers: AiProvider[] | undefined,
  providerTypes: ProviderTypeInfo[] | undefined,
) {
  const provider = providers?.find((p) => p.metadata.name === model.spec.providerName)
  return providerTypes?.find((type) => type.providerType === provider?.spec.providerType)
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
