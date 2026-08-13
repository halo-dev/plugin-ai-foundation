import { AIUIProtocolError } from './errors'
import { generateId } from './id'
import { parsePartialJson } from './partial-json'
import { validateRuntimeSchema, type DataPartSchemas, type MessageMetadataSchema } from './schema'
import type {
  DataPart,
  FinishChunk,
  ReasoningPart,
  TextPart,
  ToolChunk,
  ToolPart,
  UIMessage,
  UIMessageChunk,
  UIMessagePart,
  UIMessageStreamTerminal,
} from './types'

interface ToolInputStreamState {
  toolName: string
  text: string
}

type CanonicalToolChunkType =
  | 'tool-input-start'
  | 'tool-input-delta'
  | 'tool-input-available'
  | 'tool-input-error'
  | 'tool-output-available'
  | 'tool-output-error'
  | 'tool-approval-request'
  | 'tool-approval-response'

type CanonicalToolChunk = Extract<UIMessageChunk, { type: CanonicalToolChunkType }>
type ToolInputStartChunk = Extract<CanonicalToolChunk, { type: 'tool-input-start' }>
type ToolInputDeltaChunk = Extract<CanonicalToolChunk, { type: 'tool-input-delta' }>
type ToolInputAvailableChunk = Extract<CanonicalToolChunk, { type: 'tool-input-available' }>
type ToolInputErrorChunk = Extract<CanonicalToolChunk, { type: 'tool-input-error' }>
type ToolPartUpdateChunk = Extract<
  CanonicalToolChunk,
  {
    type:
      | 'tool-output-available'
      | 'tool-output-error'
      | 'tool-approval-request'
      | 'tool-approval-response'
  }
>

const privateToolInputStreams = new WeakMap<object, Map<string, ToolInputStreamState>>()

export interface UIMessageReducerState<METADATA = unknown> {
  message: UIMessage<METADATA>
  terminal: UIMessageStreamTerminal
  visible: boolean
  messageMetadataSchema?: MessageMetadataSchema<METADATA>
  dataPartSchemas?: DataPartSchemas
}

export interface CreateReducerOptions<METADATA = unknown> {
  message?: UIMessage<METADATA>
  messageId?: string
  metadata?: METADATA
  messageMetadataSchema?: MessageMetadataSchema<METADATA>
  dataPartSchemas?: DataPartSchemas
}

export function createUIMessageReducer<METADATA = unknown>(
  options: CreateReducerOptions<METADATA> = {},
): UIMessageReducerState<METADATA> {
  const state: UIMessageReducerState<METADATA> = {
    message: options.message ?? {
      id: options.messageId ?? generateId('msg'),
      role: 'assistant',
      parts: [],
      metadata: options.metadata,
    },
    terminal: {},
    visible: Boolean(options.message?.parts.some(isPersistedVisiblePart)),
    messageMetadataSchema: options.messageMetadataSchema,
    dataPartSchemas: options.dataPartSchemas,
  }
  privateToolInputStreams.set(state, new Map())
  return state
}

