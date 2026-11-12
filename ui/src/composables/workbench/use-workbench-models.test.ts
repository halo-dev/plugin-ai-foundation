import { ModelOptionModelTypeEnum, type ModelOption } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { useRouteQuery } from '@vueuse/router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, shallowRef, type EffectScope } from 'vue'
import { useWorkbenchModels, type WorkbenchTestMode } from './use-workbench-models'

vi.mock('@/composables/use-model-options-fetch', () => ({
  useModelOptionsFetch: vi.fn(),
}))

vi.mock('@vueuse/router', () => ({
  useRouteQuery: vi.fn(),
}))

describe('useWorkbenchModels', () => {
  const scopes: EffectScope[] = []

  beforeEach(() => {
    vi.clearAllMocks()
  })

  afterEach(() => {
    scopes.splice(0).forEach((scope) => scope.stop())
  })

  function createWorkbench(
    selectedName: string,
    initialModels?: ModelOption[],
    initialMode?: WorkbenchTestMode,
  ) {
    const modelOptions = shallowRef(initialModels)
    const selectedModelName = shallowRef<string | undefined>(selectedName)
    const modeQuery = shallowRef<string | undefined>(initialMode)
    vi.mocked(useModelOptionsFetch).mockReturnValue({
      data: modelOptions,
      isLoading: shallowRef(!initialModels),
      isFetching: shallowRef(!initialModels),
      refetch: vi.fn(),
    } as never)
    vi.mocked(useRouteQuery).mockImplementation(((key: string) =>
      key === 'mode' ? modeQuery : selectedModelName) as never)

    const scope = effectScope()
    scopes.push(scope)
    const models = scope.run(useWorkbenchModels)!
    return { models, modelOptions, modeQuery }
  }

  it('preserves the route model until model options finish loading', async () => {
    const { models, modelOptions } = createWorkbench('deepseek-chat')

    expect(models.selectedModelName.value).toBe('deepseek-chat')

    modelOptions.value = [model('deepseek-chat')]
    await nextTick()

    expect(models.selectedModelName.value).toBe('deepseek-chat')
    expect(models.testMode.value).toBe('chat')
  })

  const modelModes: [ModelOptionModelTypeEnum, WorkbenchTestMode][] = [
    [ModelOptionModelTypeEnum.Language, 'chat'],
    [ModelOptionModelTypeEnum.Decision, 'decision'],
    [ModelOptionModelTypeEnum.Embedding, 'embedding'],
    [ModelOptionModelTypeEnum.Rerank, 'rerank'],
    [ModelOptionModelTypeEnum.ImageGeneration, 'image'],
  ]

  describe.each(modelModes)('%s model deep links', (type, mode) => {
    it.each(['cold', 'cached'] as const)(
      'preserves the second model with %s options',
      async (load) => {
        const options = [model('first', type), model('second', type)]
        const { models, modelOptions } = createWorkbench(
          'second',
          load === 'cached' ? options : undefined,
        )

        if (load === 'cold') {
          expect(models.selectedModelName.value).toBe('second')
          modelOptions.value = options
        }
        await nextTick()

        expect(models.testMode.value).toBe(mode)
        expect(models.selectedModelName.value).toBe('second')
        expect(models.selectedModel.value?.name).toBe('second')
      },
    )
  })

  it('selects a model in the new category when the user switches test modes', async () => {
    const { models } = createWorkbench('decision-second', [
      model('decision-first', ModelOptionModelTypeEnum.Decision),
      model('decision-second', ModelOptionModelTypeEnum.Decision),
      model('language-first'),
      model('embedding-first', ModelOptionModelTypeEnum.Embedding),
    ])

    models.testMode.value = 'embedding'
    await nextTick()
    expect(models.selectedModelName.value).toBe('embedding-first')

    models.testMode.value = 'chat'
    await nextTick()
    expect(models.selectedModelName.value).toBe('language-first')

    models.testMode.value = 'decision'
    await nextTick()
    expect(models.selectedModelName.value).toBe('decision-first')

    models.testMode.value = 'image'
    await nextTick()
    expect(models.selectedModelName.value).toBeUndefined()
  })

  it('falls back to the first model when the route model is missing', async () => {
    const { models, modelOptions } = createWorkbench('missing-model')

    modelOptions.value = [model('first'), model('second')]
    await nextTick()

    expect(models.selectedModelName.value).toBe('first')
  })

  it('falls back within the active category when the selected model disappears', async () => {
    const first = model('first', ModelOptionModelTypeEnum.Decision)
    const { models, modelOptions } = createWorkbench('second', [
      first,
      model('second', ModelOptionModelTypeEnum.Decision),
    ])

    modelOptions.value = [first]
    await nextTick()

    expect(models.testMode.value).toBe('decision')
    expect(models.selectedModelName.value).toBe('first')
  })

  it('clears the route model after an empty result has loaded', async () => {
    const { models, modelOptions } = createWorkbench('missing-model')

    modelOptions.value = []
    await nextTick()

    expect(models.selectedModelName.value).toBeUndefined()
  })

  it('keeps an explicit rag mode when a language model is linked', async () => {
    const { models } = createWorkbench('deepseek-chat', [model('deepseek-chat')], 'rag')

    await nextTick()

    expect(models.testMode.value).toBe('rag')
    expect(models.selectedModelName.value).toBe('deepseek-chat')
  })

  it('treats an explicit mode as authoritative when the linked model has another type', async () => {
    const { models } = createWorkbench(
      'deepseek-chat',
      [model('deepseek-chat'), model('embedding-first', ModelOptionModelTypeEnum.Embedding)],
      'embedding',
    )

    await nextTick()

    expect(models.testMode.value).toBe('embedding')
    expect(models.selectedModelName.value).toBe('embedding-first')
  })

  it('mirrors mode changes into the route and clears the chat mode', async () => {
    const { models, modeQuery } = createWorkbench('language-first', [model('language-first')])

    await nextTick()
    expect(modeQuery.value).toBeUndefined()

    models.testMode.value = 'embedding'
    expect(modeQuery.value).toBe('embedding')

    models.testMode.value = 'chat'
    expect(modeQuery.value).toBeUndefined()
  })
})

function model(
  name: string,
  modelType: ModelOptionModelTypeEnum = ModelOptionModelTypeEnum.Language,
): ModelOption {
  return {
    name,
    modelId: name,
    displayName: name,
    modelType,
  }
}
