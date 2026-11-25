import { aiConsoleApiClient } from '@/api'
import type { AiProvider, ProviderHealthReport } from '@/api/generated'
import {
  AiProviderStatusFailureCategoryEnum,
  AiProviderStatusPhaseEnum,
  ProviderHealthCheckResultEnum,
  ProviderHealthCheckSkipReasonEnum,
  ProviderHealthCheckTargetEnum,
} from '@/api/generated'
import { reloadProviderQueries } from '@/composables/use-providers-fetch'
import { mount } from '@vue/test-utils'
import type { AxiosResponse } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref, type Ref } from 'vue'
import ProviderDetail from './ProviderDetail.vue'

const selectedProvider = ref<string | undefined>('openai-prod')
const setQueryData = vi.fn()
const refetchQueries = vi.fn()

let currentProvider: Ref<AiProvider | null> = ref(null)

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    provider: {
      getProvider: vi.fn(),
      deleteProvider: vi.fn(),
      testProviderConnectivity: vi.fn(),
    },
  },
}))

vi.mock('@/composables/use-provider-state', () => ({
  useProviderQueryState: () => ({ selectedProvider }),
}))

vi.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderType: () => ref({ displayName: 'OpenAI', iconUrl: undefined, requiresBaseUrl: false }),
}))

vi.mock('@/composables/use-providers-fetch', () => ({
  QK_PROVIDER: 'plugin:ai-foundation:provider',
  QK_PROVIDERS: 'plugin:ai-foundation:providers',
  reloadProviderQueries: vi.fn(),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQuery: () => ({ data: currentProvider, isLoading: ref(false) }),
  useMutation: ({ mutationFn, onSuccess, onError }: Record<string, unknown>) => ({
    isPending: ref(false),
    mutate: async (value: unknown) => {
      try {
        const response = await (mutationFn as (value: unknown) => Promise<unknown>)(value)
        ;(onSuccess as (data: unknown, value: unknown) => void)?.(response, value)
      } catch (error) {
        ;(onError as (error: unknown) => void)?.(error)
      }
    },
  }),
  useQueryClient: () => ({ setQueryData, refetchQueries, invalidateQueries: vi.fn() }),
}))

vi.mock('@halo-dev/ui-shared', () => ({
  utils: { date: { format: () => '2026-11-24 10:00:00' } },
}))

vi.mock('@halo-dev/components', () => ({
  Dialog: { warning: vi.fn() },
  Toast: { success: vi.fn(), error: vi.fn() },
  VButton: defineComponent({
    inheritAttrs: false,
    emits: ['click'],
    setup(_, { attrs, slots, emit }) {
      return () =>
        h('button', { ...attrs, onClick: (event: MouseEvent) => emit('click', event) }, [
          slots.icon?.(),
          slots.default?.(),
        ])
    },
  }),
  VCard: defineComponent({
    setup(_, { slots }) {
      return () => h('section', [slots.header?.(), slots.default?.()])
    },
  }),
  VDescription: defineComponent({
    setup(_, { slots }) {
      return () => h('dl', { class: 'description' }, slots.default?.())
    },
  }),
  VDescriptionItem: defineComponent({
    props: { label: String, content: String, verticalCenter: Boolean },
    setup(props, { slots }) {
      return () =>
        h('div', { class: 'description-item', 'data-label': props.label }, [
          h('dt', props.label),
          h('dd', slots.default?.() ?? props.content),
        ])
    },
  }),
  VDropdown: defineComponent({
    setup(_, { slots }) {
      return () => h('div', { class: 'dropdown' }, [slots.default?.(), slots.popper?.()])
    },
  }),
  VDropdownItem: defineComponent({
    emits: ['click'],
    setup(_, { slots, emit }) {
      return () =>
        h(
          'button',
          { class: 'dropdown-item', onClick: (event: MouseEvent) => emit('click', event) },
          slots.default?.(),
        )
    },
  }),
  VEmpty: defineComponent({
    props: { title: String, message: String },
    setup(props) {
      return () => h('div', `${props.title} ${props.message}`)
    },
  }),
  VLoading: defineComponent({ setup: () => () => h('div', { class: 'loading' }) }),
  VStatusDot: defineComponent({
    props: { state: String, text: String, animate: Boolean },
    setup(props) {
      return () => h('span', { class: 'status-dot', 'data-state': props.state }, props.text ?? '')
    },
  }),
}))

const stubs = {
  ProviderEditingModal: true,
  ProviderEnableSwitch: true,
  ProviderModelList: true,
}

function provider(status?: AiProvider['status']): AiProvider {
  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiProvider',
    metadata: { name: 'openai-prod' },
    spec: {
      displayName: 'OpenAI Production',
      providerType: 'openai',
      enabled: true,
      apiKeySecretName: 'openai-secret',
    },
    status,
  } as AiProvider
}

