<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import {
  ModelOptionModelTypeEnum,
  TestImageGenerationRequestResponseFormatEnum,
  type TestEmbeddingResponse,
  type TestImageGenerationResponse,
  type TestMediaContent,
  type TestRagSource,
  type TestRerankResponse,
} from '@/api/generated'
import type { Tab } from '@/components/SegmentedTabs.vue'
import SegmentedTabs from '@/components/SegmentedTabs.vue'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import AiModelSelector from '@/formkit/AiModelSelector.vue'
import {
  applyWorkbenchUIMessageSnapshot,
  buildOutputSpec,
  buildReasoningOptions,
  createAssistantUIMessage,
  createUserUIMessage,
  testRagUiMessageStreamUrl,
  testUiMessageChatStreamUrl,
  workbenchDataPartSchemas,
  workbenchMessageMetadataSchema,
  type ExamplePrompt,
  type OutputMode,
  type ReasoningEffort,
  type ReasoningMode,
  type UIMessagePart,
  type WorkbenchFileReference,
  type WorkbenchMessage,
  type WorkbenchWarning,
} from '@/utils/model-test-workbench'
import {
  DefaultChatTransport,
  lastAssistantMessageHasCompletedToolContinuations,
  useChat,
  type FilePart as HaloFilePart,
  type UIMessage as HaloUIMessage,
  type UIMessagePart as HaloUIMessagePart,
  type ToolPart,
} from '@halo-dev/ai-foundation-sdk'
import { IconRefreshLine, VButton, VEmpty, VLoading } from '@halo-dev/components'
import { utils } from '@halo-dev/ui-shared'
import { useRouteQuery } from '@vueuse/router'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import MingcuteDelete2Line from '~icons/mingcute/delete-2-line'
import RiImageLine from '~icons/ri/image-line'
import RiMessage3Line from '~icons/ri/message-3-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiStackLine from '~icons/ri/stack-line'
import ChatInputArea from './components/workbench/ChatInputArea.vue'
import ChatMessageItem from './components/workbench/ChatMessageItem.vue'
import EmbeddingTestPanel from './components/workbench/EmbeddingTestPanel.vue'
import ExamplePrompts from './components/workbench/ExamplePrompts.vue'
import ImageGenerationTestPanel from './components/workbench/ImageGenerationTestPanel.vue'
import ParameterSidebar from './components/workbench/ParameterSidebar.vue'
import RagTestPanel from './components/workbench/RagTestPanel.vue'
import RerankTestPanel from './components/workbench/RerankTestPanel.vue'

const modelType = shallowRef<string | undefined>()
const availableOnly = shallowRef<boolean | undefined>(true)
const {
  data: modelOptions,
  isLoading,
  isFetching,
  refetch,
} = useModelOptionsFetch({
  modelType,
  available: availableOnly,
})

const selectedModelName = useRouteQuery<string | undefined>('model')
type TestMode = 'chat' | 'embedding' | 'rerank' | 'image' | 'rag'

const testModeTabs: Tab[] = [
  { label: '对话', value: 'chat', icon: RiMessage3Line },
  { label: '嵌入', value: 'embedding', icon: RiStackLine },
  { label: 'Rerank', value: 'rerank', icon: RiStackLine },
  { label: '图片', value: 'image', icon: RiImageLine },
  { label: 'RAG', value: 'rag', icon: RiStackLine },
]
const testMode = shallowRef<TestMode>('chat')

const messages = ref<WorkbenchMessage[]>([])
const input = shallowRef('')
const chatFiles = shallowRef<HaloFilePart[]>([])
const systemPrompt = shallowRef('')
const temperature = shallowRef(0.7)
const topP = shallowRef(1)
const topK = shallowRef<number | undefined>()
const minP = shallowRef<number | undefined>()
const presencePenalty = shallowRef<number | undefined>()
const frequencyPenalty = shallowRef<number | undefined>()
const repetitionPenalty = shallowRef<number | undefined>()
const stopSequencesText = shallowRef('')
const logprobs = shallowRef<boolean | undefined>()
const topLogprobs = shallowRef<number | undefined>()
const parallelToolCalls = shallowRef<boolean | undefined>()
const maxTokens = shallowRef(1024)
const seed = shallowRef<number | undefined>()
const maxRetries = shallowRef<number | undefined>(2)
const reasoningMode = shallowRef<ReasoningMode>('DEFAULT')
const reasoningEffort = shallowRef<ReasoningEffort>('MEDIUM')
const testToolEnabled = shallowRef(false)
const testToolApprovalEnabled = shallowRef(false)
const externalTestToolEnabled = shallowRef(false)
const agentTestToolsEnabled = shallowRef(false)
const toolCallRepairEnabled = shallowRef(false)
const outputMode = shallowRef<OutputMode>('TEXT')
const outputSchemaText = shallowRef(`{
  "type": "object",
  "properties": {
    "title": {
      "type": "string"
    },
    "summary": {
      "type": "string"
    }
  },
  "required": ["title", "summary"]
}`)
const outputChoicesText = shallowRef('yes\nno')
const outputError = shallowRef('')
const chatHeadersText = shallowRef('{}')
const chatHeadersError = shallowRef('')
const isStreaming = shallowRef(false)
const conversationRef = ref<HTMLElement | null>(null)
const chatInputRef = ref<InstanceType<typeof ChatInputArea> | null>(null)
const shouldAutoScroll = shallowRef(true)

const embeddingInputs = shallowRef('Halo 是一个开源建站工具\nAI Foundation 提供统一 AI 能力')
const embeddingDimensions = shallowRef<number | undefined>()
const embeddingMaxBatchSize = shallowRef<number | undefined>(1)
const embeddingMaxParallelCalls = shallowRef<number | undefined>(2)
const embeddingMaxRetries = shallowRef<number | undefined>(1)
const embeddingResult = shallowRef<TestEmbeddingResponse | undefined>()
const embeddingError = shallowRef('')
const isEmbeddingTesting = shallowRef(false)

const rerankQuery = shallowRef('Halo AI Foundation 如何支持 RAG?')
const rerankDocuments = shallowRef(
  'AI Foundation 提供统一的语言模型、嵌入和 UI Message 能力\nHalo 是一个开源建站工具\nRAG 通常需要检索、上下文注入和来源展示',
)
const rerankTopN = shallowRef<number | undefined>()
const rerankResult = shallowRef<TestRerankResponse | undefined>()
const rerankError = shallowRef('')
const isRerankTesting = shallowRef(false)

