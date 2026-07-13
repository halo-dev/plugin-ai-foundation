import { ModelOptionModelTypeEnum, type ModelOption } from '@/api/generated'
import { useLanguageGenerationSettings } from '@/composables/workbench/use-language-generation-settings'
import { useChat } from '@halo-dev/ai-foundation-sdk'
import { afterEach, beforeEach, describe, expect, it, rstest } from '@rstest/core'
import { computed, createApp, ref, shallowRef, type App } from 'vue'
import { useChatWorkbench } from './use-chat-workbench'
import { useRagTest } from './use-rag-test'

rstest.mock('@halo-dev/ai-foundation-sdk', () => ({
  DefaultChatTransport: class {
    constructor(_options: unknown) {}
  },
  lastAssistantMessageHasCompletedToolContinuations: rstest.fn(),
  useChat: rstest.fn(),
}))

interface MockChat {
  messages: ReturnType<typeof shallowRef<unknown[]>>
  error: ReturnType<typeof shallowRef<Error | undefined>>
  setMessages: ReturnType<typeof rstest.fn>
  sendMessage: ReturnType<typeof rstest.fn>
  regenerate: ReturnType<typeof rstest.fn>
  addToolOutput: ReturnType<typeof rstest.fn>
  addToolApprovalResponse: ReturnType<typeof rstest.fn>
  stop: ReturnType<typeof rstest.fn>
}

const apps: App[] = []
const chatInstances: MockChat[] = []

beforeEach(() => {
  rstest.clearAllMocks()
  chatInstances.length = 0
  rstest.mocked(useChat).mockImplementation(() => {
    const chat = createMockChat()
    chatInstances.push(chat)
    return chat as never
  })
})

afterEach(() => {
  apps.splice(0).forEach((app) => app.unmount())
})

describe('useChatWorkbench', () => {
  it('completes an assistant message and maps the request parameters', async () => {
    const { result: chat } = withSetup(() => createChatWorkbench())
    chat.input.value = ' Hello '

    await chat.sendMessage()

    expect(chat.messages.value).toHaveLength(2)
    expect(chat.messages.value[0]).toMatchObject({ role: 'user', content: 'Hello' })
    expect(chat.messages.value[1]).toMatchObject({
      role: 'assistant',
      modelName: 'language-model',
      state: 'done',
    })
    expect(chat.isStreaming.value).toBe(false)
    expect(chatInstances[0].sendMessage).toHaveBeenCalledWith(undefined, {
      body: expect.objectContaining({
        temperature: 0.7,
        topP: 1,
        maxOutputTokens: 1024,
        maxRetries: 2,
      }),
    })
  })

  it('marks an assistant message as failed when streaming rejects', async () => {
    const { result: chat } = withSetup(() => createChatWorkbench())
    chatInstances[0].sendMessage.mockRejectedValue(new Error('network down'))

    await chat.sendMessage('Hello')

    expect(chat.messages.value[1]).toMatchObject({
      role: 'assistant',
      content: '请求失败: network down',
      state: 'error',
    })
    expect(chat.isStreaming.value).toBe(false)
  })

  it('stops an active stream and preserves the stopped state after abort', async () => {
    const pending = deferred<void>()
    const { result: chat } = withSetup(() => createChatWorkbench())
    chatInstances[0].sendMessage.mockReturnValue(pending.promise)

    const sending = chat.sendMessage('Hello')
    expect(chat.isStreaming.value).toBe(true)

    chat.stopGeneration()
    pending.reject(abortError())
    await sending

    expect(chatInstances[0].stop).toHaveBeenCalled()
    expect(chat.messages.value[1]).toMatchObject({ state: 'stopped' })
    expect(chat.isStreaming.value).toBe(false)
  })
})

describe('useRagTest', () => {
  it('completes a RAG response and maps query, sources, and generation settings', async () => {
    const { result: rag } = withSetup(() => {
      const settings = useLanguageGenerationSettings()
      settings.chatHeadersText.value = '{"X-Trace":"trace-1"}'
      return useRagTest({
        selectedModel: computed(() => languageModel),
        settings,
      })
    })

    await rag.runRagTest()

    expect(rag.ragMessages.value).toHaveLength(2)
    expect(rag.ragMessages.value[1]).toMatchObject({
      role: 'assistant',
      modelName: 'language-model',
      state: 'done',
    })
    expect(rag.isRagTesting.value).toBe(false)
    expect(chatInstances[0].sendMessage).toHaveBeenCalledWith(undefined, {
      body: expect.objectContaining({
        query: 'AI Foundation 如何支持 RAG?',
        topN: 4,
        temperature: 0.7,
        maxOutputTokens: 1024,
        headers: { 'X-Trace': 'trace-1' },
        sources: expect.arrayContaining([
          expect.objectContaining({ id: 'source-1', title: 'AI Foundation' }),
        ]),
      }),
    })
  })

  it('marks the RAG assistant message as stopped after an abort', async () => {
    const { result: rag } = withSetup(() =>
      useRagTest({
        selectedModel: computed(() => languageModel),
        settings: useLanguageGenerationSettings(),
      }),
    )
    chatInstances[0].sendMessage.mockRejectedValue(abortError())

    await rag.runRagTest()

    expect(rag.ragMessages.value[1]).toMatchObject({ state: 'stopped' })
    expect(rag.ragError.value).toBe('')
    expect(rag.isRagTesting.value).toBe(false)
  })

  it('surfaces RAG streaming failures and clears the loading state', async () => {
    const { result: rag } = withSetup(() =>
      useRagTest({
        selectedModel: computed(() => languageModel),
        settings: useLanguageGenerationSettings(),
      }),
    )
    chatInstances[0].sendMessage.mockRejectedValue(new Error('network down'))

    await rag.runRagTest()

    expect(rag.ragError.value).toBe('请求失败: network down')
    expect(rag.ragMessages.value[1]).toMatchObject({
      content: '请求失败: network down',
      state: 'error',
    })
    expect(rag.isRagTesting.value).toBe(false)
  })
})

function createChatWorkbench() {
  return useChatWorkbench({
    selectedModel: computed(() => languageModel),
    activeModels: computed(() => [languageModel]),
    testMode: ref('chat'),
    settings: useLanguageGenerationSettings(),
  })
}

function createMockChat(): MockChat {
  const messages = shallowRef<unknown[]>([])
  return {
    messages,
    error: shallowRef<Error>(),
    setMessages: rstest.fn((next: unknown[]) => {
      messages.value = [...next]
    }),
    sendMessage: rstest.fn().mockResolvedValue(undefined),
    regenerate: rstest.fn().mockResolvedValue(undefined),
    addToolOutput: rstest.fn().mockResolvedValue(undefined),
    addToolApprovalResponse: rstest.fn().mockResolvedValue(undefined),
    stop: rstest.fn(),
  }
}

function withSetup<T>(factory: () => T) {
  let result!: T
  const app = createApp({
    setup() {
      result = factory()
      return () => null
    },
  })
  app.mount(document.createElement('div'))
  apps.push(app)
  return { result }
}

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function abortError() {
  const error = new Error('aborted')
  error.name = 'AbortError'
  return error
}

const languageModel: ModelOption = {
  name: 'language-model',
  modelId: 'provider-model-id',
  displayName: 'Language Model',
  modelType: ModelOptionModelTypeEnum.Language,
}
