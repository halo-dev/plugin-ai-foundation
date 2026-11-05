import { aiConsoleApiClient, aiCoreApiClient } from '@/api'
import type { AiModel, ModelOption } from '@/api/generated'
import { useQuery } from '@tanstack/vue-query'
import { computed, type Ref } from 'vue'

export function useModelParameters(model: Readonly<Ref<AiModel | undefined>>) {
  return useQuery({
    queryKey: ['plugin:ai-foundation:model-parameters', model],
    enabled: computed(() => Boolean(model.value?.spec.providerName && model.value.spec.modelId)),
    queryFn: async ({ signal }) => {
      if (!model.value) return undefined
      return (
        await aiConsoleApiClient.model.resolveModelParameters({ aiModel: model.value }, { signal })
      ).data
    },
    retry: false,
  })
}

export function useSavedModelParameters(selectedModel: Readonly<Ref<ModelOption | undefined>>) {
  const name = computed(() =>
    selectedModel.value?.modelType === 'decision' ? undefined : selectedModel.value?.name,
  )
  const model = useQuery({
    queryKey: ['plugin:ai-foundation:model-parameter-source', name],
    enabled: computed(() => Boolean(name.value)),
    queryFn: async ({ signal }) => {
      if (!name.value) return undefined
      return (await aiCoreApiClient.model.getAiModel({ name: name.value }, { signal })).data
    },
    retry: false,
  })
  const parameters = useModelParameters(computed(() => model.data.value))
  return {
    data: parameters.data,
    isFetching: computed(() => model.isFetching.value || parameters.isFetching.value),
    isError: computed(() => model.isError.value || parameters.isError.value),
  }
}