const imagePrompt = shallowRef('一张简洁清晰的 Halo 控制台界面截图风格插图，浅色背景，细节真实')
const imageNegativePrompt = shallowRef('')
const imageInputUrl = shallowRef('')
const imageInputData = shallowRef('')
const imageInputMediaType = shallowRef('image/png')
const imageMaskUrl = shallowRef('')
const imageMaskData = shallowRef('')
const imageMaskMediaType = shallowRef('image/png')
const imageN = shallowRef<number | undefined>(1)
const imageWidth = shallowRef<number | undefined>(1024)
const imageHeight = shallowRef<number | undefined>(1024)
const imageAspectRatio = shallowRef('')
const imageSeed = shallowRef<number | undefined>()
const imageResponseFormat = shallowRef<'DEFAULT' | TestImageGenerationRequestResponseFormatEnum>(
  'DEFAULT',
)
const imageMaxRetries = shallowRef<number | undefined>(1)
const imageMaxParallelCalls = shallowRef<number | undefined>(1)
const imageHeadersText = shallowRef('{}')
const imageHeadersError = shallowRef('')
const imageResult = shallowRef<TestImageGenerationResponse | undefined>()
const imageError = shallowRef('')
const isImageTesting = shallowRef(false)

const ragQuery = shallowRef('AI Foundation 如何支持 RAG?')
const ragSources = ref<TestRagSource[]>([
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
])
const ragRerankModelName = shallowRef<string | undefined>()
const ragTopN = shallowRef<number | undefined>(4)
const ragMessages = ref<WorkbenchMessage[]>([])
const ragError = shallowRef('')
const isRagTesting = shallowRef(false)

let activeUiMessageModelName: string | undefined
let activeUiMessageWorkbenchId: string | undefined
let activeUiMessageParameters: ReturnType<typeof buildChatParameters> | undefined
let activeRagModelName: string | undefined
let activeRagWorkbenchId: string | undefined

const uiChat = useChat<Record<string, unknown>>({
  id: 'model-test-workbench-ui-message',
  transport: new DefaultChatTransport({
    api: '',
    prepareSendMessagesRequest: ({ body }) => {
      if (!activeUiMessageModelName) {
        throw new Error('未选择模型')
      }
      return {
        api: testUiMessageChatStreamUrl(activeUiMessageModelName, chatStreamOptions()),
        body,
      }
    },
  }),
  generateId: () => activeUiMessageWorkbenchId || utils.id.uuid(),
  sendAutomaticallyWhen: lastAssistantMessageHasCompletedToolContinuations,
  maxAutomaticSteps: 5,
  messageMetadataSchema: workbenchMessageMetadataSchema,
  dataPartSchemas: workbenchDataPartSchemas,
  onToolCall: (part) => {
    if (!agentTestToolsEnabled.value || !isWorkbenchAgentTool(part)) {
      return
    }
    void executeWorkbenchAgentTool(part)
      .then((output) =>
        uiChat.addToolOutput(
          {
            toolCallId: part.toolCallId,
            toolName: part.toolName,
            output,
          },
          activeUiMessageParameters
            ? { body: uiMessageRequestBody(activeUiMessageParameters) }
            : undefined,
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
          activeUiMessageParameters
            ? { body: uiMessageRequestBody(activeUiMessageParameters) }
            : undefined,
        ),
      )
  },
  onAutomaticStepLimitExceeded: () => {
    if (activeUiMessageWorkbenchId) {
      appendAssistantWarnings(activeUiMessageWorkbenchId, [
        {
          code: 'agent-auto-step-limit',
          message: 'Agent 工具自动续跑已达到上限，请继续手动发送消息或调整提示词。',
        },
      ])
    }
  },
  onFinish: ({ terminal }) => {
    if (activeUiMessageWorkbenchId && terminal.errorText) {
      appendAssistantError(activeUiMessageWorkbenchId, `请求失败: ${terminal.errorText}`)
    }
  },
})

const ragUiChat = useChat<Record<string, unknown>>({
  id: 'model-test-workbench-rag-ui-message',
  transport: new DefaultChatTransport({
    api: '',
    prepareSendMessagesRequest: ({ body }) => {
      if (!activeRagModelName) {
        throw new Error('未选择模型')
      }
      const ragBody = { ...body }
      delete ragBody.id
      delete ragBody.messages
      delete ragBody.trigger
      delete ragBody.messageId
      return {
        api: testRagUiMessageStreamUrl(activeRagModelName),
        body: ragBody,
      }
    },
  }),
  generateId: () => activeRagWorkbenchId || utils.id.uuid(),
  messageMetadataSchema: workbenchMessageMetadataSchema,
  dataPartSchemas: workbenchDataPartSchemas,
  onFinish: ({ terminal }) => {
    if (activeRagWorkbenchId && terminal.errorText) {
      appendRagAssistantError(activeRagWorkbenchId, terminal.errorText)
    }
  },
})

const chatModels = computed(() => {
  return (modelOptions.value || []).filter((model) => {
    return model.name && model.modelType === ModelOptionModelTypeEnum.Language
  })
})
const embeddingModels = computed(() => {
  return (modelOptions.value || []).filter((model) => {
    return model.name && model.modelType === ModelOptionModelTypeEnum.Embedding
  })
})
const rerankModels = computed(() => {
  return (modelOptions.value || []).filter((model) => {
    return model.name && model.modelType === ModelOptionModelTypeEnum.Rerank
  })
})
const imageModels = computed(() => {
  return (modelOptions.value || []).filter((model) => {
    return model.name && model.modelType === ModelOptionModelTypeEnum.ImageGeneration
  })
})
const activeModels = computed(() =>
  testMode.value === 'embedding'
    ? embeddingModels.value
    : testMode.value === 'rerank'
      ? rerankModels.value
      : testMode.value === 'image'
        ? imageModels.value
        : chatModels.value,
)
const activeModelType = computed(() =>
  testMode.value === 'embedding'
    ? ModelOptionModelTypeEnum.Embedding
    : testMode.value === 'rerank'
      ? ModelOptionModelTypeEnum.Rerank
      : testMode.value === 'image'
        ? ModelOptionModelTypeEnum.ImageGeneration
        : ModelOptionModelTypeEnum.Language,
)
const isAnyTesting = computed(
  () =>
    isStreaming.value ||
    isEmbeddingTesting.value ||
    isRerankTesting.value ||
    isImageTesting.value ||
    isRagTesting.value,
)

const selectedModel = computed(() => {
  return activeModels.value.find((model) => model.name === selectedModelName.value)
})

watch(
  modelOptions,
  (items) => {
    const models = items || []
    const selected = models.find((item) => item.name === selectedModelName.value)
    if (selected?.modelType === ModelOptionModelTypeEnum.Embedding) {
      testMode.value = 'embedding'
    } else if (selected?.modelType === ModelOptionModelTypeEnum.Rerank) {
      testMode.value = 'rerank'
    } else if (selected?.modelType === ModelOptionModelTypeEnum.ImageGeneration) {
      testMode.value = 'image'
    } else if (selected?.modelType === ModelOptionModelTypeEnum.Language) {
      if (testMode.value !== 'rag') {
        testMode.value = 'chat'
      }
    }
    const candidates = activeModels.value
    if (!candidates.length) {
      selectedModelName.value = undefined
      return
    }
    if (!candidates.some((item) => item.name === selectedModelName.value)) {
      selectedModelName.value = candidates[0].name
    }
  },
  { immediate: true },
)

watch(testMode, () => {
  const candidates = activeModels.value
  selectedModelName.value = candidates[0]?.name
})

