import { generateId } from './id'
import type {
  AbortChunk,
  DataPart,
  FinishChunk,
  ReasoningPart,
  TextPart,
  ToolApprovalResponsePart,
  ToolErrorPart,
  ToolResultPart,
  UIMessage,
  UIMessageChunk,
  UIMessagePart,
  UIMessageStreamTerminal,
} from './types'

export interface UIMessageReducerState<METADATA = unknown> {
  message: UIMessage<METADATA>
  terminal: UIMessageStreamTerminal
  visible: boolean
}

export interface CreateReducerOptions<METADATA = unknown> {
  message?: UIMessage<METADATA>
  messageId?: string
  metadata?: METADATA
}

export function createUIMessageReducer<METADATA = unknown>(
  options: CreateReducerOptions<METADATA> = {}
): UIMessageReducerState<METADATA> {
  return {
    message: options.message ?? {
      id: options.messageId ?? generateId('msg'),
      role: 'assistant',
      parts: [],
      metadata: options.metadata,
    },
    terminal: {},
    visible: Boolean(options.message?.parts.length),
  }
}

export function applyUIMessageChunk<METADATA>(
  state: UIMessageReducerState<METADATA>,
  chunk: UIMessageChunk
): UIMessageReducerState<METADATA> {
  switch (chunk.type) {
    case 'start':
      state.message = {
        ...state.message,
        id: chunk.messageId ?? state.message.id,
        metadata: mergeMetadata(state.message.metadata, chunk.messageMetadata),
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
    case 'data':
      if (!chunk.transientData) {
        upsertPart(state, {
          type: 'data',
          name: chunk.name,
          data: chunk.data,
          transientData: false,
        })
      }
      break
    case 'message-metadata':
      state.message = {
        ...state.message,
        metadata: mergeMetadata(state.message.metadata, chunk.messageMetadata),
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
    case 'file':
      upsertPart(state, {
        type: 'file',
        id: chunk.id,
        url: chunk.url,
        title: chunk.title,
        mediaType: chunk.mediaType,
        data: chunk.data,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'tool-input-start':
    case 'tool-input-delta':
      break
    case 'tool-call':
      upsertPart(state, {
        type: 'tool-call',
        toolCallId: chunk.toolCallId,
        toolName: chunk.toolName,
        input: chunk.input,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'tool-result':
      upsertPart(state, {
        type: 'tool-result',
        toolCallId: chunk.toolCallId,
        toolName: chunk.toolName,
        result: chunk.result,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'tool-error':
      upsertPart(state, {
        type: 'tool-error',
        toolCallId: chunk.toolCallId,
        toolName: chunk.toolName,
        errorText: chunk.errorText,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'tool-approval-request':
      upsertPart(state, {
        type: 'tool-approval-request',
        approvalId: chunk.approvalId,
        toolCallId: chunk.toolCallId,
        toolName: chunk.toolName,
        input: chunk.input,
        stepIndex: chunk.stepIndex,
        providerMetadata: chunk.providerMetadata,
      })
      break
    case 'tool-approval-response':
      addToolApprovalResponseToState(state, chunk)
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
        metadata: mergeMetadata(state.message.metadata, chunk.messageMetadata),
      }
      state.terminal = finishTerminal(state.terminal, chunk)
      break
    case 'error':
      state.terminal = { ...state.terminal, errorText: chunk.errorText, finishReason: 'error' }
      break
    case 'abort':
      state.terminal = { ...state.terminal, aborted: true }
      break
  }
  return state
}

export function appendToolResult<METADATA = unknown>(
  message: UIMessage<METADATA>,
  result: Omit<ToolResultPart, 'type'>
): UIMessage<METADATA> {
  return upsertMessagePart(message, { type: 'tool-result', ...result })
}

export function appendToolError<METADATA = unknown>(
  message: UIMessage<METADATA>,
  error: Omit<ToolErrorPart, 'type'>
): UIMessage<METADATA> {
  return upsertMessagePart(message, { type: 'tool-error', ...error })
}

export function appendToolApprovalResponse<METADATA = unknown>(
  message: UIMessage<METADATA>,
  response: Omit<ToolApprovalResponsePart, 'type'>
): UIMessage<METADATA> {
  return upsertMessagePart(message, { type: 'tool-approval-response', ...response })
}

function appendTextPart<METADATA>(
  state: UIMessageReducerState<METADATA>,
  id: string,
  delta: string
) {
  const part = state.message.parts.find(
    (item): item is TextPart => item.type === 'text' && item.id === id
  )
  upsertPart(state, { type: 'text', id, text: `${part?.text ?? ''}${delta ?? ''}` })
}

function appendReasoningPart<METADATA>(
  state: UIMessageReducerState<METADATA>,
  id: string,
  delta: string,
  providerMetadata?: Record<string, unknown>
) {
  const part = state.message.parts.find(
    (item): item is ReasoningPart => item.type === 'reasoning' && item.id === id
  )
  upsertPart(state, {
    type: 'reasoning',
    id,
    text: `${part?.text ?? ''}${delta ?? ''}`,
    providerMetadata: providerMetadata ?? part?.providerMetadata,
  })
}

function addToolApprovalResponseToState<METADATA>(
  state: UIMessageReducerState<METADATA>,
  response: ToolApprovalResponsePart | Extract<UIMessageChunk, { type: 'tool-approval-response' }>
) {
  upsertPart(state, {
    type: 'tool-approval-response',
    approvalId: response.approvalId,
    toolCallId: response.toolCallId,
    toolName: response.toolName,
    approved: response.approved,
    reason: response.reason,
    providerMetadata: response.providerMetadata,
  })
}

function upsertPart<METADATA>(state: UIMessageReducerState<METADATA>, part: UIMessagePart) {
  state.message = upsertMessagePart(state.message, part)
  state.visible = state.visible || isPersistedVisiblePart(part)
}

function upsertMessagePart<METADATA>(
  message: UIMessage<METADATA>,
  part: UIMessagePart
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
  if (left.type !== right.type) {
    return false
  }
  switch (right.type) {
    case 'text':
    case 'reasoning':
    case 'file':
      return 'id' in left && left.id === right.id
    case 'data':
      return (left as DataPart).name === right.name
    case 'source-url':
      return 'sourceId' in left && left.sourceId === right.sourceId
    case 'tool-call':
    case 'tool-result':
    case 'tool-error':
      return 'toolCallId' in left && left.toolCallId === right.toolCallId
    case 'tool-approval-request':
    case 'tool-approval-response':
      return 'approvalId' in left && left.approvalId === right.approvalId
  }
}

function isPersistedVisiblePart(part: UIMessagePart): boolean {
  return part.type !== 'data' || !part.transientData
}

function finishTerminal(terminal: UIMessageStreamTerminal, chunk: FinishChunk): UIMessageStreamTerminal {
  return {
    ...terminal,
    finishReason: chunk.finishReason,
    rawFinishReason: chunk.rawFinishReason,
    usage: chunk.usage,
  }
}

function mergeMetadata<METADATA>(current: METADATA | undefined, update: unknown): METADATA | undefined {
  if (update == null) {
    return current
  }
  if (isRecord(current) && isRecord(update)) {
    return { ...current, ...update } as METADATA
  }
  return update as METADATA
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
