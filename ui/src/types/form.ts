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
  group?: string
  capabilities?: string[]
  endpointType?: string
  supportedTextDelta?: boolean
}