watch(
  messages,
  async () => {
    await nextTick()
    scrollConversationToBottomIfNeeded()
  },
  { deep: true },
)

watch(
  uiChat.messages,
  (uiMessages) => {
    syncActiveUiMessageSnapshot(uiMessages ?? [])
  },
  { deep: true },
)

watch(
  ragUiChat.messages,
  (uiMessages) => {
    syncActiveRagUiMessageSnapshot(uiMessages ?? [])
  },
  { deep: true },
)

async function sendMessage(content?: string) {
  const text = (content ?? input.value).trim()
  const files = [...chatFiles.value]
  const model = selectedModel.value
  if ((!text && !files.length) || !model?.name || isStreaming.value) {
    return
  }

  const headers = parseStringMapJson(chatHeadersText.value)
  if (headers.error) {
    chatHeadersError.value = headers.error
    return
  }
  chatHeadersError.value = ''
  const outputSpec = buildOutputSpec({
    mode: outputMode.value,
    schemaText: outputSchemaText.value,
    choicesText: outputChoicesText.value,
  })
  if (outputSpec.error) {
    outputError.value = outputSpec.error
    return
  }
  outputError.value = ''

  const uiMessageId = utils.id.uuid()
  const userMessage: WorkbenchMessage = {
    id: utils.id.uuid(),
    role: 'user',
    content: text,
    files: workbenchFilesFromParts(files),
    uiMessage: createUserUIMessage(uiMessageId, text, files as UIMessagePart[]),
  }
  messages.value.push(userMessage)
  input.value = ''
  chatFiles.value = []
  shouldAutoScroll.value = true

  const parameters = buildChatParameters(headers.value, outputSpec.value)
  await streamUiMessageChatResponse(model.name, parameters)
}

async function streamUiMessageChatResponse(
  modelName: string,
  parameters: ReturnType<typeof buildChatParameters>,
  targetMessage?: WorkbenchMessage,
) {
  const model = activeModels.value.find((m) => m.name === modelName)
  if (!model) return

  const assistantMessage = targetMessage || createAssistantMessage(model)
  assistantMessage.uiMessage = createAssistantUIMessage(
    assistantMessage.uiMessage?.id || assistantMessage.id,
  )
  assistantMessage.content = ''
  assistantMessage.reasoningContent = undefined
  assistantMessage.reasoningState = undefined
  assistantMessage.toolEvents = undefined
  assistantMessage.transientData = undefined
  assistantMessage.warnings = undefined
  assistantMessage.state = 'streaming'
  if (!targetMessage) {
    messages.value.push(assistantMessage)
  }

  activeUiMessageModelName = modelName
  activeUiMessageWorkbenchId = assistantMessage.id
  activeUiMessageParameters = parameters
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
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      return
    }
    appendAssistantError(assistantMessage.id, `请求失败: ${(e as Error).message}`)
  } finally {
    isStreaming.value = false
    activeUiMessageModelName = undefined
    activeUiMessageWorkbenchId = undefined
    activeUiMessageParameters = undefined
  }
}

async function regenerateUiMessageChatResponse(
  targetMessage: WorkbenchMessage,
  modelName: string,
  parameters: ReturnType<typeof buildChatParameters>,
  messageId: string,
) {
  const model = activeModels.value.find((m) => m.name === modelName)
  if (!model) return

  uiChat.setMessages(workbenchMessagesToHalo(messages.value))
  targetMessage.uiMessage = createAssistantUIMessage(messageId)
  targetMessage.content = ''
  targetMessage.reasoningContent = undefined
  targetMessage.reasoningState = undefined
  targetMessage.toolEvents = undefined
  targetMessage.transientData = undefined
  targetMessage.warnings = undefined
  targetMessage.state = 'streaming'

  activeUiMessageModelName = modelName
  activeUiMessageWorkbenchId = targetMessage.id
  activeUiMessageParameters = parameters
  isStreaming.value = true

  try {
    await uiChat.regenerate({ messageId, body: uiMessageRequestBody(parameters) })
    if (uiChat.error.value) {
      appendAssistantError(targetMessage.id, `请求失败: ${uiChat.error.value.message}`)
      return
    }
    finishAssistantMessage(targetMessage.id, 'done')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      return
    }
    appendAssistantError(targetMessage.id, `请求失败: ${(e as Error).message}`)
  } finally {
    isStreaming.value = false
    activeUiMessageModelName = undefined
    activeUiMessageWorkbenchId = undefined
    activeUiMessageParameters = undefined
  }
}

function createAssistantMessage(model: {
  name?: string
  displayName?: string
  modelId?: string
}): WorkbenchMessage {
  return {
    id: utils.id.uuid(),
    role: 'assistant',
    content: '',
    modelName: model.name,
    modelDisplayName: model.displayName || model.modelId || model.name,
    state: 'streaming',
  }
}

async function handleRegenerate(messageIndex: number) {
  if (isStreaming.value) return

  let userMessageIndex = -1
  for (let i = messageIndex - 1; i >= 0; i--) {
    if (messages.value[i]?.role === 'user') {
      userMessageIndex = i
      break
    }
  }
  if (userMessageIndex === -1) return

  const model = selectedModel.value
  if (!model?.name) return

  const headers = parseStringMapJson(chatHeadersText.value)
  if (headers.error) {
    chatHeadersError.value = headers.error
    return
  }
  chatHeadersError.value = ''
  const outputSpec = buildOutputSpec({
    mode: outputMode.value,
    schemaText: outputSchemaText.value,
    choicesText: outputChoicesText.value,
  })
  if (outputSpec.error) {
    outputError.value = outputSpec.error
    return
  }
  outputError.value = ''

  const parameters = buildChatParameters(headers.value, outputSpec.value)

  const targetMessage = messages.value[messageIndex]
  const messageId = targetMessage?.uiMessage?.id
  if (!targetMessage || targetMessage.role !== 'assistant' || !messageId) {
    return
  }
  await regenerateUiMessageChatResponse(targetMessage, model.name, parameters, messageId)
}

function chatStreamOptions() {
  return {
    testToolEnabled: testToolEnabled.value,
    testToolApprovalEnabled: testToolApprovalEnabled.value,
    externalTestToolEnabled: externalTestToolEnabled.value,
    agentTestToolsEnabled: agentTestToolsEnabled.value,
    toolCallRepairEnabled: toolCallRepairEnabled.value,
  }
}

async function handleToolApproval(options: {
  messageId: string
  eventId: string
  approved: boolean
}) {
  if (isStreaming.value) {
    return
  }
  const model = selectedModel.value
  if (!model?.name) {
    return
  }
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
  const parameters = buildValidatedChatParameters()
  if (!parameters) {
    return
  }
  activeUiMessageModelName = model.name
  activeUiMessageWorkbenchId = message.id
  activeUiMessageParameters = parameters
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
    syncWorkbenchMessageFromUiChat(message)
    finishAssistantMessage(message.id, 'done')
  } catch (e) {
    appendAssistantError(message.id, `请求失败: ${(e as Error).message}`)
  } finally {
    isStreaming.value = false
    activeUiMessageModelName = undefined
    activeUiMessageWorkbenchId = undefined
    activeUiMessageParameters = undefined
  }
}

