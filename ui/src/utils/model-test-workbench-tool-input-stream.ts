import {
  DefaultChatTransport,
  type HttpTransportOptions,
  type UIMessageChunk,
} from '@halo-dev/ai-foundation-sdk'

const MAX_RECORDED_EVENTS = 200

export type ToolInputStreamEventType =
  | 'tool-input-start'
  | 'tool-input-delta'
  | 'tool-input-available'
  | 'tool-input-error'

export interface ToolInputStreamEvent {
  type: ToolInputStreamEventType
  preview?: string
}

export interface ToolInputStreamDiagnostic {
  toolCallId: string
  toolName?: string
  startCount: number
  deltaCount: number
  availableCount: number
  errorCount: number
  inputText: string
  input?: unknown
  errorText?: string
  events: ToolInputStreamEvent[]
  droppedEventCount: number
  protocolIssues: string[]
}

export type ToolInputStreamMode = 'provider-native-delta' | 'final-only' | 'input-error' | 'pending'

export class ObservingChatTransport<METADATA = unknown> extends DefaultChatTransport<METADATA> {
  constructor(
    options: HttpTransportOptions<METADATA>,
    private readonly onChunk: (chunk: UIMessageChunk) => void,
  ) {
    super(options)
  }

  protected async *processResponse(response: Response): AsyncIterable<UIMessageChunk> {
    for await (const chunk of super.processResponse(response)) {
      this.onChunk(chunk)
      yield chunk
    }
  }
}

export function recordToolInputStreamChunk(
  diagnostics: ToolInputStreamDiagnostic[],
  chunk: UIMessageChunk,
): ToolInputStreamDiagnostic[] {
  if (!isToolInputStreamEvent(chunk)) return diagnostics

  const existingIndex = diagnostics.findIndex((item) => item.toolCallId === chunk.toolCallId)
  const diagnostic =
    existingIndex >= 0
      ? cloneDiagnostic(diagnostics[existingIndex]!)
      : createDiagnostic(chunk.toolCallId)
  applyChunk(diagnostic, chunk)

  if (existingIndex < 0) {
    return [...diagnostics, diagnostic]
  }

  const updatedDiagnostics = [...diagnostics]
  updatedDiagnostics[existingIndex] = diagnostic
  return updatedDiagnostics
}

export function classifyToolInputStream(
  diagnostic: ToolInputStreamDiagnostic,
): ToolInputStreamMode {
  if (diagnostic.deltaCount > 0) {
    return 'provider-native-delta'
  }
  if (diagnostic.availableCount > 0) {
    return 'final-only'
  }
  if (diagnostic.errorCount > 0) {
    return 'input-error'
  }
  return 'pending'
}

function isToolInputStreamEvent(
  chunk: UIMessageChunk,
): chunk is Extract<UIMessageChunk, { type: ToolInputStreamEventType }> {
  return (
    chunk.type === 'tool-input-start' ||
    chunk.type === 'tool-input-delta' ||
    chunk.type === 'tool-input-available' ||
    chunk.type === 'tool-input-error'
  )
}

function createDiagnostic(toolCallId: string): ToolInputStreamDiagnostic {
  return {
    toolCallId,
    startCount: 0,
    deltaCount: 0,
    availableCount: 0,
    errorCount: 0,
    inputText: '',
    events: [],
    droppedEventCount: 0,
    protocolIssues: [],
  }
}

function cloneDiagnostic(value: ToolInputStreamDiagnostic): ToolInputStreamDiagnostic {
  return {
    ...value,
    events: [...value.events],
    protocolIssues: [...value.protocolIssues],
  }
}

function applyChunk(
  diagnostic: ToolInputStreamDiagnostic,
  chunk: Extract<UIMessageChunk, { type: ToolInputStreamEventType }>,
) {
  const terminalCount = diagnostic.availableCount + diagnostic.errorCount
  switch (chunk.type) {
    case 'tool-input-start':
      if (diagnostic.startCount > 0) addIssue(diagnostic, '收到重复的 tool-input-start')
      if (diagnostic.deltaCount > 0 || terminalCount > 0) {
        addIssue(diagnostic, 'tool-input-start 出现在 delta 或终态之后')
      }
      diagnostic.startCount++
      diagnostic.toolName = chunk.toolName
      appendEvent(diagnostic, { type: chunk.type })
      break
    case 'tool-input-delta':
      if (diagnostic.startCount === 0) addIssue(diagnostic, 'tool-input-delta 缺少前置 start')
      if (terminalCount > 0) addIssue(diagnostic, 'tool-input-delta 出现在终态之后')
      diagnostic.deltaCount++
      diagnostic.inputText += chunk.inputTextDelta
      appendEvent(diagnostic, {
        type: chunk.type,
        preview: compactPreview(chunk.inputTextDelta),
      })
      break
    case 'tool-input-available':
      if (terminalCount > 0) addIssue(diagnostic, '收到重复的工具入参终态')
      if (diagnostic.deltaCount > 0 && diagnostic.startCount === 0) {
        addIssue(diagnostic, '流式入参完成时仍缺少 start')
      }
      diagnostic.availableCount++
      diagnostic.toolName = chunk.toolName
      diagnostic.input = chunk.input
      appendEvent(diagnostic, { type: chunk.type })
      break
    case 'tool-input-error':
      if (terminalCount > 0) addIssue(diagnostic, '收到重复的工具入参终态')
      diagnostic.errorCount++
      diagnostic.toolName = chunk.toolName
      diagnostic.errorText = chunk.errorText
      appendEvent(diagnostic, { type: chunk.type, preview: compactPreview(chunk.errorText) })
      break
  }
}

function appendEvent(diagnostic: ToolInputStreamDiagnostic, event: ToolInputStreamEvent) {
  if (diagnostic.events.length < MAX_RECORDED_EVENTS) {
    diagnostic.events.push(event)
    return
  }
  diagnostic.droppedEventCount++
}

function addIssue(diagnostic: ToolInputStreamDiagnostic, issue: string) {
  if (!diagnostic.protocolIssues.includes(issue)) diagnostic.protocolIssues.push(issue)
}

function compactPreview(value: string) {
  const compact = value.replace(/\s+/g, ' ')
  return compact.length > 48 ? `${compact.slice(0, 48)}…` : compact
}
