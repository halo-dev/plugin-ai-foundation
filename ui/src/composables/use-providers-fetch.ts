import { aiConsoleApiClient } from '@/api'
import { useQuery } from '@tanstack/vue-query'

export const QK_PROVIDERS = 'plugin:ai-foundation:providers'

export function useProvidersFetch() {
  return useQuery({
    queryKey: [QK_PROVIDERS],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.provider.listProviders()
      return data
    },
    refetchInterval(data) {
      const hasDeletingData = data?.some((provider) => !!provider.metadata.deletionTimestamp)
      return hasDeletingData ? 1000 : false
    },
  })
}
