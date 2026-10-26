import { aiConsoleApiClient } from '@/api'
import type { AiProvider, ModelCatalogStatus, ProviderTypeInfo } from '@/api/generated'
import type { DiscoveredModel } from '@/composables/use-models-fetch'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import ProviderModelsDiscoveryModal from './ProviderModelsDiscoveryModal.vue'

const models = ref<{ models: DiscoveredModel[] }>({ models: [] })
const refetchModels = vi.fn()
const providerTypes = ref<ProviderTypeInfo[]>([])
const catalogStatus = ref<ModelCatalogStatus>({ available: true })
const isLoadingModels = ref(false)

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    provider: { refreshModelCatalog: vi.fn() },
    model: { createModel: vi.fn(), updateModel: vi.fn() },
  },
}))
vi.mock('@/composables/use-models-fetch', () => ({
  QK_MODELS: 'models',
  useDiscoverModelsFetch: () => ({
    data: models,
    isLoading: isLoadingModels,
    refetch: refetchModels,
  }),
  useModelCatalogStatusFetch: () => ({ data: catalogStatus, refetch: vi.fn() }),
}))
vi.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderTypesFetch: () => ({
    data: providerTypes,
    isLoading: ref(false),
    isError: ref(false),
  }),
}))
vi.mock('@tanstack/vue-query', () => ({ useQueryClient: () => ({ invalidateQueries: vi.fn() }) }))
vi.mock('@/utils/focus', () => ({ setFocus: vi.fn() }))
vi.mock('@halo-dev/components', () => {
  const container = defineComponent({
    setup(_, { slots, expose }) {
      expose({ close: vi.fn() })
      return () =>
        h(
          'div',
          Object.values(slots).flatMap((slot) => slot?.()),
        )
    },
  })
  return {
    Toast: { warning: vi.fn(), success: vi.fn() },
    VButton: defineComponent({
      props: ['disabled', 'loading'],
      setup:
        (props, { slots }) =>
        () =>
          h('button', { disabled: props.disabled }, slots.default?.()),
    }),
    VEmpty: container,
    VEntity: container,
    VEntityContainer: container,
    VEntityField: container,
    VLoading: container,
    VModal: container,
    VSpace: container,
    VTag: container,
  }
})

function model(id: string, features: DiscoveredModel['features']): DiscoveredModel {
  return {
    modelId: id,
    displayName: id,
    name: '',
    modelType: 'language',
    adapterType: 'openai-chat',
    features,
    source: 'rule',
    confidence: 'medium',
  }
}

describe('discovery catalog refresh', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    catalogStatus.value = { available: true }
    isLoadingModels.value = false
    models.value = { models: [model('edited', ['tool-call']), model('untouched', ['tool-call'])] }
    providerTypes.value = [
      {
        providerType: 'openai',
        supportedModelTypes: ['language'],
        supportedFeatures: ['tool-call', 'reasoning'],
        adapters: [
          {
            adapterType: 'openai-chat',
            modelType: 'language',
            supportedFeatures: ['tool-call', 'reasoning'],
          },
        ],
      },
    ]
    vi.mocked(aiConsoleApiClient.provider.refreshModelCatalog).mockResolvedValue({
      data: { available: true },
    } as never)
    vi.mocked(aiConsoleApiClient.model.createModel).mockResolvedValue({} as never)
    refetchModels.mockImplementation(async () => {
      models.value = {
        models: [model('edited', ['tool-call', 'reasoning']), model('untouched', ['reasoning'])],
      }
    })
  })

  it('updates untouched recommendations, preserves manual deselection, and imports only on request', async () => {
    const provider: AiProvider = {
      apiVersion: 'aifoundation.halo.run/v1alpha1',
      kind: 'AiProvider',
      metadata: { name: 'openai-prod' },
      spec: { providerType: 'openai', displayName: 'OpenAI', enabled: true },
    }
    const wrapper = mount(ProviderModelsDiscoveryModal, {
      props: { provider },
      global: { stubs: { SearchInput: true } },
    })
    const buttons = (label: string) =>
      wrapper.findAll('button').filter((button) => button.text() === label)
    await wrapper.findAll('input[type="checkbox"]')[0].setValue(true)
    await wrapper.findAll('input[type="checkbox"]')[1].setValue(true)
    await buttons('工具调用')[0].trigger('click')
    await buttons('刷新能力目录')[0].trigger('click')
    await flushPromises()

    expect(buttons('工具调用')[0].attributes('aria-pressed')).toBe('false')
    expect(buttons('推理')[0].attributes('aria-pressed')).toBe('false')
    expect(buttons('推理')[1].attributes('aria-pressed')).toBe('true')
    expect(aiConsoleApiClient.model.createModel).not.toHaveBeenCalled()
    expect(aiConsoleApiClient.model.updateModel).not.toHaveBeenCalled()

    await buttons('导入 2 个模型')[0].trigger('click')
    await flushPromises()
    expect(aiConsoleApiClient.model.createModel).toHaveBeenCalledTimes(2)
    expect(
      vi.mocked(aiConsoleApiClient.model.createModel).mock.calls[0][0].aiModel.spec.features,
    ).toEqual([])
    expect(
      vi.mocked(aiConsoleApiClient.model.createModel).mock.calls[1][0].aiModel.spec.features,
    ).toEqual(['reasoning'])
    wrapper.unmount()
  })

  it('applies a background catalog update after the initial discovery finishes', async () => {
    isLoadingModels.value = true
    const wrapper = mount(ProviderModelsDiscoveryModal, {
      props: {
        provider: {
          apiVersion: 'aifoundation.halo.run/v1alpha1',
          kind: 'AiProvider',
          metadata: { name: 'openai-prod' },
          spec: { providerType: 'openai', displayName: 'OpenAI', enabled: true },
        },
      },
      global: { stubs: { SearchInput: true } },
    })
    catalogStatus.value = { available: true, fetchedAt: '2026-10-26T11:00:00Z' }
    await flushPromises()
    expect(refetchModels).not.toHaveBeenCalled()

    isLoadingModels.value = false
    await flushPromises()
    expect(refetchModels).toHaveBeenCalledTimes(1)
    expect(aiConsoleApiClient.model.createModel).not.toHaveBeenCalled()
    expect(aiConsoleApiClient.model.updateModel).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
