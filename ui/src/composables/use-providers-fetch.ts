import { aiConsoleApiClient } from '@/api'
import { useQuery, type QueryClient } from '@tanstack/vue-query'
import type { Ref } from 'vue'

export const QK_PROVIDERS = 'plugin:ai-foundation:providers'
export const QK_PROVIDER = 'plugin:ai-foundation:provider'

export function reloadProviderQueries(
  queryClient: QueryClient,
  providerName?: Ref<string | undefined> | string,
) {
  queryClient.refetchQueries({ queryKey: [QK_PROVIDERS] })
  queryClient.refetchQueries({
    queryKey: providerName ? [QK_PROVIDER, providerName] : [QK_PROVIDER],
  })
}

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