function isWorkbenchAgentTool(part: ToolPart) {
  return part.toolName === 'get_current_page_context' || part.toolName === 'halo_agent_test_action'
}

async function executeWorkbenchAgentTool(part: ToolPart): Promise<Record<string, unknown>> {
  switch (part.toolName) {
    case 'get_current_page_context':
      return currentWorkbenchPageContext()
    case 'halo_agent_test_action':
      return executeWorkbenchAgentTestAction(part.input ?? {})
    default:
      throw new Error(`未知 Agent 测试工具：${part.toolName}`)
  }
}

function currentWorkbenchPageContext(): Record<string, unknown> {
  const selected = selectedModel.value
  const lastUserMessage = [...messages.value].reverse().find((message) => message.role === 'user')
  return {
    ok: true,
    url: window.location.href,
    title: document.title,
    selectedText: window.getSelection()?.toString() || '',
    channel: 'console-model-test-workbench',
    page: {
      name: 'AI Foundation 模型测试工作台',
      mode: testMode.value,
      capabilities: [
        'model-selection',
        'chat-stream-test',
        'rag-stream-test',
        'image-generation-test',
      ],
    },
    model: selected
      ? {
          name: selected.name,
          displayName: selected.displayName,
          modelId: selected.modelId,
          provider: selected.provider?.displayName || selected.provider?.name,
        }
      : undefined,
    conversation: {
      messages: messages.value.length,
      lastUserText: lastUserMessage?.content || '',
    },
    nextAction:
      '如果需要验证客户端工具续跑，可以调用 halo_agent_test_action；否则直接根据当前上下文回答用户。',
  }
}

function executeWorkbenchAgentTestAction(input: Record<string, unknown>): Record<string, unknown> {
  const message = typeof input.message === 'string' ? input.message.trim() : ''
  if (!message) {
    return {
      ok: false,
      code: 'EMPTY_AGENT_TEST_MESSAGE',
      message: '缺少需要回显的测试内容',
    }
  }
  return {
    ok: true,
    echo: message,
    target: 'console-model-test-workbench',
    message: '后台测试工作台前端 Agent 工具执行成功。',
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
  if (isStreaming.value) {
    return
  }
  const model = selectedModel.value
  if (!model?.name) {
    return
  }
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
  const parameters = buildValidatedChatParameters()
  if (!parameters) {
    return
  }
  uiChat.setMessages(workbenchMessagesToHalo(messages.value))
  await uiChat.addToolOutput(
    payload.type === 'tool-result'
      ? { toolCallId: event.toolCallId, output: payload.result }
      : { toolCallId: event.toolCallId, state: 'output-error', errorText: payload.errorText },
  )
  syncWorkbenchMessageFromUiChat(message)
  const updatedEvent = message.toolEvents?.find((item) => item.id === eventId)
  if (updatedEvent) {
    updatedEvent.externalStatus = payload.type === 'tool-result' ? 'completed' : 'failed'
    message.toolEvents = [...(message.toolEvents || [])]
  }
  await streamUiMessageChatResponse(model.name, parameters)
}

function isLastAssistantMessage(message: WorkbenchMessage) {
  return [...messages.value].reverse().find((item) => item.role === 'assistant')?.id === message.id
}

function syncActiveUiMessageSnapshot(uiMessages: readonly unknown[]) {
  if (!activeUiMessageWorkbenchId) {
    return
  }
  const target = messages.value.find((item) => item.id === activeUiMessageWorkbenchId)
  const assistant = [...uiMessages]
    .reverse()
    .find((item): item is HaloUIMessage<Record<string, unknown>> => {
      return (item as HaloUIMessage<Record<string, unknown>> | undefined)?.role === 'assistant'
    })
  if (!target || !assistant) {
    return
  }
  applyWorkbenchUIMessageSnapshot(target, haloMessageToWorkbench(assistant), 'streaming')
}

function syncActiveRagUiMessageSnapshot(uiMessages: readonly unknown[]) {
  if (!activeRagWorkbenchId) {
    return
  }
  const index = ragMessages.value.findIndex((item) => item.id === activeRagWorkbenchId)
  const assistant = [...uiMessages]
    .reverse()
    .find((item): item is HaloUIMessage<Record<string, unknown>> => {
      return (item as HaloUIMessage<Record<string, unknown>> | undefined)?.role === 'assistant'
    })
  if (index < 0 || !assistant) {
    return
  }
  const message = cloneWorkbenchMessage(ragMessages.value[index])
  applyWorkbenchUIMessageSnapshot(message, haloMessageToWorkbench(assistant), 'streaming')
  ragMessages.value.splice(index, 1, message)
}

function syncWorkbenchMessageFromUiChat(message: WorkbenchMessage) {
  const uiMessageId = message.uiMessage?.id
  if (!uiMessageId) {
    return
  }
  const next = (uiChat.messages.value ?? []).find((item) => item.id === uiMessageId)
  if (next) {
    applyWorkbenchUIMessageSnapshot(
      message,
      haloMessageToWorkbench(next as unknown as HaloUIMessage<Record<string, unknown>>),
      message.state,
    )
  }
}

function workbenchMessagesToHalo(
  items: WorkbenchMessage[],
): HaloUIMessage<Record<string, unknown>>[] {
  return items
    .map(workbenchMessageToHalo)
    .filter((message): message is HaloUIMessage<Record<string, unknown>> => !!message)
}

function workbenchMessageToHalo(
  message: WorkbenchMessage,
): HaloUIMessage<Record<string, unknown>> | undefined {
  if (message.uiMessage?.parts.length) {
    return {
      id: message.uiMessage.id,
      role: message.uiMessage.role === 'ASSISTANT' ? 'assistant' : 'user',
      parts: message.uiMessage.parts.map(workbenchPartToHalo) as HaloUIMessagePart[],
      metadata: message.uiMessage.metadata,
    }
  }
  const content = message.content.trim()
  if (!content || (message.role === 'assistant' && message.state === 'error')) {
    return undefined
  }
  return {
    id: message.id,
    role: message.role,
    parts: [{ type: 'text', id: `${message.id}-text`, text: content }],
  }
}

function workbenchPartToHalo(part: UIMessagePart): HaloUIMessagePart {
  if (part.type === 'file') {
    const id = String(part.id || part.fileId || utils.id.uuid())
    return { ...part, id, fileId: String(part.fileId || id) } as HaloUIMessagePart
  }
  return { ...part } as HaloUIMessagePart
}

function haloMessageToWorkbench(
  message: HaloUIMessage<Record<string, unknown>>,
): NonNullable<WorkbenchMessage['uiMessage']> {
  return {
    id: message.id,
    role: message.role === 'assistant' ? 'ASSISTANT' : 'USER',
    parts: message.parts.map(haloPartToWorkbench),
    metadata: message.metadata,
  }
}

function haloPartToWorkbench(part: HaloUIMessagePart): UIMessagePart {
  if (part.type === 'file') {
    return { ...part, fileId: part.fileId || part.id } as UIMessagePart
  }
  return { ...part } as UIMessagePart
}

function workbenchFilesFromParts(files: HaloFilePart[]): WorkbenchFileReference[] {
  return files.map((file) => ({
    id: file.id,
    fileId: file.fileId || file.id,
    title: file.title,
    url: file.url,
    mediaType: file.mediaType,
    data: file.data,
    providerMetadata: file.providerMetadata,
  }))
}

function uiMessageRequestBody(parameters: ReturnType<typeof buildChatParameters>) {
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

function buildValidatedChatParameters(): ReturnType<typeof buildChatParameters> | undefined {
  const headers = parseStringMapJson(chatHeadersText.value)
  if (headers.error) {
    chatHeadersError.value = headers.error
    return undefined
  }
  chatHeadersError.value = ''
  const outputSpec = buildOutputSpec({
    mode: outputMode.value,
    schemaText: outputSchemaText.value,
    choicesText: outputChoicesText.value,
  })
  if (outputSpec.error) {
    outputError.value = outputSpec.error
    return undefined
  }
  outputError.value = ''
  return buildChatParameters(headers.value, outputSpec.value)
}

function parseExternalToolResult(input: string): { value?: unknown; error?: string } {
  const content = input.trim()
  if (!content) {
    return { error: '外部工具结果不能为空' }
  }
  try {
    return { value: JSON.parse(content) }
  } catch {
    return { error: '外部工具结果必须是有效 JSON' }
  }
}

function buildChatParameters(
  headers: Record<string, string> | undefined,
  output: ReturnType<typeof buildOutputSpec>['value'],
) {
  const stopSequences = stopSequencesText.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  return {
    systemPrompt: systemPrompt.value,
    temperature: numberOrUndefined(temperature.value),
    topP: numberOrUndefined(topP.value),
    topK: numberOrUndefined(topK.value),
    minP: numberOrUndefined(minP.value),
    presencePenalty: numberOrUndefined(presencePenalty.value),
    frequencyPenalty: numberOrUndefined(frequencyPenalty.value),
    repetitionPenalty: numberOrUndefined(repetitionPenalty.value),
    stopSequences: stopSequences.length ? stopSequences : undefined,
    logprobs: logprobs.value,
    topLogprobs: numberOrUndefined(topLogprobs.value),
    parallelToolCalls: parallelToolCalls.value,
    maxOutputTokens: numberOrUndefined(maxTokens.value),
    seed: numberOrUndefined(seed.value),
    maxRetries: numberOrUndefined(maxRetries.value),
    reasoning: buildReasoningOptions({
      mode: reasoningMode.value,
      effort: reasoningEffort.value,
    }),
    headers,
    output,
  }
}

function appendAssistantWarnings(messageId: string, warnings: WorkbenchWarning[]) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.warnings = [...(message.warnings || []), ...warnings]
  }
}

