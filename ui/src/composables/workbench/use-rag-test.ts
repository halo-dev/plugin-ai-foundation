import type { ModelOption, TestRagSource } from '@/api/generated'
import type { useLanguageGenerationSettings } from '@/composables/workbench/use-language-generation-settings'
import {
  applyWorkbenchUIMessageSnapshot,
  createAssistantUIMessage,
  createUserUIMessage,
  testRagUiMessageStreamUrl,
  workbenchDataPartSchemas,
  workbenchMessageMetadataSchema,
  type WorkbenchMessage,
} from '@/utils/model-test-workbench'
import {
  applyAssistantMessageEvent,
  beginAssistantMessage,
} from '@/utils/model-test-workbench-message-state'
import {
  cloneWorkbenchMessage,
  haloMessageToWorkbench,
  workbenchMessagesToHalo,
} from '@/utils/model-test-workbench-messages'
import { numberOrUndefined } from '@/utils/model-test-workbench-request'
import {
  DefaultChatTransport,
  useChat,
  type UIMessage as HaloUIMessage,
} from '@halo-dev/ai-foundation-sdk'
import { utils } from '@halo-dev/ui-shared'
import { onBeforeUnmount, ref, shallowRef, watch, type ComputedRef } from 'vue'

interface UseRagTestOptions {
  selectedModel: ComputedRef<ModelOption | undefined>
  settings: ReturnType<typeof useLanguageGenerationSettings>
}

const initialSources = (): TestRagSource[] => [
  {
    id: 'source-1',
    title: 'AI Foundation',
    content: 'AI Foundation 提供统一的语言模型、嵌入、Rerank 和 UI Message 能力。',
    score: 0.86,
    visible: true,
    usedForContext: true,
  },
  {
    id: 'source-2',
    title: 'RAG Runtime',
    content: 'RAG 流程通常包含检索、可选重排、上下文注入、来源展示和诊断事件。',
    score: 0.78,
    visible: true,
    usedForContext: true,
  },
]

