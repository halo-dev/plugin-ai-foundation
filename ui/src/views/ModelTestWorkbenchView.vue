<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import { ModelOptionModelTypeEnum, type TestEmbeddingResponse } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import AiModelSelector from '@/formkit/AiModelSelector.vue'
import {
  applyWorkbenchStreamPart,
  buildOutputSpec,
  buildReasoningOptions,
  buildTestChatRequest,
  parseProviderOptionsJson,
  readTestChatStream,
  type GenerateTextRequest,
  type OutputMode,
  type ReasoningEffort,
  type ReasoningMode,
  type TextStreamPart,
  type WorkbenchMessage,
} from '@/utils/model-test-workbench'
import { IconRefreshLine, VButton, VEmpty, VLoading } from '@halo-dev/components'
import { useRouteQuery } from '@vueuse/router'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import MingcuteDelete2Line from '~icons/mingcute/delete-2-line'
import RiMessage3Line from '~icons/ri/message-3-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiSparkling2Line from '~icons/ri/sparkling-2-line'
import RiStackLine from '~icons/ri/stack-line'
import ChatInputArea from './components/workbench/ChatInputArea.vue'
import ChatMessageItem from './components/workbench/ChatMessageItem.vue'
import EmbeddingTestPanel from './components/workbench/EmbeddingTestPanel.vue'
import ExamplePrompts from './components/workbench/ExamplePrompts.vue'
import ParameterSidebar from './components/workbench/ParameterSidebar.vue'

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
const testMode = shallowRef<'chat' | 'embedding'>('chat')

const messages = ref<WorkbenchMessage[]>([])
const input = shallowRef('')
const systemPrompt = shallowRef('')
const temperature = shallowRef(0.7)
const topP = shallowRef(1)
const maxTokens = shallowRef(1024)
const seed = shallowRef<number | undefined>()
const maxRetries = shallowRef<number | undefined>(2)
const reasoningMode = shallowRef<ReasoningMode>('DEFAULT')
const reasoningEffort = shallowRef<ReasoningEffort>('MEDIUM')
const testToolEnabled = shallowRef(false)
const testToolApprovalEnabled = shallowRef(false)
const externalTestToolEnabled = shallowRef(false)
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
const providerOptionsText = shallowRef('{}')
const providerOptionsError = shallowRef('')
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
const embeddingProviderOptionsText = shallowRef('{}')
const embeddingProviderOptionsError = shallowRef('')
const embeddingResult = shallowRef<TestEmbeddingResponse | undefined>()
const embeddingError = shallowRef('')
const isEmbeddingTesting = shallowRef(false)

let abortController: AbortController | null = null

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
const activeModels = computed(() =>
  testMode.value === 'embedding' ? embeddingModels.value : chatModels.value,
)
const activeModelType = computed(() =>
  testMode.value === 'embedding'
    ? ModelOptionModelTypeEnum.Embedding
    : ModelOptionModelTypeEnum.Language,
)

const selectedModel = computed(() => {
  return activeModels.value.find((model) => model.name === selectedModelName.value)
})
const selectedModelLabel = computed(() => {
  const model = selectedModel.value
  return model?.displayName || model?.modelId || model?.name || '未选择模型'
})
const selectedProviderLabel = computed(() => {
  const model = selectedModel.value
  return model?.provider?.displayName || model?.provider?.name || 'Provider'
})
const selectedProviderIconUrl = computed(() => {
  return selectedModel.value?.provider?.iconUrl
})
const selectedProviderModelCount = computed(() => {
  const providerName = selectedModel.value?.provider?.name
  if (!providerName) {
    return activeModels.value.length
  }
  return activeModels.value.filter((model) => model.provider?.name === providerName).length
})

