import { ModelOptionModelTypeEnum, type ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { useRouteQuery } from '@vueuse/router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref, shallowRef } from 'vue'
import { useWorkbenchModels } from './use-workbench-models'

vi.mock('@/composables/use-model-options-fetch', () => ({
  useModelOptionsFetch: vi.fn(),
}))

vi.mock('@vueuse/router', () => ({
  useRouteQuery: vi.fn(),
}))

describe('useWorkbenchModels', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('preserves the route model until model options finish loading', async () => {
    const modelOptions = ref<ModelOption[] | undefined>()
    const selectedModelName = ref<string | undefined>('deepseek-chat')
    vi.mocked(useModelOptionsFetch).mockReturnValue({
      data: modelOptions,
      isLoading: shallowRef(true),
      isFetching: shallowRef(true),
      refetch: vi.fn(),
    } as never)
    vi.mocked(useRouteQuery).mockReturnValue(selectedModelName as never)

    const models = useWorkbenchModels()

    expect(models.selectedModelName.value).toBe('deepseek-chat')

    modelOptions.value = [languageModel('deepseek-chat')]
    await nextTick()

    expect(models.selectedModelName.value).toBe('deepseek-chat')
    expect(models.testMode.value).toBe('chat')
  })

  it('clears the route model after an empty result has loaded', async () => {
    const modelOptions = ref<ModelOption[] | undefined>()
    const selectedModelName = ref<string | undefined>('missing-model')
    vi.mocked(useModelOptionsFetch).mockReturnValue({
      data: modelOptions,
      isLoading: shallowRef(true),
      isFetching: shallowRef(true),
      refetch: vi.fn(),
    } as never)
    vi.mocked(useRouteQuery).mockReturnValue(selectedModelName as never)

    useWorkbenchModels()
    modelOptions.value = []
    await nextTick()

    expect(selectedModelName.value).toBeUndefined()
  })
})

function languageModel(name: string): ModelOption {
  return {
    name,
    modelId: name,
    displayName: name,
    modelType: ModelOptionModelTypeEnum.Language,
  }
}
