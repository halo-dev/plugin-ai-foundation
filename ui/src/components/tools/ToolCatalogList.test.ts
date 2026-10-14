import type { CatalogTool } from '@/utils/tools'
import { groupTools } from '@/utils/tools'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ToolCatalogList from './ToolCatalogList.vue'

vi.mock('@halo-dev/components', () => ({
  VCard: { template: '<article><slot /><slot name="footer" /></article>' },
  VTag: { template: '<span><slot /></span>' },
  VStatusDot: { props: ['text'], template: '<span>{{ text }}</span>' },
  VButton: { props: ['disabled'], template: '<button :disabled="disabled"><slot /></button>' },
  VSwitch: {
    name: 'VSwitch',
    props: ['disabled', 'modelValue', 'loading'],
    emits: ['update:modelValue'],
    template: `<button role="switch" :disabled="disabled || loading"
      :aria-checked="modelValue" @click="$emit('update:modelValue', !modelValue)" />`,
  },
}))

const tools: CatalogTool[] = [
  {
    id: 'ai-foundation/search_site',
    displayName: '站内搜索',
    pluginName: 'ai-foundation',
    category: 'site',
    tags: ['内容'],
    enabled: true,
    configured: true,
    status: 'ENABLED',
  },
  {
    id: 'ai-foundation/web_search',
    displayName: '联网搜索',
    pluginName: 'ai-foundation',
    category: 'web',
    tags: ['联网'],
    enabled: true,
    configured: false,
    status: 'UNCONFIGURED',
    configurationSchema: [{}],
  },
]

describe('ToolCatalogList', () => {
  it('renders tools as a flat card grid with source, category, status and tags', () => {
    const wrapper = mount(ToolCatalogList, {
      props: { groups: groupTools(tools), management: true },
    })
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(2)
    expect(wrapper.findAll('section')).toHaveLength(0)
    expect(wrapper.get('[data-tool-id="ai-foundation/search_site"]').text()).toContain('已启用')
    expect(wrapper.get('[data-tool-id="ai-foundation/web_search"]').text()).toContain('未配置')
    expect(wrapper.text()).toContain('来源：ai-foundation')
    expect(wrapper.text()).toContain('网页')
    expect(wrapper.text()).toContain('联网')
    wrapper.unmount()
  })

  it('emits management actions and suppresses them while the tool update is pending', async () => {
    const wrapper = mount(ToolCatalogList, {
      props: { groups: groupTools(tools), management: true },
    })
    await wrapper.get('button[aria-label="配置 联网搜索"]').trigger('click')
    expect(wrapper.emitted('configure')).toEqual([[tools[1]]])
    await wrapper.get('button[aria-label="启用 站内搜索"]').trigger('click')
    expect(wrapper.emitted('enabled')).toEqual([[tools[0], false]])
    await wrapper.setProps({ pendingIds: ['ai-foundation/web_search'] })
    const switchComponent = wrapper.findAllComponents({ name: 'VSwitch' })
    expect(switchComponent).toHaveLength(2)
    // A synthetic component event must obey the same pending guard as the rendered control.
    switchComponent[1]?.vm.$emit('update:modelValue', false)
    expect(wrapper.emitted('enabled')).toHaveLength(1)
    expect(wrapper.get('button[aria-label="配置 联网搜索"]').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('disables unavailable selector cards and guards synthetic DOM change events', async () => {
    const wrapper = mount(ToolCatalogList, {
      props: {
        groups: groupTools(tools),
        selectedIds: ['ai-foundation/web_search'],
        compact: true,
      },
    })
    const unavailable = wrapper.get('input[aria-label="选择 联网搜索"]')
    expect(unavailable.attributes('disabled')).toBeDefined()
    unavailable.element.dispatchEvent(new Event('change', { bubbles: true }))
    expect(wrapper.emitted('toggle')).toBeUndefined()
    await wrapper.get('input[aria-label="选择 站内搜索"]').setValue(true)
    expect(wrapper.emitted('toggle')).toEqual([['ai-foundation/search_site']])
    wrapper.unmount()
  })
})
