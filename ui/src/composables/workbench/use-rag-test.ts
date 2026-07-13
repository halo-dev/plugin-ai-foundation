import type { ModelOption, TestRagSource } from '@/api/generated'
import type { useLanguageGenerationSettings } from '@/composables/workbench/use-language-generation-settings'
import {
  applyWorkbenchUIMessageSnapshot,
  buildReasoningOptions,
  createAssistantUIMessage,
  createUserUIMessage,
  testRagUiMessageStreamUrl,
  workbenchDataPartSchemas,
  workbenchMessageMetadataSchema,
  type WorkbenchMessage,
} from '@/utils/model-test-workbench'
import {
  cloneWorkbenchMessage,
  haloMessageToWorkbench,
  workbenchMessagesToHalo,
} from '@/utils/model-test-workbench-messages'
import { numberOrUndefined, parseStringMapJson } from '@/utils/model-test-workbench-request'
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
    onFinish: ({ terminal }) => {
      if (activeWorkbenchId && terminal.errorText) {
        appendAssistantError(activeWorkbenchId, terminal.errorText)
      }
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
    const headers = parseStringMapJson(settings.chatHeadersText.value)
    settings.chatHeadersError.value = headers.error || ''
    if (headers.error) return
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
    const stopSequences = settings.stopSequencesText.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean)
    const requestBody = {
      query: ragQuery.value.trim(),
      system: settings.systemPrompt.value.trim() || undefined,
      sources,
      rerankModelName: ragRerankModelName.value,
      topN: numberOrUndefined(ragTopN.value),
      temperature: numberOrUndefined(settings.temperature.value),
      topP: numberOrUndefined(settings.topP.value),
      topK: numberOrUndefined(settings.topK.value),
      minP: numberOrUndefined(settings.minP.value),
      presencePenalty: numberOrUndefined(settings.presencePenalty.value),
      frequencyPenalty: numberOrUndefined(settings.frequencyPenalty.value),
      repetitionPenalty: numberOrUndefined(settings.repetitionPenalty.value),
      stopSequences: stopSequences.length ? stopSequences : undefined,
      logprobs: settings.logprobs.value,
      topLogprobs: numberOrUndefined(settings.topLogprobs.value),
      parallelToolCalls: settings.parallelToolCalls.value,
      maxOutputTokens: numberOrUndefined(settings.maxTokens.value),
      seed: numberOrUndefined(settings.seed.value),
      maxRetries: numberOrUndefined(settings.maxRetries.value),
      headers: headers.value,
      reasoning: buildReasoningOptions({
        mode: settings.reasoningMode.value,
        effort: settings.reasoningEffort.value,
      }),
      ragOptions: {
        emptyContextPolicy: 'CONTINUE_WITHOUT_CONTEXT',
        rerankFailurePolicy: 'USE_RETRIEVED_ORDER',
      },
    }
    isRagTesting.value = true
    activeModelName = model.name
    activeWorkbenchId = assistantMessageId
    ragUiChat.setMessages(
      workbenchMessagesToHalo(ragMessages.value.filter((item) => item.id !== assistantMessageId)),
    )
    try {
      await ragUiChat.sendMessage(undefined, { body: requestBody })
      if (ragUiChat.error.value) {
        appendAssistantError(assistantMessageId, ragUiChat.error.value.message)
        return
      }
      finishAssistantMessage(assistantMessageId, 'done')
    } catch (error) {
      if ((error as Error).name === 'AbortError') {
        finishAssistantMessage(assistantMessageId, 'stopped')
        return
      }
      ragError.value = `请求失败: ${(error as Error).message}`
      appendAssistantError(assistantMessageId, ragError.value)
    } finally {
      isRagTesting.value = false
      activeModelName = undefined
      activeWorkbenchId = undefined
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

  function finishAssistantMessage(messageId: string, state: WorkbenchMessage['state']) {
    updateAssistantMessage(messageId, (message) => {
      if (message.state !== 'streaming') return
      message.state = state
      if (message.reasoningState === 'streaming') message.reasoningState = 'done'
    })
  }

  function appendAssistantError(messageId: string, content: string) {
    updateAssistantMessage(messageId, (message) => {
      const normalizedContent = content.trim()
      if (message.state === 'error' && message.content.includes(normalizedContent)) return
      message.content = message.content.trim()
        ? `${message.content.trim()}\n\n${normalizedContent}`
        : normalizedContent
      message.state = 'error'
      if (message.reasoningState === 'streaming') message.reasoningState = 'done'
    })
  }

  function stopRagTest() {
    ragUiChat.stop()
    isRagTesting.value = false
    activeModelName = undefined
    activeWorkbenchId = undefined
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
