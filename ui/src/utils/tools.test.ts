import { describe, expect, it } from 'vitest'
import {
  filterTools,
  groupTools,
  toolCategoryLabel,
  toolSelectable,
  toolStatusLabel,
  toolStatusState,
  type CatalogTool,
} from './tools'

const tools: CatalogTool[] = [
  {
    id: 'ai-foundation/search_site',
    pluginName: 'ai-foundation',
    localName: 'search_site',
    name: 'search_site',
    displayName: '站内搜索',
    category: '内容',
    inputSchema: {},
  },
  {
    id: 'other-plugin/search',
    pluginName: 'other-plugin',
    localName: 'search',
    name: 'other_search',
    displayDescription: 'Search external pages',
    category: '网络',
    inputSchema: {},
  },
]

describe('tool catalog filters', () => {
  it('combines source, category, and case-insensitive keyword filters', () => {
    expect(
      filterTools(tools, { keyword: 'EXTERNAL', pluginName: 'other-plugin', category: '网络' }),
    ).toEqual([tools[1]])
    expect(filterTools(tools, { keyword: '站内', pluginName: 'other-plugin' })).toEqual([])
    expect(filterTools(tools, { keyword: 'ai-foundation/search_site' })).toEqual([tools[0]])
  })

  it('groups different plugins by category while preserving stable identities', () => {
    const result = groupTools([...tools, { ...tools[1], id: 'third/search', category: '内容' }])
    expect(result.map((group) => [group.category, group.tools.map((tool) => tool.id)])).toEqual([
      ['内容', ['ai-foundation/search_site', 'third/search']],
      ['网络', ['other-plugin/search']],
    ])
  })

  it('searches tags and user-facing category labels without changing filter identifiers', () => {
    const tagged = [{ ...tools[0], category: 'site', tags: ['PUBLIC-CONTENT'] }]
    expect(filterTools(tagged, { keyword: 'public-content' })).toEqual(tagged)
    expect(filterTools(tagged, { keyword: '站内', category: 'site' })).toEqual(tagged)
    expect(toolCategoryLabel('web')).toBe('网页')
    expect(toolCategoryLabel('custom')).toBe('custom')
  })
})

describe('tool availability', () => {
  it('keeps legacy descriptors selectable when availability fields are absent', () => {
    expect(toolSelectable(tools[0]!)).toBe(true)
    expect(toolStatusLabel(tools[0]!)).toBe('已启用')
  })

  it.each([
    [{ enabled: false, status: 'ENABLED' }, '已禁用', 'default'],
    [{ configured: false, status: 'ENABLED' }, '未配置', 'warning'],
    [{ status: 'DISABLED' }, '已禁用', 'default'],
    [{ status: 'UNCONFIGURED' }, '未配置', 'warning'],
  ])('rejects unavailable tool selection using explicit fields (%j)', (state, label, dotState) => {
    const tool = { ...tools[0]!, ...state }
    expect(toolSelectable(tool)).toBe(false)
    expect(toolStatusLabel(tool)).toBe(label)
    expect(toolStatusState(tool)).toBe(dotState)
  })
})
