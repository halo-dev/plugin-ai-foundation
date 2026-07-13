import type { ModelOption } from '@/api/generated'
import type { useLanguageGenerationSettings } from '@/composables/workbench/use-language-generation-settings'
import type { WorkbenchTestMode } from '@/composables/workbench/use-workbench-models'
import {
  applyWorkbenchUIMessageSnapshot,
  createAssistantUIMessage,
  createUserUIMessage,
  testUiMessageChatStreamUrl,
  workbenchDataPartSchemas,
  workbenchMessageMetadataSchema,
  type ExamplePrompt,
  type UIMessagePart,
  type WorkbenchMessage,
  type WorkbenchWarning,
} from '@/utils/model-test-workbench'
import {
  executeWorkbenchAgentTool,
  isWorkbenchAgentTool,
} from '@/utils/model-test-workbench-agent-tools'
import {
  haloMessageToWorkbench,
  workbenchFilesFromParts,
  workbenchMessagesToHalo,
} from '@/utils/model-test-workbench-messages'
import {
  DefaultChatTransport,
  lastAssistantMessageHasCompletedToolContinuations,
  useChat,
  type FilePart as HaloFilePart,
  type UIMessage as HaloUIMessage,
} from '@halo-dev/ai-foundation-sdk'
import { utils } from '@halo-dev/ui-shared'
import { onBeforeUnmount, ref, shallowRef, watch, type ComputedRef, type Ref } from 'vue'

interface UseChatWorkbenchOptions {
  selectedModel: ComputedRef<ModelOption | undefined>
  activeModels: ComputedRef<ModelOption[]>
  testMode: Ref<WorkbenchTestMode>
  settings: ReturnType<typeof useLanguageGenerationSettings>
  onConversationActivity?: () => void
}

