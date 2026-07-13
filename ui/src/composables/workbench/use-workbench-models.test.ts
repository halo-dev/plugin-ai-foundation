import { ModelOptionModelTypeEnum, type ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { beforeEach, describe, expect, it, rstest } from '@rstest/core'
import { useRouteQuery } from '@vueuse/router'
import { nextTick, ref, shallowRef } from 'vue'
import { useWorkbenchModels } from './use-workbench-models'

rstest.mock('@/composables/use-model-options-fetch', () => ({
  useModelOptionsFetch: rstest.fn(),
}))

rstest.mock('@vueuse/router', () => ({
  useRouteQuery: rstest.fn(),
}))

describe('useWorkbenchModels', () => {
  beforeEach(() => {
    rstest.clearAllMocks()
  })

  it('preserves the route model until model options finish loading', async () => {
    const modelOptions = ref<ModelOption[] | undefined>()
    const selectedModelName = ref<string | undefined>('deepseek-chat')
    rstest.mocked(useModelOptionsFetch).mockReturnValue({
      data: modelOptions,
      isLoading: shallowRef(true),
      isFetching: shallowRef(true),
      refetch: rstest.fn(),
    } as never)
    rstest.mocked(useRouteQuery).mockReturnValue(selectedModelName as never)

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
    rstest.mocked(useModelOptionsFetch).mockReturnValue({
      data: modelOptions,
      isLoading: shallowRef(true),
      isFetching: shallowRef(true),
      refetch: rstest.fn(),
    } as never)
    rstest.mocked(useRouteQuery).mockReturnValue(selectedModelName as never)

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
