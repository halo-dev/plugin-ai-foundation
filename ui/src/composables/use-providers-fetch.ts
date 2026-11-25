import { aiConsoleApiClient } from '@/api'
import { useQuery, type QueryClient } from '@tanstack/vue-query'
import { toValue, type MaybeRefOrGetter } from 'vue'

export const QK_PROVIDERS = 'plugin:ai-foundation:providers'
export const QK_PROVIDER = 'plugin:ai-foundation:provider'

export function reloadProviderQueries(
  queryClient: QueryClient,
  providerName?: MaybeRefOrGetter<string | undefined>,
) {
  const name = toValue(providerName)
  queryClient.refetchQueries({ queryKey: [QK_PROVIDERS] })
  queryClient.refetchQueries({
    queryKey: name ? [QK_PROVIDER, name] : [QK_PROVIDER],
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
