import { isAbortError, isProtocolError, toError } from './errors'
import { generateId } from './id'
import {
  applyUIMessageChunk,
  createUIMessageReducer,
  withToolApprovalDecision,
  withToolError,
  withToolOutput,
} from './message-reducer'
import type { DataPartSchemas, MessageMetadataSchema } from './schema'
import { DefaultChatTransport } from './transports'
import type {
  ChatRequestOptions,
  ChatStatus,
  ChatTransport,
  DataPart,
  FilePart,
  IdGenerator,
  ToolPart,
  ToolApprovalResponseInput,
  ToolOutputInput,
  ToolOutputSuccessInput,
  UIMessage,
  UIMessageChunk,
  UIMessageStreamTerminal,
} from './types'

export interface ChatStateAdapter<METADATA = unknown> {
  getMessages(): UIMessage<METADATA>[]
  setMessages(messages: UIMessage<METADATA>[]): void
  getStatus(): ChatStatus
  setStatus(status: ChatStatus): void
  getError(): Error | undefined
  setError(error: Error | undefined): void
}

export interface ChatInit<METADATA = unknown> {
  id?: string
  messages?: UIMessage<METADATA>[]
  state?: ChatStateAdapter<METADATA>
  transport?: ChatTransport<METADATA>
  generateId?: IdGenerator
  onError?: (error: Error) => void
  onData?: (part: DataPart) => void
  onToolCall?: (part: ToolPart) => void
  onFinish?: (event: {
    message: UIMessage<METADATA>
    messages: UIMessage<METADATA>[]
    terminal: UIMessageStreamTerminal
    isAbort: boolean
    isError: boolean
  }) => void | Promise<void>
  sendAutomaticallyWhen?: (options: {
    messages: UIMessage<METADATA>[]
  }) => boolean | PromiseLike<boolean>
  messageMetadataSchema?: MessageMetadataSchema<METADATA>
  dataPartSchemas?: DataPartSchemas
}

export interface SendMessageInput<METADATA = unknown> {
  id?: string
  role?: 'user'
  text?: string
  files?: SendMessageFileInput[]
  parts?: UIMessage['parts']
  metadata?: METADATA
  messageId?: string
}

export type SendMessageFileInput = Omit<FilePart, 'type' | 'id'> & { id?: string }

export class Chat<METADATA = unknown> {
  readonly id: string
  readonly generateId: IdGenerator
  private readonly state: ChatStateAdapter<METADATA>
  private readonly transport: ChatTransport<METADATA>
  private readonly onError?: ChatInit<METADATA>['onError']
  private readonly onData?: ChatInit<METADATA>['onData']
  private readonly onToolCall?: ChatInit<METADATA>['onToolCall']
  private readonly onFinish?: ChatInit<METADATA>['onFinish']
  private readonly sendAutomaticallyWhen?: ChatInit<METADATA>['sendAutomaticallyWhen']
  private readonly messageMetadataSchema?: ChatInit<METADATA>['messageMetadataSchema']
  private readonly dataPartSchemas?: ChatInit<METADATA>['dataPartSchemas']
  private readonly notifiedToolCalls = new Set<string>()
  private readonly listeners = new Set<() => void>()
  private activeAbortController?: AbortController

  constructor(init: ChatInit<METADATA> = {}) {
    this.generateId = init.generateId ?? (() => generateId('msg'))
    this.id = init.id ?? this.generateId()
    this.state =
      init.state ??
      createPlainChatState<METADATA>({
        messages: init.messages ?? [],
      })
    this.transport = init.transport ?? new DefaultChatTransport<METADATA>()
    this.onError = init.onError
    this.onData = init.onData
    this.onToolCall = init.onToolCall
    this.onFinish = init.onFinish
    this.sendAutomaticallyWhen = init.sendAutomaticallyWhen
    this.messageMetadataSchema = init.messageMetadataSchema
    this.dataPartSchemas = init.dataPartSchemas
  }

  get messages(): UIMessage<METADATA>[] {
    return this.state.getMessages()
  }

  set messages(messages: UIMessage<METADATA>[]) {
    this.setMessages(messages)
  }

