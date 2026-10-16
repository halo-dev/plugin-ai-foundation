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
    expect(wrapper.text()).toContain('网络')
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

  it('keeps group and standalone grids separate while the entire group header toggles once', async () => {
    const wrapper = mount(ToolCatalogList, {
      props: { groups: groupTools(groupedTools()), management: true },
    })
    const group = wrapper.get('[data-tool-group-id="ai-foundation/posts"]')
    const groupList = wrapper.get('ul[aria-label="工具组列表"]')
    const standaloneList = wrapper.get('ul[aria-label="工具列表"]')
    expect(group.element.closest('ul')).toBe(groupList.element)
    expect(groupList.element.parentElement?.nextElementSibling).toBe(standaloneList.element)
    expect(standaloneList.findAll('[data-tool-group-id]')).toHaveLength(0)
    expect(standaloneList.findAll('[data-tool-id]')).toHaveLength(1)
    expect(group.text()).toContain('文章工具')
    expect(group.text()).toContain('查询与读取文章内容')
    expect(group.text()).toContain('2 个工具')
    expect(group.text()).toContain('1 个已启用')
    expect(group.text()).toContain('1 个未配置')
    expect(group.findAll('[role="switch"]')).toHaveLength(0)
    expect(group.findAll('[data-tool-id]')).toHaveLength(0)
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(1)
    const expand = group.get('button[aria-label="展开 文章工具 工具组"]')
    expect(expand.attributes('type')).toBe('button')
    expect(expand.findAll('button, input, a')).toHaveLength(0)
    expect(expand.get('[role="heading"]').text()).toBe('文章工具')
    expect(expand.text()).toContain('查询与读取文章内容')
    expect(expand.attributes('aria-expanded')).toBe('false')
    expect(group.find(`[id="${expand.attributes('aria-controls')}"]`).exists()).toBe(true)
    await expand.get('[role="heading"]').trigger('click')
    expect(expand.attributes('aria-expanded')).toBe('true')
    expect(group.findAll('[data-tool-id]')).toHaveLength(2)
    expect(group.element.closest('ul')).toBe(groupList.element)
    expect(standaloneList.findAll('[data-tool-id]')).toHaveLength(1)
    expect(wrapper.findAll('[role="switch"]')).toHaveLength(3)
    await group.get('button[aria-label="收起 文章工具 工具组"]').trigger('click')
    expect(group.findAll('[data-tool-id]')).toHaveLength(0)
    wrapper.unmount()
  })

  it('forwards grouped leaf actions and keeps pending guards on the individual tool', async () => {
    const grouped = groupedTools()
    const wrapper = mount(ToolCatalogList, {
      props: { groups: groupTools(grouped), management: true },
    })
    await wrapper.get('button[aria-label="展开 文章工具 工具组"]').trigger('click')
    await wrapper.get('button[aria-label="配置 文章读取"]').trigger('click')
    expect(wrapper.emitted('configure')).toEqual([[grouped[1]]])
    expect(
      wrapper.get('button[aria-label="收起 文章工具 工具组"]').attributes('aria-expanded'),
    ).toBe('true')
    await wrapper.get('button[aria-label="查看 文章列表 详情"]').trigger('click')
    expect(wrapper.emitted('details')).toEqual([[grouped[0]]])
    expect(
      wrapper.get('button[aria-label="收起 文章工具 工具组"]').attributes('aria-expanded'),
    ).toBe('true')
    await wrapper.get('button[aria-label="启用 文章列表"]').trigger('click')
    expect(wrapper.emitted('enabled')).toEqual([[grouped[0], false]])
    expect(
      wrapper.get('button[aria-label="收起 文章工具 工具组"]').attributes('aria-expanded'),
    ).toBe('true')
    await wrapper.setProps({ pendingIds: [grouped[1]!.id!] })
    const pendingCard = wrapper.get('[data-tool-id="ai-foundation/get_post"]')
    expect(
      pendingCard.get('button[aria-label="配置 文章读取"]').attributes('disabled'),
    ).toBeDefined()
    pendingCard.findComponent({ name: 'VSwitch' }).vm.$emit('update:modelValue', false)
    expect(wrapper.emitted('enabled')).toHaveLength(1)
    expect(
      wrapper.get('button[aria-label="收起 文章工具 工具组"]').attributes('disabled'),
    ).toBeUndefined()
    wrapper.unmount()
  })

  it('automatically expands visible groups for search while allowing manual collapse', async () => {
    const wrapper = mount(ToolCatalogList, {
      props: { groups: groupTools(groupedTools()), management: true },
    })
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(1)
    await wrapper.setProps({ keyword: '文章' })
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(3)
    await wrapper.get('button[aria-label="收起 文章工具 工具组"]').trigger('click')
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(1)
    await wrapper.setProps({ keyword: '读取' })
    expect(wrapper.findAll('[data-tool-id]')).toHaveLength(3)
    wrapper.unmount()
  })

  it('preserves expanded groups across refresh and blocks expansion when the catalog is disabled', async () => {
    const grouped = groupedTools()
    const wrapper = mount(ToolCatalogList, {
      props: { groups: groupTools(grouped), management: true },
    })
    await wrapper.get('button[aria-label="展开 文章工具 工具组"]').trigger('click')
    await wrapper.setProps({ groups: groupTools(grouped.map((tool) => ({ ...tool }))) })
    expect(
      wrapper.get('button[aria-label="收起 文章工具 工具组"]').attributes('aria-expanded'),
    ).toBe('true')
    await wrapper.setProps({ disabled: true })
    const collapse = wrapper.get('button[aria-label="收起 文章工具 工具组"]')
    expect(collapse.attributes('disabled')).toBeDefined()
    collapse.element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(collapse.attributes('aria-expanded')).toBe('true')
    wrapper.unmount()
  })
})

function groupedTools(): CatalogTool[] {
  const group = {
    id: 'ai-foundation/posts',
    displayName: '文章工具',
    description: '查询与读取文章内容',
  }
  return [
    {
      ...tools[0],
      id: 'ai-foundation/list_posts',
      displayName: '文章列表',
      category: 'content',
      group,
    },
    {
      ...tools[1],
      id: 'ai-foundation/get_post',
      displayName: '文章读取',
      category: 'content',
      group,
    },
    {
      ...tools[0],
      id: 'standalone-plugin/clock',
      pluginName: 'standalone-plugin',
      displayName: '独立时间工具',
    },
  ]
}
