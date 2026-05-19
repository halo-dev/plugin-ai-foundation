import type {
  AiModelSpecAdapterTypeEnum,
  AiModelSpecFeaturesEnum,
  AiModelSpecModelTypeEnum,
} from '@/api/generated'

export interface ProviderFormState {
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl?: string
  apiKeySecretName?: string
  proxyHost?: string
  proxyPort?: number
}

export interface ModelFormState {
  modelId: string
  displayName: string
  enabled: boolean
  modelType: AiModelSpecModelTypeEnum
  features?: AiModelSpecFeaturesEnum[]
  adapterType?: AiModelSpecAdapterTypeEnum
}
