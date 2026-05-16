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

export function createModelFromDiscovered(providerName: string, model: DiscoveredModel): AiModel {
  const spec = {
    providerName,
    modelId: model.modelId,
    displayName: model.displayName || model.modelId,
    enabled: true,
    ...(model.suggestedEndpointType ? { endpointType: model.suggestedEndpointType } : {}),
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
