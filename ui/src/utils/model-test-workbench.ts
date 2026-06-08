import type { AiModel, OutputSpec, TestUiMessageChatRequest } from '@/api/generated'
import { AiModelSpecModelTypeEnum } from '@/api/generated'

export type ChatRole = 'user' | 'assistant'
export type ChatStreamProtocol = 'text' | 'ui-message'

export interface WorkbenchMessage {
  id: string
  role: ChatRole
  content: string
  uiMessage?: UIMessage<Record<string, unknown>>
  transientData?: Record<string, unknown>
  leadingMessages?: ModelMessage[]
  followingMessages?: ModelMessage[]
  historyParts?: ModelMessagePart[]
  responseMessages?: ModelMessage[]
  reasoningContent?: string
  reasoningState?: 'streaming' | 'done'
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
  type: 'tool-call' | 'tool-result' | 'tool-error' | 'tool-approval-request'
  approvalId?: string
  toolCallId?: string
  toolName?: string
  stepIndex?: number
  input?: Record<string, unknown>
  result?: unknown
  errorText?: string
  approvalStatus?: 'pending' | 'approved' | 'denied'
  externalStatus?: 'pending' | 'completed' | 'failed'
  continuationSupported?: boolean
  summary: string
}

export interface ExamplePrompt {
  id: string
  icon: string
  title: string
  content: string
}

export const EXAMPLE_PROMPTS: ExamplePrompt[] = [
  {
    id: 'creative-writing',
    icon: 'ri-quill-pen-line',
    title: '创意写作',
    content: '请帮我写一个关于未来城市的短故事，大约300字。',
  },
  {
    id: 'code-review',
    icon: 'ri-code-box-line',
    title: '代码审查',
    content:
      '请审查以下代码并给出改进建议：\n\n```python\ndef fib(n):\n    if n <= 1:\n        return n\n    return fib(n-1) + fib(n-2)\n```',
  },
  {
    id: 'explain-concept',
    icon: 'ri-lightbulb-line',
    title: '概念解释',
    content: '请用简单易懂的语言解释什么是"神经网络"，并举一个生活中的类比。',
  },
  {
    id: 'json-output',
    icon: 'ri-table-line',
    title: '结构化输出',
    content:
      '请分析以下产品评论，并返回一个包含 sentiment（positive/negative/neutral）和 keywords 数组的 JSON 对象。\n\n评论：这个手机的电池续航真的很棒，但摄像头效果一般。',
  },
  {
    id: 'translation',
    icon: 'ri-translate-2',
    title: '翻译',
    content: '请将以下中文翻译成英文，保持原文的语气和风格：\n\n"春风又绿江南岸，明月何时照我还。"',
  },
  {
    id: 'tool-test',
    icon: 'ri-tools-line',
    title: '工具调用',
    content: '请调用 halo_test_info 工具并告诉我返回的内容。',
  },
  {
    id: 'tool-repair-test',
    icon: 'ri-tools-line',
    title: '工具修复',
    content:
      '请调用 halo_repair_test_info 工具。请把参数写成 {"message":"repair me"}，然后告诉我工具返回的内容。',
  },
]

export async function copyToClipboard(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
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
  headers?: Record<string, string>
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
  type:
    | 'text'
    | 'reasoning'
    | 'tool-call'
    | 'tool-result'
    | 'tool-error'
    | 'tool-approval-request'
    | 'tool-approval-response'
  text?: string
  approvalId?: string
  toolCallId?: string
  toolName?: string
  stepIndex?: number
  input?: Record<string, unknown>
  result?: unknown
  errorText?: string
  approved?: boolean
  reason?: string
}

export interface ModelMessage {
  role: 'USER' | 'ASSISTANT' | 'TOOL'
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
  headers?: Record<string, string>
  output?: OutputSpec
}

