<script lang="ts" setup>
import { aiConsoleApiClient } from '@/api'
import { ModelOptionModelTypeEnum, type TestEmbeddingResponse } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import AiModelSelector from '@/formkit/AiModelSelector.vue'
import { renderMarkdown } from '@/utils/markdown'
import { modelOptionLabel } from '@/utils/model-options'
import {
  buildOutputSpec,
  buildReasoningOptions,
  buildTestChatRequest,
  flushSseJsonBuffer,
  isRenderableReasoningDelta,
  isRenderableTextDelta,
  isTerminalTextStreamPart,
  parseProviderOptionsJson,
  parseSseJsonLines,
  toToolEvent,
  type TextStreamPart,
  type OutputMode,
  type ReasoningEffort,
  type ReasoningMode,
  type WorkbenchMessage,
} from '@/utils/model-test-workbench'
import { IconRefreshLine, VButton, VEmpty, VLoading } from '@halo-dev/components'
import { useRouteQuery } from '@vueuse/router'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiStopCircleLine from '~icons/ri/stop-circle-line'

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
const isStreaming = shallowRef(false)
const conversationRef = ref<HTMLElement | null>(null)
const embeddingInputs = shallowRef('Halo 是一个开源建站工具\nAI Foundation 提供统一 AI 能力')
const embeddingDimensions = shallowRef<number | undefined>()
const embeddingMaxBatchSize = shallowRef<number | undefined>(1)
const embeddingMaxParallelCalls = shallowRef<number | undefined>(2)
const embeddingMaxRetries = shallowRef<number | undefined>(1)
const embeddingProviderOptionsText = shallowRef('{}')
const embeddingHeadersText = shallowRef('{}')
const embeddingProviderOptionsError = shallowRef('')
const embeddingHeadersError = shallowRef('')
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
const activeModels = computed(() => (testMode.value === 'embedding' ? embeddingModels.value : chatModels.value))
const activeModelType = computed(() =>
  testMode.value === 'embedding'
    ? ModelOptionModelTypeEnum.Embedding
    : ModelOptionModelTypeEnum.Language,
)
const providerOptionsHelp = computed(() => {
  return providerOptionsError.value || '请输入按服务商分组的 JSON 对象，例如 {"openai": {"seed": 42}}'
})
const outputSchemaHelp = computed(() => {
  return outputError.value || (outputMode.value === 'ARRAY' ? '用于校验每个数组元素' : '用于约束最终 JSON 对象')
})
const outputChoicesHelp = computed(() => {
  return outputError.value || '每行一个可选值'
})

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
    if (conversationRef.value) {
      conversationRef.value.scrollTop = conversationRef.value.scrollHeight
    }
  },
  { deep: true },
)

async function sendMessage() {
  const content = input.value.trim()
  const model = selectedModel.value
  if (!content || !model?.name || isStreaming.value) {
    return
  }

  const providerOptions = parseProviderOptionsJson(providerOptionsText.value)
  if (providerOptions.error) {
    providerOptionsError.value = providerOptions.error
    return
  }
  providerOptionsError.value = ''
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
    content,
  }
  messages.value.push(userMessage)
  input.value = ''

  const requestBody = buildTestChatRequest(messages.value, {
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
    providerOptions: providerOptions.value,
    output: outputSpec.value,
  })

  const assistantMessage: WorkbenchMessage = {
    id: crypto.randomUUID(),
    role: 'assistant',
    content: '',
    modelName: model.name,
    modelDisplayName: modelOptionLabel(model),
    state: 'streaming',
  }
  messages.value.push(assistantMessage)

  const controller = new AbortController()
  abortController = controller
  isStreaming.value = true

  try {
    const params = new URLSearchParams()
    if (testToolEnabled.value) {
      params.set('enableTestTool', 'true')
    }
    const query = params.toString()
    const response = await fetch(
      `/apis/console.api.aifoundation.halo.run/v1alpha1/models/${encodeURIComponent(model.name)}/test-chat/stream${query ? `?${query}` : ''}`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(requestBody),
        signal: controller.signal,
      },
    )

    if (!response.ok) {
      throw new Error((await response.text()) || `HTTP ${response.status}`)
    }

    const reader = response.body?.getReader()
    if (!reader) {
      throw new Error('无法读取响应流')
    }

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const parsed = parseSseJsonLines<TextStreamPart>(
        buffer,
        decoder.decode(value, { stream: true }),
      )
      buffer = parsed.buffer
      handleChunks(assistantMessage.id, parsed.chunks)
    }

    handleChunks(assistantMessage.id, flushSseJsonBuffer<TextStreamPart>(buffer))
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