watch(
  modelOptions,
  (items) => {
    const models = items || []
    const selected = models.find((item) => item.name === selectedModelName.value)
    if (selected?.modelType === ModelOptionModelTypeEnum.Embedding) {
      testMode.value = 'embedding'
    } else if (selected?.modelType === ModelOptionModelTypeEnum.Language) {
      testMode.value = 'chat'
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

async function sendMessage(content?: string) {
  const text = (content ?? input.value).trim()
  const model = selectedModel.value
  if (!text || !model?.name || isStreaming.value) {
    return
  }

  const providerOptions = parseProviderOptionsJson(providerOptionsText.value)
  if (providerOptions.error) {
    providerOptionsError.value = providerOptions.error
    return
  }
  providerOptionsError.value = ''
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

  const userMessage: WorkbenchMessage = {
    id: crypto.randomUUID(),
    role: 'user',
    content: text,
  }
  messages.value.push(userMessage)
  input.value = ''
  shouldAutoScroll.value = true

  const requestBody = buildTestChatRequest(messages.value, {
    ...buildChatParameters(providerOptions.value, headers.value, outputSpec.value),
  })

  await streamChatResponse(requestBody, model.name)
}

async function streamChatResponse(requestBody: GenerateTextRequest, modelName: string) {
  const model = activeModels.value.find((m) => m.name === modelName)
  if (!model) return

  const assistantMessage = createAssistantMessage(model)
  messages.value.push(assistantMessage)

  const controller = new AbortController()
  abortController = controller
  isStreaming.value = true

  try {
    await readTestChatStream({
      modelName,
      requestBody,
      signal: controller.signal,
      streamOptions: {
        testToolEnabled: testToolEnabled.value,
        testToolApprovalEnabled: testToolApprovalEnabled.value,
        externalTestToolEnabled: externalTestToolEnabled.value,
        toolCallRepairEnabled: toolCallRepairEnabled.value,
      },
      onChunks: (chunks) => handleChunks(assistantMessage.id, chunks),
    })
    finishAssistantMessage(assistantMessage.id, 'done')
  } catch (e) {
    if ((e as Error).name === 'AbortError') {
      return
    }
    appendAssistantError(assistantMessage.id, `请求失败: ${(e as Error).message}`)
  } finally {
    if (abortController === controller) {
      isStreaming.value = false
      abortController = null
    }
  }
}

function createAssistantMessage(model: {
  name?: string
  displayName?: string
  modelId?: string
}): WorkbenchMessage {
  return {
    id: crypto.randomUUID(),
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

  messages.value = messages.value.slice(0, messageIndex)

  const providerOptions = parseProviderOptionsJson(providerOptionsText.value)
  if (providerOptions.error) {
    providerOptionsError.value = providerOptions.error
    return
  }
  providerOptionsError.value = ''
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

  const requestBody = buildTestChatRequest(messages.value, {
    ...buildChatParameters(providerOptions.value, headers.value, outputSpec.value),
  })

  await streamChatResponse(requestBody, model.name)
}

function handleChunks(messageId: string, chunks: TextStreamPart[]) {
  const message = messages.value.find((item) => item.id === messageId)
  if (!message) {
    return
  }
  for (const chunk of chunks) {
    applyWorkbenchStreamPart(message, chunk)
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
  if (!message || !event || event.type !== 'tool-approval-request' || !event.approvalId) {
    return
  }

  event.approvalStatus = options.approved ? 'approved' : 'denied'
  message.toolEvents = [...(message.toolEvents || [])]
  const approvalResponseMessage = {
    role: 'TOOL' as const,
    content: [
      {
        type: 'tool-approval-response' as const,
        approvalId: event.approvalId,
        toolCallId: event.toolCallId,
        toolName: event.toolName,
        approved: options.approved,
        reason: options.approved
          ? 'Approved from console test page'
          : 'Denied from console test page',
      },
    ],
  }
  message.followingMessages = [
    ...(message.followingMessages || []).filter(
      (item) =>
        !item.content.some(
          (part) => part.type === 'tool-approval-response' && part.approvalId === event.approvalId,
        ),
    ),
    approvalResponseMessage,
  ]

  const providerOptions = parseProviderOptionsJson(providerOptionsText.value)
  if (providerOptions.error) {
    providerOptionsError.value = providerOptions.error
    return
  }
  providerOptionsError.value = ''
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

  const requestBody = buildTestChatRequest(
    messages.value,
    buildChatParameters(providerOptions.value, headers.value, outputSpec.value),
  )

  await streamChatResponse(requestBody, model.name)
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
  if (!message || !event || event.type !== 'tool-call' || !event.toolCallId || !event.toolName) {
    return
  }

  event.externalStatus = payload.type === 'tool-result' ? 'completed' : 'failed'
  message.toolEvents = [...(message.toolEvents || [])]
  const externalToolMessage = {
    role: 'TOOL' as const,
    content: [
      payload.type === 'tool-result'
        ? {
            type: 'tool-result' as const,
            toolCallId: event.toolCallId,
            toolName: event.toolName,
            result: payload.result,
          }
        : {
            type: 'tool-error' as const,
            toolCallId: event.toolCallId,
            toolName: event.toolName,
            errorText: payload.errorText,
          },
    ],
  }
  message.followingMessages = [
    ...(message.followingMessages || []).filter(
      (item) =>
        !item.content.some(
          (part) =>
            (part.type === 'tool-result' || part.type === 'tool-error') &&
            part.toolCallId === event.toolCallId,
        ),
    ),
    externalToolMessage,
  ]

  const providerOptions = parseProviderOptionsJson(providerOptionsText.value)
  if (providerOptions.error) {
    providerOptionsError.value = providerOptions.error
    return
  }
  providerOptionsError.value = ''
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

  const requestBody = buildTestChatRequest(
    messages.value,
    buildChatParameters(providerOptions.value, headers.value, outputSpec.value),
  )

  await streamChatResponse(requestBody, model.name)
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
  providerOptions: Record<string, Record<string, unknown>> | undefined,
  headers: Record<string, string> | undefined,
  output: ReturnType<typeof buildOutputSpec>['value'],
) {
  return {
    systemPrompt: systemPrompt.value,
    temperature: numberOrUndefined(temperature.value),
    topP: numberOrUndefined(topP.value),
    maxOutputTokens: numberOrUndefined(maxTokens.value),
    seed: numberOrUndefined(seed.value),
    maxRetries: numberOrUndefined(maxRetries.value),
    reasoning: buildReasoningOptions({
      mode: reasoningMode.value,
      effort: reasoningEffort.value,
    }),
    providerOptions,
    headers,
    output,
  }
}

function appendAssistantWarnings(
  messageId: string,
  warnings: NonNullable<TextStreamPart['warnings']>,
) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.warnings = [...(message.warnings || []), ...warnings]
  }
}

function appendAssistantError(messageId: string, content: string) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.content += content
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

function stopGeneration() {
  abortCurrentRequest(true)
}

function abortCurrentRequest(markStopped: boolean) {
  const controller = abortController
  controller?.abort()
  const streamingMessage = [...messages.value].reverse().find((item) => item.state === 'streaming')
  if (markStopped && streamingMessage) {
    streamingMessage.state = 'stopped'
    if (streamingMessage.reasoningState === 'streaming') {
      streamingMessage.reasoningState = 'done'
    }
  }
  if (controller && abortController === controller) {
    isStreaming.value = false
    abortController = null
  }
}

function clearMessages() {
  if (isStreaming.value) {
    stopGeneration()
  }
  messages.value = []
  shouldAutoScroll.value = true
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

function handleExampleSelect(content: string) {
  input.value = content
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
  const providerOptions = parseProviderOptionsJson(embeddingProviderOptionsText.value)
  if (providerOptions.error) {
    embeddingProviderOptionsError.value = providerOptions.error
    return
  }
  embeddingProviderOptionsError.value = ''
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
        providerOptions: providerOptions.value as
          | { [key: string]: { [key: string]: object } }
          | undefined,
      },
    })
    embeddingResult.value = data
  } catch (e) {
    embeddingError.value = `请求失败: ${(e as Error).message}`
  } finally {
    isEmbeddingTesting.value = false
  }
}

