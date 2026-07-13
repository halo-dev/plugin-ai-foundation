import type {
  AiModel,
  AiModelSpecAdapterTypeEnum,
  AiModelSpecFeaturesEnum,
  AiModelSpecModelTypeEnum,
  ModelParameterMappings,
} from '@/api/generated'

export interface ProviderFormState {
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl?: string
  chatEndpointPath?: string
  embeddingEndpointPath?: string
  rerankEndpointPath?: string
  imageEndpointPath?: string
  apiKeySecretName?: string
  proxyHost?: string
  proxyPort?: number
  parameterMappings?: ModelParameterMappings
}

export interface ModelFormState {
  modelId: string
  displayName: string
  enabled: boolean
  modelType: AiModelSpecModelTypeEnum
  features?: AiModelSpecFeaturesEnum[]
  adapterType?: AiModelSpecAdapterTypeEnum
  capabilities?: AiModel['spec']['capabilities']
  capabilitySources?: AiModel['spec']['capabilitySources']
  parameterMappings?: ModelParameterMappings
}
