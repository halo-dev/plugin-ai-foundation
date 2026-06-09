import { computed, onScopeDispose, readonly, ref, shallowRef } from 'vue'
import { Chat, type ChatInit } from './chat'
import { generateId } from './id'
import type { ChatStatus, UIMessage } from './types'

export interface UseChatOptions<METADATA = unknown> extends Omit<ChatInit<METADATA>, 'state'> {
  id?: string
}

interface ChatStore<METADATA = unknown> {
  subscribers: number
  messages: ReturnType<typeof shallowRef<UIMessage<METADATA>[]>>
  status: ReturnType<typeof ref<ChatStatus>>
  error: ReturnType<typeof shallowRef<Error | undefined>>
  chat: Chat<METADATA>
}

const stores = new Map<string, ChatStore<unknown>>()

export function useChat<METADATA = unknown>(options: UseChatOptions<METADATA> = {}) {
  const id = options.id ?? generateId('chat')
  const store = getOrCreateChatStore<METADATA>(id, options)
  store.subscribers += 1
  onScopeDispose(() => {
    store.subscribers -= 1
    if (store.subscribers <= 0) {
      stores.delete(id)
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
    addToolResult: store.chat.addToolResult.bind(store.chat),
    addToolError: store.chat.addToolError.bind(store.chat),
    addToolApprovalResponse: store.chat.addToolApprovalResponse.bind(store.chat),
  }
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
