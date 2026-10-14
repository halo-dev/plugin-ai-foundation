import type { ToolDescriptor } from '@/api/generated'
import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ToolDetailsContent from './ToolDetailsContent.vue'

vi.mock('@halo-dev/components', () => ({
  VStatusDot: { props: ['text'], template: '<span>{{ text }}</span>' },
  VTag: { template: '<span><slot /></span>' },
}))

describe('ToolDetailsContent', () => {
  it('renders tool identity, metadata, description and complete parameter schemas', () => {
    const longDescription = 'schema-description-'.repeat(200)
    const inputSchema = {
      type: 'object',
      properties: { query: { type: 'string', description: longDescription } },
    }
    const outputSchema = { type: 'object', properties: { answer: { type: 'string' } } }
    const tool: ToolDescriptor = {
      id: 'example/search',
      pluginName: 'example',
      name: 'example_search',
      category: 'web',
      tags: ['搜索', '只读'],
      configured: false,
      status: 'UNCONFIGURED',
      displayDescription: '查询公开网页内容',
      inputSchema: inputSchema as unknown as ToolDescriptor['inputSchema'],
      outputSchema: outputSchema as unknown as ToolDescriptor['outputSchema'],
    }
    const wrapper = mount(ToolDetailsContent, { props: { tool } })
    for (const value of [
      'example/search',
      'example',
      'example_search',
      '网页',
      '未配置',
      '搜索',
      '只读',
      '查询公开网页内容',
      '输入参数',
      '输出结构',
    ]) {
      expect(wrapper.text()).toContain(value)
    }
    const schemas = wrapper.findAll('pre')
    expect(schemas).toHaveLength(2)
    expect(JSON.parse(schemas[0]!.text())).toEqual(inputSchema)
    expect(JSON.parse(schemas[1]!.text())).toEqual(outputSchema)
    wrapper.unmount()
  })

  it('renders an empty input schema and fallback when no output schema is declared', () => {
    const wrapper = mount(ToolDetailsContent, {
      props: { tool: { id: 'example/tool', description: '普通工具描述' } },
    })
    expect(wrapper.get('pre').text()).toBe('{}')
    expect(wrapper.text()).toContain('工具未声明输出结构')
    expect(wrapper.text()).toContain('普通工具描述')
    wrapper.unmount()
  })
})
