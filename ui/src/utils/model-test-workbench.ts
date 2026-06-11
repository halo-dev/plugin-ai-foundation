import type { AiModel, OutputSpec, TestUiMessageChatRequest } from '@/api/generated'
import { AiModelSpecModelTypeEnum } from '@/api/generated'
import { utils } from '@halo-dev/ui-shared'
import { DefaultChatTransport, type UIMessageChunk as HaloUIMessageChunk } from '@halo-dev/ai-ui-vue'

export type ChatRole = 'user' | 'assistant'

export interface WorkbenchMessage {
  id: string
  role: ChatRole
  content: string
  uiMessage?: UIMessage<Record<string, unknown>>
  transientData?: Record<string, unknown>
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

export interface ChatStreamOptions {
  testToolEnabled?: boolean
  testToolApprovalEnabled?: boolean
  externalTestToolEnabled?: boolean
  toolCallRepairEnabled?: boolean
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
  transientData?: boolean
  sourceId?: string
  fileId?: string
  url?: string
  title?: string
  mediaType?: string
  toolCallId?: string
  toolName?: string
  state?:
    | 'input-streaming'
    | 'input-available'
    | 'approval-requested'
    | 'approval-responded'
    | 'output-available'
    | 'output-denied'
    | 'output-error'
  stepIndex?: number
  input?: Record<string, unknown>
  inputText?: string
  output?: unknown
  errorText?: string
  approval?: {
    id: string
    approved?: boolean
    reason?: string
  }
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
  transient?: boolean
  sourceId?: string
  fileId?: string
  url?: string
  title?: string
  mediaType?: string
  toolCallId?: string
  toolName?: string
  state?: UIMessagePart['state']
  stepIndex?: number
  input?: Record<string, unknown>
  inputTextDelta?: string
  output?: unknown
  errorText?: string
  approval?: UIMessagePart['approval']
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

export function buildTestUiMessageChatRequest(
  messages: WorkbenchMessage[],
  parameters: ChatParameters,
  options?: {
    trigger?: UIMessageChatTrigger
    messageId?: string
  },
): TestUiMessageChatRequest {
  return {
    id: utils.id.uuid(),
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

function normalizedSystemPrompt(parameters: ChatParameters) {
  return parameters.systemPrompt?.trim() || undefined
}

export async function readTestUiMessageChatStream(options: {
  modelName: string
  requestBody: TestUiMessageChatRequest
  streamOptions: ChatStreamOptions
  signal: AbortSignal
  onChunks: (chunks: UIMessageChunk[]) => void
}) {
  const requestBody = options.requestBody as Record<string, unknown>
  const transport = new DefaultChatTransport({
    api: testUiMessageChatStreamUrl(options.modelName, options.streamOptions),
    fetch,
  })
  const stream = await transport.sendMessages({
    chatId: String(requestBody.id || utils.id.uuid()),
    messages: (options.requestBody.messages || []) as never[],
    trigger: (options.requestBody.trigger || 'submit-message') as 'submit-message' | 'regenerate-message',
    messageId: options.requestBody.messageId,
    body: requestBody,
    abortSignal: options.signal,
  })
  for await (const chunk of stream) {
    options.onChunks([chunk as HaloUIMessageChunk as UIMessageChunk])
  }
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
  if (isUIMessageDataChunk(chunk)) {
    if (chunk.name) {
      if (chunk.transientData || chunk.transient) {
        message.transientData = { ...message.transientData, [chunk.name]: chunk.data }
      } else {
        upsertUIMessagePart(uiMessage, chunk.type, chunk.id || chunk.name, {
          id: chunk.id || chunk.name,
          name: chunk.name,
          data: chunk.data,
          transientData: false,
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
    if (chunk.toolCallId) {
      const existing = uiMessage.parts.find(
        (part) => isUIMessageToolPartType(part.type) && part.toolCallId === chunk.toolCallId,
      )
      const inputText =
        chunk.state === 'input-streaming'
          ? `${existing?.inputText || ''}${chunk.inputTextDelta || ''}`
          : existing?.inputText
      upsertUIMessagePart(uiMessage, chunk.type, chunk.toolCallId, {
        toolCallId: chunk.toolCallId,
        toolName: chunk.toolName,
        state: chunk.state,
        input: chunk.input || existing?.input,
        inputText,
        output: chunk.output ?? existing?.output,
        errorText: chunk.errorText || existing?.errorText,
        approval: chunk.approval || existing?.approval,
        stepIndex: chunk.stepIndex,
        providerMetadata: chunk.providerMetadata || existing?.providerMetadata,
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

export function applyWorkbenchUIMessageSnapshot(
  message: WorkbenchMessage,
  uiMessage: UIMessage<Record<string, unknown>>,
  state: WorkbenchMessage['state'] = 'streaming',
) {
  message.uiMessage = {
    ...uiMessage,
    parts: uiMessage.parts.map((part) => ({ ...part })),
  }
  message.state = state
  if (message.reasoningState === 'streaming' && state !== 'streaming') {
    message.reasoningState = 'done'
  }
  if (message.uiMessage.parts.some((part) => part.type === 'reasoning')) {
    message.reasoningState = state === 'streaming' ? 'streaming' : 'done'
  }
  projectUIMessage(message)
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
  if (isUIMessageDataPartType(type)) {
    return 'id'
  }
  if (type === 'source-url') {
    return 'sourceId'
  }
  if (type === 'file') {
    return 'fileId'
  }
  if (isUIMessageToolPartType(type)) {
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

function mergePlainObjects(current: unknown, update: unknown): Record<string, unknown> | undefined {
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
  return parts
    .map((part) => uiMessagePartToToolEvent(part))
    .filter((event): event is WorkbenchToolEvent => !!event)
}

function uiMessagePartToToolEvent(part: UIMessagePart): WorkbenchToolEvent | undefined {
  if (!isUIMessageToolPartType(part.type)) {
    return undefined
  }
  const eventType = uiMessageToolEventType(part)
  if (!eventType) return undefined
  const approvalId = part.approval?.id
  const result = part.output
  return {
    id: `${eventType}-${approvalId || part.toolCallId || utils.id.uuid()}`,
    type: eventType,
    approvalId,
    toolCallId: part.toolCallId,
    toolName: part.toolName,
    stepIndex: part.stepIndex,
    input: part.input,
    result,
    errorText: part.errorText,
    approvalStatus: eventType === 'tool-approval-request' ? toolApprovalStatus(part) : undefined,
    externalStatus: eventType === 'tool-call' ? toolCallStatus(part) : undefined,
    summary: uiMessageToolEventSummary(part, eventType),
  }
}

function uiMessageToolEventType(part: UIMessagePart): WorkbenchToolEvent['type'] | undefined {
  if (part.state === 'approval-requested') {
    return 'tool-approval-request'
  }
  if (part.state === 'approval-responded' || part.state === 'output-denied') {
    return 'tool-approval-request'
  }
  switch (part.state) {
    case 'input-streaming':
    case 'input-available':
      return 'tool-call'
    case 'output-available':
      return 'tool-result'
    case 'output-error':
      return 'tool-error'
    default:
      return undefined
  }
}

function toolApprovalStatus(part: UIMessagePart): WorkbenchToolEvent['approvalStatus'] {
  if (part.approval?.approved === true) {
    return 'approved'
  }
  if (part.approval?.approved === false) {
    return 'denied'
  }
  if (part.approval?.id) {
    return 'pending'
  }
  return undefined
}

function toolCallStatus(part: UIMessagePart): WorkbenchToolEvent['externalStatus'] {
  if (part.state === 'output-available') {
    return 'completed'
  }
  if (part.state === 'output-error') {
    return 'failed'
  }
  return 'pending'
}

function isUIMessageToolPartType(
  type: string | undefined,
): type is `tool-${string}` {
  return !!type && type.startsWith('tool-')
}

function isUIMessageDataChunk(chunk: UIMessageChunk): chunk is UIMessageChunk & { type: `data-${string}` } {
  return isUIMessageDataPartType(chunk.type)
}

function isUIMessageDataPartType(type: string | undefined): type is `data-${string}` {
  return !!type && type.startsWith('data-')
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

function uiMessageToolEventSummary(
  part: UIMessagePart,
  eventType: WorkbenchToolEvent['type'],
) {
  switch (eventType) {
    case 'tool-call':
    case 'tool-approval-request':
      return stringifyCompact(part.input ?? part.inputText)
    case 'tool-result':
      return stringifyCompact(part.output)
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
