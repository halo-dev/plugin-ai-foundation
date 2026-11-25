import type { AiProvider } from '@/api/generated'
import {
  AiProviderStatusFailureCategoryEnum,
  AiProviderStatusPhaseEnum,
} from '@/api/generated'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import ProviderListItem from './ProviderListItem.vue'

vi.mock('@/composables/use-provider-types-fetch', () => ({
  useProviderType: () => ({ value: { displayName: 'OpenAI', iconUrl: undefined } }),
}))

vi.mock('@halo-dev/components', () => ({
  VAvatar: defineComponent({
    props: { src: String, circle: Boolean, size: String },
    setup: () => () => h('div', { class: 'avatar' }),
  }),
  VStatusDot: defineComponent({
    props: { state: String, text: String, animate: Boolean },
    setup(props) {
      return () =>
        h('span', { class: 'status-dot', 'data-state': props.state }, props.text ?? '')
    },
  }),
}))

function provider(status?: AiProvider['status']): AiProvider {
  return {
    metadata: { name: 'openai-prod' },
    spec: { providerType: 'openai', displayName: '生产环境', enabled: true },
    status,
  } as AiProvider
}

describe('ProviderListItem', () => {
  it('shows the failure category and a truncated summary when abnormal', () => {
    const wrapper = mount(ProviderListItem, {
      props: {
        provider: provider({
          phase: AiProviderStatusPhaseEnum.Error,
          failureCategory: AiProviderStatusFailureCategoryEnum.Authentication,
          message: 'Invalid API key for provider openai-prod with a very long trailing detail',
        }),
        isSelected: false,
      },
    })

    expect(wrapper.text()).toContain('异常 · 认证')
    expect(wrapper.text()).toContain('Invalid API key')
    expect(wrapper.find('.status-dot').attributes('data-state')).toBe('error')
  })

  it('shows unchecked providers instead of a normal status', () => {
    const wrapper = mount(ProviderListItem, {
      props: { provider: provider(undefined), isSelected: false },
    })

    expect(wrapper.text()).toContain('未检查')
    expect(wrapper.find('.status-dot').attributes('data-state')).toBe('default')
  })

  it('shows a successful status without a summary line', () => {
    const wrapper = mount(ProviderListItem, {
      props: {
        provider: provider({
          phase: AiProviderStatusPhaseEnum.Ok,
          message: 'Connectivity check passed',
        }),
        isSelected: true,
      },
    })

    expect(wrapper.text()).toContain('正常')
    expect(wrapper.text()).not.toContain('Connectivity check passed')
    expect(wrapper.find('.status-dot').attributes('data-state')).toBe('success')
  })
})