function report(overrides: Partial<ProviderHealthReport> = {}): ProviderHealthReport {
  return {
    providerName: 'openai-prod',
    phase: AiProviderStatusPhaseEnum.Error,
    failureCategory: AiProviderStatusFailureCategoryEnum.Authentication,
    message: 'HTTP 401 Unauthorized',
    checkedAt: '2026-11-24T10:00:00Z',
    checks: [
      {
        target: ProviderHealthCheckTargetEnum.Configuration,
        result: ProviderHealthCheckResultEnum.Passed,
        message: 'Configuration is valid',
      },
      {
        target: ProviderHealthCheckTargetEnum.Network,
        result: ProviderHealthCheckResultEnum.Passed,
        httpStatus: 401,
        latencyMs: 420,
      },
      {
        target: ProviderHealthCheckTargetEnum.Authentication,
        result: ProviderHealthCheckResultEnum.Failed,
        category: AiProviderStatusFailureCategoryEnum.Authentication,
        httpStatus: 401,
        message: 'HTTP 401 Unauthorized',
        detail: '{"error":{"message":"Invalid API key"}}',
      },
      {
        target: ProviderHealthCheckTargetEnum.ModelDiscovery,
        result: ProviderHealthCheckResultEnum.Skipped,
        skipReason: ProviderHealthCheckSkipReasonEnum.BlockedByPreviousFailure,
      },
      {
        target: ProviderHealthCheckTargetEnum.Chat,
        result: ProviderHealthCheckResultEnum.Skipped,
        skipReason: ProviderHealthCheckSkipReasonEnum.NotRequested,
      },
      {
        target: ProviderHealthCheckTargetEnum.Embedding,
        result: ProviderHealthCheckResultEnum.Skipped,
        skipReason: ProviderHealthCheckSkipReasonEnum.NoModel,
      },
    ],
    ...overrides,
  }
}

function response(data: ProviderHealthReport): AxiosResponse<ProviderHealthReport> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: {} as AxiosResponse<ProviderHealthReport>['config']['headers'] },
  }
}

describe('ProviderDetail health diagnostics', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    currentProvider = ref(provider(undefined))
    vi.mocked(aiConsoleApiClient.provider.testProviderConnectivity).mockResolvedValue(
      response(report()),
    )
  })

  it('runs the default check without model calls', async () => {
    const wrapper = mount(ProviderDetail, { global: { stubs } })

    const quickButton = wrapper.findAll('button').find((node) => node.text() === '检查连通性')
    expect(quickButton).toBeTruthy()
    await quickButton!.trigger('click')

    expect(aiConsoleApiClient.provider.testProviderConnectivity).toHaveBeenCalledWith({
      name: 'openai-prod',
      deep: false,
    })
  })

  it('marks the deep check as explicitly requesting model calls', async () => {
    const wrapper = mount(ProviderDetail, { global: { stubs } })

    const deepButton = wrapper
      .findAll('button')
      .find((node) => node.text() === '深度检查（会实际调用模型）')
    expect(deepButton).toBeTruthy()
    await deepButton!.trigger('click')

    expect(aiConsoleApiClient.provider.testProviderConnectivity).toHaveBeenCalledWith({
      name: 'openai-prod',
      deep: true,
    })
  })

  it('applies the returned report to the provider detail and refreshes queries', async () => {
    const wrapper = mount(ProviderDetail, { global: { stubs } })

    await wrapper
      .findAll('button')
      .find((node) => node.text() === '检查连通性')!
      .trigger('click')

    expect(setQueryData).toHaveBeenCalledWith(
      ['plugin:ai-foundation:provider', 'openai-prod'],
      expect.objectContaining({
        status: expect.objectContaining({
          phase: AiProviderStatusPhaseEnum.Error,
          failureCategory: AiProviderStatusFailureCategoryEnum.Authentication,
          message: 'HTTP 401 Unauthorized',
        }),
      }),
    )
    expect(reloadProviderQueries).toHaveBeenCalled()
  })

  it('keeps diagnostics compact inside the 状态信息 row until expanded, then renders the six checks', async () => {
    currentProvider.value = provider({
      phase: AiProviderStatusPhaseEnum.Error,
      failureCategory: AiProviderStatusFailureCategoryEnum.Authentication,
      message: 'HTTP 401 Unauthorized',
      lastCheckedAt: '2026-11-24T10:00:00Z',
      checks: report().checks,
    })

    const wrapper = mount(ProviderDetail, { global: { stubs } })

    const statusItem = wrapper
      .findAll('.description-item')
      .find((node) => node.attributes('data-label') === '状态信息')
    expect(statusItem).toBeTruthy()
    expect(statusItem!.text()).toContain('认证失败：HTTP 401 Unauthorized')

    expect(wrapper.text()).not.toContain('配置检查')
    // diagnostics live in the top card's 状态信息 row, not in a separate card
    expect(wrapper.findAll('section')).toHaveLength(1)

    await wrapper
      .findAll('button')
      .find((node) => node.text().includes('展开'))!
      .trigger('click')

    const text = statusItem!.text()
    expect(text).toContain('配置检查')
    expect(text).toContain('网络连通')
    expect(text).toContain('认证')
    expect(text).toContain('模型发现')
    expect(text).toContain('对话模型')
    expect(text).toContain('向量模型')
    expect(text).toContain('HTTP 401 · 420 ms')
    expect(text).toContain('请确认 API Key 有效、未过期，或该供应商是否需要凭据。')
    expect(text).not.toContain('Invalid API key')

    const detailToggle = wrapper
      .findAll('button')
      .find((node) => node.text() === '查看原始错误摘要')
    await detailToggle!.trigger('click')

    expect(statusItem!.text()).toContain('Invalid API key')
  })

  it('surfaces an abnormal provider in the 状态信息 row instead of a separate banner', () => {
    currentProvider.value = provider({
      phase: AiProviderStatusPhaseEnum.Error,
      failureCategory: AiProviderStatusFailureCategoryEnum.Network,
      message: 'Connection refused',
    })
    const wrapper = mount(ProviderDetail, { global: { stubs } })

    const statusItem = wrapper
      .findAll('.description-item')
      .find((node) => node.attributes('data-label') === '状态信息')
    expect(statusItem!.text()).toContain('Connection refused')
    expect(statusItem!.find('[data-state="error"]').exists()).toBe(true)
  })

  it('shows an empty diagnostic state before the first check', () => {
    const wrapper = mount(ProviderDetail, { global: { stubs } })

    expect(wrapper.text()).toContain('尚未执行检查')
  })
})