export interface ChatStreamOptions {
  testToolEnabled?: boolean
  testToolApprovalEnabled?: boolean
  externalTestToolEnabled?: boolean
  toolCallRepairEnabled?: boolean
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
    | 'tool-approval-request'
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
  approvalId?: string
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
  response?: {
    messages?: ModelMessage[]
  }
}

export interface UIMessage<M = Record<string, unknown>> {
  id: string
  role: 'USER' | 'ASSISTANT'
  parts: UIMessagePart[]
  metadata?: M
}

export interface UIMessagePart {
  type: string
  id?: string
  text?: string
  name?: string
  data?: unknown
  sourceId?: string
  fileId?: string
  url?: string
  title?: string
  mediaType?: string
  approvalId?: string
  toolCallId?: string
  toolName?: string
  stepIndex?: number
  input?: Record<string, unknown>
  result?: unknown
  errorText?: string
  approved?: boolean
  reason?: string
  providerMetadata?: Record<string, unknown>
  [key: string]: unknown
}

export interface UIMessageChunk {
  type?:
    | 'start'
    | 'text-start'
    | 'text-delta'
    | 'text-end'
    | 'reasoning-start'
    | 'reasoning-delta'
    | 'reasoning-end'
    | 'data'
    | 'message-metadata'
    | 'source-url'
    | 'file'
    | 'tool-input-start'
    | 'tool-input-delta'
    | 'tool-call'
    | 'tool-result'
    | 'tool-error'
    | 'tool-approval-request'
    | 'finish-step'
    | 'finish'
    | 'error'
    | 'abort'
    | (string & {})
  messageId?: string
  messageMetadata?: unknown
  id?: string
  delta?: string
  name?: string
  data?: unknown
  transientData?: boolean
  sourceId?: string
  fileId?: string
  url?: string
  title?: string
  mediaType?: string
  approvalId?: string
  toolCallId?: string
  toolName?: string
  stepIndex?: number
  input?: Record<string, unknown>
  result?: unknown
  errorText?: string
  providerMetadata?: Record<string, unknown>
  warnings?: WorkbenchWarning[]
}

export type UIMessageChatTrigger = 'submit-message' | 'regenerate-message'

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

export function parseJsonSchema(
  input: string,
  label = 'JSON Schema',
): {
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
  options?: {
    extraMessages?: ModelMessage[]
  },
): GenerateTextRequest {
  const requestMessages = buildRequestMessages(messages, options?.extraMessages)
  return {
    system: normalizedSystemPrompt(parameters),
    messages: requestMessages,
    temperature: parameters.temperature,
    topP: parameters.topP,
    maxOutputTokens: parameters.maxOutputTokens,
    seed: parameters.seed,
    maxRetries: parameters.maxRetries,
    reasoning: parameters.reasoning,
    providerOptions: parameters.providerOptions,
    headers: parameters.headers,
    output: parameters.output,
  }
}

export function buildTestUiMessageChatRequest(
  messages: WorkbenchMessage[],
  parameters: ChatParameters,
  options?: {
    trigger?: UIMessageChatTrigger
    messageId?: string
  },
): TestUiMessageChatRequest {
  return {
    id: crypto.randomUUID(),
    messages: messages
      .map(toUIMessage)
      .filter((message): message is UIMessage => !!message) as TestUiMessageChatRequest['messages'],
    trigger: options?.trigger || 'submit-message',
    messageId: options?.messageId,
    system: normalizedSystemPrompt(parameters),
    temperature: parameters.temperature,
    topP: parameters.topP,
    maxOutputTokens: parameters.maxOutputTokens,
    seed: parameters.seed,
    maxRetries: parameters.maxRetries,
    reasoning: parameters.reasoning,
    providerOptions: parameters.providerOptions as TestUiMessageChatRequest['providerOptions'],
    headers: parameters.headers,
    output: parameters.output,
  }
}

export function createUserUIMessage(id: string, text: string): UIMessage {
  return {
    id,
    role: 'USER',
    parts: [{ type: 'text', id: `${id}-text`, text }],
  }
}