function appendAssistantError(messageId: string, content: string) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    const normalizedContent = content.trim()
    if (message.state === 'error' && message.content.includes(normalizedContent)) {
      return
    }
    message.content = message.content.trim()
      ? `${message.content.trim()}\n\n${normalizedContent}`
      : normalizedContent
    message.state = 'error'
    if (message.reasoningState === 'streaming') {
      message.reasoningState = 'done'
    }
  }
}

function finishAssistantMessage(messageId: string, state: WorkbenchMessage['state']) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message && message.state === 'streaming') {
    message.state = state
    if (message.reasoningState === 'streaming') {
      message.reasoningState = 'done'
    }
  }
}

function finishRagAssistantMessage(messageId: string, state: WorkbenchMessage['state']) {
  updateRagAssistantMessage(messageId, (message) => {
    if (message.state === 'streaming') {
      message.state = state
      if (message.reasoningState === 'streaming') {
        message.reasoningState = 'done'
      }
    }
  })
}

function appendRagAssistantError(messageId: string, content: string) {
  updateRagAssistantMessage(messageId, (message) => {
    const normalizedContent = content.trim()
    if (message.state === 'error' && message.content.includes(normalizedContent)) {
      return
    }
    message.content = message.content.trim()
      ? `${message.content.trim()}\n\n${normalizedContent}`
      : normalizedContent
    message.state = 'error'
    if (message.reasoningState === 'streaming') {
      message.reasoningState = 'done'
    }
  })
}

function updateRagAssistantMessage(
  messageId: string,
  updater: (message: WorkbenchMessage) => void,
) {
  const index = ragMessages.value.findIndex((item) => item.id === messageId)
  if (index < 0) {
    return
  }
  const message = cloneWorkbenchMessage(ragMessages.value[index])
  updater(message)
  ragMessages.value.splice(index, 1, message)
}

function cloneWorkbenchMessage(message: WorkbenchMessage): WorkbenchMessage {
  return {
    ...message,
    uiMessage: message.uiMessage
      ? {
          ...message.uiMessage,
          metadata: message.uiMessage.metadata ? { ...message.uiMessage.metadata } : undefined,
          parts: message.uiMessage.parts.map((part) => ({ ...part })),
        }
      : undefined,
    toolEvents: message.toolEvents?.map((event) => ({ ...event })),
    warnings: message.warnings?.map((warning) => ({ ...warning })),
    files: message.files?.map((file) => ({ ...file })),
    transientData: message.transientData ? { ...message.transientData } : undefined,
  }
}

function stopGeneration() {
  uiChat.stop()
  ragUiChat.stop()
  const streamingMessage = [...messages.value].reverse().find((item) => item.state === 'streaming')
  if (streamingMessage) {
    streamingMessage.state = 'stopped'
    if (streamingMessage.reasoningState === 'streaming') {
      streamingMessage.reasoningState = 'done'
    }
  }
  isStreaming.value = false
  activeUiMessageModelName = undefined
  activeUiMessageWorkbenchId = undefined
  activeUiMessageParameters = undefined
  activeRagModelName = undefined
  activeRagWorkbenchId = undefined
}

function clearMessages() {
  if (isStreaming.value) {
    stopGeneration()
  }
  messages.value = []
  chatFiles.value = []
  uiChat.setMessages([])
  shouldAutoScroll.value = true
}

function clearRagMessages() {
  if (isRagTesting.value) {
    ragUiChat.stop()
  }
  ragMessages.value = []
  ragError.value = ''
}

watch(testToolApprovalEnabled, (enabled) => {
  if (enabled && !testToolEnabled.value) {
    testToolEnabled.value = true
  }
})

watch(testToolEnabled, (enabled) => {
  if (!enabled) {
    testToolApprovalEnabled.value = false
  }
})

function handleExampleSelect(prompt: ExamplePrompt) {
  input.value = prompt.content
  if (prompt.id === 'agent-tool-test') {
    testToolEnabled.value = false
    testToolApprovalEnabled.value = false
    externalTestToolEnabled.value = false
    agentTestToolsEnabled.value = true
    toolCallRepairEnabled.value = false
  }
  chatInputRef.value?.focus()
}

function handleEmbeddingKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && (e.metaKey || e.ctrlKey)) {
    e.preventDefault()
    if (embeddingInputs.value.trim() && selectedModel.value && !isEmbeddingTesting.value) {
      runEmbeddingTest()
    }
  }
}

function handleConversationScroll() {
  const el = conversationRef.value
  if (!el) return
  shouldAutoScroll.value = distanceToConversationBottom(el) < 48
}

function scrollConversationToBottomIfNeeded() {
  const el = conversationRef.value
  if (!el || !shouldAutoScroll.value) return
  el.scrollTop = el.scrollHeight
}

function distanceToConversationBottom(el: HTMLElement) {
  return el.scrollHeight - el.scrollTop - el.clientHeight
}

function numberOrUndefined(value: number | undefined) {
  return Number.isFinite(value) ? value : undefined
}

function parseStringMapJson(input: string): {
  value?: Record<string, string>
  error?: string
} {
  const content = input.trim()
  if (!content) {
    return {}
  }
  try {
    const parsed = JSON.parse(content)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return { error: 'Headers 必须是 JSON 对象' }
    }
    const result: Record<string, string> = {}
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      result[key] = String(value)
    }
    return { value: result }
  } catch {
    return { error: 'Headers 不是有效的 JSON' }
  }
}

function buildTestMediaContent(options: {
  url: string
  data: string
  mediaType: string
  label: string
}): { value?: TestMediaContent; error?: string } {
  const url = options.url.trim()
  const data = options.data.trim()
  if (url && data) {
    return { error: `${options.label} 只能填写 URL 或 base64 其中一种` }
  }
  if (url) {
    return { value: { url, mediaType: options.mediaType.trim() || undefined } }
  }
  if (data) {
    return { value: { data, mediaType: options.mediaType.trim() || 'image/png' } }
  }
  return {}
}

async function runImageGenerationTest() {
  const model = selectedModel.value
  if (!model?.name || isImageTesting.value) {
    return
  }
  if (!imagePrompt.value.trim()) {
    imageError.value = '请输入 Prompt'
    return
  }
  const image = buildTestMediaContent({
    url: imageInputUrl.value,
    data: imageInputData.value,
    mediaType: imageInputMediaType.value,
    label: '参考图',
  })
  if (image.error) {
    imageError.value = image.error
    return
  }
  const mask = buildTestMediaContent({
    url: imageMaskUrl.value,
    data: imageMaskData.value,
    mediaType: imageMaskMediaType.value,
    label: 'Mask',
  })
  if (mask.error) {
    imageError.value = mask.error
    return
  }
  const headers = parseStringMapJson(imageHeadersText.value)
  if (headers.error) {
    imageHeadersError.value = headers.error
    return
  }
  imageHeadersError.value = ''
  imageError.value = ''
  imageResult.value = undefined
  isImageTesting.value = true
  try {
    const { data } = await aiConsoleApiClient.model.testModelImageGeneration({
      name: model.name,
      testImageGenerationRequest: {
        prompt: imagePrompt.value.trim(),
        negativePrompt: imageNegativePrompt.value.trim() || undefined,
        images: image.value ? [image.value] : undefined,
        mask: mask.value,
        n: numberOrUndefined(imageN.value),
        width: numberOrUndefined(imageWidth.value),
        height: numberOrUndefined(imageHeight.value),
        aspectRatio: imageAspectRatio.value.trim() || undefined,
        seed: numberOrUndefined(imageSeed.value),
        responseFormat:
          imageResponseFormat.value === 'DEFAULT' ? undefined : imageResponseFormat.value,
        maxRetries: numberOrUndefined(imageMaxRetries.value),
        maxParallelCalls: numberOrUndefined(imageMaxParallelCalls.value),
        headers: headers.value,
      },
    })
    imageResult.value = data
  } catch (e) {
    imageError.value = `请求失败: ${(e as Error).message}`
  } finally {
    isImageTesting.value = false
  }
}

async function runEmbeddingTest() {
  const model = selectedModel.value
  if (!model?.name || isEmbeddingTesting.value) {
    return
  }
  const inputs = embeddingInputs.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  if (!inputs.length) {
    embeddingError.value = '请至少输入一行文本'
    return
  }
  embeddingError.value = ''
  embeddingResult.value = undefined
  isEmbeddingTesting.value = true
  try {
    const { data } = await aiConsoleApiClient.model.testModelEmbedding({
      name: model.name,
      testEmbeddingRequest: {
        inputs,
        dimensions: numberOrUndefined(embeddingDimensions.value),
        maxBatchSize: numberOrUndefined(embeddingMaxBatchSize.value),
        maxParallelCalls: numberOrUndefined(embeddingMaxParallelCalls.value),
        maxRetries: numberOrUndefined(embeddingMaxRetries.value),
      },
    })
    embeddingResult.value = data
  } catch (e) {
    embeddingError.value = `请求失败: ${(e as Error).message}`
  } finally {
    isEmbeddingTesting.value = false
  }
}

async function runRerankTest() {
  const model = selectedModel.value
  if (!model?.name || isRerankTesting.value) {
    return
  }
  const documents = rerankDocuments.value
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean)
  if (!rerankQuery.value.trim()) {
    rerankError.value = '请输入 Query'
    return
  }
  if (!documents.length) {
    rerankError.value = '请至少输入一个候选文档'
    return
  }
  rerankError.value = ''
  rerankResult.value = undefined
  isRerankTesting.value = true
  try {
    const { data } = await aiConsoleApiClient.model.testModelRerank({
      name: model.name,
      testRerankRequest: {
        query: rerankQuery.value,
        documents,
        topN: numberOrUndefined(rerankTopN.value),
      },
    })
    rerankResult.value = data
  } catch (e) {
    rerankError.value = `请求失败: ${(e as Error).message}`
  } finally {
    isRerankTesting.value = false
  }
}

