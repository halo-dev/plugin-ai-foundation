import type { ToolDescriptor } from '@/api/generated'
import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AiToolSelectOption from './AiToolSelectOption.vue'

vi.mock('@halo-dev/components', () => ({
  VButton: {
    name: 'VButton',
    props: ['disabled', 'type'],
    template: '<button type="button" :disabled="disabled"><slot /></button>',
  },
  VStatusDot: { props: ['state', 'text'], template: '<span>{{ text }}</span>' },
  VTag: { template: '<span><slot /></span>' },
}))

const tool: ToolDescriptor = {
  id: 'ai-foundation/search_site',
  pluginName: 'ai-foundation',
  localName: 'search_site',
  name: 'search_site',
  displayName: '站内搜索',
  displayDescription: '搜索公开内容',
  category: 'site',
  tags: ['站内', '只读', '只读'],
  enabled: true,
  configured: true,
  status: 'ENABLED',
  inputSchema: {
    type: 'object',
    properties: { query: { type: 'string' } },
  } as unknown as ToolDescriptor['inputSchema'],
  outputSchema: {
    type: 'object',
    properties: { results: { type: 'array' } },
  } as unknown as ToolDescriptor['outputSchema'],
}

describe('AiToolSelectOption', () => {
  it('uses a label-wrapped native checkbox with the stable tool ID and deduplicated metadata', async () => {
    const wrapper = mount(AiToolSelectOption, { props: { tool, selected: false } })
    const checkbox = wrapper.get('label input[type="checkbox"]')
    expect(checkbox.attributes('value')).toBe(tool.id)
    expect(checkbox.attributes('aria-label')).toBe('选择 站内搜索')
    expect(checkbox.attributes('tabindex')).toBeUndefined()
    expect(wrapper.text()).toContain('站内 · 只读')
    expect(wrapper.text()).not.toContain('已启用')
    expect(wrapper.text()).not.toContain(tool.id!)
    expect(wrapper.get('[title="搜索公开内容"]').text()).toBe('搜索公开内容')
    await checkbox.setValue(true)
    expect(wrapper.emitted('toggle')).toEqual([[tool.id]])
    await wrapper.setProps({ selected: true })
    expect((checkbox.element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
  })

  it('expands and collapses unavailable tool details within the row without selecting it', async () => {
    const unavailable = { ...tool, configured: false, status: 'UNCONFIGURED' }
    const wrapper = mount(AiToolSelectOption, {
      attachTo: document.body,
      props: { tool: unavailable, selected: false },
    })
    await flushPromises()
    expect(wrapper.get('label').find('button').exists()).toBe(false)
    expect(wrapper.text()).toContain('未配置')
    expect(wrapper.get('input').attributes('disabled')).toBeDefined()
    const trigger = wrapper.get('button[aria-label="查看 站内搜索 详情"]')
    expect(wrapper.getComponent({ name: 'VButton' }).props('type')).toBe('default')
    expect(trigger.attributes('type')).toBe('button')
    expect(trigger.attributes('aria-expanded')).toBe('false')
    const contentId = trigger.attributes('aria-controls')
    expect(contentId).toBeTruthy()
    const content = wrapper.get('[role="region"][aria-label="站内搜索 详情"]')
    expect(content.element).toBe(document.getElementById(contentId!))
    expect(content.attributes('aria-hidden')).toBe('true')
    await trigger.trigger('click')
    await flushPromises()
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(trigger.attributes('aria-label')).toBe('收起 站内搜索 详情')
    expect(trigger.text()).toBe('收起')
    expect(content.attributes('aria-hidden')).toBeUndefined()
    expect(content.text()).toContain(tool.id!)
    expect(content.text()).toContain('输入参数')
    expect(content.text()).toContain('输出结构')
    expect(content.findAll('pre').map((block) => block.text())).toEqual([
      JSON.stringify(tool.inputSchema, null, 2),
      JSON.stringify(tool.outputSchema, null, 2),
    ])
    expect(wrapper.get('[data-tool-id]').element.contains(content.element)).toBe(true)
    expect(wrapper.emitted('toggle')).toBeUndefined()
    await trigger.trigger('click')
    await flushPromises()
    expect(trigger.attributes('aria-expanded')).toBe('false')
    expect(trigger.attributes('aria-label')).toBe('查看 站内搜索 详情')
    expect(trigger.text()).toBe('详情')
    expect(content.attributes('aria-hidden')).toBe('true')
    expect(content.find('dl').exists()).toBe(false)
    expect(wrapper.emitted('toggle')).toBeUndefined()
    wrapper.unmount()
  })

  it('preserves expanded details when the controlled checkbox value changes', async () => {
    const wrapper = mount(AiToolSelectOption, { props: { tool, selected: false } })
    const trigger = wrapper.get('button')
    await trigger.trigger('click')
    await wrapper.get('input').setValue(true)
    expect(wrapper.emitted('toggle')).toEqual([[tool.id]])
    await wrapper.setProps({ selected: true })
    expect(trigger.attributes('aria-expanded')).toBe('true')
    expect(wrapper.get('[role="region"]').text()).toContain(tool.id!)
    expect((wrapper.get('input').element as HTMLInputElement).checked).toBe(true)
    wrapper.unmount()
  })

  it('allows independent row expansion and starts collapsed after remounting', async () => {
    const otherTool = { ...tool, id: 'other/tool', displayName: '其他工具' }
    const wrapper = mount({
      components: { AiToolSelectOption },
      setup: () => ({ tool, otherTool }),
      template:
        '<div><AiToolSelectOption :tool="tool" :selected="false" /><AiToolSelectOption :tool="otherTool" :selected="false" /></div>',
    })
    const triggers = wrapper.findAll('button')
    await triggers[0]!.trigger('click')
    await triggers[1]!.trigger('click')
    expect(triggers.map((trigger) => trigger.attributes('aria-expanded'))).toEqual(['true', 'true'])
    expect(triggers[0]!.attributes('aria-controls')).not.toBe(
      triggers[1]!.attributes('aria-controls'),
    )
    await triggers[0]!.trigger('click')
    expect(triggers.map((trigger) => trigger.attributes('aria-expanded'))).toEqual([
      'false',
      'true',
    ])
    wrapper.unmount()
    const remounted = mount(AiToolSelectOption, { props: { tool, selected: false } })
    expect(remounted.get('button').attributes('aria-expanded')).toBe('false')
    expect(remounted.get('[role="region"]').attributes('aria-hidden')).toBe('true')
    remounted.unmount()
  })

  it.each([
    { tool: { ...tool, enabled: false, status: 'DISABLED' }, disabled: false },
    { tool: { ...tool, configured: false, status: 'UNCONFIGURED' }, disabled: false },
    { tool, disabled: true },
  ])('blocks synthetic changes for unavailable or disabled options (%j)', async (props) => {
    const wrapper = mount(AiToolSelectOption, { props: { ...props, selected: true } })
    const checkbox = wrapper.get('input')
    expect(checkbox.attributes('disabled')).toBeDefined()
    checkbox.element.dispatchEvent(new Event('change', { bubbles: true }))
    expect(wrapper.emitted('toggle')).toBeUndefined()
    if (props.disabled) {
      expect(wrapper.get('button').attributes('disabled')).toBeDefined()
      wrapper.get('button').element.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()
      expect(wrapper.get('button').attributes('aria-expanded')).toBe('false')
      expect(wrapper.get('[role="region"]').attributes('aria-hidden')).toBe('true')
    }
    await wrapper.setProps({ selected: false })
    expect((checkbox.element as HTMLInputElement).checked).toBe(false)
    wrapper.unmount()
  })
})
