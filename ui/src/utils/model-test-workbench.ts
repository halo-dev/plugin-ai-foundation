import { AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel } from '@/api/generated'

export type ChatRole = 'user' | 'assistant'

export interface WorkbenchMessage {
  id: string
  role: ChatRole
  content: string
  modelName?: string
  modelDisplayName?: string
  state?: 'streaming' | 'done' | 'error' | 'stopped'
}

export interface ChatParameters {
  systemPrompt?: string
  temperature?: number
  topP?: number
  maxOutputTokens?: number
  providerOptions?: Record<string, Record<string, unknown>>
}

export interface SseParseResult<T> {
  buffer: string
  chunks: T[]
}

export interface ModelMessagePart {
  type: 'text'
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
  providerOptions?: Record<string, Record<string, unknown>>
}

export interface TextStreamPart {
  type?: 'start' | 'text-start' | 'text-delta' | 'text-end' | 'finish' | 'error'
  messageId?: string
  id?: string
  delta?: string
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
    requestMessages.push({
      role: message.role === 'assistant' ? 'ASSISTANT' : 'USER',
      content: [{ type: 'text', text: content }],
    })
  }

  return {
    system: systemPrompt || undefined,
    messages: requestMessages,
    temperature: parameters.temperature,
    topP: parameters.topP,
    maxOutputTokens: parameters.maxOutputTokens,
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
