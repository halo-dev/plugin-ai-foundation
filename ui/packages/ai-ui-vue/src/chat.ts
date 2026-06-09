import { isAbortError, toError } from './errors'
import { generateId } from './id'
import {
  appendToolApprovalResponse,
  appendToolError,
  appendToolResult,
  applyUIMessageChunk,
  createUIMessageReducer,
} from './message-reducer'
import { DefaultChatTransport, createUserMessage } from './transports'
import type {
  ChatRequestOptions,
  ChatStatus,
  ChatTransport,
  IdGenerator,
  ToolApprovalRequestPart,
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
}

export interface SendMessageInput<METADATA = unknown> {
  id?: string
  role?: 'user'
  text?: string
  parts?: UIMessage['parts']
  metadata?: METADATA
  messageId?: string
}

export class Chat<METADATA = unknown> {
  readonly id: string
  readonly generateId: IdGenerator
  private readonly state: ChatStateAdapter<METADATA>
  private readonly transport: ChatTransport<METADATA>
  private readonly onError?: ChatInit<METADATA>['onError']
  private readonly onFinish?: ChatInit<METADATA>['onFinish']
  private readonly sendAutomaticallyWhen?: ChatInit<METADATA>['sendAutomaticallyWhen']
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
    this.onFinish = init.onFinish
    this.sendAutomaticallyWhen = init.sendAutomaticallyWhen
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
    this.state.setMessages([...messages])
  }

  clearError(): void {
    this.state.setError(undefined)
    if (this.status === 'error') {
      this.state.setStatus('ready')
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

  async addToolResult(
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
      appendToolResult(message, { ...input, toolName: tool.toolName })
    )
    await this.maybeSendAutomatically(options)
  }

  async addToolError(
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
      appendToolError(message, { ...input, toolName: tool.toolName })
    )
    await this.maybeSendAutomatically(options)
  }

  async addToolOutput(input: ToolOutputInput, options?: ChatRequestOptions): Promise<void> {
    const toolName = input.toolName ?? input.tool
    if ('state' in input && input.state === 'output-error') {
      await this.addToolError(
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
    await this.addToolResult(
      {
        toolCallId: outputInput.toolCallId,
        toolName,
        result: outputInput.output ?? outputInput.result,
        providerMetadata: outputInput.providerMetadata,
      },
      options
    )
  }

  async addToolApprovalResponse(
    input: ToolApprovalResponseInput,
    options?: ChatRequestOptions
  ): Promise<void> {
    const approval = this.resolveApprovalRequest(input.id ?? input.approvalId)
    this.updateLastAssistant((message) =>
      appendToolApprovalResponse(message, {
        approvalId: approval.approvalId,
        toolCallId: input.toolCallId ?? approval.toolCallId,
        toolName: input.toolName ?? input.tool ?? approval.toolName,
        approved: input.approved,
        reason: input.reason,
        providerMetadata: input.providerMetadata,
      })
    )
    await this.maybeSendAutomatically(options)
  }

  private applyUserMessage(message: SendMessageInput<METADATA>): void {
    const messages = this.messages
    const nextMessage =
      message.text != null
        ? ({
            ...createUserMessage(message.text, message.id ?? this.generateId()),
            metadata: message.metadata,
          } as UIMessage<METADATA>)
        : ({
            id: message.id ?? this.generateId(),
            role: message.role ?? 'user',
            parts: message.parts ?? [],
            metadata: message.metadata,
          } as UIMessage<METADATA>)

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

  private async makeRequest({
    trigger,
    messageId,
    options,
    allowAutoSubmit = true,
    requestMessages,
  }: {
    trigger: 'submit-message' | 'regenerate-message'
    messageId?: string
    options?: ChatRequestOptions
    allowAutoSubmit?: boolean
    requestMessages?: UIMessage<METADATA>[]
  }) {
    this.state.setStatus('submitted')
    this.state.setError(undefined)
    let isAbort = false
    let isError = false
    const abortController = new AbortController()
    this.activeAbortController = abortController
    const reducer = createUIMessageReducer<METADATA>({ messageId: this.generateId() })

    try {
      const stream = await this.transport.sendMessages({
        chatId: this.id,
        messages: requestMessages ?? this.messages,
        trigger,
        messageId,
        headers: options?.headers,
        body: options?.body,
        credentials: options?.credentials,
        metadata: options?.metadata,
        abortSignal: abortController.signal,
      })

      for await (const chunk of stream) {
        this.state.setStatus('streaming')
        this.applyAssistantChunk(reducer, chunk)
      }

      this.state.setStatus('ready')
    } catch (error) {
      if (isAbortError(error) || abortController.signal.aborted) {
        isAbort = true
        this.state.setStatus('ready')
        return
      }
      isError = true
      const normalized = toError(error)
      this.state.setError(normalized)
      this.state.setStatus('error')
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

    if (!isError && allowAutoSubmit && (await this.shouldSendAutomatically())) {
      await this.makeRequest({
        trigger: 'submit-message',
        messageId: this.messages[this.messages.length - 1]?.id,
        options,
        allowAutoSubmit: false,
      })
    }
  }

  private applyAssistantChunk(
    reducer: ReturnType<typeof createUIMessageReducer<METADATA>>,
    chunk: UIMessageChunk
  ) {
    applyUIMessageChunk(reducer, chunk)
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

  private resolveApprovalRequest(approvalId: string | undefined): ToolApprovalRequestPart {
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
      })
    }
  }

  private async shouldSendAutomatically(): Promise<boolean> {
    if (!this.sendAutomaticallyWhen) {
      return false
    }
    return Boolean(await this.sendAutomaticallyWhen({ messages: this.messages }))
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
      if (
        (part.type === 'tool-call' || part.type === 'tool-approval-request') &&
        part.toolCallId === toolCallId
      ) {
        return { toolName: part.toolName }
      }
    }
  }
  return undefined
}

function findLastApprovalRequest<METADATA>(
  messages: UIMessage<METADATA>[],
  approvalId: string
): ToolApprovalRequestPart | undefined {
  for (let messageIndex = messages.length - 1; messageIndex >= 0; messageIndex -= 1) {
    const message = messages[messageIndex]
    if (message.role !== 'assistant') {
      continue
    }
    for (let partIndex = message.parts.length - 1; partIndex >= 0; partIndex -= 1) {
      const part = message.parts[partIndex]
      if (part.type === 'tool-approval-request' && part.approvalId === approvalId) {
        return part
      }
    }
  }
  return undefined
}
