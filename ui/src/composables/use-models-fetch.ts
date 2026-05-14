import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'

export interface DiscoveredModel {
  modelId: string
  displayName: string
  name: string
  capabilities: string[]
}

export const QK_MODELS = 'plugin:ai-foundation:models'
export const QK_DISCOVERED_MODELS = 'plugin:ai-foundation:discovered-models'

export function useModelsFetch({ providerName }: { providerName: Ref<string | undefined> }) {
  return useQuery<AiModel[]>({
    queryKey: [QK_MODELS, providerName],
    queryFn: async () => {
      const fieldSelector = []

      if (providerName.value) {
        fieldSelector.push(`spec.providerName=${providerName.value}`)
      }

      const { data } = await aiConsoleApiClient.model.listModels({
        fieldSelector,
      })
      return data
    },
    refetchInterval(data) {
      const hasDeletingData = data?.some((model) => !!model.metadata.deletionTimestamp)
      return hasDeletingData ? 1000 : false
    },
  })
}

export function useDiscoverModelsFetch(providerName: Ref<string | undefined>) {
  return useQuery({
    queryKey: [QK_DISCOVERED_MODELS, providerName],
    queryFn: async () => {
      if (!providerName.value) {
        return null
      }

      const { data } = await aiConsoleApiClient.provider.discoverProviderModels({
        name: providerName.value,
      })

      return data as unknown as { models: DiscoveredModel[]; providerName: string }
    },
  })
}
