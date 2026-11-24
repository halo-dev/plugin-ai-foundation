import { aiConsoleApiClient } from '@/api'
import type {
  AiProvider,
  MissingRemoteModelItem,
  ModelCatalogStatus,
  ModelParameterConstraints,
  ModelSyncRequest,
  ProviderTypeInfo,
} from '@/api/generated'
import type { DiscoveredModel } from '@/composables/use-models-fetch'
import { featureConstraints, featuresWithConstraints } from '@/utils/model-feature-support'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import ProviderModelsDiscoveryModal from './ProviderModelsDiscoveryModal.vue'

const models = ref<{
  models: DiscoveredModel[]
  missingModels?: MissingRemoteModelItem[]
}>({ models: [] })
const refetchModels = vi.fn()
const providerTypes = ref<ProviderTypeInfo[]>([])
const catalogStatus = ref<ModelCatalogStatus>({ available: true })
const isLoadingModels = ref(false)

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    provider: { refreshModelCatalog: vi.fn(), syncProviderModels: vi.fn() },
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
    Dialog: {
      warning: vi.fn((options: { onConfirm?: () => void | Promise<void> }) =>
        options.onConfirm?.(),
      ),
    },
    Toast: { warning: vi.fn(), success: vi.fn(), error: vi.fn() },
    VButton: defineComponent({
      props: ['disabled', 'loading'],
      setup:
        (props, { slots }) =>
        () =>
          h('button', { disabled: props.disabled }, slots.default?.()),
    }),
    IconInformation: defineComponent({ template: '<svg />' }),
    IconRefreshLine: defineComponent({ template: '<svg />' }),
    VEmpty: container,
    VEntity: container,
    VEntityContainer: container,
    VEntityField: defineComponent({
      props: ['title', 'description'],
      setup: (props) => () => h('div', `${props.title || ''} ${props.description || ''}`),
    }),
    VLoading: container,
    VModal: container,
    VSpace: container,
    VStatusDot: container,
    VTag: container,
  }
})

const tooltipStub = {
  mounted(el: HTMLElement, binding: { value: string }) {
    el.setAttribute('data-tooltip', binding.value)
  },
}

function model(
  id: string,
  features: DiscoveredModel['features'],
  overrides: Partial<DiscoveredModel> = {},
): DiscoveredModel {
  return {
    modelId: id,
    displayName: id,
    name: '',
    modelType: 'language',
    adapterType: 'openai-chat',
    features,
    source: 'rule',
    confidence: 'medium',
    syncStatus: 'new',
    syncable: true,
    differences: [],
    ...overrides,
  }
}

function mountDiscovery() {
  const provider: AiProvider = {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiProvider',
    metadata: { name: 'openai-prod' },
    spec: { providerType: 'openai', displayName: 'OpenAI', enabled: true },
  }
  return mount(ProviderModelsDiscoveryModal, {
    props: { provider },
    global: { stubs: { SearchInput: true }, directives: { tooltip: tooltipStub } },
  })
}

