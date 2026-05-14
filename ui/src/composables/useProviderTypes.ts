import { aiConsoleApiClient } from '@/api'
import type { ProviderTypeInfo } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'

export function useProviderTypes() {
  return useQuery<ProviderTypeInfo[]>({
    queryKey: ['ai-provider-types'],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.provider.listProviderTypes()
      return data
    },
    staleTime: Infinity,
  })
}