async function runRagTest() {
  const model = selectedModel.value
  if (!model?.name || isRagTesting.value) {
    return
  }
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
  ragMessages.value = [
    {
      id: utils.id.uuid(),
      role: 'user',
      content: ragQuery.value.trim(),
      uiMessage: createUserUIMessage(utils.id.uuid(), ragQuery.value.trim()),
    },
  ]
  const assistantMessage: WorkbenchMessage = createAssistantMessage(model)
  assistantMessage.uiMessage = createAssistantUIMessage(assistantMessage.id)
  ragMessages.value.push(assistantMessage)
  const assistantMessageId = assistantMessage.id
  const requestBody = {
    query: ragQuery.value.trim(),
    system: systemPrompt.value.trim() || undefined,
    sources,
    rerankModelName: ragRerankModelName.value,
    topN: numberOrUndefined(ragTopN.value),
    temperature: numberOrUndefined(temperature.value),
    topP: numberOrUndefined(topP.value),
    topK: numberOrUndefined(topK.value),
    minP: numberOrUndefined(minP.value),
    presencePenalty: numberOrUndefined(presencePenalty.value),
    frequencyPenalty: numberOrUndefined(frequencyPenalty.value),
    repetitionPenalty: numberOrUndefined(repetitionPenalty.value),
    stopSequences: stopSequencesText.value
      .split('\n')
      .map((item) => item.trim())
      .filter(Boolean),
    logprobs: logprobs.value,
    topLogprobs: numberOrUndefined(topLogprobs.value),
    parallelToolCalls: parallelToolCalls.value,
    maxOutputTokens: numberOrUndefined(maxTokens.value),
    seed: numberOrUndefined(seed.value),
    maxRetries: numberOrUndefined(maxRetries.value),
    reasoning: buildReasoningOptions({
      mode: reasoningMode.value,
      effort: reasoningEffort.value,
    }),
    ragOptions: {
      emptyContextPolicy: 'CONTINUE_WITHOUT_CONTEXT',
      rerankFailurePolicy: 'USE_RETRIEVED_ORDER',
    },
  }
  isRagTesting.value = true
  activeRagModelName = model.name
  activeRagWorkbenchId = assistantMessageId
  ragUiChat.setMessages(workbenchMessagesToHalo(ragMessages.value.filter((item) => item.id !== assistantMessageId)))
  try {
    await ragUiChat.sendMessage(undefined, { body: requestBody })
    if (ragUiChat.error.value) {
      appendRagAssistantError(assistantMessageId, ragUiChat.error.value.message)
      return
    }
    finishRagAssistantMessage(assistantMessageId, 'done')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      finishRagAssistantMessage(assistantMessageId, 'stopped')
      return
    }
    ragError.value = `请求失败: ${(e as Error).message}`
    appendRagAssistantError(assistantMessageId, ragError.value)
  } finally {
    isRagTesting.value = false
    activeRagModelName = undefined
    activeRagWorkbenchId = undefined
  }
}

onBeforeUnmount(() => {
  uiChat.stop()
  ragUiChat.stop()
})
</script>