function handleChunks(messageId: string, chunks: TextStreamPart[]) {
  for (const chunk of chunks) {
    const toolEvent = toToolEvent(chunk)
    if (toolEvent) {
      appendToolEvent(messageId, toolEvent)
      continue
    }
    if (chunk.type === 'error') {
      appendAssistantError(messageId, chunk.errorText || '请求失败')
      continue
    }
    if (isRenderableReasoningDelta(chunk)) {
      appendAssistantReasoning(messageId, chunk.delta)
      continue
    }
    if (isRenderableTextDelta(chunk)) {
      appendAssistantContent(messageId, chunk.delta)
      continue
    }
    if (chunk.type === 'finish-step' && chunk.warnings?.length) {
      appendAssistantWarnings(messageId, chunk.warnings)
      continue
    }
    if (isTerminalTextStreamPart(chunk)) {
      finishAssistantMessage(messageId, 'done')
    }
  }
}

function appendToolEvent(messageId: string, event: NonNullable<ReturnType<typeof toToolEvent>>) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.toolEvents = [...(message.toolEvents || []), event]
  }
}

function appendAssistantContent(messageId: string, content: string) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.content += content
  }
}

function appendAssistantReasoning(messageId: string, content: string) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.reasoningContent = `${message.reasoningContent || ''}${content}`
  }
}

function appendAssistantWarnings(messageId: string, warnings: NonNullable<TextStreamPart['warnings']>) {
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
  }
}

