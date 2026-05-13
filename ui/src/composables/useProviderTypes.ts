import { axiosInstance } from '@halo-dev/api-client'
import { useQuery } from '@tanstack/vue-query'

export interface ProviderTypeInfo {
  providerType: string
  displayName: string
  description?: string
  iconUrl?: string
  documentationUrl?: string
  websiteUrl?: string
  builtIn: boolean
  requiresBaseUrl: boolean
  defaultBaseUrl?: string
  supportedEndpointTypes: string[]
  supportsEmbeddings: boolean
}

export function useProviderTypes() {
  return useQuery<ProviderTypeInfo[]>({
    queryKey: ['ai-provider-types'],
    queryFn: async () => {
      const { data } = await axiosInstance.get(
        '/apis/console.api.aifoundation.halo.run/v1alpha1/provider-types',
      )
      return data
    },
    staleTime: Infinity,
  })
}
