import { describe, expect, it } from 'vitest'
import {
  buildToolCollections,
  filterTools,
  groupTools,
  normalizeToolCategory,
  toolCategoryLabel,
  toolFilterOptions,
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
    category: 'content',
    enabled: true,
    configured: true,
    status: 'ENABLED',
    inputSchema: {},
  },
  {
    id: 'other-plugin/search',
    pluginName: 'other-plugin',
    localName: 'search',
    name: 'other_search',
    displayDescription: 'Search external pages',
    category: 'web',
    enabled: true,
    configured: true,
    status: 'ENABLED',
    inputSchema: {},
  },
]

describe('tool catalog filters', () => {
  it('combines source, category, and case-insensitive keyword filters', () => {
    expect(
      filterTools(tools, { keyword: 'EXTERNAL', pluginName: 'other-plugin', category: 'web' }),
    ).toEqual([tools[1]])
    expect(filterTools(tools, { keyword: '站内', pluginName: 'other-plugin' })).toEqual([])
    expect(filterTools(tools, { keyword: 'ai-foundation/search_site' })).toEqual([tools[0]])
  })

  it('groups different plugins by category while preserving stable identities', () => {
    const result = groupTools([...tools, { ...tools[1], id: 'third/search', category: 'content' }])
    expect(result.map((group) => [group.category, group.tools.map((tool) => tool.id)])).toEqual([
      ['content', ['ai-foundation/search_site', 'third/search']],
      ['web', ['other-plugin/search']],
    ])
  })

  it('searches tags and user-facing category labels without changing filter identifiers', () => {
    const tagged = [{ ...tools[0], category: 'site', tags: ['PUBLIC-CONTENT'] }]
    expect(filterTools(tagged, { keyword: 'public-content' })).toEqual(tagged)
    expect(filterTools(tagged, { keyword: '站点', category: 'site' })).toEqual(tagged)
    expect(toolCategoryLabel('web')).toBe('网络')
    expect(toolCategoryLabel('custom')).toBe('其他')
  })

  it.each(['content', 'web', 'media', 'site', 'utility', 'other'])(
    'preserves the standard category identifier %s',
    (category) => {
      expect(normalizeToolCategory(category)).toBe(category)
    },
  )

  it('groups missing and unknown category identifiers as other without changing their spelling', () => {
    const catalog = [
      ...tools,
      { ...tools[0], id: 'missing/category', category: undefined },
      { ...tools[1], id: 'unknown/category', category: 'custom' },
    ]
    expect(toolFilterOptions(catalog).categories).toEqual(['content', 'web', 'other'])
    expect(groupTools(catalog).map((group) => group.category)).toEqual(['content', 'web', 'other'])
    expect(filterTools(catalog, { category: 'other' })).toEqual(catalog.slice(2))
    for (const category of [undefined, '', 'custom', 'WEB', ' web ', 'toString']) {
      expect(normalizeToolCategory(category)).toBe('other')
    }
    for (const category of ['内容', '网络', 'Utilities', 'WEB', 'custom']) {
      expect(filterTools(catalog, { category })).toEqual([])
    }
  })

  it('matches category identifiers independently of tags', () => {
    const catalog = [tools[0]!, { ...tools[1], category: 'web', tags: ['content'] }]
    expect(filterTools(catalog, { category: 'content' })).toEqual([catalog[0]])
    expect(filterTools(catalog, { category: 'web' })).toEqual([catalog[1]])
  })

  it('finds group and preset descriptions while preserving leaf search results', () => {
    const grouped = tools.map((tool) => ({
      ...tool,
      group: {
        id: 'demo/posts',
        displayName: '文章管理',
        description: '管理草稿',
        presets: [{ id: 'demo/posts/read', displayName: '只读检索', toolIds: [tool.id!] }],
      },
    }))
    expect(filterTools(grouped, { keyword: '文章管理' })).toEqual(grouped)
    expect(filterTools(grouped, { keyword: '只读检索' })).toEqual(grouped)
    expect(filterTools(grouped, { keyword: 'EXTERNAL' })).toEqual([grouped[1]])
  })
})

describe('business tool collections', () => {
  it('groups within a source and leaves ungrouped tools standalone in discovery order', () => {
    const group = { id: 'demo/posts', displayName: '文章', presets: [] }
    const first = { ...tools[0], id: 'demo/list', pluginName: 'demo', group }
    const second = { ...first, id: 'demo/get' }
    const otherSource = { ...first, id: 'other/list', pluginName: 'other' }
    const result = buildToolCollections([first, tools[1]!, second, otherSource])
    expect(result.map((item) => item.tools.map((tool) => tool.id))).toEqual([
      ['demo/list', 'demo/get'],
      ['other-plugin/search'],
      ['other/list'],
    ])
    expect(result[0]?.group).toEqual(group)
    expect(result[1]?.group).toBeUndefined()
    expect(result[2]?.key).not.toBe(result[0]?.key)
  })
})

describe('tool availability', () => {
  it('selects only tools with complete enabled and configured status', () => {
    expect(toolSelectable(tools[0]!)).toBe(true)
    expect(toolStatusLabel(tools[0]!)).toBe('已启用')
  })

  it('rejects tools when any required availability field is absent', () => {
    for (const field of ['enabled', 'configured', 'status']) {
      const incomplete = { ...tools[0]! }
      Reflect.deleteProperty(incomplete, field)
      expect(toolSelectable(incomplete)).toBe(false)
      expect(toolStatusLabel(incomplete)).toBe('已禁用')
      expect(toolStatusState(incomplete)).toBe('default')
    }
    const incomplete = { ...tools[0]! }
    for (const field of ['enabled', 'configured', 'status']) {
      Reflect.deleteProperty(incomplete, field)
    }
    expect(toolSelectable(incomplete)).toBe(false)
    expect(toolStatusLabel(incomplete)).toBe('已禁用')
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
