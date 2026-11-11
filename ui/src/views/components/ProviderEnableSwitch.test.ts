import { aiCoreApiClient } from '@/api'
import type { AiProvider } from '@/api/generated'
import { QK_MODEL_OPTIONS } from '@/composables/use-model-options-fetch'
import { QK_PROVIDER, QK_PROVIDERS } from '@/composables/use-providers-fetch'
import { mount } from '@vue/test-utils'
import type { AxiosResponse } from 'axios'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import ProviderEnableSwitch from './ProviderEnableSwitch.vue'

const refetchQueries = vi.fn()
const invalidateQueries = vi.fn()

vi.mock('@/api', () => ({
  aiCoreApiClient: {
    provider: {
      patchAiProvider: vi.fn(),
    },
  },
}))

vi.mock('@halo-dev/components', () => ({
  Toast: {
    success: vi.fn(),
    error: vi.fn(),
  },
  VSwitch: defineComponent({
    props: {
      modelValue: Boolean,
      loading: Boolean,
      disabled: Boolean,
    },
    emits: ['click'],
    setup(props, { emit }) {
      return () =>
        h('button', {
          type: 'button',
          'aria-checked': props.modelValue,
          disabled: props.disabled,
          onClick: (event: MouseEvent) => emit('click', event),
        })
    },
  }),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({
    refetchQueries,
    invalidateQueries,
  }),
  useMutation: ({ mutationFn, onSuccess, onError }: Record<string, unknown>) => {
    const isPending = ref(false)
    return {
      isPending,
      mutate: async (value: boolean) => {
        isPending.value = true
        try {
          const data = await (mutationFn as (value: boolean) => Promise<unknown>)(value)
          ;(onSuccess as (data: unknown, value: boolean) => void)?.(data, value)
        } catch (error) {
          ;(onError as (error: unknown) => void)?.(error)
        } finally {
          isPending.value = false
        }
      },
    }
  },
}))

describe('ProviderEnableSwitch', () => {
  it('patches provider enabled state and refreshes affected queries', async () => {
    const patchAiProvider = vi.mocked(aiCoreApiClient.provider.patchAiProvider)
    patchAiProvider.mockResolvedValue(response(provider(true)))

    const wrapper = mount(ProviderEnableSwitch, {
      props: {
        provider: provider(true),
      },
      global: {
        directives: {
          tooltip: () => {},
        },
      },
    })

    await wrapper.find('button').trigger('click')

    expect(patchAiProvider).toHaveBeenCalledWith({
      name: 'openai-prod',
      jsonPatchInner: [
        {
          op: 'add',
          path: '/spec/enabled',
          value: false,
        },
      ],
    })
    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDERS] })
    expect(refetchQueries).toHaveBeenCalledWith({ queryKey: [QK_PROVIDER, 'openai-prod'] })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: [QK_MODEL_OPTIONS] })
  })
})

function response(data: AiProvider): AxiosResponse<AiProvider> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: {
      headers: {} as AxiosResponse<AiProvider>['config']['headers'],
    },
  }
}

function provider(enabled: boolean): AiProvider {
  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiProvider',
    metadata: {
      name: 'openai-prod',
    },
    spec: {
      displayName: 'OpenAI Production',
      providerType: 'openai',
      enabled,
      apiKeySecretName: 'openai-secret',
    },
  }
}
