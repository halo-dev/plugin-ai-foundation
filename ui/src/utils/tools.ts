import type { ToolDescriptor } from '@/api/generated'
import type { StatusDotState } from '@halo-dev/components'

export type CatalogTool = ToolDescriptor

export interface ToolGroup {
  category: string
  tools: CatalogTool[]
}

/** A business group, or one standalone tool contributed without a group. */
export interface ToolCollection {
  key: string
  group?: NonNullable<CatalogTool['group']>
  tools: CatalogTool[]
}

const categoryLabels: Record<string, string> = {
  content: '内容',
  web: '网络',
  media: '媒体',
  site: '站点',
  utility: '实用',
  other: '其他',
}

export function normalizeToolCategory(category?: string) {
  return category && Object.prototype.hasOwnProperty.call(categoryLabels, category)
    ? category
    : 'other'
}

export function toolStatus(tool: CatalogTool): 'ENABLED' | 'DISABLED' | 'UNCONFIGURED' {
  if (tool.enabled === false || tool.status === 'DISABLED') return 'DISABLED'
  if (tool.configured === false || tool.status === 'UNCONFIGURED') return 'UNCONFIGURED'
  return tool.enabled === true && tool.configured === true && tool.status === 'ENABLED'
    ? 'ENABLED'
    : 'DISABLED'
}

export function toolStatusLabel(tool: CatalogTool) {
  return { ENABLED: '已启用', DISABLED: '已禁用', UNCONFIGURED: '未配置' }[toolStatus(tool)]
}

export function toolStatusState(tool: CatalogTool): StatusDotState {
  return ({ ENABLED: 'success', DISABLED: 'default', UNCONFIGURED: 'warning' } as const)[
    toolStatus(tool)
  ]
}

export function toolSelectable(tool: CatalogTool) {
  return !!tool.id && toolStatus(tool) === 'ENABLED'
}

export function toolCategoryLabel(category?: string) {
  return categoryLabels[normalizeToolCategory(category)] || categoryLabels.other!
}

export function toolDisplayName(tool: ToolDescriptor) {
  return tool.displayName || tool.localName || tool.name || tool.id || '未知工具'
}

export function toolDisplayDescription(tool: ToolDescriptor) {
  return tool.displayDescription || tool.description || ''
}

export function filterTools(
  tools: CatalogTool[],
  { keyword = '', pluginName = '', category = '' } = {},
) {
  const search = keyword.trim().toLocaleLowerCase()
  return tools.filter((tool) => {
    return (
      (!pluginName || tool.pluginName === pluginName) &&
      (!category || normalizeToolCategory(tool.category) === category) &&
      (!search ||
        [
          tool.id,
          tool.name,
          tool.localName,
          tool.displayName,
          tool.description,
          tool.displayDescription,
          tool.pluginName,
          tool.category,
          toolCategoryLabel(tool.category),
          tool.group?.id,
          tool.group?.displayName,
          tool.group?.description,
          ...(tool.group?.presets || []).flatMap((preset) => [
            preset.displayName,
            preset.description,
          ]),
          ...(tool.tags || []),
        ].some((value) => value?.toLocaleLowerCase().includes(search)))
    )
  })
}

export function buildToolCollections(tools: CatalogTool[]): ToolCollection[] {
  const collections = new Map<string, ToolCollection>()
  for (const tool of tools) {
    const group = tool.group?.id ? tool.group : undefined
    // The source remains part of the identity even if a custom catalog reuses a group id.
    const key = JSON.stringify([tool.pluginName, group ? 'group' : 'tool', group?.id || tool.id])
    const collection = collections.get(key)
    if (collection) collection.tools.push(tool)
    else collections.set(key, { key, group, tools: [tool] })
  }
  return [...collections.values()]
}

export function groupTools(tools: CatalogTool[]): ToolGroup[] {
  const groups = new Map<string, CatalogTool[]>()
  for (const tool of tools) {
    const category = normalizeToolCategory(tool.category)
    const group = groups.get(category)
    if (group) group.push(tool)
    else groups.set(category, [tool])
  }
  return Array.from(groups, ([category, tools]) => ({ category, tools }))
}

export function toolFilterOptions(tools: ToolDescriptor[]) {
  return {
    pluginNames: Array.from(
      new Set(tools.map((tool) => tool.pluginName).filter((name): name is string => !!name)),
    ),
    categories: Array.from(new Set(tools.map((tool) => normalizeToolCategory(tool.category)))),
  }
}
