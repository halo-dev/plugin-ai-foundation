import { AIUIError, AIUIProtocolError } from './errors'
import type { FetchFunction } from './types'

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
  tags?: string[]
  configurationSchema?: Record<string, unknown>[]
  enabled?: boolean
  configured?: boolean
  status?: string
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
    ['enabled', 'configured'].every(
      (field) => tool[field] === undefined || typeof tool[field] === 'boolean',
    ) &&
    (tool.status === undefined || typeof tool.status === 'string') &&
    (tool.tags === undefined ||
      (Array.isArray(tool.tags) && tool.tags.every((tag) => typeof tag === 'string'))) &&
    (tool.configurationSchema === undefined ||
      (Array.isArray(tool.configurationSchema) &&
        tool.configurationSchema.every(
          (field) => !!field && typeof field === 'object' && !Array.isArray(field),
        )))
  )
}
