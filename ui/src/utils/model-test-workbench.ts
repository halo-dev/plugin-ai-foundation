import type { AiModel, Message, TestChatRequest } from '@/api/generated'

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
  maxTokens?: number
  providerOptions?: Record<string, unknown>
}

export interface SseParseResult<T> {
  buffer: string
  chunks: T[]
}

export function isEnabledChatModel(model: AiModel) {
  return model.spec.enabled !== false && model.spec.capabilities?.includes('chat')
}

export function filterEnabledChatModels(models: AiModel[] | undefined) {
  return (models || []).filter(isEnabledChatModel)
}

export function parseProviderOptionsJson(input: string): {
  value?: Record<string, unknown>
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
    return { value: parsed }
  } catch {
    return { error: 'Provider Options 不是有效的 JSON' }
  }
}

export function buildTestChatRequest(
  messages: WorkbenchMessage[],
  parameters: ChatParameters,
): TestChatRequest {
  const requestMessages: Message[] = []

  const systemPrompt = parameters.systemPrompt?.trim()
  if (systemPrompt) {
    requestMessages.push({ role: 'system', content: systemPrompt })
  }

  for (const message of messages) {
    const content = message.content.trim()
    if (!content || (message.role === 'assistant' && message.state === 'error')) {
      continue
    }
    requestMessages.push({ role: message.role, content })
  }

  return {
    messages: requestMessages,
    temperature: parameters.temperature,
    topP: parameters.topP,
    maxTokens: parameters.maxTokens,
    providerOptions: parameters.providerOptions as TestChatRequest['providerOptions'],
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
  return data ? [JSON.parse(data) as T] : []
}
