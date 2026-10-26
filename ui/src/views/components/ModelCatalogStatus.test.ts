import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import ModelCatalogProvenance from './ModelCatalogProvenance.vue'
import ModelCatalogStatus from './ModelCatalogStatus.vue'

const global = {
  stubs: {
    VButton: { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
  },
}

describe('model catalog status', () => {
  it('offers refresh without blocking discovery when the catalog is unavailable', async () => {
    const wrapper = mount(ModelCatalogStatus, { props: { refreshing: false }, global })
    expect(wrapper.text()).toContain('暂无缓存，使用供应商信息和默认规则')
    await wrapper.get('button').trigger('click')
    expect(wrapper.emitted('refresh')).toHaveLength(1)
    expect(wrapper.text()).toContain('刷新不会修改已保存模型')
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
    expect(wrapper.get('button').attributes('disabled')).toBeDefined()
  })

  it('shows exact catalog identity and supplemented fields', () => {
    const wrapper = mount(ModelCatalogProvenance, {
      props: {
        match: {
          providerId: 'moonshotai-cn',
          modelId: 'kimi-k2.5',
          fetchedAt: '2026-10-26T10:00:00Z',
          fields: ['features.tool-call', 'language.imageInput'],
        },
      },
    })
    expect(wrapper.text()).toContain('models.dev 补全')
    expect(wrapper.text()).toContain('moonshotai-cn / kimi-k2.5')
    expect(wrapper.text()).toContain('工具调用、图片输入')
  })
})