onBeforeUnmount(() => {
  abortCurrentRequest(false)
})
</script>

<template>
  <div class=":uno: h-[calc(100vh-9.25rem)] min-h-[34rem] bg-[#eef3f7] p-2">
    <VLoading v-if="isLoading" />

    <VEmpty
      v-else-if="!chatModels.length && !embeddingModels.length"
      title="暂无可测试的模型"
      message="你可以在配置选项卡中添加或启用支持对话或嵌入能力的模型"
    >
      <template #actions>
        <VButton :loading="isFetching" @click="refetch()">刷新</VButton>
      </template>
    </VEmpty>

    <div
      v-else
      class=":uno: grid grid-cols-1 h-full min-h-0 overflow-hidden border border-slate-200/80 rounded-lg bg-white shadow-[0_18px_45px_rgba(15,23,42,0.08)] lg:grid-cols-[minmax(0,1fr)_23rem]"
    >
      <section class=":uno: min-h-0 min-w-0 flex flex-col bg-[#f8fafc]">
        <header class=":uno: border-b border-slate-200/80 bg-white/95 px-4 py-3 backdrop-blur">
          <div class=":uno: flex flex-col gap-3 xl:flex-row xl:items-center">
            <div class=":uno: min-w-0 flex flex-1 items-center gap-3">
              <div
                class=":uno: size-9 flex flex-none items-center justify-center border border-slate-200 rounded-lg bg-white text-slate-700 shadow-sm"
              >
                <img
                  v-if="selectedProviderIconUrl"
                  :src="selectedProviderIconUrl"
                  :alt="selectedProviderLabel"
                  class=":uno: size-5 object-contain"
                />
                <RiSparkling2Line v-else class=":uno: h-4.5 w-4.5" />
              </div>
              <div class=":uno: min-w-0">
                <div class=":uno: min-w-0 flex items-center gap-2">
                  <div class=":uno: truncate text-sm text-slate-950 font-semibold">
                    {{ selectedModelLabel }}
                  </div>
                  <span
                    class=":uno: hidden border border-emerald-200 rounded bg-emerald-50 text-[10px] text-emerald-700 font-medium sm:inline-flex !px-1.5 !py-0.5"
                  >
                    {{ testMode === 'chat' ? 'Language' : 'Embedding' }}
                  </span>
                </div>
                <div class=":uno: mt-0.5 truncate text-xs text-slate-500">
                  {{ selectedProviderLabel }} · {{ selectedProviderModelCount }} 个可用模型
                </div>
              </div>
            </div>

            <div class=":uno: min-w-0 flex flex-col gap-2 xl:w-[42rem] sm:flex-row sm:items-center">
              <div
                class=":uno: h-9 inline-flex flex-none items-center border border-slate-200 rounded-lg bg-slate-100/80 !p-0.5"
              >
                <button
                  type="button"
                  class=":uno: h-7 inline-flex items-center gap-1.5 rounded-md text-xs font-medium transition-all !px-3"
                  :class="
                    testMode === 'chat'
                      ? ':uno: bg-white text-slate-950 shadow-sm ring-1 ring-slate-200'
                      : ':uno: text-slate-500 hover:text-slate-800'
                  "
                  :disabled="isStreaming || isEmbeddingTesting || !chatModels.length"
                  @click="testMode = 'chat'"
                >
                  <RiMessage3Line class=":uno: size-3.5" />
                  对话
                </button>
                <button
                  type="button"
                  class=":uno: h-7 inline-flex items-center gap-1.5 rounded-md text-xs font-medium transition-all !px-3"
                  :class="
                    testMode === 'embedding'
                      ? ':uno: bg-white text-slate-950 shadow-sm ring-1 ring-slate-200'
                      : ':uno: text-slate-500 hover:text-slate-800'
                  "
                  :disabled="isStreaming || isEmbeddingTesting || !embeddingModels.length"
                  @click="testMode = 'embedding'"
                >
                  <RiStackLine class=":uno: size-3.5" />
                  嵌入
                </button>
              </div>

              <AiModelSelector
                v-model="selectedModelName"
                name="model"
                :model-type="activeModelType"
                :available="availableOnly"
                :disabled="isStreaming || isEmbeddingTesting"
                placeholder="选择测试模型"
                search-placeholder="搜索模型..."
                full-width
                class=":uno: min-w-0 flex-1 !py-0"
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
            class=":uno: min-h-0 flex-1 overflow-y-auto bg-[radial-gradient(circle_at_top_left,rgba(20,184,166,0.10),transparent_30%),linear-gradient(180deg,#f8fafc_0%,#f1f5f9_100%)] px-4 py-5"
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
            :is-streaming="isStreaming"
            :disabled="!selectedModel"
            @send="sendMessage()"
            @stop="stopGeneration"
          />
        </template>

        <template v-else>
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
                  class=":uno: pointer-events-none absolute bottom-3 right-14 text-[11px] text-slate-400 font-mono"
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
      </section>

      <ParameterSidebar
        :mode="testMode"
        :system-prompt="systemPrompt"
        :temperature="temperature"
        :top-p="topP"
        :max-tokens="maxTokens"
        :seed="seed"
        :max-retries="maxRetries"
        :reasoning-mode="reasoningMode"
        :reasoning-effort="reasoningEffort"
        :test-tool-enabled="testToolEnabled"
        :test-tool-approval-enabled="testToolApprovalEnabled"
        :external-test-tool-enabled="externalTestToolEnabled"
        :tool-call-repair-enabled="toolCallRepairEnabled"
        :output-mode="outputMode"
        :output-schema-text="outputSchemaText"
        :output-choices-text="outputChoicesText"
        :provider-options-text="providerOptionsText"
        :provider-options-error="providerOptionsError"
        :chat-headers-text="chatHeadersText"
        :chat-headers-error="chatHeadersError"
        :output-error="outputError"
        :embedding-dimensions="embeddingDimensions"
        :embedding-max-batch-size="embeddingMaxBatchSize"
        :embedding-max-parallel-calls="embeddingMaxParallelCalls"
        :embedding-max-retries="embeddingMaxRetries"
        :embedding-provider-options-text="embeddingProviderOptionsText"
        :embedding-provider-options-error="embeddingProviderOptionsError"
        @update:system-prompt="systemPrompt = $event"
        @update:temperature="temperature = $event"
        @update:top-p="topP = $event"
        @update:max-tokens="maxTokens = $event"
        @update:seed="seed = $event"
        @update:max-retries="maxRetries = $event"
        @update:reasoning-mode="reasoningMode = $event"
        @update:reasoning-effort="reasoningEffort = $event"
        @update:test-tool-enabled="testToolEnabled = $event"
        @update:test-tool-approval-enabled="testToolApprovalEnabled = $event"
        @update:external-test-tool-enabled="externalTestToolEnabled = $event"
        @update:tool-call-repair-enabled="toolCallRepairEnabled = $event"
        @update:output-mode="outputMode = $event"
        @update:output-schema-text="outputSchemaText = $event"
        @update:output-choices-text="outputChoicesText = $event"
        @update:provider-options-text="providerOptionsText = $event"
        @update:chat-headers-text="chatHeadersText = $event"
        @update:embedding-dimensions="embeddingDimensions = $event"
        @update:embedding-max-batch-size="embeddingMaxBatchSize = $event"
        @update:embedding-max-parallel-calls="embeddingMaxParallelCalls = $event"
        @update:embedding-max-retries="embeddingMaxRetries = $event"
        @update:embedding-provider-options-text="embeddingProviderOptionsText = $event"
      />
    </div>
  </div>
</template>
