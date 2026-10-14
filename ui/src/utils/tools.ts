import type { ToolDescriptor } from '@/api/generated'
import type { StatusDotState } from '@halo-dev/components'

export type CatalogTool = ToolDescriptor

export interface ToolGroup {
  category: string
  tools: CatalogTool[]
}

export function toolStatus(tool: CatalogTool): 'ENABLED' | 'DISABLED' | 'UNCONFIGURED' {
  if (tool.enabled === false || tool.status === 'DISABLED') return 'DISABLED'
  if (tool.configured === false || tool.status === 'UNCONFIGURED') return 'UNCONFIGURED'
  if (tool.status && tool.status !== 'ENABLED') return 'DISABLED'
  return 'ENABLED'
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
  return (
    ({ web: '网页', site: '站内' } as Record<string, string>)[category || ''] || category || '其他'
  )
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
      (!category || (tool.category || '其他') === category) &&
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
          ...(tool.tags || []),
        ].some((value) => value?.toLocaleLowerCase().includes(search)))
    )
  })
}

export function groupTools(tools: CatalogTool[]): ToolGroup[] {
  const groups = new Map<string, CatalogTool[]>()
  for (const tool of tools) {
    const category = tool.category || '其他'
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
    categories: Array.from(new Set(tools.map((tool) => tool.category || '其他'))),
  }
}