export function createAssistantUIMessage(id: string): UIMessage {
  return {
    id,
    role: 'ASSISTANT',
    parts: [],
  }
}

function toUIMessage(message: WorkbenchMessage): UIMessage | undefined {
  if (message.uiMessage) {
    return message.uiMessage
  }
  const content = message.content.trim()
  if (!content || (message.role === 'assistant' && message.state === 'error')) {
    return undefined
  }
  return {
    id: message.id,
    role: message.role === 'assistant' ? 'ASSISTANT' : 'USER',
    parts: [{ type: 'text', id: `${message.id}-text`, text: content }],
  }
}

function buildRequestMessages(
  messages: WorkbenchMessage[],
  extraMessages?: ModelMessage[],
): ModelMessage[] {
  const requestMessages: ModelMessage[] = []
  for (const message of messages) {
    appendWorkbenchMessage(requestMessages, message)
  }
  if (extraMessages?.length) {
    requestMessages.push(...extraMessages)
  }
  return requestMessages
}

function appendWorkbenchMessage(requestMessages: ModelMessage[], message: WorkbenchMessage) {
  if (message.responseMessages?.length) {
    requestMessages.push(...message.responseMessages)
    appendFollowingMessages(requestMessages, message)
    return
  }
  appendLeadingMessages(requestMessages, message)
  const modelMessage = toModelMessage(message)
  if (modelMessage) {
    requestMessages.push(modelMessage)
  }
  appendFollowingMessages(requestMessages, message)
}

function toModelMessage(message: WorkbenchMessage): ModelMessage | undefined {
  const content = message.content.trim()
  const historyParts = message.role === 'assistant' ? message.historyParts || [] : []
  if ((!content && !historyParts.length) || (message.role === 'assistant' && message.state === 'error')) {
    return undefined
  }
  const parts = [...historyParts]
  if (content) {
    parts.push({ type: 'text', text: content })
  }
  return {
    role: message.role === 'assistant' ? 'ASSISTANT' : 'USER',
    content: parts,
  }
}

function appendLeadingMessages(requestMessages: ModelMessage[], message: WorkbenchMessage) {
  if (message.leadingMessages?.length) {
    requestMessages.push(...message.leadingMessages)
  }
}

function appendFollowingMessages(requestMessages: ModelMessage[], message: WorkbenchMessage) {
  if (message.followingMessages?.length) {
    requestMessages.push(...message.followingMessages)
  }
}

function normalizedSystemPrompt(parameters: ChatParameters) {
  return parameters.systemPrompt?.trim() || undefined
}

export function parseSseJsonLines<T>(buffer: string, text: string): SseParseResult<T> {
  const lines = (buffer + text).split('\n')
  const nextBuffer = lines.pop() || ''
  const chunks: T[] = []

  for (const line of lines) {
    appendSseJsonChunk(chunks, line)
  }

  return {
    buffer: nextBuffer,
    chunks,
  }
}

export function flushSseJsonBuffer<T>(buffer: string) {
  const chunks: T[] = []
  appendSseJsonChunk(chunks, buffer.trim())
  return chunks
}

function appendSseJsonChunk<T>(chunks: T[], line: string) {
  let data = line.trim()
  if (!data.startsWith('data:')) {
    return
  }
  while (data.startsWith('data:')) {
    data = data.slice(5).trim()
  }
  if (data && data !== '[DONE]') {
    chunks.push(JSON.parse(data) as T)
  }
}