function syncOperations() {
  const calls = vi.mocked(aiConsoleApiClient.provider.syncProviderModels).mock.calls
  return calls.length ? (calls[0][0].modelSyncRequest as ModelSyncRequest).operations || [] : []
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
    vi.mocked(aiConsoleApiClient.provider.syncProviderModels).mockImplementation(
      async (requestParameters) =>
        ({
          data: {
            providerName: 'openai-prod',
            results: (requestParameters.modelSyncRequest.operations || []).map((operation) => ({
              modelId: operation.modelId,
              action: operation.action,
              outcome: operation.action === 'create' ? 'created' : 'updated',
            })),
          },
        }) as never,
    )
    vi.mocked(aiConsoleApiClient.model.createModel).mockResolvedValue({} as never)
    refetchModels.mockImplementation(async () => {
      models.value = {
        models: [model('edited', ['tool-call', 'reasoning']), model('untouched', ['reasoning'])],
      }
    })
  })

  it('updates untouched recommendations, preserves manual deselection, and syncs only on request', async () => {
    const wrapper = mountDiscovery()
    const buttons = (label: string) =>
      wrapper.findAll('button').filter((button) => button.text() === label)
    await buttons('工具调用')[0].trigger('click')
    await wrapper.get('button[aria-label="刷新能力目录"]').trigger('click')
    await flushPromises()

    expect(buttons('工具调用')[0].attributes('aria-pressed')).toBe('false')
    expect(buttons('推理')[0].attributes('aria-pressed')).toBe('true')
    expect(buttons('推理')[1].attributes('aria-pressed')).toBe('true')
    expect(aiConsoleApiClient.provider.syncProviderModels).not.toHaveBeenCalled()

    await buttons('应用同步（2）')[0].trigger('click')
    await flushPromises()
    expect(aiConsoleApiClient.provider.syncProviderModels).toHaveBeenCalledTimes(1)
    const operations = syncOperations()
    expect(operations).toHaveLength(2)
    expect(operations[0].action).toBe('create')
    expect(operations[0].model?.spec.features).toEqual(['reasoning'])
    expect(operations[0].model?.spec.parameterConstraints).toEqual({ toolCalling: false })
    expect(operations[1].model?.spec.features).toEqual(['reasoning'])
    wrapper.unmount()
  })

  it('applies a background catalog update after the initial discovery finishes', async () => {
    isLoadingModels.value = true
    const wrapper = mountDiscovery()
    catalogStatus.value = { available: true, fetchedAt: '2026-10-26T11:00:00Z' }
    await flushPromises()
    expect(refetchModels).not.toHaveBeenCalled()

    isLoadingModels.value = false
    await flushPromises()
    expect(refetchModels).toHaveBeenCalledTimes(1)
    expect(aiConsoleApiClient.provider.syncProviderModels).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('keeps a deliberate choice after toggling back to the original recommendation', async () => {
    models.value = { models: [model('edited', ['tool-call'])] }
    refetchModels.mockImplementation(async () => {
      models.value = { models: [model('edited', ['tool-call', 'reasoning'])] }
    })
    const wrapper = mountDiscovery()
    const toolButton = wrapper.findAll('button').find((button) => button.text() === '工具调用')!
    await toolButton.trigger('click')
    await toolButton.trigger('click')
    await wrapper.get('button[aria-label="刷新能力目录"]').trigger('click')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '应用同步（1）')!
      .trigger('click')
    await flushPromises()

    const imported = syncOperations()[0].model!
    expect(imported.spec.features).toEqual(['tool-call', 'reasoning'])
    expect(imported.spec.parameterConstraints).toEqual({ toolCalling: true })
    wrapper.unmount()
  })

  it.each([
    { feature: 'tool-call', label: '工具调用', constraints: { toolCalling: false } },
    { feature: 'structured-output', label: '结构化输出', constraints: { structuredOutput: false } },
    { feature: 'reasoning', label: '推理', constraints: { reasoning: { supported: false } } },
  ] as const)(
    'persists an explicit $feature choice when refreshed recommendations agree',
    async ({ feature, label, constraints }) => {
      const supportedFeatures = ['tool-call', 'structured-output', 'reasoning'] as const
      providerTypes.value[0].supportedFeatures = [...supportedFeatures]
      providerTypes.value[0].adapters![0].supportedFeatures = [...supportedFeatures]
      models.value = { models: [model('edited', [feature])] }
      refetchModels.mockImplementation(async () => {
        models.value = { models: [model('edited', [])] }
      })
      const wrapper = mountDiscovery()
      await wrapper
        .findAll('button')
        .find((button) => button.text() === label)!
        .trigger('click')
      await wrapper.get('button[aria-label="刷新能力目录"]').trigger('click')
      await flushPromises()
      await wrapper
        .findAll('button')
        .find((button) => button.text() === '应用同步（1）')!
        .trigger('click')
      await flushPromises()

      const imported = syncOperations()[0].model!
      expect(imported.spec.parameterConstraints).toEqual(constraints)
      const laterRecommendations: ModelParameterConstraints = {
        toolCalling: true,
        structuredOutput: true,
        reasoning: { supported: true },
      }
      expect(
        featuresWithConstraints(
          imported.spec.features,
          featureConstraints(
            { constraints: laterRecommendations },
            imported.spec.parameterConstraints,
          ),
        ),
      ).not.toContain(feature)
      wrapper.unmount()
    },
  )
})

