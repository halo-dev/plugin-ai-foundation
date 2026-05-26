import { AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel } from '@/api/generated'

export type ChatRole = 'user' | 'assistant'

export interface WorkbenchMessage {
  id: string
  role: ChatRole
  content: string
  reasoningContent?: string
  toolEvents?: WorkbenchToolEvent[]
  modelName?: string
  modelDisplayName?: string
  state?: 'streaming' | 'done' | 'error' | 'stopped'
}

export interface WorkbenchToolEvent {
  id: string
  type: 'tool-call' | 'tool-result' | 'tool-error'
  toolCallId?: string
  toolName?: string
  summary: string
}

export interface ChatParameters {
  systemPrompt?: string
  temperature?: number
  topP?: number
  maxOutputTokens?: number
  maxSteps?: number
  providerOptions?: Record<string, Record<string, unknown>>
}

export interface SseParseResult<T> {
  buffer: string
  chunks: T[]
}

export interface ModelMessagePart {
  type: 'text' | 'reasoning'
  text: string
}

export interface ModelMessage {
  role: 'USER' | 'ASSISTANT'
  content: ModelMessagePart[]
}

export interface GenerateTextRequest {
  system?: string
  messages?: ModelMessage[]
  temperature?: number
  topP?: number
  maxOutputTokens?: number
  maxSteps?: number
  providerOptions?: Record<string, Record<string, unknown>>
}

export interface TextStreamPart {
  type?:
    | 'start'
    | 'start-step'
    | 'text-start'
    | 'text-delta'
    | 'text-end'
    | 'reasoning-start'
    | 'reasoning-delta'
    | 'reasoning-end'
    | 'tool-call'
    | 'tool-result'
    | 'tool-error'
    | 'finish-step'
    | 'finish'
    | 'raw'
    | 'abort'
    | 'error'
    | (string & {})
  messageId?: string
  id?: string
  stepIndex?: number
  delta?: string
  providerMetadata?: Record<string, unknown>
  toolCallId?: string
  toolName?: string
  input?: Record<string, unknown>
  result?: unknown
  errorText?: string
}

export function isEnabledChatModel(model: AiModel) {
  return model.spec.enabled !== false && model.spec.modelType === AiModelSpecModelTypeEnum.Language
}

export function filterEnabledChatModels(models: AiModel[] | undefined) {
  return (models || []).filter(isEnabledChatModel)
}

export function parseProviderOptionsJson(input: string): {
  value?: Record<string, Record<string, unknown>>
  error?: string
} {
  const content = input.trim()
  if (!content) {
    return {}
  }

  try {
    const parsed = JSON.parse(content)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return { error: 'Provider Options 必须是 JSON 对象' }
    }
    for (const value of Object.values(parsed as Record<string, unknown>)) {
      if (!value || Array.isArray(value) || typeof value !== 'object') {
        return { error: 'Provider Options 必须按服务商命名空间分组' }
      }
    }
    return { value: parsed as Record<string, Record<string, unknown>> }
  } catch {
    return { error: 'Provider Options 不是有效的 JSON' }
  }
}

export function buildTestChatRequest(
  messages: WorkbenchMessage[],
  parameters: ChatParameters,
): GenerateTextRequest {
  const requestMessages: ModelMessage[] = []

  const systemPrompt = parameters.systemPrompt?.trim()

  for (const message of messages) {
    const content = message.content.trim()
    if (!content || (message.role === 'assistant' && message.state === 'error')) {
      continue
    }
    const parts: ModelMessagePart[] = []
    const reasoningContent = message.reasoningContent?.trim()
    if (message.role === 'assistant' && reasoningContent) {
      parts.push({ type: 'reasoning', text: reasoningContent })
    }
    parts.push({ type: 'text', text: content })
    requestMessages.push({
      role: message.role === 'assistant' ? 'ASSISTANT' : 'USER',
      content: parts,
    })
  }

  return {
    system: systemPrompt || undefined,
    messages: requestMessages,
    temperature: parameters.temperature,
    topP: parameters.topP,
    maxOutputTokens: parameters.maxOutputTokens,
    maxSteps: parameters.maxSteps,
    providerOptions: parameters.providerOptions,
  }
}

export function parseSseJsonLines<T>(buffer: string, text: string): SseParseResult<T> {
  const lines = (buffer + text).split('\n')
  const nextBuffer = lines.pop() || ''
  const chunks: T[] = []

  for (const line of lines) {
    if (!line.startsWith('data:')) {
      continue
    }
    const data = line.slice(5).trim()
    if (!data) {
      continue
    }
    if (data === '[DONE]') {
      continue
    }
    chunks.push(JSON.parse(data) as T)
  }

  return {
    buffer: nextBuffer,
    chunks,
  }
}

export function flushSseJsonBuffer<T>(buffer: string) {
  const line = buffer.trim()
  if (!line.startsWith('data:')) {
    return []
  }
  const data = line.slice(5).trim()
  return data && data !== '[DONE]' ? [JSON.parse(data) as T] : []
}

export function isRenderableTextDelta(
  part: TextStreamPart,
): part is TextStreamPart & { type: 'text-delta'; delta: string } {
  return part.type === 'text-delta' && !!part.delta
}

export function isRenderableReasoningDelta(
  part: TextStreamPart,
): part is TextStreamPart & { type: 'reasoning-delta'; delta: string } {
  return part.type === 'reasoning-delta' && !!part.delta
}

export function isTerminalTextStreamPart(part: TextStreamPart) {
  return part.type === 'finish' || part.type === 'error' || part.type === 'abort'
}

export function toToolEvent(part: TextStreamPart): WorkbenchToolEvent | undefined {
  if (!isToolStreamPartType(part.type)) {
    return undefined
  }

  return {
    id: `${part.type}-${part.toolCallId || crypto.randomUUID()}`,
    type: part.type,
    toolCallId: part.toolCallId,
    toolName: part.toolName,
    summary: toolEventSummary(part),
  }
}

function isToolStreamPartType(type: TextStreamPart['type']): type is WorkbenchToolEvent['type'] {
  return type === 'tool-call' || type === 'tool-result' || type === 'tool-error'
}

function toolEventSummary(part: TextStreamPart) {
  switch (part.type) {
    case 'tool-call':
      return stringifyCompact(part.input)
    case 'tool-result':
      return stringifyCompact(part.result)
    case 'tool-error':
      return part.errorText || '工具执行失败'
    default:
      return ''
  }
}

function stringifyCompact(value: unknown) {
  if (value === undefined || value === null) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
