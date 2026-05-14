import { aiConsoleApiClient } from '@/api'
import type { AiProvider, ProviderTypeInfo } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'

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

export function useProviderType(provider: Ref<AiProvider | undefined | null>) {
  const { data: providerTypes } = useProviderTypesFetch()
  return computed(() => {
    return providerTypes.value?.find((t) => t.providerType === provider.value?.spec.providerType)
  })
}
