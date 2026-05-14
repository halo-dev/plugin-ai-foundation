import { aiConsoleApiClient } from '@/api'
import type { ProviderTypeInfo } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'

export const QK_PROVIDER_TYPES = 'plugin:ai-foundation:provider-types'

export function useProviderTypesFetch() {
  return useQuery<ProviderTypeInfo[]>({
    queryKey: [QK_PROVIDER_TYPES],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.providerType.listProviderTypes()
      return data
    },
  })
}