export function useChatWorkbench(options: UseChatWorkbenchOptions) {
  const { selectedModel, activeModels, testMode, settings } = options
  const messages = ref<WorkbenchMessage[]>([])
  const input = shallowRef('')
  const chatFiles = shallowRef<HaloFilePart[]>([])
  const isStreaming = shallowRef(false)
  let activeModelName: string | undefined
  let activeWorkbenchId: string | undefined
  let activeParameters: ReturnType<typeof settings.buildParameters> | undefined

  const uiChat = useChat<Record<string, unknown>>({
    id: 'model-test-workbench-ui-message',
    transport: new DefaultChatTransport({
      api: '',
      prepareSendMessagesRequest: ({ body }) => {
        if (!activeModelName) throw new Error('未选择模型')
        return { api: testUiMessageChatStreamUrl(activeModelName, settings.streamOptions()), body }
      },
    }),
    generateId: () => activeWorkbenchId || utils.id.uuid(),
    sendAutomaticallyWhen: lastAssistantMessageHasCompletedToolContinuations,
    maxAutomaticSteps: 5,
    messageMetadataSchema: workbenchMessageMetadataSchema,
    dataPartSchemas: workbenchDataPartSchemas,
    onToolCall: (part) => {
      if (!settings.agentTestToolsEnabled.value || !isWorkbenchAgentTool(part)) return
      const context = {
        selectedModel: selectedModel.value,
        testMode: testMode.value,
        messages: messages.value,
      }
      void executeWorkbenchAgentTool(part, context)
        .then((output) =>
          uiChat.addToolOutput(
            { toolCallId: part.toolCallId, toolName: part.toolName, output },
            activeParameters ? { body: uiMessageRequestBody(activeParameters) } : undefined,
          ),
        )
        .catch((error) =>
          uiChat.addToolOutput(
            {
              toolCallId: part.toolCallId,
              toolName: part.toolName,
              state: 'output-error',
              errorText: error instanceof Error ? error.message : 'Agent 工具执行失败',
            },
            activeParameters ? { body: uiMessageRequestBody(activeParameters) } : undefined,
          ),
        )
    },
    onAutomaticStepLimitExceeded: () => {
      if (activeWorkbenchId) {
        appendAssistantWarnings(activeWorkbenchId, [
          {
            code: 'agent-auto-step-limit',
            message: 'Agent 工具自动续跑已达到上限，请继续手动发送消息或调整提示词。',
          },
        ])
      }
    },
    onFinish: ({ terminal }) => {
      if (activeWorkbenchId && terminal.errorText) {
        appendAssistantError(activeWorkbenchId, `请求失败: ${terminal.errorText}`)
      }
    },
  })

  watch(uiChat.messages, (uiMessages) => syncActiveSnapshot(uiMessages ?? []), { deep: true })

  async function sendMessage(content?: string) {
    const text = (content ?? input.value).trim()
    const files = [...chatFiles.value]
    const model = selectedModel.value
    if ((!text && !files.length) || !model?.name || isStreaming.value) return

    const parameters = settings.buildValidatedParameters()
    if (!parameters) return

    const uiMessageId = utils.id.uuid()
    messages.value.push({
      id: utils.id.uuid(),
      role: 'user',
      content: text,
      files: workbenchFilesFromParts(files),
      uiMessage: createUserUIMessage(uiMessageId, text, files as UIMessagePart[]),
    })
    input.value = ''
    chatFiles.value = []
    options.onConversationActivity?.()
    await streamResponse(model.name, parameters)
  }

  async function streamResponse(
    modelName: string,
    parameters: ReturnType<typeof settings.buildParameters>,
    targetMessage?: WorkbenchMessage,
  ) {
    const model = activeModels.value.find((item) => item.name === modelName)
    if (!model) return

    const assistantMessage = targetMessage || createAssistantMessage(model)
    assistantMessage.uiMessage = createAssistantUIMessage(
      assistantMessage.uiMessage?.id || assistantMessage.id,
    )
    resetAssistantMessage(assistantMessage)
    if (!targetMessage) messages.value.push(assistantMessage)

    activeModelName = modelName
    activeWorkbenchId = assistantMessage.id
    activeParameters = parameters
    uiChat.setMessages(
      workbenchMessagesToHalo(messages.value.filter((item) => item !== assistantMessage)),
    )
    isStreaming.value = true
    try {
      await uiChat.sendMessage(undefined, { body: uiMessageRequestBody(parameters) })
      if (uiChat.error.value) {
        appendAssistantError(assistantMessage.id, `请求失败: ${uiChat.error.value.message}`)
        return
      }
      finishAssistantMessage(assistantMessage.id, 'done')
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        appendAssistantError(assistantMessage.id, `请求失败: ${(error as Error).message}`)
      }
    } finally {
      resetActiveRequest()
    }
  }

  async function regenerate(messageIndex: number) {
    if (isStreaming.value) return
    let userMessageIndex = -1
    for (let index = messageIndex - 1; index >= 0; index--) {
      if (messages.value[index]?.role === 'user') {
        userMessageIndex = index
        break
      }
    }
    if (userMessageIndex === -1) return
    const model = selectedModel.value
    if (!model?.name) return
    const parameters = settings.buildValidatedParameters()
    if (!parameters) return
    const targetMessage = messages.value[messageIndex]
    const messageId = targetMessage?.uiMessage?.id
    if (!targetMessage || targetMessage.role !== 'assistant' || !messageId) return

    uiChat.setMessages(workbenchMessagesToHalo(messages.value))
    targetMessage.uiMessage = createAssistantUIMessage(messageId)
    resetAssistantMessage(targetMessage)
    activeModelName = model.name
    activeWorkbenchId = targetMessage.id
    activeParameters = parameters
    isStreaming.value = true
    try {
      await uiChat.regenerate({ messageId, body: uiMessageRequestBody(parameters) })
      if (uiChat.error.value) {
        appendAssistantError(targetMessage.id, `请求失败: ${uiChat.error.value.message}`)
        return
      }
      finishAssistantMessage(targetMessage.id, 'done')
    } catch (error) {
      if ((error as Error).name !== 'AbortError') {
        appendAssistantError(targetMessage.id, `请求失败: ${(error as Error).message}`)
      }
    } finally {
      resetActiveRequest()
    }
  }

  function resetAssistantMessage(message: WorkbenchMessage) {
    message.content = ''
    message.reasoningContent = undefined
    message.reasoningState = undefined
    message.toolEvents = undefined
    message.transientData = undefined
    message.warnings = undefined
    message.state = 'streaming'
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

  async function handleToolApproval(options: {
    messageId: string
    eventId: string
    approved: boolean
  }) {
    if (isStreaming.value) return
    const model = selectedModel.value
    if (!model?.name) return
    const message = messages.value.find((item) => item.id === options.messageId)
    const event = message?.toolEvents?.find((item) => item.id === options.eventId)
    if (
      !message ||
      !isLastAssistantMessage(message) ||
      !event ||
      event.type !== 'tool-approval-request' ||
      !event.approvalId ||
      event.approvalStatus !== 'pending'
    ) {
      return
    }
    const parameters = settings.buildValidatedParameters()
    if (!parameters) return
    activeModelName = model.name
    activeWorkbenchId = message.id
    activeParameters = parameters
    uiChat.setMessages(workbenchMessagesToHalo(messages.value))
    isStreaming.value = true
    try {
      await uiChat.addToolApprovalResponse(
        {
          id: event.approvalId,
          approved: options.approved,
          reason: options.approved
            ? 'Approved from console test page'
            : 'Denied from console test page',
        },
        { body: uiMessageRequestBody(parameters) },
      )
      if (uiChat.error.value) {
        appendAssistantError(message.id, `请求失败: ${uiChat.error.value.message}`)
        return
      }
      syncMessageFromUiChat(message)
      finishAssistantMessage(message.id, 'done')
    } catch (error) {
      appendAssistantError(message.id, `请求失败: ${(error as Error).message}`)
    } finally {
      resetActiveRequest()
    }
  }

  async function handleExternalToolResult(options: {
    messageId: string
    eventId: string
    resultText: string
  }) {
    const parsed = parseExternalToolResult(options.resultText)
    if (parsed.error) {
      appendAssistantWarnings(options.messageId, [
        { code: 'external-tool-result-invalid', message: parsed.error },
      ])
      return
    }
    await continueExternalTool(options.messageId, options.eventId, {
      type: 'tool-result',
      result: parsed.value,
    })
  }

  async function handleExternalToolError(options: {
    messageId: string
    eventId: string
    errorText: string
  }) {
    const errorText = options.errorText.trim()
    if (!errorText) {
      appendAssistantWarnings(options.messageId, [
        { code: 'external-tool-error-empty', message: '外部工具错误不能为空' },
      ])
      return
    }
    await continueExternalTool(options.messageId, options.eventId, {
      type: 'tool-error',
      errorText,
    })
  }

  async function continueExternalTool(
    messageId: string,
    eventId: string,
    payload: { type: 'tool-result'; result: unknown } | { type: 'tool-error'; errorText: string },
  ) {
    if (isStreaming.value) return
    const model = selectedModel.value
    if (!model?.name) return
    const message = messages.value.find((item) => item.id === messageId)
    const event = message?.toolEvents?.find((item) => item.id === eventId)
    if (
      !message ||
      !isLastAssistantMessage(message) ||
      !event ||
      event.type !== 'tool-call' ||
      !event.toolCallId ||
      !event.toolName ||
      event.externalStatus !== 'pending'
    ) {
      return
    }
    const parameters = settings.buildValidatedParameters()
    if (!parameters) return
    uiChat.setMessages(workbenchMessagesToHalo(messages.value))
    await uiChat.addToolOutput(
      payload.type === 'tool-result'
        ? { toolCallId: event.toolCallId, output: payload.result }
        : { toolCallId: event.toolCallId, state: 'output-error', errorText: payload.errorText },
    )
    syncMessageFromUiChat(message)
    const updatedEvent = message.toolEvents?.find((item) => item.id === eventId)
    if (updatedEvent) {
      updatedEvent.externalStatus = payload.type === 'tool-result' ? 'completed' : 'failed'
      message.toolEvents = [...(message.toolEvents || [])]
    }
    await streamResponse(model.name, parameters)
  }

  function syncActiveSnapshot(uiMessages: readonly unknown[]) {
    if (!activeWorkbenchId) return
    const target = messages.value.find((item) => item.id === activeWorkbenchId)
    const assistant = [...uiMessages]
      .reverse()
      .find((item): item is HaloUIMessage<Record<string, unknown>> => {
        return (item as HaloUIMessage<Record<string, unknown>> | undefined)?.role === 'assistant'
      })
    if (target && assistant) {
      applyWorkbenchUIMessageSnapshot(target, haloMessageToWorkbench(assistant), 'streaming')
    }
  }

  function syncMessageFromUiChat(message: WorkbenchMessage) {
    const uiMessageId = message.uiMessage?.id
    if (!uiMessageId) return
    const next = (uiChat.messages.value ?? []).find((item) => item.id === uiMessageId)
    if (next) {
      applyWorkbenchUIMessageSnapshot(
        message,
        haloMessageToWorkbench(next as unknown as HaloUIMessage<Record<string, unknown>>),
        message.state,
      )
    }
  }

  function isLastAssistantMessage(message: WorkbenchMessage) {
    return (
      [...messages.value].reverse().find((item) => item.role === 'assistant')?.id === message.id
    )
  }

  function appendAssistantWarnings(messageId: string, warnings: WorkbenchWarning[]) {
    const message = messages.value.find((item) => item.id === messageId)
    if (message) message.warnings = [...(message.warnings || []), ...warnings]
  }

  function appendAssistantError(messageId: string, content: string) {
    const message = messages.value.find((item) => item.id === messageId)
    if (!message) return
    const normalizedContent = content.trim()
    if (message.state === 'error' && message.content.includes(normalizedContent)) return
    message.content = message.content.trim()
      ? `${message.content.trim()}\n\n${normalizedContent}`
      : normalizedContent
    message.state = 'error'
    if (message.reasoningState === 'streaming') message.reasoningState = 'done'
  }

  function finishAssistantMessage(messageId: string, state: WorkbenchMessage['state']) {
    const message = messages.value.find((item) => item.id === messageId)
    if (message?.state !== 'streaming') return
    message.state = state
    if (message.reasoningState === 'streaming') message.reasoningState = 'done'
  }

  function parseExternalToolResult(value: string): { value?: unknown; error?: string } {
    const content = value.trim()
    if (!content) return { error: '外部工具结果不能为空' }
    try {
      return { value: JSON.parse(content) }
    } catch {
      return { error: '外部工具结果必须是有效 JSON' }
    }
  }

  function uiMessageRequestBody(parameters: ReturnType<typeof settings.buildParameters>) {
    return {
      system: parameters.systemPrompt?.trim() || undefined,
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
      reasoning: parameters.reasoning,
      headers: parameters.headers,
      output: parameters.output,
    }
  }

  function resetActiveRequest() {
    isStreaming.value = false
    activeModelName = undefined
    activeWorkbenchId = undefined
    activeParameters = undefined
  }

  function stopGeneration() {
    uiChat.stop()
    const streamingMessage = [...messages.value]
      .reverse()
      .find((item) => item.state === 'streaming')
    if (streamingMessage) {
      streamingMessage.state = 'stopped'
      if (streamingMessage.reasoningState === 'streaming') streamingMessage.reasoningState = 'done'
    }
    resetActiveRequest()
  }

  function clearMessages() {
    if (isStreaming.value) stopGeneration()
    messages.value = []
    chatFiles.value = []
    uiChat.setMessages([])
    options.onConversationActivity?.()
  }

  function applyExamplePrompt(prompt: ExamplePrompt) {
    input.value = prompt.content
    if (prompt.id !== 'agent-tool-test') return
    settings.testToolEnabled.value = false
    settings.testToolApprovalEnabled.value = false
    settings.externalTestToolEnabled.value = false
    settings.agentTestToolsEnabled.value = true
    settings.toolCallRepairEnabled.value = false
  }

  onBeforeUnmount(() => uiChat.stop())

  return {
    messages,
    input,
    chatFiles,
    isStreaming,
    sendMessage,
    regenerate,
    handleToolApproval,
    handleExternalToolResult,
    handleExternalToolError,
    stopGeneration,
    clearMessages,
    applyExamplePrompt,
  }
}