  get status(): ChatStatus {
    return this.state.getStatus()
  }

  get error(): Error | undefined {
    return this.state.getError()
  }

  setMessages(messages: UIMessage<METADATA>[]): void {
    this.setChatMessages([...messages])
  }

  clearError(): void {
    this.setChatError(undefined)
    if (this.status === 'error' || this.status === 'disconnected') {
      this.setChatStatus('ready')
    }
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  async sendMessage(message?: SendMessageInput<METADATA>, options?: ChatRequestOptions): Promise<void> {
    if (message) {
      this.applyUserMessage(message)
    }
    await this.makeRequest({
      trigger: 'submit-message',
      messageId: message?.messageId,
      options,
    })
  }

  async regenerate({
    messageId,
    ...options
  }: { messageId?: string } & ChatRequestOptions = {}): Promise<void> {
    const messages = this.messages
    const index =
      messageId == null
        ? findLastIndex(messages, (message) => message.role === 'assistant')
        : messages.findIndex((message) => message.id === messageId)

    if (index === -1) {
      throw new Error(`message ${messageId ?? '<last assistant>'} not found`)
    }

    const keepUntil = messages[index].role === 'assistant' ? index : index + 1
    this.setMessages(messages.slice(0, keepUntil))
    await this.makeRequest({ trigger: 'regenerate-message', messageId, options, requestMessages: messages })
  }

  stop(): void {
    this.activeAbortController?.abort()
  }

  isLastAssistantMessageToolComplete(): boolean {
    const assistant = [...this.messages].reverse().find((message) => message.role === 'assistant')
    if (!assistant) {
      return true
    }
    return assistant.parts
      .filter(isToolPart)
      .every((part) => part.state === 'output-available' || part.state === 'output-error' || part.state === 'output-denied')
  }

  private async appendToolOutputSuccess(
    input: {
      toolCallId: string
      toolName?: string
      result: unknown
      providerMetadata?: Record<string, unknown>
    },
    options?: ChatRequestOptions
  ): Promise<void> {
    const tool = this.resolveToolCall(input.toolCallId, input.toolName)
    this.updateLastAssistant((message) =>
      withToolOutput(message, { ...input, toolName: tool.toolName })
    )
    await this.maybeSendAutomatically(options)
  }

  private async appendToolOutputError(
    input: {
      toolCallId: string
      toolName?: string
      errorText: string
      providerMetadata?: Record<string, unknown>
    },
    options?: ChatRequestOptions
  ): Promise<void> {
    const tool = this.resolveToolCall(input.toolCallId, input.toolName)
    this.updateLastAssistant((message) =>
      withToolError(message, { ...input, toolName: tool.toolName })
    )
    await this.maybeSendAutomatically(options)
  }

  async addToolOutput(input: ToolOutputInput, options?: ChatRequestOptions): Promise<void> {
    const toolName = input.toolName ?? input.tool
    if ('state' in input && input.state === 'output-error') {
      await this.appendToolOutputError(
        {
          toolCallId: input.toolCallId,
          toolName,
          errorText: input.errorText,
          providerMetadata: input.providerMetadata,
        },
        options
      )
      return
    }
    const outputInput = input as ToolOutputSuccessInput
    await this.appendToolOutputSuccess(
      {
        toolCallId: outputInput.toolCallId,
        toolName,
        result: outputInput.output ?? outputInput.result,
        providerMetadata: outputInput.providerMetadata,
      },
      options
    )
  }

  async rejectToolCall(
    input: Omit<ToolApprovalResponseInput, 'approved'> & { approved?: false },
    options?: ChatRequestOptions
  ): Promise<void> {
    await this.addToolApprovalResponse({ ...input, approved: false }, options)
  }

  async addToolApprovalResponse(
    input: ToolApprovalResponseInput,
    options?: ChatRequestOptions
  ): Promise<void> {
    const approval =
      input.id || input.approvalId
        ? this.resolveApprovalRequest(input.id ?? input.approvalId)
        : undefined
    const toolCallId = input.toolCallId ?? approval?.toolCallId
    if (!toolCallId) {
      throw new Error('Tool call id is required.')
    }
    const tool = this.resolveToolCall(toolCallId, input.toolName ?? input.tool)
    this.updateLastAssistant((message) =>
      withToolApprovalDecision(message, {
        approvalId: approval?.approval?.id ?? input.id ?? input.approvalId ?? toolCallId,
        toolCallId,
        toolName: input.toolName ?? input.tool ?? tool.toolName,
        approved: input.approved,
        reason: input.reason,
        providerMetadata: input.providerMetadata,
      })
    )
    await this.maybeSendAutomatically(options)
  }

  private applyUserMessage(message: SendMessageInput<METADATA>): void {
    const messages = this.messages
    const nextMessage = {
      id: message.id ?? this.generateId(),
      role: message.role ?? 'user',
      parts: this.userMessageParts(message),
      metadata: message.metadata,
    } as UIMessage<METADATA>

    if (message.messageId) {
      const index = messages.findIndex((item) => item.id === message.messageId)
      if (index === -1) {
        throw new Error(`message with id ${message.messageId} not found`)
      }
      if (messages[index].role !== 'user') {
        throw new Error(`message with id ${message.messageId} is not a user message`)
      }
      this.setMessages([
        ...messages.slice(0, index),
        { ...nextMessage, id: message.messageId },
      ])
      return
    }

    this.setMessages([...messages, nextMessage])
  }

  private userMessageParts(message: SendMessageInput<METADATA>): UIMessage['parts'] {
    if (message.parts) {
      return message.parts
    }
    const parts: UIMessage['parts'] = []
    if (message.text != null) {
      parts.push({ type: 'text', id: generateId('text'), text: message.text })
    }
    for (const file of message.files ?? []) {
      parts.push({
        type: 'file',
        id: file.id ?? generateId('file'),
        url: file.url,
        title: file.title,
        mediaType: file.mediaType,
        data: file.data,
        providerMetadata: file.providerMetadata,
      })
    }
    return parts
  }

  private async makeRequest({
    trigger,
    messageId,
    options,
    allowAutoSubmit = true,
    requestMessages,
    reducerMessage,
  }: {
    trigger: 'submit-message' | 'regenerate-message'
    messageId?: string
    options?: ChatRequestOptions
    allowAutoSubmit?: boolean
    requestMessages?: UIMessage<METADATA>[]
    reducerMessage?: UIMessage<METADATA>
  }) {
    const outcome = await this.consumeAssistantStream({
      reducerMessage: reducerMessage ?? { id: this.generateId(), role: 'assistant', parts: [] },
      createStream: (abortSignal) =>
        this.transport.sendMessages({
          chatId: this.id,
          messages: requestMessages ?? this.messages,
          trigger,
          messageId,
          headers: options?.headers,
          body: options?.body,
          credentials: options?.credentials,
          metadata: options?.metadata,
          abortSignal,
        }),
    })

    if (this.status === 'ready' && !outcome.isError && allowAutoSubmit && (await this.shouldSendAutomatically())) {
      await this.makeRequest({
        trigger: 'submit-message',
        messageId: this.messages[this.messages.length - 1]?.id,
        options,
        allowAutoSubmit: false,
        reducerMessage: this.lastAssistantMessage(),
      })
    }
  }

  private async consumeAssistantStream({
    reducerMessage,
    createStream,
  }: {
    reducerMessage: UIMessage<METADATA>
    createStream: (abortSignal: AbortSignal) => Promise<AsyncIterable<UIMessageChunk>>
  }): Promise<{ isAbort: boolean; isError: boolean }> {
    this.setChatStatus('submitted')
    this.setChatError(undefined)
    let isAbort = false
    let isError = false
    let hasStartedReadableStream = false
    const abortController = new AbortController()
    this.activeAbortController = abortController
    const reducer = createUIMessageReducer<METADATA>({
      message: reducerMessage,
      messageMetadataSchema: this.messageMetadataSchema,
      dataPartSchemas: this.dataPartSchemas,
    })

    try {
      const stream = await createStream(abortController.signal)
      for await (const chunk of stream) {
        this.applyAssistantChunk(reducer, chunk)
        hasStartedReadableStream = true
        this.setChatStatus('streaming')
      }
      if (reducer.terminal.errorText) {
        isError = true
        const normalized = new Error(reducer.terminal.errorText)
        this.setChatError(normalized)
        this.setChatStatus('error')
        this.onError?.(normalized)
      } else {
        this.setChatStatus('ready')
      }
    } catch (error) {
      if (isAbortError(error) || abortController.signal.aborted) {
        isAbort = true
        this.setChatStatus('ready')
        return { isAbort, isError }
      }
      abortController.abort()
      isError = true
      const normalized = toError(error)
      this.setChatError(normalized)
      if (hasStartedReadableStream && !isProtocolError(normalized)) {
        isError = false
        this.setChatStatus('disconnected')
      } else {
        this.setChatStatus('error')
      }
      this.onError?.(normalized)
    } finally {
      if (this.activeAbortController === abortController) {
        this.activeAbortController = undefined
      }
      await this.onFinish?.({
        message: reducer.message,
        messages: this.messages,
        terminal: reducer.terminal,
        isAbort,
        isError,
      })
    }
    return { isAbort, isError }
  }

  private applyAssistantChunk(
    reducer: ReturnType<typeof createUIMessageReducer<METADATA>>,
    chunk: UIMessageChunk
  ) {
    applyUIMessageChunk(reducer, chunk)
    if (isDataChunk(chunk)) {
      this.onData?.(dataPartFromChunk(reducer.message, chunk))
    }
    const lastPart = reducer.message.parts[reducer.message.parts.length - 1]
    if (lastPart && isToolPart(lastPart) && lastPart.state === 'input-available') {
      const tool = lastPart
      if (!this.notifiedToolCalls.has(tool.toolCallId)) {
        this.notifiedToolCalls.add(tool.toolCallId)
        this.onToolCall?.(tool)
      }
    }
    if (!reducer.visible && chunk.type !== 'error' && chunk.type !== 'abort') {
      return
    }
    const messages = this.messages
    const last = messages[messages.length - 1]
    if (last?.role === 'assistant' && last.id === reducer.message.id) {
      this.setMessages([...messages.slice(0, -1), reducer.message])
    } else {
      this.setMessages([...messages, reducer.message])
    }
  }

  private updateLastAssistant(update: (message: UIMessage<METADATA>) => UIMessage<METADATA>): void {
    const messages = this.messages
    const index = findLastIndex(messages, (message) => message.role === 'assistant')
    if (index === -1) {
      throw new Error('No assistant message is available for tool continuation.')
    }
    this.setMessages([
      ...messages.slice(0, index),
      update(messages[index]),
      ...messages.slice(index + 1),
    ])
  }

  private resolveToolCall(toolCallId: string, toolName?: string): { toolName: string } {
    const found = findLastToolPart(this.messages, toolCallId)
    const resolvedToolName = toolName ?? found?.toolName
    if (!resolvedToolName) {
      throw new Error(`Tool call ${toolCallId} was not found.`)
    }
    return { toolName: resolvedToolName }
  }

  private resolveApprovalRequest(approvalId: string | undefined): ToolPart {
    if (!approvalId) {
      throw new Error('Tool approval id is required.')
    }
    const found = findLastApprovalRequest(this.messages, approvalId)
    if (!found) {
      throw new Error(`Tool approval request ${approvalId} was not found.`)
    }
    return found
  }

  private async maybeSendAutomatically(options?: ChatRequestOptions): Promise<void> {
    if (this.status === 'submitted' || this.status === 'streaming') {
      return
    }
    if (await this.shouldSendAutomatically()) {
      await this.makeRequest({
        trigger: 'submit-message',
        messageId: this.messages[this.messages.length - 1]?.id,
        options,
        allowAutoSubmit: false,
        reducerMessage: this.lastAssistantMessage(),
      })
    }
  }

  private lastAssistantMessage(): UIMessage<METADATA> | undefined {
    return [...this.messages].reverse().find((message) => message.role === 'assistant')
  }

  private async shouldSendAutomatically(): Promise<boolean> {
    if (!this.sendAutomaticallyWhen) {
      return false
    }
    return Boolean(await this.sendAutomaticallyWhen({ messages: this.messages }))
  }

  private setChatMessages(messages: UIMessage<METADATA>[]): void {
    this.state.setMessages(messages)
    this.emitChange()
  }

  private setChatStatus(status: ChatStatus): void {
    this.state.setStatus(status)
    this.emitChange()
  }

  private setChatError(error: Error | undefined): void {
    this.state.setError(error)
    this.emitChange()
  }

  private emitChange(): void {
    for (const listener of this.listeners) {
      listener()
    }
  }
}

export function createPlainChatState<METADATA = unknown>({
  messages = [],
  status = 'ready',
  error,
}: {
  messages?: UIMessage<METADATA>[]
  status?: ChatStatus
  error?: Error
} = {}): ChatStateAdapter<METADATA> {
  let currentMessages = messages
  let currentStatus = status
  let currentError = error
  return {
    getMessages: () => currentMessages,
    setMessages: (messages) => {
      currentMessages = messages
    },
    getStatus: () => currentStatus,
    setStatus: (status) => {
      currentStatus = status
    },
    getError: () => currentError,
    setError: (error) => {
      currentError = error
    },
  }
}

function findLastIndex<T>(items: T[], predicate: (item: T) => boolean): number {
  for (let index = items.length - 1; index >= 0; index -= 1) {
    if (predicate(items[index])) {
      return index
    }
  }
  return -1
}

function findLastToolPart<METADATA>(
  messages: UIMessage<METADATA>[],
  toolCallId: string
): { toolName: string } | undefined {
  for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
    const message = messages[messageIndex]
    if (message.role !== 'assistant') {
      continue
    }
    for (let partIndex = message.parts.length - 1; partIndex >= 0; partIndex -= 1) {
      const part = message.parts[partIndex]
      if (isToolPart(part) && part.toolCallId === toolCallId) {
        return { toolName: part.toolName }
      }
    }
  }
  return undefined
}

