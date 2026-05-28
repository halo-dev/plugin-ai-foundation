import { AiModelSpecModelTypeEnum } from '@/api/generated'
import type { AiModel, OutputSpec } from '@/api/generated'

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
  warnings?: WorkbenchWarning[]
}

export interface WorkbenchWarning {
  code?: string
  message?: string
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
  seed?: number
  maxRetries?: number
  reasoning?: ReasoningOptions
  providerOptions?: Record<string, Record<string, unknown>>
  output?: OutputSpec
}

export type OutputMode = 'TEXT' | 'OBJECT' | 'ARRAY' | 'CHOICE' | 'JSON'
export type ReasoningMode = 'DEFAULT' | 'ENABLED' | 'DISABLED' | 'EFFORT'
export type ReasoningEffort = 'LOW' | 'MEDIUM' | 'HIGH'

export interface ReasoningOptions {
  mode?: 'DEFAULT' | 'ENABLED' | 'DISABLED'
  effort?: ReasoningEffort
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
  seed?: number
  maxRetries?: number
  reasoning?: ReasoningOptions
  providerOptions?: Record<string, Record<string, unknown>>
  output?: OutputSpec
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
    | 'tool-input-start'
    | 'tool-input-delta'
    | 'tool-call'
    | 'tool-result'
    | 'tool-error'
    | 'source'
    | 'file'
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
  url?: string
  title?: string
  mediaType?: string
  data?: unknown
  warnings?: WorkbenchWarning[]
}

export function isEnabledChatModel(model: AiModel) {
  return model.spec.enabled !== false && model.spec.modelType === AiModelSpecModelTypeEnum.Language
}

export function isEnabledTestableModel(model: AiModel) {
  return (
    model.spec.enabled !== false &&
    (model.spec.modelType === AiModelSpecModelTypeEnum.Language ||
      model.spec.modelType === AiModelSpecModelTypeEnum.Embedding)
  )
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

export function parseJsonSchema(input: string, label = 'JSON Schema'): {
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
      return { error: `${label} 必须是 JSON 对象` }
    }
    return { value: parsed as Record<string, unknown> }
  } catch {
    return { error: `${label} 不是有效的 JSON` }
  }
}

export function buildOutputSpec(options: {
  mode: OutputMode
  schemaText?: string
  choicesText?: string
}): { value?: OutputSpec; error?: string } {
  if (options.mode === 'TEXT') {
    return {}
  }
  if (options.mode === 'JSON') {
    return { value: { type: 'JSON' } }
  }
  if (options.mode === 'CHOICE') {
    const choices = (options.choicesText || '')
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    if (!choices.length) {
      return { error: '请至少填写一个枚举选项' }
    }
    return { value: { type: 'CHOICE', choices } }
  }

  const parsedSchema = parseJsonSchema(
    options.schemaText || '',
    options.mode === 'ARRAY' ? '元素 JSON Schema' : 'JSON Schema',
  )
  if (parsedSchema.error) {
    return { error: parsedSchema.error }
  }
  if (!parsedSchema.value) {
    return { error: options.mode === 'ARRAY' ? '请填写元素 JSON Schema' : '请填写 JSON Schema' }
  }
  return options.mode === 'ARRAY'
    ? { value: { type: 'ARRAY', elementSchema: parsedSchema.value } as OutputSpec }
    : { value: { type: 'OBJECT', schema: parsedSchema.value } as OutputSpec }
}

export function buildReasoningOptions(options: {
  mode: ReasoningMode
  effort?: ReasoningEffort
}): ReasoningOptions | undefined {
  switch (options.mode) {
    case 'ENABLED':
      return { mode: 'ENABLED' }
    case 'DISABLED':
      return { mode: 'DISABLED' }
    case 'EFFORT':
      return { mode: 'ENABLED', effort: options.effort || 'MEDIUM' }
    default:
      return undefined
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
    seed: parameters.seed,
    maxRetries: parameters.maxRetries,
    reasoning: parameters.reasoning,
    providerOptions: parameters.providerOptions,
    output: parameters.output,
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
