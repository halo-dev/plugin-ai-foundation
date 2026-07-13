import { ModelOptionModelTypeEnum, type ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { useRouteQuery } from '@vueuse/router'
import { computed, shallowRef, watch, type ComputedRef, type ShallowRef } from 'vue'

export type WorkbenchTestMode = 'chat' | 'embedding' | 'rerank' | 'image' | 'rag'

export interface WorkbenchModels {
  availableOnly: ShallowRef<boolean | undefined>
  selectedModelName: ReturnType<typeof useRouteQuery<string | undefined>>
  testMode: ShallowRef<WorkbenchTestMode>
  chatModels: ComputedRef<ModelOption[]>
  embeddingModels: ComputedRef<ModelOption[]>
  rerankModels: ComputedRef<ModelOption[]>
  imageModels: ComputedRef<ModelOption[]>
  activeModels: ComputedRef<ModelOption[]>
  activeModelType: ComputedRef<ModelOptionModelTypeEnum>
  selectedModel: ComputedRef<ModelOption | undefined>
  isLoading: ReturnType<typeof useModelOptionsFetch>['isLoading']
  isFetching: ReturnType<typeof useModelOptionsFetch>['isFetching']
  refetch: ReturnType<typeof useModelOptionsFetch>['refetch']
}

const modelTypeByMode: Record<WorkbenchTestMode, ModelOptionModelTypeEnum> = {
  chat: ModelOptionModelTypeEnum.Language,
  embedding: ModelOptionModelTypeEnum.Embedding,
  rerank: ModelOptionModelTypeEnum.Rerank,
  image: ModelOptionModelTypeEnum.ImageGeneration,
  rag: ModelOptionModelTypeEnum.Language,
}

export function useWorkbenchModels(): WorkbenchModels {
  const modelType = shallowRef<string | undefined>()
  const availableOnly = shallowRef<boolean | undefined>(true)
  const {
    data: modelOptions,
    isLoading,
    isFetching,
    refetch,
  } = useModelOptionsFetch({
    modelType,
    available: availableOnly,
  })
  const selectedModelName = useRouteQuery<string | undefined>('model')
  const testMode = shallowRef<WorkbenchTestMode>('chat')

  const modelsByType = (type: ModelOptionModelTypeEnum) =>
    computed(() =>
      (modelOptions.value || []).filter((model) => model.name && model.modelType === type),
    )
  const chatModels = modelsByType(ModelOptionModelTypeEnum.Language)
  const embeddingModels = modelsByType(ModelOptionModelTypeEnum.Embedding)
  const rerankModels = modelsByType(ModelOptionModelTypeEnum.Rerank)
  const imageModels = modelsByType(ModelOptionModelTypeEnum.ImageGeneration)
  const activeModelType = computed(() => modelTypeByMode[testMode.value])
  const activeModels = computed(() => {
    switch (activeModelType.value) {
      case ModelOptionModelTypeEnum.Embedding:
        return embeddingModels.value
      case ModelOptionModelTypeEnum.Rerank:
        return rerankModels.value
      case ModelOptionModelTypeEnum.ImageGeneration:
        return imageModels.value
      default:
        return chatModels.value
    }
  })
  const selectedModel = computed(() =>
    activeModels.value.find((model) => model.name === selectedModelName.value),
  )

  watch(
    modelOptions,
    (items) => {
      if (!items) return
      const selected = items.find((item) => item.name === selectedModelName.value)
      switch (selected?.modelType) {
        case ModelOptionModelTypeEnum.Embedding:
          testMode.value = 'embedding'
          break
        case ModelOptionModelTypeEnum.Rerank:
          testMode.value = 'rerank'
          break
        case ModelOptionModelTypeEnum.ImageGeneration:
          testMode.value = 'image'
          break
        case ModelOptionModelTypeEnum.Language:
          if (testMode.value !== 'rag') {
            testMode.value = 'chat'
          }
      }
      const candidates = activeModels.value
      if (!candidates.length) {
        selectedModelName.value = undefined
      } else if (!candidates.some((item) => item.name === selectedModelName.value)) {
        selectedModelName.value = candidates[0].name
      }
    },
    { immediate: true },
  )

  watch(testMode, () => {
    selectedModelName.value = activeModels.value[0]?.name
  })

  return {
    availableOnly,
    selectedModelName,
    testMode,
    chatModels,
    embeddingModels,
    rerankModels,
    imageModels,
    activeModels,
    activeModelType,
    selectedModel,
    isLoading,
    isFetching,
    refetch,
  }
}
