import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'
import { Chat, type ChatInit } from './chat'
import { generateId } from './id'
import type { ChatStatus, UIMessage } from './types'

export interface UseChatOptions<METADATA = unknown> extends Omit<ChatInit<METADATA>, 'state'> {
  id?: string
  chat?: Chat<METADATA>
}

interface ChatStore<METADATA = unknown> {
  subscribers: number
  messages: ReturnType<typeof shallowRef<UIMessage<METADATA>[]>>
  status: ReturnType<typeof ref<ChatStatus>>
  error: ReturnType<typeof shallowRef<Error | undefined>>
  chat: Chat<METADATA>
  unsubscribe?: () => void
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
    setMessages: store.chat.setMessages.bind(store.chat),
    clearError: store.chat.clearError.bind(store.chat),
    addToolOutput: store.chat.addToolOutput.bind(store.chat),
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
  const status = ref<ChatStatus>(chat.status)
  const error = shallowRef<Error | undefined>(chat.error)
  const sync = () => {
    messages.value = chat.messages
    status.value = chat.status
    error.value = chat.error
  }
  const store = {
    subscribers: 0,
    messages,
    status,
    error,
    chat,
    unsubscribe: chat.subscribe(sync),
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
  const status = ref<ChatStatus>('ready')
  const error = shallowRef<Error | undefined>()
  const chat = new Chat<METADATA>({
    ...options,
    id,
    state: {
      getMessages: () => messages.value,
      setMessages: (next) => {
        messages.value = next
      },
      getStatus: () => status.value,
      setStatus: (next) => {
        status.value = next
      },
      getError: () => error.value,
      setError: (next) => {
        error.value = next
      },
    },
  })
  const store = { subscribers: 0, messages, status, error, chat }
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
  ]
  const mixedKeys = creationOptionKeys.filter((key) => options[key] !== undefined)
  if (mixedKeys.length > 0) {
    throw new Error(`useChat({ chat }) cannot be mixed with creation options: ${mixedKeys.join(', ')}.`)
  }
}