export function useRagTest({ selectedModel, settings }: UseRagTestOptions) {
  const ragQuery = shallowRef('AI Foundation 如何支持 RAG?')
  const ragSources = ref<TestRagSource[]>(initialSources())
  const ragRerankModelName = shallowRef<string | undefined>()
  const ragTopN = shallowRef<number | undefined>(4)
  const ragMessages = ref<WorkbenchMessage[]>([])
  const ragError = shallowRef('')
  const isRagTesting = shallowRef(false)
  let activeRequestId: string | undefined
  let activeModelName: string | undefined
  let activeWorkbenchId: string | undefined

  const ragUiChat = useChat<Record<string, unknown>>({
    id: 'model-test-workbench-rag-ui-message',
    transport: new DefaultChatTransport({
      api: '',
      prepareSendMessagesRequest: ({ body }) => {
        if (!activeModelName) throw new Error('未选择模型')
        const ragBody = { ...body }
        delete ragBody.id
        delete ragBody.messages
        delete ragBody.trigger
        delete ragBody.messageId
        return { api: testRagUiMessageStreamUrl(activeModelName), body: ragBody }
      },
    }),
    generateId: () => activeWorkbenchId || utils.id.uuid(),
    messageMetadataSchema: workbenchMessageMetadataSchema,
    dataPartSchemas: workbenchDataPartSchemas,
    onFinish: ({ message, terminal }) => {
      if (!terminal.errorText) return
      const target = ragMessages.value.find((item) => item.id === message.id)
      if (!target || (target.requestId && target.requestId !== activeRequestId)) return
      appendAssistantError(target.id, terminal.errorText, target.requestId)
    },
  })

  watch(ragUiChat.messages, (uiMessages) => syncActiveSnapshot(uiMessages ?? []), { deep: true })

  async function runRagTest() {
    const model = selectedModel.value
    if (!model?.name || isRagTesting.value) return

    const sources = ragSources.value
      .map((source, index) => ({
        ...source,
        id: source.id?.trim() || `source-${index + 1}`,
        title: source.title?.trim() || undefined,
        url: source.url?.trim() || undefined,
        content: source.content?.trim(),
      }))
      .filter((source) => !!source.content)
    if (!ragQuery.value.trim()) {
      ragError.value = '请输入 Query'
      return
    }
    if (!sources.length) {
      ragError.value = '请至少填写一个来源内容'
      return
    }
    ragError.value = ''
    const parameters = settings.buildValidatedParameters({ tools: false, output: false })
    if (!parameters) return
    ragMessages.value = [
      {
        id: utils.id.uuid(),
        role: 'user',
        content: ragQuery.value.trim(),
        uiMessage: createUserUIMessage(utils.id.uuid(), ragQuery.value.trim()),
      },
    ]
    const assistantMessage = createAssistantMessage(model)
    assistantMessage.uiMessage = createAssistantUIMessage(assistantMessage.id)
    ragMessages.value.push(assistantMessage)
    const assistantMessageId = assistantMessage.id
    const requestBody = {
      query: ragQuery.value.trim(),
      system: parameters.systemPrompt.trim() || undefined,
      sources,
      rerankModelName: ragRerankModelName.value,
      topN: numberOrUndefined(ragTopN.value),
      temperature: parameters.temperature,
      topP: parameters.topP,
      topK: parameters.topK,
      minP: parameters.minP,
      presencePenalty: parameters.presencePenalty,
      frequencyPenalty: parameters.frequencyPenalty,
      repetitionPenalty: parameters.repetitionPenalty,
      stopSequences: parameters.stopSequences,
      logprobs: parameters.logprobs,
      topLogprobs: parameters.topLogprobs,
      parallelToolCalls: parameters.parallelToolCalls,
      maxOutputTokens: parameters.maxOutputTokens,
      seed: parameters.seed,
      maxRetries: parameters.maxRetries,
      headers: parameters.headers,
      reasoning: parameters.reasoning,
      ragOptions: {
        emptyContextPolicy: 'CONTINUE_WITHOUT_CONTEXT',
        rerankFailurePolicy: 'USE_RETRIEVED_ORDER',
      },
    }
    const requestId = utils.id.uuid()
    beginAssistantMessage(assistantMessage, requestId)
    isRagTesting.value = true
    activeRequestId = requestId
    activeModelName = model.name
    activeWorkbenchId = assistantMessageId
    ragUiChat.setMessages(
      workbenchMessagesToHalo(ragMessages.value.filter((item) => item.id !== assistantMessageId)),
    )
    try {
      await ragUiChat.sendMessage(undefined, { body: requestBody })
      if (ragUiChat.error.value) {
        appendAssistantError(assistantMessageId, ragUiChat.error.value.message, requestId)
        return
      }
      finishAssistantMessage(assistantMessageId, requestId)
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        stopAssistantMessage(assistantMessageId, requestId)
        return
      }
      ragError.value = `请求失败: ${(error as Error).message}`
      appendAssistantError(assistantMessageId, ragError.value, requestId)
    } finally {
      if (activeRequestId === requestId) {
        isRagTesting.value = false
        activeRequestId = undefined
        activeModelName = undefined
        activeWorkbenchId = undefined
      }
    }
  }

  function createAssistantMessage(model: ModelOption): WorkbenchMessage {
    return {
      id: utils.id.uuid(),
      role: 'assistant',
      content: '',
      modelName: model.name,
      modelDisplayName: model.displayName || model.modelId || model.name,
      state: 'streaming',
    }
  }

  function syncActiveSnapshot(uiMessages: readonly unknown[]) {
    if (!activeWorkbenchId) return
    const index = ragMessages.value.findIndex((item) => item.id === activeWorkbenchId)
    if (index >= 0 && ragMessages.value[index]?.requestId !== activeRequestId) return
    const assistant = [...uiMessages]
      .reverse()
      .find((item): item is HaloUIMessage<Record<string, unknown>> => {
        return (item as HaloUIMessage<Record<string, unknown>> | undefined)?.role === 'assistant'
      })
    if (index < 0 || !assistant) return
    const message = cloneWorkbenchMessage(ragMessages.value[index])
    applyWorkbenchUIMessageSnapshot(message, haloMessageToWorkbench(assistant), 'streaming')
    ragMessages.value.splice(index, 1, message)
  }

  function updateAssistantMessage(messageId: string, updater: (message: WorkbenchMessage) => void) {
    const index = ragMessages.value.findIndex((item) => item.id === messageId)
    if (index < 0) return
    const message = cloneWorkbenchMessage(ragMessages.value[index])
    updater(message)
    ragMessages.value.splice(index, 1, message)
  }

  function finishAssistantMessage(messageId: string, requestId: string) {
    updateAssistantMessage(messageId, (message) => {
      if (message.requestId !== requestId) return
      applyAssistantMessageEvent(message, { type: 'finish' })
    })
  }

  function stopAssistantMessage(messageId: string, requestId: string) {
    updateAssistantMessage(messageId, (message) => {
      if (message.requestId !== requestId) return
      applyAssistantMessageEvent(message, { type: 'stop' })
    })
  }

  function appendAssistantError(messageId: string, content: string, requestId?: string) {
    updateAssistantMessage(messageId, (message) => {
      if (requestId && message.requestId !== requestId) return
      applyAssistantMessageEvent(message, { type: 'fail', content })
    })
  }

  function stopRagTest() {
    const requestId = activeRequestId
    ragUiChat.stop()
    if (activeWorkbenchId && requestId) {
      stopAssistantMessage(activeWorkbenchId, requestId)
    }
    if (activeRequestId === requestId) {
      isRagTesting.value = false
      activeRequestId = undefined
      activeModelName = undefined
      activeWorkbenchId = undefined
    }
  }

  function clearRagMessages() {
    if (isRagTesting.value) stopRagTest()
    ragMessages.value = []
    ragError.value = ''
  }

  onBeforeUnmount(stopRagTest)

  return {
    ragQuery,
    ragSources,
    ragRerankModelName,
    ragTopN,
    ragMessages,
    ragError,
    isRagTesting,
    runRagTest,
    clearRagMessages,
    stopRagTest,
  }
}
