import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'
import { Chat, type ChatInit } from './chat'
import { generateId } from './id'
import type { ChatStatus, UIMessage } from './types'

export interface UseChatOptions<METADATA = unknown> extends Omit<ChatInit<METADATA>, 'state'> {
  id?: string
  chat?: Chat<METADATA>
  experimental_throttle?: ExperimentalThrottleOption
}

export type ExperimentalThrottleOption = number | { intervalMs?: number }

interface ChatStore<METADATA = unknown> {
  subscribers: number
  messages: ReturnType<typeof shallowRef<UIMessage<METADATA>[]>>
  status: ReturnType<typeof ref<ChatStatus>>
  error: ReturnType<typeof shallowRef<Error | undefined>>
  chat: Chat<METADATA>
  unsubscribe?: () => void
  flushMessages?: () => void
}

const stores = new Map<string, ChatStore<unknown>>()
const externalChatStores = new WeakMap<Chat<unknown>, ChatStore<unknown>>()

export function useChat<METADATA = unknown>(options: UseChatOptions<METADATA> = {}) {
  const store = options.chat
    ? getOrCreateExternalChatStore(options.chat, options)
    : getOrCreateChatStore<METADATA>(options.id ?? generateId('chat'), options)
  const id = store.chat.id
  store.subscribers += 1
  onScopeDispose(() => {
    store.subscribers -= 1
    if (store.subscribers <= 0) {
      store.unsubscribe?.()
      if (options.chat) {
        externalChatStores.delete(options.chat as Chat<unknown>)
      } else {
        stores.delete(id)
      }
    }
  })

  return {
    id,
    messages: readonly(store.messages),
    status: readonly(store.status),
    error: readonly(store.error),
    isLoading: computed(() => store.status.value === 'submitted' || store.status.value === 'streaming'),
    chat: store.chat,
    sendMessage: store.chat.sendMessage.bind(store.chat),
    regenerate: store.chat.regenerate.bind(store.chat),
    stop: store.chat.stop.bind(store.chat),
    setMessages: (messages: UIMessage<METADATA>[]) => {
      store.chat.setMessages(messages)
      store.flushMessages?.()
    },
    clearError: store.chat.clearError.bind(store.chat),
    addToolOutput: store.chat.addToolOutput.bind(store.chat),
    addToolApprovalResponse: store.chat.addToolApprovalResponse.bind(store.chat),
    rejectToolCall: store.chat.rejectToolCall.bind(store.chat),
    isLastAssistantMessageToolComplete: store.chat.isLastAssistantMessageToolComplete.bind(store.chat),
  }
}

function getOrCreateExternalChatStore<METADATA>(
  chat: Chat<METADATA>,
  options: UseChatOptions<METADATA>
): ChatStore<METADATA> {
  assertNoCreationOptionsWithChat(options)
  const existing = externalChatStores.get(chat as Chat<unknown>)
  if (existing) {
    return existing as ChatStore<METADATA>
  }

  const messages = shallowRef<UIMessage<METADATA>[]>(chat.messages)
  const committer = createMessageCommitter(messages, chat.messages, options.experimental_throttle)
  const status = ref<ChatStatus>(chat.status)
  const error = shallowRef<Error | undefined>(chat.error)
  const sync = () => {
    committer.set(chat.messages)
    status.value = chat.status
    error.value = chat.error
    if (shouldFlushMessagesForStatus(chat.status)) {
      committer.flush()
    }
  }
  const unsubscribe = chat.subscribe(sync)
  const store = {
    subscribers: 0,
    messages,
    status,
    error,
    chat,
    unsubscribe: () => {
      unsubscribe()
      committer.dispose()
    },
    flushMessages: committer.flush,
  }
  externalChatStores.set(chat as Chat<unknown>, store as ChatStore<unknown>)
  return store
}

function getOrCreateChatStore<METADATA>(
  id: string,
  options: UseChatOptions<METADATA>
): ChatStore<METADATA> {
  const existing = stores.get(id)
  if (existing) {
    return existing as ChatStore<METADATA>
  }

  const messages = shallowRef<UIMessage<METADATA>[]>(options.messages ?? [])
  const committer = createMessageCommitter(messages, messages.value, options.experimental_throttle)
  const status = ref<ChatStatus>('ready')
  const error = shallowRef<Error | undefined>()
  const chat = new Chat<METADATA>({
    ...options,
    id,
    state: {
      getMessages: () => committer.current(),
      setMessages: (next) => {
        committer.set(next)
      },
      getStatus: () => status.value,
      setStatus: (next) => {
        if (shouldFlushMessagesForStatus(next)) {
          committer.flush()
        }
        status.value = next
      },
      getError: () => error.value,
      setError: (next) => {
        error.value = next
      },
    },
  })
  const store = {
    subscribers: 0,
    messages,
    status,
    error,
    chat,
    unsubscribe: committer.dispose,
    flushMessages: committer.flush,
  }
  stores.set(id, store as ChatStore<unknown>)
  return store
}

function assertNoCreationOptionsWithChat<METADATA>(options: UseChatOptions<METADATA>): void {
  const creationOptionKeys: Array<keyof UseChatOptions<METADATA>> = [
    'id',
    'messages',
    'transport',
    'generateId',
    'onError',
    'onData',
    'onToolCall',
    'onFinish',
    'sendAutomaticallyWhen',
    'messageMetadataSchema',
    'dataPartSchemas',
  ]
  const mixedKeys = creationOptionKeys.filter((key) => options[key] !== undefined)
  if (mixedKeys.length > 0) {
    throw new Error(`useChat({ chat }) cannot be mixed with creation options: ${mixedKeys.join(', ')}.`)
  }
}

function createMessageCommitter<METADATA>(
  target: ReturnType<typeof shallowRef<UIMessage<METADATA>[]>>,
  initial: UIMessage<METADATA>[],
  option: ExperimentalThrottleOption | undefined
) {
  let currentMessages = initial
  let pendingMessages: UIMessage<METADATA>[] | undefined
  let timer: ReturnType<typeof setTimeout> | undefined
  const intervalMs = resolveThrottleInterval(option)

  const flush = () => {
    if (timer) {
      clearTimeout(timer)
      timer = undefined
    }
    if (pendingMessages) {
      target.value = pendingMessages
      pendingMessages = undefined
    }
  }

  const schedule = () => {
    if (!intervalMs) {
      flush()
      return
    }
    if (!timer) {
      timer = setTimeout(flush, intervalMs)
    }
  }

  return {
    current: () => currentMessages,
    set: (messages: UIMessage<METADATA>[]) => {
      currentMessages = messages
      pendingMessages = messages
      schedule()
    },
    flush,
    dispose: () => {
      if (timer) {
        clearTimeout(timer)
      }
      timer = undefined
      pendingMessages = undefined
    },
  }
}

function resolveThrottleInterval(option: ExperimentalThrottleOption | undefined): number | undefined {
  const intervalMs = typeof option === 'number' ? option : option?.intervalMs
  return typeof intervalMs === 'number' && intervalMs > 0 ? intervalMs : undefined
}

function shouldFlushMessagesForStatus(status: ChatStatus): boolean {
  return status === 'submitted' || status === 'ready' || status === 'error' || status === 'disconnected'
}