export async function readTestChatStream(options: {
  modelName: string
  requestBody: GenerateTextRequest
  streamOptions: ChatStreamOptions
  signal: AbortSignal
  onChunks: (chunks: TextStreamPart[]) => void
}) {
  const response = await fetch(testChatStreamUrl(options.modelName, options.streamOptions), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(options.requestBody),
    signal: options.signal,
  })
  if (!response.ok) {
    throw new Error((await response.text()) || `HTTP ${response.status}`)
  }
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法读取响应流')
  }

  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    const parsed = parseSseJsonLines<TextStreamPart>(
      buffer,
      decoder.decode(value, { stream: true }),
    )
    buffer = parsed.buffer
    options.onChunks(parsed.chunks)
  }
  options.onChunks(flushSseJsonBuffer<TextStreamPart>(buffer))
}

export async function readTestUiMessageChatStream(options: {
  modelName: string
  requestBody: TestUiMessageChatRequest
  streamOptions: ChatStreamOptions
  signal: AbortSignal
  onChunks: (chunks: UIMessageChunk[]) => void
}) {
  const response = await fetch(testUiMessageChatStreamUrl(options.modelName, options.streamOptions), {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(options.requestBody),
    signal: options.signal,
  })
  if (!response.ok) {
    throw new Error((await response.text()) || `HTTP ${response.status}`)
  }
  const reader = response.body?.getReader()
  if (!reader) {
    throw new Error('无法读取响应流')
  }

  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) break

    const parsed = parseSseJsonLines<UIMessageChunk>(
      buffer,
      decoder.decode(value, { stream: true }),
    )
    buffer = parsed.buffer
    options.onChunks(parsed.chunks)
  }
  options.onChunks(flushSseJsonBuffer<UIMessageChunk>(buffer))
}

export function testChatStreamUrl(modelName: string, options: ChatStreamOptions) {
  const params = chatStreamQueryParams(options)
  const query = params.toString()
  return `/apis/console.api.aifoundation.halo.run/v1alpha1/models/${encodeURIComponent(modelName)}/test-chat/stream${query ? `?${query}` : ''}`
}

export function testUiMessageChatStreamUrl(modelName: string, options: ChatStreamOptions) {
  const params = chatStreamQueryParams(options)
  const query = params.toString()
  return `/apis/console.api.aifoundation.halo.run/v1alpha1/models/${encodeURIComponent(modelName)}/test-chat/ui-message/stream${query ? `?${query}` : ''}`
}

function chatStreamQueryParams(options: ChatStreamOptions) {
  const params = new URLSearchParams()
  if (options.testToolEnabled) {
    params.set('enableTestTool', 'true')
  }
  if (options.testToolApprovalEnabled) {
    params.set('enableTestToolApproval', 'true')
  }
  if (options.externalTestToolEnabled) {
    params.set('enableExternalTestTool', 'true')
  }
  if (options.toolCallRepairEnabled) {
    params.set('enableToolCallRepair', 'true')
  }
  return params
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
    id: `${part.type}-${part.approvalId || part.toolCallId || crypto.randomUUID()}`,
    type: part.type,
    approvalId: part.approvalId,
    toolCallId: part.toolCallId,
    toolName: part.toolName,
    stepIndex: part.stepIndex,
    input: part.input,
    result: part.result,
    errorText: part.errorText,
    approvalStatus: part.type === 'tool-approval-request' ? 'pending' : undefined,
    externalStatus: part.type === 'tool-call' ? 'pending' : undefined,
    summary: toolEventSummary(part),
  }
}

export function applyWorkbenchStreamPart(message: WorkbenchMessage, part: TextStreamPart) {
  const toolEvent = toToolEvent(part)
  if (toolEvent) {
    appendWorkbenchToolEvent(message, toolEvent)
    return
  }
  if (part.type === 'error') {
    appendWorkbenchError(message, part.errorText || '请求失败')
    return
  }
  if (part.type === 'reasoning-start') {
    message.reasoningState = 'streaming'
    return
  }
  if (isRenderableReasoningDelta(part)) {
    message.reasoningContent = `${message.reasoningContent || ''}${part.delta}`
    message.reasoningState = 'streaming'
    return
  }
  if (part.type === 'reasoning-end') {
    message.reasoningState = 'done'
    return
  }
  if (isRenderableTextDelta(part)) {
    message.content += part.delta
    return
  }
  if (part.type === 'finish-step') {
    if (part.response?.messages?.length) {
      message.responseMessages = part.response.messages
      message.leadingMessages = undefined
      message.historyParts = undefined
    }
    if (part.warnings?.length) {
      message.warnings = [...(message.warnings || []), ...part.warnings]
    }
    return
  }
  if (isTerminalTextStreamPart(part)) {
    finishWorkbenchMessage(message, 'done')
  }
}