export function applyUIMessageChunk<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: UIMessageChunk,
): UIMessageReducerState<METADATA> {
  validateUIMessageChunk(chunk)
  if (isDataChunk(chunk)) {
    if (!chunk.transient) {
      const data = validateDataPart(state, chunk)
      upsertPart(state, {
        type: chunk.type,
        id: chunk.id,
        name: chunk.name,
        data,
        transientData: false,
      })
    }
    return state
  }
  if (isCanonicalToolChunk(chunk)) {
    applyCanonicalToolChunk(state, chunk)
    return state
  }
  if (isLegacyToolChunk(chunk)) {
    upsertToolPart(state, chunk)
    return state
  }
  switch (chunk.type) {
    case 'start':
      state.message = {
        ...state.message,
        id: chunk.messageId ?? state.message.id,
        metadata: mergeAndValidateMetadata(state, chunk.messageMetadata),
      }
      break
    case 'text-start':
      upsertPart(state, { type: 'text', id: chunk.id, text: '' })
      break
    case 'text-delta':
      appendTextPart(state, chunk.id, chunk.delta)
      break
    case 'text-end':
      break
    case 'reasoning-start':
      upsertPart(state, { type: 'reasoning', id: chunk.id, text: '' })
      break
    case 'reasoning-delta':
      appendReasoningPart(state, chunk.id, chunk.delta, chunk.providerMetadata)
      break
    case 'reasoning-end':
      break
    case 'message-metadata':
      state.message = {
        ...state.message,
        metadata: mergeAndValidateMetadata(state, chunk.messageMetadata),
      }
      break
    case 'source-url':
      upsertPart(state, {
        type: 'source-url',
        sourceId: chunk.sourceId ?? chunk.id ?? chunk.url,
        url: chunk.url,
        title: chunk.title,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'source-document':
      upsertPart(state, {
        type: 'source-document',
        sourceId: chunk.sourceId,
        mediaType: chunk.mediaType,
        title: chunk.title,
        filename: chunk.filename,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'file': {
      const fileId = chunk.id ?? chunk.fileId
      if (!fileId) {
        throw new AIUIProtocolError('File chunk id is required.')
      }
      upsertPart(state, {
        type: 'file',
        id: fileId,
        fileId,
        url: chunk.url,
        title: chunk.title,
        mediaType: chunk.mediaType,
        data: chunk.data,
        providerMetadata: chunk.providerMetadata,
      })
      break
    }
    case 'start-step':
      upsertPart(state, { type: 'step-start' })
      break
    case 'finish-step':
      state.terminal = {
        ...state.terminal,
        finishReason: chunk.finishReason ?? state.terminal.finishReason,
        rawFinishReason: chunk.rawFinishReason ?? state.terminal.rawFinishReason,
        usage: chunk.usage ?? state.terminal.usage,
      }
      break
    case 'finish':
      state.message = {
        ...state.message,
        metadata: mergeAndValidateMetadata(state, chunk.messageMetadata),
      }
      state.terminal = finishTerminal(state.terminal, chunk)
      break
    case 'error':
      state.terminal = { ...state.terminal, errorText: chunk.errorText }
      break
    case 'abort':
      state.terminal = { ...state.terminal, aborted: true }
      break
  }
  return state
}

export function validateUIMessageChunk(chunk: UIMessageChunk): void {
  if (!chunk || typeof chunk.type !== 'string' || chunk.type.length === 0) {
    throw new AIUIProtocolError('UI message chunk type is required.')
  }
  if (isDataChunk(chunk)) {
    validateDynamicName(chunk.name, /^[A-Za-z][A-Za-z0-9_-]*$/, 'data name')
    if (chunk.type !== `data-${chunk.name}`) {
      throw new AIUIProtocolError(`Data chunk type must be data-${chunk.name}.`)
    }
    if (!chunk.id) {
      throw new AIUIProtocolError('Data chunk id is required.')
    }
    return
  }
  if (isCanonicalToolChunk(chunk)) {
    if (chunk.type === 'tool-input-delta') {
      if (!chunk.toolCallId) {
        throw new AIUIProtocolError(`${chunk.type} chunk toolCallId is required.`)
      }
    } else {
      validateToolIdentity(chunk.type, chunk.toolCallId, chunk.toolName)
    }
    if (chunk.type === 'tool-input-delta' && !chunk.inputTextDelta) {
      throw new AIUIProtocolError('Tool input-delta chunk inputTextDelta is required.')
    }
    if (chunk.type === 'tool-output-error' && !chunk.errorText) {
      throw new AIUIProtocolError('Tool output-error chunk errorText is required.')
    }
    if (chunk.type === 'tool-input-error' && !chunk.errorText) {
      throw new AIUIProtocolError('Tool input-error chunk errorText is required.')
    }
    if (
      (chunk.type === 'tool-approval-request' || chunk.type === 'tool-approval-response') &&
      !chunk.approvalId
    ) {
      throw new AIUIProtocolError('Tool approval chunk approvalId is required.')
    }
    if (chunk.type === 'tool-approval-response' && typeof chunk.approved !== 'boolean') {
      throw new AIUIProtocolError('Tool approval-response chunk approved is required.')
    }
    return
  }
  if (isLegacyToolChunk(chunk)) {
    validateDynamicName(chunk.toolName, /^[A-Za-z][A-Za-z0-9_-]*$/, 'tool name')
    if (chunk.type !== `tool-${chunk.toolName}`) {
      throw new AIUIProtocolError(`Tool chunk type must be tool-${chunk.toolName}.`)
    }
    if (!chunk.toolCallId) {
      throw new AIUIProtocolError('Tool chunk toolCallId is required.')
    }
    if (!chunk.state) {
      throw new AIUIProtocolError('Tool chunk state is required.')
    }
    if (chunk.state === 'output-error' && !chunk.errorText) {
      throw new AIUIProtocolError('Tool output-error chunk errorText is required.')
    }
  }
  if (chunk.type === 'source-document') {
    if (!chunk.sourceId) {
      throw new AIUIProtocolError('Document source chunk sourceId is required.')
    }
    if (!chunk.mediaType) {
      throw new AIUIProtocolError('Document source chunk mediaType is required.')
    }
    if (!chunk.title) {
      throw new AIUIProtocolError('Document source chunk title is required.')
    }
  }
  if (chunk.type === 'file' && !(chunk.id ?? chunk.fileId)) {
    throw new AIUIProtocolError('File chunk id is required.')
  }
}

export function withToolOutput<METADATA = unknown>(
  message: UIMessage<METADATA>,
  result: {
    toolCallId: string
    toolName: string
    output?: unknown
    result?: unknown
    providerMetadata?: Record<string, unknown>
  },
): UIMessage<METADATA> {
  const existing = findToolPart(message, result.toolCallId)
  return upsertMessagePart(message, {
    type: `tool-${result.toolName}`,
    toolCallId: result.toolCallId,
    toolName: result.toolName,
    state: 'output-available',
    input: existing?.input,
    output: result.output ?? result.result,
    approval: existing?.approval,
    providerMetadata: result.providerMetadata ?? existing?.providerMetadata,
  })
}

export function withToolError<METADATA = unknown>(
  message: UIMessage<METADATA>,
  error: {
    toolCallId: string
    toolName: string
    errorText: string
    providerMetadata?: Record<string, unknown>
  },
): UIMessage<METADATA> {
  const existing = findToolPart(message, error.toolCallId)
  return upsertMessagePart(message, {
    type: `tool-${error.toolName}`,
    toolCallId: error.toolCallId,
    toolName: error.toolName,
    state: 'output-error',
    input: existing?.input,
    errorText: error.errorText,
    approval: existing?.approval,
    providerMetadata: error.providerMetadata ?? existing?.providerMetadata,
  })
}

export function withToolApprovalDecision<METADATA = unknown>(
  message: UIMessage<METADATA>,
  response: {
    approvalId: string
    toolCallId: string
    toolName: string
    approved: boolean
    reason?: string
    providerMetadata?: Record<string, unknown>
  },
): UIMessage<METADATA> {
  const existing = findToolPart(message, response.toolCallId)
  return upsertMessagePart(message, {
    type: `tool-${response.toolName}`,
    toolCallId: response.toolCallId,
    toolName: response.toolName,
    state: 'approval-responded',
    input: existing?.input,
    errorText: existing?.errorText,
    approval: { id: response.approvalId, approved: response.approved, reason: response.reason },
    providerMetadata: response.providerMetadata ?? existing?.providerMetadata,
  })
}

function appendTextPart<METADATA>(
  state: UIMessageReducerState<METADATA>,
  id: string,
  delta: string,
) {
  const part = state.message.parts.find(
    (item): item is TextPart => item.type === 'text' && item.id === id,
  )
  upsertPart(state, { type: 'text', id, text: `${part?.text ?? ''}${delta ?? ''}` })
}

function appendReasoningPart<METADATA>(
  state: UIMessageReducerState<METADATA>,
  id: string,
  delta: string,
  providerMetadata?: Record<string, unknown>,
) {
  const part = state.message.parts.find(
    (item): item is ReasoningPart => item.type === 'reasoning' && item.id === id,
  )
  upsertPart(state, {
    type: 'reasoning',
    id,
    text: `${part?.text ?? ''}${delta ?? ''}`,
    providerMetadata: providerMetadata ?? part?.providerMetadata,
  })
}

function upsertPart<METADATA>(state: UIMessageReducerState<METADATA>, part: UIMessagePart) {
  state.message = upsertMessagePart(state.message, part)
  state.visible = state.visible || isPersistedVisiblePart(part)
}

function upsertMessagePart<METADATA>(
  message: UIMessage<METADATA>,
  part: UIMessagePart,
): UIMessage<METADATA> {
  const index = message.parts.findIndex((item) => samePartIdentity(item, part))
  const parts = [...message.parts]
  if (index === -1) {
    parts.push(part)
  } else {
    parts[index] = part
  }
  return { ...message, parts }
}

function samePartIdentity(left: UIMessagePart, right: UIMessagePart): boolean {
  if (isToolPart(left) && isToolPart(right)) {
    return left.toolCallId === right.toolCallId
  }
  if (left.type !== right.type) {
    return false
  }
  if (isDataPart(right)) {
    return isDataPart(left) && left.id === right.id
  }
  switch (right.type) {
    case 'text':
    case 'reasoning':
    case 'file':
      return 'id' in left && left.id === right.id
    case 'source-url':
      return 'sourceId' in left && left.sourceId === right.sourceId
    case 'source-document':
      return 'sourceId' in left && left.sourceId === right.sourceId
    default:
      return false
  }
}

function isPersistedVisiblePart(part: UIMessagePart): boolean {
  return part.type !== 'step-start' && (!isDataPart(part) || !part.transientData)
}

function finishTerminal(
  terminal: UIMessageStreamTerminal,
  chunk: FinishChunk,
): UIMessageStreamTerminal {
  return {
    ...terminal,
    finishReason: chunk.finishReason,
    rawFinishReason: chunk.rawFinishReason,
    usage: chunk.usage,
  }
}

function mergeMetadata<METADATA>(
  current: METADATA | undefined,
  update: unknown,
): METADATA | undefined {
  if (update == null) {
    return current
  }
  if (isRecord(current) && isRecord(update)) {
    return { ...current, ...update } as METADATA
  }
  return update as METADATA
}

function mergeAndValidateMetadata<METADATA>(
  state: UIMessageReducerState<METADATA>,
  update: unknown,
): METADATA | undefined {
  const merged = mergeMetadata(state.message.metadata, update)
  if (update == null || !state.messageMetadataSchema) {
    return merged
  }
  return validateRuntimeSchema(merged, state.messageMetadataSchema, {
    target: 'message-metadata',
  })
}

function validateDataPart<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: Extract<UIMessageChunk, { type: `data-${string}` }>,
): unknown {
  const schema = state.dataPartSchemas?.[chunk.name]
  if (!schema) {
    return chunk.data
  }
  return validateRuntimeSchema(chunk.data, schema, {
    target: 'data-part',
    partType: chunk.type,
    partName: chunk.name,
    partId: chunk.id,
  })
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function messageText(message: UIMessage): string {
  return message.parts
    .filter((part): part is TextPart => part.type === 'text')
    .map((part) => part.text)
    .join('')
}

function upsertToolPart<METADATA>(state: UIMessageReducerState<METADATA>, chunk: ToolChunk) {
  const existing = findToolPart(state.message, chunk.toolCallId)
  upsertPart(state, {
    type: chunk.type,
    toolCallId: chunk.toolCallId,
    toolName: chunk.toolName,
    state: chunk.state,
    input: chunk.input ?? existing?.input,
    output: chunk.output ?? existing?.output,
    errorText: chunk.errorText ?? existing?.errorText,
    approval: chunk.approval ?? existing?.approval,
    providerMetadata: chunk.providerMetadata ?? existing?.providerMetadata,
  })
}

function applyCanonicalToolChunk<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: CanonicalToolChunk,
): void {
  switch (chunk.type) {
    case 'tool-input-start':
      startToolInput(state, chunk)
      return
    case 'tool-input-delta':
      appendToolInput(state, chunk)
      return
    case 'tool-input-available':
      completeToolInput(state, chunk)
      return
    case 'tool-input-error':
      failToolInput(state, chunk)
      return
    default:
      upsertToolPart(state, toolPartUpdateFromCanonicalChunk(chunk))
  }
}

function startToolInput<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: ToolInputStartChunk,
): void {
  toolInputStreams(state).set(chunk.toolCallId, {
    toolName: chunk.toolName,
    text: '',
  })
  upsertPart(state, {
    type: `tool-${chunk.toolName}`,
    toolCallId: chunk.toolCallId,
    toolName: chunk.toolName,
    state: 'input-streaming',
    input: undefined,
  })
}

function appendToolInput<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: ToolInputDeltaChunk,
): void {
  const stream = toolInputStreams(state).get(chunk.toolCallId)
  if (!stream) {
    throw new AIUIProtocolError(
      `Tool input delta for ${chunk.toolCallId} requires a preceding tool-input-start.`,
    )
  }

  stream.text += chunk.inputTextDelta
  const existing = findToolPart(state.message, chunk.toolCallId)
  upsertPart(state, {
    type: `tool-${stream.toolName}`,
    toolCallId: chunk.toolCallId,
    toolName: stream.toolName,
    state: 'input-streaming',
    input: parsePartialJson(stream.text),
    providerMetadata: existing?.providerMetadata,
  })
}

function completeToolInput<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: ToolInputAvailableChunk,
): void {
  toolInputStreams(state).delete(chunk.toolCallId)
  const existing = findToolPart(state.message, chunk.toolCallId)
  upsertPart(state, {
    type: `tool-${chunk.toolName}`,
    toolCallId: chunk.toolCallId,
    toolName: chunk.toolName,
    state: 'input-available',
    input: chunk.input,
    providerMetadata: chunk.providerMetadata ?? existing?.providerMetadata,
  })
}