function finishAssistantMessage(messageId: string, state: WorkbenchMessage['state']) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message && message.state === 'streaming') {
    message.state = state
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
  const headers = parseStringMapJson(embeddingHeadersText.value)
  if (headers.error) {
    embeddingHeadersError.value = headers.error
    return
  }
  embeddingHeadersError.value = ''
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
          providerOptions: providerOptions.value as { [key: string]: { [key: string]: object } } | undefined,
          headers: headers.value,
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
  <div class=":uno: h-[calc(100vh-8.25rem)] min-h-[34rem] p-2">
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
      class=":uno: rounded-base grid grid-cols-1 h-full min-h-0 overflow-hidden border border-gray-200 bg-white shadow-sm lg:grid-cols-[minmax(0,1fr)_20rem]"
    >
      <section class=":uno: min-h-0 min-w-0 flex flex-col">
        <header
          class=":uno: flex flex-col gap-3 border-b border-gray-200 bg-white px-3 py-3 sm:flex-row sm:items-center"
        >
          <div class=":uno: flex flex-none border border-gray-200 rounded-md bg-gray-50 p-0.5">
            <button
              type="button"
              class=":uno: h-8 rounded-[5px] px-3 text-sm"
              :class="testMode === 'chat' ? ':uno: bg-white text-gray-950 shadow-sm' : ':uno: text-gray-500'"
              :disabled="isStreaming || isEmbeddingTesting || !chatModels.length"
              @click="testMode = 'chat'"
            >
              对话
            </button>
            <button
              type="button"
              class=":uno: h-8 rounded-[5px] px-3 text-sm"
              :class="testMode === 'embedding' ? ':uno: bg-white text-gray-950 shadow-sm' : ':uno: text-gray-500'"
              :disabled="isStreaming || isEmbeddingTesting || !embeddingModels.length"
              @click="testMode = 'embedding'"
            >
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

          <div class=":uno: flex flex-none items-center gap-2">
            <button
              type="button"
              class=":uno: group h-9 w-9 inline-flex items-center justify-center border border-gray-200 rounded-md bg-white hover:bg-gray-50"
              v-tooltip="`刷新模型`"
              @click="refetch()"
            >
              <IconRefreshLine
                class=":uno: h-4 w-4 text-gray-500 group-hover:text-gray-900"
                :class="{ ':uno: animate-spin': isFetching }"
              />
            </button>
            <button
              type="button"
              class=":uno: group h-9 w-9 inline-flex items-center justify-center border border-gray-200 rounded-md bg-white hover:bg-gray-50"
              v-tooltip="`清空会话`"
              @click="clearMessages"
            >
              <RiDeleteBinLine class=":uno: h-4 w-4 text-gray-500 group-hover:text-gray-900" />
            </button>
          </div>
        </header>

        <div
          v-if="testMode === 'chat'"
          class=":uno: min-h-0 flex-1 overflow-y-auto bg-gray-50/80 px-4 py-4"
          ref="conversationRef"
        >
          <div v-if="!messages.length" class=":uno: h-full flex items-center justify-center">
            <div
              class=":uno: rounded-base max-w-sm w-full border border-gray-200 border-dashed bg-white px-6 py-8 text-center"
            >
              <div class=":uno: text-sm text-gray-800 font-medium">暂无会话</div>
              <div class=":uno: mt-1 text-xs text-gray-500">发送消息后会在这里显示响应</div>
            </div>
          </div>

          <div v-else class=":uno: mx-auto max-w-4xl space-y-4">
            <div
              v-for="message in messages"
              :key="message.id"
              class=":uno: flex"
              :class="{ ':uno: justify-end': message.role === 'user' }"
            >
              <div
                class=":uno: rounded-base max-w-[86%] border px-4 py-3 text-sm leading-6 shadow-sm"
                :class="{
                  ':uno: border-blue-200 bg-blue-600 text-white': message.role === 'user',
                  ':uno: border-gray-200 bg-white text-gray-900': message.role === 'assistant',
                  ':uno: border-red-200 bg-red-50 text-red-700': message.state === 'error',
                }"
              >
                <div
                  v-if="message.role === 'assistant'"
                  class=":uno: mb-2 flex flex-wrap items-center gap-2 text-xs text-gray-500"
                >
                  <span>{{ message.modelDisplayName }}</span>
                  <VTag v-if="message.state === 'streaming'">生成中</VTag>
                  <VTag v-else-if="message.state === 'stopped'">已停止</VTag>
                  <VTag v-else-if="message.state === 'error'" theme="danger">错误</VTag>
                </div>

                <details
                  v-if="message.role === 'assistant' && message.reasoningContent"
                  class=":uno: mb-3 border-b border-gray-100 pb-2 text-xs text-gray-500"
                  :open="message.state === 'streaming'"
                >
                  <summary class=":uno: cursor-pointer select-none text-gray-600 font-medium">
                    推理
                  </summary>
                  <div class=":uno: ai-markdown mt-2 break-words text-gray-500" v-html="renderMarkdown(message.reasoningContent)" />
                </details>

                <div
                  v-if="message.role === 'assistant' && message.toolEvents?.length"
                  class=":uno: mb-3 border-b border-gray-100 pb-2 space-y-1"
                >
                  <div
                    v-for="event in message.toolEvents"
                    :key="event.id"
                    class=":uno: rounded-md bg-gray-50 px-2 py-1 text-xs text-gray-600"
                  >
                    <span class=":uno: font-medium">
                      {{
                        event.type === "tool-call"
                          ? "工具调用"
                          : event.type === "tool-result"
                            ? "工具结果"
                            : "工具错误"
                      }}
                    </span>
                    <span v-if="event.toolName" class=":uno: ml-1 font-mono">
                      {{ event.toolName }}
                    </span>
                    <span v-if="event.summary" class=":uno: ml-1 break-all">
                      {{ event.summary }}
                    </span>
                  </div>
                </div>

                <div
                  v-if="message.role === 'assistant' && message.warnings?.length"
                  class=":uno: mb-3 border border-yellow-200 rounded-md bg-yellow-50 px-2 py-1.5"
                >
                  <div class=":uno: text-xs text-yellow-800 font-medium">Warnings</div>
                  <ul class=":uno: mt-1 text-xs text-yellow-800 space-y-1">
                    <li
                      v-for="warning in message.warnings"
                      :key="`${warning.code}-${warning.message}`"
                    >
                      <span class=":uno: font-mono">{{ warning.code }}</span>
                      <span v-if="warning.message">: {{ warning.message }}</span>
                    </li>
                  </ul>
                </div>

                <div v-if="message.role === 'user'" class=":uno: whitespace-pre-wrap">
                  {{ message.content }}
                </div>
                <div
                  v-else
                  class=":uno: ai-markdown break-words"
                  v-html="
                    renderMarkdown(message.content || (message.state === 'streaming' ? ' ' : ''))
                  "
                />
              </div>
            </div>
          </div>
        </div>

        <div v-else class=":uno: min-h-0 flex-1 overflow-y-auto bg-gray-50/80 px-4 py-4">
          <div class=":uno: mx-auto max-w-4xl space-y-4">
            <div
              v-if="embeddingError"
              class=":uno: rounded-base border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700"
            >
              {{ embeddingError }}
            </div>

            <div
              v-if="!embeddingResult && !embeddingError"
              class=":uno: rounded-base border border-gray-200 border-dashed bg-white px-6 py-8 text-center"
            >
              <div class=":uno: text-sm text-gray-800 font-medium">暂无嵌入测试结果</div>
              <div class=":uno: mt-1 text-xs text-gray-500">输入多行文本后点击运行测试</div>
            </div>

            <div
              v-if="embeddingResult"
              class=":uno: rounded-base border border-gray-200 bg-white p-4 shadow-sm"
            >
              <div class=":uno: flex flex-wrap items-center gap-2 text-sm text-gray-900">
                <span class=":uno: font-medium">结果</span>
                <VTag>{{ embeddingResult.embeddingsCount }} 个向量</VTag>
                <VTag v-if="embeddingResult.usage?.tokens !== undefined">
                  {{ embeddingResult.usage.tokens }} tokens
                </VTag>
                <VTag v-if="embeddingResult.response?.model">
                  {{ embeddingResult.response.model }}
                </VTag>
              </div>

              <div
                v-if="embeddingResult.firstPairSimilarity !== undefined"
                class=":uno: mt-3 rounded-md bg-gray-50 px-3 py-2 text-sm text-gray-700"
              >
                前两个输入的 cosine similarity：
                <span class=":uno: font-mono">{{ embeddingResult.firstPairSimilarity.toFixed(6) }}</span>
              </div>

              <div class=":uno: mt-4 space-y-2">
                <div
                  v-for="item in embeddingResult.embeddings"
                  :key="item.index"
                  class=":uno: border border-gray-100 rounded-md px-3 py-2"
                >
                  <div class=":uno: flex items-center justify-between text-xs text-gray-500">
                    <span>#{{ (item.index ?? 0) + 1 }}</span>
                    <span>{{ item.dimensions }} 维</span>
                  </div>
                  <div class=":uno: mt-1 break-all text-xs text-gray-700 font-mono">
                    [{{ (item.preview || []).map((value) => Number(value).toFixed(4)).join(', ') }}]
                  </div>
                </div>
              </div>

              <div
                v-if="embeddingResult.warnings?.length"
                class=":uno: mt-4 border border-yellow-200 rounded-md bg-yellow-50 px-3 py-2"
              >
                <div class=":uno: text-xs text-yellow-800 font-medium">Warnings</div>
                <ul class=":uno: mt-1 text-xs text-yellow-800 space-y-1">
                  <li v-for="warning in embeddingResult.warnings" :key="`${warning.code}-${warning.message}`">
                    <span class=":uno: font-mono">{{ warning.code }}</span>
                    <span v-if="warning.message">: {{ warning.message }}</span>
                  </li>
                </ul>
              </div>

              <details class=":uno: mt-4 text-xs text-gray-600">
                <summary class=":uno: cursor-pointer select-none font-medium">诊断信息</summary>
                <pre class=":uno: mt-2 overflow-auto rounded-md bg-gray-950 p-3 text-gray-100">{{ JSON.stringify({
                  response: embeddingResult.response,
                  providerMetadata: embeddingResult.providerMetadata,
                  usage: embeddingResult.usage,
                }, null, 2) }}</pre>
              </details>
            </div>
          </div>
        </div>

        <footer v-if="testMode === 'chat'" class=":uno: border-t border-gray-200 bg-white p-3">
          <div class=":uno: flex flex-col gap-2 sm:flex-row">
            <textarea
              id="model-test-workbench-input"
              v-model="input"
              name="message"
              aria-label="输入消息"
              rows="2"
              placeholder="输入消息..."
              class=":uno: min-h-18 flex-1 resize-none rounded-md text-sm outline-none !border !border-gray-200 !border-solid !px-3 !py-2.5 focus:ring-2 focus:ring-blue-500/10 focus:!border-blue-500"
              :disabled="isStreaming"
              @keydown.meta.enter.prevent="sendMessage"
              @keydown.ctrl.enter.prevent="sendMessage"
            />
            <div class=":uno: flex flex-none sm:items-end">
              <VButton
                v-if="isStreaming"
                type="secondary"
                class=":uno: w-full sm:w-auto"
                @click="stopGeneration"
              >
                <template #icon>
                  <RiStopCircleLine />
                </template>
                停止
              </VButton>
              <VButton
                v-else
                type="primary"
                class=":uno: w-full sm:w-auto"
                :disabled="!input.trim() || !selectedModel"
                @click="sendMessage"
              >
                <template #icon>
                  <RiSendPlaneLine />
                </template>
                发送
              </VButton>
            </div>
          </div>
        </footer>
        <footer v-else class=":uno: border-t border-gray-200 bg-white p-3">
          <div class=":uno: flex flex-col gap-2 sm:flex-row">
            <textarea
              v-model="embeddingInputs"
              rows="3"
              placeholder="每行一段需要向量化的文本"
              class=":uno: min-h-24 flex-1 resize-none rounded-md text-sm outline-none !border !border-gray-200 !border-solid !px-3 !py-2.5 focus:ring-2 focus:ring-blue-500/10 focus:!border-blue-500"
              :disabled="isEmbeddingTesting"
            />
            <div class=":uno: flex flex-none sm:items-end">
              <VButton
                type="primary"
                class=":uno: w-full sm:w-auto"
                :loading="isEmbeddingTesting"
                :disabled="!embeddingInputs.trim() || !selectedModel"
                @click="runEmbeddingTest"
              >
                运行测试
              </VButton>
            </div>
          </div>
        </footer>
      </section>

      <aside
        class=":uno: max-h-80 overflow-y-auto border-t border-gray-200 bg-white p-4 lg:max-h-none lg:border-l lg:border-t-0"
      >
        <div class=":uno: mb-4">
          <div class=":uno: text-sm text-gray-950 font-semibold">参数</div>
          <div class=":uno: mt-1 text-xs text-gray-500">当前请求的可选控制项</div>
        </div>

        <FormKit v-if="testMode === 'chat'" type="form" :actions="false">
          <FormKit
            v-model="systemPrompt"
            type="textarea"
            name="systemPrompt"
            label="系统提示词"
            rows="4"
            placeholder="可选"
          />

          <FormKit
            v-model="temperature"
            type="number"
            number
            name="temperature"
            label="Temperature"
            min="0"
            max="2"
            step="0.1"
          />

          <FormKit
            v-model="topP"
            type="number"
            number
            name="topP"
            label="Top P"
            min="0"
            max="1"
            step="0.05"
          />

          <FormKit
            v-model="maxTokens"
            type="number"
            number
            name="maxTokens"
            label="Max Tokens"
            min="1"
            step="1"
          />

          <FormKit
            v-model="seed"
            type="number"
            number
            name="seed"
            label="Seed"
            step="1"
          />

          <FormKit
            v-model="maxRetries"
            type="number"
            number
            name="maxRetries"
            label="Max Retries"
            min="0"
            step="1"
            help="仅作用于可重试的非流式 provider 调用，0 表示不重试。"
          />

          <FormKit
            v-model="reasoningMode"
            type="select"
            name="reasoningMode"
            label="推理控制"
            :options="[
              { label: '默认', value: 'DEFAULT' },
              { label: '启用', value: 'ENABLED' },
              { label: '禁用', value: 'DISABLED' },
              { label: '按 effort', value: 'EFFORT' },
            ]"
            help="默认表示不传 provider 原生推理参数；启用、禁用和 effort 需要当前 provider 支持。"
            outer-class=":uno: mt-4"
          />

          <FormKit
            v-if="reasoningMode === 'EFFORT'"
            v-model="reasoningEffort"
            type="select"
            name="reasoningEffort"
            label="推理 Effort"
            :options="[
              { label: 'Low', value: 'LOW' },
              { label: 'Medium', value: 'MEDIUM' },
              { label: 'High', value: 'HIGH' },
            ]"
            outer-class=":uno: mt-4"
          />

          <FormKit
            v-model="testToolEnabled"
            type="checkbox"
            name="testToolEnabled"
            label="启用测试工具"
            help="后台会注入 halo_test_info。可输入：请调用 halo_test_info 测试工具并告诉我返回内容。"
            outer-class=":uno: mt-4"
          />

          <FormKit
            v-model="outputMode"
            type="select"
            name="outputMode"
            label="结构化输出"
            :options="[
              { label: '文本', value: 'TEXT' },
              { label: 'JSON 对象', value: 'OBJECT' },
              { label: 'JSON 数组', value: 'ARRAY' },
              { label: '枚举选择', value: 'CHOICE' },
              { label: '任意 JSON', value: 'JSON' },
            ]"
            outer-class=":uno: mt-4"
          />

          <FormKit
            v-if="outputMode === 'OBJECT' || outputMode === 'ARRAY'"
            v-model="outputSchemaText"
            type="textarea"
            name="outputSchema"
            :label="outputMode === 'ARRAY' ? '元素 JSON Schema' : 'JSON Schema'"
            rows="8"
            :help="outputSchemaHelp"
            outer-class=":uno: mt-4"
            input-class=":uno: font-mono text-xs"
          />

          <FormKit
            v-if="outputMode === 'CHOICE'"
            v-model="outputChoicesText"
            type="textarea"
            name="outputChoices"
            label="枚举选项"
            rows="4"
            :help="outputChoicesHelp"
            outer-class=":uno: mt-4"
          />

          <FormKit
            v-model="providerOptionsText"
            type="textarea"
            name="providerOptions"
            label="Provider Options"
            rows="6"
            :help="providerOptionsHelp"
            outer-class=":uno: mt-4"
            input-class=":uno: font-mono text-xs"
          />
        </FormKit>
        <FormKit v-else type="form" :actions="false">
          <FormKit
            v-model="embeddingDimensions"
            type="number"
            number
            name="embeddingDimensions"
            label="Dimensions"
            min="1"
            step="1"
            help="可选，provider 支持时覆盖输出维度"
          />
          <FormKit
            v-model="embeddingMaxBatchSize"
            type="number"
            number
            name="embeddingMaxBatchSize"
            label="Max Batch Size"
            min="1"
            step="1"
          />
          <FormKit
            v-model="embeddingMaxParallelCalls"
            type="number"
            number
            name="embeddingMaxParallelCalls"
            label="Max Parallel Calls"
            min="1"
            step="1"
          />
          <FormKit
            v-model="embeddingMaxRetries"
            type="number"
            number
            name="embeddingMaxRetries"
            label="Max Retries"
            min="0"
            step="1"
          />
          <FormKit
            v-model="embeddingProviderOptionsText"
            type="textarea"
            name="embeddingProviderOptions"
            label="Provider Options"
            rows="6"
            :help="embeddingProviderOptionsError || '例如 {&quot;openai&quot;: {&quot;dimensions&quot;: 512}}'"
            outer-class=":uno: mt-4"
            input-class=":uno: font-mono text-xs"
          />
          <FormKit
            v-model="embeddingHeadersText"
            type="textarea"
            name="embeddingHeaders"
            label="Headers"
            rows="4"
            :help="embeddingHeadersError || '请求级 headers，当前 provider 不支持时会返回 warning'"
            outer-class=":uno: mt-4"
            input-class=":uno: font-mono text-xs"
          />
        </FormKit>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.ai-markdown :deep(*) {
  max-width: 100%;
}

.ai-markdown :deep(p) {
  margin: 0.25rem 0;
}

.ai-markdown :deep(p:first-child) {
  margin-top: 0;
}

.ai-markdown :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-markdown :deep(ul),
.ai-markdown :deep(ol) {
  margin: 0.5rem 0;
  padding-left: 1.25rem;
}

.ai-markdown :deep(li) {
  margin: 0.125rem 0;
}

.ai-markdown :deep(pre) {
  margin: 0.5rem 0;
  overflow-x: auto;
  border-radius: 0.375rem;
  background: #f3f4f6;
  padding: 0.75rem;
}

.ai-markdown :deep(code) {
  border-radius: 0.25rem;
  background: #f3f4f6;
  padding: 0.125rem 0.25rem;
  font-size: 0.875em;
}

.ai-markdown :deep(pre code) {
  background: transparent;
  padding: 0;
}

.ai-markdown :deep(blockquote) {
  margin: 0.5rem 0;
  border-left: 3px solid #d1d5db;
  padding-left: 0.75rem;
  color: #4b5563;
}

.ai-markdown :deep(a) {
  color: #2563eb;
  text-decoration: underline;
}
</style>