<template>
  <div class=":uno: h-[calc(100vh-7.5rem)] min-h-[34rem] bg-[#eef3f7] p-2">
    <VLoading v-if="isLoading" />

    <VEmpty
      v-else-if="
        !chatModels.length && !embeddingModels.length && !rerankModels.length && !imageModels.length
      "
      title="暂无可测试的模型"
      message="你可以在配置选项卡中添加或启用支持对话、嵌入、Rerank 或图片生成能力的模型"
    >
      <template #actions>
        <VButton :loading="isFetching" @click="refetch()">刷新</VButton>
      </template>
    </VEmpty>

    <div
      v-else
      class=":uno: grid grid-cols-1 h-full min-h-0 overflow-hidden border border-slate-200/80 rounded-lg bg-white lg:grid-cols-[minmax(0,1fr)_23rem]"
    >
      <section class=":uno: min-h-0 min-w-0 flex flex-col bg-[#f8fafc]">
        <header class=":uno: border-b border-slate-200/80 bg-white px-4 py-3">
          <div class=":uno: flex flex-col gap-3 xl:flex-row xl:items-center">
            <div
              class=":uno: min-w-0 w-full flex flex-col gap-2 sm:flex-row sm:flex-wrap sm:items-center"
            >
              <SegmentedTabs
                :model-value="testMode"
                :tabs="testModeTabs"
                :disabled="isAnyTesting"
                compact
                aria-label="模型测试模式"
                @update:model-value="testMode = $event as TestMode"
              />

              <AiModelSelector
                v-model="selectedModelName"
                name="model"
                :model-type="activeModelType"
                :available="availableOnly"
                :disabled="isAnyTesting"
                placeholder="选择测试模型"
                search-placeholder="搜索模型..."
                full-width
                class=":uno: min-w-[13rem] flex-1 !py-0"
              />

              <div class=":uno: flex flex-none items-center gap-1">
                <button
                  type="button"
                  class=":uno: group size-9 inline-flex items-center justify-center border border-slate-200 rounded-lg bg-white text-slate-500 shadow-sm transition-colors hover:border-slate-300 hover:bg-slate-50 hover:text-slate-900"
                  v-tooltip="`刷新模型`"
                  @click="refetch()"
                >
                  <IconRefreshLine
                    class=":uno: size-3.5"
                    :class="{ ':uno: animate-spin': isFetching }"
                  />
                </button>
                <button
                  type="button"
                  class=":uno: group size-9 inline-flex items-center justify-center border border-slate-200 rounded-lg bg-white text-slate-500 shadow-sm transition-colors hover:border-rose-200 hover:bg-rose-50 hover:text-rose-600"
                  v-tooltip="`清空会话`"
                  @click="clearMessages"
                >
                  <MingcuteDelete2Line class=":uno: size-3.5" />
                </button>
              </div>
            </div>
          </div>
        </header>

        <template v-if="testMode === 'chat'">
          <div
            ref="conversationRef"
            class=":uno: min-h-0 flex-1 overflow-y-auto bg-[#f8fafc] px-4 py-5"
            @scroll.passive="handleConversationScroll"
          >
            <ExamplePrompts v-if="!messages.length" @select="handleExampleSelect" />

            <div v-else class=":uno: mx-auto max-w-4xl space-y-5">
              <ChatMessageItem
                v-for="(message, index) in messages"
                :key="message.id"
                :message="message"
                :index="index"
                @regenerate="handleRegenerate"
                @tool-approval="handleToolApproval"
                @external-tool-result="handleExternalToolResult"
                @external-tool-error="handleExternalToolError"
              />
            </div>
          </div>

          <ChatInputArea
            ref="chatInputRef"
            v-model="input"
            v-model:files="chatFiles"
            :is-streaming="isStreaming"
            :disabled="!selectedModel"
            @send="sendMessage()"
            @stop="stopGeneration"
          />
        </template>

        <template v-else-if="testMode === 'embedding'">
          <EmbeddingTestPanel
            :inputs="embeddingInputs"
            :result="embeddingResult"
            :error="embeddingError"
            :is-loading="isEmbeddingTesting"
            :disabled="!selectedModel"
            @update:inputs="embeddingInputs = $event"
            @run="runEmbeddingTest"
          />

          <div class=":uno: border-t border-slate-200 bg-white/95 px-4 py-3">
            <div class=":uno: mx-auto max-w-4xl">
              <div
                class=":uno: relative flex-1 border border-slate-200 rounded-lg bg-slate-50 shadow-inner transition-colors focus-within:border-teal-400 focus-within:bg-white focus-within:ring-3 focus-within:ring-teal-500/10"
              >
                <textarea
                  v-model="embeddingInputs"
                  rows="3"
                  placeholder="每行一段需要向量化的文本... (Cmd/Ctrl + Enter 发送)"
                  class=":uno: min-h-20 w-[calc(100%-4.5rem)] resize-none text-sm text-slate-900 leading-relaxed outline-none !border-none !bg-transparent !px-4 !py-3 placeholder:text-slate-400"
                  :disabled="!selectedModel || isEmbeddingTesting"
                  @keydown="handleEmbeddingKeydown"
                />
                <div
                  class=":uno: pointer-events-none absolute bottom-3 right-14 text-[11px] text-slate-400"
                >
                  {{ embeddingInputs.length }}
                </div>
                <VButton
                  type="primary"
                  class=":uno: absolute bottom-2 right-2 h-8 w-8 shadow-sm !rounded-md !p-0"
                  :loading="isEmbeddingTesting"
                  :disabled="!embeddingInputs.trim() || !selectedModel"
                  @click="runEmbeddingTest"
                >
                  <RiSendPlaneLine class=":uno: size-4" />
                </VButton>
              </div>
            </div>
          </div>
        </template>

        <template v-else-if="testMode === 'rerank'">
          <RerankTestPanel
            :query="rerankQuery"
            :documents="rerankDocuments"
            :top-n="rerankTopN"
            :result="rerankResult"
            :error="rerankError"
            :is-loading="isRerankTesting"
            :disabled="!selectedModel"
            @update:query="rerankQuery = $event"
            @update:documents="rerankDocuments = $event"
            @update:top-n="rerankTopN = $event"
            @run="runRerankTest"
          />
        </template>

        <template v-else-if="testMode === 'image'">
          <ImageGenerationTestPanel
            :prompt="imagePrompt"
            :negative-prompt="imageNegativePrompt"
            :input-url="imageInputUrl"
            :input-data="imageInputData"
            :input-media-type="imageInputMediaType"
            :mask-url="imageMaskUrl"
            :mask-data="imageMaskData"
            :mask-media-type="imageMaskMediaType"
            :result="imageResult"
            :error="imageError"
            :is-loading="isImageTesting"
            :disabled="!selectedModel"
            @update:prompt="imagePrompt = $event"
            @update:negative-prompt="imageNegativePrompt = $event"
            @update:input-url="imageInputUrl = $event"
            @update:input-data="imageInputData = $event"
            @update:input-media-type="imageInputMediaType = $event"
            @update:mask-url="imageMaskUrl = $event"
            @update:mask-data="imageMaskData = $event"
            @update:mask-media-type="imageMaskMediaType = $event"
            @run="runImageGenerationTest"
          />
        </template>

        <template v-else-if="testMode === 'rag'">
          <RagTestPanel
            :query="ragQuery"
            :sources="ragSources"
            :rerank-model-name="ragRerankModelName"
            :rerank-models="rerankModels"
            :top-n="ragTopN"
            :messages="ragMessages"
            :error="ragError"
            :is-loading="isRagTesting"
            :disabled="!selectedModel"
            @update:query="ragQuery = $event"
            @update:sources="ragSources = $event"
            @update:rerank-model-name="ragRerankModelName = $event"
            @update:top-n="ragTopN = $event"
            @run="runRagTest"
            @clear="clearRagMessages"
          />
        </template>
      </section>

      <ParameterSidebar
        :mode="testMode === 'rag' ? 'chat' : testMode"
        :system-prompt="systemPrompt"
        :temperature="temperature"
        :top-p="topP"
        :top-k="topK"
        :min-p="minP"
        :presence-penalty="presencePenalty"
        :frequency-penalty="frequencyPenalty"
        :repetition-penalty="repetitionPenalty"
        :stop-sequences-text="stopSequencesText"
        :logprobs="logprobs"
        :top-logprobs="topLogprobs"
        :parallel-tool-calls="parallelToolCalls"
        :max-tokens="maxTokens"
        :seed="seed"
        :max-retries="maxRetries"
        :reasoning-mode="reasoningMode"
        :reasoning-effort="reasoningEffort"
        :test-tool-enabled="testToolEnabled"
        :test-tool-approval-enabled="testToolApprovalEnabled"
        :external-test-tool-enabled="externalTestToolEnabled"
        :agent-test-tools-enabled="agentTestToolsEnabled"
        :tool-call-repair-enabled="toolCallRepairEnabled"
        :output-mode="outputMode"
        :output-schema-text="outputSchemaText"
        :output-choices-text="outputChoicesText"
        :chat-headers-text="chatHeadersText"
        :chat-headers-error="chatHeadersError"
        :output-error="outputError"
        :embedding-dimensions="embeddingDimensions"
        :embedding-max-batch-size="embeddingMaxBatchSize"
        :embedding-max-parallel-calls="embeddingMaxParallelCalls"
        :embedding-max-retries="embeddingMaxRetries"
        :image-n="imageN"
        :image-width="imageWidth"
        :image-height="imageHeight"
        :image-aspect-ratio="imageAspectRatio"
        :image-seed="imageSeed"
        :image-response-format="imageResponseFormat"
        :image-max-retries="imageMaxRetries"
        :image-max-parallel-calls="imageMaxParallelCalls"
        :image-headers-text="imageHeadersText"
        :image-headers-error="imageHeadersError"
        @update:system-prompt="systemPrompt = $event"
        @update:temperature="temperature = $event"
        @update:top-p="topP = $event"
        @update:top-k="topK = $event"
        @update:min-p="minP = $event"
        @update:presence-penalty="presencePenalty = $event"
        @update:frequency-penalty="frequencyPenalty = $event"
        @update:repetition-penalty="repetitionPenalty = $event"
        @update:stop-sequences-text="stopSequencesText = $event"
        @update:logprobs="logprobs = $event"
        @update:top-logprobs="topLogprobs = $event"
        @update:parallel-tool-calls="parallelToolCalls = $event"
        @update:max-tokens="maxTokens = $event"
        @update:seed="seed = $event"
        @update:max-retries="maxRetries = $event"
        @update:reasoning-mode="reasoningMode = $event"
        @update:reasoning-effort="reasoningEffort = $event"
        @update:test-tool-enabled="testToolEnabled = $event"
        @update:test-tool-approval-enabled="testToolApprovalEnabled = $event"
        @update:external-test-tool-enabled="externalTestToolEnabled = $event"
        @update:agent-test-tools-enabled="agentTestToolsEnabled = $event"
        @update:tool-call-repair-enabled="toolCallRepairEnabled = $event"
        @update:output-mode="outputMode = $event"
        @update:output-schema-text="outputSchemaText = $event"
        @update:output-choices-text="outputChoicesText = $event"
        @update:chat-headers-text="chatHeadersText = $event"
        @update:embedding-dimensions="embeddingDimensions = $event"
        @update:embedding-max-batch-size="embeddingMaxBatchSize = $event"
        @update:embedding-max-parallel-calls="embeddingMaxParallelCalls = $event"
        @update:embedding-max-retries="embeddingMaxRetries = $event"
        @update:image-n="imageN = $event"
        @update:image-width="imageWidth = $event"
        @update:image-height="imageHeight = $event"
        @update:image-aspect-ratio="imageAspectRatio = $event"
        @update:image-seed="imageSeed = $event"
        @update:image-response-format="imageResponseFormat = $event"
        @update:image-max-retries="imageMaxRetries = $event"
        @update:image-max-parallel-calls="imageMaxParallelCalls = $event"
        @update:image-headers-text="imageHeadersText = $event"
      />
    </div>
  </div>
</template>
