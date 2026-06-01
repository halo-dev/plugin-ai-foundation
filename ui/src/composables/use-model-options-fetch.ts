import { aiConsoleApiClient } from '@/api'
import type { ModelOption } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'
import type { Ref } from 'vue'

export const QK_MODEL_OPTIONS = 'plugin:ai-foundation:model-options'

export interface ModelOptionsFetchParams {
  modelType?: Ref<string | undefined>
  providerName?: Ref<string | undefined>
  providerType?: Ref<string | undefined>
  enabled?: Ref<boolean | undefined>
  available?: Ref<boolean | null | undefined>
  requiredFeatures?: Ref<string[] | undefined>
  keyword?: Ref<string | undefined>
}

export function useModelOptionsFetch(params: ModelOptionsFetchParams = {}) {
  return useQuery<ModelOption[]>({
    queryKey: [
      QK_MODEL_OPTIONS,
      params.modelType,
      params.providerName,
      params.providerType,
      params.enabled,
      params.available,
      params.requiredFeatures,
      params.keyword,
    ],
    queryFn: async () => {
      const { data } = await aiConsoleApiClient.modelOption.listModelOptions({
        modelType: params.modelType?.value,
        providerName: params.providerName?.value,
        providerType: params.providerType?.value,
        enabled: params.enabled?.value,
        available: params.available?.value ?? undefined,
        requiredFeatures: params.requiredFeatures?.value?.join(','),
        keyword: params.keyword?.value,
      })
      return data
    },
  })
}
