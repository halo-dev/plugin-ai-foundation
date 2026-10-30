import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelCatalogProvenance from './ModelCatalogProvenance.vue'
import ModelCatalogStatus from './ModelCatalogStatus.vue'

const tooltipStub = {
  mounted(el: HTMLElement, binding: { value: string }) {
    el.setAttribute('data-tooltip', binding.value)
  },
}

const global = {
  directives: { tooltip: tooltipStub },
  stubs: {
    VButton: { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
    VStatusDot: { props: ['state', 'animate'], template: '<span data-test="status-dot" />' },
    IconRefreshLine: { template: '<svg />' },
    IconInformation: { template: '<svg />' },
  },
}

describe('model catalog status', () => {
  it('offers refresh without blocking discovery when the catalog is unavailable', async () => {
    const wrapper = mount(ModelCatalogStatus, { props: { refreshing: false }, global })
    expect(wrapper.text()).toContain('暂无目录缓存，使用供应商信息和默认规则')
    const refresh = wrapper.get('button[aria-label="刷新能力目录"]')
    await refresh.trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
    expect(refresh.attributes('data-tooltip')).toContain('不会修改已保存的模型')
  })

  it('shows the last successful timestamp and failure while using stale data', () => {
    const wrapper = mount(ModelCatalogStatus, {
      props: {
        refreshing: false,
        status: {
          available: true,
          stale: true,
          fetchedAt: '2026-10-26T10:00:00Z',
          error: '目录同步失败，已保留上次成功数据',
        },
      },
      global,
    })
    expect(wrapper.text()).toContain('使用旧缓存')
    expect(wrapper.text()).toContain('2026')
    expect(wrapper.text()).toContain('已保留上次成功数据')
  })

  it('prevents duplicate refreshes during synchronization or import', () => {
    const wrapper = mount(ModelCatalogStatus, { props: { refreshing: true }, global })
    expect(wrapper.text()).toContain('正在同步')
    expect(wrapper.get('button[aria-label="刷新能力目录"]').attributes('disabled')).toBeDefined()
  })

  it('shows supplemented fields inline and keeps catalog identity in the tooltip', () => {
    const wrapper = mount(ModelCatalogProvenance, {
      props: {
        match: {
          providerId: 'moonshotai-cn',
          modelId: 'kimi-k2.5',
          fetchedAt: '2026-10-26T10:00:00Z',
          fields: ['features.tool-call', 'language.imageInput'],
        },
      },
      global,
    })
    expect(wrapper.text()).toContain('models.dev 补全：工具调用、图片输入')
    expect(wrapper.get('p').attributes('data-tooltip')).toContain('moonshotai-cn / kimi-k2.5')
    expect(wrapper.get('p').attributes('data-tooltip')).toContain('目录同步于')
  })
})
