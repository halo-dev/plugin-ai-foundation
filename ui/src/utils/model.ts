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

export function inferEndpointType(
  model: DiscoveredModel,
  supportedEndpointTypes: string[] | undefined,
): string {
  const endpointTypes = supportedEndpointTypes || []
  const capabilities = model.capabilities || []

  if (capabilities.includes('embedding')) {
    const embeddingType = endpointTypes.find((type) => type.includes('embedding'))
    return embeddingType || endpointTypes[0] || 'openai-embedding'
  }

  const chatType = endpointTypes.find((type) => type.includes('chat'))
  return chatType || endpointTypes[0] || 'openai-chat'
}