export function applyWorkbenchUIMessageChunk(message: WorkbenchMessage, chunk: UIMessageChunk) {
  const uiMessage = ensureAssistantUIMessage(message)
  if (chunk.type === 'start') {
    if (chunk.messageId) {
      uiMessage.id = chunk.messageId
    }
    mergeUIMessageMetadata(uiMessage, chunk.messageMetadata)
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'message-metadata') {
    mergeUIMessageMetadata(uiMessage, chunk.messageMetadata)
    return
  }
  if (chunk.type === 'text-start') {
    upsertUIMessagePart(uiMessage, 'text', chunk.id || 'text', { text: '' })
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'text-delta') {
    const part = upsertUIMessagePart(uiMessage, 'text', chunk.id || 'text', {})
    part.text = `${part.text || ''}${chunk.delta || ''}`
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'reasoning-start') {
    upsertUIMessagePart(uiMessage, 'reasoning', chunk.id || 'reasoning', { text: '' })
    message.reasoningState = 'streaming'
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'reasoning-delta') {
    const part = upsertUIMessagePart(uiMessage, 'reasoning', chunk.id || 'reasoning', {
      providerMetadata: chunk.providerMetadata,
    })
    part.text = `${part.text || ''}${chunk.delta || ''}`
    part.providerMetadata = mergePlainObjects(part.providerMetadata, chunk.providerMetadata)
    message.reasoningState = 'streaming'
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'reasoning-end') {
    message.reasoningState = 'done'
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'data') {
    if (chunk.name) {
      if (chunk.transientData) {
        message.transientData = { ...(message.transientData || {}), [chunk.name]: chunk.data }
      } else {
        upsertUIMessagePart(uiMessage, 'data', chunk.name, {
          name: chunk.name,
          data: chunk.data,
        })
      }
    }
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'source-url' && chunk.sourceId) {
    upsertUIMessagePart(uiMessage, 'source-url', chunk.sourceId, {
      sourceId: chunk.sourceId,
      url: chunk.url,
      title: chunk.title,
      providerMetadata: chunk.providerMetadata,
    })
    projectUIMessage(message)
    return
  }
  if (chunk.type === 'file' && chunk.fileId) {
    upsertUIMessagePart(uiMessage, 'file', chunk.fileId, {
      fileId: chunk.fileId,
      url: chunk.url,
      title: chunk.title,
      mediaType: chunk.mediaType,
      data: chunk.data,
      providerMetadata: chunk.providerMetadata,
    })
    projectUIMessage(message)
    return
  }
  if (isUIMessageToolPartType(chunk.type)) {
    const key = chunk.type === 'tool-approval-request' ? chunk.approvalId : chunk.toolCallId
    if (key) {
      upsertUIMessagePart(uiMessage, chunk.type, key, {
        approvalId: chunk.approvalId,
        toolCallId: chunk.toolCallId,
        toolName: chunk.toolName,
        input: chunk.input,
        result: chunk.result,
        errorText: chunk.errorText,
        stepIndex: chunk.stepIndex,
        providerMetadata: chunk.providerMetadata,
      })
      projectUIMessage(message)
    }
    return
  }
  if (chunk.type === 'finish-step') {
    if (chunk.warnings?.length) {
      message.warnings = [...(message.warnings || []), ...chunk.warnings]
    }
    return
  }
  if (chunk.type === 'finish') {
    mergeUIMessageMetadata(uiMessage, chunk.messageMetadata)
    projectUIMessage(message)
    finishWorkbenchMessage(message, 'done')
    return
  }
  if (chunk.type === 'error') {
    appendWorkbenchError(message, chunk.errorText || '请求失败')
    return
  }
  if (chunk.type === 'abort') {
    finishWorkbenchMessage(message, 'stopped')
  }
}

