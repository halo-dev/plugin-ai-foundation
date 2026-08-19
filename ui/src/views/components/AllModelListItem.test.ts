import { aiConsoleApiClient } from '@/api'
import type { AiModel } from '@/api/generated'
import { QK_MODELS } from '@/composables/use-models-fetch'
import { Dialog } from '@halo-dev/components'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import AllModelListItem from './AllModelListItem.vue'

const invalidateQueries = vi.fn()

vi.mock('@/api', () => ({
  aiConsoleApiClient: {
    model: {
      deleteModel: vi.fn(),
    },
  },
}))

vi.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderTypesFetch: () => ({ data: ref([]) }),
}))

vi.mock('@/composables/use-providers-fetch', () => ({
  useProvidersFetch: () => ({ data: ref([]) }),
}))

vi.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
}))

vi.mock('@vueuse/core', () => ({
  useClipboard: () => ({
    copy: vi.fn(),
    isSupported: ref(true),
  }),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@halo-dev/components', () => {
  const Stub = {
    template:
      '<div><slot /><slot name="start" /><slot name="end" /><slot name="dropdownItems" /></div>',
  }
  const DropdownItem = {
    emits: ['click'],
    template: '<button type="button" @click="$emit(\'click\')"><slot /></button>',
  }
  return {
    Dialog: { warning: vi.fn() },
    Toast: {
      success: vi.fn(),
      error: vi.fn(),
    },
    VAvatar: Stub,
    VDropdownDivider: Stub,
    VDropdownItem: DropdownItem,
    VEntity: Stub,
    VEntityField: Stub,
    VStatusDot: Stub,
    VTag: Stub,
  }
})

describe('AllModelListItem', () => {
  it('invalidates all model list queries after deletion', async () => {
    const deleteModel = vi.mocked(aiConsoleApiClient.model.deleteModel)
    const dialogWarning = vi.mocked(Dialog.warning)
    deleteModel.mockResolvedValue({} as Awaited<ReturnType<typeof deleteModel>>)

    const wrapper = mount(AllModelListItem, {
      props: {
        model: model(),
      },
      global: {
        stubs: {
          ModelBadgeGroup: true,
          ModelEnableSwitch: true,
        },
      },
    })

    const deleteButton = wrapper.findAll('button').find((button) => button.text() === '删除')
    expect(deleteButton).toBeDefined()
    await deleteButton?.trigger('click')

    const { onConfirm } = dialogWarning.mock.calls[0][0] as {
      onConfirm: () => Promise<void>
    }
    await onConfirm()

    expect(deleteModel).toHaveBeenCalledWith({ name: 'openai-prod-gpt-4' })
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: [QK_MODELS] })
  })
})

function model(): AiModel {
  return {
    apiVersion: 'aifoundation.halo.run/v1alpha1',
    kind: 'AiModel',
    metadata: {
      name: 'openai-prod-gpt-4',
    },
    spec: {
      providerName: 'openai-prod',
      modelId: 'gpt-4',
      displayName: 'GPT-4',
      enabled: true,
      modelType: 'language',
    },
  }
}
