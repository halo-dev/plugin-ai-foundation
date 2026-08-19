import { aiConsoleApiClient } from '@/api'
import { describe, expect, it, rstest } from '@rstest/core'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, ref, type PropType } from 'vue'
import UsageResetModal from '../UsageResetModal.vue'

const invalidateQueries = rstest.fn()

rstest.mock('@/api', () => ({
  aiConsoleApiClient: {
    usageStatistics: {
      resetAiUsageStatistics: rstest.fn(),
    },
  },
}))

rstest.mock('@halo-dev/components', () => ({
  Toast: {
    success: rstest.fn(),
    error: rstest.fn(),
  },
  VModal: defineComponent({
    emits: ['close'],
    setup(_, { slots }) {
      return () =>
        h('div', { class: 'v-modal' }, [slots.default?.(), h('div', slots.footer?.() || [])])
    },
  }),
  VSpace: defineComponent({
    setup(_, { slots }) {
      return () => h('div', slots.default?.())
    },
  }),
  VButton: defineComponent({
    props: {
      loading: Boolean,
      disabled: Boolean,
      type: String as PropType<string>,
    },
    emits: ['click'],
    setup(props, { emit, slots }) {
      return () =>
        h(
          'button',
          {
            disabled: props.disabled,
            'data-type': props.type,
            onClick: () => emit('click'),
          },
          slots.default?.(),
        )
    },
  }),
}))

rstest.mock('@tanstack/vue-query', () => ({
  useQueryClient: () => ({ invalidateQueries }),
  useMutation: ({ mutationFn, onSuccess, onError }: Record<string, unknown>) => {
    const isPending = ref(false)
    return {
      isPending,
      mutate: async () => {
        isPending.value = true
        try {
          const data = await (mutationFn as () => Promise<unknown>)()
          ;(onSuccess as (data: unknown) => void)?.(data)
        } catch (error) {
          ;(onError as (error: unknown) => void)?.(error)
        } finally {
          isPending.value = false
        }
      },
    }
  },
}))

function mountModal() {
  return mount(UsageResetModal)
}

describe('UsageResetModal', () => {
  it('keeps the confirm button disabled until RESET is typed', async () => {
    const wrapper = mountModal()
    const confirm = wrapper.find('button[data-type="danger"]')
    expect(confirm.attributes('disabled')).toBeDefined()

    await wrapper.find('input').setValue('reset')
    expect(confirm.attributes('disabled')).toBeDefined()

    await wrapper.find('input').setValue('RESET')
    expect(confirm.attributes('disabled')).toBeUndefined()
  })

  it('does not call the API before explicit confirmation', async () => {
    const wrapper = mountModal()
    await wrapper.find('button[data-type="danger"]').trigger('click')
    expect(aiConsoleApiClient.usageStatistics.resetAiUsageStatistics).not.toHaveBeenCalled()
  })

  it('resets statistics and invalidates usage queries after typing RESET', async () => {
    rstest
      .mocked(aiConsoleApiClient.usageStatistics.resetAiUsageStatistics)
      .mockResolvedValue({ data: { epoch: 2 } } as never)

    const wrapper = mountModal()
    await wrapper.find('input').setValue('RESET')
    await wrapper.find('button[data-type="danger"]').trigger('click')
    await flushPromises()

    expect(aiConsoleApiClient.usageStatistics.resetAiUsageStatistics).toHaveBeenCalledWith({
      resetRequest: { confirmation: 'RESET' },
    })
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['plugin:ai-foundation:usage-summary'],
    })
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['plugin:ai-foundation:usage-calls'],
    })
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['plugin:ai-foundation:usage-health'],
    })
  })
})