function ensureAssistantUIMessage(message: WorkbenchMessage): UIMessage<Record<string, unknown>> {
  if (!message.uiMessage) {
    message.uiMessage = createAssistantUIMessage(message.id)
  }
  return message.uiMessage
}

function upsertUIMessagePart(
  message: UIMessage,
  type: string,
  id: string,
  patch: Partial<UIMessagePart>,
) {
  const key = uiMessagePartKey(type)
  const existing = message.parts.find((part) => part.type === type && part[key] === id)
  if (existing) {
    Object.assign(existing, patch)
    return existing
  }
  const part: UIMessagePart = { type, [key]: id, ...patch }
  message.parts.push(part)
  return part
}

function uiMessagePartKey(type: string) {
  if (type === 'data') {
    return 'name'
  }
  if (type === 'source-url') {
    return 'sourceId'
  }
  if (type === 'file') {
    return 'fileId'
  }
  if (type === 'tool-approval-request') {
    return 'approvalId'
  }
  if (type === 'tool-call' || type === 'tool-result' || type === 'tool-error') {
    return 'toolCallId'
  }
  return 'id'
}

function mergeUIMessageMetadata(message: UIMessage, metadata: unknown) {
  const merged = mergePlainObjects(message.metadata, metadata)
  if (merged) {
    message.metadata = merged
  }
}

function mergePlainObjects(
  current: unknown,
  update: unknown,
): Record<string, unknown> | undefined {
  if (!isPlainRecord(update)) {
    return isPlainRecord(current) ? current : undefined
  }
  return {
    ...(isPlainRecord(current) ? current : {}),
    ...update,
  }
}

function isPlainRecord(value: unknown): value is Record<string, unknown> {
  return !!value && !Array.isArray(value) && typeof value === 'object'
}

function projectUIMessage(message: WorkbenchMessage) {
  const uiMessage = message.uiMessage
  if (!uiMessage) {
    return
  }
  message.content = uiMessage.parts
    .filter((part) => part.type === 'text')
    .map((part) => part.text || '')
    .join('')
  message.reasoningContent =
    uiMessage.parts
      .filter((part) => part.type === 'reasoning')
      .map((part) => part.text || '')
      .join('') || undefined
  const toolEvents = uiMessagePartsToToolEvents(uiMessage.parts)
  message.toolEvents = toolEvents.length ? toolEvents : undefined
}

function uiMessagePartsToToolEvents(parts: UIMessagePart[]): WorkbenchToolEvent[] {
  const terminalToolParts = parts.filter(
    (part) => part.type === 'tool-result' || part.type === 'tool-error',
  )
  const approvalRequestParts = parts.filter((part) => part.type === 'tool-approval-request')
  const approvalResponseParts = parts.filter((part) => part.type === 'tool-approval-response')
  return parts
    .map((part) =>
      uiMessagePartToToolEvent(
        part,
        terminalToolParts,
        approvalRequestParts,
        approvalResponseParts,
      ),
    )
    .filter((event): event is WorkbenchToolEvent => !!event)
}