function findLastApprovalRequest<METADATA>(
  messages: UIMessage<METADATA>[],
  approvalId: string
): ToolPart | undefined {
  for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
    const message = messages[messageIndex]
    if (message.role !== 'assistant') {
      continue
    }
    for (let partIndex = message.parts.length - 1; partIndex >= 0; partIndex -= 1) {
      const part = message.parts[partIndex]
      if (
        isToolPart(part) &&
        part.state === 'approval-requested' &&
        part.approval?.id === approvalId
      ) {
        return part
      }
    }
  }
  return undefined
}

function isToolPart(part: UIMessage['parts'][number]): part is ToolPart {
  return part.type.startsWith('tool-')
}

function isDataChunk(chunk: UIMessageChunk): chunk is Extract<UIMessageChunk, { type: `data-${string}` }> {
  return chunk.type.startsWith('data-')
}

function dataPartFromChunk<METADATA>(
  message: UIMessage<METADATA>,
  chunk: Extract<UIMessageChunk, { type: `data-${string}` }>
): DataPart {
  if (chunk.transient) {
    return {
      type: chunk.type,
      id: chunk.id,
      name: chunk.name,
      data: chunk.data,
      transientData: true,
    }
  }
  const part = message.parts.find(
    (item): item is DataPart =>
      item.type === chunk.type &&
      item.type.startsWith('data-') &&
      item.id === chunk.id
  )
  return part ?? {
    type: chunk.type,
    id: chunk.id,
    name: chunk.name,
    data: chunk.data,
    transientData: false,
  }
}

export function lastAssistantMessageIsCompleteWithApprovalResponses<METADATA = unknown>({
  messages,
}: {
  messages: UIMessage<METADATA>[]
}): boolean {
  const assistant = [...messages].reverse().find((message) => message.role === 'assistant')
  if (!assistant) {
    return false
  }
  const approvalParts = assistant.parts.filter(
    (part): part is ToolPart =>
      isToolPart(part) && (part.state === 'approval-requested' || part.state === 'approval-responded')
  )
  return (
    approvalParts.length > 0 &&
    approvalParts.every((part) => part.state === 'approval-responded')
  )
}
