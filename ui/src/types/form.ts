export interface ProviderFormState {
  providerType: string
  displayName: string
  enabled: boolean
  baseUrl?: string
  apiKeySecretName?: string
  proxyHost?: string
  proxyPort?: number
}