describe('discovery sync state', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    catalogStatus.value = { available: true }
    isLoadingModels.value = false
    providerTypes.value = [
      {
        providerType: 'openai',
        supportedModelTypes: ['language'],
        supportedFeatures: ['tool-call'],
        adapters: [
          {
            adapterType: 'openai-chat',
            modelType: 'language',
            supportedFeatures: ['tool-call'],
          },
        ],
      },
    ]
    vi.mocked(aiConsoleApiClient.provider.syncProviderModels).mockImplementation(
      async (requestParameters) =>
        ({
          data: {
            providerName: 'openai-prod',
            results: (requestParameters.modelSyncRequest.operations || []).map((operation) => ({
              modelId: operation.modelId,
              action: operation.action,
              outcome:
                operation.action === 'update'
                  ? 'updated'
                  : operation.action === 'enable'
                    ? 'enabled'
                    : operation.action === 'disable'
                      ? 'disabled'
                      : 'created',
            })),
          },
        }) as never,
    )
    refetchModels.mockImplementation(async () => undefined)
  })

  it('selects new models by default and groups existing and missing models', async () => {
    models.value = {
      models: [
        model('new-model', ['tool-call']),
        model('existing-model', ['tool-call'], {
          syncStatus: 'existing',
          localModelName: 'saved-existing-model',
          localEnabled: true,
          localDisplayName: 'Existing Model',
          differences: [
            {
              field: 'displayName',
              localValue: 'Existing Model',
              remoteValue: 'existing-model',
              applied: false,
            },
          ],
        }),
        model('disabled-model', ['tool-call'], {
          syncStatus: 'disabled',
          localModelName: 'saved-disabled-model',
          localEnabled: false,
        }),
      ],
      missingModels: [
        {
          modelName: 'saved-missing-model',
          modelId: 'missing-model',
          displayName: 'Missing Model',
          modelType: 'language',
          enabled: true,
        },
      ],
    } as never
    const wrapper = mountDiscovery()
    await flushPromises()

    const groups = wrapper.findAll('section').map((section) => section.text())
    expect(groups[0]).toContain('新增模型')
    expect(groups[0]).toContain('new-model')
    expect(groups[1]).toContain('已存在模型')
    expect(groups[1]).toContain('Existing Model')
    expect(groups[2]).toContain('本地已禁用')
    expect(groups[3]).toContain('远端已消失')
    expect(groups[3]).toContain('不会被自动删除')
    expect(wrapper.text()).toContain('应用同步（1）')

    const checkboxes = wrapper.findAll('input[type="checkbox"]')
    expect((checkboxes[0].element as HTMLInputElement).checked).toBe(true)
    expect((checkboxes[1].element as HTMLInputElement).checked).toBe(false)
    expect((checkboxes[2].element as HTMLInputElement).checked).toBe(false)
    wrapper.unmount()
  })

  it('sends update and enable operations for selected existing models', async () => {
    models.value = {
      models: [
        model('existing-model', ['tool-call'], {
          syncStatus: 'disabled',
          localModelName: 'saved-existing-model',
          localEnabled: false,
          differences: [
            {
              field: 'features',
              localValue: 'tool-call',
              remoteValue: 'tool-call, reasoning',
              applied: true,
            },
          ],
        }),
      ],
    }
    const wrapper = mountDiscovery()
    await flushPromises()
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '应用同步（1）')!
      .trigger('click')
    await flushPromises()

    const operations = syncOperations()
    expect(operations).toHaveLength(1)
    expect(operations[0].action).toBe('update')
    expect(operations[0].localModelName).toBe('saved-existing-model')
    expect(operations[0].discoveredModel?.modelId).toBe('existing-model')
    wrapper.unmount()
  })

  it('does not offer updates when selected models only have local-only differences', async () => {
    models.value = {
      models: [
        model('existing-model', ['tool-call'], {
          syncStatus: 'existing',
          localModelName: 'saved-existing-model',
          localEnabled: true,
          localDisplayName: 'Existing Model',
          differences: [
            {
              field: 'displayName',
              localValue: 'Existing Model',
              remoteValue: 'existing-model',
              applied: false,
            },
          ],
        }),
      ],
    }
    const wrapper = mountDiscovery()
    await flushPromises()
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await flushPromises()

    expect(wrapper.text()).toContain('1 项差异，均保留本地')
    const applyButton = wrapper
      .findAll('button')
      .find((button) => button.text().startsWith('应用同步'))!
    expect(applyButton.text()).toBe('应用同步（0）')
    expect(applyButton.attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('enables locally disabled models without applying profile updates', async () => {
    models.value = {
      models: [
        model('disabled-model', ['tool-call'], {
          syncStatus: 'disabled',
          localModelName: 'saved-disabled-model',
          localEnabled: false,
        }),
      ],
    }
    const wrapper = mountDiscovery()
    await flushPromises()
    await wrapper.get('input[type="checkbox"]').setValue(true)
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '启用 1')!
      .trigger('click')
    await flushPromises()

    const operations = syncOperations()
    expect(operations).toHaveLength(1)
    expect(operations[0]).toEqual({
      action: 'enable',
      modelId: 'disabled-model',
      localModelName: 'saved-disabled-model',
    })
    wrapper.unmount()
  })
})