function failToolInput<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: ToolInputErrorChunk,
): void {
  toolInputStreams(state).delete(chunk.toolCallId)
  const existing = findToolPart(state.message, chunk.toolCallId)
  upsertPart(state, {
    type: `tool-${chunk.toolName}`,
    toolCallId: chunk.toolCallId,
    toolName: chunk.toolName,
    state: 'output-error',
    input: existing?.input,
    errorText: chunk.errorText,
    providerMetadata: chunk.providerMetadata ?? existing?.providerMetadata,
  })
}

function toolInputStreams<METADATA>(
  state: UIMessageReducerState<METADATA>,
): Map<string, ToolInputStreamState> {
  let streams = privateToolInputStreams.get(state)
  if (!streams) {
    streams = new Map()
    privateToolInputStreams.set(state, streams)
  }
  return streams
}

function toolPartUpdateFromCanonicalChunk(chunk: ToolPartUpdateChunk): ToolChunk {
  const base = {
    type: `tool-${chunk.toolName}` as `tool-${string}`,
    toolCallId: chunk.toolCallId,
    toolName: chunk.toolName,
    providerMetadata: 'providerMetadata' in chunk ? chunk.providerMetadata : undefined,
  }
  switch (chunk.type) {
    case 'tool-output-available':
      return {
        ...base,
        state: 'output-available',
        output: chunk.output,
      }
    case 'tool-output-error':
      return {
        ...base,
        state: 'output-error',
        errorText: chunk.errorText,
      }
    case 'tool-approval-request':
      return {
        ...base,
        state: 'approval-requested',
        input: chunk.input,
        approval: { id: chunk.approvalId },
      }
    case 'tool-approval-response':
      return {
        ...base,
        state: 'approval-responded',
        approval: { id: chunk.approvalId, approved: chunk.approved, reason: chunk.reason },
      }
  }
}

