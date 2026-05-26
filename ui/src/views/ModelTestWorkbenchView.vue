<script lang="ts" setup>
import { ModelOptionModelTypeEnum } from '@/api/generated'
import { useModelOptionsFetch } from '@/composables/use-model-options-fetch'
import { renderMarkdown } from '@/utils/markdown'
import { groupModelOptionsByProvider, modelOptionLabel } from '@/utils/model-options'
import {
  buildTestChatRequest,
  flushSseJsonBuffer,
  parseProviderOptionsJson,
  parseSseJsonLines,
  type TextStreamPart,
  type WorkbenchMessage,
} from '@/utils/model-test-workbench'
import { IconRefreshLine, VButton, VEmpty, VLoading, VTag } from '@halo-dev/components'
import { useRouteQuery } from '@vueuse/router'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiSendPlaneLine from '~icons/ri/send-plane-line'
import RiStopCircleLine from '~icons/ri/stop-circle-line'

const languageModelType = shallowRef<string | undefined>(ModelOptionModelTypeEnum.Language)
const availableOnly = shallowRef<boolean | undefined>(true)
const {
  data: modelOptions,
  isLoading,
  isFetching,
  refetch,
} = useModelOptionsFetch({
  modelType: languageModelType,
  available: availableOnly,
})

const selectedModelName = useRouteQuery<string | undefined>('model')

const messages = ref<WorkbenchMessage[]>([])
const input = shallowRef('')
const systemPrompt = shallowRef('')
const temperature = shallowRef(0.7)
const topP = shallowRef(1)
const maxTokens = shallowRef(1024)
const providerOptionsText = shallowRef('{}')
const providerOptionsError = shallowRef('')
const isStreaming = shallowRef(false)
const conversationRef = ref<HTMLElement | null>(null)

let abortController: AbortController | null = null

const chatModels = computed(() => {
  return (modelOptions.value || []).filter((model) => {
    return model.name && model.modelType === ModelOptionModelTypeEnum.Language
  })
})
const chatModelGroups = computed(() => groupModelOptionsByProvider(chatModels.value))
const providerOptionsHelp = computed(() => {
  return providerOptionsError.value || '请输入按服务商分组的 JSON 对象，例如 {"openai": {"seed": 42}}'
})

const selectedModel = computed(() => {
  return chatModels.value.find((model) => model.name === selectedModelName.value)
})

const selectedModelProviderTypeDisplayName = computed(() => {
  return selectedModel.value?.provider?.providerTypeDisplayName
})

watch(
  chatModels,
  (items) => {
    if (!items.length) {
      selectedModelName.value = undefined
      return
    }
    if (!items.some((item) => item.name === selectedModelName.value)) {
      selectedModelName.value = items[0].name
    }
  },
  { immediate: true },
)

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
    providerOptions: providerOptions.value,
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
    const response = await fetch(
      `/apis/console.api.aifoundation.halo.run/v1alpha1/models/${encodeURIComponent(
        model.name,
      )}/test-chat/stream`,
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
    if (chunk.type === 'error') {
      appendAssistantError(messageId, chunk.errorText || '请求失败')
      continue
    }
    if (chunk.type === 'text-delta' && chunk.delta) {
      appendAssistantContent(messageId, chunk.delta)
      continue
    }
    if (chunk.type === 'finish') {
      finishAssistantMessage(messageId, 'done')
    }
  }
}

function appendAssistantContent(messageId: string, content: string) {
  const message = messages.value.find((item) => item.id === messageId)
  if (message) {
    message.content += content
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

onBeforeUnmount(() => {
  abortCurrentRequest(false)
})
</script>

<template>
  <div class=":uno: h-[calc(100vh-8.25rem)] min-h-[34rem] p-2">
    <VLoading v-if="isLoading" />

    <VEmpty
      v-else-if="!chatModels.length"
      title="暂无可测试的对话模型"
      message="你可以在配置选项卡中添加或启用支持对话能力的模型"
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
          <select
            id="model-test-workbench-model"
            v-model="selectedModelName"
            name="model"
            aria-label="测试模型"
            class=":uno: h-9 min-w-0 flex-1 border border-gray-200 rounded-md bg-white px-3 text-sm text-gray-800 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
            :disabled="isStreaming"
          >
            <optgroup v-for="group in chatModelGroups" :key="group.key" :label="group.label">
              <option v-for="model in group.models" :key="model.name" :value="model.name">
                {{ modelOptionLabel(model) }}
              </option>
            </optgroup>
          </select>

          <div class=":uno: flex flex-none items-center gap-2">
            <VTag v-if="selectedModelProviderTypeDisplayName">{{
              selectedModelProviderTypeDisplayName
            }}</VTag>
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

        <footer class=":uno: border-t border-gray-200 bg-white p-3">
          <div class=":uno: flex flex-col gap-2 sm:flex-row">
            <textarea
              id="model-test-workbench-input"
              v-model="input"
              name="message"
              aria-label="输入消息"
              rows="2"
              placeholder="输入消息..."
              class=":uno: min-h-18 flex-1 resize-none border border-gray-200 rounded-md px-3 py-2.5 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/10"
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
      </section>

      <aside
        class=":uno: max-h-80 overflow-y-auto border-t border-gray-200 bg-white p-4 lg:max-h-none lg:border-l lg:border-t-0"
      >
        <div class=":uno: mb-4">
          <div class=":uno: text-sm text-gray-950 font-semibold">参数</div>
          <div class=":uno: mt-1 text-xs text-gray-500">当前请求的可选控制项</div>
        </div>

        <FormKit type="form" :actions="false">
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