function uiMessagePartToToolEvent(
  part: UIMessagePart,
  terminalToolParts: UIMessagePart[],
  approvalRequestParts: UIMessagePart[],
  approvalResponseParts: UIMessagePart[],
): WorkbenchToolEvent | undefined {
  if (!isUIMessageToolPartType(part.type)) {
    return undefined
  }
  return {
    id: `${part.type}-${part.approvalId || part.toolCallId || crypto.randomUUID()}`,
    type: part.type,
    approvalId: part.approvalId,
    toolCallId: part.toolCallId,
    toolName: part.toolName,
    stepIndex: part.stepIndex,
    input: part.input,
    result: part.result,
    errorText: part.errorText,
    approvalStatus:
      part.type === 'tool-approval-request'
        ? toolApprovalStatus(part.approvalId, approvalResponseParts)
        : undefined,
    externalStatus:
      part.type === 'tool-call' && !hasApprovalRequest(part.toolCallId, approvalRequestParts)
        ? toolCallStatus(part.toolCallId, terminalToolParts)
        : undefined,
    summary: toolEventSummary(part as TextStreamPart),
  }
}

function hasApprovalRequest(
  toolCallId: string | undefined,
  approvalRequestParts: UIMessagePart[],
) {
  return !!toolCallId && approvalRequestParts.some((part) => part.toolCallId === toolCallId)
}

function toolApprovalStatus(
  approvalId: string | undefined,
  approvalResponseParts: UIMessagePart[],
): WorkbenchToolEvent['approvalStatus'] {
  if (!approvalId) {
    return undefined
  }
  const response = approvalResponseParts.find((part) => part.approvalId === approvalId)
  if (!response) {
    return 'pending'
  }
  return response.approved === false ? 'denied' : 'approved'
}

function toolCallStatus(
  toolCallId: string | undefined,
  terminalToolParts: UIMessagePart[],
): WorkbenchToolEvent['externalStatus'] {
  if (!toolCallId) {
    return undefined
  }
  const terminalPart = terminalToolParts.find((part) => part.toolCallId === toolCallId)
  if (terminalPart?.type === 'tool-result') {
    return 'completed'
  }
  if (terminalPart?.type === 'tool-error') {
    return 'failed'
  }
  return 'pending'
}

function isUIMessageToolPartType(type: string | undefined): type is WorkbenchToolEvent['type'] {
  return (
    type === 'tool-call' ||
    type === 'tool-result' ||
    type === 'tool-error' ||
    type === 'tool-approval-request'
  )
}

function isToolStreamPartType(type: TextStreamPart['type']): type is WorkbenchToolEvent['type'] {
  return (
    type === 'tool-call' ||
    type === 'tool-result' ||
    type === 'tool-error' ||
    type === 'tool-approval-request'
  )
}

function appendWorkbenchToolEvent(message: WorkbenchMessage, event: WorkbenchToolEvent) {
  if (
    event.toolCallId &&
    (event.type === 'tool-result' ||
      event.type === 'tool-error' ||
      event.type === 'tool-approval-request')
  ) {
    message.toolEvents = (message.toolEvents || []).map((item) => {
      if (item.type !== 'tool-call' || item.toolCallId !== event.toolCallId) {
        return item
      }
      return {
        ...item,
        externalStatus:
          event.type === 'tool-result'
            ? 'completed'
            : event.type === 'tool-error'
              ? 'failed'
              : undefined,
      }
    })
  }
  message.toolEvents = [...(message.toolEvents || []), event]
}

function appendWorkbenchError(message: WorkbenchMessage, content: string) {
  message.content += content
  message.state = 'error'
  if (message.reasoningState === 'streaming') {
    message.reasoningState = 'done'
  }
}

function finishWorkbenchMessage(message: WorkbenchMessage, state: WorkbenchMessage['state']) {
  if (message.state === 'streaming') {
    message.state = state
    if (message.reasoningState === 'streaming') {
      message.reasoningState = 'done'
    }
  }
}

function toolEventSummary(part: TextStreamPart) {
  switch (part.type) {
    case 'tool-call':
      return stringifyCompact(part.input)
    case 'tool-result':
      return stringifyCompact(part.result)
    case 'tool-error':
      return part.errorText || '工具执行失败'
    case 'tool-approval-request':
      return stringifyCompact(part.input)
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