function findToolPart<METADATA>(
  message: UIMessage<METADATA>,
  toolCallId: string,
): ToolPart | undefined {
  return message.parts.find(
    (part): part is ToolPart => isToolPart(part) && part.toolCallId === toolCallId,
  )
}

function isDataChunk(
  chunk: UIMessageChunk,
): chunk is Extract<UIMessageChunk, { type: `data-${string}` }> {
  return chunk.type.startsWith('data-')
}

function isCanonicalToolChunk(chunk: UIMessageChunk): chunk is CanonicalToolChunk {
  return (
    chunk.type === 'tool-input-start' ||
    chunk.type === 'tool-input-delta' ||
    chunk.type === 'tool-input-available' ||
    chunk.type === 'tool-input-error' ||
    chunk.type === 'tool-output-available' ||
    chunk.type === 'tool-output-error' ||
    chunk.type === 'tool-approval-request' ||
    chunk.type === 'tool-approval-response'
  )
}

function isLegacyToolChunk(chunk: UIMessageChunk): chunk is ToolChunk {
  return chunk.type.startsWith('tool-') && !isCanonicalToolChunk(chunk)
}

function isDataPart(part: UIMessagePart): part is DataPart {
  return part.type.startsWith('data-')
}

function isToolPart(part: UIMessagePart): part is ToolPart {
  return part.type.startsWith('tool-')
}

function validateDynamicName(value: string | undefined, pattern: RegExp, label: string): void {
  if (!value || !pattern.test(value)) {
    throw new AIUIProtocolError(`${label} must be a simple identifier.`)
  }
}

function validateToolIdentity(
  type: string,
  toolCallId: string | undefined,
  toolName: string | undefined,
): void {
  validateDynamicName(toolName, /^[A-Za-z][A-Za-z0-9_-]*$/, 'tool name')
  if (!toolCallId) {
    throw new AIUIProtocolError(`${type} chunk toolCallId is required.`)
  }
}
