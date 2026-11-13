import { AIUIError, AIUIProtocolError } from './errors'
import type { FetchFunction } from './types'

/** A shortcut that adds explicit tool IDs to a selection. */
export interface ToolPresetDescriptor {
  id: string
  displayName?: string
  description?: string
  toolIds: string[]
}

/** A source-owned business group. It has no executor or independent enabled state. */
export interface ToolGroupDescriptor {
  id: string
  displayName?: string
  description?: string
  presets: ToolPresetDescriptor[]
}

/** A discovered server-side tool. Persist its id, rather than its model-facing name. */
export interface ToolDescriptor {
  id: string
  pluginName: string
  localName: string
  name: string
  displayName?: string
  description?: string
  displayDescription?: string
  category?: string
  group?: ToolGroupDescriptor
  tags?: string[]
  configurationSchema?: Record<string, unknown>[]
  enabled: boolean
  configured: boolean
  status: 'ENABLED' | 'DISABLED' | 'UNCONFIGURED'
  inputSchema?: Record<string, unknown>
  outputSchema?: Record<string, unknown>
}

export const AI_TOOL_SELECTOR_FORMKIT_TYPE = 'aiToolSelector'

/** Props accepted by the globally registered AI Foundation FormKit input. */
export interface AiToolSelectorOptions {
  pluginName?: string
  category?: string
  placeholder?: string
  searchPlaceholder?: string
  fullWidth?: boolean
  disabled?: boolean
}

export interface ListToolsOptions {
  api?: string
  headers?: HeadersInit
  credentials?: RequestCredentials
  fetch?: FetchFunction
  abortSignal?: AbortSignal
}

/** Lists tools currently registered by enabled plugins through the Console API. */
export async function listTools(options: ListToolsOptions = {}): Promise<ToolDescriptor[]> {
  const fetchImpl = options.fetch ?? globalThis.fetch
  if (!fetchImpl) {
    throw new AIUIError('No fetch implementation is available.')
  }
  const response = await fetchImpl(
    options.api ?? '/apis/console.api.aifoundation.halo.run/v1alpha1/tools',
    {
      method: 'GET',
      headers: options.headers,
      credentials: options.credentials,
      signal: options.abortSignal,
    },
  )
  if (!response.ok) {
    throw new AIUIError((await response.text()) || 'Failed to list AI Foundation tools.', {
      status: response.status,
      response,
    })
  }
  const data: unknown = await response.json()
  if (!Array.isArray(data) || !data.every(isToolDescriptor)) {
    throw new AIUIProtocolError('The tool catalog response is invalid.')
  }
  return data
}

function isToolDescriptor(value: unknown): value is ToolDescriptor {
  if (!value || typeof value !== 'object') {
    return false
  }
  const tool = value as Record<string, unknown>
  return (
    ['id', 'pluginName', 'localName', 'name'].every(
      (field) => typeof tool[field] === 'string' && !!tool[field],
    ) &&
    ['enabled', 'configured'].every((field) => typeof tool[field] === 'boolean') &&
    (tool.status === 'ENABLED' || tool.status === 'DISABLED' || tool.status === 'UNCONFIGURED') &&
    (tool.group === undefined || isToolGroupDescriptor(tool.group)) &&
    (tool.tags === undefined ||
      (Array.isArray(tool.tags) && tool.tags.every((tag) => typeof tag === 'string'))) &&
    (tool.configurationSchema === undefined ||
      (Array.isArray(tool.configurationSchema) &&
        tool.configurationSchema.every(
          (field) => !!field && typeof field === 'object' && !Array.isArray(field),
        )))
  )
}

function isOptionalText(value: unknown) {
  return value === undefined || typeof value === 'string'
}

function isToolGroupDescriptor(value: unknown): value is ToolGroupDescriptor {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const group = value as Record<string, unknown>
  return (
    typeof group.id === 'string' &&
    !!group.id &&
    isOptionalText(group.displayName) &&
    isOptionalText(group.description) &&
    Array.isArray(group.presets) &&
    group.presets.every((value) => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) return false
      const preset = value as Record<string, unknown>
      return (
        typeof preset.id === 'string' &&
        !!preset.id &&
        isOptionalText(preset.displayName) &&
        isOptionalText(preset.description) &&
        Array.isArray(preset.toolIds) &&
        preset.toolIds.every((id) => typeof id === 'string' && !!id)
      )
    })
  )
}
