export type UIMessageRole = 'system' | 'user' | 'assistant'

export type UIMessageChatTrigger = 'submit-message' | 'regenerate-message'

export type ChatStatus = 'submitted' | 'streaming' | 'ready' | 'error' | 'disconnected'

export type FinishReason =
  | 'stop'
  | 'length'
  | 'content-filter'
  | 'tool-calls'
  | 'error'
  | 'other'
  | 'unknown'
  | string

export interface LanguageModelUsage {
  inputTokens?: number
  outputTokens?: number
  totalTokens?: number
  [key: string]: unknown
}

export interface UIMessage<METADATA = unknown> {
  id: string
  role: UIMessageRole
  parts: UIMessagePart[]
  metadata?: METADATA
}

export type UIMessagePart =
  | TextPart
  | ReasoningPart
  | DataPart
  | SourceUrlPart
  | FilePart
  | ToolPart

export interface TextPart {
  type: 'text'
  id: string
  text: string
}

export interface ReasoningPart {
  type: 'reasoning'
  id: string
  text: string
  providerMetadata?: Record<string, unknown>
}

export interface DataPart<T = unknown> {
  type: `data-${string}`
  id: string
  name: string
  data: T
  transientData?: boolean
}

export interface SourceUrlPart {
  type: 'source-url'
  sourceId: string
  url: string
  title?: string
  providerMetadata?: Record<string, unknown>
}

export interface FilePart {
  type: 'file'
  id: string
  url?: string
  title?: string
  mediaType?: string
  data?: unknown
  providerMetadata?: Record<string, unknown>
}

export type ToolPartState =
  | 'input-streaming'
  | 'input-available'
  | 'approval-requested'
  | 'approval-responded'
  | 'output-available'
  | 'output-denied'
  | 'output-error'

export interface ToolApproval {
  id: string
  approved?: boolean
  reason?: string
}

export interface ToolPart {
  type: `tool-${string}`
  toolCallId: string
  toolName: string
  state: ToolPartState
  input?: Record<string, unknown>
  inputText?: string
  output?: unknown
  errorText?: string
  approval?: ToolApproval
  providerMetadata?: Record<string, unknown>
}

export interface ToolOutputSuccessInput {
  toolCallId: string
  toolName?: string
  tool?: string
  result?: unknown
  output?: unknown
  providerMetadata?: Record<string, unknown>
}

export interface ToolOutputErrorInput {
  toolCallId: string
  toolName?: string
  tool?: string
  state: 'output-error'
  errorText: string
  providerMetadata?: Record<string, unknown>
}

export type ToolOutputInput = ToolOutputSuccessInput | ToolOutputErrorInput

export interface ToolApprovalResponseInput {
  id?: string
  approvalId?: string
  toolCallId?: string
  toolName?: string
  tool?: string
  approved: boolean
  reason?: string
  providerMetadata?: Record<string, unknown>
}

export type UIMessageChunk =
  | StartChunk
  | TextStartChunk
  | TextDeltaChunk
  | TextEndChunk
  | ReasoningStartChunk
  | ReasoningDeltaChunk
  | ReasoningEndChunk
  | DataChunk
  | MessageMetadataChunk
  | SourceUrlChunk
  | FileChunk
  | ToolChunk
  | FinishStepChunk
  | FinishChunk
  | ErrorChunk
  | AbortChunk

export interface StartChunk {
  type: 'start'
  messageId?: string
  messageMetadata?: unknown
}

export interface TextStartChunk {
  type: 'text-start'
  id: string
}

export interface TextDeltaChunk {
  type: 'text-delta'
  id: string
  delta: string
}

export interface TextEndChunk {
  type: 'text-end'
  id: string
}

export interface ReasoningStartChunk {
  type: 'reasoning-start'
  id: string
}

export interface ReasoningDeltaChunk {
  type: 'reasoning-delta'
  id: string
  delta: string
  providerMetadata?: Record<string, unknown>
}

export interface ReasoningEndChunk {
  type: 'reasoning-end'
  id: string
}

export interface DataChunk<T = unknown> {
  type: `data-${string}`
  id: string
  name: string
  data: T
  transient?: boolean
}

export interface MessageMetadataChunk {
  type: 'message-metadata'
  messageMetadata?: unknown
}

export interface SourceUrlChunk {
  type: 'source-url'
  sourceId?: string
  id?: string
  url: string
  title?: string
  providerMetadata?: Record<string, unknown>
}

export interface FileChunk {
  type: 'file'
  id: string
  url?: string
  title?: string
  mediaType?: string
  data?: unknown
  providerMetadata?: Record<string, unknown>
}

export interface ToolChunk {
  type: `tool-${string}`
  toolCallId: string
  toolName: string
  state: ToolPartState
  input?: Record<string, unknown>
  inputTextDelta?: string
  output?: unknown
  errorText?: string
  approval?: ToolApproval
  providerMetadata?: Record<string, unknown>
}

export interface FinishStepChunk {
  type: 'finish-step'
  stepIndex?: number
  finishReason?: FinishReason
  rawFinishReason?: string
  usage?: LanguageModelUsage
  warnings?: unknown[]
  request?: unknown
  response?: unknown
  providerMetadata?: Record<string, unknown>
}

export interface FinishChunk {
  type: 'finish'
  finishReason?: FinishReason
  rawFinishReason?: string
  usage?: LanguageModelUsage
  messageMetadata?: unknown
}

export interface ErrorChunk {
  type: 'error'
  errorText: string
  stepIndex?: number
  providerMetadata?: Record<string, unknown>
}

export interface AbortChunk {
  type: 'abort'
}

export interface UIMessageChatRequest<METADATA = unknown> {
  id: string
  messages: UIMessage<METADATA>[]
  trigger: UIMessageChatTrigger
  messageId?: string | null
}

export interface UIMessageStreamTerminal {
  finishReason?: FinishReason
  rawFinishReason?: string
  usage?: LanguageModelUsage
  aborted?: boolean
  errorText?: string
}

export interface ChatRequestOptions {
  headers?: HeadersInit
  body?: Record<string, unknown>
  credentials?: RequestCredentials
  metadata?: unknown
}

export interface SendMessagesOptions<METADATA = unknown> extends ChatRequestOptions {
  chatId: string
  messages: UIMessage<METADATA>[]
  trigger: UIMessageChatTrigger
  messageId?: string
  abortSignal?: AbortSignal
}

export interface ChatTransport<METADATA = unknown> {
  sendMessages(options: SendMessagesOptions<METADATA>): Promise<AsyncIterable<UIMessageChunk>>
}

export type FetchFunction = typeof fetch

export type Resolvable<T> = T | (() => T | Promise<T>)

export interface PreparedRequest {
  api?: string
  body?: Record<string, unknown>
  headers?: HeadersInit
  credentials?: RequestCredentials
}

export type IdGenerator = () => string

export interface JsonSchema {
  type?: string
  properties?: Record<string, JsonSchema>
  required?: string[]
  items?: JsonSchema
  enum?: unknown[]
  [key: string]: unknown
}

export type DeepPartial<T> = T extends Array<infer U>
  ? Array<DeepPartial<U>>
  : T extends object
    ? { [K in keyof T]?: DeepPartial<T[K]> }
    : T
